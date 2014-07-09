/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), available at http://sourceforge.net/projects/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa HealthCare.
 * Portions created by the Initial Developer are Copyright (C) 2006-2008
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See listed authors below.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */
package org.dcm4chex.archive.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date$
 * @since Mar 4, 2009
 */
public class CacheJournal {

    private static final String DEFAULT_FILE_PATH_PATTERN = "yyyy/MM/dd/HH";
    private static final Logger log = 
            LoggerFactory.getLogger(CacheJournal.class);

    private File journalRootDir;
    private File dataRootDir;
    private SimpleDateFormat journalFilePathFormat =
            new SimpleDateFormat(DEFAULT_FILE_PATH_PATTERN);
    private boolean freeIsRunning = false;

    public File getJournalRootDir() {
        return journalRootDir;
    }

    public void setJournalRootDir(File journalRootDir) {
        assertWritableDiretory(journalRootDir);
        this.journalRootDir = journalRootDir;
    }

    public File getDataRootDir() {
        return dataRootDir;
    }

    public void setDataRootDir(File dataRootDir) {
        assertWritableDiretory(dataRootDir);
        this.dataRootDir = dataRootDir;
    }

    private static void assertWritableDiretory(File dir) {
        mkdirs(dir);
        if (!dir.isDirectory() || !dir.canWrite()) {
            throw new IllegalArgumentException(
                    "Not a writable directory:" + dir);
        }
    }

    public String getJournalFilePathFormat() {
        return journalFilePathFormat.toPattern();
    }

    public void setJournalFilePathFormat(String format) {
        this.journalFilePathFormat = new SimpleDateFormat(format);
    }

    public synchronized void record(File f) throws IOException {
        record(f, false);
    }

    public synchronized void record(File f, boolean update)
            throws IOException {
        String path = f.getPath().substring(
                    dataRootDir.getPath().length() + 1);
        long time = System.currentTimeMillis();
        File journalFile = getJournalFile(time);
        if (journalFile.exists()) {
            if (update && journalFile.equals(getJournalFile(f.lastModified()))) {
                log.debug("{} already contains entry for {}", journalFile, f);
                return;
            }
            log.debug("M-UPDATE {}", journalFile);
        } else {
            mkdirs(journalFile.getParentFile());
            log.debug("M-WRITE {}", journalFile);
        }
        FileWriter journal = new FileWriter(journalFile, true);
        try {
            journal.write(path + '\n');
        } finally {
            journal.close();
        }
        f.setLastModified(time);
    }

    private static void mkdirs(File dir) {
        if (dir.exists()) {
            return;
        }
        mkdirs(dir.getParentFile());
        if (dir.mkdir()) {
            log.info("M-WRITE {}", dir);
        }
    }

    private synchronized File getJournalFile(long time) {
        return new File(journalRootDir,
                journalFilePathFormat.format(new Date(time)));
    }

    public long free(long size) throws IOException {
        synchronized (this) {
            if (freeIsRunning) {
                return 0L;
            }
            freeIsRunning = true;
        }
        try {
            return free(size, journalRootDir);
        } finally {
            freeIsRunning = false;
        }
    }

    public void clearCache() {
        deleteFilesOrDirectories(journalRootDir.listFiles());
        deleteFilesOrDirectories(dataRootDir.listFiles());
    }

    public boolean isEmpty() {
        return dataRootDir.list().length == 0;
    }

    public static void deleteFilesOrDirectories(File[] files) {
        for (File f : files) {
            deleteFileOrDirectory(f);
        }
    }

    public static boolean deleteFileOrDirectory(File f) {
        if (f.isDirectory()) {
            deleteFilesOrDirectories(f.listFiles());
        }
        if (!f.delete()) {
            log.warn("Failed to delete {}", f);
            return false;
        }
        log.info("M-DELETE {}", f);
        return true;
    }

    private long free(long size, File dir) throws IOException {
        long free = 0L;
        if (dir.isDirectory()) {
            String[] fnames = dir.list();
            Arrays.sort(fnames);
            for (String fname : fnames) {
                free += free(size - free, new File(dir, fname));
                if (free >= size) {
                    break;
                }
            }
        } else {
            BufferedReader journal = new BufferedReader(new FileReader(dir));
            try {
                String path;
                while ((path = journal.readLine()) != null) {
                    File f = new File(dataRootDir, path);
                    if (!f.exists()) {
                        log.debug("{} already deleted", f);
                        continue;
                    }
                    if (!getJournalFile(f.lastModified()).equals(dir)) {
                        log.debug("{} was accessed after record in {}", f, dir);
                        continue;
                    }
                    long flen = sizeOfFileOrDirectory(f);
                    if (deleteFileAndParents(f, dataRootDir)) {
                        free += flen;
                    }
                }
            } finally {
                journal.close();
            }
            deleteFileAndParents(dir, journalRootDir);
        }
        return free;
    }

    public static long sizeOfFileOrDirectory(File f) {
        if (f.isFile()) {
            return f.length();
        }
        long size = 0;
        File[] files = f.listFiles();
        for (int i = 0; i < files.length; i++) {
            size += sizeOfFileOrDirectory(files[i]);
        }
        return size;
    }

    public static boolean deleteFileAndParents(File f, File baseDir) {
        if (!deleteFileOrDirectory(f)) {
            return false;
        }
        File dir = f.getParentFile();
        while (!dir.equals(baseDir)) {
            if (!dir.delete()) {
               break;
            }
            log.info("M-DELETE {}", dir);
            dir = dir.getParentFile();
        }
        return true;
    }

}
