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

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

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
import org.dcm4chex.archive.common.DatasetUtils;
import org.dcm4chex.archive.common.PatientMatching;
import org.dcm4chex.archive.common.PrivateTags;
import org.dcm4chex.archive.ejb.conf.AttributeFilter;
import org.dcm4chex.archive.ejb.interfaces.OtherPatientIDLocal;
import org.dcm4chex.archive.ejb.interfaces.OtherPatientIDLocalHome;
import org.dcm4chex.archive.ejb.interfaces.PatientLocal;
import org.dcm4chex.archive.ejb.interfaces.PatientLocalHome;
import org.dcm4chex.archive.ejb.interfaces.StudyLocal;
import org.dcm4chex.archive.exceptions.CircularMergedException;
import org.dcm4chex.archive.exceptions.ConfigurationException;
import org.dcm4chex.archive.exceptions.NonUniquePatientException;
import org.dcm4chex.archive.exceptions.NonUniquePatientIDException;
import org.dcm4chex.archive.exceptions.PatientMergedException;
import org.dcm4chex.archive.util.Convert;

/**
 * @ejb.bean name="Patient" type="CMP" view-type="local"
 *           local-jndi-name="ejb/Patient" primkey-field="pk"
 * @jboss.container-configuration name="Instance Per Transaction CMP 2.x EntityBean"
 * @ejb.transaction type="Required"
 * @ejb.persistence table-name="patient"
 * @jboss.load-group name="pid"
 * @jboss.entity-command name="hsqldb-fetch-key"
 * @jboss.audit-created-time field-name="createdTime"
 * @jboss.audit-updated-time field-name="updatedTime"
 * 
 * @ejb.finder signature="Collection findAll()"
 *             query="SELECT OBJECT(p) FROM Patient AS p"
 *             transaction-type="Supports"
 * @ejb.finder signature="Collection findAll(int offset, int limit)"
 *             query=""
 *             transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findAll(int offset, int limit)"
 *              query="SELECT OBJECT(p) FROM Patient AS p ORDER BY p.pk OFFSET ?1 LIMIT ?2"
 *              strategy="on-find"
 * @ejb.finder signature="java.util.Collection findByPatientId(java.lang.String pid)"
 *             query="SELECT OBJECT(p) FROM Patient AS p WHERE p.patientId = ?1"
 *             transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findByPatientId(java.lang.String pid)"
 *              strategy="on-find" eager-load-group="*"
 * 
 * @ejb.finder signature="java.util.Collection findByPatientIdLike(java.lang.String pid)"
 *             query="SELECT OBJECT(p) FROM Patient AS p WHERE p.patientId LIKE ?1"
 *             transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findByPatientIdLike(java.lang.String pid)"
 *              strategy="on-find" eager-load-group="*"
 * 
 * @ejb.finder signature="org.dcm4chex.archive.ejb.interfaces.PatientLocal findByPatientIdWithIssuer(java.lang.String pid, java.lang.String issuer)"
 *             query="SELECT OBJECT(p) FROM Patient AS p WHERE p.patientId = ?1 AND p.issuerOfPatientId = ?2"
 *             transaction-type="Supports"
 * @jboss.query signature="org.dcm4chex.archive.ejb.interfaces.PatientLocal findByPatientIdWithIssuer(java.lang.String pid, java.lang.String issuer)"
 *              strategy="on-find" eager-load-group="*"
 *
 * @ejb.finder signature="java.util.Collection findByPatientIdWithIssuerLike(java.lang.String pid, java.lang.String issuer)"
 *             query="SELECT OBJECT(p) FROM Patient AS p WHERE p.patientId LIKE ?1 AND p.issuerOfPatientId = ?2"
 *             transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findByPatientIdWithIssuerLike(java.lang.String pid, java.lang.String issuer)"
 *              strategy="on-find" eager-load-group="*"
 *
 * @ejb.finder signature="java.util.Collection findByPatientName(java.lang.String pn)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findByPatientName(java.lang.String pn)"
 *             query="SELECT OBJECT(p) FROM Patient AS p WHERE p.patientName LIKE ?1"
 *             strategy="on-find" eager-load-group="*"
 *
 * @ejb.finder signature="java.util.Collection findCorresponding(java.lang.String pid, java.lang.String issuer)"
 *             query="SELECT DISTINCT OBJECT(p1) FROM Patient AS p1,
 *             IN(p1.otherPatientIds) opid,
 *             IN(opid.patients) p2
 *             WHERE (p1.patientId = ?1 AND p1.issuerOfPatientId = ?2)
 *             OR (p2.patientId = ?1 AND p2.issuerOfPatientId = ?2)
 *             OR (opid.patientId = ?1 AND opid.issuerOfPatientId = ?2)"
 *             transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findCorresponding(java.lang.String pid, java.lang.String issuer)"
 *              strategy="on-find" eager-load-group="pid"
 *
 * @ejb.finder signature="java.util.Collection findCorrespondingLike(java.lang.String pid, java.lang.String issuer)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findCorrespondingLike(java.lang.String pid, java.lang.String issuer)"
 *             query="SELECT DISTINCT OBJECT(p1) FROM Patient AS p1,
 *             IN(p1.otherPatientIds) opid,
 *             IN(opid.patients) p2
 *             WHERE (p1.patientId LIKE ?1 AND p1.issuerOfPatientId = ?2)
 *             OR (p2.patientId LIKE ?1 AND p2.issuerOfPatientId = ?2)
 *             OR (opid.patientId LIKE ?1 AND opid.issuerOfPatientId = ?2)"
 *             strategy="on-find" eager-load-group="pid"
 *
 * @ejb.finder signature="java.util.Collection findCorrespondingByPrimaryPatientID(java.lang.String pid, java.lang.String issuer)"
 *             query="SELECT DISTINCT OBJECT(p1) FROM Patient AS p1,
 *             IN(p1.otherPatientIds) opid,
 *             IN(opid.patients) p2
 *             WHERE (p1.patientId = ?1 AND p1.issuerOfPatientId = ?2)
 *             OR (p2.patientId = ?1 AND p2.issuerOfPatientId = ?2)"
 *             transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findCorrespondingByPrimaryPatientID(java.lang.String pid, java.lang.String issuer)"
 *              strategy="on-find" eager-load-group="pid"
 *
 * @ejb.finder signature="java.util.Collection findCorrespondingByPrimaryPatientIDLike(java.lang.String pid, java.lang.String issuer)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findCorrespondingByPrimaryPatientIDLike(java.lang.String pid, java.lang.String issuer)"
 *             query="SELECT DISTINCT OBJECT(p1) FROM Patient AS p1,
 *             IN(p1.otherPatientIds) opid,
 *             IN(opid.patients) p2
 *             WHERE (p1.patientId LIKE ?1 AND p1.issuerOfPatientId = ?2)
 *             OR (p2.patientId LIKE ?1 AND p2.issuerOfPatientId = ?2)"
 *             strategy="on-find" eager-load-group="pid"
 *
 * @ejb.finder signature="java.util.Collection findCorrespondingByOtherPatientID(java.lang.String pid, java.lang.String issuer)"
 *             query="SELECT OBJECT(p1) FROM Patient AS p1,
 *             IN(p1.otherPatientIds) opid
 *             WHERE (opid.patientId = ?1 AND opid.issuerOfPatientId = ?2)"
 *             transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findCorrespondingByOtherPatientID(java.lang.String pid, java.lang.String issuer)"
 *              strategy="on-find" eager-load-group="pid"
 *
 * @ejb.finder signature="java.util.Collection findCorrespondingByOtherPatientIDLike(java.lang.String pid, java.lang.String issuer)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findCorrespondingByOtherPatientIDLike(java.lang.String pid, java.lang.String issuer)"
 *             query="SELECT OBJECT(p1) FROM Patient AS p1,
 *             IN(p1.otherPatientIds) opid
 *             WHERE (opid.patientId LIKE ?1 AND opid.issuerOfPatientId = ?2)"
 *             strategy="on-find" eager-load-group="pid"
 *
 * @ejb.ejb-ref ejb-name="OtherPatientID" view-type="local" ref-name="ejb/OtherPatientID"
 *
 * @author <a href="mailto:gunter@tiani.com">Gunter Zeilinger</a>
 *
 */
