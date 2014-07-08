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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
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
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.net.DcmServiceException;
import org.dcm4chex.archive.common.PatientMatching;
import org.dcm4chex.archive.ejb.interfaces.InstanceLocal;
import org.dcm4chex.archive.ejb.interfaces.InstanceLocalHome;
import org.dcm4chex.archive.ejb.interfaces.MPPSLocalHome;
import org.dcm4chex.archive.ejb.interfaces.MWLItemLocalHome;
import org.dcm4chex.archive.ejb.interfaces.PatientLocal;
import org.dcm4chex.archive.ejb.interfaces.PatientLocalHome;
import org.dcm4chex.archive.ejb.interfaces.SeriesLocal;
import org.dcm4chex.archive.ejb.interfaces.SeriesLocalHome;
import org.dcm4chex.archive.ejb.interfaces.StudyLocal;
import org.dcm4chex.archive.ejb.interfaces.StudyLocalHome;
import org.dcm4chex.archive.exceptions.NonUniquePatientException;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date$
 * @since Aug 6, 2009
 *
 * @ejb.bean name="AttributesModification" type="Stateless" view-type="remote"
 *           jndi-name="ejb/AttributesModification"
 * @ejb.ejb-ref ejb-name="Patient" view-type="local" ref-name="ejb/Patient"
 * @ejb.ejb-ref ejb-name="Study"  view-type="local" ref-name="ejb/Study"
 * @ejb.ejb-ref ejb-name="Series" view-type="local" ref-name="ejb/Series"
 * @ejb.ejb-ref ejb-name="Instance" view-type="local" ref-name="ejb/Instance"
 * @ejb.ejb-ref ejb-name="MPPS" view-type="local" ref-name="ejb/MPPS" 
 * @ejb.ejb-ref ejb-name="MWLItem" view-type="local" ref-name="ejb/MWLItem"
 * 
 * @ejb.transaction-type type="Container"
 * @ejb.transaction type="Required"
 */
public abstract class AttributesModificationBean implements SessionBean {

    private static Logger log = Logger.getLogger(StorageBean.class);

    private PatientLocalHome patHome;
    private StudyLocalHome studyHome;
    private SeriesLocalHome seriesHome;
    private InstanceLocalHome instHome;
    private MPPSLocalHome mppsHome;
    private MWLItemLocalHome mwlHome;

    public void setSessionContext(SessionContext ctx) {
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
            mppsHome = (MPPSLocalHome) jndiCtx
            .lookup("java:comp/env/ejb/MPPS");
            mwlHome = (MWLItemLocalHome) jndiCtx
            .lookup("java:comp/env/ejb/MWLItem");
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
        instHome = null;
    }

    /**
     * @ejb.interface-method
     */
    public boolean modifyAttributes(Dataset attrs, Date time, String system,
            String reason, boolean updateOriginalAttributesSeq,
            int entityNotFoundErrorCode) throws DcmServiceException {
        try {
            String level = attrs.getString(Tags.QueryRetrieveLevel);
            if (level == null)
                throw new IllegalArgumentException(
                        "Missing Query/Retrieve Level");
            if ("IMAGE".equals(level))
                return updateInstanceAttrs(attrs, time, system, reason,
                        updateOriginalAttributesSeq);
            if ("SERIES".equals(level))
                return updateSeriesAttrs(attrs, time, system, reason,
                        updateOriginalAttributesSeq);
            if ("STUDY".equals(level))
                return updateStudyAttrs(attrs, time, system, reason,
                        updateOriginalAttributesSeq);
            throw new IllegalArgumentException(
                    "Illegal Query/Retrieve Level: " + level);
        } catch (IllegalArgumentException e) {
            throw new DcmServiceException(
                    Status.DataSetDoesNotMatchSOPClassError, e.getMessage());
        } catch (ObjectNotFoundException e) {
            if (entityNotFoundErrorCode != 0) {
                throw new DcmServiceException(entityNotFoundErrorCode,
                        "No entity with specified uid found");
            }
            log.info("No entity with specified uid found - ignore update:");
            log.debug(attrs);
            return false;
        } catch (FinderException e) {
            throw new EJBException(e);
        }
    }

    private boolean updateStudyAttrs(Dataset attrs, Date time, String system,
            String reason, boolean updateOriginalAttributesSeq)
            throws FinderException {
        String styiuid = attrs.getString(Tags.StudyInstanceUID);
        if (styiuid ==  null)
            throw new IllegalArgumentException("Missing Study Instance UID");
        StudyLocal study = studyHome.findByStudyIuid(styiuid);
        Dataset origAttrs = updateOriginalAttributesSeq
                ? DcmObjectFactory.getInstance().newDataset()
                : null;
        if (!study.updateAttributes(attrs, origAttrs))
            return false;
        if (updateOriginalAttributesSeq)
            updateOriginalAttributesSeq(study, time, system, reason, origAttrs);
        return true;
    }

