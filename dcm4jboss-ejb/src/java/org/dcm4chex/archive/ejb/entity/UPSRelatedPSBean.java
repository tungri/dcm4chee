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

package org.dcm4chex.archive.ejb.entity;

import javax.ejb.CreateException;
import javax.ejb.EntityBean;
import javax.ejb.RemoveException;

import org.apache.log4j.Logger;
import org.dcm4che.data.Dataset;
import org.dcm4che.dict.Tags;
import org.dcm4chex.archive.ejb.interfaces.UPSLocal;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date:: xxxx-xx-xx $
 * @since Apr 2, 2010
 * 
 * @ejb.bean name="UPSRelatedPS" type="CMP" view-type="local"
 *           local-jndi-name="ejb/UPSRelatedPS" primkey-field="pk"
 * @jboss.container-configuration name="Instance Per Transaction CMP 2.x EntityBean"
 * @ejb.persistence table-name="ups_rel_ps"
 * @ejb.transaction type="Required"
 * @jboss.entity-command name="hsqldb-fetch-key"
 */
public abstract class UPSRelatedPSBean implements EntityBean {

    private static final Logger LOG = Logger.getLogger(UPSRelatedPSBean.class);

    /**
     * @ejb.create-method
     */
    public Long ejbCreate(Dataset ds, UPSLocal ups) throws CreateException {
        setRefSOPInstanceUID(ds.getString(Tags.RefSOPInstanceUID));
        setRefSOPClassUID(ds.getString(Tags.RefSOPClassUID));
        return null;
    }

    public void ejbPostCreate(Dataset ds, UPSLocal ups) throws CreateException {
        setUPS(ups);
        LOG.info(prompt("Created UPSRelatedPS[pk=]"));
    }

    public void ejbRemove() throws RemoveException {
        LOG.info(prompt("Deleting UPSRelatedPS[pk="));
    }

    private String prompt(String prefix) {
        return prefix + getPk()
                + ", iuid=" + getRefSOPInstanceUID()
                + ", cuid=" + getRefSOPClassUID() + "]";
   }

    /**
     * Auto-generated Primary Key
     * 
     * @ejb.interface-method
     * @ejb.pk-field
     * @ejb.persistence column-name="pk"
     * @jboss.persistence auto-increment="true"
     *  
     */
    public abstract Long getPk();

    public abstract void setPk(Long pk);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="sop_iuid"
     */
    public abstract String getRefSOPInstanceUID();

    public abstract void setRefSOPInstanceUID(String uid);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="sop_cuid"
     */
    public abstract String getRefSOPClassUID();

    public abstract void setRefSOPClassUID(String uid);

    /**
     * @ejb.relation name="ups-related-ps" role-name="related-ps-of-ups"
     *               cascade-delete="yes"
     * @jboss.relation fk-column="ups_fk" related-pk-field="pk"
     */
    public abstract void setUPS(UPSLocal ups);

    /**
     * @ejb.interface-method
     */
    public abstract UPSLocal getUPS();
}
