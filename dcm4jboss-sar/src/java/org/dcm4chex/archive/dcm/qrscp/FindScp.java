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

package org.dcm4chex.archive.dcm.qrscp;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.ObjectName;
import javax.security.auth.Subject;
import javax.xml.transform.Templates;

import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.net.AAssociateAC;
import org.dcm4che.net.AAssociateRQ;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.Association;
import org.dcm4che.net.AssociationListener;
import org.dcm4che.net.DcmServiceBase;
import org.dcm4che.net.DcmServiceException;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.DimseListener;
import org.dcm4che.net.ExtNegotiation;
import org.dcm4che.net.PDU;
import org.dcm4chex.archive.common.PIDWithIssuer;
import org.dcm4chex.archive.ejb.jdbc.QueryCmd;
import org.dcm4chex.archive.perf.PerfCounterEnum;
import org.dcm4chex.archive.perf.PerfMonDelegate;
import org.dcm4chex.archive.perf.PerfPropertyEnum;
import org.jboss.logging.Logger;

/**
 * @author Gunter.Zeilinger@tiani.com
 * @version $Revision: 17758 $ $Date: 2008-02-27 22:38:35 +0100 (Wed, 27 Feb
 *          2008) $
 * @since 31.08.2003
 */
public class FindScp extends DcmServiceBase implements AssociationListener {
    protected static final int PID = 0;
    protected static final int ISSUER = 1;
    private static final int FUZZY_MATCHING = 2;
    private static final String QUERY_XSL = "cfindrq.xsl";
    private static final String RESULT_XSL = "cfindrsp.xsl";
    private static final String QUERY_XML = "-cfindrq.xml";
    private static final String RESULT_XML = "-cfindrsp.xml";
    
    protected static final String FORCE_PIX_QUERY_FLAG = "ForcePIXQueryFlag";

    private static final MultiDimseRsp NO_MATCH_RSP = new MultiDimseRsp() {

        public DimseListener getCancelListener() {
            return null;
        }

        public Dataset next(ActiveAssociation assoc, Dimse rq, Command rspCmd)
                throws DcmServiceException {
            rspCmd.putUS(Tags.Status, Status.Success);
            return null;
        }

        public void release() {
        }
    };

    protected final QueryRetrieveScpService service;

    protected final boolean filterResult;

    protected final Logger log;

    private PerfMonDelegate perfMon;

    public FindScp(QueryRetrieveScpService service, boolean filterResult) {
        this.service = service;
        this.log = service.getLog();
        this.filterResult = filterResult;
        perfMon = new PerfMonDelegate(this.service);
    }

    public final ObjectName getPerfMonServiceName() {
        return perfMon.getPerfMonServiceName();
    }

    public final void setPerfMonServiceName(ObjectName perfMonServiceName) {
        perfMon.setPerfMonServiceName(perfMonServiceName);
    }

