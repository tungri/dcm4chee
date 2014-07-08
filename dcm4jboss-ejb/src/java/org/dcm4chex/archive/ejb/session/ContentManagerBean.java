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

import java.rmi.RemoteException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ejb.EJBException;
import javax.ejb.FinderException;
import javax.ejb.ObjectNotFoundException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Tags;
import org.dcm4cheri.util.StringUtils;
import org.dcm4chex.archive.common.PrivateTags;
import org.dcm4chex.archive.ejb.interfaces.CodeLocal;
import org.dcm4chex.archive.ejb.interfaces.CodeLocalHome;
import org.dcm4chex.archive.ejb.interfaces.FileLocal;
import org.dcm4chex.archive.ejb.interfaces.InstanceLocal;
import org.dcm4chex.archive.ejb.interfaces.InstanceLocalHome;
import org.dcm4chex.archive.ejb.interfaces.MPPSLocal;
import org.dcm4chex.archive.ejb.interfaces.MPPSLocalHome;
import org.dcm4chex.archive.ejb.interfaces.PatientLocal;
import org.dcm4chex.archive.ejb.interfaces.PatientLocalHome;
import org.dcm4chex.archive.ejb.interfaces.PrivateFileLocal;
import org.dcm4chex.archive.ejb.interfaces.PrivateInstanceLocal;
import org.dcm4chex.archive.ejb.interfaces.PrivateInstanceLocalHome;
import org.dcm4chex.archive.ejb.interfaces.PrivatePatientLocal;
import org.dcm4chex.archive.ejb.interfaces.PrivatePatientLocalHome;
import org.dcm4chex.archive.ejb.interfaces.PrivateSeriesLocal;
import org.dcm4chex.archive.ejb.interfaces.PrivateSeriesLocalHome;
import org.dcm4chex.archive.ejb.interfaces.PrivateStudyLocal;
import org.dcm4chex.archive.ejb.interfaces.PrivateStudyLocalHome;
import org.dcm4chex.archive.ejb.interfaces.SeriesLocal;
import org.dcm4chex.archive.ejb.interfaces.SeriesLocalHome;
import org.dcm4chex.archive.ejb.interfaces.StudyLocal;
import org.dcm4chex.archive.ejb.interfaces.StudyLocalHome;
import org.dcm4chex.archive.util.Convert;

/**
 * 
 * @author gunter.zeilinger@tiani.com
 * @version $Revision: 18054 $ $Date: 2013-09-16 07:31:39 +0000 (Mon, 16 Sep 2013) $
 * @since 14.01.2004
 * 
 * @ejb.bean name="ContentManager" type="Stateless" view-type="remote"
 *           jndi-name="ejb/ContentManager"
 * @ejb.transaction-type type="Container"
 * @ejb.transaction type="Required"

 * @ejb.ejb-ref ejb-name="Patient" view-type="local" ref-name="ejb/Patient" 
 * @ejb.ejb-ref ejb-name="Study" view-type="local" ref-name="ejb/Study" 
 * @ejb.ejb-ref ejb-name="Series" view-type="local" ref-name="ejb/Series" 
 * @ejb.ejb-ref ejb-name="Instance" view-type="local" ref-name="ejb/Instance"
 * @ejb.ejb-ref ejb-name="Code" view-type="local" ref-name="ejb/Code" 
 * 
 * @ejb.ejb-ref ejb-name="PrivatePatient" view-type="local" ref-name="ejb/PrivatePatient" 
 * @ejb.ejb-ref ejb-name="PrivateStudy" view-type="local" ref-name="ejb/PrivateStudy" 
 * @ejb.ejb-ref ejb-name="PrivateSeries" view-type="local" ref-name="ejb/PrivateSeries" 
 * @ejb.ejb-ref ejb-name="PrivateInstance" view-type="local" ref-name="ejb/PrivateInstance" 
 * 
 * @ejb.ejb-ref ejb-name="MPPS" view-type="local" ref-name="ejb/MPPS" 
 *  
 */

