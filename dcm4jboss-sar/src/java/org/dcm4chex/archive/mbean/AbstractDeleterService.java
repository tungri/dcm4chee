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

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.management.Attribute;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.dcm4che.data.Dataset;
import org.dcm4che.dict.Tags;
import org.dcm4cheri.util.StringUtils;
import org.dcm4chex.archive.common.Availability;
import org.dcm4chex.archive.common.DeleteStudyOrder;
import org.dcm4chex.archive.common.DeleteStudyOrdersAndMaxAccessTime;
import org.dcm4chex.archive.common.FileSystemStatus;
import org.dcm4chex.archive.config.DeleterThresholds;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.dcm.findscu.FindScuDelegate;
import org.dcm4chex.archive.ejb.interfaces.FileSystemDTO;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2Home;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.dcm4chex.archive.util.FileSystemUtils;
import org.dcm4chex.archive.util.FileUtils;
import org.jboss.system.ServiceMBeanSupport;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @author Franz Willer <franz.willer@gmail.com>
 * @version $Revision$ $Date$
 * @since Aug 8, 2008
 */
public abstract class AbstractDeleterService extends ServiceMBeanSupport {

    public static final long MIN_FREE_DISK_SPACE = 20 * FileUtils.MEGA;

    protected static final String NONE = "NONE";

    protected static final String AUTO = "AUTO";
    
    protected static final String NEW_LINE = System.getProperty("line.separator", "\n");

    private final FindScuDelegate findScu = new FindScuDelegate(this);

    protected final SchedulerDelegate scheduler = new SchedulerDelegate(this);

    private Lock scheduleStudiesForDeletionLock;
    private String timerIDScheduleStudiesForDeletion;

    private long scheduleStudiesForDeletionInterval;
    private volatile boolean isRunningScheduleStudiesForDeletion;

    private DeleterThresholds deleterThresholds;

    private long maxNotAccessedFor = 0;

    private long minNotAccessedFor = 0;

    private boolean externalRetrievable;

    private String instanceAvailabilityOfExternalRetrievable;

    private boolean storageNotCommited;

    private boolean copyOnMedia;

    private String[] copyOnFSGroup;

    private boolean copyArchived;

    private boolean copyOnReadOnlyFS;

    private int scheduleStudiesForDeletionBatchSize;

    private Integer scheduleStudiesForDeletionListenerID;


    public AbstractDeleterService() {
    	this(new ReentrantLock());
	}

	AbstractDeleterService(Lock scheduleStudiesForDeletionLock) {
		this.scheduleStudiesForDeletionLock = scheduleStudiesForDeletionLock;
	}

    public ObjectName getFindScuServiceName() {
        return findScu.getFindScuServiceName();
    }

    public void setFindScuServiceName(ObjectName findScuServiceName) {
        findScu.setFindScuServiceName(findScuServiceName);
    }

    public ObjectName getSchedulerServiceName() {
        return scheduler.getSchedulerServiceName();
    }

    public void setSchedulerServiceName(ObjectName schedulerServiceName) {
        scheduler.setSchedulerServiceName(schedulerServiceName);
    }

    public void setTimerIDScheduleStudiesForDeletion(String timerID) {
        this.timerIDScheduleStudiesForDeletion = timerID;
    }

    public String getTimerIDScheduleStudiesForDeletion() {
        return timerIDScheduleStudiesForDeletion;
    }

    public String getScheduleStudiesForDeletionInterval() {
        return RetryIntervalls.formatIntervalZeroAsNever(
                scheduleStudiesForDeletionInterval);
    }

    public void setScheduleStudiesForDeletionInterval(String interval)
            throws Exception {
        this.scheduleStudiesForDeletionInterval = RetryIntervalls
                .parseIntervalOrNever(interval);
        if (getState() == STARTED) {
            scheduler.stopScheduler(timerIDScheduleStudiesForDeletion,
                    scheduleStudiesForDeletionListenerID,
                    scheduleStudiesForDeletionListener);
            scheduleStudiesForDeletionListenerID = scheduler.startScheduler(
                    timerIDScheduleStudiesForDeletion,
                    scheduleStudiesForDeletionInterval,
                    scheduleStudiesForDeletionListener);
        }
    }

    public boolean isRunningScheduleStudiesForDeletion() {
        return isRunningScheduleStudiesForDeletion;
    }

