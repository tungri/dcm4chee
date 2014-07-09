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
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Map.Entry;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.dcm4chex.archive.common.FileStatus;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.ejb.interfaces.FileDTO;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2Home;
import org.dcm4chex.archive.ejb.interfaces.MD5;
import org.dcm4chex.archive.exceptions.ConfigurationException;
import org.dcm4chex.archive.hsm.module.HSMException;
import org.dcm4chex.archive.mbean.SchedulerDelegate;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.jboss.system.ServiceMBeanSupport;

/**
 * @author gunter.zeilinger@tiani.com
 * @version $Revision: 17804 $ $Date: 2013-05-24 11:08:47 +0000 (Fri, 24 May 2013) $
 * @since Nov 22, 2005
 */
public class SyncFileStatusService extends ServiceMBeanSupport {

    private static final String NONE = "NONE";
    private static final String DELETE = "DELETE";
    private static final String NEWLINE = System.getProperty("line.separator", "\n");
    
    private final SchedulerDelegate scheduler = new SchedulerDelegate(this);

    private String timerIDCheckSyncFileStatus;
    
    private boolean isRunning;

    private long minFileAge = 0L;

    private long taskInterval = 0L;

    private int disabledStartHour;

    private int disabledEndHour;

    private int limitNumberOfFilesPerTask;

    private int checkFileStatus;

    private ArrayList<String> fileSystem = new ArrayList<String>();

    private Integer listenerID;

    private ObjectName hsmModuleServicename = null;
    private ObjectName tarRetrieverName;
    
    private Timestamp oldestCreatedTimeOfCheckFileStatus;
    private long nextUpdate;
    
    private boolean verifyTar;
    private HashMap<Integer,Integer> skipVerifyTarHSMStati = new HashMap<Integer,Integer>();
    private int notInTarStatus;
    private int invalidTarStatus;
    private byte[] buf = new byte[8192];
    
