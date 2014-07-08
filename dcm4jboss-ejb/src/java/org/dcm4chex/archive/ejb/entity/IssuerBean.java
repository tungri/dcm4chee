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
import javax.ejb.FinderException;
import javax.ejb.ObjectNotFoundException;
import javax.ejb.RemoveException;

import org.apache.log4j.Logger;
import org.dcm4che.data.Dataset;
import org.dcm4che.dict.Tags;
import org.dcm4chex.archive.ejb.interfaces.IssuerLocal;
import org.dcm4chex.archive.ejb.interfaces.IssuerLocalHome;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date:: xxxx-xx-xx $
 * @since Dec 22, 2010

 * @ejb.bean name="Issuer" type="CMP" view-type="local"
 *           local-jndi-name="ejb/Issuer" primkey-field="pk"
 * @jboss.container-configuration name="Instance Per Transaction CMP 2.x EntityBean"
 * @ejb.persistence table-name="issuer"
 * @ejb.transaction type="Required"
 * @jboss.entity-command name="hsqldb-fetch-key"
 * 
 * @ejb.finder
 *      signature="org.dcm4chex.archive.ejb.interface.IssuerLocal findByLocalNamespaceEntityID(java.lang.String id)"
 *      query="SELECT OBJECT(i) FROM Issuer AS i WHERE i.localNamespaceEntityID = ?1"
 *  transaction-type="Supports"
 *
 * @jboss.query
 *      signature="org.dcm4chex.archive.ejb.interface.IssuerLocal findByLocalNamespaceEntityID(java.lang.String id)"
 *      strategy="on-find"
 *      eager-load-group="*"
 * 
 * @ejb.finder
 *      signature="org.dcm4chex.archive.ejb.interface.IssuerLocal findByUniversalEntityID(java.lang.String uid, java.lang.String type)"
 *      query="SELECT OBJECT(i) FROM Issuer AS i WHERE i.universalEntityID = ?1 AND i.universalEntityIDType = ?2"
 *  transaction-type="Supports"
 *
 * @jboss.query
 *      signature="org.dcm4chex.archive.ejb.interface.IssuerLocal findByUniversalEntityID(java.lang.String uid, java.lang.String type)"
 *      strategy="on-find"
 *      eager-load-group="*"
 */
public abstract class IssuerBean implements EntityBean {

    private static final Logger log = Logger.getLogger(IssuerBean.class);

    public static IssuerLocal valueOf(IssuerLocalHome issuerHome, Dataset item)
            throws CreateException, FinderException {
        if (item == null)
            return null;

        String id = item.getString(Tags.LocalNamespaceEntityID);
        String uid = item.getString(Tags.UniversalEntityID);
        String type = item.getString(Tags.UniversalEntityIDType);
        if (uid == null) {
            if (id == null)
                throw new IllegalArgumentException(
                        "Missing Local Name Space and Universal Entity ID");
        } else if (type == null)
            throw new IllegalArgumentException(
                    "Missing Universal Entity ID Type");
        if (uid != null) {
            try {
                IssuerLocal issuer = 
                        issuerHome.findByUniversalEntityID(uid, type);
                if (id != null) {
                    String eid = issuer.getLocalNamespaceEntityID();
                    if (eid == null) {
                        issuer.setLocalNamespaceEntityID(id);
                        log.info("Update " + issuer.asString());
                    } else if (!id.equals(eid)) {
                        throw new CreateException(
                                "Existing " + issuer.asString()
                                + " Issuer with given Universal Entity ID"
                                + " but different Local Namespace Entity ID");
                    }
                }
            } catch (ObjectNotFoundException onfe) {}
        }
        if (id != null){
            try {
                IssuerLocal issuer =
                        issuerHome.findByLocalNamespaceEntityID(id);
                if (uid != null && type != null) {
                    String euid = issuer.getUniversalEntityID();
                    String etype = issuer.getUniversalEntityIDType();
                    if (euid == null) {
                        issuer.setUniversalEntityID(uid);
                        issuer.setUniversalEntityIDType(type);
                        log.info("Update " + issuer.asString());
                    } else if (!uid.equals(euid) || !type.equals(etype)) {
                        throw new CreateException(
                                "Existing " + issuer.asString()
                                + " with given Local Namespace Entity ID"
                                + " but different Universal Entity ID");
                    }
                }
                return issuer;
            } catch (ObjectNotFoundException onfe) {}
         }
         return issuerHome.create(id, uid, type);
    }

    /**
     * @ejb.create-method
     */
    public Long ejbCreate(String id, String uid, String type)
            throws CreateException {
        setLocalNamespaceEntityID(id);
        setUniversalEntityID(uid);
        setUniversalEntityIDType(type);
        return null;
    }

    public void ejbPostCreate(String value, String designator, String version,
            String meaning) throws CreateException {
        log.info("Created " + prompt());

    }

    public void ejbRemove() throws RemoveException {
        log.info("Deleting " + prompt());
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

    /**
     * Local Namespace Entity ID
     * 
     * @ejb.interface-method
     * @ejb.persistence column-name="entity_id"
     */
    public abstract String getLocalNamespaceEntityID();

    /**
     * @ejb.interface-method
     */
    public abstract void setLocalNamespaceEntityID(String id);

    /**
     * Universal Entity ID
     * 
     * @ejb.interface-method
     * @ejb.persistence column-name="entity_uid"
     */
    public abstract String getUniversalEntityID();

    /**
     * @ejb.interface-method
     */
    public abstract void setUniversalEntityID(String uid);


    /**
     * Universal Entity ID Type
     * 
     * @ejb.interface-method
     * @ejb.persistence column-name="entity_uid_type"
     */
    public abstract String getUniversalEntityIDType();

    /**
     * @ejb.interface-method
     */
    public abstract void setUniversalEntityIDType(String type);

    /**
     * @ejb.interface-method
     */
    public String asString() {
        return prompt();
    }

    private String prompt() {
        return "Issuer[pk=" + getPk()
                + ", id=" + getLocalNamespaceEntityID()
                + ", uid=" + getUniversalEntityID()
                + ", type=" + getUniversalEntityIDType() + "]";
    }
}
