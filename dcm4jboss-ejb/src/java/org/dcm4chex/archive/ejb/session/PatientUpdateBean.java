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
import java.util.Collection;
import java.util.Iterator;

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
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Tags;
import org.dcm4chex.archive.common.PatientMatching;
import org.dcm4chex.archive.common.SPSStatus;
import org.dcm4chex.archive.ejb.entity.AttrUtils;
import org.dcm4chex.archive.ejb.interfaces.PatientUpdateLocal;
import org.dcm4chex.archive.ejb.interfaces.SeriesLocal;
import org.dcm4chex.archive.ejb.interfaces.MWLItemLocal;
import org.dcm4chex.archive.ejb.interfaces.PatientLocal;
import org.dcm4chex.archive.ejb.interfaces.PatientLocalHome;
import org.dcm4chex.archive.ejb.interfaces.StudyLocal;
import org.dcm4chex.archive.exceptions.NonUniquePatientException;
import org.dcm4chex.archive.exceptions.PatientAlreadyExistsException;

/**
 * 
 * @author gunter.zeilinger@tiani.com
 * @version $Revision: 17964 $ $Date: 2013-08-02 06:32:15 +0000 (Fri, 02 Aug 2013) $
 * @since 14.01.2004
 * 
 * @ejb.bean
 *  name="PatientUpdate"
 *  type="Stateless"
 *  view-type="both"
 *  jndi-name="ejb/PatientUpdate"
 * 
 * @ejb.transaction-type 
 *  type="Container"
 * 
 * @ejb.transaction 
 *  type="Required"
 * 
 * @ejb.ejb-ref
 *  ejb-name="Patient" 
 *  view-type="local"
 *  ref-name="ejb/Patient" 
 */
public abstract class PatientUpdateBean implements SessionBean {

    private static final Logger LOG = Logger.getLogger(PatientUpdateBean.class);

    private SessionContext sessionCtx;
    private PatientLocalHome patHome;

