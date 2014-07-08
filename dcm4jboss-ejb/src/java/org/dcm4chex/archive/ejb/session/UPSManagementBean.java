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

package org.dcm4chex.archive.ejb.session;

import java.util.Collection;

import javax.ejb.EJBException;
import javax.ejb.FinderException;
import javax.ejb.ObjectNotFoundException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.log4j.Logger;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.net.DcmServiceException;
import org.dcm4chex.archive.common.PatientMatching;
import org.dcm4chex.archive.common.UPSState;
import org.dcm4chex.archive.ejb.interfaces.PatientLocal;
import org.dcm4chex.archive.ejb.interfaces.PatientLocalHome;
import org.dcm4chex.archive.ejb.interfaces.UPSGlobalSubscriptionLocal;
import org.dcm4chex.archive.ejb.interfaces.UPSGlobalSubscriptionLocalHome;
import org.dcm4chex.archive.ejb.interfaces.UPSLocal;
import org.dcm4chex.archive.ejb.interfaces.UPSLocalHome;
import org.dcm4chex.archive.ejb.interfaces.UPSSubscriptionLocal;
import org.dcm4chex.archive.ejb.interfaces.UPSSubscriptionLocalHome;
import org.dcm4chex.archive.exceptions.NonUniquePatientException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date:: xxxx-xx-xx $
 * @since Apr 22, 2010
 * 
 * @ejb.bean name="UPSManager" type="Stateless" view-type="remote"
 *           jndi-name="ejb/UPSManager"
 * @ejb.transaction-type type="Container"
 * @ejb.transaction type="Required"
 * @ejb.ejb-ref ejb-name="Patient" view-type="local" ref-name="ejb/Patient"
 * @ejb.ejb-ref ejb-name="UPS" view-type="local" ref-name="ejb/UPS"
 * @ejb.ejb-ref ejb-name="UPSSubscription" view-type="local" ref-name="ejb/UPSSubscription"
 * @ejb.ejb-ref ejb-name="UPSGlobalSubscription" view-type="local" ref-name="ejb/UPSGlobalSubscription"
 */
public abstract class UPSManagementBean implements SessionBean {

    private static Logger LOG = Logger.getLogger(UPSManagementBean.class);

    private static final int ALREADY_IN_REQUESTED_STATE = 0xB304;

    private static final int MAY_NO_LONGER_BE_UPDATED = 0xC300;
    private static final int CORRECT_TRANSACTION_UID_NOT_PROVIDED = 0xC301;
    private static final int ALREADY_IN_PROGRESS = 0xC302;
    private static final int NOT_MET_FINAL_STATE_REQUIREMENTS = 0xC304; 
    private static final int NO_SUCH_UPS = 0xC307;
    private static final int NOT_IN_PROGRESS = 0xC310;

    private PatientLocalHome patHome;
    private UPSLocalHome upsHome;
    private UPSSubscriptionLocalHome subsHome;
    private UPSGlobalSubscriptionLocalHome gsubsHome;
    private SessionContext sessionCtx;

