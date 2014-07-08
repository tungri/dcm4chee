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

package org.dcm4chex.archive.ejb.entity;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.EntityBean;
import javax.ejb.EntityContext;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.log4j.Logger;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.PersonName;
import org.dcm4che.dict.Tags;
import org.dcm4che.net.DcmServiceException;
import org.dcm4cheri.util.StringUtils;
import org.dcm4chex.archive.common.Availability;
import org.dcm4chex.archive.common.DatasetUtils;
import org.dcm4chex.archive.common.PrivateTags;
import org.dcm4chex.archive.ejb.conf.AttributeFilter;
import org.dcm4chex.archive.ejb.interfaces.CodeLocalHome;
import org.dcm4chex.archive.ejb.interfaces.IssuerLocal;
import org.dcm4chex.archive.ejb.interfaces.IssuerLocalHome;
import org.dcm4chex.archive.ejb.interfaces.MediaDTO;
import org.dcm4chex.archive.ejb.interfaces.MediaLocal;
import org.dcm4chex.archive.ejb.interfaces.PatientLocal;
import org.dcm4chex.archive.ejb.interfaces.SeriesLocal;
import org.dcm4chex.archive.exceptions.ConfigurationException;
import org.dcm4chex.archive.util.AETs;
import org.dcm4chex.archive.util.Convert;

/**
 * @author <a href="mailto:gunter@tiani.com">Gunter Zeilinger</a>
 * 
 * @ejb.bean name="Study" 
 *           type="CMP" 
 *           view-type="local"
 *           local-jndi-name="ejb/Study" 
 *           primkey-field="pk"
 * @jboss.container-configuration name="Instance Per Transaction CMP 2.x EntityBean"
 * @ejb.persistence table-name="study"
 * @ejb.transaction type="Required"
 * @jboss.entity-command name="hsqldb-fetch-key"
 * @jboss.audit-created-time field-name="createdTime"
 * @jboss.audit-updated-time field-name="updatedTime"
 *
 * @ejb.finder signature="Collection findAll()"
 *             query="SELECT OBJECT(s) FROM Study AS s"
 *             transaction-type="Supports"
 *
 * @ejb.finder transaction-type="Supports"
 *             signature="org.dcm4chex.archive.ejb.interfaces.StudyLocal findByStudyIuid(java.lang.String uid)"
 *             query="SELECT OBJECT(a) FROM Study AS a WHERE a.studyIuid = ?1"
 * @ejb.finder signature="java.util.Collection findStudiesOnMedia(org.dcm4chex.archive.ejb.interfaces.MediaLocal media)"
 *             query="SELECT DISTINCT OBJECT(st) FROM Study st, IN(st.series) s, IN(s.instances) i WHERE i.media = ?1"
 *             transaction-type="Supports"
 * @ejb.finder signature="java.util.Collection findStudiesNotOnMedia(java.sql.Timestamp minCreatedTime)"
 *             query="SELECT DISTINCT OBJECT(st) FROM Study st, IN(st.series) s, IN(s.instances) i WHERE i.media IS NULL and st.createdTime < ?1 "
 *             transaction-type="Supports"
 * @jboss.query signature="org.dcm4chex.archive.ejb.interfaces.StudyLocal findByStudyIuid(java.lang.String uid)"
 *              strategy="on-find"
 *              eager-load-group="*"
 * @ejb.finder signature="java.util.Collection findStudyToCheck(java.sql.Timestamp minCreatedTime, java.sql.Timestamp maxCreatedTime, java.sql.Timestamp checkedBefore, int limit)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findStudyToCheck(java.sql.Timestamp minCreatedTime, java.sql.Timestamp maxCreatedTime, java.sql.Timestamp checkedBefore, int limit)"
 *              query="SELECT OBJECT(s) FROM Study AS s WHERE (s.createdTime BETWEEN ?1 AND ?2) AND (s.timeOfLastConsistencyCheck IS NULL OR s.timeOfLastConsistencyCheck < ?3) LIMIT ?4"
 *  
 * @ejb.finder signature="java.util.Collection findStudiesWithStatus(int status, java.sql.Timestamp createdBefore, int limit)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findStudiesWithStatus(int status, java.sql.Timestamp createdBefore, int limit)"
 *              query="SELECT OBJECT(s) FROM Study AS s WHERE (s.studyStatus = ?1) AND (s.createdTime < ?2) LIMIT ?3"
 *  
 * @ejb.finder signature="java.util.Collection findStudiesFromAE(java.lang.String sourceAET, int limit)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findStudiesFromAE(java.lang.String sourceAET, int limit)"
 *              query="SELECT DISTINCT OBJECT(st) FROM Study AS st, IN(st.series) s WHERE (s.sourceAET = ?1) LIMIT ?2"
 *              
 * @ejb.finder signature="java.util.Collection findStudiesWithStatusFromAE(int status, java.lang.String sourceAET, int limit)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findStudiesWithStatusFromAE(int status, java.lang.String sourceAET, int limit)"
 *              query="SELECT DISTINCT OBJECT(st) FROM Study AS st, IN(st.series) s WHERE (st.studyStatus = ?1) AND (s.sourceAET = ?2) LIMIT ?3"
 *
 * @ejb.finder signature="java.util.Collection findStudiesWithFilesOnFileSystem(org.dcm4chex.archive.ejb.interfaces.FileSystemLocal fs, int offset, int limit)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findStudiesWithFilesOnFileSystem(org.dcm4chex.archive.ejb.interfaces.FileSystemLocal fs, int offset, int limit)"
 *             query="SELECT DISTINCT OBJECT(st) FROM Study AS st, IN(st.series) s, IN(s.instances) i, IN(i.files) f WHERE f.fileSystem = ?1 ORDER BY st.pk OFFSET ?2 LIMIT ?3"
  *
 * @jboss.query signature="int ejbSelectNumberOfStudyRelatedSeries(java.lang.Long pk)"
 * 	            query="SELECT COUNT(s) FROM Series s WHERE s.study.pk = ?1"
 * @jboss.query signature="int ejbSelectNumberOfStudyRelatedInstances(java.lang.Long pk)"
 *              query="SELECT COUNT(i) FROM Instance i WHERE i.series.study.pk = ?1"
 * @jboss.query signature="int ejbSelectNumberOfStudyRelatedInstancesOnMediaWithStatus(java.lang.Long pk, int status)"
 *              query="SELECT COUNT(i) FROM Instance i WHERE i.series.study.pk = ?1 AND i.media.mediaStatus = ?2"
 * @jboss.query signature="int ejbSelectNumberOfCommitedInstances(java.lang.Long pk)"
 * 	            query="SELECT COUNT(i) FROM Instance i WHERE i.series.study.pk = ?1 AND i.commitment = TRUE"
 * @jboss.query signature="int ejbSelectNumberOfStudyRelatedInstancesOnROFS(java.lang.Long pk, int status)"
 *              query="SELECT COUNT(DISTINCT i) FROM Instance i, IN(i.files) f WHERE i.series.study.pk = ?1 AND f.fileStatus = ?2 AND f.fileSystem.availability <> 3 AND f.fileSystem.status = 2"
 * @jboss.query signature="int ejbSelectAvailability(java.lang.Long pk)"
 * 	            query="SELECT MAX(s.availability) FROM Series s WHERE s.study.pk = ?1"
 * @jboss.query signature="java.lang.Long ejbSelectStudyFileSize(java.lang.Long studyPk, java.lang.Long fsPk)"
 * 	            query="SELECT SUM(f.fileSize) FROM File f WHERE f.instance.series.study.pk = ?1 AND f.fileSystem.pk = ?2"
 * @jboss.query signature="int ejbSelectNumberOfStudyRelatedInstancesForAvailability(java.lang.Long pk, int availability)"
 *              query="SELECT COUNT(DISTINCT i) FROM Instance i, IN(i.files) f WHERE i.series.study.pk = ?1 AND f.fileSystem.availability = ?2"
 * @jboss.query signature="int ejbSelectNumberOfReceivingSeries(java.lang.Long pk)"
 *                  query="SELECT COUNT(s) FROM Series s WHERE s.study.pk = ?1 AND s.seriesStatus <> 0"
 *
 * @ejb.ejb-ref ejb-name="Code" view-type="local" ref-name="ejb/Code"
 * @ejb.ejb-ref ejb-name="Issuer" view-type="local" ref-name="ejb/Issuer"
 *
 */