    protected MultiDimseRsp doCFind(ActiveAssociation assoc, Dimse rq,
            Command rspCmd) throws IOException, DcmServiceException {
        Association a = assoc.getAssociation();
        String callingAET = a.getCallingAET();
        try {
            perfMon.start(assoc, rq, PerfCounterEnum.C_FIND_SCP_QUERY_DB);

            Command rqCmd = rq.getCommand();
            Dataset rqData = rq.getDataset();
            perfMon.setProperty(assoc, rq, PerfPropertyEnum.REQ_DIMSE, rq);

            if (log.isDebugEnabled()) {
                log.debug("Identifier:\n");
                log.debug(rqData);
            }

            service.logDIMSE(a, QUERY_XML, rqData);
            service.logDicomQuery(a, rq.getCommand().getAffectedSOPClassUID(),
                    rqData);
            Dataset coerce = service.getCoercionAttributesFor(callingAET,
                    QUERY_XSL, rqData, a);
            if (coerce != null) {
                service.coerceAttributes(rqData, coerce);
            }
            service.postCoercionProcessing(rqData, Command.C_FIND_RQ, assoc.getAssociation());
            int[] excludeFromRSP = excludeFromRSP(rqData);
            service.supplementIssuerOfPatientID(rqData, a, callingAET, true);

            Dataset issuerOfAccessionNumberInRQ = rqData.getItem(Tags.IssuerOfAccessionNumberSeq);
            if (!"PATIENT".equals(rqData.getString(Tags.QueryRetrieveLevel)))
                service.supplementIssuerOfAccessionNumber(rqData, a, callingAET, true);

            boolean pixQuery = forcePixQuery(assoc)
                    || service.isPixQueryCallingAET(callingAET);
            Set<PIDWithIssuer> pidWithIssuer = pixQuery ? pixQuery(rqData) : null;
            boolean adjustPatientID = pixQuery && pidWithIssuer == null
                    && rqData.containsValue(Tags.IssuerOfPatientID);
            // return OtherPatientIDs needed to adjust Patient IDs
            rqData.putSQ(Tags.OtherPatientIDSeq);

            boolean hideWithoutIssuerOfPID = 
                    service.isHideWithoutIssuerOfPIDFromAET(callingAET);
            boolean fuzzyMatchingOfPN = fuzzyMatchingOfPN(
                    a.getAcceptedExtNegotiation(
                            rqCmd.getAffectedSOPClassUID()));
            MultiDimseRsp rsp;
            if (service.hasUnrestrictedQueryPermissions(callingAET)) {
                rsp = newMultiCFindRsp(rqData, pidWithIssuer, adjustPatientID,
                        excludeFromRSP, issuerOfAccessionNumberInRQ,
                        fuzzyMatchingOfPN, hideWithoutIssuerOfPID, null);
            } else {
                Subject subject = (Subject) a.getProperty("user");
                if (subject != null) {
                    rsp = newMultiCFindRsp(rqData, pidWithIssuer, adjustPatientID,
                            excludeFromRSP, issuerOfAccessionNumberInRQ,
                            fuzzyMatchingOfPN, hideWithoutIssuerOfPID, subject);
                } else {
                    log
                            .info("Missing user identification -> no records returned");
                    rsp = NO_MATCH_RSP;
                }
            }
            perfMon.stop(assoc, rq, PerfCounterEnum.C_FIND_SCP_QUERY_DB);
            return rsp;
        } catch (DcmServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Query DB failed:", e);
            throw new DcmServiceException(Status.UnableToProcess, e);
        }
    }

    private static final int[] EXCLUDE_FROM_RSP_TAGS = {
        Tags.OtherPatientIDSeq,
        Tags.IssuerOfPatientID,
        Tags.IssuerOfAccessionNumberSeq
    };
    
    private static int[] excludeFromRSP(Dataset rqData) {
        int count = 0;
        boolean[] exclude = new boolean[EXCLUDE_FROM_RSP_TAGS.length];
        for (int i = 0; i < EXCLUDE_FROM_RSP_TAGS.length; i++) {
            if (exclude[i] = !rqData.contains(EXCLUDE_FROM_RSP_TAGS[i]))
                count++;
        }
        int[] tags = new int[count];
        for (int i = 0, j = 0; i < exclude.length; i++) {
            if (exclude[i])
                tags[j++] = EXCLUDE_FROM_RSP_TAGS[i];
        }
        return tags;
    }

    private boolean forcePixQuery(ActiveAssociation assoc) {
        Object flag = assoc.getAssociation().getProperty(FORCE_PIX_QUERY_FLAG);
        return (flag instanceof Boolean) && ((Boolean) flag).booleanValue();
    }

    private boolean fuzzyMatchingOfPN(ExtNegotiation extNeg) {
        byte[] info;
        return extNeg != null 
                && (info = extNeg.info()).length > FUZZY_MATCHING
                && info[FUZZY_MATCHING] != 0;
    }

    private boolean isUniversalMatching(String key) {
        if (key == null) {
            return true;
        }
        char[] a = key.toCharArray();
        for (int i = 0; i < a.length; i++) {
            if (a[i] != '*') {
                return false;
            }
        }
        return true;
    }

    private boolean isWildCardMatching(String key) {
        return key.indexOf('*') != -1 || key.indexOf('?') != -1;
    }

    protected Set<PIDWithIssuer> pixQuery(Dataset rqData) throws DcmServiceException {
        HashSet<PIDWithIssuer> result = new HashSet<PIDWithIssuer>();
        pixQuery(rqData, result);
        DcmElement opidsq = rqData.get(Tags.OtherPatientIDSeq);
        if (opidsq != null) {
            for (int i = 0, n = opidsq.countItems(); i < n; i++)
                pixQuery(opidsq.getItem(i), result);
        }

        return result.isEmpty() ? null : result;
    }

