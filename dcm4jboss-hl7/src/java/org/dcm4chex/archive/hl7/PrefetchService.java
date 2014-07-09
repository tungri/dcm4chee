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
 * Agfa-Gevaert Group.
 * Portions created by the Initial Developer are Copyright (C) 2003-2005
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

package org.dcm4chex.archive.hl7;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
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
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.log4j.Logger;
import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.PersonName;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.AssociationFactory;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.ExtNegotiation;
import org.dcm4che.net.FutureRSP;
import org.dcm4che.util.DTFormat;
import org.dcm4che2.audit.message.ActiveParticipant;
import org.dcm4che2.audit.message.AuditEvent;
import org.dcm4che2.audit.message.AuditMessage;
import org.dcm4che2.audit.message.ParticipantObject;
import org.dcm4che2.audit.message.ParticipantObjectDescription;
import org.dcm4chex.archive.config.DicomPriority;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.dcm.AbstractScuService;
import org.dcm4chex.archive.mbean.HttpUserInfo;
import org.dcm4chex.archive.mbean.JMSDelegate;
import org.dcm4chex.archive.mbean.TemplatesDelegate;
import org.dcm4chex.archive.util.ContentHandlerAdapter;
import org.dcm4chex.archive.util.FileUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.DocumentSource;
import org.dom4j.io.SAXContentHandler;
import org.regenstrief.xhl7.HL7XMLReader;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision: 15750 $ $Date: 2011-08-03 08:14:20 +0000 (Wed, 03 Aug 2011) $
 * @since Nov 30, 2006
 */
