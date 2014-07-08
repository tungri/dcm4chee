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
package org.dcm4chex.archive.dcm.modify;

import java.io.IOException;
import java.util.Date;

import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.DcmServiceBase;
import org.dcm4che.net.DcmServiceException;
import org.dcm4che.net.Dimse;
import org.dcm4chex.archive.common.PrivateTags;
import org.dcm4chex.archive.ejb.interfaces.AttributesModification;
import org.dcm4chex.archive.ejb.interfaces.AttributesModificationHome;
import org.dcm4chex.archive.util.EJBHomeFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date$
 * @since Aug 6, 2009
 */
public class AttributesModificationScp extends DcmServiceBase {

    private final AttributesModificationScpService service;

    public AttributesModificationScp(AttributesModificationScpService service) {
        this.service = service;
    }

    @Override
    protected void doCStore(ActiveAssociation assoc, Dimse rq, Command rspCmd)
            throws IOException, DcmServiceException {
        try {
            Dataset ds = rq.getDataset();
            String callingAET = assoc.getAssociation().getCallingAET();
            ds.setPrivateCreatorID(PrivateTags.CreatorID);
            ds.putAE(PrivateTags.CallingAET, callingAET);
            ds.setPrivateCreatorID(null);
            String modifyingSystem =
                    ds.getString(Tags.ModifyingSystem, callingAET);
            String reason = 
                    ds.getString(Tags.ReasonForTheAttributeModification, "COERCE");
            if ("PATIENT".equals(ds.getString(Tags.QueryRetrieveLevel))) {
                if (getAttributesModification().moveStudyToPatient(ds, 
                        service.patientMatching(), service.isCreatePatientOnMoveStudy()))
                    service.sendAttributesModificationNotification(ds);
            } else if (getAttributesModification().modifyAttributes(
                    ds, new Date(), modifyingSystem, reason,
                    service.isUpdateOriginalAttributesSeq(),
                    service.getEntityNotFoundErrorCode()))
                service.sendAttributesModificationNotification(ds);
        } catch (DcmServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new DcmServiceException(Status.ProcessingFailure, e);
        }
    }

    private AttributesModification getAttributesModification() throws Exception {
        return ((AttributesModificationHome) EJBHomeFactory.getFactory().lookup(
                AttributesModificationHome.class, AttributesModificationHome.JNDI_NAME)).create();
    }

}