    public void setSessionContext(SessionContext ctx) {
        sessionCtx = ctx;
        Context jndiCtx = null;
        try {
            jndiCtx = new InitialContext();
            patHome = (PatientLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/Patient");
            upsHome = (UPSLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/UPS");
            subsHome = (UPSSubscriptionLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/UPSSubscription");
            gsubsHome = (UPSGlobalSubscriptionLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/UPSGlobalSubscription");
        } catch (NamingException e) {
            throw new EJBException(e);
        } finally {
            if (jndiCtx != null) {
                try {
                    jndiCtx.close();
                } catch (NamingException ignore) {
                }
            }
        }
    }

    public void unsetSessionContext() {
        sessionCtx = null;
        patHome = null;
        upsHome = null;
        subsHome = null;
        gsubsHome = null;
    }

    /**
     * @ejb.interface-method
     */
    public void createUPS(Dataset ds, PatientMatching matching)
            throws DcmServiceException {
        checkDuplicate(ds.getString(Tags.SOPInstanceUID));
        try {
            UPSLocal ups = upsHome.create(ds,
                    ds.containsValue(Tags.PatientID)
                        ? findOrCreatePatient(ds, matching)
                        : null);
            Collection<UPSGlobalSubscriptionLocal> gSubs =
                        gsubsHome.findAll();
            for (UPSGlobalSubscriptionLocal gSub : gSubs)
                subsHome.create(gSub.getReceivingAETitle(),
                        gSub.getDeletionLock(), ups);
        } catch (Exception e) {
            LOG.error("Creation of UPS(iuid="
                    + ds.getString(Tags.SOPInstanceUID) + ") failed: ", e);
            sessionCtx.setRollbackOnly();
            throw new DcmServiceException(Status.ProcessingFailure, e);
        }
    }

    private void checkDuplicate(String iuid) throws DcmServiceException {
        try {
            upsHome.findBySopInstanceUID(iuid);
            sessionCtx.setRollbackOnly();
            throw new DcmServiceException(Status.DuplicateSOPInstance);
        } catch (ObjectNotFoundException e) { // Ok
        } catch (FinderException e) {
            LOG.error("Query for UPS(iuid=" + iuid + ") failed: ", e);
            sessionCtx.setRollbackOnly();
            throw new DcmServiceException(Status.ProcessingFailure, e);
        }
    }

    private PatientLocal findOrCreatePatient(Dataset ds,
            PatientMatching matching) throws DcmServiceException {
        try {
            try {
                return patHome.selectPatient(ds, matching, true);
            } catch (ObjectNotFoundException enfe) {
                return patHome.create(ds);
            } catch (NonUniquePatientException onfe) {
                return patHome.create(ds);
            }
        } catch (Exception e) {
            sessionCtx.setRollbackOnly();
            throw new DcmServiceException(Status.ProcessingFailure, e);
        }
    }

    /**
     * @ejb.interface-method
     */
    public Dataset getUPS(String uid) throws DcmServiceException {
        final UPSLocal ups = findUPS(uid);
        final PatientLocal pat = ups.getPatient();
        Dataset attrs = ups.getAttributes();
        if (pat != null)
            attrs.putAll(pat.getAttributes(false));
        return attrs;
    }

    /**
     * @ejb.interface-method
     */
    public int getUPSState(String uid) throws DcmServiceException {
        return findUPS(uid).getStateAsInt();
    }

    /**
     * @ejb.interface-method
     */
    @SuppressWarnings("unchecked")
    public boolean updateMatchingUPS(Dataset ds) throws FinderException {
        Dataset refRequest = ds.getItem(Tags.RefRequestSeq);
        if (refRequest == null)
            return false;
        String rpid = refRequest.getString(Tags.RequestedProcedureID);
        Dataset wkitem = ds.getItem(Tags.ScheduledWorkitemCodeSeq);
        String codeValue = wkitem.getString(Tags.CodeValue);
        String codingSchemeDesignator =
                wkitem.getString(Tags.CodingSchemeDesignator);
        String codingSchemeVersion =
                wkitem.getString(Tags.CodingSchemeVersion);
        int state = UPSState.toInt(ds.getString(Tags.UPSState));
        Collection<UPSLocal> matchingUPS = (Collection<UPSLocal>) (codingSchemeVersion == null
                ? upsHome.findByStateAndRequestedProcedureIdAndWorkItemCode(
                        state, rpid, codeValue, codingSchemeDesignator)
                : upsHome.findByStateAndRequestedProcedureIdAndWorkItemCode(
                        state, rpid, codeValue, codingSchemeDesignator,
                        codingSchemeVersion));
        if (matchingUPS.isEmpty())
            return false;
        if (matchingUPS.size() > 1) {
            LOG.info("More than one UPS (" + codeValue + ", "
                    + codingSchemeDesignator + ", \"" + codingSchemeVersion
                    + "\") with rpid: " + rpid + " found");
            return false;
        }
        matchingUPS.iterator().next().updateAttributes(ds);
        return true;
    }

    /**
     * @ejb.interface-method
     */
    public void updateUPS(String iuid, Dataset ds) throws DcmServiceException {
        UPSLocal ups = findUPS(iuid);
        switch (ups.getStateAsInt()) {
        case UPSState.SCHEDULED:
            if (ds.contains(Tags.TransactionUID))
                throw new DcmServiceException(NOT_IN_PROGRESS);
            break;
        case UPSState.IN_PROGRESS:
            if (!ups.getTransactionUID()
                    .equals(ds.getString(Tags.TransactionUID)))
                throw new DcmServiceException(
                        CORRECT_TRANSACTION_UID_NOT_PROVIDED);
            break;
        case UPSState.COMPLETED:
        case UPSState.CANCELED:
            throw new DcmServiceException(MAY_NO_LONGER_BE_UPDATED);
        }
        ups.updateAttributes(ds);
    }

    /**
     * @ejb.interface-method
     */
    public Collection<String> getReceivingAETs(String iuid)
            throws DcmServiceException {
        try {
            return subsHome.getReceivingAETs(iuid);
        } catch (Exception e) {
            sessionCtx.setRollbackOnly();
            throw new DcmServiceException(Status.ProcessingFailure, e);
        }
    }

    /**
     * @ejb.interface-method
     */
    public void setRequestUPSCancelInfo(String iuid, Dataset info)
            throws DcmServiceException {
        if (info.isEmpty())
            return;
        UPSLocal ups = findUPS(iuid);
        Dataset attrs = ups.getAttributes();
        DcmElement progressInfoSeq = attrs.get(Tags.UPSProgressInformationSeq); 
        if (progressInfoSeq == null)
            progressInfoSeq = attrs.putSQ(Tags.UPSProgressInformationSeq);
        Dataset progressInfo = progressInfoSeq.isEmpty()
                ? progressInfoSeq.addNewItem()
                : progressInfoSeq.getItem();
        String reason = info.getString(Tags.ReasonForCancellation);
        if (reason != null)
            progressInfo.putLT(Tags.ReasonForCancellation, reason);
        Dataset code = info.getItem(
                Tags.UPSDiscontinuationReasonCodeSeq);
        if (code != null)
            progressInfo.putSQ(Tags.UPSDiscontinuationReasonCodeSeq)
                    .addItem(code);
        String contactURI = info.getString(Tags.ContactURI);
        String contactDisplayName = info.getString(Tags.ContactDisplayName);
        if (contactURI != null || contactDisplayName != null) {
            DcmElement sq = progressInfo.get(Tags.UPSCommunicationsURISeq);
            if (sq == null)
                sq = attrs.putSQ(Tags.UPSCommunicationsURISeq);
            Dataset item = sq.addNewItem();
            if (contactURI != null)
                item.putST(Tags.ContactURI, contactURI);
            if (contactDisplayName != null)
                item.putLO(Tags.ContactDisplayName, contactDisplayName);
        }

    }

    /**
     * @ejb.interface-method
     */
    public Dataset changeUPSState(String iuid, int newState, String tuid)
            throws DcmServiceException {
        try {
            UPSLocal ups = findUPS(iuid);
            int prevState = ups.getStateAsInt();
            if (newState == UPSState.IN_PROGRESS) {
                if (prevState != UPSState.SCHEDULED)
                    throw new DcmServiceException(
                            prevState == UPSState.IN_PROGRESS
                                ? ALREADY_IN_PROGRESS
                                : MAY_NO_LONGER_BE_UPDATED);
                ups.setTransactionUID(tuid);
            } else {
                if (prevState != UPSState.IN_PROGRESS)
                    throw new DcmServiceException(
                            prevState == newState
                                ? ALREADY_IN_REQUESTED_STATE
                                : MAY_NO_LONGER_BE_UPDATED);
                if (!tuid.equals(ups.getTransactionUID()))
                    throw new DcmServiceException(
                            CORRECT_TRANSACTION_UID_NOT_PROVIDED);
                if (newState == UPSState.COMPLETED
                        && !meetFinalStateRequirements(ups.getAttributes()))
                    throw new DcmServiceException(NOT_MET_FINAL_STATE_REQUIREMENTS);
            }
            ups.updateState(newState);
            Dataset stateReport = getUPSStateReport(ups);
            if (newState != UPSState.IN_PROGRESS 
                    && !subsHome.hasDeletionLocks(ups))
                ups.remove();
            return stateReport;
        } catch (DcmServiceException e) {
            throw e;
        } catch (Exception e) {
            sessionCtx.setRollbackOnly();
            throw new DcmServiceException(Status.ProcessingFailure, e);
        }
    }

    /**
     * @ejb.interface-method
     */
    public Dataset getUPSStateReport(String iuid) throws FinderException {
        return getUPSStateReport(upsHome.findBySopInstanceUID(iuid));
    }

    private Dataset getUPSStateReport(UPSLocal ups) {
        Dataset stateReport = DcmObjectFactory.getInstance().newDataset();
        stateReport.putCS(Tags.UPSState, UPSState.toString(ups.getStateAsInt()));
        Dataset progressInfo = ups.getAttributes()
            .getItem(Tags.UPSProgressInformationSeq);
        if (progressInfo != null) {
            String s = progressInfo.getString(Tags.ReasonForCancellation);
            if (s != null)
                stateReport.putLT(Tags.ReasonForCancellation, s);
            Dataset code = progressInfo.getItem(
                    Tags.UPSDiscontinuationReasonCodeSeq);
            if (code != null)
                stateReport.putSQ(Tags.UPSDiscontinuationReasonCodeSeq)
                        .addItem(code);
        }
        return stateReport;
    }

    private boolean meetFinalStateRequirements(Dataset ds) {
        DcmElement sq = ds.get(Tags.UPSPerformedProcedureSeq);
        if (sq.isEmpty())
            return false;
        for (int i = 0, n = sq.countItems(); i < n; ++i) {
            Dataset item = sq.getItem(i);
            if (!item.containsValue(Tags.PerformedStationNameCodeSeq)
                    || !item.containsValue(Tags.PerformedWorkitemCodeSeq)
                    || !item.containsValue(Tags.PPSStartDate)
                    || !item.containsValue(Tags.PPSStartTime)
                    || !item.containsValue(Tags.PPSEndDate)
                    || !item.containsValue(Tags.PPSEndTime))
                return false;
        }
        return true;
    }

    private UPSLocal findUPS(String iuid) throws DcmServiceException {
        try {
            return upsHome.findBySopInstanceUID(iuid);
        } catch (ObjectNotFoundException e) {
            throw new DcmServiceException(NO_SUCH_UPS);
        } catch (Exception e) {
            sessionCtx.setRollbackOnly();
            throw new DcmServiceException(Status.ProcessingFailure, e);
        }
    }


    /**
     * @ejb.interface-method
     */
    public boolean subscribeReceiveUPSEventReports(String iuid, String aet,
            boolean deletionLock) throws DcmServiceException {
        try {
            try {
                UPSSubscriptionLocal sub = subsHome
                        .findByReceivingAETAndUPSInstanceUID(aet, iuid);
                if (deletionLock != sub.getDeletionLock()) {
                    sub.setDeletionLock(deletionLock);
                    if (!deletionLock) {
                        UPSLocal ups = sub.getUPS();
                        if (!subsHome.hasDeletionLocks(ups))
                            ups.remove();
                    }
                }
                return false;
            } catch (ObjectNotFoundException e) {
                subsHome.create(aet, deletionLock, findUPS(iuid));
                return true;
            }
        } catch (Exception e) {
            sessionCtx.setRollbackOnly();
            throw new DcmServiceException(Status.ProcessingFailure, e);
        }
    }

    /**
     * @ejb.interface-method
     */
    public Collection<String> subscribeGlobally(String aet, boolean dellock)
            throws DcmServiceException {
        try {
            try {
                gsubsHome.findByReceivingAET(aet).setDeletionLock(dellock);
            } catch (ObjectNotFoundException e) {
                gsubsHome.create(aet, dellock);
            }
            Collection<String> uids = upsHome.getNotSubscribed(aet);
            for (String uid : uids)
                subscribeReceiveUPSEventReports(uid, aet, dellock);
            return uids;
        } catch (DcmServiceException e) {
            throw e;
        } catch (Exception e) {
            sessionCtx.setRollbackOnly();
            throw new DcmServiceException(Status.ProcessingFailure, e);
        }
    }

    /**
     * @ejb.interface-method
     */
    public void unsubscribeReceiveUPSEventReports(String iuid, String aet)
            throws DcmServiceException {
        try {
            try {
                removeSubscription(
                        subsHome.findByReceivingAETAndUPSInstanceUID(
                                aet, iuid));
            } catch (ObjectNotFoundException e) {
                // check if UPS exists
                findUPS(iuid);
            }
        } catch (DcmServiceException e) {
            throw e;
        } catch (Exception e) {
            sessionCtx.setRollbackOnly();
            throw new DcmServiceException(Status.ProcessingFailure, e);
        }
    }

    void removeSubscription(UPSSubscriptionLocal sub) throws Exception {
        boolean dellock = sub.getDeletionLock();
        UPSLocal ups = sub.getUPS();
        sub.remove();
        if (dellock && !subsHome.hasDeletionLocks(ups))
            ups.remove();
    }

    /**
     * @ejb.interface-method
     */
    public void unsubscribeGlobally(String aet) throws DcmServiceException {
        try {
            Collection<UPSSubscriptionLocal> allSubs = 
                    subsHome.findByReceivingAET(aet);
            for (UPSSubscriptionLocal sub : allSubs)
                removeSubscription(sub);
            suspendGlobalSubscription(aet);
        } catch (DcmServiceException e) {
            throw e;
        } catch (Exception e) {
            sessionCtx.setRollbackOnly();
            throw new DcmServiceException(Status.ProcessingFailure, e);
        }
    }

    /**
     * @ejb.interface-method
     */
    public void suspendGlobalSubscription(String aet)
            throws DcmServiceException {
        try {
            gsubsHome.findByReceivingAET(aet).remove();
        } catch (ObjectNotFoundException e) {
            // no global subscribtion
        } catch (Exception e) {
            sessionCtx.setRollbackOnly();
            throw new DcmServiceException(Status.ProcessingFailure, e);
        }
    }
}
