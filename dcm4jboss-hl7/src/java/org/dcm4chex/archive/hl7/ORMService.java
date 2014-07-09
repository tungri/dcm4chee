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

package org.dcm4chex.archive.hl7;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import javax.management.ObjectName;
import javax.xml.transform.Templates;

import org.apache.log4j.Logger;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.PersonName;
import org.dcm4che.dict.Tags;
import org.dcm4che2.audit.message.AuditMessage;
import org.dcm4che2.audit.message.InstancesAccessedMessage;
import org.dcm4che2.audit.message.ParticipantObject;
import org.dcm4che2.audit.message.ParticipantObjectDescription;
import org.dcm4che2.audit.message.ProcedureRecordMessage;
import org.dcm4che2.audit.message.AuditEvent.ActionCode;
import org.dcm4cheri.util.StringUtils;
import org.dcm4chex.archive.common.PPSStatus;
import org.dcm4chex.archive.common.PatientMatching;
import org.dcm4chex.archive.common.SPSStatus;
import org.dcm4chex.archive.ejb.interfaces.MPPSManager;
import org.dcm4chex.archive.ejb.interfaces.MPPSManagerHome;
import org.dcm4chex.archive.ejb.interfaces.MWLManager;
import org.dcm4chex.archive.ejb.interfaces.MWLManagerHome;
import org.dcm4chex.archive.exceptions.DuplicateMWLItemException;
import org.dcm4chex.archive.exceptions.PatientMergedException;
import org.dcm4chex.archive.exceptions.PatientMismatchException;
import org.dcm4chex.archive.mbean.HttpUserInfo;
import org.dcm4chex.archive.mbean.TemplatesDelegate;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.dcm4chex.archive.util.XSLTUtils;
import org.dom4j.Document;
import org.dom4j.Element;
import org.xml.sax.ContentHandler;

/**
 * @author gunter.zeilinger@tiani.com
 * @version $Revision: 18099 $ $Date: 2013-10-16 14:22:56 +0000 (Wed, 16 Oct 2013) $
 * @since 17.02.2005
 * 
 */

public class ORMService extends AbstractHL7Service {
    private TemplatesDelegate aeTemplates = new TemplatesDelegate(this);
    private static final String MWL2STORE_XSL = "mwl-cfindrsp2cstorerq.xsl";

    private static final String[] OP_CODES = { "NW", "XO", "XO(SCHEDULED)", "XO(COMPLETED)", "CA", "NOOP",
        "SC(SCHEDULED)", "SC(ARRIVED)", "SC(READY)", "SC(STARTED)",
        "SC(COMPLETED)", "SC(DISCONTINUED)" };
    
    private static final List<String> OP_CODES_LIST = Arrays.asList(OP_CODES);

    private static final int NW = 0;

    private static final int XO = 1;
    private static final int XO_SC = 2;
    private static final int XO_CM = 3;

    private static final int CA = 4;

    private static final int NOOP = 5;
    
    private static final int SC_OFF = 6;

    private List<String> orderControls;
    
    private int[] ops = {};
    
    private boolean updateDifferentPatientOfExistingStudy;
    private boolean createMissingOrderOnStatusChange;
    private boolean updateRequestAttributesForXO;
    
    private ObjectName deviceServiceName;

    protected String xslPath;

    private String defaultStationAET = "UNKOWN";

    private String defaultStationName = "UNKOWN";

    private String defaultModality = "OT";

    public PatientMatching patientMatching;

    public String getPatientMatching() {
        return patientMatching.toString();
    }

    public void setPatientMatching(String s) {
        this.patientMatching = new PatientMatching(s.trim());
    }

    public final String getStylesheet() {
        return xslPath;
    }

    public void setStylesheet(String path) {
        this.xslPath = path;
    }

    public final ObjectName getDeviceServiceName() {
        return deviceServiceName;
    }

