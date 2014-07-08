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
 * Fuad Ibrahimov <fuad@ibrahimov.de>
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

package org.dcm4chex.archive.dcm.storescp;

import java.io.File;
import java.net.Socket;
import java.rmi.RemoteException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Map.Entry;

import javax.ejb.CreateException;
import javax.ejb.FinderException;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.apache.log4j.Logger;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.FileMetaInfo;
import org.dcm4che.data.PersonName;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.net.AcceptorPolicy;
import org.dcm4che.net.DcmServiceException;
import org.dcm4che.net.DcmServiceRegistry;
import org.dcm4che2.audit.message.AuditMessage;
import org.dcm4che2.audit.message.InstancesTransferredMessage;
import org.dcm4che2.audit.message.ParticipantObject;
import org.dcm4che2.audit.message.ParticipantObjectDescription;
import org.dcm4che2.audit.message.PatientRecordMessage;
import org.dcm4che2.audit.util.InstanceSorter;
import org.dcm4cheri.util.StringUtils;
import org.dcm4chex.archive.common.PrivateTags;
import org.dcm4chex.archive.common.SeriesStored;
import org.dcm4chex.archive.config.CompressionRules;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.dcm.AbstractScpService;
import org.dcm4chex.archive.ejb.interfaces.AEDTO;
import org.dcm4chex.archive.ejb.interfaces.AEManager;
import org.dcm4chex.archive.ejb.interfaces.FileDTO;
import org.dcm4chex.archive.ejb.interfaces.FileSystemDTO;
import org.dcm4chex.archive.ejb.interfaces.Storage;
import org.dcm4chex.archive.ejb.interfaces.StorageHome;
import org.dcm4chex.archive.exceptions.UnknownAETException;
import org.dcm4chex.archive.mbean.FileSystemMgt2Delegate;
import org.dcm4chex.archive.mbean.HttpUserInfo;
import org.dcm4chex.archive.mbean.SchedulerDelegate;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.dcm4chex.archive.util.FileUtils;
import org.dcm4chex.archive.util.HomeFactoryException;


/**
 * @author Gunter.Zeilinger@tiani.com
 * @version $Revision: 18249 $ $Date:: 2014-02-21#$
 * @since 03.08.2003
 */

public class StoreScpService extends AbstractScpService {

    public static final String EVENT_TYPE_OBJECT_STORED = 
            "org.dcm4chex.archive.dcm.storescp";
    private static final String NEWLINE = System.getProperty("line.separator", "\n");

    public static final NotificationFilter NOTIF_FILTER = 
            new NotificationFilter() {
        private static final long serialVersionUID = -7557458153348143439L;

        public boolean isNotificationEnabled(Notification notif) {
            return EVENT_TYPE_OBJECT_STORED.equals(notif.getType());
        }
    };
    public static final String EVENT_TYPE_NEW_STUDY = 
        "org.dcm4chex.archive.dcm.storescp#newStudy";

    public static final NotificationFilter NOTIF_FILTER_NEW_STUDY = 
            new NotificationFilter() {
        private static final long serialVersionUID = -7557458153348143439L;
    
        public boolean isNotificationEnabled(Notification notif) {
            return EVENT_TYPE_NEW_STUDY.equals(notif.getType());
        }
    };

    private final SchedulerDelegate scheduler = new SchedulerDelegate(this);

    private final NotificationListener checkPendingSeriesStoredListener = new NotificationListener() {
        public void handleNotification(Notification notif, Object handback) {
            synchronized(this) {
                if (isRunning) {
                    log.info("checkPendingSeriesStored is already running!");
                    return;
                }
                isRunning = true;
            }
            new Thread(new Runnable(){
                public void run() {
                    try {
                        log.debug("Check for Pending Series Stored");
                        checkPendingSeriesStored();
                    } catch (Exception e) {
                        log.error("Check for Pending Series Stored failed:", e);
                    } finally {
                        isRunning = false;
                    }
                }}).start();
        }
    };

    private Integer listenerID;
    private long checkPendingSeriesStoredInterval;
    private Map<String,Long> seriesStoredNotificationDelays = new HashMap<String,Long>();
    private String defFileSystemGroupID;

    /**
     * Map containing accepted Image SOP Class UID. key is name (as in config
     * string), value is real uid)
     */
    private Map imageCUIDS = new LinkedHashMap();

    /**
     * Map containing accepted Image Transfer Syntax UIDs. key is name (as in
     * config string), value is real uid)
     */
    private Map imageTSUIDS = new LinkedHashMap();

    /**
     * Map containing accepted Waveform SOP Class UID. key is name (as in config
     * string), value is real uid)
     */
    private Map waveformCUIDS = new LinkedHashMap();

    /**
     * Map containing accepted Waveform Transfer Syntax UIDs. key is name (as in
     * config string), value is real uid)
     */
    private Map waveformTSUIDS = new LinkedHashMap();

    /**
     * Map containing accepted Video SOP Class UID. key is name (as in config
     * string), value is real uid)
     */
    private Map videoCUIDS = new LinkedHashMap();

