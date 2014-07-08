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
 * Portions created by the Initial Developer are Copyright (C) 2010
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below.
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
import java.util.Collection;

import javax.ejb.EJBException;
import javax.ejb.FinderException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.data.PersonName;
import org.dcm4chex.archive.ejb.conf.AttributeFilter;
import org.dcm4chex.archive.ejb.interfaces.GPSPSPerformerLocal;
import org.dcm4chex.archive.ejb.interfaces.GPSPSPerformerLocalHome;
import org.dcm4chex.archive.ejb.interfaces.MWLItemLocal;
import org.dcm4chex.archive.ejb.interfaces.MWLItemLocalHome;
import org.dcm4chex.archive.ejb.interfaces.PatientLocal;
import org.dcm4chex.archive.ejb.interfaces.PatientLocalHome;
import org.dcm4chex.archive.ejb.interfaces.SeriesLocal;
import org.dcm4chex.archive.ejb.interfaces.SeriesLocalHome;
import org.dcm4chex.archive.ejb.interfaces.SeriesRequestLocal;
import org.dcm4chex.archive.ejb.interfaces.SeriesRequestLocalHome;
import org.dcm4chex.archive.ejb.interfaces.SoundexUpdateLocal;
import org.dcm4chex.archive.ejb.interfaces.StudyLocal;
import org.dcm4chex.archive.ejb.interfaces.StudyLocalHome;
import org.dcm4chex.archive.ejb.interfaces.VerifyingObserverLocal;
import org.dcm4chex.archive.ejb.interfaces.VerifyingObserverLocalHome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date:: xxxx-xx-xx $
 * @since Nov 22, 2010
 *
 * @ejb.bean name="SoundexUpdate" type="Stateless" view-type="both"
 *           jndi-name="ejb/SoundexUpdate"
 * 
 * @ejb.transaction-type type="Container"
 * 
 * @ejb.transaction type="Required"
 * 
 * @ejb.ejb-ref ejb-name="Patient" view-type="local" ref-name="ejb/Patient"
 * @ejb.ejb-ref ejb-name="Study" view-type="local" ref-name="ejb/Study"
 * @ejb.ejb-ref ejb-name="Series" view-type="local" ref-name="ejb/Series"
 * @ejb.ejb-ref ejb-name="SeriesRequest" view-type="local" ref-name="ejb/SeriesRequest"
 * @ejb.ejb-ref ejb-name="VerifyingObserver" view-type="local" ref-name="ejb/VerifyingObserver"
 * @ejb.ejb-ref ejb-name="MWLItem" view-type="local" ref-name="ejb/MWLItem"
 * @ejb.ejb-ref ejb-name="GPSPSPerformer" view-type="local" ref-name="ejb/GPSPSPerformer"
 */
public abstract class SoundexUpdateBean implements SessionBean {

    private static final Logger LOG =
            LoggerFactory.getLogger(SoundexUpdateBean.class);

    private SessionContext ctx;
    private PatientLocalHome patHome;
    private StudyLocalHome studyHome;
    private SeriesLocalHome seriesHome;
    private SeriesRequestLocalHome seriesRequestHome;
    private VerifyingObserverLocalHome observerHome;
    private MWLItemLocalHome mwlItemHome;
    private GPSPSPerformerLocalHome gpspsPerformerHome;

