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

package org.dcm4chex.archive.dcm.ianscu;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationFilterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;

import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObject;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.Association;
import org.dcm4che.net.AssociationFactory;
import org.dcm4che.net.DcmServiceException;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.PresContext;
import org.dcm4che.util.UIDGenerator;
import org.dcm4cheri.util.StringUtils;
import org.dcm4chex.archive.common.IANAndPatientID;
import org.dcm4chex.archive.common.SeriesStored;
import org.dcm4chex.archive.config.DicomPriority;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.dcm.AbstractScuService;
import org.dcm4chex.archive.dcm.mppsscp.MPPSScpService;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2Home;
import org.dcm4chex.archive.ejb.interfaces.MPPSManager;
import org.dcm4chex.archive.ejb.interfaces.MPPSManagerHome;
import org.dcm4chex.archive.mbean.JMSDelegate;
import org.dcm4chex.archive.mbean.TemplatesDelegate;
import org.dcm4chex.archive.notif.StudyDeleted;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.dcm4chex.archive.util.FileUtils;
import org.dcm4chex.archive.util.HomeFactoryException;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author gunter.zeilinger@tiani.com
 * @version $Revision: 14744 $ $Date: 2011-01-21 13:22:59 +0000 (Fri, 21 Jan 2011) $
 * @since 27.08.2004
 */