    /**
     * Map containing accepted Video Transfer Syntax UIDs. key is name (as in
     * config string), value is real uid)
     */
    private Map videoTSUIDS = new LinkedHashMap();

    /**
     * Map containing accepted SR SOP Class UID. key is name (as in config
     * string), value is real uid)
     */
    private Map srCUIDS = new LinkedHashMap();

    /**
     * Map containing accepted SR Transfer Syntax UIDs. key is name (as in
     * config string), value is real uid)
     */
    private Map srTSUIDS = new LinkedHashMap();

    /**
     * Map containing accepted other SOP Class UIDs. key is name (as in config
     * string), value is real uid)
     */
    private Map otherCUIDS = new LinkedHashMap();

    private String timerIDCheckPendingSeriesStored;
    private boolean isRunning;

    private String[] unrestrictedAppendPermissionsToAETitles;

    private FileSystemMgt2Delegate fsmgt = new FileSystemMgt2Delegate(this);
    private ObjectName mwlScuServiceName;

    private static final int MIN_MAX_VALLEN = 0x10000; // 64K

    private int maxValueLength = Integer.MAX_VALUE;

    private int bufferSize = 8192;

    private boolean md5sum = true;

    private boolean syncFileBeforeCStoreRSP = true;

    private boolean syncFileAfterCStoreRSP = false;

    private boolean storeOriginalPatientIDInOtherPatientIDsSeq;

    private boolean storeOriginalPatientIDInOriginalAttrsSeq;
    
    private StoreScp scp = null;

    public StoreScpService() {
        scp = createScp();
    }
    
    protected StoreScp createScp() {
        return new StoreScp(this);
    }
    
    protected StoreScp getScp() {
        return scp;
    }

    public String getDefaultFileSystemGroupID() {
        return defFileSystemGroupID;
    }

    public void setDefaultFileSystemGroupID(String defFileSystemGroupID) {
        this.defFileSystemGroupID = defFileSystemGroupID;
    }

    public final String getCheckPendingSeriesStoredInterval() {
        return RetryIntervalls
                .formatInterval(checkPendingSeriesStoredInterval);
    }

    public void setCheckPendingSeriesStoredInterval(String interval)
            throws Exception {
        long oldInterval = checkPendingSeriesStoredInterval;
        checkPendingSeriesStoredInterval = RetryIntervalls
                .parseInterval(interval);
        if (getState() == STARTED
                && oldInterval != checkPendingSeriesStoredInterval) {
            stopSeriesStoredScheduler();
            startSeriesStoredScheduler();
        }
    }

    public boolean isRunning() {
        return isRunning;
    }
            
    public final String getSeriesStoredNotificationDelay() {
        String defaultDelay = null;
        StringBuilder sb = new StringBuilder();
        for (Entry<String, Long> delay : seriesStoredNotificationDelays.entrySet()) {
            if (delay.getKey() == null) {
                defaultDelay = RetryIntervalls.formatInterval(delay.getValue());
            } else {
                sb.append(delay.getKey()).append(":")
                .append(RetryIntervalls.formatInterval(delay.getValue())).append(NEWLINE);
                
            }
        }
        if (defaultDelay != null) {
            sb.append(defaultDelay).append(NEWLINE);
        }
        return sb.toString();
    }

    public void setSeriesStoredNotificationDelay(String s) {
        HashMap<String,Long> delays = new HashMap<String,Long>();
        String aet, delay;
        int pos;
        for (StringTokenizer st = new StringTokenizer(s, "\n\t\r,;") ; st.hasMoreElements() ; ) {
            aet = null;
            delay = st.nextToken();
            pos = delay.indexOf(':');
            if (pos != -1) {
                aet = delay.substring(0,pos);
                delay = delay.substring(++pos);
            }
            delays.put(aet, RetryIntervalls.parseInterval(delay));
        }
        if (delays.containsKey(null)) {
            seriesStoredNotificationDelays = delays;
        } else {
            throw new IllegalArgumentException("Missing default SeriesStored delay!");
        }
    }

    public final boolean isMd5sum() {
        return md5sum;
    }

    public final void setMd5sum(boolean md5sum) {
        this.md5sum = md5sum;
    }

    public final void setSyncFileBeforeCStoreRSP(boolean syncFileBeforeCStoreRSP) {
        this.syncFileBeforeCStoreRSP = syncFileBeforeCStoreRSP;
    }

    public final boolean isSyncFileBeforeCStoreRSP() {
        return syncFileBeforeCStoreRSP;
    }

    public final void setSyncFileAfterCStoreRSP(boolean syncFileAfterCStoreRSP) {
        this.syncFileAfterCStoreRSP = syncFileAfterCStoreRSP;
    }

    public final boolean isSyncFileAfterCStoreRSP() {
        return syncFileAfterCStoreRSP;
    }

    public final boolean isStoreOriginalPatientIDInOtherPatientIDsSeq() {
        return storeOriginalPatientIDInOtherPatientIDsSeq;
    }

    public final void setStoreOriginalPatientIDInOtherPatientIDsSeq(
            boolean storeOriginalPatientIDInOtherPatientIDsSeq) {
        this.storeOriginalPatientIDInOtherPatientIDsSeq = storeOriginalPatientIDInOtherPatientIDsSeq;
    }
    