    public void setSessionContext(SessionContext ctx) throws EJBException,
            RemoteException {
        this.ctx = ctx;
        Context jndiCtx = null;
        try {
            jndiCtx = new InitialContext();
            patHome = (PatientLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/Patient");
            studyHome = (StudyLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/Study");
            seriesHome = (SeriesLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/Series");
            seriesRequestHome = (SeriesRequestLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/SeriesRequest");
            observerHome = (VerifyingObserverLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/VerifyingObserver");
            mwlItemHome = (MWLItemLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/MWLItem");
            gpspsPerformerHome = (GPSPSPerformerLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/GPSPSPerformer");
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
        patHome = null;
        studyHome = null;
        seriesHome = null;
        seriesRequestHome = null;
        observerHome = null;
        mwlItemHome = null;
        gpspsPerformerHome = null;
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="Never"
     */
    public int updatePatientNameSoundex() {
        if (!AttributeFilter.isSoundexEnabled())
            return 0;

        try {
            Collection<PatientLocal> c = patHome.findAll();
            LOG.info("Start updating Patient Name Soundex codes");
            SoundexUpdateLocal ejb =
                    (SoundexUpdateLocal) ctx.getEJBLocalObject();
            for (PatientLocal pat : c) {
                ejb.updatePatientNameSoundex(pat);
            }
            LOG.info("Finished updating Patient Name Soundex codes");
            return c.size();
        } catch (FinderException e) {
            throw new EJBException(e);
        }
    }

    /**
     * @ejb.interface-method view-type="local"
     */
    public void updatePatientNameSoundex(PatientLocal pat) {
        PersonName pn = newPersonName(pat.getPatientName());
        pat.setPatientFamilyNameSoundex(
                AttributeFilter.toSoundex(pn, PersonName.FAMILY, "*"));
        pat.setPatientGivenNameSoundex(
                AttributeFilter.toSoundex(pn, PersonName.GIVEN, "*"));
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="Never"
     */
    public int updateReferringPhysicianNameSoundex() {
        if (!AttributeFilter.isSoundexEnabled())
            return 0;

        try {
            Collection<StudyLocal> c = studyHome.findAll();
            LOG.info("Start updating Referring Physician Name Soundex codes");
            SoundexUpdateLocal ejb =
                    (SoundexUpdateLocal) ctx.getEJBLocalObject();
            for (StudyLocal study : c) {
                ejb.updateReferringPhysicianNameSoundex(study);
            }
            LOG.info("Finished updating Referring Physician Name Soundex codes");
            return c.size();
        } catch (FinderException e) {
            throw new EJBException(e);
        }
    }

    /**
     * @ejb.interface-method view-type="local"
     */
    public void updateReferringPhysicianNameSoundex(StudyLocal study) {
        PersonName pn = newPersonName(study.getReferringPhysicianName());
        study.setReferringPhysicianFamilyNameSoundex(
                AttributeFilter.toSoundex(pn, PersonName.FAMILY, "*"));
        study.setReferringPhysicianGivenNameSoundex(
                AttributeFilter.toSoundex(pn, PersonName.GIVEN, "*"));
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="Never"
     */
    public int updatePerformingPhysicianNameSoundex() {
        if (!AttributeFilter.isSoundexEnabled())
            return 0;

        try {
            Collection<SeriesLocal> c = seriesHome.findAll();
            LOG.info("Start updating Performing Physician Name Soundex codes");
            SoundexUpdateLocal ejb =
                    (SoundexUpdateLocal) ctx.getEJBLocalObject();
            for (SeriesLocal series : c) {
                ejb.updatePerformingPhysicianNameSoundex(series);
            }
            LOG.info("Finished updating Performing Physician Name Soundex codes");
            return c.size();
        } catch (FinderException e) {
            throw new EJBException(e);
        }
    }

    /**
     * @ejb.interface-method view-type="local"
     */
    public void updatePerformingPhysicianNameSoundex(SeriesLocal series) {
        PersonName pn = newPersonName(series.getPerformingPhysicianName());
        series.setPerformingPhysicianFamilyNameSoundex(
                AttributeFilter.toSoundex(pn, PersonName.FAMILY, "*"));
        series.setPerformingPhysicianGivenNameSoundex(
                AttributeFilter.toSoundex(pn, PersonName.GIVEN, "*"));
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="Never"
     */
    public int updateRequestingPhysicianNameSoundex() {
        if (!AttributeFilter.isSoundexEnabled())
            return 0;

        try {
            Collection<SeriesRequestLocal> c = seriesRequestHome.findAll();
            LOG.info("Start updating Requesting Physician Name Soundex codes");
            SoundexUpdateLocal ejb =
                    (SoundexUpdateLocal) ctx.getEJBLocalObject();
            for (SeriesRequestLocal seriesRequest : c) {
                ejb.updateRequestingPhysicianNameSoundex(seriesRequest);
            }
            LOG.info("Finished updating Requesting Physician Name Soundex codes");
            return c.size();
        } catch (FinderException e) {
            throw new EJBException(e);
        }
    }

    /**
     * @ejb.interface-method view-type="local"
     */
    public void updateRequestingPhysicianNameSoundex(SeriesRequestLocal seriesRequest) {
        PersonName pn = newPersonName(seriesRequest.getRequestingPhysician());
        seriesRequest.setRequestingPhysicianFamilyNameSoundex(
                AttributeFilter.toSoundex(pn, PersonName.FAMILY, "*"));
        seriesRequest.setRequestingPhysicianGivenNameSoundex(
                AttributeFilter.toSoundex(pn, PersonName.GIVEN, "*"));
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="Never"
     */
    public int updateVerifyingObserverNameSoundex() {
        if (!AttributeFilter.isSoundexEnabled())
            return 0;

        try {
            Collection<VerifyingObserverLocal> c = observerHome.findAll();
            LOG.info("Start updating Verifying Observer Name Soundex codes");
            SoundexUpdateLocal ejb =
                    (SoundexUpdateLocal) ctx.getEJBLocalObject();
            for (VerifyingObserverLocal observer : c) {
                ejb.updateVerifyingObserverNameSoundex(observer);
            }
            LOG.info("Finished updating Verifying Observer Name Soundex codes");
            return c.size();
        } catch (FinderException e) {
            throw new EJBException(e);
        }
    }

    /**
     * @ejb.interface-method view-type="local"
     */
    public void updateVerifyingObserverNameSoundex(VerifyingObserverLocal observer) {
        PersonName pn = newPersonName(observer.getVerifyingObserverName());
        observer.setVerifyingObserverFamilyNameSoundex(
                AttributeFilter.toSoundex(pn, PersonName.FAMILY, "*"));
        observer.setVerifyingObserverGivenNameSoundex(
                AttributeFilter.toSoundex(pn, PersonName.GIVEN, "*"));
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="Never"
     */
    public int updateMWLPerformingPhysicianNameSoundex() {
        if (!AttributeFilter.isSoundexEnabled())
            return 0;

        try {
            Collection<MWLItemLocal> c = mwlItemHome.findAll();
            LOG.info("Start updating MWL Performing Physician Name Soundex codes");
            SoundexUpdateLocal ejb =
                    (SoundexUpdateLocal) ctx.getEJBLocalObject();
            for (MWLItemLocal mwlitem : c) {
                ejb.updateMWLPerformingPhysicianNameSoundex(mwlitem);
            }
            LOG.info("Finished updating MWL Performing Physician Name Soundex codes");
            return c.size();
        } catch (FinderException e) {
            throw new EJBException(e);
        }
    }

    /**
     * @ejb.interface-method view-type="local"
     */
    public void updateMWLPerformingPhysicianNameSoundex(MWLItemLocal mwlitem) {
        PersonName pn = newPersonName(mwlitem.getPerformingPhysicianName());
        mwlitem.setPerformingPhysicianFamilyNameSoundex(
                AttributeFilter.toSoundex(pn, PersonName.FAMILY, "*"));
        mwlitem.setPerformingPhysicianGivenNameSoundex(
                AttributeFilter.toSoundex(pn, PersonName.GIVEN, "*"));
    }

    /**
     * @ejb.interface-method
     * @ejb.transaction type="Never"
     */
    public int updateGPSPSHumanPerformerNameSoundex() {
        if (!AttributeFilter.isSoundexEnabled())
            return 0;

        try {
            Collection<GPSPSPerformerLocal> c = gpspsPerformerHome.findAll();
            LOG.info("Start updating GPSPS Human Performer Name Soundex codes");
            SoundexUpdateLocal ejb =
                    (SoundexUpdateLocal) ctx.getEJBLocalObject();
            for (GPSPSPerformerLocal performer : c) {
                ejb.updateGPSPSPerformerNameSoundex(performer);
            }
            LOG.info("Finished updating GPSPS Human Performer Name Soundex codes");
            return c.size();
        } catch (FinderException e) {
            throw new EJBException(e);
        }
    }

    /**
     * @ejb.interface-method view-type="local"
     */
    public void updateGPSPSPerformerNameSoundex(GPSPSPerformerLocal performer) {
        PersonName pn = newPersonName(performer.getHumanPerformerName());
        performer.setHumanPerformerFamilyNameSoundex(
                AttributeFilter.toSoundex(pn, PersonName.FAMILY, "*"));
        performer.setHumanPerformerGivenNameSoundex(
                AttributeFilter.toSoundex(pn, PersonName.GIVEN, "*"));
    }

    private PersonName newPersonName(String pname) {
        try {
            return DcmObjectFactory.getInstance().newPersonName(pname);
        } catch (IllegalArgumentException e) {
            LOG.warn("Cannot generate Soundex code for Illegal Person Name: "
                    + pname + " - treat as unknown.");
            return null;
        }
    }

}