public class IANScuService extends AbstractScuService implements
        MessageListener {

    public static final String EVENT_TYPE = "org.dcm4chex.archive.dcm.ianscu";
    public static final NotificationFilter NOTIF_FILTER = new NotificationFilter() {

        private static final long serialVersionUID = -6323628592613659041L;

        public boolean isNotificationEnabled(Notification notif) {
            return EVENT_TYPE.equals(notif.getType());
        }
    };

    private static final String NONE = "NONE";

    private static final String[] EMPTY = {};

    private static final String[] IAN_ONLY = { UIDs.InstanceAvailabilityNotificationSOPClass };

    private static final String[] IAN_AND_SCN = {
            UIDs.InstanceAvailabilityNotificationSOPClass,
            UIDs.BasicStudyContentNotification };

     private static final int[] PAT_SUMMARY_AND_STUDY_IDS = {
            Tags.PatientName, Tags.PatientID, Tags.StudyID
    };

     private static final int[] EXCLUDE_ATTRS = {
         Tags.InstanceAvailability, Tags.RetrieveURI, Tags.RetrieveLocationUID
     };

    private final NotificationListener seriesStoredListener = new NotificationListener() {
        public void handleNotification(Notification notif, Object handback) {
            onSeriesStored((SeriesStored) notif.getUserData());
        }
    };

    private final NotificationListener mppsReceivedListener = new NotificationListener() {
        public void handleNotification(Notification notif, Object handback) {
            Dataset mpps = (Dataset) notif.getUserData();
            if (!isIgnoreMPPS(mpps)) {
                String mppsiuid = mpps.getString(Tags.SOPInstanceUID);
                notifyIfRefInstancesAvailable(mppsiuid, null);
            }
        }
    };
    
    private static final NotificationFilterSupport mppsFilter = 
        new NotificationFilterSupport();
    
    static {
        mppsFilter.enableType(MPPSScpService.EVENT_TYPE_MPPS_RECEIVED); 
    }

    private final NotificationListener studyDeletedListener = new NotificationListener() {
        public void handleNotification(Notification notif, Object handback) {
            onStudyDeleted((StudyDeleted) notif.getUserData());
        }
    };

    private ObjectName storeScpServiceName;

    private ObjectName mppsScpServiceName;

    private ObjectName deleteStudyServiceName;

    private ObjectName contentEditServiceName;

    private String queueName;

    private boolean preferInstanceAvailableNotification = true;

    private String[] offeredSOPClasses = IAN_AND_SCN;

    private int scnPriority = 0;

    private String[] notifiedAETs = EMPTY;

    private boolean notifyOtherServices;

    private RetryIntervalls retryIntervalls = new RetryIntervalls();

    private boolean sendOneIANforEachMPPS;

    private int concurrency = 1;

    private boolean onMppsLinkedEnabled;
    
    private String xslPath;
    
    private String retrieveURI;
    private String retrieveLocationUID;

    private JMSDelegate jmsDelegate = new JMSDelegate(this);
    private TemplatesDelegate templates = new TemplatesDelegate(this);

    public final ObjectName getJmsServiceName() {
        return jmsDelegate.getJmsServiceName();
    }

    public final void setJmsServiceName(ObjectName jmsServiceName) {
        jmsDelegate.setJmsServiceName(jmsServiceName);
    }

    public final int getConcurrency() {
        return concurrency;
    }

    public final void setConcurrency(int concurrency) throws Exception {
        if (concurrency <= 0)
            throw new IllegalArgumentException("Concurrency: " + concurrency);
        if (this.concurrency != concurrency) {
            final boolean restart = getState() == STARTED;
            if (restart)
                stop();
            this.concurrency = concurrency;
            if (restart)
                start();
        }
    }

    public final String getNotifiedAETs() {
        return notifiedAETs.length > 0 ? StringUtils.toString(notifiedAETs,
                '\\') : NONE;
    }

    public final void setNotifiedAETs(String notifiedAETs) {
        this.notifiedAETs = NONE.equalsIgnoreCase(notifiedAETs) ? EMPTY
                : StringUtils.split(notifiedAETs, '\\');
    }
    
    public boolean isEnabled() {
        return notifiedAETs.length != 0 || this.xslPath != null;
    }

    public final void setNotifyOtherServices(boolean notifyOtherServices) {
        this.notifyOtherServices = notifyOtherServices;
    }

    public final boolean isNotifyOtherServices() {
        return notifyOtherServices;
    }

    public final boolean isSendOneIANforEachMPPS() {
        return sendOneIANforEachMPPS;
    }

    public final void setSendOneIANforEachMPPS(boolean sendOneIANforEachMPPS) {
        this.sendOneIANforEachMPPS = sendOneIANforEachMPPS;
    }

    public boolean isOnMppsLinkedEnabled() {
        return onMppsLinkedEnabled;
    }

    public void setOnMppsLinkedEnabled(boolean enable) {
        if ( enable) 
            mppsFilter.enableType(MPPSScpService.EVENT_TYPE_MPPS_LINKED);
        else
            mppsFilter.disableType(MPPSScpService.EVENT_TYPE_MPPS_LINKED);
        this.onMppsLinkedEnabled = enable;
    }

    public final String getRetryIntervalls() {
        return retryIntervalls.toString();
    }

    public final void setRetryIntervalls(String s) {
        this.retryIntervalls = new RetryIntervalls(s);
    }

    public final boolean isOfferStudyContentNotification() {
        return offeredSOPClasses == IAN_AND_SCN;
    }

    public final void setOfferStudyContentNotification(boolean offerSCN) {
        this.offeredSOPClasses = offerSCN ? IAN_AND_SCN : IAN_ONLY;
    }

    public final boolean isPreferInstanceAvailableNotification() {
        return preferInstanceAvailableNotification;
    }

    public final void setPreferInstanceAvailableNotification(boolean preferIAN) {
        this.preferInstanceAvailableNotification = preferIAN;
    }

    public final String getScnPriority() {
        return DicomPriority.toString(scnPriority);
    }

    public final void setScnPriority(String scnPriority) {
        this.scnPriority = DicomPriority.toCode(scnPriority);
    }

    public final String getStylesheet() {
        return xslPath == null ? NONE : xslPath;
    }

    public void setStylesheet(String path) {
        this.xslPath = NONE.equals(path) ? null : path;
    }

    public String getRetrieveURI() {
        return retrieveURI == null ? NONE : retrieveURI;
    }

    public void setRetrieveURI(String uri) {
        this.retrieveURI = NONE.equals(uri) ? null : uri;
    }

    public String getRetrieveLocationUID() {
        return retrieveLocationUID == null ? NONE : retrieveLocationUID;
    }

    public void setRetrieveLocationUID(String uid) {
        if (NONE.equals(uid)) {
            retrieveLocationUID = null;
        } else {
            if (!UIDs.isValid(uid))
                throw new IllegalArgumentException("Retrieve Location UID must be a valid UID!");
            retrieveLocationUID = uid;
        }
    }

    public final ObjectName getTemplatesServiceName() {
        return templates.getTemplatesServiceName();
    }

    public final void setTemplatesServiceName(ObjectName serviceName) {
        templates.setTemplatesServiceName(serviceName);
    }
    
    public final ObjectName getStoreScpServiceName() {
        return storeScpServiceName;
    }

    public final void setStoreScpServiceName(ObjectName storeScpServiceName) {
        this.storeScpServiceName = storeScpServiceName;
    }

    public final ObjectName getMppsScpServiceName() {
        return mppsScpServiceName;
    }

    public final void setMppsScpServiceName(ObjectName name) {
        this.mppsScpServiceName = name;
    }

    public ObjectName getDeleteStudyServiceName() {
        return deleteStudyServiceName;
    }

    public void setDeleteStudyServiceName(ObjectName name) {
        this.deleteStudyServiceName = name;
    }

    public void setContentEditServiceName(ObjectName name) {
        this.contentEditServiceName = name;
    }

    public ObjectName getContentEditServiceName() {
        return contentEditServiceName;
    }

    public final String getQueueName() {
        return queueName;
    }

    public final void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    protected void startService() throws Exception {
        jmsDelegate.startListening(queueName, this, concurrency);
        server.addNotificationListener(storeScpServiceName,
                seriesStoredListener, SeriesStored.NOTIF_FILTER, null);
        server.addNotificationListener(deleteStudyServiceName, studyDeletedListener,
                StudyDeleted.NOTIF_FILTER, null);
        server.addNotificationListener(contentEditServiceName, studyDeletedListener,
                StudyDeleted.NOTIF_FILTER, null);
        server.addNotificationListener(mppsScpServiceName,
                mppsReceivedListener, mppsFilter, null);

    }

    protected void stopService() throws Exception {
        server.removeNotificationListener(storeScpServiceName,
                seriesStoredListener, SeriesStored.NOTIF_FILTER, null);
        server.removeNotificationListener(deleteStudyServiceName,
                studyDeletedListener, StudyDeleted.NOTIF_FILTER, null);
        server.removeNotificationListener(contentEditServiceName,
                studyDeletedListener, StudyDeleted.NOTIF_FILTER, null);
        server.removeNotificationListener(mppsScpServiceName,
                mppsReceivedListener, mppsFilter, null);
        jmsDelegate.stopListening(queueName);
    }

    private void onSeriesStored(SeriesStored stored) {
        if (!notifyOtherServices && !isEnabled())
            return;
        Dataset ian = stored.getIAN();
        if (!sendOneIANforEachMPPS) {
            schedule(stored.getPatientID(), stored.getPatientName(),
                    stored.getStudyID(), ian);
        }
        Dataset pps = ian.getItem(Tags.RefPPSSeq);
        if (pps != null
                && (notifyOtherServices || (sendOneIANforEachMPPS && isEnabled()))) {
            notifyIfRefInstancesAvailable(pps.getString(Tags.RefSOPInstanceUID),
                    stored.getSeriesInstanceUID());
        }
    }

    private MPPSManagerHome getMPPSManagerHome() throws HomeFactoryException {
        return (MPPSManagerHome) EJBHomeFactory.getFactory().lookup(
                MPPSManagerHome.class, MPPSManagerHome.JNDI_NAME);
    }

    private void notifyIfRefInstancesAvailable(String mppsIuid,
            String seriesStoredIuid) {
        try {
            MPPSManager mppsManager = getMPPSManagerHome().create();
            Dataset ian = mppsManager.createIANwithPatSummaryAndStudyID(
                    mppsIuid, seriesStoredIuid);
            if (ian != null) {
                if (sendOneIANforEachMPPS) {
                    schedule(ian.getString(Tags.PatientID),
                            ian.getString(Tags.PatientName),
                            ian.getString(Tags.StudyID),
                            ian.exclude(PAT_SUMMARY_AND_STUDY_IDS));
                }
                if (notifyOtherServices) {
                    sendMPPSInstancesAvailableNotification(mppsManager
                            .getMPPS(mppsIuid));
                }
            }
        } catch (Exception e) {
            log.error("Failure processing notifyIfRefInstancesAvailable(): ",
                            e);
        }
    }

    void sendMPPSInstancesAvailableNotification(Dataset mpps) {
        long eventID = super.getNextNotificationSequenceNumber();
        Notification notif = new Notification(EVENT_TYPE, this, eventID);
        notif.setUserData(mpps);
        super.sendNotification(notif);
    }

    private boolean isIgnoreMPPS(Dataset mpps) {
        if (!notifyOtherServices
                && (!isEnabled() || !sendOneIANforEachMPPS)) {
            return true;
        }
        if (mpps.get(Tags.PerformedSeriesSeq) == null) {
            return true;
        }
        String status = mpps.getString(Tags.PPSStatus);
        if ("COMPLETED".equals(status)) {
            return false;
        }
        if (!"DISCONTINUE".equals(status)) {
            return true;
        }
        Dataset item = mpps.getItem(Tags.PPSDiscontinuationReasonCodeSeq);
        if (item != null && "110514".equals(item.getString(Tags.CodeValue))
                && "DCM".equals(item.getString(Tags.CodingSchemeDesignator))) {
            log.info("Ignore MPPS with Discontinuation Reason Code: "
                    + "Wrong Worklist Entry Selected");
            return true;
        }
        return false;
    }

    private void onStudyDeleted(StudyDeleted deleted) {
        if (!isEnabled())
            return;
        schedule(null, null, null,
                deleted.getInstanceAvailabilityNotification());
    }

    private void schedule(String patid, String patname, String studyid,
            Dataset ian) {
        updateRetrieveURIandLocationUID(ian);
        if (log.isDebugEnabled()) {
            log.debug("IAN Dataset:");
            log.debug(ian);
        }
        Dataset mpps = null;
        if ( xslPath != null) {
            Dataset refpps = ian.getItem(Tags.RefPPSSeq);
            if (refpps != null) {
                try {
                    mpps = getMPPSManagerHome().create().getMPPS(refpps.getString(Tags.RefSOPInstanceUID));
                } catch (Exception x) {
                    log.error("Failed to get referenced MPPS of IAN!", x);
                }
            }
        }
        for (String dest : getDestinations(mpps)) {
            IANOrder order = new IANOrder(dest, patid, patname, studyid, ian);
            order.processOrderProperties();
            
            try {
                log.info("Scheduling " + order);
                jmsDelegate.queue(queueName, order, Message.DEFAULT_PRIORITY,
                        0L);
            } catch (Exception e) {
                log.error("Failed to schedule " + order, e);
            }
        }
    }
    
    private void updateRetrieveURIandLocationUID(Dataset ian) {
        if (retrieveURI != null || retrieveLocationUID != null) {
            DcmElement refSerSeq = ian.get(Tags.RefSeriesSeq);
            for (int i = 0, n = refSerSeq.countItems(); i < n; i++) {
                Dataset refSer = refSerSeq.getItem(i);
                DcmElement refSopSeq = refSer.get(Tags.RefSOPSeq);
                for (int j = 0, m = refSopSeq.countItems(); j < m; j++) {
                    Dataset refSOP = refSopSeq.getItem(j);
                    refSOP.putUT(Tags.RetrieveURI, retrieveURI);
                    refSOP.putUT(Tags.RetrieveLocationUID, retrieveLocationUID);
                }
            }
        }
    }

    private Set<String> getDestinations(Dataset mpps) {
        final Set<String> destinations = new HashSet<String>();
        if (mpps != null && xslPath != null) {
            try {
                File xslFile = FileUtils.toExistingFile(xslPath);
                Templates tpl = templates.getTemplates(xslFile);
                log.debug("IAN SCU transform mpps:");
                log.debug(mpps);
                TransformerHandler th = ((SAXTransformerFactory)TransformerFactory.newInstance()).newTransformerHandler(tpl);
                th.setResult(new SAXResult(new DefaultHandler(){

                    public void startElement(String uri, String localName,
                             String qName, Attributes attrs) {
                         if (qName.equals("destination")) {
                             destinations.add(attrs.getValue("aet"));
                         }
                     }
                 }));
                mpps.writeDataset2(th, null, null, 64, null);
            } catch (Exception e) {
                log.error("Applying IAN destination rules to MPPS fails:", e);
            }

        }
        for (int i = 0; i < notifiedAETs.length; ++i) {
            destinations.add(notifiedAETs[i]);
        }
        return destinations;
    }

    public void onMessage(Message message) {
        ObjectMessage om = (ObjectMessage) message;
        try {
            IANOrder order = (IANOrder) om.getObject();
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
                    jmsDelegate.fail(queueName, order);
                } else {
                    log.warn("Failed to process " + order
                            + ". Scheduling retry.", e);
                    jmsDelegate.queue(queueName, order, 0, System
                            .currentTimeMillis()
                            + delay);
                }
            }
        } catch (JMSException e) {
            log.error("jms error during processing message: " + message, e);
        } catch (Throwable e) {
            log.error("unexpected error during processing message: " + message,
                    e);
        }
    }

    private void process(IANOrder order) throws Exception {
        ActiveAssociation aa = openAssociation(order.getDestination(),
                offeredSOPClasses);
        try {
            invokeDimse(aa, order);
        } finally {
            try {
                aa.release(true);
            } catch (Exception e) {
                log.warn(
                        "Failed to release association " + aa.getAssociation(),
                        e);
            }
        }
    }

    private void invokeDimse(ActiveAssociation aa, IANOrder order)
            throws DcmServiceException, IOException, InterruptedException {
        Association a = aa.getAssociation();
        List ianPC = a.listAcceptedPresContext(
                UIDs.InstanceAvailabilityNotificationSOPClass);
        boolean ianAccepted = !ianPC.isEmpty();
        List scnPC = a.listAcceptedPresContext(
                UIDs.BasicStudyContentNotification);
        boolean scnAccepted = !scnPC.isEmpty();
        AssociationFactory af = AssociationFactory.getInstance();
        String iuid = UIDGenerator.getInstance().createUID();
        Command cmdRq = DcmObjectFactory.getInstance().newCommand();
        final Dimse dimseRq;
        if (ianAccepted
                && (preferInstanceAvailableNotification || !scnAccepted)) {
            cmdRq.initNCreateRQ(a.nextMsgID(),
                    UIDs.InstanceAvailabilityNotificationSOPClass, iuid);
            dimseRq = af.newDimse(((PresContext) ianPC.get(0)).pcid(), cmdRq,
                    order.getIAN());
        } else {
            cmdRq.initCStoreRQ(a.nextMsgID(),
                    UIDs.BasicStudyContentNotification, iuid, scnPriority);
            Dataset scn = toSCN(order);
            scn.putUI(Tags.SOPClassUID, UIDs.BasicStudyContentNotification);
            scn.putUI(Tags.SOPInstanceUID, iuid);
            dimseRq = af.newDimse(((PresContext) scnPC.get(0)).pcid(), cmdRq,
                    scn);
        }
        log.debug("Dataset:\n");
        log.debug(dimseRq.getDataset());
        final Dimse dimseRsp = aa.invoke(dimseRq).get();
        final Command cmdRsp = dimseRsp.getCommand();
        final int status = cmdRsp.getStatus();
        switch (status) {
        case 0x0000:
        case 0x0001:
        case 0x0002:
        case 0x0003:
            break;
        case 0x0116:
            log.warn("Received Warning Status 116H "
                    + "(=Attribute Value Out of Range) from remote AE "
                    + order.getDestination());
            break;
        default:
            throw new DcmServiceException(status, cmdRsp
                    .getString(Tags.ErrorComment));
        }
    }

    private Dataset toSCN(IANOrder order) {
        Dataset scn = DcmObjectFactory.getInstance().newDataset();
        scn.putLO(Tags.PatientID, order.getPatientID());
        scn.putPN(Tags.PatientName, order.getPatientName());
        scn.putSH(Tags.StudyID, order.getStudyID());
        DcmObject ian = order.getIAN();
        scn.putUI(Tags.StudyInstanceUID, ian .getString(Tags.StudyInstanceUID));
        DcmElement ianSeriesSeq = ian.get(Tags.RefSeriesSeq);
        DcmElement scnSeriesSeq = scn.putSQ(Tags.RefSeriesSeq);
        for (int i = 0, n = ianSeriesSeq.countItems(); i < n; ++i) {
            Dataset ianSeries = ianSeriesSeq.getItem(i);
            Dataset scnSeries = scnSeriesSeq.addNewItem();
            scnSeries.putUI(Tags.SeriesInstanceUID, ianSeries
                    .getString(Tags.SeriesInstanceUID));
            DcmElement ianSOPSeq = ianSeries.get(Tags.RefSOPSeq);
            DcmElement scnSOPSeq = scnSeries.putSQ(Tags.RefImageSeq);
            for (int j = 0, m = ianSOPSeq.countItems(); j < m; ++j) {
                scnSOPSeq.addItem(
                        ianSOPSeq.getItem(j).exclude(EXCLUDE_ATTRS));
            }
        }
        return scn;
    }

    public void scheduleRandomIAN(String retrieveAET, String availability,
            int numSeries, int numInstancesPerSeries) {
        Dataset ian = DcmObjectFactory.getInstance().newDataset();
        UIDGenerator gen = UIDGenerator.getInstance();
        ian.putUI(Tags.StudyInstanceUID, gen.createUID());
        DcmElement ianSeriesSeq = ian.putSQ(Tags.RefSeriesSeq);
        for (int i = 0; i < numSeries; i++) {
            Dataset ianSeries = ianSeriesSeq.addNewItem();
            ianSeries.putUI(Tags.SeriesInstanceUID, gen.createUID());
            DcmElement ianSOPSeq = ianSeries.putSQ(Tags.RefSOPSeq);
            for (int j = 0; j < numInstancesPerSeries; j++) {
                Dataset refSOP = ianSOPSeq.addNewItem();
                refSOP.putUI(Tags.RefSOPClassUID, UIDs.SecondaryCaptureImageStorage);
                refSOP.putUI(Tags.RefSOPInstanceUID, gen.createUID());
                refSOP.putAE(Tags.RetrieveAET, retrieveAET);
                refSOP.putCS(Tags.InstanceAvailability, availability);
            }
        }
        schedule(null, null, null, ian);
    }

    public void scheduleIANforStudy(String uid) throws Exception {
        IANAndPatientID ian = fileSystemMgt().createIANforStudy(uid);
        for (Dataset ds : ian.ians) {
            schedule(ian.patid, ian.patname, ian.studyid, ds);
        }
    }

    public void scheduleIANforSeries(String uid) throws Exception {
        IANAndPatientID ian = fileSystemMgt().createIANforSeries(uid);
        schedule(ian.patid, ian.patname, ian.studyid, ian.ians.iterator().next());
   }

    static FileSystemMgt2 fileSystemMgt() throws Exception {
        FileSystemMgt2Home home = (FileSystemMgt2Home) EJBHomeFactory
                .getFactory().lookup(FileSystemMgt2Home.class,
                        FileSystemMgt2Home.JNDI_NAME);
        return home.create();
    }
}