    private boolean updateSeriesAttrs(Dataset attrs, Date time, String system,
            String reason, boolean updateOriginalAttributesSeq)
            throws FinderException {
        String seriuid = attrs.getString(Tags.SeriesInstanceUID);
        if (seriuid ==  null)
            throw new IllegalArgumentException("Missing Series Instance UID");
        SeriesLocal series = seriesHome.findBySeriesIuid(seriuid);
        Dataset origAttrs = updateOriginalAttributesSeq
                ? DcmObjectFactory.getInstance().newDataset()
                : null;
        if (!series.updateAttributes(attrs, true, origAttrs))
            return false;
        if (updateOriginalAttributesSeq)
            updateOriginalAttributesSeq(series, time, system, reason, origAttrs);
        return true;
    }

    private boolean updateInstanceAttrs(Dataset attrs, Date time,
            String system, String reason, boolean updateOriginalAttributesSeq)
            throws FinderException {
        String sopiuid = attrs.getString(Tags.SOPInstanceUID);
        if (sopiuid == null)
            throw new IllegalArgumentException("Missing SOP Instance UID");
        InstanceLocal inst = instHome.findBySopIuid(sopiuid);
        Dataset origAttrs = updateOriginalAttributesSeq
                ? DcmObjectFactory.getInstance().newDataset()
                : null;
        if (!inst.updateAttributes(attrs, origAttrs))
            return false;
        if (updateOriginalAttributesSeq)
            updateOriginalAttributesSeq(inst, time, system, reason, origAttrs);
        return true;
    }
    
    /**
     * @throws CreateException 
     * @throws RemoveException 
     * @ejb.interface-method
     */
    public boolean moveStudyToPatient(Dataset attrs, PatientMatching matching, boolean create) throws FinderException, RemoveException, CreateException {
        String[] suids = attrs.getStrings(Tags.StudyInstanceUID);
        if (suids == null || suids.length == 0) {
            throw new IllegalArgumentException("Missing Study Instance UID for moveStudyToPatient");
        }
        StudyLocal study = studyHome.findByStudyIuid(suids[0]);
        PatientLocal pat = this.getPatient(attrs, matching, create);
        if (pat != null && !pat.isIdentical(study.getPatient())) {
            for (int i = 0 ; i < suids.length ; i++) {
                pat.getMpps().addAll(mppsHome.findByStudyIuid(suids[i]));
                pat.getMwlItems().addAll(mwlHome.findByStudyIuid(suids[i]));
                pat.getStudies().add(i==0 ? study : studyHome.findByStudyIuid(suids[i]));
            }
            return true;
        }
        return false;
    }

    private void updateOriginalAttributesSeq(StudyLocal study,
            Date time, String system, String reason, Dataset origAttrs) {
        for (Iterator iter = study.getSeries().iterator(); iter.hasNext();)
            updateOriginalAttributesSeq((SeriesLocal) iter.next(), 
                    time, system, reason, origAttrs);
    }

    private void updateOriginalAttributesSeq(SeriesLocal series,
            Date time, String system, String reason, Dataset origAttrs) {
        for (Iterator iter = series.getInstances().iterator(); iter.hasNext();)
            updateOriginalAttributesSeq((InstanceLocal) iter.next(),
                    time, system, reason, origAttrs);
    }

    private void updateOriginalAttributesSeq(InstanceLocal inst,
            Date time, String system, String reason, Dataset origAttrs) {
        Dataset attrs = inst.getAttributes(false);
        DcmElement origAttrsSeq = attrs.get(Tags.OriginalAttributesSeq);
        if (origAttrsSeq == null)
            origAttrsSeq = attrs.putSQ(Tags.OriginalAttributesSeq);
        Dataset origAttrsItem = origAttrsSeq.addNewItem();
        origAttrsItem.putLO(Tags.SourceOfPreviousValues);
        origAttrsItem.putDT(Tags.AttributeModificationDatetime, time);
        origAttrsItem.putLO(Tags.ModifyingSystem, system);
        origAttrsItem.putCS(Tags.ReasonForTheAttributeModification, reason);
        origAttrsItem.putSQ(Tags.ModifiedAttributesSeq).addItem(origAttrs);
        inst.setAttributes(attrs);
    }
    
    
    private PatientLocal getPatient(Dataset attrs, PatientMatching matching, boolean create) 
            throws FinderException, NonUniquePatientException, RemoveException, CreateException {
        PatientLocal pat;
        try {
            return patHome.selectPatient(attrs, matching, true);
        } catch (ObjectNotFoundException onfe) {
            if (create) {
                try {
                    pat = patHome.create(attrs);
                    // Check if patient record was also inserted by concurrent thread
                    try {
                        return patHome.selectPatient(attrs, matching, true);
                    } catch (NonUniquePatientException nupe) {
                        pat.remove();
                        pat = patHome.selectPatient(attrs, matching, true);
                    } catch (ObjectNotFoundException onfe2) {
                        // Just inserted Patient not found because of missing value
                        // of attribute configured as required for Patient Matching
                        return pat;
                    }
                 } catch (CreateException ce) {
                    // Check if patient record was inserted by concurrent thread
                    // with unique index on (pat_id, pat_id_issuer)
                     try {
                         pat = patHome.selectPatient(attrs, matching, true);
                     } catch (ObjectNotFoundException onfe2) {
                         throw ce;
                     }
                }
            } else {
                throw onfe;
            }
        } catch (NonUniquePatientException nupe) {
            if (create)
                return patHome.create(attrs);
            else
                throw nupe;
        }
        return pat;
    }

}