    public final boolean isStoreOriginalPatientIDInOriginalAttrsSeq() {
    	return storeOriginalPatientIDInOriginalAttrsSeq;
    }
    
    public final void setStoreOriginalPatientIDInOriginalAttrsSeq(
    		boolean storeOriginalPatientIDInOriginalAttrsSeq) {
    	this.storeOriginalPatientIDInOriginalAttrsSeq = storeOriginalPatientIDInOriginalAttrsSeq;
    }

    public int getMaxValueLength() {
        return maxValueLength;
    }

    public void setMaxValueLength(int maxValueLength) {
        if (maxValueLength < MIN_MAX_VALLEN)
            throw new IllegalArgumentException("maxValueLength: "
                    + maxValueLength + " < " + MIN_MAX_VALLEN);
        this.maxValueLength = maxValueLength;
    }

    public final int getBufferSize() {
        return bufferSize;
    }

    public final void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public final boolean isStudyDateInFilePath() {
        return scp.isStudyDateInFilePath();
    }

    public final void setStudyDateInFilePath(boolean enable) {
        scp.setStudyDateInFilePath(enable);
    }
    
    public final boolean isSourceAETInFilePath() {
        return scp.isSourceAETInFilePath();
    }

    public final void setSourceAETInFilePath(boolean enable) {
        scp.setSourceAETInFilePath(enable);
    }

    public final boolean isYearInFilePath() {
        return scp.isYearInFilePath();
    }

    public final void setYearInFilePath(boolean enable) {
        scp.setYearInFilePath(enable);
    }

    public final boolean isMonthInFilePath() {
        return scp.isMonthInFilePath();
    }

    public final void setMonthInFilePath(boolean enable) {
        scp.setMonthInFilePath(enable);
    }

    public final boolean isDayInFilePath() {
        return scp.isDayInFilePath();
    }

    public final void setDayInFilePath(boolean enable) {
        scp.setDayInFilePath(enable);
    }

    public final boolean isHourInFilePath() {
        return scp.isHourInFilePath();
    }

    public final void setHourInFilePath(boolean enable) {
        scp.setHourInFilePath(enable);
    }

    public final String getReferencedDirectoryPath() {
        return scp.getReferencedDirectoryPath();
    }

    public final void setReferencedDirectoryPath(String pathOrURI) {
        scp.setReferencedDirectoryPath(pathOrURI);
    }

    public String getReferencedFileSystemGroupID() {
        return scp.getReferencedFileSystemGroupID();
    }

    public void setReferencedFileSystemGroupID(String groupID) {
        scp.setReferencedFileSystemGroupID(groupID);
    }

    public final boolean isReadReferencedFile() {
        return scp.isReadReferencedFile();
    }

    public final void setReadReferencedFile(boolean readReferencedFile) {
        scp.setReadReferencedFile(readReferencedFile);
    }

    public final boolean isMd5sumReferencedFile() {
        return scp.isMd5sumReferencedFile();
    }

    public final void setMd5sumReferencedFile(boolean md5ReferencedFile) {
        scp.setMd5sumReferencedFile(md5ReferencedFile);
    }

    public final boolean isAcceptMissingPatientID() {
        return scp.isAcceptMissingPatientID();
    }

    public final void setAcceptMissingPatientID(boolean accept) {
        scp.setAcceptMissingPatientID(accept);
    }

    public final boolean isAcceptMissingPatientName() {
        return scp.isAcceptMissingPatientName();
    }

    public final void setAcceptMissingPatientName(boolean accept) {
        scp.setAcceptMissingPatientName(accept);
    }

    public final boolean isSerializeDBUpdate() {
        return scp.isSerializeDBUpdate();
    }

    public final void setSerializeDBUpdate(boolean serialize) {
        scp.setSerializeDBUpdate(serialize);
    }

    public ObjectName getSchedulerServiceName() {
        return scheduler.getSchedulerServiceName();
    }

    public void setSchedulerServiceName(ObjectName schedulerServiceName) {
        scheduler.setSchedulerServiceName(schedulerServiceName);
    }

    public String getFileSystemMgtServiceNamePrefix() {
        return fsmgt.getFileSystemMgtServiceNamePrefix();
    }

    public void setFileSystemMgtServiceNamePrefix(String prefix) {
        fsmgt.setFileSystemMgtServiceNamePrefix(prefix);
    }

    public final ObjectName getMwlScuServiceName() {
        return mwlScuServiceName;
    }

    public final void setMwlScuServiceName(ObjectName mwlScuServiceName) {
        this.mwlScuServiceName = mwlScuServiceName;
    }

    public final String getUnrestrictedAppendPermissionsToAETitles() {
        return unrestrictedAppendPermissionsToAETitles == null ? ANY
                : StringUtils.toString(unrestrictedAppendPermissionsToAETitles,
                        '\\');
    }