public abstract class StudyBean implements EntityBean {
    private class StudyDerivedFieldsUpdater extends
            org.dcm4chex.archive.common.StudyDerivedFieldsUpdater implements
            Serializable {
        @Override
        protected int getNumberOfStudyRelatedInstances() {
            return StudyBean.this.getNumberOfStudyRelatedInstances();
        }

        @Override
        protected long getPk() {
            return StudyBean.this.getPk();
        }

        @Override
        protected String getStudyIuid() {
            return StudyBean.this.getStudyIuid();
        }

        @Override
        protected int getAvailability() {
            return StudyBean.this.getAvailability();
        }

        @Override
        protected int deriveAvailability() throws FinderException {
            return ejbSelectAvailability(getPk());
        }

        @Override
        protected void setAvailability(int availability) {
            StudyBean.this.setAvailability(availability);
        }

        @Override
        protected String getExternalRetrieveAET() {
            return StudyBean.this.getExternalRetrieveAET();
        }

        @Override
        protected void setExternalRetrieveAET(String aet) {
            StudyBean.this.setExternalRetrieveAET(aet);
        }

        @Override
        protected Set<String> deriveExternalRetrieveAETs() throws FinderException {
            return ejbSelectExternalRetrieveAETs(getPk());
        }

        @Override
        protected String getModalitiesInStudy() {
            return StudyBean.this.getModalitiesInStudy();
        }

        @Override
        protected void setModalitiesInStudy(String mds) {
            StudyBean.this.setModalitiesInStudy(mds);
        }

        @Override
        protected Set<String> deriveModalitiesInStudies() throws FinderException {
            return ejbSelectModalityInStudies(getPk());
        }

        @Override
        protected void setNumberOfStudyRelatedInstances(int numI) {
            StudyBean.this.setNumberOfStudyRelatedInstances(numI);
        }

        @Override
        protected int deriveNumberOfStudyRelatedInstances()
                throws FinderException {
            return ejbSelectNumberOfStudyRelatedInstances(getPk());
        }

        @Override
        protected int getNumberOfStudyRelatedSeries() {
            return StudyBean.this.getNumberOfStudyRelatedSeries();
        }

        @Override
        protected void setNumberOfStudyRelatedSeries(int numS) {
            StudyBean.this.setNumberOfStudyRelatedSeries(numS);
        }

        @Override
        protected int deriveNumberOfStudyRelatedSeries() throws FinderException {
            return ejbSelectNumberOfStudyRelatedSeries(getPk());
        }

        @Override
        protected void setRetrieveAETs(String aets) {
            StudyBean.this.setRetrieveAETs(aets);
        }

        @Override
        protected String getRetrieveAETs() {
            return StudyBean.this.getRetrieveAETs();
        }

        @Override
        protected Set<String> deriveSeriesRetrieveAETs() throws FinderException {
            return ejbSelectSeriesRetrieveAETs(getPk());
        }

        @Override
        protected void setSopClassesInStudy(String uids) {
            StudyBean.this.setSopClassesInStudy(uids);
        }

        @Override
        protected String getSopClassesInStudy() {
            return StudyBean.this.getSopClassesInStudy();
        }

        @Override
        protected Set<String> deriveSOPClassesInStudies()
                throws FinderException {
            return ejbSelectSOPClassesInStudies(getPk());
        }
    }

