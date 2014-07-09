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

package org.dcm4chex.archive.ejb.session;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.FinderException;
import javax.ejb.ObjectNotFoundException;
import javax.ejb.RemoveException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.log4j.Logger;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.FileMetaInfo;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.net.DcmServiceException;
import org.dcm4cheri.util.StringUtils;
import org.dcm4chex.archive.common.Availability;
import org.dcm4chex.archive.common.FileStatus;
import org.dcm4chex.archive.common.PatientMatching;
import org.dcm4chex.archive.common.PublishedStudyStatus;
import org.dcm4chex.archive.common.SeriesStored;
import org.dcm4chex.archive.ejb.interfaces.ContentEditLocal;
import org.dcm4chex.archive.ejb.interfaces.FileDTO;
import org.dcm4chex.archive.ejb.interfaces.FileLocal;
import org.dcm4chex.archive.ejb.interfaces.FileLocalHome;
import org.dcm4chex.archive.ejb.interfaces.FileSystemLocal;
import org.dcm4chex.archive.ejb.interfaces.FileSystemLocalHome;
import org.dcm4chex.archive.ejb.interfaces.InstanceLocal;
import org.dcm4chex.archive.ejb.interfaces.InstanceLocalHome;
import org.dcm4chex.archive.ejb.interfaces.PatientLocal;
import org.dcm4chex.archive.ejb.interfaces.PatientLocalHome;
import org.dcm4chex.archive.ejb.interfaces.PublishedStudyLocal;
import org.dcm4chex.archive.ejb.interfaces.PublishedStudyLocalHome;
import org.dcm4chex.archive.ejb.interfaces.SeriesLocal;
import org.dcm4chex.archive.ejb.interfaces.SeriesLocalHome;
import org.dcm4chex.archive.ejb.interfaces.StorageLocal;
import org.dcm4chex.archive.ejb.interfaces.StudyLocal;
import org.dcm4chex.archive.ejb.interfaces.StudyLocalHome;
import org.dcm4chex.archive.ejb.interfaces.StudyOnFileSystemLocalHome;
import org.dcm4chex.archive.exceptions.NonUniquePatientException;
import org.dcm4chex.archive.exceptions.NonUniquePatientIDException;

/**
 * Storage Bean
 * 
 * @ejb.bean name="Storage" type="Stateless" view-type="both" 
 * 			 jndi-name="ejb/Storage"
 * @ejb.transaction-type type="Container"
 * @ejb.transaction type="Required"
 * 
 * @ejb.ejb-ref ejb-name="Patient" view-type="local" ref-name="ejb/Patient"
 * @ejb.ejb-ref ejb-name="Study"  view-type="local" ref-name="ejb/Study"
 * @ejb.ejb-ref ejb-name="Series" view-type="local" ref-name="ejb/Series"
 * @ejb.ejb-ref ejb-name="Instance" view-type="local" ref-name="ejb/Instance"
 * @ejb.ejb-ref ejb-name="File" view-type="local" ref-name="ejb/File"
 * @ejb.ejb-ref ejb-name="FileSystem" view-type="local" ref-name="ejb/FileSystem"
 * @ejb.ejb-ref ejb-name="StudyOnFileSystem" view-type="local" ref-name="ejb/StudyOnFileSystem"
 * @ejb.ejb-ref ejb-name="StudyOnFileSystem" view-type="local" ref-name="ejb/StudyOnFileSystem"
 * @ejb.ejb-ref ejb-name="PublishedStudy" view-type="local" ref-name="ejb/PublishedStudy"
 * 
 * @author <a href="mailto:gunter@tiani.com">Gunter Zeilinger </a>
 * @version $Revision: 18249 $ $Date: 2014-02-21 16:18:22 +0000 (Fri, 21 Feb 2014) $
 *  
 */
public abstract class StorageBean implements SessionBean {

    public static final int STORED = 0;
	public static final int RECEIVED = 1;

    private static Logger log = Logger.getLogger(StorageBean.class);

    private PatientLocalHome patHome;

    private StudyLocalHome studyHome;

    private SeriesLocalHome seriesHome;

