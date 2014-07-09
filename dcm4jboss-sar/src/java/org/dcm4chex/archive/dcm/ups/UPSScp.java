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

import java.io.IOException;
import java.util.Date;

import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.Association;
import org.dcm4che.net.DcmServiceBase;
import org.dcm4che.net.DcmServiceException;
import org.dcm4che.net.Dimse;
import org.dcm4chex.archive.common.UPSState;
import org.jboss.logging.Logger;


/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date:: xxxx-xx-xx $
 * @since Apr 19, 2010
 */
class UPSScp extends DcmServiceBase {

    private static enum ItemCount { SINGLE, MULTIPLE };
    private static final int CORRECT_TRANSACTION_UID_NOT_PROVIDED = 0xC301;
    private static final int MAY_ONLY_BECOME_SCHEDULED_VIA_NCREATE = 0xC303;
    private static final int UPS_STATE_WAS_NOT_SCHEDULED = 0xC309;

    private final UPSScpService service;

    private Logger log;

    public UPSScp(UPSScpService service) {
        this.service = service;
        this.log = service.getLog();
    }

    @Override
    protected Dataset doNCreate(ActiveAssociation assoc, Dimse rq,
            Command rspCmd) throws IOException, DcmServiceException {
        String calledAET = assoc.getAssociation().getCalledAET();
        Command rqCmd = rq.getCommand();
        Dataset rqData = rq.getDataset();
        if (!abstractSyntaxEquals(assoc, rq,
                UIDs.UnifiedProcedureStepPushSOPClass))
            throw new DcmServiceException(UNRECOGNIZE_OPERATION);
        log.debug("Identifier:\n");
        log.debug(rqData);
        checkNCreateRQ(rqCmd, rqData);
        coerceNCreateRQ(rspCmd, rqData);
        service.createUPS(calledAET, rqData);
        return null;
    }

    private void coerceNCreateRQ(Command rspCmd, Dataset rqData) {
        rqData.putUI(Tags.SOPClassUID, rspCmd.getAffectedSOPClassUID());
        rqData.putUI(Tags.SOPInstanceUID, rspCmd.getAffectedSOPInstanceUID());
        initSPSModificationDateandTime(rqData);
        if (!rqData.containsValue(Tags.WorklistLabel))
            rqData.putLO(Tags.WorklistLabel, service.getWorklistLabel());
    }

    private void initSPSModificationDateandTime(Dataset rqData) {
        rqData.putDT(Tags.SPSModificationDateandTime, new Date());
    }

    private static void checkNCreateRQ(Command rqCmd, Dataset rqData)
            throws DcmServiceException {
        shallBeEmpty(rqData, Tags.TransactionUID);
        notAllowed(rqData, Tags.SOPClassUID);
        notAllowed(rqData, Tags.SOPInstanceUID);
        type1(rqData, Tags.SPSPriority);
        type2(rqData, Tags.SPSModificationDateandTime);
        type1(rqData, Tags.ProcedureStepLabel);
        type2(rqData, Tags.WorklistLabel);
        type2(rqData, Tags.ScheduledProcessingParametersSeq);
        checkContentItem(rqData, Tags.ScheduledProcessingParametersSeq);
        type2(rqData, Tags.ScheduledStationNameCodeSeq);
        checkCodeItem(rqData, ItemCount.MULTIPLE,
                Tags.ScheduledStationNameCodeSeq);
        type2(rqData, Tags.ScheduledStationClassCodeSeq);
        checkCodeItem(rqData, ItemCount.MULTIPLE,
                Tags.ScheduledStationClassCodeSeq);
        type2(rqData, Tags.ScheduledStationGeographicLocationCodeSeq);
        checkCodeItem(rqData, ItemCount.MULTIPLE,
                Tags.ScheduledStationGeographicLocationCodeSeq);
        type2(rqData, Tags.ScheduledProcessingApplicationsCodeSeq);
        checkCodeItem(rqData, ItemCount.MULTIPLE,
                Tags.ScheduledProcessingApplicationsCodeSeq);
        checkScheduledHumanPerformers(rqData);
        type1(rqData, Tags.SPSStartDateAndTime);
        type2(rqData, Tags.ScheduledWorkitemCodeSeq);
        checkCodeItem(rqData, ItemCount.SINGLE, Tags.ScheduledWorkitemCodeSeq);
        type2(rqData, Tags.SPSComments);
        type2(rqData, Tags.InputInformationSeq);
        checkImageSOPInstanceAndSourceReference(rqData,
                Tags.InputInformationSeq);
        type2(rqData, Tags.AdmissionID);
        type2(rqData, Tags.IssuerOfAdmissionIDSeq);
        type2(rqData, Tags.AdmittingDiagnosisDescription);
        type2(rqData, Tags.AdmittingDiagnosisCodeSeq);
        checkCodeItem(rqData, ItemCount.MULTIPLE,
                Tags.AdmittingDiagnosisCodeSeq);
        type2(rqData, Tags.RefRequestSeq);
        checkReferencedRequests(rqData);
        type2(rqData, Tags.RelatedProcedureStepSeq);
        checkRelatedProcedureSteps(rqData);
        type1(rqData, Tags.UPSState);
        if (UPSScpService.upsStateAsInt(rqData.getString(Tags.UPSState))
                != UPSState.SCHEDULED)
            throw new DcmServiceException(UPS_STATE_WAS_NOT_SCHEDULED,
                    "The provided value of UPS State was not SCHEDULED");
        shallBeEmpty(rqData, Tags.UPSProgressInformationSeq);
        shallBeEmpty(rqData, Tags.UPSPerformedProcedureSeq);
   }

