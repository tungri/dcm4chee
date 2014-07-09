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
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.dcm4cheri.util.StringUtils;
import org.dcm4chex.archive.common.FileStatus;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.ejb.interfaces.FileSystemDTO;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2Home;
import org.dcm4chex.archive.ejb.jdbc.QueryHSMMigrateCmd;
import org.dcm4chex.archive.mbean.SchedulerDelegate;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.dcm4chex.archive.util.FileUtils;
import org.jboss.system.ServiceMBeanSupport;

/**
 * @author franz.willer@gmail.com
 * @version $Revision: $
 * @since June 28, 2012
 */
public class HSMMigrateService extends ServiceMBeanSupport {

    private static final String NONE = "NONE";
    
    private final SchedulerDelegate scheduler = new SchedulerDelegate(this);

    private String[] timerIDs = new String[3];
    
    private long[] taskIntervals = new long[]{0L,0L,0L};

    private int[] disabledStartHours = new int[]{0,0,0};
    private int[] disabledEndHours = new int[]{0,0,0};

    private int limitNumberOfFilesPerMigrateTask;
    private int limitNumberOfFilesPerRemoveTask;

    private boolean lastPksFirst;
    
    private int targetFileStatus;

    private String srcFilesystem;
    private String targetFilesystem;
    private long srcFsPk;
    private long targetFsPk;
    
    private int concurrency = 1;
    private boolean isQueryRunning;
    private ArrayList<MigrationTask> taskList = new ArrayList<MigrationTask>();
    
    private ObjectName hsmModuleServicename;
    
    private boolean verifyTar;
    private boolean createNewTargetFilename;
    private int filenameBase = (int) (System.currentTimeMillis() & 0xffffffff);
    
