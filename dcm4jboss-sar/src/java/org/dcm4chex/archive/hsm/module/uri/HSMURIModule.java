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
 * TIANI Medgraph AG.
 * Portions created by the Initial Developer are Copyright (C) 2005
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * Gunter Zeilinger <gunter.zeilinger@tiani.com>
 * Franz Willer <franz.willer@gwi-ag.com>
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

package org.dcm4chex.archive.hsm.module.uri;

import java.io.File;
import java.io.IOException;

import org.dcm4chex.archive.common.FileStatus;
import org.dcm4chex.archive.hsm.module.AbstractHSMModule;
import org.dcm4chex.archive.hsm.module.HSMException;
import org.dcm4chex.archive.util.FileUtils;
import org.apache.log4j.Logger;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

//@formatter:off
/**
 * @author kianusch.sayah-karadji@agfa.com
 * @version $Revision: $ $Date: $
 * @since Aug 17, 2010
 */

/* 
 *  put
 *    file:     /impax/server_RAD/sts/cache/tar-outgoing/F07E874F-1421814.tar
 *    fsid:     tar:/impax/server_RAD/lts filePath:
 *    filePath: 2013/3/31/0/D1E8EC6C/F07E874F-1421814.tar
 * 
 *  get
 *    fsid:         tar:/impax/server_RAD/lts
 *    filePath:     2007/11/10/20/43C273CF/B6769CBC-A85D7E1D.tar
 *    (destination) /impax/server_RAD/sts/cache/tar-incoming/B6769CBC-A85D7E1D.tar
 */
//@formatter:on

public class HSMURIModule extends AbstractHSMModule {
    private static Logger log = Logger.getLogger(HSMURIModule.class);

    private String destinationFilePathFormat;

    private String chkCmd;

    private File sshPrivateKeyFile;

    private File absSshPrivateKeyFile;

    private File outgoingDir;

    private File absOutgoingDir;

    private File incomingDir;

    private File absIncomingDir;

    private int fileStoredStatus;

    private int fileNotStoredStatus;

    private HashMap<String, Integer> extensionStatusMap = new HashMap<String, Integer>();

    private Integer noStatusFileStatus;

    private boolean setAccessTimeAfterSetReadonly;

    private int[] retentionTime = new int[2];

    private static final String CAL_FIELD_NAMES = "yMd";

    private static final int[] CAL_FIELDS = new int[] { Calendar.YEAR, Calendar.MONTH, Calendar.DAY_OF_MONTH };

    private static final String NEWLINE = System.getProperty("line.separator", "\n");

    public final void setCheckCommand(String cmd) {
        this.chkCmd = cmd;
    }

    public final String getCheckCommand() {
        return chkCmd;
    }

    public final void setFileStoredStatus(String status) {
        this.fileStoredStatus = FileStatus.toInt(status);
    }

    public final String getFileStoredStatus() {
        return FileStatus.toString(fileStoredStatus);
    }

    public final void setFileNotStoredStatus(String status) {
        this.fileNotStoredStatus = FileStatus.toInt(status);
    }

    public final String getFileNotStoredStatus() {
        return FileStatus.toString(fileNotStoredStatus);
    }

    public final String getDestinationFilePathFormat() {
        return destinationFilePathFormat;
    }

    public final void setDestinationFilePathFormat(String pattern) {
        this.destinationFilePathFormat = pattern;
    }

    public final String getIncomingDir() {
        return incomingDir.getPath();
    }

    public final void setIncomingDir(String dir) {
        this.incomingDir = new File(dir);
        this.absIncomingDir = FileUtils.resolve(this.incomingDir);
    }

    public final String getOutgoingDir() {
        return outgoingDir.getPath();
    }

    public final void setOutgoingDir(String dir) {
        this.outgoingDir = new File(dir);
        this.absOutgoingDir = FileUtils.resolve(this.outgoingDir);
    }

    public final void setSshPrivateKeyFile(String file) {
        this.sshPrivateKeyFile = new File(file);
        this.absSshPrivateKeyFile = FileUtils.resolve(this.sshPrivateKeyFile);
    }

    public final String getSshPrivateKeyFile() {
        return sshPrivateKeyFile.getPath();
    }

