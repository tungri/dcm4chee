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
package org.dcm4chex.archive.ejb.session;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

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
import org.dcm4che.dict.Tags;
import org.dcm4chex.archive.common.Availability;
import org.dcm4chex.archive.common.DatasetUtils;
import org.dcm4chex.archive.common.DeleteStudyOrder;
import org.dcm4chex.archive.common.DeleteStudyOrdersAndMaxAccessTime;
import org.dcm4chex.archive.common.FileStatus;
import org.dcm4chex.archive.common.FileSystemStatus;
import org.dcm4chex.archive.common.IANAndPatientID;
import org.dcm4chex.archive.common.SeriesStored;
import org.dcm4chex.archive.ejb.interfaces.ContentEditLocal;
import org.dcm4chex.archive.ejb.interfaces.ContentEditLocalHome;
import org.dcm4chex.archive.ejb.interfaces.FileDTO;
import org.dcm4chex.archive.ejb.interfaces.FileLocal;
import org.dcm4chex.archive.ejb.interfaces.FileLocalHome;
import org.dcm4chex.archive.ejb.interfaces.FileSystemDTO;
import org.dcm4chex.archive.ejb.interfaces.FileSystemLocal;
import org.dcm4chex.archive.ejb.interfaces.FileSystemLocalHome;
import org.dcm4chex.archive.ejb.interfaces.FileSystemMgt2Local;
import org.dcm4chex.archive.ejb.interfaces.InstanceLocal;
import org.dcm4chex.archive.ejb.interfaces.InstanceLocalHome;
import org.dcm4chex.archive.ejb.interfaces.PatientLocal;
import org.dcm4chex.archive.ejb.interfaces.PrivateFileLocal;
import org.dcm4chex.archive.ejb.interfaces.PrivateFileLocalHome;
import org.dcm4chex.archive.ejb.interfaces.PrivateManagerLocal;
import org.dcm4chex.archive.ejb.interfaces.SeriesLocal;
import org.dcm4chex.archive.ejb.interfaces.SeriesLocalHome;
import org.dcm4chex.archive.ejb.interfaces.StudyLocal;
import org.dcm4chex.archive.ejb.interfaces.StudyLocalHome;
import org.dcm4chex.archive.ejb.interfaces.StudyOnFileSystemLocal;
import org.dcm4chex.archive.ejb.interfaces.StudyOnFileSystemLocalHome;
import org.dcm4chex.archive.exceptions.ConcurrentStudyStorageException;
import org.dcm4chex.archive.exceptions.NoSuchSeriesException;
import org.dcm4chex.archive.exceptions.NoSuchStudyException;

/**
 * @ejb.bean name="FileSystemMgt2" type="Stateless" view-type="both"
 *     jndi-name="ejb/FileSystemMgt2"
 * @ejb.transaction-type type="Container"
 * @ejb.transaction type="Required"
 * 
 * @ejb.ejb-ref ejb-name="FileSystem" view-type="local"
 *              ref-name="ejb/FileSystem"
 * @ejb.ejb-ref ejb-name="File" view-type="local" ref-name="ejb/File"
 * @ejb.ejb-ref ejb-name="PrivateFile" view-type="local"
 *              ref-name="ejb/PrivateFile"
 * @ejb.ejb-ref ejb-name="Study" ref-name="ejb/Study" view-type="local"
 * @ejb.ejb-ref ejb-name="Series" ref-name="ejb/Series" view-type="local"
 * @ejb.ejb-ref ejb-name="Instance" ref-name="ejb/Instance" view-type="local"
 * @ejb.ejb-ref ejb-name="StudyOnFileSystem" ref-name="ejb/StudyOnFileSystem"
 *              view-type="local"
 * 
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date$
 * @since Aug 13, 2008
 */
public abstract class FileSystemMgt2Bean implements SessionBean {

    private static Logger log = Logger.getLogger(FileSystemMgt2Bean.class);

    private static final int[] IAN_PAT_TAGS = { Tags.SpecificCharacterSet,
            Tags.PatientName, Tags.PatientID };

    private SessionContext ctx;
    private FileSystemLocalHome fileSystemHome;
    private FileLocalHome fileHome;
    private PrivateFileLocalHome privFileHome;
    private StudyLocalHome studyHome;
    private SeriesLocalHome seriesHome;
    private InstanceLocalHome instHome;
    private StudyOnFileSystemLocalHome sofHome;

