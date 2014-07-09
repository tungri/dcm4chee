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

package org.dcm4chex.archive.hsm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.apache.commons.compress.tar.TarEntry;
import org.apache.commons.compress.tar.TarOutputStream;
import org.dcm4che.util.BufferedOutputStream;
import org.dcm4che.util.MD5Utils;
import org.dcm4chex.archive.common.DeleteStudyOrder;
import org.dcm4chex.archive.common.FileStatus;
import org.dcm4chex.archive.common.FileSystemStatus;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.ejb.interfaces.FileDTO;
import org.dcm4chex.archive.ejb.interfaces.FileSystemDTO;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2;
import org.dcm4chex.archive.exceptions.ConcurrentStudyStorageException;
import org.dcm4chex.archive.mbean.AbstractDeleterService;
import org.dcm4chex.archive.mbean.FileSystemMgt2Delegate;
import org.dcm4chex.archive.mbean.JMSDelegate;
import org.dcm4chex.archive.util.FileSystemUtils;
import org.dcm4chex.archive.util.FileUtils;

/**
 * File Move Service to move (or copy) files from an ONLINE (RW) file system group to another RW file system group.
 * 
 * @author franz.willer@gmail.com
 * @version $Revision: $ $Date: $
 * @since Nov 6, 2010
 */
public class FileMoveService extends AbstractDeleterService implements MessageListener {

    private static final String ERROR = "ERROR";
    private static final String NOT_APPLICABLE = "N.A.";
    private String srcFsGroup;
    private String destFsGroup;
    
    private Long minFree = null;
 
    private FileSystemMgt2Delegate fsmgt = new FileSystemMgt2Delegate(this);
    
    private JMSDelegate jmsDelegate = new JMSDelegate(this);
    private String moveFilesOfStudyQueueName;
    private RetryIntervalls retryIntervalls = new RetryIntervalls();
    private boolean keepSrcFiles;
    private boolean keepMovedFilesOnError;
    Integer destFileStatus;
    
    private int bufferSize;
    private boolean verifyCopy;
    
    private ObjectName hsmModuleServicename = null;
    
    private static final int MD5SUM_ENTRY_LEN = 52;
    
    public String getSrcFsGroup() {
        return srcFsGroup == null ? NONE : srcFsGroup;
    }

    public void setSrcFsGroup(String srcFsGroup) {
        this.srcFsGroup = NONE.equals(srcFsGroup) ? null : srcFsGroup;
    }

    public String getDestFsGroup() {
        return destFsGroup == null ? NONE : destFsGroup;
    }

    public void setDestFsGroup(String destFsGroup) {
        if (NONE.equals(destFsGroup)) {
            this.destFsGroup = null;
        } else {
            if (destFsGroup.equals(srcFsGroup)) {
                throw new IllegalArgumentException("Source and Destination must not be the same!");
            }
            this.destFsGroup = destFsGroup;
        }
    }

    public long getMinFreeDiskSpaceBytes() {
        try {
            return srcFsGroup == null ? 0 : minFree != null ? minFree.longValue() : 
                updateMinFreeDiskSpaceFromSrcFSGroup();
        } catch (Exception x) {
            log.error("Can't get MinFreeDiskSpaceFromSrcFS! return MIN_FREE_DISK_SPACE:"+MIN_FREE_DISK_SPACE);
            return MIN_FREE_DISK_SPACE;
        } 
    }
    
    public long updateMinFreeDiskSpaceFromSrcFSGroup() throws Exception  {
        String mfd = (String) server.getAttribute(new ObjectName(getFileSystemMgtServiceNamePrefix()+srcFsGroup), 
                "MinimumFreeDiskSpace");
        log.info("getMinFreeDiskSpaceFromSrcFS group:"+srcFsGroup+" minFree:"+mfd);
        if (mfd.equalsIgnoreCase(NONE)) 
            return 0;
        if (mfd.endsWith("%")) {
            float ratio = Float.parseFloat(mfd.substring(0,mfd.length()-1));
            FileSystemDTO[] fsDTOs =
                fileSystemMgt().getFileSystemsOfGroup(srcFsGroup);
            long total = 0L;
            for (FileSystemDTO fsDTO : fsDTOs) {
                int status = fsDTO.getStatus();
                if (status == FileSystemStatus.RW
                        || status == FileSystemStatus.DEF_RW) {
                    File dir = FileUtils.toFile(fsDTO.getDirectoryPath());
                    if (dir.isDirectory()) {
                        total += FileSystemUtils.totalSpace(dir.getPath());
                    }
                }
            }
            minFree = (long) (total * ratio / 100);
        } else {
            minFree = FileUtils.parseSize(mfd, MIN_FREE_DISK_SPACE);
        }
        return minFree;
    }
    