    private static void checkRelatedProcedureSteps(Dataset rqData)
            throws DcmServiceException {
        DcmElement sq = rqData.get(Tags.RelatedProcedureStepSeq);
        if (sq == null)
            return;
        for (int i = 0, n = sq.countItems(); i < n; i++) {
            Dataset item = sq.getItem(i);
            type1(item, Tags.RefSOPClassUID, Tags.RelatedProcedureStepSeq);
            type1(item, Tags.RefSOPInstanceUID, Tags.RelatedProcedureStepSeq);
            type1(item, Tags.PurposeOfReferenceCodeSeq, Tags.RelatedProcedureStepSeq);
            checkCodeItem(item, ItemCount.SINGLE,
                    Tags.PurposeOfReferenceCodeSeq, Tags.RelatedProcedureStepSeq);
        }
     }

    private static void checkReferencedRequests(Dataset rqData)
            throws DcmServiceException {
        DcmElement sq = rqData.get(Tags.RefRequestSeq);
        if (sq == null)
            return;
        for (int i = 0, n = sq.countItems(); i < n; i++) {
            Dataset item = sq.getItem(i);
            type1(item, Tags.StudyInstanceUID, Tags.RefRequestSeq);
            type2(item, Tags.AccessionNumber, Tags.RefRequestSeq);
            type2(item, Tags.RequestedProcedureID, Tags.RefRequestSeq);
            type2(item, Tags.RequestedProcedureDescription,
                    Tags.RefRequestSeq);
            type2(item, Tags.RequestedProcedureCodeSeq, Tags.RefRequestSeq);
            checkCodeItem(item, ItemCount.SINGLE,
                    Tags.RequestedProcedureCodeSeq, Tags.RefRequestSeq);
            checkCodeItem(item, ItemCount.MULTIPLE,
                    Tags.ReasonforRequestedProcedureCodeSeq, Tags.RefRequestSeq);
        }
    }

    private static void checkImageSOPInstanceAndSourceReference(Dataset rqData,
            int tag, int... sqTags) throws DcmServiceException {
        DcmElement sq = rqData.get(tag);
        if (sq == null)
            return;
        int[] sqTags1 = cat(sqTags, tag);
        int[] sqTags2 = cat(sqTags1, Tags.RefSOPSeq);
        for (int i = 0, n = sq.countItems(); i < n; i++) {
            Dataset item = sq.getItem(i);
            type1(item, Tags.RefSOPSeq, sqTags1);
            DcmElement refSOPSeq = item.get(Tags.RefSOPSeq);
            for (int j = 0, m = refSOPSeq.countItems(); j < m; j++) {
                Dataset refSOP = refSOPSeq.getItem(j);
                type1(refSOP, Tags.RefSOPClassUID, sqTags2);
                type1(refSOP, Tags.RefSOPInstanceUID, sqTags2);
            }
        }
    }

