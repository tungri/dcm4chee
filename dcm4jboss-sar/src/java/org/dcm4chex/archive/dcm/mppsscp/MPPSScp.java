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

package org.dcm4chex.archive.dcm.mppsscp;

import java.io.IOException;
import java.rmi.RemoteException;

import javax.ejb.CreateException;

import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.Association;
import org.dcm4che.net.DcmServiceBase;
import org.dcm4che.net.DcmServiceException;
import org.dcm4che.net.Dimse;
import org.dcm4chex.archive.common.PPSStatus;
import org.dcm4chex.archive.common.PrivateTags;
import org.dcm4chex.archive.ejb.interfaces.MPPSManager;
import org.dcm4chex.archive.ejb.interfaces.MPPSManagerHome;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.dcm4chex.archive.util.HomeFactoryException;
import org.jboss.logging.Logger;

/**
 * @author Gunter.Zeilinger@tiani.com
 * @version $Revision: 14845 $ $Date:: 2011-02-04 11:20:33#$
 */
public class MPPSScp extends DcmServiceBase {

    private static final String CREATE_XSL = "mpps-ncreaterq.xsl";
    private static final String SET_XSL = "mpps-nsetrq.xsl";
    private static final String CREATE_XML = "mpps-ncreaterq.xml";
    private static final String SET_XML = "mpps-nsetrq.xml";

    private static final int[] TYPE1_NCREATE_ATTR = {
            Tags.ScheduledStepAttributesSeq, Tags.PPSID,
            Tags.PerformedStationAET, Tags.PPSStartDate, Tags.PPSStartTime,
            Tags.PPSStatus, Tags.Modality};

    private static final int[] ONLY_NCREATE_ATTR = {
            Tags.ScheduledStepAttributesSeq, Tags.PatientName, Tags.PatientID,
            Tags.PatientBirthDate, Tags.PatientSex, Tags.PPSID,
            Tags.PerformedStationAET, Tags.PerformedStationName,
            Tags.PerformedLocation, Tags.PPSStartDate, Tags.PPSStartTime,
            Tags.Modality, Tags.StudyID};

    private static final int[] TYPE1_FINAL_ATTR = { Tags.PPSEndDate,
            Tags.PPSEndTime, Tags.PerformedSeriesSeq};

    private final MPPSScpService service;

	private final Logger log;

    public MPPSScp(MPPSScpService service) {
        this.service = service;
		this.log = service.getLog();
    }

    protected Dataset doNCreate(ActiveAssociation assoc, Dimse rq,
            Command rspCmd) throws IOException, DcmServiceException {
        Association as = assoc.getAssociation();
        String callingAET = as.getCallingAET();
        String calledAET = as.getCalledAET();
        final Command cmd = rq.getCommand();
        final Dataset mpps = rq.getDataset();
        final String cuid = cmd.getAffectedSOPClassUID();
        String iuid = cmd.getAffectedSOPInstanceUID();
        if (iuid == null) {
            iuid = rspCmd.getAffectedSOPInstanceUID();
        }
        log.debug("Creating MPPS:\n");
        log.debug(mpps);
        service.logDIMSE(as, CREATE_XML, mpps);
        Dataset coerce = service.getCoercionAttributesFor(callingAET,
                CREATE_XSL, mpps, as);
        if (coerce != null) {
            service.coerceAttributes(mpps, coerce);
        }
        checkCreateAttributs(mpps);
        if (!callingAET.equals(calledAET)) {
            service.ignorePatientIDForUnscheduled(mpps,
                    Tags.ScheduledStepAttributesSeq, callingAET);
            service.supplementIssuerOfPatientID(mpps, as, callingAET, false);
            service.generatePatientID(mpps,
                    mpps.getItem(Tags.ScheduledStepAttributesSeq), calledAET);
            DcmElement ssasq = mpps.get(Tags.ScheduledStepAttributesSeq);
            for (int i = 0, n = ssasq.countItems(); i < n; i++)
                service.supplementIssuerOfAccessionNumber(
                        ssasq.getItem(i), as, callingAET, false);
        }
        mpps.putUI(Tags.SOPClassUID, cuid);
        mpps.putUI(Tags.SOPInstanceUID, iuid);
        createMPPS(mpps);
        mpps.setPrivateCreatorID(PrivateTags.CreatorID);
        mpps.putAE(PrivateTags.CallingAET, callingAET);
        mpps.setPrivateCreatorID(null);
        service.sendMPPSNotification(mpps, MPPSScpService.EVENT_TYPE_MPPS_RECEIVED);
        return null;
    }

    private MPPSManager getMPPSManager()
            throws HomeFactoryException, RemoteException, CreateException {
        return ((MPPSManagerHome) EJBHomeFactory.getFactory().lookup(
                MPPSManagerHome.class, MPPSManagerHome.JNDI_NAME)).create();
    }

    
    private void createMPPS(Dataset mpps)
            throws DcmServiceException {
        try {
            getMPPSManager().createMPPS(mpps, service.patientMatching());
        } catch (DcmServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DcmServiceException(Status.ProcessingFailure, e);
        }
    }


