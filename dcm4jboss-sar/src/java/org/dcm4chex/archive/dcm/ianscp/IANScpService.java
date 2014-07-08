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
package org.dcm4chex.archive.dcm.ianscp;

import java.io.IOException;

import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.dict.Status;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.net.AcceptorPolicy;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.DcmService;
import org.dcm4che.net.DcmServiceBase;
import org.dcm4che.net.DcmServiceException;
import org.dcm4che.net.DcmServiceRegistry;
import org.dcm4che.net.Dimse;
import org.dcm4chex.archive.common.Availability;
import org.dcm4chex.archive.dcm.AbstractScpService;
import org.dcm4chex.archive.ejb.interfaces.AvailabilityUpdate;
import org.dcm4chex.archive.ejb.interfaces.AvailabilityUpdateHome;
import org.dcm4chex.archive.util.EJBHomeFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date$
 * @since Feb 24, 2009
 */
public class IANScpService extends AbstractScpService {

    private final DcmService ianScp = new DcmServiceBase() {

        @Override
        protected Dataset doNCreate(ActiveAssociation assoc, Dimse rq,
                Command rspCmd) throws IOException, DcmServiceException {
            process(rq.getDataset());
            return null;
        }
        
    };

    protected void process(Dataset ian) throws DcmServiceException {
        log.debug("Received IAN:\n");
        log.debug(ian);
        validate(ian);
        try {
            int n = getAvailabilityUpdate().updateAvailability(ian);
            if (n > 0) {
                log.info("Updated Availability of " + n
                        + " Instances of Study[uid="
                        + ian.getString(Tags.StudyInstanceUID) + "].");
            }
        } catch (Exception e) {
            throw new DcmServiceException(Status.ProcessingFailure, e);
        }
    }

    private AvailabilityUpdate getAvailabilityUpdate() throws Exception {
        return ((AvailabilityUpdateHome) EJBHomeFactory.getFactory()
                .lookup(AvailabilityUpdateHome.class,
                        AvailabilityUpdateHome.JNDI_NAME))
                .create();
    }

    private void validate(Dataset ian) throws DcmServiceException {
        assertType1(ian, Tags.StudyInstanceUID);
        assertType1(ian, Tags.RefSeriesSeq);
        DcmElement refSeriesSq = ian.get(Tags.RefSeriesSeq);
        for (int i = 0, nS = refSeriesSq.countItems(); i < nS; i++) {
            Dataset refSeries = refSeriesSq.getItem(i);
            assertType1(refSeries, Tags.SeriesInstanceUID);
            assertType1(refSeries, Tags.RefSOPSeq);
            DcmElement refSOPSq = refSeries.get(Tags.RefSOPSeq);
            for (int j = 0, nI = refSOPSq.countItems(); j < nI; j++) {
                Dataset refSop = refSOPSq.getItem(j);
                assertType1(refSop, Tags.RefSOPClassUID);
                assertType1(refSop, Tags.RefSOPInstanceUID);
                assertType1(refSop, Tags.InstanceAvailability);
                assertType1(refSop, Tags.RetrieveAET);
                validateAvailability(
                        refSop.getString(Tags.InstanceAvailability));
            }
        }
    }

    private void validateAvailability(String value)
            throws DcmServiceException {
        try {
            Availability.toInt(value);
        } catch (IllegalArgumentException e) {
            throw new DcmServiceException(Status.AttributeValueOutOfRange,
                    "Illegal value for Instance Availability: " + value);
        }
        
    }

    private void assertType1(Dataset ian, int tag) throws DcmServiceException {
        if (!ian.containsValue(tag)) {
            throw new DcmServiceException(Status.MissingAttributeValue,
                    "Missing attribute (value): " + Tags.toString(tag));
        }
    }

    @Override
    protected void bindDcmServices(DcmServiceRegistry services) {
        services.bind(UIDs.InstanceAvailabilityNotificationSOPClass, ianScp );
    }

    @Override
    protected void unbindDcmServices(DcmServiceRegistry services) {
        services.unbind(UIDs.InstanceAvailabilityNotificationSOPClass);
        
    }

    @Override
    protected void enablePresContexts(AcceptorPolicy policy) {
        policy.putPresContext(UIDs.InstanceAvailabilityNotificationSOPClass,
                valuesToStringArray(tsuidMap));
    }

    @Override
    protected void disablePresContexts(AcceptorPolicy policy) {
        policy.putPresContext(UIDs.InstanceAvailabilityNotificationSOPClass,
                null);
    }

}
