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

package org.dcm4chex.archive.mbean;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Tags;
import org.dcm4chex.archive.common.SeriesStored;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.util.FileUtils;
import org.jboss.system.ServiceMBeanSupport;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Franz Willer <franz.willer@gmail.com>
 * @version $Revision: $ $Date: $
 * @since Oct 14, 2009
 */
public class WadoPrefetchService extends ServiceMBeanSupport implements MessageListener {

    private static final String WADO_PREFETCH_XSL = "wado-prefetch.xsl";
    private static final int BUF_LEN = 65536;

    private final NotificationListener seriesStoredListener = new NotificationListener() {
        public void handleNotification(Notification notif, Object handback) {
            WadoPrefetchService.this.onSeriesStored((SeriesStored) notif.getUserData());
        }
    };

    private String wadoBaseUrl;
    private String exportBasePath;
    
    private ObjectName storeScpServiceName;
    private String queueName;
    private JMSDelegate jmsDelegate = new JMSDelegate(this);
    private RetryIntervalls retryIntervalls = new RetryIntervalls();

    private TemplatesDelegate templates = new TemplatesDelegate(this);

    public String getWadoBaseUrl() {
        return wadoBaseUrl;
    }

    public void setWadoBaseUrl(String wadoBaseUrl) {
        this.wadoBaseUrl = wadoBaseUrl;
    }

    public String getExportBasePath() {
        return exportBasePath;
    }

    public void setExportBasePath(String path) {
        this.exportBasePath = path;
    }

    public final String getConfigDir() {
        return templates.getConfigDir();
    }

    public final void setConfigDir(String path) {
        templates.setConfigDir(path);
    }

    public final ObjectName getTemplatesServiceName() {
        return templates.getTemplatesServiceName();
    }

    public final void setTemplatesServiceName(ObjectName serviceName) {
        templates.setTemplatesServiceName(serviceName);
    }
    
    public final ObjectName getStoreScpServiceName() {
        return storeScpServiceName;
    }

    public final void setStoreScpServiceName(ObjectName storeScpServiceName) {
        this.storeScpServiceName = storeScpServiceName;
    }

    public final ObjectName getJmsServiceName() {
        return jmsDelegate.getJmsServiceName();
    }

    public final void setJmsServiceName(ObjectName jmsServiceName) {
        jmsDelegate.setJmsServiceName(jmsServiceName);
    }

    public final String getQueueName() {
        return queueName;
    }

    public final void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public final String getRetryIntervalls() {
        return retryIntervalls.toString();
    }

    public final void setRetryIntervalls(String text) {
        retryIntervalls = new RetryIntervalls(text);
    }
    
    protected void startService() throws Exception {
        jmsDelegate.startListening(queueName, this, 1);
        server.addNotificationListener(storeScpServiceName,
                seriesStoredListener, SeriesStored.NOTIF_FILTER, null);
    }

    protected void stopService() throws Exception {
        server.removeNotificationListener(storeScpServiceName,
                seriesStoredListener, SeriesStored.NOTIF_FILTER, null);
        jmsDelegate.stopListening(queueName);
    }
    
    private void onSeriesStored(final SeriesStored stored) {
        if (stored.getRetrieveAET() == null) {
            log.warn("Ignore SeriesStored notification! Reason: Series is not locally retrievable.");
            return;
        }
        log.info("Handle SeriesStored Notification! stored:"+stored);
        Templates tpl = null;
        try {
            tpl = templates.getTemplatesForAET(
                stored.getSourceAET(), WADO_PREFETCH_XSL);
        } catch (Throwable t) {
            log.error("Failed to get Template for wado-prefetch.xsl!", t );
        }
        if (tpl != null) {
            Dataset ds = DcmObjectFactory.getInstance().newDataset();
            ds.putAll(stored.getPatientAttrs());
            ds.putAll(stored.getStudyAttrs());
            ds.putAll(stored.getSeriesAttrs());
            log.debug("WADOPrefetch schedule transform input:");log.debug(ds);
            try {
                xslt(stored.getSourceAET(), stored.getRetrieveAET(),
                        ds, tpl, new DefaultHandler(){

                    public void startElement(String uri, String localName,
                            String qName, Attributes attrs) {
                        if (qName.equals("prefetch")) {
                            WadoPrefetchOrder order = new WadoPrefetchOrder(attrs.getValue("wadourl"),
                                    attrs.getValue("exportPath"), stored.getIAN());
                            try {
                                jmsDelegate.queue(queueName, order, 
                                        Message.DEFAULT_PRIORITY, 0L);
                            } catch (Exception e) {
                                log.error("Failed to schedule " + order, e);
                            }
                        }
                    }});
            } catch (Exception e) {
                log.error("Applying WADO prefetch rules to " + stored + " fails:", e);
            }
        }
    }

