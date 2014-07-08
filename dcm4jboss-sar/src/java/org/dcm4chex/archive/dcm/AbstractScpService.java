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

package org.dcm4chex.archive.dcm;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;

import javax.ejb.ObjectNotFoundException;
import javax.management.Attribute;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.Notification;
import javax.management.NotificationFilterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.xml.transform.Templates;
import javax.xml.transform.sax.TransformerHandler;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObject;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.net.AcceptorPolicy;
import org.dcm4che.net.Association;
import org.dcm4che.net.AssociationFactory;
import org.dcm4che.net.DcmService;
import org.dcm4che.net.DcmServiceException;
import org.dcm4che.net.DcmServiceRegistry;
import org.dcm4che.net.PDataTF;
import org.dcm4che.net.UserIdentityNegotiator;
import org.dcm4che.server.DcmHandler;
import org.dcm4che.util.DTFormat;
import org.dcm4che2.audit.message.AuditMessage;
import org.dcm4che2.audit.message.QueryMessage;
import org.dcm4cheri.util.StringUtils;
import org.dcm4chex.archive.common.DatasetUtils;
import org.dcm4chex.archive.common.PatientMatching;
import org.dcm4chex.archive.ejb.interfaces.AEDTO;
import org.dcm4chex.archive.ejb.interfaces.AEManager;
import org.dcm4chex.archive.ejb.interfaces.AEManagerHome;
import org.dcm4chex.archive.ejb.interfaces.Storage;
import org.dcm4chex.archive.ejb.interfaces.StorageHome;
import org.dcm4chex.archive.ejb.interfaces.StudyPermissionManager;
import org.dcm4chex.archive.ejb.interfaces.StudyPermissionManagerHome;
import org.dcm4chex.archive.exceptions.UnknownAETException;
import org.dcm4chex.archive.mbean.TemplatesDelegate;
import org.dcm4chex.archive.notif.AetChanged;
import org.dcm4chex.archive.notif.CallingAetChanged;
import org.dcm4chex.archive.util.CoercionUtils;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.dcm4chex.archive.util.XSLTUtils;
import org.jboss.logging.Logger;
import org.jboss.system.ServiceMBeanSupport;

/**
 * @author Gunter.Zeilinger@tiani.com
 * @version $Revision: 15104 $ $Date: 2008-10-16 10:02:19 +0200 (Thu, 16 Oct
 *          2008) $
 * @since 31.08.2003
 */
public abstract class AbstractScpService extends ServiceMBeanSupport {

    protected static final String ASSOC_ISSUER_OF_PAT_ID = "ISSUER_OF_PAT_ID";
    protected static final String ASSOC_ISSUER_OF_ACC_NO = "ISSUER_OF_ACC_NO";
    protected static final String ASSOC_INST_NAME = "ASSOC_INST_NAME";
    protected static final String ASSOC_DEPT_NAME = "ASSOC_DEPT_NAME";
    protected static final String ANY = "ANY";
    protected static final String CONFIGURED_AETS = "CONFIGURED_AETS";
    protected static final String NONE = "NONE";
    protected static final String COERCE = "COERCE";

    private static int sequenceInt = new Random().nextInt();

    protected ObjectName dcmServerName;
    protected ObjectName aeServiceName;

    protected DcmHandler dcmHandler;

    protected UserIdentityNegotiator userIdentityNegotiator;

    protected String[] calledAETs;

    /**
     * List of allowed calling AETs. <p /> <code>null</code> means ANY<br /> An
     * empty list (length=0) means CONFIGURED_AETS.
     */
    protected String[] callingAETs;

    protected String[] generatePatientID = null;
    protected boolean pnameHashInGeneratePatientID;

    protected String issuerOfGeneratedPatientID;

    protected boolean supplementIssuerOfPatientID;

    protected boolean supplementIssuerOfAccessionNumber;
    
    protected boolean supplementInstitutionName;
    
    protected boolean supplementInstitutionalDepartmentName;

    protected boolean supplementByHostName;

    protected boolean supplementByHostAddress;

    protected String[] generatePatientIDForUnscheduledFromAETs;

    protected boolean invertGeneratePatientIDForUnscheduledFromAETs;

    protected PatientMatching patientMatching;

    /**
     * Map containing accepted Transfer Syntax UIDs. key is name (as in config
     * string), value is real uid)
     */
    protected Map<String, String> tsuidMap = new LinkedHashMap<String, String>();

    protected int maxPDULength = PDataTF.DEF_MAX_PDU_LENGTH;

    protected int maxOpsInvoked = 1;

    protected int maxOpsPerformed = 1;

    protected String[] logCallingAETs = {};

    protected File logDir;
    private boolean writeCoercionXmlLog;

    protected TemplatesDelegate templates = new TemplatesDelegate(this);

    private static final NotificationFilterSupport callingAETsChangeFilter = new NotificationFilterSupport();
    static {
        callingAETsChangeFilter.enableType(CallingAetChanged.class.getName());
    }
    private static final NotificationFilterSupport aetChangeFilter = new NotificationFilterSupport();
    static {
        aetChangeFilter.enableType(AetChanged.class.getName());
    }

