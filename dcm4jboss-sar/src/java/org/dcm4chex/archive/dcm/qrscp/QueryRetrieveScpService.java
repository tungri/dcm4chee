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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.rmi.RemoteException;
import java.sql.Types;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import javax.ejb.FinderException;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.xml.transform.Templates;

import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.DictionaryFactory;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDDictionary;
import org.dcm4che.dict.UIDs;
import org.dcm4che.net.AcceptorPolicy;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.Association;
import org.dcm4che.net.AssociationFactory;
import org.dcm4che.net.DataSource;
import org.dcm4che.net.DcmServiceException;
import org.dcm4che.net.DcmServiceRegistry;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.ExtNegotiator;
import org.dcm4che.net.PresContext;
import org.dcm4che2.audit.message.AuditMessage;
import org.dcm4che2.audit.message.InstancesTransferredMessage;
import org.dcm4che2.audit.message.ParticipantObjectDescription;
import org.dcm4che2.audit.util.InstanceSorter;
import org.dcm4cheri.util.StringUtils;
import org.dcm4chex.archive.common.Availability;
import org.dcm4chex.archive.common.DatasetUtils;
import org.dcm4chex.archive.common.PIDWithIssuer;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.dcm.AbstractScpService;
import org.dcm4chex.archive.dcm.qrscp.FileRetrieveFailedException;
import org.dcm4chex.archive.ejb.conf.AttributeFilter;
import org.dcm4chex.archive.ejb.interfaces.AEDTO;
import org.dcm4chex.archive.ejb.interfaces.FileDTO;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2Home;
import org.dcm4chex.archive.ejb.jdbc.FileInfo;
import org.dcm4chex.archive.ejb.jdbc.QueryCmd;
import org.dcm4chex.archive.ejb.jdbc.QueryExternalRetrieveAETsOfSeriesCmd;
import org.dcm4chex.archive.ejb.jdbc.QueryFilesOfSeriesCmd2;
import org.dcm4chex.archive.ejb.jdbc.RetrieveCmd;
import org.dcm4chex.archive.exceptions.ConfigurationException;
import org.dcm4chex.archive.exceptions.NoPresContextException;
import org.dcm4chex.archive.exceptions.UnknownAETException;
import org.dcm4chex.archive.mbean.DicomSecurityDelegate;
import org.dcm4chex.archive.mbean.TLSConfigDelegate;
import org.dcm4chex.archive.perf.PerfMonDelegate;
import org.dcm4chex.archive.perf.PerfPropertyEnum;
import org.dcm4chex.archive.util.DatasetUpdater;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.dcm4chex.archive.util.FileDataSource;
import org.dcm4chex.archive.util.FileUtils;
import org.jboss.logging.Logger;
import org.jboss.util.deadlock.ApplicationDeadlockException;

/**
 * @author Gunter.Zeilinger@tiani.com
 * @version $Revision: 18251 $ $Date: 2014-03-04 14:23:25 +0000 (Tue, 04 Mar 2014) $
 * @since 31.08.2003
 */
public class QueryRetrieveScpService extends AbstractScpService {

    private static final int FUZZY_MATCHING = 2;

    private static final String ANY = "ANY";

    private static final String NONE = "NONE";

    private static final String[] EMPTY = {};

    private static final String SEND_BUFFER = "SEND_BUFFER";

    public static final String NEW_LINE = System.getProperty("line.separator", "\n");

    private static final Timer pendingRspTimer = new Timer(true);

    static final UIDDictionary uidDict = 
            DictionaryFactory.getInstance().getDefaultUIDDictionary();
    
    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private boolean patchJpegLS;

    private String patchJpegLSImplCUID;

    private String patchJpegLSNewImplCUID;

    private String[] sendNoPixelDataToAETs = null;

    private String[] offerNoPixelDataToAETs = null;

    private String[] offerNoPixelDataDeflateToAETs = null;

    private String[] sendWithDefaultTransferSyntaxToAETs = null;

    private String[] ignoreUnsupportedSOPClassFailuresByAETs = null;
    
    private String[] unrestrictedQueryPermissionsToAETitles = null;

    private String[] unrestrictedReadPermissionsToAETitles = null;

    private String[] unrestrictedExportPermissionsToAETitles = null;

    private String[] hideWithoutIssuerOfPIDFromAETs;

    private boolean invertHideWithoutIssuerOfPIDFromAETs;

    private Map ignorableSOPClasses = new LinkedHashMap();

    private Map<String, String> notDecompressTsuidMap = new LinkedHashMap<String, String>();

    private Set<String> notDecompressTsuidSet;

    private ObjectName stgCmtScuScpName;

    HashMap<String, ObjectName> protocolRetriever = new HashMap<String, ObjectName>();

    private ObjectName pixQueryServiceName;

    private ObjectName updateAttributesServiceName;
    
    private TLSConfigDelegate tlsConfig = new TLSConfigDelegate(this);

    private DicomSecurityDelegate dicomSecurity =
            new DicomSecurityDelegate(this);

    private boolean sendPendingCMoveRSP = true;

    private boolean sendPendingCGetRSP = true;

    private long pendingCMoveRSPInterval = 5000;

    private long pendingCGetRSPInterval = 5000;
    
    private boolean forwardAsMoveOriginator = true;

    private String directForwardingList="";

    private Map<String,List<String>> directForwardingMap =
            new HashMap<String, List<String>>();

    protected String localStorageAET;

    private boolean recordStudyAccessTime = true;

    private boolean noMatchForNoValue = true;

    private String[] pixQueryCallingAETs;
    
    private String[] pixQueryIssuers;
    
    private String pixQueryDefIssuer;

    private boolean adjustPatientIDOnRetrieval;

    private boolean adjustAccessionNumberOnRetrieval;

    private int acTimeout = 5000;

    private int dimseTimeout = 0;

    private int soCloseDelay = 500;

    private int maxStoreOpsInvoked = 0;

    private FindScp dicomFindScp = null;

    private FindScp tianiFindScp = new FindScp(this, false);

    private FindScp tianiBlockedFindScp = new BlockedFindScp(this);

    private MoveScp moveScp = null;

    private GetScp getScp = null;

    private int maxUIDsPerMoveRQ = 100;

    private int maxBlockedFindRSP = 10000;

    private int bufferSize = 8192;

    private int retrieveRspStatusForNoMatchingInstanceToRetrieve = 0x0000;
    
    private int fetchSize;

    private static final String CSTORE_OUT_XSL = "out-cstorerq.xsl";
    
    private static final String COERCE_TPL = "COERCE_TPL";

    /**
     * Map containing accepted SOP Class UIDs. key is name (as in config
     * string), value is real uid)
     */
    private Map standardCuidMap = new LinkedHashMap();

    /**
     * Map containing accepted private SOP Class UIDs. key is name (as in config
     * string), value is real uid)
     */
    private Map privateCuidMap = new LinkedHashMap();

    /**
     * Map containing accepted Transfer Syntax UIDs for private SOP Classes. key
     * is name (as in config string), value is real uid)
     */
    private Map privateTSuidMap = new LinkedHashMap();

    private boolean coerceAttributeTopDown = false;

    private boolean cFindRspDebugLogDeferToDoBeforeRsp = false;

    private boolean verifyMD5OnMakeCStoreRQ = false;
    
    private HashSet<String> updateSOFrequest = new HashSet<String>();

    private String ignoreFileSizeCheckFS;

    private String autoUpdateAttributesFromFS;

    private Date autoUpdateAttributesBeforeDate;
    
    public QueryRetrieveScpService() {
    	moveScp = createMoveScp();
        getScp = createGetScp();
    	dicomFindScp = createFindScp();
    }
    
    DicomSecurityDelegate dicomSecurity() {
        return dicomSecurity;
    }
    
    protected MoveScp createMoveScp() {
        return new MoveScp(this);
    }

    protected GetScp createGetScp() {
        return new GetScp(this);
    }

    protected FindScp createFindScp() {
        return new FindScp(this, true);
    }

    public final TLSConfigDelegate getTLSConfig() {
    	return tlsConfig;
    }
    
    public final ObjectName getTLSConfigName() {
        return tlsConfig.getTLSConfigName();
    }

    public final void setTLSConfigName(ObjectName tlsConfigName) {
        tlsConfig.setTLSConfigName(tlsConfigName);
    }

    public final ObjectName getDicomSecurityServiceName() {
        return dicomSecurity.getDicomSecurityServiceName();
    }

