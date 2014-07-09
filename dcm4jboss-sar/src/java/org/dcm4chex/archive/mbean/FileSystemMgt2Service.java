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
package org.dcm4chex.archive.mbean;

import static java.util.Arrays.asList;

import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Calendar;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.ejb.FinderException;
import javax.ejb.ObjectNotFoundException;
import javax.management.Attribute;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.dcm4chex.archive.common.Availability;
import org.dcm4chex.archive.common.DeleteStudyOrder;
import org.dcm4chex.archive.common.FileSystemStatus;
import org.dcm4chex.archive.common.SeriesStored;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.ejb.interfaces.FileSystemDTO;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2Local;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2LocalHome;
import org.dcm4chex.archive.notif.StorageFileSystemSwitched;
import org.dcm4chex.archive.util.FileDeleter;
import org.dcm4chex.archive.util.FileSystemUtils;
import org.dcm4chex.archive.util.FileUtils;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date$
 * @since Aug 8, 2008
 */
public class FileSystemMgt2Service extends AbstractDeleterService {

    protected static final String GROUP = "group";

    private long minFreeDiskSpace = MIN_FREE_DISK_SPACE;
    private float minFreeDiskSpaceRatio = -1f;
        
    private final DeleteStudyDelegate deleteStudy =
            new DeleteStudyDelegate(this);

    private Lock deleteOrphanedPrivateFilesLock;
    private String timerIDDeleteOrphanedPrivateFiles;


    private long deleteOrphanedPrivateFilesInterval;
    private volatile boolean isRunningDeleteOrphanedPrivateFiles;

    private String defRetrieveAET;

    private int defAvailability;

    private String defUserInfo;

    private String defStorageDir;

    private boolean checkStorageFileSystemStatus = true;

    private boolean makeStorageDirectory = true;

    private String mountFailedCheckFile = "NO_MOUNT";

    private long expectedDataVolumePerDay = 100000L;

    private long adjustExpectedDataVolumePerDay = 0;

    private long checkFreeDiskSpaceMinInterval;

    private long checkFreeDiskSpaceMaxInterval;

    private long checkFreeDiskSpaceRetryInterval;

    private long checkFreeDiskSpaceTime;

    private boolean scheduleStudiesForDeletionOnSeriesStored;

    private int deleteOrphanedPrivateFilesBatchSize;

    private int updateStudiesBatchSize;

    private FileSystemDTO storageFileSystem;

    private Integer deleteOrphanedPrivateFilesListenerID;

    private ObjectName storeScpServiceName;

    private final JndiHelper jndiHelper;
    private final FileDeleter fileDeleter;
    
    private byte[] switchFileSystemMonitor = new byte[0];
    
    public FileSystemMgt2Service() {
    	this.jndiHelper = new JndiHelper();
    	this.fileDeleter = new FileDeleter(log);
    	this.deleteOrphanedPrivateFilesLock = new ReentrantLock();
	}

	protected FileSystemMgt2Service(JndiHelper jndiHelper,
			FileDeleter fileDeleter, Lock schedulesStudiesForDeletionLock,
			Lock deleteOrphanedPrivateFilesLock) {
		super(schedulesStudiesForDeletionLock);
		this.jndiHelper = jndiHelper;
		this.fileDeleter = fileDeleter;
		this.deleteOrphanedPrivateFilesLock = deleteOrphanedPrivateFilesLock;
	}
    
    public String getMinFreeDiskSpace() {
        return minFreeDiskSpaceRatio > 0 ? minFreeDiskSpaceRatio+"%" :
                minFreeDiskSpace == 0 ? NONE
                : FileUtils.formatSize(minFreeDiskSpace);
    }

    public long getMinFreeDiskSpaceBytes() {
        return minFreeDiskSpace;
    }

    public void setMinFreeDiskSpace(String str) {
        if (str.endsWith("%")) {
            minFreeDiskSpaceRatio = Float.parseFloat(str.substring(0,str.length()-1));
            minFreeDiskSpace = calcFreeDiskSpace(this.storageFileSystem);
        } else {
            minFreeDiskSpaceRatio = -1;
            minFreeDiskSpace = str.equalsIgnoreCase(NONE) ? 0
                : FileUtils.parseSize(str, MIN_FREE_DISK_SPACE);
        }
    }
    
