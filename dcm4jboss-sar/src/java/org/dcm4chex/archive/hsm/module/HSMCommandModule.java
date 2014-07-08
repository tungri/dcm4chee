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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

import org.dcm4chex.archive.common.FileStatus;
import org.dcm4chex.archive.util.FileUtils;

/**
 * @author franz.willer@gmail.com
 * @version $Revision:  $ $Date: $
 * @since Aug 17, 2010
 */
public class HSMCommandModule extends AbstractHSMModule {

    private static final String SRC_DST_PARAM = "%p";
    private static final String FS_PARAM = "%d";
    private static final String FILE_PARAM = "%f";
    private static final String DIR_PARAM = "%d";
    private static final String INFO_PARAM = "%i";
    
    private String[] copyCmd;
    private String[] fetchCmd;
    private File outgoingDir;
    private File absOutgoingDir;
    private File incomingDir;
    private File absIncomingDir;
    private boolean fileIDFromStdOut;
    private int commandFailedFileStatus;
    private int nonZeroExitFileStatus;
    private int matchFileStatus;
    private int noMatchFileStatus;
    private String[] qryCmd;

    private Pattern pattern;
    
    public final String getCopyCommand() {
        String s = cmd2str(copyCmd);
        if (fileIDFromStdOut)
            s += ":%f";
        return s;
    }

    public final void setCopyCommand(String cmd) {
        if (fileIDFromStdOut = cmd.endsWith(":%f"))
            cmd = cmd.substring(0, cmd.length()-3);
        copyCmd = str2cmd(cmd);
    }

    public final String getFetchCommand() {
        return cmd2str(fetchCmd);
    }

    public final void setFetchCommand(String cmd) {
        fetchCmd =str2cmd(cmd);
    }

    public final String getQueryCommand() {
        return cmd2str(qryCmd);
    }

    public final void setQueryCommand(String cmd) {
        qryCmd = str2cmd(cmd);
    }

    public final String getPattern() {
        return pattern.pattern();
    }

    public final void setPattern(String pattern) {
        this.pattern = Pattern.compile(pattern, Pattern.DOTALL);
    }

    public final String getNonZeroExitFileStatus() {
        return FileStatus.toString(nonZeroExitFileStatus);
    }

    public final void setNonZeroExitFileStatus(String status) {
        this.nonZeroExitFileStatus = FileStatus.toInt(status);
    }

    public final String getMatchFileStatus() {
        return FileStatus.toString(matchFileStatus);
    }

    public final void setMatchFileStatus(String status) {
        this.matchFileStatus = FileStatus.toInt(status);
    }

    public final String getNoMatchFileStatus() {
        return FileStatus.toString(noMatchFileStatus);
    }

    public final void setNoMatchFileStatus(String status) {
        this.noMatchFileStatus = FileStatus.toInt(status);
    }

    public final String getCommandFailedFileStatus() {
        return FileStatus.toString(commandFailedFileStatus);
    }

    public final void setCommandFailedFileStatus(String status) {
        this.commandFailedFileStatus = FileStatus.toInt(status);
    }
    
    public final String getOutgoingDir() {
        return outgoingDir.getPath();
    }

    public final void setOutgoingDir(String dir) {
        this.outgoingDir = new File(dir);
        this.absOutgoingDir = FileUtils.resolve(this.outgoingDir);
    }
    public final String getIncomingDir() {
        return incomingDir.getPath();
    }

    public final void setIncomingDir(String dir) {
        this.incomingDir = new File(dir);
        this.absIncomingDir = FileUtils.resolve(this.incomingDir);
    }

    public boolean isFileIDFromStdOut() {
        return fileIDFromStdOut;
    }

    public void setFileIDFromStdOut(boolean fileIDFromStdOut) {
        this.fileIDFromStdOut = fileIDFromStdOut;
    }

