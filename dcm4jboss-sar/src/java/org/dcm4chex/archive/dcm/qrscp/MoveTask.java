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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;

import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.net.AAbort;
import org.dcm4che.net.AAssociateAC;
import org.dcm4che.net.AAssociateRQ;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.Association;
import org.dcm4che.net.AssociationFactory;
import org.dcm4che.net.DcmServiceException;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.DimseListener;
import org.dcm4che.net.PDU;
import org.dcm4chex.archive.common.Availability;
import org.dcm4chex.archive.dcm.qrscp.FileRetrieveFailedException;
import org.dcm4chex.archive.common.PIDWithIssuer;
import org.dcm4chex.archive.ejb.interfaces.AEDTO;
import org.dcm4chex.archive.ejb.jdbc.FileInfo;
import org.dcm4chex.archive.ejb.jdbc.RetrieveCmd;
import org.dcm4chex.archive.perf.PerfCounterEnum;
import org.dcm4chex.archive.perf.PerfMonDelegate;
import org.dcm4chex.archive.perf.PerfPropertyEnum;
import org.jboss.logging.Logger;

/**
 * @author Gunter.Zeilinger@tiani.com
 * @version $Revision: 17733 $
 * @since 16.09.2003
 */
public class MoveTask implements Runnable {

    protected final QueryRetrieveScpService service;

    protected final Logger log;

    private final String moveDest;

    private final AEDTO aeData;

    private final int movePcid;

    private final Dataset moveRqData;

    private final String moveOriginatorAET;

    private final String moveCalledAET;

    private final int priority;

    private final int msgID;

    private final String sopClassUID;

    private ActiveAssociation moveAssoc;

    private final Set<String> remainingIUIDs;

    private final Set<String> failedIUIDs;

    private final Set<String> failedOnlineRetrieveIUIDs;
    
    private final Set<String> failedNearlineRetrieveIUIDs;

    private final Map<PIDWithIssuer, Set<PIDWithIssuer>> pixQueryResults;

    private final int total;

    private int remaining;

    private int warnings = 0;

    private int completed = 0;

    private boolean canceled = false;

    private boolean moveAssocClosed = false;
    
    private ArrayList<FileInfo> successfulTransferred =
            new ArrayList<FileInfo>();
    
    private MoveScu moveScu;

    private boolean directForwarding;

    private RetrieveInfo retrieveInfo;

    private Dataset stgCmtActionInfo;
    
    private DcmElement refSOPSeq;

    private PerfMonDelegate perfMon;

    private Set<String> invalidRequestedUIDs = new HashSet<String>();

    private DimseListener cancelListener = new DimseListener() {

        public void dimseReceived(Association assoc, Dimse dimse) {
            canceled = true;
            MoveScu tmp = moveScu;
            if (tmp != null) {
                tmp.forwardCancelRQ(dimse);
            }
        }
    };

    private TimerTask sendPendingRsp = new TimerTask() {

        public void run() {
            if (!canceled && remaining > 0) {
                Command rsp = makeMoveRsp(Status.Pending);
                MoveScu tmp = moveScu;
                if (directForwarding && tmp != null) {
                    tmp.adjustPendingRsp(rsp);
                }
                if (rsp.getInt(Tags.NumberOfRemainingSubOperations, 0) > 0) {
                    notifyMoveSCU(rsp, null);
                }
            }
        }};

    public MoveTask(QueryRetrieveScpService service,
            ActiveAssociation moveAssoc, int movePcid, Command moveRqCmd,
            Dataset moveRqData, FileInfo[][] fileInfo, AEDTO aeData,
            String moveDest) throws DcmServiceException {
        this.service = service;
        this.log = service.getLog();
        this.moveAssoc = moveAssoc;
        this.movePcid = movePcid;
        this.moveRqData = moveRqData;
        this.aeData = aeData;
        this.moveDest = moveDest;
        this.moveOriginatorAET = moveAssoc.getAssociation().getCallingAET();
        this.moveCalledAET = moveAssoc.getAssociation().getCalledAET();
        this.perfMon = service.getMoveScp().getPerfMonDelegate();
        this.priority = moveRqCmd.getInt(Tags.Priority, Command.MEDIUM);
        this.msgID = moveRqCmd.getMessageID();
        this.sopClassUID = moveRqCmd.getAffectedSOPClassUID();
        this.total = fileInfo.length;
        this.remaining = total;
        this.retrieveInfo = new RetrieveInfo(service, fileInfo);
        this.remainingIUIDs = retrieveInfo.getAvailableIUIDs();
        this.failedIUIDs = retrieveInfo.getNotAvailableIUIDs();
        this.failedOnlineRetrieveIUIDs = new HashSet<String>();
        this.failedNearlineRetrieveIUIDs = new HashSet<String>();
        moveAssoc.addCancelListener(msgID, cancelListener);
        this.pixQueryResults = service.isAdjustPatientIDOnRetrieval()
                ? new HashMap<PIDWithIssuer, Set<PIDWithIssuer>>()
                : null;
        
        if (service.getRetrieveRspStatusForNoMatchingInstanceToRetrieve() != Status.Success)
        	findInvalidUIDsInRequest(moveRqData, fileInfo);
    }