public abstract class ContentManagerBean implements SessionBean {

    private static final int[] MPPS_FILTER_TAGS = { 
        Tags.PerformedStationAET, Tags.PerformedStationName,
        Tags.PPSStartDate, Tags.PPSStartTime, Tags.PPSEndDate,
        Tags.PPSEndTime, Tags.PPSStatus, Tags.PPSID,
        Tags.PPSDescription, Tags.PerformedProcedureTypeDescription,
        Tags.PerformedProtocolCodeSeq, Tags.ScheduledStepAttributesSeq, 
		Tags.PPSDiscontinuationReasonCodeSeq };
	
    private static final DcmObjectFactory dof = DcmObjectFactory.getInstance();
    private PatientLocalHome patHome;
    private StudyLocalHome studyHome;
    private SeriesLocalHome seriesHome;
    private InstanceLocalHome instanceHome;
    private CodeLocalHome codeHome;

    private PrivatePatientLocalHome privPatHome;
    private PrivateStudyLocalHome privStudyHome;
    private PrivateSeriesLocalHome privSeriesHome;
    private PrivateInstanceLocalHome privInstanceHome;

    private MPPSLocalHome mppsHome;
    
    public void setSessionContext(SessionContext arg0)
        throws EJBException, RemoteException {
        Context jndiCtx = null;
        try {
            jndiCtx = new InitialContext();
            patHome =
                (PatientLocalHome) jndiCtx.lookup("java:comp/env/ejb/Patient");
            studyHome =
                (StudyLocalHome) jndiCtx.lookup("java:comp/env/ejb/Study");
            seriesHome =
                (SeriesLocalHome) jndiCtx.lookup("java:comp/env/ejb/Series");
            instanceHome =
                (InstanceLocalHome) jndiCtx.lookup("java:comp/env/ejb/Instance");
            codeHome =
                (CodeLocalHome) jndiCtx.lookup("java:comp/env/ejb/Code");

            privPatHome =
                (PrivatePatientLocalHome) jndiCtx.lookup("java:comp/env/ejb/PrivatePatient");
            privStudyHome =
                (PrivateStudyLocalHome) jndiCtx.lookup("java:comp/env/ejb/PrivateStudy");
            privSeriesHome =
                (PrivateSeriesLocalHome) jndiCtx.lookup("java:comp/env/ejb/PrivateSeries");
            privInstanceHome =
                (PrivateInstanceLocalHome) jndiCtx.lookup("java:comp/env/ejb/PrivateInstance");
            
            mppsHome = (MPPSLocalHome) jndiCtx.lookup("java:comp/env/ejb/MPPS");
            
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
        patHome = null;
        studyHome = null;
        seriesHome = null;
        instanceHome = null;
        codeHome = null;
        privPatHome = null;
        privStudyHome = null;
        privSeriesHome = null;
        privInstanceHome = null;
    }

    /**
     * @throws FinderException 
     * @ejb.interface-method
     */
    public Dataset getPatient(long pk) throws FinderException {
        PatientLocal pat = patHome.findByPrimaryKey(new Long(pk));
        return pat.getAttributes(true);
        
    }
    /**
     * @throws FinderException 
     * @ejb.interface-method
     */
    public Dataset getPatientByID(String pid, String issuer) throws FinderException  {
        PatientLocal pat = this.getPatientLocal(pid, issuer);
        if ( pat == null) {
            return null;
        } else if ( issuer == null && pat.getIssuerOfPatientId() != null) {
            return null;
        }
        return pat.getAttributes(true);
        
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public Dataset getStudy(long studyPk) throws FinderException {
        return studyHome.findByPrimaryKey(new Long(studyPk)).getAttributes(true);
    }
   
    /**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public Dataset getStudyByIUID(String studyIUID) throws FinderException {
        return studyHome.findByStudyIuid(studyIUID).getAttributes(true);
    }

    /**
     * get Dataset objects for 'prior' studies of patient, referenced by current study IUID.
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public Set<Dataset> getPriorInfos(String studyIUID, boolean instanceLevel, int minAvail, Long createdAfter, String[] retrAETs, String[] modalities) throws FinderException {
        PatientLocal pat = studyHome.findByStudyIuid(studyIUID).getPatient();
        Dataset dsPat = pat.getAttributes(true);
        Collection seriess = seriesHome.listPriorOfPatient(pat, studyIUID, 
                createdAfter == null ? null : new Timestamp(createdAfter), 
                minAvail, modalities);
        long studyPk = -1;
        SeriesLocal series;
        HashSet<Dataset> infos = new HashSet<Dataset>();
        Dataset dsStudy = null, dsSeries, info;
        for (Iterator<SeriesLocal> itSeries = seriess.iterator(); itSeries.hasNext() ; ) {
            series = itSeries.next();
            if ( !isRetrieveableFrom(series.getRetrieveAETs(), retrAETs))
                continue;
            dsSeries = series.getAttributes(true);
            if (studyPk != series.getStudy().getPk()) {
                dsStudy = series.getStudy().getAttributes(true);
                studyPk = series.getStudy().getPk();
            }
            if (instanceLevel) {
                for (Iterator<InstanceLocal> itInst = series.getInstances().iterator(); itInst.hasNext() ; ) {
                    info = itInst.next().getAttributes(true);
                    info.putAll(dsSeries);
                    info.putAll(dsStudy);
                    info.putAll(dsPat);
                    infos.add(info);

                }
            } else {
                info = dsSeries;
                info.putAll(dsStudy);
                info.putAll(dsPat);
                infos.add(info);
            }
        }
        return infos;
    }

    private boolean isRetrieveableFrom(String retrieveAETs, String[] retrAETs) {
        if (retrAETs == null)
            return true;
        if (retrieveAETs == null || retrieveAETs.length() < 1)
            return false;
        String[] aets = StringUtils.split(retrieveAETs, '\\');
        for (int i = 0 ; i < aets.length ; i++) {
            for (int j = 0 ; j < retrAETs.length ; j++) {
                if (retrAETs[j].equals(aets[i]))
                    return true;
            }
        }
        return false;
    }
    
    private boolean matchModality(String mod, String[] mods) {
        if (mods == null)
            return true;
        for (int i = 0 ; i < mods.length ; i++) {
            if (mods[i].equals(mod))
                return true;
        }
        return false;
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public Dataset getSeries(long seriesPk) throws FinderException {
        return seriesHome.findByPrimaryKey(new Long(seriesPk)).getAttributes(true);
    }
    /**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public Dataset getSeriesByIUID(String seriesIUID) throws FinderException {
        return seriesHome.findBySeriesIuid(seriesIUID).getAttributes(true);
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public Dataset getInstance(long instancePk) throws FinderException {
        return instanceHome.findByPrimaryKey(new Long(instancePk)).getAttributes(true);
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public Dataset getInstanceByIUID(String sopiuid) throws FinderException {
        return instanceHome.findBySopIuid(sopiuid).getAttributes(true);
    }
    
    /**
     * Get the Info of an instance.
     * <p>
     * Info means the Dataset with all attributes stored in DB for the instance 
     * (instance, series, study and patient attributes)
     * 
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public Dataset getInstanceInfo(String iuid, boolean supplement) throws FinderException {
        InstanceLocal il = instanceHome.findBySopIuid(iuid);
		return getInstanceInfo(il, supplement);
    }

    /**
     * Get the Info of an instance.
     * <p>
     * Info means the Dataset with all attributes stored in DB for the instance 
     * (instance, series, study and patient attributes)
     * 
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public List listInstanceInfos(String[] iuids, boolean supplement) throws FinderException {
        Collection c = instanceHome.listByIUIDs(iuids);
        return toDatasetList(c, supplement);
    }

    /**
     * Get the Info of an instance.
     * <p>
     * Info means the Dataset with all attributes stored in DB for the instance 
     * (instance, series, study and patient attributes)
     * 
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public List listInstanceInfosByPatientAndSRCode(String pid, String issuer, Collection codes, Collection cuids) throws FinderException {
        PatientLocal pat = this.getPatientLocal(pid, issuer);
        if ( pat == null ) return null;
        List srCodes = null;
        if ( codes != null ) {
            srCodes = new ArrayList( codes.size() );
            Dataset ds;
            for ( Iterator iter = codes.iterator() ; iter.hasNext() ; ) {
                ds = (Dataset) iter.next();
                Collection c = codeHome.findByValueAndDesignator(
                        ds.getString(Tags.CodeValue),
                        ds.getString(Tags.CodingSchemeDesignator));
                for (Iterator iterator = c.iterator(); iterator.hasNext();) {
                    CodeLocal code = (CodeLocal) iterator.next();
                    srCodes.add(code.getPk());
                }
            }
            if (srCodes.isEmpty()) {
                return Collections.EMPTY_LIST;
            }
        }
        Collection c = instanceHome.listByPatientAndSRCode(pat, srCodes, cuids);
        return toDatasetList(c, false);
    }
    
    private PatientLocal getPatientLocal(String pid, String issuer) throws FinderException {
        try {
            return patHome.selectPatient(pid, issuer);
        } catch (ObjectNotFoundException onfe) {
            return null;
        }
    }

    /**
     * Get the Info of an instance.
     * <p>
     * Info means the Dataset with all attributes stored in DB for the instance 
     * (instance, series, study and patient attributes)
     * 
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public List listInstanceInfosByStudyAndSRCode(String suid, String cuid, String code, String designator, boolean supplement) throws FinderException {
        Collection c = instanceHome.findByStudyAndSrCode(suid, cuid, code, designator);
        return toDatasetList(c, supplement);
    }

    private List toDatasetList(Collection c, boolean supplement) {
        ArrayList list = new ArrayList(c.size());
        InstanceLocal il;
        for ( Iterator iter = c.iterator(); iter.hasNext() ; ) {
            il = (InstanceLocal) iter.next();
            list.add(getInstanceInfo(il, supplement));
        }
        return list;
    }

    private Dataset getInstanceInfo(InstanceLocal il, boolean supplement) {
        Dataset ds = il.getAttributes(supplement);
        SeriesLocal series = il.getSeries();
        ds.putAll( series.getAttributes(supplement));
        StudyLocal study = series.getStudy();
        ds.putAll( study.getAttributes(supplement));
        ds.putAll( study.getPatient().getAttributes(supplement));
        return ds;
    }
    
    /**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public List listStudiesOfPatient(long patientPk) throws FinderException {
        Collection c =
            patHome.findByPrimaryKey(new Long(patientPk)).getStudies();
        List result = new ArrayList(c.size());
        StudyLocal study;
        for (Iterator it = c.iterator(); it.hasNext();) {
            study = (StudyLocal) it.next();
           	result.add(study.getAttributes(true));
        }
        return result;
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public List listSeriesOfStudy(long studyPk) throws FinderException {
        Collection c =
            studyHome.findByPrimaryKey(new Long(studyPk)).getSeries();
        List result = new ArrayList(c.size());
        SeriesLocal series;
        for (Iterator it = c.iterator(); it.hasNext();) {
            series = (SeriesLocal) it.next();
           	result.add( mergeMPPSAttr(series.getAttributes(true), series.getMpps()) );
        }
        return result;
    }

    /**
	 * @param attributes
	 * @param ppsIuid
	 * @return
	 */
	private Dataset mergeMPPSAttr(Dataset ds, MPPSLocal mpps) {
		if ( mpps != null ) {
			ds.putAll( mpps.getAttributes().subSet(MPPS_FILTER_TAGS));
		}
		return ds;
	}

	/**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public List listInstancesOfSeries(long seriesPk) throws FinderException {
        Collection c =
            instanceHome.findBySeriesPk(new Long(seriesPk));
        List result = new ArrayList(c.size());
        InstanceLocal inst;
        for (Iterator it = c.iterator(); it.hasNext();) {
            inst = (InstanceLocal) it.next();
           	result.add(inst.getAttributes(true));
        }
        return result;
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public List listFilesOfInstance(long instancePk) throws FinderException {
        Collection c =
            instanceHome.findByPrimaryKey(new Long(instancePk)).getFiles();
        List result = new ArrayList(c.size());
        for (Iterator it = c.iterator(); it.hasNext();) {
            FileLocal file = (FileLocal) it.next();
            result.add(file.getFileDTO());
        }
        return result;
    }

    
    /**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public List listStudiesOfPrivatePatient(long patientPk) throws FinderException {
        Collection c =
            privPatHome.findByPrimaryKey(new Long(patientPk)).getStudies();
        List result = new ArrayList(c.size());
        PrivateStudyLocal study;
        Dataset ds;
        for (Iterator it = c.iterator(); it.hasNext();) {
            study = (PrivateStudyLocal) it.next();
            ds = study.getAttributes();
            ds.setPrivateCreatorID(PrivateTags.CreatorID);
            ds.putOB(PrivateTags.StudyPk, Convert.toBytes(study.getPk().longValue()) );
            ds.setPrivateCreatorID(null);
            result.add(ds);
        }
        return result;
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public List listSeriesOfPrivateStudy(long studyPk) throws FinderException {
        Collection c =
            privStudyHome.findByPrimaryKey(new Long(studyPk)).getSeries();
        List result = new ArrayList(c.size());
        PrivateSeriesLocal series;
        Dataset ds;
        Dataset refPPS;
        String ppsUID;
        for (Iterator it = c.iterator(); it.hasNext();) {
            series = (PrivateSeriesLocal) it.next();
            ds = series.getAttributes();
            ds.setPrivateCreatorID(PrivateTags.CreatorID);
            ds.putOB(PrivateTags.SeriesPk, Convert.toBytes(series.getPk().longValue()) );
            ds.setPrivateCreatorID(null);
            refPPS = ds.getItem(Tags.RefPPSSeq);
            if ( refPPS != null) {
                ppsUID = refPPS.getString(Tags.RefSOPInstanceUID);
                if ( ppsUID != null ) {
                    try {
                        this.mergeMPPSAttr(ds, mppsHome.findBySopIuid(ppsUID));
                    } catch ( FinderException ignore ) {}
                }
            }
            result.add( ds );
        }
        return result;
    }

	/**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public List listInstancesOfPrivateSeries(long seriesPk) throws FinderException {
        Collection c =
            privSeriesHome.findByPrimaryKey(new Long(seriesPk)).getInstances();
        List result = new ArrayList(c.size());
        PrivateInstanceLocal inst;
        Dataset ds;
        for (Iterator it = c.iterator(); it.hasNext();) {
            inst = (PrivateInstanceLocal) it.next();
            ds = inst.getAttributes();
            ds.setPrivateCreatorID(PrivateTags.CreatorID);
            ds.putOB(PrivateTags.InstancePk, Convert.toBytes(inst.getPk().longValue()) );
            result.add(ds);
            ds.setPrivateCreatorID(null);
        }
        return result;
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public List listFilesOfPrivateInstance(long instancePk) throws FinderException {
        Collection c =
            privInstanceHome.findByPrimaryKey(new Long(instancePk)).getFiles();
        List result = new ArrayList(c.size());
        PrivateFileLocal file;
        for (Iterator it = c.iterator(); it.hasNext();) {
            file = (PrivateFileLocal) it.next();
            result.add(file.getFileDTO());
        }
        return result;
    }

	/**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public List[] listInstanceFilesToRecover(long pk) throws FinderException {
    	List[] result = new List[]{new ArrayList(),new ArrayList()};
    	addInstanceFilesToRecover(privInstanceHome.findByPrimaryKey(new Long(pk)), result, null);
        return result;
    }
    
	/**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public List[] listSeriesFilesToRecover(long pk) throws FinderException {
    	List[] result = new List[]{new ArrayList(),new ArrayList()};
    	addSeriesToRecover(privSeriesHome.findByPrimaryKey(new Long(pk)), result, null);
        return result;
    }
    
	/**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public List[] listStudyFilesToRecover(long pk) throws FinderException {
    	List[] result = new List[]{new ArrayList(),new ArrayList()};
    	addStudyToRecover(privStudyHome.findByPrimaryKey(new Long(pk)), result, null);
        return result;
    }

	/**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public List[] listPatientFilesToRecover(long pk) throws FinderException {
    	List[] result = new List[]{new ArrayList(),new ArrayList()};
    	PrivatePatientLocal pat = privPatHome.findByPrimaryKey(new Long(pk));
    	for ( Iterator iter = pat.getStudies().iterator() ; iter.hasNext() ; ) {
        	addStudyToRecover((PrivateStudyLocal)iter.next(), result, pat.getAttributes());
    	}
        return result;
    }
    
    private void addStudyToRecover(PrivateStudyLocal study, List[] result, Dataset patAttrs) throws FinderException {
    	Dataset studyAttrs = study.getAttributes();
    	if ( patAttrs == null ) patAttrs = study.getPatient().getAttributes();
    	studyAttrs.putAll(patAttrs);
    	for ( Iterator iter = study.getSeries().iterator() ; iter.hasNext() ; ) {
        	addSeriesToRecover((PrivateSeriesLocal)iter.next(), result, studyAttrs);
    	}
    }
    private void addSeriesToRecover(PrivateSeriesLocal series, List[] result, Dataset studyAttrs) throws FinderException {
    	Dataset seriesAttrs = series.getAttributes();
    	if ( studyAttrs == null ) {
    		studyAttrs = series.getStudy().getAttributes();
    		studyAttrs.putAll( series.getStudy().getPatient().getAttributes() );
    	}
    	seriesAttrs.putAll(studyAttrs);
    	for ( Iterator iter = series.getInstances().iterator() ; iter.hasNext() ; ) {
        	addInstanceFilesToRecover((PrivateInstanceLocal)iter.next(), result, seriesAttrs);
    	}
    }
	private void addInstanceFilesToRecover(PrivateInstanceLocal instance, List[] result, Dataset seriesAttrs) throws FinderException {
    	Dataset instanceAttrs = instance.getAttributes();
    	if ( seriesAttrs == null ) {
    		seriesAttrs = instance.getSeries().getAttributes();
    		seriesAttrs.putAll(instance.getSeries().getStudy().getAttributes());
    		seriesAttrs.putAll( instance.getSeries().getStudy().getPatient().getAttributes() );
    	}
    	instanceAttrs.putAll(seriesAttrs);
    	Iterator iter = listFilesOfPrivateInstance( instance.getPk().longValue() ).iterator();
    	if ( iter.hasNext() ) {
			result[0].add( iter.next() );
			result[1].add( instanceAttrs );
    	}
	}
    
	/**
     * @throws FinderException
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public Dataset getSOPInstanceRefMacro( long studyPk, boolean insertModality ) throws FinderException {
    	Dataset ds = dof.newDataset();
    	StudyLocal sl = studyHome.findByPrimaryKey( new Long( studyPk ) );
    	ds.putUI( Tags.StudyInstanceUID, sl.getStudyIuid() );
		DcmElement refSerSq = ds.putSQ(Tags.RefSeriesSeq);
		Iterator iterSeries = sl.getSeries().iterator();
		SeriesLocal series;
		String aet;
		int pos;
		while ( iterSeries.hasNext() ) {
			series = (SeriesLocal) iterSeries.next();
			Dataset serDS = refSerSq.addNewItem();
			serDS.putUI(Tags.SeriesInstanceUID, series.getSeriesIuid() );
			aet = series.getRetrieveAETs(); 
			if ( aet != null ) {
				pos = aet.indexOf('\\');
				if ( pos != -1 ) aet = aet.substring(0,pos);
			}
			serDS.putAE( Tags.RetrieveAET, aet );
			serDS.putAE( Tags.StorageMediaFileSetID, series.getFilesetId() );
			serDS.putAE( Tags.StorageMediaFileSetUID, series.getFilesetIuid() );
			if ( insertModality ) {
				serDS.putCS( Tags.Modality, series.getModality() );
				serDS.putIS( Tags.SeriesNumber, series.getSeriesNumber() ); //Q&D 
			}
			DcmElement refSopSq = serDS.putSQ(Tags.RefSOPSeq);
			Collection col = series.getInstances();
			List l = ( col instanceof List ) ? (List)col : new ArrayList(col);
			Collections.sort( l, new InstanceNumberComparator() );
			Iterator iterInstances = l.iterator();
			InstanceLocal instance;
			while ( iterInstances.hasNext() ) {
				instance = (InstanceLocal) iterInstances.next();
				Dataset instDS = refSopSq.addNewItem();
				instDS.putUI( Tags.RefSOPInstanceUID, instance.getSopIuid() );
				instDS.putUI( Tags.RefSOPClassUID, instance.getSopCuid() );
			}
		} 
    	return ds;
    }

	/**
	 * Get a collection of SOP Instance Reference Macro Datasets.
	 * <p>
	 * The parameter <code>instanceUIDs</code> can either use SOP Instance UIDs (String) or 
	 * Instance.pk values (Long).
	 * 
     * @throws FinderException
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public Collection getSOPInstanceRefMacros( Collection instanceUIDs ) throws FinderException {
    	HashMap result = new HashMap();
    	HashMap mapRefSopSQ = new HashMap();
    	InstanceLocal instance;
    	SeriesLocal series;
    	StudyLocal study;
    	Object o;
    	for ( Iterator iter = instanceUIDs.iterator() ; iter.hasNext() ; ) {
    		o = iter.next();
    		instance = ( o instanceof Long ) ? 
    						instanceHome.findByPrimaryKey((Long)o) : 
    						instanceHome.findBySopIuid( o.toString() );
    		series = instance.getSeries();
    		study = series.getStudy();
        	Dataset ds = (Dataset) result.get( study.getPk() );
        	if ( ds == null ) {
        		ds = dof.newDataset();
        		ds.putUI( Tags.StudyInstanceUID, study.getStudyIuid() );
        		ds.putSQ(Tags.RefSeriesSeq);
        		result.put( study.getPk(), ds );
        	}
        	DcmElement refSopSq = (DcmElement)mapRefSopSQ.get( series.getPk() );
        	if ( refSopSq == null ) {
        		DcmElement refSeriesSq = ds.get(Tags.RefSeriesSeq);
        		Dataset serDS = refSeriesSq.addNewItem();
    			serDS.putUI(Tags.SeriesInstanceUID, series.getSeriesIuid() );
    			String aet = series.getRetrieveAETs();
    			if ( aet != null ) {
    				int pos = aet.indexOf('\\');
    				if ( pos != -1 ) aet = aet.substring(0,pos);
    				serDS.putAE( Tags.RetrieveAET, aet );
    			}
    			serDS.putAE( Tags.StorageMediaFileSetID, series.getFilesetId() );
    			serDS.putAE( Tags.StorageMediaFileSetUID, series.getFilesetIuid() );
    			refSopSq = serDS.putSQ(Tags.RefSOPSeq);
    			mapRefSopSQ.put( series.getPk(), refSopSq );
        	}
			Dataset instDS = refSopSq.addNewItem();
			instDS.putUI( Tags.RefSOPInstanceUID, instance.getSopIuid() );
			instDS.putUI( Tags.RefSOPClassUID, instance.getSopCuid() );
     	}
    	return result.values();
    }
    
    /**
     * @throws FinderException
     *
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public Dataset getPatientForStudy(long studyPk) throws FinderException {
    	StudyLocal sl = studyHome.findByPrimaryKey( new Long( studyPk ) );
    	return sl.getPatient().getAttributes(false);
    }    

    /**
     * @throws FinderException
     *
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public Dataset getPatientForStudy(String studyIUID) throws FinderException {
    	StudyLocal sl = studyHome.findByStudyIuid( studyIUID );
    	return sl.getPatient().getAttributes(false);
    }    
    
	/**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public boolean isStudyAvailable(long studyPk, int availability) throws FinderException {
    	StudyLocal sl = studyHome.findByPrimaryKey( new Long( studyPk ) );
    	return sl.isStudyAvailable(availability);
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public Dataset getHeaderInfo(long patPk, long studyPk, long seriesPk, long instancePk) throws FinderException {
    	Dataset ds = dof.newDataset();
        if ( patPk != -1 )
        	ds.putAll( patHome.findByPrimaryKey(new Long(patPk)).getAttributes(true) );
        if ( studyPk != -1 )
        	ds.putAll( studyHome.findByPrimaryKey(new Long(studyPk)).getAttributes(true) );
        if ( seriesPk != -1 )
        	ds.putAll( seriesHome.findByPrimaryKey(new Long(seriesPk)).getAttributes(true) );
        if ( instancePk != -1 )
        	ds.putAll( instanceHome.findByPrimaryKey(new Long(instancePk)).getAttributes(true) );
        return ds;
    }
    
    /**
     * @throws FinderException 
     * @ejb.interface-method
     * @ejb.transaction type="Required"
     */
    public Dataset getXDSManifestInfo(Dataset manifest) throws FinderException {
        DcmElement crpeSQ = manifest.get(Tags.CurrentRequestedProcedureEvidenceSeq);
        if (crpeSQ != null && crpeSQ.hasItems()) {
            DcmElement sq = crpeSQ.getItem().get(Tags.RefSeriesSeq);
            if (sq != null && sq.hasItems()) {
                String seriesIUID = sq.getItem().getString(Tags.SeriesInstanceUID);
                SeriesLocal series = seriesHome.findBySeriesIuid(seriesIUID);
                Dataset info = series.getAttributes(true);
                info.putAll(series.getStudy().getAttributes(true));
                info.putAll(series.getStudy().getPatient().getAttributes(true));
                return info;
            }
        }
        return null;
    }
    
	public class InstanceNumberComparator implements Comparator {

		public InstanceNumberComparator() {
		}

		/**
		 * Compares the instance number of two InstanceLocal objects.
		 * <p>
		 * Compares its two arguments for order. Returns a negative integer, zero, or a positive integer 
		 * as the first argument is less than, equal to, or greater than the second.
		 * <p>
		 * Throws an Exception if one of the arguments is null or neither a InstanceContainer or InstanceLocal object.<br>
		 * Also both arguments must be of the same type!
		 * <p>
		 * If arguments are of type InstanceLocal, the getInstanceSize Method of InstanceCollector is used to get filesize.
		 *  
		 * @param arg0 	First argument
		 * @param arg1	Second argument
		 * 
		 * @return <0 if arg0<arg1, 0 if equal and >0 if arg0>arg1
		 */
		public int compare(Object arg0, Object arg1) {
			String in0 = ((InstanceLocal) arg0).getInstanceNumber();
			String in1 = ((InstanceLocal) arg1).getInstanceNumber();
			if ( in0 == null ) {
			    return in1 == null ? 0 : 1;
			} else if ( in1 == null ) {
			    return 0;
			} else {
			    return new Integer(in0).compareTo( new Integer(in1) );
			}
		}
		
	}// end class
    
}