    public final String getStatusExtensions() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : extensionStatusMap.entrySet()) {
            sb.append(entry.getKey()).append("=").append(FileStatus.toString(entry.getValue())).append(NEWLINE);
        }
        sb.append(noStatusFileStatus == null ? NONE : FileStatus.toString(noStatusFileStatus));
        return sb.toString();
    }

    public final void setStatusExtensions(String s) {
        extensionStatusMap.clear();
        noStatusFileStatus = null;
        StringTokenizer st = new StringTokenizer(s, " \t\r\n;");
        int pos;
        String token;
        while (st.hasMoreTokens()) {
            token = st.nextToken().trim();
            if ((pos = token.indexOf('=')) == -1) {
                noStatusFileStatus = NONE.equals(token) ? null : FileStatus.toInt(token);
            } else {
                extensionStatusMap.put(token.substring(0, pos), FileStatus.toInt(token.substring(++pos)));
            }
        }
    }

    public String getRetentionTime() {
        return (setAccessTimeAfterSetReadonly ? "+" : "") + String.valueOf(retentionTime[0])
                + CAL_FIELD_NAMES.charAt(retentionTime[1]);
    }

    public void setRetentionTime(String s) {
        int len = s.length();
        setAccessTimeAfterSetReadonly = s.charAt(0) == '+';
        retentionTime[0] = Integer.parseInt(s.substring(setAccessTimeAfterSetReadonly ? 1 : 0, --len));
        if (retentionTime[0] == 0) {
            retentionTime[1] = -1;
        } else {
            int idx = CAL_FIELD_NAMES.indexOf(s.charAt(len));
            if (idx < 0 || idx > 2) {
                throw new IllegalArgumentException("Last character must be 'y', 'M' or 'd'!");
            }
            retentionTime[1] = idx;
        }
    }

    private long getRetentionDate() {
        if (retentionTime[0] == 0)
            return 0L;
        Calendar c = Calendar.getInstance();
        c.add(CAL_FIELDS[retentionTime[1]], retentionTime[0]);
        return (c.getTimeInMillis() / 1000);
    }

    @Override
    public File prepareHSMFile(String fsID, String filePath) {
        return new File(absOutgoingDir, new File(filePath).getName());
    }

    @Override
    public String storeHSMFile(File file, String fsID, String filePath) throws HSMException {
        if (destinationFilePathFormat != null && (!destinationFilePathFormat.equals("NONE"))) {
            SimpleDateFormat sdf = new SimpleDateFormat(destinationFilePathFormat);
            filePath = sdf.format(new Date()) + '/' + new File(filePath).getName();
        }

        try {
            log.info("Copy to URI: " + file.getPath() + " " + stripTarIdentifier(fsID) + " " + filePath);
            Uri.copyTo(file.getPath(), stripTarIdentifier(fsID), filePath, absSshPrivateKeyFile.getPath(), getRetentionDate(), setAccessTimeAfterSetReadonly);
            return filePath;
        } catch (Exception e) {
            throw new HSMException("copy failed...", e);
        } finally {
            log.info("M-DELETE " + file);
            file.delete();
        }
    }

    @Override
    public void failedHSMFile(File file, String fsID, String filePath) {
    }

    @Override
    public File fetchHSMFile(String fsID, String filePath) throws HSMException {
        try {
            if (absIncomingDir.mkdirs()) {
                log.info("M-WRITE " + absIncomingDir);
            }
            File tarFile;
            try {
            tarFile = File.createTempFile("hsm_", ".tar", absIncomingDir);
        } catch (IOException x) {
            throw new HSMException("Failed to create temp file in " + absIncomingDir, x);
        }
            Uri.copyFrom(stripTarIdentifier(fsID) + '/' + filePath, tarFile.getPath(), absSshPrivateKeyFile.getPath());
            return tarFile;
        } catch (Exception e) {
            throw new HSMException("fetch failed...", e);
        }
    }

    @Override
    public void fetchHSMFileFinished(String fsID, String filePath, File file) throws HSMException {
        file.delete();
    }

    @Override
    public Integer queryStatus(String fsID, String filePath, String userInfo) throws HSMException {
        try {
            for (Map.Entry<String, Integer> entry : extensionStatusMap.entrySet()) {
                if (Uri.exists(stripTarIdentifier(fsID) + '/' + filePath + entry.getKey(),
                        absSshPrivateKeyFile.getPath()) >= 0) {
                    if (!chkCmd.equals("NONE")) {
                File tmpTarFile = fetchHSMFile(fsID, filePath);
                String cmd = new String();
                cmd = chkCmd + " " + tmpTarFile;
                try {
                    this.doCommand(cmd, null, "URI queryStatus");
                    tmpTarFile.delete();
                } catch (HSMException e) {
                    if (tmpTarFile.exists())
                        tmpTarFile.delete();
                    return fileNotStoredStatus;
                }
            }
                    return entry.getValue();
                }
            }
            return noStatusFileStatus;
        } catch (Exception e) {
            throw new HSMException("query failed...", e);
        }
    }

}