    public String getMinFreeDiskSpace() {
        if(srcFsGroup == null) {
            return NOT_APPLICABLE;
        }
        try {
            updateMinFreeDiskSpaceFromSrcFSGroup();
        } catch (Exception x) {
            log.error("Can't get MinFreeDiskSpaceFromSrcFS! return MIN_FREE_DISK_SPACE:"+MIN_FREE_DISK_SPACE, x);
            return ERROR;
        }
        return minFree == 0 ? NONE : FileUtils.formatSize(minFree);
    }

    public long getExpectedDataVolumePerDayBytes() {
        try {
            return (srcFsGroup == null) ? -1 :
                (Long) server.getAttribute(new ObjectName(getFileSystemMgtServiceNamePrefix()+srcFsGroup), "ExpectedDataVolumePerDayBytes");
        } catch (Exception x) {
            log.error("Failed to get ExpectedDataVolumePerDayBytes!", x);
            return -1 ;
        }
    }

    public final String getExpectedDataVolumePerDay() throws Exception {
        return  srcFsGroup == null ? NOT_APPLICABLE : FileUtils.formatSize(getExpectedDataVolumePerDayBytes());
    }

    public String getUsableDiskSpaceStringOnDest() {
        try {
            if (destFsGroup == null) 
                return NOT_APPLICABLE;
            if (destFsGroup.indexOf('@') == -1)
                return (String) server.getAttribute(new ObjectName(getFileSystemMgtServiceNamePrefix()+destFsGroup), "UsableDiskSpaceString");
            FileSystemDTO fsDTO = getDestinationFilesystem(fileSystemMgt());
            if (fsDTO == null)
                return "Dest FS not found!";
            String dirPath = fsDTO.getDirectoryPath();
            if (dirPath.startsWith("tar:"))
                dirPath = dirPath.substring(4);
            File dir = FileUtils.toFile(dirPath);
            return dir.isDirectory() ? FileUtils.formatSize(FileSystemUtils.freeSpace(dir.getPath()) - getMinFreeDiskSpaceOnDestFS())
                                     : hsmModuleServicename == null ? "UNKNOWN (destination path not found)" : "n.a. (HSM)";

        } catch (Exception x) {
            log.error("Failed to get UsableDiskSpaceStringOnDest!", x);
            return ERROR ;
        }
    }
    
    public long getUsableDiskSpaceOnDest() {
        try {
            if (destFsGroup == null) 
                return -1;
            if (destFsGroup.indexOf('@') == -1)
                return (Long) server.getAttribute(new ObjectName(getFileSystemMgtServiceNamePrefix()+destFsGroup), "UsableDiskSpace");
            FileSystemDTO fsDTO = getDestinationFilesystem(fileSystemMgt());
            if (fsDTO == null)
                return -2;
            String dirPath = fsDTO.getDirectoryPath();
            if (dirPath.startsWith("tar:"))
                dirPath = dirPath.substring(4);
            File dir = FileUtils.toFile(dirPath);
            return dir.isDirectory() ? FileSystemUtils.freeSpace(dir.getPath()) - getMinFreeDiskSpaceOnDestFS()
                                     : hsmModuleServicename == null ? -1 : Long.MAX_VALUE;

        } catch (Exception x) {
            log.error("Failed to get UsableDiskSpaceOnDest!", x);
            return -3;
        }
    }
    
    private long getMinFreeDiskSpaceOnDestFS() throws Exception {
        int pos = destFsGroup.indexOf('@');
        String fsGrp = (pos == -1) ? destFsGroup : destFsGroup.substring(++pos);
        return (Long) server.getAttribute(new ObjectName(getFileSystemMgtServiceNamePrefix()+fsGrp), "MinimumFreeDiskSpaceBytes");
    }