    public final void setUnrestrictedAppendPermissionsToAETitles(String s) {
        String trim = s.trim();
        this.unrestrictedAppendPermissionsToAETitles = trim
                .equalsIgnoreCase(ANY) ? null : StringUtils.split(trim, '\\');
    }

    final boolean hasUnrestrictedAppendPermissions(String aet) {
        return unrestrictedAppendPermissionsToAETitles == null
                || Arrays.asList(unrestrictedAppendPermissionsToAETitles)
                        .contains(aet);
    }

    public String getCoerceWarnCallingAETs() {
        return scp.getCoerceWarnCallingAETs();
    }

    public void setCoerceWarnCallingAETs(String aets) {
        scp.setCoerceWarnCallingAETs(aets);
    }

    public final boolean isCoerceBeforeWrite() {
        return scp.isCoerceBeforeWrite();
    }

    public final void setCoerceBeforeWrite(boolean CoerceBeforeWrite) {
        scp.setCoerceBeforeWrite(CoerceBeforeWrite);
    }
    
    public String getAcceptMismatchIUIDCallingAETs() {
        return scp.getAcceptMismatchIUIDCallingAETs();
    }

    public void setAcceptMismatchIUIDCallingAETs(String aets) {
        scp.setAcceptMismatchIUIDCallingAETs(aets);
    }

    public final String getOnlyWellKnownInstancesCallingAETs() {
        return scp.getOnlyWellKnownInstancesCallingAETs();
    }

    public final void setOnlyWellKnownInstancesCallingAETs(String aets) {
        scp.setOnlyWellKnownInstancesCallingAETs(aets);
    }

    public boolean isStoreDuplicateIfDiffHost() {
        return scp.isStoreDuplicateIfDiffHost();
    }

    public void setStoreDuplicateIfDiffHost(boolean storeDuplicate) {
        scp.setStoreDuplicateIfDiffHost(storeDuplicate);
    }

    public boolean isStoreDuplicateIfDiffMD5() {
        return scp.isStoreDuplicateIfDiffMD5();
    }

    public void setStoreDuplicateIfDiffMD5(boolean storeDuplicate) {
        scp.setStoreDuplicateIfDiffMD5(storeDuplicate);
    }

    public String getAllowDuplicateForBetterAvailabilityForAET() {
        return scp.getAllowDuplicateForBetterAvailabilityForAET();
    }

    public void setAllowDuplicateForBetterAvailabilityForAET(String aet) {
        scp.setAllowDuplicateForBetterAvailabilityForAET(aet);
    }

    public boolean isDontStoreDuplicateIfFromExternalRetrieveAET() {
        return scp.isDontStoreDuplicateIfFromExternalRetrieveAET();
    }

    public void setDontStoreDuplicateIfFromExternalRetrieveAET(boolean b) {
        scp.setDontStoreDuplicateIfFromExternalRetrieveAET(b);
    }

    public final String getCompressionRules() {
        return scp.getCompressionRules().toString();
    }

    public void setCompressionRules(String rules) {
        scp.setCompressionRules(new CompressionRules(rules));
    }

    public final int getUpdateDatabaseMaxRetries() {
        return scp.getUpdateDatabaseMaxRetries();
    }

    public final void setUpdateDatabaseMaxRetries(int updateDatabaseMaxRetries) {
        scp.setUpdateDatabaseMaxRetries(updateDatabaseMaxRetries);
    }

    public final int getMaxCountUpdateDatabaseRetries() {
        return scp.getMaxCountUpdateDatabaseRetries();
    }

    public final void resetMaxCountUpdateDatabaseRetries() {
        scp.setMaxCountUpdateDatabaseRetries(0);
    }

    public final long getUpdateDatabaseRetryInterval() {
        return scp.getUpdateDatabaseRetryInterval();
    }

    public final void setUpdateDatabaseRetryInterval(long interval) {
        scp.setUpdateDatabaseRetryInterval(interval);
    }

    public String getAcceptedImageSOPClasses() {
        return toString(imageCUIDS);
    }

    public void setAcceptedImageSOPClasses(String s) {
        updateAcceptedSOPClass(imageCUIDS, s, scp);
    }

    public String getAcceptedTransferSyntaxForImageSOPClasses() {
        return toString(imageTSUIDS);
    }

    public void setAcceptedTransferSyntaxForImageSOPClasses(String s) {
        updateAcceptedTransferSyntax(imageTSUIDS, s);
    }

    public String getAcceptedVideoSOPClasses() {
        return toString(videoCUIDS);
    }

    public void setAcceptedVideoSOPClasses(String s) {
        updateAcceptedSOPClass(videoCUIDS, s, scp);
    }

    public String getAcceptedTransferSyntaxForVideoSOPClasses() {
        return toString(videoTSUIDS);
    }

    public void setAcceptedTransferSyntaxForVideoSOPClasses(String s) {
        updateAcceptedTransferSyntax(videoTSUIDS, s);
    }

    public String getAcceptedSRSOPClasses() {
        return toString(srCUIDS);
    }

    public void setAcceptedSRSOPClasses(String s) {
        updateAcceptedSOPClass(srCUIDS, s, scp);
    }

