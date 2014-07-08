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

import javax.management.Notification;
import javax.management.NotificationFilter;

import org.dcm4che.data.Dataset;
import org.dcm4che.dict.UIDs;
import org.dcm4che.net.AcceptorPolicy;
import org.dcm4che.net.DcmServiceRegistry;
import org.dcm4chex.archive.dcm.AbstractScpService;
import org.dcm4chex.archive.ejb.jdbc.RetrieveCmd;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date$
 * @since Aug 6, 2009
 */
public class AttributesModificationScpService extends AbstractScpService {

    public static final String NOTIF_TYPE = "org.dcm4chex.archive.dcm.modify";

    public static final NotificationFilter NOTIF_FILTER = 
        new NotificationFilter() {
            private static final long serialVersionUID = 6762311590840314136L;

            public boolean isNotificationEnabled(Notification notif) {
                return NOTIF_TYPE.equals(notif.getType());
        }
    };

    private boolean updateOriginalAttributesSeq;

    private int entityNotFoundErrorCode;

    private boolean createPatientOnMoveStudy;
    
    private AttributesModificationScp scp = new AttributesModificationScp(this);

    public final void setUpdateOriginalAttributesSeq(boolean enable) {
        this.updateOriginalAttributesSeq = enable;
    }

    public final boolean isUpdateOriginalAttributesSeq() {
        return updateOriginalAttributesSeq;
    }

    public final void setEntityNotFoundErrorCodeAsString(
            String entityNotFoundErrorCode) {
        this.entityNotFoundErrorCode = entityNotFoundErrorCode.endsWith("H")
                ? Integer.parseInt(entityNotFoundErrorCode
                        .substring(0, entityNotFoundErrorCode.length()-1), 16)
                : Integer.parseInt(entityNotFoundErrorCode);
    }

    public final String getEntityNotFoundErrorCodeAsString() {
        return String.format("%04XH", entityNotFoundErrorCode);
    }

    public final int getEntityNotFoundErrorCode() {
        return entityNotFoundErrorCode;
    }

    public boolean isCreatePatientOnMoveStudy() {
        return createPatientOnMoveStudy;
    }

    public void setCreatePatientOnMoveStudy(boolean createPatientOnMoveStudy) {
        this.createPatientOnMoveStudy = createPatientOnMoveStudy;
    }

    protected void bindDcmServices(DcmServiceRegistry services) {
        services.bind(UIDs.Dcm4cheAttributesModificationNotificationSOPClass, scp);
    }

    protected void unbindDcmServices(DcmServiceRegistry services) {
        services.unbind(UIDs.Dcm4cheAttributesModificationNotificationSOPClass);
    }

    protected void enablePresContexts(AcceptorPolicy policy) {
        policy.putPresContext(UIDs.Dcm4cheAttributesModificationNotificationSOPClass,
                valuesToStringArray(tsuidMap));
    }

    protected void disablePresContexts(AcceptorPolicy policy) {
        policy.putPresContext(UIDs.Dcm4cheAttributesModificationNotificationSOPClass,
                null);
    }

    void sendAttributesModificationNotification(Dataset ds) {
        RetrieveCmd.clearCachedSeriesAttrs();
        long eventID = super.getNextNotificationSequenceNumber();
        Notification notif = new Notification(NOTIF_TYPE, this, eventID);
        notif.setUserData(ds);
        super.sendNotification(notif);
    }

}