    private ActiveAssociation openAssociation() throws Exception {
        AssociationFactory asf = AssociationFactory.getInstance();
        Association a = asf.newRequestor(
                service.createSocket(moveCalledAET, aeData));
        a.setAcTimeout(service.getAcTimeout());
        a.setDimseTimeout(service.getDimseTimeout());
        a.setSoCloseDelay(service.getSoCloseDelay());
        a.putProperty("MoveAssociation", moveAssoc);
        AAssociateRQ rq = asf.newAAssociateRQ();
        rq.setCalledAET(moveDest);
        rq.setCallingAET(moveAssoc.getAssociation().getCalledAET());
        int maxOpsInvoked = service.getMaxStoreOpsInvoked();
        if (maxOpsInvoked != 1) {
            rq.setAsyncOpsWindow(asf.newAsyncOpsWindow(maxOpsInvoked, 1));
        }
        retrieveInfo.addPresContext(rq,
                service.notDecompressTsuidSet(),
                service.isSendWithDefaultTransferSyntax(moveDest),
                service.isOfferNoPixelData(moveDest),
                service.isOfferNoPixelDataDeflate(moveDest));

        perfMon.assocEstStart(a, Command.C_STORE_RQ);
        PDU pdu = a.connect(rq);
        perfMon.assocEstEnd(a, Command.C_STORE_RQ);
        if (!(pdu instanceof AAssociateAC)) {
            throw new IOException("Association not accepted by "
                    + moveDest + ":\n" + pdu);
        }
        ActiveAssociation storeAssoc = asf.newActiveAssociation(a, null);
        storeAssoc.start();
        if (a.countAcceptedPresContext() == 0) {
            try {
                storeAssoc.release(false);
            } catch (Exception e) {
                log.info("Exception during release of assocation to "
                        + moveDest, e);
            }
            throw new IOException("No Presentation Context for Storage accepted by "
                    + moveDest);
        }
        removeInstancesOfUnsupportedStorageSOPClasses(a);
        return storeAssoc;
    }

    private void removeInstancesOfUnsupportedStorageSOPClasses(Association a) {
        Iterator<String> it = retrieveInfo.getCUIDs();
        String cuid;
        Collection<String> iuids;
        while (it.hasNext()) {
            cuid = it.next();
            if (a.listAcceptedPresContext(cuid).isEmpty()) {
                iuids = retrieveInfo.removeInstancesOfClass(cuid);
                it.remove(); // Use Iterator itself to remove the current
                                // item to avoid ConcurrentModificationException
                noPresentationContext(cuid, iuids, "No Presentation Context for "
                        + QueryRetrieveScpService.uidDict.toString(cuid)
                        + " accepted by " + moveDest
                        + "\n\tCannot send " + iuids.size()
                        + " instances of this class");
            } else {
                Set<String> tsuids = new HashSet<String>(
                        service.notDecompressTsuidSet());
                tsuids.retainAll(retrieveInfo.getTransferSyntaxesOfClass(cuid));
                for (String tsuid : tsuids) {
                    if (a.getAcceptedPresContext(cuid, tsuid) == null
                            && a.getAcceptedPresContext(cuid, UIDs.NoPixelData) == null
                            && a.getAcceptedPresContext(cuid, UIDs.NoPixelDataDeflate) == null) {
                        iuids = retrieveInfo.removeLocalFilesOfClassWithTransferSyntax(cuid, tsuid);
                        if (!iuids.isEmpty()) {
                            noPresentationContext(cuid, iuids, "No Presentation Context for "
                                    + QueryRetrieveScpService.uidDict.toString(cuid)
                                    + " with " + QueryRetrieveScpService.uidDict.toString(tsuid)
                                    + " accepted by " + moveDest
                                    + "\n\tCannot send " + iuids.size()
                                    + " instances of this class");
                        }
                    }
                }
            }
        }
    }