    public void setSessionContext(SessionContext ctx) {
        this.ctx = ctx;
        Context jndiCtx = null;
        try {
            jndiCtx = new InitialContext();
            this.fileSystemHome = (FileSystemLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/FileSystem");
            this.fileHome = (FileLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/File");
            this.privFileHome = (PrivateFileLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/PrivateFile");
            this.studyHome = (StudyLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/Study");
            this.seriesHome = (SeriesLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/Series");
            this.instHome = (InstanceLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/Instance");
            this.sofHome = (StudyOnFileSystemLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/StudyOnFileSystem");
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
       ctx = null;
       fileSystemHome = null;
       fileHome = null;
       privFileHome = null;
       studyHome = null;
       seriesHome = null;
       instHome = null;
       sofHome = null;
    }

    /**
     * @ejb.interface-method
     */
    public FileSystemDTO addFileSystem(FileSystemDTO dto)
            throws CreateException {
        return fileSystemHome.create(dto).toDTO();
    }

    /**
     * @ejb.interface-method
     */
    public FileSystemDTO removeFileSystem(String groupID, String dirPath)
            throws FinderException, RemoveException {
    	String fn = "removeFileSystem: ";
        FileSystemLocal fs = fileSystemHome
                .findByGroupIdAndDirectoryPath(groupID, dirPath);
        FileSystemDTO dto = fs.toDTO();
        
        int numFiles = fs.countFiles();
        if ( numFiles > 0 ) {
        	throw new RemoveException("cannot remove filesystem " + fs.getDirectoryPath() + " because there are still "+ numFiles + " file records");
        }
        int numPrivFiles = fs.countPrivateFiles();
        if ( numPrivFiles > 0 ) {
        	throw new RemoveException("cannot remove filesystem" + fs.getDirectoryPath() + " because there are still " + numPrivFiles + " private file records");
        }
        removeFileSystem(fs);
        return dto;
    }

    private void removeFileSystem(FileSystemLocal fs)
            throws RemoveException, FinderException {
        FileSystemLocal next = fs.getNextFileSystem();
        if (next != null && fs.isIdentical(next)) {
            next = null;
        }
        Collection c = fs.getPreviousFileSystems();
        FileSystemLocal[] prevs = (FileSystemLocal[])
                c.toArray(new FileSystemLocal[c.size()]);
        for (int i = 0; i < prevs.length; i++) {
            prevs[i].setNextFileSystem(next);
        }
        if (fs.getStatus() == FileSystemStatus.DEF_RW && next != null
                && next.getStatus() == FileSystemStatus.RW) {
            next.setStatus(FileSystemStatus.DEF_RW);
        }
        fs.remove();
    }

    /**
     * @ejb.interface-method
     */
    public FileSystemDTO getFileSystem(long pk) throws FinderException {
        return fileSystemHome.findByPrimaryKey(new Long(pk)).toDTO();
    }

    /**
     * @ejb.interface-method
     */
    public FileSystemDTO getFileSystem(String path) throws FinderException {
        return fileSystemHome.findByDirectoryPath(path).toDTO();
    }

    /**
     * @ejb.interface-method
     */
    public FileSystemDTO getFileSystemOfGroup(String groupID, String path)
            throws FinderException {
        return toDTO(
                fileSystemHome.findByGroupIdAndDirectoryPath(groupID, path));
    }

    /**
     * @ejb.interface-method
     */
    public FileSystemDTO[] getAllFileSystems() throws FinderException {
        return toDTO(fileSystemHome.findAll());
    }

    /**
     * @ejb.interface-method
     */
    public FileSystemDTO[] getFileSystemsOfGroup(String groupId)
            throws FinderException {
        return toDTO(fileSystemHome.findByGroupId(groupId));
    }

    /**
     * @ejb.interface-method
     */
    public FileSystemDTO[] getRWFileSystemsOfGroup(String groupId)
            throws FinderException {
        return toDTO(fileSystemHome.findRWByGroupId(groupId));
    }

    /**
     * @ejb.interface-method
     */
    public FileSystemDTO getDefRWFileSystemsOfGroup(String groupId)
            throws FinderException {
        return toDTO(selectDefRWFileSystemsOfGroup(groupId));
    }

    private FileSystemLocal selectDefRWFileSystemsOfGroup(String groupId)
            throws FinderException {
        Collection c = fileSystemHome.findByGroupIdAndStatus(groupId,
                FileSystemStatus.DEF_RW);
        if (!c.isEmpty()) {
            return (FileSystemLocal)c.iterator().next();
        }
        c = fileSystemHome.findByGroupIdAndStatus(groupId, FileSystemStatus.RW);
        
        Iterator fsItr = c.iterator();
        while (fsItr.hasNext()) {
        	FileSystemLocal fs = (FileSystemLocal)fsItr.next();
        	if (fs.getAvailability() != Availability.OFFLINE && 
        			fs.getAvailability() != Availability.UNAVAILABLE) {
        		log.info("Update status of " + fs.asString() + " to RW+");
        		fs.setStatus(FileSystemStatus.DEF_RW);
        		return fs;
        	}
        }
        return null;
    }

    /**
     * @ejb.interface-method
     */
    public FileSystemDTO updateFileSystemStatus(long pk, int status)
            throws FinderException {
        return updateFileSystemStatus(
                fileSystemHome.findByPrimaryKey(new Long(pk)), status);
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="RequiresNew"
     */
    public FileSystemDTO updateFileSystemStatus(String groupID, String dirPath,
            int status) throws FinderException {
        return updateFileSystemStatus(
                fileSystemHome.findByGroupIdAndDirectoryPath(groupID, dirPath),
                status);
    }

    private FileSystemDTO updateFileSystemStatus(FileSystemLocal fs, int status)
            throws FinderException {
        if (status != fs.getStatus()) {
            if (status == FileSystemStatus.DEF_RW) {
            	int availability = fs.getAvailability(); 
            	if (availability == Availability.UNAVAILABLE || availability == Availability.OFFLINE)
            		throw new IllegalStateException("The file system is " + Availability.toString(availability) + ", and cannot be selected as the active volume");
            		
                // set status of previous default RW file system(s) to RW
                Collection c = fileSystemHome.findByGroupIdAndStatus(
                        fs.getGroupID(), FileSystemStatus.DEF_RW);
                for (Iterator iterator = c.iterator(); iterator.hasNext();) {
                    ((FileSystemLocal) iterator.next())
                            .setStatus(FileSystemStatus.RW);
                }
            }
            fs.setStatus(status);
        }
        return fs.toDTO();
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="NotSupported"
     */
    public FileSystemDTO updateFileSystemRetrieveAET(String groupID,
            String dirPath, String retrieveAET, int limit)
            throws FinderException {
        FileSystemLocal fs =
                fileSystemHome.findByGroupIdAndDirectoryPath(groupID, dirPath);
        String oldAET = fs.getRetrieveAET();
        if (!retrieveAET.equals(oldAET)) {
            fs.setRetrieveAET(retrieveAET);
            updateRetrieveAETForStudyOnFileSystem(fs, oldAET, retrieveAET, limit);
        }
        return fs.toDTO();
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="NotSupported"
     */
    public int updateFileSystemRetrieveAET(String oldAET, String newAET,
            int limit) throws FinderException {
        if (oldAET.equals(newAET)) {
            return 0;
        }
        Collection fss = fileSystemHome.findByRetrieveAET(oldAET);
        for (Iterator it = fss.iterator(); it.hasNext();) {
            FileSystemLocal fs = (FileSystemLocal) it.next();
            fs.setRetrieveAET(newAET);
            updateRetrieveAETForStudyOnFileSystem(fs, oldAET, newAET, limit);
        }
        return fss.size();
    }

    private void updateRetrieveAETForStudyOnFileSystem(FileSystemLocal fs,
            String oldAET, String newAET, int batchsize) throws FinderException {
        for (int offset = 0; ; offset += batchsize) {
            Collection studies = studyHome.findStudiesWithFilesOnFileSystem(fs,
                    offset, batchsize);
            for (Iterator it = studies.iterator(); it.hasNext();) {
                StudyLocal study = (StudyLocal) it.next();
                FileSystemMgt2Local ejb =
                    (FileSystemMgt2Local) ctx.getEJBLocalObject();
                study.updateRetrieveAETs(oldAET, newAET);
            }
            if (studies.size() < batchsize) {
                break;
            }
        }
    }

    /**
     * @ejb.interface-method
     */
    public FileSystemDTO updateAvailabilityForStudyOnFileSystem(String groupID,
            String dirPath, int availabilityOfExtRetr, int batchsize)
            throws FinderException {
        FileSystemLocal fs = fileSystemHome.findByGroupIdAndDirectoryPath(
                groupID, dirPath);
        for (int offset = 0; ; offset += batchsize) {
            Collection studies = studyHome.findStudiesWithFilesOnFileSystem(fs,
                    offset, batchsize);
            for (Iterator it = studies.iterator(); it.hasNext();) {
                StudyLocal study = (StudyLocal) it.next();
                FileSystemMgt2Local ejb =
                    (FileSystemMgt2Local) ctx.getEJBLocalObject();
                ejb.updateAvailabilityForStudy(study, availabilityOfExtRetr);
            }
            if (studies.size() < batchsize) {
                break;
            }
        }
        return fs.toDTO();
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="RequiresNew"
     */
    public boolean updateFileSystemAvailability(String groupID, String dirPath,
            int availability) throws FinderException {
        FileSystemLocal fs =
            fileSystemHome.findByGroupIdAndDirectoryPath(groupID, dirPath);
        if (fs.getAvailability() == availability)
            return false;
        fs.setAvailability(availability);
        return true;
    }

    /**
     * @ejb.interface-method view-type="local"
     * @ejb.transaction type="RequiresNew"
     */
    public boolean updateAvailabilityForStudy(StudyLocal study,
            int availabilityOfExtRetr) throws FinderException {
        Collection series = study.getSeries();
        boolean updated = false;
        boolean updateStudy = false;
        for (Iterator serit = series.iterator(); serit.hasNext();) {
            SeriesLocal ser = (SeriesLocal) serit.next();
            Collection insts = ser.getInstances();
            boolean updateSeries = false;
            for (Iterator instit = insts.iterator(); instit.hasNext();) {
                InstanceLocal inst = (InstanceLocal) instit.next();
                if (inst.updateAvailability(availabilityOfExtRetr)) {
                    updateSeries = updated = true;
                }
            }
            if (updateSeries) {
                if (ser.updateAvailability()) {
                    updateStudy = true;
                }
            }
        }
        if (updateStudy) {
            study.updateAvailability();
        }
        return updated;
    }

    /**
     * @ejb.interface-method
     */
    public FileSystemDTO linkFileSystems(String groupID, String dirPath,
            String next) throws FinderException {
        FileSystemLocal prevfs = fileSystemHome
                .findByGroupIdAndDirectoryPath(groupID, dirPath);
        FileSystemLocal nextfs = (next != null && next.length() != 0)
                ? fileSystemHome.findByGroupIdAndDirectoryPath(groupID, next)
                : null;
        prevfs.setNextFileSystem(nextfs);
        return prevfs.toDTO();
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="RequiresNew"
     */
    public FileSystemDTO addAndLinkFileSystem(FileSystemDTO dto)
            throws FinderException, CreateException {
        FileSystemLocal prev = selectDefRWFileSystemsOfGroup(dto.getGroupID());
        if (prev == null) {
            dto.setStatus(FileSystemStatus.DEF_RW);
			
            // there is no RW+ or RW file-system, how about other states (i.e. RO, PENDING)
            Collection c = fileSystemHome.findByGroupId(dto.getGroupID());
            if (!c.isEmpty()) {
            	prev = (FileSystemLocal) c.iterator().next();
            }
        }

        FileSystemLocal fs = fileSystemHome.create(dto);
        if (prev != null) {
            FileSystemLocal prev0 = prev;
            FileSystemLocal next;
            while ((next = prev.getNextFileSystem()) != null
                    && !next.isIdentical(prev0)) {
                prev = next;
            }
            prev.setNextFileSystem(fs);
            fs.setNextFileSystem(next);
        }
        return fs.toDTO();
    }

    /**
     * @ejb.interface-method
     */
    public long sizeOfFilesCreatedAfter(long pk, long after)
            throws FinderException {
        return fileSystemHome
                .sizeOfFilesCreatedAfter(new Long(pk), new Timestamp(after));
    }

    /**
     * @ejb.interface-method
     */
    public Timestamp minCreatedTimeOnFsWithFileStatus(List<String> dirPath, int status) throws FinderException {
        return fileHome.minCreatedTimeOnFsWithFileStatus(dirPath, status);
    }
    
    private static FileSystemDTO toDTO(FileSystemLocal fs) {
        return fs != null ? fs.toDTO() : null;
    }

    private static FileSystemDTO[] toDTO(Collection c) {
        FileSystemDTO[] dto = new FileSystemDTO[c.size()];
        Iterator it = c.iterator();
        for (int i = 0; i < dto.length; i++) {
            dto[i] = ((FileSystemLocal) it.next()).toDTO();
        }
        return dto;
    }

    /**
     * @ejb.interface-method
     */
    public long getStudySize(DeleteStudyOrder order) throws FinderException {
        return studyHome.selectStudySize(order.getStudyPk(), order.getFsPk());
    }

    /**
     * @ejb.interface-method
     */
    public boolean markStudyOnFSRecordForDeletion(DeleteStudyOrder order, boolean b)
            throws RemoveException, FinderException {
        StudyOnFileSystemLocal sof = sofHome.findByPrimaryKey(order.getSoFsPk());
        boolean marked = sof.getMarkedForDeletion();
        if (b) {
            if (marked) {
                logOrderInfoMsg(order,"is already marked for deletion!");
                return false;
            }
            if (sof.getAccessTime().getTime() > order.getAccessTime()) {
                logOrderInfoMsg(order, "may have updated after check of deletion constraints"
                        + " -> do not mark study for deletion.");
                return false;
            }
        } else if (!marked) {
            logOrderInfoMsg(order, "Remove of deletion mark ignored! (not marked for deletion)");
            return false;
        }
        sof.setMarkedForDeletion(b);
        return true;
    }

    /**
     * @ejb.interface-method
     */
    public boolean removeStudyOnFSRecord(DeleteStudyOrder order)
            throws RemoveException {
        StudyOnFileSystemLocal sof;
        try {
            sof = sofHome.findByPrimaryKey(order.getSoFsPk());
        } catch (FinderException x) {
            log.debug("StudyOnFSRecord already removed! pk:"+order.getSoFsPk());
            return false;
        }
        if (!sof.getMarkedForDeletion()) {
            logOrderInfoMsg(order, "is not marked for deletion -> do not remove StudyOnFSRecord!");
            return false;
        }
        sof.remove();
        return true;
    }

    /**
     * @ejb.interface-method
     */
    public void createStudyOnFSRecord(DeleteStudyOrder order)
            throws CreateException, FinderException {
        sofHome.create(studyHome.findByPrimaryKey(order.getStudyPk()),
                fileSystemHome.findByPrimaryKey(order.getFsPk()));
    }

    /**    
     * @ejb.interface-method
     */
    public DeleteStudyOrdersAndMaxAccessTime createDeleteOrdersForStudiesOnFSGroup(
            String fsGroup, long minAccessTime, long maxAccessTime, int limit,
            boolean externalRetrieveable, boolean storageNotCommited,
            boolean copyOnMedia, String copyOnFSGroup, boolean copyArchived,
            boolean copyOnReadOnlyFS) throws FinderException {
        return createDeleteOrders(sofHome.findByFSGroupAndAccessedBetween(fsGroup,
                new Timestamp(minAccessTime), new Timestamp(maxAccessTime), limit),
                externalRetrieveable, storageNotCommited, copyOnMedia,
                copyOnFSGroup, copyArchived, copyOnReadOnlyFS);
    }

    /**    
     * @ejb.interface-method
     */
    public Collection<DeleteStudyOrder> createDeleteOrdersForStudyOnFSGroup(String suid,
            String fsGroup) throws FinderException {
        Collection<StudyOnFileSystemLocal> sofs = sofHome.findByStudyIUIDAndFSGroup(suid, fsGroup);
        Collection<DeleteStudyOrder> orders = new ArrayList<DeleteStudyOrder>(sofs.size());
        for (Iterator<StudyOnFileSystemLocal> iter = sofs.iterator(); iter.hasNext();) {
            StudyOnFileSystemLocal sof = iter.next();
            StudyLocal study = sof.getStudy();
            DeleteStudyOrder deleteStudyOrder = new DeleteStudyOrder(
                    sof.getPk(), study.getPk(), sof.getFileSystem().getPk(),
                    sof.getAccessTime().getTime(),
                    study.getExternalRetrieveAET(), study.getStudyIuid());
            deleteStudyOrder.processOrderProperties(suid);
            orders.add(deleteStudyOrder);
        }
        return orders;
    }

    private DeleteStudyOrdersAndMaxAccessTime createDeleteOrders(
            Collection sofs, boolean externalRetrieveable,
            boolean storageNotCommited, boolean copyOnMedia,
            String copyOnFSGroup, boolean copyArchived,
            boolean copyOnReadOnlyFS) throws FinderException {
        if (sofs.isEmpty())
            return null;
        Collection<DeleteStudyOrder> orders = new ArrayList<DeleteStudyOrder>(sofs.size());
        long maxAccessTime = 0;
        for (Iterator iter = sofs.iterator(); iter.hasNext();) {
            StudyOnFileSystemLocal sof = (StudyOnFileSystemLocal) iter.next();
            maxAccessTime = sof.getAccessTime().getTime();
            if (sof.matchDeleteConstrains(externalRetrieveable,
                    storageNotCommited, copyOnMedia, copyOnFSGroup,
                    copyArchived, copyOnReadOnlyFS)) {
                StudyLocal study = sof.getStudy();
                DeleteStudyOrder deleteStudyOrder = new DeleteStudyOrder(
                        sof.getPk(), study.getPk(), sof.getFileSystem().getPk(),
                        maxAccessTime, study.getExternalRetrieveAET(),
                        study.getStudyIuid());
                deleteStudyOrder.processOrderProperties(study.getStudyIuid());
                orders.add(deleteStudyOrder);
            }
        }
        return new DeleteStudyOrdersAndMaxAccessTime(orders, maxAccessTime);
    }

    /**
     * @ejb.interface-method
     */
    public Collection<Dataset> createIANforStudy(Long studyPk)
            throws FinderException, NoSuchStudyException {
        try {
            return createIANs(studyHome.findByPrimaryKey(studyPk));
        } catch (ObjectNotFoundException e) {
            throw new NoSuchStudyException(e);
        }
    }

    /**
     * @ejb.interface-method
     */
    public IANAndPatientID createIANforStudy(String uid)
            throws FinderException, NoSuchStudyException {
        try {
            StudyLocal study = studyHome.findByStudyIuid(uid);
            Dataset styAttrs = study.getAttributes(false);
            Dataset patAttrs = study.getPatient().getAttributes(false);
            Collection<Dataset> ians = createIANs(study);
            return new IANAndPatientID(patAttrs.getString(Tags.PatientID),
                    patAttrs.getString(Tags.PatientName),
                    styAttrs.getString(Tags.StudyID), ians);
        } catch (ObjectNotFoundException e) {
            throw new NoSuchStudyException(e);
        }
    }

    private Collection<Dataset> createIANs(StudyLocal study) {
        PatientLocal pat = study.getPatient();
        Dataset ian;
        DcmElement refSerSeq;
        HashMap<String, Dataset> ianMap = new HashMap<String, Dataset>();
        String refPPSIuid;
        for (Iterator siter = study.getSeries().iterator(); siter.hasNext();) {
            SeriesLocal series = (SeriesLocal) siter.next();
            Dataset serAttrs = series.getAttributes(false);
            Dataset refPPS = serAttrs.getItem(Tags.RefPPSSeq);
            refPPSIuid = refPPS == null ? null : refPPS.getString(Tags.RefSOPInstanceUID);
            ian = ianMap.get(refPPSIuid);
            if (ian == null) {
                ian = DcmObjectFactory.getInstance().newDataset();
                ian.putAll(pat.getAttributes(false).subSet(IAN_PAT_TAGS));
                ian.putSH(Tags.StudyID, study.getStudyId());
                ian.putUI(Tags.StudyInstanceUID, study.getStudyIuid());
                DcmElement refPPSSeq = ian.putSQ(Tags.RefPPSSeq);
                refSerSeq = ian.putSQ(Tags.RefSeriesSeq);
                if (refPPSIuid != null)
                    refPPSSeq.addItem(refPPS);
                ianMap.put(refPPSIuid, ian);
            } else {
                refSerSeq = ian.get(Tags.RefSeriesSeq);
            }
            Dataset refSer = refSerSeq.addNewItem();
            refSer.putUI(Tags.SeriesInstanceUID, series.getSeriesIuid());
            DcmElement refSopSeq = refSer.putSQ(Tags.RefSOPSeq);
            Collection insts = series.getInstances();
            for (Iterator iiter = insts.iterator(); iiter.hasNext();) {
                InstanceLocal inst = (InstanceLocal) iiter.next();
                Dataset refSOP = refSopSeq.addNewItem();
                refSOP.putUI(Tags.RefSOPClassUID, inst.getSopCuid());
                refSOP.putUI(Tags.RefSOPInstanceUID, inst.getSopIuid());
                DatasetUtils.putRetrieveAET(refSOP, inst.getRetrieveAETs(),
                        inst.getExternalRetrieveAET());
                refSOP.putCS(Tags.InstanceAvailability,
                        Availability.toString(inst.getAvailabilitySafe()));
             }
        }
        return ianMap.values();
    }

    /**
     * @ejb.interface-method
     */
    public Dataset createIANforSeries(Long seriesPk) 
            throws FinderException, NoSuchSeriesException {
        try {
            return createIAN(seriesHome.findByPrimaryKey(seriesPk));
        } catch (ObjectNotFoundException e) {
            throw new NoSuchSeriesException(e);
        }
    }

    /**
     * @ejb.interface-method
     */
    public IANAndPatientID createIANforSeries(String uid) 
            throws FinderException, NoSuchSeriesException {
        try {
            SeriesLocal series = seriesHome.findBySeriesIuid(uid);
            StudyLocal study = series.getStudy();
            Dataset styAttrs = study.getAttributes(false);
            Dataset patAttrs = study.getPatient().getAttributes(false);
            ArrayList<Dataset> ians = new ArrayList<Dataset>(1);
            ians.add(createIAN(series));
            return new IANAndPatientID(patAttrs.getString(Tags.PatientID),
                    patAttrs.getString(Tags.PatientName),
                    styAttrs.getString(Tags.StudyID), ians);
        } catch (ObjectNotFoundException e) {
            throw new NoSuchSeriesException(e);
        }
    }

    private Dataset createIAN(SeriesLocal series) {
        StudyLocal study = series.getStudy();
        PatientLocal pat = study.getPatient();
        Dataset ian = DcmObjectFactory.getInstance().newDataset();
        ian.putAll(pat.getAttributes(false).subSet(IAN_PAT_TAGS));
        ian.putSH(Tags.StudyID, study.getStudyId());
        ian.putUI(Tags.StudyInstanceUID, study.getStudyIuid());
        DcmElement refPPSSeq = ian.putSQ(Tags.RefPPSSeq);
        HashSet ppsuids = new HashSet();
        DcmElement refSerSeq = ian.putSQ(Tags.RefSeriesSeq);
        Dataset serAttrs = series.getAttributes(false);
        Dataset refPPS = serAttrs.getItem(Tags.RefPPSSeq);
        if (refPPS != null) {
            refPPSSeq.addItem(refPPS);
        }
        Dataset refSer = refSerSeq.addNewItem();
        refSer.putUI(Tags.SeriesInstanceUID, series.getSeriesIuid());
        DcmElement refSopSeq = refSer.putSQ(Tags.RefSOPSeq);
        Collection insts = series.getInstances();
        for (Iterator iiter = insts.iterator(); iiter.hasNext();) {
            InstanceLocal inst = (InstanceLocal) iiter.next();
            Dataset refSOP = refSopSeq.addNewItem();
            refSOP.putUI(Tags.RefSOPClassUID, inst.getSopCuid());
            refSOP.putUI(Tags.RefSOPInstanceUID, inst.getSopIuid());
            DatasetUtils.putRetrieveAET(refSOP, inst.getRetrieveAETs(),
                    inst.getExternalRetrieveAET());
            refSOP.putCS(Tags.InstanceAvailability,
                    Availability.toString(inst.getAvailabilitySafe()));
        }
        return ian;
    }

    /**    
     * @ejb.interface-method
     * 
     * @ejb.transaction type="RequiresNew"
     */
    @SuppressWarnings("unchecked")
	public Collection<FileDTO> deleteStudy(DeleteStudyOrder order,
            boolean delStudyFromDB, boolean delPatientWithoutObjects)
            throws ConcurrentStudyStorageException, CreateException {
        try {
        	
            long fsPk = order.getFsPk();
            StudyLocal study = studyHome.findByPrimaryKey(order.getStudyPk());
            FileSystemLocal fs = fileSystemHome.findByPrimaryKey(fsPk);
            checkConcurrentStudyStorage(study, fs);

			Collection<FileDTO> fileDTOs = marshallPrivFiles(
					privManager().moveFilesToTrash(study.getFiles(fsPk)));

            if (delStudyFromDB && study.getAllFiles().isEmpty()) {
                PatientLocal pat = study.getPatient();
                markPublishedStudy(study, true);
                study.remove();
                if (delPatientWithoutObjects) {
                    deletePatientWithoutObjects(pat);
                }
            } else {
                int availabilityOfExtRetr =
                        order.getExternalRetrieveAvailability();
                updateStudyAvailability(study, availabilityOfExtRetr);
                markPublishedStudy(study, false);
             }
            
            return fileDTOs;
        } catch (FinderException e) {
            throw new EJBException(e);
        } catch (RemoveException e) {
            throw new EJBException(e);
        }
    }

	private Collection<FileDTO> marshallPrivFiles(
			Collection<PrivateFileLocal> privFiles) {
		Collection<FileDTO> fileDTOs = new ArrayList<FileDTO>(privFiles.size());
		
		for (PrivateFileLocal privFile : privFiles) {
			fileDTOs.add(privFile.getFileDTO());
		}
		return fileDTOs;
	}

    private void checkConcurrentStudyStorage(StudyLocal study,
            FileSystemLocal fs) throws FinderException,
            ConcurrentStudyStorageException {
        try {
            // check if new objects belonging to the study were stored
            // after this DeleteStudyOrder was scheduled (MarkedForDeletion is set to false)
            if (!sofHome.findByStudyAndFileSystem(study, fs).getMarkedForDeletion())
                throw new ConcurrentStudyStorageException(
                    "Concurrent storage of study[uid="
                    + study.getStudyIuid() + "] on file system[dir="
                    + fs.getDirectoryPath() + "] - do not delete study");
        } catch (ObjectNotFoundException onfe) {
        }
    }

    
    /**    
     * @ejb.interface-method
     */
    public FileDTO[][] getFilesOfStudy(DeleteStudyOrder order) throws ConcurrentStudyStorageException {
        try {
            long fsPk = order.getFsPk();
            StudyLocal study = studyHome.findByPrimaryKey(order.getStudyPk());
            FileSystemLocal fs = fileSystemHome.findByPrimaryKey(fsPk);
            checkConcurrentStudyStorage(study, fs);
            Collection series = study.getSeries();
            FileDTO[][] dtos = new FileDTO[series.size()][];
            int i = 0;
            for (Iterator it = series.iterator() ; it.hasNext() ; i++) {
             dtos[i] = toFileDTOs(((SeriesLocal) it.next()).getFiles(fsPk));
            }
            return dtos;
        } catch (FinderException x) {
            throw new EJBException(x);
        }
    }
    /**
     * 
     * Move File to another filesystem and/or path
     * @param order
     * @param dtos FileDTO with parameter of the destination file (filesystemPk and filePath). Pk is of file to move:
     * @param destFileStatus File status of destination file. Null means: do not change!
     * @param keepSrcFiles 
     * @param keepMovedFilesOnError  If set return a list of failed files instead of rollback and fail the whole order.
     * 
     * @return null or List of failed files
     * @throws ConcurrentStudyStorageException
     *
     * @ejb.interface-method
     */
    public List<FileDTO> moveFiles(DeleteStudyOrder order, FileDTO[][] dtos, Integer destFileStatus, boolean keepSrcFiles, boolean keepMovedFilesOnError) throws ConcurrentStudyStorageException {
        try {
            List<FileDTO> failed = null;
            long fsPk = order.getFsPk();
            StudyLocal study = studyHome.findByPrimaryKey(order.getStudyPk());
            FileSystemLocal fs = fileSystemHome.findByPrimaryKey(fsPk);
            checkConcurrentStudyStorage(study, fs);
            FileSystemLocal fsDest = fileSystemHome.findByPrimaryKey(dtos[0][0].getFileSystemPk());
            Collection<FileLocal> c = study.getFiles(fsPk);
            FileLocal[] files = c.toArray(new FileLocal[c.size()]);
            FileLocal fSrc;
            FileDTO dto;
            for (int i = 0, k = 0 ; i< dtos.length ; i++) {
                for (int j = 0; j < dtos[i].length ; j++, k++) {
                    dto = dtos[i][j];
                    try {
                        fSrc = getFile(dto.getPk(), files, k);
                        if (fSrc != null) {
                            if (keepSrcFiles) { 
                                fileHome.create(dto.getFilePath(), fSrc.getFileTsuid(), 
                                    fSrc.getFileSize(), fSrc.getFileMd5(), fSrc.getOrigMd5(), 
                                    destFileStatus == null ? fSrc.getFileStatus() : destFileStatus.intValue(), 
                                    fSrc.getInstance(), fsDest);
                            } else {
                                fSrc.setFilePath(dto.getFilePath());
                                fSrc.setFileSystem(fsDest);
                                if (destFileStatus != null) {
                                    fSrc.setFileStatus(destFileStatus.intValue());
                                }
                            }
                        } else {
                            log.error("Missing source file for:"+dto);
                        }
                    } catch (Exception e) {
                        if (!keepMovedFilesOnError) {
                            throw (e instanceof RuntimeException) ? 
                                    (RuntimeException) e : new EJBException(e);
                        }
                        if (failed == null) {
                            failed = new ArrayList<FileDTO>();
                            order.setThrowable(e);
                        }
                        failed.add(dto);
                    }
                }
            }
            int availabilityOfExtRetr =
                order.getExternalRetrieveAvailability();
            updateStudyAvailability(study, availabilityOfExtRetr);
            return failed;
        } catch (FinderException x) {
            throw new EJBException(x);
        }
    }

    private FileLocal getFile(long pk, FileLocal[] files, int i) {
        if ( files[i].getPk() == pk)
            return files[i];
        for (int j = i+1 ; j < files.length ; j++) {
            if (files[j].getPk() == pk) {
                FileLocal f = files[j];
                files[j] = files[i];
                files[i] = f;
                return f;
            }
        }
        return null;
    }

    private void updateStudyAvailability(StudyLocal study, int availabilityOfExtRetr) {
        Collection seriess = study.getSeries();
        for (Iterator siter = seriess.iterator(); siter.hasNext();) {
            SeriesLocal series = (SeriesLocal) siter.next();
            Collection insts = series.getInstances();
            for (Iterator iiter = insts.iterator(); iiter.hasNext();) {
                InstanceLocal inst = (InstanceLocal) iiter.next();
                inst.updateRetrieveAETs();
                inst.updateAvailability(availabilityOfExtRetr);
            }
            series.updateRetrieveAETs();
            series.updateAvailability();
        }
        study.updateRetrieveAETs();
        study.updateAvailability();
    }
    
    /**    
     * @ejb.interface-method
     */
    public Collection getSeriesPks(DeleteStudyOrder order)
            throws ConcurrentStudyStorageException {
        try {
            long fsPk = order.getFsPk();
            StudyLocal study = studyHome.findByPrimaryKey(order.getStudyPk());
            Collection seriesPKs = study.getSeriesPks();
            FileSystemLocal fs = fileSystemHome.findByPrimaryKey(fsPk);
            checkConcurrentStudyStorage(study, fs);
            return seriesPKs;
        } catch (FinderException e) {
            throw new EJBException(e);
        }
    }

    /**    
     * @ejb.interface-method
     * 
     * @ejb.transaction type="RequiresNew"
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
	public Collection<FileDTO> deleteSeries(DeleteStudyOrder order, Long seriesPk,
            boolean delStudyFromDB, boolean delPatientWithoutObjects)
            throws ConcurrentStudyStorageException, CreateException {
        try {
            long fsPk = order.getFsPk();
            SeriesLocal series = seriesHome.findByPrimaryKey(seriesPk);
            Collection files = series.getFiles(fsPk);
            
			Collection<FileDTO> fileDTOs = marshallPrivFiles(privManager()
					.moveFilesToTrash(files));

            StudyLocal study = series.getStudy();
            if (delStudyFromDB && series.getAllFiles().isEmpty()) {
                series.remove();
                if (study.getSeries().isEmpty()) {
                    PatientLocal pat = study.getPatient();
                    markPublishedStudy(study, true);
                    study.remove();
                    if (delPatientWithoutObjects) {
                        deletePatientWithoutObjects(pat);
                    }
                }
            } else {
                int availabilityOfExtRetr =
                        order.getExternalRetrieveAvailability();
                Collection insts = series.getInstances();
                for (Iterator iiter = insts.iterator(); iiter.hasNext();) {
                    InstanceLocal inst = (InstanceLocal) iiter.next();
                    inst.updateRetrieveAETs();
                    inst.updateAvailability(availabilityOfExtRetr);
                }
                series.updateRetrieveAETs();
                series.updateAvailability();
                study.updateRetrieveAETs();
                study.updateAvailability();
                markPublishedStudy(study, false);
             }
            
            return fileDTOs;
        } catch (FinderException e) {
            throw new EJBException(e);
        } catch (RemoveException e) {
            throw new EJBException(e);
        }
    }

    private void deletePatientWithoutObjects(PatientLocal patient)
            throws RemoveException {
        if ( patient.getStudies().isEmpty() &&
                patient.getMwlItems().isEmpty() &&
                patient.getGsps().isEmpty() &&
                patient.getMpps().isEmpty() &&
                patient.getGppps().isEmpty() &&
                patient.getUPS().isEmpty() ) {
            patient.remove();
        }
    }

    /**
     * @ejb.interface-method
     */
    public FileDTO[] findFilesToCompress(FileSystemDTO fsDTO, String cuid,
            Timestamp before, int limit) throws FinderException {
        if (log.isDebugEnabled())
            log.debug("Querying for files to compress in "
                    + fsDTO.getDirectoryPath());
        Collection c = fileHome.findFilesToCompress(fsDTO.getPk(), cuid,
                before, limit);
        if (log.isDebugEnabled())
            log.debug("Found " + c.size() + " files to compress in "
                    + fsDTO.getDirectoryPath());
        return toFileDTOs(c);
    }

    /**
     * @ejb.interface-method
     */
    public FileDTO[] findFilesForMD5Check(String dirPath, Timestamp before,
            int limit) throws FinderException {
        if (log.isDebugEnabled())
            log.debug("Querying for files to check md5 in " + dirPath);
        Collection c = fileHome.findToCheckMd5(dirPath, before, limit);
        if (log.isDebugEnabled())
            log.debug("Found " + c.size() + " files to check md5 in "
                    + dirPath);
        return toFileDTOs(c);
    }

    /**
     * @ejb.interface-method
     */
    public FileDTO[] findFilesByStatusAndFileSystem(List<String> dirPath, int status, Timestamp notBefore,
            Timestamp before, int limit) throws FinderException {
        if (log.isDebugEnabled())
            log.debug("Querying for files with status " + status + " in "
                    + dirPath);
        Collection c = dirPath.size() == 1 ? 
                fileHome.findByStatusAndFileSystem(dirPath.get(0), status, notBefore, before, limit)
                : fileHome.selectByStatusAndFileSystem(dirPath, status, notBefore, before, limit);
        if (log.isDebugEnabled())
            log.debug("Found " + c.size() + " files with status " + status
                    + " in " + dirPath);
        return toFileDTOs(c);
    }

    /**
     * @ejb.interface-method
     */
    public int migrateFilesOfTarFile(String fsId, String tarFilename, String newFsId, String newTarFilename, int newFileStatus) throws FinderException, CreateException {
        Collection<FileLocal> c = fileHome.findFilesOfTarFile(fsId, tarFilename+"%");
        if (log.isDebugEnabled())
            log.debug("Found "+c.size()+" files to migrate for tar file "+tarFilename);
        FileSystemLocal newFS = this.fileSystemHome.findByDirectoryPath(newFsId);
        if (!newTarFilename.endsWith("!"))
            newTarFilename += "!";
        FileLocal f1;
        boolean sameTarFN = tarFilename.equals(newTarFilename);
        String newPath;
        int pos;
        int count = 0;
        for (FileLocal f : c) {
            if (f.getFileStatus() == FileStatus.MD5_CHECK_FAILED) {
                log.warn("Skip migration of file with status MD%_CHECK_FAILED:"+f);
                continue;
            }
            newPath = f.getFilePath();
            if (!sameTarFN) {
                pos = newPath.indexOf('!');
                newPath = newPath.substring(++pos);
                newPath = newTarFilename+newPath;
            }
            fileHome.create(newPath, f.getFileTsuid(), f.getFileSize(), f.getFileMd5(), 
                    f.getOrigMd5(), newFileStatus, f.getInstance(), newFS);
            f.setFileStatus(FileStatus.MIGRATED);
            count++;
        }
        return count;
    }

    /**
     * @ejb.interface-method
     */
    public void setFilestatusOfFilesOfTarFile(String fsId, String tarFilename, int fileStatus) throws FinderException, CreateException {
        Collection<FileLocal> c = fileHome.findFilesOfTarFile(fsId, tarFilename+"%");
        for (FileLocal f : c) {
            f.setFileStatus(fileStatus);
        }
    }
    
    /**
     * @ejb.interface-method
     */
    public FileDTO[] getFilesOfTarFile(String fsId, String tarFilename) throws FinderException, CreateException {
        Collection<FileLocal> c = fileHome.findFilesOfTarFile(fsId, tarFilename+"%");
        return toFileDTOs(c);
    }

    /**
     * @ejb.interface-method
     */
    public void updateTimeOfLastMd5Check(long pk) throws FinderException {
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        if (log.isDebugEnabled())
            log.debug("update time of last md5 check to " + ts);
        FileLocal fl = fileHome.findByPrimaryKey(new Long(pk));
        fl.setTimeOfLastMd5Check(ts);
    }

    /**
     * @ejb.interface-method
     */
    public void replaceFile(long pk, String path, String tsuid, long size,
            byte[] md5, int status) throws FinderException {
        FileLocal oldFile = fileHome.findByPrimaryKey(pk);
        oldFile.setFilePath(path);
        oldFile.setFileTsuid(tsuid);
        oldFile.setFileSize(size);
        oldFile.setFileMd5(md5);
        oldFile.setFileStatus(status);
    }

    /**
     * @ejb.interface-method
     */
    public void replaceFileAndCoerceAttributes(long fspk, long pk, String path,
            String tsuid, long size, byte[] md5, int status, Dataset ds) {
        try {
            FileLocal oldFile = fileHome.findByPrimaryKey(pk);
            oldFile.setFileSystem(fileSystemHome.findByPrimaryKey(fspk));
            oldFile.setFilePath(path);
            oldFile.setFileTsuid(tsuid);
            oldFile.setFileSize(size);
            oldFile.setFileMd5(md5);
            oldFile.setFileStatus(status);
            oldFile.getInstance().coerceAttributes(ds, null);
        } catch (Exception e) {
            throw new EJBException(e);
        }
    }

    /**
     * @ejb.interface-method
     */
    public void setFileStatus(long pk, int status, List<String> archiveFileSystems) throws FinderException {
        FileLocal f = fileHome.findByPrimaryKey(pk);
        f.setFileStatus(status);
        if (status == FileStatus.ARCHIVED) {
            InstanceLocal il = f.getInstance();
            if (il.getArchived()) {
                log.debug("Instance "+il.getSopIuid()+" is already marked as ARCHIVED!");
            } else {
                if (archiveFileSystems != null && archiveFileSystems.size() > 1) {
                    
                    Collection<FileLocal> files = il.getFiles();
                    loop: for (String fs : archiveFileSystems) {
                        for (FileLocal file : files) {
                            if (file.getFileStatus() == FileStatus.ARCHIVED &&
                                    fs.equals(file.getFileSystem().getDirectoryPath()))
                                continue loop;
                        }
                        return;//no archived file found for filesystem
                    }
                }
                il.setArchived(true);
                log.info("Instance "+il.getSopIuid()+" marked as ARCHIVED! File:"+f.asString());
            }
        }
    }

    /**
     * @ejb.interface-method
     */
    public void setFileStatus(long pk, int status) throws FinderException {
        FileLocal f = fileHome.findByPrimaryKey(pk);
        f.setFileStatus(status);
        if (status == FileStatus.ARCHIVED) {
            InstanceLocal il = f.getInstance();
            if (il.getArchived()) {
                log.debug("Instance "+il.getSopIuid()+" is already marked as ARCHIVED!");
            } else {
                il.setArchived(true);
                log.info("Instance "+il.getSopIuid()+" marked as ARCHIVED! File:"+f.asString());
            }
        }
    }

    private FileDTO[] toFileDTOs(Collection c) {
        FileDTO[] dto = new FileDTO[c.size()];
        Iterator it = c.iterator();
        for (int i = 0; i < dto.length; ++i) {
            dto[i] = ((FileLocal) it.next()).getFileDTO();
        }
        return dto;
    }

    /**
     * @ejb.interface-method
     */
    public FileDTO[] getOrphanedPrivateFilesOnFSGroup(String groupID, int limit)
            throws FinderException {
        return toFileDTOsPrivate(
                privFileHome.findOrphanedOnFSGroup(groupID, limit));
    }

    private FileDTO[] toFileDTOsPrivate(Collection c) {
        FileDTO[] dto = new FileDTO[c.size()];
        Iterator it = c.iterator();
        for (int i = 0; i < dto.length; ++i) {
            dto[i] = ((PrivateFileLocal) it.next()).getFileDTO();
        }
        return dto;
    }

    /**
     * @ejb.interface-method
     */
    public void deletePrivateFile(long file_pk) throws RemoveException {
        privFileHome.remove(file_pk);
    }

    /**
     * @ejb.interface-method
     */
    public void touchStudyOnFileSystem(String siud, String dirPath)
            throws FinderException, CreateException {
        try {
            sofHome.findByStudyAndFileSystem(siud, dirPath).touch();
        } catch (ObjectNotFoundException e) {
            try {
                sofHome.create(studyHome.findByStudyIuid(siud), fileSystemHome
                        .findByDirectoryPath(dirPath));
            } catch (Exception ignore) {
                log.info("Create StudyOnFS failed! Check if concurrent create.", ignore);
                sofHome.findByStudyAndFileSystem(siud, dirPath).touch();
            }
        }
    }

    /**
     * @ejb.interface-method
     */
    public FileDTO[] deleteStoredSeries(SeriesStored seriesStored) {
        try {
            SeriesLocal series = seriesHome.findBySeriesIuid(
                    seriesStored.getSeriesInstanceUID());
            StudyLocal study = series.getStudy();
            Collection instances = series.getInstances();
            ArrayList fileDTOs = new ArrayList(instances.size());
            DcmElement refSopSeq = seriesStored.getIAN()
                .getItem(Tags.RefSeriesSeq).get(Tags.RefSOPSeq);
            int numRefInst = refSopSeq.countItems();
            HashSet iuids = new HashSet(numRefInst * 4 / 3 + 1);
            for (int i = 0; i < numRefInst; i++) {
                iuids.add(refSopSeq.getItem(i)
                        .getString(Tags.RefSOPInstanceUID));
            }
            ArrayList toRemove = new ArrayList(numRefInst);
            for (Iterator itInst = instances.iterator(); itInst.hasNext();) {
                InstanceLocal inst = (InstanceLocal) itInst.next();
                if (iuids.contains(inst.getSopIuid())) {
                    Collection files = inst.getFiles();
                    for (Iterator itFiles = files.iterator();
                            itFiles.hasNext();) {
                        FileLocal file = (FileLocal) itFiles.next();
                        fileDTOs.add(file.getFileDTO());
                    }
                    toRemove.add(inst);
                }
            }
            if (toRemove.size() == instances.size()) {
                series.remove();
            } else {
                for (Iterator itInst = toRemove.iterator(); itInst.hasNext();) {
                    InstanceLocal inst = (InstanceLocal) itInst.next();
                    inst.remove();
                }
                UpdateDerivedFieldsUtils.updateDerivedFieldsOf(series);
            }
            UpdateDerivedFieldsUtils.updateDerivedFieldsOf(study);
            return (FileDTO[]) fileDTOs.toArray(new FileDTO[fileDTOs.size()]);
        } catch (FinderException e) {
            throw new EJBException(e);
        } catch (RemoveException e) {
            throw new EJBException(e);
        }
    }

    private static final Comparator DESC_FILE_PK = new Comparator() {

        /**
         * This will make sure the most available file will be listed first
         */
        public int compare(Object o1, Object o2) {
            FileDTO dto1 = (FileDTO) o1;
            FileDTO dto2 = (FileDTO) o2;
            int diffAvail = dto1.getAvailability() - dto2.getAvailability();
            long diffPk = dto2.getPk() - dto1.getPk();
            return diffAvail != 0 ? diffAvail 
                        : diffPk == 0 ? 0 : diffPk < 0 ? -1 : 1;
        }
    };

    /**
     * @ejb.interface-method
     */
    public FileDTO[] getFilesOfInstance(String iuid) throws FinderException {
        FileDTO[] dtos = toFileDTOs(instHome.findBySopIuid(iuid).getFiles());
        Arrays.sort(dtos, DESC_FILE_PK);
                return dtos;
    }

    /**
     * @ejb.interface-method
     */
    public String getExternalRetrieveAET(String iuid) throws FinderException {
        return instHome.findBySopIuid(iuid).getExternalRetrieveAET();
    }

    /**
     * @ejb.interface-method
     */
    public FileDTO[]  findFilesToLossyCompress(String fsGroupID,
            String cuid, String bodyPart, String srcAET, Timestamp before,
            int limit) throws FinderException {
        return toFileDTOs(bodyPart == null
                ? fileHome.findFilesToLossyCompress(
                        fsGroupID, cuid, srcAET, before, limit)
                : fileHome.findFilesToLossyCompress(
                        fsGroupID, cuid, bodyPart, srcAET, before, limit));
    }

    /**
     * @ejb.interface-method
     */
    public FileDTO[]  findFilesToLossyCompressWithExternalRetrieveAET(
            String fsGroupID, String retrieveAET,
            String cuid, String bodyPart, String srcAET, Timestamp before,
            int limit) throws FinderException {
        return toFileDTOs(bodyPart == null
                ? fileHome.findFilesToLossyCompressWithExternalRetrieveAET(
                        fsGroupID, retrieveAET, cuid, srcAET, before, limit)
                : fileHome.findFilesToLossyCompressWithExternalRetrieveAET(
                        fsGroupID, retrieveAET, cuid, bodyPart, srcAET, before, limit));
    }

    /**
     * @ejb.interface-method
     */
    public FileDTO[]  findFilesToLossyCompressWithCopyOnOtherFileSystemGroup(
            String fsGroupID, String otherFSGroupID,
            String cuid, String bodyPart, String srcAET, Timestamp before,
            int limit) throws FinderException {
        return toFileDTOs(bodyPart == null
                ? fileHome.findFilesToLossyCompressWithCopyOnOtherFileSystemGroup(
                        fsGroupID, otherFSGroupID, cuid, srcAET, before, limit)
                : fileHome.findFilesToLossyCompressWithCopyOnOtherFileSystemGroup(
                        fsGroupID, otherFSGroupID, cuid, bodyPart, srcAET, before, limit));
    }

    /**
     * @ejb.interface-method
     */
    public int syncArchivedFlag(String fsPath, int limit) throws FinderException {
        Collection<FileLocal> c = fileHome.findToSyncArchived(fsPath, limit);
        log.info("Found files to sync archived flag on instance:"+c.size());
        for (FileLocal f : c) {
            f.getInstance().setArchived(true);
        }
        return c.size();
    }
    /**
     * @ejb.interface-method
     */
    public int deleteFilesOfInvalidTarFile(String fsId, String tarFilename) throws FinderException, RemoveException {
        if (fsId.startsWith("tar:") && tarFilename.length() > 10) {
            Collection<FileLocal> c = fileHome.findFilesOfTarFile(fsId, tarFilename+"%");
            log.info("Delete "+c.size()+" files of tar file "+tarFilename);
            for (FileLocal f : c) {
                f.remove();
            }
            return c.size();
        } else {
            log.warn("Given arguments probably not a tar file! Delete request denied!");
            return -1;
        }
    }
    /**
     * @ejb.interface-method
     */
    public boolean deleteFileOnTarFs(String fsId, long filePk) {
        if (fsId.startsWith("tar:")) {
            try {
                fileHome.findByPrimaryKey(filePk).remove();
                return true;
            } catch (Exception x) {
                log.error("Failed to remove file entity (pk="+filePk+")", x);
            }
        } else {
            log.warn("Given Filesystem ID is not a tar file system! Delete request denied!");
        }
        return false;
    }
    
    private void logOrderInfoMsg(DeleteStudyOrder order, String msg) {
       log.info( "Study "+order.getStudyIUID()+" [pk=" + order.getStudyPk() + 
           "] on FileSystem[pk="+ order.getFsPk()+ "] "+msg);
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
     * @ejb.ejb-ref ejb-name="PrivateManager" view-type="local" ref-name="ejb/PrivateManagerLocal"  
     */
    protected abstract PrivateManagerLocal privManager();
    /**
     * @ejb.ejb-ref ejb-name="ContentEdit" view-type="local" ref-name="ejb/ContentEdit"
     */
    protected abstract ContentEditLocal contentEdit();
}


