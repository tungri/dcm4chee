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
 * Portions created by the Initial Developer are Copyright (C) 2010
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below.
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

package org.dcm4chex.archive.dcm.ups;

import java.sql.Types;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.management.ObjectName;

import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.net.AAssociateAC;
import org.dcm4che.net.AAssociateRQ;
import org.dcm4che.net.AcceptorPolicy;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.Association;
import org.dcm4che.net.AssociationFactory;
import org.dcm4che.net.DcmServiceException;
import org.dcm4che.net.DcmServiceRegistry;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.ExtNegotiator;
import org.dcm4che.net.PDU;
import org.dcm4che.net.RoleSelection;
import org.dcm4che.util.UIDGenerator;
import org.dcm4chex.archive.common.UPSState;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.dcm.AbstractScpService;
import org.dcm4chex.archive.ejb.conf.AttributeFilter;
import org.dcm4chex.archive.ejb.interfaces.AEDTO;
import org.dcm4chex.archive.ejb.interfaces.AEManager;
import org.dcm4chex.archive.ejb.interfaces.UPSManager;
import org.dcm4chex.archive.ejb.interfaces.UPSManagerHome;
import org.dcm4chex.archive.ejb.jdbc.UPSQueryCmd;
import org.dcm4chex.archive.exceptions.UnknownAETException;
import org.dcm4chex.archive.mbean.JMSDelegate;
import org.dcm4chex.archive.mbean.TLSConfigDelegate;
import org.dcm4chex.archive.util.EJBHomeFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date:: xxxx-xx-xx $
 * @since Apr 19, 2010
 */