    public String getDestFileStatus() {
        return destFileStatus == null ? "-" : FileStatus.toString(destFileStatus);
    }

    public void setDestFileStatus(String status) {
        this.destFileStatus = "-".equals(status) ? null : FileStatus.toInt(status);
    }

    public String getFileSystemMgtServiceNamePrefix() {
        return fsmgt.getFileSystemMgtServiceNamePrefix();
    }

    public void setFileSystemMgtServiceNamePrefix(String prefix) {
        fsmgt.setFileSystemMgtServiceNamePrefix(prefix);
    }
    
    public final String getRetryIntervalls() {
        return retryIntervalls.toString();
    }

    public final void setRetryIntervalls(String s) {
        this.retryIntervalls = new RetryIntervalls(s);
    }

    public boolean isKeepSrcFiles() {
        return keepSrcFiles;
    }

    public void setKeepSrcFiles(boolean keepSrcFiles) {
        this.keepSrcFiles = keepSrcFiles;
    }

    public boolean isKeepMovedFilesOnError() {
        return keepMovedFilesOnError;
    }

    public void setKeepMovedFilesOnError(boolean b) {
        this.keepMovedFilesOnError = b;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public boolean isVerifyCopy() {
        return verifyCopy;
    }

    public void setVerifyCopy(boolean verifyCopy) {
        this.verifyCopy = verifyCopy;
    }

    public String getQueueName() {
        return moveFilesOfStudyQueueName;
    }

    public void setQueueName(String name) {
        this.moveFilesOfStudyQueueName = name;
    }

    public ObjectName getJmsServiceName() {
        return jmsDelegate.getJmsServiceName();
    }

    public void setJmsServiceName(ObjectName jmsServiceName) {
        jmsDelegate.setJmsServiceName(jmsServiceName);
    }

    public final String getHSMModulServicename() {
        return hsmModuleServicename == null ? NONE : hsmModuleServicename.toString();
    }

    public final void setHSMModulServicename(String name) throws MalformedObjectNameException {
        this.hsmModuleServicename = NONE.equals(name) ? null : ObjectName.getInstance(name);
    }
    
    @Override
        protected void startService() throws Exception {
        super.startService();
        jmsDelegate.startListening(moveFilesOfStudyQueueName, this , 1);
    }

    @Override
        protected void stopService() throws Exception {
        jmsDelegate.stopListening(moveFilesOfStudyQueueName);
        super.stopService();
    }
    protected void schedule(DeleteStudyOrder order, long scheduledTime) throws Exception {
        if (srcFsGroup == null || destFsGroup == null) {
            String msg = "FileMove service is disabled! Set SourceFileSystemGroupID and DestinationFileSystemGroupID to enable it.";
            log.info(msg);
            throw new RuntimeException(msg);
        } else if ("ERROR".equals(getUsableDiskSpaceStringOnDest())) {
            String msg = "UsableDiskSpaceStringOnDest reports an error! Scheduling Move Order cancelled! Please check DestinationFileSystemGroupID configuration!";
            log.error(msg);
            throw new RuntimeException(msg);
        } else {
            if (log.isInfoEnabled()) {
                String scheduledTimeStr = scheduledTime > 0
                        ? new Date(scheduledTime).toString()
                        : "now";
                log.info("Scheduling job [" + order + "] at "
                        + scheduledTimeStr + ". Retry times: "
                        + order.getFailureCount());
            }
            jmsDelegate.queue(moveFilesOfStudyQueueName, order, Message.DEFAULT_PRIORITY, scheduledTime);
        }
    }
    
    public int scheduleStudiesForMove() throws Exception {
        return this.scheduleStudiesForDeletion();
    }

    public long scheduleStudyForMove(String suid) throws Exception {
        return scheduleStudyForDeletion(suid);
    }

    @Override
    protected String getFileSystemGroupIDForDeleter() {
        return srcFsGroup;
    }
    @Override
    protected void scheduleDeleteOrder(DeleteStudyOrder order) throws Exception {
        schedule(order, 0L);
    }
    
    public String showMoveCriteria() {
        return this.showDeleterCriteria();
    }

    public void onMessage(Message message) {
        ObjectMessage om = (ObjectMessage) message;
        try {
            DeleteStudyOrder order = (DeleteStudyOrder) om.getObject();
            log.info("Start processing " + order);
            try {
                process(order);
                log.info("Finished processing " + order);
            } catch (Exception e) {
                order.setThrowable(e);
                final int failureCount = order.getFailureCount() + 1;
                order.setFailureCount(failureCount);
                final long delay = retryIntervalls.getIntervall(failureCount);
                if (delay == -1L) {
                    log.error("Give up to process " + order, e);
                    jmsDelegate.fail(moveFilesOfStudyQueueName, order);
                } else {
                    log.warn("Failed to process " + order + ". Scheduling retry.", e);
                    jmsDelegate.queue(moveFilesOfStudyQueueName, order, 0, System.currentTimeMillis() + delay);
                }
            }
        } catch (JMSException e) {
            log.error("jms error during processing message: " + message, e);
        } catch (Throwable e) {
            log.error("unexpected error during processing message: " + message, e);
        }
    }


    private void process(DeleteStudyOrder order) throws Exception {
        try {
            if (destFsGroup == null)
                throw new RuntimeException("No destination file system configured!");
            FileSystemMgt2 mgt = fileSystemMgt();
            FileSystemDTO fsDTO = getDestinationFilesystem(mgt);
            if (fsDTO == null) {
                throw new RuntimeException("No destination file system (with free disk space) available!");
            }
            long availOnDest = getUsableDiskSpaceOnDest();
            log.debug("Available disk space on destination FS:"+availOnDest);
            FileDTO[][] files = null;
            if (availOnDest > 0) {
                files = mgt.getFilesOfStudy(order);
                for (int i = 0 ; i < files.length && availOnDest > 0; i++) {
                    for (int j = 0 ; j < files[i].length && availOnDest > 0; j++) {
                        availOnDest -= files[i][j].getFileSize();
                    }
                }
            }
            log.debug("Expected available disk space on destination FS after move:"+availOnDest);
            if (availOnDest < 0) {
                log.error("Not enough space left on destination filesystem to move study ("+order.getStudyIUID()+")!");
                throw new RuntimeException("Not enough space left on destination filesystem!");
            }
            File[] srcFiles = null;
            log.info("Move "+ files.length +" files of study "+order.getStudyIUID()+" to Filesystem:"+fsDTO);
            if (files.length > 0) {
                String destPath = fsDTO.getDirectoryPath();
                if (destPath.startsWith("tar:")) {
                    for (int i=0 ; i < files.length ; i++) {
                        srcFiles = copyTar(files[i], destPath, fsDTO.getPk());
                    }
                    List<FileDTO> failed =  mgt.moveFiles(order, files, destFileStatus, keepSrcFiles, false);
                } else {
                    srcFiles = copyFiles(files, destPath, fsDTO.getPk());
                    List<FileDTO> failed = mgt.moveFiles(order, files, destFileStatus, keepSrcFiles, keepMovedFilesOnError);
                    log.info("moveFiles done! failed:"+failed);
                    if (failed != null) {
                        if (keepSrcFiles) {
                            FileDTO dto;
                            for (int i = 0 ; i < failed.size() ; i++) {
                                dto = failed.get(i);
                                deleteFile(FileUtils.toFile(dto.getDirectoryPath() + 
                                            '/' + dto.getFilePath()) );
                            }
                            
                        } else {
                            int k = 0, s = 0;
                            FileDTO failedDto = failed.get(k);
                            for (int i = 0 ; i < files.length ; i++) {
                                for (int j = 0 ; j < files[i].length ; j++) {
                                    if (failedDto != null && files[i][j].getPk() == failedDto.getPk()) {
                                        deleteFile(FileUtils.toFile(failedDto.getDirectoryPath() + 
                                                '/' + failedDto.getFilePath()) );
                                        failedDto = ++k < failed.size() ? failed.get(k) : null;
                                    } else {
                                        deleteFile(srcFiles[s]);
                                    }
                                    s++;
                                }
                            }
                        }
                        throw (Exception)order.getThrowable();
                    }
                }
                if (!keepSrcFiles) {
                    for (int i = 0 ; i < srcFiles.length ; i++) {
                        deleteFile(srcFiles[i]);
                    }
                    try {
                        mgt.removeStudyOnFSRecord(order);
                    } catch (Exception x) {
                        log.warn("Remove StudyOnFS record failed for "+order, x);
                    }
                }
            }
        } catch (ConcurrentStudyStorageException x) {
            log.info(x.getMessage());
        }
        
    }
    
    private FileSystemDTO getDestinationFilesystem(FileSystemMgt2 mgt) throws Exception {
        if (destFsGroup == null) 
            return null;
        int pos = destFsGroup.indexOf('@');
        if (pos == -1)
            return fsmgt.selectStorageFileSystem(destFsGroup);
        return mgt.getFileSystemOfGroup(destFsGroup.substring(pos+1), destFsGroup.substring(0, pos));
    }

    private void deleteFile(File f) {
        log.info("M-DELETE file:" + f);
        if (!f.delete())
            log.error("Failed to delete file:"+f);
    }
    
    private File[] copyFiles(FileDTO[][] files, String dirPath, long destFsPk) throws Exception {
        byte[] buffer = new byte[bufferSize];
        Exception ex = null;
        MessageDigest digest = null;
        List<File> srcFiles = new ArrayList<File>();
        if (verifyCopy)
            digest = MessageDigest.getInstance("MD5");
        FileDTO dtoSrc;
        File file;
        for (int i = 0 ; i < files.length ; i++) {
            for (int j = 0 ; j < files[i].length ; j++) {
                dtoSrc = files[i][j];
                file = FileUtils.toFile(dtoSrc.getDirectoryPath() + '/' + dtoSrc.getFilePath());
                srcFiles.add(file);
                File dst = FileUtils.toFile(dirPath + '/' + dtoSrc.getFilePath());
                try {
                    if (dst.getParentFile().mkdirs())
                        log.info("M-WRITE dir:"+dst.getParent());
                    log.info("M-WRITE file:" + dst);
                    FileUtils.copyFile(file, dst);
                    byte[] md5sum0 = dtoSrc.getFileMd5();
                    if (md5sum0 != null && digest != null) {
                        byte[] md5sum = MD5Utils.md5sum(dst, digest, buffer);
                        if (!Arrays.equals(md5sum0, md5sum)) {
                            String prompt = "md5 sum of copy " + dst
                                    + " differs from md5 sum in DB for file " + file;
                            log.warn(prompt);
                            throw new IOException(prompt);
                        }
                    }
                    dtoSrc.setFileSystemPk(destFsPk);
                    dtoSrc.setDirectoryPath(dirPath);
                } catch (Exception e) {
                    deleteFile(dst);
                    ex = e;
                }
            }
        }
        if (ex != null) {
            throw ex;
        }
        return (File[]) srcFiles.toArray(new File[srcFiles.size()]);
    }

    private File[] copyTar(FileDTO[] files, String destPath, long destFsPk) throws Exception {
        String tarPath = mkTarPath(files[0].getFilePath());
        String[] tarEntryNames = new String[files.length];
        File[] srcFiles;
        for (int i = 0; i < tarEntryNames.length; i++) {
            tarEntryNames[i] = mkTarEntryName(files[i]);
        }
        if (hsmModuleServicename == null) {
            File tarFile = FileUtils.toFile(destPath.substring(4), tarPath);
            srcFiles = mkTar(files, tarFile, tarEntryNames);
        } else {
            File tarFile = prepareHSMFile(destPath, tarPath);
            try {
                srcFiles = mkTar(files, tarFile, tarEntryNames);
                tarPath = storeHSMFile(tarFile, destPath, tarPath);
            } catch (Exception x) {
                log.error("Make Tar file failed!",x);
                deleteFile(tarFile);
                failedHSMFile(tarFile,destPath, tarPath);
                throw x;
            }
        }
        for (int i = 0; i < tarEntryNames.length; i++) {
            String fileId = tarPath + '!' + tarEntryNames[i];
            files[i].setFileSystemPk(destFsPk);
            files[i].setDirectoryPath(destPath);
            files[i].setFilePath(fileId);
        }
        return srcFiles;
    }
    
    private File[] mkTar(FileDTO[] dto, File tarFile,
            String[] tarEntryNames) throws Exception {
        File[] srcFiles = new File[dto.length];
        try {
            if (tarFile.getParentFile().mkdirs()) {
                log.info("M-WRITE " + tarFile.getParent());
            }    
            log.info("M-WRITE " + tarFile);
            TarOutputStream tar = new TarOutputStream(
                    new FileOutputStream(tarFile));
            try {
                writeMD5SUM(tar, dto, tarEntryNames);
                for (int i = 0; i < tarEntryNames.length; i++) {
                    srcFiles[i] = writeFile(tar, dto[i], tarEntryNames[i]);
                }
            } finally {
                tar.close();
            }
            if (verifyCopy) {
                VerifyTar.verify(tarFile, new byte[bufferSize]);
            }
        } catch (Exception e) {
            deleteFile(tarFile);
            throw e;
        }
        return srcFiles;
    }
    
    private void writeMD5SUM(TarOutputStream tar, FileDTO[] dto,
            String[] tarEntryNames)
            throws IOException {
        byte[] md5sum = new byte[dto.length * MD5SUM_ENTRY_LEN];
        final TarEntry tarEntry = new TarEntry("MD5SUM");
        tarEntry.setSize(md5sum.length);
        tar.putNextEntry(tarEntry);
        int i = 0;
        for (int j = 0; j < tarEntryNames.length; j++) {
            MD5Utils.toHexChars(dto[j].getFileMd5(), md5sum, i);
            md5sum[i+32] = ' ';
            md5sum[i+33] = ' ';
            System.arraycopy(
                    tarEntryNames[j].getBytes("US-ASCII"), 0, 
                    md5sum, i+34, 17);
            md5sum[i+51] = '\n';
            i += MD5SUM_ENTRY_LEN;
        }
        tar.write(md5sum);
        tar.closeEntry();
    }

    private File writeFile(TarOutputStream tar, FileDTO dto,
            String tarEntryName) 
    throws IOException, FileNotFoundException {
        File file = FileUtils.toFile(dto.getDirectoryPath(), dto.getFilePath());
        TarEntry entry = new TarEntry(tarEntryName);
        entry.setSize(dto.getFileSize());
        tar.putNextEntry(entry);
        FileInputStream fis = new FileInputStream(file);
        try {
            tar.copyEntryContents(fis);
        } finally {
            fis.close();
        }
        tar.closeEntry();
        return file;
    }

    private File prepareHSMFile(String fsID, String filePath) 
            throws InstanceNotFoundException, MBeanException, ReflectionException {
        return (File) server.invoke(hsmModuleServicename, "prepareHSMFile", new Object[]{fsID, filePath}, 
            new String[]{String.class.getName(),String.class.getName()});
    }

    private String storeHSMFile(File file, String fsID, String filePath) 
            throws InstanceNotFoundException, MBeanException, ReflectionException {
        return (String) server.invoke(hsmModuleServicename, "storeHSMFile", 
            new Object[]{file, fsID, filePath}, 
            new String[]{File.class.getName(),String.class.getName(),String.class.getName()});
    }

    private void failedHSMFile(File file, String fsID, String filePath) 
            throws InstanceNotFoundException, MBeanException, ReflectionException {
        server.invoke(hsmModuleServicename, "failedHSMFile", 
            new Object[]{file, fsID, filePath}, 
            new String[]{File.class.getName(),String.class.getName(),String.class.getName()});
    }
    
    private String mkTarPath(String filePath) {
        StringBuffer sb = new StringBuffer(filePath);
        sb.setLength(filePath.lastIndexOf('/'));
        sb.append('-').append(System.currentTimeMillis()%3600000).append(".tar");
        return sb.toString();
    }
    
    private String mkTarEntryName(FileDTO dto) {
        StringBuilder sb = new StringBuilder(17);
        String fileId = dto.getFilePath();
        int pos = fileId.lastIndexOf('/');
        int pos1 = fileId.lastIndexOf('/', pos-1);
        sb.append(fileId.substring(pos1+1, pos));
        sb.append('/');
        sb.append(FileUtils.toHex((int)(dto.getPk())));
        return sb.toString();
    }

}
