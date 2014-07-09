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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Map.Entry;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.security.jacc.PolicyContext;
import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.log4j.Logger;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Tags;
import org.dcm4che2.audit.message.ActiveParticipant;
import org.dcm4che2.audit.message.AuditEvent;
import org.dcm4che2.audit.message.AuditMessage;
import org.dcm4che2.audit.message.ParticipantObject;
import org.dcm4che2.audit.message.QueryMessage;
import org.dcm4chex.archive.config.ForwardingRules;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.ejb.interfaces.AEDTO;
import org.dcm4chex.archive.ejb.interfaces.AEManager;
import org.dcm4chex.archive.ejb.interfaces.AEManagerHome;
import org.dcm4chex.archive.exceptions.ConfigurationException;
import org.dcm4chex.archive.mbean.JMSDelegate;
import org.dcm4chex.archive.mbean.TLSConfigDelegate;
import org.dcm4chex.archive.mbean.TemplatesDelegate;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.dcm4chex.archive.util.XSLTUtils;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.DocumentSource;
import org.dom4j.io.SAXContentHandler;
import org.jboss.system.ServiceMBeanSupport;
import org.regenstrief.xhl7.HL7XMLLiterate;
import org.regenstrief.xhl7.HL7XMLReader;
import org.regenstrief.xhl7.HL7XMLWriter;
import org.regenstrief.xhl7.MLLPDriver;
import org.regenstrief.xhl7.XMLWriter;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

