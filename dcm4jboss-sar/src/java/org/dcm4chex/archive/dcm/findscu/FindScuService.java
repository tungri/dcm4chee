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

package org.dcm4chex.archive.dcm.findscu;

import java.util.List;

import org.dcm4che.data.Command;
import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Tags;
import org.dcm4che.dict.UIDs;
import org.dcm4che.net.ActiveAssociation;
import org.dcm4che.net.AssociationFactory;
import org.dcm4che.net.DcmServiceException;
import org.dcm4che.net.Dimse;
import org.dcm4che.net.FutureRSP;
import org.dcm4chex.archive.config.DicomPriority;
import org.dcm4chex.archive.dcm.AbstractScuService;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date:: xxxx-xx-xx $
 * @since Mar 4, 2010
 */
public class FindScuService extends AbstractScuService {

    private int priority = 0;

    public final String getPriority() {
        return DicomPriority.toString(priority);
    }

    public final void setPriority(String priority) {
        this.priority = DicomPriority.toCode(priority.trim());
    }

    public String availabilityOfStudy(String aet, String uid)
            throws Exception {
        Dataset rsp = findStudy(aet, uid);
        return rsp != null ? rsp.getString(Tags.InstanceAvailability)
                : "UNAVAILABLE";
    }

    @SuppressWarnings("unchecked")
    public Dataset findStudy(String aet, String uid) throws Exception {
        ActiveAssociation aa = openAssociation(aet,
                UIDs.StudyRootQueryRetrieveInformationModelFIND);
        try {
            AssociationFactory af = AssociationFactory.getInstance();
            DcmObjectFactory dof = DcmObjectFactory.getInstance();
            Command cmd = dof.newCommand();
            cmd.initCFindRQ(1,
                    UIDs.StudyRootQueryRetrieveInformationModelFIND, priority);
            Dataset ds = dof.newDataset();
            ds.putCS(Tags.QueryRetrieveLevel, "STUDY");
            ds.putUI(Tags.StudyInstanceUID, uid);
            FutureRSP futureRSP = aa.invoke(af.newDimse(1, cmd, ds));
            int status = futureRSP.get().getCommand().getStatus();
            List<Dimse> pending = futureRSP.listPending();
            if (status != 0)
                throw new DcmServiceException(status);
            return !pending.isEmpty() ? pending.get(0).getDataset() : null;
        } finally {
            aa.release(false);
        }
    }
}