    private final NotificationListener callingAETChangeListener = new NotificationListener() {
        public void handleNotification(Notification notif, Object handback) {
            try {
                CallingAetChanged userData = (CallingAetChanged) notif
                        .getUserData();
                if (areCalledAETsAffected(userData.getAffectedCalledAETs())) {
                    String[] newCallingAets = userData.getNewCallingAETs();
                    String newCallingAETs = newCallingAets == null ? ANY
                            : newCallingAets.length == 0 ? CONFIGURED_AETS
                                    : StringUtils
                                            .toString(newCallingAets, '\\');
                    log.debug("newCallingAETs:" + newCallingAETs);
                    server.setAttribute(serviceName, new Attribute(
                            "CallingAETitles", newCallingAETs));
                }
            } catch (Throwable th) {
                log.warn("Failed to process callingAET change notification: ",
                        th);
            }
        }

        private boolean areCalledAETsAffected(String[] affectedCalledAETs) {
            if (calledAETs == null)
                return true;
            if (affectedCalledAETs != null) {
                for (int i = 0; i < affectedCalledAETs.length; i++) {
                    for (int j = 0; j < calledAETs.length; j++) {
                        if (affectedCalledAETs[i].equals(calledAETs[j]))
                            return true;
                    }
                }
            }
            return false;
        }
    };
    private final NotificationListener aetChangeListener = new NotificationListener() {
        public void handleNotification(Notification notif, Object handback) {
            if (callingAETs != null && callingAETs.length == 0) {
                try {
                    log.debug("Handle AE Title change notification!");
                    AetChanged userData = (AetChanged) notif.getUserData();
                    String removeAET = userData.getOldAET();
                    String addAET = userData.getNewAET();
                    AcceptorPolicy policy = dcmHandler.getAcceptorPolicy();
                    for (int i = 0; i < calledAETs.length; ++i) {
                        AcceptorPolicy policy1 = policy
                                .getPolicyForCalledAET(calledAETs[i]);
                        if (removeAET != null) {
                            policy1.removeCallingAET(removeAET);
                        }
                        if (addAET != null) {
                            policy1.addCallingAET(addAET);
                        }
                    }

                } catch (Throwable th) {
                    log.warn(
                            "Failed to process AE Title change notification: ",
                            th);
                }
            }
        }

    };

    public final ObjectName getDcmServerName() {
        return dcmServerName;
    }

    public final void setDcmServerName(ObjectName dcmServerName) {
        this.dcmServerName = dcmServerName;
    }

    public final ObjectName getTemplatesServiceName() {
        return templates.getTemplatesServiceName();
    }

    public final void setTemplatesServiceName(ObjectName serviceName) {
        templates.setTemplatesServiceName(serviceName);
    }

    public ObjectName getAEServiceName() {
        return aeServiceName;
    }

    public void setAEServiceName(ObjectName aeServiceName) {
        this.aeServiceName = aeServiceName;
    }

    public final String getCalledAETs() {
        return calledAETs == null ? "" : StringUtils.toString(calledAETs, '\\');
    }

    public final void setCalledAETs(String calledAETs) {
        if (getCalledAETs().equals(calledAETs))
            return;
        disableService();
        this.calledAETs = StringUtils.split(calledAETs, '\\');
        enableService();
    }

    public final String getLogCallingAETs() {
        return StringUtils.toString(logCallingAETs, '\\');
    }

    public final void setLogCallingAETs(String aets) {
        logCallingAETs = StringUtils.split(aets, '\\');
    }

    public final String getGeneratePatientID() {
        if (generatePatientID == null) {
            return NONE;
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < generatePatientID.length; i++) {
            sb.append(generatePatientID[i]);
        }
        return sb.toString();
    }

    public final void setGeneratePatientID(String pattern) {
        if (pattern.equalsIgnoreCase(NONE)) {
            this.generatePatientID = null;
            return;
        }
        int pl = pattern.indexOf('#');
        boolean pnameHash = pl != -1;
        int pr = pnameHash ? pattern.lastIndexOf('#') : -1;
        int sl = pattern.indexOf('$');
        int sr = sl != -1 ? pattern.lastIndexOf('$') : -1;
        if (!pnameHash && sl == -1) {
            this.generatePatientID = new String[] { pattern };
        } else if (pnameHash && sl != -1) {
            this.generatePatientID = pl < sl 
                    ? split(pattern, pl, pr, sl, sr)
                    : split(pattern, sl, sr, pl, pr);
        } else {
            this.generatePatientID = pnameHash 
                    ? split(pattern, pl, pr)
                    : split(pattern, sl, sr);
        }
        pnameHashInGeneratePatientID =  pnameHash;
    }

    private static String[] split(String pattern, int l1, int r1) {
        return new String[] { pattern.substring(0, l1),
                pattern.substring(l1, r1 + 1), pattern.substring(r1 + 1), };
    }

    private static String[] split(String pattern, int l1, int r1, int l2, int r2) {
        if (r1 > l2) {
            throw new IllegalArgumentException(pattern);
        }
        return new String[] { pattern.substring(0, l1),
                pattern.substring(l1, r1 + 1), pattern.substring(r1 + 1, l2),
                pattern.substring(l2, r2 + 1), pattern.substring(r2 + 1) };
    }

    public final String getIssuerOfGeneratedPatientID() {
        return issuerOfGeneratedPatientID;
    }

    public final void setIssuerOfGeneratedPatientID(
            String issuerOfGeneratedPatientID) {
        this.issuerOfGeneratedPatientID = issuerOfGeneratedPatientID;
    }

    public final boolean isSupplementIssuerOfPatientID() {
        return supplementIssuerOfPatientID;
    }

    public final void setSupplementIssuerOfPatientID(
            boolean supplementIssuerOfPatientID) {
        this.supplementIssuerOfPatientID = supplementIssuerOfPatientID;
    }

    public final boolean isSupplementIssuerOfAccessionNumber() {
        return supplementIssuerOfAccessionNumber;
    }

    public final void setSupplementIssuerOfAccessionNumber(boolean enable) {
        this.supplementIssuerOfAccessionNumber = enable;
    }

    public final boolean isSupplementInstitutionName() {
        return supplementInstitutionName;
    }

    public final void setSupplementInstitutionName(
            boolean supplementInstitutionName) {
        this.supplementInstitutionName = supplementInstitutionName;
    }
    
    public final boolean isSupplementInstitutionalDepartmentName() {
        return supplementInstitutionalDepartmentName;
    }

    public final void setSupplementInstitutionalDepartmentName(
            boolean supplementInstitutionalDepartmentName) {
        this.supplementInstitutionalDepartmentName = supplementInstitutionalDepartmentName;
    }

    public final boolean isSupplementByHostName() {
        return supplementByHostName;
    }