    public void setSessionContext(SessionContext ctx) throws EJBException,
            RemoteException {
        sessionCtx = ctx;
        Context jndiCtx = null;
        try {
            jndiCtx = new InitialContext();
            patHome = (PatientLocalHome) jndiCtx
                    .lookup("java:comp/env/ejb/Patient");
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
    }

    /**
     * @throws RemoveException 
     * @throws EJBException 
     * @ejb.interface-method
     */
    public void changePatientIdentifierList(Dataset correct, Dataset prior,
            PatientMatching matching, boolean keepPrior) throws CreateException, FinderException,
                    PatientAlreadyExistsException, EJBException, RemoveException {
        LOG.info("Change PID for " + correct.getString(Tags.PatientName)
                + " from " + toPidAndIssuer(prior)
                + " to " + toPidAndIssuer(correct));
        try {
            patHome.selectPatient(correct, matching, false);
            String prompt = "Patient with PID "
                + toPidAndIssuer(correct) + " already exists";
            LOG.warn(prompt);
            throw new PatientAlreadyExistsException(prompt);
        } catch (ObjectNotFoundException e) {}
        try {
            PatientLocal priorPat = patHome.selectPatient(prior, matching, true);
            Dataset newAttrs = priorPat.getAttributes(true);
            newAttrs.putLO(Tags.PatientID, correct.getString(Tags.PatientID));
            newAttrs.putLO(Tags.IssuerOfPatientID, correct.getString(Tags.IssuerOfPatientID));
            if (keepPrior) {
                PatientLocal correctPat = patHome.create(newAttrs);
                merge(correctPat, priorPat, keepPrior);
            } else {
                priorPat.setAttributes(newAttrs);
            }
        } catch (ObjectNotFoundException x) {
            LOG.warn("Prior patient of ChangePatientIdentifierList request not found! Ignored!");
        }
    }

    /**
     * @throws RemoveException 
     * @throws EJBException 
     * @ejb.interface-method
     */
    public void mergePatient(Dataset dominant, Dataset prior,
            PatientMatching matching, boolean keepPrior) throws CreateException, FinderException, EJBException, RemoveException {
        LOG.info("Merge " + prior.getString(Tags.PatientName)
                + " with PID " + toPidAndIssuer(prior)
                + " to " + dominant.getString(Tags.PatientName)
                + " with PID " + toPidAndIssuer(dominant));
        PatientLocal dominantPat = updateOrCreate(dominant, matching);
        PatientLocal priorPat= updateOrCreate(prior, matching);
        LOG.debug("Merge patient.pk "+priorPat.getPk()+" to patient.pk "+dominantPat.getPk());
        merge(dominantPat, priorPat, keepPrior);
    }

    private void merge(PatientLocal dominantPat, PatientLocal priorPat, boolean keepPrior) throws EJBException, RemoveException {
        if (dominantPat.equals(priorPat)) {
            throw new IllegalArgumentException("Prior and dominant patients are the same! Can't merge patient to itself!");
        }
        dominantPat.getStudies().addAll(priorPat.getStudies());
        dominantPat.getMpps().addAll(priorPat.getMpps());
        dominantPat.getMwlItems().addAll(priorPat.getMwlItems());
        dominantPat.getGsps().addAll(priorPat.getGsps());
        dominantPat.getGppps().addAll(priorPat.getGppps());
        dominantPat.getUPS().addAll(priorPat.getUPS());
        dominantPat.getMerged().addAll(priorPat.getMerged());
        if (keepPrior) {
            priorPat.setMergedWith(dominantPat);
        } else {
            priorPat.remove();  
        }
    }
    
    /**
     * 
     * @ejb.interface-method
     */
    public void updatePatient(StudyLocal study, Dataset attrs, 
        PatientMatching matching ) throws FinderException, CreateException {
        updatePatient(study, attrs, null, matching);    	
    }

    /**
     * Update patient data as well as relink study with the patient if the patient
     * is different than original one.
     * @throws CreateException 
     * 
     * @ejb.interface-method
     */
    public void updatePatient(StudyLocal study, Dataset attrs, Dataset modified,
            PatientMatching matching) throws FinderException, CreateException {
		String pid = attrs.getString(Tags.PatientID);

		// If the patient id is not included, then we don't have to do any
		// patient update. Although patient id is type 2 in DICOM, but for DC,
		// we enforce this.
		if (pid == null || pid.length() == 0)
			return;
		
		PatientLocal newPatient = updateOrCreate(attrs, modified, matching);
		PatientLocal oldPat = study.getPatient();
		// Case 1: it's matching the same patient. Do nothing
		if(oldPat.getPk() == newPatient.getPk())
			return;
		if(modified != null) {
			modified.putAll( attrs );
		}
		// Case 2: there's no matching, a new patient is created. The study is updated.
		// Case 3: it's matching another existing patient. The study is updated.
		study.setPatient(newPatient);
		SeriesLocal series;
		for ( Iterator it = study.getSeries().iterator() ; it.hasNext() ; ) {
		    series = (SeriesLocal) it.next();
		    if (series.getMpps() != null) {
		        series.getMpps().setPatient(newPatient);
		    }
		}
    }

    /**
     * @ejb.interface-method
     */
    public void updatePatient(Dataset attrs, PatientMatching matching)
            throws CreateException, FinderException {
        updatePatient(attrs, null, matching);
    }

    /**
     * @ejb.interface-method
     */
    public void updatePatient(Dataset attrs, Dataset modified, PatientMatching matching)
            throws CreateException, FinderException {
        LOG.info("Update " + attrs.getString(Tags.PatientName)
                + " with PID " + toPidAndIssuer(attrs));
        updateOrCreate(attrs, modified, matching);
    }

    /**
     * @ejb.interface-method view-type="local"
     */
    public PatientLocal updateOrCreate(Dataset ds, PatientMatching matching)
            throws CreateException, FinderException {
       return updateOrCreate(ds, null, matching);
    }

   private PatientLocal updateOrCreate(Dataset ds, Dataset modified, PatientMatching matching)
            throws CreateException, FinderException {
       try {
            return findAndUpdatePatient(ds, modified, matching);
       } catch (ObjectNotFoundException e) {
            PatientLocal pat;
            try {
                pat = ((PatientUpdateLocal) sessionCtx.getEJBLocalObject()).createPatient(ds);
            } catch (CreateException ce) {
                // Check if patient record was inserted by concurrent thread
                // with unique index on (pat_id, pat_id_issuer)
                try {
                    return findAndUpdatePatient(ds, modified, matching);
                } catch (FinderException fe) {
                    throw ce;
                }
            }
            // Check if patient record was also inserted by concurrent thread
            // with non-unique index on (pat_id, pat_id_issuer)
            try {
                patHome.selectPatient(ds, matching, false);
                if(modified!=null) {
                     modified.putAll(ds);	
                }	
                return pat;
            } catch (NonUniquePatientException nupe) {
                try {
                    pat.remove();
                } catch (RemoveException e1) {
                    throw new EJBException(e);
                }
                return findAndUpdatePatient(ds, modified, matching);
            }
        }
    }

    private PatientLocal findAndUpdatePatient(Dataset ds, Dataset modified,
            PatientMatching matching) throws FinderException {
    	Dataset origModAttrs=null;
    	if( modified != null ) {
    	   origModAttrs = DcmObjectFactory.getInstance().newDataset(); 	
        }
        PatientLocal pat = patHome.selectPatient(ds, matching, false);
        if( pat.updateAttributes(ds, origModAttrs) ) {
            AttrUtils.fetchModifiedAttributes(ds, origModAttrs, modified);
        }
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
     */
    public boolean deletePatient(Dataset ds, PatientMatching matching)
            throws RemoveException, FinderException {
        LOG.info("Delete " + ds.getString(Tags.PatientName)
                + " with PID " + toPidAndIssuer(ds));
        try {
            patHome.selectPatient(ds, matching, false).remove();
            return true;
        } catch (ObjectNotFoundException e) {
            return false;
        }
    }

    /**
     * @ejb.interface-method
     */
    public void patientArrived(Dataset ds, PatientMatching matching)
            throws FinderException {
        LOG.info("Change status of SPS for " + ds.getString(Tags.PatientName)
                + " with PID " + toPidAndIssuer(ds) + " to ARRIVED");
        try {
            PatientLocal pat = patHome.selectPatient(ds, matching, false);
            Collection c = pat.getMwlItems();
            for (Iterator iter = c.iterator(); iter.hasNext();) {
                MWLItemLocal mwlitem = (MWLItemLocal) iter.next();
                if ( mwlitem.getSpsStatusAsInt() == SPSStatus.SCHEDULED )
                    mwlitem.updateSpsStatus(SPSStatus.ARRIVED);
            }
        } catch (ObjectNotFoundException e) {
        }
    }
    
    /**
     * @param matching 
     * @ejb.interface-method
     */
    public void updateOtherPatientIDsOrCreate(Dataset ds,
            PatientMatching matching) throws FinderException, CreateException {
        try {
            patHome.selectPatient(ds, matching, false)
                    .updateOtherPatientIDs(ds);
        } catch (ObjectNotFoundException e) {
            patHome.create(ds);
        }
    }
    
    private String toPidAndIssuer(Dataset ds) {
        return ds.getString(Tags.PatientID)+"^^^"+ds.getString(Tags.IssuerOfPatientID, "");
    }
}