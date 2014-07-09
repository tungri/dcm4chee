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
 * Agfa-Gevaert Group.
 * Portions created by the Initial Developer are Copyright (C) 2003-2005
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

import org.dcm4che.data.Dataset;
import org.dcm4che.data.PersonName;
import org.dcm4che.dict.Tags;
import org.dcm4chex.archive.ejb.conf.AttributeFilter;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision: 14397 $ $Date: 2010-11-25 13:07:32 +0000 (Thu, 25 Nov 2010) $
 * @since Jun 27, 2007
 * 
 * @ejb.bean name="VerifyingObserver" type="CMP" view-type="local"
 *           local-jndi-name="ejb/VerifyingObserver" primkey-field="pk"
 * @jboss.container-configuration name="Instance Per Transaction CMP 2.x EntityBean"
 * @ejb.persistence table-name="verify_observer"
 * @ejb.transaction type="Required"
 * @jboss.entity-command name="hsqldb-fetch-key"
 *
 * @ejb.finder signature="Collection findAll()"
 *             query="SELECT OBJECT(o) FROM VerifyingObserver AS o"
 *             transaction-type="Supports"
 */
public abstract class VerifyingObserverBean implements EntityBean {

    /**
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
     * @ejb.persistence column-name="verify_datetime"
     */
    public abstract java.sql.Timestamp getVerificationDateTime();
    public abstract void setVerificationDateTime(java.sql.Timestamp dateTime);
    
    private void setVerificationDateTime(java.util.Date date) {
        setVerificationDateTime(date != null
                ? new java.sql.Timestamp(date.getTime())
                : null);
    }
    
    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="observer_name"
     */
    public abstract String getVerifyingObserverName();
    public abstract void setVerifyingObserverName(String name);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="observer_fn_sx"
     */
    public abstract String getVerifyingObserverFamilyNameSoundex();

    /**
     * @ejb.interface-method
     */
    public abstract void setVerifyingObserverFamilyNameSoundex(String name);
        
    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="observer_gn_sx"
     */
    public abstract String getVerifyingObserverGivenNameSoundex();

    /**
     * @ejb.interface-method
     */
    public abstract void setVerifyingObserverGivenNameSoundex(String name);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="observer_i_name"
     */
    public abstract String getVerifyingObserverIdeographicName();
    public abstract void setVerifyingObserverIdeographicName(String name);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="observer_p_name"
     */
    public abstract String getVerifyingObserverPhoneticName();
    public abstract void setVerifyingObserverPhoneticName(String name);
    
    private void setVerifyingObserverName(PersonName pn) {
        if (pn != null) {
            PersonName ipn = pn.getIdeographic();
            PersonName ppn = pn.getPhonetic();
            String name;
            if ((name = pn.toComponentGroupString(false)) != null) {
                AttributeFilter filter = AttributeFilter.getInstanceAttributeFilter(null);
                setVerifyingObserverName(filter.toUpperCase(name, Tags.VerifyingObserverName));
            }
            if (ipn != null && (name = ipn.toComponentGroupString(false)) != null) {
                setVerifyingObserverIdeographicName(name.toUpperCase());
            }
            if (ppn != null && (name = ppn.toComponentGroupString(false)) != null) {
                setVerifyingObserverPhoneticName(name.toUpperCase());
            }
        }
        if (AttributeFilter.isSoundexEnabled()) {
            setVerifyingObserverFamilyNameSoundex(
                    AttributeFilter.toSoundex(pn, PersonName.FAMILY, "*"));
            setVerifyingObserverGivenNameSoundex(
                    AttributeFilter.toSoundex(pn, PersonName.GIVEN, "*"));
        }
    }

    /**
     * @ejb.create-method
     */
    public Long ejbCreate(Dataset item) throws CreateException {            
       setVerificationDateTime(item.getDate(Tags.VerificationDateTime));
       setVerifyingObserverName(item.getPersonName(Tags.VerifyingObserverName));
       return null;
    }

    public void ejbPostCreate(Dataset item) throws CreateException {
    }
}