    public final void setSupplementByHostName(boolean supplementByHostName) {
        this.supplementByHostName = supplementByHostName;
    }

    public final boolean isSupplementByHostAddress() {
        return supplementByHostAddress;
    }

    public final void setSupplementByHostAddress(boolean supplementByHostAddress) {
        this.supplementByHostAddress = supplementByHostAddress;
    }

    public final String getGeneratePatientIDForUnscheduledFromAETs() {
        return invertGeneratePatientIDForUnscheduledFromAETs ? "!\\" : ""
            + (generatePatientIDForUnscheduledFromAETs == null ? "NONE"
                    : StringUtils.toString(
                            generatePatientIDForUnscheduledFromAETs, '\\'));
    }

    public final void setGeneratePatientIDForUnscheduledFromAETs(String aets) {
        if (invertGeneratePatientIDForUnscheduledFromAETs = aets.startsWith("!\\")) {
            aets = aets.substring(2);
        }
        generatePatientIDForUnscheduledFromAETs = aets.equalsIgnoreCase("NONE")
                ? null : StringUtils.split(aets, '\\');
    }

    protected boolean isGeneratePatientIDForUnscheduledFromAET(String callingAET) {
        if (generatePatientIDForUnscheduledFromAETs != null) {
            for (String aet : generatePatientIDForUnscheduledFromAETs) {
                if (aet.equals(callingAET)) {
                    return !invertGeneratePatientIDForUnscheduledFromAETs;
                }
            }
        }
        return invertGeneratePatientIDForUnscheduledFromAETs;
    }

    public String getPatientMatching() {
        return patientMatching.toString();
    }

    public void setPatientMatching(String s) {
        this.patientMatching = new PatientMatching(s.trim());
    }

    public final PatientMatching patientMatching() {
        return patientMatching;
    }

    public final int getMaxPDULength() {
        return maxPDULength;
    }

    public final void setMaxPDULength(int maxPDULength) {
        if (this.maxPDULength == maxPDULength)
            return;
        this.maxPDULength = maxPDULength;
        enableService();
    }

    public final int getMaxOpsInvoked() {
        return maxOpsInvoked;
    }

    public final void setMaxOpsInvoked(int maxOpsInvoked) {
        if (this.maxOpsInvoked == maxOpsInvoked)
            return;
        this.maxOpsInvoked = maxOpsInvoked;
        enableService();
    }

    public final int getMaxOpsPerformed() {
        return maxOpsPerformed;
    }

    public final void setMaxOpsPerformed(int maxOpsPerformed) {
        if (this.maxOpsPerformed == maxOpsPerformed)
            return;
        this.maxOpsPerformed = maxOpsPerformed;
        enableService();
    }

    public final String getCoerceConfigDir() {
        return templates.getConfigDir();
    }

    public final void setCoerceConfigDir(String path) {
        templates.setConfigDir(path);
    }

    public boolean isWriteCoercionXmlLog() {
        return writeCoercionXmlLog;
    }

    public void setWriteCoercionXmlLog(boolean writeCoercionXmlLog) {
        this.writeCoercionXmlLog = writeCoercionXmlLog;
    }