    protected Dataset doNSet(ActiveAssociation assoc, Dimse rq, Command rspCmd)
            throws IOException, DcmServiceException {
        Association as = assoc.getAssociation();
        final String callingAET = as.getCallingAET();
        final Command cmd = rq.getCommand();
        final Dataset mpps = rq.getDataset();
        final String iuid = cmd.getRequestedSOPInstanceUID();
        log.debug("Set MPPS:\n");
        log.debug(mpps);
        service.logDIMSE(as, SET_XML, mpps);
        Dataset coerce = service.getCoercionAttributesFor(callingAET, SET_XSL,
                mpps, as);
        if (coerce != null) {
            service.coerceAttributes(mpps, coerce);
        }
        checkSetAttributs(mpps);
        mpps.putUI(Tags.SOPInstanceUID, iuid);
        updateMPPS(mpps);
        mpps.setPrivateCreatorID(PrivateTags.CreatorID);
        mpps.putAE(PrivateTags.CallingAET, callingAET);
        mpps.setPrivateCreatorID(null);
        service.sendMPPSNotification(mpps, MPPSScpService.EVENT_TYPE_MPPS_RECEIVED);
        return null;
    }
    
    private void updateMPPS(Dataset mpps)
            throws DcmServiceException {
        try {
            getMPPSManager().updateMPPS(mpps);
        } catch (DcmServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DcmServiceException(Status.ProcessingFailure, e);
        }
    }

	private void checkCreateAttributs(Dataset mpps) throws DcmServiceException {
        for (int i = 0; i < TYPE1_NCREATE_ATTR.length; ++i) {
            if (!mpps.containsValue(TYPE1_NCREATE_ATTR[i]))
                    throw new DcmServiceException(Status.MissingAttributeValue,
                            "Missing Type 1 Attribute "
                                    + Tags.toString(TYPE1_NCREATE_ATTR[i]));
        }
        DcmElement ssaSq = mpps.get(Tags.ScheduledStepAttributesSeq);
        for (int i = 0, n = ssaSq.countItems(); i < n; ++i) {
            if (!ssaSq.getItem(i).containsValue(Tags.StudyInstanceUID))
                    throw new DcmServiceException(Status.MissingAttributeValue,
                            "Missing Study Instance UID in Scheduled Step Attributes Seq.");
        }
        checkCommonAttributs(mpps);
        final String status = mpps.getString(Tags.PPSStatus);
        try {
            if (PPSStatus.toInt(status) == PPSStatus.IN_PROGRESS) {
                return;
            }
        } catch (IllegalArgumentException e) {}
        throw new DcmServiceException(Status.InvalidAttributeValue,
                "Invalid MPPS Status: " + status);
    }

    private void checkSetAttributs(Dataset mpps) throws DcmServiceException {
        for (int i = 0; i < ONLY_NCREATE_ATTR.length; ++i) {
            if (mpps.contains(ONLY_NCREATE_ATTR[i]))
                    throw new DcmServiceException(Status.ProcessingFailure,
                            "Cannot update attribute "
                                    + Tags.toString(ONLY_NCREATE_ATTR[i]));
        }
        checkCommonAttributs(mpps);
        final String status = mpps.getString(Tags.PPSStatus);
        try {
            if (status == null || PPSStatus.toInt(status) == PPSStatus.IN_PROGRESS) {
                return;
            }
        } catch (IllegalArgumentException e) {
                throw new DcmServiceException(Status.InvalidAttributeValue,
                        "Invalid MPPS Status: " + status);
        }
        for (int i = 0; i < TYPE1_FINAL_ATTR.length; ++i) {
            if (!mpps.containsValue(TYPE1_FINAL_ATTR[i]))
                    throw new DcmServiceException(Status.MissingAttributeValue,
                            "Missing Type 1 Attribute "
                                    + Tags.toString(TYPE1_FINAL_ATTR[i]));
        }
    }

    private void checkCommonAttributs(Dataset mpps) throws DcmServiceException {
        checkCode(mpps, Tags.ProcedureCodeSeq, true);
        checkCode(mpps, Tags.ReasonForPerformedProcedureCodeSeq, false);
        checkCode(mpps, Tags.PPSDiscontinuationReasonCodeSeq, true);
    }

    private void checkCode(Dataset mpps, int tag, boolean single)
            throws DcmServiceException {
        DcmElement codesq = mpps.get(tag);
        if (codesq == null)
            return;

        int n = codesq.countItems();
        if (n == 0)
            return;

        if (n > 1 && single)
            throw new DcmServiceException(Status.InvalidAttributeValue,
                    Tags.toString(tag) + " contains " + n + " items");

        for (int i = 0; i < n; i++) {
            Dataset code = codesq.getItem(i);
            if (!code.containsValue(Tags.CodeValue))
                throw new DcmServiceException(Status.MissingAttributeValue,
                        "Missing Code Value in item of " + Tags.toString(tag));

            if (!code.containsValue(Tags.CodingSchemeDesignator))
                throw new DcmServiceException(Status.MissingAttributeValue,
                        "Missing Coding Scheme Designator in item of "
                        + Tags.toString(tag));
        }
    }

}
