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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.EntityBean;
import javax.ejb.EntityContext;
import javax.ejb.FinderException;
import javax.ejb.ObjectNotFoundException;
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
import org.dcm4chex.archive.common.Availability;
import org.dcm4chex.archive.common.DatasetUtils;
import org.dcm4chex.archive.common.PrivateTags;
import org.dcm4chex.archive.ejb.conf.AttributeFilter;
import org.dcm4chex.archive.ejb.interfaces.CodeLocal;
import org.dcm4chex.archive.ejb.interfaces.CodeLocalHome;
import org.dcm4chex.archive.ejb.interfaces.InstanceLocal;
import org.dcm4chex.archive.ejb.interfaces.MPPSLocal;
import org.dcm4chex.archive.ejb.interfaces.MPPSLocalHome;
import org.dcm4chex.archive.ejb.interfaces.MediaDTO;
import org.dcm4chex.archive.ejb.interfaces.MediaLocal;
import org.dcm4chex.archive.ejb.interfaces.PatientLocal;
import org.dcm4chex.archive.ejb.interfaces.SeriesLocal;
import org.dcm4chex.archive.ejb.interfaces.SeriesRequestLocal;
import org.dcm4chex.archive.ejb.interfaces.SeriesRequestLocalHome;
import org.dcm4chex.archive.ejb.interfaces.StudyLocal;
import org.dcm4chex.archive.exceptions.ConfigurationException;
import org.dcm4chex.archive.util.AETs;
import org.dcm4chex.archive.util.Convert;
import org.dcm4chex.archive.util.DynamicQueryBuilder;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision: 18276 $ $Date: 2014-03-25 21:06:52 +0000 (Tue, 25 Mar 2014) $
 *
 * @ejb.bean name="Series" type="CMP" view-type="local" primkey-field="pk"
 *           local-jndi-name="ejb/Series"
 * @jboss.container-configuration name="Instance Per Transaction CMP 2.x EntityBean"
 * @ejb.transaction type="Required"
 * @ejb.persistence table-name="series"
 * @jboss.entity-command name="hsqldb-fetch-key"
 * @jboss.audit-created-time field-name="createdTime"
 * @jboss.audit-updated-time field-name="updatedTime"
 * 
 * @ejb.finder signature="Collection findAll()"
 *             query="SELECT OBJECT(s) FROM Series AS s"
 *             transaction-type="Supports"
 *
 * @ejb.finder signature="java.util.Collection findSeriesOnMedia(org.dcm4chex.archive.ejb.interfaces.MediaLocal media)"
 *             query="SELECT DISTINCT OBJECT(s) FROM Series s, IN(s.instances) i WHERE i.media = ?1"
 *             transaction-type="Supports"
 *             
 * @ejb.finder signature="org.dcm4chex.archive.ejb.interfaces.SeriesLocal findBySeriesIuid(java.lang.String uid)"
 *             query="SELECT OBJECT(s) FROM Series AS s WHERE s.seriesIuid = ?1"
 *             transaction-type="Supports"
 * @jboss.query signature="org.dcm4chex.archive.ejb.interfaces.SeriesLocal findBySeriesIuid(java.lang.String uid)"
 *              strategy="on-find"
 *              eager-load-group="*"
 *              
 * @ejb.finder signature="java.util.Collection findByPpsIuid(java.lang.String uid)"
 *             query="SELECT OBJECT(s) FROM Series AS s WHERE s.ppsIuid = ?1"
 *             transaction-type="Supports"
 * 
 * @ejb.finder signature="java.util.Collection findWithNoPpsIuidFromSrcAETReceivedLastOfStudyBefore(java.lang.String srcAET, java.sql.Timestamp receivedBefore)"
 *             query="SELECT OBJECT(s) FROM Series AS s WHERE s.ppsIuid IS NULL AND s.sourceAET = ?1 AND s.study.updatedTime < ?2"
 *             transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findWithNoPpsIuidFromSrcAETReceivedLastOfStudyBefore(java.lang.String srcAET, java.sql.Timestamp receivedBefore)"
 *              strategy="on-find"
 *              eager-load-group="*"
 * 
 * @jboss.query signature="int ejbSelectNumberOfSeriesRelatedInstancesOnMediaWithStatus(java.lang.Long pk, int status)"
 *              query="SELECT COUNT(i) FROM Instance i WHERE i.series.pk = ?1 AND i.media.mediaStatus = ?2"
 * @jboss.query signature="int ejbSelectNumberOfSeriesRelatedInstances(java.lang.Long pk)"
 *              query="SELECT COUNT(i) FROM Instance i WHERE i.series.pk = ?1"
 * @jboss.query signature="int ejbSelectAvailability(java.lang.Long pk)"
 *              query="SELECT MAX(i.availability) FROM Instance i WHERE i.series.pk = ?1"
 * @jboss.query signature="java.sql.Timestamp ejbSelectMaxUpdatedTimeOfSeriesRelatedInstances(java.lang.Long pk)"
 *              query="SELECT MAX(i.updatedTime) FROM Instance i WHERE i.series.pk = ?1"
 * @jboss.query signature="java.util.Collection ejbSelectSeriesIuidsByModalityAndSrcAETAndUpdatedTime(int availability, java.lang.String modality, java.lang.String srcAET, java.sql.Timestamp updatedAfter, java.sql.Timestamp updatedBefore, int offset, int limit)"
 *              query="SELECT s.seriesIuid FROM Series AS s WHERE s.availability = ?1 AND s.modality = ?2 AND s.sourceAET = ?3 AND s.updatedTime BETWEEN ?4 AND ?5 ORDER BY s.pk DESC OFFSET ?6 LIMIT ?7"
 * @jboss.query signature="java.util.Collection ejbSelectSeriesIuidsByModalityAndUpdatedTime(int availability, java.lang.String modality, java.sql.Timestamp updatedAfter, java.sql.Timestamp updatedBefore, int offset, int limit)"
 *              query="SELECT s.seriesIuid FROM Series AS s WHERE s.availability = ?1 AND s.modality = ?2 AND s.updatedTime BETWEEN ?3 AND ?4 ORDER BY s.pk DESC OFFSET ?5 LIMIT ?6"
 * @jboss.query signature="java.util.Collection ejbSelectSeriesIuidsBySrcAETAndUpdatedTime(int availability, java.lang.String srcAET, java.sql.Timestamp updatedAfter, java.sql.Timestamp updatedBefore, int offset, int limit)"
 *              query="SELECT s.seriesIuid FROM Series AS s WHERE s.availability = ?1 AND s.sourceAET = ?2 AND s.updatedTime BETWEEN ?3 AND ?4 ORDER BY s.pk DESC OFFSET ?5 LIMIT ?6"
 * @jboss.query signature="java.util.Collection ejbSelectSeriesIuidsByUpdatedTime(int availability, java.sql.Timestamp updatedAfter, java.sql.Timestamp updatedBefore, int offset, int limit)"
 *              query="SELECT s.seriesIuid FROM Series AS s WHERE s.availability = ?1 AND s.updatedTime BETWEEN ?2 AND ?3 ORDER BY s.pk DESC OFFSET ?4 LIMIT ?5"
 * @jboss.query signature="int ejbSelectCountSeriesByModalityAndSrcAETAndUpdatedTime(int availability, java.lang.String modality, java.lang.String srcAET, java.sql.Timestamp updatedAfter, java.sql.Timestamp updatedBefore)"
 *              query="SELECT COUNT(s) FROM Series AS s WHERE s.availability = ?1 AND s.modality = ?2 AND s.sourceAET = ?3 AND s.updatedTime BETWEEN ?4 AND ?5"
 * @jboss.query signature="int ejbSelectCountSeriesByModalityAndUpdatedTime(int availability, java.lang.String modality, java.sql.Timestamp updatedAfter, java.sql.Timestamp updatedBefore)"
 *              query="SELECT COUNT(s) FROM Series AS s WHERE s.availability = ?1 AND s.modality = ?2 AND s.updatedTime BETWEEN ?3 AND ?4"
 * @jboss.query signature="int ejbSelectCountSeriesBySrcAETAndUpdatedTime(int availability, java.lang.String srcAET, java.sql.Timestamp updatedAfter, java.sql.Timestamp updatedBefore)"
 *              query="SELECT COUNT(s) FROM Series AS s WHERE s.availability = ?1 AND s.sourceAET = ?2 AND s.updatedTime BETWEEN ?3 AND ?4"
 * @jboss.query signature="int ejbSelectCountSeriesByUpdatedTime(int availability, java.sql.Timestamp updatedAfter, java.sql.Timestamp updatedBefore)"
 *              query="SELECT COUNT(s) FROM Series AS s WHERE s.availability = ?1 AND s.updatedTime BETWEEN ?2 AND ?3"
 * 
 * @jboss.query signature="java.util.Collection ejbSelectGeneric(java.lang.String jbossQl, java.lang.Object[] args)"
 *              dynamic="true" strategy="on-load" page-size="20"
 *              eager-load-group="*"
 * 
 * @ejb.ejb-ref ejb-name="MPPS" view-type="local" ref-name="ejb/MPPS"
 * @ejb.ejb-ref ejb-name="SeriesRequest" view-type="local" ref-name="ejb/Request"
 * @ejb.ejb-ref ejb-name="Code" view-type="local" ref-name="ejb/Code"
 * 
 */
