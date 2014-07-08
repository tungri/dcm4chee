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
 * Portions created by the Initial Developer are Copyright (C) 2003-2005
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

package org.dcm4chex.archive.mbean;

import static java.lang.String.format;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import javax.ejb.FinderException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.dcm4che.dict.UIDs;
import org.dcm4cheri.util.StringUtils;
import org.dcm4chex.archive.codec.CodecCmd;
import org.dcm4chex.archive.codec.CompressCmd;
import org.dcm4chex.archive.codec.CompressionFailedException;
import org.dcm4chex.archive.codec.DecompressCmd;
import org.dcm4chex.archive.common.FileStatus;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.ejb.interfaces.FileDTO;
import org.dcm4chex.archive.ejb.interfaces.FileSystemDTO;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2Home;
import org.dcm4chex.archive.ejb.jdbc.ClaimCompressingFileCmd;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.dcm4chex.archive.util.FileUtils;
import org.jboss.system.ServiceMBeanSupport;

/**
 * @author gunter.zeilinger@tiani.com
 * @version $Revision: 17811 $ $Date: 2013-05-28 12:02:29 +0000 (Tue, 28 May 2013) $
 * @since 12.09.2004
 * 
 */
public class CompressionService extends ServiceMBeanSupport {

    private static final String _DCM = ".dcm";
    private static final String _COMPRESSED = ".comp";
    private static final String _DECOMPRESSED = ".decomp";

    private final SchedulerDelegate scheduler = new SchedulerDelegate(this);

    // ScheduledExecutorService is used to schedule tasks that run independently on each node.
    private final ScheduledExecutorService scheduledExecutorService = newSingleThreadScheduledExecutor(); 

    private long taskInterval;

    private int disabledStartHour;

    private int disabledEndHour;

    private String[] fileSystemGroupIDs;

    private int limitNumberOfFilesPerTask;

    private boolean verifyCompression;

    private Integer listenerID;

    private File tmpDir = new File("tmp");

    private long keepTempFileIfVerificationFails = 0L;

    private List compressionRuleList = new ArrayList();

    private int bufferSize = 8192;

    private String timerIDCheckFilesToCompress;
    private boolean isRunning;            

    private static final String[] CODEC_NAMES = new String[] { "JPLL", "JLSL",
            "J2KR" };

    private static final String[] COMPRESS_TRANSFER_SYNTAX = new String[] {
            UIDs.JPEGLossless, UIDs.JPEGLSLossless, UIDs.JPEG2000Lossless };