    public final void setDicomSecurityServiceName(ObjectName serviceName) {
        this.dicomSecurity.setDicomSecurityServiceName(serviceName);
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

    public final ObjectName getStgCmtScuScpName() {
        return stgCmtScuScpName;
    }

    public final void setStgCmtScuScpName(ObjectName stgCmtScuScpName) {
        this.stgCmtScuScpName = stgCmtScuScpName;
    }

    public String getProtocolRetrieverMapping() {
        if (protocolRetriever.isEmpty())
            return NONE;
        StringBuilder sb = new StringBuilder();
        for (Entry<String, ObjectName> entry : protocolRetriever.entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append(NEW_LINE);
        }
        return sb.toString();
    }
    
    public void setProtocolRetrieverMapping(String s) throws MalformedObjectNameException, NullPointerException {
        s = s.trim();
        if(NONE.equals(s)) {
            protocolRetriever.clear();
        } else {
            StringTokenizer st = new StringTokenizer(s, " \t\n\r;");
            int pos;
            HashMap<String, ObjectName> newMap = new HashMap<String, ObjectName>();
            for (String tk ; st.hasMoreElements() ;) {
                tk = st.nextToken();
                pos = tk.indexOf('=');
                if (pos == -1)
                    throw new IllegalArgumentException("Wrong format: must be <protocol>=<Retriever Service Name>[<NewLine>...]");
                newMap.put(tk.substring(0, pos), new ObjectName(tk.substring(++pos)));
            }
            protocolRetriever = newMap;
        }
    }
    public final ObjectName getPixQueryServiceName() {
        return pixQueryServiceName;
    }

    public final void setPixQueryServiceName(ObjectName name) {
        this.pixQueryServiceName = name;
    }
    
    public String getPixQueryCallingAETs() {
        return pixQueryCallingAETs == null ? ANY
                : pixQueryCallingAETs.length == 0 ? NONE
                : StringUtils.toString(pixQueryCallingAETs, '\\');
    }

    public void setPixQueryCallingAETs(String s) {
        String trim = s.trim();
        this.pixQueryCallingAETs = trim.equalsIgnoreCase(ANY) ? null
                : trim.equalsIgnoreCase(NONE) ? EMPTY
                : StringUtils.split(trim, '\\');
    }
    
    public boolean isPixQueryCallingAET(String aet) {
        return pixQueryCallingAETs == null
            || Arrays.asList(pixQueryCallingAETs).contains(aet);
    }

    public String getPixQueryIssuers() {
        return pixQueryIssuers == null ? ANY
                : StringUtils.toString(pixQueryIssuers, ',');
    }

    public void setPixQueryIssuers(String s) {
        String trim = s.trim();
        this.pixQueryIssuers = trim.equalsIgnoreCase(ANY) ? null
                : StringUtils.split(trim, ',');
    }

    public boolean isPixQueryIssuer(String issuer) {
        return pixQueryIssuers == null
            || Arrays.asList(pixQueryIssuers).contains(issuer);
    }
    
    public final String getPixQueryDefIssuer() {
        return pixQueryDefIssuer;
    }

    public final void setPixQueryDefIssuer(String pixQueryDefIssuer) {
        this.pixQueryDefIssuer = pixQueryDefIssuer;
    }

    public final boolean isAdjustPatientIDOnRetrieval() {
        return adjustPatientIDOnRetrieval;
    }

    public final void setAdjustPatientIDOnRetrieval(
            boolean adjustPatientIDOnRetrieval) {
        this.adjustPatientIDOnRetrieval = adjustPatientIDOnRetrieval;
    }

    public final boolean isAdjustAccessionNumberOnRetrieval() {
        return adjustAccessionNumberOnRetrieval;
    }

    public final void setAdjustAccessionNumberOnRetrieval(
            boolean adjustAccessionNumberOnRetrieval) {
        this.adjustAccessionNumberOnRetrieval = adjustAccessionNumberOnRetrieval;
    }

    public final String getUnrestrictedQueryPermissionsToAETitles() {
        return unrestrictedQueryPermissionsToAETitles == null ? ANY
                : StringUtils.toString(
                        unrestrictedQueryPermissionsToAETitles, '\\');
    }

    public final void setUnrestrictedQueryPermissionsToAETitles(String s) {
        String trim = s.trim();
        this.unrestrictedQueryPermissionsToAETitles = 
                trim.equalsIgnoreCase(ANY) ? null 
                        : StringUtils.split(trim, '\\');
    }

    final boolean hasUnrestrictedQueryPermissions(String aet) {
        return unrestrictedQueryPermissionsToAETitles == null 
                || Arrays.asList(unrestrictedQueryPermissionsToAETitles)
                        .contains(aet);
    }
        
    public final String getUnrestrictedReadPermissionsToAETitles() {
        return unrestrictedReadPermissionsToAETitles == null ? ANY
                : StringUtils.toString(
                        unrestrictedReadPermissionsToAETitles, '\\');
    }

    public final void setUnrestrictedReadPermissionsToAETitles(String s) {
        String trim = s.trim();
        this.unrestrictedReadPermissionsToAETitles = 
                trim.equalsIgnoreCase(ANY) ? null 
                        : StringUtils.split(trim, '\\');
    }

    final boolean hasUnrestrictedReadPermissions(String aet) {
        return unrestrictedReadPermissionsToAETitles == null 
                || Arrays.asList(unrestrictedReadPermissionsToAETitles)
                        .contains(aet);
    }
        
    public final String getUnrestrictedExportPermissionsToAETitles() {
        return unrestrictedExportPermissionsToAETitles == null ? ANY
                : StringUtils.toString(
                        unrestrictedExportPermissionsToAETitles, '\\');
    }

    public final void setUnrestrictedExportPermissionsToAETitles(String s) {
        String trim = s.trim();
        this.unrestrictedExportPermissionsToAETitles = 
                trim.equalsIgnoreCase(ANY) ? null 
                        : StringUtils.split(trim, '\\');
    }

    final boolean hasUnrestrictedExportPermissions(String aet) {
        return unrestrictedExportPermissionsToAETitles == null 
                || Arrays.asList(unrestrictedExportPermissionsToAETitles)
                        .contains(aet);
    }

    public final String getHideWithoutIssuerOfPatientIDFromAETs() {
        return invertHideWithoutIssuerOfPIDFromAETs ? "!\\" : ""
            + (hideWithoutIssuerOfPIDFromAETs == null ? "NONE"
                    : StringUtils.toString(
                            hideWithoutIssuerOfPIDFromAETs, '\\'));
    }

    public final void setHideWithoutIssuerOfPatientIDFromAETs(String aets) {
        if (invertHideWithoutIssuerOfPIDFromAETs = aets.startsWith("!\\")) {
            aets = aets.substring(2);
        }
        hideWithoutIssuerOfPIDFromAETs = aets.equalsIgnoreCase("NONE")
                ? null : StringUtils.split(aets, '\\');
    }

    boolean isHideWithoutIssuerOfPIDFromAET(String callingAET) {
        if (hideWithoutIssuerOfPIDFromAETs != null) {
            for (String aet : hideWithoutIssuerOfPIDFromAETs) {
                if (aet.equals(callingAET)) {
                    return !invertHideWithoutIssuerOfPIDFromAETs;
                }
            }
        }
        return invertHideWithoutIssuerOfPIDFromAETs;
    }

    public final boolean isNoMatchForNoValue() {
        return noMatchForNoValue;
    }

    public final void setNoMatchForNoValue(boolean noMatchForNoValue) {
        this.noMatchForNoValue = noMatchForNoValue;
    }

    public final boolean getLazyFetchSeriesAttrsOnImageLevelQuery() {
        return QueryCmd.lazyFetchSeriesAttrsOnImageLevelQuery;
    }

    public final void setLazyFetchSeriesAttrsOnImageLevelQuery(boolean enable) {
        QueryCmd.lazyFetchSeriesAttrsOnImageLevelQuery = enable;
    }

    public final boolean getCacheSeriesAttrsOnImageLevelQuery() {
        return QueryCmd.cacheSeriesAttrsOnImageLevelQuery ||
                QueryCmd.lazyFetchSeriesAttrsOnImageLevelQuery;
    }

    public final void setCacheSeriesAttrsOnImageLevelQuery(boolean enable) {
        QueryCmd.cacheSeriesAttrsOnImageLevelQuery = enable;
    }

    public final boolean getAccessBlobAsLongVarBinaryOnQuery() {
        return QueryCmd.blobAccessType == Types.LONGVARBINARY;
    }

    public final void setAccessBlobAsLongVarBinaryOnQuery(boolean enable) {
        QueryCmd.blobAccessType = enable ? Types.LONGVARBINARY : Types.BLOB;
    }

    public final boolean getAccessSeriesBlobAsLongVarBinaryOnImageLevelQuery() {
        return QueryCmd.seriesBlobAccessType == Types.LONGVARBINARY;
    }

    public final void setAccessSeriesBlobAsLongVarBinaryOnImageLevelQuery(boolean enable) {
        QueryCmd.seriesBlobAccessType = enable ? Types.LONGVARBINARY : Types.BLOB;
    }

    public final boolean getLazyFetchSeriesAttrsOnRetrieve() {
        return RetrieveCmd.lazyFetchSeriesAttrs;
    }

    public final void setLazyFetchSeriesAttrsOnRetrieve(boolean enable) {
        RetrieveCmd.lazyFetchSeriesAttrs = enable;
    }

    public final boolean getCacheSeriesAttrsOnRetrieve() {
        return RetrieveCmd.cacheSeriesAttrs || RetrieveCmd.lazyFetchSeriesAttrs;
    }

    public final void setCacheSeriesAttrsOnRetrieve(boolean enable) {
        RetrieveCmd.cacheSeriesAttrs = enable;
    }

    public final int getCacheSeriesAttrsOnRetrieveMaxSize() {
        return RetrieveCmd.getSeriesAttrsCacheMaxSize();
    }

    public final void setCacheSeriesAttrsOnRetrieveMaxSize(int maxSize) {
        RetrieveCmd.setSeriesAttrsCacheMaxSize(maxSize);
    }

    public final String getCacheSeriesAttrsOnRetrieveCurrencyTimeLimit() {
        return RetryIntervalls.formatInterval(
                RetrieveCmd.seriesAttrsCacheCurrencyTimeLimit);
    }

    public final void setCacheSeriesAttrsOnRetrieveCurrencyTimeLimit(String s) {
        RetrieveCmd.seriesAttrsCacheCurrencyTimeLimit =
                RetryIntervalls.parseInterval(s);
    }

    public final boolean getAccessBlobAsLongVarBinaryOnRetrieve() {
        return RetrieveCmd.blobAccessType == Types.LONGVARBINARY;
    }

    public final void setAccessBlobAsLongVarBinaryOnRetrieve(boolean enable) {
        RetrieveCmd.blobAccessType = enable ? Types.LONGVARBINARY : Types.BLOB;
    }

    public final boolean getAccessSeriesBlobAsLongVarBinaryOnRetrieve() {
        return RetrieveCmd.seriesBlobAccessType == Types.LONGVARBINARY;
    }

    public final void setAccessSeriesBlobAsLongVarBinaryOnRetrieve(boolean enable) {
        RetrieveCmd.seriesBlobAccessType = enable ? Types.LONGVARBINARY : Types.BLOB;
    }

    public final String getQueryTransactionIsolationLevel() {
        return QueryCmd.transactionIsolationLevelAsString(
                QueryCmd.transactionIsolationLevel);
    }

    public final void setQueryTransactionIsolationLevel(String level) {
        QueryCmd.transactionIsolationLevel =
                QueryCmd.transactionIsolationLevelOf(level);
    }

    public final String getRetrieveTransactionIsolationLevel() {
        return RetrieveCmd.transactionIsolationLevelAsString(
                RetrieveCmd.transactionIsolationLevel);
    }

    public final void setRetrieveTransactionIsolationLevel(String level) {
        RetrieveCmd.transactionIsolationLevel =
                RetrieveCmd.transactionIsolationLevelOf(level);
    }
    
    public final boolean isRetrieveWithoutLeftJoins() {
        return RetrieveCmd.isNoLeftJoin();
    }

    public final void setRetrieveWithoutLeftJoins(boolean noLeftJoin) {
        RetrieveCmd.setNoLeftJoin(noLeftJoin);
    }    

    public String getAcceptedPrivateSOPClasses() {
        return toString(privateCuidMap);
    }

    public void setAcceptedPrivateSOPClasses(String s) {
        updateAcceptedSOPClass(privateCuidMap, s, null);
    }

    public String getAcceptedTransferSyntaxForPrivateSOPClasses() {
        return toString(privateTSuidMap);
    }

    public void setAcceptedTransferSyntaxForPrivateSOPClasses(String s) {
        updateAcceptedTransferSyntax(privateTSuidMap, s);
    }

    public String getAcceptedStandardSOPClasses() {
        return toString(standardCuidMap);
    }

    public void setAcceptedStandardSOPClasses(String s) {
        updateAcceptedSOPClass(standardCuidMap, s, null);
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

    public final int getMaxStoreOpsInvoked() {
        return maxStoreOpsInvoked;
    }

    public final void setMaxStoreOpsInvoked(int maxStoreOpsInvoked) {
        this.maxStoreOpsInvoked = maxStoreOpsInvoked;
    }

    public final boolean isSendPendingCMoveRSP() {
        return sendPendingCMoveRSP;
    }

    public final void setSendPendingCMoveRSP(boolean sendPendingCMoveRSP) {
        this.sendPendingCMoveRSP = sendPendingCMoveRSP;
    }

    public final boolean isSendPendingCGetRSP() {
        return sendPendingCGetRSP;
    }

    public final void setSendPendingCGetRSP(boolean sendPendingCGetRSP) {
        this.sendPendingCGetRSP = sendPendingCGetRSP;
    }

   public final void setPendingCMoveRSPInterval(long ms) {
        if (ms <= 0) {
            throw new IllegalArgumentException("pendingCMoveRSPInterval: " +  ms);
        }
        pendingCMoveRSPInterval = ms ;
    }
    
    public final long getPendingCMoveRSPInterval() {
        return pendingCMoveRSPInterval ;
    }
    
    public final void setPendingCGetRSPInterval(long ms) {
        if (ms <= 0) {
            throw new IllegalArgumentException("pendingCGetRSPInterval: " +  ms);
        }
        pendingCGetRSPInterval = ms ;
    }
    
    public final long getPendingCGetRSPInterval() {
        return pendingCGetRSPInterval ;
    }
    
    public final boolean isForwardAsMoveOriginator() {
        return forwardAsMoveOriginator;
    }

    public final void setForwardAsMoveOriginator(boolean forwardAsMoveOriginator) {
        this.forwardAsMoveOriginator = forwardAsMoveOriginator;
    }

    public final boolean isRecordStudyAccessTime() {
        return recordStudyAccessTime;
    }

    public final void setRecordStudyAccessTime(boolean updateAccessTime) {
        this.recordStudyAccessTime = updateAccessTime;
    }

    public final String getDoNotDecompressTransferSyntaxes() {
        return toString(notDecompressTsuidMap);
    }

    public final void setDoNotDecompressTransferSyntaxes(String uids) {
        this.notDecompressTsuidMap = parseUIDs(uids);
        this.notDecompressTsuidSet = new HashSet<String>(
                notDecompressTsuidMap.values());
    }

    Set<String> notDecompressTsuidSet() {
        return notDecompressTsuidSet;
    }

    public final String getPatchJpegLSImplCUID() {
        return patchJpegLS ? maskNull(patchJpegLSImplCUID, ANY) : NONE;
    }

    public final void setPatchJpegLSImplCUID(String s) {
        String trim = s.trim();
        patchJpegLS = !trim.equalsIgnoreCase(NONE);
        this.patchJpegLSImplCUID = patchJpegLS ? unmaskNull(trim, ANY) : null;
    }

    private static String unmaskNull(String s, String mask) {
        return s.equalsIgnoreCase(mask) ? null : s;
    }

    private static String maskNull(String s, String mask) {
        return s == null ? mask : s;
    }

    public final String getPatchJpegLSNewImplCUID() {
        return maskNull(patchJpegLSNewImplCUID, NONE);
    }

    public final void setPatchJpegLSNewImplCUID(String s) {
        this.patchJpegLSNewImplCUID = unmaskNull(s.trim(), NONE);;
    }

    public final String getSendNoPixelDataToAETs() {
        return sendNoPixelDataToAETs == null ? NONE : StringUtils.toString(
                sendNoPixelDataToAETs, '\\');
    }

    public final void setSendNoPixelDataToAETs(String aets) {
        this.sendNoPixelDataToAETs = NONE.equalsIgnoreCase(aets) ? null
                : StringUtils.split(aets, '\\');
    }

    public final String getOfferNoPixelDataToAETs() {
        return offerNoPixelDataToAETs == null ? ANY
                : offerNoPixelDataToAETs.length == 0 ? NONE
                : StringUtils.toString(offerNoPixelDataToAETs, '\\');
    }

    public final void setOfferNoPixelDataToAETs(String aets) {
        this.offerNoPixelDataToAETs = ANY.equalsIgnoreCase(aets) ? null
                : NONE.equalsIgnoreCase(aets) ? EMPTY
                : StringUtils.split(aets, '\\');
    }

    public final String getOfferNoPixelDataDeflateToAETs() {
        return offerNoPixelDataDeflateToAETs == null ? ANY
                : offerNoPixelDataDeflateToAETs.length == 0 ? NONE
                : StringUtils.toString(offerNoPixelDataDeflateToAETs, '\\');
    }

    public final void setOfferNoPixelDataDeflateToAETs(String aets) {
        this.offerNoPixelDataDeflateToAETs = ANY.equalsIgnoreCase(aets) ? null
                : NONE.equalsIgnoreCase(aets) ? EMPTY
                : StringUtils.split(aets, '\\');
    }

    public final String getSendWithDefaultTransferSyntaxToAETs() {
        return sendWithDefaultTransferSyntaxToAETs == null ? ANY
                : sendWithDefaultTransferSyntaxToAETs.length == 0 ? NONE
                : StringUtils.toString(sendWithDefaultTransferSyntaxToAETs,
                        '\\');
    }

    public final void setSendWithDefaultTransferSyntaxToAETs(String aets) {
        this.sendWithDefaultTransferSyntaxToAETs = ANY.equalsIgnoreCase(aets) 
                ? null : NONE.equalsIgnoreCase(aets) ? EMPTY
                        : StringUtils.split(aets, '\\');
    }

    public final String getIgnoreUnsupportedSOPClassFailuresByAETs() {
        return ignoreUnsupportedSOPClassFailuresByAETs == null ? NONE
                : StringUtils.toString(ignoreUnsupportedSOPClassFailuresByAETs,
                        '\\');
    }

    public final void setIgnoreUnsupportedSOPClassFailuresByAETs(String aets) {
        this.ignoreUnsupportedSOPClassFailuresByAETs = NONE
                .equalsIgnoreCase(aets) ? null : StringUtils.split(aets, '\\');
    }

    public final String getIgnorableSOPClasses() {
        return toString(ignorableSOPClasses);
    }

    public final void setIgnorableSOPClasses(String s) {
        this.ignorableSOPClasses = parseUIDs(s);
    }


    public final int getMaxUIDsPerMoveRQ() {
        return maxUIDsPerMoveRQ;
    }

    public final void setMaxUIDsPerMoveRQ(int max) {
        this.maxUIDsPerMoveRQ = max;
    }

    public final void setMaxBlockedFindRSP(int max) {
        this.maxBlockedFindRSP = max;
    }

    public final int getMaxBlockedFindRSP() {
        return maxBlockedFindRSP;
    }

    public synchronized void setDirectForwardingList(String directForwardingList) {
        if (!this.directForwardingList.equals(directForwardingList)) {
            this.directForwardingList = directForwardingList;
            directForwardingMap.clear();
            String[] mappings = directForwardingList.split("\\;");
            for (String mapping : mappings) {
                int k = mapping.indexOf(',');
                String src;
                String dests;
                if (k < 0) {
                    dests = "any";
                    src = mapping;
                } else {
                    dests = mapping.substring(k + 1);
                    src = mapping.substring(0, k).trim();
                    if (dests.equals("") || dests.equals("*")) {
                        dests = "any";
                    }
                }
                List<String> lst = new ArrayList<String>();
                if (!dests.equalsIgnoreCase("any")) {
                    lst.addAll(Arrays.asList(dests.split("\\,")));
                }
                directForwardingMap.put(src, lst);
            }
        }
    }

    public synchronized String getDirectForwardingList() {
        return directForwardingList;
    }

    public synchronized void addDirectForwarding(String aet, String dest) {
        if (dest == null || dest.equals("") || dest.equals("*")) {
            dest = "any";
        }
        List<String> lst = directForwardingMap.get(aet);
        if (lst != null) {
            if (lst.size() == 0 || lst.contains(dest)) {
                return;
            }
            if (dest.equalsIgnoreCase("any")) {
                lst.clear();
            } else
                lst.add(dest);
        } else {
            lst = new ArrayList<String>();
            if (!dest.equalsIgnoreCase("any")) {
                lst.add(dest);
            }
        }
        setDirectForwardingList(toDirectForwardList());
    }

    public synchronized void removeDirectForwarding(String aet) {
        removeDirectForwarding(aet, "any");
    }

    public synchronized void removeDirectForwarding(String aet, String dest) {
        if (dest == null || dest.equals("") || dest.equals("*")) {
            dest = "any";
        }
        if (dest.equalsIgnoreCase("any")) {
            directForwardingMap.remove(aet);
        } else {
            List<String> lst = directForwardingMap.get(aet);
            if (lst == null || !lst.contains(dest)) {
                return;
            }
            if (lst.size() == 1)
                directForwardingMap.remove(aet);
            else
                lst.remove(dest);
        }
        setDirectForwardingList(toDirectForwardList());
    }

    private String toDirectForwardList() {
        StringBuffer buf = new StringBuffer();
        int i = 0;
        for (String aet : directForwardingMap.keySet()) {
            if (i > 0)
                buf.append(";");
            List<String> lst = directForwardingMap.get(aet);
            buf.append(aet);
            if (lst.size() == 0) {
                buf.append(",any");
            } else {
                for (String dest : lst) {
                    buf.append(",").append(dest);
                }
            }
        }
        return buf.toString();
    }

    public synchronized boolean isDirectForwarding(String retrieveAET,
            String destination) {
        List<String> lst = directForwardingMap.get(retrieveAET);
        if (lst != null) {
            return lst.size() == 0 || lst.contains(destination);
        }
        return false;
    }

    public void setLocalStorageAET(String localStorageAET) {
        this.localStorageAET = localStorageAET;
    }

    public String getLocalStorageAET() {
        return localStorageAET;
    }

    public int getFetchSize() {
        return fetchSize;
    }

    public void setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
    }

    public String getIgnoreFileSizeCheckFS() {
        return ignoreFileSizeCheckFS;
    }

    public void setIgnoreFileSizeCheckFS(String ignoreFileSizeCheckFS) {
        this.ignoreFileSizeCheckFS = ignoreFileSizeCheckFS;
    }

    public String getAutoUpdateAttributesFromFS() {
        return autoUpdateAttributesFromFS;
    }

    public void setAutoUpdateAttributesFromFS(String updateAttributesFromFS) {
        this.autoUpdateAttributesFromFS = updateAttributesFromFS;
    }

    public String getAutoUpdateAttributesBeforeDate() {
        return new SimpleDateFormat(DATE_TIME_FORMAT).format(autoUpdateAttributesBeforeDate);
    }

    public void setAutoUpdateAttributesBeforeDate(String autoUpdateAttributesBeforeDate) throws ParseException {
        this.autoUpdateAttributesBeforeDate = new SimpleDateFormat(DATE_TIME_FORMAT).parse(autoUpdateAttributesBeforeDate);
    }

    public final ObjectName getPerfMonServiceName() {
		return dicomFindScp.getPerfMonServiceName();
	}

	public final void setPerfMonServiceName(ObjectName perfMonServiceName) {
		dicomFindScp.setPerfMonServiceName(perfMonServiceName);
		moveScp.setPerfMonServiceName(perfMonServiceName);
	}
	
	public ObjectName getUpdateAttributesServiceName() {
        return updateAttributesServiceName;
    }

    public void setUpdateAttributesServiceName(ObjectName updateAttributesServiceName) {
        this.updateAttributesServiceName = updateAttributesServiceName;
    }

    public MoveScp getMoveScp() {
		return moveScp;
	}

    protected void bindDcmServices(DcmServiceRegistry services) {
        services.bind(UIDs.PatientRootQueryRetrieveInformationModelFIND,
                dicomFindScp);
        services.bind(UIDs.StudyRootQueryRetrieveInformationModelFIND,
                dicomFindScp);
        services.bind(UIDs.PatientStudyOnlyQueryRetrieveInformationModelFIND,
                dicomFindScp);

        services.bind(UIDs.Dcm4cheStudyRootQueryRetrieveInformationModelFIND,
                tianiFindScp);
        services.bind(
                UIDs.Dcm4cheBlockedStudyRootQueryRetrieveInformationModelFIND,
                tianiBlockedFindScp);

        services.bind(UIDs.PatientRootQueryRetrieveInformationModelMOVE,
                moveScp);
        services.bind(UIDs.StudyRootQueryRetrieveInformationModelMOVE,
                moveScp);
        services.bind(UIDs.PatientStudyOnlyQueryRetrieveInformationModelMOVE,
                moveScp);

        services.bind(UIDs.PatientRootQueryRetrieveInformationModelGET,
                getScp);
        services.bind(UIDs.StudyRootQueryRetrieveInformationModelGET,
                getScp);
        services.bind(UIDs.PatientStudyOnlyQueryRetrieveInformationModelGET,
                getScp);

        dcmHandler.addAssociationListener(dicomFindScp);
        dcmHandler.addAssociationListener(moveScp);
    }

    protected void unbindDcmServices(DcmServiceRegistry services) {
        services.unbind(UIDs.PatientRootQueryRetrieveInformationModelFIND);
        services.unbind(UIDs.StudyRootQueryRetrieveInformationModelFIND);
        services.unbind(UIDs.PatientStudyOnlyQueryRetrieveInformationModelFIND);

        services.unbind(UIDs.Dcm4chePatientRootQueryRetrieveInformationModelFIND);
        services.unbind(UIDs.Dcm4cheStudyRootQueryRetrieveInformationModelFIND);
        services
                .unbind(UIDs.Dcm4chePatientStudyOnlyQueryRetrieveInformationModelFIND);

        services
                .unbind(UIDs.Dcm4cheBlockedPatientRootQueryRetrieveInformationModelFIND);
        services
                .unbind(UIDs.Dcm4cheBlockedStudyRootQueryRetrieveInformationModelFIND);
        services
                .unbind(UIDs.Dcm4cheBlockedPatientStudyOnlyQueryRetrieveInformationModelFIND);

        services.unbind(UIDs.PatientRootQueryRetrieveInformationModelMOVE);
        services.unbind(UIDs.StudyRootQueryRetrieveInformationModelMOVE);
        services.unbind(UIDs.PatientStudyOnlyQueryRetrieveInformationModelMOVE);

        services.unbind(UIDs.PatientRootQueryRetrieveInformationModelGET);
        services.unbind(UIDs.StudyRootQueryRetrieveInformationModelGET);
        services.unbind(UIDs.PatientStudyOnlyQueryRetrieveInformationModelGET);

        dcmHandler.removeAssociationListener(dicomFindScp);
        dcmHandler.removeAssociationListener(moveScp);
    }

    private static final ExtNegotiator extNegotiator = new ExtNegotiator() {
        public byte[] negotiate(byte[] offered) {
            if (offered.length > FUZZY_MATCHING)
                offered[FUZZY_MATCHING] &=
                    AttributeFilter.isSoundexEnabled() ? 1 : 0;
            return offered;
        }
    };

    protected void enablePresContexts(AcceptorPolicy policy) {
        putPresContexts(policy, valuesToStringArray(privateCuidMap),
                valuesToStringArray(privateTSuidMap));
        putPresContexts(policy, valuesToStringArray(standardCuidMap),
                valuesToStringArray(tsuidMap));
    }

    protected void disablePresContexts(AcceptorPolicy policy) {
        putPresContexts(policy, valuesToStringArray(privateCuidMap), null);
        putPresContexts(policy, valuesToStringArray(standardCuidMap), null);
    }

    protected void putPresContexts(AcceptorPolicy policy, String[] cuids,
            String[] tsuids) {
        super.putPresContexts(policy, cuids, tsuids);
        ExtNegotiator neg = tsuids != null ? extNegotiator : null;
        for (int i = 0; i < cuids.length; i++) {
            policy.putExtNegPolicy(cuids[i], neg);
        }
    }

    public AEDTO queryAEData(String aet, InetAddress address)
            throws DcmServiceException, UnknownAETException {
        // String host = address != null ? address.getCanonicalHostName() :
        // null;
        try {
            Object o = server.invoke(aeServiceName, "getAE", new Object[] {
                    aet, address }, new String[] { String.class.getName(),
                    InetAddress.class.getName() });
            if (o == null)
                throw new UnknownAETException("Unkown AET: " + aet);
            return (AEDTO) o;
        } catch (JMException e) {
            log.error("Failed to query AEData", e);
            throw new DcmServiceException(Status.UnableToProcess, e);
        }
    }
   
    Set<PIDWithIssuer> queryCorrespondingPIDs(String pid, String issuer,
            Map<PIDWithIssuer, Set<PIDWithIssuer>> map) {
        PIDWithIssuer key = new PIDWithIssuer(pid, issuer);
        Set<PIDWithIssuer> pidWithIssuers = map.get(key);
        if (pidWithIssuers == null) {
            List<String[]> results = null;
            try {
                results = isPixQueryIssuer(issuer) ? queryCorrespondingPIDs(pid, issuer) : null;
            } catch (DcmServiceException e) {}
            if (results == null || results.isEmpty()) {
                pidWithIssuers = new HashSet<PIDWithIssuer>(2);
                pidWithIssuers.add(key);
                map.put(key, pidWithIssuers);
                return pidWithIssuers;
            }
            pidWithIssuers = new HashSet<PIDWithIssuer>();
            for (String[] result : results) {
                PIDWithIssuer pidWithIssuer = 
                    new PIDWithIssuer(result[0], result[1]);
                pidWithIssuers.add(pidWithIssuer);
                map.put(pidWithIssuer, pidWithIssuers);
            }
        }
        return pidWithIssuers;
    }

    List<String[]> queryCorrespondingPIDs(String pid, String issuer)
    throws DcmServiceException {
        try {
            return (List<String[]>) server.invoke(this.pixQueryServiceName,
                    "queryCorrespondingPIDs",
                    new Object[] { pid, 
                            issuer != null ? issuer : pixQueryDefIssuer,
                            null },
                    new String[] { String.class.getName(),
                            String.class.getName(),
                            String[].class.getName() });
        } catch (JMException e) {
            log.error("Failed to perform PIX Query", e);
            throw new DcmServiceException(Status.UnableToProcess, e);
        }
    }
    
    public boolean isPixQueryLocal() throws DcmServiceException {
        try {
            return ((Boolean) server.getAttribute(this.pixQueryServiceName,
                    "PIXManagerLocal")).booleanValue();
        } catch (JMException e) {
            log.error("Failed to access PIX Service", e);
            throw new DcmServiceException(Status.UnableToProcess, e);
        }        
    }
    
    public boolean isLocalRetrieveAET(String aet) {
        for (int i = 0; i < calledAETs.length; i++) {
            if (aet.equals(calledAETs[i]))
                return true;
        }
        return false;
    }

    public final int getBufferSize() {
        return bufferSize;
    }

    public final void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    boolean isWithoutPixelData(String moveDest) {
        return sendNoPixelDataToAETs != null
                && Arrays.asList(sendNoPixelDataToAETs).contains(moveDest);
    }

    boolean isOfferNoPixelData(String moveDest) {
        return offerNoPixelDataToAETs == null
                || Arrays.asList(offerNoPixelDataToAETs).contains(moveDest);
    }

    boolean isOfferNoPixelDataDeflate(String moveDest) {
        return offerNoPixelDataDeflateToAETs == null
                || Arrays.asList(offerNoPixelDataDeflateToAETs).contains(moveDest);
    }

    boolean isSendWithDefaultTransferSyntax(String moveDest) {
        return sendWithDefaultTransferSyntaxToAETs == null
                || Arrays.asList(sendWithDefaultTransferSyntaxToAETs)
                        .contains(moveDest);
    }

    boolean isIgnorableSOPClass(String cuid, String moveDest) {
        return ignorableSOPClasses.containsValue(cuid)
                || ignoreUnsupportedSOPClassFailuresByAETs != null
                && Arrays.asList(ignoreUnsupportedSOPClassFailuresByAETs)
                        .contains(moveDest);
    }

   protected void logInstancesSent(Association moveOrGetAs,
            Association storeAs, ArrayList fileInfos) {
        try {
            InstanceSorter sorter = new InstanceSorter();
            FileInfo fileInfo = null;
            for (Iterator iter = fileInfos.iterator(); iter.hasNext();) {
                fileInfo = (FileInfo) iter.next();
                sorter.addInstance(fileInfo.studyIUID, fileInfo.sopCUID,
                        fileInfo.sopIUID, null);
            }
            String destAET = storeAs.isRequestor() ? storeAs.getCalledAET()
                                                   : storeAs.getCallingAET();
            String destHost = AuditMessage.hostNameOf(
                    storeAs.getSocket().getInetAddress());
            String origAET = moveOrGetAs.getCallingAET();
            boolean dstIsRequestor = origAET.equals(destAET);
            boolean srcIsRequestor = !dstIsRequestor 
                    && Arrays.asList(calledAETs).contains(origAET);
            InstancesTransferredMessage msg = 
                    new InstancesTransferredMessage(
                            InstancesTransferredMessage.EXECUTE);
            msg.addSourceProcess(AuditMessage.getProcessID(), 
                    calledAETs, AuditMessage.getProcessName(), 
                    AuditMessage.getLocalHostName(), srcIsRequestor);
            msg.addDestinationProcess(destHost, new String[] { destAET }, null, 
                    destHost, dstIsRequestor);
            if (!dstIsRequestor && !srcIsRequestor) {
                String origHost = AuditMessage.hostNameOf(
                        moveOrGetAs.getSocket().getInetAddress());
                msg.addOtherParticipantProcess(origHost,
                        new String[] { origAET }, null, origHost, true);
            }
            msg.addPatient(fileInfo.patID, formatPN(fileInfo.patName));
            for (String suid : sorter.getSUIDs()) {
                ParticipantObjectDescription desc = 
                        new ParticipantObjectDescription();
                for (String cuid : sorter.getCUIDs(suid)) {
                    ParticipantObjectDescription.SOPClass sopClass =
                            new ParticipantObjectDescription.SOPClass(cuid);
                    sopClass.setNumberOfInstances(
                            sorter.countInstances(suid, cuid));
                    desc.addSOPClass(sopClass);
                }
                msg.addStudy(suid, desc);
            }
            msg.validate();
            Logger.getLogger("auditlog").info(msg);
        } catch (Exception e) {
            log.warn("Audit Log failed:", e);
        }
    }
    
    protected Socket createSocket(String moveCalledAET, AEDTO destAE) throws Exception {
        return tlsConfig.createSocket(aeMgr().findByAET(moveCalledAET), destAE);
    }

    void onInstancesRetrieved(String moveScp, String moveDest,
            Dataset stgCmtActionInfo) {
        try {
            server.invoke(stgCmtScuScpName, "onInstancesRetrieved",
                    new Object[] { moveScp, moveDest, stgCmtActionInfo, 0 },
                    new String[] {
                            String.class.getName(),
                            String.class.getName(),
                            Dataset.class.getName(),
                            int.class.getName()
                    });
        } catch (JMException e) {
            log.error("Failed to invoke onInstancesRetrieved", e);
        }
    }

    File retrieveFile(String fsID, String fileID) throws Exception {
        try {
            return (File) server.invoke(getRetrieverNameForProtocol(fsID), "retrieveFile",
                    new Object[] { fsID, fileID }, new String[] {
                            String.class.getName(), String.class.getName() });
        } catch (InstanceNotFoundException e) {
            throw new ConfigurationException(e.getMessage(), e);
        } catch (MBeanException e) {
            throw e.getTargetException();
        } catch (ReflectionException e) {
            throw new ConfigurationException(e.getMessage(), e);
        }
    }

    private ObjectName getRetrieverNameForProtocol(String fsID) {
        int pos = fsID.indexOf(':');
        String protocol = fsID.substring(0, ++pos);
        ObjectName n = protocolRetriever.get(protocol);
        if (n == null) {
            throw new ConfigurationException("No Retriever servicename configured for protocol:"+protocol);
        }
        return n;
    }

    private FileSystemMgt2 getFileSystemMgt() throws Exception {
        return ((FileSystemMgt2Home) EJBHomeFactory.getFactory().lookup(
                FileSystemMgt2Home.class, FileSystemMgt2Home.JNDI_NAME)).create();
    }

    void updateStudyAccessTime(Set<StudyInstanceUIDAndDirPath> studyInfos) {
        if (!recordStudyAccessTime)
            return;

        FileSystemMgt2 fsMgt;
        try {
            fsMgt = getFileSystemMgt();
        } catch (Exception e) {
            log.fatal("Failed to access FileSystemMgt EJB");
            return;
        }
        try {
            for (Iterator<StudyInstanceUIDAndDirPath> it = studyInfos.iterator(); it.hasNext();) {
                StudyInstanceUIDAndDirPath studyInfo = it.next();
                updateStudyAccessTime(studyInfo.studyIUID, studyInfo.dirpath);
            }
        } finally {
            try {
                fsMgt.remove();
            } catch (Exception ignore) {
            }
        }
    }

    private void updateStudyAccessTime(String studyIUID, String dirpath) {
        if (recordStudyAccessTime) {
            String key = studyIUID+"_"+dirpath;
            synchronized(this) {
                if (!updateSOFrequest.add(key)) {
                    if (log.isDebugEnabled())
                        log.debug("updateStudyAccessTime for "+key+" already requested!");
                    return;
                }
            }
            try {
                getFileSystemMgt().touchStudyOnFileSystem(studyIUID, dirpath);
            } catch (Exception e) {
                log.warn("Failed to update access time for study "
                        + studyIUID + " on " + dirpath, e);
            }
            updateSOFrequest.remove(key);
        }
    }

    void scheduleSendPendingCMoveRsp(TimerTask sendPendingRsp) {
        if (sendPendingCMoveRSP) {
            pendingRspTimer.schedule(sendPendingRsp, 0, pendingCMoveRSPInterval);
        }
     }

    void scheduleSendPendingCGetRsp(TimerTask sendPendingRsp) {
        if (sendPendingCGetRSP) {
            pendingRspTimer.schedule(sendPendingRsp, 0, pendingCGetRSPInterval);
        }
     }

    Dataset makeRetrieveRspIdentifier(Collection<String> failedIUIDs) {
        if (failedIUIDs.isEmpty() )
            return null;
        Dataset ds = DcmObjectFactory.getInstance().newDataset();
        String[] a = failedIUIDs.toArray(new String[failedIUIDs.size()]);
        ds.putUI(Tags.FailedSOPInstanceUIDList, a);
        // check if 64k limit for UI attribute is reached
        if (ds.get(Tags.FailedSOPInstanceUIDList).length() < 0x10000)
            return ds;
        log.warn("Failed SOP InstanceUID List exceeds 64KB limit - send empty attribute instead");
        ds.putUI(Tags.FailedSOPInstanceUIDList);
        return ds;
    }

    Dimse makeCStoreRQ(ActiveAssociation activeAssoc, FileInfo info, AEDTO aeData,
            int priority, String moveOriginatorAET, int moveRqMsgID,
            PerfMonDelegate perfMon, 
            Map<PIDWithIssuer, Set<PIDWithIssuer>> pixQueryResults)
    throws Exception {
    	if (isVerifyMD5OnMakeCStoreRQ()) {
    		FileUtils.verifyMD5(info);
    	}
        Association assoc = activeAssoc.getAssociation();
        String dest = assoc.isRequestor() ? assoc.getCalledAET() 
                : assoc.getCallingAET();
        
        File f;
        try {
        	f = getFile(info);
        } catch (Exception e) {
        	throw new FileRetrieveFailedException("failed to retrieve file from local storage", e);
        }
        PresContext presCtx = selectAcceptedPresContext(assoc, info);
        if (presCtx == null)
            throw new NoPresContextException(
                    "No Presentation Context for "
                        + uidDict.toString(info.sopCUID)
                        + (assoc.isRequestor() ? " accepted by "
                                               : " offered by ")
                        + dest);
        Command storeRqCmd = DcmObjectFactory.getInstance().newCommand();
        storeRqCmd.initCStoreRQ(assoc.nextMsgID(), info.sopCUID, info.sopIUID,
                priority);
        if (moveOriginatorAET != null) {
            storeRqCmd.putUS(Tags.MoveOriginatorMessageID, moveRqMsgID);
            storeRqCmd.putAE(Tags.MoveOriginatorAET, moveOriginatorAET);
        }

        Dataset mergeAttrs;
        if (coerceAttributeTopDown) {
        	mergeAttrs = DatasetUtils.fromByteArray(info.instAttrs,
                    DatasetUtils.fromByteArray(info.seriesAttrs, DatasetUtils
                    .fromByteArray(info.studyAttrs, DatasetUtils
                    .fromByteArray(info.patAttrs))));
        } else {
        	mergeAttrs = DatasetUtils.fromByteArray(info.patAttrs,
                DatasetUtils.fromByteArray(info.studyAttrs, DatasetUtils
                        .fromByteArray(info.seriesAttrs, DatasetUtils
                                .fromByteArray(info.instAttrs))));
        }
        if (adjustPatientIDOnRetrieval 
                && mergeAttrs.containsValue(Tags.PatientID)
                && mergeAttrs.containsValue(Tags.IssuerOfPatientID)) {
            String assocIssuer = getAssociatedIssuerOfPatientID(assoc, dest);
            if (assocIssuer.length() > 0)
                adjustPatientID(mergeAttrs, assocIssuer, pixQueryResults);
        }
        adjustAccessionNumberOnRetrieval(mergeAttrs, assoc, dest);
        coerceOutboundCStoreRQ(mergeAttrs, aeData, assoc, dest);
        byte[] buf = (byte[]) assoc.getProperty(SEND_BUFFER);
        if (buf == null) {
            buf = new byte[bufferSize];
            assoc.putProperty(SEND_BUFFER, buf);
        }
        
        DatasetUpdater datasetUpdater = createDatasetUpdater(assoc);
        FileDataSource ds = createFileDataSource(f, mergeAttrs, buf, datasetUpdater);
        ds.setWithoutPixeldata(isWithoutPixelData(dest));
        ds.setPatchJpegLS(patchJpegLS);
        ds.setPatchJpegLSImplCUID(patchJpegLSImplCUID);
        ds.setPatchJpegLSNewImplCUID(patchJpegLSNewImplCUID);
        Dimse rq = AssociationFactory.getInstance().newDimse(
                presCtx.pcid(), storeRqCmd, ds);
        if (perfMon != null) {
            perfMon.setProperty(activeAssoc, rq, PerfPropertyEnum.DICOM_FILE, f);
        }
        return rq;
    }
    
    protected DatasetUpdater createDatasetUpdater(Association assoc) {
    	return null;
    }
    
    /**
     * Stub for manipulation of the dataset prior to issuing C-STORE rq
     * 
     * @param ds
     *            the current dataset
     * @param aeData
     *            {@link AEDTO} object representing the destination AET. This
     *            may be null (e.g. C-GET code doesn't currently pass any value,
     *            so check first before using it.
     * @param assoc
     *            the active association
     */
    protected void coerceOutboundCStoreRQ(Dataset ds, AEDTO aeData,
            Association assoc, String dest) throws Exception {
        /*
         * Apply outbound CStore sytlesheet
         */
        Templates coerceTpl = (Templates) assoc.getProperty(COERCE_TPL);
        if (coerceTpl == null) {
            coerceTpl = getCoercionTemplates(dest, CSTORE_OUT_XSL);
            assoc.putProperty(COERCE_TPL, coerceTpl);
        }
        Dataset coerce = getCoercionAttributesFor(assoc, CSTORE_OUT_XSL, ds,
                coerceTpl);
        if (coerce != null) {
            /*
             * Calling the base class method instead of the overriden method
             * here because we only need to update the dataset here. No need to
             * split the issuer for data sharing.
             */
            coerceAttributes(ds, coerce);
        }
        postCoercionProcessing(ds, Command.C_STORE_RQ,assoc);
    }
    
	protected PresContext selectAcceptedPresContext(Association a, FileInfo info) throws Exception {
		return selectAcceptedPresContext(a, info, new String[] {UIDs.NoPixelDataDeflate,
				UIDs.NoPixelData, info.tsUID, UIDs.ExplicitVRLittleEndian,
				UIDs.ImplicitVRLittleEndian});
	}    

	protected PresContext selectAcceptedPresContext(Association a,
			FileInfo info, String[] tsuids) {
        PresContext presCtx = null;
        
        for (int i = 0; presCtx == null && i < tsuids.length; i++) {
            presCtx = a.getAcceptedPresContext(info.sopCUID, tsuids[i]);
        }
        
        return presCtx;
	}    

    void adjustPatientID(Dataset ds, String requestedIssuer,
            Map<PIDWithIssuer, Set<PIDWithIssuer>> pixQueryResults) {
        String pid = ds.getString(Tags.PatientID);
        String issuer = ds.getString(Tags.IssuerOfPatientID);
        Set<PIDWithIssuer> correspondingPIDs =
                queryCorrespondingPIDs(pid, issuer, pixQueryResults);
        ds.putLO(Tags.PatientID);
        ds.remove(Tags.IssuerOfPatientID);
        DcmElement opids = ds.get(Tags.OtherPatientIDSeq);
        if (opids != null)
            for (int i = 0, n = opids.countItems(); i < n; i++) {
                Dataset opid = opids.getItem(i);
                correspondingPIDs.add(new PIDWithIssuer(
                        opid.getString(Tags.PatientID),
                        opid.getString(Tags.IssuerOfPatientID)));
            }
        opids = ds.putSQ(Tags.OtherPatientIDSeq);
        for (PIDWithIssuer pidWithIssuer : correspondingPIDs) {
            Dataset opid = (ds.contains(Tags.IssuerOfPatientID)
                    || requestedIssuer != null
                    && !pidWithIssuer.issuer.equals(requestedIssuer))
                            ? opids.addNewItem()
                            : ds; 
            opid.putLO(Tags.PatientID, pidWithIssuer.pid);
            opid.putLO(Tags.IssuerOfPatientID, pidWithIssuer.issuer);
        }
    }

    private void adjustAccessionNumberOnRetrieval(Dataset ds,
            Association as, String dest) {
        if (adjustAccessionNumberOnRetrieval) {
            doAdjustAccessionNumberOnRetrieval(ds, as, dest);
            doAdjustAccessionNumberOnRetrieval(
                    ds.get(Tags.RequestAttributesSeq), as, dest);
            doAdjustAccessionNumberOnRetrieval(
                    ds.get(Tags.RefRequestSeq), as, dest);
        }
    }

    private void doAdjustAccessionNumberOnRetrieval(
            DcmElement sq, Association as, String dest) {
        if (sq != null)
            for (int i = 0, n = sq.countItems(); i < n; i++)
                doAdjustAccessionNumberOnRetrieval(sq.getItem(i), as, dest);
    }

    private void doAdjustAccessionNumberOnRetrieval(Dataset ds,
            Association as, String dest) {
        Dataset issuer;
        if (ds.containsValue(Tags.AccessionNumber)
                && (issuer = ds.getItem(Tags.IssuerOfAccessionNumberSeq))
                        != null
                && !equalsIssuer(issuer,
                        getAssociatedIssuerOfAccessionNumber(as, dest))) {
            log.info("Nullify Accession Number with issuer "
                    + issuer.getString(Tags.LocalNamespaceEntityID)
                    + '&'
                    + issuer.getString(Tags.UniversalEntityID)
                    + '&'
                    + issuer.getString(Tags.UniversalEntityIDType)
                    );
            ds.putSH(Tags.AccessionNumber);
            ds.remove(Tags.IssuerOfAccessionNumberSeq);
        }
    }

    private boolean equalsIssuer(Dataset item, String[] assocIssuer) {
        return assocIssuer.length == 0
                || equals(item.getString(Tags.LocalNamespaceEntityID),
                        assocIssuer[0])
                && (assocIssuer.length == 1
                || equals(item.getString(Tags.UniversalEntityID),
                        assocIssuer[1])
                && (assocIssuer.length == 2
                || equals(item.getString(Tags.UniversalEntityIDType),
                                assocIssuer[2])));
    }

    private boolean equals(String s1, String s2) {
        return s1 == null || s1.length() == 0 
                || s2 == null || s2.length() == 0
                || s1.equals(s2);
    }

    protected File getFile(FileInfo info) throws Exception {
        return getFile(info.basedir, info.fileID, info.size);
    }

    protected File getFile(FileDTO dto) throws Exception {
        return getFile(dto.getDirectoryPath(), dto.getFilePath(), dto.getFileSize());
    }

    protected File getFile(String fsID, String fileID, long fileSize)
            throws Exception {
        File f = getFile(fsID, fileID);
        if ((f.length() < fileSize) && !fsID.startsWith(ignoreFileSizeCheckFS))
            throw new IOException("File is of unexpected size it was truncated at "
                    + (fileSize - f.length()) + " bytes, please check file: " + f);
        return f;
    }

    public File getFile(String fsID, String fileID) throws Exception {
        File file = fsID.indexOf(':',3) > 0 ? retrieveFile(fsID, fileID)
                        : FileUtils.toFile(fsID, fileID);
        if (!file.canRead()) {
            throw new FileNotFoundException(file.getPath());
        }
        return file;
    }

    public Object locateInstance(String sopIUID) throws Exception {
        return locateInstance(sopIUID, null);
    }

    public Map<String, Object> locateInstancesOfSeries(String seriesIUID,
            String studyIUID) throws Exception {
        HashSet<String> dirPaths = new HashSet<String>();
        QueryFilesOfSeriesCmd2 query = new QueryFilesOfSeriesCmd2(seriesIUID, fetchSize);
        Map<String, List<FileDTO>> fileDTOsByIUID = query.getFileDTOsByIUID();
        Map<String, Object> fileOrAETByIUID = new HashMap<String, Object>();
        for (Map.Entry<String, List<FileDTO>> entry :
                fileDTOsByIUID.entrySet()) {
            String iuid = entry.getKey();
            List<FileDTO> value = entry.getValue();
            FileDTO[] fileDTOs = value.toArray(new FileDTO[value.size()]);
            Arrays.sort(fileDTOs);
            File[] resolvedFile = new File[]{null};
            FileDTO localFileDTO = getLocalFileDTO(fileDTOs, resolvedFile);
            if (localFileDTO != null) {
                String dirPath = localFileDTO.getDirectoryPath();
                if (studyIUID != null && studyIUID.length() != 0
                        && dirPaths.add(dirPath)) {
                    updateStudyAccessTime(studyIUID, dirPath);
                }
                fileOrAETByIUID.put(iuid, resolvedFile[0]);
            } else {
                // no local accessible file -> return Retrieve AET
                fileOrAETByIUID.put(iuid, fileDTOs[0].getRetrieveAET());
            }
        }
        if (fileOrAETByIUID.size() < query.getNumberOfSeriesRelatedInstances()) {
            Collection<String[]> iuidAndAETs = 
                    new QueryExternalRetrieveAETsOfSeriesCmd(seriesIUID, fetchSize)
                            .getRetrieveAETs();
            for (String[] iuidAndAET : iuidAndAETs) {
                if (!fileOrAETByIUID.containsKey(iuidAndAET[0])) {
                    // no file for instance -> return External Retrieve AET or null
                    fileOrAETByIUID.put(iuidAndAET[0], iuidAndAET[1]);
                }
            }
        }
        return fileOrAETByIUID;
    }

    private FileDTO getLocalFileDTO(FileDTO[] fileDTOs, File[] resolvedFile) {
        ArrayList<Long> failedFSpks = new ArrayList<Long>();
        FileDTO dto;
        for ( int i = 0 ; i < fileDTOs.length ;  ) {
            dto = fileDTOs[i++];
            if (dto.getAvailability() <= Availability.NEARLINE && 
                    isLocalRetrieveAET(dto.getRetrieveAET()) && 
                    !failedFSpks.contains(dto.getFileSystemPk())) {
                try {
                    File f = getFile(dto);
                    if (resolvedFile != null && resolvedFile.length > 0)
                        resolvedFile[0] = f;
                } catch (Exception x) {
                    failedFSpks.add(dto.getFileSystemPk());
                    if ( i < fileDTOs.length ) {
                        log.warn("File can't be read! try another one. fileDTO:"+dto);
                        log.debug("Reason:"+x.getMessage(), x);
                    } else {
                        log.warn("File can't be read! fileDTO:"+dto);
                        log.info("Reason:"+x.getMessage(), x);
                    }
                    continue;
                }
                return dto;
            }
        }
        return null;
    }

    public Object locateInstance(String sopIUID, String studyIUID)
            throws Exception {
        FileDTO[] fileDTOs = null;
        String aet = null;
        FileSystemMgt2 fsMgt = getFileSystemMgt();
        try {
            fileDTOs = getFilesOfInstance(fsMgt, sopIUID);
            if (fileDTOs.length == 0) {
                aet = fsMgt.getExternalRetrieveAET(sopIUID);
            } else {
                File[] resolvedFile = new File[]{null};
                FileDTO localFileDTO = getLocalFileDTO(fileDTOs, resolvedFile);
                if (localFileDTO != null) {
                    if (localFileDTO.getDirectoryPath().startsWith(autoUpdateAttributesFromFS))
                        updateInstanceAttributes(sopIUID);
                    if (studyIUID != null && studyIUID.length() != 0) {
                        updateStudyAccessTime(studyIUID,
                                localFileDTO.getDirectoryPath());
                    }
                    return resolvedFile[0];
                }
                aet = fileDTOs[0].getRetrieveAET();
            }
        } catch (FinderException ignore) {}
        if ( aet == null ) return null;
        AEDTO aeData = aeMgr().findByAET(aet);
        return aeData;
    }

    private FileDTO[] getFilesOfInstance(FileSystemMgt2 fsMgt, String sopIUID) 
    throws RemoteException, FinderException {
        try {
            return fsMgt.getFilesOfInstance(sopIUID);
        } catch (RemoteException ex) {
            Throwable cause = null;
            RemoteException rex = ex;
            while (rex.detail != null) {
                cause = rex.detail;
                if (cause instanceof ApplicationDeadlockException) {
                    log.warn("Detect Application Deadlock - retry:", cause);
                    return fsMgt.getFilesOfInstance(sopIUID);
                }
                if (cause instanceof RemoteException) {
                    rex = (RemoteException)cause;
                } else {
                    break;
                }
            }
            throw ex;
        }
    }

    public DataSource getDatasourceOfInstance(String iuid) throws Exception {
        Dataset dsQ = DcmObjectFactory.getInstance().newDataset();
        dsQ.putUI(Tags.SOPInstanceUID, iuid);
        dsQ.putCS(Tags.QueryRetrieveLevel, "IMAGE");
        RetrieveCmd cmd = RetrieveCmd.create(dsQ);
        cmd.setFetchSize(fetchSize);
        FileInfo infoList[][] = cmd.getFileInfos();
        if (infoList.length == 0)
            return null;
        FileInfo[] fileInfos = infoList[0];
        for (int i = 0; i < fileInfos.length; ++i) {
            final FileInfo info = fileInfos[i];
            if (isLocalRetrieveAET(info.fileRetrieveAET)) {
                File f = getFile(info);
                Dataset mergeAttrs = DatasetUtils.fromByteArray(
                        info.patAttrs,
                        DatasetUtils .fromByteArray(
                                info.studyAttrs,
                                DatasetUtils .fromByteArray(
                                        info.seriesAttrs,
                                        DatasetUtils .fromByteArray(
                                                info.instAttrs))));
                FileDataSource ds = new FileDataSource(f, mergeAttrs, new byte[bufferSize]);
                ds.setPatchJpegLS(patchJpegLS);
                ds.setPatchJpegLSImplCUID(patchJpegLSImplCUID);
                ds.setPatchJpegLSNewImplCUID(patchJpegLSNewImplCUID);
                return ds;
            }
        }
        return null;
    }

    void prefetchTars(Collection<List<FileInfo>> localFiles) throws Exception {
        HashSet<String> tarPaths = null;
        for (List<FileInfo> list : localFiles) {
            FileInfo fileInfo = list.get(0);
            String fsID = fileInfo.basedir;
            if (!fsID.startsWith("tar:")) {
                continue;
            }
            String fileID = fileInfo.fileID;
            int tarEnd = fileID.indexOf('!');
            if (tarEnd == -1) {
                // invalid fileID will be handled by retrieveLocal()
                continue;
            }
            String tarPath = fileID.substring(0, tarEnd);
            if (tarPaths == null) {
                tarPaths = new HashSet<String>(8);
            }
            if (tarPaths.add(tarPath)) {
                try {
                    retrieveFile(fsID, fileID);
                } catch (Exception e) {
                    throw new Exception("Failed to retrieve TAR " + tarPath
                            + " from file system " + fsID, e);
                }
             }
        }
    }

    void updateAttributes(Collection<List<FileInfo>> localFiles) {
        HashSet<String> seriesIuids = new HashSet<String>();
        try {
            log.debug("Executing auto update attributes");
            for (List<FileInfo> list : localFiles) {
                final FileInfo fileInfo = list.get(0);
                String fsID = fileInfo.basedir;
                if (!fsID.startsWith(autoUpdateAttributesFromFS))
                    continue;
                
                if (seriesIuids.contains(fileInfo.seriesIUID))
                    continue;
                
                if (fileInfo.seriesUpdatedTime.before(autoUpdateAttributesBeforeDate)) {
                    log.debug("Updating attributes of series " + fileInfo.seriesIUID);
                    new Thread( new Runnable() {
                        public void run() {
                            try {
                                server.invoke(updateAttributesServiceName, "updateSeries", 
                                        new Object[]{fileInfo.seriesIUID}, 
                                        new String[]{String.class.getName()});
                            } catch (Exception e) {
                                log.error("Failed to update Attributes for series "+fileInfo.seriesIUID, e);
                            }
                        }
                    }).start();
                }
                seriesIuids.add(fileInfo.seriesIUID);
            }
        } catch (Exception e) {
            log.debug("Failed to auto update attributes for series " + seriesIuids, e);
        }
    }
    
    private void updateInstanceAttributes(String sopIUID) {
        try {
            server.invoke(updateAttributesServiceName, "updateInstance", 
                    new Object[]{sopIUID}, 
                    new String[]{String.class.getName()});
        } catch (Exception e) {
            log.error("Failed to update Attributes for Instance "+sopIUID, e);
        }
    }

    /**
     * Callback for post-processing the dataset after the dataset has been
     * coerced.
     *
     * @param ds
     *                the coerced dataset
     * @param command
     *                DICOM command type
     * @param assoc
     *                current Association
     * @throws Exception
     */
    void postCoercionProcessing(Dataset ds, int command, Association assoc) throws Exception {
        doPostCoercionProcessing(ds,command,assoc);
    }
    protected void doPostCoercionProcessing(Dataset ds, int command, Association assoc) throws Exception {
        // Extension Point for customized QueryRetrieveScpService
    }

    void preCoercionProcessing(Dataset ds, int command, Association assoc) throws Exception {
        doPreCoercionProcessing(ds,command,assoc);
    }
    protected void doPreCoercionProcessing(Dataset ds, int command, Association assoc) throws Exception {
        // Extension Point for customized QueryRetrieveScpService
    }
    
	public int getRetrieveRspStatusForNoMatchingInstanceToRetrieve() {
		return retrieveRspStatusForNoMatchingInstanceToRetrieve;
	}

	public void setRetrieveRspStatusForNoMatchingInstanceToRetrieve(
			int retrieveRspStatusForNoMatchingInstanceToRetrieve) {
		this.retrieveRspStatusForNoMatchingInstanceToRetrieve = retrieveRspStatusForNoMatchingInstanceToRetrieve;
	}

    public boolean isCoerceAttributeTopDown() {
        return coerceAttributeTopDown;
    }

    public void setCoerceAttributeTopDown(boolean reverse) {
        this.coerceAttributeTopDown = reverse;
    }

    public boolean isCFindRspDebugLogDeferToDoBeforeRsp() {
        return cFindRspDebugLogDeferToDoBeforeRsp;
    }

    public void setCFindRspDebugLogDeferToDoBeforeRsp(boolean defer) {
        this.cFindRspDebugLogDeferToDoBeforeRsp = defer;
    }

    public void setVerifyMD5OnMakeCStoreRQ(boolean verifyMD5OnMakeCStoreRQ) {
        this.verifyMD5OnMakeCStoreRQ = verifyMD5OnMakeCStoreRQ;
    }

    public boolean isVerifyMD5OnMakeCStoreRQ() {
        return verifyMD5OnMakeCStoreRQ;
    }

    protected FileDataSource createFileDataSource(File f,
            Dataset mergeAttrs, byte[] buf, DatasetUpdater datasetUpdater) {
        return new FileDataSource(f, mergeAttrs, buf, datasetUpdater);
    }
    
    public void clearCachedSeriesAttrs() {
        RetrieveCmd.clearCachedSeriesAttrs();
    }
}