    protected boolean skipPixQuery(String pid,String issuer) throws DcmServiceException {
        return isUniversalMatching(pid)
                || !service.isPixQueryIssuer(issuer)
                || isWildCardMatching(pid) && !service.isPixQueryLocal();
    }

    protected void pixQuery(Dataset rqData, Set<PIDWithIssuer> result)
            throws DcmServiceException {
        String pid = rqData.getString(Tags.PatientID);
        String issuer = rqData.getString(Tags.IssuerOfPatientID);
        if (skipPixQuery(pid, issuer) )
            return;

        List<String[]> pidAndIssuers =
                service.queryCorrespondingPIDs(pid, issuer);
        if (pidAndIssuers == null)
            // pid was not known by pix manager.
            return;

        for (String[] pidAndIssuer : pidAndIssuers)
            result.add(new PIDWithIssuer(pidAndIssuer[0], pidAndIssuer[1]));
    }

    protected MultiDimseRsp newMultiCFindRsp(Dataset rqData,
            Set<PIDWithIssuer> pidWithIssuers, boolean adjustPatientIDs,
            int[] excludeFromRSP, Dataset issuerOfAccessionNumberInRQ,
            boolean fuzzyMatchingOfPN, boolean hideWithoutIssuerOfPID,
            Subject subject)
            throws SQLException, DcmServiceException {
        QueryCmd queryCmd = QueryCmd.create(rqData, pidWithIssuers,
                filterResult, fuzzyMatchingOfPN, service.isNoMatchForNoValue(),
                hideWithoutIssuerOfPID, subject);
        if (filterResult && issuerOfAccessionNumberInRQ != null)
            // restore Issuer of Accession Number Sequence to value in request
            // to return only requested item attributes 
            rqData.putSQ(Tags.IssuerOfAccessionNumberSeq)
                    .addItem(issuerOfAccessionNumberInRQ);
        queryCmd.setFetchSize(service.getFetchSize()).execute();
        return new MultiCFindRsp(queryCmd, adjustPatientIDs,
                rqData.getString(Tags.IssuerOfPatientID), excludeFromRSP);
    }

    protected Dataset getDataset(QueryCmd queryCmd) throws SQLException,
            DcmServiceException {
        return queryCmd.getDataset();
    }

    protected void doBeforeRsp(ActiveAssociation assoc, Dimse rsp) {
        if (log.isDebugEnabled())
        {
        	if (service.isCFindRspDebugLogDeferToDoBeforeRsp()) {
        		try {
        			Dataset ds = rsp.getDataset();
        			if (ds != null) {
        				log.debug("Identifier:\n");
        				log.debug(ds);
        			}
        		} catch (IOException iOException) {
        			log.error("Failed to debug log C-Find response", iOException);
        		}
        	}
        }
    }

    protected void doMultiRsp(ActiveAssociation assoc, Dimse rq,
            Command rspCmd, MultiDimseRsp mdr) throws IOException,
            DcmServiceException {
        try {
            DimseListener cl = mdr.getCancelListener();
            if (cl != null) {
                assoc.addCancelListener(
                        rspCmd.getMessageIDToBeingRespondedTo(), cl);
            }

            do {
                perfMon.start(assoc, rq, PerfCounterEnum.C_FIND_SCP_RESP_OUT);

                Dataset rspData = mdr.next(assoc, rq, rspCmd);
                Dimse rsp = fact.newDimse(rq.pcid(), rspCmd, rspData);
                doBeforeRsp(assoc, rsp);
                assoc.getAssociation().write(rsp);

                perfMon.setProperty(assoc, rq, PerfPropertyEnum.RSP_DATASET,
                        rspData);
                perfMon.stop(assoc, rq, PerfCounterEnum.C_FIND_SCP_RESP_OUT);

                doAfterRsp(assoc, rsp);
            } while (rspCmd.isPending());
        } finally {
            assoc.removeCancelListener(rspCmd.getMessageIDToBeingRespondedTo());
            mdr.release();
        }
    }

    protected class MultiCFindRsp implements MultiDimseRsp {

        private final QueryCmd queryCmd;
        private final boolean adjustPatientIDs;
        private final String requestedIssuer;
        private final int[] excludeFromRSP;
        private final Map<PIDWithIssuer, Set<PIDWithIssuer>> pixQueryResults;
        private boolean canceled = false;
        private final int pendingStatus;
        private int count = 0;
        private Templates coerceTpl;