    public void onMessage(Message message) {
        ObjectMessage om = (ObjectMessage) message;
        try {
            WadoPrefetchOrder order = (WadoPrefetchOrder) om.getObject();
            log.info("Start processing order" + order);
            try {
                process(order);
                log.info("Processing finished! order:"+order);
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
                    jmsDelegate.queue(queueName, order, 0, System.currentTimeMillis()
                            + delay);
                }
            }
        } catch (Throwable e) {
            log.error("unexpected error during processing message: " + message,
                    e);
        }
    }
    
    private void process(WadoPrefetchOrder order) throws IOException {
        String wadourl = order.getWadoUrl();
        String dest = order.getExportPath();
        Dataset ian = order.getIAN();
        DcmElement refSopSq = ian.getItem(Tags.RefSeriesSeq).get(Tags.RefSOPSeq);
        Dataset item;
        for ( int i = 0 ; i < refSopSq.countItems() ; i++ ) {
            item = refSopSq.getItem(i);
            prefetch(wadourl, dest, item.getString(Tags.RefSOPInstanceUID));
        }
    }

    private void prefetch(String baseURL, String dest, String iuid) throws IOException {
        String url = baseURL+"&objectUID="+iuid;
        try {
            URL wadourl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) wadourl.openConnection();
            if (conn.getResponseCode() != HttpServletResponse.SC_OK) {
                log.warn("Prefetch WADO URL failed:"+wadourl);
                throw new IOException(conn.getResponseCode()+":"+conn.getResponseMessage());
            }
            if (dest != null) {
                export(dest, iuid, conn);
            }
        } catch (MalformedURLException e) {
            log.error("Prefetch request ignored: Malformed WADO URL! Need configuration change in wado-prefetch.xsl! url:"+url);
        }
    }

    private void export(String dest, String iuid, HttpURLConnection conn)
            throws IOException, FileNotFoundException {
        InputStream is = conn.getInputStream();
        File file = getFile(dest, iuid);
        log.info("M-WRITE "+file);
        BufferedOutputStream out = new BufferedOutputStream(
                new FileOutputStream(file));
        byte[] buf = new byte[BUF_LEN];
        try {
            int len = is.read(buf);
            while (len > 0) {
                out.write(buf, 0, len);
                len = is.read(buf);
            }
        } finally {
            is.close();
            out.flush();
            out.close();
        }
    }

    private File getFile(String dest, String iuid) {
        String fn = MessageFormat.format(dest, new Object[]{iuid});
        File file = FileUtils.resolve(new File(fn));
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        return file;
    }
    
    private void xslt(String sourceAET, String retrieveAET,
            Dataset ds, Templates tpl, ContentHandler ch)
            throws TransformerConfigurationException, IOException {
        SAXTransformerFactory tf = (SAXTransformerFactory)
            TransformerFactory.newInstance();
        TransformerHandler th = tf.newTransformerHandler(tpl);
        Transformer t = th.getTransformer();
        t.setParameter("source-aet", sourceAET);
        t.setParameter("retrieve-aet", retrieveAET);
        t.setParameter("wado-baseurl", wadoBaseUrl);
        t.setParameter("export-path", exportBasePath);
        th.setResult(new SAXResult(ch));
        ds.writeDataset2(th, null, null, 64, null);
    }
}