    protected boolean enableService() {
        if (dcmHandler == null)
            return false;
        boolean changed = false;
        String[] callingAETs = getCallingAETsForPolicy();
        AcceptorPolicy policy = dcmHandler.getAcceptorPolicy();
        for (int i = 0; i < calledAETs.length; ++i) {
            AcceptorPolicy policy1 = policy
                    .getPolicyForCalledAET(calledAETs[i]);
            if (policy1 == null) {
                policy1 = AssociationFactory.getInstance().newAcceptorPolicy();
                policy1.setCallingAETs(callingAETs);
                policy1.setUserIdentityNegotiator(userIdentityNegotiator);
                policy.putPolicyForCalledAET(calledAETs[i], policy1);
                policy.addCalledAET(calledAETs[i]);
                changed = true;
            } else {
                String[] aets = policy1.getCallingAETs();
                if (aets.length == 0) {
                    if (callingAETs != null) {
                        policy1.setCallingAETs(callingAETs);
                        changed = true;
                    }
                } else {
                    if (!haveSameItems(aets, callingAETs)) {
                        policy1.setCallingAETs(callingAETs);
                        changed = true;
                    }
                }
            }
            policy1.setMaxPDULength(maxPDULength);
            policy1.setAsyncOpsWindow(maxOpsInvoked, maxOpsPerformed);
            enablePresContexts(policy1);
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private String[] getCallingAETsForPolicy() {
        if (callingAETs == null)
            return null;
        if (callingAETs.length != 0)
            return callingAETs;
        log.debug("Use 'CONFIGURED_AETS' for list of calling AETs");
        try {
            Collection<AEDTO> l = aeMgr().findAll();
            if (l.size() == 0) {
                log.warn("No AETs configured! No calling AET is allowed!");
                return callingAETs;
            }
            List<String> dicomAEs = new ArrayList<String>(l.size());
            String aet;
            for (Iterator<AEDTO> iter = l.iterator(); iter.hasNext();) {
                aet = iter.next().getTitle();
                if (aet.indexOf('^') == -1) {// filter 'HL7' AETs
                    dicomAEs.add(aet);
                }
            }
            log
                    .debug("Use 'CONFIGURED_AETS'. Current list of configured (dicom) AETs"
                            + dicomAEs);
            String[] sa = new String[dicomAEs.size()];
            return dicomAEs.toArray(sa);
        } catch (Exception e) {
            log
                    .error(
                            "Failed to query configured AETs! No calling AET is allowed!",
                            e);
            return callingAETs;
        }
    }

    // Only check if all items in o1 are also in o2! (and same length)
    // e.g. {"a","a","d"}, {"a","d","d"} will also return true!
    private boolean haveSameItems(Object[] o1, Object[] o2) {
        if (o1 == null || o2 == null || o1.length != o2.length)
            return false;
        if (o1.length == 1)
            return o1[0].equals(o2[0]);
        iloop: for (int i = 0, len = o1.length; i < len; i++) {
            for (int j = 0; j < len; j++) {
                if (o1[i].equals(o2[j]))
                    continue iloop;
            }
            return false;
        }
        return true;
    }

    private void disableService() {
        if (dcmHandler == null)
            return;
        AcceptorPolicy policy = dcmHandler.getAcceptorPolicy();
        for (int i = 0; i < calledAETs.length; ++i) {
            AcceptorPolicy policy1 = policy
                    .getPolicyForCalledAET(calledAETs[i]);
            if (policy1 != null) {
                disablePresContexts(policy1);
                if (policy1.listPresContext().isEmpty()) {
                    policy.putPolicyForCalledAET(calledAETs[i], null);
                    policy.removeCalledAET(calledAETs[i]);
                }
            }
        }
    }

    public final String getCallingAETs() {
        return callingAETs == null ? ANY
                : callingAETs.length == 0 ? CONFIGURED_AETS : StringUtils
                        .toString(callingAETs, '\\');
    }

    public final void setCallingAETs(String callingAETs)
            throws InstanceNotFoundException, MBeanException,
            ReflectionException {
        if (getCallingAETs().equals(callingAETs))
            return;
        this.callingAETs = ANY.equalsIgnoreCase(callingAETs) ? null
                : CONFIGURED_AETS.equalsIgnoreCase(callingAETs) ? new String[0]
                        : StringUtils.split(callingAETs, '\\');
        if (enableService()) {
            server.invoke(dcmServerName, "notifyCallingAETchange",
                    new Object[] { calledAETs, this.callingAETs },
                    new String[] { String[].class.getName(),
                            String[].class.getName() });
        }
    }

    protected void updateAcceptedSOPClass(Map<String, String> cuidMap,
            String newval, DcmService scp) {
        Map<String, String> tmp = parseUIDs(newval);
        if (cuidMap.keySet().equals(tmp.keySet()))
            return;
        disableService();
        if (scp != null)
            unbindAll(valuesToStringArray(cuidMap));
        cuidMap.clear();
        cuidMap.putAll(tmp);
        if (scp != null)
            bindAll(valuesToStringArray(cuidMap), scp);
        enableService();
    }

    protected static String[] valuesToStringArray(Map<String, String> tsuid) {
        return tsuid.values().toArray(new String[tsuid.size()]);
    }

    protected void bindAll(String[] cuids, DcmService scp) {
        if (dcmHandler == null)
            return; // nothing to do!
        DcmServiceRegistry services = dcmHandler.getDcmServiceRegistry();
        for (int i = 0; i < cuids.length; i++) {
            services.bind(cuids[i], scp);
        }
    }

    protected void unbindAll(String[] cuids) {
        if (dcmHandler == null)
            return; // nothing to do!
        DcmServiceRegistry services = dcmHandler.getDcmServiceRegistry();
        for (int i = 0; i < cuids.length; i++) {
            services.unbind(cuids[i]);
        }
    }

    public String getAcceptedTransferSyntax() {
        return toString(tsuidMap);
    }

    public void setAcceptedTransferSyntax(String s) {
        updateAcceptedTransferSyntax(tsuidMap, s);
    }

    protected void updateAcceptedTransferSyntax(Map<String, String> tsuidMap,
            String newval) {
        Map<String, String> tmp = parseUIDs(newval);
        if (equals(tsuidMap, tmp))
            return;
        tsuidMap.clear();
        tsuidMap.putAll(tmp);
        enableService();
    }

    private static boolean equals(Map<String, String> tsuidMap1,
            Map<String, String> tsuidMap2) {
        if (tsuidMap1.size() != tsuidMap2.size())
            return false;
        Iterator<String> iter1 = tsuidMap1.keySet().iterator();
        Iterator<String> iter2 = tsuidMap2.keySet().iterator();
        while (iter1.hasNext())
            if (!iter1.next().equals(iter2.next()))
                return false;
        return true;
    }

    protected String toString(Map<String, String> uids) {
        if (uids == null || uids.isEmpty())
            return "";
        String nl = System.getProperty("line.separator", "\n");
        StringBuffer sb = new StringBuffer();
        Iterator<String> iter = uids.keySet().iterator();
        while (iter.hasNext()) {
            sb.append(iter.next()).append(nl);
        }
        return sb.toString();
    }

    protected static Map<String, String> parseUIDs(String uids) {
        StringTokenizer st = new StringTokenizer(uids, " \t\r\n;");
        String uid, name;
        Map<String, String> map = new LinkedHashMap<String, String>();
        while (st.hasMoreTokens()) {
            uid = st.nextToken().trim();
            name = uid;

            if (isDigit(uid.charAt(0))) {
                if (!UIDs.isValid(uid))
                    throw new IllegalArgumentException("UID " + uid
                            + " isn't a valid UID!");
            } else {
                uid = UIDs.forName(name);
            }
            map.put(name, uid);
        }
        return map;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    protected void startService() throws Exception {
        logDir = new File(System.getProperty("jboss.server.log.dir")); 
        userIdentityNegotiator = (UserIdentityNegotiator) server.invoke(
                dcmServerName, "userIdentityNegotiator", null, null);
        dcmHandler = (DcmHandler) server.invoke(dcmServerName, "dcmHandler",
                null, null);
        bindDcmServices(dcmHandler.getDcmServiceRegistry());
        server.addNotificationListener(dcmServerName, callingAETChangeListener,
                callingAETsChangeFilter, null);
        server.addNotificationListener(aeServiceName, aetChangeListener,
                aetChangeFilter, null);
        enableService();
    }

    protected void stopService() throws Exception {
        disableService();
        unbindDcmServices(dcmHandler.getDcmServiceRegistry());
        dcmHandler = null;
        userIdentityNegotiator = null;
        server.removeNotificationListener(dcmServerName,
                callingAETChangeListener);
        server.removeNotificationListener(aeServiceName, aetChangeListener);
    }

    protected abstract void bindDcmServices(DcmServiceRegistry services);

    protected abstract void unbindDcmServices(DcmServiceRegistry services);

    protected abstract void enablePresContexts(AcceptorPolicy policy);

    protected abstract void disablePresContexts(AcceptorPolicy policy);

    protected void putPresContexts(AcceptorPolicy policy, String[] cuids,
            String[] tsuids) {
        for (int i = 0; i < cuids.length; i++) {
            policy.putPresContext(cuids[i], tsuids);
        }
    }

    protected void putRoleSelections(AcceptorPolicy policy, String[] cuids,
            boolean scu, boolean scp) {
        for (int i = 0; i < cuids.length; i++) {
            policy.putRoleSelection(cuids[i], scu, scp);
        }
    }

    protected void removeRoleSelections(AcceptorPolicy policy, String[] cuids) {
        for (int i = 0; i < cuids.length; i++) {
            policy.removeRoleSelection(cuids[i]);
        }
    }

    public File getLogFile(Date now, String callingAET, String suffix) {
        File dir = new File(logDir, callingAET);
        dir.mkdirs();
        return new File(dir, new DTFormat().format(now) + suffix);
    }

    private boolean contains(Object[] a, Object e) {
        for (int i = 0; i < a.length; i++) {
            if (a[i].equals(e)) {
                return true;
            }
        }
        return false;
    }

    public void logDIMSE(Association a, String suffix, Dataset ds) {
        String callingAET = a.getCallingAET();
        if (contains(logCallingAETs, callingAET)) {
            try {
                XSLTUtils.writeTo(ds,
                        getLogFile(new Date(), callingAET, suffix));
            } catch (Exception e) {
                log.warn("Logging of attributes failed:", e);
            }
        }
    }

    public Dataset getCoercionAttributesFor(String aet, String xsl,
            Dataset in, Association a) {
        return getCoercionAttributesFor(a, xsl,
                in, getCoercionTemplates(aet, xsl));
    }

    public Templates getCoercionTemplates(String aet, String xsl) {
        return templates.getTemplatesForAET(aet, xsl);
    }

    public Dataset getCoercionAttributesFor(Association a,
            String xsl, Dataset in, Templates stylesheet) {
        Dataset out = CoercionUtils.getCoercionAttributesFor(a, in, stylesheet);
        logCoercion(a, xsl, in, out);
        return out;
    }

    public Dataset getCoercionAttributesFor(Association a, String xsl,
            Dataset in, TransformerHandler th) {
    	Dataset out = CoercionUtils.getCoercionAttributesFor(in, th);
        logCoercion(a, xsl, in, out);
        return out;
    }

    private void logCoercion(Association a, String xsl, Dataset in, Dataset out) {
        if (writeCoercionXmlLog && contains(logCallingAETs, a.getCallingAET())) {
        	try {
	            Date now = new Date();
	            XSLTUtils.writeTo(in,
	                    getLogFile(now, "coercion", "." + xsl + ".in"));
	            XSLTUtils.writeTo(out, getLogFile(now, "coercion", "." + xsl
	                    + ".out"));
        	} catch (Exception e) {
                log.error("Error logging coercion:", e);
            }
        }
    }

    public void coerceAttributes(DcmObject ds, DcmObject coerce) {
    	CoercionUtils.coerceAttributes(ds, coerce);
    }
    
    public void sendJMXNotification(Object o) {
        if (log.isDebugEnabled()) {
            log.debug("Send JMX Notification: " + o);
        }
        long eventID = super.getNextNotificationSequenceNumber();
        Notification notif = new Notification(o.getClass().getName(), this,
                eventID);
        notif.setUserData(o);
        super.sendNotification(notif);
    }

    public void logDicomQuery(Association assoc, String cuid, Dataset keys) {
        try {
            QueryMessage msg = new QueryMessage();
            msg.addDestinationProcess(AuditMessage.getProcessID(),
                    calledAETs, AuditMessage.getProcessName(), AuditMessage
                            .getLocalHostName(), false);

            String srcHost = AuditMessage.hostNameOf(assoc.getSocket()
                    .getInetAddress());
            msg.addSourceProcess(srcHost, new String[] { assoc
                    .getCallingAET() }, null, srcHost, true);
            byte[] query = DatasetUtils.toByteArray(keys);
            msg.addQuerySOPClass(cuid, UIDs.ExplicitVRLittleEndian, query);
            msg.validate();
            Logger.getLogger("auditlog").info(msg);
        } catch (Exception e) {
            log.warn("Audit Log failed:", e);
        }
    }

    public static String formatPN(String pname) {
        if (pname == null || pname.length() == 0) {
            return null;
        }
        return DcmObjectFactory.getInstance().newPersonName(pname).format();
    }

    public void supplementIssuerOfPatientID(Dataset ds, Association as,
            String aet, boolean forQuery) {
        if (supplementIssuerOfPatientID
                && !ds.containsValue(Tags.IssuerOfPatientID)
                && (ds.containsValue(Tags.PatientID)
                        || forQuery && !ds.contains(Tags.IssuerOfPatientID))) {
            String issuer = getAssociatedIssuerOfPatientID(as, aet);
            if (issuer.length() != 0) {
                ds.putLO(Tags.IssuerOfPatientID, issuer);
                log.info("Supplement Issuer Of Patient ID " + issuer);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("No Issuer Of Patient ID associated to " + as
                            + " - no supplement of Issuer Of Patient ID");
                }
            }
        }
    }

    protected String getAssociatedIssuerOfPatientID(Association as, String aet) {
        String issuer = (String) as.getProperty(ASSOC_ISSUER_OF_PAT_ID);
        if (issuer == null) {
            try {
                AEManager aeMgr = aeMgr();
                try {
                    issuer = aeMgr.findByAET(aet)
                            .getIssuerOfPatientID();
                } catch (UnknownAETException e) { }
                InetAddress addr = null;
                if ((issuer == null || issuer.length() == 0)
                        && supplementByHostName) {
                    addr = as.getSocket().getInetAddress();
                    String hostName = addr.getHostName();
                    issuer = issuerOfPatientID(
                            aeMgr.findByHostName(hostName), hostName);
                }
                if ((issuer == null || issuer.length() == 0)
                        && supplementByHostAddress) {
                    if (addr == null)
                        addr = as.getSocket().getInetAddress();
                    String hostAddress = addr.getHostAddress();
                    issuer = issuerOfPatientID(
                            aeMgr.findByHostName(hostAddress), hostAddress);
                }
            } catch (Exception e) {
                log.error("Failed to access AE configuration:", e);
            } finally {
                if (issuer == null)
                    issuer = "";
                as.putProperty(ASSOC_ISSUER_OF_PAT_ID, issuer);
            }
        }
        return issuer;
    }

    private String getAssociatedInstitutionName(Association as, String aet) {
        String instName = (String) as.getProperty(ASSOC_INST_NAME);
        if (instName == null) {
            String deptName = null;
            try {
                AEManager aeMgr = aeMgr();
                AEDTO ae = null;
                try {
                    ae = aeMgr.findByAET(aet);
                    instName = ae.getInstitution();
                    deptName = ae.getDepartment();
                } catch (UnknownAETException e) { }
                InetAddress addr = null;
                if ((instName == null || instName.length() == 0)
                        && supplementByHostName) {
                    addr = as.getSocket().getInetAddress();
                    String hostName = addr.getHostName();
                    String[] tmp = institutionalData(
                            aeMgr.findByHostName(hostName), hostName);
                    if (tmp != null) {
                        instName = tmp[0];
                        deptName = tmp[1];
                    }
                }
                if ((instName == null || instName.length() == 0)
                        && supplementByHostAddress) {
                    if (addr == null)
                        addr = as.getSocket().getInetAddress();
                    String hostAddress = addr.getHostAddress();
                    String[] tmp = institutionalData(
                            aeMgr.findByHostName(hostAddress), hostAddress);
                    if (tmp != null) {
                        instName = tmp[0];
                        deptName = tmp[1];
                    }
                }
            } catch (Exception e) {
                log.error("Failed to access AE configuration:", e);
            } finally {
                if (instName == null)
                    instName = "";
                if (deptName == null)
                    deptName = "";
                as.putProperty(ASSOC_INST_NAME, instName);
                as.putProperty(ASSOC_DEPT_NAME, deptName);
            }
        }
        return instName;
    }

    protected String[] getAssociatedIssuerOfAccessionNumber(Association as, String aet) {
        String[] issuer = (String[]) as.getProperty(ASSOC_ISSUER_OF_ACC_NO);
        if (issuer == null) {
            try {
                AEManager aeMgr = aeMgr();
                try {
                    issuer = aeMgr.findByAET(aet)
                            .getIssuerOfAccessionNumber();
                } catch (UnknownAETException e) { }
                InetAddress addr = null;
                if ((issuer == null || issuer.length == 0)
                        && supplementByHostName) {
                    addr = as.getSocket().getInetAddress();
                    String hostName = addr.getHostName();
                    issuer = issuerOfAccessionNumber(
                            aeMgr.findByHostName(hostName), hostName);
                }
                if ((issuer == null || issuer.length == 0)
                        && supplementByHostAddress) {
                    if (addr == null)
                        addr = as.getSocket().getInetAddress();
                    String hostAddress = addr.getHostAddress();
                    issuer = issuerOfAccessionNumber(
                            aeMgr.findByHostName(hostAddress), hostAddress);
                }
            } catch (Exception e) {
                log.error("Failed to access AE configuration:", e);
            } finally {
                if (issuer == null)
                    issuer = new String[0];
                as.putProperty(ASSOC_ISSUER_OF_ACC_NO, issuer);
            }
        }
        return issuer;
    }

    private String getAssociatedInstitutionalDepartmentName(Association as, String aet) {
        String deptName = (String) as.getProperty(ASSOC_DEPT_NAME);
        if (deptName == null) {
            String instName = null;
            try {
                AEManager aeMgr = aeMgr();
                AEDTO ae = null;
                try {
                    ae = aeMgr.findByAET(aet);
                    instName = ae.getInstitution();
                    deptName = ae.getDepartment();
                } catch (UnknownAETException e) { }
                InetAddress addr = null;
                if ((deptName == null || deptName.length() == 0)
                        && supplementByHostName) {
                    addr = as.getSocket().getInetAddress();
                    String hostName = addr.getHostName();
                    String[] tmp = institutionalData(
                            aeMgr.findByHostName(hostName), hostName);
                    if (tmp != null) {
                        instName = tmp[0];
                        deptName = tmp[1];
                    }
                }
                if ((deptName == null || deptName.length() == 0)
                        && supplementByHostAddress) {
                    if (addr == null)
                        addr = as.getSocket().getInetAddress();
                    String hostAddress = addr.getHostAddress();
                    String[] tmp = institutionalData(
                            aeMgr.findByHostName(hostAddress), hostAddress);
                    if (tmp != null) {
                        instName = tmp[0];
                        deptName = tmp[1];
                    }
                }
            } catch (Exception e) {
                log.error("Failed to access AE configuration:", e);
            } finally {
                if (instName == null)
                    instName = "";
                if (deptName == null)
                    deptName = "";
                as.putProperty(ASSOC_INST_NAME, instName);
                as.putProperty(ASSOC_DEPT_NAME, deptName);
            }
        }
        return deptName;
    }

    private String issuerOfPatientID(Collection<AEDTO> aes, String host) {
        String issuer = null;
        for (AEDTO ae : aes) {
            String tmp = ae.getIssuerOfPatientID();
            if (tmp != null && tmp.length() != 0) {
                if (issuer == null)
                    issuer = tmp;
                else if (!issuer.equals(tmp)) {
                    log.warn("Different Issuer of Patient IDs associated to "
                            + host);
                    return null;
                }
            }
        }
        return issuer;
    }

    private String[] issuerOfAccessionNumber(Collection<AEDTO> aes, String host) {
        String[] issuer = null;
        for (AEDTO ae : aes) {
            String[] tmp = ae.getIssuerOfAccessionNumber();
            if (tmp != null && tmp.length != 0) {
                if (issuer == null)
                    issuer = tmp;
                else if (!Arrays.equals(issuer, tmp)) {
                    log.warn("Different Issuer of Accession Number associated to "
                            + host);
                    return null;
                }
            }
        }
        return issuer;
    }

    private String[] institutionalData(Collection<AEDTO> aes, String host) {
        String[] instData = null;
        for (AEDTO ae : aes) {
            String inst = ae.getInstitution();
            if (inst == null)
                inst = "";
            String dep = ae.getDepartment();
            if (dep == null)
                dep = "";
            if (inst.length() != 0 || dep.length() != 0) {
                String[] tmp = { inst, dep };
                if (instData == null)
                    instData = new String[] { inst,  dep };
                else if (!instData[0].equals(inst)
                        || !instData[1].equals(dep)) {
                    log.warn("Different Institution and/or Department Name associated to "
                            + host);
                    return null;
                }
            }
        }
        return instData;
    }

    public void supplementIssuerOfAccessionNumber(Dataset ds, Association as,
            String aet, boolean forQuery) {

        if (!supplementIssuerOfAccessionNumber)
            return;

        String[] issuer = null;
        if (shallSupplementIssuerOfAccessionNumber(ds,
                forQuery)) {
            issuer = getAssociatedIssuerOfAccessionNumber(as, aet);
            if (issuer.length == 0)
                return;
            supplementIssuerOfAccessionNumber(ds, issuer);
        }
        DcmElement reqAttrSeq = ds.get(Tags.RequestAttributesSeq);
        if (reqAttrSeq != null && reqAttrSeq.hasItems()) {
            for (int i = 0, n = reqAttrSeq.countItems(); i < n; i++) {
                Dataset item = reqAttrSeq.getItem(i);
                if (shallSupplementIssuerOfAccessionNumber(item, 
                        forQuery)) {
                    if (issuer == null)
                        issuer = getAssociatedIssuerOfAccessionNumber(as, aet);
                    if (issuer.length == 0)
                        return;
                    supplementIssuerOfAccessionNumber(item, issuer);
                }
            }
        }
    }

    private static boolean shallSupplementIssuerOfAccessionNumber(Dataset ds,
            boolean forQuery) {
        return !containsIssuerOfAccessionNumber(ds)
                && (ds.containsValue(Tags.AccessionNumber)
                || forQuery && !ds.contains(Tags.IssuerOfAccessionNumberSeq));
    }

    private static boolean containsIssuerOfAccessionNumber(Dataset ds) {
        Dataset item = ds.getItem(Tags.IssuerOfAccessionNumberSeq);
        return item != null
                && (item.containsValue(Tags.LocalNamespaceEntityID)
                || item.containsValue(Tags.UniversalEntityID));
    }

    private void supplementIssuerOfAccessionNumber(Dataset ds, String[] issuer) {
        DcmObject item = ds.putSQ(Tags.IssuerOfAccessionNumberSeq).addNewItem();
        if (issuer[0].length() > 0)
            item .putUT(Tags.LocalNamespaceEntityID, issuer[0]);
        if (issuer.length > 2) {
            item.putUT(Tags.UniversalEntityID, issuer[1]);
            item.putCS(Tags.UniversalEntityIDType, issuer[2]);
        }
        log.info("Supplement Issuer Of Accession Number "
                + StringUtils.toString(issuer, '^'));
    }

    public void supplementInstitutionalData(Dataset ds, Association as,
            String aet) {
        if (supplementInstitutionName) {
            String origName = ds.getString(Tags.InstitutionName);
            Dataset codeItem = ds.getItem(Tags.InstitutionCodeSeq);
            if (codeItem != null && !codeItem.containsValue(Tags.CodeValue))
                codeItem = null;
            if (origName == null || codeItem == null) {
                String name = getAssociatedInstitutionName(as, aet);
                if (name.length() != 0) {
                    String[] code = StringUtils.split(name, '^');
                    if (code.length == 3) {
                        if (codeItem == null) {
                            supplementInstitutionCode(ds, as, code, origName);
                            log.info("Supplement Institution Code " + name);
                        }
                        name = code[2];
                    } 
                    if (!ds.containsValue(Tags.InstitutionName)) {
                        ds.putLO(Tags.InstitutionName, name);
                        log.info("Supplement Institution Name " + name);
                    }
                } else {
                    if (origName == null && log.isDebugEnabled()) {
                        log.debug("No Institution Name associated to " + as
                                + " - no supplement of Institution Name");
                    }
                }
            }
        }
        if (supplementInstitutionalDepartmentName
                && !ds.containsValue(Tags.InstitutionalDepartmentName)) {
            String name = getAssociatedInstitutionalDepartmentName(as, aet);
            if (name.length() != 0) {
                ds.putLO(Tags.InstitutionalDepartmentName, name);
                log.info("Supplement Institutional Department Name " + name);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("No Institutional Department associated to " + as
                            + " - no supplement of Institutional Department Name");
                }
            }
       }
    }

    private void supplementInstitutionCode(Dataset ds, Association as,
            String[] code, String origName) {
        Dataset codeItem = ds.putSQ(Tags.InstitutionCodeSeq).addNewItem();
        codeItem.putSH(Tags.CodeValue, code[0]);
        String scheme = code[1];
        String meaning = code[2];
        int delim;
        if (scheme.endsWith("]")
                && (delim = scheme.lastIndexOf('[')) > 0) {
            codeItem.putSH(Tags.CodingSchemeVersion,
                    scheme.substring(delim+1, scheme.length()-1));
            scheme = scheme.substring(0, delim);
        }
        codeItem.putSH(Tags.CodingSchemeDesignator, scheme);
        codeItem.putLO(Tags.CodeMeaning, meaning);
        if (origName != null && !origName.equals(meaning)) {
            DcmElement origAttrSq = ds.get(Tags.OriginalAttributesSeq);
            if (origAttrSq == null)
                origAttrSq = ds.putSQ(Tags.OriginalAttributesSeq);
            Dataset origAttrItem = origAttrSq.addNewItem();
            origAttrItem.putLO(Tags.SourceOfPreviousValues, as.getCallingAET());
            origAttrItem.putLO(Tags.ModifyingSystem, as.getCalledAET());
            origAttrItem.putDT(Tags.AttributeModificationDatetime, new Date());
            origAttrItem.putCS(Tags.ReasonForTheAttributeModification, COERCE);
            Dataset modifiedAtts = origAttrItem
                    .putSQ(Tags.ModifiedAttributesSeq).addNewItem();
            modifiedAtts.putLO(Tags.InstitutionName, origName);
            ds.remove(Tags.InstitutionName);
        }
    }

    public void generatePatientID(Dataset pat, Dataset sty, String calledAET)
            throws DcmServiceException {
        if (generatePatientID == null) {
            return;
        }
        String pid = pat.getString(Tags.PatientID);
        if (pid != null) {
            return;
        }
        String pname = pat.getString(Tags.PatientName);
        String issuer = issuerOfGeneratedPatientID(calledAET);
        if (generatePatientID.length == 1) {
            pid = generatePatientID[0];
        } else {
            String suid = sty != null ? sty.getString(Tags.StudyInstanceUID)
                    : null;
            int suidHash = suid != null ? suid.hashCode() : ++sequenceInt;
            // generate different Patient IDs for different studies
            // if no Patient Name
            int pnameHash = pname == null ? suidHash : pname.hashCode() * 37
                    + pat.getString(Tags.PatientBirthDate, "").hashCode()
                    + pat.getString(Tags.PatientSex, "").hashCode();
            
            pid = generatePatientID(pnameHash, suidHash);
            if (pnameHashInGeneratePatientID && pname != null) {
                try {
                    Storage s = getStorage();
                    while (!pname.equals(
                            s.getPatientByIDWithIssuer(pid, issuer)
                            .getString(Tags.PatientName)))
                        pid = generatePatientID(++pnameHash, suidHash);
                } catch (ObjectNotFoundException e) {
                } catch (Exception e) {
                    log.error("Failed to query DB for patient with pid="
                            + pid + ", issuer=" + issuer);
                    throw new DcmServiceException(Status.ProcessingFailure, e);
                }
            }
        }
        pat.putLO(Tags.PatientID, pid);
        pat.putLO(Tags.IssuerOfPatientID, issuer);
        if (log.isInfoEnabled())
            log.info("Generate Patient ID: " + pid + "^^^" + issuer
                    + " for Patient: " + pname);
    }

    private String generatePatientID(int pnameHash, int suidHash) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < generatePatientID.length; i++) {
            String s = generatePatientID[i];
            int l = s.length();
            if (l == 0)
                continue;
            char c = s.charAt(0);
            if (c != '#' && c != '$') {
                sb.append(s);
                continue;
            }
            String v = Long
                    .toString((c == '#' ? pnameHash : suidHash) & 0xffffffffL);
            for (int j = v.length() - l; j < 0; j++) {
                sb.append('0');
            }
            sb.append(v);
        }
        return sb.toString();
    }

    private String issuerOfGeneratedPatientID(String calledAET) {
        try {
            AEDTO ae = aeMgr().findByAET(calledAET);
            String issuer = ae.getIssuerOfPatientID();
            if (issuer != null)
                return issuer;
        } catch (Exception ignore) {}
        return issuerOfGeneratedPatientID;
    }

    public boolean ignorePatientIDForUnscheduled(Dataset ds,
            int requestAttrsSeqTag, String callingAET) {
        String pid = ds.getString(Tags.PatientID);
        Dataset requestAttrs = ds.getItem(requestAttrsSeqTag);
        if (pid != null
                && (requestAttrs == null
                        || !requestAttrs.containsValue(Tags.SPSID))
                && isGeneratePatientIDForUnscheduledFromAET(callingAET)) {
            String issuer = ds.getString(Tags.IssuerOfPatientID);
            ds.putLO(Tags.PatientID);
            ds.remove(Tags.IssuerOfPatientID);
            if (log.isInfoEnabled()) {
                StringBuffer prompt = new StringBuffer("Ignore Patient ID: ");
                prompt.append(pid);
                if (issuer != null) {
                    prompt.append("^^^").append(issuer);
                }
                prompt.append(" for Patient: ")
                    .append(ds.getString(Tags.PatientName));
                log.info(prompt.toString());
            }
            return true;
        }
        return false;
    }

    protected AEManager aeMgr() throws Exception {
        AEManagerHome home = (AEManagerHome) EJBHomeFactory.getFactory()
                .lookup(AEManagerHome.class, AEManagerHome.JNDI_NAME);
        return home.create();
    }

    public StudyPermissionManager getStudyPermissionManager(Association a)
            throws Exception {
        StudyPermissionManager mgt = (StudyPermissionManager) a
                .getProperty(StudyPermissionManagerHome.JNDI_NAME);
        if (mgt == null) {
            mgt = ((StudyPermissionManagerHome) EJBHomeFactory.getFactory()
                    .lookup(StudyPermissionManagerHome.class,
                            StudyPermissionManagerHome.JNDI_NAME)).create();
            a.putProperty(StudyPermissionManagerHome.JNDI_NAME, mgt);
        }
        return mgt;
    }


    private Storage getStorage() throws Exception {
        StorageHome home = (StorageHome) EJBHomeFactory.getFactory()
                .lookup(StorageHome.class, StorageHome.JNDI_NAME);
        return home.create();
    }
}
