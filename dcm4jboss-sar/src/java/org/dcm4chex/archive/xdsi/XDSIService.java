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
package org.dcm4chex.archive.xdsi;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;

import javax.activation.DataHandler;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.RuntimeMBeanException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.log4j.Logger;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.util.UIDGenerator;
import org.dcm4che2.audit.message.AuditEvent;
import org.dcm4che2.audit.message.AuditMessage;
import org.dcm4che2.audit.message.DataExportMessage;
import org.dcm4che2.audit.message.ParticipantObjectDescription;
import org.dcm4che2.audit.util.InstanceSorter;
import org.dcm4cheri.util.StringUtils;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.dcm.ianscu.IANScuService;
import org.dcm4chex.archive.ejb.interfaces.ContentEdit;
import org.dcm4chex.archive.ejb.interfaces.ContentEditHome;
import org.dcm4chex.archive.ejb.interfaces.ContentManager;
import org.dcm4chex.archive.ejb.interfaces.ContentManagerHome;
import org.dcm4chex.archive.ejb.interfaces.FileDTO;
import org.dcm4chex.archive.ejb.jdbc.QueryFilesCmd;
import org.dcm4chex.archive.ejb.jdbc.QueryXdsPublishCmd;
import org.dcm4chex.archive.ejb.jdbc.QueryXdsPublishCmd.PublishStudy;
import org.dcm4chex.archive.mbean.HttpUserInfo;
import org.dcm4chex.archive.mbean.SchedulerDelegate;
import org.dcm4chex.archive.mbean.TemplatesDelegate;
import org.dcm4chex.archive.util.DateTimeFormat;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.dcm4chex.archive.util.FileUtils;
import org.dcm4chex.archive.util.XSLTUtils;
import org.jboss.system.ServiceMBeanSupport;
import org.jboss.system.server.ServerImplMBean;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


//import com.sun.xml.messaging.saaj.util.JAXMStreamSource;

/**
 * @author franz.willer@gwi-ag.com
 * @version $Revision: 18232 $ $Date: 2014-02-10 10:17:46 +0000 (Mon, 10 Feb 2014) $
 * @since Feb 15, 2006
 */
public class XDSIService extends ServiceMBeanSupport implements NotificationListener {

    private static final String STUDY_INSTANCE_UID = "studyInstanceUID";
    private static final String SRC_PATIENT_ID = "srcPatientID";
    private static final String XAD_PATIENT_ID = "xadPatientID";
    private static final String SQL_NOT_VALID_REASON = "!SQL NOT VALID! Reason: ";
    private static final String RPLC_DOC_ENTRY_UID = "rplcDocEntryUID";
    private static final String DEFAULT_XDSB_SOURCE_SERVICE = "dcm4chee.xds:service=XDSbSourceService";
    public static final String DOCUMENT_ID = "doc_1";
    public static final String PDF_DOCUMENT_ID = "pdf_doc_1";
    public static final String AUTHOR_SPECIALITY = "authorSpeciality";
    public static final String AUTHOR_PERSON = "authorPerson";
    public static final String AUTHOR_ROLE = "authorRole";
    public static final String AUTHOR_ROLE_DISPLAYNAME = "authorRoleDisplayName";
    public static final String AUTHOR_INSTITUTION = "authorInstitution";
    public static final String SOURCE_ID = "sourceId";

    private static final String NONE = "NONE";
    private static final String NEWLINE = System.getProperty("line.separator", "\n");

    private ObjectName ianScuServiceName;
    private ObjectName keyObjectServiceName;
    private ObjectName xdsbSourceServiceName;
    private ObjectName xdsHttpCfgServiceName;
    private ObjectName xdsQueryServiceName;
    private Boolean httpCfgServiceAvailable;

    protected String[] autoPublishAETs;
    private String autoPublishDocTitle;

    private static Logger log = Logger.getLogger(XDSIService.class.getName());

    private Map usr2author = new TreeMap();


//  http attributes to document repository actor (synchron) 	
    private String docRepositoryURI;
    private String docRepositoryAET;

//  Metadata attributes
    private File propertyFile;
    private File docTitleCodeListFile;
    private File classCodeListFile;
    private File contentTypeCodeListFile;
    private File eventCodeListFile;
    private File healthCareFacilityCodeListFile;
    private File autoPublishPropertyFile;
    private File autoPublishStudyDeletedPDFFile;
    private File autoPublishXSLFile;

    private List authorRoles = new ArrayList();
    private List confidentialityCodes;

    private Properties metadataProps = new Properties();

    private Map mapCodeLists = new HashMap();

    private ObjectName pixQueryServiceName;
    private String sourceID;
    private String localDomain;
    private String affinityDomain;

    private String ridURL;

    private boolean logSOAPMessage = true;
    private boolean indentSOAPLog = true;
    private boolean useXDSb = false;
    private boolean removeNamespaceID = false;

    private final NotificationListener ianListener = 
        new NotificationListener() {
        public void handleNotification(Notification notif, Object handback) {
            log.info("ianListener called!");
            onIAN((Dataset) notif.getUserData());
        }

    };
    private boolean logITI15;

    private boolean isRunning;
    private Integer listenerID;
    private String timerIDAutoPublish;
    private long autoPublishInterval;
    private Timestamp beforeDate;
    private Timestamp afterDate;
    private long olderThan;
    private long notOlderThan;
    private List<String> autoPublishSQL = new ArrayList<String>();
    private List<QueryXdsPublishCmd> autoPublishSQLcmd = new ArrayList<QueryXdsPublishCmd>();
    private int maxNumberOfStudiesByOneTask;
    private boolean addStudyInstanceUIDSlot;
    private boolean disableStudyDeleteSQL;
    private String sqlCheckStudyDeleted = "@SELECT study_fk, pk, docentry_uid from published_study where study_fk IS NULL";
    private QueryXdsPublishCmd checkStudyDeletedCmd;
    private int fetchSize;
    private final SchedulerDelegate scheduler = new SchedulerDelegate(this);
    private TemplatesDelegate templates = new TemplatesDelegate(this);

    private final NotificationListener autoPublishListener = new NotificationListener() {
        public void handleNotification(Notification notif, Object handback) {
            synchronized(this) {
                if (isRunning) {
                    log.info("XDS autoPublish is already running!");
                    return;
                }
                isRunning = true;
            }
            new Thread(new Runnable(){
                public void run() {
                    try {
                        log.debug("Start checkAutoPublish");
                        checkAutoPublish();
                    } catch (Exception e) {
                        log.error("checkAutoPublish failed:", e);
                    } finally {
                        isRunning = false;
                    }
                }}).start();
        }
    };

    public void handleNotification(Notification notification, Object handback) {
        if (notification.getType().equals(org.jboss.system.server.Server.START_NOTIFICATION_TYPE)) {
            try {
                updateSQLCmds();
                startScheduler();
            } catch (Exception x) {
                log.error("Can not start timer!", x);
            }
        }
    }
    /**
     * @return Returns the property file path.
     */
    public String getPropertyFile() {
        return propertyFile.getPath();
    }
    public void setPropertyFile(String file) throws IOException {
        if ( file == null || file.trim().length() < 1) return;
        propertyFile = new File(file.replace('/', File.separatorChar));
        try {
            readPropertyFile();
        } catch ( Throwable ignore ) {
            log.warn("Property file "+file+" cant be read!");
        }
    }

    public String getDocTitleCodeListFile() {
        return docTitleCodeListFile == null ? null : docTitleCodeListFile.getPath();
    }
    public void setDocTitleCodeListFile(String file) throws IOException {
        if ( file == null || file.trim().length() < 1) {
            docTitleCodeListFile = null;
        } else {
            docTitleCodeListFile = new File(file.replace('/', File.separatorChar));
        }
    }

    public String getClassCodeListFile() {
        return classCodeListFile == null ? null : classCodeListFile.getPath();
    }
    public void setClassCodeListFile(String file) throws IOException {
        if ( file == null || file.trim().length() < 1) {
            classCodeListFile = null;
        } else {
            classCodeListFile = new File(file.replace('/', File.separatorChar));
        }
    }

    public String getContentTypeCodeListFile() {
        return contentTypeCodeListFile == null ? null : contentTypeCodeListFile.getPath();
    }
    public void setContentTypeCodeListFile(String file) throws IOException {
        if ( file == null || file.trim().length() < 1) {
            contentTypeCodeListFile = null;
        } else {
            contentTypeCodeListFile = new File(file.replace('/', File.separatorChar));
        }
    }

    public String getHealthCareFacilityCodeListFile() {
        return healthCareFacilityCodeListFile == null ? null : healthCareFacilityCodeListFile.getPath();
    }
    public void setHealthCareFacilityCodeListFile(String file) throws IOException {
        if ( file == null || file.trim().length() < 1) {
            healthCareFacilityCodeListFile = null;
        } else {
            healthCareFacilityCodeListFile = new File(file.replace('/', File.separatorChar));
        }
    }

    public String getEventCodeListFile() {
        return eventCodeListFile == null ? null : eventCodeListFile.getPath();
    }
    public void setEventCodeListFile(String file) throws IOException {
        if ( file == null || file.trim().length() < 1) {
            eventCodeListFile = null;
        } else {
            eventCodeListFile = new File(file.replace('/', File.separatorChar));
        }
    }