    private InstanceLocalHome instHome;

    private FileLocalHome fileHome;

    private FileSystemLocalHome fileSystemHome;
    
    private StudyOnFileSystemLocalHome sofHome;
    
    private PublishedStudyLocalHome publishedStudyHome;
    
    private SessionContext sessionCtx;
    
    private static final int MAX_PK_CACHE_ENTRIES = 100;
    private static Map seriesPkCache = Collections.synchronizedMap(
     new LinkedHashMap() {
        protected boolean removeEldestEntry(Map.Entry eldest) {
           return size() > MAX_PK_CACHE_ENTRIES;
        }
    });

    public void setSessionContext(SessionContext ctx) {
        sessionCtx = ctx;
        Context jndiCtx = null;
        try {
            jndiCtx = new InitialContext();
            patHome = (PatientLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/Patient");
            studyHome = (StudyLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/Study");
            seriesHome = (SeriesLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/Series");
            instHome = (InstanceLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/Instance");
            fileHome = (FileLocalHome) jndiCtx.lookup("java:comp/env/ejb/File");
            fileSystemHome = (FileSystemLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/FileSystem");
            sofHome = (StudyOnFileSystemLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/StudyOnFileSystem");
            publishedStudyHome = (PublishedStudyLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/PublishedStudy");
        } catch (NamingException e) {
            throw new EJBException(e);
        } finally {
            if (jndiCtx != null) {
                try {
                    jndiCtx.close();
                } catch (NamingException ignore) {
                }
            }
        }
    }

    public void unsetSessionContext() {
        sessionCtx = null;
        patHome = null;
        studyHome = null;
        seriesHome = null;
        instHome = null;
        fileHome = null;
        fileSystemHome = null;
        sofHome = null;
        publishedStudyHome = null;
    }

    /**
     * @ejb.interface-method
     */
    public org.dcm4che.data.Dataset store(org.dcm4che.data.Dataset ds, String currentCallingAET,
            long fspk, java.lang.String fileid, long size, byte[] md5, byte[] origMd5, int fileStatus,
            boolean updateStudyAccessTime, boolean clearExternalRetrieveAET, boolean dontChangeReceivedStatus, PatientMatching matching) throws DcmServiceException, NonUniquePatientIDException {
    	return store(ds, currentCallingAET, fspk, fileid, size, md5, origMd5, fileStatus, updateStudyAccessTime, 
    	        clearExternalRetrieveAET, dontChangeReceivedStatus, matching, true);
    }
    /**
     * @ejb.interface-method
     */
    public org.dcm4che.data.Dataset store(org.dcm4che.data.Dataset ds, String currentCallingAET,
            long fspk, java.lang.String fileid, long size, byte[] md5, byte[] origMd5, int fileStatus,
            boolean updateStudyAccessTime, boolean clearExternalRetrieveAET, boolean dontChangeReceivedStatus, PatientMatching matching,
            boolean canRollback) throws DcmServiceException, NonUniquePatientIDException {
        FileMetaInfo fmi = ds.getFileMetaInfo();
        final String iuid = fmi.getMediaStorageSOPInstanceUID();
        final String tsuid = fmi.getTransferSyntaxUID();
        log.info("inserting instance " + fmi);
        try {
            Dataset coercedElements = DcmObjectFactory.getInstance().newDataset();
            InstanceLocal instance;
            int prevAvailability = Availability.UNAVAILABLE;
            try {
                instance = instHome.findBySopIuid(iuid);
                prevAvailability = instance.getAvailabilitySafe();
                coerceInstanceIdentity(instance, ds, coercedElements);
                if (clearExternalRetrieveAET && instance.getExternalRetrieveAET() != null) {
                    if ( instance.getExternalRetrieveAET().equals(currentCallingAET)) {
                        log.debug("CallingAET == ExternalRetrieveAET! Don't clear ExternalRetrieveAET of instance "+instance.getSopIuid());
                    } else {
                        log.info("Clear ExternalRetrieveAET of instance "+instance.getSopIuid());
                        instance.setExternalRetrieveAET(null);
                    }
                }
            } catch (ObjectNotFoundException onfe) {
                instance = instHome.create(ds,
                        getSeries(matching, ds, coercedElements));
                Collection<PublishedStudyLocal> pStudies = publishedStudyHome.findByStudyPkAndStatus(instance.getSeries().getStudy().getPk(), 
                            PublishedStudyStatus.STUDY_COMPLETE);
                for (PublishedStudyLocal pStudy : pStudies) {
                    pStudy.setStatus(PublishedStudyStatus.STUDY_CHANGED);
                }
            }
            if (fspk != -1) {
                FileSystemLocal fs = fileSystemHome.findByPrimaryKey(new Long(fspk));
                fileHome.create(fileid, tsuid, size, md5, origMd5, fileStatus, instance, fs);
                instance.setAvailability(Math.min(fs.getAvailability(), prevAvailability));
                instance.addRetrieveAET(fs.getRetrieveAET());
                if (updateStudyAccessTime) {
                    touchStudyOnFileSystem(ds.getString(Tags.StudyInstanceUID), fs);
                }
            } else {
                instance.setAvailability(
                        Availability.toInt(ds.getString(Tags.InstanceAvailability)));
                instance.setExternalRetrieveAET(ds.getString(Tags.RetrieveAET));
            }
            if (fileStatus == FileStatus.ARCHIVED)
                instance.setArchived(true);
            if (!dontChangeReceivedStatus) {
                instance.setInstanceStatus(RECEIVED);
                instance.getSeries().setSeriesStatus(RECEIVED);
            } else {
                if (log.isDebugEnabled())
                    log.debug("Dont change Received Status! instStatus:"+instance.getInstanceStatus()+
                        " seriesStatus:"+instance.getSeries().getSeriesStatus());
            }
            log.info("inserted records for instance[uid=" + iuid + "]");
            return coercedElements;
        } catch (Exception e) {
            log.warn("inserting records for instance[uid=" + iuid
                    + "] failed: " + e.getMessage());
            if (canRollback) sessionCtx.setRollbackOnly(); 
            if (e instanceof NonUniquePatientIDException) {
                throw (NonUniquePatientIDException) e;
            } else if (e instanceof DcmServiceException){
                throw (DcmServiceException) e;
            } else {
                throw new DcmServiceException(Status.ProcessingFailure, e);
            }
        }
    }

    /**
     * @ejb.interface-method
     */
    public Collection<FileDTO> getDuplicateFiles(FileDTO dto) throws FinderException {
        try {
            InstanceLocal instance = instHome.findBySopIuid(dto.getSopInstanceUID());
            return toFileDTOs(instance.getDuplicateFiles(dto.getFileSystemPk(), dto.getMd5String()));
        } catch (ObjectNotFoundException ignore) {
            return Collections.EMPTY_LIST;
        }
    }
    
    private Collection<FileDTO> toFileDTOs(Collection c) {
        Collection<FileDTO> dtos = new ArrayList<FileDTO>();
        for (FileLocal f : (Collection<FileLocal>) c) {
            dtos.add(f.getFileDTO());
        }
        return dtos;
    }

    private void touchStudyOnFileSystem(String siud, FileSystemLocal fs)
            throws FinderException, CreateException {
        String dirPath = fs.getDirectoryPath();
        try {
            sofHome.findByStudyAndFileSystem(siud, dirPath).touch();
        } catch (ObjectNotFoundException e) {
            try {
                sofHome.create(studyHome.findByStudyIuid(siud), fs);
            } catch (Exception ignore) {
                log.info("Create StudyOnFS failed! Check if concurrent create.", ignore);
                sofHome.findByStudyAndFileSystem(siud, dirPath).touch();
            }
        }
    }

    /**
     * @ejb.interface-method
     */
    public SeriesStored makeSeriesStored(String seriuid)
            throws FinderException {
        return makeSeriesStored(findBySeriesIuid(seriuid));
    }

    /**
     * @ejb.interface-method
     */
    public void commitSeriesStored(SeriesStored seriesStored)
            throws FinderException {
        Dataset ian = seriesStored.getIAN();
        Dataset refSeries = ian.get(Tags.RefSeriesSeq).getItem(0);
        DcmElement refSOPs = refSeries.get(Tags.RefSOPSeq);
        int numI = refSOPs.countItems();
        HashSet iuids = new HashSet(numI * 4 / 3 + 1);
        for (int i = 0; i < numI; i++) {
            iuids.add(refSOPs.getItem(i).getString(Tags.RefSOPInstanceUID));
        }
        String seriuid = refSeries.getString(Tags.SeriesInstanceUID);
        SeriesLocal series = findBySeriesIuid(seriuid);
        Collection c = series.getInstances();
        int remaining = 0;
        for (Iterator iter = c.iterator(); iter.hasNext();) {
            InstanceLocal inst = (InstanceLocal) iter.next();
            if (inst.getInstanceStatus() != RECEIVED) {
                continue;
            }
            if (iuids.remove(inst.getSopIuid())) {
                inst.setInstanceStatus(STORED);
            } else {
                ++remaining;
            }
        }
        if (remaining == 0) {
            series.setSeriesStatus(STORED);
        }
   }
    
    /**
     * @ejb.interface-method
     */
    public Collection getPksOfPendingSeries(Timestamp updatedBefore)
            throws FinderException {
        return seriesHome.getSeriesPksWithStatusAndUpdatedBefore(
                RECEIVED, updatedBefore);
    }

    /**
     * @ejb.interface-method
     */
    public Collection<Long> getPksOfPendingSeries(Map<String, Long> delays)
            throws FinderException {
        Long minDelay = null;
        for (Long delay : delays.values()) {
            if (minDelay == null || delay < minDelay) 
                minDelay = delay;
        }
        long ts = System.currentTimeMillis();
        Timestamp updatedBefore = new Timestamp(ts - minDelay);
        Collection<SeriesLocal> c = seriesHome.getSeriesWithStatusAndUpdatedBefore(RECEIVED, updatedBefore);
        String aet;
        Long delay;
        ArrayList<Long> pks = new ArrayList<Long>();
        for (SeriesLocal s: c) {
            aet = s.getSourceAET();
            delay = delays.get(aet);
            if (delay == null) {
                delay = delays.get(null);
                if (delay == null) {
                    log.warn("No default delay and delay for sourceAET "+aet+" is not configured! Skipped!");
                    continue;
                }
            }
            if (s.getUpdatedTime().getTime() < ts - delay) {
                pks.add(s.getPk());
            }
        }
        return pks;
    }

    /**
     * @ejb.interface-method
     */
    public SeriesStored makeSeriesStored(Long seriesPk, Timestamp updatedBefore)
            throws FinderException {
        SeriesLocal series = seriesHome.findByPrimaryKey(seriesPk);
        Timestamp lastUpdated = series.getMaxUpdatedTimeOfSeriesRelatedInstances();
        return (lastUpdated != null && lastUpdated.before(updatedBefore))
                ? makeSeriesStored(series) : null;
    }

    private SeriesStored makeSeriesStored(SeriesLocal series)
            throws FinderException {
        StudyLocal study = series.getStudy();
        PatientLocal pat = study.getPatient();
        if ( pat == null ) {
            log.warn("Failed: SeriesStored for series "+series.getSeriesIuid()+" (pk="+series.getPk()+
                    ") Reason: Missing reference to a patient! Study iuid:"+study.getStudyIuid());
            return null;
        }
        UpdateDerivedFieldsUtils.updateDerivedFieldsOf(series);
        UpdateDerivedFieldsUtils.updateDerivedFieldsOf(study);
        Dataset ian = DcmObjectFactory.getInstance().newDataset();
        ian.putUI(Tags.StudyInstanceUID, study.getStudyIuid());
        Dataset refSeries = ian.putSQ(Tags.RefSeriesSeq).addNewItem();
        DcmElement refSOPs = refSeries.putSQ(Tags.RefSOPSeq);
        refSeries.putUI(Tags.SeriesInstanceUID, series.getSeriesIuid());
        HashSet commonRetrieveAETs = null;
        Collection c = series.getInstances();
        String extRetrieveAET = null;
        boolean extRetrieveFlag = true;
        boolean archived = true;
        for (Iterator iter = c.iterator(); iter.hasNext();) {
            InstanceLocal inst = (InstanceLocal) iter.next();
            if (inst.getInstanceStatus() != RECEIVED) {
                continue;
            }
            String[] retrieveAETs = StringUtils.split(
                    inst.getRetrieveAETs(), '\\');
            if ( retrieveAETs == null ) {
                log.debug("Use SeriesStored without local file for series "+series.getSeriesIuid()+" (pk="+series.getPk()+
                        ") Reason: Found Instance with status RECEIVED but without Retrieve AET! iuid:"+inst.getSopIuid());
                if (commonRetrieveAETs == null)
                    commonRetrieveAETs = new HashSet();
            } else if (commonRetrieveAETs == null) {
                commonRetrieveAETs = new HashSet();
                commonRetrieveAETs.addAll(Arrays.asList(retrieveAETs));
            } else {
                commonRetrieveAETs.retainAll(Arrays.asList(retrieveAETs));
            }
            if (extRetrieveFlag) {
                if (inst.getExternalRetrieveAET() == null) {
                    extRetrieveFlag = false;
                } else if (extRetrieveAET == null) {
                    extRetrieveAET = inst.getExternalRetrieveAET();
                } else if (!extRetrieveAET.equals(inst.getExternalRetrieveAET())) {
                    extRetrieveFlag = false;
                }
            }
            archived &= inst.getArchived();
            Dataset refSOP = refSOPs.addNewItem();           
            refSOP.putUI(Tags.RefSOPClassUID, inst.getSopCuid());
            refSOP.putUI(Tags.RefSOPInstanceUID, inst.getSopIuid());
            refSOP.putAE(Tags.RetrieveAET, retrieveAETs);
            refSOP.putCS(Tags.InstanceAvailability,
                    Availability.toString(inst.getAvailabilitySafe()));
        }
        if (commonRetrieveAETs == null) {
            return null;
        }
        Dataset patAttrs = pat.getAttributes(false);
        Dataset studyAttrs = study.getAttributes(false);
        Dataset seriesAttrs = series.getAttributes(false);
        Dataset pps = seriesAttrs.getItem(Tags.RefPPSSeq);
        DcmElement refPPSSeq = ian.putSQ(Tags.RefPPSSeq);
        if (pps != null) {
            if (!pps.contains(Tags.PerformedWorkitemCodeSeq)) {
                pps.putSQ(Tags.PerformedWorkitemCodeSeq);
            }
            refPPSSeq.addItem(pps);
        }
        SeriesStored seriesStored = new SeriesStored(series.getSourceAET(),
                commonRetrieveAETs.isEmpty() ? null
                        : (String) commonRetrieveAETs.iterator().next(),
                        extRetrieveFlag ? extRetrieveAET : null, archived,
                patAttrs, studyAttrs, seriesAttrs, ian);
        return seriesStored;
    }

    /**
     * @ejb.interface-method
     */
    public void updateDerivedStudyAndSeriesFields(String seriuid)
            throws FinderException {
        SeriesLocal series = findBySeriesIuid(seriuid);
        UpdateDerivedFieldsUtils.updateDerivedFieldsOf(series);
        UpdateDerivedFieldsUtils.updateDerivedFieldsOf(series.getStudy());
    }

    /**
     * @ejb.interface-method
     */
    public void storeFile(java.lang.String iuid, java.lang.String tsuid,
            java.lang.String dirpath, java.lang.String fileid,
            int size, byte[] md5, byte[] origMd5, int status)
            throws CreateException, FinderException
    {
        FileSystemLocal fs = fileSystemHome.findByDirectoryPath(dirpath);
        InstanceLocal instance = instHome.findBySopIuid(iuid);
        fileHome.create(fileid, tsuid, size, md5, origMd5, status, instance, fs);    	
    }

    private SeriesLocal getSeries(PatientMatching matching, Dataset ds,
            Dataset coercedElements) throws Exception {
        final String uid = ds.getString(Tags.SeriesInstanceUID);
        SeriesLocal series;
        try {
            series = findBySeriesIuid(uid);
        } catch (ObjectNotFoundException onfe) {
            try {
                return seriesHome.create(ds,
                        getStudy(matching, ds, coercedElements));
            } catch (CreateException e1) {
                // check if Series record was inserted by concurrent thread
                try {
                    series = findBySeriesIuid(uid);
                } catch (Exception e2) {
                    throw e1;
                }
            }
        }
        coerceSeriesIdentity(series, ds, coercedElements);
        return series;
    }

    private StudyLocal getStudy(PatientMatching matching, Dataset ds,
            Dataset coercedElements) throws Exception {
        final String uid = ds.getString(Tags.StudyInstanceUID);
        StudyLocal study;
        try {
            study = studyHome.findByStudyIuid(uid);
        } catch (ObjectNotFoundException onfe) {
            try {
                return studyHome.create(ds,
                        getPatient(matching, ds, coercedElements));
            } catch (CreateException e1) {
                // check if Study record was inserted by concurrent thread
                try {
                    study = studyHome.findByStudyIuid(uid);
                } catch (Exception e2) {
                    throw e1;
                }
            }
        }
        coerceStudyIdentity(study, ds, coercedElements);
        return study;
    }

    private PatientLocal getPatient(PatientMatching matching, Dataset ds,
            Dataset coercedElements) throws Exception {
        PatientLocal pat;
        try {
            pat = patHome.selectPatient(ds, matching, true);
        } catch (ObjectNotFoundException onfe) {
            try {
                 pat = ((StorageLocal)sessionCtx.getEJBLocalObject()).createPatient(ds);
                // Check if patient record was also inserted by concurrent thread
                try {
                    return patHome.selectPatient(ds, matching, true);
                } catch (NonUniquePatientException nupe) {
                    ((StorageLocal)sessionCtx.getEJBLocalObject()).deletePatient(pat);
                    pat = patHome.selectPatient(ds, matching, true);
                } catch (ObjectNotFoundException onfe2) {
                    // Just inserted Patient not found because of missing value
                    // of attribute configured as required for Patient Matching
                    return pat;
                }
             } catch (CreateException ce) {
                // Check if patient record was inserted by concurrent thread
                // with unique index on (pat_id, pat_id_issuer)
                 try {
                     pat = patHome.selectPatient(ds, matching, true);
                 } catch (ObjectNotFoundException onfe2) {
                     throw ce;
                 }
            }
        } catch (NonUniquePatientException nupe) {
            return patHome.create(ds);
        }
        coercePatientIdentity(pat, ds, coercedElements);
        return pat;
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="RequiresNew"
     */
    public PatientLocal createPatient(Dataset ds) throws CreateException {
        return patHome.create(ds);
    }
    /**
     * @ejb.interface-method
     * @ejb.transaction type="RequiresNew"
     */
    public void deletePatient( PatientLocal pat ) throws javax.ejb.RemoveException {
        patHome.remove(pat);
    }
    private void coercePatientIdentity(PatientLocal patient, Dataset ds,
            Dataset coercedElements) throws DcmServiceException, CreateException {
        patient.coerceAttributes(ds, coercedElements);
    }

    private void coerceStudyIdentity(StudyLocal study, Dataset ds,
            Dataset coercedElements) throws Exception {
        coercePatientIdentity(getPatient(study, ds), ds, coercedElements);
        study.coerceAttributes(ds, coercedElements);
    }

    private PatientLocal getPatient(StudyLocal study, Dataset ds)
            throws Exception{
        String pid = ds.getString(Tags.PatientID);
        String issuer = ds.getString(Tags.IssuerOfPatientID);
        PatientLocal priorPat = study.getPatient();
        if (priorPat.getIssuerOfPatientId() == null && issuer != null) {
            try {
                PatientLocal dominantPat =
                        patHome.selectPatient(pid, issuer);
                if (!dominantPat.isIdentical(priorPat)) {
                    log.info("Detect duplicate Patient Record: "
                            + dominantPat.asString());
                    dominantPat.getStudies().addAll(priorPat.getStudies());
                    dominantPat.getMpps().addAll(priorPat.getMpps());
                    dominantPat.getMwlItems().addAll(priorPat.getMwlItems());
                    dominantPat.getGsps().addAll(priorPat.getGsps());
                    dominantPat.getGppps().addAll(priorPat.getGppps());
                    dominantPat.getUPS().addAll(priorPat.getUPS());
                    dominantPat.getMerged().addAll(priorPat.getMerged());
                    priorPat.remove();
                    return dominantPat;
                }
            } catch (ObjectNotFoundException e) {}
        }
        return priorPat;
    }

    private void coerceSeriesIdentity(SeriesLocal series, Dataset ds,
            Dataset coercedElements) throws Exception {
        coerceStudyIdentity(series.getStudy(), ds, coercedElements);
        series.coerceAttributes(ds, coercedElements);
    }

    private void coerceInstanceIdentity(InstanceLocal instance, Dataset ds,
            Dataset coercedElements) throws Exception {
        coerceSeriesIdentity(instance.getSeries(), ds, coercedElements);
        instance.coerceAttributes(ds, coercedElements);
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="NotSupported"
     */
    public void commit(String iuid) throws FinderException {
        instHome.findBySopIuid(iuid).setCommitment(true);
    }

    /**
     * @ejb.interface-method
     */
    public void commited(Dataset stgCmtResult) throws FinderException {
        DcmElement refSOPSeq = stgCmtResult.get(Tags.RefSOPSeq);
        if (refSOPSeq == null) return;
        HashSet seriesSet = new HashSet();
        HashSet studySet = new HashSet();
        final String aet0 = stgCmtResult.getString(Tags.RetrieveAET);
        for (int i = 0, n = refSOPSeq.countItems(); i < n; ++i) {
            final Dataset refSOP = refSOPSeq.getItem(i);
            final String iuid = refSOP.getString(Tags.RefSOPInstanceUID);
            final String aet = refSOP.getString(Tags.RetrieveAET, aet0);
            if (iuid != null && aet != null)
                commited(seriesSet, studySet, iuid, aet);
        }
        for (Iterator series = seriesSet.iterator(); series.hasNext();) {
            final SeriesLocal ser = findBySeriesIuid((String) series.next());
            ser.updateExternalRetrieveAET();
        }
        for (Iterator studies = studySet.iterator(); studies.hasNext();) {
            final StudyLocal study = studyHome.findByStudyIuid((String) studies.next());
            study.updateExternalRetrieveAET();
        }
    }

    private void commited(HashSet seriesSet, HashSet studySet,
            final String iuid, final String aet) throws FinderException {
        InstanceLocal inst = instHome.findBySopIuid(iuid);
        inst.setExternalRetrieveAET(aet);
        SeriesLocal series = inst.getSeries();
        seriesSet.add(series.getSeriesIuid());
        StudyLocal study = series.getStudy();
        studySet.add(study.getStudyIuid());
    }

    /**
     * @ejb.interface-method
     */
    public int numberOfStudyRelatedInstances(String iuid) {
        try {
            StudyLocal study = studyHome.findByStudyIuid(iuid);
            return study.getNumberOfStudyRelatedInstances();
        } catch (ObjectNotFoundException onfe) {
            return -1;
        } catch (FinderException e) {
            throw new EJBException(e);
        }
    }

    /**
     * @ejb.interface-method
     */
    public boolean studyExists(String iuid) {
        try {
            studyHome.findByStudyIuid(iuid);
            return true;
        } catch (ObjectNotFoundException onfe) {
            return false;
        } catch (FinderException e) {
            throw new EJBException(e);
        }
    }

    /**
     * @ejb.interface-method
     */
    public boolean instanceExists(String iuid) {
        try {
            instHome.findBySopIuid(iuid);
            return true;
        } catch (ObjectNotFoundException onfe) {
            return false;
        } catch (FinderException e) {
            throw new EJBException(e);
        }
    }
    
    /**
     * @ejb.interface-method
     */
    public void deleteInstances(String[] iuids, boolean deleteSeries, 
            boolean deleteStudy) 
            throws FinderException, EJBException, RemoveException
    {
        for (int i = 0; i < iuids.length; i++)
        {
            InstanceLocal inst = instHome.findBySopIuid(iuids[i]);
            SeriesLocal series = inst.getSeries();
            StudyLocal study = series.getStudy();
            inst.remove();
            UpdateDerivedFieldsUtils.updateDerivedFieldsOf(series);
            if (deleteSeries && series.getNumberOfSeriesRelatedInstances() == 0)
                series.remove();	    	
            UpdateDerivedFieldsUtils.updateDerivedFieldsOf(study);
            markPublishedStudy(study, study.getNumberOfStudyRelatedSeries() == 0);
            if (deleteStudy && study.getNumberOfStudyRelatedSeries() == 0) {
                study.remove();
            }
        }
    }

    private SeriesLocal findBySeriesIuid(String uid)
            throws javax.ejb.FinderException {
        Long pk = (Long)seriesPkCache.get(uid);
        if (pk != null) {
            try { 
                return seriesHome.findByPrimaryKey(pk);
            } catch ( ObjectNotFoundException x ) {
                log.warn("Series "+uid+" not found with cached pk! Cache entry removed!");
                seriesPkCache.remove(uid);
            }
        }
        SeriesLocal ser = seriesHome.findBySeriesIuid(uid);
        seriesPkCache.put(uid, ser.getPk());
        return ser;
    }
    
    /**
     * @ejb.interface-method
     * @ejb.transaction type="NotSupported"
     */
    public void removeFromSeriesPkCache(String uid){
        seriesPkCache.remove(uid);
    }
    
    /**
     * @ejb.interface-method
     */
    public Dataset getPatientByIDWithIssuer(String pid, String issuer)
            throws FinderException{
        return patHome.findByPatientIdWithIssuer(pid, issuer)
                .getAttributes(false);
    }
    
    /**
     * @ejb.interface-method
     */
    public List<String> getSopIuidsForRejectionNote(Dataset rejNote, String srcAet)
            throws FinderException{
        List<String> iuids = new ArrayList<String>();
        DcmElement sq = rejNote.get(Tags.CurrentRequestedProcedureEvidenceSeq);
        Dataset seriesItem;
        SeriesLocal series;
        String seriesIuid;
        for (int i = 0, iLen = sq.countItems(); i < iLen; i++) {
            DcmElement refSerSq = sq.getItem(i).get(Tags.RefSeriesSeq);
            for (int j = 0, jLen = refSerSq.countItems(); j < jLen; j++) {
                seriesItem = refSerSq.getItem(j);
                seriesIuid = seriesItem.getString(Tags.SeriesInstanceUID);
                try {
                    series = seriesHome.findBySeriesIuid(seriesIuid);
                    if (!srcAet.equals(series.getSourceAET()) ) {
                        log.info("CallingAET ("+srcAet+") of RejectionNote request is different to SourceAET ("+
                                series.getSourceAET()+") of Series "+seriesIuid+"! Ignore this series in RejectionNote!");
                    } else {
                        DcmElement refSopSq = seriesItem.get(Tags.RefSOPSeq);
                        for (int k = 0, kLen = refSopSq.countItems(); k < kLen; k++) {
                            iuids.add(refSopSq.getItem(k).getString(Tags.RefSOPInstanceUID));
                        }
                    }
                } catch (FinderException x) {
                    log.warn("Series "+seriesIuid+" not found. Ignore this series in RejectionNote!");
                }
            }
        }
        return iuids;
    }

    private void markPublishedStudy(StudyLocal study, boolean deleted){
        try {
            if (deleted) {
                contentEdit().markPublishedStudyDeleted(study.getStudyIuid());
            } else {
                contentEdit().markPublishedStudyChanged(study.getStudyIuid());
            }
        } catch (Exception ignore) {}
    }

    /**
     * @ejb.ejb-ref ejb-name="ContentEdit" view-type="local" ref-name="ejb/ContentEdit"
     */
    protected abstract ContentEditLocal contentEdit();

}


