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

package org.dcm4chex.archive.dcm.stgcmt;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.dict.Tags;
import org.dcm4chex.archive.common.BaseJmsOrder;
import org.dcm4chex.archive.common.JmsOrderProperties;

/**
 * @author <a href="mailto:gunter@tiani.com">Gunter Zeilinger</a>
 * @version $Revision: 12253 $ $Date: 2009-10-06 15:33:24 +0000 (Tue, 06 Oct 2009) $
 * @since Jan 5, 2005
 */
public class StgCmtOrder extends BaseJmsOrder implements Serializable {

    private static final long serialVersionUID = 3256437014860936248L;

	private final String callingAET;

    private final String calledAET;

    private final Dataset actionInfo;

    private final boolean scpRole;

    public StgCmtOrder(String callingAET, String calledAET, Dataset actionInfo,
            boolean scpRole) {
        this.callingAET = callingAET;
        this.calledAET = calledAET;
        this.actionInfo = actionInfo;
        this.scpRole = scpRole;
    }

    public final Dataset getActionInfo() {
        return actionInfo;
    }
    
    public final String getCalledAET() {
        return calledAET;
    }
    
    public final String getCallingAET() {
        return callingAET;
    }
    
    public final boolean isScpRole() {
        return scpRole;
    }
    
    public String toString() {
        return "calling=" + callingAET + ", called=" + calledAET
            + ", role=" + (scpRole ? "SCP" : "SCU");
    }
    
    /**
     * Processes order attributes based on the {@code Dataset} and AE titles set in the {@code ctor}.
     * @see BaseJmsOrder#processOrderProperties(Object...)
     */
    @Override
    public void processOrderProperties(Object... properties) {
        this.setOrderProperty(JmsOrderProperties.CALLED_AE_TITLE, calledAET);
        this.setOrderProperty(JmsOrderProperties.CALLING_AE_TITLE, callingAET);
        this.setOrderProperty(JmsOrderProperties.TRANSACTION_UID, actionInfo.getString(Tags.TransactionUID));
    }
}
