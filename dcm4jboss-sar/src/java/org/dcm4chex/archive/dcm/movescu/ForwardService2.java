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

package org.dcm4chex.archive.dcm.movescu;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Tags;
import org.dcm4che.util.DTFormat;
import org.dcm4cheri.util.StringUtils;
import org.dcm4chex.archive.common.Availability;
import org.dcm4chex.archive.common.SeriesStored;
import org.dcm4chex.archive.config.DicomPriority;
import org.dcm4chex.archive.config.ForwardingRules;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.ejb.interfaces.ContentManager;
import org.dcm4chex.archive.ejb.interfaces.ContentManagerHome;
import org.dcm4chex.archive.mbean.TemplatesDelegate;
import org.dcm4chex.archive.util.ContentHandlerAdapter;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.jboss.system.ServiceMBeanSupport;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision: 17788 $ $Date: 2013-05-15 13:25:00 +0000 (Wed, 15 May 2013) $
 * @since Apr 17, 2007
 */
public class ForwardService2 extends ServiceMBeanSupport {

    private static final String FORWARD_XSL = "forward.xsl";
    private static final String FORWARD_PRIORS_XSL = "forward_priors.xsl";

    private static final String ALL = "ALL";

    private static final String NONE = "NONE";

    private static final String[] EMPTY = {};

    private String[] forwardOnInstanceLevelFromAETs = EMPTY;
    
    private final NotificationListener seriesStoredListener = new NotificationListener() {
        public void handleNotification(Notification notif, Object handback) {
            ForwardService2.this.onSeriesStored((SeriesStored) notif.getUserData());
        }
    };

    private ObjectName storeScpServiceName;

    private ObjectName moveScuServiceName;

    private TemplatesDelegate templates = new TemplatesDelegate(this);
    private boolean logForwardPriorXML = true;
    private boolean ignoreNotLocalRetrievable;

    public static final SAXTransformerFactory tf = (SAXTransformerFactory) TransformerFactory.newInstance();

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
    
    public final ObjectName getMoveScuServiceName() {
        return moveScuServiceName;
    }

    public final void setMoveScuServiceName(ObjectName moveScuServiceName) {
        this.moveScuServiceName = moveScuServiceName;
    }

    public final ObjectName getStoreScpServiceName() {
        return storeScpServiceName;
    }

    public final void setStoreScpServiceName(ObjectName storeScpServiceName) {
        this.storeScpServiceName = storeScpServiceName;
    }

    public String getForwardOnInstanceLevelFromAETs() {
        return forwardOnInstanceLevelFromAETs == null ? ALL 
                : forwardOnInstanceLevelFromAETs.length == 0 ? NONE 
                : StringUtils.toString(forwardOnInstanceLevelFromAETs, ',');
    }

    public void setForwardOnInstanceLevelFromAETs(String s) {
        forwardOnInstanceLevelFromAETs = ALL.equals(s) ? null
                : NONE.equals(s) ? EMPTY : StringUtils.split(s, ',');
    }

    private boolean isForwardOnInstanceLevelFromAET(String aet) {
        if (forwardOnInstanceLevelFromAETs == null) {
            return true;
        }
        for (int i = 0; i < forwardOnInstanceLevelFromAETs.length; i++) {
            if (aet.equals(forwardOnInstanceLevelFromAETs[i]))
                return true;
        }
        return false;
    }

    public boolean isLogForwardPriorXML() {
        return logForwardPriorXML;
    }

    public void setLogForwardPriorXML(boolean logForwardPriorXML) {
        this.logForwardPriorXML = logForwardPriorXML;
    }

    public boolean isIgnoreNotLocalRetrievable() {
        return ignoreNotLocalRetrievable;
    }

    public void setIgnoreNotLocalRetrievable(boolean ignoreNotLocalRetrievable) {
        this.ignoreNotLocalRetrievable = ignoreNotLocalRetrievable;
    }

    
    protected void startService() throws Exception {
        server.addNotificationListener(storeScpServiceName,
                seriesStoredListener, SeriesStored.NOTIF_FILTER, null);
    }

    protected void stopService() throws Exception {
        server.removeNotificationListener(storeScpServiceName,
                seriesStoredListener, SeriesStored.NOTIF_FILTER, null);
    }
    