public abstract class SeriesBean implements EntityBean {
    private class SeriesDerivedFieldsUpdater extends org.dcm4chex.archive.common.SeriesDerivedFieldsUpdater implements Serializable {
        @Override
        protected long getPk() {
            return SeriesBean.this.getPk();
        }

        @Override
        protected String getSeriesIuid() {
            return SeriesBean.this.getSeriesIuid();
        }

        @Override
        protected int getNumberOfSeriesRelatedInstances() {
            return SeriesBean.this.getNumberOfSeriesRelatedInstances();
        }

        @Override
        protected void setNumberOfSeriesRelatedInstances(int numI) {
            SeriesBean.this.setNumberOfSeriesRelatedInstances(numI);
        }

        @Override
        protected int deriveNumberOfSeriesRelatedInstances()
                throws FinderException {
            return ejbSelectNumberOfSeriesRelatedInstances(getPk());
        }

        @Override
        protected void setRetrieveAETs(String aets) {
            SeriesBean.this.setRetrieveAETs(aets);
        }

        @Override
        protected String getRetrieveAETs() {
            return SeriesBean.this.getRetrieveAETs();
        }

        @Override
        protected Set<String> deriveInternalRetrieveAETs()
                throws FinderException {
            return ejbSelectInternalRetrieveAETs(getPk());
        }

        @Override
        protected String getExternalRetrieveAET() {
            return SeriesBean.this.getExternalRetrieveAET();
        }

        @Override
        protected void setExternalRetrieveAET(String aet) {
            SeriesBean.this.setExternalRetrieveAET(aet);
        }

        @Override
        protected Set<String> deriveExternalRetrieveAETs()
                throws FinderException {
            return ejbSelectExternalRetrieveAETs(getPk());
        }

        @Override
        protected int getAvailability() {
            return SeriesBean.this.getAvailability();
        }