    private static void checkScheduledHumanPerformers(Dataset rqData)
            throws DcmServiceException {
        DcmElement sq = rqData.get(Tags.ScheduledHumanPerformersSeq);
        if (sq == null) 
            return;
        for (int i = 0, n = sq.countItems(); i < n; i++) {
            Dataset item = sq.getItem(i);
            type1(item, Tags.HumanPerformerCodeSeq,
                    Tags.ScheduledHumanPerformersSeq);
            checkCodeItem(item, ItemCount.SINGLE, Tags.HumanPerformerCodeSeq,
                    Tags.ScheduledHumanPerformersSeq);
            type1(item, Tags.HumanPerformerName,
                    Tags.ScheduledHumanPerformersSeq);
            type1(item, Tags.HumanPerformerOrganization,
                    Tags.ScheduledHumanPerformersSeq);
        }
    }

    private static void checkCodeItem(Dataset rqData, ItemCount itemCount,
            int tag, int... sqTags)
            throws DcmServiceException {
        DcmElement sq = rqData.get(tag);
        if (sq == null || sq.isEmpty())
            return;
        int n = sq.countItems();
        if (itemCount == ItemCount.SINGLE && n > 1)
            throw new DcmServiceException(Status.InvalidAttributeValue,
                    errorMessage("More than 1 item of Attribute: ",
                            tag, sqTags));
        int[] sqTags1 = cat(sqTags, tag);
        for (int i = 0; i < n; i++) {
            Dataset item = sq.getItem(i);
            type1(item, Tags.CodeValue, sqTags1);
            type1(item, Tags.CodingSchemeDesignator, sqTags1);
            type1(item, Tags.CodeMeaning, sqTags1);
        }
    }

    private static int[] cat(int[] sqTags, int tag) {
        int last = sqTags.length;
        int[] dest = new int[last + 1];
        System.arraycopy(sqTags, 0, dest, 0, last);
        dest[last] = tag;
        return dest;
    }

    private static void checkContentItem(Dataset rqData, int tag,
            int... sqTags) throws DcmServiceException {
        DcmElement sq = rqData.get(tag);
        if (sq == null || sq.isEmpty())
            return;
        int[] sqTags1 = cat(sqTags, tag);
        for (int i = 0, n = sq.countItems(); i < n; i++) {
            Dataset item = sq.getItem(i);
            type1(item, Tags.ValueType, sqTags1);
            type1(item, Tags.ConceptNameCodeSeq, sqTags1);
            checkCodeItem(item, ItemCount.SINGLE, Tags.ConceptNameCodeSeq,
                    sqTags1);
            String valueType = item.getString(Tags.ValueType);
            if (valueType.equals("DATETIME"))
                type1(item, Tags.DateTime, sqTags1);
            else if (valueType.equals("DATE"))
                type1(item, Tags.Date, sqTags1);
            else if (valueType.equals("TIME"))
                type1(item, Tags.Time, sqTags1);
            else if (valueType.equals("PNAME"))
                type1(item, Tags.PersonName, sqTags1);
            else if (valueType.equals("UIDREF"))
                type1(item, Tags.UID, sqTags1);
            else if (valueType.equals("TEXT"))
                type1(item, Tags.TextValue, sqTags1);
            else if (valueType.equals("CODE")) {
                type1(item, Tags.ConceptCodeSeq, sqTags1);
                checkCodeItem(item, ItemCount.SINGLE, Tags.ConceptCodeSeq,
                        sqTags1);
            } else if (valueType.equals("NUMERIC")) {
                type1(item, Tags.NumericValue, sqTags1);
                type1(item, Tags.MeasurementUnitsCodeSeq, sqTags1);
                checkCodeItem(item, ItemCount.SINGLE,
                        Tags.MeasurementUnitsCodeSeq, sqTags1);
            } else
                throw new DcmServiceException(Status.InvalidAttributeValue,
                        errorMessage("Invalid Value Type: " + valueType + " ",
                                Tags.ValueType, sqTags1));
        }
    }

    private static void shallBeEmpty(Dataset rqData, int tag, int... sqTags)
            throws DcmServiceException {
        type2(rqData, tag, sqTags);
        if (rqData.containsValue(tag)) {
            throw new DcmServiceException(Status.InvalidAttributeValue,
                    errorMessage("Shall be empty Attribute: ", tag, sqTags));
        }
    }