    @Override
    public File prepareHSMFile(String fsID, String filePath) {
        String path = stripTarIdentifier(fsID);
        if (copyCmd == null) {
            return FileUtils.toFile(path +"/"+ filePath);
        } else {
            return new File(absOutgoingDir,
                    new File(filePath).getName());
        }
    }

    @Override
    public String storeHSMFile(File file, String fsID, String filePath) throws HSMException {
        if (copyCmd == null) {
            log.warn("No copy command configured!");
            return filePath;
        } else {
            try {
                String cmd = makeCopyCommand(file.getPath(), fsID, filePath);
                log.info("Copy to HSM: " + cmd);
                ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                this.doCommand(cmd, stdout, "storeHSMFile");
                return fileIDFromStdOut ? stdout.toString().trim() : filePath;
            } finally {
                log.info("M-DELETE " + file);
                file.delete();
            }
        }
    }
    
    @Override
    public void failedHSMFile(File file, String fsID, String filePath) {
    }

    @Override
    public File fetchHSMFile(String fsID, String filePath) throws HSMException {
        if (fetchCmd == null) {
            int pos = fsID.indexOf(':');
            if (pos != -1)
                fsID = fsID.substring(++pos);
            return FileUtils.toFile(fsID, filePath);
        } else {
            if (absIncomingDir.mkdirs()) {
                log.info("M-WRITE "+absIncomingDir);
            }
            File tarFile;
            try {
                tarFile = File.createTempFile("hsm_", ".tar", absIncomingDir);
            } catch (IOException x) {
                throw new HSMException("Failed to create temp file in "+absIncomingDir, x);
            }
            String cmd = makeFetchCommand(fsID, filePath, tarFile.getPath());
            this.doCommand(cmd, null, "fetchHSMFile");
            return tarFile;
        }
    }

    @Override
    public void fetchHSMFileFinished(String fsID, String filePath, File file) throws HSMException {
        if (fetchCmd != null) {
            log.info("M-DELETE " + file);
            file.delete();
        }
    }
    
    @Override
    public Integer queryStatus(String fsID, String filePath, String userInfo) {
        if (qryCmd == null) {
            log.warn("No QueryCommand configured! HSM File Status can not be updated!");
            return null;
        }
        String cmd = makeQueryCommand(fsID, filePath, userInfo);
        try {
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            doCommand(cmd, stdout, "queryStatus");
            String result = stdout.toString();
            return pattern.matcher(result).matches() ? matchFileStatus
                        : noMatchFileStatus;
        } catch (Exception e) {
            log.error("Failed to execute " + cmd, e);
            return commandFailedFileStatus;
        }
    }
    
    public boolean applyPattern(String s) {
        return pattern.matcher(s).matches();
    }

    private String makeCopyCommand(String srcParam, String fsID, String fileID) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < copyCmd.length; i++) {
            sb.append(copyCmd[i] == SRC_DST_PARAM ? srcParam
                    : copyCmd[i] == FS_PARAM ? fsID
                    : copyCmd[i] == FILE_PARAM ? fileID
                    : copyCmd[i]);
        }
        return sb.toString();
    }

    private String makeFetchCommand(String fsParam, String fileParam,
            String dstParam) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < fetchCmd.length; i++) {
            sb.append(fetchCmd[i] == FS_PARAM ? fsParam
                    : fetchCmd[i] == FILE_PARAM ? fileParam
                    : fetchCmd[i] == SRC_DST_PARAM ? dstParam
                    : fetchCmd[i]);
        }
        return sb.toString();
    }
    
    private String makeQueryCommand(String dirParam, String fileParam,
            String infoParam) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < qryCmd.length; i++) {
            sb
                    .append(qryCmd[i] == DIR_PARAM ? dirParam
                            : qryCmd[i] == FILE_PARAM ? fileParam
                                    : qryCmd[i] == INFO_PARAM ? infoParam
                                            : qryCmd[i]);
        }
        return sb.toString();
    }

    
}