    private static final Logger log = Logger.getLogger(StudyBean.class);

    private static final Class[] STRING_PARAM = new Class[] { String.class };
    
    private CodeLocalHome codeHome;
    private StudyDerivedFieldsUpdater studyDerivedFieldsUpdater;

    private IssuerLocalHome issuerHome;
    /**
     * Default constructor. 
     */
    public StudyBean() {
        studyDerivedFieldsUpdater = new StudyDerivedFieldsUpdater();
    }

    public void setEntityContext(EntityContext ctx) {
        Context jndiCtx = null;
        try {
            jndiCtx = new InitialContext();
            codeHome = (CodeLocalHome) jndiCtx.lookup("java:comp/env/ejb/Code");
            issuerHome = (IssuerLocalHome) jndiCtx.lookup("java:comp/env/ejb/Issuer");
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

    public void unsetEntityContext() {
        codeHome = null;
        issuerHome = null;
    }
    
    /**
     * @ejb.interface-method
     * @ejb.pk-field
     * @ejb.persistence column-name="pk"
     * @jboss.persistence auto-increment="true"
     *
     */
    public abstract Long getPk();
    public abstract void setPk(Long pk);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="created_time"
     */
    public abstract java.sql.Timestamp getCreatedTime();
    public abstract void setCreatedTime(java.sql.Timestamp time);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="updated_time"
     */
    public abstract java.sql.Timestamp getUpdatedTime();
    public abstract void setUpdatedTime(java.sql.Timestamp time);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="study_iuid"
     */
    public abstract String getStudyIuid();
    public abstract void setStudyIuid(String uid);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="study_id"
     */
    public abstract String getStudyId();
    public abstract void setStudyId(String uid);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="study_datetime"
     */
    public abstract java.sql.Timestamp getStudyDateTime();
    public abstract void setStudyDateTime(java.sql.Timestamp dateTime);
    
    private void setStudyDateTime(java.util.Date date) {
        setStudyDateTime(date != null
                ? new java.sql.Timestamp(date.getTime())
                : null);
    }
    
    /**
     * Accession Number
     *
     * @ejb.interface-method
     * @ejb.persistence column-name="accession_no"
     */
    public abstract String getAccessionNumber();
    public abstract void setAccessionNumber(String no);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="ref_physician"
     */
    public abstract String getReferringPhysicianName();
    public abstract void setReferringPhysicianName(String name);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="ref_phys_fn_sx"
     */
    public abstract String getReferringPhysicianFamilyNameSoundex();

    /**
     * @ejb.interface-method
     */
    public abstract void setReferringPhysicianFamilyNameSoundex(String name);
        
    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="ref_phys_gn_sx"
     */
    public abstract String getReferringPhysicianGivenNameSoundex();

    /**
     * @ejb.interface-method
     */
    public abstract void setReferringPhysicianGivenNameSoundex(String name);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="ref_phys_i_name"
     */
    public abstract String getReferringPhysicianIdeographicName();
    public abstract void setReferringPhysicianIdeographicName(String name);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="ref_phys_p_name"
     */
    public abstract String getReferringPhysicianPhoneticName();
    public abstract void setReferringPhysicianPhoneticName(String name);
        
    /**
     * Study Description
     *
     * @ejb.interface-method
     * @ejb.persistence column-name="study_desc"
     */
    public abstract String getStudyDescription();

    public abstract void setStudyDescription(String description);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="study_custom1"
     */
    public abstract String getStudyCustomAttribute1();
    public abstract void setStudyCustomAttribute1(String value);

    /**
     * @ejb.persistence column-name="study_custom2"
     */
    public abstract String getStudyCustomAttribute2();
    public abstract void setStudyCustomAttribute2(String value);

    /**
     * @ejb.persistence column-name="study_custom3"
     */
    public abstract String getStudyCustomAttribute3();
    public abstract void setStudyCustomAttribute3(String value);

    /**
     * Study Status
     *
     * @ejb.interface-method
     * @ejb.persistence column-name="study_status"
     */
    public abstract int getStudyStatus();

    /**
     * @ejb.interface-method
     */
    public abstract void setStudyStatus(int status);
    
    /**
     * Study Status ID
     *
     * @ejb.interface-method
     * @ejb.persistence column-name="study_status_id"
     */
    public abstract String getStudyStatusId();

    /**
     * @ejb.interface-method
     */
    public abstract void setStudyStatusId(String statusId);
    
    
    /**
     * Number Of Study Related Series
     *
     * @ejb.interface-method
     * @ejb.persistence column-name="num_series"
     * 
     */
    public abstract int getNumberOfStudyRelatedSeries();

    public abstract void setNumberOfStudyRelatedSeries(int num);

    /**
     * Number Of Study Related Instances
     *
     * @ejb.interface-method
     * @ejb.persistence column-name="num_instances"
     * 
     */
    public abstract int getNumberOfStudyRelatedInstances();

    public abstract void setNumberOfStudyRelatedInstances(int num);

    /**
     * Study DICOM Attributes
     *
     * @ejb.persistence column-name="study_attrs"
     * 
     */
    public abstract byte[] getEncodedAttributes();

    public abstract void setEncodedAttributes(byte[] bytes);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="fileset_iuid"
     */
    public abstract String getFilesetIuid();

    public abstract void setFilesetIuid(String iuid);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="fileset_id"
     */
    public abstract String getFilesetId();

    public abstract void setFilesetId(String id);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="ext_retr_aet"
     */
    public abstract String getExternalRetrieveAET();

    /**
     * @ejb.interface-method
     */ 
    public abstract void setExternalRetrieveAET(String aet);

    /**
     * Retrieve AETs
     *
     * @ejb.interface-method
     * @ejb.persistence column-name="retrieve_aets"
     */
    public abstract String getRetrieveAETs();

    public abstract void setRetrieveAETs(String aets);

    /**
     * Instance Availability
     *
     * @ejb.persistence column-name="availability"
     */
    public abstract int getAvailability();

    /**
     * @ejb.interface-method
     */
    public int getAvailabilitySafe() {
        try {
            return getAvailability();
        } catch (NullPointerException npe) {
            return 0;
        }
    }

    public abstract void setAvailability(int availability);

    /**
     * Modalities In Study
     *
     * @ejb.interface-method
     * @ejb.persistence column-name="mods_in_study"
     */
    public abstract String getModalitiesInStudy();
    
    public abstract void setModalitiesInStudy(String mds);

    /**
     * SOP Classes In Study
     *
     * @ejb.interface-method
     * @ejb.persistence column-name="cuids_in_study"
     */
    public abstract String getSopClassesInStudy();
    
    public abstract void setSopClassesInStudy(String uids);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="checked_time"
     */
    public abstract java.sql.Timestamp getTimeOfLastConsistencyCheck();

    /**
     * @ejb.interface-method
     */
    public abstract void setTimeOfLastConsistencyCheck(java.sql.Timestamp time);
    
    /**
     * @ejb.interface-method view-type="local"
     * 
     * @ejb.relation name="patient-study"
     *               role-name="study-of-patient"
     *               cascade-delete="yes"
     *
     * @jboss.relation fk-column="patient_fk"
     *                 related-pk-field="pk"
     * 
     * @param patient patient of this study
     */
    public abstract void setPatient(PatientLocal patient);

    /**
     * @ejb.interface-method
     * 
     * @return patient of this study
     */
    public abstract PatientLocal getPatient();

    /**
     * @ejb.interface-method
     *
     * @param series all series of this study
     */
    public abstract void setSeries(java.util.Collection series);

    /**
     * @ejb.interface-method
     * @ejb.relation name="study-series"
     *               role-name="study-has-series"
     *    
     * @return all series of this study
     */
    public abstract java.util.Collection getSeries();

    /**
     * @ejb.relation name="study-pcode" role-name="study-with-pcode"
     *               target-ejb="Code" target-role-name="pcode-for-study"
     *               target-multiple="yes"
     * @jboss.relation-table table-name="rel_study_pcode"
     * @jboss.relation fk-column="pcode_fk" related-pk-field="pk"     
     * @jboss.target-relation fk-column="study_fk" related-pk-field="pk"     
     */    
    public abstract java.util.Collection getProcedureCodes();
    public abstract void setProcedureCodes(java.util.Collection codes);
    
    /**
     * @ejb.relation name="study-issuer-of-accno" role-name="study-with-issuer-of-accno"
     *               target-ejb="Issuer" target-role-name="issuer-of-study-accno"
     *               target-multiple="yes"
     * @jboss.relation fk-column="accno_issuer_fk" related-pk-field="pk"
     */
    public abstract IssuerLocal getIssuerOfAccessionNumber();
    public abstract void setIssuerOfAccessionNumber(IssuerLocal issuer);

    /**
     * Create study.
     *
     * @ejb.create-method
     */
    public Long ejbCreate(Dataset ds, PatientLocal patient)
            throws CreateException {    	
        setAttributes(ds);
       return null;
    }

    public void ejbPostCreate(Dataset ds, PatientLocal patient)
            throws CreateException {
        try {
            setPatient(patient);
            DcmElement proceCodeSq = ds.get(Tags.ProcedureCodeSeq);
            if (proceCodeSq != null
                    && CodeBean.checkCodes(
                            "Procedure Code Sequence (0008,1032)", proceCodeSq)) {
                CodeBean.addCodesTo(codeHome, proceCodeSq, getProcedureCodes());
            }
            updateIssuerOfAccessionNumber(null,
                    ds.getItem(Tags.IssuerOfAccessionNumberSeq));
        } catch (Exception e) {
            // ensure to rollback transaction
            throw new EJBException(e);
        }
        log.info("Created " + prompt());
    }

    public void ejbRemove() throws RemoveException {
        log.info("Deleting " + prompt());
    }

    /**
     * @ejb.select query="SELECT DISTINCT s.retrieveAETs FROM Series s WHERE s.study.pk = ?1"
     */
    public abstract Set ejbSelectSeriesRetrieveAETs(Long pk)
            throws FinderException;

    /**
     * @ejb.select query="SELECT DISTINCT i.externalRetrieveAET FROM Study st, IN(st.series) s, IN(s.instances) i WHERE st.pk = ?1"
     */
    public abstract java.util.Set ejbSelectExternalRetrieveAETs(Long pk)
            throws FinderException;

    /**
     * @ejb.select query="SELECT DISTINCT i.media FROM Study st, IN(st.series) s, IN(s.instances) i WHERE st.pk = ?1 AND i.media.mediaStatus = ?2"
     */
    public abstract java.util.Set ejbSelectMediaWithStatus(Long pk,
            int status) throws FinderException;

    /**
     * @ejb.select query="SELECT DISTINCT s.modality FROM Study st, IN(st.series) s WHERE st.pk = ?1"
     */
    public abstract Set ejbSelectModalityInStudies(Long pk)
            throws FinderException;

    /**
     * @ejb.select query=""
     */
    public abstract int ejbSelectNumberOfStudyRelatedInstancesOnMediaWithStatus(
            Long pk, int status) throws FinderException;

    /**
     * @ejb.select query=""
     */
    public abstract int ejbSelectNumberOfStudyRelatedInstances(Long pk)
            throws FinderException;

    /**
     * @ejb.select query=""
     */
    public abstract int ejbSelectNumberOfStudyRelatedSeries(Long pk)
            throws FinderException;

    /**
     * @ejb.select query=""
     */ 
    public abstract int ejbSelectNumberOfCommitedInstances(Long pk) throws FinderException;

    /**
     * @ejb.select query=""
     */ 
    public abstract int ejbSelectNumberOfReceivingSeries(Long pk) throws FinderException;

    /**
     * @ejb.interface-method
     */
    public int getNumberOfCommitedInstances() throws FinderException {
        return ejbSelectNumberOfCommitedInstances(getPk());
    }

    /**
     * @ejb.interface-method
     */
    public int getNumberOfReceivingSeries() throws FinderException {
        return ejbSelectNumberOfReceivingSeries(getPk());
    }

    /**
     * @ejb.select query=""
     */
    public abstract int ejbSelectAvailability(Long pk)
            throws FinderException;

    /**
     * @ejb.select query=""
     */
    public abstract Long ejbSelectStudyFileSize(Long studyPk, Long fsPk)
            throws FinderException;

 
    /**
     * @ejb.select query=""
     */
    public abstract int ejbSelectNumberOfStudyRelatedInstancesOnROFS(java.lang.Long pk, int status)
            throws FinderException;

    /**
     * @ejb.select query=""
     */
    public abstract int ejbSelectNumberOfStudyRelatedInstancesForAvailability(java.lang.Long pk, int availability)
            throws FinderException;

    /**    
     * @throws FinderException
     * @ejb.home-method
     */
    public long ejbHomeSelectStudySize( Long studyPk, Long fsPk ) throws FinderException {
        Long l = ejbSelectStudyFileSize(studyPk, fsPk);
        return l == null ? 0l : l.longValue();
    }

    /**
     * @ejb.interface-method
     */
    public boolean updateRetrieveAETs() {
        return studyDerivedFieldsUpdater.updateRetrieveAETs();
    }
    
    /**
     * @ejb.interface-method
     */
    public boolean updateExternalRetrieveAET() {
        return studyDerivedFieldsUpdater.updateExternalRetrieveAET();
    }

    /**
     * @ejb.interface-method
     */
    public boolean updateAvailability() {
        return studyDerivedFieldsUpdater.updateAvailability();
        }

    /**
     * @ejb.interface-method
     */
    public boolean updateNumberOfStudyRelatedSeries() {
        return studyDerivedFieldsUpdater.updateNumberOfStudyRelatedSeries();
        }

    /**
     * @ejb.interface-method
     */
    public boolean updateNumberOfStudyRelatedInstances() {
        return studyDerivedFieldsUpdater
                .updateNumberOfStudyRelatedInstances();
    }

    /**
     * @ejb.interface-method
     */
    public void updateDerivedFields() {
        studyDerivedFieldsUpdater.updateDerivedFields();
        updateFilesetId();
    }

    /**
     * @ejb.interface-method
     */
    public boolean updateFilesetId() {
        boolean updated = false;
        String fileSetId = null;
        String fileSetIuid = null;
        int numI = getNumberOfStudyRelatedInstances();
        if (numI > 0) {
            Long pk = getPk();
            try {
                if (ejbSelectNumberOfStudyRelatedInstancesOnMediaWithStatus(pk,
                        MediaDTO.COMPLETED) == numI) {
                    Set c = ejbSelectMediaWithStatus(pk, MediaDTO.COMPLETED);
                    if (c.size() == 1) {
                        MediaLocal media = (MediaLocal) c.iterator().next();
                        fileSetId = media.getFilesetId();
                        fileSetIuid = media.getFilesetIuid();
                    }
                }
            } catch (FinderException e) {
                throw new EJBException(e);
            }
        }
        if (fileSetId == null ? getFilesetId() != null
                              : !fileSetId.equals(getFilesetId())) {
            setFilesetId(fileSetId);
            updated = true;
        }
        if (fileSetIuid == null ? getFilesetIuid() != null
                                : !fileSetIuid.equals(getFilesetIuid())) {
            setFilesetIuid(fileSetIuid);
            updated = true;
        }
        return updated;
    }

    /**
     * @ejb.interface-method
     */
    public boolean updateModalitiesInStudy() {
        return studyDerivedFieldsUpdater.updateModalitiesInStudy();
    }

    /** 
     * @ejb.interface-method
     */
    public boolean updateSOPClassesInStudy() {
        return studyDerivedFieldsUpdater.updateSOPClassesInStudy();
    }

    /**
     * @ejb.select query="SELECT DISTINCT i.sopCuid FROM Study st, IN(st.series) s, IN(s.instances) i WHERE st.pk = ?1"
     */
    public abstract Set ejbSelectSOPClassesInStudies(Long pk) throws FinderException;

    /** 
     * @ejb.interface-method
     */
    public void updateRetrieveAETs(String oldAET, String newAET) {
        Collection series = getSeries();
        for (Iterator it = series.iterator(); it.hasNext();) {
            ((SeriesLocal) it.next()).updateRetrieveAETs(oldAET, newAET);
        }
        setRetrieveAETs(AETs.update(getRetrieveAETs(), oldAET, newAET));
    }

    /**
     * @ejb.interface-method
     */
    public boolean isStudyAvailableOnMedia() throws FinderException {
        String fsuid = getFilesetIuid();
        return (fsuid != null && fsuid.length() != 0)
                || ejbSelectNumberOfStudyRelatedInstancesOnMediaWithStatus(
                        getPk(), MediaDTO.COMPLETED) == getNumberOfStudyRelatedInstances();
    }

    /**
     * @ejb.interface-method
     */
    public boolean isStudyAvailableOnROFs(int validFileStatus) throws FinderException {
        return ( ejbSelectNumberOfStudyRelatedInstancesOnROFS(getPk(), validFileStatus) == getNumberOfStudyRelatedInstances() );
    }
    
    /**
     * @ejb.interface-method
     */
    public boolean isStudyAvailable(int availability) throws FinderException {
        return ( ejbSelectNumberOfStudyRelatedInstancesForAvailability(getPk(), availability) == getNumberOfStudyRelatedInstances() );
    }
    
    /**
     * @ejb.interface-method
     */
    public Dataset getAttributes(boolean supplement) {
        Dataset ds = DatasetUtils.fromByteArray(getEncodedAttributes());
        if (ds.isEmpty()) {
            log.warn("Empty Dataset in Study BLOB (pk:"+getPk()+")! Use Dataset with DB values");
            ds.putUI(Tags.StudyInstanceUID, this.getStudyIuid());
            ds.putSH(Tags.StudyID, this.getStudyId());
            ds.putCS(Tags.StudyStatusID, this.getStudyStatusId());
            ds.putDA(Tags.StudyDate, this.getStudyDateTime());
            ds.putTM(Tags.StudyTime, this.getStudyDateTime());
            ds.putSH(Tags.AccessionNumber, this.getAccessionNumber());
            ds.putPN(Tags.ReferringPhysicianName, this.getReferringPhysicianName());
            ds.putLO(Tags.StudyDescription, this.getStudyDescription());
        }
        if (supplement) {
            ds.setPrivateCreatorID(PrivateTags.CreatorID);
            ds.putOB(PrivateTags.StudyPk, Convert.toBytes(getPk().longValue()));
            ds.setPrivateCreatorID(null);
            ds.putCS(Tags.ModalitiesInStudy, StringUtils.split(
                    getModalitiesInStudy(), '\\'));
            ds.putIS(Tags.NumberOfStudyRelatedSeries,
                    getNumberOfStudyRelatedSeries());
            ds.putIS(Tags.NumberOfStudyRelatedInstances,
                    getNumberOfStudyRelatedInstances());
            ds.putSH(Tags.StorageMediaFileSetID, getFilesetId());
            ds.putUI(Tags.StorageMediaFileSetUID, getFilesetIuid());
            DatasetUtils.putRetrieveAET(ds, getRetrieveAETs(),
            		getExternalRetrieveAET());
            ds.putCS(Tags.InstanceAvailability, Availability
                    .toString(getAvailabilitySafe()));
            ds.putCS(Tags.StudyStatusID, getStudyStatusId());
        }
        return ds;
    }

    /**
     * @ejb.interface-method
     */
    public void setAttributes(Dataset ds) {
        AttributeFilter filter = AttributeFilter.getStudyAttributeFilter();
        setStudyIuid(ds.getString(Tags.StudyInstanceUID));
        setStudyId(filter.getString(ds, Tags.StudyID));
        setStudyStatusId(filter.getString(ds, Tags.StudyStatusID));
        try {
            setStudyDateTime(ds.getDateTime(Tags.StudyDate, Tags.StudyTime));
        } catch (IllegalArgumentException e) {
            log.warn("Illegal Study Date/Time format: " + e.getMessage());
        }
        
        setAccessionNumber(filter.getString(ds, Tags.AccessionNumber));
        PersonName pn = ds.getPersonName(Tags.ReferringPhysicianName);
        if (pn != null) {
            setReferringPhysicianName(
                    filter.toUpperCase(pn.toComponentGroupString(false),
                            Tags.ReferringPhysicianName));
            PersonName ipn = pn.getIdeographic();
            if (ipn != null) {
                setReferringPhysicianIdeographicName(ipn.toComponentGroupString(false));                
            }
            PersonName ppn = pn.getPhonetic();
            if (ppn != null) {
                setReferringPhysicianPhoneticName(ppn.toComponentGroupString(false));                
            }
        }
        if (AttributeFilter.isSoundexEnabled()) {
            setReferringPhysicianFamilyNameSoundex(
                    AttributeFilter.toSoundex(pn, PersonName.FAMILY, "*"));
            setReferringPhysicianGivenNameSoundex(
                    AttributeFilter.toSoundex(pn, PersonName.GIVEN, "*"));
        }
        setStudyDescription(filter.getString(ds, Tags.StudyDescription));
        
        byte[] b = DatasetUtils.toByteArray(filter.filter(ds),
                filter.getTransferSyntaxUID());
        if (log.isDebugEnabled()) {
            log.debug("setEncodedAttributes(byte[" + b.length + "])");
        }
        setEncodedAttributes(b);
        int[] fieldTags = filter.getFieldTags();
        for (int i = 0; i < fieldTags.length; i++) {
            setField(filter.getField(fieldTags[i]), filter.getString(ds, fieldTags[i]));
        }
    }

    private void setField(String field, String value ) {
        try {
            Method m = StudyBean.class.getMethod("set" 
                    + Character.toUpperCase(field.charAt(0))
                    + field.substring(1), STRING_PARAM);
            m.invoke(this, new Object[] { value });
        } catch (Exception e) {
            throw new ConfigurationException(e);
        }       
    }
    
    private boolean updateIssuerOfAccessionNumber(Dataset oldIssuer, Dataset newIssuer) {
        if (oldIssuer == null ? newIssuer == null 
                : oldIssuer.equals(newIssuer)) {
            return false;
        }
        try {
            setIssuerOfAccessionNumber(
                    IssuerBean.valueOf(issuerHome, newIssuer));
        } catch (CreateException e) {
            throw new EJBException(e);
        } catch (FinderException e) {
            throw new EJBException(e);
        }
        return true;
    }

    /** 
    * @ejb.interface-method
    */
   public boolean updateAttributes( Dataset newAttrs, Dataset modifiedAttrs) {
       Dataset oldAttrs = getAttributes(false);
       if ( oldAttrs == null ) {
           updateIssuerOfAccessionNumber(null,
                   newAttrs.getItem(Tags.IssuerOfAccessionNumberSeq));
           setAttributes( newAttrs );
       } else {
           updateIssuerOfAccessionNumber(
                   oldAttrs.getItem(Tags.IssuerOfAccessionNumberSeq),
                   newAttrs.getItem(Tags.IssuerOfAccessionNumberSeq));
           AttributeFilter filter = AttributeFilter.getStudyAttributeFilter();
           if (!AttrUtils.updateAttributes(oldAttrs, filter.filter(newAttrs),
                   modifiedAttrs, log) )
               return false;
           setAttributes(oldAttrs);
       }
       return true;
   }
    /**
     * @throws DcmServiceException 
     * @ejb.interface-method
     */
    public void coerceAttributes(Dataset ds, Dataset coercedElements)
    throws DcmServiceException {
        AttributeFilter filter = AttributeFilter.getStudyAttributeFilter();
        if (filter.isOverwrite()) {
            Dataset attrs;
            if (filter.isMerge()) {
                attrs = getAttributes(false);
                AttrUtils.updateAttributes(attrs, filter.filter(ds), null, log);
            } else {
                attrs = ds;
            }
            setAttributes(attrs);
        } else {
            Dataset attrs = getAttributes(false);
            AttrUtils.coerceAttributes(attrs, ds, coercedElements, filter, log);
            if (filter.isMerge()
                    && AttrUtils.mergeAttributes(attrs, filter.filter(ds), log)) {
                setAttributes(attrs);
            }
        }
    }

    /**
     * 
     * @ejb.interface-method
     */
    public String asString() {
        return prompt();
    }

    private String prompt() {
        return "Study[pk=" + getPk() + ", uid=" + getStudyIuid()
                + ", patient->" + getPatient() + "]";
    }
    
    /**
     * @ejb.select query="SELECT OBJECT(f) FROM File f WHERE f.instance.series.study.pk = ?1 AND f.fileSystem.pk = ?2 ORDER BY f.pk"
     *             transaction-type="Supports"
     */
    public abstract Collection ejbSelectFiles(java.lang.Long study_fk, java.lang.Long filesystem_fk)
            throws FinderException;

    /**    
     * @ejb.interface-method
     */
    public Collection getFiles(Long fsPk) throws FinderException {    	
        return ejbSelectFiles(getPk(), fsPk);
    }
    
    /**
	 * @ejb.select query=
	 *             "SELECT COUNT(DISTINCT f) FROM File f WHERE f.instance.series.study.pk = ?1 AND f.fileSystem.pk = ?2"
	 *             transaction-type="Supports"
	 */
    public abstract int ejbSelectNumberOfFiles(java.lang.Long study_fk, java.lang.Long filesystem_fk)
            throws FinderException;

    /**    
     * @ejb.interface-method
     */
    public int getNumberOfFiles(Long fsPk) throws FinderException {    	
        return ejbSelectNumberOfFiles(getPk(), fsPk);
    }

    /**
     * @ejb.select query="SELECT DISTINCT OBJECT(s) FROM Series s, IN(s.instances) i, IN(i.files) f WHERE s.study.pk = ?1 AND f.fileSystem.pk = ?2"
     *             transaction-type="Supports"
     */
    public abstract Collection ejbSelectSeries(java.lang.Long study_fk, java.lang.Long filesystem_fk)
            throws FinderException;

    /**    
     * @ejb.interface-method
     */
    public Collection getSeries(Long fsPk) throws FinderException {      
        return ejbSelectSeries(getPk(), fsPk);
    }

    /**
     * @ejb.select query="SELECT OBJECT(f) FROM File f WHERE f.instance.series.study.pk = ?1"
     *             transaction-type="Supports"
     */
    public abstract Collection ejbSelectAllFiles(java.lang.Long study_fk)
            throws FinderException;

    /**    
     * @ejb.interface-method
     */
    public Collection getAllFiles() throws FinderException {      
        return ejbSelectAllFiles(getPk());
    }
    
    /**
     * @ejb.select query="SELECT s.pk FROM Series s WHERE s.study.pk = ?1"
     *             transaction-type="Supports"
     */
    public abstract Collection ejbSelectSeriesPks(java.lang.Long study_fk)
            throws FinderException;

    /**    
     * @ejb.interface-method
     */
    public Collection getSeriesPks() throws FinderException {      
        return ejbSelectSeriesPks(getPk());
    }
    
    /**
     * @ejb.select query="SELECT Object(i) FROM Instance i WHERE i.series.study.pk = ?1 AND i.media IS NULL"
     *             transaction-type="Supports"
     */
    public abstract Collection ejbSelectInstancesNotOnMedia(java.lang.Long study_pk)
            throws FinderException;
    
    /**    
     * @ejb.interface-method
     */
    public Collection getInstancesNotOnMedia() throws FinderException {    	
        return ejbSelectInstancesNotOnMedia(getPk());
    }
    
    /**
     * @ejb.select query="SELECT DISTINCT st.pk FROM Study AS st, IN(st.series) s WHERE s.ppsIuid IS NULL AND s.sourceAET = ?1 AND st.updatedTime < ?2"
     *             transaction-type="Supports"
     */
    public abstract Collection ejbSelectWithMissingPpsIuidFromSrcAETReceivedLastOfStudyBefore(java.lang.String srcAET, java.sql.Timestamp receivedBefore) throws FinderException;

    /**
     * @ejb.home-method
     */
    public Collection ejbHomeSelectWithMissingPpsIuidFromSrcAETReceivedLastOfStudyBefore(java.lang.String srcAET, java.sql.Timestamp receivedBefore) throws FinderException {
        return ejbSelectWithMissingPpsIuidFromSrcAETReceivedLastOfStudyBefore(srcAET, receivedBefore);
    }
}