public abstract class PatientBean implements EntityBean {

    private static final Logger log = Logger.getLogger(PatientBean.class);

    private static final int[] OTHER_PID_SQ = { Tags.OtherPatientIDSeq};
    
    private static final Class[] STRING_PARAM = new Class[] { String.class };

    private OtherPatientIDLocalHome opidHome;

    private EntityContext ctx;

    public void setEntityContext(EntityContext ctx) {
        Context jndiCtx = null;
        try {
            this.ctx = ctx;
            jndiCtx = new InitialContext();
            opidHome = (OtherPatientIDLocalHome)
                    jndiCtx.lookup("java:comp/env/ejb/OtherPatientID");
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
        ctx = null;
        opidHome = null;
    }

    /**
     * Auto-generated Primary Key
     *
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

    public abstract void setUpdatedTime(java.sql.Timestamp time);
	
    /**
     * Patient ID
     *
     * @ejb.interface-method
     * @ejb.persistence column-name="pat_id"
     * @jboss.load-group name="pid"
     */
    public abstract String getPatientId();

    public abstract void setPatientId(String pid);

    /**
     * Patient ID Issuer
     *
     * @ejb.interface-method
     * @ejb.persistence column-name="pat_id_issuer"
     * @jboss.load-group name="pid"
     */
    public abstract String getIssuerOfPatientId();

    /**
     * @ejb.interface-method
     */
    public abstract void setIssuerOfPatientId(String issuer);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="pat_name"
     */
    public abstract String getPatientName();
    public abstract void setPatientName(String name);
        
    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="pat_fn_sx"
     */
    public abstract String getPatientFamilyNameSoundex();

    /**
     * @ejb.interface-method
     */
    public abstract void setPatientFamilyNameSoundex(String name);
        
    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="pat_gn_sx"
     */
    public abstract String getPatientGivenNameSoundex();

    /**
     * @ejb.interface-method
     */
    public abstract void setPatientGivenNameSoundex(String name);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="pat_i_name"
     */
    public abstract String getPatientIdeographicName();

    /**
     * @ejb.interface-method
     */
    public abstract void setPatientIdeographicName(String name);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="pat_p_name"
     */
    public abstract String getPatientPhoneticName();
    public abstract void setPatientPhoneticName(String name);

    /**
     * Patient Birth Date
     *
     * @ejb.interface-method
     * @ejb.persistence column-name="pat_birthdate"
     */
    public abstract String getPatientBirthDate();

    /**
     * @ejb.interface-method
     */
    public abstract void setPatientBirthDate(String dateString);

    /**
     * Patient Sex
     *
     * @ejb.interface-method
     * @ejb.persistence column-name="pat_sex"
     */
    public abstract String getPatientSex();

    /**
     * @ejb.interface-method
     *
     */
    public abstract void setPatientSex(String sex);

    /**
     * @ejb.persistence column-name="pat_custom1"
     */
    public abstract String getPatientCustomAttribute1();
    public abstract void setPatientCustomAttribute1(String value);

    /**
     * @ejb.persistence column-name="pat_custom2"
     */
    public abstract String getPatientCustomAttribute2();
    public abstract void setPatientCustomAttribute2(String value);

    /**
     * @ejb.persistence column-name="pat_custom3"
     */
    public abstract String getPatientCustomAttribute3();
    public abstract void setPatientCustomAttribute3(String value);

   /**
     * Patient DICOM Attributes
     *
     * @ejb.persistence
     *  column-name="pat_attrs"
     * 
     */
    public abstract byte[] getEncodedAttributes();

    public abstract void setEncodedAttributes(byte[] bytes);

    /**
     * @ejb.interface-method
     * @ejb.relation name="patient-other-pid" role-name="patient-with other-pids"
     * @jboss.relation-table table-name="rel_pat_other_pid"
     * @jboss.relation fk-column="other_pid_fk" related-pk-field="pk"     
     */
    public abstract java.util.Collection getOtherPatientIds();
    public abstract void setOtherPatientIds(java.util.Collection otherPIds);
    
    /**
     * @return Patient, with which this Patient was merged.
     *
     * @ejb.interface-method view-type="local"
     * @ejb.relation name="merged-patients"
     *    role-name="dereferenced-patient"
     *    cascade-delete="yes"
     *
     * @jboss.relation fk-column="merge_fk" related-pk-field="pk"
     */
    public abstract PatientLocal getMergedWith();

    /**
     * @param mergedWith, Patient, with which this Patient was merged.
     *
     * @ejb.interface-method
     */
    public abstract void setMergedWith(PatientLocal mergedWith);

    /**
     * @ejb.interface-method view-type="local"
     * @ejb.relation name="merged-patients"
     *    role-name="dominant-patient"
     *    
     * @return all patients merged with this patient
     */
    public abstract java.util.Collection getMerged();
    public abstract void setMerged(java.util.Collection patients);

    /**
     * @ejb.interface-method view-type="local"
     *
     * @param studies all studies of this patient
     */
    public abstract void setStudies(java.util.Collection studies);

    /**
     * @ejb.interface-method view-type="local"
     * @ejb.relation name="patient-study" role-name="patient-has-studies"
     *    
     * @return all studies of this patient
     */
    public abstract java.util.Collection getStudies();

    /**
     * @ejb.interface-method view-type="local"
     */
    public abstract void setMwlItems(java.util.Collection mwlItems);

    /**
     * @ejb.interface-method view-type="local"
     * @ejb.relation name="patient-mwlitems" role-name="patient-has-mwlitems"
     */
    public abstract java.util.Collection getMwlItems();

    /**
     * @ejb.interface-method view-type="local"
     */
    public abstract void setMpps(java.util.Collection mpps);

    /**
     * @ejb.interface-method view-type="local"
     * @ejb.relation name="patient-mpps" role-name="patient-has-mpps"
     */
    public abstract java.util.Collection getMpps();

    /**
     * @ejb.interface-method view-type="local"
     */
    public abstract void setGppps(java.util.Collection mpps);

    /**
     * @ejb.interface-method view-type="local"
     * @ejb.relation name="patient-gppps" role-name="patient-has-gppps"
     */
    public abstract java.util.Collection getGppps();

    /**
     * @ejb.interface-method view-type="local"
     * @ejb.relation name="patient-gpsps" role-name="patient-has-gpsps"
     */
    public abstract java.util.Collection getGsps();

    /**
     * @ejb.interface-method view-type="local"
     */
    public abstract void setGsps(java.util.Collection gsps);

    /**
     * @ejb.interface-method view-type="local"
     * @ejb.relation name="patient-ups" role-name="patient-has-ups"
     */
    public abstract java.util.Collection getUPS();

    /**
     * @ejb.interface-method view-type="local"
     */
    public abstract void setUPS(java.util.Collection ups);

    /**
     * Create patient.
     *
     * @ejb.create-method
     */
    public Long ejbCreate(Dataset ds) throws CreateException {
        setAttributes(ds);
        return null;
    }

    public void ejbPostCreate(Dataset ds) throws CreateException {
        try {
            createOtherPatientIds(ds.get(Tags.OtherPatientIDSeq));
        } catch (FinderException e) {
            throw new EJBException(e);
        }
        log.info("Created " + prompt());
    }

    private void createOtherPatientIds(DcmElement opidsq)
            throws CreateException, FinderException {
        if (opidsq == null || opidsq.isEmpty() || opidsq.getItem().isEmpty()) {
            return;
        }
        AttributeFilter filter = AttributeFilter.getPatientAttributeFilter();
        Collection opids = getOtherPatientIds();
        for (int i = 0, n = opidsq.countItems(); i < n; i++) {
            Dataset opid = opidsq.getItem(i);
            opids.add(opidHome.valueOf(
                    filter.getString(opid, Tags.PatientID),
                    filter.getString(opid, Tags.IssuerOfPatientID)));
        }
    }

    public void ejbRemove() throws RemoveException {
        log.info("Deleting " + prompt());
        // Remove OtherPatientIDs only related to this Patient
        for ( Iterator iter = getOtherPatientIds().iterator() ; iter.hasNext() ; ) {
            OtherPatientIDLocal opid = (OtherPatientIDLocal) iter.next();
            if (opid.getPatients().size() == 1) {
            	iter.remove();
                opid.remove();
            }
        }
        // we have to delete studies explicitly here due to an foreign key constrain error 
        // if an mpps key is set in one of the series.
        for ( Iterator iter = getStudies().iterator() ; iter.hasNext() ; ) {
            StudyLocal study = (StudyLocal) iter.next();
            iter.remove(); 
            study.remove();
        }
    }

    /**
     * @ejb.home-method
     */
    public PatientLocal ejbHomeSelectPatient(String pid, String issuer)
            throws FinderException {
        if (pid == null) {
            throw new ObjectNotFoundException();
        }
        AttributeFilter filter = AttributeFilter.getPatientAttributeFilter();
        pid = filter.toUpperCase(pid, Tags.PatientID);
        issuer = filter.toUpperCase(issuer, Tags.IssuerOfPatientID);
        PatientLocalHome patHome = (PatientLocalHome) ctx.getEJBLocalHome();
        Collection<PatientLocal> c = patHome.findByPatientId(pid);
        if (issuer != null)
            matchIssuer(true, issuer, c);
        Iterator<PatientLocal> patIter = c.iterator();
        if (!patIter.hasNext())
            throw new ObjectNotFoundException();
        PatientLocal pat = patIter.next();
        if (patIter.hasNext())
            throw new NonUniquePatientException("Patient[id="
                    + pid + ", issuer=" + issuer + "] ambiguous");
        return pat;
    }

    /**
     * @ejb.home-method
     */
    public PatientLocal ejbHomeSelectPatient(Dataset ds,
            PatientMatching matching, boolean followMerged)
            throws FinderException {
        String pid = ds.getString(Tags.PatientID);
        String issuer = ds.getString(Tags.IssuerOfPatientID);
        PersonName pn = ds.getPersonName(Tags.PatientName);
        String familyName = pn != null ? pn.get(PersonName.FAMILY) : null;
        String givenName = pn != null ? pn.get(PersonName.GIVEN) : null;
        String middleName = pn != null ? pn.get(PersonName.MIDDLE) : null;
        String namePrefix = pn != null ? pn.get(PersonName.PREFIX) : null;
        String nameSuffix = pn != null ? pn.get(PersonName.SUFFIX) : null;
        String birthdate = normalizeDA(ds.getString(Tags.PatientBirthDate));
        String sex = ds.getString(Tags.PatientSex);
        return selectPatient(pid, issuer, familyName, givenName, middleName,
                namePrefix, nameSuffix, birthdate, sex, matching, followMerged);
    }

    private PatientLocal selectPatient(String pid, String issuer,
            String familyName, String givenName, String middleName,
            String namePrefix, String nameSuffix, String birthdate,
            String sex, PatientMatching matching, boolean followMerged)
            throws ObjectNotFoundException, FinderException,
            NonUniquePatientException, NonUniquePatientIDException,
            CircularMergedException, PatientMergedException {
        if (matching.noMatchesFor(pid, issuer, familyName, givenName,
                middleName, namePrefix, nameSuffix, birthdate, sex)) {
            throw new ObjectNotFoundException();
        }
        AttributeFilter filter = AttributeFilter.getPatientAttributeFilter();
        pid = filter.toUpperCase(pid, Tags.PatientID);
        issuer = filter.toUpperCase(issuer, Tags.IssuerOfPatientID);
        if (filter.isICase(Tags.PatientName)) {
            familyName = AttributeFilter.toUpperCase(familyName);
            givenName = AttributeFilter.toUpperCase(givenName);
            middleName = AttributeFilter.toUpperCase(middleName);
            namePrefix = AttributeFilter.toUpperCase(namePrefix);
            nameSuffix = AttributeFilter.toUpperCase(nameSuffix);
        }
        PatientLocalHome patHome = (PatientLocalHome) ctx.getEJBLocalHome();
        Collection<PatientLocal> c;
        if (pid != null) {
            c = patHome.findByPatientId(pid);
            if (c.isEmpty()) {
                throw new ObjectNotFoundException();
            }
            if (issuer != null) {
                int countWithIssuer = matchIssuer(
                        matching.isUnknownIssuerAlwaysMatch(), issuer, c);
                if (!matching.isTrustPatientIDWithIssuer() || countWithIssuer == 0) {
                    PatientLocal matchWithIssuer = (countWithIssuer > 0) ?
                                (PatientLocal) c.iterator().next() : null;
                    matchDemographics(matching, familyName, givenName,
                            middleName, namePrefix, nameSuffix, birthdate, sex, c);
                    if (matchWithIssuer != null && c.isEmpty()) {
                        throw new NonUniquePatientIDException(
                                "Existing " + matchWithIssuer.asString() +
                                " with equal Patient ID but different demographis than Patient[id="
                                + pid + ", issuer=" + issuer
                                + ", name=" + familyName + '^' + givenName
                                + '^' + namePrefix + '^' + nameSuffix + "]");
                    }
                }
            } else {
                matchDemographics(matching, familyName, givenName,
                        middleName, namePrefix, nameSuffix, birthdate, sex, c);
            }
        } else {
            c = patHome.findByPatientName(familyName.toUpperCase() + "^%");
            if (c.isEmpty()) {
                throw new ObjectNotFoundException();
            }
            matchDemographics(matching, familyName, givenName, middleName,
                    namePrefix, nameSuffix, birthdate, sex, c);
        }
        Iterator<PatientLocal> patIter = c.iterator();
        if (!patIter.hasNext()) {
            throw new ObjectNotFoundException();
        }
        PatientLocal pat = patIter.next();
        if (patIter.hasNext()) {
            throw new NonUniquePatientException("Patient[id="
                    + pid + ", issuer=" + issuer
                    + ", name=" + familyName + '^' + givenName + '^'
                    + namePrefix + '^' + nameSuffix + "] ambiguous");
        }
        PatientLocal merged = pat.getMergedWith();
        if (merged != null) {
            if (followMerged) {
                PatientLocal pat1 = pat;
                while (merged != null) {
                    if (merged.isIdentical(pat1)) {
                        String prompt = "Detect circular merged Patient "
                            + pat1.asString();
                        log.warn(prompt);
                        throw new CircularMergedException(prompt);
                    }
                    pat = merged;
                    merged = pat.getMergedWith();
                }
            } else {
                if (pat.getIssuerOfPatientId() == null && issuer != null) {
                    throw new ObjectNotFoundException("Select patient with issuer but found only merged patient without issuer!");
                }
                String prompt = "Patient ID[id="
                    + pat.getPatientId() + ",issuer="
                    + pat.getIssuerOfPatientId()
                    + "] merged with Patient ID[id="
                    + merged.getPatientId() + ",issuer=" 
                    + merged.getIssuerOfPatientId() + "]";
                log.warn(prompt);
                throw new PatientMergedException(prompt);
            }
        }
        return pat;
    }

    private int matchIssuer(boolean unknownIssuerAlwaysMatch, String issuer,
            Collection<PatientLocal> c) {
        int countWithIssuer = 0;
        for (Iterator<PatientLocal> iter = c.iterator(); iter.hasNext();) {
            PatientLocal pat = iter.next();
            String issuer2 = pat.getIssuerOfPatientId();
            if (issuer2 != null) {
                if (issuer2.equals(issuer)) {
                    countWithIssuer++;
                } else {
                    iter.remove();
                }
            }
        }
        if (countWithIssuer > 0 || !unknownIssuerAlwaysMatch) {
            for (Iterator<PatientLocal> iter = c.iterator(); iter.hasNext();) {
                PatientLocal pat = iter.next();
                String issuer2 = pat.getIssuerOfPatientId();
                if (issuer2 == null) {
                    iter.remove();
                }
            }
        }
        return countWithIssuer;
    }

    private void matchDemographics(PatientMatching matching, String familyName,
            String givenName, String middleName, String namePrefix, 
            String nameSuffix, String birthdate, String sex,
            Collection<PatientLocal> c) throws ObjectNotFoundException {
        if (matching.allMatchesFor(familyName, givenName, middleName,
                namePrefix, nameSuffix, birthdate, sex)) {
            return;
        }
        if (matching.noMatchesFor(familyName, givenName, middleName,
                namePrefix, nameSuffix, birthdate, sex)) {
            throw new ObjectNotFoundException();
        }
        List<Pattern> pnPatterns = matching.compilePNPatterns(
                familyName, givenName, middleName, namePrefix, nameSuffix);
        for (Iterator<PatientLocal>  iter = c.iterator(); iter.hasNext();) {
            PatientLocal pat = iter.next();
            if (!matching.matches(pat.getPatientName(),
                    pat.getPatientBirthDate(), pat.getPatientSex(),
                    pnPatterns.iterator(), birthdate, sex))
                iter.remove();
        }
    }

     /**
     * @ejb.interface-method
     */
    public Dataset getAttributes(boolean supplement) {
        Dataset ds = DatasetUtils.fromByteArray(getEncodedAttributes());
        if (ds.isEmpty()) {
            log.warn("Empty Dataset in Patient BLOB (pk:"+getPk()+")! Use Dataset with DB values");
            ds.putLO(Tags.PatientID, this.getPatientId());
            ds.putLO(Tags.IssuerOfPatientID, this.getIssuerOfPatientId());
            ds.putPN(Tags.PatientName, this.getPatientName());
            ds.putDA(Tags.PatientBirthDate, this.getPatientBirthDate());
            ds.putCS(Tags.PatientSex, this.getPatientSex());
        }
        if (supplement) {
            ds.setPrivateCreatorID(PrivateTags.CreatorID);
            ds.putOB(PrivateTags.PatientPk, Convert.toBytes(getPk().longValue()));
            ds.setPrivateCreatorID(null);
        }
        return ds;
    }

    /**
     * @ejb.interface-method
     */
    public void setAttributes(Dataset ds) {
        AttributeFilter filter = AttributeFilter.getPatientAttributeFilter();
        setPatientId(filter.getString(ds, Tags.PatientID));
        setIssuerOfPatientId(filter.getString(ds, Tags.IssuerOfPatientID));
        PersonName pn = ds.getPersonName(Tags.PatientName);
        if (pn != null) {
            setPatientName(
                    filter.toUpperCase(pn.toComponentGroupString(false),
                            Tags.PatientName));
            PersonName ipn = pn.getIdeographic();
            if (ipn != null) {
                setPatientIdeographicName(ipn.toComponentGroupString(false));
            }
            PersonName ppn = pn.getPhonetic();
            if (ppn != null) {
                setPatientPhoneticName(ppn.toComponentGroupString(false));
            }
        }
        if (AttributeFilter.isSoundexEnabled()) {
            setPatientFamilyNameSoundex(
                    AttributeFilter.toSoundex(pn, PersonName.FAMILY, "*"));
            setPatientGivenNameSoundex(
                    AttributeFilter.toSoundex(pn, PersonName.GIVEN, "*"));
        }
        setPatientBirthDate(normalizeDA(ds.getString(Tags.PatientBirthDate)));
        setPatientSex(filter.getString(ds, Tags.PatientSex));
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
            Method m = PatientBean.class.getMethod("set" 
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
        AttributeFilter filter = AttributeFilter.getPatientAttributeFilter();
        
        if (filter.isOverwrite()) {
            Dataset attrs;
            if (filter.isMerge()) {
                attrs = getAttributes(false);
                appendOtherPatientIds(attrs, ds, null, filter);
                AttrUtils.updateAttributes(attrs, 
                        filter.filter(ds).exclude(OTHER_PID_SQ), null, log);
            } else {
                log.debug("-merge update-strategy not specified.  Not synchronizing other patient ids!");
                attrs = ds;
            }
            setAttributes(attrs);
        } else {
            Dataset attrs = getAttributes(false);
            boolean b = false;
            if (filter.isMerge())
                 b = appendOtherPatientIds(attrs, ds, null, filter);
            else
                log.debug("-merge update-strategy not specified.  Not synchronizing other patient ids!");

            AttrUtils.coerceAttributes(attrs, ds, coercedElements, filter, log);
            if (filter.isMerge()
                    && (AttrUtils.mergeAttributes(attrs, filter.filter(ds), log) || b)) {
                setAttributes(attrs);
            }
        }
    }
    
    /**
     * @ejb.interface-method
     */
    public boolean updateAttributes(Dataset ds) {
        return updateAttributes(ds, null);
        }

    /**
     * @ejb.interface-method
     */
    public boolean updateAttributes(Dataset attrs, Dataset modifiedAttrs) {
        Dataset oldAttrs = getAttributes(false);
        AttributeFilter filter = AttributeFilter.getPatientAttributeFilter();
        Dataset newAttrs = filter.filter(attrs);
        boolean b = appendOtherPatientIds(oldAttrs, newAttrs, modifiedAttrs, filter);
        if( oldAttrs==null ) {
            setAttributes( newAttrs );
        } else { 
            if (!AttrUtils.updateAttributes(oldAttrs,
                    newAttrs.exclude(OTHER_PID_SQ), modifiedAttrs, log) && ! b) 
                 return false;
            setAttributes(oldAttrs);
        }
        return true;    
    }

    private boolean appendOtherPatientIds(Dataset oldAttrs, Dataset newAttrs,
                Dataset modifiedAttrs, AttributeFilter filter) {
        DcmElement nopidsq = newAttrs.get(Tags.OtherPatientIDSeq);
        if (nopidsq == null || nopidsq.isEmpty() || nopidsq.getItem().isEmpty()) {
            return false;
        }
        DcmElement oopidsq = oldAttrs.get(Tags.OtherPatientIDSeq);
        if( oopidsq != null && nopidsq.equals(oopidsq) ) {
            return false;
        }
        if (oopidsq == null) {
            oopidsq = oldAttrs.putSQ(Tags.OtherPatientIDSeq);
        }
        if( modifiedAttrs != null ) {
            DcmElement sq = modifiedAttrs.putSQ(Tags.OtherPatientIDSeq);
            for (int i = 0, n = oopidsq.countItems(); i < n; i++) {
                sq.addItem(oopidsq.getItem(i));
            }
        }
        boolean updated=false;
        for (int i = 0, n = nopidsq.countItems(); i < n; i++) {
            Dataset nItem = nopidsq.getItem(i);
            String nopid = filter.getString(nItem, Tags.PatientID);
            String issuer = filter.getString(nItem, Tags.IssuerOfPatientID);
            Dataset oItem = findOtherPIDByIssuer(issuer,oopidsq);
            if (oItem==null) {
                oopidsq.addItem(nItem);
                getOtherPatientIds().add(opidHome.valueOf(nopid, issuer));
                log.info("Add additional Other Patient ID: "
                        + nopid + "^^^"
                        +  issuer
                        + " to " + prompt());
                updated=true;
            }	
            else {
            	try {
            	    String oopid = filter.getString(oItem, Tags.PatientID);
            	    if( ! oopid.equals(nopid) ) {
            	        oItem.putLO(Tags.PatientID, nItem.getString(Tags.PatientID));
                        OtherPatientIDLocal oopidBean = opidHome.findByPatientIdAndIssuer(oopid, issuer);
                        getOtherPatientIds().remove(oopidBean);
                        if (oopidBean.getPatients().isEmpty()) {
                            oopidBean.remove();
                        }
                        getOtherPatientIds().add(opidHome.valueOf(nopid, issuer));
                        log.info("Other Patient ID of " + oopid + "^^^" + issuer
                              + " is replaced by" + nopid + "^^^" + issuer + " to " + prompt());
                        updated=true;
                    }
            	} catch (Exception onfe) {}
            }	
        }
        return updated;
    }

    /**
     * @ejb.interface-method
     */
    public boolean updateOtherPatientIDs(Dataset ds) {
        AttributeFilter filter = AttributeFilter.getPatientAttributeFilter();
        Dataset attrs = getAttributes(false);
        DcmElement opidsq = attrs.remove(Tags.OtherPatientIDSeq);
        DcmElement nopidsq = ds.get(Tags.OtherPatientIDSeq);
        boolean update = false;
        if (opidsq != null) {
            for (int i = 0, n = opidsq.countItems(); i < n; i++) {
                Dataset opid = opidsq.getItem(i);
                String pid = filter.getString(opid, Tags.PatientID);
                String issuer = filter.getString(opid, Tags.IssuerOfPatientID);
                if (nopidsq == null || !containsPID(pid, issuer, nopidsq, filter)) {
                    try {
                        OtherPatientIDLocal otherPatientId = 
                            opidHome.findByPatientIdAndIssuer(pid, issuer);
                        getOtherPatientIds().remove(otherPatientId);
                        if (otherPatientId.getPatients().isEmpty()) {
                            otherPatientId.remove();
                        }
                    } catch (FinderException e) {
                        throw new EJBException(e);
                    } catch (RemoveException e) {
                        throw new EJBException(e);
                    }
                    update = true;
                    log.info("Remove Other Patient ID: " + pid + "^^^" 
                            +  issuer + " from " + prompt());
                }
            }
        }
        if (nopidsq != null) {
            for (int i = 0, n = nopidsq.countItems(); i < n; i++) {
                Dataset nopid = nopidsq.getItem(i);
                String pid = filter.getString(nopid, Tags.PatientID);
                String issuer = filter.getString(nopid, Tags.IssuerOfPatientID);
                if (opidsq == null || !containsPID(pid, issuer, opidsq, filter)) {
                    getOtherPatientIds().add(opidHome.valueOf(pid, issuer));
                    update = true;
                    log.info("Add additional Other Patient ID: "
                            + pid + "^^^" +  issuer + " to " + prompt());
                }
            }
            if (update) {
                opidsq = attrs.putSQ(Tags.OtherPatientIDSeq);
                for (int i = 0, n = nopidsq.countItems(); i < n; i++) {
                    opidsq.addItem(nopidsq.getItem(i));
                }
            }
        }
        if (update) {
            setAttributes(attrs);
        }
        return update;
    }

    private boolean containsPID(String pid, String issuer, DcmElement opidsq,
            AttributeFilter filter) {
        for (int i = 0, n = opidsq.countItems(); i < n; i++) {
            Dataset opid = opidsq.getItem(i);
            if (filter.getString(opid, Tags.PatientID)
                    .equals(pid)
                && filter.getString(opid, Tags.IssuerOfPatientID)
                    .equals(issuer)) {
                    return true;
            }
        }
        return false;
    }

    private Dataset findOtherPIDByIssuer(String issuer, DcmElement opidsq) {
        for (int i = 0, n = opidsq.countItems(); i < n; i++) {
            Dataset opid = opidsq.getItem(i);
            if (opid == null || opid.isEmpty() || !opid.containsValue(Tags.IssuerOfPatientID))
                continue;
            if (opid.getString(Tags.IssuerOfPatientID).equals(issuer)) {
                    return opid;
            }
        }
        return null;
    }

    private static String normalizeDA(String s) {
        if (s == null) {
            return null;
        }
        String trim = s.trim();
        int l = trim.length();
        if (l == 0) {
            return null;
        }
        if (l == 10 && trim.charAt(4) == '-' && trim.charAt(7) == '-') {
            StringBuilder sb = new StringBuilder(8);
            sb.append(trim.substring(0, 4));
            sb.append(trim.substring(5, 7));
            sb.append(trim.substring(8));
            return sb.toString();
        }
        return trim;
    }

    /**
     * @ejb.interface-method
     */
    public String asString() {
        return prompt();
    }

    private String prompt() {
        return "Patient[pk=" + getPk()
                + ", pid=" + getPatientId()
                + ", issuer=" + getIssuerOfPatientId()
                + ", name=" + getPatientName() + "]";
    }
}