    private void onSeriesStored(final SeriesStored stored) {
        if (stored.getRetrieveAET() == null && ignoreNotLocalRetrievable) {
            log.warn("Ignore SeriesStored notification! Reason: Series is not locally retrievable.");
            return;
        }
        Templates tpl = templates.getTemplatesForAET(
                stored.getSourceAET(), FORWARD_XSL);
        if (tpl != null) {
            Dataset ds = DcmObjectFactory.getInstance().newDataset();
            ds.putAll(stored.getPatientAttrs());
            ds.putAll(stored.getStudyAttrs());
            ds.putAll(stored.getSeriesAttrs());
            final Calendar cal = Calendar.getInstance();
            try {
                log.debug("Forward2 transform input:");
                log.debug(ds);
                xslt(cal, stored, null,
                        ds, tpl, new DefaultHandler(){

                   public void startElement(String uri, String localName,
                            String qName, Attributes attrs) {
                        if (qName.equals("destination")) {
                            if (attrs.getValue("includePrior") == null || !scheduleMoveWithPriors(stored, attrs, cal) ) {   
                                scheduleMove(stored.getRetrieveAET(),
                                    attrs.getValue("aet"),
                                    toPriority(attrs.getValue("priority")),
                                    null,
                                    stored.getStudyInstanceUID(),
                                    stored.getSeriesInstanceUID(),
                                    sopIUIDsOrNull(stored),
                                    toScheduledTime(cal, attrs.getValue("delay")));
                            }
                        }
                    }
                });
            } catch (Exception e) {
                log.error("Applying forwarding rules to " + stored + " fails:", e);
            }
        }
    }

    private boolean scheduleMoveWithPriors(final SeriesStored stored, final Attributes attrs, final Calendar cal) {
        log.info("scheduleMoveWithPriors! attrs:"+attrs);
        String includePriors = attrs.getValue("includePrior");
        Templates tpl = templates.getTemplatesForAET(
                stored.getSourceAET(), FORWARD_PRIORS_XSL);
        if (tpl == null) {
            log.warn("Missing forward_priors.xsl! source AET:"+stored.getSourceAET()+" includePriors:"+includePriors);
            return false;
        }
        Properties transformParams = toTransformParams(attrs);
        long delay = toScheduledTime(cal, attrs.getValue("delay"));
        try {
            Set<Dataset> priors = findPriors(stored, attrs);
            log.debug("priors found:"+priors.size());
            if (priors.isEmpty()) {
                log.info("No priors found to forward!");
                return false;
            }
            Map<String, Map<String, Set<String>>> studies = getPriorsToForward(
                    stored, cal, tpl, transformParams, priors);
            
            ensureSeriesStoredInForward(stored, studies);
            String retrAET = stored.getRetrieveAET();
            String destAET = attrs.getValue("aet");
            int priority = toPriority(attrs.getValue("priority"));
            Map<String, Set<String>> series;
            String studyIUID;
            for ( Entry<String, Map<String, Set<String>>> studyEntry : studies.entrySet()) {
                studyIUID = studyEntry.getKey();
                series = studyEntry.getValue();
                if (series == null || series.isEmpty()) {
                    scheduleMove(retrAET, destAET, priority, null, studyIUID, null, null, delay);
                } else {
                    for (Entry<String,Set<String>> seriesEntry : series.entrySet()) {
                        scheduleMove(retrAET, destAET, priority, null, 
                                studyIUID, seriesEntry.getKey(), sopIUIDsOrNull(seriesEntry.getValue()), delay);
                    }
                }
            }
            return true;
        } catch (Exception x) {
            log.error("Failed to schedule forward with priors!", x);
            return false;
        }
    }