public class UPSScpService extends AbstractScpService
        implements MessageListener {

    private static final int FUZZY_MATCHING = 2;

    private static final int UNKNOWN_RECEIVING_AET = 0xC308;
    private static final int MSG_ID = 1;
    private static final int ERR_UPSEVENT_RJ = -2;
    private static final int ERR_ASSOC_RJ = -1;
    private static final int PCID_UPSEVENT = 1;
    private static final UIDGenerator uidgen = UIDGenerator.getInstance();

    private static final ExtNegotiator extNegotiator = new ExtNegotiator() {
        public byte[] negotiate(byte[] offered) {
            if (offered.length > FUZZY_MATCHING)
                offered[FUZZY_MATCHING] &= 
                        AttributeFilter.isSoundexEnabled() ? 1 : 0;
            return offered;
        }
    };

    private boolean noMatchForNoValue;

    private String worklistLabel;

    private final Map<String, String> cuidMap =
            new LinkedHashMap<String, String>();
    private final UPSScp pushScp = new UPSScp(this);
    private final UPSFindScp findScp = new UPSFindScp(this);

    private TLSConfigDelegate tlsConfig = new TLSConfigDelegate(this);

    private int acTimeout = 5000;

    private int dimseTimeout = 0;

    private int soCloseDelay = 500;

    private RetryIntervalls.Map reportRetryIntervalls;

    private int concurrency = 1;

    private JMSDelegate jmsDelegate = new JMSDelegate(this);

    private String queueName = "UPSScp";
    
    private int fetchSize;

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

    public String getReportRetryIntervalls() {
        return reportRetryIntervalls != null 
                ? reportRetryIntervalls.toString()
                : "NEVER\n";
    }

    public void setReportRetryIntervalls(String text) {
        reportRetryIntervalls = new RetryIntervalls.Map(text);
    }


    public final ObjectName getTLSConfigName() {
        return tlsConfig.getTLSConfigName();
    }

    public final void setTLSConfigName(ObjectName tlsConfigName) {
        tlsConfig.setTLSConfigName(tlsConfigName);
    }

    public final int getReceiveBufferSize() {
        return tlsConfig.getReceiveBufferSize();
    }

    public final void setReceiveBufferSize(int size) {
        tlsConfig.setReceiveBufferSize(size);
    }

    public final int getSendBufferSize() {
        return tlsConfig.getSendBufferSize();
    }

    public final void setSendBufferSize(int size) {
        tlsConfig.setSendBufferSize(size);
    }

    public final boolean isTcpNoDelay() {
        return tlsConfig.isTcpNoDelay();
    }

    public final void setTcpNoDelay(boolean on) {
        tlsConfig.setTcpNoDelay(on);
    }

    public final String getQueueName() {
        return queueName;
    }

    public final void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public final int getAcTimeout() {
        return acTimeout;
    }

    public final void setAcTimeout(int acTimeout) {
        this.acTimeout = acTimeout;
    }

    public final int getDimseTimeout() {
        return dimseTimeout;
    }

    public final void setDimseTimeout(int dimseTimeout) {
        this.dimseTimeout = dimseTimeout;
    }

    public final int getSoCloseDelay() {
        return soCloseDelay;
    }

    public final void setSoCloseDelay(int soCloseDelay) {
        this.soCloseDelay = soCloseDelay;
    }

    public final String getWorklistLabel() {
        return worklistLabel;
    }

    public final void setWorklistLabel(String s) {
        String trim = s.trim();
        if (trim.length() == 0)
            throw new IllegalArgumentException(
                    "Worklist Label cannot be empty!");
        this.worklistLabel = trim;
    }

    public String getAcceptedSOPClasses() {
        return toString(cuidMap);
    }

    public void setAcceptedSOPClasses(String s) {
        updateAcceptedSOPClass(cuidMap, s, null);
    }

    public final boolean isNoMatchForNoValue() {
        return noMatchForNoValue;
    }

    public final void setNoMatchForNoValue(boolean noMatchForNoValue) {
        this.noMatchForNoValue = noMatchForNoValue;
    }

    public final boolean getAccessBlobAsLongVarBinary() {
        return UPSQueryCmd.blobAccessType == Types.LONGVARBINARY;
    }

    public final void setAccessBlobAsLongVarBinary(boolean enable) {
        UPSQueryCmd.blobAccessType = enable ? Types.LONGVARBINARY : Types.BLOB;
    }

    public final String getTransactionIsolationLevel() {
        return UPSQueryCmd.transactionIsolationLevelAsString(
                UPSQueryCmd.transactionIsolationLevel);
    }

    public final void setTransactionIsolationLevel(String level) {
        UPSQueryCmd.transactionIsolationLevel = 
            UPSQueryCmd.transactionIsolationLevelOf(level);
    }

    public int getFetchSize() {
        return fetchSize;
    }

    public void setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
    }


    protected void bindDcmServices(DcmServiceRegistry services) {
        services.bind(UIDs.UnifiedProcedureStepPushSOPClass, pushScp);
        services.bind(UIDs.UnifiedProcedureStepPullSOPClass, findScp);
        services.bind(UIDs.UnifiedProcedureStepWatchSOPClass, findScp);
   }

    protected void unbindDcmServices(DcmServiceRegistry services) {
        services.unbind(UIDs.UnifiedProcedureStepPushSOPClass);
        services.unbind(UIDs.UnifiedProcedureStepPullSOPClass);
        services.unbind(UIDs.UnifiedProcedureStepWatchSOPClass);
    }

    protected void enablePresContexts(AcceptorPolicy policy) {
        putPresContexts(policy, valuesToStringArray(cuidMap),
                valuesToStringArray(tsuidMap));
        policy.putExtNegPolicy(UIDs.UnifiedProcedureStepPullSOPClass,
                extNegotiator);
        policy.putExtNegPolicy(UIDs.UnifiedProcedureStepWatchSOPClass,
                extNegotiator);
    }

    protected void disablePresContexts(AcceptorPolicy policy) {
        putPresContexts(policy, valuesToStringArray(cuidMap),null);
        policy.putExtNegPolicy(UIDs.UnifiedProcedureStepPullSOPClass, null);
        policy.putExtNegPolicy(UIDs.UnifiedProcedureStepWatchSOPClass, null);
    }

    String createUID() {
        return uidgen.createUID();
    }

    public void updateOrCreateUPS(Dataset ups) throws Exception {
        if (!upsManager().updateMatchingUPS(ups)) {
            ups.putUI(Tags.SOPInstanceUID, createUID());
            createUPS(calledAETs[0], ups);
        }
    }

    @SuppressWarnings("unchecked")
    public void createUPS(String scpAET, Dataset ds)
            throws DcmServiceException {
        String iuid = ds.getString(Tags.SOPInstanceUID);
        try {
            UPSManager upsmgr = upsManager();
            upsmgr.createUPS(ds, patientMatching());
            Collection<String> receivingAETs =
                    upsmgr.getReceivingAETs(iuid);
            if (!receivingAETs.isEmpty()) {
                Dataset stateReport = DcmObjectFactory.getInstance()
                        .newDataset();
                stateReport.putCS(Tags.UPSState, ds.getString(Tags.UPSState));
                for (String receivingAET : receivingAETs)
                    queueUPSEventReportOrder(scpAET, receivingAET, iuid,
                            UPSEventReportOrder.UPS_STATE_REPORT,
                            stateReport);
            }
        } catch (DcmServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DcmServiceException(Status.ProcessingFailure,
                    e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public void updateUPS(String scpAET, String iuid, Dataset rqData)
            throws DcmServiceException {
        try {
            UPSManager upsmgr = upsManager();
            upsmgr.updateUPS(iuid, rqData);
            Dataset processInfo = rqData.getItem(Tags.UPSProgressInformationSeq);
            if (processInfo != null && (processInfo.contains(Tags.UPSProgress)
                    || processInfo.contains(Tags.UPSProgressDescription))) {
                Collection<String> receivingAETs =
                        upsmgr.getReceivingAETs(iuid);
                for (String receivingAET : receivingAETs)
                    queueUPSEventReportOrder(scpAET, receivingAET, iuid,
                            UPSEventReportOrder.UPS_PROGRESS_REPORT, processInfo);
            }
        } catch (DcmServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DcmServiceException(Status.ProcessingFailure,
                    e.getMessage(), e);
        }
     }

    @SuppressWarnings("unchecked")
    public void requestUPSCancel(String scpAET, String iuid, 
            String requestingAET, Dataset rqData) throws DcmServiceException {
        try {
            UPSManager upsmgr = upsManager();
            int upsState = upsmgr.getUPSState(iuid);
            Collection<String> receivingAETs =
                    upsmgr.getReceivingAETs(iuid);
            boolean noReceivingAETs = receivingAETs.isEmpty();
            switch (upsState) {
                case UPSState.IN_PROGRESS:
                if (noReceivingAETs)
                        throw new DcmServiceException(0xC312,
                                "The performer cannot be contacted");
                break;
                case UPSState.COMPLETED:
                    throw new DcmServiceException(0xC312,
                            "UPS is already COMPLETED");
                case UPSState.CANCELED:
                    throw new DcmServiceException(0xB304,
                            "UPS is already CANCELED");
            }
            if (!noReceivingAETs) {
                Dataset cancelRequested = DcmObjectFactory.getInstance()
                        .newDataset();
                cancelRequested.putAll(rqData);
                cancelRequested.putAE(Tags.RequestingAE, requestingAET);
                for (String receivingAET : receivingAETs)
                    queueUPSEventReportOrder(scpAET, receivingAET, iuid,
                        UPSEventReportOrder.UPS_CANCEL_REQUESTED, cancelRequested);
            }
            if (upsState == UPSState.SCHEDULED) {
                if (!rqData.isEmpty())
                    upsmgr.setRequestUPSCancelInfo(iuid, rqData);
                String tuid = startUPS(scpAET, iuid);
                cancelUPS(scpAET, iuid, tuid);
            }
        } catch (DcmServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DcmServiceException(Status.ProcessingFailure,
                    e.getMessage(), e);
        }
    }

    Dataset getUPS(String iuid, int[] attrList) throws DcmServiceException {
        try {
            Dataset ups = upsManager().getUPS(iuid);
            return (attrList != null) ? ups.subSet(attrList) : ups;
        } catch (DcmServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DcmServiceException(Status.ProcessingFailure,
                    e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    void changeUPSState(String scpAET, String iuid, int state, String tuid)
            throws DcmServiceException {
        try {
            UPSManager upsmgr = upsManager();
            // get subscribed AEs BEFORE changeUPSState, 
            // because changeUPSState may delete UPS with its subscriptions
            Collection<String> receivingAETs = upsmgr.getReceivingAETs(iuid);
            Dataset stateReport = upsmgr.changeUPSState(iuid, state, tuid);
            for (String receivingAET : receivingAETs)
                queueUPSEventReportOrder(scpAET, receivingAET, iuid,
                        UPSEventReportOrder.UPS_STATE_REPORT, stateReport);
        } catch (DcmServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DcmServiceException(Status.ProcessingFailure,
                    e.getMessage(), e);
        }
    }

    public String startUPS(String scpAET, String iuid) throws DcmServiceException {
        String tsuid = createUID();
        changeUPSState(scpAET, iuid, UPSState.IN_PROGRESS, tsuid);
        return tsuid;
    }

    public void completeUPS(String scpAET, String iuid, String tsuid)
            throws DcmServiceException {
        changeUPSState(scpAET, iuid, UPSState.COMPLETED, tsuid);
    }

    public void cancelUPS(String scpAET, String iuid, String tsuid)
            throws DcmServiceException {
        changeUPSState(scpAET, iuid, UPSState.CANCELED, tsuid);
    }

    public void subscribeReceiveUPSEventReports(String scpAET, String iuid,
            String receivingAET, boolean dellock) throws DcmServiceException {
        try {
            aeMgr().findByAET(receivingAET);
            UPSManager upsmgr = upsManager();
            upsmgr.subscribeReceiveUPSEventReports(iuid, receivingAET, dellock);
            Dataset stateReport = upsmgr.getUPSStateReport(iuid);
            queueUPSEventReportOrder(scpAET, receivingAET, iuid,
                    UPSEventReportOrder.UPS_STATE_REPORT, stateReport);
        } catch (DcmServiceException e) {
            throw e;
        } catch (UnknownAETException e) {
            throw new DcmServiceException(UNKNOWN_RECEIVING_AET, 
                    "Receiving AE-TITLE: " + receivingAET + " is Unknown to this SCP");
        } catch (Exception e) {
            throw new DcmServiceException(Status.ProcessingFailure,
                    e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public void subscribeGlobally(String scpAET, String receivingAET,
            boolean dellock) throws DcmServiceException {
        try {
            aeMgr().findByAET(receivingAET);
            UPSManager upsmgr = upsManager();
            Collection<String> iuids =
                    upsmgr.subscribeGlobally(receivingAET, dellock);
            if (dellock)
                for (String iuid : iuids) {
                    Dataset stateReport = upsmgr.getUPSStateReport(iuid);
                    queueUPSEventReportOrder(scpAET, receivingAET, iuid,
                            UPSEventReportOrder.UPS_STATE_REPORT, stateReport);
                }
        } catch (DcmServiceException e) {
            throw e;
        } catch (UnknownAETException e) {
            throw new DcmServiceException(UNKNOWN_RECEIVING_AET, 
                    "Receiving AE-TITLE: " + receivingAET + " is Unknown to this SCP");
        } catch (Exception e) {
            throw new DcmServiceException(Status.ProcessingFailure,
                    e.getMessage(), e);
        }
    }

    public void unsubscribeReceiveUPSEventReports(String iuid, String aet)
            throws DcmServiceException {
        try {
            upsManager().unsubscribeReceiveUPSEventReports(iuid, aet);
        } catch (DcmServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DcmServiceException(Status.ProcessingFailure,
                    e.getMessage(), e);
        }
    }

    public void unsubscribeGlobally(String aet)
            throws DcmServiceException {
        try {
            upsManager().unsubscribeGlobally(aet);
        } catch (DcmServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DcmServiceException(Status.ProcessingFailure,
                    e.getMessage(), e);
        }
    }

    public void suspendGlobalSubscription(String aet)
            throws DcmServiceException {
        try {
            upsManager().suspendGlobalSubscription(aet);
        } catch (Exception e) {
            throw new DcmServiceException(Status.ProcessingFailure,
                    e.getMessage(), e);
        }
    }

    private UPSManager upsManager() throws Exception {
        return ((UPSManagerHome) EJBHomeFactory.getFactory()
                .lookup(UPSManagerHome.class, UPSManagerHome.JNDI_NAME))
                .create();
    }

    public void queueUPSEventReportOrder(String callingAET, String calledAET,
            String iuid, int eventTypeID, Dataset eventInfo)
            throws Exception {
        UPSEventReportOrder order = new UPSEventReportOrder(callingAET,
                calledAET, iuid, eventTypeID, eventInfo);
        jmsDelegate.queue(queueName, order , 0, 0);
    }

    public void onMessage(Message message) {
        ObjectMessage om = (ObjectMessage) message;
        try {
            UPSEventReportOrder order = (UPSEventReportOrder) om.getObject();
            log.info("Start processing " + order);
            try {
                process(order);
                log.info("Finished processing " + order);
            } catch (Exception e) {
                order.setThrowable(e);
                final int failureCount = order.getFailureCount() + 1;
                order.setFailureCount(failureCount);
                final long delay = reportRetryIntervalls.getIntervall(
                        order.getCalledAET(), failureCount);
                if (delay == -1L) {
                    log.error("Give up to process " + order, e);
                    jmsDelegate.fail(queueName, order);
                } else {
                    log.warn("Failed to process " + order
                            + ". Scheduling retry.", e);
                    jmsDelegate.queue(queueName, order, 0, 
                            System.currentTimeMillis() + delay);
                }
            }
        } catch (JMSException e) {
            log.error("jms error during processing message: " + message, e);
        } catch (Throwable e) {
            log.error("unexpected error during processing message: " + message,
                    e);
        }
    }

    private void process(UPSEventReportOrder order) throws Exception {
        AEManager aeMgr = aeMgr();
        String aet = order.getCalledAET();
        String callingAET = order.getCallingAET();
        AEDTO callingAE = aeMgr.findByAET(callingAET);
        AEDTO remoteAE = aeMgr.findByAET(aet);
        Dataset ds = order.getEventInfo();
        AssociationFactory af = AssociationFactory.getInstance();
        Association a = af.newRequestor(tlsConfig.createSocket(callingAE,
                remoteAE));
        a.setAcTimeout(acTimeout);
        a.setDimseTimeout(dimseTimeout);
        a.setSoCloseDelay(soCloseDelay);
        AAssociateRQ rq = af.newAAssociateRQ();
        rq.setCalledAET(aet);
        rq.setCallingAET(callingAET);
        rq.addPresContext(af.newPresContext(PCID_UPSEVENT,
                UIDs.UnifiedProcedureStepEventSOPClass,
                valuesToStringArray(tsuidMap)));
        rq.addRoleSelection(af.newRoleSelection(
                UIDs.UnifiedProcedureStepEventSOPClass, false, true));
        PDU ac = a.connect(rq);
        if (!(ac instanceof AAssociateAC)) {
            throw new DcmServiceException(ERR_ASSOC_RJ,
                    "Association not accepted by " + aet + ": " + ac);
        }
        ActiveAssociation aa = af.newActiveAssociation(a, null);
        aa.start();
        try {
            if (a.getAcceptedTransferSyntaxUID(PCID_UPSEVENT) == null)
                throw new DcmServiceException(ERR_UPSEVENT_RJ,
                        "UPS Event SOP Class not supported by remote AE: " + aet);
            Command cmdRq = DcmObjectFactory.getInstance().newCommand();
            RoleSelection rs = ((AAssociateAC) ac)
                    .getRoleSelection(UIDs.UnifiedProcedureStepEventSOPClass);
            if (rs == null || !rs.scp()) {
                log.warn("SCU Role of UPS Event SOP Class rejected by "
                        + aet + " - try to send N_EVENT_REPORT anyway");
            }
            cmdRq.initNEventReportRQ(MSG_ID, 
                    UIDs.UnifiedProcedureStepPushSOPClass,
                    order.getSOPInstanceUID(), order.getEventTypeID());
            log.debug(ds);
            Dimse rsp = aa.invoke(af.newDimse(PCID_UPSEVENT, cmdRq, ds)).get();
            final Command cmdRsp = rsp.getCommand();
            int status = cmdRsp.getStatus();
            if (status != 0)
                throw new DcmServiceException(status, cmdRsp
                        .getString(Tags.ErrorComment));
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

    protected void startService() throws Exception {
        super.startService();
        jmsDelegate.startListening(queueName, this, concurrency);
    }

    protected void stopService() throws Exception {
        jmsDelegate.stopListening(queueName);
        super.stopService();
    }

    static int upsStateAsInt(String upsState) throws DcmServiceException {
        try {
            return UPSState.toInt(upsState);
        } catch (IllegalArgumentException e) {
            throw new DcmServiceException(Status.InvalidAttributeValue,
                    "Illegal UPS State Value: " + upsState);
        }
    }
}