    private final NotificationListener timerListenerMigrate = new NotificationListener() {
        public void handleNotification(Notification notif, Object handback) {
            if (targetFilesystem == null || srcFilesystem == null) {
                log.debug("HSM Migration service disabled (srcFilesystems or targetFilesystem is NONE)!");
                return;
            }
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if (isDisabled(hour, disabledStartHours[0], disabledEndHours[0])) {
                if (log.isDebugEnabled())
                    log.debug("HSM Migration service disabled in time between "
                            + disabledStartHours[0] + " and " + disabledEndHours[0]
                            + " !");
            } else { 
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            migrate();
                        } catch (Exception e) {
                            log.error("HSM Migration task failed!", e);
                        }
                    }
                }).start();
            }
        }

    };
    private final NotificationListener timerListenerRetry = new NotificationListener() {
        public void handleNotification(Notification notif, Object handback) {
            if (targetFilesystem == null || srcFilesystem == null) {
                log.debug("HSM Migration service disabled (srcFilesystems or targetFilesystem is NONE)!");
                return;
            }
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if (isDisabled(hour, disabledStartHours[1], disabledEndHours[1])) {
                if (log.isDebugEnabled())
                    log.debug("HSM Migration service disabled in time between "
                            + disabledStartHours[1] + " and " + disabledEndHours[1]
                            + " !");
            } else { 
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            retry();
                        } catch (Exception e) {
                            log.error("HSM Migration Retry task failed!", e);
                        }
                    }
                }).start();
            }
        }

    };
    private final NotificationListener timerListenerRemove = new NotificationListener() {
        public void handleNotification(Notification notif, Object handback) {
            if (targetFilesystem == null || srcFilesystem == null) {
                log.debug("HSM Migration service disabled (srcFilesystems or targetFilesystem is NONE)!");
                return;
            }
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if (isDisabled(hour, disabledStartHours[2], disabledEndHours[2])) {
                if (log.isDebugEnabled())
                    log.debug("HSM Migration 'remove source files' service disabled in time between "
                            + disabledStartHours[2] + " and " + disabledEndHours[2]
                            + " !");
            } else { 
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            removeSourceFiles();
                        } catch (Exception e) {
                            log.error("HSM removeSourceFiles failed!", e);
                        }
                    }
                }).start();
            }
        }

    };

    private Integer listenerIDs[] = new Integer[3];

    private Integer removeSrcOnTargetFileStatus;
    private boolean removeSourceIsRunning;

    private QueryStatusMode queryStatusMode;

    private int offsetForRemove;
    private int offsetForRetry;
    
    public final String getSourceFileSystem() {
        return srcFilesystem == null ? NONE : srcFilesystem;
    }
    public final String getSourceFileSystemPk() {
        return srcFilesystem == null ? NONE : String.valueOf(srcFsPk);
    }

    public final void setSourceFileSystem(String s) throws Exception {
        if (NONE.equals(s)) {
            srcFilesystem = null;
        } else {
            FileSystemDTO dto = null;
            try {
                dto = this.newFileSystemMgt().getFileSystem(s);
                srcFsPk = dto.getPk();
            } catch (Exception x) {
                if (this.getState() == STARTED) {
                    throw x;
                }
            }
            srcFilesystem = s;
        }
    }
    
    public final String getTargetFileSystem() {
        return targetFilesystem == null ? NONE : targetFilesystem;
    }
    public final String getTargetFileSystemPk() {
        return targetFilesystem == null ? NONE : String.valueOf(targetFsPk);
    }

    public final void setTargetFileSystem(String s) throws Exception {
        if (NONE.equals(s)) {
            targetFilesystem = null;
        } else {
            FileSystemDTO dto = null;
            try {
                dto = this.newFileSystemMgt().getFileSystem(s);
                targetFsPk = dto.getPk();
            } catch (Exception x) {
                if (this.getState() == STARTED) {
                    throw x;
                }
            }
            targetFilesystem = s;
        }
    }

    protected long getSrcFsPk() {
        return srcFsPk;
    }
    protected long getTargetFsPk() {
        return targetFsPk;
    }
    public final String getTargetFileStatus() {
        return FileStatus.toString(targetFileStatus);
    }

    public final void setTargetFileStatus(String status) {
        targetFileStatus = FileStatus.toInt(status);
    }

    public String getRemoveSrcOnTargetFileStatus() {
        return removeSrcOnTargetFileStatus == null ? NONE: FileStatus.toString(removeSrcOnTargetFileStatus);
    }
    public void setRemoveSrcOnTargetFileStatus(String status) {
        this.removeSrcOnTargetFileStatus = NONE.equals(status) ? null : FileStatus.toInt(status);
    }
    
    public String getQueryStatusMode() {
        return queryStatusMode.name();
    }
    public void setQueryStatusMode(String mode) {
        this.queryStatusMode = QueryStatusMode.valueOf(mode);
    }
    
    public boolean isVerifyTar() {
        return verifyTar;
    }
    public void setVerifyTar(boolean verifyTar) {
        this.verifyTar = verifyTar;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public String getTimerIDHSMMigrate() {
        return timerIDs[0];
    }

    public void setTimerIDHSMMigrate(String timerID) {
        this.timerIDs[0] = timerID;
    }
    public String getTimerIDHSMRtry() {
        return timerIDs[1];
    }

    public void setTimerIDHSMRetry(String timerID) {
        this.timerIDs[1] = timerID;
    }
    public String getTimerIDHSMRemove() {
        return timerIDs[2];
    }

    public void setTimerIDHSMRemove(String timerID) {
        this.timerIDs[2] = timerID;
    }
 
    public final String getTaskIntervalMigrate() {
        return getTaskInterval(0);
    }
    public void setTaskIntervalMigrate(String interval) throws Exception {
        setTaskInterval(interval, 0, timerListenerMigrate); 
    }
    
    public final String getTaskIntervalRetry() {
        return getTaskInterval(1);
    }
    public void setTaskIntervalRetry(String interval) throws Exception {
        setTaskInterval(interval, 1, timerListenerRetry); 
    }

    public final String getTaskIntervalRemove() {
        return getTaskInterval(2);
    }
    public void setTaskIntervalRemove(String interval) throws Exception {
        setTaskInterval(interval, 2, timerListenerRemove); 
    }

    private final String getTaskInterval(int idx) {
        String s = RetryIntervalls.formatIntervalZeroAsNever(taskIntervals[idx]);
        return (disabledEndHours[idx] == -1) ? s : s + "!" + disabledStartHours[idx] + "-"
                + disabledEndHours[idx];
    }

    private final void setTaskInterval(String interval, int idx, NotificationListener timerListener) throws Exception {
        long oldInterval = taskIntervals[idx];
        int pos = interval.indexOf('!');
        if (pos == -1) {
            taskIntervals[idx] = RetryIntervalls.parseIntervalOrNever(interval);
            disabledEndHours[idx] = -1;
        } else {
            taskIntervals[idx] = RetryIntervalls.parseIntervalOrNever(interval
                    .substring(0, pos));
            int pos1 = interval.indexOf('-', pos);
            disabledStartHours[idx] = Integer.parseInt(interval.substring(pos + 1,
                    pos1));
            disabledEndHours[idx] = Integer.parseInt(interval.substring(pos1 + 1));
        }
        if (getState() == STARTED && oldInterval != taskIntervals[idx]) {
            scheduler.stopScheduler(timerIDs[idx], listenerIDs[idx],
                    timerListener);
            listenerIDs[idx] = scheduler.startScheduler(timerIDs[idx],
                    taskIntervals[idx], timerListener);
        }
    }
    
    public boolean isLastPksFirst() {
        return lastPksFirst;
    }

    public void setLastPksFirst(boolean b) {
        lastPksFirst = b;
    }

    public int getLimitNumberOfFilesPerMigrateTask() {
        return limitNumberOfFilesPerMigrateTask;
    }

    public void setLimitNumberOfFilesPerMigrateTask(int limit) {
        this.limitNumberOfFilesPerMigrateTask = limit;
    }

    public int getLimitNumberOfFilesPerRemoveTask() {
        return limitNumberOfFilesPerRemoveTask;
    }
    public void setLimitNumberOfFilesPerRemoveTask(
            int limitNumberOfFilesPerRemoveTask) {
        this.limitNumberOfFilesPerRemoveTask = limitNumberOfFilesPerRemoveTask;
    }
    
    public boolean isCreateNewTargetFilename() {
        return createNewTargetFilename;
    }
    public void setCreateNewTargetFilename(boolean createNewTargetFilename) {
        this.createNewTargetFilename = createNewTargetFilename;
    }
    public int getOffsetForRemove() {
        return offsetForRemove ;
    }
    
    public int getOffsetForRetry() {
        return offsetForRetry ;
    }
    
    public ObjectName getSchedulerServiceName() {
        return scheduler.getSchedulerServiceName();
    }

    public void setSchedulerServiceName(ObjectName schedulerServiceName) {
        scheduler.setSchedulerServiceName(schedulerServiceName);
    }

    public final ObjectName getHSMModuleServicename() {
        return hsmModuleServicename;
    }

    public final void setHSMModuleServicename(ObjectName name) {
        this.hsmModuleServicename = name;
    }

    private boolean isDisabled(int hour, int disabledStartHour, int disabledEndHour) {
        if (disabledEndHour == -1)
            return false;
        boolean sameday = disabledStartHour <= disabledEndHour;
        boolean inside = hour >= disabledStartHour && hour < disabledEndHour;
        return sameday ? inside : !inside;
    }

    public boolean isQueryRunning() {
        return isQueryRunning;
    }
    public boolean isRunning() {
        synchronized(taskList) {
            return !taskList.isEmpty();
        }
    }
    public boolean isRemoveSourceRunning() {
        return removeSourceIsRunning;
    }

    protected void startService() throws Exception {
        listenerIDs[0] = scheduler.startScheduler(timerIDs[0],
                taskIntervals[0], timerListenerMigrate);
        listenerIDs[1] = scheduler.startScheduler(timerIDs[1],
                taskIntervals[1], timerListenerRetry);
        listenerIDs[2] = scheduler.startScheduler(timerIDs[2],
                taskIntervals[2], timerListenerRemove);
        if (this.srcFilesystem != null) {//to get pk's
            this.setSourceFileSystem(srcFilesystem);
            this.setTargetFileSystem(targetFilesystem);
        }
    }

    protected void stopService() throws Exception {
        scheduler.stopScheduler(timerIDs[0], listenerIDs[0],
                timerListenerMigrate);
        scheduler.stopScheduler(timerIDs[1], listenerIDs[1],
                timerListenerRemove);
        scheduler.stopScheduler(timerIDs[2], listenerIDs[2],
                timerListenerRemove);
    }

    public int migrate() throws Exception  {
        return migrate(new int[]{0,1,2}, false);
    }
    
    public int retry() throws Exception  {
        return migrate(new int[]{FileStatus.MIGRATION_FAILED}, true);
    }
    
    public int migrate(int[] fileStati, boolean retry) throws Exception  {
        if (srcFilesystem == null || this.targetFilesystem == null) {
            return 0;
        }
        synchronized(this) {
            if (isQueryRunning) {
                log.info("HSM Migration service is already running! (Query for items to migrate)");
                return -1;
            }
            if (taskList.size() > 0) {
                log.info("HSM Migration service is already running! remaining migration tasks:"+taskList.size());
                return -1;
            }
            isQueryRunning = true;
        }
        if (retry) {
            log.info("Start retry of HSM Migration!");
        } else {
            log.info("Start HSM Migration!");
        }
        int[] counts = new int[]{0,0};
        FileSystemMgt2 mgr = newFileSystemMgt();
        Collection<String> tarFiles = new QueryHSMMigrateCmd()
            .getTarFilenamesToMigrate(srcFsPk, fileStati, lastPksFirst, 
                    limitNumberOfFilesPerMigrateTask, retry ? offsetForRetry : 0);
        int len = tarFiles.size();
        if (retry)
            offsetForRetry = len < 1 ? 0 : offsetForRetry + len;
        log.info("Found "+len+" tar files to migrate on filesystem "+srcFilesystem);
        Iterator<String> iter = tarFiles.iterator();
        synchronized(taskList) {
            counts[0] = Math.min(len, concurrency);
            for (int i = 0 ; i < counts[0] ; i++) {
                MigrationTask t = new MigrationTask(this, iter, mgr, counts);
                taskList.add(t);
                log.debug("New "+t+"! request thread:"+Thread.currentThread());
                new Thread(t).start();
                log.debug(this+" - thread started.");
            }
            isQueryRunning = false;
        }
        while (counts[0] > 0) {
            Thread.sleep(1000);
        }
        return counts[1];
    }

    public int removeSourceFiles() throws Exception  {
        if (srcFilesystem == null || this.targetFilesystem == null || removeSrcOnTargetFileStatus == null) {
            return 0;
        }
        synchronized(this) {
            if (removeSourceIsRunning) {
                log.info("Remove source files is already running!");
                return -1;
            }
            removeSourceIsRunning = true;
        }
        log.info("Start of Removing migrated source files if target file status is "+
                FileStatus.toString(removeSrcOnTargetFileStatus));
        int count = 0;
        try {
            int[] stati = queryStatusMode == QueryStatusMode.NEVER ?
                    new int[]{removeSrcOnTargetFileStatus} : queryStatusMode == QueryStatusMode.ON_WRONG ?
                            new int[]{0,1,2,3,4} : null;
            List<String[]> tarFilesWithStati = new QueryHSMMigrateCmd()
                .getTarFilenamesAndStatus(srcFsPk, targetFsPk, stati, 
                        limitNumberOfFilesPerRemoveTask, offsetForRemove);
            int len = tarFilesWithStati.size();
            offsetForRemove = len < 1 ? 0 : offsetForRemove + len;
            log.info("Found "+len+" source tar files to check for removal from filesystem "+srcFilesystem);
            String[] sa;
            for (int i = 0 ; i < len ; i++) {
                sa = tarFilesWithStati.get(i);
                count += processStatusCheck(sa[0], sa[2], FileStatus.toInt(sa[3]));
            }
        } finally {
            removeSourceIsRunning = false;
        }
        log.info("Source file entities removed:"+count);
        return count;
    }

    public String showMigrationStatus() throws Exception  {
        if (srcFilesystem == null || this.targetFilesystem == null) {
            return "Migration service not active! Source Filesystem and Target Filesystem must be configured!";
        }
        List<int[]> srcFilesPerStatus = new QueryHSMMigrateCmd().countFilesPerStatus(srcFsPk);
        int srcTotal = 0;
        int remaining = 0;
        for (int[] ia : srcFilesPerStatus) {
            srcTotal += ia[1];
            if (ia[0] >= FileStatus.DEFAULT && ia[0] <FileStatus.MIGRATED) {
                remaining += ia[1];
            }
        }
        List<int[]> targetFilesPerStatus = new QueryHSMMigrateCmd().countFilesPerStatus(targetFsPk);
        int targetTotal = 0;
        for (int[] ia : targetFilesPerStatus) {
            targetTotal += ia[1];
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Migration Status: ");
        if (remaining == 0) {
            sb.append("FINISHED! ").append(targetTotal).append(" dicom files migrated");
        } else {
            sb.append(remaining).append(" dicom files remaining. ").append(targetTotal).append(" dicom files migrated!");
        }
        sb.append("\n\nSource filesystem:").append(srcFilesystem)
        .append("\nTarget filesystem:").append(targetFilesystem).append("\n\n");
        addFilesystemDetail(srcFilesPerStatus, srcFilesystem, sb);
        addFilesystemDetail(targetFilesPerStatus, targetFilesystem, sb);
        return sb.toString();
    }
    
    private void addFilesystemDetail(List<int[]> result, String fsPath, StringBuilder sb) throws SQLException {
        sb.append("Details for Filesystem ").append(fsPath).append(":\n");
        for (int[] ia : result) {
            sb.append(FileStatus.toString(ia[0])).append(":").append(ia[1]).append("<br/>");
        }
        sb.append("-----------------------------------------\n\n");
    }
    
    private int processStatusCheck(String srcTarFilename, String targetTarFilename, int currentTargetFileStatus) throws Exception {
        FileSystemMgt2 mgr = newFileSystemMgt();
        if (queryStatusMode == QueryStatusMode.ALWAYS || 
                (currentTargetFileStatus >= 0 && currentTargetFileStatus != removeSrcOnTargetFileStatus && 
                 queryStatusMode == QueryStatusMode.ON_WRONG)) {
            Integer status = queryStatus(targetFilesystem, targetTarFilename, null);
            if (status == null) {
                log.debug("No status change of target tar file "+targetTarFilename);
            } else {
                if (status != currentTargetFileStatus) {
                    log.debug("Set new file status "+FileStatus.toString(status)+" for "+targetTarFilename);
                    mgr.setFilestatusOfFilesOfTarFile(targetFilesystem, targetTarFilename, status);
                    currentTargetFileStatus = status;
                }
            }
        }
        if (currentTargetFileStatus == this.removeSrcOnTargetFileStatus) {
            log.debug("Remove file entities of source tar file:"+srcTarFilename);
            return mgr.deleteFilesOfInvalidTarFile(srcFilesystem, srcTarFilename);
        } else {
            log.debug("Remove source file entities skipped! current status:"+FileStatus.toString(currentTargetFileStatus));
            return 0;
        }
    }
    
    
    protected void removeMigrationTask(MigrationTask task) {
        synchronized(taskList) {
            taskList.remove(task);
            task.getCounts()[0]--;
            log.info(task+" removed! remaining MigrationTasks:"+taskList.size());
        }
    }


    protected File fetchTarFile(String fsID, String tarPath) throws IOException {
        try {
            return (File) server.invoke(hsmModuleServicename, "fetchHSMFile", new Object[]{fsID, tarPath}, 
                new String[]{String.class.getName(),String.class.getName()});
        } catch (Exception x) {
            log.error("Fetch of HSMFile failed! fsID:"+fsID+" tarPath:"+tarPath, x);
            IOException iox = new IOException("Fetch of HSMFile failed!");
            iox.initCause(x);
            throw iox;
        }
    }
    protected void fetchTarFileFinished(String fsID, String tarPath, File tarFile) {
        try {
            server.invoke(hsmModuleServicename, "fetchHSMFileFinished", new Object[]{fsID, tarPath, tarFile}, 
                        new String[]{String.class.getName(),String.class.getName(),File.class.getName()});
        } catch (Exception x) {
            log.warn("fetchHSMFileFinished failed! fsID:"+fsID+" tarPath:"+tarPath+" tarFile:"+tarFile, x);
        }
    }
    protected File prepareHSMFile(String fsID, String filePath) throws InstanceNotFoundException, MBeanException, ReflectionException {
        return (File) server.invoke(hsmModuleServicename, "prepareHSMFile", new Object[]{fsID, filePath}, 
               new String[]{String.class.getName(),String.class.getName()});
    }

    protected String storeHSMFile(File file, String fsID, String filePath) throws InstanceNotFoundException, MBeanException,
              ReflectionException {
        return (String) server.invoke(hsmModuleServicename, "storeHSMFile", 
               new Object[]{file, fsID, filePath}, 
               new String[]{File.class.getName(),String.class.getName(),String.class.getName()});
    }

    protected void failedHSMFile(File file, String fsID, String filePath) throws InstanceNotFoundException, MBeanException,
              ReflectionException {
        server.invoke(hsmModuleServicename, "failedHSMFile", new Object[]{file, fsID, filePath}, 
            new String[]{File.class.getName(),String.class.getName(),String.class.getName()});
    }
    protected Integer queryStatus(String fsID, String filePath, String userInfo) throws InstanceNotFoundException, MBeanException,
            ReflectionException {
        return (Integer) server.invoke(hsmModuleServicename, "queryStatus", new Object[]{fsID, filePath, userInfo}, 
                new String[]{String.class.getName(),String.class.getName(),String.class.getName()});
    }

    private FileSystemMgt2 newFileSystemMgt() throws Exception {
        return ((FileSystemMgt2Home) EJBHomeFactory.getFactory().lookup(
                FileSystemMgt2Home.class, FileSystemMgt2Home.JNDI_NAME)).create();
    }
    
    public String toTargetFilename(String fn) {
        if (createNewTargetFilename) {
            Calendar cal = Calendar.getInstance();
            StringBuilder sb = new StringBuilder();
            sb.append(String.valueOf(cal.get(Calendar.YEAR))).append(File.separatorChar)
            .append(String.valueOf(cal.get(Calendar.MONTH) + 1)).append(File.separatorChar)
            .append(String.valueOf(cal.get(Calendar.DAY_OF_MONTH))).append(File.separatorChar)
            .append(String.valueOf(cal.get(Calendar.HOUR_OF_DAY))).append(File.separatorChar)
            .append(FileUtils.toHex(filenameBase++));
            fn = sb.toString();
        }
        return fn;
    }

    private enum QueryStatusMode {
        NEVER, ALWAYS, ON_WRONG
    }
}
