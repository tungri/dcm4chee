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

package org.dcm4chex.archive.dcm.movescu;

import java.io.IOException;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.management.ObjectName;

import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.AssociationFactory;
import org.dcm4che.net.DcmServiceException;
import org.dcm4che.net.Dimse;
import org.dcm4chex.archive.config.RetryIntervalls;
import org.dcm4chex.archive.dcm.AbstractScuService;
import org.dcm4chex.archive.mbean.JMSDelegate;

/**
 * @author gunter.zeilinger@tiani.com
 * @version $Revision: 18281 $ $Date: 2014-04-09 19:06:44 +0000 (Wed, 09 Apr 2014) $
 * @since 17.12.2003
 */
public class MoveScuService extends AbstractScuService implements
        MessageListener {
    /**
     * A message listener that handles incoming {@link MoveOrder MoveOrders}.
     */
    protected class MessageListenerImpl implements MessageListener {
        public void onMessage(Message message) {
            try {
                process((MoveOrder) ((ObjectMessage) message).getObject());
            } catch (Throwable e) {
                log.error("unexpected error during processing message: " + message,
                        e);
            }
        }

        /**
         * A delegate method that does most of the order handling, including
         * catching {@link Exception Exceptions} and rescheduling failed jobs.
         * 
         * This method is, in turn, a template method, which delegates to
         * {@link #doProcess(MoveOrder)}, which performs the core processing without
         * error handling (i.e., non-scheduling).
         * 
         * @param order the {@link MoveOrder} to process
         * @throws Exception if an error occurs
         */
        protected void process(MoveOrder order) throws Exception {
            try {
                log.info("Start processing " + order);

                doProcess(order);
                
                log.info("Finished processing " + order);
            } catch (Exception e) {
                order.setThrowable(e);
                final int failureCount = order.getFailureCount() + 1;
                order.setFailureCount(failureCount);
                final long delay = retryIntervalls.getIntervall(
                        order.getMoveDestination(), failureCount);
                if (delay == -1L) {
                    log.error("Give up to process " + order, e);
                    jmsDelegate.fail(queueName, order);
                } else {
                    log.warn("Failed to process " + order
                            + ". Scheduling retry.", e);
                    scheduleMoveOrder(order, System.currentTimeMillis() + delay);
                }
            }
        }

        /**
         * Performs the core order processing.
         *
         * @param order the {@link MoveOrder} to process
         * @throws Exception if an error occurs
         */
        protected void doProcess(MoveOrder order) throws Exception {
            String aet = order.getRetrieveAET();
            if (forceCalledAET || aet == null) {
                aet = calledAET;
            }

            ActiveAssociation aa = openAssociation(aet,
                    UIDs.PatientRootQueryRetrieveInformationModelMOVE);

            try {
                invokeDimse(aa, order);
            } finally {
                try {
                    aa.release(true);
                    // workaround to ensure that the final MOVE-RSP is
                    // processed
                    // before to continue
                    Thread.sleep(10);
                } catch (Exception e) {
                    log.warn(
                            "Failed to release association "
                                    + aa.getAssociation(), e);
                }
            }
        }

        private void invokeDimse(ActiveAssociation aa, MoveOrder order)
                throws InterruptedException, IOException, DcmServiceException {
            AssociationFactory af = AssociationFactory.getInstance();
            DcmObjectFactory dof = DcmObjectFactory.getInstance();
            Command cmd = dof.newCommand();
            cmd.initCMoveRQ(aa.getAssociation().nextMsgID(),
                    UIDs.PatientRootQueryRetrieveInformationModelMOVE, order
                            .getPriority(), order.getMoveDestination());
            Dataset ds = dof.newDataset();
            ds.putCS(Tags.QueryRetrieveLevel, order.getQueryRetrieveLevel());
            putLO(ds, Tags.PatientID, order.getPatientId());
            putUI(ds, Tags.StudyInstanceUID, order.getStudyIuids());
            putUI(ds, Tags.SeriesInstanceUID, order.getSeriesIuids());
            putUI(ds, Tags.SOPInstanceUID, order.getSopIuids());
            modifyMoveRq(cmd, ds, order);
            log.debug("Move Identifier:\n");
            log.debug(ds);
            Dimse dimseRsp = aa.invoke(af.newDimse(PCID_MOVE, cmd, ds)).get();
            Command cmdRsp = dimseRsp.getCommand();
            int status = cmdRsp.getStatus();
            if (status != 0) {
                if (status == Status.SubOpsOneOrMoreFailures
                        && order.getSopIuids() != null) {
                    Dataset moveRspData = dimseRsp.getDataset();
                    if (moveRspData != null) {
                        String[] failedUIDs = ds
                                .getStrings(Tags.FailedSOPInstanceUIDList);
                        if (failedUIDs != null && failedUIDs.length != 0) {
                            order.setSopIuids(failedUIDs);
                        }
                    }
                }
                throw new DcmServiceException(status, cmdRsp
                        .getString(Tags.ErrorComment));
            }
        }

        protected void modifyMoveRq(Command cmd, Dataset ds, MoveOrder order ) {
        }
    }

    private static final int PCID_MOVE = 1;

    private static final String DEF_CALLED_AET = "QR_SCP";

    private String calledAET = DEF_CALLED_AET;

    private RetryIntervalls.Map retryIntervalls;

    private int concurrency = 1;

    private String queueName;
    
    private boolean forceCalledAET;

    private JMSDelegate jmsDelegate = new JMSDelegate(this);

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

    public final String getCalledAET() {
        return calledAET;
    }

    public final void setCalledAET(String retrieveAET) {
        this.calledAET = retrieveAET;
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
        return retryIntervalls != null
                ? retryIntervalls.toString()
                : "NEVER\n";
    }

    public void setRetryIntervalls(String text) {
        retryIntervalls = new RetryIntervalls.Map(text);
    }

    public boolean isForceCalledAET() {
        return forceCalledAET;
    }

    public void setForceCalledAET(boolean forceCalledAET) {
        this.forceCalledAET = forceCalledAET;
    }

    public void scheduleMove(String retrieveAET, String destAET,
            int priority, String pid, String studyIUID, String seriesIUID,
            String[] sopIUIDs, long scheduledTime) {
        MoveOrder moveOrder = new MoveOrder(retrieveAET, destAET, priority, pid,
                        studyIUID, seriesIUID, sopIUIDs);        
        moveOrder.processOrderProperties();
        scheduleMoveOrder(moveOrder, scheduledTime);
    }

    public void scheduleMove(String retrieveAET, String destAET, int priority,
            String pid, String[] studyIUIDs, String[] seriesIUIDs,
            String[] sopIUIDs, long scheduledTime) {
        MoveOrder moveOrder = new MoveOrder(retrieveAET, destAET, priority, pid,
                        studyIUIDs, seriesIUIDs, sopIUIDs);
        moveOrder.processOrderProperties();
        scheduleMoveOrder(moveOrder, scheduledTime);
    }

    public void scheduleMoveOrder(MoveOrder order, long scheduledTime) {
        try {
            log.info("Schedule order: " + order);            
            jmsDelegate.queue(queueName, order, JMSDelegate.toJMSPriority(order
                    .getPriority()), scheduledTime);
        } catch (Exception e) {
            log.error("Failed to schedule order: " + order);
        }
    }

    protected void startService() throws Exception {
        jmsDelegate.startListening(queueName, this, concurrency);
    }

    protected void stopService() throws Exception {
        jmsDelegate.stopListening(queueName);
    }

    public void onMessage(Message message) {
        getMessageListener().onMessage(message);
    }

    protected MessageListener getMessageListener() {
        return new MessageListenerImpl();
    }

    private static void putLO(Dataset ds, int tag, String s) {
        if (s != null)
            ds.putLO(tag, s);
    }

    private static void putUI(Dataset ds, int tag, String[] uids) {
        if (uids != null)
            ds.putUI(tag, uids);
    }
}