        @Override
        protected void setAvailability(int availability) {
            SeriesBean.this.setAvailability(availability);
        }

        @Override
        protected int deriveAvailability() throws FinderException {
            return ejbSelectAvailability(getPk());
        }
    }

    private static final Logger log = Logger.getLogger(SeriesBean.class);

    private static final Class[] STRING_PARAM = new Class[] { String.class };

    private EntityContext ejbctx;
    private MPPSLocalHome mppsHome;
    private SeriesRequestLocalHome reqHome;
    private CodeLocalHome codeHome;

    private final DynamicQueryBuilder dynamicQueryBuilder;
    private final SeriesDerivedFieldsUpdater seriesDerivedFieldsUpdater;

    public SeriesBean() {
		this(new DynamicQueryBuilder());
	}

	public SeriesBean(DynamicQueryBuilder dynamicQueryBuilder) {
		this.dynamicQueryBuilder = dynamicQueryBuilder;
        
        this.seriesDerivedFieldsUpdater = new SeriesDerivedFieldsUpdater();
	}

    public void setEntityContext(EntityContext ctx) {
        ejbctx = ctx;
        Context jndiCtx = null;
        try {
            jndiCtx = new InitialContext();
            mppsHome = (MPPSLocalHome) 
                    jndiCtx.lookup("java:comp/env/ejb/MPPS");
            reqHome = (SeriesRequestLocalHome)
                    jndiCtx.lookup("java:comp/env/ejb/Request");
            codeHome = (CodeLocalHome)
                    jndiCtx.lookup("java:comp/env/ejb/Code");
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
        mppsHome = null;
        reqHome = null;
        codeHome = null;
        ejbctx = null;
    }

    /**
     * @ejb.interface-method
     * @ejb.pk-field
     * @ejb.persistence column-name="pk"
     * @jboss.persistence auto-increment="true"
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
    /**
     * @ejb.interface-method
     */
    public abstract void setUpdatedTime(java.sql.Timestamp time);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="series_iuid"
     */
    public abstract String getSeriesIuid();
    public abstract void setSeriesIuid(String uid);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="series_no"
     */
    public abstract String getSeriesNumber();
    public abstract void setSeriesNumber(String no);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="modality"
     */
    public abstract String getModality();
    public abstract void setModality(String md);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="body_part"
     */
    public abstract String getBodyPartExamined();
    public abstract void setBodyPartExamined(String bodyPart);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="laterality"
     */
    public abstract String getLaterality();
    public abstract void setLaterality(String laterality);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="series_desc"
     */
    public abstract String getSeriesDescription();
    public abstract void setSeriesDescription(String description);
    
    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="department"
     */
    public abstract String getInstitutionalDepartmentName();
    public abstract void setInstitutionalDepartmentName(String name);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="institution"
     */
    public abstract String getInstitutionName();
    public abstract void setInstitutionName(String name);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="station_name"
     */
    public abstract String getStationName();
    public abstract void setStationName(String name);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="perf_physician"
     */
    public abstract String getPerformingPhysicianName();
    public abstract void setPerformingPhysicianName(String name);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="perf_phys_fn_sx"
     */
    public abstract String getPerformingPhysicianFamilyNameSoundex();

    /**
     * @ejb.interface-method
     */
    public abstract void setPerformingPhysicianFamilyNameSoundex(String name);
        
    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="perf_phys_gn_sx"
     */
    public abstract String getPerformingPhysicianGivenNameSoundex();

    /**
     * @ejb.interface-method
     */
    public abstract void setPerformingPhysicianGivenNameSoundex(String name);

    /**
     * @ejb.persistence column-name="perf_phys_i_name"
     */
    public abstract String getPerformingPhysicianIdeographicName();
    public abstract void setPerformingPhysicianIdeographicName(String name);

    /**
     * @ejb.persistence column-name="perf_phys_p_name"
     */
    public abstract String getPerformingPhysicianPhoneticName();
    public abstract void setPerformingPhysicianPhoneticName(String name);
    
    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="pps_start"
     */
    public abstract java.sql.Timestamp getPpsStartDateTime();
    public abstract void setPpsStartDateTime(java.sql.Timestamp datetime);

    private void setPpsStartDateTime(java.util.Date date) {
        setPpsStartDateTime(date != null 
                ? new java.sql.Timestamp(date.getTime())
                : null);
    }

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="pps_iuid"
     */
    public abstract String getPpsIuid();
    public abstract void setPpsIuid(String uid);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="series_custom1"
     */
    public abstract String getSeriesCustomAttribute1();
    public abstract void setSeriesCustomAttribute1(String value);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="series_custom2"
     */
    public abstract String getSeriesCustomAttribute2();
    public abstract void setSeriesCustomAttribute2(String value);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="series_custom3"
     */
    public abstract String getSeriesCustomAttribute3();
    public abstract void setSeriesCustomAttribute3(String value);
    
    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="num_instances"
     */
    public abstract int getNumberOfSeriesRelatedInstances();
    public abstract void setNumberOfSeriesRelatedInstances(int num);
    
    /**
     * @ejb.persistence column-name="series_attrs"
     */
    public abstract byte[] getEncodedAttributes();
    public abstract void setEncodedAttributes(byte[] attr);

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
     * @ejb.persistence column-name="src_aet"
     */
    public abstract String getSourceAET();
    public abstract void setSourceAET(String aet);

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
     * @ejb.interface-method
     * @ejb.persistence column-name="retrieve_aets"
     */
    public abstract String getRetrieveAETs();

    public abstract void setRetrieveAETs(String aets);

    /**
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
     * @ejb.interface-method
     * @ejb.persistence column-name="series_status"
     */
    public abstract int getSeriesStatus();

    /**
     * @ejb.interface-method
     */
    public abstract void setSeriesStatus(int status);
    
    
    /**
     * @ejb.relation name="series-inst-code" role-name="series-with-inst-code"
     *               target-ejb="Code" target-role-name="inst-code-of-series"
     *               target-multiple="yes"
     * @jboss.relation fk-column="inst_code_fk" related-pk-field="pk"
     */
    public abstract CodeLocal getInstitutionCode();
    public abstract void setInstitutionCode(CodeLocal instCode);

    /**
     * @ejb.interface-method
     * @ejb.relation name="study-series" role-name="series-of-study"
     *               cascade-delete="yes"
     * @jboss.relation fk-column="study_fk" related-pk-field="pk"
     */
    public abstract StudyLocal getStudy();
	
    /**
     * @ejb.interface-method 
     */
    public abstract void setStudy(StudyLocal study);

    /**
     * @ejb.interface-method
     * @ejb.relation name="mpps-series" role-name="series-of-mpps"
     * @jboss.relation fk-column="mpps_fk" related-pk-field="pk"
     */
    public abstract MPPSLocal getMpps();
    public abstract void setMpps(MPPSLocal mpps);

    /**
     * @ejb.interface-method
     * @ejb.relation name="series-request-attributes"
     *               role-name="series-has-request-attributes"
     */
    public abstract java.util.Collection getRequestAttributes();
    public abstract void setRequestAttributes(java.util.Collection series);

    /**
     * @ejb.interface-method
     * @ejb.relation name="series-instance" role-name="series-has-instance"
     */
    public abstract java.util.Collection getInstances();
    public abstract void setInstances(java.util.Collection insts);


    /**
     * @ejb.create-method
     */
    public Long ejbCreate(Dataset ds, StudyLocal study)
            throws CreateException {
        ds.setPrivateCreatorID(PrivateTags.CreatorID);
        setSourceAET(ds.getString(PrivateTags.CallingAET));
        ds.setPrivateCreatorID(null);
        setAttributes(ds);
        return null;
    }

    public void ejbPostCreate(Dataset ds, StudyLocal study)
            throws CreateException {
        updateSeriesRequest(null, ds, false);
        updateInstitutionCode(null, ds.getItem(Tags.InstitutionCodeSeq));
        setStudy(study);
        updateMpps();
        log.info("Created " + prompt());
    }

    private boolean updateInstitutionCode(Dataset oldCode, Dataset newCode) {
        if (oldCode == null ? newCode == null 
                : oldCode.equals(newCode)) {
            return false;
        }
        try {
            setInstitutionCode(newCode == null ? null
                    : CodeBean.valueOf(codeHome, newCode));
        } catch (CreateException e) {
            throw new EJBException(e);
        } catch (FinderException e) {
            throw new EJBException(e);
        }
        return true;
    }

    /**
     * Update series attributes and SeriesRequest.
     * <p>
     * Deletes SeriesRequest objects if RequestAttributesSeq in newAttrs is empty. 
     *  
     * @param ds Dataset with series attributes.
     * @throws CreateException 
     * 
     * @ejb.interface-method
     */
    public boolean updateAttributes( Dataset newAttrs, 
            boolean overwriteReqAttrSQ, Dataset modifiedAttrs ) {
        Dataset oldAttrs = getAttributes(false);
        updateSeriesRequest(oldAttrs, newAttrs, overwriteReqAttrSQ);
        if ( oldAttrs == null ) {
            updateInstitutionCode(null,
                    newAttrs.getItem(Tags.InstitutionCodeSeq));
            setAttributes( newAttrs );
        } else {
            updateInstitutionCode(oldAttrs.getItem(Tags.InstitutionCodeSeq),
                    newAttrs.getItem(Tags.InstitutionCodeSeq));
            AttributeFilter filter = AttributeFilter.getSeriesAttributeFilter();
            if (!AttrUtils.updateAttributes(oldAttrs, filter.filter(newAttrs),
                    modifiedAttrs, log) )
                return false;
            setAttributes(oldAttrs);
        }
        return true;
    }
    
    private boolean updateSeriesRequest(Dataset oldAttrs, Dataset newAttrs,
            boolean overwriteReqAttrSQ) {
        DcmElement newReqAttrSQ = newAttrs.get(Tags.RequestAttributesSeq);
        if (newReqAttrSQ == null) {
            return false;
        }
        if (oldAttrs != null
                && newReqAttrSQ.equals(oldAttrs.get(Tags.RequestAttributesSeq))) {
            return false;
        }
        Collection c = getRequestAttributes();
        if ( overwriteReqAttrSQ && ! c.isEmpty() ) {
            oldAttrs.remove(Tags.RequestAttributesSeq);//remove to force update of RequestAttributesSeq
            SeriesRequestLocal[] srls = new SeriesRequestLocal[c.size()];
            srls = (SeriesRequestLocal[]) c.toArray(srls);
            for ( int i = 0 ; i < srls.length ; i++) {
                try {
                    srls[i].remove();
                } catch (Exception ignore) {
                    log.warn("Cant delete SeriesRequest! Ignore deletion of "+srls[i],ignore);
                }
            }
            c.clear();
        }
        SeriesLocal series = (SeriesLocal) ejbctx.getEJBLocalObject();
        for (int i = 0, len = newReqAttrSQ.countItems(); i < len; i++) {
            try {
                c.add(reqHome.create(newReqAttrSQ.getItem(i), series));
            } catch (CreateException e) {
                throw new EJBException(e);
            }
        }
        return true;
    }

    /**
     * @ejb.home-method
     */
    public Collection ejbHomeGetSeriesPksWithStatusAndUpdatedBefore(
            int status, Timestamp updatedBefore) throws FinderException {
        return ejbSelectSeriesPksByStatusAndUpdatedBefore(status, updatedBefore);
    }

    /**
     * @ejb.select query="SELECT s.pk FROM Series s WHERE s.seriesStatus = ?1 AND s.updatedTime < ?2"
     */
    public abstract Collection ejbSelectSeriesPksByStatusAndUpdatedBefore(int status, Timestamp updatedBefore) throws FinderException;

    /**
     * @ejb.home-method
     */
    public Collection<SeriesLocal> ejbHomeGetSeriesWithStatusAndUpdatedBefore(
            int status, Timestamp updatedBefore) throws FinderException {
        return ejbSelectSeriesByStatusAndUpdatedBefore(status, updatedBefore);
    }

    /**
     * @ejb.select query="SELECT OBJECT(s) FROM Series s WHERE s.seriesStatus = ?1 AND s.updatedTime < ?2"
     */
    public abstract Collection<SeriesLocal> ejbSelectSeriesByStatusAndUpdatedBefore(int status, Timestamp updatedBefore) throws FinderException;

    /**
     * @ejb.select query="SELECT DISTINCT i.retrieveAETs FROM Instance i WHERE i.series.pk = ?1"
     */
    public abstract java.util.Set ejbSelectInternalRetrieveAETs(Long pk) throws FinderException;

    /**
     * @ejb.select query="SELECT DISTINCT i.externalRetrieveAET FROM Instance i WHERE i.series.pk = ?1"
     */ 
    public abstract java.util.Set ejbSelectExternalRetrieveAETs(Long pk) throws FinderException;
    
    /**
     * @ejb.select query="SELECT DISTINCT i.media FROM Instance i WHERE i.series.pk = ?1 AND i.media.mediaStatus = ?2"
     */ 
    public abstract java.util.Set ejbSelectMediaWithStatus(Long pk, int status) throws FinderException;

    /**
     * @ejb.select query=""
     */ 
    public abstract int ejbSelectNumberOfSeriesRelatedInstancesOnMediaWithStatus(Long pk, int status) throws FinderException;

    /**
     * @ejb.select query=""
     */ 
    public abstract int ejbSelectNumberOfSeriesRelatedInstances(Long pk) throws FinderException;
    
    /**
     * @ejb.select query=""
     */ 
    public abstract Timestamp ejbSelectMaxUpdatedTimeOfSeriesRelatedInstances(Long pk) throws FinderException;
    
    /**
     * @ejb.select query=""
     */ 
    public abstract int ejbSelectAvailability(Long pk) throws FinderException;
    
    /**
     * @ejb.select query=""
     */
    public abstract Collection ejbSelectSeriesIuidsByModalityAndSrcAETAndUpdatedTime(
            int availability, String modality, String srcAET,
            Timestamp updatedAfter, Timestamp updatedBefore, int offset, int limit)
            throws FinderException;

    /**
     * @ejb.select query=""
     */
    public abstract Collection ejbSelectSeriesIuidsByModalityAndUpdatedTime(
            int availability, String modality, Timestamp updatedAfter,
            Timestamp updatedBefore, int offset, int limit) throws FinderException;

    /**
     * @ejb.select query=""
     */
    public abstract Collection ejbSelectSeriesIuidsBySrcAETAndUpdatedTime(
            int availability, String srcAET, Timestamp updatedAfter,
            Timestamp updatedBefore, int offset, int limit) throws FinderException;

    /**
     * @ejb.select query=""
     */
    public abstract Collection ejbSelectSeriesIuidsByUpdatedTime(
            int availability, Timestamp updatedAfter, Timestamp updatedBefore,
            int offset, int limit) throws FinderException;

    /**
     * @ejb.select query=""
     */
    public abstract int ejbSelectCountSeriesByModalityAndSrcAETAndUpdatedTime(
            int availability, String modality, String srcAET, Timestamp updatedAfter,
            Timestamp updatedBefore) throws FinderException;

    /**
     * @ejb.select query=""
     */
    public abstract int ejbSelectCountSeriesByModalityAndUpdatedTime(
            int availability, String modality, Timestamp updatedAfter,
            Timestamp updatedBefore) throws FinderException;

    /**
     * @ejb.select query=""
     */
    public abstract int ejbSelectCountSeriesBySrcAETAndUpdatedTime(
            int availability, String srcAET, Timestamp updatedAfter,
            Timestamp updatedBefore) throws FinderException;

    /**
     * @ejb.select query=""
     */
    public abstract int ejbSelectCountSeriesByUpdatedTime(
            int availability, Timestamp updatedAfter, Timestamp updatedBefore)
            throws FinderException;

    /**
     * @ejb.home-method
     */
    public Collection ejbHomeSeriesIuidsForAttributesUpdate(int availability,
            String modality, String srcAET, Timestamp updatedAfter,
            Timestamp updatedBefore, int offset, int limit)
            throws FinderException {
        return (modality == null || modality.length() == 0)
                ? ((srcAET == null || srcAET.length() == 0)
                        ? ejbSelectSeriesIuidsByUpdatedTime(availability,
                                updatedAfter, updatedBefore, offset, limit)
                        : ejbSelectSeriesIuidsBySrcAETAndUpdatedTime(
                                availability, srcAET, updatedAfter,
                                updatedBefore, offset, limit))
                : ((srcAET == null || srcAET.length() == 0)
                        ? ejbSelectSeriesIuidsByModalityAndUpdatedTime(
                                availability, modality, updatedAfter,
                                updatedBefore, offset, limit)
                        : ejbSelectSeriesIuidsByModalityAndSrcAETAndUpdatedTime(
                                availability, modality, srcAET, updatedAfter,
                                updatedBefore, offset, limit));
    }

    /**
     * @ejb.home-method
     */
    public int ejbHomeCountSeriesForAttributesUpdate(int availability,
            String modality, String srcAET, Timestamp updatedAfter,
            Timestamp updatedBefore) throws FinderException {
        return (modality == null || modality.length() == 0)
                ? ((srcAET == null || srcAET.length() == 0)
                        ? ejbSelectCountSeriesByUpdatedTime(availability,
                                updatedAfter, updatedBefore)
                        : ejbSelectCountSeriesBySrcAETAndUpdatedTime(
                                availability, srcAET, updatedAfter, 
                                updatedBefore))
                : ((srcAET == null || srcAET.length() == 0)
                        ? ejbSelectCountSeriesByModalityAndUpdatedTime(
                                availability, modality, updatedAfter,
                                updatedBefore)
                        : ejbSelectCountSeriesByModalityAndSrcAETAndUpdatedTime(
                                availability, modality, srcAET, updatedAfter,
                                updatedBefore));
    }

    /**
     * @ejb.select query="" transaction-type="Supports"
     */
    public abstract Collection ejbSelectGeneric(String jbossQl, Object[] args)
            throws FinderException;
    
    /**
     * @ejb.home-method
     */
    public Collection ejbHomeListPriorOfPatient(PatientLocal pat, String ignoreStudyIUID, Timestamp createdAfter, int minAvail, 
            String[] modalities) throws FinderException {
        StringBuffer jbossQl = new StringBuffer(
                "SELECT OBJECT(s) FROM Series s");
        jbossQl.append(" WHERE s.study.patient = ?1");
        ArrayList params = new ArrayList();
        params.add(pat);
        int idx = 2;
        if (ignoreStudyIUID != null) {
            jbossQl.append(" AND s.study.studyIuid <> ?").append(idx++);
            params.add(ignoreStudyIUID);
        }
        if (createdAfter != null) {
            jbossQl.append(" AND s.study.createdTime > ?").append(idx++);
            params.add(createdAfter);
        }
        if (minAvail < Availability.UNAVAILABLE) {
            jbossQl.append(" AND s.availability <= ?").append(idx++);
            params.add(minAvail);
        }
        if (modalities != null && modalities.length > 0) {
            jbossQl.append(" AND s.modality");
            idx = addIN(jbossQl, params, idx, modalities);
        }
        log.debug("Execute JBossQL: " + jbossQl);
        return ejbSelectGeneric(jbossQl.toString(), params.toArray());
    }

    /**
     * @ejb.home-method
     */
    public Collection ejbHomeListBySeriesIUIDsWithoutPPS(String[] seriesIUIDs) throws FinderException {
        if (seriesIUIDs == null || seriesIUIDs.length < 1) {
            return new ArrayList();
        }
        StringBuffer sb = new StringBuffer();
        sb.append("SELECT OBJECT(s) FROM Series s WHERE s.ppsIuid IS NULL AND s.seriesIuid");
        ArrayList params = new ArrayList();
        addIN(sb, params, 1, seriesIUIDs);
        log.debug("Execute JBossQL: " + sb);
        return ejbSelectGeneric(sb.toString(), params.toArray());
    }

    private int addIN(StringBuffer jbossQl, List params, int idx, String[] values) {
        params.add(values[0]);
        if (values.length > 1) {
            jbossQl.append(" IN ( ");
            for (int i = 1; i < values.length; i++) {
                jbossQl.append("?").append(idx++).append(", ");
                params.add(values[i]);
            }
            jbossQl.append("?").append(idx++).append(")");
        } else {
            jbossQl.append(" = ?").append(idx++);
        }
        return idx;
    }
    
    /**
     * @ejb.interface-method
     */
    public boolean updateRetrieveAETs() {
        return seriesDerivedFieldsUpdater.updateRetrieveAETs();
    }

    /* Commented out: Does not work for instances which are only external
     * retrieveable (= no (longer) files located on filesystems of this archive
     * installation) [GZ]
    private Set getInternalRetrieveAETs(Long pk) throws FinderException {
    	Collection aets = fsHome.allRetrieveAETs();
    	if(aets.size() > 1)
        	return ejbSelectInternalRetrieveAETs(pk);
    	else
        	// If there's only one AET registered with all existing file systems
        	// we just simply return this only one.
    		return new HashSet(aets);    	
    }
     */

    /**
     * @ejb.interface-method
     */
    public boolean updateExternalRetrieveAET() {
        return seriesDerivedFieldsUpdater.updateExternalRetrieveAET();
    }

    /**
     * @ejb.interface-method
     */
    public boolean updateAvailability() {
        return seriesDerivedFieldsUpdater.updateAvailability();
        }

    /**
     * @ejb.interface-method
     */
    public boolean updateNumberOfSeriesRelatedInstances() {
        return seriesDerivedFieldsUpdater
                .updateNumberOfSeriesRelatedInstances();
        }

    /**
     * @ejb.interface-method
     */
    public void updateDerivedFields() {
        seriesDerivedFieldsUpdater.updateDerivedFields();
        updateFilesetId();
    }

    /**
     * @ejb.interface-method
     */
    public Timestamp getMaxUpdatedTimeOfSeriesRelatedInstances() {
        try {
            return ejbSelectMaxUpdatedTimeOfSeriesRelatedInstances(getPk());
        } catch (FinderException e) {
            throw new EJBException(e);
        }
    }

    /**
     * @ejb.interface-method
     */
    public boolean updateFilesetId() {
        boolean updated = false;
        String fileSetId = null;
        String fileSetIuid = null;
        int numI = getNumberOfSeriesRelatedInstances();
        if (numI > 0) {
            Long pk = getPk();
            try {
                if (ejbSelectNumberOfSeriesRelatedInstancesOnMediaWithStatus(pk,
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
    public void updateRetrieveAETs(String oldAET, String newAET) {
        Collection insts = getInstances();
        for (Iterator it = insts.iterator(); it.hasNext();) {
            ((InstanceLocal) it.next()).updateRetrieveAETs(oldAET, newAET);
        }
        setRetrieveAETs(AETs.update(getRetrieveAETs(), oldAET, newAET));
    }
    
    /**    
     * @ejb.interface-method
     */
    public void removeMPPS() {      
        this.setMpps(null);
    }

    private void updateMpps() {
        final String ppsiuid = getPpsIuid();
        MPPSLocal mpps = null;
        if (ppsiuid != null) try {
            mpps = mppsHome.findBySopIuid(ppsiuid);
            PatientLocal pat = this.getStudy().getPatient();
            if ( pat.getPk() != mpps.getPatient().getPk() ) {
                mpps.setPatient(pat);
            }
        } catch (ObjectNotFoundException ignore) {
        } catch (FinderException e) {
            throw new EJBException(e);
        }
        setMpps(mpps);
    }
	
    public void ejbRemove() throws RemoveException {
        log.info("Deleting " + prompt());
    }

    /**
     * 
     * @ejb.interface-method
     */
    public void setAttributes(Dataset ds) {
        AttributeFilter filter = AttributeFilter.getSeriesAttributeFilter();
        setSeriesIuid(ds.getString(Tags.SeriesInstanceUID));
        setSeriesNumber(filter.getString(ds, Tags.SeriesNumber));
        setModality(filter.getString(ds, Tags.Modality));
        setBodyPartExamined(filter.getString(ds, Tags.BodyPartExamined));
        setLaterality(filter.getString(ds, Tags.Laterality));
        setSeriesDescription(filter.getString(ds, Tags.SeriesDescription));
        setInstitutionName(filter.getString(ds, Tags.InstitutionName));
        setInstitutionalDepartmentName(
                filter.getString(ds, Tags.InstitutionalDepartmentName));
        setStationName(filter.getString(ds, Tags.StationName));
        PersonName pn = ds.getPersonName(Tags.PerformingPhysicianName);
        if (pn != null) {
            setPerformingPhysicianName(
                    filter.toUpperCase(pn.toComponentGroupString(false),
                            Tags.PerformingPhysicianName));
            PersonName ipn = pn.getIdeographic();
            if (ipn != null) {
                setPerformingPhysicianIdeographicName(ipn.toComponentGroupString(false));
            }
            PersonName ppn = pn.getPhonetic();
            if (ppn != null) {
                setPerformingPhysicianPhoneticName(ppn.toComponentGroupString(false));
             }
        }
        if (AttributeFilter.isSoundexEnabled()) {
            setPerformingPhysicianFamilyNameSoundex(
                    AttributeFilter.toSoundex(pn, PersonName.FAMILY, "*"));
            setPerformingPhysicianGivenNameSoundex(
                    AttributeFilter.toSoundex(pn, PersonName.GIVEN, "*"));
        }
        try {
            setPpsStartDateTime(
                    ds.getDateTime(Tags.PPSStartDate, Tags.PPSStartTime));
        } catch (IllegalArgumentException e) {
            log.warn("Illegal PPS Date/Time format: " + e.getMessage());
        }
        Dataset refPPS = ds.getItem(Tags.RefPPSSeq);
        setPpsIuid(refPPS == null ? null : refPPS.getString(Tags.RefSOPInstanceUID));
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
        if ( getSourceAET() == null ) {
            ds.setPrivateCreatorID(PrivateTags.CreatorID);
            setSourceAET(ds.getString(PrivateTags.CallingAET));
            ds.setPrivateCreatorID(null);
        }
    }

    private void setField(String field, String value ) {
        try {
            Method m = SeriesBean.class.getMethod("set" 
                    + Character.toUpperCase(field.charAt(0))
                    + field.substring(1), STRING_PARAM);
            m.invoke(this, new Object[] { value });
        } catch (Exception e) {
            throw new ConfigurationException(e);
        }       
    }

    /**
     * @throws DcmServiceException 
     * @ejb.interface-method
     */
    public void coerceAttributes(Dataset ds, Dataset coercedElements)
    throws DcmServiceException {
        AttributeFilter filter = AttributeFilter.getSeriesAttributeFilter();
        Dataset attrs = getAttributes(false);
        Dataset oldInstCode = attrs.getItem(Tags.InstitutionCodeSeq);
        if (filter.isOverwrite()) {
            if (filter.isMerge()) {
                AttrUtils.updateAttributes(attrs, filter.filter(ds), null, log);
            } else {
                attrs = ds;
            }
            setAttributes(attrs);
        } else {
            AttrUtils.coerceAttributes(attrs, ds, coercedElements, filter, log);
            if (filter.isMerge()
                    && AttrUtils.mergeAttributes(attrs, filter.filter(ds), log)) {
                setAttributes(attrs);
            }
        }
        updateInstitutionCode(oldInstCode,
                attrs.getItem(Tags.InstitutionCodeSeq));
    }

    /**
     * @ejb.interface-method
     */
    public Dataset getAttributes(boolean supplement) {
        Dataset ds = DatasetUtils.fromByteArray(getEncodedAttributes());
        if (ds.isEmpty()) {
            log.warn("Empty Dataset in Series BLOB (pk:"+getPk()+")! Use Dataset with DB values");
            ds.putUI(Tags.SeriesInstanceUID, this.getSeriesIuid());
            ds.putIS(Tags.SeriesNumber, this.getSeriesNumber());
            ds.putCS(Tags.Modality, this.getModality());
            ds.putCS(Tags.BodyPartExamined, this.getBodyPartExamined());
            ds.putCS(Tags.Laterality, this.getLaterality());
            ds.putLO(Tags.SeriesDescription, this.getSeriesDescription());
            ds.putLO(Tags.InstitutionName, this.getInstitutionName());
            ds.putLO(Tags.InstitutionalDepartmentName, this.getInstitutionalDepartmentName());
            ds.putSH(Tags.StationName, this.getStationName());
            ds.putPN(Tags.PerformingPhysicianName, this.getPerformingPhysicianName());
            ds.putDA(Tags.PPSStartDate, this.getPpsStartDateTime());
            ds.putTM(Tags.PPSStartTime, this.getPpsStartDateTime());
        }
        if (supplement) {
            ds.setPrivateCreatorID(PrivateTags.CreatorID);
            ds.putOB(PrivateTags.SeriesPk, Convert.toBytes(getPk().longValue()));
            ds.putAE(PrivateTags.CallingAET, getSourceAET());
            ds.setPrivateCreatorID(null);
            ds.putIS(Tags.NumberOfSeriesRelatedInstances,
                    getNumberOfSeriesRelatedInstances());
            ds.putSH(Tags.StorageMediaFileSetID, getFilesetId());
            ds.putUI(Tags.StorageMediaFileSetUID, getFilesetIuid());
            DatasetUtils.putRetrieveAET(ds, getRetrieveAETs(),
            		getExternalRetrieveAET());
            ds.putCS(Tags.InstanceAvailability,
            		Availability.toString(getAvailabilitySafe()));
        }
        return ds;
    }

    /**
     * 
     * @ejb.interface-method
     */
    public String asString() {
        return prompt();
    }

    private String prompt() {
        return "Series[pk=" + getPk() + ", uid=" + getSeriesIuid()
                + ", study->" + getStudy() + "]";
    }

    /**
     * @ejb.select query="SELECT OBJECT(f) FROM File f WHERE f.instance.series.pk = ?1 AND f.fileSystem.pk = ?2"
     *             transaction-type="Supports"
     */
    public abstract Collection ejbSelectFiles(java.lang.Long series_fk, java.lang.Long filesystem_fk)
            throws FinderException;

    /**    
     * @ejb.interface-method
     */
    public Collection getFiles(Long fsPk) throws FinderException {      
        return ejbSelectFiles(getPk(), fsPk);
    }

    /**
     * @ejb.select query="SELECT COUNT(f) FROM File f WHERE f.instance.series.pk = ?1 AND f.fileSystem.pk = ?2"
     *             transaction-type="Supports"
     */
    public abstract int ejbSelectNumberOfFiles(java.lang.Long series_fk, java.lang.Long filesystem_fk)
            throws FinderException;

    /**    
     * @ejb.interface-method
     */
    public int getNumberOfFiles(Long fsPk) throws FinderException {      
        return ejbSelectNumberOfFiles(getPk(), fsPk);
    }

    /**
     * @ejb.select query="SELECT DISTINCT OBJECT(i) FROM Instance i, IN(i.files) f WHERE i.series.pk = ?1 AND f.fileSystem.pk = ?2"
     *             transaction-type="Supports"
     */
    public abstract Collection ejbSelectInstances(java.lang.Long series_fk, java.lang.Long filesystem_fk)
            throws FinderException;

    /**    
     * @ejb.interface-method
     */
    public Collection getInstances(Long fsPk) throws FinderException {      
        return ejbSelectInstances(getPk(), fsPk);
    }

    /**
     * @ejb.select query="SELECT OBJECT(f) FROM File f WHERE f.instance.series.pk = ?1"
     *             transaction-type="Supports"
     */
    public abstract Collection ejbSelectAllFiles(java.lang.Long series_fk)
            throws FinderException;

    /**    
     * @ejb.interface-method
     */
    public Collection getAllFiles() throws FinderException {      
        return ejbSelectAllFiles(getPk());
    }    
        
    /**
     * @ejb.home-method
     */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Collection ejbHomeListByIUIDs(String[] iuids) throws FinderException {
        if (iuids == null || iuids.length < 1)
            return new ArrayList();
        
        log.debug("List by IUIDs:" + iuids.length);
        long t0 = System.currentTimeMillis();
        Iterator<DynamicQueryBuilder.DynamicQuery> dynamicQueries = dynamicQueryBuilder.getDynamicQueries("SELECT OBJECT(s) FROM Series s WHERE s.seriesIuid", iuids, 1);
        
        DynamicQueryBuilder.DynamicQuery dynamicQuery = dynamicQueries.next();
        Collection c = execute(dynamicQuery);

        while (dynamicQueries.hasNext()) {
        	c.addAll(execute(dynamicQueries.next()));
        }
        
        log.debug("Total time:" + (System.currentTimeMillis() - t0));
        
        return c;
    }

	@SuppressWarnings("rawtypes")
	private Collection execute(DynamicQueryBuilder.DynamicQuery dynamicQuery)
			throws FinderException {
		log.debug("Execute JBossQL: " + dynamicQuery.getJbossQl());
		
        return ejbSelectGeneric(dynamicQuery.getJbossQl(), dynamicQuery.getArgs());
	}
}