public class PrefetchService extends AbstractScuService implements
        NotificationListener, MessageListener {

    private static final String ONLINE = "ONLINE";
    private static final String NONE = "NONE";
    
    private MessageTypeMatcher[] prefetchMessageTypes;
    private String prefetchSourceAET;
    private String destinationQueryAET;
    private String destinationStorageAET;
    private String xslPath;
    private String postSelectXslPath;
    private ObjectName hl7ServerName;
    private ObjectName moveScuServiceName;
    private String queueName;
    private RetryIntervalls retryIntervalls = new RetryIntervalls();
    private int sourceQueryPriority = 0;
    private int destinationQueryPriority = 0;
    private int retrievePriority = 0;
    private boolean onlyKnownSeries;
    private boolean logPostSelectXML;
    private AuditEvent.ID prefetchAuditEventID;
    
    private int concurrency = 1;

    private JMSDelegate jmsDelegate = new JMSDelegate(this);
    private TemplatesDelegate templates = new TemplatesDelegate(this);
    public static final SAXTransformerFactory tf = (SAXTransformerFactory) TransformerFactory.newInstance();
    private static final byte[] RELATIONAL_QUERY = { 1 };

    public String getPrefetchMessageTypes() {
        if (prefetchMessageTypes == null || prefetchMessageTypes.length == 0) {
            return NONE;
        }
        StringBuffer sb = new StringBuffer();
        for (MessageTypeMatcher prefetchMessageType : prefetchMessageTypes) {
            prefetchMessageType.toString(sb).append(',');
        }
        sb.setLength(sb.length()-1);
        return sb.toString();
    }

    public void setPrefetchMessageTypes(String messageTypes) {
        String trim = messageTypes.trim();
        if (NONE.equalsIgnoreCase(trim)) {
            prefetchMessageTypes = null;
        } else {
            StringTokenizer stk = new StringTokenizer(messageTypes, ", ");
            MessageTypeMatcher[] tmp =
                    new MessageTypeMatcher[stk.countTokens()];
            for (int i = 0; i < tmp.length; i++) {
                tmp[i] = new MessageTypeMatcher(stk.nextToken());
            }
            prefetchMessageTypes = tmp;
        }
    }

    public final String getPrefetchSourceAET() {
        return prefetchSourceAET;
    }

    public final void setPrefetchSourceAET(String aet) {
        this.prefetchSourceAET = aet;
    }
    
    public final String getDestinationQueryAET() {
        return destinationQueryAET;
    }

    public final void setDestinationQueryAET(String aet) {
        this.destinationQueryAET = aet;
    }

    public final String getDestinationStorageAET() {
        return destinationStorageAET;
    }

    public final void setDestinationStorageAET(String aet) {
        this.destinationStorageAET = aet;
    }

    public final String getSourceQueryPriority() {
        return DicomPriority.toString(sourceQueryPriority);
    }

    public final void setSourceQueryPriority(String cs) {
        this.sourceQueryPriority = DicomPriority.toCode(cs);
    }
    
    public final String getDestinationQueryPriority() {
        return DicomPriority.toString(destinationQueryPriority);
    }

    public final void setDestinationQueryPriority(String cs) {
        this.destinationQueryPriority = DicomPriority.toCode(cs);
    }

    public final String getRetrievePriority() {
        return DicomPriority.toString(retrievePriority);
    }

    public final void setRetrievePriority(String retrievePriority) {
        this.retrievePriority = DicomPriority.toCode(retrievePriority);
    }
    
    public final String getStylesheet() {
        return xslPath;
    }

    public void setStylesheet(String path) {
        this.xslPath = path;
    }
    
    public boolean isOnlyKnownSeries() {
        return onlyKnownSeries;
    }
    public void setOnlyKnownSeries(boolean b) {
        onlyKnownSeries = b;
    }

    public final String getPostSelectStylesheet() {
        return postSelectXslPath == null ? NONE : postSelectXslPath;
    }

    public void setPostSelectStylesheet(String path) {
        postSelectXslPath = NONE.equals(path) ? null : path;
    }

    public boolean isLogPostSelectXML() {
        return logPostSelectXML;
    }
    public void setLogPostSelectXML(boolean b) {
        logPostSelectXML = b;
    }
    
    public String getPrefetchAuditEventID() {
        return prefetchAuditEventID == null ? NONE : 
            prefetchAuditEventID.getCode()+"^"+prefetchAuditEventID.getCodeSystemName()+
            "^"+prefetchAuditEventID.getDisplayName();
    }

    public void setPrefetchAuditEventID(String code) {
        if (NONE.equals(code)) {
            prefetchAuditEventID = null;
        } else {
            StringTokenizer st = new StringTokenizer(code, "^");
            if (st.countTokens() != 3)
                throw new IllegalArgumentException("EventID must be <code>^<code system>^<display name>! " + code);
            prefetchAuditEventID = new AuditEvent.ID(st.nextToken(), st.nextToken(), st.nextToken());
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

    public String getRetryIntervalls() {
        return retryIntervalls.toString();
    }

    public void setRetryIntervalls(String text) {
        retryIntervalls = new RetryIntervalls(text);
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

    public final ObjectName getMoveScuServiceName() {
            return moveScuServiceName;
    }

    public final void setMoveScuServiceName(ObjectName moveScuServiceName) {
            this.moveScuServiceName = moveScuServiceName;
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
        Object[] data = (Object[]) notif.getUserData();
        Document hl7doc = (Document) data[1];
        if (matchPrefetchMessageTypes(hl7doc)) {
            Dataset findRQ = DcmObjectFactory.getInstance().newDataset();
            try {
                File xslFile = FileUtils.toExistingFile(xslPath);
                Transformer t = templates.getTemplates(xslFile).newTransformer();
                t.transform(new DocumentSource(hl7doc), new SAXResult(findRQ
                        .getSAXHandler2(null)));
            } catch (TransformerException e) {
                log.error("Failed to transform ORM into prefetch request", e);
                return;
            } catch (FileNotFoundException e) {
                log.error("No such stylesheet: " + xslPath);
                return;
            }
            prepareFindReqDS(findRQ);
            PrefetchOrder order = new PrefetchOrder(findRQ, hl7doc);
            try {
                log.info("Scheduling " + order);
                jmsDelegate.queue(queueName, order, Message.DEFAULT_PRIORITY,
                        0L);
            } catch (Exception e) {
                log.error("Failed to schedule " + order, e);
            }            
        }
    }

    private boolean matchPrefetchMessageTypes(Document hl7doc) {
        if (prefetchMessageTypes == null || prefetchMessageTypes.length == 0) {
            return false;
        }
        MSH msh = new MSH(hl7doc);
        for (MessageTypeMatcher prefetchMessageType : prefetchMessageTypes) {
            if (prefetchMessageType.match(msh, hl7doc)) {
                return true;
            }
        }
        return false;
    }

    public void onMessage(Message message) {
        ObjectMessage om = (ObjectMessage) message;
        try {
            PrefetchOrder order = (PrefetchOrder) om.getObject();
            log.info("Start processing " + order);
            try {
                process(order);
                log.info("Finished processing " + order);
            } catch (Exception e) {
            	order.setThrowable(e);
                final int failureCount = order.getFailureCount() + 1;
                order.setFailureCount(failureCount);
                final long delay = retryIntervalls.getIntervall(failureCount);
                if (delay == -1L) {
                    log.error("Give up to process " + order, e);
                    jmsDelegate.fail(queueName, order);
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

    private void process(PrefetchOrder order) throws Exception {
        Dataset keys = order.getDataset();
        log.debug("SearchDS from order:");log.debug(keys);
        Map<String, Dataset> srcList = doCFIND(prefetchSourceAET, keys, sourceQueryPriority);
        Map<String, Dataset> destList = doCFIND(destinationQueryAET, keys, destinationQueryPriority);
        List<Dataset> notAvail = this.getListOfNotAvail(srcList, destList);
        if (notAvail.size() > 0 ) {
            log.debug("notAvail:"+notAvail);
            if ( postSelectXslPath == null || !postSelect(notAvail, order, srcList) ) {
                log.info(notAvail.size()+" Series are not available on destination AE! Schedule for Pre-Fetch");
                for ( Iterator<Dataset> iter = notAvail.iterator() ; iter.hasNext() ; ) {
                    scheduleMove( prefetchSourceAET, destinationStorageAET, retrievePriority, iter.next(), 0l);
                }
            }
        }
    }

    private boolean postSelect(final List<Dataset> notAvail, final PrefetchOrder order, final Map<String, Dataset> srcList) {
        try {
            Document doc = order.getHL7Document();
            if (logPostSelectXML) {
                File logFile = new File(System.getProperty("jboss.server.log.dir"), 
                        "postselect/"+new DTFormat().format(new Date())+".xml");
                logFile.getParentFile().mkdirs();
                TransformerHandler thLog = tf.newTransformerHandler();
                thLog.setResult(new StreamResult(new FileOutputStream(logFile)));
                transformPostSelect(notAvail, thLog, doc);
                log.info("postselect XML logged in "+logFile);
            }            
            File xslFile = FileUtils.toExistingFile(postSelectXslPath);
            ContentHandler ch = new DefaultHandler() {
                public void startElement (String uri, String localName,
                                          String qName, Attributes attr) {
                    if ("schedule".equals(qName)) {
                        String seriesIUID = attr.getValue("seriesIUID");
                        if (seriesIUID == null)
                            throw new IllegalArgumentException("Missing seriesIUID attribute in schedule tag!");
                        Dataset ds = srcList.get(seriesIUID);
                        if (ds == null) {
                            log.warn("Series IUID of schedule tag is not known on source! Ignored.");
                        } else {
                            String dt = attr.getValue("scheduleAt");
                            long l;
                            try {
                                l = dt == null ? 0l : new DTFormat().parse(dt).getTime();
                            } catch (ParseException x) {
                                log.error("Attribute 'scheduleAt' is not in DateTime format (yyyyMMddHHmmss.SSS )!", x);
                                l = 0l;
                            }
                            log.info("Schedule post selected series:"+seriesIUID+" at "+dt+" reason:"+attr.getValue("reason"));
                            scheduleMove( prefetchSourceAET, destinationStorageAET, retrievePriority, ds, l);
                        }                        
                    }
                }
            };
            TransformerHandler th = tf.newTransformerHandler(templates.getTemplates(xslFile));
            th.setResult(new SAXResult(ch));
            transformPostSelect(notAvail, th, doc);
            return true;
        } catch (Exception x) {
            log.error("PostSelect of C-FIND results failed! Schedule prefetch C-MOVE requests without post selection!", x);
            return false;
        }
    }

    private void transformPostSelect(final List<Dataset> notAvail,
            ContentHandler handler, Document hl7Doc) throws SAXException,
            IOException, TransformerFactoryConfigurationError,
            TransformerException {
        ContentHandlerAdapter cha = new ContentHandlerAdapter(handler);
        cha.forcedStartDocument();
        cha.startElement("prefetch");
        if (hl7Doc != null) {
            DocumentSource src = new DocumentSource(hl7Doc);
            Transformer trans = TransformerFactory.newInstance().newTransformer();
            trans.transform(src, new SAXResult(cha));
        } 
        for ( Iterator<Dataset> iter = notAvail.iterator() ; iter.hasNext() ; ) {
            iter.next().writeDataset2(cha, null, null, 64, null);
        }
        cha.endElement("prefetch");
        cha.forcedEndDocument();
    }

    /**
     * @param keys
     */
    private void prepareFindReqDS(Dataset keys) {
        String qrLevel = keys.getString(Tags.QueryRetrieveLevel);
        if ( qrLevel != null && !qrLevel.equals("SERIES") ) {
            log.warn("QueryRetrieveLevel of PrefetchOrder is "+qrLevel+"! Set to SERIES!");
        }
        keys.putCS(Tags.QueryRetrieveLevel,"SERIES");
        if ( !keys.contains(Tags.PatientID) ) keys.putUI(Tags.PatientID);
        if ( !keys.contains(Tags.StudyInstanceUID) ) keys.putUI(Tags.StudyInstanceUID);
        if ( !keys.contains(Tags.SeriesInstanceUID) ) keys.putUI(Tags.SeriesInstanceUID);
        keys.putIS(Tags.NumberOfSeriesRelatedInstances);
        keys.putCS(Tags.InstanceAvailability);
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Dataset> doCFIND(String calledAET, Dataset keys, int priority )
            throws Exception {
        ExtNegotiation extNeg = AssociationFactory.getInstance().newExtNegotiation(
                UIDs.StudyRootQueryRetrieveInformationModelFIND, RELATIONAL_QUERY);
        ActiveAssociation assoc = openAssociation(calledAET,
                UIDs.StudyRootQueryRetrieveInformationModelFIND, extNeg);
        try {
            Map<String, Dataset> result = new HashMap<String, Dataset>();
            // send cfind request.
            Command cmd = DcmObjectFactory.getInstance().newCommand();
            cmd.initCFindRQ(1, UIDs.StudyRootQueryRetrieveInformationModelFIND,
                    priority);
            Dimse mcRQ = AssociationFactory.getInstance().newDimse(1, cmd,
                    keys);
            FutureRSP findRsp = assoc.invoke(mcRQ);
            Dimse dimse = findRsp.get();
            List<Dimse> pending = (List<Dimse>) findRsp.listPending();
            Iterator<Dimse> iter = pending.iterator();
            Dataset ds;
            while (iter.hasNext()) {
                ds = iter.next().getDataset();
                result.put(ds.getString(Tags.SeriesInstanceUID), ds);
                log.debug(calledAET+": received Dataset:");log.debug(ds);
            }
            if (log.isDebugEnabled()) {
                log.debug(calledAET+" : received final C-FIND RSP :"
                        + dimse);
            }
            return result;
        } finally {
            if (assoc != null)
                try {
                    assoc.release(true);
                } catch (Exception e1) {
                    log.error(
                            "Cant release association for CFIND"
                                    + assoc.getAssociation(), e1);
                }
        }
    }
        
    private List<Dataset> getListOfNotAvail(Map<String, Dataset> srcList, Map<String, Dataset> destList ) {
        ArrayList<Dataset> l = new ArrayList<Dataset>();
        Dataset ds, dsAll;
        Entry<String, Dataset> entry;
        String seriesIUID;
        StringBuffer sb = new StringBuffer();
        for ( Iterator<Entry<String, Dataset>> iter = srcList.entrySet().iterator() ; iter.hasNext() ; ) {
            entry = iter.next();
            seriesIUID = entry.getKey();
            dsAll = entry.getValue();
            ds = destList.get(seriesIUID);
            sb.setLength(0);
            sb.append("Series ").append(seriesIUID).append(": ");
            if ( ds == null ) {
                if (onlyKnownSeries) {
                    sb.append(" - Ignored! Must be known on destination!");
                } else {
                    sb.append("Only known on source AE");
                    l.add( dsAll );
                }
                log.debug(sb);
            } else if ( ! ONLINE.equals( ds.getString(Tags.InstanceAvailability)) ) {
                log.debug(sb.append("Instances are not available (ONLINE) on destination AE!"));
                l.add( dsAll ); 
            } else {
                int noi = ds.getInt(Tags.NumberOfSeriesRelatedInstances, -1);
                int noi1 = dsAll.getInt(Tags.NumberOfSeriesRelatedInstances, -1);
                sb.append("NumberOfSeriesRelatedInstances ");
                if ( noi == -1 || noi1 == -1 ) {
                    sb.append("is not available to check count of instances! dest:");
                    log.warn(sb.append(noi).append(" src:").append(noi1));
                } else if ( noi < noi1 ) {
                    sb.append("on destination AE is less than on source AE! dest:");
                    log.debug(sb.append(noi).append(" src:").append(noi1));
                    l.add(dsAll);
                }
            }
        }
        return l;
    }
 
    private void scheduleMove(String retrieveAET, String destAET, int priority,
            Dataset ds, long scheduledTime) {
        boolean success = false;
        try {
            server.invoke(moveScuServiceName, "scheduleMove", new Object[] {
                    retrieveAET, destAET, new Integer(priority), ds.getString(Tags.PatientID),
                    ds.getString(Tags.StudyInstanceUID), 
                    ds.getString(Tags.SeriesInstanceUID), null, new Long(scheduledTime) },
                    new String[] { String.class.getName(),
                            String.class.getName(), int.class.getName(),
                            String.class.getName(), String.class.getName(),
                            String.class.getName(), String[].class.getName(),
                            long.class.getName() });
            success = true;
        } catch (Exception e) {
            log.error("Schedule Move failed:", e);
        }
        if (prefetchAuditEventID != null) {
            logPrefetchSchedule(retrieveAET, destAET, ds, scheduledTime, success);
        }
    }
    
    
    private void logPrefetchSchedule(String retrieveAET, String destAET,
            Dataset ds, long scheduledTime, boolean success) {
        HttpUserInfo userInfo = new HttpUserInfo(AuditMessage
                .isEnableDNSLookups());
        try {
            SchedulePrefetchMessage msg = new SchedulePrefetchMessage(prefetchAuditEventID);
            msg.setOutcomeIndicator(success ? AuditEvent.OutcomeIndicator.SUCCESS:
                AuditEvent.OutcomeIndicator.MINOR_FAILURE);
            msg.addActiveParticipant(ActiveParticipant.createActivePerson(userInfo.getUserId(), null, null, userInfo
                    .getHostName(), true));
            PersonName pn = ds.getPersonName(Tags.PatientName);
            String pname = pn != null ? pn.format() : null;
            msg.addParticipantObject(ParticipantObject.createPatient(ds.getString(Tags.PatientID), pname));
            ParticipantObjectDescription descr = new ParticipantObjectDescription();
            ParticipantObject study = msg.addParticipantObject(
                    ParticipantObject.createStudy(ds.getString(Tags.StudyInstanceUID), descr));
            String scheduledAt = scheduledTime == 0 ? "NOW" : new Date(scheduledTime).toString();
            study.addParticipantObjectDetail("Description", "Prefetching series "+
                    ds.getString(Tags.SeriesInstanceUID)+" from "+retrieveAET+" to "+destAET+
                    " scheduled at "+scheduledAt);
            msg.validate();
            Logger.getLogger("auditlog").info(msg);
        } catch (Exception x) {
            log.warn("Audit Log 'Prefetch Schedule' failed:", x);
        }
    }

    public void processFile(String filename) throws DocumentException, IOException, SAXException {
        Dataset findRQ = DcmObjectFactory.getInstance().newDataset();
        HL7XMLReader reader = new HL7XMLReader();
        File file = new File(filename);
        SAXContentHandler hl7in = new SAXContentHandler();
        reader.setContentHandler(hl7in);
        reader.parse(new InputSource( new FileInputStream(file)));
        Document doc = hl7in.getDocument();
        try {
            File xslFile = FileUtils.toExistingFile(xslPath);
            Transformer t = templates.getTemplates(xslFile).newTransformer();
            t.transform(new DocumentSource(doc),
                    new SAXResult(findRQ.getSAXHandler2(null)));
        } catch (TransformerException e) {
            log.error("Failed to transform into prefetch request", e);
            return;
        }
        this.prepareFindReqDS(findRQ);
        PrefetchOrder order = new PrefetchOrder(findRQ, doc);
        try {
            log.info("Scheduling Test PrefetchOrder:" + order);
            jmsDelegate.queue(queueName, order, Message.DEFAULT_PRIORITY, 0L);
        } catch (Exception e) {
            log.error("Failed to schedule Test Order" + order, e);
        }            
     }
    
    
    class SchedulePrefetchMessage extends AuditMessage {
        public SchedulePrefetchMessage(AuditEvent.ID eventID) {
            super(new AuditEvent(eventID, AuditEvent.ActionCode.EXECUTE));
        }
    }
}
