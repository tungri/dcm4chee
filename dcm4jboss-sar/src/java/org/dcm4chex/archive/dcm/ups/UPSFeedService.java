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

package org.dcm4chex.archive.dcm.ups;

import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.xml.transform.Templates;

import org.dcm4che.data.Dataset;
import org.dcm4che.data.DcmElement;
import org.dcm4che.data.DcmObjectFactory;
import org.dcm4che.dict.Tags;
import org.dcm4chex.archive.common.PrivateTags;
import org.dcm4chex.archive.dcm.ianscu.IANScuService;
import org.dcm4chex.archive.mbean.TemplatesDelegate;
import org.dcm4chex.archive.util.XSLTUtils;
import org.jboss.system.ServiceMBeanSupport;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date:: xxxx-xx-xx $
 * @since Jul 20, 2010
 */
public class UPSFeedService extends ServiceMBeanSupport {

    private static final String MPPS2UPS_XSL = "mpps2ups.xsl";

    private TemplatesDelegate templates = new TemplatesDelegate(this);

    private ObjectName ianScuServiceName;

    private ObjectName upsScpServiceName;

   private final NotificationListener ianListener = new NotificationListener(){

        public void handleNotification(Notification notif, Object handback) {
            UPSFeedService.this.onIAN((Dataset) notif.getUserData());
        }

    };

    public final ObjectName getTemplatesServiceName() {
        return templates.getTemplatesServiceName();
    }

    public final void setTemplatesServiceName(ObjectName serviceName) {
        templates.setTemplatesServiceName(serviceName);
    }
    
    public final String getWorkItemConfigDir() {
        return templates.getConfigDir();
    }

    public final void setWorkItemConfigDir(String path) {
        templates.setConfigDir(path);
    }

    public final ObjectName getIANScuServiceName() {
        return ianScuServiceName;
    }

    public final void setIANScuServiceName(ObjectName ianScuServiceName) {
        this.ianScuServiceName = ianScuServiceName;
    }

    public final ObjectName getUPSScpServiceName() {
        return upsScpServiceName;
    }

    public final void setUPSScpServiceName(ObjectName upsScpServiceName) {
        this.upsScpServiceName = upsScpServiceName;
    }

    protected void startService() throws Exception {
        server.addNotificationListener(ianScuServiceName,
                ianListener , IANScuService.NOTIF_FILTER, null);
    }

    protected void stopService() throws Exception {
        server.removeNotificationListener(ianScuServiceName,
                ianListener, IANScuService.NOTIF_FILTER, null);
    }

    private void onIAN(Dataset mpps) {
        String aet = mpps.getString(Tags.PerformedStationAET);
        Templates stylesheet = templates.getTemplatesForAET(aet, MPPS2UPS_XSL);
        if (stylesheet == null) {
            log.info("No mpps2ups.xsl found for " + aet);
            return;
        }
        Dataset wkitems = DcmObjectFactory.getInstance().newDataset();
        try {
            XSLTUtils.xslt(mpps, stylesheet, wkitems);
        } catch (Exception e) {
            log.error("Failed to create UPS triggered by MPPS from " + aet, e);
            return;
        }
        DcmElement wkitemSeq = wkitems.get(PrivateTags.WorkItemSeq);
        int n = wkitemSeq.countItems();
        log.info("Creating " + n + " UPS(s) triggered by MPPS from " + aet);
        try {
            for (int i = 0; i < n; i++)
                updateOrCreateUPS(wkitemSeq.getItem(i));
        } catch (Exception e) {
            log.error("Failed to create UPS triggered by MPPS from " + aet, e);
        }
    }

    private void updateOrCreateUPS(Dataset ups) throws Exception {
        server.invoke(upsScpServiceName, "updateOrCreateUPS",
                new Object[]{ups}, new String[]{ Dataset.class.getName() });
        
    }
}