    public String getAcceptedTransferSyntaxForSRSOPClasses() {
        return toString(srTSUIDS);
    }

    public void setAcceptedTransferSyntaxForSRSOPClasses(String s) {
        updateAcceptedTransferSyntax(srTSUIDS, s);
    }

    public String getAcceptedWaveformSOPClasses() {
        return toString(waveformCUIDS);
    }

    public void setAcceptedWaveformSOPClasses(String s) {
        updateAcceptedSOPClass(waveformCUIDS, s, scp);
    }

    public String getAcceptedTransferSyntaxForWaveformSOPClasses() {
        return toString(waveformTSUIDS);
    }

    public void setAcceptedTransferSyntaxForWaveformSOPClasses(String s) {
        updateAcceptedTransferSyntax(waveformTSUIDS, s);
    }

    public String getAcceptedOtherSOPClasses() {
        return toString(otherCUIDS);
    }

    public void setAcceptedOtherSOPClasses(String s) {
        updateAcceptedSOPClass(otherCUIDS, s, scp);
    }

    protected String[] getCUIDs() {
        return valuesToStringArray(otherCUIDS);
    }

    /**
     * @return Returns the checkIncorrectWorklistEntry.
     */
    public boolean isCheckIncorrectWorklistEntry() {
        return scp.isCheckIncorrectWorklistEntry();
    }

    /**
     * Enable/disable check if an MPPS with Discontinued reason 'Incorrect
     * worklist selected' is referenced.
     * 
     * @param check
     *                The checkIncorrectWorklistEntry to set.
     */
    public void setCheckIncorrectWorklistEntry(boolean check) {
        scp.setCheckIncorrectWorklistEntry(check);
    }

    public String getTimerIDCheckPendingSeriesStored() {
        return timerIDCheckPendingSeriesStored;
    }

    public void setTimerIDCheckPendingSeriesStored(
            String timerIDCheckPendingSeriesStored) {
        this.timerIDCheckPendingSeriesStored = timerIDCheckPendingSeriesStored;
    }

    public final ObjectName getPerfMonServiceName() {
        return scp.getPerfMonServiceName();
    }

    public final void setPerfMonServiceName(ObjectName perfMonServiceName) {
        scp.setPerfMonServiceName(perfMonServiceName);
    }

    protected void startService() throws Exception {
        super.startService();
        startSeriesStoredScheduler();
    }

    protected void startSeriesStoredScheduler() throws Exception {
        listenerID = scheduler.startScheduler(timerIDCheckPendingSeriesStored,
                checkPendingSeriesStoredInterval,
                checkPendingSeriesStoredListener);
    }

    protected void stopService() throws Exception {
        stopSeriesStoredScheduler();
        super.stopService();
    }

    protected void stopSeriesStoredScheduler() throws Exception {
        scheduler.stopScheduler(timerIDCheckPendingSeriesStored, listenerID,
                checkPendingSeriesStoredListener);
    }

    protected void bindDcmServices(DcmServiceRegistry services) {
        bindAll(valuesToStringArray(imageCUIDS), scp);
        bindAll(valuesToStringArray(videoCUIDS), scp);
        bindAll(valuesToStringArray(srCUIDS), scp);
        bindAll(valuesToStringArray(waveformCUIDS), scp);
        bindAll(valuesToStringArray(otherCUIDS), scp);
        dcmHandler.addAssociationListener(scp);
    }

    protected void unbindDcmServices(DcmServiceRegistry services) {
        unbindAll(valuesToStringArray(imageCUIDS));
        unbindAll(valuesToStringArray(videoCUIDS));
        unbindAll(valuesToStringArray(srCUIDS));
        unbindAll(valuesToStringArray(waveformCUIDS));
        unbindAll(valuesToStringArray(otherCUIDS));
        dcmHandler.removeAssociationListener(scp);
    }

    protected void enablePresContexts(AcceptorPolicy policy) {
        String[] cuids;
        putPresContexts(policy, cuids = valuesToStringArray(imageCUIDS),
                valuesToStringArray(imageTSUIDS));
        putRoleSelections(policy, cuids, true, true);
        putPresContexts(policy, cuids = valuesToStringArray(videoCUIDS),
                valuesToStringArray(videoTSUIDS));
        putRoleSelections(policy, cuids, true, true);
        putPresContexts(policy, cuids = valuesToStringArray(srCUIDS),
                valuesToStringArray(srTSUIDS));
        putRoleSelections(policy, cuids, true, true);
        putPresContexts(policy, cuids = valuesToStringArray(waveformCUIDS),
                valuesToStringArray(waveformTSUIDS));
        putRoleSelections(policy, cuids, true, true);
        putPresContexts(policy, cuids = valuesToStringArray(otherCUIDS),
                valuesToStringArray(tsuidMap));
        putRoleSelections(policy, cuids, true, true);
    }