    private String[] sopIUIDsOrNull(Set<String> instances) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        return instances.toArray(new String[instances.size()]);
    }

    private Properties toTransformParams(final Attributes attrs) {
        Properties transformParams = new Properties();
        for (int i = 0, len=attrs.getLength() ; i < len ; i++) {
            transformParams.setProperty(attrs.getQName(i), attrs.getValue(i));
        }
        return transformParams;
    }

    private void ensureSeriesStoredInForward(final SeriesStored stored,
            final Map<String, Map<String, Set<String>>> studies) {
        Map<String,Set<String>> series = studies.get(stored.getStudyInstanceUID());
        if (series == null) {
            log.debug("Study of SeriesStored not in forwardWithPriors! Add studyIUID:"+stored.getStudyInstanceUID());
            series = new HashMap<String,Set<String>>();
            studies.put(stored.getStudyInstanceUID(), series);
            series.put(stored.getSeriesInstanceUID(), new HashSet<String>());
        }
        if (series.size() > 0) {
            Set<String> instances = series.get(stored.getSeriesInstanceUID());
            if (instances == null || instances.isEmpty()) {
                String[] iuids = sopIUIDsOrNull(stored);
                if (iuids != null && iuids.length > 0) {
                    for (int i = 0 ; i < iuids.length ; i++) {
                        instances.add(iuids[i]);
                    }
                }
            }
        } else {
            log.debug("Study of SeriesStored has no series referenced! -> forward whole study!");
        }
    }

    private Map<String, Map<String, Set<String>>> getPriorsToForward(
            final SeriesStored stored, final Calendar cal, Templates tpl,
            Properties transformParams, Set<Dataset> priors)
            throws TransformerFactoryConfigurationError,
            TransformerConfigurationException, SAXException, IOException {
        final Map<String,Map<String,Set<String>>> studies = new HashMap<String,Map<String,Set<String>>>();

        if (logForwardPriorXML ) {
            FileOutputStream fos = null;
            try {
                File logFile = new File(System.getProperty("jboss.server.log.dir"), 
                        "forward_prior/"+new DTFormat().format(new Date())+".xml");
                logFile.getParentFile().mkdirs();
                TransformerHandler thLog = tf.newTransformerHandler();
                fos = new FileOutputStream(logFile);
                thLog.setResult(new StreamResult(fos));
                transformPrior(stored, priors, thLog);
                log.info("ForwardPrior XML logged in "+logFile);
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch(Exception ignore) {}
                }
            }
        }            

        TransformerHandler th = prepareTransformHandler(cal, stored, 
                transformParams, tpl, new DefaultHandler() {
                   public void startElement(String uri, String localName,
                            String qName, Attributes attrs) {
                        if (qName.equals("forward")) {
                            add(studies, attrs);
                        }
                    }
                });
        transformPrior(stored, priors, th);
        return studies;
    }

    private void transformPrior(final SeriesStored stored, Set<Dataset> priors,
            TransformerHandler th) throws SAXException, IOException {
        ContentHandlerAdapter cha = new ContentHandlerAdapter(th);
        cha.forcedStartDocument();
        cha.startElement("forward");
        cha.startElement("seriesStored");
        stored.getIAN().writeDataset2(cha, null, null, 64, null);
        cha.endElement("seriesStored");
        cha.startElement("priors");
        for (Dataset ds : priors) {
            ds.writeDataset2(cha, null, null, 64, null);
        }
        cha.endElement("priors");
        cha.endElement("forward");
        cha.forcedEndDocument();
    }
    
    private Set<Dataset> findPriors(SeriesStored stored, Attributes attrs) {
        boolean instanceLevel = "INSTANCE".equals(attrs.getValue("level"));
        String avail = attrs.getValue("availability");
        int minAvail = avail == null ? Availability.NEARLINE : Availability.toInt(avail);
        String retrAETsVal = attrs.getValue("retrAETs");
        String[] retrAETs = null;
        if (retrAETsVal == null) {
            retrAETs = new String[]{stored.getRetrieveAET()};
        } else if (retrAETsVal.trim().length() > 0 && !NONE.equals(retrAETsVal)) {
            retrAETs = StringUtils.split(retrAETsVal, '\\');
        }
        String modalities = attrs.getValue("modalities");
        String notOlderThan = attrs.getValue("notOlderThan");
        Long createdAfter = null;
        if (notOlderThan != null) {
            createdAfter = new Long(System.currentTimeMillis()-RetryIntervalls.parseInterval(notOlderThan));
        }
        try {
            return getContentManager().getPriorInfos(stored.getStudyInstanceUID(), instanceLevel,
                    minAvail, createdAfter, retrAETs, StringUtils.split(modalities, '\\'));
        } catch (Exception x) {
            log.error("Failed to get prior studies for seriesStored:"+stored,x);
            return null;
        }
    }

    private boolean add(Map<String,Map<String,Set<String>>> studies, Attributes attrs) {
        String studyIUID = attrs.getValue("studyIUID");
        if (studyIUID == null) {
            log.warn("Missing studyIUID attribute in destination element of forward_priors.xsl! Ignored!");
            return false;
        }
        Map<String,Set<String>> series = studies.get(studyIUID);
        if (series == null) {
            series = new HashMap<String,Set<String>>();
            studies.put(studyIUID, series);
        }
        String seriesIUID = attrs.getValue("seriesIUID");
        String iuid = attrs.getValue("iuid");
        if (seriesIUID != null) {
            Set<String> instances = series.get(seriesIUID);
            if (instances == null) {
                instances = new HashSet<String>();
                series.put(seriesIUID, instances);
            }
            if (iuid != null) {
                instances.add(iuid);
            }
        } else if (iuid != null) {
            log.warn("forward_priors.xsl: Missing seriesIUID attribute (sop iuid:"+iuid+
                    ")! SOP Instance UID ignored! -> Study Level");
        }
        return true;
    }
    
    private String[] sopIUIDsOrNull(SeriesStored seriesStored) {
        int numI = seriesStored.getNumberOfInstances();
        if (numI > 1 && !isForwardOnInstanceLevelFromAET(
                        seriesStored.getSourceAET())) {
            return null;
        }
        String[] iuids = new String[numI];
        DcmElement sq = seriesStored.getIAN().getItem(Tags.RefSeriesSeq)
                .get(Tags.RefSOPSeq);
        for (int i = 0; i < iuids.length; i++) {
            iuids[i] = sq.getItem(i).getString(Tags.RefSOPInstanceUID);
        }
        return iuids;
    }

    private static int toPriority(String s) {
        return s != null ? DicomPriority.toCode(s) : 0;
    }

    private long toScheduledTime(Calendar cal, String s) {
        if (s == null || s.length() == 0) {
            return 0;
        }
        int index = s.indexOf('!');
        if (index == -1) {
            return cal.getTimeInMillis() + RetryIntervalls.parseInterval(s); 
        }
        if (index != 0) {
            cal.setTimeInMillis(cal.getTimeInMillis()
                    + RetryIntervalls.parseInterval(s.substring(0, index)));
        }
        return ForwardingRules.afterBusinessHours(cal, s.substring(index+1));
    }
    
    private static void xslt(Calendar cal, SeriesStored stored, Properties params,
            Dataset ds, Templates tpl, ContentHandler ch)
            throws TransformerConfigurationException, IOException {
        TransformerHandler th = prepareTransformHandler(cal, stored, params, tpl, ch);
        ds.writeDataset2(th, null, null, 64, null);
    }

    private static TransformerHandler prepareTransformHandler(Calendar cal,
            SeriesStored stored, Properties params,
            Templates tpl, ContentHandler ch)
            throws TransformerFactoryConfigurationError,
            TransformerConfigurationException {
        TransformerHandler th = tf.newTransformerHandler(tpl);
        Transformer t = th.getTransformer();
        t.setParameter("source-aet", stored.getSourceAET());
        t.setParameter("retrieve-aet", stored.getRetrieveAET());
        if (stored.getExtRetrieveAET() != null)
            t.setParameter("ext-retrieve-aet", stored.getExtRetrieveAET());
        t.setParameter("archived", String.valueOf(stored.isArchived()));
        t.setParameter("year", new Integer(cal.get(Calendar.YEAR)));
        t.setParameter("month", new Integer(cal.get(Calendar.MONTH)+1));
        t.setParameter("date", new Integer(cal.get(Calendar.DAY_OF_MONTH)));
        t.setParameter("day", new Integer(cal.get(Calendar.DAY_OF_WEEK)-1));
        t.setParameter("hour", new Integer(cal.get(Calendar.HOUR_OF_DAY)));
        if (params != null) {
            for (Entry<?, ?> e :params.entrySet()) {
                t.setParameter((String)e.getKey(), e.getValue());
            }
        }
        th.setResult(new SAXResult(ch));
        return th;
    }

    private void scheduleMove(String retrieveAET, String destAET, int priority,
            String pid, String studyIUID, String seriesIUID, String[] sopIUIDs,
            long scheduledTime) {
        try {
            server.invoke(moveScuServiceName, "scheduleMove", new Object[] {
                    retrieveAET, destAET, new Integer(priority), pid,
                    studyIUID, seriesIUID, sopIUIDs, new Long(scheduledTime) },
                    new String[] { String.class.getName(),
                            String.class.getName(), int.class.getName(),
                            String.class.getName(), String.class.getName(),
                            String.class.getName(), String[].class.getName(),
                            long.class.getName() });
        } catch (Exception e) {
            log.error("Schedule Move failed:", e);
        }
    }
    
    private ContentManager getContentManager() throws Exception {
        ContentManagerHome home = (ContentManagerHome) EJBHomeFactory
                .getFactory().lookup(ContentManagerHome.class,
                        ContentManagerHome.JNDI_NAME);
        return home.create();
    }

}
