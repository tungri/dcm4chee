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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import javax.management.Notification;
import javax.management.NotificationFilterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.dict.DictionaryFactory;
import org.dcm4che.dict.TagDictionary;
import org.dcm4che.dict.Tags;
import org.dcm4chex.archive.dcm.mppsscp.MPPSScpService;
import org.dcm4chex.archive.ejb.interfaces.MPPSManager;
import org.dcm4chex.archive.ejb.interfaces.MPPSManagerHome;
import org.dcm4chex.archive.mbean.TemplatesDelegate;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.dcm4chex.archive.util.FileUtils;
import org.dcm4chex.archive.util.HomeFactoryException;
import org.jboss.system.ServiceMBeanSupport;
import org.regenstrief.xhl7.HL7XMLWriter;
import org.regenstrief.xhl7.XMLWriter;

/**
 * @author gunter.zeilinger@tiani.com
 * @version $Revision: 17586 $ $Date: 2013-01-10 11:02:36 +0000 (Thu, 10 Jan 2013) $
 * @since Oct 3, 2005
 */
public class MPPS2ORMService extends ServiceMBeanSupport implements
        NotificationListener {

    private static final String ISO_8859_1 = "ISO-8859-1";

    private static final int INIT_BUFFER_SIZE = 512;

    private static final NotificationFilterSupport mppsFilter = 
        new NotificationFilterSupport();
    
    private ObjectName mppsScpServiceName;

    private ObjectName hl7SendServiceName;
    
    private TemplatesDelegate templates = new TemplatesDelegate(this);

    private String xslPath;

    private String receivedSendingApplication;
    private String receivedSendingFacility;
    private String receivedReceivingApplication;
    private String receivedReceivingFacility;

    private String linkedSendingApplication;
    private String linkedSendingFacility;
    private String linkedReceivingApplication;
    private String linkedReceivingFacility;
    
    private boolean mppsReceivedEnabled;
    private boolean mppsLinkedEnabled;

    private boolean ignoreUnscheduled;

    private boolean ignoreInProgress;

    private boolean oneORMperSPS;

    private File logDir;

    private boolean logXSLT;

    public final ObjectName getTemplatesServiceName() {
        return templates.getTemplatesServiceName();
    }

    public final void setTemplatesServiceName(ObjectName serviceName) {
        templates.setTemplatesServiceName(serviceName);
    }
    
    public final String getStylesheet() {
        return xslPath;
    }

    public void setStylesheet(String path) {
        this.xslPath = path;
    }
    
    public final String getSendingApplication() {
        return receivedSendingApplication;
    }

    public final void setSendingApplication(String sendingApplication) {
        this.receivedSendingApplication = sendingApplication;
    }

    public final String getSendingFacility() {
        return receivedSendingFacility;
    }

    public final void setSendingFacility(String sendingFacility) {
        this.receivedSendingFacility = sendingFacility;
    }

    public final String getReceivingApplication() {
        return receivedReceivingApplication;
    }

    public final void setReceivingApplication(String receivingApplication) {
        this.receivedReceivingApplication = receivingApplication;
    }

    public final String getReceivingFacility() {
        return receivedReceivingFacility;
    }

    public final void setReceivingFacility(String receivingFacility) {
        this.receivedReceivingFacility = receivingFacility;
    }

    public final boolean isMPPSReceivedEnabled() {
        return mppsReceivedEnabled;
    }

    public final void setMPPSReceivedEnabled(boolean enabled) {
        if (enabled)
            mppsFilter.enableType(MPPSScpService.EVENT_TYPE_MPPS_RECEIVED);
        else
            mppsFilter.disableType(MPPSScpService.EVENT_TYPE_MPPS_RECEIVED);
        this.mppsReceivedEnabled = enabled;
    }

    public final boolean isMPPSLinkedEnabled() {
        return mppsLinkedEnabled;
    }
    
    public final void setMPPSLinkedEnabled(boolean enabled) {
        if (enabled)
            mppsFilter.enableType(MPPSScpService.EVENT_TYPE_MPPS_LINKED);
        else
            mppsFilter.disableType(MPPSScpService.EVENT_TYPE_MPPS_LINKED);
        mppsLinkedEnabled = enabled;
    }

    
    public String getLinkedSendingApplication() {
        return linkedSendingApplication;
    }

    public void setLinkedSendingApplication(String linkedSendingApplication) {
        this.linkedSendingApplication = linkedSendingApplication;
    }

    public String getLinkedSendingFacility() {
        return linkedSendingFacility;
    }

    public void setLinkedSendingFacility(String linkedSendingFacility) {
        this.linkedSendingFacility = linkedSendingFacility;
    }

    public String getLinkedReceivingApplication() {
        return linkedReceivingApplication;
    }

    public void setLinkedReceivingApplication(String linkedReceivingApplication) {
        this.linkedReceivingApplication = linkedReceivingApplication;
    }

    public String getLinkedReceivingFacility() {
        return linkedReceivingFacility;
    }

    public void setLinkedReceivingFacility(String linkedReceivingFacility) {
        this.linkedReceivingFacility = linkedReceivingFacility;
    }

    public final boolean isIgnoreUnscheduled() {
        return ignoreUnscheduled;
    }

    public final void setIgnoreUnscheduled(boolean ignoreUnscheduled) {
        this.ignoreUnscheduled = ignoreUnscheduled;
    }

    public final boolean isIgnoreInProgress() {
        return ignoreInProgress;
    }

    public final void setIgnoreInProgress(boolean ignoreInProgress) {
        this.ignoreInProgress = ignoreInProgress;
    }

    public final boolean isOneORMperSPS() {
        return oneORMperSPS;
    }

    public final void setOneORMperSPS(boolean splitMPPS) {
        this.oneORMperSPS = splitMPPS;
    }

    public final boolean isLogXSLT() {
        return logXSLT;
    }

    public final void setLogXSLT(boolean logXSLT) {
        this.logXSLT = logXSLT;
    }

    private MPPSManagerHome getMPPSManagerHome() throws HomeFactoryException {
        return (MPPSManagerHome) EJBHomeFactory.getFactory().lookup(
                MPPSManagerHome.class, MPPSManagerHome.JNDI_NAME);
    }

    public final ObjectName getMppsScpServiceName() {
        return mppsScpServiceName;
    }

    public final void setMppsScpServiceName(ObjectName mppsScpServiceName) {
        this.mppsScpServiceName = mppsScpServiceName;
    }

    public final ObjectName getHl7SendServiceName() {
        return hl7SendServiceName;
    }

    public final void setHl7SendServiceName(ObjectName hl7SendServiceName) {
        this.hl7SendServiceName = hl7SendServiceName;
    }
    
    protected void startService() throws Exception {
        server.addNotificationListener(mppsScpServiceName, this,
                mppsFilter, null);
        logDir = new File(System.getProperty("jboss.server.log.dir")); 
    }

    protected void stopService() throws Exception {
        server.removeNotificationListener(mppsScpServiceName, this,
                mppsFilter, null);
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.management.NotificationListener#handleNotification(javax.management.Notification,
     *      java.lang.Object)
     */
    public void handleNotification(Notification notif, Object handback) {
        log.debug("handleNotification called! type:"+notif.getType());
        Dataset mpps = (Dataset) notif.getUserData();
        boolean linked = MPPSScpService.EVENT_TYPE_MPPS_LINKED.equals(notif.getType());
        if (!linked) {
            if (ignoreInProgress
                    && "IN PROGRESS".equals(mpps.getString(Tags.PPSStatus)))
                return;
    
            final String iuid = mpps.getString(Tags.SOPInstanceUID);
            mpps = getMPPS(iuid);
        }
        handle(mpps, linked);
    }

    private void handle(Dataset mpps, boolean linked) {
        DcmElement sq = mpps.get(Tags.ScheduledStepAttributesSeq);
        if (sq == null || sq.isEmpty()) {
            log
                    .error("Missing Scheduled Step Attributes Seq in MPPS - "
                            + mpps.getString(Tags.SOPInstanceUID));
            return;
        }
        if (ignoreUnscheduled
                && sq.getItem().getString(Tags.AccessionNumber) == null) {
            return;
        }
        if (oneORMperSPS) {
            for (int i = 0, n = sq.countItems(); i < n; i++) {
                mpps.putSQ(Tags.ScheduledStepAttributesSeq).addItem(
                        sq.getItem(i));
                scheduleORM(makeORM(mpps, linked));
            }
        } else {
            scheduleORM(makeORM(mpps, linked));
        }
    }

    private void scheduleORM(byte[] bs) {
        if (bs == null)
            return;
        try {
            server.invoke(hl7SendServiceName, "forward", new Object[] { bs },
                    new String[] { byte[].class.getName() });
        } catch (Exception e) {
            log.error("Failed to schedule ORM", e);
        }
    }

    private byte[] makeORM(Dataset mpps, boolean linked) {
        if (mpps == null)
            return null;
        try {
            if (logXSLT)
                try {
                    logXSLT(mpps, linked);
                } catch (Exception e) {
                    log.warn("Failed to log XSLT:", e);
                }
            ByteArrayOutputStream out = new ByteArrayOutputStream(
                    INIT_BUFFER_SIZE);
            TransformerHandler th = getTransformerHandler(linked);
            XMLWriter xmlWriter = new HL7XMLWriter(
            		new OutputStreamWriter(out, ISO_8859_1));
            th.setResult(new SAXResult(xmlWriter.getContentHandler()));
            mpps.writeDataset2(th, null, null, 10240, null);
            log.info(new String(out.toByteArray()));
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to convert MPPS to ORM", e);
            log.error(mpps);
            return null;
        }
    }

    private void logXSLT(Dataset mpps, boolean linked) throws Exception {
        SAXTransformerFactory tf = (SAXTransformerFactory) TransformerFactory
                .newInstance();
        String uid = mpps.getString(Tags.SOPInstanceUID);
        String fn = (linked ? "mpps-linked-" : "mpps-received-")+uid;
        logXSLT(mpps, tf.newTransformerHandler(), new File(logDir, fn + ".xml"));
        logXSLT(mpps, getTransformerHandler(linked), new File(logDir, fn +".orm.xml"));
    }

    private void logXSLT(Dataset mpps, TransformerHandler th, File logFile)
            throws Exception {
        TagDictionary dict = DictionaryFactory.getInstance()
                .getDefaultTagDictionary();
        FileOutputStream out = new FileOutputStream(logFile);
        try {
            th.setResult(new StreamResult(out));
            mpps.writeDataset2(th, dict, null, 10240, null);
        } finally {
            out.close();
        }
    }

    private TransformerHandler getTransformerHandler( boolean linked ) throws Exception {
        SAXTransformerFactory tf = (SAXTransformerFactory) TransformerFactory
                .newInstance();
        File xslFile = FileUtils.toExistingFile(xslPath);
        TransformerHandler th = tf.newTransformerHandler(
                templates.getTemplates(xslFile));
        Transformer t = th.getTransformer();
        t.setParameter("SendingApplication", linked ? linkedSendingApplication : receivedSendingApplication);
        t.setParameter("SendingFacility", linked ? linkedSendingFacility : receivedSendingFacility);
        t.setParameter("ReceivingApplication", linked ? linkedReceivingApplication : receivedReceivingApplication);
        t.setParameter("ReceivingFacility", linked ? linkedReceivingFacility : receivedReceivingFacility);
        return th;
    }

    private Dataset getMPPS(String iuid) {
        try {
            MPPSManager mgr = getMPPSManagerHome().create();
            try {
                return mgr.getMPPS(iuid);
            } finally {
                try {
                    mgr.remove();
                } catch (Exception ignore) {
                }
            }
        } catch (Exception e) {
            log.error("Failed to load MPPS - " + iuid, e);
            return null;
        }
    }
}