    private static void notAllowed(Dataset rqData, int tag)
            throws DcmServiceException {
        if (rqData.contains(tag))
            throw new DcmServiceException(Status.NoSuchAttribute,
                    errorMessage("Not allowed Attribute: ", tag));
    }

    private static void type1(Dataset rqData, int tag, int... sqTags)
        throws DcmServiceException {
        if (!rqData.containsValue(tag)) {
            type2(rqData, tag, sqTags);
            new DcmServiceException(
                    sqTags.length == 0 ? Status.MissingAttributeValue
                                       : Status.InvalidAttributeValue,
                    errorMessage("Missing Attribute Value: ", tag, sqTags));
        }
    }

    private static String errorMessage(String msg, int tag, int... sqTags) {
        StringBuffer sb = new StringBuffer(msg);
        if (sqTags != null && sqTags.length != 0)
            for (int sqTag : sqTags)
                Tags.toString(sb,sqTag).append('/');
        Tags.toString(sb,tag);
        return sb.toString();
    }

    private static void type2(Dataset rqData, int tag, int... sqTags)
            throws DcmServiceException {
        if (!rqData.contains(tag))
            new DcmServiceException(
                    sqTags.length == 0 ? Status.MissingAttribute
                                       : Status.InvalidAttributeValue,
                    errorMessage("Missing Attribute ", tag, sqTags));
    }

    private static boolean abstractSyntaxEquals(ActiveAssociation assoc,
            Dimse rq, String uid) {
        String asuid = assoc.getAssociation()
                .getProposedPresContext(rq.pcid()).getAbstractSyntaxUID();
        return asuid.equals(uid);
    }

    private static boolean abstractSyntaxEquals(ActiveAssociation assoc,
            Dimse rq, String uid1, String uid2) {
        String asuid = assoc.getAssociation()
                .getProposedPresContext(rq.pcid()).getAbstractSyntaxUID();
        return asuid.equals(uid1) || asuid.equals(uid2);
    }

    @Override
    protected Dataset doNSet(ActiveAssociation assoc, Dimse rq, Command rspCmd)
            throws IOException, DcmServiceException {
        String calledAET = assoc.getAssociation().getCalledAET();
        Command rqCmd = rq.getCommand();
        Dataset rqData = rq.getDataset();
        if (!abstractSyntaxEquals(assoc, rq,
                UIDs.UnifiedProcedureStepPullSOPClass))
            throw new DcmServiceException(UNRECOGNIZE_OPERATION);
        log.debug("Identifier:\n");
        log.debug(rqData);
        checkNSetRQ(rqCmd, rqData);
        initSPSModificationDateandTime(rqData);
        service.updateUPS(calledAET, rspCmd.getAffectedSOPInstanceUID(), rqData);
        return null;
    }

    private void checkNSetRQ(Command rqCmd, Dataset rqData)
            throws DcmServiceException {
        notAllowed(rqData, Tags.SOPClassUID);
        notAllowed(rqData, Tags.SOPInstanceUID);
        checkContentItem(rqData, Tags.ScheduledProcessingParametersSeq);
        checkCodeItem(rqData, ItemCount.MULTIPLE,
                Tags.ScheduledStationNameCodeSeq);
        checkCodeItem(rqData, ItemCount.MULTIPLE,
                Tags.ScheduledStationClassCodeSeq);
        checkCodeItem(rqData, ItemCount.MULTIPLE,
                Tags.ScheduledStationGeographicLocationCodeSeq);
        checkScheduledHumanPerformers(rqData);
        checkCodeItem(rqData, ItemCount.SINGLE, Tags.ScheduledWorkitemCodeSeq);
        checkImageSOPInstanceAndSourceReference(rqData,
                Tags.InputInformationSeq);
        notAllowed(rqData, Tags.PatientID);
        notAllowed(rqData, Tags.IssuerOfPatientID);
        notAllowed(rqData, Tags.TypeOfPatientID);
        notAllowed(rqData, Tags.OtherPatientIDSeq);
        notAllowed(rqData, Tags.PatientBirthDate);
        notAllowed(rqData, Tags.PatientSex);
        notAllowed(rqData, Tags.AdmissionID);
        notAllowed(rqData, Tags.IssuerOfAdmissionIDSeq);
        notAllowed(rqData, Tags.AdmittingDiagnosisDescription);
        notAllowed(rqData, Tags.AdmittingDiagnosisCodeSeq);
        notAllowed(rqData, Tags.RefRequestSeq);
        checkRelatedProcedureSteps(rqData);
        notAllowed(rqData, Tags.UPSState);
        checkUPSProgressInformation(rqData);
        checkUPSPerformedProcedure(rqData);
    }