    public String getConfidentialityCodes() {
        return getListString(confidentialityCodes);
    }
    public void setConfidentialityCodes(String codes) throws IOException {
        confidentialityCodes = setListString( codes );;
    }

    /**
     * @return Returns the authorPerson or a user to authorPerson mapping.
     */
    public String getAuthorPersonMapping() {
        if ( usr2author.isEmpty() ) {
            return metadataProps.getProperty(AUTHOR_PERSON);
        } else {
            return this.getMappingString(usr2author);
        }
    }

    /**
     * Set either a fix authorPerson or a mapping user to authorPerson.
     * <p>
     * Mapping format: &lt;user&gt;=&lt;authorPerson&gt;<br>
     * Use either newline or semicolon to seperate mappings.
     * <p>
     * If '=' is ommited, a fixed autorPerson is set in <code>metadataProps</code>
     * 
     * @param s The authorPerson(-mapping) to set.
     */
    public void setAuthorPersonMapping(String s) {
        if ( s == null || s.trim().length() < 1) return;
        usr2author.clear();
        if ( s.indexOf('=') == -1) {
            metadataProps.setProperty(AUTHOR_PERSON, s); //NO mapping user -> authorPerson; use fix authorPerson instead
        } else {
            this.addMappingString(s, usr2author);
        }
    }

    /**
     * get the authorPerson value for given user.
     * 
     * @param user
     * @return
     */
    public String getAuthorPerson( String user ) {
        String person = (String)usr2author.get(user);
        if ( person == null ) {
            person = metadataProps.getProperty(AUTHOR_PERSON);
        }
        return person;
    }


    public String getSourceID() {
        if ( sourceID == null ) sourceID = metadataProps.getProperty(SOURCE_ID);
        return sourceID;
    }

    public void setSourceID(String id) {
        sourceID = id;
        if ( sourceID != null )
            metadataProps.setProperty(SOURCE_ID, sourceID);
    }

    /**
     * @return Returns a list of authorRoles (with displayName) as String.
     */
    public String getAuthorRoles() {
        return getListString(authorRoles);
    }

    /**
     * Set authorRoles (with displayName).
     * <p>
     * Format: &lt;role&gt;^&lt;displayName&gt;<br>
     * Use either newline or semicolon to seperate roles.
     * <p>
     * @param s The roles to set.
     */
    public void setAuthorRoles(String s) {
        if ( s == null || s.trim().length() < 1) return;
        authorRoles = setListString(s);
    }

    public Properties joinMetadataProperties(Properties props) {
        Properties p = new Properties();//we should not change metadataProps!
        p.putAll(metadataProps);
        if ( props == null )
            p.putAll(props);
        return p;
    }

//  http
    /**
     * @return Returns the docRepositoryURI.
     */
    public String getDocRepositoryURI() {
        return docRepositoryURI;
    }
    /**
     * @param docRepositoryURI The docRepositoryURI to set.
     */
    public void setDocRepositoryURI(String docRepositoryURI) {
        this.docRepositoryURI = docRepositoryURI;
    }

    /**
     * @return Returns the docRepositoryAET.
     */
    public String getDocRepositoryAET() {
        return docRepositoryAET == null ? "NONE" : docRepositoryAET;
    }
    /**
     * @param docRepositoryAET The docRepositoryAET to set.
     */
    public void setDocRepositoryAET(String docRepositoryAET) {
        if ( "NONE".equals(docRepositoryAET))
            this.docRepositoryAET = null;
        else
            this.docRepositoryAET = docRepositoryAET;
    }

    public final ObjectName getPixQueryServiceName() {
        return pixQueryServiceName;
    }

    public final void setPixQueryServiceName(ObjectName name) {
        this.pixQueryServiceName = name;
    }

    public final ObjectName getIANScuServiceName() {
        return ianScuServiceName;
    }

    public final void setIANScuServiceName(ObjectName ianScuServiceName) {
        this.ianScuServiceName = ianScuServiceName;
    }

    public final ObjectName getKeyObjectServiceName() {
        return keyObjectServiceName;
    }

    public final void setKeyObjectServiceName(ObjectName keyObjectServiceName) {
        this.keyObjectServiceName = keyObjectServiceName;
    }

    public String getXdsbSourceServiceName() {
        return xdsbSourceServiceName == null ? NONE : xdsbSourceServiceName.toString();
    }
    public void setXdsbSourceServiceName(String name) throws MalformedObjectNameException, NullPointerException {
        this.xdsbSourceServiceName = NONE.equals(name) ? null : ObjectName.getInstance(name);
    }

    public String getXdsQueryServiceName() {
        return xdsQueryServiceName == null ? NONE : xdsQueryServiceName.toString();
    }
    public void setXdsQueryServiceName(String name) throws MalformedObjectNameException, NullPointerException {
        xdsQueryServiceName = NONE.equals(name) ? null : ObjectName.getInstance(name);
    }
    
    public String getXdsHttpCfgServiceName() {
        return xdsHttpCfgServiceName == null ? NONE : xdsHttpCfgServiceName.toString();
    }
    public void setXdsHttpCfgServiceName(String name) throws MalformedObjectNameException, NullPointerException {
        try {
            xdsHttpCfgServiceName = NONE.equals(name) ? null : ObjectName.getInstance(name);
        } finally {
            //Set available flag to false when ObjectName is not set or null ('unchecked') otherwise because we have
            //no dependency to the optional configuration service.
            httpCfgServiceAvailable = xdsHttpCfgServiceName == null ? Boolean.FALSE : null;
        }
    }
    
    public ObjectName getSchedulerServiceName() {
        return scheduler.getSchedulerServiceName();
    }

    public void setSchedulerServiceName(ObjectName schedulerServiceName) {
        scheduler.setSchedulerServiceName(schedulerServiceName);
    }

    public final ObjectName getTemplatesServiceName() {
        return templates.getTemplatesServiceName();
    }

    public final void setTemplatesServiceName(ObjectName serviceName) {
        templates.setTemplatesServiceName(serviceName);
    }
    
    public boolean isHttpCfgServiceAvailable() {
        if (httpCfgServiceAvailable==null) {
            if ( xdsHttpCfgServiceName != null ) {
                if ( server.isRegistered(xdsHttpCfgServiceName) ) {
                    this.httpCfgServiceAvailable = Boolean.TRUE;
                } else {
                    this.httpCfgServiceAvailable = Boolean.FALSE;
                }
            } else {
                return false;
            }
        }
        return httpCfgServiceAvailable.booleanValue();
    }

    public boolean isUseXDSb() {
        return useXDSb;
    }
    public void setUseXDSb(boolean useXDSb) {
        if ( this.useXDSb != useXDSb ) {
            this.useXDSb = useXDSb;
            if ( useXDSb && xdsbSourceServiceName == null ) {
                try {
                    setXdsbSourceServiceName(DEFAULT_XDSB_SOURCE_SERVICE);
                } catch (Exception x) {
                    log.warn("Cant set default XDS.b Service ("+DEFAULT_XDSB_SOURCE_SERVICE+")!",x);
                }
            }
        }
    }
    public boolean isLogITI15() {
        return logITI15;
    }
    public void setLogITI15(boolean logIti15) {
        this.logITI15 = logIti15;
    }
    public final String getAutoPublishAETs() {
        return autoPublishAETs.length > 0 ? StringUtils.toString(autoPublishAETs,
        '\\') : NONE;
    }

    public final void setAutoPublishAETs(String autoPublishAETs) {
        this.autoPublishAETs = NONE.equalsIgnoreCase(autoPublishAETs)
        ? new String[0]
                     : StringUtils.split(autoPublishAETs, '\\');
    }

    public String getTimerIDAutoPublish() {
        return timerIDAutoPublish;
    }
    public void setTimerIDAutoPublish(String timerID) {
        this.timerIDAutoPublish = timerID;
    }
    public final String getAutoPublishInterval() {
        return RetryIntervalls.formatIntervalZeroAsNever(autoPublishInterval);
    }

    public void setAutoPublishInterval(String interval)
            throws Exception {
        long oldInterval = autoPublishInterval;
        autoPublishInterval = RetryIntervalls.parseIntervalOrNever(interval);
        if (getState() == STARTED && oldInterval != autoPublishInterval) {
            stopScheduler();
            startScheduler();
        }
    }
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public String getAutoPublishSQL() {
        if (autoPublishSQL.isEmpty())
            return NONE;
        StringBuilder sb = new StringBuilder();
        for (String s: autoPublishSQL) {
            sb.append(s).append(NEWLINE);
        }
        return sb.toString(); 
    }
    public void setAutoPublishSQL(String sql) {
        if (sql.equals(this.getAutoPublishSQL())) {
            return;
        }
        autoPublishSQL.clear();
        autoPublishSQLcmd.clear();
        if (!NONE.equals(sql.trim())) {
            boolean serviceStarted = this.getState() == STARTED;
            String tk, sqlError;
            for (StringTokenizer st = new StringTokenizer(sql, ";\r\n") ; st.hasMoreElements() ; ) {
                tk = st.nextToken();
                if (serviceStarted) {
                    Map<String, Object> params = getSQLparams();
                    if (tk.length() == 0 || tk.charAt(0) == '!') {
                        if (!tk.startsWith(SQL_NOT_VALID_REASON)) {
                            continue;
                        } else {
                            tk = tk.substring(1);
                        }
                    }
                    if (tk.charAt(0) != '*') {
                        sqlError = checkSQL(tk, params);
                        if (sqlError == null) {
                            addSQLcmd(tk);
                        } else {
                            autoPublishSQL.add(SQL_NOT_VALID_REASON+sqlError);
                            tk = "!"+tk;
                        }
                    }
                }
                autoPublishSQL.add(tk);
            }
            if (!disableStudyDeleteSQL && serviceStarted) {
                try {
                    autoPublishSQLcmd.add(new QueryXdsPublishCmd(sqlCheckStudyDeleted, fetchSize, getMaxNumberOfStudiesByOneTask()));
                } catch (SQLException ignore) {}
            }
        }
    }
    