    private final NotificationListener timerListener = new NotificationListener() {
        public void handleNotification(Notification notif, Object handback) {
            if (fileSystem.isEmpty()) {
                log.debug("SyncFileStatus disabled (fileSystem=NONE)!");
            }
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if (isDisabled(hour)) {
                if (log.isDebugEnabled())
                    log.debug("SyncFileStatus ignored in time between "
                            + disabledStartHour + " and " + disabledEndHour
                            + " !");
            } else { 
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            check();
                        } catch (Exception e) {
                            log.error("check file status failed!", e);
                        }
                    }
                }).start();
            }
        }

    };

    public final ObjectName getTarRetrieverName() {
        return tarRetrieverName;
    }

    public final void setTarRetrieverName(ObjectName tarRetrieverName) {
        this.tarRetrieverName = tarRetrieverName;
    }
    
    public ObjectName getSchedulerServiceName() {
        return scheduler.getSchedulerServiceName();
    }

    public void setSchedulerServiceName(ObjectName schedulerServiceName) {
        scheduler.setSchedulerServiceName(schedulerServiceName);
    }

    public final String getFileSystem() {
        if (fileSystem.isEmpty())
            return NONE;
        StringBuilder sb = new StringBuilder();
        for (int i = 0, len = fileSystem.size() ; i < len ; i++ ) {
            sb.append(fileSystem.get(i)).append(NEWLINE);
        }
        return sb.toString();
    }

    public final void setFileSystem(String s) {
        fileSystem.clear();
        if (!NONE.equalsIgnoreCase(s)) {
            for (StringTokenizer st = new StringTokenizer(s, "\n\t\r,;") ; st.hasMoreElements() ; ) {
                fileSystem.add(st.nextToken());
            }
        }
    }

    public final String getCheckFileStatus() {
        return FileStatus.toString(checkFileStatus);
    }

    public final void setCheckFileStatus(String status) {
        this.checkFileStatus = FileStatus.toInt(status);
    }

    public boolean isVerifyTar() {
        return verifyTar;
    }

    public void setVerifyTar(boolean verifyTar) {
        this.verifyTar = verifyTar;
    }
    
    public String getSkipVerifyTarHSMStati() {
        if (skipVerifyTarHSMStati.isEmpty()) {
            return NONE;
        }
        StringBuilder sb = new StringBuilder();
        Entry<Integer,Integer> e;
        for (Iterator<Entry<Integer,Integer>> it = skipVerifyTarHSMStati.entrySet().iterator() ; it.hasNext() ; ) {
            e = it.next();
            sb.append(FileStatus.toString(e.getKey()));
            if ( e.getValue()!=null) {
                int newStatus = e.getValue();
                sb.append('=').append(newStatus == Integer.MIN_VALUE ? DELETE : FileStatus.toString(newStatus));
            }
            sb.append(NEWLINE);
        }
        return sb.toString();
    }
    
    public void setSkipVerifyTarHSMStati(String s) {
        if (NONE.equals(s)) {
            skipVerifyTarHSMStati.clear();
        } else {
            HashMap<Integer,Integer> stati = new HashMap<Integer,Integer>();
            String tk;
            int pos;
            for (StringTokenizer st = new StringTokenizer(s, "\n\t\r,;") ; st.hasMoreElements() ;) {
                tk = st.nextToken();
                pos = tk.indexOf('=');
                if (pos == -1) {
                    stati.put(FileStatus.toInt(tk), null);
                } else {
                    String ns = tk.substring(pos+1);
                    int newStatus = DELETE.equals(ns) ? Integer.MIN_VALUE : FileStatus.toInt(ns);
                    stati.put(FileStatus.toInt(tk.substring(0, pos)), newStatus);
                }
                skipVerifyTarHSMStati = stati;
            }
        }
    }

    public String getInvalidTarStatus() {
        return invalidTarStatus == Integer.MIN_VALUE ? DELETE : FileStatus.toString(invalidTarStatus);
    }
    public void setInvalidTarStatus(String status) {
        this.invalidTarStatus = DELETE.equals(status) ? Integer.MIN_VALUE : FileStatus.toInt(status);
    }
    public String getNotInTarStatus() {
        return notInTarStatus == Integer.MIN_VALUE ? DELETE : FileStatus.toString(notInTarStatus);
    }
    public void setNotInTarStatus(String status) {
        this.notInTarStatus = DELETE.equals(status) ? Integer.MIN_VALUE : FileStatus.toInt(status);
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
            scheduler.stopScheduler(timerIDCheckSyncFileStatus, listenerID,
                    timerListener);
            listenerID = scheduler.startScheduler(timerIDCheckSyncFileStatus,
                    taskInterval, timerListener);
        }
    }

    public final String getMinimumFileAge() {
        return RetryIntervalls.formatInterval(minFileAge);
    }

    public final void setMinimumFileAge(String intervall) {
        this.minFileAge = RetryIntervalls.parseInterval(intervall);
    }

    public int getLimitNumberOfFilesPerTask() {
        return limitNumberOfFilesPerTask;
    }

    public void setLimitNumberOfFilesPerTask(int limit) {
        this.limitNumberOfFilesPerTask = limit;
    }

    public String getOldestCreatedTimeOfCheckFileStatus() {
        return oldestCreatedTimeOfCheckFileStatus == null ? "UNKNOWN" : 
            new SimpleDateFormat("yyyy/MM/dd hh:mm:ss").format(oldestCreatedTimeOfCheckFileStatus);
    }

    public void updateOldestCreatedTimeOfCheckFileStatus() {
        if (fileSystem.isEmpty()) {
            log.info("SyncFileStatus disabled (fileSystem=NONE)!");
        } else {
            log.info("Start updateOldestCreatedTimeOfCheckFileStatus! current:"+oldestCreatedTimeOfCheckFileStatus);
        }
        try {
            oldestCreatedTimeOfCheckFileStatus = newFileSystemMgt().minCreatedTimeOnFsWithFileStatus(this.fileSystem, this.checkFileStatus);
            if (oldestCreatedTimeOfCheckFileStatus == null) {
                nextUpdate = System.currentTimeMillis() + this.minFileAge;
                log.info("OldestCreatedTimeOfCheckFileStatus is null! -> There is no file with fileStatus="+checkFileStatus+" on filesystem="+fileSystem);
                log.info("Next update of OldestCreatedTimeOfCheckFileStatus in "+getMinimumFileAge()+" (when new files are old enough to be considered)");
            } else {
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                cal.set(Calendar.MILLISECOND, 999);
                nextUpdate = cal.getTimeInMillis();
                log.info("OldestCreatedTimeOfCheckFileStatus updated to "+oldestCreatedTimeOfCheckFileStatus+" ! Next update after midnight.");
            }
        } catch (Exception x) {
            log.warn("Update OldestCreatedTimeOfCheckFileStatus failed!", x);
        }
    }

    public final String getHSMModulServicename() {
        return hsmModuleServicename == null ? NONE : hsmModuleServicename.toString();
    }

    public final void setHSMModulServicename(String name) throws MalformedObjectNameException {
        this.hsmModuleServicename = NONE.equals(name) ? null : ObjectName.getInstance(name);
    }
    
    private boolean isDisabled(int hour) {
        if (disabledEndHour == -1)
            return false;
        boolean sameday = disabledStartHour <= disabledEndHour;
        boolean inside = hour >= disabledStartHour && hour < disabledEndHour;
        return sameday ? inside : !inside;
    }

    public boolean isRunning() {
        return isRunning;
    }
    
    protected void startService() throws Exception {
        listenerID = scheduler.startScheduler(timerIDCheckSyncFileStatus,
                taskInterval, timerListener);
    }

    protected void stopService() throws Exception {
        scheduler.stopScheduler(timerIDCheckSyncFileStatus, listenerID,
                timerListener);
        super.stopService();
    }

    public int check() throws Exception  {
        if (fileSystem.isEmpty()) {
            return 0;
        }
        if (hsmModuleServicename == null) {
            log.warn("HSM Module Servicename not configured! SyncFileStatusService disabled!");
            return 0;
        }
        synchronized(this) {
            if (isRunning) {
                log.info("SyncFileStatus is already running!");
                return -1;
            }
            isRunning = true;
        }
        try {
            
            if (oldestCreatedTimeOfCheckFileStatus == null || System.currentTimeMillis() > nextUpdate) {
                this.updateOldestCreatedTimeOfCheckFileStatus();
            }
            if (oldestCreatedTimeOfCheckFileStatus == null) {
               log.info("OldestCreatedTimeOfCheckFileStatus is null! SyncFileStatus skipped!");
               return 0;
            }
            log.info("Start SyncFileStatus!");
            FileSystemMgt2 fsmgt = newFileSystemMgt();
            FileDTO[] c = fsmgt.findFilesByStatusAndFileSystem(fileSystem, checkFileStatus, this.oldestCreatedTimeOfCheckFileStatus,
                    new Timestamp(System.currentTimeMillis() - minFileAge), limitNumberOfFilesPerTask);
            if (log.isDebugEnabled()) log.debug("found "+c.length+" files to check status.");
            if (c.length == 0) {
                return 0;
            }
            int count = 0;
            HashMap<String, Integer> checkedTarsStatus = new HashMap<String, Integer>();
            HashMap<String, Map<String, byte[]>> checkedTarsMD5 = verifyTar ? new HashMap<String, Map<String, byte[]>>() : null;
            ArrayList<Long> failedFilesystems = new ArrayList<Long>();
            for (int i = 0; i < c.length; i++) {
                if (failedFilesystems.contains(c[i].getFileSystemPk()))
                        continue;
                try {
                    if (check(fsmgt, c[i], checkedTarsStatus, checkedTarsMD5))
                        ++count;
                } catch (HSMException x) {
                    if (x.getErrorLevel() == HSMException.ERROR_ON_FILESYSTEM_LEVEL) {
                        failedFilesystems.add(c[i].getFileSystemPk());
                        log.warn("Check of file "+c[i].getFilePath()+" failed on filesystem level! skip all files of "+
                                c[i].getDirectoryPath()+" for this request!");
                    }
                } catch (Exception x) {
                    log.warn("Check of file "+c[i].getFilePath()+" failed! skipped!");
                }
            }
            log.info("SyncFileStatus finished! changed files:"+count);
            return count;
        } finally {
            isRunning = false;
        }
    }

    private boolean check(FileSystemMgt2 fsmgt, FileDTO fileDTO,
            HashMap<String, Integer> checkedTarsStatus, HashMap<String, Map<String, byte[]>> checkedTarsMD5) throws IOException, VerifyTarException, HSMException {
        String fsId = fileDTO.getDirectoryPath();
        String filePath = fileDTO.getFilePath();
        String tarPathKey = null;
        Integer status, tarStatus;
        if (fsId.startsWith("tar:")) {
            String tarfilePath = filePath.substring(0, filePath.indexOf('!'));
            tarPathKey = fsId.substring(4) + '/' + tarfilePath;
            status = (Integer) checkedTarsStatus.get(tarPathKey);
            tarStatus = null;
            if (status == null) {
                status = queryHSM(fsId, tarfilePath, fileDTO.getUserInfo());
                checkedTarsStatus.put(tarPathKey, status);
                if (verifyTar) {
                    if (checkSkipVerifyTarStatus(status, fileDTO)) {
                        tarStatus = skipVerifyTarHSMStati.get(status);
                    } else {
                        tarStatus = verifyTar(fileDTO, tarPathKey, checkedTarsMD5);
                    }
                }
            } else if (verifyTar) {
                if (checkSkipVerifyTarStatus(status, fileDTO)) {
                    tarStatus = skipVerifyTarHSMStati.get(status);
                } else {
                    tarStatus = checkFileInTar(fileDTO, tarPathKey, checkedTarsMD5.get(tarPathKey));
                }
            }
            if (tarStatus != null) {
                status = tarStatus;
            }
        } else {
            status = queryHSM(fsId, filePath, fileDTO.getUserInfo());
        }
        return status == null ? false : status == Integer.MIN_VALUE ? 
                true : updateFileStatus(fsmgt, fileDTO, status);
    }

    private boolean checkSkipVerifyTarStatus(Integer status, FileDTO dto) {
       if (skipVerifyTarHSMStati.containsKey(status)) {
           Integer tarStatus = skipVerifyTarHSMStati.get(status);
           if (tarStatus != null && tarStatus == Integer.MIN_VALUE) {
               log.error("Delete file entity for skipVerifyTarHSMStati "+FileStatus.toString(status)+" mapped to DELETE:"+dto.getFilePath());
               deleteFileOnTarFS(dto);
           }
           return true;
       }
       return false;
    }

    private Integer verifyTar(FileDTO dto, String tarPathKey, HashMap<String, Map<String, byte[]>> checkedTarsMD5) {
        log.info("Verify tar file "+tarPathKey);
        String filePath = dto.getFilePath();
        String tarfilePath = filePath.substring(0, filePath.indexOf('!'));
        Map<String, byte[]> entries = null;
        if (checkedTarsMD5.containsKey(tarPathKey)) {
            entries = checkedTarsMD5.get(tarPathKey);
            if (log.isDebugEnabled()) log.debug("entries of checked tar file "+tarPathKey+" :"+entries);
        } else {
            File tarFile = null;
            try {
                tarFile = fetchTarFile(dto.getDirectoryPath(), tarfilePath);
                entries = VerifyTar.verify(tarFile, buf);
            } catch (Exception x) {
                log.error("Verification of tar file "+tarPathKey+" failed! Reason:"+x.getMessage());
                if (invalidTarStatus == Integer.MIN_VALUE) {
                    try {
                        log.error("Delete file entities of invalid tar file :"+tarfilePath);
                        newFileSystemMgt().deleteFilesOfInvalidTarFile(dto.getDirectoryPath(), tarfilePath);
                    } catch (Exception e) {
                        log.error("Failed to delete files of invalid tar file! tarFile:"+tarfilePath);
                    }
                }
            } finally {
                if (tarFile != null) {
                    fetchTarFileFinished(dto.getDirectoryPath(), tarfilePath, tarFile);
                }
            }
            checkedTarsMD5.put(tarPathKey, entries);
        }
        return checkFileInTar(dto, tarPathKey, entries);
    }

    private Integer checkFileInTar(FileDTO dto, String tarPathKey, Map<String, byte[]> entries) {
        String filePath = dto.getFilePath();
        String filepathInTar = filePath.substring(filePath.indexOf('!')+1);
        if (entries == null) {
            log.error("TAR file "+tarPathKey+" not valid -> " + (invalidTarStatus == Integer.MIN_VALUE ?
                    "File is deleted!" : "set status to "+FileStatus.toString(invalidTarStatus)));
            return invalidTarStatus;
        }
        byte[] md5 = entries.get(filepathInTar);
        if (md5 == null) {
            log.error("Tar File "+tarPathKey+" does NOT contain File "+filepathInTar);
            if (notInTarStatus == Integer.MIN_VALUE) {
                log.error("Delete file entity that is missing in tar file:"+filePath);
                deleteFileOnTarFS(dto);
            } else {
                log.error("Set file status to "+FileStatus.toString(notInTarStatus));
            }
            return notInTarStatus;
        } else {
            if (!Arrays.equals(dto.getFileMd5(), md5)) {
                log.error("Tar File "+tarPathKey+": MD5 of File "+filepathInTar+" is NOT equal to MD5 of file entity! ("+
                        MD5.toString(md5)+" vs. "+dto.getMd5String()+ ")");
                return invalidTarStatus;
            }
        }
        return null;
    }

    private void deleteFileOnTarFS(FileDTO dto) {
        try {
            newFileSystemMgt().deleteFileOnTarFs(dto.getDirectoryPath(), dto.getPk());
        } catch (Exception e) {
            log.error("Failed to delete file entity! filePath:"+dto.getFilePath());
        }
    }
    
    private File fetchTarFile(String fsID, String tarPath) throws Exception {
        try {
            return (File) server.invoke(tarRetrieverName, "fetchTarFile",
                    new Object[] { fsID, tarPath }, new String[] {
                            String.class.getName(), String.class.getName() });
        } catch (InstanceNotFoundException e) {
            throw new ConfigurationException(e.getMessage(), e);
        } catch (MBeanException e) {
            throw e.getTargetException();
        } catch (ReflectionException e) {
            throw new ConfigurationException(e.getMessage(), e);
        }
    }
    
    private void fetchTarFileFinished(String fsID, String tarPath, File tarFile) {
        try {
            String tarRetrieverHSMModule = (String) server.getAttribute(tarRetrieverName, "HSMModulServicename");
            if (!NONE.equals(tarRetrieverHSMModule)) {
                server.invoke(new ObjectName(tarRetrieverHSMModule), "fetchHSMFileFinished", new Object[]{fsID, tarPath, tarFile}, 
                        new String[]{String.class.getName(),String.class.getName(),File.class.getName()});
            }
        } catch (Exception x) {
            log.warn("fetchHSMFileFinished failed! fsID:"+fsID+" tarPath:"+tarPath+" tarFile:"+tarFile, x);
        }
    }

    private boolean updateFileStatus(FileSystemMgt2 fsmgt, FileDTO fileDTO,
            int status) {
        if (fileDTO.getFileStatus() != status) {
            log.info("Change status of " + fileDTO + " to " + status);
            try {
                fsmgt.setFileStatus(fileDTO.getPk(), status, fileSystem);
                return true;
            } catch (Exception e) {
                log.error("Failed to update status of file " + fileDTO, e);
            }
        }
        return false;
    }

    private Integer queryHSM(String fsID, String filePath, String userInfo) throws IOException, HSMException {
        try {
            return (Integer) server.invoke(hsmModuleServicename, 
                    "queryStatus", new Object[]{fsID, filePath, userInfo}, 
                    new String[]{String.class.getName(),String.class.getName(),String.class.getName()});
        } catch (Exception x) {
            log.error("queryHSM failed! fsID:"+fsID+" filePath:"+
                    filePath+" userInfo:"+userInfo, x);
            if (x.getCause() != null && (x.getCause() instanceof HSMException))
                throw (HSMException)x.getCause();
            IOException iox = new IOException("Query status of HSMFile failed!");
            iox.initCause(x);
            throw iox;
        }
    }

    protected FileSystemMgt2 newFileSystemMgt() throws Exception {
        return ((FileSystemMgt2Home) EJBHomeFactory.getFactory().lookup(
                FileSystemMgt2Home.class, FileSystemMgt2Home.JNDI_NAME)).create();
    }

    public String getTimerIDCheckSyncFileStatus() {
        return timerIDCheckSyncFileStatus;
    }

    public void setTimerIDCheckSyncFileStatus(String timerIDCheckSyncFileStatus) {
        this.timerIDCheckSyncFileStatus = timerIDCheckSyncFileStatus;
    }
 
    public int syncArchivedStatusOfInstances(String fsID, String limitStr) throws Exception {
        if (fsID == null || fsID.trim().length() < 1)
            return 0;
        int limit = (limitStr == null || limitStr.trim().length() < 1) ? 1000 : Integer.parseInt(limitStr);
        return newFileSystemMgt().syncArchivedFlag(fsID, limit < 1 ? 1000 : limit);
    }
}