    private long calcFreeDiskSpace(FileSystemDTO fs) {
        if (fs==null) {
            return -1;
        }
        File dir = checkFS(fs, " - can not calculate minimum free disc space!");
        if (dir == null) {
            return -1;
        }
        long total = FileSystemUtils.totalSpace(dir.getAbsolutePath());
        log.info("Total space of "+fs.getDirectoryPath()+" :"+FileUtils.formatSize(total));
        return (long) (total * minFreeDiskSpaceRatio / 100);
    }

    public final String getExpectedDataVolumePerDay() {
        return FileUtils.formatSize(expectedDataVolumePerDay);
    }

    public long getExpectedDataVolumePerDayBytes() throws Exception {
        Calendar now = Calendar.getInstance();
        if (adjustExpectedDataVolumePerDay != 0
                && now.getTimeInMillis() > adjustExpectedDataVolumePerDay) {
           adjustExpectedDataVolumePerDay();
           adjustExpectedDataVolumePerDay = nextMidnight();
        }
        return expectedDataVolumePerDay;
    }

    public final void setExpectedDataVolumePerDay(String s) {
        this.expectedDataVolumePerDay = FileUtils.parseSize(s, FileUtils.MEGA);
    }

    public final boolean isAdjustExpectedDataVolumePerDay() {
        return adjustExpectedDataVolumePerDay != 0L;
    }

    public final void setAdjustExpectedDataVolumePerDay(boolean b) {
        this.adjustExpectedDataVolumePerDay = b ? nextMidnight() : 0L;
    }

    public String adjustExpectedDataVolumePerDay() throws Exception {
        FileSystemMgt2 fsMgt = fileSystemMgt();
        return adjustExpectedDataVolumePerDay(fsMgt,
                fsMgt.getFileSystemsOfGroup(getFileSystemGroupIDForDeleter()));
    }

