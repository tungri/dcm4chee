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

import java.util.Collection;

import javax.ejb.CreateException;
import javax.ejb.EntityBean;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;

import org.apache.log4j.Logger;
import org.dcm4chex.archive.ejb.interfaces.UPSLocal;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date:: xxxx-xx-xx $
 * @since Apr 20, 2010
 *
 * @ejb.bean name="UPSSubscription" type="CMP" view-type="local" primkey-field="pk"
 *           local-jndi-name="ejb/UPSSubscription"
 * @ejb.transaction type="Required"
 * @ejb.persistence table-name="ups_subscr"
 * 
 * @jboss.container-configuration name="Instance Per Transaction CMP 2.x EntityBean"
 * @jboss.entity-command name="hsqldb-fetch-key"
 * 
 * @ejb.finder signature="org.dcm4chex.archive.ejb.interfaces.UPSSubscriptionLocal findByReceivingAETAndUPSInstanceUID(java.lang.String aet, java.lang.String iuid)"
 *             query="SELECT OBJECT(s) FROM UPSSubscription AS s WHERE s.receivingAETitle = ?1 AND s.uPS.sopInstanceUID = ?2"
 *             transaction-type="Supports"
 * @ejb.finder signature="java.util.Collection findByReceivingAET(java.lang.String aet)"
 *             query="SELECT OBJECT(s) FROM UPSSubscription AS s WHERE s.receivingAETitle = ?1"
 *             transaction-type="Supports"
 * @jboss.query signature="int ejbSelectNumberDeletionLocks(org.dcm4chex.archive.ejb.interfaces.UPSLocal ups)"
 *              query="SELECT COUNT(s) FROM UPSSubscription AS s WHERE s.uPS = ?1 AND s.deletionLock = TRUE"
 */
public abstract class UPSSubscriptionBean implements EntityBean {

    private static final Logger LOG = Logger.getLogger(UPSSubscriptionBean.class);

    /**
     * @ejb.create-method
     */
    public Long ejbCreate(String aet, boolean dellock, UPSLocal ups)
            throws CreateException {
        setReceivingAETitle(aet);
        setDeletionLock(dellock);
        return null;
    }

    public void ejbPostCreate(String aet, boolean dellock, UPSLocal ups)
            throws CreateException {
        setUPS(ups);
        LOG.info(prompt("Created UPSSubscription[pk="));
    }

    public void ejbRemove() throws RemoveException {
        LOG.info(prompt("Deleting UPSSubscription[pk="));
    }

    private String prompt(String prefix) {
        return prefix + getPk() + ", aet=" + getReceivingAETitle()
                + ", dellock=" + getDeletionLock() + "]";
    }

    /**
     * Auto-generated Primary Key
     * 
     * @ejb.interface-method
     * @ejb.pk-field
     * @ejb.persistence column-name="pk"
     * @jboss.persistence auto-increment="true"
     */
    public abstract Long getPk();

    public abstract void getPk(Long pk);

    /**
     * @ejb.persistence column-name="aet"
     * @ejb.interface-method
     */
    public abstract String getReceivingAETitle();

    public abstract void setReceivingAETitle(String aet);

    /**
     * @ejb.persistence column-name="deletion_lock"
     * @ejb.interface-method
     */
    public abstract boolean getDeletionLock();

    /**
     * @ejb.interface-method
     */
    public abstract void setDeletionLock(boolean dellock);

    /**
     * @ejb.relation name="ups-subscription" role-name="subscription-for-ups"
     *               cascade-delete="yes"
     * @jboss.relation fk-column="ups_fk" related-pk-field="pk"
     */
    public abstract void setUPS(UPSLocal ups);

    /**
     * @ejb.interface-method
     */
    public abstract UPSLocal getUPS();

    /**
     * @ejb.select query=""
     */ 
    public abstract int ejbSelectNumberDeletionLocks(UPSLocal ups) throws FinderException;

    /**
     * @ejb.home-method
     */
    public boolean ejbHomeHasDeletionLocks(UPSLocal ups) throws FinderException {
        return ejbSelectNumberDeletionLocks(ups) > 0;
    }

    /**
     * @ejb.select query="SELECT s.receivingAETitle FROM UPSSubscription AS s WHERE s.uPS.sopInstanceUID = ?1"
     */ 
    public abstract Collection<String> ejbSelectReceivingAETs(String iuid)
            throws FinderException;

    /**
     * @ejb.home-method
     */
    public Collection<String> ejbHomeGetReceivingAETs(String iuid)
            throws FinderException {
        return ejbSelectReceivingAETs(iuid);
    }

}