    private final NotificationListener delayedCompressionListener = new NotificationListener() {
        public void handleNotification(Notification notif, Object handback) {
            checkForTempFilesToDelete();
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if (isDisabled(hour)) {
                if (log.isDebugEnabled())
                    log.debug("trigger ignored in time between "
                            + disabledStartHour + " and " + disabledEndHour
                            + " !");
            } else {
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            checkForFilesToCompress();
                        } catch (Exception e) {
                            log.error("Delayed compression failed!", e);
                        }
                    }
                }).start();
            }
        }
    };

    private ScheduledFuture<?> cleanupTempFilesScheduledFuture;
    private long cleanupTempFilesInterval;
    private final Runnable cleanupTempFilesRunnable = new Runnable() {
		public void run() {
			try {
				checkForTempFilesToDelete();	
			} catch (Exception e) {
            	getLog().warn("Error encountered while cleaning up temporary files.", e);
            }
		}
	};

    public String getFileSystemGroupIDs() {
        return StringUtils.toString(fileSystemGroupIDs, ',');
    }

    public void setFileSystemGroupIDs(String fileSystemGroupIDs) {
        StringTokenizer st = new StringTokenizer(fileSystemGroupIDs,
                ",;\n\r\t ");
        this.fileSystemGroupIDs = new String[st.countTokens()];
        for (int i = 0; i < this.fileSystemGroupIDs.length; i++) {
            this.fileSystemGroupIDs[i] = st.nextToken();
        }
    }

    public final int getBufferSize() {
        return bufferSize;
    }

    public final void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public ObjectName getSchedulerServiceName() {
        return scheduler.getSchedulerServiceName();
    }

    public void setSchedulerServiceName(ObjectName schedulerServiceName) {
        scheduler.setSchedulerServiceName(schedulerServiceName);
    }

    public final String getKeepTempFileIfVerificationFails() {
        return RetryIntervalls.formatInterval(keepTempFileIfVerificationFails);
    }

    public final void setKeepTempFileIfVerificationFails(String interval) {
        this.keepTempFileIfVerificationFails = RetryIntervalls
                .parseInterval(interval);
    }

    public final String getTaskInterval() {
        String s = RetryIntervalls.formatIntervalZeroAsNever(taskInterval);
        return (disabledEndHour == -1) ? s : s + "!" + disabledStartHour + "-"
                + disabledEndHour;
    }

    public void setTaskInterval(String interval) throws Exception {
        long oldInterval = taskInterval;
        int pos = interval.indexOf('!');
        if (pos == -1) {
            taskInterval = RetryIntervalls.parseIntervalOrNever(interval);
            disabledEndHour = -1;
        } else {
            taskInterval = RetryIntervalls.parseIntervalOrNever(interval
                    .substring(0, pos));
            int pos1 = interval.indexOf('-', pos);
            disabledStartHour = Integer.parseInt(interval.substring(pos + 1,
                    pos1));
            disabledEndHour = Integer.parseInt(interval.substring(pos1 + 1));
        }
        if (getState() == STARTED && oldInterval != taskInterval) {
            scheduler.stopScheduler(timerIDCheckFilesToCompress, listenerID,
                    delayedCompressionListener);
            listenerID = scheduler.startScheduler(timerIDCheckFilesToCompress,
                    taskInterval, delayedCompressionListener);
        }
    }
    
    public boolean isRunning() {
        return isRunning;
    }            

    public final void setCompressionRules(String rules) {
        this.compressionRuleList.clear();
        if (rules == null || rules.trim().length() < 1)
            return;
        StringTokenizer st = new StringTokenizer(rules, ",;\n\r\t ");
        while (st.hasMoreTokens()) {
            compressionRuleList.add(new CompressionRule(st.nextToken()));
        }
    }

    public final String getCompressionRules() {
        StringBuffer sb = new StringBuffer();
        Iterator iter = this.compressionRuleList.iterator();
        while (iter.hasNext()) {
            sb.append(((CompressionRule) iter.next())).append("\r\n");
        }
        return sb.toString();
    }

    public int getLimitNumberOfFilesPerTask() {
        return limitNumberOfFilesPerTask;
    }

    public void setLimitNumberOfFilesPerTask(int limit) {
        this.limitNumberOfFilesPerTask = limit;
    }

    public boolean isVerifyCompression() {
        return verifyCompression;
    }

    public void setVerifyCompression(boolean checkCompression) {
        this.verifyCompression = checkCompression;
    }

    public final int getMaxConcurrentCodec() {
        return CodecCmd.getMaxConcurrentCodec();
    }

    public final void setMaxConcurrentCodec(int maxConcurrentCodec) {
        CodecCmd.setMaxConcurrentCodec(maxConcurrentCodec);
    }

    public final int getMaxConcurrentCompression() {
        return CompressCmd.getMaxConcurrentCompression();
    }

    public final void setMaxConcurrentCompression(int maxConcurrent) {
        CompressCmd.setMaxConcurrentCompression(maxConcurrent);
    }

    public final int getMaxConcurrentDecompression() {
        return DecompressCmd.getMaxConcurrentDecompression();
    }

    public final void setMaxConcurrentDecompression(int maxConcurrent) {
        DecompressCmd.setMaxConcurrentDecompression(maxConcurrent);
    }

    public final int getMaxBufferedImagePoolSize() {
        return CodecCmd.getMaxBufferedImagePoolSize();
    }

    public final void setMaxBufferedImagePoolSize(int maxSize) {
        CodecCmd.setMaxBufferedImagePoolSize(maxSize);
    }

    public final int getCurrentBufferedImagePoolSize() {
        return CodecCmd.getCurrentBufferedImagePoolSize();
    }

    public final String getMaxBufferedImagePoolMemory() {
        return FileUtils.formatSize(CodecCmd.getMaxBufferedImagePoolMemory());
    }

    public final void setMaxBufferedImagePoolMemory(String maxMemory) {
        CodecCmd.setMaxBufferedImagePoolMemory(FileUtils.parseSize(maxMemory,
                0L));
    }

    public final String getCurrentBufferedImagePoolMemory() {
        return FileUtils.formatSize(CodecCmd
                .getCurrentBufferedImagePoolMemory());
    }

    public final float getBufferedImagePoolHitRate() {
        return CodecCmd.getBufferedImagePoolHitRate();
    }

    public final void resetBufferedImagePoolHitRate() {
        CodecCmd.resetBufferedImagePoolHitRate();
    }

    public final void setTempDir(String dirPath) {
        tmpDir = new File(dirPath);
    }

    public final String getTempDir() {
        return tmpDir.toString();
    }

    public void checkForTempFilesToDelete() {
        if (keepTempFileIfVerificationFails <= 0)
            return;
        File absTmpDir = FileUtils.resolve(tmpDir);
        if (!absTmpDir.isDirectory())
            return;
        final long before = System.currentTimeMillis()
                - keepTempFileIfVerificationFails;
        File[] files = absTmpDir.listFiles(new FileFilter() {
            public boolean accept(File f) {
                return f.getName().endsWith(_DCM) && f.lastModified() < before;
            }
        });
        for (int i = 0; i < files.length; i++) {
            FileUtils.delete(files[i], false);
        }
    }

    public void checkForFilesToCompress() throws FinderException, IOException {
        synchronized(this) {
            if (isRunning) {
                log.info("checkForFilesToCompress is already running!");
                return;
            }
            isRunning = true;
        }
        try {
            log.info("Check For Files To Compress on attached filesystems!");
            String cuid;
            Timestamp before;
            CompressionRule info;
            FileDTO[] files;
            int limit = limitNumberOfFilesPerTask;
            byte[] buffer = null;
            FileSystemMgt2 fsMgt = newFileSystemMgt();
            for (int g = 0; g < fileSystemGroupIDs.length; g++) {
                FileSystemDTO[] fsDTOs = fsMgt.getRWFileSystemsOfGroup(fileSystemGroupIDs[g]);
                for (int i = 0, len = compressionRuleList.size(); i < len && limit > 0; i++) {
                    info = (CompressionRule) compressionRuleList.get(i);
                    cuid = info.getCUID();
                    before = new Timestamp(System.currentTimeMillis() - info.getDelay());
                    for (int j = 0; j < fsDTOs.length; j++) {
                        files = fsMgt.findFilesToCompress(fsDTOs[j], cuid,
                                before, limit);
                        if (files.length > 0) {
                            if (buffer == null)
                                buffer = new byte[bufferSize];
                            log.debug("Compress " + files.length
                                    + " files on filesystem " + fsDTOs[j]
                                    + " triggered by " + info);
                            for (int k = 0; k < files.length; k++) {
                                try {
                                    doCompress(fsMgt, files[k], info, buffer);
                                } catch (CompressionFailedException e) {
								    // exception should be already logged appropriately.
                                }
                            }
                            limit -= files.length;
                            if (limit <= 0) {
                            	break;
                            }
                        }
                    }
                }
            }
        } catch (ExceptionInInitializerError e) {
          log.error("Error initializing compression libraries!", e); 
          throw e;
        } finally {
            isRunning = false;
        }
    }

    public boolean compress(FileDTO fileDTO)
            throws IOException, CompressionFailedException {
        if (!isUncompressed(fileDTO.getFileTsuid())) {
            log.debug("The image is already compressed, file = "
                    + fileDTO.getFilePath());
            return false;
        }
        CompressionRule rule = getCompressionRule(fileDTO.getSopClassUID());
        if (rule == null) {
            log.debug("There is no compression rule for this image SOP Class: "
                    + fileDTO.getSopClassUID());
            return false;
        }
        return doCompress(newFileSystemMgt(), fileDTO, rule, new byte[bufferSize]);
    }

    private CompressionRule getCompressionRule(String cuid) {
        for (Iterator iter = compressionRuleList.iterator(); iter.hasNext();) {
            CompressionRule rule = (CompressionRule) iter.next();
            if (cuid.equals(rule.getCUID())) {
                return rule;
            }
        }
        return null;
    }

    private boolean isUncompressed(String tsuid) {
        return tsuid.equals(UIDs.ExplicitVRLittleEndian)
                || tsuid.equals(UIDs.ExplicitVRBigEndian)
                || tsuid.equals(UIDs.ImplicitVRLittleEndian);
    }

    private boolean doCompress(FileSystemMgt2 fsMgt, FileDTO fileDTO,
            CompressionRule info, byte[] buffer)
            throws CompressionFailedException {
        File baseDir = FileUtils.toFile(fileDTO.getDirectoryPath());
        File srcFile = FileUtils.toFile(fileDTO.getDirectoryPath(), fileDTO
                .getFilePath());
        File destFile = null;
        try {
            FileUtils.verifyMD5(srcFile, fileDTO.getMd5String());
            if (!ClaimCompressingFileCmd.claim(fileDTO.getPk())) {
                log.info("File " + srcFile
                        + " already compressed by concurrent thread!");
                return false;
            }
            destFile = FileUtils.createNewFile(srcFile.getParentFile(),
                    (int) Long.parseLong(srcFile.getName(), 16) + 1);
            if (log.isDebugEnabled())
                log.debug("Compress file " + srcFile + " to " + destFile
                        + " with CODEC:" + info.getCodec() + "("
                        + info.getTransferSyntax() + ")");
            int[] planarConfiguration = new int[1];
            int[] pxvalVR = new int[1];
            byte[] md5 = CompressCmd.compressFile(srcFile, destFile, info
                    .getTransferSyntax(), planarConfiguration, pxvalVR, buffer);
            if (verifyCompression && fileDTO.getFileMd5() != null) {
                File decFile = getTempDecompressedFile(fileDTO);
                byte[] dec_md5 = DecompressCmd.decompressFile(destFile,
                        decFile, fileDTO.getFileTsuid(), planarConfiguration[0],
                        pxvalVR[0], buffer);
                if (!Arrays.equals(dec_md5, fileDTO.getFileMd5())) {
                    log.info("MD5 sum after compression+decompression of "
                            + srcFile + " differs - compare pixel matrix");
                    if (!FileUtils.equalsPixelData(srcFile, decFile)) {
                        String errmsg = "Pixel matrix after decompression differs from original file "
                                + srcFile + "! Keep original uncompressed file.";
                        log.warn(errmsg);
                        fsMgt.setFileStatus(fileDTO.getPk(),
                                FileStatus.VERIFY_COMPRESS_FAILED);
                        
                        if (keepTempFileIfVerificationFails <= 0L) {
                            FileUtils.delete(destFile, false);
                            FileUtils.delete(decFile, false);
                        } else {
                            saveCompressedFile(destFile, getTempCompressedFile(fileDTO));
                        }
                        
                        throw new CompressionFailedException(errmsg);
                    }
                }
                FileUtils.delete(decFile, false);
            }
            final int baseDirPathLength = baseDir.getPath().length();
            final String destFilePath = destFile.getPath().substring(
                    baseDirPathLength + 1).replace(File.separatorChar, '/');
            if (log.isDebugEnabled())
                log.debug("replace File " + srcFile + " with " + destFile);
            fsMgt.replaceFile(fileDTO.getPk(), destFilePath,
                    info.getTransferSyntax(), destFile.length(), md5,
                    FileStatus.DEFAULT);
            fileDTO.setPk(0);
            fileDTO.setFilePath(destFilePath);
            fileDTO.setFileSize((int) destFile.length());
            fileDTO.setFileMd5(md5);
            fileDTO.setFileTsuid(info.getTransferSyntax());
            FileUtils.delete(srcFile, false);
            return true;
        } catch (CompressionFailedException e) {
            throw e;
        } catch (Exception e) {
            String errmsg = "Can't compress file:" + srcFile;
            log.error(errmsg, e);
            if (destFile != null && destFile.exists()) {
                if (keepTempFileIfVerificationFails <= 0L) {
                    FileUtils.delete(destFile, false);
                } else {
                    saveCompressedFile(destFile, getTempCompressedFile(fileDTO));
                }
            }
            try {
                fsMgt.setFileStatus(fileDTO.getPk(),
                                FileStatus.COMPRESS_FAILED);
            } catch (Exception x1) {
                log.error("Failed to set FAILED_TO_COMPRESS for file "
                        + srcFile);
            }
            throw new CompressionFailedException(errmsg, e);
        }
    }

    private void saveCompressedFile(File srcFile, File dstFile) {
        try {
            FileUtils.moveFile(srcFile, dstFile);
        } catch (IOException e) {
            log.warn(format("Error encountered while moving %s to %s.", srcFile, dstFile), e);
            srcFile.delete();
        }
    }

    private File getTempCompressedFile(FileDTO fileDTO) {
        return getTempFile(fileDTO, _COMPRESSED + _DCM);
    }

    private File getTempDecompressedFile(FileDTO fileDTO) {
        return getTempFile(fileDTO, _DECOMPRESSED + _DCM);
    }

    private File getTempFile(FileDTO fileDTO, String extension) {
        File absTmpDir = FileUtils.resolve(tmpDir);
        if (absTmpDir.mkdirs())
            log.info("M-WRITE: Create temp directory for compressed/decompressed files:"+absTmpDir);
        return new File(absTmpDir, fileDTO.getFilePath().replace('/', '-') + extension);
    }

    private boolean isDisabled(int hour) {
        if (disabledEndHour == -1)
            return false;
        boolean sameday = disabledStartHour <= disabledEndHour;
        boolean inside = hour >= disabledStartHour && hour < disabledEndHour;
        return sameday ? inside : !inside;
    }

    protected void startService() throws Exception {
        listenerID = scheduler.startScheduler(timerIDCheckFilesToCompress,
                taskInterval, delayedCompressionListener);
        
		scheduleCleanupTempFiles(cleanupTempFilesInterval);
		
		super.startService();
    }

	private void scheduleCleanupTempFiles(long cleanupTempFilesInterval) {
		cleanupTempFilesScheduledFuture = scheduledExecutorService
				.scheduleAtFixedRate(cleanupTempFilesRunnable, 0,
						cleanupTempFilesInterval, MILLISECONDS);
    }

    protected void stopService() throws Exception {
    	scheduledExecutorService.shutdownNow();
    	
        scheduler.stopScheduler(timerIDCheckFilesToCompress, listenerID,
                delayedCompressionListener);
        
        super.stopService();
    }

    private FileSystemMgt2 newFileSystemMgt() {
        try {
            FileSystemMgt2Home home = (FileSystemMgt2Home) EJBHomeFactory
                    .getFactory().lookup(FileSystemMgt2Home.class,
                            FileSystemMgt2Home.JNDI_NAME);
            return home.create();
        } catch (Exception e) {
            throw new RuntimeException("Failed to access File System Mgt EJB:",
                    e);
        }
    }

    public class CompressionRule {
        String cuid;
        int codec;
        long delay;

        public CompressionRule(String s) {
            String[] a = StringUtils.split(s, ':');
            if (a.length != 3)
                throw new IllegalArgumentException("Wrong format - " + s);
            cuid = a[0];
            getCUID(); // check cuid
            codec = Arrays.asList(CODEC_NAMES).indexOf(a[1]);
            if (codec == -1)
                throw new IllegalArgumentException("Unrecognized codec - "
                        + a[1]);
            delay = RetryIntervalls.parseInterval(a[2]);
        }

        /**
         * @return
         */
        public String getCodec() {
            return CODEC_NAMES[codec];
        }

        /**
         * @return
         */
        public String getCUID() {
            return Character.isDigit(cuid.charAt(0)) ? cuid : UIDs
                    .forName(cuid);
        }

        /**
         * @return Returns the before.
         */
        public long getDelay() {
            return delay;
        }

        /**
         * @return Returns the codec.
         */
        public String getTransferSyntax() {
            return COMPRESS_TRANSFER_SYNTAX[codec];
        }

        public String toString() {
            return cuid + ":" + getCodec() + ':'
                    + RetryIntervalls.formatInterval(delay);
        }
    }

    public String getTimerIDCheckFilesToCompress() {
        return timerIDCheckFilesToCompress;
    }

    public void setTimerIDCheckFilesToCompress(
            String timerIDCheckFilesToCompress) {
        this.timerIDCheckFilesToCompress = timerIDCheckFilesToCompress;
    }

	public String getCleanupTempFilesInterval() {
		return RetryIntervalls.formatInterval(this.cleanupTempFilesInterval);
	}

	public void setCleanupTempFilesInterval(String cleanupTempFilesInterval) {
		long ms = RetryIntervalls.parseInterval(cleanupTempFilesInterval);

		if (ms <= 0) {
			throw new IllegalArgumentException(
					format("Cannot parse interval [%s], cleanup interval will remain as %d.",
							cleanupTempFilesInterval,
							this.cleanupTempFilesInterval));
		}
		
		if (getState() == STARTED) {
			if (!isCleanupTempFilesScheduledFutureCancelled()) {
				throw new IllegalStateException(
						format("Cannot reschedule cleanup task [%s], cleanup interval will remain as %d.",
								cleanupTempFilesScheduledFuture.toString(),
								this.cleanupTempFilesInterval));
			}

			scheduleCleanupTempFiles(ms);
		}

		this.cleanupTempFilesInterval = ms;
	}

	private boolean isCleanupTempFilesScheduledFutureCancelled() {
		return cleanupTempFilesScheduledFuture.isCancelled()
				|| cleanupTempFilesScheduledFuture.isDone()
				|| cleanupTempFilesScheduledFuture.cancel(false);
	}
}
