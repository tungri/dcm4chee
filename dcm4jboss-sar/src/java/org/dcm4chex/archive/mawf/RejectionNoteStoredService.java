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
 * Agfa HealthCare.
 * Portions created by the Initial Developer are Copyright (C) 2006-2008
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See listed authors below.
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
package org.dcm4chex.archive.mawf;

import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ejb.CreateException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.srom.Code;
import org.dcm4che.srom.SRDocumentFactory;
import org.dcm4chex.archive.common.BaseJmsOrder;
import org.dcm4chex.archive.common.PrivateTags;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.dcm.storescp.StoreScpService;
import org.dcm4chex.archive.ejb.interfaces.Storage;
import org.dcm4chex.archive.ejb.interfaces.StorageHome;
import org.dcm4chex.archive.exceptions.ConfigurationException;
import org.dcm4chex.archive.mbean.JMSDelegate;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.dcm4chex.archive.util.HomeFactoryException;
import org.jboss.system.ServiceMBeanSupport;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date$
 * @since Mar 31, 2009
 */
public class RejectionNoteStoredService extends ServiceMBeanSupport
    implements NotificationListener, MessageListener {

    private static Pattern PATTERN = Pattern.compile("\\s*(\\([^\\(\\)]+\\))");

    private ObjectName storeScpServiceName;
    private ObjectName contentEditServiceName;
    private JMSDelegate jmsDelegate = new JMSDelegate(this);
    private String queueName;
    private List<Code> rejectionNoteCodes;
    private long deletionDelay;
    private boolean keepRejectionNote;
    private RetryIntervalls retryIntervals = new RetryIntervalls();

    private boolean checkCallingAET;

    public ObjectName getStoreScpServiceName() {
        return storeScpServiceName;
    }

    public final void setStoreScpServiceName(ObjectName storeScpServiceName) {
        this.storeScpServiceName = storeScpServiceName;
    }

    public void setContentEditServiceName(ObjectName serviceName) {
        this.contentEditServiceName = serviceName;
    }

    public ObjectName getContentEditServiceName() {
        return contentEditServiceName;
    }

    public ObjectName getJmsServiceName() {
        return jmsDelegate.getJmsServiceName();
    }
    
    public void setJmsServiceName(ObjectName jmsServiceName) {
        jmsDelegate.setJmsServiceName(jmsServiceName);
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getQueueName() {
        return queueName;
    }

    public String getRejectionNoteCodes() {
        StringBuilder sb = new StringBuilder();
        for (Code code : rejectionNoteCodes) {
            sb.append(code).append("\r\n");
        }
        return sb.toString();
    }

    public void setRejectionNoteCodes(String input) {
        Matcher m = PATTERN.matcher(input);
        List<Code> tmp = new ArrayList<Code>();
        while (m.find()) {
            tmp.add(SRDocumentFactory.getInstance().newCode(m.group(1)));
        }
        this.rejectionNoteCodes = tmp;
    }

    public String getDeletionDelay() {
        return RetryIntervalls.formatInterval(deletionDelay);
    }

    public void setDeletionDelay(String delay) {
        this.deletionDelay = RetryIntervalls.parseInterval(delay.trim());
    }

    public void setKeepRejectionNote(boolean keepRejectionNote) {
        this.keepRejectionNote = keepRejectionNote;
    }

    public boolean isKeepRejectionNote() {
        return keepRejectionNote;
    }

    public boolean isCheckCallingAET() {
        return checkCallingAET;
    }

    public void setCheckCallingAET(boolean checkCallingAET) {
        this.checkCallingAET = checkCallingAET;
    }

    public void setRetryIntervals(String retryIntervals) {
        this.retryIntervals = new RetryIntervalls(retryIntervals);
    }

    public String getRetryIntervals() {
        return retryIntervals.toString();
    }

    public boolean scheduleDeleteOnRejectionNoteStored(Dataset ds)
            throws Exception {
        if (!UIDs.KeyObjectSelectionDocument.equals(
                ds.getString(Tags.SOPClassUID))) {
            return false;
        }
        Dataset title = ds.getItem(Tags.ConceptNameCodeSeq);
        if (!rejectionNoteCodes.contains(
                SRDocumentFactory.getInstance().newCode(title))) {
            return false;
        }
        schedule(new RejectionNoteStoredOrder(ds), deletionDelay != 0 
                    ? System.currentTimeMillis() + deletionDelay : 0L);
        return true;
    }

    protected void schedule(BaseJmsOrder order, long scheduledTime)
            throws Exception {
        if (log.isInfoEnabled()) {
            String scheduledTimeStr = scheduledTime > 0
                    ? new Date(scheduledTime).toString()
                    : "now";
            log.info("Scheduling job [" + order + "] at "
                    + scheduledTimeStr + ". Retry times: "
                    + order.getFailureCount());
        }
        jmsDelegate.queue(queueName, order,
                Message.DEFAULT_PRIORITY, scheduledTime);
    }

    protected void startService() throws Exception {
        server.addNotificationListener(storeScpServiceName, this,
                StoreScpService.NOTIF_FILTER, null);
        jmsDelegate.startListening(queueName, this , 1);
    }

    protected void stopService() throws Exception {
        server.removeNotificationListener(storeScpServiceName, this,
                StoreScpService.NOTIF_FILTER, null);
        jmsDelegate.stopListening(queueName);
    }


    public void handleNotification(Notification notif, Object handback) {
        Dataset ds = (Dataset) notif.getUserData();
        if (UIDs.KeyObjectSelectionDocument.equals(
                ds.getString(Tags.SOPClassUID))) {
            Dataset title = ds.getItem(Tags.ConceptNameCodeSeq);
            if (rejectionNoteCodes.contains(
                    SRDocumentFactory.getInstance().newCode(title))) {
                RejectionNoteStoredOrder order =
                    new RejectionNoteStoredOrder(ds);
                try {
                    schedule(order, deletionDelay == 0 ? 0L
                            : System.currentTimeMillis() + deletionDelay);
                } catch (Exception e) {
                    log.error("Failed to schedule " + order, e);
                }
            }
        }
    }

    public void onMessage(Message msg) {
        RejectionNoteStoredOrder order;
        try {
            order = (RejectionNoteStoredOrder)
                    ((ObjectMessage) msg).getObject();
        } catch (Exception e) {
            log.error("Processing JMS message failed! message:" + msg, e);
            return;
        }
        if (log.isDebugEnabled())
            log.debug("Processing " + order);
        try {
            moveInstancesToTrash(iuidsOf(order.getDataset()));
            if (log.isDebugEnabled())
                log.debug("Finished processing " + order);
        } catch (Exception e) {
            final int failureCount = order.getFailureCount() + 1;
            final long delay = retryIntervals.getIntervall(failureCount);
            order.setFailureCount(failureCount);
            if (delay == -1L) {
                order.setThrowable(e);
                log.error("Give up to process " + order);
                try {
                    jmsDelegate.fail(queueName, order);
                } catch (Exception e2) {
                    log.error("Failed to notify JMSDelgate of failed job!"
                            + " Give up to process" + order, e2);
                }
            } else {
                Throwable thisThrowable = e;
                if (e instanceof InvocationTargetException)
                    thisThrowable = ((InvocationTargetException) e)
                            .getTargetException();

                if (failureCount == 1
                        || (order.getThrowable() != null && !thisThrowable
                                .getClass().equals(
                                        order.getThrowable().getClass()))) {
                    // If this happens first time, log as error
                    log.error("Failed to process JMS job."
                            +" Will schedule retry... Dumping - "
                            + order.toLongString(), e);
                    // Record this exception
                    order.setThrowable(thisThrowable);
                } else {
                    // otherwise, if it's the same exception as before
                    log.warn("Failed to process " + order
                            + ". Details should have been provided."
                            + " Will schedule retry.");
                }
                try {
                    schedule(order, System.currentTimeMillis() + delay);
                } catch (Exception e2) {
                    log.error("Failed to schedule retry! Give up to process "
                            + order, e2);
                }
            }
        }
    }

    private String[] iuidsOf(Dataset ds) {
        List<String> iuids;
        if (checkCallingAET) {
            ds.setPrivateCreatorID(PrivateTags.CreatorID);
            String callingAET = ds.getString(PrivateTags.CallingAET);
            ds.setPrivateCreatorID(null);
            try {
                Storage store = this.getStorage();
                iuids = store.getSopIuidsForRejectionNote(ds, callingAET);
            } catch (Exception x) {
                log.error("Get SOPInstance UIDs for RejectionNote failed!", x);
                iuids =  new ArrayList<String>();
            }
        } else {
            iuids = new ArrayList<String>();
            DcmElement stysq = ds.get(Tags.CurrentRequestedProcedureEvidenceSeq);
            for (int i = 0, styCount = stysq.countItems(); i < styCount; i++) {
                DcmElement sersq = stysq.getItem(i).get(Tags.RefSeriesSeq);
                for (int j = 0, serCount = sersq.countItems(); j < serCount; j++) {
                    DcmElement refsopsq = sersq.getItem(j).get(Tags.RefSOPSeq);
                    for (int k = 0, refsopsqCount = refsopsq.countItems();
                            k < refsopsqCount; k++) {
                        iuids.add(refsopsq.getItem(k)
                                .getString(Tags.RefSOPInstanceUID));
                    }
                }
            }
       }
       if (!keepRejectionNote) {
           iuids.add(ds.getString(Tags.SOPInstanceUID));
       }
       return iuids.toArray(new String[iuids.size()]);
    }

    private void moveInstancesToTrash(String[] iuids) throws Exception {
        try {
            server.invoke(contentEditServiceName,
                    "moveInstancesToTrash",
                    new Object[] { iuids },
                    new String[] { String[].class.getName() });
        } catch (MBeanException e) {
            throw e.getTargetException();
        } catch (InstanceNotFoundException e) {
            throw new ConfigurationException(e);
        } catch (ReflectionException e) {
            throw new ConfigurationException(e);
        }
    }
    
    Storage getStorage() throws RemoteException, CreateException, HomeFactoryException {
        return ((StorageHome) EJBHomeFactory.getFactory().lookup(
                StorageHome.class, StorageHome.JNDI_NAME)).create();
    }

}