    protected void disablePresContexts(AcceptorPolicy policy) {
        String[] cuids;
        putPresContexts(policy, cuids = valuesToStringArray(imageCUIDS), null);
        removeRoleSelections(policy, cuids);
        putPresContexts(policy, cuids = valuesToStringArray(videoCUIDS), null);
        removeRoleSelections(policy, cuids);
        putPresContexts(policy, cuids = valuesToStringArray(srCUIDS), null);
        removeRoleSelections(policy, cuids);
        putPresContexts(policy, cuids = valuesToStringArray(waveformCUIDS), null);
        removeRoleSelections(policy, cuids);
        putPresContexts(policy, cuids = valuesToStringArray(otherCUIDS), null);
        removeRoleSelections(policy, cuids);
    }
    
    public FileSystemDTO selectStorageFileSystem(String fsgrpID)
            throws DcmServiceException {
        try {
            FileSystemDTO fsDTO = fsmgt.selectStorageFileSystem(fsgrpID);
            if (fsDTO == null)
                throw new DcmServiceException(Status.OutOfResources);
            return fsDTO;
        } catch (Exception e) {
            throw new DcmServiceException(Status.ProcessingFailure, e);
        }
    }

    public FileDTO makeFile(String fileSystemGroupID, Dataset dataset) 
            throws Exception {
        FileSystemDTO fsDTO = selectStorageFileSystem(fileSystemGroupID);
        File baseDir = FileUtils.toFile(fsDTO.getDirectoryPath());
        File file = scp.makeFile(baseDir, dataset, null);
        String filePath = file.getPath().substring(
                baseDir.getPath().length() + 1)
                .replace(File.separatorChar, '/');
        FileDTO fileDTO = new FileDTO();
        fileDTO.setFileSystemPk(fsDTO.getPk());
        fileDTO.setAvailability(fsDTO.getAvailability());
        fileDTO.setDirectoryPath(fsDTO.getDirectoryPath());
        fileDTO.setFilePath(filePath);
        return fileDTO;
    }

    public void logInstancesStored(Socket s, SeriesStored seriesStored) {
        try {
            InstanceSorter sorter = new InstanceSorter();
            Dataset ian = seriesStored.getIAN();
            String suid = ian.getString(Tags.StudyInstanceUID);
            Dataset series = ian.getItem(Tags.RefSeriesSeq);
            DcmElement refSops = series.get(Tags.RefSOPSeq);
            for (int i = 0, n = refSops.countItems(); i < n; i++) {
                final Dataset refSop = refSops.getItem(i);
                sorter.addInstance(suid, refSop
                        .getString(Tags.RefSOPClassUID), refSop
                        .getString(Tags.RefSOPInstanceUID), null);
            }
            InstancesTransferredMessage msg = new InstancesTransferredMessage(
                    InstancesTransferredMessage.CREATE);
            String srcAET = seriesStored.getSourceAET();
            String srcHost = s != null ? AuditMessage.hostNameOf(s
                    .getInetAddress()) : null;
            String srcID = srcHost != null ? srcHost : srcAET;
            msg.addSourceProcess(srcID, new String[] { srcAET }, null,
                    srcHost, true);
            msg.addDestinationProcess(AuditMessage.getProcessID(),
                    calledAETs, AuditMessage.getProcessName(), AuditMessage
                            .getLocalHostName(), false);
            msg.addPatient(seriesStored.getPatientID(),
                    formatPN(seriesStored.getPatientName()));
            String accno = seriesStored.getAccessionNumber();
            Dataset pps = ian.getItem(Tags.RefPPSSeq);
            ParticipantObjectDescription desc = new ParticipantObjectDescription();
            if (accno != null && accno.length() != 0) {
                desc.addAccession(accno);
            }
            if (pps != null) {
                String uid = pps.getString(Tags.RefSOPInstanceUID);
                if (uid != null && uid.length() != 0)
                    desc.addMPPS(uid);
            }
            for (String cuid : sorter.getCUIDs(suid)) {
                ParticipantObjectDescription.SOPClass sopClass = new ParticipantObjectDescription.SOPClass(
                        cuid);
                sopClass.setNumberOfInstances(sorter.countInstances(suid,
                        cuid));
                desc.addSOPClass(sopClass);
            }
            msg.addStudy(ian.getString(Tags.StudyInstanceUID), desc);
            msg.validate();
            Logger.getLogger("auditlog").info(msg);
        } catch (Exception e) {
            log.warn("Audit Log failed:", e);
        }
    }

