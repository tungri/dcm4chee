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
 * Portions created by the Initial Developer are Copyright (C) 2006
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
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
package org.dcm4chex.archive.hsm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.dcm4chex.archive.common.FileStatus;
import org.dcm4chex.archive.ejb.interfaces.FileDTO;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2;
import org.dcm4chex.archive.ejb.interfaces.MD5;
import org.dcm4chex.archive.ejb.jdbc.QueryHSMMigrateCmd;

/**
 * @author franz.willer@gmail.com
 * @version $Revision: $
 * @since June 28, 2012
 */
public class MigrationTask implements Runnable {

    private static long taskCounter = 0;
    private long taskNumber;
    
    private HSMMigrateService service;
    private Iterator<String> tarfilenames;
    FileSystemMgt2 mgr;
    String sourceFs, targetFs;
    int[] counts;
    
    private byte[] buf = new byte[8192];

    private static final Logger log = Logger.getLogger(MigrationTask.class);
    
    public MigrationTask(HSMMigrateService service, Iterator<String> tarfilenames, 
            FileSystemMgt2 mgr, int[] count) {
        taskNumber = ++taskCounter;
        this.service = service;
        this.tarfilenames = tarfilenames;
        this.mgr = mgr;
        sourceFs = service.getSourceFileSystem();
        targetFs = service.getTargetFileSystem();
        this.counts = count;
    }
    private void migrateTarFile(String srcFsId, String srcTarFilename, String targetFsId) throws Exception {
        int nrOfCopies = new QueryHSMMigrateCmd().countFileCopiesOfTarFile(service.getSrcFsPk(), srcTarFilename, service.getTargetFsPk());
        log.debug("########### nrOfCopies:"+nrOfCopies);
        FileDTO[] dtos = null;
        if (nrOfCopies > 0 || service.isVerifyTar()) {
            dtos = mgr.getFilesOfTarFile(srcFsId, srcTarFilename);
            log.debug("########### dtos.length:"+dtos.length);
            if (nrOfCopies > 0 && dtos.length == nrOfCopies) {
                log.warn(this+" - File copies of all files in source tar file "+srcTarFilename+" already exists! Set file status of source to MIGRATED.");
                mgr.setFilestatusOfFilesOfTarFile(srcFsId, srcTarFilename, FileStatus.MIGRATED);
                return;
            }
        }
        File src = service.fetchTarFile(srcFsId, srcTarFilename);
        if (service.isVerifyTar()) {
            try {
                List<FileDTO> missingFiles = verifyTar(src, dtos, true);
                for (int i = 0, len = missingFiles.size() ; i < len ; i++) {
                    mgr.setFileStatus(missingFiles.get(i).getPk(), FileStatus.MD5_CHECK_FAILED);
                }
            } catch (Exception x) {
                log.error(x.getMessage()+" Set file status of source tar file entities to MD5_CHECK_FAILED and skip migration of "+srcTarFilename);
                mgr.setFilestatusOfFilesOfTarFile(srcFsId, srcTarFilename, FileStatus.MD5_CHECK_FAILED);
                service.fetchTarFileFinished(srcFsId, srcTarFilename, src);
                return;
            }
        }
        int nrOfFiles = 0;
        String targetTarFilename = service.toTargetFilename(srcTarFilename);
        File target = null;
        try {
            target = service.prepareHSMFile(targetFsId, targetTarFilename);
            if(!target.exists()) {
                if (!target.getParentFile().exists()) {
                    log.info("M-CREATE Directory "+target);
                    target.getParentFile().mkdirs();
                }
                log.info("M-CREATE "+target);
                target.createNewFile();
            }
            if (target.length() > 0) {
                log.warn(this+" - Target tar file "+target+" already exists! Skip migration with assumption that this file is already migrated and set file status of source to MIGRATED.");
                mgr.setFilestatusOfFilesOfTarFile(srcFsId, srcTarFilename, FileStatus.MIGRATED);
                return;
            } else {
                FileChannel srcCh = null;
                FileChannel destCh = null;
                try {
                    srcCh = new FileInputStream(src).getChannel();
                    destCh = new FileOutputStream(target).getChannel();
                    destCh.transferFrom(srcCh, 0, srcCh.size());
                } finally {
                  if(srcCh != null)
                      srcCh.close();
                  if(destCh != null)
                      destCh.close();
                }
            }
            targetTarFilename = service.storeHSMFile(target, targetFsId, targetTarFilename);
            nrOfFiles = mgr.migrateFilesOfTarFile(srcFsId, srcTarFilename, targetFsId, targetTarFilename, 
                    FileStatus.toInt(service.getTargetFileStatus()));
            if (service.isVerifyTar()) {
                verifyTar(service.fetchTarFile(targetFsId, targetTarFilename), mgr.getFilesOfTarFile(targetFsId, targetTarFilename), false);
            }
        } catch (Exception x) {
            if (target != null) {
                service.failedHSMFile(target, targetFsId, targetTarFilename);
                target.delete();
                log.error("Remove file entities of failed migrated tar file!");
                mgr.deleteFilesOfInvalidTarFile(targetFsId, targetTarFilename);
            }
            throw x;
        } finally {
            service.fetchTarFileFinished(srcFsId, srcTarFilename, src);
        }
        synchronized(counts) {
            counts[1] += nrOfFiles;
            log.info(nrOfFiles+" files migrated by "+this+" - Tasks in progress:"+counts[0]);
        }
    }