    public final void setDeviceServiceName(ObjectName deviceServiceName) {
        this.deviceServiceName = deviceServiceName;
    }

    public String getOrderControlOperationMap() {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < ops.length; i++) {
            sb.append(orderControls.get(i)).append(':')
                .append(OP_CODES[ops[i]]).append("\r\n");
        }
        return sb.toString();
    }

    public void setOrderControlOperationMap(String s) {
        StringTokenizer stk = new StringTokenizer(s, " \r\n\t,;");
        int lines = stk.countTokens();
        int[] newops = new int[lines];
        List<String> newocs = new ArrayList<String>(lines);
        for (int i = 0; i < lines; i++) {
            String[] ocop = StringUtils.split(stk.nextToken(), ':');
            if (ocop.length != 2
                    || (newops[i] = OP_CODES_LIST.indexOf(ocop[1])) == -1) {
                throw new IllegalArgumentException(s);
            }
            newocs.add(ocop[0]);            
        }
        ops = newops;
        orderControls = newocs;
    }
    
    public boolean isCreateMissingOrderOnStatusChange() {
        return createMissingOrderOnStatusChange;
    }

    public void setCreateMissingOrderOnStatusChange(
            boolean createMissingOrderOnStatusChange) {
        this.createMissingOrderOnStatusChange = createMissingOrderOnStatusChange;
    }

    public final boolean isUpdateDifferentPatientOfExistingStudy() {
        return updateDifferentPatientOfExistingStudy;
    }

    public final void setUpdateDifferentPatientOfExistingStudy(boolean update) {
        this.updateDifferentPatientOfExistingStudy = update;
    }

    public boolean isUpdateRequestAttributesForXO() {
        return updateRequestAttributesForXO;
    }

    public void setUpdateRequestAttributesForXO(boolean updateRequestAttributesForXO) {
        this.updateRequestAttributesForXO = updateRequestAttributesForXO;
    }

    public final String getDefaultModality() {
        return defaultModality;
    }

    public final void setDefaultModality(String defaultModality) {
        this.defaultModality = defaultModality;
    }

    public final String getDefaultStationAET() {
        return defaultStationAET;
    }

    public final void setDefaultStationAET(String defaultStationAET) {
        this.defaultStationAET = defaultStationAET;
    }

    public final String getDefaultStationName() {
        return defaultStationName;
    }

    public final void setDefaultStationName(String defaultStationName) {
        this.defaultStationName = defaultStationName;
    }

    public final String getMWL2StoreConfigDir() {
        return aeTemplates.getConfigDir();
    }

    public final void setMWL2StoreConfigDir(String path) {
        aeTemplates.setConfigDir(path);
    }

    public boolean process(MSH msh, Document msg, ContentHandler hl7out, String[] xslSubdirs)
            throws HL7Exception {
        process(toOp(msg), msg, xslSubdirs);
        return true;
    }
    
    public void process(String orderControl, String orderStatus, Document msg, String[] xslSubdirs)
            throws HL7Exception {
        process(new int[] { toOp(orderControl, orderStatus) }, msg, xslSubdirs);
    }
    
    private void process(int op[], Document msg, String[] xslSubdirs) throws HL7Exception {
        try {
            Dataset ds = xslt(msg, xslPath, xslSubdirs);
            final String pid = ds.getString(Tags.PatientID);
            if (pid == null)
                throw new HL7Exception("AR",
                        "Missing required PID-3: Patient ID (Internal ID)");
            final String pname = ds.getString(Tags.PatientName);
            if (pname == null)
                throw new HL7Exception("AR",
                        "Missing required PID-5: Patient Name");
            mergeProtocolCodes(ds, op);
            ds = addScheduledStationInfo(ds);
            MWLManager mwlManager = getMWLManager();
            DcmElement spsSq = ds.remove(Tags.SPSSeq);
            Dataset sps;
            int opIdx;
            for (int i = 0, n = spsSq.countItems(); i < n; ++i) {
                sps = spsSq.getItem(i);
                ds.putSQ(Tags.SPSSeq).addItem(sps);
                adjustAttributes(ds);
                String status = null;
                opIdx = op.length == 1 ? 0 : i;
                switch (op[opIdx]) {
                case NW:
                	processNW(ds, mwlManager);
                    break;
                case XO:
                    processXO(ds, mwlManager);
                    break;
                case XO_SC:
                    processXO(ds, mwlManager);
                    status = SPSStatus.toString(SPSStatus.SCHEDULED);
                    break;
                case XO_CM:
                    processXO(ds, mwlManager);
                    status = SPSStatus.toString(SPSStatus.COMPLETED);
                    break;
                case CA:
                	processCA(ds, mwlManager);
                	break;
                case NOOP:
                    log("NOOP", ds);
                    break;
                default:
                    status = SPSStatus.toString(op[opIdx]-SC_OFF);
                    break;
                }
                if (status != null) {
                    sps.putCS(Tags.SPSStatus, status);
                    updateSPSStatus(ds, mwlManager, createMissingOrderOnStatusChange);
                }
            }
        } catch (HL7Exception e) {
            throw e;
        } catch (PatientMismatchException e) {
            throw new HL7Exception("AR", e.getMessage(), e);
        } catch (PatientMergedException e) {
            throw new HL7Exception("AR", e.getMessage(), e);
        } catch (DuplicateMWLItemException e) {
            throw new HL7Exception("AR", e.getMessage(), e);
        } catch (Exception e) {
            throw new HL7Exception("AE", e.getMessage(), e);
        }
    }

    protected void processNW(Dataset ds, MWLManager mwlManager) throws Exception {
        addMissingAttributes(ds);
        log("Schedule", ds);
        logDataset("Insert MWL Item:", ds);
        mwlManager.addWorklistItem(ds, patientMatching);
        updateRequestAttributes(ds, mwlManager);
    }

    protected void processXO(Dataset ds, MWLManager mwlManager) throws Exception {
        log("Update", ds);
        logDataset("Update MWL Item:", ds);
        if (!mwlManager.updateWorklistItem(ds, patientMatching)) {
            log("No Such ", ds);
            addMissingAttributes(ds);
            log("->Schedule New ", ds);
            logDataset("Insert MWL Item:", ds);
            mwlManager.addWorklistItem(ds, patientMatching);
            updateRequestAttributes(ds, mwlManager);
        } else if (updateRequestAttributesForXO) {
            updateRequestAttributes(ds, mwlManager);
        }
    }

    protected void processCA(Dataset ds, MWLManager mwlManager) throws Exception {
        log("Cancel", ds);
        if (mwlManager.removeWorklistItem(ds, patientMatching) == null) {
            log("No Such ", ds);
        } else {
            ds.getItem(Tags.SPSSeq).putCS(Tags.SPSStatus, "DISCONTINUED");
            updateRequestAttributes(ds, mwlManager);
        }
    }

    @SuppressWarnings("unchecked")
    private void updateRequestAttributes(Dataset mwlitem,
            MWLManager mwlManager) throws Exception {
        MPPSManager mppsManager = getMPPSManager();
        List<Dataset>[] mppsAndMovedStudies = 
            mppsManager.updateScheduledStepAttributes(mwlitem,
                    patientMatching, updateDifferentPatientOfExistingStudy);
        if (mppsAndMovedStudies != null) {
            if ("DISCONTINUED".equals(mwlitem.getItem(Tags.SPSSeq).getString(Tags.SPSStatus))) {
                HashSet<String> seriesIuids = new HashSet<String>();
                for (Dataset mpps : mppsAndMovedStudies[0]) {
                    DcmElement perfSeriesSq = mpps.get(Tags.PerformedSeriesSeq);
                    if (perfSeriesSq != null && !perfSeriesSq.isEmpty()) {
                        for (int i = 0, n = perfSeriesSq.countItems(); i < n; i++) {
                            seriesIuids.add(perfSeriesSq.getItem(i).getString(Tags.SeriesInstanceUID));
                        }
                    }
                }
                mppsManager.removeRequestAttributesInSeries(mwlitem, seriesIuids);
            } else {
                updateSPSStatus(mwlitem, mppsAndMovedStudies[0].get(0), mwlManager);
                updateRequestAttributesInSeries(mwlitem, mppsAndMovedStudies[0], mppsManager);
            }
            logLinkingAction(mppsAndMovedStudies[0], mwlitem);
            if (mppsAndMovedStudies[1] != null && mppsAndMovedStudies[1].size() > 0) 
                logStudyMoved(mppsAndMovedStudies[1]);
        }
    }

    private void updateRequestAttributesInSeries(Dataset mwlitem,
            List<Dataset> mppsList, MPPSManager mppsManager) throws Exception {
        for (Dataset mpps : mppsList) {
            String aet = mpps.getString(Tags.PerformedStationAET);
            Templates xslt = null;
            try {
                if ( aeTemplates.getTemplatesServiceName() == null) {
                    aeTemplates.setTemplatesServiceName(this.getTemplatesServiceName());
                }
                xslt = aeTemplates.getTemplatesForAET(aet, MWL2STORE_XSL);
            } catch (Exception x) {
                log.error("Internal error to get template for "+MWL2STORE_XSL, x);
            }
            if (xslt == null) {
                log.warn("Failed to find or load stylesheet "
                            + MWL2STORE_XSL
                            + " for "
                            + aet
                            + ". Cannot update object attributes with request information.");
                continue;
            }
            Dataset rqAttrs = DcmObjectFactory.getInstance().newDataset();
            XSLTUtils.xslt(mwlitem, xslt, rqAttrs);
            DcmElement perfSeriesSq = mpps.get(Tags.PerformedSeriesSeq);
            if (perfSeriesSq != null && !perfSeriesSq.isEmpty()) {
                for (int i = 0, n = perfSeriesSq.countItems(); i < n; i++) {
                    String uid = perfSeriesSq.getItem(i)
                            .getString(Tags.SeriesInstanceUID);
                    updateSeriesAttributes(mppsManager, uid , rqAttrs, i == 0);
                }
            }
        }
    }
    
    protected void updateSeriesAttributes(MPPSManager mppsManager, String uid, Dataset newAttrs,
        boolean updateStudyAttributes) {
        try {
            mppsManager.updateSeriesAttributes(uid, newAttrs, updateStudyAttributes);
        } catch (Exception x) {
            log.warn("Failed to update series attributes! Series IUID:"+uid+" - Reason:"+x);
        }
    }

    protected void updateSPSStatus(Dataset mwlitem, Dataset mpps,
            MWLManager mwlManager) throws PatientMismatchException,
            RemoteException {
        String spsStatus;
        Dataset sps = mwlitem.get(Tags.SPSSeq).getItem();
        switch (PPSStatus.toInt(mpps.getString(Tags.PPSStatus))) {
        case PPSStatus.IN_PROGRESS:
            spsStatus = "STARTED";
            break;
        case PPSStatus.COMPLETED:
            spsStatus = "COMPLETED";
            break;
        default: // PPSStatus.DISCONTINUED
            spsStatus = "DISCONTINUED";
            break;
        }
        sps.putCS(Tags.SPSStatus, spsStatus);
        updateSPSStatus(mwlitem, mwlManager, false);
    }

    protected void updateSPSStatus(Dataset ds, MWLManager mwlManager, boolean createMissingOrder)
            throws PatientMismatchException, RemoteException {
        log("Change SPS status of MWL Item:", ds);
        if (!mwlManager.updateSPSStatus(ds, patientMatching)) {
            log("No Such ", ds);
            if (createMissingOrder) {
                addMissingAttributes(ds);
                log("->Create new MWL Item with status "+ds.getString(Tags.SPSStatus)+":", ds);
                logDataset("Insert MWL Item:", ds);
                try {
                    mwlManager.addWorklistItem(ds, patientMatching);
                    updateRequestAttributes(ds, mwlManager);
                } catch (Exception x) {
                    log.warn("Create missing order on Status Change (SC) failed!", x);
                }
            }
        }
    }

    private void log(String op, Dataset ds) {
        Dataset sps = ds.getItem(Tags.SPSSeq);
        log.info(op
                + " Procedure Step[id:"
                + (sps == null ? "<unknown>(SPSSeq missing)"
                               : sps.getString(Tags.SPSID))
                + "] of Requested Procedure[id:"
                + ds.getString(Tags.RequestedProcedureID) + ", uid:"
                + ds.getString(Tags.StudyInstanceUID) + "] of Order[accNo:"
                + ds.getString(Tags.AccessionNumber) + "] for Patient [name:"
                + ds.getString(Tags.PatientName) + ",id:"
                + ds.getString(Tags.PatientID) + "]");
    }

    private MWLManager getMWLManager() throws Exception {
        return ((MWLManagerHome) EJBHomeFactory.getFactory().lookup(
                MWLManagerHome.class, MWLManagerHome.JNDI_NAME)).create();
    }

    private MPPSManager getMPPSManager() throws Exception {
        return ((MPPSManagerHome) EJBHomeFactory.getFactory().lookup(
                MPPSManagerHome.class, MPPSManagerHome.JNDI_NAME)).create();
    }

    @SuppressWarnings("unchecked")
    private int[] toOp(Document msg) throws HL7Exception {
        Element rootElement = msg.getRootElement();
        List<Element> orcs = rootElement.elements("ORC");
        List<Element> obrs = rootElement.elements("OBR");
        if (orcs.isEmpty()) {
            throw new HL7Exception("AR", "Missing ORC Segment");                             
        }
        int[] op = new int[orcs.size()];
        for (int i = 0; i < op.length; i++) {           
            List<Element> orc = orcs.get(i).elements("field");
            String orderControl = getText(orc, 0);
            String orderStatus = getText(orc, 4);
            if (orderStatus.length() == 0 && obrs.size() > i) {
                // use Result Status (OBR-25), if no Order Status (ORC-5);
                List<Element> obr = obrs.get(i).elements("field");
                orderStatus = getText(obr, 24);
            }
            op[i] = toOp(orderControl, orderStatus);
        }
        return op;
    }

    private int toOp(String orderControl, String orderStatus)
            throws HL7Exception {
        int opIndex = orderControls.indexOf(orderControl + "(" + orderStatus + ")");
        if (opIndex == -1) {
            opIndex = orderControls.indexOf(orderControl);
            if (opIndex == -1) {
                throw new HL7Exception("AR", "Illegal Order Control Code ORC-1:"
                        + orderControl);
            }
        }
        return ops[opIndex];
    }

    private String getText(List<Element> fields, int i) throws HL7Exception {
        try {
            return fields.get(i).getText();
        } catch (IndexOutOfBoundsException e) {
            return "";
        }
    }

    private Dataset addScheduledStationInfo(Dataset spsItems) throws Exception {
        return (Dataset) server.invoke(deviceServiceName,
                "addScheduledStationInfo", new Object[] { spsItems },
                new String[] { Dataset.class.getName() });
    }

    private void addMissingAttributes(Dataset ds) {
        Dataset sps = ds.getItem(Tags.SPSSeq);
        if (!sps.containsValue(Tags.ScheduledStationAET)) {
            log.info("No Scheduled Station AET - use default: "
                    + defaultStationAET);
            sps.putAE(Tags.ScheduledStationAET, defaultStationAET);
        }
        if (!sps.containsValue(Tags.ScheduledStationName)) {
            log.info("No Scheduled Station Name - use default: "
                    + defaultStationName);
            sps.putSH(Tags.ScheduledStationName, defaultStationName);
        }
        if (!sps.containsValue(Tags.Modality)) {
            log.info("No Modality - use default: " + defaultModality);
            sps.putCS(Tags.Modality, defaultModality);
        }
        if (!sps.containsValue(Tags.SPSStartDate)) {
            log.info("No SPS Start Date - use current date/time");
            Date now = new Date();
            sps.putDA(Tags.SPSStartDate, now);
            sps.putTM(Tags.SPSStartTime, now);
        }
    }

    private void adjustAttributes(Dataset ds) {
        Dataset sps = ds.getItem(Tags.SPSSeq);
        String val;
        Dataset code;
        if ((val = sps.getString(Tags.RequestingPhysician)) != null) {
            log.info("Detect Requesting Physician on SPS Level");
            ds.putPN(Tags.RequestingPhysician, val);
            sps.remove(Tags.RequestingPhysician);
        }
        if ((val = sps.getString(Tags.RequestingService)) != null) {
            log.info("Detect Requesting Service on SPS Level");
            ds.putLO(Tags.RequestingService, val);
            sps.remove(Tags.RequestingService);
        }
        if ((val = sps.getString(Tags.StudyInstanceUID)) != null) {
            log.info("Detect Study Instance UID on SPS Level");
            ds.putUI(Tags.StudyInstanceUID, val);
            sps.remove(Tags.StudyInstanceUID);
        }
        if ((val = sps.getString(Tags.AccessionNumber)) != null) {
            log.info("Detect Accession Number on SPS Level");
            ds.putSH(Tags.AccessionNumber, val);
            sps.remove(Tags.AccessionNumber);
        }
        if ((val = sps.getString(Tags.RequestedProcedurePriority)) != null) {
            log.info("Detect Requested Procedure Priority on SPS Level");
            ds.putCS(Tags.RequestedProcedurePriority, val);
            sps.remove(Tags.RequestedProcedurePriority);
        }
        if ((val = sps.getString(Tags.RequestedProcedureID)) != null) {
            log.info("Detect Requested Procedure ID on SPS Level");
            ds.putSH(Tags.RequestedProcedureID, val);
            sps.remove(Tags.RequestedProcedureID);
        }
        if ((val = sps.getString(Tags.RequestedProcedureDescription)) != null) {
            log.info("Detect Requested Procedure Description on SPS Level");
            ds.putLO(Tags.RequestedProcedureDescription, val);
            sps.remove(Tags.RequestedProcedureDescription);
        }
        if ((code = sps.getItem(Tags.RequestedProcedureCodeSeq)) != null) {
            log.info("Detect Requested Procedure Code on SPS Level");
            ds.putSQ(Tags.RequestedProcedureCodeSeq).addItem(code);
            sps.remove(Tags.RequestedProcedureCodeSeq);
        }
    }

    private void mergeProtocolCodes(Dataset orm, int[] op) {
        DcmElement prevSpsSq = orm.remove(Tags.SPSSeq);
        DcmElement newSpsSq = orm.putSQ(Tags.SPSSeq);
        HashMap<String,DcmElement> spcSqMap = new HashMap<String,DcmElement>();
        DcmElement spcSq0, spcSqI;
        Dataset sps;
        String spsid;
        for (int i = 0, j = 0, n = prevSpsSq.countItems(); i < n; ++i) {
            sps = prevSpsSq.getItem(i);
            spsid = sps.getString(Tags.SPSID);
            spcSqI = sps.get(Tags.ScheduledProtocolCodeSeq);
            spcSq0 = spcSqMap.get(spsid);
            if (spcSq0 != null) {
                spcSq0.addItem(spcSqI.getItem());
            } else {
                spcSqMap.put(spsid, spcSqI);
                newSpsSq.addItem(sps);
                if ( op.length > 1 ) 
                    op[j++] = op[i];
            }
        }
    }
    
    private void logLinkingAction(List<Dataset> mppsList, Dataset mwlitem) {
        try {
            DcmElement spsSQ = mwlitem.get(Tags.SPSSeq);
            String spsID = spsSQ.getItem().getString(Tags.SPSID);
            String accNr = mwlitem.getString(Tags.AccessionNumber);
            StringBuffer sb = new StringBuffer();
            sb.append("ORM result: ").append(
               "DISCONTINUED".equals(mwlitem.getItem(Tags.SPSSeq).getString(Tags.SPSStatus)) ? 
                       "Unlink" : "Link")
               .append(" SPS ID: ").append(spsID).append(" and ");
            int baseLen = sb.length();
            for (Dataset mpps : mppsList) {
                sb.append("MPPS iuid:").append(mpps.getString(Tags.SOPInstanceUID));
                if (mpps.contains(Tags.ModifiedAttributesSeq)) {
                    logProcedureRecord(mpps, accNr, ProcedureRecordMessage.UPDATE, sb.toString());
                }
                sb.setLength(baseLen);
            }
        } catch (Exception x) {
            log.warn("Failed to audit linking action!", x);
        }
    }
    private void logProcedureRecord(Dataset mppsAttrs, String accNr, ActionCode actionCode,
            String desc) {
        HttpUserInfo userInfo = new HttpUserInfo(AuditMessage
                .isEnableDNSLookups());
        if ( log.isDebugEnabled()) {
            log.debug("log Procedure Record! actionCode:" + actionCode);
            log.debug("mppsAttrs:");log.debug(mppsAttrs);
        }
        try {
            ProcedureRecordMessage msg = new ProcedureRecordMessage(actionCode);
            msg.addUserPerson(userInfo.getUserId(), null, null, userInfo
                    .getHostName(), true);
            PersonName pn = mppsAttrs.getPersonName(Tags.PatientName);
            String pname = pn != null ? pn.format() : null;
            msg.addPatient(mppsAttrs.getString(Tags.PatientID), pname);
            ParticipantObjectDescription poDesc = new ParticipantObjectDescription();
            if (accNr != null)
                poDesc.addAccession(accNr);
            ParticipantObject study = msg.addStudy(mppsAttrs.getItem(Tags.ScheduledStepAttributesSeq).getString(Tags.StudyInstanceUID),
                    poDesc);
            study.addParticipantObjectDetail("Description", desc);
            msg.validate();
            Logger.getLogger("auditlog").info(msg);
        } catch (Exception x) {
            log.warn("Audit Log 'Procedure Record' failed:", x);
        }
    }

    private void logStudyMoved(List<Dataset> movedStudies) {
        try {
            HttpUserInfo userInfo = new HttpUserInfo(AuditMessage
                    .isEnableDNSLookups());
            InstancesAccessedMessage msg = new InstancesAccessedMessage(
                    InstancesAccessedMessage.UPDATE);
            msg.addUserPerson(userInfo.getUserId(), null, null, userInfo.getHostName(), true);
            Dataset studyDs = movedStudies.get(0);
            PersonName pn = studyDs.getPersonName(Tags.PatientName);
            String pname = pn != null ? pn.format() : null;
            msg.addPatient(studyDs.getString(Tags.PatientID), pname);
            ParticipantObject study;
            for (int i = 0, len = movedStudies.size(); i < len ; i++) {
                studyDs = movedStudies.get(i);
                study = msg.addStudy(studyDs.getString(Tags.StudyInstanceUID), null);
                study.addParticipantObjectDetail("Description", "Study moved to Patient");
            }
            msg.validate();
            Logger.getLogger("auditlog").info(msg);
        } catch (Exception x) {
            log.warn("Failed to audit study moved action!", x);
        }
    }

}