    /**
     * Imports a DICOM file.
     * <p>
     * The FileDTO object refers to an existing DICOM file (This method does NOT
     * check this file!) and the Dataset object holds the meta data for
     * database.
     * <p>
     * 
     * @param fileDTO
     *                Refers the DICOM file.
     * @param ds
     *                Dataset with metadata for database.
     * @param last
     *                last file to import
     */
    public void importFile(FileDTO fileDTO, Dataset ds, String prevseriuid,
            boolean last, boolean deleteFileIfDuplicateExists) throws Exception {
        Storage store = getStorage();
        String seriuid = ds.getString(Tags.SeriesInstanceUID);
        String iuid = ds.getString(Tags.SOPInstanceUID);
        if (prevseriuid != null && !prevseriuid.equals(seriuid)) {
            logInstancesStoredAndSendSeriesStoredNotification(store, prevseriuid);
        }
        if (!iuid.equals(fileDTO.getSopInstanceUID()))
               fileDTO.setSopInstanceUID(iuid); 
        Collection<FileDTO> duplicates = store.getDuplicateFiles(fileDTO);
        if (duplicates.isEmpty()) {
            String cuid = ds.getString(Tags.SOPClassUID);
            FileMetaInfo fmi = DcmObjectFactory.getInstance().newFileMetaInfo(cuid,
                    iuid, fileDTO.getFileTsuid());
            ds.setFileMetaInfo(fmi);
            ds.setPrivateCreatorID(PrivateTags.CreatorID);
            String sourceAET = ds.getString(PrivateTags.CallingAET);
            ds.setPrivateCreatorID(null);
            String filePath = fileDTO.getFilePath();
            scp.updateDB(store, ds, sourceAET, fileDTO.getFileSystemPk(), filePath, fileDTO.getFileSize(),
                    fileDTO.getFileMd5(), fileDTO.getOrigMd5(), fileDTO.getFileStatus(), true, false, false);
        } else {
            log.info("Import of file "+fileDTO+" ignored! Duplicate already exists!");
            if (deleteFileIfDuplicateExists) {
                boolean delete = true;
                for (FileDTO dto : duplicates) {
                    if (dto.getFilePath().equals(fileDTO.getFilePath())) {
                        delete = false;
                        break;
                    }
                }
                if (delete) {
                    File f = FileUtils.toFile(fileDTO.getDirectoryPath(), fileDTO.getFilePath());
                    log.info("Remove imported file (duplicate exists):"+f);
                    if (f.delete())
                        log.info("M-DELETE file:"+f);
                } else {
                    log.info("Skip deleting imported file. File is already referenced by instance!");
                }
            }
        }
        if (last) {
            logInstancesStoredAndSendSeriesStoredNotification(store, seriuid);
        }
    }

    private void logInstancesStoredAndSendSeriesStoredNotification(
            Storage store, String seriuid)
            throws FinderException, RemoteException {
        SeriesStored seriesStored = store.makeSeriesStored(seriuid);
        if (seriesStored == null) {
            return;
        }
        logInstancesStored(null, seriesStored);
        sendSeriesStoredNotification(store, seriesStored);
    }

    public void logInstancesStoredAndUpdateDerivedFields(Storage store,
            Socket s, SeriesStored seriesStored)
            throws FinderException, RemoteException {
        logInstancesStored(s, seriesStored);
        store.updateDerivedStudyAndSeriesFields(
                seriesStored.getSeriesInstanceUID());
    }

    public void sendSeriesStoredNotification(Storage store,
            SeriesStored seriesStored)
            throws FinderException, RemoteException {
        sendJMXNotification(seriesStored);
        store.commitSeriesStored(seriesStored);
    }

    private void sendObjectStoredNotification(Dataset ds) {
        long eventID = super.getNextNotificationSequenceNumber();
        Notification notif = new Notification(EVENT_TYPE_OBJECT_STORED,
                this, eventID);
        notif.setUserData(ds);
        super.sendNotification(notif);
    }
    
    protected void sendNewStudyNotification(Dataset ds) {
        long eventID = super.getNextNotificationSequenceNumber();
        Notification notif = new Notification(EVENT_TYPE_NEW_STUDY,
                this, eventID);
        notif.setUserData(ds);
        super.sendNotification(notif);
    }

    private void checkPendingSeriesStored() throws Exception {
        Storage store = getStorage();
        Collection<Long> seriesPks;
        Timestamp updatedBefore;
        if (seriesStoredNotificationDelays.size() == 1) {
            updatedBefore = new Timestamp(
                System.currentTimeMillis() - seriesStoredNotificationDelays.get(null));
            seriesPks = store.getPksOfPendingSeries(updatedBefore);
        } else {
            updatedBefore = new Timestamp(System.currentTimeMillis());
            seriesPks = store.getPksOfPendingSeries(seriesStoredNotificationDelays);
            log.info("Found seriesPks for SeriesStored:"+seriesPks);
        }
        for (Long seriesPk : seriesPks) {
            try {
                SeriesStored seriesStored = 
                        store.makeSeriesStored(seriesPk, updatedBefore);
                if (seriesStored != null)
                    sendSeriesStoredNotification(store, seriesStored);
            } catch ( Exception x ) {
                log.warn("makeSeriesStored for series(pk="+seriesPk+") failed! Ignored! Reason:"+x);
            }
        }
    }

    Storage getStorage() throws RemoteException, CreateException,
            HomeFactoryException {
        return ((StorageHome) EJBHomeFactory.getFactory().lookup(
                StorageHome.class, StorageHome.JNDI_NAME)).create();
    }

    public List findMWLEntries(Dataset ds) throws Exception {
        List resp = new ArrayList();
        server.invoke(mwlScuServiceName, "findMWLEntries", 
                new Object[] { ds, false, resp },
                new String[] {
                    Dataset.class.getName(),
                    boolean.class.getName(),
                    List.class.getName() });
        return resp;
    }