    private void checkUPSProgressInformation(Dataset rqData) throws DcmServiceException {
        DcmElement sq = rqData.get(Tags.UPSProgressInformationSeq);
        if (sq == null || sq.isEmpty())
            return;
        int n = sq.countItems();
        if (n > 1)
            throw new DcmServiceException(Status.InvalidAttributeValue,
                    errorMessage("More than 1 item of Attribute: ",
                            Tags.UPSProgressInformationSeq));
        Dataset item = sq.getItem();
        checkUPSCommunicationsURI(item);
        checkCodeItem(item, ItemCount.MULTIPLE,
                Tags.UPSDiscontinuationReasonCodeSeq,
                Tags.UPSProgressInformationSeq);
    }


    private void checkUPSCommunicationsURI(Dataset progressInfo)
            throws DcmServiceException {
        DcmElement sq = progressInfo.get(Tags.UPSCommunicationsURISeq);
        if (sq == null)
            return;
        for (int i = 0, n = sq.countItems(); i < n; i++) {
            Dataset item = sq.getItem(i);
            type1(item, Tags.ContactURI, Tags.UPSProgressInformationSeq,
                    Tags.UPSCommunicationsURISeq);
        }
    }

    private void checkUPSPerformedProcedure(Dataset rqData)
            throws DcmServiceException {
        DcmElement sq = rqData.get(Tags.UPSPerformedProcedureSeq);
        if (sq == null || sq.isEmpty())
            return;
        int n = sq.countItems();
        if (n > 1)
            throw new DcmServiceException(Status.InvalidAttributeValue,
                    errorMessage("More than 1 item of Attribute: ",
                            Tags.UPSPerformedProcedureSeq));
        Dataset item = sq.getItem();
        checkActualHumanPerformers(item);
        checkCodeItem(item, ItemCount.MULTIPLE,
                Tags.PerformedStationNameCodeSeq,
                Tags.UPSPerformedProcedureSeq);
        checkCodeItem(item, ItemCount.MULTIPLE,
                Tags.PerformedStationClassCodeSeq,
                Tags.UPSPerformedProcedureSeq);
        checkCodeItem(item, ItemCount.MULTIPLE,
                Tags.PerformedStationGeographicLocationCodeSeq,
                Tags.UPSPerformedProcedureSeq);
        checkCodeItem(item, ItemCount.MULTIPLE,
                Tags.PerformedProcessingApplicationsCodeSeq,
                Tags.UPSPerformedProcedureSeq);
        checkCodeItem(item, ItemCount.SINGLE,
                Tags.PerformedWorkitemCodeSeq,
                Tags.UPSPerformedProcedureSeq);
        checkContentItem(item, Tags.PerformedProcessingParametersSeq,
                Tags.UPSPerformedProcedureSeq);
        checkImageSOPInstanceAndSourceReference(item,
                Tags.OutputInformationSeq, Tags.UPSPerformedProcedureSeq);
    }

    private static void checkActualHumanPerformers(Dataset performedProcedure)
            throws DcmServiceException {
        DcmElement sq = performedProcedure.get(Tags.ActualHumanPerformersSeq);
        if (sq == null) 
            return;
        for (int i = 0, n = sq.countItems(); i < n; i++) {
            Dataset item = sq.getItem(i);
            checkCodeItem(item, ItemCount.SINGLE, Tags.HumanPerformerCodeSeq,
                    Tags.ActualHumanPerformersSeq,
                    Tags.UPSPerformedProcedureSeq);
        }
    }

    @Override
    protected Dataset doNGet(ActiveAssociation assoc, Dimse rq, Command rspCmd)
            throws IOException, DcmServiceException {
        Command rqCmd = rq.getCommand();
        Dataset rqData = rq.getDataset();
        if (!abstractSyntaxEquals(assoc, rq,
                UIDs.UnifiedProcedureStepPullSOPClass,
                UIDs.UnifiedProcedureStepWatchSOPClass))
            throw new DcmServiceException(Status.UnrecognizedOperation);
        if (rqData != null)
            throw new DcmServiceException(Status.MistypedArgument,
                    "N-GET-RQ includes Data Set");
        return service.getUPS(rspCmd.getAffectedSOPInstanceUID(),
                rqCmd.getTags(Tags.AttributeIdentifierList));
    }