    private String adjustExpectedDataVolumePerDay(FileSystemMgt2 fsMgt,
            FileSystemDTO[] fss) throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.roll(Calendar.DAY_OF_MONTH, false);
        long after = cal.getTimeInMillis();
        long sum = 0L;
        for (FileSystemDTO fs : fss) {
            sum += fsMgt.sizeOfFilesCreatedAfter(fs.getPk(), after);
        }
        String size = FileUtils.formatSize(sum);
        if (sum > expectedDataVolumePerDay) {
            server.setAttribute(super.serviceName, new Attribute(
                    "ExpectedDataVolumePerDay", size));
        }
        return size;
    }

    public ObjectName getStoreScpServiceName() {
        return storeScpServiceName;
    }

    public final void setStoreScpServiceName(ObjectName storeScpServiceName) {
        this.storeScpServiceName = storeScpServiceName;
    }

    public ObjectName getDeleteStudyServiceName() {
        return deleteStudy.getDeleteStudyServiceName();
    }

    public void setDeleteStudyServiceName(ObjectName deleteStudyServiceName) {
        deleteStudy.setDeleteStudyServiceName(deleteStudyServiceName);
    }


    public String getTimerIDDeleteOrphanedPrivateFiles() {
        return timerIDDeleteOrphanedPrivateFiles;
    }

    public void setTimerIDDeleteOrphanedPrivateFiles(
            String timerIDDeleteOrphanedPrivateFiles) {
        this.timerIDDeleteOrphanedPrivateFiles =
                timerIDDeleteOrphanedPrivateFiles;
    }

    public String getDeleteOrphanedPrivateFilesInterval() {
        return RetryIntervalls.formatIntervalZeroAsNever(
                deleteOrphanedPrivateFilesInterval);
    }

    public void setDeleteOrphanedPrivateFilesInterval(String interval)
            throws Exception {
        this.deleteOrphanedPrivateFilesInterval = RetryIntervalls
                .parseIntervalOrNever(interval);
        if (getState() == STARTED) {
            scheduler.stopScheduler(timerIDDeleteOrphanedPrivateFiles,
                    deleteOrphanedPrivateFilesListenerID,
                    deleteOrphanedPrivateFilesListener);
            deleteOrphanedPrivateFilesListenerID = scheduler.startScheduler(
                    timerIDDeleteOrphanedPrivateFiles,
                    deleteOrphanedPrivateFilesInterval,
                    deleteOrphanedPrivateFilesListener);
        }
    }

    public boolean isRunningDeleteOrphanedPrivateFiles() {
        return isRunningDeleteOrphanedPrivateFiles;
    }

    @Override
	protected void startService() throws Exception {
        super.startService();
        deleteOrphanedPrivateFilesListenerID = scheduler.startScheduler(
                timerIDDeleteOrphanedPrivateFiles,
                deleteOrphanedPrivateFilesInterval,
                deleteOrphanedPrivateFilesListener);
        server.addNotificationListener(storeScpServiceName,
                scheduleStudiesForDeletionOnSeriesStoredListener,
                SeriesStored.NOTIF_FILTER, null);
    }

    @Override
	protected void stopService() throws Exception {
        scheduler.stopScheduler(timerIDDeleteOrphanedPrivateFiles,
                deleteOrphanedPrivateFilesListenerID,
                deleteOrphanedPrivateFilesListener);
        server.removeNotificationListener(storeScpServiceName,
                scheduleStudiesForDeletionOnSeriesStoredListener,
                SeriesStored.NOTIF_FILTER, null);
    }

    private final NotificationListener scheduleStudiesForDeletionOnSeriesStoredListener =
            new NotificationListener() {
    public void handleNotification(Notification notif, Object handback) {
        if (scheduleStudiesForDeletionOnSeriesStored) {
            startScheduleStudiesForDeletion();
        }
    }
};

    private NotificationListener deleteOrphanedPrivateFilesListener =
            new NotificationListener() {
        private Thread thread;

        public void handleNotification(Notification notif, Object handback) {
            if (thread == null) {
                thread = new Thread(new Runnable(){
                    public void run() {
                        try {
                            deleteOrphanedPrivateFiles();
                        } catch (Exception e) {
                            log.error("deleteOrphanedPrivateFiles() failed:", e);
                        }
                        thread = null;
                    }});
                thread.start();
            } else {
                log.info("deleteOrphanedPrivateFiles() still in progress! " +
                        "Ignore this timer notification!");
            }
        }
    };

    public String getFileSystemGroupID() {
        return serviceName.getKeyProperty(GROUP);
    }

    public String getDefRetrieveAET() {
        return defRetrieveAET;
    }

    public void setDefRetrieveAET(String defRetrieveAET) {
        this.defRetrieveAET = defRetrieveAET;
    }

    public String getDefAvailability() {
        return Availability.toString(defAvailability);
    }

    public void setDefAvailability(String availability) {
        this.defAvailability = Availability.toInt(availability);
    }

    public String getDefUserInfo() {
        return defUserInfo;
    }

    public void setDefUserInfo(String defUserInfo) {
        this.defUserInfo = defUserInfo;
    }

    public String getDefStorageDir() {
        return defStorageDir != null ? defStorageDir : NONE;
    }

    public void setDefStorageDir(String defStorageDir) {
        String trimmed = defStorageDir.trim();
        this.defStorageDir = trimmed.equalsIgnoreCase(NONE) ? null : trimmed;
    }

    public final boolean isCheckStorageFileSystemStatus() {
        return checkStorageFileSystemStatus;
    }

    public final void setCheckStorageFileSystemStatus(
            boolean checkStorageFileSystemStatus) {
        this.checkStorageFileSystemStatus = checkStorageFileSystemStatus;
    }

    public boolean isMakeStorageDirectory() {
        return makeStorageDirectory;
    }

    public void setMakeStorageDirectory(boolean makeStorageDirectory) {
        this.makeStorageDirectory = makeStorageDirectory;
    }

    public final String getMountFailedCheckFile() {
        return mountFailedCheckFile;
    }

    public final void setMountFailedCheckFile(String mountFailedCheckFile) {
        this.mountFailedCheckFile = mountFailedCheckFile;
    }

    public long getFreeDiskSpaceOnCurFS() throws IOException {
        if (storageFileSystem == null)
            return -1L;
        File dir = FileUtils.toFile(storageFileSystem.getDirectoryPath());
        return dir.isDirectory() ? FileSystemUtils.freeSpace(dir.getPath())
                                 : -1L;
    }

    public String getFreeDiskSpaceOnCurFSString() throws IOException {
        return FileUtils.formatSize(getFreeDiskSpaceOnCurFS());
    }

    public long getUsableDiskSpaceOnCurFS() throws IOException {
        long free = getFreeDiskSpaceOnCurFS();
        return free == -1L ? -1L : Math.max(0, free - getMinFreeDiskSpaceBytes());
    }

    public String getUsableDiskSpaceOnCurFSString() throws IOException {
        return FileUtils.formatSize(getUsableDiskSpaceOnCurFS());
    }


    public final String getCheckFreeDiskSpaceMinimalInterval() {
        return RetryIntervalls.formatInterval(checkFreeDiskSpaceMinInterval);
    }

    public final void setCheckFreeDiskSpaceMinimalInterval(String s) {
        this.checkFreeDiskSpaceMinInterval = RetryIntervalls.parseInterval(s);
    }

    public final String getCheckFreeDiskSpaceMaximalInterval() {
        return RetryIntervalls.formatInterval(checkFreeDiskSpaceMaxInterval);
    }

    public final void setCheckFreeDiskSpaceMaximalInterval(String s) {
        this.checkFreeDiskSpaceMaxInterval = RetryIntervalls.parseInterval(s);
    }

    public final String getCheckFreeDiskSpaceRetryInterval() {
        return RetryIntervalls.formatInterval(checkFreeDiskSpaceRetryInterval);
    }

    public final void setCheckFreeDiskSpaceRetryInterval(String s) {
        this.checkFreeDiskSpaceRetryInterval = RetryIntervalls.parseInterval(s);
    }

    public void setScheduleStudiesForDeletionOnSeriesStored(
            boolean scheduleStudiesForDeletionOnSeriesStored) {
        this.scheduleStudiesForDeletionOnSeriesStored =
                scheduleStudiesForDeletionOnSeriesStored;
    }

    public boolean isScheduleStudiesForDeletionOnSeriesStored() {
        return scheduleStudiesForDeletionOnSeriesStored;
    }

    public void setDeleteOrphanedPrivateFilesBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize: " + batchSize);
        }
        this.deleteOrphanedPrivateFilesBatchSize = batchSize;
    }

    public int getDeleteOrphanedPrivateFilesBatchSize() {
        return deleteOrphanedPrivateFilesBatchSize;
    }

    public int getUpdateStudiesBatchSize() {
        return updateStudiesBatchSize;
    }

    public void setUpdateStudiesBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize: " + batchSize);
        }
        this.updateStudiesBatchSize = batchSize;
    }

    public String listAllFileSystems() throws Exception {
        FileSystemDTO[] fss = fileSystemMgt().getAllFileSystems();
        sortFileSystems(fss);
        return toString(fss);
    }

    public String listFileSystems() throws Exception {
        FileSystemDTO[] fss = fileSystemMgt()
                .getFileSystemsOfGroup(getFileSystemGroupID());
        sortFileSystems(fss);
        return toString(fss);
    }

    public File[] listFileSystemDirectories() throws Exception {
        FileSystemDTO[] fss = fileSystemMgt()
                .getFileSystemsOfGroup(getFileSystemGroupID());
        sortFileSystems(fss);
        File[] dirs = new File[fss.length];
        for (int i = 0; i < fss.length; i++)
            dirs[i] = FileUtils.toFile(fss[i].getDirectoryPath());
        return dirs;
    }

    public FileSystemDTO addRWFileSystem(String dirPath) throws Exception {
        return addRWFileSystem( mkFileSystemDTO(dirPath.trim(), FileSystemStatus.RW) );
    }
    
    protected FileSystemDTO addRWFileSystem( FileSystemDTO fsDTO ) throws Exception {
        FileSystemDTO dto = fileSystemMgt().addAndLinkFileSystem( fsDTO );
        if (dto.getStatus() == FileSystemStatus.DEF_RW){
        	storageFileSystem = dto;
        }
    	return dto; 
    }

    private FileSystemDTO mkFileSystemDTO(String dirPath, int status) {
        FileSystemDTO fs = new FileSystemDTO();
        fs.setDirectoryPath(dirPath);
        fs.setGroupID(getFileSystemGroupID());
        fs.setRetrieveAET(defRetrieveAET);
        fs.setAvailability(defAvailability);
        fs.setUserInfo(defUserInfo);
        fs.setStatus(status);
        return fs;
    }

    public FileSystemDTO removeFileSystem(String dirPath) throws Exception {
        FileSystemDTO fsDTO = fileSystemMgt()
                .removeFileSystem(getFileSystemGroupID(), dirPath);
        if (storageFileSystem != null
                && storageFileSystem.getPk() == fsDTO.getPk()) {
            selectStorageFileSystem();
        }
        return fsDTO;
    }

    public FileSystemDTO linkFileSystems(String dirPath, String next)
            throws Exception {
        return fileSystemMgt()
                .linkFileSystems(getFileSystemGroupID(), dirPath, next);
    }

    public FileSystemDTO updateFileSystemStatus(String dirPath, String status)
            throws Exception {
        FileSystemDTO fsDTO = fileSystemMgt().updateFileSystemStatus(
                getFileSystemGroupID(), dirPath,
                FileSystemStatus.toInt(status));
		selectStorageFileSystem();
        return fsDTO;
    }

    public FileSystemDTO updateFileSystemAvailability(String dirPath,
            String availability, String availabilityOfExternalRetrievable)
            throws Exception {
        String fsGroupID = getFileSystemGroupID();
        FileSystemMgt2 fsMgt = fileSystemMgt();
        return fsMgt.updateFileSystemAvailability(fsGroupID, dirPath,
                Availability.toInt(availability)) 
                ? fsMgt.updateAvailabilityForStudyOnFileSystem(fsGroupID,
                        dirPath,
                        Availability.toInt(availabilityOfExternalRetrievable),
                        updateStudiesBatchSize)
                : fsMgt.getFileSystemOfGroup(fsGroupID, dirPath);
    }

    public FileSystemDTO updateFileSystemRetrieveAETitle(String dirPath,
            String aet) throws Exception {
        return fileSystemMgt().updateFileSystemRetrieveAET(
                getFileSystemGroupID(), dirPath, aet, updateStudiesBatchSize);
    }

    public FileSystemDTO selectStorageFileSystem() throws Exception { 
        FileSystemMgt2 fsMgt = fileSystemMgt();
        if (storageFileSystem == null) {
            initStorageFileSystem(fsMgt);
        } else {
            if (checkStorageFileSystemStatus)
                checkStorageFileSystemStatus(fsMgt);
        }
        if (storageFileSystem == null) {
            log.warn("No writeable storage file system configured in group "
                    + getFileSystemGroupID() + " - storage will fail until "
                    + "a writeable storage file system is configured.");
            return null;
        }
        if (checkFreeDiskSpaceTime < System.currentTimeMillis()) {
            if (!(checkFreeDiskSpace(storageFileSystem)
                    || switchFileSystem(fsMgt, storageFileSystem))) {
                log.error("High Water Mark reached on storage file system "
                        + storageFileSystem + " - no alternative storage file "
                        + "system configured for file system group "
                        + getFileSystemGroupID());
                storageFileSystem = null;
                return null;
            }
        } else {
            if (checkFS(storageFileSystem, " - check if storage FS is still available") == null && 
                    !switchFileSystem(fsMgt, storageFileSystem)) {
                log.error("Storage file system not available: " + storageFileSystem 
                        + " - no alternative storage file found for file system group "
                        + getFileSystemGroupID());
                return null;
            }
        }
        return storageFileSystem;
    }

    public File selectStorageDirectory() throws Exception {
        FileSystemDTO dto = selectStorageFileSystem();
        return dto != null ? FileUtils.toFile(dto.getDirectoryPath()) : null;
    }
    
    public boolean switchFileSystem() throws Exception {
        return switchFileSystem(fileSystemMgt(), storageFileSystem);
    }

    private boolean switchFileSystem(FileSystemMgt2 fsMgt,
            FileSystemDTO fsDTO) throws Exception {
		synchronized (switchFileSystemMonitor) {
	if (!updateStorageFileSystem(fsMgt)) {
	    return selectStorageFileSystem() != null;
	}
        if (storageFileSystem == null || fsDTO == null) {
            log.info("Storage filesystem not set! No RW filesystem configured or no space left!");
            return false;
        } else if (storageFileSystem.getPk() != fsDTO.getPk()) {
            log.info("Storage file system has already been switched from "
                    + fsDTO + " to " + storageFileSystem
                    + " by another thread.");
            return true; 
        }
        FileSystemDTO tmp = storageFileSystem;
        String next;
        while ((next = tmp.getNext()) != null &&
                !next.equals(storageFileSystem.getDirectoryPath())) {
            tmp = fsMgt.getFileSystemOfGroup(getFileSystemGroupID(), next);
            if (minFreeDiskSpaceRatio > 0) {
                minFreeDiskSpace = calcFreeDiskSpace(tmp);
            }
            if (tmp.getStatus() == FileSystemStatus.RW && 
            		tmp.getAvailability() == Availability.toInt(getDefAvailability()) &&
                    checkFreeDiskSpace(tmp)) {
                storageFileSystem = fsMgt.updateFileSystemStatus(
                        tmp.getPk(), FileSystemStatus.DEF_RW);
                log.info("Switch storage file system from " + fsDTO + " to "
                        + storageFileSystem);
                sendJMXNotification(new StorageFileSystemSwitched(
                        fsDTO, storageFileSystem));
                return true;
            }
        }
        return false;
    }
    }

    void sendJMXNotification(Object o) {
        long eventID = super.getNextNotificationSequenceNumber();
        Notification notif = new Notification(o.getClass().getName(), this,
                eventID);
        notif.setUserData(o);
        super.sendNotification(notif);
    }

    private boolean checkFreeDiskSpace(FileSystemDTO fsDTO) throws IOException {
        File dir = checkFS(fsDTO, " - try to switch to next configured storage directory");
        if (dir == null)
            return false;
        if (getMinFreeDiskSpaceBytes() == 0) {
            return true;
        }
        final long freeSpace = FileSystemUtils.freeSpace(dir.getPath());
        log.info("Free disk space on " + dir + ": "
                + FileUtils.formatSize(freeSpace));
        if (freeSpace < getMinFreeDiskSpaceBytes()) {
            log.info("High Water Mark reached on current storage directory "
                    + dir
                    + " - try to switch to next configured storage directory");
            return false;
        }
        checkFreeDiskSpaceTime = System.currentTimeMillis() + Math.min(
                freeSpace * checkFreeDiskSpaceMinInterval / getMinFreeDiskSpaceBytes(),
                checkFreeDiskSpaceMaxInterval);
        return true;
    }

    private File checkFS(FileSystemDTO fsDTO, String msgPostfix) {
        File dir = FileUtils.toFile(fsDTO.getDirectoryPath());
        if (!dir.exists()) {
            if (!makeStorageDirectory) {
                log.warn("No such directory " + dir + msgPostfix);
                return null;
            }
            log.info("M-WRITE " + dir);
            if (!dir.mkdirs()) {
                log.warn("Failed to create directory " + dir + msgPostfix);
                return null;
            }
        }
        File nomount = new File(dir, mountFailedCheckFile);
        if (nomount.exists()) {
            log.warn("Mount on " + dir + " seems broken" + msgPostfix);
            return null;
        }
        return dir;
    }

    private void checkStorageFileSystemStatus(FileSystemMgt2 fsMgt)
            throws FinderException, RemoteException {
        try {
            FileSystemDTO tmpFS = fsMgt.getFileSystem(storageFileSystem.getPk());
            if (tmpFS.getStatus() == FileSystemStatus.DEF_RW) {
                return;
            }
            log.info("Status of previous storage file system changed: "
                    + storageFileSystem);
        } catch (ObjectNotFoundException onfe) {
            log.info("Previous storage file system: " + storageFileSystem
                    + " was removed from configuration");
        }
        storageFileSystem = fsMgt.getDefRWFileSystemsOfGroup(
                getFileSystemGroupID());
        if (storageFileSystem != null) {
            log.info("New storage file system: " + storageFileSystem);
        }
        if (minFreeDiskSpaceRatio > 0) {
            minFreeDiskSpace = calcFreeDiskSpace(storageFileSystem);
        }
        checkFreeDiskSpaceTime = 0;
    }
    private boolean updateStorageFileSystem(FileSystemMgt2 fsMgt) throws FinderException, RemoteException {
        if (storageFileSystem == null)
            return false;
        try {
            storageFileSystem = fsMgt.getFileSystem(storageFileSystem.getPk());
            return true;
        } catch (ObjectNotFoundException ignore) {
            log.warn("Current storage file system: " + storageFileSystem
                    + " was removed from configuration");
            storageFileSystem = null;
            return false;
        }
    }

    private void initStorageFileSystem(FileSystemMgt2 fsMgt) throws Exception {
        storageFileSystem = fsMgt.getDefRWFileSystemsOfGroup(
                getFileSystemGroupID());
        if (storageFileSystem == null) {
            if (defStorageDir != null) {
                storageFileSystem = fsMgt.addFileSystem(
                        mkFileSystemDTO(defStorageDir, FileSystemStatus.DEF_RW));
                log.info("No writeable storage file system configured in group "
                        + getFileSystemGroupID() + " - auto configure "
                        + storageFileSystem);
                return;
            }
        }
        checkFreeDiskSpaceTime = 0;
        if (minFreeDiskSpaceRatio > 0) {
            minFreeDiskSpace = calcFreeDiskSpace(storageFileSystem);
        }
    }


    private static String toString(FileSystemDTO[] fss) {
        StringBuilder sb = new StringBuilder();
        for (FileSystemDTO fs : fss) {
            sb.append(fs).append("\r\n");
        }
        String s = sb.toString();
        return s;
    }

    private static void sortFileSystems(FileSystemDTO[] fss) {
        for (int i = 0; i < fss.length; i++) {
            selectRoot(fss, i);
            while (selectNext(fss, i)) {
                i++;
            }
        }
    }

    private static boolean selectRoot(FileSystemDTO[] fss, int index) {
        for (int i = index; i < fss.length; i++) {
            if (!hasPrevious(fss, i)) {
                swap(fss, index, i);
                return true;
            }
        }
        return false;
    }

    private static boolean selectNext(FileSystemDTO[] fss, int index) {
        String next = fss[index].getNext();
        if (next == null) {
            return false;
        }
        for (int i = index+1; i < fss.length; i++) {
            if (next.equals(fss[i].getDirectoryPath())) {
                swap(fss, index+1, i);
                return true;
            }
        }
        return false;
    }

    private static void swap(FileSystemDTO[] fss, int i, int j) {
        if (i == j) return;
        FileSystemDTO tmp = fss[i];
        fss[i] = fss[j];
        fss[j] = tmp;
    }

    private static boolean hasPrevious(FileSystemDTO[] fss, int index) {
        String next = fss[index].getDirectoryPath();
        for (int i = 0; i < fss.length; i++) {
            if (i != index && next.equals(fss[i].getNext())) {
                return true;
            }
        }
        return false;
    }

    protected String showTriggerInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Trigger intervall: ").append(getScheduleStudiesForDeletionInterval())
        .append("\nTrigger on SeriesStored:").append(scheduleStudiesForDeletionOnSeriesStored);
        if ("NEVER".equals(getScheduleStudiesForDeletionInterval()) && !scheduleStudiesForDeletionOnSeriesStored) {
            sb.append("\nWARN: No studies will be deleted automatically from file systems of file system group ")
                .append(this.getFileSystemGroupID()).append("\n\n");
        }
        return sb.toString();
    }

    public int deleteOrphanedPrivateFiles() throws Exception {
		if (deleteOrphanedPrivateFilesLock.tryLock()) {
        try {
				isRunningDeleteOrphanedPrivateFiles = true;
            return internalDeleteOrphanedPrivateFiles();
        } finally {
				deleteOrphanedPrivateFilesLock.unlock();
	            isRunningDeleteOrphanedPrivateFiles = false;
        	}
		} else {
			log.info("DeleteOrphanedPrivateFiles is already running!");
			return -1;
        }
    }

	private int internalDeleteOrphanedPrivateFiles() throws Exception,
			FinderException, RemoteException {
		log.info("Check file system group " + getFileSystemGroupID()
		        + " for deletion of orphaned private files");

		FileSystemMgt2Local fsMgt = fileSystemMgt2();
		
		return fileDeleter.deleteFiles(fsMgt, asList(fsMgt.getOrphanedPrivateFilesOnFSGroup(
				getFileSystemGroupID(), deleteOrphanedPrivateFilesBatchSize)));
	}

    @Override
    protected void scheduleDeleteOrder(DeleteStudyOrder order) throws Exception {
        this.deleteStudy.scheduleDeleteOrder(order);
        
    }

    @Override
    protected String getFileSystemGroupIDForDeleter() {
        return this.getFileSystemGroupID();
    }
    
    private long nextMidnight() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }
    
    private FileSystemMgt2Local fileSystemMgt2() throws Exception {
		return ((FileSystemMgt2LocalHome) jndiHelper
				.jndiLookup(FileSystemMgt2LocalHome.JNDI_NAME)).create();
    }
}