    private void updateSQLCmds() {
        autoPublishSQLcmd.clear();
        if (!autoPublishSQL.isEmpty()) {
            for (String s : autoPublishSQL) {
                if (s.charAt(0) != '*' && s.charAt(0) != '!') {
                    addSQLcmd(s);
                }
            }
            if (!disableStudyDeleteSQL) {
                try {
                    autoPublishSQLcmd.add(new QueryXdsPublishCmd(sqlCheckStudyDeleted, fetchSize, getMaxNumberOfStudiesByOneTask()));
                } catch (SQLException ignore) {}
            }
        }
    }
    
    private void addSQLcmd(String sql) {
        try {
            autoPublishSQLcmd.add(new QueryXdsPublishCmd(sql, fetchSize, getMaxNumberOfStudiesByOneTask()));
        } catch (Exception ignore) {
            log.warn("addSQLcmd failed! sql:"+sql, ignore);
        }
    }
    
    private String checkSQL(String sql, Map<String, Object> params) {
        String sqlUC = sql.toUpperCase();
        if ( sqlUC.indexOf("DELETE ") != -1 ) {
            return "DELETE is not allowed in this SQL statement!";
        }
        if ( sqlUC.indexOf("UPDATE ") != -1 ) {
            return "UPDATE is not allowed in this SQL statement!";
        }
        try {
            QueryXdsPublishCmd cmd = new QueryXdsPublishCmd(sql, 1, 1);
            cmd.getPublishStudies(params);
        } catch (Exception x) {
            return x.getCause() == null ? x.toString() : x.getCause().toString();
        }
        return null;
    }
    public boolean isDisableStudyDeleteSQL() {
        return disableStudyDeleteSQL;
    }
    public void setDisableStudyDeleteSQL(boolean b) {
        if (disableStudyDeleteSQL != b) {
            this.disableStudyDeleteSQL = b;
            this.updateSQLCmds();
        }
    }
    
    public String getBeforeDate() {
        return beforeDate == null ? NONE : new DateTimeFormat(true).format(beforeDate);
    }
    public void setBeforeDate(String createdBefore) throws ParseException {
        this.beforeDate = NONE.equals(createdBefore) ? null : new Timestamp(new DateTimeFormat(true).parse(createdBefore).getTime());
    }
    public String getAfterDate() {
        return afterDate == null ? NONE : new DateTimeFormat().format(afterDate);
    }
    public void setAfterDate(String createdAfter) throws ParseException {
        this.afterDate = NONE.equals(createdAfter) ? null : new Timestamp(new DateTimeFormat().parse(createdAfter).getTime());
    }
    public String getOlderThan() {
        return RetryIntervalls.formatInterval(olderThan);
    }
    public void setOlderThan(String olderThan) {
        this.olderThan = RetryIntervalls.parseInterval(olderThan);
    }
    public String getNotOlderThan() {
        return RetryIntervalls.formatInterval(notOlderThan);
    }
    public void setNotOlderThan(String notOlderThan) {
        this.notOlderThan = RetryIntervalls.parseInterval(notOlderThan);
    }
    public int getMaxNumberOfStudiesByOneTask() {
        return maxNumberOfStudiesByOneTask;
    }
    public void setMaxNumberOfStudiesByOneTask(
            int maxNumberOfStudiesByOneTask) {
        if (this.maxNumberOfStudiesByOneTask != maxNumberOfStudiesByOneTask) {
            this.maxNumberOfStudiesByOneTask = maxNumberOfStudiesByOneTask;
            if (getState() == STARTED)
                updateSQLCmds();
        }
    }
    
    public boolean isAddStudyInstanceUIDSlot() {
        return addStudyInstanceUIDSlot;
    }
    public void setAddStudyInstanceUIDSlot(boolean addStudyInstanceUIDSlot) {
        this.addStudyInstanceUIDSlot = addStudyInstanceUIDSlot;
    }
    public int getFetchSize() {
        return fetchSize;
    }
    public void setFetchSize(int fetchSize) {
        if (this.fetchSize != fetchSize) {
            this.fetchSize = fetchSize;
            if (getState() == STARTED)
                updateSQLCmds();
        }
    }


    public final String getAutoPublishDocTitle() {
        return autoPublishDocTitle;
    }
    public final void setAutoPublishDocTitle(String autoPublishDocTitle ) {
        this.autoPublishDocTitle = autoPublishDocTitle;
    }

    public String getAutoPublishPropertyFile() {
        return autoPublishPropertyFile == null ? "NONE" : autoPublishPropertyFile.getPath();
    }
    public void setAutoPublishPropertyFile(String file) throws IOException {
        if ( file == null || file.trim().length() < 1 || file.equalsIgnoreCase("NONE")) {
            autoPublishPropertyFile = null;
        } else {
            autoPublishPropertyFile = new File(file.replace('/', File.separatorChar));
        }
    }
    public String getAutoPublishStudyDeletedPDFFile() {
        return autoPublishStudyDeletedPDFFile == null ? "TEXT" : autoPublishStudyDeletedPDFFile.getPath();
    }
    public void setAutoPublishStudyDeletedPDFFile(String file) throws IOException {
        if ( file == null || file.trim().length() < 1 || file.equalsIgnoreCase("TEXT")) {
            autoPublishStudyDeletedPDFFile = null;
        } else {
            autoPublishStudyDeletedPDFFile = new File(file.replace('/', File.separatorChar));
        }
    }
    public String getAutoPublishXSLFile() {
        return autoPublishXSLFile == null ? "NONE" : autoPublishXSLFile.getPath();
    }
    public void setAutoPublishXSLFile(String file) throws IOException {
        if ( file == null || file.trim().length() < 1 || file.equalsIgnoreCase("NONE")) {
            autoPublishXSLFile = null;
        } else {
            autoPublishXSLFile = new File(file.replace('/', File.separatorChar));
        }
    }

    public String getLocalDomain() {
        return localDomain == null ? "NONE" : localDomain;
    }
    public void setLocalDomain(String domain) {
        localDomain = ( domain==null || 
                domain.trim().length()<1 || 
                domain.equalsIgnoreCase("NONE") ) ? null : domain;
    }

    public String getAffinityDomain() {
        return affinityDomain;
    }
    public void setAffinityDomain(String domain) {
        if (domain.length() > 2 && domain.charAt(0) == '=' && domain.charAt(1) == '?') {
            int pos = domain.indexOf('[', 2);
            int pos2 = domain.indexOf(']', 2);
            if (pos > 2 || pos == -1 && pos2 != -1 ||
                pos == 2 && (pos2 < 3 || domain.length() < pos2 + 5) ) {
                throw new IllegalArgumentException("Wrong format for dedicated issuer replacement! Format: '=?[<issuer>]<AffinityDomain>");
            }
        }
        affinityDomain = domain;
    }

    public boolean isRemoveNamespaceID() {
        return removeNamespaceID;
    }
    public void setRemoveNamespaceID(boolean removeNamespaceID) {
        this.removeNamespaceID = removeNamespaceID;
    }
    
    /**
     * Adds a 'mappingString' (format:&lt;key&gt;=&lt;value&gt;...) to a map.
     * 
     * @param s
     */
    private void addMappingString(String s, Map map) {
        StringTokenizer st = new StringTokenizer( s, ",;\n\r\t ");
        String t;
        int pos;
        while ( st.hasMoreTokens() ) {
            t = st.nextToken();
            pos = t.indexOf('=');
            if ( pos == -1) {
                map.put(t,t);
            } else {
                map.put(t.substring(0,pos), t.substring(++pos));
            }
        }
    }
    /**
     * Returns the String representation of a map
     * @return
     */
    private String getMappingString(Map map) {
        if ( map == null || map.isEmpty() ) return null;
        StringBuffer sb = new StringBuffer();
        String key;
        for ( Iterator iter = map.keySet().iterator() ; iter.hasNext() ; ) {
            key = iter.next().toString();
            sb.append(key).append('=').append(map.get(key)).append( System.getProperty("line.separator", "\n"));
        }
        return sb.toString();
    }

    private List setListString(String s) {
        List l = new ArrayList();
        if ( NONE.equals(s) ) return l;
        StringTokenizer st = new StringTokenizer( s, ";\n\r");
        while ( st.hasMoreTokens() ) {
            l.add(st.nextToken());
        }
        return l;
    }

    private String getListString(List l) {
        if ( l == null || l.isEmpty() ) return NONE;
        StringBuffer sb = new StringBuffer();
        for ( Iterator iter = l.iterator() ; iter.hasNext() ; ) {
            sb.append(iter.next()).append( System.getProperty("line.separator", "\n"));
        }
        return sb.toString();
    }