    /**
     * Callback for pre-processing the dataset
     * 
     * @param ds
     *                the original dataset
     * @throws Exception
     */
    void preProcess(Dataset ds) throws Exception {
        doPreProcess(ds);
    }

    protected void doPreProcess(Dataset ds) throws Exception {
        // Extension Point for customized StoreScpService
    }

    /**
     * Callback for post-processing the dataset
     * 
     * @param ds
     *                the coerced dataset
     * @throws Exception
     */
    void postProcess(Dataset ds) throws Exception {
        sendObjectStoredNotification(ds);
        doPostProcess(ds);
    }

    protected void doPostProcess(Dataset ds) throws Exception {
        // Extension Point for customized StoreScpService
    }

    /**
     * Callback for post-processing the dataset after the dataset has been
     * coerced.
     * 
     * @param ds
     *                the coerced dataset
     * @throws Exception
     */
    void postCoercionProcessing(Dataset ds) throws Exception {
        doPostCoercionProcessing(ds);
    }

    protected void doPostCoercionProcessing(Dataset ds) throws Exception {
        // Extension Point for customized StoreScpService
    }

    String selectFileSystemGroup(String callingAET, String calledAET, Dataset ds)
            throws Exception {
        AEManager mgr = aeMgr();
        String fsgrid;
        try {
            fsgrid = mgr.findByAET(callingAET).getFileSystemGroupID();
            if (fsgrid != null && fsgrid.length() != 0) {
                return fsgrid;
            }
        } catch (UnknownAETException e) {
        }
        try {
            fsgrid = mgr.findByAET(calledAET).getFileSystemGroupID();
            if (fsgrid != null && fsgrid.length() != 0) {
                return fsgrid;
            }
        } catch (UnknownAETException e) {
        }
        return defFileSystemGroupID;
    }

    boolean isFileSystemGroupLocalAccessable(String fsgrpid) {
        return fsmgt.isFileSystemGroupLocalAccessable(fsgrpid);
    }

    void coercePatientID(Dataset ds) throws DcmServiceException {
        if (storeOriginalPatientIDInOtherPatientIDsSeq) {
            DcmElement opidsq = ds.get(Tags.OtherPatientIDSeq);
            if (opidsq == null)
                opidsq = ds.putSQ(Tags.OtherPatientIDSeq);
            Dataset opiditem = opidsq.addNewItem();
            opiditem.putLO(Tags.PatientID, ds.getString(Tags.PatientID));
            opiditem.putLO(Tags.IssuerOfPatientID,
                    ds.getString(Tags.IssuerOfPatientID));
        }
        if (storeOriginalPatientIDInOriginalAttrsSeq) {
        	DcmElement originalAttributesSequence = ds.get(Tags.OriginalAttributesSeq);
        	if (originalAttributesSequence == null)
        		originalAttributesSequence = ds.putSQ(Tags.OriginalAttributesSeq);
        	Dataset oaSeqValues = originalAttributesSequence.addNewItem();
        	oaSeqValues.putLO(Tags.SourceOfPreviousValues);
            oaSeqValues.putDT(Tags.AttributeModificationDatetime, new Date());
            oaSeqValues.putLO(Tags.ModifyingSystem, ds.getString(PrivateTags.CalledAET));
            oaSeqValues.putCS(Tags.ReasonForTheAttributeModification, "CORRECT");
            DcmElement modifiedAttributesSequence = oaSeqValues.putSQ(Tags.ModifiedAttributesSeq);
            Dataset maSeqValues = modifiedAttributesSequence.addNewItem();
            maSeqValues.putLO(Tags.PatientID, ds.getString(Tags.PatientID));
            maSeqValues.putLO(Tags.IssuerOfPatientID, ds.getString(Tags.IssuerOfPatientID));
        }
        String origPatID = ds.getString(Tags.PatientID);
        ds.remove(Tags.PatientID);
        ds.remove(Tags.IssuerOfPatientID);
        ds.setPrivateCreatorID(PrivateTags.CreatorID);
        String calledAET = ds.getString(PrivateTags.CalledAET);
        ds.setPrivateCreatorID(null);
        generatePatientID(ds, ds, calledAET);
        logPatientIDUpdate(origPatID, ds);
    }

    private void logPatientIDUpdate(String origPatID, Dataset ds) {
    	HttpUserInfo userInfo = new HttpUserInfo(AuditMessage.isEnableDNSLookups());
    	PatientRecordMessage msg = new PatientRecordMessage(PatientRecordMessage.UPDATE);
    	msg.addUserPerson(userInfo.getUserId(), null, null, userInfo.getHostName(), true);
    	PersonName pn = ds.getPersonName(Tags.PatientName);
    	String pname = pn != null ? pn.format() : null;
    	ParticipantObject patient = msg.addPatient(origPatID, pname);
    	patient.addParticipantObjectDetail("Description", "Conflicting patient record found," +
    			" assigning generated PatientID " + ds.getString(Tags.PatientID));
    	msg.validate();
    	Logger.getLogger("auditlog").info(msg);
    }

    protected String getOriginalCallingAET(Dataset ds, String callingAET) throws DcmServiceException {
		return callingAET;
	}
}