    @Override
    protected Dataset doNAction(ActiveAssociation assoc, Dimse rq,
            Command rspCmd) throws IOException, DcmServiceException {
        Association a = assoc.getAssociation();
        String calledAET = a.getCalledAET();
        String callingAET = a.getCallingAET();
        String receivingAET;
        Command rqCmd = rq.getCommand();
        Dataset rqData = rq.getDataset();
        int actionTypeID = rqCmd.getInt(Tags.ActionTypeID, 0);
        rspCmd.putUS(Tags.ActionTypeID, actionTypeID);
        String iuid = rqCmd.getRequestedSOPInstanceUID();
        String tuid;
        int state;
        boolean dellock;
        switch (actionTypeID) {
        case 1:
            if (abstractSyntaxEquals(assoc, rq,
                    UIDs.UnifiedProcedureStepPullSOPClass)) {
                type1(rqData, Tags.UPSState);
                state = UPSScpService.upsStateAsInt(rqData.getString(Tags.UPSState));
                if (state == UPSState.SCHEDULED)
                        throw new DcmServiceException(MAY_ONLY_BECOME_SCHEDULED_VIA_NCREATE);
                tuid = rqData.getString(Tags.TransactionUID);
                if (tuid == null)
                    throw new DcmServiceException(CORRECT_TRANSACTION_UID_NOT_PROVIDED);
                service.changeUPSState(calledAET, iuid, state, tuid);
                return rqData;
            }
            break;
        case 2:
            if (abstractSyntaxEquals(assoc, rq,
                    UIDs.UnifiedProcedureStepPushSOPClass,
                    UIDs.UnifiedProcedureStepWatchSOPClass)) {
                checkCodeItem(rqData, ItemCount.SINGLE, Tags.UPSDiscontinuationReasonCodeSeq);
                service.requestUPSCancel(calledAET, iuid, callingAET, rqData);
                return rqData;
            }
            break;
        case 3:
            if (abstractSyntaxEquals(assoc, rq,
                    UIDs.UnifiedProcedureStepWatchSOPClass)) {
                type1(rqData, Tags.ReceivingAE);
                type1(rqData, Tags.DeletionLock);
                receivingAET = rqData.getString(Tags.ReceivingAE);
                dellock = deletionLockAsBoolean(
                        rqData.getString(Tags.DeletionLock));
                if (iuid.equals(
                        UIDs.UnifiedWorklistandProcedureStepSOPInstance))
                    service.subscribeGlobally(calledAET, receivingAET, dellock);
                else
                    service.subscribeReceiveUPSEventReports(calledAET, iuid, receivingAET, dellock);
                return rqData;
            }
            break;
        case 4:
            if (abstractSyntaxEquals(assoc, rq,
                    UIDs.UnifiedProcedureStepWatchSOPClass)) {
                type1(rqData, Tags.ReceivingAE);
                receivingAET = rqData.getString(Tags.ReceivingAE);
                if (iuid.equals(
                        UIDs.UnifiedWorklistandProcedureStepSOPInstance))
                    service.unsubscribeGlobally(receivingAET);
                else
                    service.unsubscribeReceiveUPSEventReports(iuid, receivingAET);
                return rqData;
            }
            break;
        case 5:
            if (abstractSyntaxEquals(assoc, rq,
                    UIDs.UnifiedProcedureStepWatchSOPClass)
                    && iuid.equals(
                            UIDs.UnifiedWorklistandProcedureStepSOPInstance)) {
                type1(rqData, Tags.ReceivingAE);
                receivingAET = rqData.getString(Tags.ReceivingAE);
                service.suspendGlobalSubscription(receivingAET);
                return rqData;
            }
            break;
        }
        throw new DcmServiceException(Status.NoSuchActionType)
                .setActionTypeID(actionTypeID);
    }

    private static boolean deletionLockAsBoolean(String deletionLock)
            throws DcmServiceException {
        if ("TRUE".equals(deletionLock))
            return true;
        if ("FALSE".equals(deletionLock))
            return false;
        throw new DcmServiceException(Status.InvalidAttributeValue,
                "Illegal Deletion Lock Value: " + deletionLock);
    }
    

}