    private List<FileDTO> verifyTar(File file, FileDTO[] files, boolean markMissingFiles) throws IOException, VerifyTarException {
        Map<String, byte[]> entries = VerifyTar.verify(file, buf);
        if (entries == null) 
            throw new VerifyTarException("Verify tar failed! Tar file has no entries.");
        List<FileDTO> missingFiles = new ArrayList<FileDTO>();
        for (int i = 0 ; i < files.length ; i++) {
            String filePath = files[i].getFilePath();
            String filepathInTar = files[i].getFilePath().substring(filePath.indexOf('!')+1);
            byte[] md5 = entries.get(filepathInTar);
            if (md5 == null) {
                String msg = this+" - Verify tar failed! "+filepathInTar+" not found in tar file "+file;
                if (markMissingFiles) {
                    log.error(msg+". Set file status to MD5_CHECK_FAILED");
                    missingFiles.add(files[i]);
                } else {
                    throw new VerifyTarException(msg);
                }
            }
            if (!Arrays.equals(files[i].getFileMd5(), md5)) {
                String msg = this+" - Verify tar failed! Different MD5 of file "+filepathInTar+" and file entity! ("+
                              MD5.toString(md5)+" vs. "+files[i].getMd5String()+ ")";
                if (markMissingFiles) {
                    log.error(msg+". Set file status to MD5_CHECK_FAILED");
                    missingFiles.add(files[i]);
                } else {
                    throw new VerifyTarException(msg);
                }
            }
        }
        if (missingFiles.size() == files.length)
            throw new VerifyTarException(this+" - Verify tar failed! No file entity found in tar file.");
        return missingFiles;
    }

    /**
     * Get counts of migrationTasks
     * [0]: remaining tasks for a specific migration run
     * [1]: count migrated tar files.
     * @return
     */
    public int[] getCounts() {
        return counts;
    }

    public void run() {
        String fn = "";
        synchronized (tarfilenames) {
            fn = tarfilenames.hasNext() ? tarfilenames.next() : null;
        }
        while(fn != null) {
            log.info(this+" - Migrate tar file: "+fn);
            try {
                migrateTarFile(sourceFs, fn, targetFs);
            } catch (Exception e) {
                try {
                    log.error(this+" - Migration of "+fn+" failed!", e);
                    mgr.setFilestatusOfFilesOfTarFile(sourceFs, fn, FileStatus.MIGRATION_FAILED);
                } catch (Exception x) {
                    log.error(this+" - Set FileStatus (MIGRATION_FAILED) of "+fn+" failed!", x);
                }
            }
            synchronized (tarfilenames) {
                fn = tarfilenames.hasNext() ? tarfilenames.next() : null;
            }
        }
        service.removeMigrationTask(this);
    }

    public String toString() {
        return "MigrationTask:"+taskNumber;
    }
}