public class HL7SendService extends ServiceMBeanSupport implements
        NotificationListener, MessageListener {

    private static final String WEB_REQUEST_KEY =
            "javax.servlet.http.HttpServletRequest";

    private static final ParticipantObject.IDTypeCode PIX_QUERY = 
            new ParticipantObject.IDTypeCode(
                    "ITI-9","IHE Transactions","PIX Query");

    private static final AuditEvent.TypeCode PIX_QUERY_EVENT_TYPE = 
            new AuditEvent.TypeCode(
                    "ITI-9","IHE Transactions","PIX Query");

    private static final String LOCAL_HL7_AET = "LOCAL^LOCAL";

    private static final String DATETIME_FORMAT = "yyyyMMddHHmmss";
    
    private static final String FORWARD_XSL = "hl7forward";
    private static final String XSL_EXT = ".xsl";

    private static long msgCtrlid = System.currentTimeMillis();

    private static long queryTag = msgCtrlid;

    private String queueName;

    private String sendingApplication;

    private String sendingFacility;

    private int acTimeout;

    private int soCloseDelay;

    private boolean auditPIXQuery;

    private ObjectName hl7ServerName;

    private TLSConfigDelegate tlsConfig = new TLSConfigDelegate(this);

    private RetryIntervalls retryIntervalls = new RetryIntervalls();

    private ForwardingRules forwardingRules = new ForwardingRules("");
    
    private int concurrency = 1;

    private JMSDelegate jmsDelegate = new JMSDelegate(this);
    
    protected TemplatesDelegate templates = new TemplatesDelegate(this);


    public String getCharsetName() {
        try {
            return (String) (this.getServer()
            		.getAttribute(hl7ServerName, "CharsetName"));
        } catch (Exception x) {
            throw new ConfigurationException(x);
        }
    }
    
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

    public final String getSendingApplication() {
        return sendingApplication;
    }

    public final void setSendingApplication(String sendingApplication) {
        this.sendingApplication = sendingApplication;
    }

    public final String getSendingFacility() {
        return sendingFacility;
    }

    public final void setSendingFacility(String sendingFacility) {
        this.sendingFacility = sendingFacility;
    }

    public String getRetryIntervalls() {
        return retryIntervalls.toString();
    }

    public void setRetryIntervalls(String text) {
        retryIntervalls = new RetryIntervalls(text);
    }

    public final int getAcTimeout() {
        return acTimeout;
    }

    public final void setAcTimeout(int acTimeout) {
        this.acTimeout = acTimeout;
    }

    public final int getSoCloseDelay() {
        return soCloseDelay;
    }

    public final void setSoCloseDelay(int soCloseDelay) {
        this.soCloseDelay = soCloseDelay;
    }

    public final boolean isAuditPIXQuery() {
        return auditPIXQuery;
    }

    public final void setAuditPIXQuery(boolean auditPIXQuery) {
        this.auditPIXQuery = auditPIXQuery;
    }

    public final String getForwardTemplateDir() {
        return templates.getConfigDir();
    }

    public final void setForwardTemplateDir(String path) {
        templates.setConfigDir(path);
    }

    public final ObjectName getTLSConfigName() {
        return tlsConfig.getTLSConfigName();
    }

    public final void setTLSConfigName(ObjectName tlsConfigName) {
        tlsConfig.setTLSConfigName(tlsConfigName);
    }

    public final String getQueueName() {
        return queueName;
    }

    public final void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public final ObjectName getHL7ServerName() {
        return hl7ServerName;
    }

    public final void setHL7ServerName(ObjectName hl7ServerName) {
        this.hl7ServerName = hl7ServerName;
    }

    public final String getForwardingRules() {
        return forwardingRules.toString();
    }

    public final void setForwardingRules(String s) {
        this.forwardingRules = new ForwardingRules(s);
    }

    public final ObjectName getTemplatesServiceName() {
        return templates.getTemplatesServiceName();
    }

    public final void setTemplatesServiceName(ObjectName serviceName) {
        templates.setTemplatesServiceName(serviceName);
    }

    protected void startService() throws Exception {
        jmsDelegate.startListening(queueName, this, concurrency);
        server.addNotificationListener(hl7ServerName, this,
                HL7ServerService.NOTIF_FILTER, null);
    }

    protected void stopService() throws Exception {
        server.removeNotificationListener(hl7ServerName, this,
                HL7ServerService.NOTIF_FILTER, null);
        jmsDelegate.stopListening(queueName);
    }

    public void handleNotification(Notification notif, Object handback) {
        Object[] hl7msg = (Object[]) notif.getUserData();
        forward((byte[]) hl7msg[0], (Document) hl7msg[1]);
    }

    public int forward(byte[] hl7msg) {
        XMLReader xmlReader = new HL7XMLReader();
        SAXContentHandler hl7in = new SAXContentHandler();
        xmlReader.setContentHandler(hl7in);
        try {
            InputSource in = new InputSource(new InputStreamReader(
                    new ByteArrayInputStream(hl7msg), getCharsetName()));
            xmlReader.parse(in);
        } catch (Exception e) {
            log.error("Failed to parse HL7 message", e);
            return -1;
        }
        return forward(hl7msg, hl7in.getDocument());
    }

    private int forward(byte[] hl7msg, Document msg) {
        MSH msh = new MSH(msg);
        Map<String,String[]> param = new HashMap<String,String[]>();
        String receiving = msh.receivingApplication + '^' + msh.receivingFacility;
        param.put("sending", new String[] { msh.sendingApplication + '^' + msh.sendingFacility });
        param.put("receiving", new String[] { receiving });
        param.put("msgtype", new String[] { msh.messageType + '^'
                + msh.triggerEvent });
        String[] dests = forwardingRules.getForwardDestinationsFor(param);
        int count = 0;
        for (int i = 0; i < dests.length; i++) {
            hl7msg = preprocessForward(hl7msg, msg, msh, dests[i]);
            HL7SendOrder order = new HL7SendOrder(hl7msg, dests[i]);
            try {
                order.processOrderProperties(msh);
            } catch (Exception e) {
                log.error("Failed to process order properties for " + order, e);
            }
            try {
                log.info("Scheduling " + order);
                jmsDelegate.queue(queueName, order, Message.DEFAULT_PRIORITY,
                        0L);
                ++count;
            } catch (Exception e) {
                log.error("Failed to schedule " + order, e);
            }
        }
        return count;
    }

    private byte[] preprocessForward(byte[] hl7msg, Document msg, MSH msh, String receiving) {
        String[] variations = new String[] {"_"+msh.messageType+"^"+msh.triggerEvent, "_"+msh.messageType, "" };
        Templates xslt = templates.findTemplates(new String[]{receiving}, FORWARD_XSL, variations, XSL_EXT);
        if (xslt != null) {
            log.info("Transform HL7 message with hl7forward stylesheet!");
            try {
                Transformer t = xslt.newTransformer();
                ByteArrayOutputStream bos = new ByteArrayOutputStream(hl7msg.length);
                XMLWriter xmlWriter = new HL7XMLWriter(
                        new OutputStreamWriter(bos, getCharsetName()));
                t.transform(new DocumentSource(msg), new SAXResult(xmlWriter.getContentHandler()));
                hl7msg = bos.toByteArray();
            } catch (Exception x) {
                log.error("Can not apply hl7forward stylesheet!", x);
            }
            
        }
        return hl7msg;
    }

    public void onMessage(Message message) {
        ObjectMessage om = (ObjectMessage) message;
        try {
            HL7SendOrder order = (HL7SendOrder) om.getObject();
            try {
            	log.info("Start processing " + order);
                sendTo(order.getHL7Message(), order.getReceiving());
                log.info("Finished processing " + order);
            } catch (Exception e) {
                order.setThrowable(e);
                final int failureCount = order.getFailureCount() + 1;
                order.setFailureCount(failureCount);
                final long delay = retryIntervalls.getIntervall(failureCount);
                if (delay == -1L) {
                    log.error("Give up to process " + order);
                    jmsDelegate.fail(queueName,order);
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

    public Document invoke(byte[] message, String receiver) throws Exception {
        AEDTO localAE = new AEDTO();
        localAE.setTitle(receiver);
        localAE.setHostName("127.0.0.1");
        localAE.setPort(getLocalHL7Port());
        AEDTO remoteAE = LOCAL_HL7_AET.equals(receiver) 
                ? localAE : aeMgt().findByAET(receiver);
        Socket s = tlsConfig.createSocket(localAE, remoteAE);
        boolean ignoreMissingAck = false;
        try {
            MLLPDriver mllpDriver = new MLLPDriver(s.getInputStream(), s
                    .getOutputStream(), true);
            writeMessage(message, receiver, mllpDriver.getOutputStream());
            mllpDriver.turn();
            ignoreMissingAck = Boolean.getBoolean("org.dcm4che.hl7.ignoreMissingAck");
            if (acTimeout > 0) {
                s.setSoTimeout(acTimeout);
            }
            if (!mllpDriver.hasMoreInput()) {
                throw new IOException("Receiver " + receiver
                        + " closed socket " + s
                        + " during waiting on response.");
            }
            return readMessage(mllpDriver.getInputStream());
        } catch (Exception x) {
            if (ignoreMissingAck) {
                log.info("Missing Acknowledge ignored! return null as response message.");
                return null;
            }
            throw x;
        } finally {
            if (soCloseDelay > 0)
                try {
                    Thread.sleep(soCloseDelay);
                } catch (InterruptedException ignore) {
                }
            s.close();
        }
    }

    public void sendTo(byte[] message, String receiver) throws Exception {
        Document rsp = invoke(message, receiver);
        if (rsp != null)
            checkResponse(rsp);
    }

    private void checkResponse(Document rsp) throws HL7Exception {
        MSH msh = new MSH(rsp);
        if ("ACK".equals(msh.messageType)) {
            ACK ack = new ACK(rsp);
            if (!("AA".equals(ack.acknowledgmentCode)
                    || "CA".equals(ack.acknowledgmentCode)))
                throw new HL7Exception(ack.acknowledgmentCode, ack.textMessage);
        } else {
            log.warn("Unsupport response message type: " + msh.messageType
                    + '^' + msh.triggerEvent
                    + ". Assume successful message forward.");
        }
    }

    /**
     * @return
     */
    private int getLocalHL7Port() {
        try {
            return ((Integer) this.getServer().getAttribute(hl7ServerName,
                    "Port")).intValue();
        } catch (Exception x) {
            log.error("Cant get local HL7 port!", x);
            return -1;
        }
    }

    private Document readMessage(InputStream mllpIn) throws IOException,
            SAXException {
        InputSource in = new InputSource(mllpIn);
        in.setEncoding(getCharsetName());
        XMLReader xmlReader = new HL7XMLReader();
        SAXContentHandler hl7in = new SAXContentHandler();
        xmlReader.setContentHandler(hl7in);
        xmlReader.parse(in);
        Document msg = hl7in.getDocument();
        return msg;
    }

    private void writeMessage(byte[] message, String receiving, OutputStream out)
            throws UnsupportedEncodingException, IOException {
        final String charsetName = getCharsetName();
        int offs = writePartTo(out, message, '|', 0, 4); //write MSH 1-4
        final int delim = receiving.indexOf('^');
        out.write(receiving.substring(0, delim).getBytes(charsetName));
        out.write('|');
        out.write(receiving.substring(delim + 1).getBytes(charsetName));
        while (message[++offs] != '|') {} //skip MSH-5 receiving application
        while (message[++offs] != '|') {} //skip MSH-6 receiving facility
        // write remaining message
        out.write(message, offs, message.length - offs);
    }
    
    private int writePartTo(OutputStream out, byte[] ba, char b, int offs, int count) throws IOException {
        for ( int i = offs ; i < ba.length ; i++) {
            if (ba[i]==b) {
                count--;
                if (count == 0) {
                    out.write(ba, offs, i-offs+1);
                    return i;
                }
            }
        }
        return -1;
    }

    public void sendHL7PatientXXX(Dataset ds, String msgType, String sending,
            String receiving, boolean useForward) throws Exception {
        String timestamp = new SimpleDateFormat(DATETIME_FORMAT)
                .format(new Date());
        StringBuffer sb = makeMSH(timestamp, msgType, sending, receiving,
                ++msgCtrlid, "2.3.1");// get MSH for patient information update (ADT^A08)
        addEVN(sb, timestamp);
        addPID(sb, ds);
        sb.append("\rPV1||||||||||||||||||||||||||||||||||||||||||||||||||||\r");
        // PatientClass(2),VisitNr(19) and VisitIndicator(51) ???
        final String charsetName = getCharsetName();
        if (useForward) {
            forward(sb.toString().getBytes(charsetName));
        } else {
            sendTo(sb.toString().getBytes(charsetName), receiving);
        }
    }

    public void sendHL7PatientMerge(Dataset dsDominant, Dataset[] priorPats,
            String sending, String receiving, boolean useForward)
            throws Exception {
        String timestamp = new SimpleDateFormat(DATETIME_FORMAT)
                .format(new Date());
        StringBuffer sb = makeMSH(timestamp, "ADT^A40", sending, receiving,
                ++msgCtrlid, "2.3.1");// get MSH for patient merge (ADT^A40)
        addEVN(sb, timestamp);
        addPID(sb, dsDominant);
        int SBlen = sb.length();
        final String charsetName = getCharsetName();
        for (int i = 0, len = priorPats.length; i < len; i++) {
            sb.setLength(SBlen);
            addMRG(sb, priorPats[i]);
            sb.append('\r');
            if (useForward) {
                forward(sb.toString().getBytes(charsetName));
            } else {
                sendTo(sb.toString().getBytes(charsetName), receiving);
            }
        }

    }

    /**
     * Sends a PDQ query with the parameters being contains in the input Map, and the output
     * being contained in a List of Maps of Strings to Strings - the data being the result of the
     * query.  The last list element is a continuation element if count is non-zero.
     */
    public List<Map<String,String>> sendQBP_Q22(String pdqManager, Map<String,String> query, String domain, int count, String continuation) 
    throws Exception {
        String timestamp = new SimpleDateFormat(DATETIME_FORMAT).format(new Date());
        StringBuffer sb = makeMSH(timestamp, "QBP^Q22", null, pdqManager, ++msgCtrlid, "2.5");
        String qpd = makeQPD_Q22(query,domain);
        sb.append('\r').append(qpd).append("\rRCP|I||||||\r");
        String s = sb.toString();
        log.info("Query PDQ Manager " + pdqManager + ":\n"
                + s.replace('\r', '\n'));
        final String charsetName = getCharsetName();
        Document msg = invoke(s.getBytes(charsetName), pdqManager);
        log.info("PDQ Query returns:");
        logMessage(msg);
        List<Map<String,String>> ret = parsePDQ(msg);
        log.info("Returning PDQ response now:"+toString(ret));
        return ret;
    }
    
    
    public static final String toString(List<Map<String,String>> lst) {        
        StringBuffer sb = new StringBuffer("[");
        boolean listFirst = true;
        for(Map<String,String> map : lst) {
            if(listFirst) listFirst = false;
            else sb.append(",\n  ");
            sb.append("{");
            boolean first = true;
            for(Map.Entry<String,String> me : map.entrySet()) {
                if( first ) first = false;
                else sb.append(", ");
                sb.append(me.getKey()).append(":\"").append(me.getValue()).append("\"");
            }
            sb.append("}");
        }
        sb.append(']');
        return sb.toString();
    }
    
    private static String[] FIELD_NAMES = new String[]{
      null,
      // PID-1
      null,
      null,
      "PatientIDList",
      null,
      // PID-5
      "PatientName",
      "MothersMaidenName", 
      "PatientBirthDate", 
      "PatientSex", 
      // PID-9
      "PatientAlias",
      "Race", 
      "PatientAddress",
      "CountyCode",
      // PID-13
      "PhoneNumberHome",
      "PhoneNumberBusiness",
      "PrimaryLanguage",
      "MaritalStatus",
      // PID-17
      "Religion",
      "PatientAccountNumber",
      "SSNNumber",
      "DriversLicenseNumber",
      // PID-21
      "MothersIdentifier",
      "EthnicGroup",
      "BirthPlace",
      "MultipleBirthIndicator",
      //PID-25
      "BirthOrder",
      "Citizenship",
      "VeteransMilitaryStatus",
      "Nationality",
      //PID-29
      "PatientDeathDateTime",
      "PatientDeathIndicator",
      "IdentityUnknownIndicator",
      "IdentityReliabilityCode",
      //PID-33
      "LastUpdateDateTime",
      "LastUpdateFacility",
      "SpeciesCode",
      "BreedCode",
      //PID-37
      "Strain",
      "ProductionClassCode",
      "TribalCitizenship",
    };
    /** Parse the PID entries into a list of maps */
    public static final List<Map<String,String>> parsePDQ(Document msg) {
        List<Map<String,String>> ret = new ArrayList<Map<String,String>>();
        Element root = msg.getRootElement();
        List<?> content = root.content();
        for(Object c : content) {
            if( !(c instanceof Element) ) continue;
            Element e = (Element) c;
            if( e.getName().equals("PID") ) {
                Map<String,String> pid = new HashMap<String,String>();
                pid.put("Type", "Patient");
                List<?> fields = e.elements(HL7XMLLiterate.TAG_FIELD);
                
                int pidNo = 0;
                for(Object f : fields) {
                    pidNo++;
                    if( pidNo>=FIELD_NAMES.length ) continue;
                    String fieldName = FIELD_NAMES[pidNo];
                    if( fieldName==null ) continue;
                    Element field = (Element) f;
                    if( field.isTextOnly() ) {
                        String txt = field.getText();
                        if( txt==null || txt.length() == 0 ) continue;
                        pid.put(fieldName, txt);
                        continue;
                    }
                    if( pidNo==3 ) {
                        List<?> comps = field.elements(HL7XMLLiterate.TAG_COMPONENT);
                        if (comps.size() < 3) {
                            throw new IllegalArgumentException("Missing Authority in PID-3");           
                        }
                        Element authority = (Element) comps.get(2);
                        List<?> authorityUID = authority.elements(HL7XMLLiterate.TAG_SUBCOMPONENT);
                        pid.put("PatientID",field.getText());
                        StringBuffer issuer = new StringBuffer(authority.getText());
                        for (int i = 0; i < authorityUID.size(); i++) {
                            issuer.append("&").append(((Element) authorityUID.get(i)).getText());
                        }
                        pid.put("IssuerOfPatientID", issuer.toString());
                        continue;
                    }
                    if( pidNo==5 ) {
                        String name = field.getText() + "^" + field.elementText(HL7XMLLiterate.TAG_COMPONENT);
                        pid.put(fieldName,name);
                        continue;
                    }
                    pid.put(fieldName, field.asXML());
                }
                ret.add(pid);
            }
        }
        return ret;
    }
    
    /**
     * Sends a PDQ query with the parameters being contains in the input Map, and the output
     * being a multi-line set of results.  Intended for test purposes, use the above method for
     * parsing the results.
     */
    public String showQBP_Q22(String pdqManager, String query, String domain) 
    throws Exception {
        String timestamp = new SimpleDateFormat(DATETIME_FORMAT).format(new Date());
        StringBuffer sb = makeMSH(timestamp, "QBP^Q22", null, pdqManager, ++msgCtrlid, "2.5");
        String qpd = makeQPD_Q22(query,domain);
        sb.append('\r').append(qpd).append("\rRCP|I||||||\r");
        String s = sb.toString();
        log.info("Query PDQ Manager " + pdqManager + ":\n"
                + s.replace('\r', '\n'));
        final String charsetName = getCharsetName();
        Document msg = invoke(s.getBytes(charsetName), pdqManager);
        log.info("PIX Query returns:");
        logMessage(msg);
        List<Map<String,String>> results = parsePDQ(msg);
        return "PDQ query results:\n"+toString(results);
    }

    /** Sends a PIX query on the given patient ID and issuer, asking for
     * items from domain, or all id's if domains is null.
     * @param pixManager
     * @param pixQueryName
     * @param patientID
     * @param issuer
     * @param domains
     * @return
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public List<String[]> sendQBP_Q23(String pixManager, String pixQueryName,
            String patientID, String issuer, String[] domains)
            throws Exception {
        String timestamp = new SimpleDateFormat(DATETIME_FORMAT)
                .format(new Date());
        long msgCtrlid = ++HL7SendService.msgCtrlid;
        long queryTag = ++HL7SendService.queryTag;
        StringBuffer sb = makeMSH(timestamp, "QBP^Q23^QBP_Q21", null, pixManager, msgCtrlid, "2.5");
        String qpd = makeQPD(pixQueryName, queryTag, patientID, issuer, domains);
        sb.append('\r').append(qpd).append("\rRCP|I||||||\r");
        String s = sb.toString();
        log.info("Query PIX Manager " + pixManager + ":\n"
                + s.replace('\r', '\n'));
        final String charsetName = getCharsetName();
        try {
            Document msg = invoke(s.getBytes(charsetName), pixManager);
            log.info("PIX Query returns:");
            logMessage(msg);
            MSH msh = new MSH(msg);
            if (!"RSP".equals(msh.messageType) || !"K23".equals(msh.triggerEvent)) {
                String prompt = "Unsupport response message type: "
                        + msh.messageType + '^' + msh.triggerEvent;
                log.error(prompt);
                throw new IOException(prompt);
            }
            RSP rsp = new RSP(msg);
            if ("AE".equals(rsp.acknowledgmentCode)) {
                ERR err = new ERR(msg);
                if (err.isUnknownKeyIdentifier()) return null;
            }
            if (!"AA".equals(rsp.acknowledgmentCode)) {
                log.error("PIX Query fails with code " + rsp.acknowledgmentCode
                        + " - " + rsp.textMessage);
                throw new HL7Exception(rsp.acknowledgmentCode, rsp.textMessage);
            }
            if (auditPIXQuery) {
                auditPIXQuery(pixManager, patientID, issuer, msgCtrlid, queryTag, qpd, null);
            }
            return rsp.getPatientIDs();
        } catch (Exception e) {
            if (auditPIXQuery) {
                auditPIXQuery(pixManager, patientID, issuer, msgCtrlid, queryTag, qpd, e);
            }
            throw e;
        }
    }

    private String makeQPD(String pixQueryName, long queryTag, 
            String patientID, String issuer, String[] domains) {
        StringBuffer sb = new StringBuffer("QPD|");
        sb.append(pixQueryName).append('|');
        sb.append(queryTag).append('|');
        sb.append(patientID).append("^^^").append(issuer);
        if (domains != null && domains.length > 0) {
            sb.append("|^^^").append(domains[0]);
            for (int i = 1; i < domains.length; i++) {
                sb.append("~^^^").append(domains[i]);// ~ is repeat delimiter
                // used in makeMSH
            }
        }
        return sb.toString();
    }

    private String makeQPD_Q22(Map<String,String> params,String domain) {
        StringBuffer sb = new StringBuffer("QPD|IHE PDQ Query|");
        sb.append((++queryTag)).append('|');
        Iterator<Entry<String, String>> it = params.entrySet().iterator();
        String sep = null;
        while(it.hasNext()) {
            Map.Entry<String,String> me = it.next();
            if( sep==null ) sep = "~";
            else sb.append(sep);
            sb.append('@').append(me.getKey()).append("^").append(me.getValue());
        }
        sb.append("|||||");
        if( domain!=null ) sb.append("^^^").append(domain);
        return sb.toString();
    }

    /** Make the query assuming the string is already formatted for HL7 PDQ query. */
    private String makeQPD_Q22(String query, String domain) {
        StringBuffer sb = new StringBuffer("QPD|IHE PDQ Query|");
        sb.append((++queryTag)).append('|').append(query);
        if( domain!=null ) sb.append("|||||^^^").append(domain);
        return sb.toString();
    }

    private void auditPIXQuery(String pixManager, String patientID,
            String issuer, long msgCtrlid, long queryTag, String qpd,
            Exception e) {
        try {
            QueryMessage msg = new QueryMessage();
            msg.getAuditEvent().addEventTypeCode(PIX_QUERY_EVENT_TYPE);

            ActiveParticipant source1 = ActiveParticipant.createActivePerson(
                    sendingFacility + '|' + sendingApplication, // userID
                    AuditMessage.getProcessID(),                // altUserID
                    null,                                       // userName
                    AuditMessage.getLocalHostName(),            // napID
                    true);                                      // requester
            source1.addRoleIDCode(ActiveParticipant.RoleIDCode.SOURCE);
            msg.addActiveParticipant(source1);

            HttpServletRequest httprq = (HttpServletRequest)
                    PolicyContext.getContext(WEB_REQUEST_KEY);
            if (httprq != null) {
                String remoteUser = httprq.getRemoteUser();
                String remoteHost = httprq.getRemoteHost();
                if (remoteUser != null) {
                    ActiveParticipant source2 =
                        ActiveParticipant.createActivePerson(
                                remoteUser, // userID
                                null,       // altUserID
                                remoteUser, // userName
                                remoteHost, // napID
                                true);      // requester
                    msg.addActiveParticipant(source2);
                }
            }

            AEDTO pixManagerInfo= aeMgt().findByAET(pixManager);
            ActiveParticipant dest = ActiveParticipant.createActivePerson(
                    pixManager.replace('^','|'),  // userID
                    null,                         // altUserID
                    null,                         // userName
                    pixManagerInfo.getHostName(), // napID
                    false);                       // requester
            dest.addRoleIDCode(ActiveParticipant.RoleIDCode.DESTINATION);
            msg.addActiveParticipant(dest);

            ParticipantObject patObj = new ParticipantObject(
                    patientID + "^^^" + issuer, 
                    ParticipantObject.IDTypeCode.PATIENT_ID);
            patObj.setParticipantObjectTypeCode(
                    ParticipantObject.TypeCode.PERSON);
            patObj.setParticipantObjectTypeCodeRole(
                    ParticipantObject.TypeCodeRole.PATIENT);
            msg.addParticipantObject(patObj);

            ParticipantObject queryObj = new ParticipantObject(
                    String.valueOf(queryTag), PIX_QUERY);
            queryObj.setParticipantObjectTypeCode(
                    ParticipantObject.TypeCode.SYSTEM);
            queryObj.setParticipantObjectTypeCodeRole(
                    ParticipantObject.TypeCodeRole.QUERY);
            queryObj.setParticipantObjectQuery(qpd.getBytes("UTF-8"));
            queryObj.addParticipantObjectDetail("MSH-10", String.valueOf(msgCtrlid));
            msg.addParticipantObject(queryObj);

            Logger auditlog = Logger.getLogger("auditlog");
            if (e == null) {
                auditlog.info(msg);
            } else {
                msg.setOutcomeIndicator(AuditEvent.OutcomeIndicator.MAJOR_FAILURE);
                auditlog.warn(msg);
            }
        } catch (Exception e2) {
            log.warn("Failed to send Audit Log Used message", e2);
        }
    }

    private void logMessage(Document msg) {
        try {
            server.invoke(hl7ServerName, "logMessage",
                    new Object[]{ msg },
                    new String[]{ Document.class.getName() });
        } catch (Exception e) {
            log.warn("Failed to log message", e);
        }        
    }

    private StringBuffer makeMSH(String timestamp, String msgType,
            String sending, String receiving, long msgCtrlid, String version) {
        StringBuffer sb = new StringBuffer();
        sb.append("MSH|^~\\&|");
        int delim;
        if (sending == null || sending.trim().length() == 0) {
            sb.append(getSendingApplication()).append("|");
            sb.append(getSendingFacility()).append("|");
        } else {
            delim = sending.indexOf('^');
            sb.append(sending.substring(0, delim)).append("|");
            sb.append(sending.substring(delim + 1)).append("|");
        }
        delim = receiving.indexOf('^');
        sb.append(receiving.substring(0, delim)).append("|");
        sb.append(receiving.substring(delim + 1)).append("|");
        sb.append(timestamp).append("||");
        sb.append(msgType).append("|");
        sb.append(msgCtrlid).append("|P|");
        sb.append(version).append("||||||||");
        return sb;
    }

    private void addEVN(StringBuffer sb, String timeStamp) {
        sb.append("\rEVN||").append(timeStamp).append("||||").append(timeStamp);
    }

    /**
     * @param sb
     *            PID will be added to this StringBuffer.
     * @param ds
     *            Dataset to get PID informations
     */
    private void addPID(StringBuffer sb, Dataset ds) {
        String s;
        Date d;
        sb.append("\rPID|||");
        appendPatIDwithIssuer(sb, ds);
        sb.append("||");
        addPersonName(sb, ds.getString(Tags.PatientName));
        sb.append("||");
        d = ds.getDateTime(Tags.PatientBirthDate, Tags.PatientBirthTime);
        if (d != null)
            sb.append(new SimpleDateFormat(DATETIME_FORMAT).format(d));
        sb.append("|");
        s = ds.getString(Tags.PatientSex);
        if (s != null)
            sb.append(s);
        sb.append("||||||||||||||||||||||");
        // patient Account number ???(field 18)
    }

    // concerns different order of name suffix, prefix in HL7 XPN compared to
    // DICOM PN
    private void addPersonName(StringBuffer sb, final String patName) {
        StringTokenizer stk = new StringTokenizer(patName, "^", true);
        for (int i = 0; i < 6 && stk.hasMoreTokens(); ++i) {
            sb.append(stk.nextToken());
        }
        if (stk.hasMoreTokens()) {
            String prefix = stk.nextToken();
            if (stk.hasMoreTokens()) {
                stk.nextToken(); // skip delim
                if (stk.hasMoreTokens()) {
                    sb.append(stk.nextToken()); // name suffix
                }
            }
            sb.append('^').append(prefix);
        }
    }

    private void appendPatIDwithIssuer(StringBuffer sb, Dataset ds) {
        sb.append(ds.getString(Tags.PatientID));
        String s = ds.getString(Tags.IssuerOfPatientID);
        if (s != null)
            sb.append("^^^").append(s); // issuer of patient ID
    }

    /**
     * @param sb
     * @param ds
     */
    private void addMRG(StringBuffer sb, Dataset ds) {
        sb.append("\rMRG|");
        appendPatIDwithIssuer(sb, ds);
        sb.append("||||||");
        String name = ds.getString(Tags.PatientName);
        sb.append(name != null ? name : "patName");
    }

    private AEManager aeMgt() throws Exception {
        AEManagerHome home = (AEManagerHome) EJBHomeFactory.getFactory()
                .lookup(AEManagerHome.class, AEManagerHome.JNDI_NAME);
        return home.create();
    }
    
    public void sendHL7File(File file, String receiver) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        byte[] msg = new byte[(int)file.length()];
        fis.read(msg);
        this.sendTo(msg, receiver);
    }
    
    public boolean sendHl7FromDataset( String dsFilename, String xslFilename, String sender, String receiver) throws IOException, TransformerConfigurationException, TransformerFactoryConfigurationError {
        Dataset ds = DcmObjectFactory.getInstance().newDataset(); 
        ds.readFile(new File(dsFilename), null, Tags.PixelData);
        Templates tpl = TransformerFactory.newInstance().newTemplates(new StreamSource(new File(xslFilename)));
        return sendHl7FromDataset( ds, tpl, sender, receiver);
    }
    public boolean sendHl7FromDataset( Dataset ds, Templates tpl, String sender, String receiver) {
        Socket s = null;
        try {
            AEDTO localAE = new AEDTO();
            localAE.setTitle(receiver);
            localAE.setHostName("127.0.0.1");
            localAE.setPort(getLocalHL7Port());
            AEDTO remoteAE = LOCAL_HL7_AET.equals(receiver) 
                    ? localAE : aeMgt().findByAET(receiver);
            s = tlsConfig.createSocket(localAE, remoteAE);
            MLLPDriver mllpDriver = new MLLPDriver(s.getInputStream(), s
                    .getOutputStream(), true);
            OutputStream out = mllpDriver.getOutputStream();
            writeDatasetAsHL7msg(ds, tpl, sender, receiver, out);         
            mllpDriver.turn();
            if (acTimeout > 0) {
                s.setSoTimeout(acTimeout);
            }
            if (!mllpDriver.hasMoreInput()) {
                throw new IOException("Receiver " + receiver
                        + " closed socket " + s
                        + " during waiting on response.");
            }
            Document rsp = readMessage(mllpDriver.getInputStream());
            checkResponse(rsp);
            return true;
        } catch (Exception x) {
            log.warn("Sending HL7 message from Dataset failed! Reason:"+x.getMessage(), x);
            final long delay = retryIntervalls.getIntervall(1);
            if (delay != -1L) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    writeDatasetAsHL7msg(ds, tpl, sender, receiver, baos);
                    byte[] msg = baos.toByteArray();
                    if ( msg.length > 3 && msg[0]=='M' && msg[1]=='S' && msg[2]=='H') {
                        HL7SendOrder order = new HL7SendOrder(msg, receiver);
                        order.setFailureCount(1);
                        jmsDelegate.queue(queueName, order, 0, System.currentTimeMillis() + delay);
                    } else {
                        log.error("Message generated from Dataset is not valid (Does not start with MSH!) and will not be scheduled for retry!");
                        log.debug("Dataset:");log.debug(ds);
                    }
                } catch (Exception e) {
                    log.error("Cannot schedule 'retry' order for failed 'send HL7 message from Dataset'!",e);
                    return false;
                }         
            }
            return false;
        } finally {
            if ( s != null ) {
                if (soCloseDelay > 0)
                    try {
                        Thread.sleep(soCloseDelay);
                    } catch (InterruptedException ignore) {
                    }
                try {
                    s.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    private void writeDatasetAsHL7msg(Dataset ds, Templates tpl, String sender,
            String receiver, OutputStream out)
            throws TransformerConfigurationException, IOException {
        TransformerHandler th = getTransformHandler(tpl, sender, receiver);
        th.setResult(new StreamResult(out));
        ds.writeDataset2(th, null, null, 64, null);
    }

    private TransformerHandler getTransformHandler(Templates tpl, String sender, String receiver)
            throws TransformerConfigurationException {
        TransformerHandler th = XSLTUtils.transformerFactory.newTransformerHandler(tpl);
        XSLTUtils.setDateParameters(th);
        Transformer t = th.getTransformer();
        final SimpleDateFormat tsFormat = new SimpleDateFormat("yyyyMMddHHmmss.SSS");
        t.setParameter("messageControlID", String.valueOf(++msgCtrlid) );
        t.setParameter("messageDateTime", tsFormat.format(new Date()));
        int pos = receiver.indexOf('^');
        t.setParameter("receivingApplication", receiver.substring(0, pos++));
        t.setParameter("receivingFacility", receiver.substring(pos));
        if ( sender != null ) {
            pos = sender.indexOf('^');
            t.setParameter("sendingApplication", sender.substring(0, pos++));
            t.setParameter("sendingFacility", sender.substring(pos));
        } else {
            t.setParameter("sendingApplication", sendingApplication);
            t.setParameter("sendingFacility", sendingFacility);
        }
        return th;
    }

}