    private void noPresentationContext(String cuid, Collection<String> iuids,
            final String prompt) {
        if (!service.isIgnorableSOPClass(cuid, moveDest)) {
            failedIUIDs.addAll(iuids);
            log.warn(prompt);
        } else {
            completed += iuids.size();
            log.info(prompt);
        }
        remainingIUIDs.removeAll(iuids);
        remaining = remainingIUIDs.size();
    }

    public void run() {
        service.scheduleSendPendingCMoveRsp(sendPendingRsp);
        try {
            Set<String> localUIDs = retrieveInfo.removeLocalIUIDs();
            boolean updateLocalUIDs = false;
            while (!canceled && retrieveInfo.nextMoveForward()) {
                String retrieveAET = retrieveInfo.getMoveForwardAET();
                Collection<String> iuids = retrieveInfo.getMoveForwardUIDs();
                directForwarding = !retrieveInfo.isExternalRetrieveAET()
                        || service.isDirectForwarding(retrieveAET, moveDest);
                String moveDest1 = directForwarding ? moveDest 
                        : service.getLocalStorageAET();
                String callingAET = (directForwarding
                        && service.isForwardAsMoveOriginator()) 
                            ? moveOriginatorAET
                            : moveCalledAET;
                moveScu = new MoveScu(service, moveCalledAET, callingAET,
                        retrieveAET, remaining);
                if (iuids.size() == total) {
                    if (log.isDebugEnabled())
                        log.debug("Forward original Move RQ to " + retrieveAET);
                    MoveScu.addStudySeriesIUIDs(moveRqData,
                            retrieveInfo.getStudyIUIDs(),
                            retrieveInfo.getSeriesIUIDs());
                    moveScu.forwardMoveRQ(movePcid, msgID, priority,
                            moveDest1, moveRqData, iuids);
                } else {
                    moveScu.splitAndForwardMoveRQ(movePcid, msgID, priority,
                            moveDest1, retrieveInfo.getStudyIUIDs(),
                            retrieveInfo.getSeriesIUIDs(), iuids);
                }
                if (directForwarding) {
                    completed += moveScu.completed();
                    warnings += moveScu.warnings();
                    failedIUIDs.addAll(moveScu.failedIUIDs());
                    remainingIUIDs.removeAll(iuids);
                    remaining = canceled ? moveScu.remaining() 
                            : remainingIUIDs.size();
                } else {
                    if (moveScu.completed() > 0 || moveScu.warnings() > 0) {
                        updateLocalUIDs = true;
                    }
                }
                moveScu = null;
            }
            if (!canceled) {
                try {
                    if (updateLocalUIDs) {
                        RetrieveCmd cmd = RetrieveCmd.create(moveRqData);
                        cmd.setFetchSize(service.getFetchSize());
                        FileInfo[][] fileInfo = cmd.getFileInfos();
                        retrieveInfo = new RetrieveInfo(service, fileInfo);
                        localUIDs = retrieveInfo.removeLocalIUIDs();
                    }
                    if (!localUIDs.isEmpty()) {
                        service.prefetchTars(retrieveInfo.getLocalFiles());
                        service.updateAttributes(retrieveInfo.getLocalFiles());
                        ActiveAssociation storeAssoc = openAssociation();
                        retrieveLocal(storeAssoc);
                    }
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
            }
        } finally {
            sendPendingRsp.cancel();
        }
        if (!canceled) {
            failedIUIDs.addAll(remainingIUIDs);
            remainingIUIDs.clear();
            remaining = 0;
        }
        notifyMoveSCU(makeMoveRsp(status()),
                service.makeRetrieveRspIdentifier(failedIUIDs));
    }

    private void retrieveLocal(ActiveAssociation storeAssoc) {
        this.stgCmtActionInfo = DcmObjectFactory.getInstance().newDataset();
        this.refSOPSeq = stgCmtActionInfo.putSQ(Tags.RefSOPSeq);
        Set<StudyInstanceUIDAndDirPath> studyInfos = 
                new HashSet<StudyInstanceUIDAndDirPath>();
        Association a = storeAssoc.getAssociation();
        Collection<List<FileInfo>> localFiles = retrieveInfo.getLocalFiles();

        makeCStoreRQs(localFiles, storeAssoc, studyInfos, failedOnlineRetrieveIUIDs);
        
        // Failover to nearline retrieve in a single batch for all instances that failed during online retrievals 
        if (!failedOnlineRetrieveIUIDs.isEmpty()) {
        	Collection<List<FileInfo>> nearlineFilesToRetrieve = new ArrayList<List<FileInfo>>();
        	for (Iterator<String> iter = failedOnlineRetrieveIUIDs.iterator(); iter.hasNext();) {
        		String iuid = iter.next();
        		List<FileInfo> nearlineFiles = retrieveInfo.getNearlineFiles(iuid);
        		if (nearlineFiles.isEmpty()) {
        			if (log.isDebugEnabled()) {
        				log.debug("instance failed online retrieval has no nearline location to fail over to, iuid " + iuid);
        			}
        			failedIUIDs.add(iuid);
                    remainingIUIDs.remove(iuid);
                    --remaining;
        		} else {
        			if (log.isDebugEnabled()) {
        				log.debug("retrieve failing over from online to nearline storage, iuid " + iuid);
        			}
        			nearlineFilesToRetrieve.add(nearlineFiles);
        		}
        	}
        	if (!nearlineFilesToRetrieve.isEmpty()) {
        		try {
        			service.prefetchTars(nearlineFilesToRetrieve);
        			makeCStoreRQs(nearlineFilesToRetrieve, storeAssoc, studyInfos, failedNearlineRetrieveIUIDs);
        		} catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
        	}
        }
        
        if (a.getState() == Association.ASSOCIATION_ESTABLISHED) {
            try {
            	perfMon.assocRelStart(a, Command.C_STORE_RQ);
            	
                storeAssoc.release(true);
                
                perfMon.assocRelEnd(a, Command.C_STORE_RQ);
                
                // workaround to ensure that last STORE-RSP is processed before
                // finally MOVE-RSP is sent
                Thread.sleep(10);
            } catch (Exception e) {
                log.error("Exception during release:", e);
            }
        } else {
            try {
                a.abort(AssociationFactory.getInstance().newAAbort(
                        AAbort.SERVICE_PROVIDER, AAbort.REASON_NOT_SPECIFIED));
            } catch (IOException ignore) {
            }
        }
        if (!successfulTransferred.isEmpty()) {
            service.logInstancesSent(moveAssoc.getAssociation(), a, successfulTransferred);
            service.onInstancesRetrieved(moveCalledAET, moveDest, stgCmtActionInfo);
        }
        service.updateStudyAccessTime(studyInfos);
    }

    private void makeCStoreRQs(Collection<List<FileInfo>> localFiles, ActiveAssociation storeAssoc, 
    		Set<StudyInstanceUIDAndDirPath> studyInfos, Set<String> failedStorageRetrieveIUIDs) {
        for (List<FileInfo> list : localFiles) {
            final FileInfo fileInfo = list.get(0);
            final String iuid = fileInfo.sopIUID;
            DimseListener storeScpListener = new DimseListener() {

                public void dimseReceived(Association assoc, Dimse dimse) {
                    switch (dimse.getCommand().getStatus()) {
                    case Status.Success:
                        ++completed;
                        updateStgCmtActionInfo(fileInfo);
                        successfulTransferred.add(fileInfo);
                        break;
                    case Status.CoercionOfDataElements:
                    case Status.DataSetDoesNotMatchSOPClassWarning:
                    case Status.ElementsDiscarded:
                        ++warnings;
                        updateStgCmtActionInfo(fileInfo);
                        successfulTransferred.add(fileInfo);
                        break;
                    default:
                        failedIUIDs.add(iuid);
                        break;
                    }
                    remainingIUIDs.remove(iuid);
                    --remaining;
                }
            };
            
            Dimse rq;
            try {
                rq = service.makeCStoreRQ(storeAssoc, fileInfo, aeData, 
                        priority, moveOriginatorAET, msgID, perfMon,
                        pixQueryResults);
            } catch (FileRetrieveFailedException frfe) {
            	// failed during online/nearline retrieve
            	log.error(frfe.getMessage(), frfe);
            	
            	if (fileInfo.availability == Availability.NEARLINE) {
            		failedIUIDs.add(iuid);
                    remainingIUIDs.remove(iuid);
                    --remaining;
            	} else {
            		// failed online retrieve, add to list for fail over to nearline later
            		failedStorageRetrieveIUIDs.add(iuid);
            	}
            	continue;
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                failedIUIDs.add(iuid);
                remainingIUIDs.remove(iuid);
                --remaining;
                continue;
            }
            
            try {
                perfMon.start(storeAssoc, rq, PerfCounterEnum.C_STORE_SCU_OBJ_OUT );
                perfMon.setProperty(storeAssoc, rq, PerfPropertyEnum.REQ_DIMSE, rq);
                perfMon.setProperty(storeAssoc, rq, PerfPropertyEnum.STUDY_IUID, fileInfo.studyIUID);

                storeAssoc.invoke(rq, storeScpListener);
                
                perfMon.stop(storeAssoc, rq, PerfCounterEnum.C_STORE_SCU_OBJ_OUT);
            } catch (Exception e) {
                log.error("Exception during move of " + iuid, e);
            }
            // track access on ONLINE FS
            if (fileInfo.availability == Availability.ONLINE) {
                studyInfos.add(new StudyInstanceUIDAndDirPath(fileInfo));
            }
            if (canceled || storeAssoc.getAssociation().getState() != Association.ASSOCIATION_ESTABLISHED) {
                break;
            }
        }   
    }

    private void updateStgCmtActionInfo(FileInfo fileInfo) {
        Dataset item = refSOPSeq.addNewItem();
        item.putUI(Tags.RefSOPClassUID, fileInfo.sopCUID);
        item.putUI(Tags.RefSOPInstanceUID, fileInfo.sopIUID);
    }

    private int status() {
        int status = canceled ? Status.Cancel 
                : failedIUIDs.isEmpty() ? Status.Success
                : completed == 0 && warnings == 0 
                        ? Status.UnableToPerformSuboperations
                        : Status.SubOpsOneOrMoreFailures;
        
        if (status == Status.Success && !invalidRequestedUIDs.isEmpty()) {
        	status = service.getRetrieveRspStatusForNoMatchingInstanceToRetrieve();
        	
        	warnings += invalidRequestedUIDs.size();
        }
        
        return status;
    }

    protected void notifyMoveSCU(Command moveRspCmd, Dataset moveRspData) {
        if (!moveAssocClosed) {
            try {
                if (!moveRspCmd.isPending())
                    moveAssoc.removeCancelListener(msgID);
                moveAssoc.getAssociation().write(
                        AssociationFactory.getInstance().newDimse(movePcid,
                                moveRspCmd, moveRspData));
            } catch (Exception e) {
                log.info("Failed to send Move RSP to Move Originator:", e);
                moveAssocClosed  = true;
            }
        }
    }

    private Command makeMoveRsp(int status) {
        Command rspCmd = DcmObjectFactory.getInstance().newCommand();
        rspCmd.initCMoveRSP(msgID, sopClassUID, status);
        if (remaining > 0) {
            rspCmd.putUS(Tags.NumberOfRemainingSubOperations,
                    remaining);
        } else {
            rspCmd.remove(Tags.NumberOfRemainingSubOperations);
        }
        rspCmd.putUS(Tags.NumberOfCompletedSubOperations, completed);
        rspCmd.putUS(Tags.NumberOfWarningSubOperations, warnings);
        rspCmd.putUS(Tags.NumberOfFailedSubOperations, failedIUIDs.size());
        return rspCmd;
    }

    private void findInvalidUIDsInRequest(Dataset moveRqData, FileInfo[][] fileInfo){
        String qrLevel = moveRqData.getString(Tags.QueryRetrieveLevel);
        String[] uids = null;
        Set<String> existingIDs = new HashSet<String>();
        
        if ("IMAGE".equals(qrLevel)){
        	uids = moveRqData.getStrings(Tags.SOPInstanceUID);
        	for (FileInfo[] fi : fileInfo){
        		existingIDs.add(fi[0].sopIUID);
        	}
        }
        if ("SERIES".equals(qrLevel)){
        	uids = moveRqData.getStrings(Tags.SeriesInstanceUID);
        	for (FileInfo[] fi : fileInfo){
        		existingIDs.add(fi[0].seriesIUID);
        	}
        }
        if ("STUDY".equals(qrLevel)){
        	uids = moveRqData.getStrings(Tags.StudyInstanceUID);
        	for (FileInfo[] fi : fileInfo){
        		existingIDs.add(fi[0].studyIUID);
        	}
        }
        if ("PATIENT".equals(qrLevel)){
        	uids = new String[] {moveRqData.getString(Tags.PatientID)};
        	if (fileInfo.length != 0)
        		existingIDs.add(fileInfo[0][0].patID);
        }
        
        for (String rqUID : uids){
        	if (!existingIDs.contains(rqUID))
        		invalidRequestedUIDs.add(rqUID);
        }
        
        if (!invalidRequestedUIDs.isEmpty())
        	log.warn("Found invalid UIDs in c-move: "+invalidRequestedUIDs);
    }
}