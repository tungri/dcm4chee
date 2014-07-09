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

package org.dcm4chex.archive.dcm.ianscu;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.dict.Tags;
import org.dcm4chex.archive.common.BaseJmsOrder;
import org.dcm4chex.archive.common.JmsOrderProperties;

/**
 * @author gunter.zeilinger@tiani.com
 * @version $Revision: 12253 $ $Date: 2005-10-06 21:55:27 +0200 (Thu, 06 Oct
 *          2005) $
 * @since 28.08.2004
 * 
 */
public class IANOrder extends BaseJmsOrder implements Serializable {

    private static final long serialVersionUID = -9036249052896177876L;

    private final String dest;

    private final String patname;

    private final String patid;

    private final String studyid;

    private final Dataset ian;

    public IANOrder(String dest, String patid, String patname, String studyid,
            Dataset ian) {
        if (dest == null)
            throw new NullPointerException();
        if (ian == null)
            throw new NullPointerException();
        this.dest = dest;
        this.patid = patid;
        this.patname = patname;
        this.studyid = studyid;
        this.ian = ian;
    }

    public final String getDestination() {
        return dest;
    }

    public String getPatientID() {
        return patid;
    }

    public String getPatientName() {
        return patname;
    }

    public String getStudyID() {
        return studyid;
    }

    public final Dataset getIAN() {
        return ian;
    }

    public String getOrderDetails() {
        return "dest=" + dest + ", suid="
                + ian.getString(Tags.StudyInstanceUID);
    }

    /**
     * Processes order attributes based on the {@code Dataset} and patientID set in the {@code ctor}.
     * @see BaseJmsOrder#processOrderProperties(Object...)
     */
    @Override
    public void processOrderProperties(Object... properties) {
        this.setOrderProperty(JmsOrderProperties.STUDY_INSTANCE_UID, ian.getString(Tags.StudyInstanceUID));
        this.setOrderProperty(JmsOrderProperties.PATIENT_ID, 
                this.patid != null ? this.patid : ian.getString(Tags.PatientID));
        this.setOrderProperty(JmsOrderProperties.ISSUER_OF_PATIENT_ID, ian.getString(Tags.IssuerOfPatientID));
        
        List<String> seriesUIDList = new ArrayList<String>();
        DcmElement refSeriesSeq = ian.get(Tags.RefSeriesSeq);
        if ( refSeriesSeq != null ) {
            for ( int j = 0; j < refSeriesSeq.countItems(); j++ ) {
                Dataset refSeriesDS = refSeriesSeq.getItem(j);
                seriesUIDList.add(refSeriesDS.getString(Tags.SeriesInstanceUID));
            }
        }
        
        this.setOrderMultiProperty(JmsOrderProperties.SERIES_INSTANCE_UID, seriesUIDList.toArray(new String[0]));
    }
}