    /**
     * @return Returns the ridURL.
     */
    public String getRidURL() {
        return ridURL;
    }
    /**
     * @param ridURL The ridURL to set.
     */
    public void setRidURL(String ridURL) {
        this.ridURL = ridURL;
    }
    /**
     * @return Returns the logSOAPMessage.
     */
    public boolean isLogSOAPMessage() {
        return logSOAPMessage;
    }
    /**
     * @param logSOAPMessage The logSOAPMessage to set.
     */
    public void setLogSOAPMessage(boolean logSOAPMessage) {
        this.logSOAPMessage = logSOAPMessage;
    }

    public boolean isIndentSOAPLog() {
        return indentSOAPLog;
    }
    public void setIndentSOAPLog(boolean indentSOAPLog) {
        this.indentSOAPLog = indentSOAPLog;
    }

//  Operations	

    /**
     * @throws IOException
     */
    public void readPropertyFile() throws IOException {
        File propFile = FileUtils.resolve(this.propertyFile);
        BufferedInputStream bis= new BufferedInputStream( new FileInputStream( propFile ));
        try {
            metadataProps.clear();
            metadataProps.load(bis);
            if ( sourceID != null ) {
                metadataProps.setProperty(SOURCE_ID, sourceID);
            }
        } finally {
            bis.close();
        }
    }

    public List listAuthorRoles() throws IOException {
        return this.authorRoles;
    }
    public List listDocTitleCodes() throws IOException {
        return readCodeFile(docTitleCodeListFile);
    }
    public List listEventCodes() throws IOException {
        return readCodeFile(eventCodeListFile);
    }
    public List listClassCodes() throws IOException {
        return readCodeFile(classCodeListFile);
    }
    public List listContentTypeCodes() throws IOException {
        return readCodeFile(contentTypeCodeListFile);
    }
    public List listHealthCareFacilityTypeCodes() throws IOException {
        return readCodeFile(healthCareFacilityCodeListFile);
    }
    public List listConfidentialityCodes() throws IOException {
        return confidentialityCodes;
    }

    /**
     * @throws IOException
     * 
     */
    public List readCodeFile(File codeFile) throws IOException {
        if ( codeFile == null ) return new ArrayList();
        List l = (List) mapCodeLists.get(codeFile);
        if ( l == null ) {
            l = new ArrayList();
            File file = FileUtils.resolve(codeFile);
            if ( file.exists() ) {
                BufferedReader r = new BufferedReader( new FileReader(file));
                String line;
                while ( (line = r.readLine()) != null ) {
                    if ( ! (line.charAt(0) == '#') ) {
                        l.add( line );
                    }
                }
                log.debug("Codes read from code file "+codeFile);
                log.debug("Codes:"+l);
                mapCodeLists.put(codeFile,l);
            } else {
                log.warn("Code File "+file+" does not exist! return empty code list!");
            }
        }
        return l;
    }

    public void clearCodeFileCache() {
        mapCodeLists.clear();
    }

    public boolean sendSOAP( String metaDataFilename, String docNames, String url ) {
        File metaDataFile = new File( metaDataFilename);

        XDSIDocument[] docFiles = null;
        if ( docNames != null && docNames.trim().length() > 0) {
            StringTokenizer st = new StringTokenizer( docNames, "," );
            docFiles = new XDSIDocument[ st.countTokens() ];
            for ( int i=0; st.hasMoreTokens(); i++ ) {
                docFiles[i] = XDSIFileDocument.valueOf( st.nextToken() );
            }
        }
        return sendSOAP( readXMLFile(metaDataFile), docFiles, url );
    }

