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
import org.dcm4che.data.PersonName;
import org.dcm4che.dict.Tags;
import org.dcm4chex.archive.ejb.conf.AttributeFilter;
import org.dcm4chex.archive.ejb.interfaces.IssuerLocal;
import org.dcm4chex.archive.ejb.interfaces.IssuerLocalHome;
import org.dcm4chex.archive.ejb.interfaces.SeriesLocal;

/**
 * @author gunter.zeilinger@tiani.com
 * @version Revision $Date: 2010-12-24 16:10:14 +0000 (Fri, 24 Dec 2010) $
 * @since 01.04.2005
 * 
 * @ejb.bean name="SeriesRequest" type="CMP" view-type="local"
 *           local-jndi-name="ejb/SeriesRequest" primkey-field="pk"
 * @jboss.container-configuration name="Instance Per Transaction CMP 2.x EntityBean"
 * @ejb.persistence table-name="series_req"
 * @ejb.transaction type="Required"
 * @jboss.entity-command name="hsqldb-fetch-key"
 *
 * @ejb.finder signature="Collection findAll()"
 *             query="SELECT OBJECT(r) FROM SeriesRequest AS r"
 *             transaction-type="Supports"
 * @ejb.ejb-ref ejb-name="Issuer" view-type="local" ref-name="ejb/Issuer"
 */

public abstract class SeriesRequestBean implements EntityBean {

    private static final Logger log = Logger.getLogger(SeriesRequestBean.class);

    private IssuerLocalHome issuerHome;

    public void setEntityContext(EntityContext ctx) {
        Context jndiCtx = null;
        try {
            jndiCtx = new InitialContext();
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
        issuerHome = null;
    }

    /**
     * @ejb.create-method
     */
    public Long ejbCreate(Dataset ds, SeriesLocal series)
            throws CreateException {
        AttributeFilter filter = AttributeFilter.getSeriesAttributeFilter();
        setAccessionNumber(ds.getString(Tags.AccessionNumber));
        setStudyIuid(ds.getString(Tags.StudyInstanceUID));
        setRequestedProcedureId(filter.getString(ds, Tags.RequestedProcedureID));
        setSpsId(filter.getString(ds, Tags.SPSID));
        setRequestingService(filter.getString(ds, Tags.RequestingService));
        PersonName pn = ds.getPersonName(Tags.RequestingPhysician);
        if (pn != null) {
            setRequestingPhysician(
                    filter.toUpperCase(pn.toComponentGroupString(false),
                            Tags.RequestingPhysician));
            PersonName ipn = pn.getIdeographic();
            if (ipn != null) {
                setRequestingPhysicianIdeographicName(
                        ipn.toComponentGroupString(false));                
            }
            PersonName ppn = pn.getPhonetic();
            if (ppn != null) {
                setRequestingPhysicianPhoneticName(
                        ppn.toComponentGroupString(false));                
            }
        }
        if (AttributeFilter.isSoundexEnabled()) {
            setRequestingPhysicianFamilyNameSoundex(
                    AttributeFilter.toSoundex(pn, PersonName.FAMILY, "*"));
            setRequestingPhysicianGivenNameSoundex(
                    AttributeFilter.toSoundex(pn, PersonName.GIVEN, "*"));
        }
        return null;
    }

    public void ejbPostCreate(Dataset ds, SeriesLocal series)
            throws CreateException {
        setSeries(series);
        try {
            setIssuerOfAccessionNumber(
                    IssuerBean.valueOf(issuerHome, ds.getItem(Tags.IssuerOfAccessionNumberSeq)));
        } catch (FinderException e) {
            throw new CreateException(e.getMessage());
        }
        log.info("Created " + prompt());
    }

    public void ejbRemove() throws RemoveException {
        log.info("Deleting " + prompt());
    }
    
    /**
     * Auto-generated Primary Key
     * 
     * @ejb.interface-method
     * @ejb.pk-field
     * @ejb.persistence column-name="pk"
     * @jboss.persistence auto-increment="true"
     *  
     */
    public abstract Long getPk();

    public abstract void setPk(Long pk);
    
    /**
     * Accession Number
     *
     * @ejb.interface-method
     * @ejb.persistence column-name="accession_no"
     */
    public abstract String getAccessionNumber();
    public abstract void setAccessionNumber(String no);

    /**
     * @ejb.persistence column-name="study_iuid"
     */
    public abstract String getStudyIuid();

    public abstract void setStudyIuid(String uid);
   
    /**
     * @ejb.persistence column-name="req_proc_id"
     */
    public abstract String getRequestedProcedureId();

    public abstract void setRequestedProcedureId(String id);

    /**
     * @ejb.persistence column-name="sps_id"
     */
    public abstract String getSpsId();

    public abstract void setSpsId(String no);

    /**
     * @ejb.persistence column-name="req_service"
     */
    public abstract String getRequestingService();

    public abstract void setRequestingService(String service);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="req_physician"
     */
    public abstract String getRequestingPhysician();
    public abstract void setRequestingPhysician(String name);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="req_phys_fn_sx"
     */
    public abstract String getRequestingPhysicianFamilyNameSoundex();

    /**
     * @ejb.interface-method
     */
    public abstract void setRequestingPhysicianFamilyNameSoundex(String name);
        
    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="req_phys_gn_sx"
     */
    public abstract String getRequestingPhysicianGivenNameSoundex();

    /**
     * @ejb.interface-method
     */
    public abstract void setRequestingPhysicianGivenNameSoundex(String name);

    /**
     * @ejb.persistence column-name="req_phys_i_name"
     */
    public abstract String getRequestingPhysicianIdeographicName();
    public abstract void setRequestingPhysicianIdeographicName(String name);

    /**
     * @ejb.persistence column-name="req_phys_p_name"
     */
    public abstract String getRequestingPhysicianPhoneticName();
    public abstract void setRequestingPhysicianPhoneticName(String name);
    
    /**
     * @ejb.relation name="series-request-attributes"
     *               role-name="request-attributes-of-series"
     *               cascade-delete="yes"
     * @jboss.relation fk-column="series_fk" related-pk-field="pk"
     */
    public abstract void setSeries(SeriesLocal series);

    /**
     * @ejb.relation name="series-request-issuer-of-accno" role-name="series-request-with-issuer-of-accno"
     *               target-ejb="Issuer" target-role-name="issuer-of-series-request-accno"
     *               target-multiple="yes"
     * @jboss.relation fk-column="accno_issuer_fk" related-pk-field="pk"
     */
    public abstract IssuerLocal getIssuerOfAccessionNumber();
    public abstract void setIssuerOfAccessionNumber(IssuerLocal issuer);

    /**
     * @ejb.interface-method
     */
    public abstract SeriesLocal getSeries();

    private String prompt() {
        return "SeriesRequestAttribute[pk=" + getPk() 
                + ", accno=" + getAccessionNumber()
                + ", rpid=" + getRequestedProcedureId()
                + ", spsid=" + getSpsId()
                + ", service=" + getRequestingService()
                + ", phys=" + getRequestingPhysician()
                + ", series->" + getSeries() + "]";
    }
    
}
