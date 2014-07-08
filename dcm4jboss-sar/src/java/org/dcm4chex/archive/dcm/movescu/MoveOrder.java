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

package org.dcm4chex.archive.dcm.movescu;

import java.io.Serializable;

import org.dcm4chex.archive.common.BaseJmsOrder;
import org.dcm4chex.archive.common.JmsOrderProperties;

/**
 * @author gunter.zeilinger@tiani.com
 * @version $Revision: 12253 $ $Date: 2009-10-06 15:33:24 +0000 (Tue, 06 Oct 2009) $
 * @since 26.08.2004
 *
 */
public class MoveOrder extends BaseJmsOrder implements Serializable {

	private static final long serialVersionUID = 3617856386927702068L;

    private String retrieveAET;

    private String moveDestination;

    private String patientId;

    private String[] studyIuids;

    private String[] seriesIuids;

    private String[] sopIuids;

    private int priority;

    public MoveOrder(String retrieveAET, String moveDestination, int priority,
            String patientId, String[] studyIuids, String[] seriesIuids,
            String[] sopIuids) {
        this.priority = priority;
        this.retrieveAET = retrieveAET;
        this.moveDestination = moveDestination;
        this.patientId = patientId;
        this.studyIuids = studyIuids;
        this.seriesIuids = seriesIuids;
        this.sopIuids = sopIuids;
    }

    public MoveOrder(String retrieveAET, String moveDestination, int priority,
			String patientId, String studyIuid, String seriesIuid,
			String[] sopIuids) {
		this(retrieveAET, moveDestination, priority, patientId,
				studyIuid != null ? new String[] { studyIuid } : null,
				seriesIuid != null ? new String[] { seriesIuid } : null,
				sopIuids);
	}
	
    public MoveOrder(String retrieveAET, String moveDestination, int priority,
			String patientId, String studyIuid, String[] seriesIuids) {
		this(retrieveAET, moveDestination, priority, patientId,
				studyIuid != null ? new String[] { studyIuid } : null,
				seriesIuids, null);
	}

    public MoveOrder(String retrieveAET, String moveDestination, int priority,
			String patientId, String studyIuid) {
		this(retrieveAET, moveDestination, priority, patientId,
				studyIuid, null, null);
	}
	
	
    public MoveOrder(String retrieveAET, String moveDestination, int priority,
			String patientId, String[] studyIuids) {
		this(retrieveAET, moveDestination, priority, patientId, studyIuids, null, null);
	}
	
    public String getOrderDetails() {
        StringBuffer sb = new StringBuffer();
        if (sopIuids != null) {
            sb.append(sopIuids.length).append(" Instances, dest=");
        } else if (seriesIuids != null) {
            sb.append(seriesIuids.length).append(" Series, dest=");
        } else if (studyIuids != null) {
            sb.append(studyIuids.length).append(" Studies, dest=");
        } else {
            sb.append("1 Patient, dest=");
        }
        sb.append(moveDestination);
        sb.append(", qrscp=").append(retrieveAET);
        sb.append(", priority=").append(priority);
        return sb.toString();
    }

    public final String getQueryRetrieveLevel() {
        return sopIuids != null ? "IMAGE" : seriesIuids != null ? "SERIES"
                : studyIuids != null ? "STUDY" : "PATIENT";
    }

    public final int getPriority() {
        return priority;
    }

    public final void setPriority(int priority) {
        this.priority = priority;
    }

    public final String getMoveDestination() {
        return moveDestination;
    }

    public final void setMoveDestination(String moveDestination) {
        this.moveDestination = moveDestination;
    }

    public final String getPatientId() {
        return patientId;
    }

    public final void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public final String getRetrieveAET() {
        return retrieveAET;
    }

    public final void setRetrieveAET(String retrieveAET) {
        this.retrieveAET = retrieveAET;
    }

    public final String[] getSeriesIuids() {
        return seriesIuids;
    }

    public final void setSeriesIuids(String[] seriesIuids) {
        this.seriesIuids = seriesIuids;
    }

    public final String[] getSopIuids() {
        return sopIuids;
    }

    public final void setSopIuids(String[] sopIuids) {
        this.sopIuids = sopIuids;
    }

    public final String[] getStudyIuids() {
        return studyIuids;
    }

    public final void setStudyIuids(String[] studyIuids) {
        this.studyIuids = studyIuids;
    }

    /**
     * Processes order attributes based on the values set in the {@code ctor}.
     * @see BaseJmsOrder#processOrderProperties(Object...)
     */
    @Override
    public void processOrderProperties(Object... properties) {
        this.setOrderProperty(JmsOrderProperties.RETRIEVE_AE_TITLE, this.retrieveAET);
        this.setOrderProperty(JmsOrderProperties.DESTINATION_AE_TITLE, this.moveDestination);
        this.setOrderProperty(JmsOrderProperties.PATIENT_ID, this.patientId);
        this.setOrderMultiProperty(JmsOrderProperties.STUDY_INSTANCE_UID, this.studyIuids);
        this.setOrderMultiProperty(JmsOrderProperties.SERIES_INSTANCE_UID, this.seriesIuids);
    }
}