    public boolean sendSOAP( Document metaData, XDSIDocument[] docs, String url ) {
        if ( isUseXDSb() ) {
            if ( xdsbSourceServiceName != null ) {
                return exportXDSb(metaData, docs);
            } else {
                log.warn("UseXDSb is enabled but XdsbSourceServiceName is not configured! Use XDS.a instead!");
            }
        }
        if ( url == null ) url = this.docRepositoryURI;
        log.info("Send 'Provide and Register Document Set' request to "+url);
        SOAPConnection conn = null;
        try {
            configTLS(url);
            MessageFactory messageFactory = MessageFactory.newInstance();
            SOAPMessage message = messageFactory.createMessage();
            MimeHeaders hd = message.getMimeHeaders();
            hd.addHeader("SOAPAction", "urn:ihe:iti:2007:ProvideAndRegisterDocumentSet");
            
            SOAPEnvelope envelope = message.getSOAPPart().getEnvelope();
            SOAPBody soapBody = envelope.getBody();
            SOAPElement bodyElement = soapBody.addDocument(metaData);
            if ( docs != null ) {
                for (int i = 0; i < docs.length; i++) {
                    DataHandler dhAttachment = docs[i].getDataHandler();
                    AttachmentPart part = message.createAttachmentPart(dhAttachment);
                    part.setMimeHeader("Content-Type", docs[i].getMimeType());
                    String docId = docs[i].getDocumentID();
                    if ( docId.charAt(0) != '<' ) {//Wrap with < >
                        docId = "<"+docId+">";
                    }

                    part.setContentId(docId);
                    if ( log.isDebugEnabled()){
                        log.debug("Add Attachment Part ("+(i+1)+"/"+docs.length+")! Document ID:"+part.getContentId()+" mime:"+docs[i].getMimeType());
                    }
                    message.addAttachmentPart(part);
                }
            }
            SOAPConnectionFactory connFactory = SOAPConnectionFactory.newInstance();

            conn = connFactory.createConnection();
            log.debug("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
            log.debug("send request to "+url);
            dumpSOAPMessage(message, "SOAP request");
            SOAPMessage response = conn.call(message, url);
            dumpSOAPMessage(response, "SOAP response");
            log.debug("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
            return checkResponse( response );
        } catch ( Throwable x ) {
            log.error("Cant send SOAP message! Reason:", x);
            return false;
        } finally {
            if ( conn != null ) try {
                conn.close();
            } catch (SOAPException ignore) {}
        }

    }


    private void configTLS(String url) {
        if ( isHttpCfgServiceAvailable() ) {
            try {
                server.invoke(xdsHttpCfgServiceName,
                        "configTLS",
                        new Object[] { url },
                        new String[] { String.class.getName() } );
            } catch ( Exception x ) {
                log.error( "Exception occured in configTLS: "+x.getMessage(), x );
            }
        }
    }
    private boolean exportXDSb(Document metaData, XDSIDocument[] docs) {
        log.info("export Document(s) as XDS.b Document Source!");
        Node rsp = null;
        try {
            Map mapDocs = new HashMap();
            if ( docs != null) {
                for ( int i = 0 ; i < docs.length ; i++) {
                    mapDocs.put(docs[i].getDocumentID(), docs[i].getDataHandler() ); 
                }
            }
            log.info("call xds.b exportDocuments");
            rsp = (Node) server.invoke(this.xdsbSourceServiceName,
                    "exportDocuments",
                    new Object[] { metaData, mapDocs },
                    new String[] { Node.class.getName(), Map.class.getName() });
            log.info("response from xds.b exportDocuments:"+rsp);
            return checkResponse(rsp.getFirstChild());
        } catch (Exception x) {
            log.error("Export Documents failed via XDS.b transaction",x);
            return false;
        }
    }
    
    private boolean checkXDSDocument(PublishStudy study, String docUID) {
        log.info("get XDS Document for documentUniqueID:"+docUID);
        String rsp = null;
        try {
            rsp = getXDSDocument(docUID, "getDocumentsByUniqueId");
            log.info("############ StoredQuery getDocumentsByUniqueId rsp:"+rsp);
            final String[] docEntryUIDandRepUID = new String[2];

            SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
            DefaultHandler handler = new DefaultHandler(){
                private boolean isRepositoryUniqueId;
                private boolean isSlotValue;
                public void startElement(String uri, String localName,
                         String qName, Attributes attrs) {
                    localName = toLocalName(localName, qName);
                     if (localName.equals("ExtrinsicObject")) {
                         if (docEntryUIDandRepUID[0] == null)
                             docEntryUIDandRepUID[0] = attrs.getValue("id");
                     } else if (localName.equals("Slot") && "".equals(attrs.getValue("name"))) {
                         isRepositoryUniqueId = true;
                     } else if (localName.equals("Value")) {
                         isSlotValue = isRepositoryUniqueId;
                     }
                }

                public void characters(char ch[], int start, int length) throws SAXException {
                    if (isSlotValue) {
                        docEntryUIDandRepUID[1] = new String(ch, start, length);
                        isSlotValue = false;
                        isRepositoryUniqueId = false;
                    }
                }
             };
            parser.parse(new ByteArrayInputStream(rsp.getBytes("UTF-8")), handler);
            if (docEntryUIDandRepUID[0] != null) {
                if (study.getStudyPk() != null) {
                    getContentEdit().commitPublishedStudy(study.getStudyPk(), docUID, docEntryUIDandRepUID[0], docEntryUIDandRepUID[1]);
                } else {
                    getContentEdit().removePublishedStudy(study.getPublishedStudyPk());
                }
                return true;
            } else {
                log.warn("Query result of published document ("+docUID+") is empty! response:"+rsp);
            }
        } catch (Exception x) {
            log.error("checkXDSDocument failed via XDS.b transaction!",x);
        }
        return false;
    }
    
    private String toLocalName(String localName, String qName) {
        if (localName == null | localName.trim().length() == 0) {
            int pos = qName.indexOf(':');
            localName = pos == -1 ? qName : qName.substring(++pos);
        }
        return localName;
    }

    private String getXDSDocument(String docUID, String qryCmd)
            throws ReflectionException, InstanceNotFoundException,
            MBeanException {
        String rsp = (String) server.invoke(this.xdsQueryServiceName,
                "getAsXML",
                new Object[] { qryCmd, docUID, false },
                new String[] { String.class.getName(), String.class.getName(), boolean.class.getName() });
        if (log.isDebugEnabled()) 
            log.debug("response from xds.b getDocumentsByUniqueId:"+rsp);
        return rsp;
    }
    
    private void logITI15(String submissionUid, String patId, boolean success) {
        if ( log.isDebugEnabled() )
                log.debug("log ITI-15 (XDS export) message! submissionUid:"+submissionUid+" patId:"+patId+" success:"+success);
        try {
            server.invoke(this.xdsbSourceServiceName,
                    "logExport",
                    new Object[] { submissionUid, patId, new Boolean(success) },
                    new String[] { String.class.getName(), String.class.getName(), "boolean" });
        } catch (Exception x) {
            log.error("Audit log ITI-15 failed!",x);
        }
    }

    public static String resolvePath(String fn) {
        File f = new File(fn);
        if (f.isAbsolute()) return f.getAbsolutePath();
        File serverHomeDir = new File(System.getProperty("jboss.server.home.dir"));
        return new File(serverHomeDir, f.getPath()).getAbsolutePath();
    }

    public boolean sendSOAP(String kosIuid, Properties mdProps) throws SQLException {
        Dataset kos = queryInstance( kosIuid );
        if ( kos == null ) return false;
        if ( mdProps == null ) mdProps = this.metadataProps;
        List files = new QueryFilesCmd(kosIuid).getFileDTOs();
        if ( files == null || files.size() == 0 ) {
            return false;
        }
        FileDTO fileDTO = (FileDTO) files.iterator().next();
        File file = FileUtils.toFile(fileDTO.getDirectoryPath(), fileDTO.getFilePath());
        XDSIDocument[] docs = new XDSIFileDocument[]
                                                   {new XDSIFileDocument(file,"application/dicom",DOCUMENT_ID,kosIuid)};
        XDSMetadata md = new XDSMetadata(kos, mdProps, docs);
        Document metadata = md.getMetadata();
        return sendSOAP(metadata, docs, null);
    }


    private Dataset queryInstance(String iuid) {
        try {
            return getContentManager().getInstanceInfo(iuid, true);
        } catch (Exception e) {
            log.error("Query for SOP Instance UID:" + iuid + " failed!", e);
        }
        return null;
    }

    public boolean sendSOAP(String kosIuid) throws SQLException {
        Dataset kos = queryInstance( kosIuid );
        return sendSOAP(kos,null);
    }
    public boolean sendSOAP(Dataset kos, Properties mdProps) throws SQLException {
        log.debug("Manifest Key Selection Object:");log.debug(kos);
        String affPatID = null;
        if (kos != null && !kos.isEmpty()) {
            affPatID = getAffinityDomainPatientID(kos);
            if (affPatID == null)
                return false;
        }
        if ( mdProps == null ) mdProps = this.metadataProps;
        XDSIDocument mainDoc;
        boolean hasInstances = hasManifestInstances(kos);
        if ( !hasInstances ) {
            if (mdProps.getProperty(RPLC_DOC_ENTRY_UID) == null) {
                log.info("Ignore publishing of empty study!");
                return false;
            }
            mdProps.setProperty("submissionSetTitle", "Study deleted");
            log.info("Publish a 'study deleted' PDF document for empty study to replace manifest!");
            kos.putUI(Tags.SOPInstanceUID, UIDGenerator.getInstance().createUID());
            mainDoc = getStudyDeletedDocument(mdProps, kos);
            if (mainDoc == null)
                return false;
        } else {
            if (addStudyInstanceUIDSlot) {
                mdProps.setProperty(STUDY_INSTANCE_UID, kos.getString(Tags.StudyInstanceUID));
            }
            mainDoc = new XDSIDatasetDocument(kos, "application/dicom", DOCUMENT_ID);
        }
        if (affPatID != null) {
            mdProps.setProperty(XAD_PATIENT_ID, affPatID);
            mdProps.setProperty(SRC_PATIENT_ID, getSourcePatientID(kos));
        }
        String user = mdProps.getProperty("user");
        XDSIDocument[] docs;
        String pdfIUID = mdProps.getProperty("pdf_iuid");
        if ( pdfIUID == null || !UIDs.isValid(pdfIUID) ) {
            docs = new XDSIDocument[]{ mainDoc };
        } else {
            String pdfUID = UIDGenerator.getInstance().createUID();
            log.info("Add PDF document with IUID "+pdfIUID+" to this submission set!");
            try {
                docs = new XDSIDocument[]{ mainDoc,
                        new XDSIURLDocument(new URL(ridURL+pdfIUID),"application/pdf",PDF_DOCUMENT_ID,pdfUID)};
            } catch (Exception x) {
                log.error("Cant attach PDF document! :"+x.getMessage(), x);
                return false;
            }
        }
        addAssociations(docs, mdProps);
        String rplcDocUID = mdProps.getProperty(RPLC_DOC_ENTRY_UID);
        if (rplcDocUID != null) {
            docs[0].addAssociation(rplcDocUID, "RPLC", null);
        }
        XDSMetadata md = new XDSMetadata(kos, mdProps, docs);
        Document metadata = md.getMetadata();
        boolean b = sendSOAP(metadata, docs , null);
        logExport(md, user, b, hasInstances);
        return b;
    }

    private boolean hasManifestInstances(Dataset kos) {
        Dataset item = kos.getItem(Tags.CurrentRequestedProcedureEvidenceSeq);
        if (item != null) {
            Dataset refSerItem = item.getItem(Tags.RefSeriesSeq);
            if ( refSerItem != null) {
                Dataset refSopItem = refSerItem.getItem(Tags.RefSOPSeq);
                if (refSopItem != null)
                    return true;
            }
        }
        return false;
    }
    
    private XDSIDocument getStudyDeletedDocument(final Properties mdProps, final Dataset kos) {
        String docUID = mdProps.getProperty(RPLC_DOC_ENTRY_UID);
        try {
            String rsp = getXDSDocument(docUID, "getDocuments");
            log.info("###### StoredQuery getDocuments rsp:"+rsp);
            final boolean[] hasExtrinsicObject = new boolean[]{false};
            try {
                SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
                DefaultHandler handler = new DefaultHandler(){
                    private String slotName;
                    private StringBuilder slotValue;
                    private boolean isSlotValue;
                    public void startElement(String uri, String localName,
                             String qName, Attributes attrs) {
                         localName = toLocalName(localName, qName);
                         if (localName.equals("ExtrinsicObject")) {
                             hasExtrinsicObject[0] = true;
                         } else if (localName.equals("ExternalIdentifier")) {
                             if (UUID.XDSDocumentEntry_patientId.equals(attrs.getValue("identificationScheme"))) {
                                 mdProps.setProperty(XAD_PATIENT_ID, toPID(attrs.getValue("value")));
                             }
                         } else if (localName.equals("Slot")) {
                             slotName = attrs.getValue("name");
                             slotValue = new StringBuilder();
                         } else if (localName.equals("Value")) {
                             isSlotValue = slotName != null;
                         }
                    }
                    public void endElement (String uri, String localName, String qName) throws SAXException {
                        localName = toLocalName(localName, qName);
                        if (localName.equals("Slot")) {
                            slotName = null;
                            slotValue = null;
                        } else if (isSlotValue && localName.equals("Value")) {
                            isSlotValue = false;
                            if ("sourcePatientId".equals(slotName)) {
                                String srcPID = toPID(slotValue.toString());
                                mdProps.setProperty(SRC_PATIENT_ID, srcPID);
                                int pos = srcPID.indexOf('^');
                                kos.putLO(Tags.PatientID, srcPID.substring(0,pos));
                                kos.putLO(Tags.IssuerOfPatientID, srcPID.substring(pos+3));
                            } else if ("creationTime".equals(slotName)) {
                                setTime(kos,Tags.InstanceCreationDate, Tags.InstanceCreationTime, slotValue.toString());
                            } else if ("serviceStartTime".equals(slotName)) {
                                setTime(kos,Tags.StudyDate, Tags.StudyTime, slotValue.toString());
                            } else if ("sourcePatientInfo".equals(slotName)) {
                                setPatInfo(kos, slotValue.toString());
                            } else if (STUDY_INSTANCE_UID.equals(slotName)) {
                                String suid = slotValue.toString();
                                mdProps.setProperty(STUDY_INSTANCE_UID, suid);
                                kos.putUI(Tags.StudyInstanceUID, suid);
                            }
                        }
                    }
                    
                    public void characters(char ch[], int start, int length) throws SAXException {
                        if (isSlotValue) {
                            slotValue.append(new String(ch, start, length));
                        }
                    }
                 };
                parser.parse(new ByteArrayInputStream(rsp.getBytes("UTF-8")), handler);
                if (!hasExtrinsicObject[0]) {
                    log.warn("ExtrinsicObject missing in response :"+docUID);
                    return null;
                }
            } catch (Exception e) {
                log.error("getStudyDeletedDocument: SAX parse of response failed! Reason:", e);
            }
        } catch (Exception x) {
            log.error("GetDocument for "+docUID+" failed!",x);
            return null;
        }
        if (this.autoPublishStudyDeletedPDFFile != null) {
            File pdfFile = FileUtils.resolve(this.autoPublishStudyDeletedPDFFile);
            if (pdfFile.isFile()) {
                return new XDSIFileDocument(pdfFile, pdfFile.getName().endsWith(".txt") ? "text/plain" : "application/pdf", 
                        DOCUMENT_ID, kos.getString(Tags.SOPInstanceUID));
            } else {
                log.warn("autoPublishStudyDeletedPDFFile does not exist! use TEXT as fallback!");
            }
        }
        return new XDSIByteArrayDocument("Study has been removed from Source PACS System!", "text/plain", 
                DOCUMENT_ID, kos.getString(Tags.SOPInstanceUID));
    }
    
    private void setPatInfo(Dataset kos, String v) {
        switch (v.charAt(4)) {
            case 5:
                kos.putPN(Tags.PatientName, v.substring(6));
                break;
            case 7:
                kos.putDA(Tags.PatientBirthDate, v.substring(6));
                break;
            case 8:
                kos.putCS(Tags.PatientSex, v.substring(6));
                break;
        }
    }
    private void setTime(Dataset kos, int dateTag, int timeTag, String value) {
        Date d;
        try {
            d = new SimpleDateFormat("yyyyMMddhhmmss").parse(value);
        } catch (ParseException e) {
            log.warn("Failed to parse Date/Time value for "+Tags.toString(dateTag)+"! Use current date.", e);
            d = new Date();
        }
        kos.putDA(dateTag, d);
        kos.putTM(timeTag, d);
    }
    
    private String toPID(String s) {
        return s== null ? null : s.replaceAll("&amp;", "&");
    }
    private void addAssociations(XDSIDocument[] docs, Properties mdProps) {
        if ( mdProps.getProperty("nrOfAssociations") == null) return; 
        int len = Integer.parseInt(mdProps.getProperty("nrOfAssociations"));
        String assoc, uuid, type, status;
        StringTokenizer st;
        for ( int i=0; i < len ; i++ ) {
            assoc = mdProps.getProperty("association_"+i);
            st = new StringTokenizer(assoc,"|");
            uuid = st.nextToken();
            type = st.nextToken();
            status = st.nextToken();
            for ( int j = 0 ; j < docs.length ; j++) {
                docs[j].addAssociation(uuid, type, status);
            }
        }

    }
    public boolean exportPDF(String iuid) throws SQLException, MalformedURLException {
        return exportPDF(iuid,null);
    }
    public boolean exportPDF(String iuid, Properties mdProps) throws SQLException, MalformedURLException {
        log.debug("export PDF to XDS Instance UID:"+iuid);
        Dataset ds = queryInstance(iuid);
        if ( ds == null ) return false;
        String affPatID = getAffinityDomainPatientID(ds);
        if (affPatID == null)
            return false;
        String pdfUID = UIDGenerator.getInstance().createUID();
        log.info("Document UID of exported PDF:"+pdfUID);
        ds.putUI(Tags.SOPInstanceUID,pdfUID);
        if ( mdProps == null ) mdProps = this.metadataProps;
        String user = mdProps.getProperty("user");
        mdProps.setProperty("mimetype", "application/pdf");
        mdProps.setProperty(XAD_PATIENT_ID, affPatID);
        mdProps.setProperty(SRC_PATIENT_ID, getSourcePatientID(ds));
        XDSIDocument[] docs = new XDSIURLDocument[]
                                                  {new XDSIURLDocument(new URL(ridURL+iuid),"application/pdf",PDF_DOCUMENT_ID,pdfUID)};
        addAssociations(docs, mdProps);
        XDSMetadata md = new XDSMetadata(ds, mdProps, docs);
        Document metadata = md.getMetadata();
        boolean b = sendSOAP(metadata,docs , null);
        logExport(md, user, b, false);
        return b;
    }

    public boolean createFolder( Properties mdProps ) {
        String patDsIUID = mdProps.getProperty("folder.patDatasetIUID");
        Dataset ds = queryInstance(patDsIUID);
        String affPatID = getAffinityDomainPatientID(ds);
        if (affPatID == null)
            return false;
        log.info("create XDS Folder for patient:"+ds.getString(Tags.PatientID));
        mdProps.setProperty(XAD_PATIENT_ID, affPatID);
        mdProps.setProperty(SRC_PATIENT_ID, getSourcePatientID(ds));
        log.info("XAD patient:"+mdProps.getProperty(XAD_PATIENT_ID));
        XDSMetadata md = new XDSMetadata(null, mdProps, null);
        Document metadata = md.getMetadata();
        boolean b = sendSOAP(metadata, null , null);
        return b;
    }

    /**
     * @param kos
     * @return
     */
    private Set getSUIDs(Dataset kos) {
        Set suids = null;
        DcmElement sq = kos.get(Tags.CurrentRequestedProcedureEvidenceSeq);
        if ( sq != null ) {
            suids = new LinkedHashSet();
            for ( int i = 0,len=sq.countItems() ; i < len ; i++ ) {
                suids.add(sq.getItem(i).getString(Tags.StudyInstanceUID));
            }
        }
        return suids;
    }

    private void logExport(XDSMetadata metaData, String user, boolean success, boolean hasInstances) {
        Dataset manifest = metaData.getManifest();
        if (hasInstances) {
            try {
                String requestHost = null;
                HttpUserInfo userInfo = new HttpUserInfo(AuditMessage.isEnableDNSLookups());
                user = userInfo.getUserId();
                requestHost = userInfo.getHostName();
                DataExportMessage msg = new DataExportMessage();
                msg.setOutcomeIndicator(success ? AuditEvent.OutcomeIndicator.SUCCESS:
                    AuditEvent.OutcomeIndicator.MINOR_FAILURE);
                msg.addExporterProcess(AuditMessage.getProcessID(), 
                        AuditMessage.getLocalAETitles(),
                        AuditMessage.getProcessName(), user == null,
                        AuditMessage.getLocalHostName());
                if (user != null) {
                    msg.addExporterPerson(user, null, null, true, requestHost);
                }
                String host = "unknown";
                try {
                    host = new URL(docRepositoryURI).getHost();
                } catch (MalformedURLException ignore) {
                }
                msg.addDestinationMedia(docRepositoryURI, null, "XDS-I Export", false, host );
                msg.addPatient(manifest.getString(Tags.PatientID), manifest.getString(Tags.PatientName));
                InstanceSorter sorter = getInstanceSorter(manifest);
                for (String suid : sorter.getSUIDs()) {
                    ParticipantObjectDescription desc = new ParticipantObjectDescription();
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
            } catch (Exception ignore) {
                log.error("Audit log of XDS-I Dicom Export message failed! Ignored!", ignore);
            }
        }
        if ( logITI15 && xdsbSourceServiceName != null) {
            this.logITI15(metaData.getSubmissionSetUID(), metaData.getSubmissionSetPatId(), success);
        }
        
    }

    private InstanceSorter getInstanceSorter(Dataset dsKos) {
        InstanceSorter sorter = new InstanceSorter();
        DcmObjectFactory df = DcmObjectFactory.getInstance();
        DcmElement sq = dsKos.get(Tags.CurrentRequestedProcedureEvidenceSeq);
        if ( sq != null ) {
            for (int i = 0, n = sq.countItems(); i < n; i++) {
                Dataset refStudyItem = sq.getItem(i);
                String suid = refStudyItem.getString(Tags.StudyInstanceUID);
                DcmElement refSerSeq = refStudyItem.get(Tags.RefSeriesSeq);
                for (int j = 0, m = refSerSeq.countItems(); j < m; j++) {
                    Dataset refSer = refSerSeq.getItem(j);
                    DcmElement srcRefSOPSeq = refSer.get(Tags.RefSOPSeq);
                    for (int k = 0, l = srcRefSOPSeq.countItems(); k < l; k++) {
                        Dataset srcRefSOP = srcRefSOPSeq.getItem(k);
                        Dataset refSOP = df.newDataset();
                        String cuid = srcRefSOP.getString(Tags.RefSOPClassUID);
                        refSOP.putUI(Tags.RefSOPClassUID, cuid);
                        String iuid = srcRefSOP.getString(Tags.RefSOPInstanceUID);
                        refSOP.putUI(Tags.RefSOPInstanceUID, iuid);
                        sorter.addInstance(suid, cuid, iuid, null);
                    }
                }
            }
        } else { //not a manifest! (PDF)
            sorter.addInstance(dsKos.getString(Tags.StudyInstanceUID), dsKos.getString(Tags.SOPClassUID),
                    dsKos.getString(Tags.SOPInstanceUID), null);
        }
        return sorter;
    }

    /**
     * @param kos
     * @return
     */
    public String getAffinityDomainPatientID(Dataset kos) {
        String patID = kos.getString(Tags.PatientID);
        String issuer = kos.getString(Tags.IssuerOfPatientID);
        if ( affinityDomain.charAt(0) == '=') {
            if ( affinityDomain.length() == 1 ) {
                patID+="^^^";
                if ( issuer == null ) return patID;
                return patID+issuer;
            } else if (affinityDomain.charAt(1)=='?') {
                log.info("PIX Query disabled: replace issuer with affinity domain! ");
                String newIssuer;
                if (affinityDomain.charAt(2) == '[') {
                    int pos = affinityDomain.indexOf(']', 3);
                    String rplcIssuer = affinityDomain.substring(3, pos);
                    if (rplcIssuer.equals(issuer)) {
                        newIssuer = affinityDomain.substring(++pos);
                    } else {
                        log.info("PatientID not valid for XDS-I export! Wrong Issuer! patID:"+patID+"^^^"+issuer+" should have issuer "+rplcIssuer+")");
                        return null;
                    }
                } else {
                    newIssuer = affinityDomain.substring(2);
                }
                log.debug("patID changed! ("+patID+"^^^"+issuer+" -> "+patID+"^^^"+newIssuer.substring(2)+")");
                return patID+"^^^"+newIssuer;
            } else {
                log.info("PIX Query disabled: replace configured patient ID! :"+affinityDomain.substring(1));
                return affinityDomain.substring(1);
            }
        }
        if ( this.pixQueryServiceName == null ) {
            log.info("PIX Query disabled: use source patient ID!");
            patID+="^^^";
            if ( issuer == null ) return patID;
            return patID+issuer;
        } else {
            try {
                if ( localDomain != null ) {
                    if ( localDomain.charAt(0) == '=') {
                        String oldIssuer = issuer;
                        issuer = localDomain.substring(1);
                        log.info("PIX Query: Local affinity domain changed from "+oldIssuer+" to "+issuer);
                    } else if ( issuer == null ) {
                        log.info("PIX Query: Unknown local affinity domain changed to "+issuer);
                        issuer = localDomain;
                    }
                } else if ( issuer == null ) {
                    issuer = "";
                }
                List pids = (List) server.invoke(this.pixQueryServiceName,
                        "queryCorrespondingPIDs",
                        new Object[] { patID, issuer, new String[]{affinityDomain} },
                        new String[] { String.class.getName(), String.class.getName(), String[].class.getName() });
                String pid, affPid;
                for ( Iterator iter = pids.iterator() ; iter.hasNext() ; ) {
                    pid = toPIDString((String[]) iter.next());
                    log.debug("Check if from domain! PatientID:"+pid);
                    if ( isFromDomain(pid) ) {
                        return pid;
                    }
                }
                log.error("Patient ID is not known in Affinity domain:"+affinityDomain);
                return null;
            } catch (Exception e) {
                log.error("Failed to get patientID for Affinity Domain:", e);
                return null;
            }
        }
    }

    public String getSourcePatientID(Dataset kos) {
        String patID = kos.getString(Tags.PatientID);
        String issuer = kos.getString(Tags.IssuerOfPatientID);
        if ( localDomain != null ) {
            if ( localDomain.charAt(0) == '=') {
                String oldIssuer = issuer;
                issuer = localDomain.substring(1);
                log.info("Local domain changed from "+oldIssuer+" to "+issuer);
            } else if ( issuer == null ) {
                issuer = localDomain;
                log.info("Unknown local assigning authority changed to "+issuer);
            }
        } else if ( issuer == null ) {
            issuer = "";
        }
        String pid = toPIDString(StringUtils.split(patID+"&"+issuer, '&'));
        log.debug("Source PatientID:"+pid);
        return pid;
    }
    
    protected boolean isFromDomain(String pid) {
        String assAuth = getAssigningAuthority(pid);
        if (assAuth == null || assAuth.indexOf('&') == -1)
            return false;
        if (assAuth.charAt(0) != '&') { //is namespace id subcomponent not empty?
            //we only compare <universal ID> and <universal ID type
            //leave subcomponent delimiter! (affinityDomain will almost always have no namespace id)
            assAuth = assAuth.substring(assAuth.indexOf('&'));
        }
        if (affinityDomain.charAt(0) == '&') {
            return assAuth.equals(affinityDomain);
        } else { //affinityDomain has namespace id but we ignore that!
            return assAuth.equals(affinityDomain.substring(affinityDomain.indexOf('&')));
        }
    }

    private String getAssigningAuthority(String pid) {
        int pos = 0;
        for ( int i = 0 ; i < 3 ; i++) {
            pos = pid.indexOf('^', pos);
            if ( pos == -1 ) {
                log.warn("patient id does not contain AssigningAuthority component! :"+pid);
                return null;
            }
            pos++;
        }
        int end = pid.indexOf('^', pos);
        return end == -1 ? pid.substring(pos) : pid.substring(pos, end);
    }

    private String toPIDString(String[] pid) {
        if (pid == null || pid.length < 1) return "";
        StringBuffer sb = new StringBuffer(pid[0]);
        log.debug("pid[0]:"+pid[0]);
        if ( pid.length > 1 ) {
            sb.append("^^^");
            if (removeNamespaceID) {
                log.info("Remove Namespace ID '"+pid[1]+"'!");
            } else {
                sb.append(pid[1]);
                log.debug("pid[1]:"+pid[1]);
            }
            for (int i = 2 ; i < pid.length; i++) {
                sb.append('&').append(pid[i]);
                log.debug("pid["+i+"]:"+pid[i]);
            }
        }
        return sb.toString();
    }
    /**
     * @param response
     * @return
     * @throws SOAPException
     */
    private boolean checkResponse(Node n) throws SOAPException {
        String status = n.getAttributes().getNamedItem("status").getNodeValue();
        log.info("XDSI: SOAP response status."+status);
        if ("Success".equals(status)) {
            return true;
        } else {
            StringBuffer sb = new StringBuffer();
            try {
                NodeList errList = n.getChildNodes().item(0).getChildNodes();
                Node errNode;
                for ( int j = 0, lenj = errList.getLength() ; j < lenj ; j++ ) {
                    sb.setLength(0); 
                    sb.append("Error (").append(j).append("):");
                    if ( (errNode = errList.item(j)) != null && errNode.getFirstChild() != null ) {
                        sb.append( errNode.getFirstChild().getNodeValue());
                    }
                    log.info(sb.toString());
                }
            } catch (Exception ignoreMissingErrorList){}
            return false;
        }
    }
    private boolean checkResponse(SOAPMessage response) throws SOAPException {
        log.debug("checkResponse:"+response);
        try {
            SOAPBody body = response.getSOAPBody();
            log.debug("SOAPBody:"+body );
            NodeList nl = body.getChildNodes();
            if ( nl.getLength() > 0  ) {
                for ( int i = 0, len = nl.getLength() ; i < len ; i++ ) {
                    Node n = nl.item(i);
                    if ( n.getNodeType() == Node.ELEMENT_NODE &&
                            "RegistryResponse".equals(n.getLocalName() ) ) {
                        return checkResponse(n);
                    }
                }
            } else {
                log.warn("XDSI: Empty SOAP response!");
            }
        } catch ( Exception x ) {
            log.error("Cant check response!", x);
        }
        return false;
    }
    /**
     * @param message
     * @return
     * @throws IOException
     * @throws SOAPException
     * @throws SAXException
     * @throws ParserConfigurationException
     */
    private void dumpSOAPMessage(SOAPMessage message, String title) throws SOAPException, IOException, ParserConfigurationException, SAXException {
        if ( !logSOAPMessage )
            return;
        log.info("-------------------------------- "+title+" ----------------------------------");
        Source s = message.getSOAPPart().getContent();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write("SOAP message:".getBytes());
            Transformer t = TransformerFactory.newInstance().newTransformer();
            if (indentSOAPLog)
                t.setOutputProperty("indent", "yes");
            t.transform(s, new StreamResult(out));
            log.info(out.toString());
        } catch (Exception e) {
            log.warn("Failed to log SOAP message", e);
        }
        log.info("-------------------------------");
    }

    private Document readXMLFile(File xmlFile){
        Document document = null;
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        try {
            dbFactory.setNamespaceAware(true);
            DocumentBuilder builder = dbFactory.newDocumentBuilder();
            document = builder.parse(xmlFile);
        } catch (Exception x) {
            log.error("Cant read xml file:"+xmlFile, x);
        }
        return document;
    }

    protected void startService() throws Exception {
        server.addNotificationListener(ianScuServiceName,
                ianListener, IANScuService.NOTIF_FILTER, null);
        server.addNotificationListener(ServerImplMBean.OBJECT_NAME, this, null, null);
    }

    protected void stopService() throws Exception {
        stopScheduler();
        server.removeNotificationListener(ianScuServiceName,
                ianListener, IANScuService.NOTIF_FILTER, null);
    }
    
    protected void startScheduler() throws Exception {
        listenerID = scheduler.startScheduler(timerIDAutoPublish,
                autoPublishInterval,
                autoPublishListener);
    }
    private void stopScheduler() throws Exception {
        scheduler.stopScheduler(timerIDAutoPublish, listenerID,
                autoPublishListener);
    }


    private void onIAN(Dataset mpps) {
        try {
            log.debug("Received mpps");
            log.debug(mpps);

            String aet = mpps.getString(Tags.PerformedStationAET);
            List autoPublish = Arrays.asList(autoPublishAETs);
            if (autoPublish.indexOf(aet) != -1) {
                List iuids = getIUIDS(mpps);
                log.debug("iuids:" + iuids);
                Dataset manifest = getKeyObject(iuids, getAutoPublishRootInfo(), null);
                log.debug("Created manifest KOS:");
                log.debug(manifest);
                try {
                    sendSOAP(manifest, getAutoPublishMetadataProperties(manifest, null));
                } catch (SQLException x) {
                    log.error("XDS-I Autopublish failed! Reason:", x);
                }
                return;
            }
        } catch (Exception e) {
            log.error("Error in onIAN:" + e);
            log.debug("Exception:", e);
        }
    }

    private List getIUIDS(Dataset mpps) {
        List l = new ArrayList();
        DcmElement refSerSQ = mpps.get(Tags.PerformedSeriesSeq);
        if ( refSerSQ != null ) {
            Dataset item;
            DcmElement refSopSQ;
            for ( int i = 0 ,len = refSerSQ.countItems() ; i < len ; i++){
                refSopSQ = refSerSQ.getItem(i).get(Tags.RefImageSeq);
                for ( int j = 0 ,len1 = refSopSQ.countItems() ; j < len1 ; j++){
                    item = refSopSQ.getItem(j);
                    log.debug("refSerItem:" + i + " refSopItem:" + j + " item:" + item);
                    l.add( item.getString(Tags.RefSOPInstanceUID));
                }
            }
        }
        return l;
    }

    private Dataset getAutoPublishRootInfo() {
        Dataset rootInfo = DcmObjectFactory.getInstance().newDataset();
        DcmElement sq = rootInfo.putSQ(Tags.ConceptNameCodeSeq);
        Dataset item = sq.addNewItem();
        StringTokenizer st = new StringTokenizer(autoPublishDocTitle,"^");
        item.putSH(Tags.CodeValue,st.hasMoreTokens() ? st.nextToken():"autoPublish");
        item.putLO(Tags.CodeMeaning, st.hasMoreTokens() ? st.nextToken():"default doctitle for autopublish");
        item.putSH(Tags.CodingSchemeDesignator,st.hasMoreTokens() ? st.nextToken():null);
        return rootInfo;
    }

    private Properties getAutoPublishMetadataProperties(Dataset manifest, String rplcDocEntryUID) {
        final Properties props = new Properties();
        if (autoPublishPropertyFile != null ) {
        BufferedInputStream bis = null;
            try {
                File propFile = FileUtils.resolve(this.autoPublishPropertyFile);
                bis= new BufferedInputStream( new FileInputStream( propFile ));
                props.load(bis);
                if ( sourceID != null ) {
                    new Properties().setProperty(SOURCE_ID, sourceID);
                }
            } catch (IOException x) {
                log.error("Cant read Metadata Properties for AutoPublish!",x);
            } finally {
                if (bis != null) {
                    try {
                        bis.close();
                    } catch (IOException ignore) {}
                }
            }
        }
        log.info("++++++ autoPublishXSLFile:"+autoPublishXSLFile);
        log.info("++++++ manifest:"+manifest);
        if (autoPublishXSLFile != null && manifest != null && !manifest.isEmpty()) {
            try {
                File xslFile = FileUtils.resolve(autoPublishXSLFile);
                Templates tpl = templates.getTemplates(xslFile);
                Dataset manifestInfo = this.getContentManager().getXDSManifestInfo(manifest);
                log.info("XDS-I transform series info to metadata properties:");
                log.debug(manifestInfo);
                XSLTUtils.logDataset(manifestInfo, "xds", "manifestInfo");
                TransformerHandler th = ((SAXTransformerFactory)TransformerFactory.newInstance()).newTransformerHandler(tpl);
                th.setResult(new SAXResult(new DefaultHandler(){

                    public void startElement(String uri, String localName,
                             String qName, Attributes attrs) {
                         if (qName.equals("property")) {
                             log.info("++++++ setProperty("+attrs.getValue("name")+", "+attrs.getValue("value")+")");
                             props.setProperty(attrs.getValue("name"), attrs.getValue("value"));
                         }
                     }
                 }));
                log.info("++++++ update setProperty by xslt");
                manifestInfo.writeDataset2(th, null, null, 64, null);
            } catch (Exception e) {
                log.error("Get metadata properties from seriesInfo failed:", e);
            }

        }

        if (rplcDocEntryUID != null)
            props.setProperty(RPLC_DOC_ENTRY_UID, rplcDocEntryUID);
        return props;
    }

    private Dataset getKeyObject(Collection iuids, Dataset rootInfo, List contentItems) {
        Object o = null;
        try {
            o = server.invoke(keyObjectServiceName,
                    "getKeyObject",
                    new Object[] { iuids, rootInfo, contentItems },
                    new String[] { Collection.class.getName(), Dataset.class.getName(), Collection.class.getName() });
        } catch (RuntimeMBeanException x) {
            log.warn("RuntimeException thrown in KeyObject Service:"+x.getCause());
            throw new IllegalArgumentException(x.getCause().getMessage());
        } catch (Exception e) {
            log.warn("Failed to create Key Object:", e);
            throw new IllegalArgumentException("Error: KeyObject Service cant create manifest Key Object! Reason:"+e.getClass().getName());
        }
        return (Dataset) o;
    }
    
    private void checkAutoPublish() {
        log.info("############ check Studies for XDS AutoPublish");
        if (xdsQueryServiceName == null) {
            log.info("XdsQueryServiceName not configured! Skipped");
            return;
        }
        if (autoPublishSQLcmd.isEmpty()) {
            log.info("No (valid) SQL defined for auto publish!");
        }
        long t1 = System.currentTimeMillis();
        Map<String, Object> params = getSQLparams();
        int countSQL = 0, nrStudies = 0, countSuccess = 0, countSkipped = 0;
        for (QueryXdsPublishCmd cmd : autoPublishSQLcmd) {
            countSQL++;
            if (!cmd.checkProposedExecutionHours()) {
                if (log.isDebugEnabled())
                    log.debug("Execution of SQL #"+countSQL+" is skipped due to proposedExecutionHours! sql:"+cmd);
                countSkipped++;
                continue;
            }
            log.debug("Use SQL:"+cmd);
            try {
                List<PublishStudy> studies = cmd.getPublishStudies(params);
                nrStudies += studies.size();
                if (log.isDebugEnabled()) {
                    log.debug("SQL #"+countSQL+": Found Studies to publish:"+studies);
                } else {
                    log.info("SQL #"+countSQL+": Found "+studies.size()+" Studies to publish!");
                }
                for (PublishStudy study : studies) {
                    if (publishStudy(study))
                        countSuccess++;
                }
            } catch (SQLException x) {
                log.error("Execute SQL failed!", x);
            }
        }
        if (countSkipped == autoPublishSQLcmd.size()) {
            log.info("############ No SQL query defined for current hour of day!");
        } else {
            log.info("############ "+countSuccess+" of "+nrStudies+" Studies published to XDS in "+
                (System.currentTimeMillis() - t1)+"ms.");
        }
    }
    private Map<String, Object> getSQLparams() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("AfterDate", this.afterDate);
        params.put("BeforeDate", this.beforeDate);
        params.put("OlderThan", this.olderThan);
        params.put("NotOlderThan", this.notOlderThan);
        if (log.isDebugEnabled())
            log.debug("SQL params:"+params);
        return params;
    }

    private boolean publishStudy(PublishStudy study) {
        log.debug("Publish study pk:"+study.getStudyPk());
        Dataset manifest = null;
        try {
            manifest = study.getStudyPk() != null ?
                getKeyObject(study.getStudyPk(), getAutoPublishRootInfo(), null) :
                DcmObjectFactory.getInstance().newDataset();
            if (sendSOAP(manifest, getAutoPublishMetadataProperties(manifest, study.getDocumentEntryUID()))) {
                return checkXDSDocument(study, manifest.getString(Tags.SOPInstanceUID));
            }
        } catch (Exception x) {
            log.error("Failed to publish Study! "+ (manifest == null ? "Create manifest KOS failed:" : "Provide and register failed:"), x);
        }
        return false;
    }
    
    private Dataset getKeyObject(long studyPk, Dataset rootInfo, List contentItems) {
        Object o = null;
        try {
            o = server.invoke(keyObjectServiceName,
                    "getKeyObject",
                    new Object[] { studyPk, rootInfo, contentItems },
                    new String[] { long.class.getName(), Dataset.class.getName(), Collection.class.getName() });
        } catch (RuntimeMBeanException x) {
            log.warn("RuntimeException thrown in KeyObject Service:"+x.getCause());
            throw new IllegalArgumentException(x.getCause().getMessage());
        } catch (Exception e) {
            log.warn("Failed to create Key Object:", e);
            throw new IllegalArgumentException("Error: KeyObject Service cant create manifest Key Object! Reason:"+e.getClass().getName());
        }
        return (Dataset) o;
    }
    
    
    private ContentManager getContentManager() throws Exception {
        ContentManagerHome home = (ContentManagerHome) EJBHomeFactory.getFactory()
        .lookup(ContentManagerHome.class, ContentManagerHome.JNDI_NAME);
        return home.create();
    }
    private ContentEdit getContentEdit() throws Exception {
        ContentEditHome home = (ContentEditHome) EJBHomeFactory.getFactory()
        .lookup(ContentEditHome.class, ContentEditHome.JNDI_NAME);
        return home.create();
    }
}