    protected void startService() throws Exception {
        scheduleStudiesForDeletionListenerID = scheduler.startScheduler(
                timerIDScheduleStudiesForDeletion,
                scheduleStudiesForDeletionInterval,
                scheduleStudiesForDeletionListener);
    }
    
    @Override
    protected void stopService() throws Exception {
        scheduler.stopScheduler(timerIDScheduleStudiesForDeletion,
                scheduleStudiesForDeletionListenerID,
                scheduleStudiesForDeletionListener);
    }

    private final NotificationListener scheduleStudiesForDeletionListener =
            new NotificationListener() {
        public void handleNotification(Notification notif, Object handback) {
            startScheduleStudiesForDeletion();
        }
    };

    protected void startScheduleStudiesForDeletion() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    scheduleStudiesForDeletion();
                } catch (Exception e) {
                    log.error("Schedule Studies for deletion failed:", e);
                }
            }
        }).start();
    }
    
    protected abstract String getFileSystemGroupIDForDeleter();

    public abstract long getMinFreeDiskSpaceBytes();

    public long getFreeDiskSpace() throws Exception {
        boolean allRW = getMinFreeDiskSpaceBytes() != 0;
        FileSystemDTO[] fsDTOs =
            fileSystemMgt().getFileSystemsOfGroup(getFileSystemGroupIDForDeleter());
        long free = 0L;
        for (FileSystemDTO fsDTO : fsDTOs) {
            int status = fsDTO.getStatus();
            if ( (allRW && status == FileSystemStatus.RW)
                    || status == FileSystemStatus.DEF_RW) {
                File dir = FileUtils.toFile(fsDTO.getDirectoryPath());
                if (dir.isDirectory()) {
                    free += FileSystemUtils.freeSpace(dir.getPath());
                }
            }
        }
        return free;
    }

    public String getFreeDiskSpaceString() throws Exception {
        return FileUtils.formatSize(getFreeDiskSpace());
    }

    public long getUsableDiskSpace() throws Exception {
        return calcUsableDiskSpace(getMinFreeDiskSpaceBytes());
    }
    
    public long calcUsableDiskSpace(long minFree) throws Exception {
        return calcUsableDiskSpace(fileSystemMgt().getFileSystemsOfGroup(getFileSystemGroupIDForDeleter()), minFree);
    }

    private long calcUsableDiskSpace(FileSystemDTO[] fsDTOs, long minFree) throws IOException {
        boolean allRW = minFree != 0;
        long free = 0L;
        for (FileSystemDTO fsDTO : fsDTOs) {
            int status = fsDTO.getStatus();
            if ( (allRW && status == FileSystemStatus.RW) || status == FileSystemStatus.DEF_RW) {
                File dir = FileUtils.toFile(fsDTO.getDirectoryPath());
                if (dir.isDirectory()) {
                    free += Math.max(0, FileSystemUtils.freeSpace(dir.getPath()) - minFree);
                }
            }
        }
        return free;
    }

    public String getUsableDiskSpaceString() throws Exception {
        return FileUtils.formatSize(getUsableDiskSpace());
    }

    public final String getDeleterThresholds() {
        return deleterThresholds == null ? NONE : deleterThresholds.toString();
    }

    public final void setDeleterThresholds(String s) {
        this.deleterThresholds = s.equalsIgnoreCase(NONE) ? null
                : new DeleterThresholds(s, true);
    }
    
    public abstract long getExpectedDataVolumePerDayBytes() throws Exception;

    public long getCurrentDeleterThreshold() throws Exception {
        if (deleterThresholds == null) {
            return -1L;
        }
        FileSystemMgt2 fsMgt = fileSystemMgt();
        return getCurrentDeleterThreshold(fsMgt,
                fsMgt.getFileSystemsOfGroup(getFileSystemGroupIDForDeleter()));
    }

    private long getCurrentDeleterThreshold(FileSystemMgt2 fsMgt,
            FileSystemDTO[] fsDTOs) throws Exception {
        long exp = getExpectedDataVolumePerDayBytes();
        return exp == -1L ? -1L : deleterThresholds.getDeleterThreshold(Calendar.getInstance()).getFreeSize(exp);
    }

    public String getDeleteStudyIfNotAccessedFor() {
        return RetryIntervalls.formatIntervalZeroAsNever(maxNotAccessedFor);
    }

    public void setDeleteStudyIfNotAccessedFor(String interval) {
        this.maxNotAccessedFor = RetryIntervalls.parseIntervalOrNever(interval);
    }

    public String getDeleteStudyOnlyIfNotAccessedFor() {
        return RetryIntervalls.formatInterval(minNotAccessedFor);
    }

    public void setDeleteStudyOnlyIfNotAccessedFor(String interval) {
        this.minNotAccessedFor = RetryIntervalls.parseInterval(interval);
    }

    public boolean isDeleteStudyOnlyIfStorageNotCommited() {
        return storageNotCommited;
    }

    public void setDeleteStudyOnlyIfStorageNotCommited(
            boolean storageNotCommited) {
        this.storageNotCommited = storageNotCommited;
    }

    public boolean isDeleteStudyOnlyIfCopyOnMedia() {
        return copyOnMedia;
    }

    public boolean isDeleteStudyOnlyIfCopyExternalRetrievable() {
        return externalRetrievable;
    }

    public void setDeleteStudyOnlyIfCopyExternalRetrievable(
            boolean externalRetrievable) {
        this.externalRetrievable = externalRetrievable;
    }

    public final String getInstanceAvailabilityOfExternalRetrievable() {
        return instanceAvailabilityOfExternalRetrievable != null
                ? instanceAvailabilityOfExternalRetrievable : AUTO;
    }

    public final void setInstanceAvailabilityOfExternalRetrievable(
            String availability) {
        String trimmed = availability.trim();
        this.instanceAvailabilityOfExternalRetrievable = 
            trimmed.equalsIgnoreCase(AUTO) ? null 
                    : Availability.toString(Availability.toInt(trimmed));
    }

    public void setDeleteStudyOnlyIfCopyOnMedia(boolean copyOnMedia) {
        this.copyOnMedia = copyOnMedia;
    }

    public String getDeleteStudyOnlyIfCopyOnFileSystemOfFileSystemGroup() {
        if (copyOnFSGroup == null) {
            return NONE;
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0 ; i < copyOnFSGroup.length ; i++) {
                sb.append(copyOnFSGroup[i]).append(NEW_LINE);
            }
            return sb.toString();
        }
    }

    public void setDeleteStudyOnlyIfCopyOnFileSystemOfFileSystemGroup(
            String copyOnFSGroup) {
        String trimmed = copyOnFSGroup.trim();
        if (serviceName != null // may not be initialized when invoked during bean registration
                && trimmed.equals(getFileSystemGroupIDForDeleter())) {
            throw new IllegalArgumentException(
                    "Must differ from file system group managed by this service");
        }
        if (trimmed.equalsIgnoreCase(NONE)) {
            this.copyOnFSGroup =  null;
        } else {
            StringTokenizer st = new StringTokenizer(trimmed, " \t\r\n;");
            this.copyOnFSGroup = new String[st.countTokens()];
            for (int i = 0 ; st.hasMoreTokens() ; i++) {
                this.copyOnFSGroup[i] = st.nextToken();
            }
        }

    }

    public boolean isDeleteStudyOnlyIfCopyArchived() {
        return copyArchived;
    }

    public void setDeleteStudyOnlyIfCopyArchived(boolean copyArchived) {
        this.copyArchived = copyArchived;
    }

    public boolean isDeleteStudyOnlyIfCopyOnReadOnlyFileSystem() {
        return copyOnReadOnlyFS;
    }

    public void setDeleteStudyOnlyIfCopyOnReadOnlyFileSystem(
            boolean copyOnReadOnlyFS) {
        this.copyOnReadOnlyFS = copyOnReadOnlyFS;
    }

    public void setScheduleStudiesForDeletionBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize: " + batchSize);
        }
        this.scheduleStudiesForDeletionBatchSize = batchSize;
    }

    public int getScheduleStudiesForDeletionBatchSize() {
        return scheduleStudiesForDeletionBatchSize;
    }

    protected FileSystemMgt2 fileSystemMgt() throws Exception {
        FileSystemMgt2Home home = (FileSystemMgt2Home) EJBHomeFactory
                .getFactory().lookup(FileSystemMgt2Home.class,
                        FileSystemMgt2Home.JNDI_NAME);
        return home.create();
    }

    public int scheduleStudiesForDeletion() throws Exception {
        if (maxNotAccessedFor == 0 && deleterThresholds == null) {
            return 0;
        }
        
		if (scheduleStudiesForDeletionLock.tryLock()) {
			try {
				isRunningScheduleStudiesForDeletion = true;
				return doScheduleStudiesForDeletion();
			} finally {
				scheduleStudiesForDeletionLock.unlock();
				isRunningScheduleStudiesForDeletion = false;
			}
		} else {
                log.info("ScheduleStudiesForDeletion is already running!");
                return -1;
            }
        } 

	protected int doScheduleStudiesForDeletion() throws Exception {
        String fsGroup = getFileSystemGroupIDForDeleter();
            FileSystemMgt2 fsMgt = fileSystemMgt();
            FileSystemDTO[] fsDTOs = fsMgt.getFileSystemsOfGroup(fsGroup);
            if (fsDTOs.length == 0) {
			log.info("No Filesystem configured in file system group "
					+ fsGroup
					+ "! Ignore check for deletion of studies!");
                return 0;
            }
            log.info("Check file system group " + fsGroup
                    + " for deletion of studies");
            int countStudies = 0;
            if (maxNotAccessedFor > 0) {
                countStudies = scheduleStudiesForDeletion(fsMgt,
                        System.currentTimeMillis() - maxNotAccessedFor,
                        Long.MAX_VALUE);
            }
            if (deleterThresholds != null) {
                long threshold = getCurrentDeleterThreshold(fsMgt, fsDTOs);
			long usable = calcUsableDiskSpace(fsDTOs,
					getMinFreeDiskSpaceBytes());
                long sizeToDel = threshold - usable;
                if (sizeToDel > 0) {
				log.info("Try to free " + sizeToDel
						+ " of disk space on file system group "
						+ fsGroup);
                    countStudies += scheduleStudiesForDeletion(fsMgt,
                            System.currentTimeMillis() - minNotAccessedFor,
                            sizeToDel);
                }
            }
            if (countStudies > 0) {
			log.info("Scheduled " + countStudies
					+ " studies for deletion on file system group "
					+ fsGroup);
            }
            return countStudies;
    }

    private int scheduleStudiesForDeletion(FileSystemMgt2 fsMgt,
            long notAccessedAfter, long sizeToDel0) throws Exception {
        int countStudies = 0;
        long minAccessTime = 0;
        long sizeToDel = sizeToDel0;
        String copyOnFSGroupString = StringUtils.toString(copyOnFSGroup, '&');
        do {
            DeleteStudyOrdersAndMaxAccessTime deleteOrdersAndAccessTime = 
                    fsMgt.createDeleteOrdersForStudiesOnFSGroup(
                            getFileSystemGroupIDForDeleter(), minAccessTime,
                            notAccessedAfter,
                            scheduleStudiesForDeletionBatchSize,
                            externalRetrievable, storageNotCommited,
                            copyOnMedia, copyOnFSGroupString, copyArchived,
                            copyOnReadOnlyFS);
            if (deleteOrdersAndAccessTime == null) {
                if (sizeToDel0 != Long.MAX_VALUE) {
                    log.warn("Could not find any further study for deletion on "
                            + "file system group " + getFileSystemGroupIDForDeleter());
                }
                break;
            }
            Iterator<DeleteStudyOrder> orderIter = 
                    deleteOrdersAndAccessTime.deleteStudyOrders.iterator();
            long[] result = markAndScheduleDeleteOrders(deleteOrdersAndAccessTime.deleteStudyOrders, sizeToDel);
            if (result[0] != 0) {
                sizeToDel -= result[0];
                countStudies += result[1];
            }
            if (deleteOrdersAndAccessTime.deleteStudyOrders.size() == 0 && 
                    minAccessTime == deleteOrdersAndAccessTime.maxAccessTime) {
                log.warn("Possible infinite loop in deleter thread detected! Please check access_time in study_on_fs! Current minAccessTime:"+minAccessTime);
                minAccessTime++;
            } else {
                minAccessTime = deleteOrdersAndAccessTime.maxAccessTime;
            }
        } while (sizeToDel > 0 && isRunningScheduleStudiesForDeletion);
        if (countStudies == 0 && sizeToDel > 0) {
            log.warn("No study found for clean up filesystem group "+getFileSystemGroupIDForDeleter()+"! Please check your configuration!");
            log.warn(showDeleterCriteria());
        }
        return countStudies;
    }

    private long[] markAndScheduleDeleteOrders(Collection<DeleteStudyOrder> orders, long maxSize) throws Exception {
        FileSystemMgt2 fsMgt = fileSystemMgt();
        long[] result = new long[]{0,0};
        if (orders.size() > 0) {
            Iterator<DeleteStudyOrder> iter = orders.iterator();
            boolean dontCheckMax = maxSize < 1;
            DeleteStudyOrder order;
            while (iter.hasNext() && (dontCheckMax || result[0] < maxSize )) {
                order = iter.next();
                if (!checkExternalRetrievable(order))
                    continue;
                if (fsMgt.markStudyOnFSRecordForDeletion(order, true)) {
                    try {
                        scheduleDeleteOrder(order);
                    } catch (Exception e) {
                        fsMgt.markStudyOnFSRecordForDeletion(order, false);
                        throw e;
                    }
                    result[0] += fsMgt.getStudySize(order);
                    result[1]++;
                }
            }
        }
        return result;
    }
    
    protected abstract void scheduleDeleteOrder(DeleteStudyOrder order) throws Exception;

    protected String showTriggerInfo() {
        return "Trigger intervall: "+getScheduleStudiesForDeletionInterval();
    }

    public String showDeleterCriteria() {
        StringBuilder sb = new StringBuilder();
        sb.append(showTriggerInfo());
        if (maxNotAccessedFor==0) {
            sb.append("\nOnly triggered by running out of disk space! Studies not accessed for ")
            .append(getDeleteStudyOnlyIfNotAccessedFor());
        } else {
            sb.append("\nAll studies not accessed for ").append(getDeleteStudyIfNotAccessedFor());
            sb.append(".\n And studies not accessed for ").append(getDeleteStudyOnlyIfNotAccessedFor())
            .append(" when running out of disk space!");
        }
        sb.append("\nDeleter Criteria: ");
        int i = 0;
        if (externalRetrievable)
            sb.append("\n  ").append(++i).append(") External Retrievable");
        if (copyOnFSGroup != null)
            sb.append("\n  ").append(++i).append(") Copy on Filesystem Group "+StringUtils.toString(copyOnFSGroup,','));
        if (copyArchived)
            sb.append("\n  ").append(++i).append(") Copy must be archived");
        if (copyOnReadOnlyFS)
            sb.append("\n  ").append(++i).append(") Copy on a ReadOnly Filesystem");
        if (copyOnMedia)
            sb.append("\n  ").append(++i).append(") Copy on Media");
        if (storageNotCommited)
            sb.append("\n  ").append(++i).append(") Storage Not Commited");
        if (i==0) 
            sb.append("\n  WARNING! No Deletion criteria configured!");
        return sb.toString();
    }
    
    public void stopCurrentDeleterThread() {
        isRunningScheduleStudiesForDeletion = false;
    }

    private boolean checkExternalRetrievable(DeleteStudyOrder order) {
        String aet = order.getExternalRetrieveAET();
        if (aet == null)
            return true;
        
        String availability = instanceAvailabilityOfExternalRetrievable;
        String studyIUID = order.getStudyIUID();
        if (availability == null) {
            try {
                Dataset findRsp = findScu.findStudy(aet, studyIUID);
                if (findRsp == null) {
                    log.warn("Study:" + studyIUID + " not found at Retrieve AE: "
                            + aet);
                    return false;
                }
                availability = findRsp.getString(Tags.InstanceAvailability);
                if (availability == null) {
                    log.warn("Retrieve AE: " + aet
                            + " does not return Instance Availability for study: "
                            + studyIUID);
                    return false;
                }
            } catch (Exception e) {
               log.warn("Query external Retrieve AE: " + aet + " for study: "
                       + studyIUID +  "failed:", e);
               return false;
            }
        }
        int availabilityAsInt = Availability.toInt(availability);
        if (availabilityAsInt == Availability.UNAVAILABLE) {
            log.warn("Retrieve AE: " + aet
                    + " returns Instance Availability: UNAVAILABLE for study: "
                    + studyIUID);
            return false;
        }
        order.setExternalRetrieveAvailability(availabilityAsInt);
        return true;
    }

    public long scheduleStudyForDeletion(String suid) throws Exception {
        FileSystemMgt2 fsMgt = fileSystemMgt();
        Collection<DeleteStudyOrder> orders = 
            fsMgt.createDeleteOrdersForStudyOnFSGroup(suid,
                    getFileSystemGroupIDForDeleter());
        return markAndScheduleDeleteOrders(orders, -1)[0];
    }
}
