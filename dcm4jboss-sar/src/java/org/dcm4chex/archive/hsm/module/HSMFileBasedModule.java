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

package org.dcm4chex.archive.hsm.module;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.dcm4chex.archive.common.FileStatus;
import org.dcm4chex.archive.hsm.VerifyTar;
import org.dcm4chex.archive.util.FileUtils;

/**
 * @author franz.willer@gmail.com
 * @version $Revision:  $ $Date: $
 * @since Aug 17, 2010
 */
public class HSMFileBasedModule extends AbstractHSMModule {

    private static final String FILE_PARAM = "%f";
    private static final String DATE_PARAM = "%d";
    private static final String NEWLINE = System.getProperty("line.separator", "\n");
    private static final String CAL_FIELD_NAMES = "yMd";
    private static final int[] CAL_FIELDS = new int[]{Calendar.YEAR,Calendar.MONTH,Calendar.DAY_OF_MONTH};
    private String mountFailedCheckFile = "NO_MOUNT";
    
    private byte[] buf = new byte[8192];

    private int[] retentionTime = new int[2];
    private String[] accessTimeCmd;
    private boolean setAccessTimeAfterSetReadonly;
    
    private SimpleDateFormat df;
    
    private HashMap<String,Integer> extensionStatusMap = new HashMap<String,Integer>();
    
    private Integer noStatusFileStatus;
    private boolean checkMD5forStatusChange;

    public String getRetentionTime() {
        return (setAccessTimeAfterSetReadonly ? "+" : "" )+String.valueOf(retentionTime[0])+CAL_FIELD_NAMES.charAt(retentionTime[1]);
    }

    public void setRetentionTime(String s) {
        int len = s.length();
        setAccessTimeAfterSetReadonly = s.charAt(0) == '+';
        retentionTime[0] = Integer.parseInt(s.substring(setAccessTimeAfterSetReadonly ? 1 : 0, --len));
        int idx = CAL_FIELD_NAMES.indexOf(s.charAt(len));
        if (idx<0 || idx > 2) {
            throw new IllegalArgumentException("Last character must be 'y', 'M' or 'd'!");
        }
        retentionTime[1] = idx; 
    }

    public final String getAccessTimeCmd() {
        return cmd2str(accessTimeCmd);
    }

    public final void setAccessTimeCmd(String cmd) {
        accessTimeCmd = str2cmd(cmd);
    }
    
    public String getPattern() {
        return df.toPattern();
    }

    public void setPattern(String pattern) {
        df  = new SimpleDateFormat(pattern);
    }

    public String getStatusExtensions() {
        StringBuilder sb = new StringBuilder();
        for ( Map.Entry<String,Integer> entry : extensionStatusMap.entrySet()) {
            sb.append(entry.getKey()).append("=")
                .append(FileStatus.toString(entry.getValue())).append(NEWLINE);
        }
        sb.append(noStatusFileStatus == null ? NONE : FileStatus.toString(noStatusFileStatus));
        return sb.toString();
    }
    
    public void setStatusExtensions(String s) {
        extensionStatusMap.clear();
        noStatusFileStatus = null;
        StringTokenizer st = new StringTokenizer(s, " \t\r\n;");
        int pos;
        String token;
        while (st.hasMoreTokens()) {
            token = st.nextToken().trim();
            if ((pos=token.indexOf('=')) == -1) {
                noStatusFileStatus = NONE.equals(token) ? null : FileStatus.toInt(token);
            } else {
                extensionStatusMap.put(token.substring(0,pos), 
                        FileStatus.toInt(token.substring(++pos)));
            }
        }
    }
    
    public boolean isCheckMD5forStatusChange() {
        return checkMD5forStatusChange;
    }

    public void setCheckMD5forStatusChange(boolean checkMD5forStatusChange) {
        this.checkMD5forStatusChange = checkMD5forStatusChange;
    }

    public final String getMountFailedCheckFile() {
        return mountFailedCheckFile;
    }

    public final void setMountFailedCheckFile(String mountFailedCheckFile) {
        this.mountFailedCheckFile = mountFailedCheckFile;
    }
    
    protected void checkMount(String fsID) throws HSMException {
        File nomount = FileUtils.toFile(stripTarIdentifier(fsID), mountFailedCheckFile);
        if (nomount.exists()) {
            log.warn("Mount on " + fsID + " seems broken! mountFailedCheckFile file exists:" + mountFailedCheckFile);
            throw new HSMException("Filesystem not mounted! fsID:"+fsID, null, HSMException.ERROR_ON_FILESYSTEM_LEVEL);
        }
    }
    
    @Override
    public File prepareHSMFile(String fsID, String filePath) throws HSMException {
        checkMount(fsID);
        return FileUtils.toFile(stripTarIdentifier(fsID), filePath);
    }

    @Override
    public String storeHSMFile(File file, String fsID, String filePath) throws HSMException {
        checkMount(fsID);
        if (setAccessTimeAfterSetReadonly)
            file.setReadOnly();
        if (accessTimeCmd != null) {
            String cmd = makeAccessTimeCommand(file.getAbsolutePath(), getRetentionDate());
            doCommand(cmd, null, "Set Access Time of file "+file);
        }
        if (!setAccessTimeAfterSetReadonly)
            file.setReadOnly();
        return filePath;
    }

    private String getRetentionDate() {
        Calendar c = Calendar.getInstance();
        c.add(CAL_FIELDS[retentionTime[1]], retentionTime[0]);
        return df.format(c.getTime());
    }

    @Override
    public void failedHSMFile(File file, String fsID, String filePath) {
    }

    @Override
    public File fetchHSMFile(String fsID, String filePath) throws HSMException {
        checkMount(fsID);
        return FileUtils.toFile(stripTarIdentifier(fsID), filePath);
    }

    @Override
    public void fetchHSMFileFinished(String fsID, String filePath, File file) throws HSMException {
    }

    @Override
    public Integer queryStatus(String fsID, String filePath, String userInfo) throws HSMException {
        checkMount(fsID);
        boolean isTar = fsID.startsWith("tar:");
        for ( Map.Entry<String,Integer> entry : extensionStatusMap.entrySet()) {
            if (FileUtils.toFile(isTar ? fsID.substring(4) : fsID, filePath+entry.getKey()).exists()) {
                if (checkMD5forStatusChange) {
                    if (isTar) {
                        try {
                            VerifyTar.verify(FileUtils.toFile(fsID.substring(4), filePath), buf);
                        } catch (Exception x) {
                            log.error("Verify tar file failed! dirPath:"+fsID+" filePath:"+filePath, x);
                            return FileStatus.MD5_CHECK_FAILED;
                        }
                    } else {
                        log.info("Check MD5 for Status change ignored. Not a tar filesystem!");
                    }
                }
                return entry.getValue();
            }
        }
        return noStatusFileStatus;
    }
    

    private String makeAccessTimeCommand(String file, String date) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < accessTimeCmd.length; i++) {
            sb.append(accessTimeCmd[i] == DATE_PARAM ? date : accessTimeCmd[i] == FILE_PARAM ? file : accessTimeCmd[i]);
        }
        return sb.toString();
    }
    
}
