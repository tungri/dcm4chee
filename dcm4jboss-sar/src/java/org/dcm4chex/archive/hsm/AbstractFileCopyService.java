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

package org.dcm4chex.archive.hsm;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.dcm4che.data.Dataset;
import org.dcm4chex.archive.common.BaseJmsOrder;
import org.dcm4chex.archive.common.FileStatus;
import org.dcm4chex.archive.common.SeriesStored;
import org.dcm4chex.archive.config.Condition;
import org.dcm4chex.archive.config.ForwardingRules;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.ejb.interfaces.StorageHome;
import org.dcm4chex.archive.exceptions.ConfigurationException;
import org.dcm4chex.archive.mbean.JMSDelegate;
import org.dcm4chex.archive.util.EJBHomeFactory;
import org.dcm4chex.archive.util.HomeFactoryException;
import org.jboss.system.ServiceMBeanSupport;

/**
 * @author gunter.zeilinger@tiani.com
 * @version $Revision: 17361 $ $Date: 2012-10-31 07:20:53 +0000 (Wed, 31 Oct 2012) $
 * @since Jan 16, 2006
 */
public abstract class AbstractFileCopyService extends ServiceMBeanSupport
        implements MessageListener, NotificationListener {

    protected ObjectName storeScpServiceName;

    protected ObjectName queryRetrieveScpServiceName;

    protected String queueName;

    protected int concurrency = 1;

    protected int fileStatus = FileStatus.TO_ARCHIVE;

    protected boolean verifyCopy;
    
    protected Condition condition = null;
    
    protected String destination = null;

    protected RetryIntervalls retryIntervalls = new RetryIntervalls();

    protected int bufferSize = 8192;

    protected JMSDelegate jmsDelegate = new JMSDelegate(this);

    private long notReadyDelay;

    private int fetchSize;

    public final ObjectName getJmsServiceName() {
        return jmsDelegate.getJmsServiceName();
    }

    public final void setJmsServiceName(ObjectName jmsServiceName) {
        jmsDelegate.setJmsServiceName(jmsServiceName);
    }
        
    public final int getBufferSize() {
        return bufferSize;
    }

    public final void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public final ObjectName getStoreScpServiceName() {
        return storeScpServiceName;
    }

    public final void setStoreScpServiceName(ObjectName storeScpServiceName) {
        this.storeScpServiceName = storeScpServiceName;
    }

    public ObjectName getQueryRetrieveScpServiceName() {
        return queryRetrieveScpServiceName;
    }

    public void setQueryRetrieveScpServiceName(ObjectName name) {
        this.queryRetrieveScpServiceName = name;
    }

    protected String getRetrieveAETs() {
        try {
            return (String) server.getAttribute(queryRetrieveScpServiceName,
                    "CalledAETitles");
        } catch (Exception e) {
            throw new ConfigurationException(
                    "Failed to access CalledAETitles from "
                    + queryRetrieveScpServiceName, e);
        }
    }

    public final int getConcurrency() {
        return concurrency;
    }

    public final String getDestination() {
        return destination == null ? "NONE" : condition == null ? destination
                : condition.toString() + destination;
    }

    public final void setDestination(String destination) throws Exception {
        destination = destination.trim();
        if ("NONE".equalsIgnoreCase(destination)) {
            this.condition = null;
            this.destination = null;
            return;
        }
        Condition newCondition = null;
        int startDest = destination.indexOf(']');
        if (startDest != -1) {
            newCondition = new Condition(destination.substring(0, startDest+1));
            destination = destination.substring(startDest+1);
        }
        this.condition = newCondition;
        this.destination = destination;
    }

    public final String getFileStatus() {
        return FileStatus.toString(fileStatus);
    }

    public final void setFileStatus(String fileStatus) {
        this.fileStatus = FileStatus.toInt(fileStatus);
    }

    public final boolean isVerifyCopy() {
        return verifyCopy;
    }

    public final void setVerifyCopy(boolean verifyCopy) {
        this.verifyCopy = verifyCopy;
    }

    public final String getRetryIntervalls() {
        return retryIntervalls.toString();
    }

    public final void setRetryIntervalls(String s) {
        this.retryIntervalls = new RetryIntervalls(s);
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

    public String getNotReadyDelay() {
        return RetryIntervalls.formatInterval(notReadyDelay);
    }

    public void setNotReadyDelay(String notReadyDelay) {
        this.notReadyDelay = RetryIntervalls.parseInterval(notReadyDelay);
    }

    public final String getQueueName() {
        return queueName;
    }

    public final void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    protected void startService() throws Exception {
        jmsDelegate.startListening(queueName, this, concurrency);
        server.addNotificationListener(storeScpServiceName, this,
                SeriesStored.NOTIF_FILTER, null);
    }

    protected void stopService() throws Exception {
        server.removeNotificationListener(storeScpServiceName, this,
                SeriesStored.NOTIF_FILTER, null);
        jmsDelegate.stopListening(queueName);
    }

    public void handleNotification(Notification notif, Object handback) {
        if (destination == null) {
            return;
        }
        SeriesStored seriesStored = (SeriesStored) notif.getUserData();
        if (seriesStored.getRetrieveAET() == null) {
            log.warn("Ignore SeriesStored notification! Reason: Series is not locally retrievable.");
            return;
        }
        if (condition != null) {
            Map param = new HashMap();
            param.put("calling", new String[] { seriesStored.getSourceAET() });
            if (!condition.isTrueFor(param)) {
                return;
            }
        }
        
        schedule(createOrder(seriesStored.getIAN()), -1l);
    }
    
    public boolean scheduleByIAN(Dataset ian, long scheduleTime) {
        if (destination == null) {
            return false;
        }
        return schedule(createOrder(ian), scheduleTime);
    }
    
    protected boolean schedule(BaseJmsOrder order) {
        return schedule(order, isReady() ?
                ForwardingRules.toScheduledTime(destination) : notReadyDelay);
    }

    protected boolean schedule(BaseJmsOrder order, long scheduledTime) {
        if (!isReady()) {
            scheduledTime = notReadyDelay;
        } else if (scheduledTime < 0) {
            scheduledTime = ForwardingRules.toScheduledTime(destination);
        }
        try {
            log.info("Scheduling " + order + (scheduledTime == 0 ? 
                    " now" : " at "+new Date(scheduledTime)) );
            jmsDelegate.queue(queueName, order, Message.DEFAULT_PRIORITY,
                    scheduledTime);
            return true;
        } catch (Exception e) {
            log.error("Failed to schedule " + order, e);
            return false;
        }
    }

    public void onMessage(Message message) {
        ObjectMessage om = (ObjectMessage) message;
        try {
            BaseJmsOrder order = (BaseJmsOrder) om.getObject();
            if (!isReady()) {
                log.warn("FileCopy service not ready! Rescheduled with delay of "+notReadyDelay+ "ms!");
                schedule(order, System.currentTimeMillis() + notReadyDelay);
                return;
            }
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
                    jmsDelegate.fail(queueName,order);
                } else {
                    log.warn("Failed to process " + order
                            + ". Scheduling retry.", e);
                    schedule(order, System.currentTimeMillis() + delay);
                }
            }
        } catch (Throwable e) {
            log.error("unexpected error during processing message: " + message,
                    e);
        }
    }

    public abstract boolean isReady();
    
    protected abstract BaseJmsOrder createOrder(Dataset ian);

    protected abstract void process(BaseJmsOrder order) throws Exception;

    public int getFetchSize() {
        return fetchSize;
    }

    public void setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
    }

    protected static StorageHome getStorageHome() throws HomeFactoryException {
        return (StorageHome) EJBHomeFactory.getFactory().lookup(
                StorageHome.class, StorageHome.JNDI_NAME);
    }
}