        public MultiCFindRsp(QueryCmd queryCmd, boolean adjustPatientIDs,
                String requestedIssuer, int[] excludeFromRSP) {
            this.queryCmd = queryCmd;
            this.adjustPatientIDs = adjustPatientIDs;
            this.requestedIssuer = requestedIssuer;
            this.excludeFromRSP = excludeFromRSP;
            this.pixQueryResults = adjustPatientIDs
                        ? new HashMap<PIDWithIssuer, Set<PIDWithIssuer>>()
                        : null;
            this.pendingStatus = queryCmd.isKeyNotSupported() ? 0xff01 : 0xff00;
        }

        public DimseListener getCancelListener() {
            return new DimseListener() {

                public void dimseReceived(Association assoc, Dimse dimse) {
                    canceled = true;
                }
            };
        }

        public Dataset next(ActiveAssociation assoc, Dimse rq, Command rspCmd)
                throws DcmServiceException {
            if (canceled) {
                rspCmd.putUS(Tags.Status, Status.Cancel);
                return null;
            }
            try {
                Association a = assoc.getAssociation();
                String callingAET = a.getCallingAET();
                if (!queryCmd.next()) {
                    rspCmd.putUS(Tags.Status, Status.Success);
                    return null;
                }
                rspCmd.putUS(Tags.Status, pendingStatus);
                Dataset data = getDataset(queryCmd);
                if (adjustPatientIDs
                        && data.containsValue(Tags.PatientID)
                        && data.containsValue(Tags.IssuerOfPatientID)) {
                    if (filterResult) {
                        Dataset tmp = DcmObjectFactory.getInstance().newDataset();
                        tmp.putAll(data);
                        data = tmp;
                    }
                    service.adjustPatientID(data, requestedIssuer, pixQueryResults);
                    if (!data.contains(Tags.IssuerOfPatientID))
                        data.putLO(Tags.IssuerOfPatientID, requestedIssuer);
                }
                if (filterResult) {
                    data = data.exclude(excludeFromRSP);
                    for (int tag : excludeFromRSP)
                        data.remove(tag);
                }
                if (!service.isCFindRspDebugLogDeferToDoBeforeRsp()) {
                    log.debug("Identifier:\n");
                    log.debug(data);
                }
                service.logDIMSE(a, RESULT_XML, data);
                if (count++ == 0) {
                    coerceTpl = service.getCoercionTemplates(callingAET, 
                            RESULT_XSL);
                }
                service.preCoercionProcessing(data, Command.C_FIND_RSP, assoc.getAssociation());
                Dataset coerce = service.getCoercionAttributesFor(
                        a, RESULT_XSL, data, coerceTpl);
                if (coerce != null) {
                    service.coerceAttributes(data, coerce);
                }
                service.postCoercionProcessing(data, Command.C_FIND_RSP, assoc.getAssociation());
                return data;
            } catch (DcmServiceException e) {
                throw e;
            } catch (SQLException e) {
                log.error("Retrieve DB record failed:", e);
                throw new DcmServiceException(Status.UnableToProcess, e);
            } catch (Exception e) {
                log.error("Corrupted DB record:", e);
                throw new DcmServiceException(Status.UnableToProcess, e);
            }
        }

        public void release() {
            queryCmd.close();
        }
    }

    public void write(Association src, PDU pdu) {
        if (pdu instanceof AAssociateAC)
            perfMon.assocEstEnd(src, Command.C_FIND_RQ);
    }

    public void received(Association src, PDU pdu) {
        if (pdu instanceof AAssociateRQ)
            perfMon.assocEstStart(src, Command.C_FIND_RQ);
    }

    public void write(Association src, Dimse dimse) {
    }

    public void received(Association src, Dimse dimse) {
    }

    public void error(Association src, IOException ioe) {
    }

    public void closing(Association assoc) {
        if (assoc.getAAssociateAC() != null)
            perfMon.assocRelStart(assoc, Command.C_FIND_RQ);
    }

    public void closed(Association assoc) {
        if (assoc.getAAssociateAC() != null)
            perfMon.assocRelEnd(assoc, Command.C_FIND_RQ);
    }
}
