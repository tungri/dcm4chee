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
import javax.ejb.EJBException;
import javax.ejb.EntityBean;
import javax.ejb.EntityContext;
import javax.ejb.FinderException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.dcm4che.data.Dataset;
import org.dcm4che.dict.Tags;
import org.dcm4chex.archive.ejb.conf.AttributeFilter;
import org.dcm4chex.archive.ejb.interfaces.CodeLocal;
import org.dcm4chex.archive.ejb.interfaces.CodeLocalHome;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date:: xxxx-xx-xx $
 * @since Jan 3, 2011
 * 
 * @ejb.bean name="ContentItem" type="CMP" view-type="local" primkey-field="pk"
 *           local-jndi-name="ejb/ContentItem"
 * @jboss.container-configuration name="Instance Per Transaction CMP 2.x EntityBean"
 * @ejb.transaction type="Required"
 * @ejb.persistence table-name="content_item"
 * @jboss.entity-command name="hsqldb-fetch-key"
 * 
 * @ejb.ejb-ref ejb-name="Code" view-type="local" ref-name="ejb/Code"
 */
public abstract class ContentItemBean implements EntityBean {

    private CodeLocalHome codeHome;

    public void setEntityContext(EntityContext ctx) {
        Context jndiCtx = null;
        try {
            jndiCtx = new InitialContext();
            codeHome = (CodeLocalHome) jndiCtx.lookup("java:comp/env/ejb/Code");
        } catch (NamingException e) {
            throw new EJBException(e);
        } finally {
            if (jndiCtx != null) {
                try {
                    jndiCtx.close();
                } catch (NamingException ignore) {
                }
            }
        }
    }

    public void unsetEntityContext() {
        codeHome = null;
    }

    /**
     * Auto-generated Primary Key
     * 
     * @ejb.pk-field
     * @ejb.persistence column-name="pk"
     * @jboss.persistence auto-increment="true"
     */
    public abstract Long getPk();
    public abstract void setPk(Long pk);

    /**
      * @ejb.persistence column-name="rel_type"
     */
    public abstract String getRelationshipType();
    public abstract void setRelationshipType(String relType);

    /**
     * @ejb.persistence column-name="text_value"
    */
   public abstract String getTextValue();
   public abstract void setTextValue(String textValue);

    /**
     * @ejb.relation name="content-item-concept-name"
     *               role-name="content-item-with-concept-name"
     *               target-ejb="Code"
     *               target-role-name="concept-name-of-content-item"
     *               target-multiple="yes"
     * @jboss.relation fk-column="name_fk" related-pk-field="pk"
     */
    public abstract CodeLocal getConceptName();
    public abstract void setConceptName(CodeLocal conceptName);

    /**
     * @ejb.relation name="content-item-concept-code"
     *               role-name="content-item-with-concept-code"
     *               target-ejb="Code"
     *               target-role-name="concept-code-of-content-item"
     *               target-multiple="yes"
     * @jboss.relation fk-column="code_fk" related-pk-field="pk"
     */
    public abstract CodeLocal getConceptCode();
    public abstract void setConceptCode(CodeLocal codeValue);

    /**
     * @ejb.create-method
     */
    public Long ejbCreate(Dataset item) throws CreateException {
        setRelationshipType(item.getString(Tags.RelationshipType));
        String s = item.getString(Tags.TextValue);
        if (s != null) {
            AttributeFilter filter = AttributeFilter.getInstanceAttributeFilter(null);
            int maxLength = filter.getContentItemTextValueMaxLength();
            if (filter.isICase(Tags.TextValue))
                s = s.toUpperCase();
            if (s.length() > maxLength )
                s = s.substring(0, maxLength);
        }
        setTextValue(s);
        return null;
    }

    public void ejbPostCreate(Dataset item) throws CreateException {
        try {
            setConceptName(CodeBean.valueOf(codeHome,
                    item.getItem(Tags.ConceptNameCodeSeq)));
            setConceptCode(CodeBean.valueOf(codeHome,
                    item.getItem(Tags.ConceptCodeSeq)));
        } catch (FinderException e) {
            throw new CreateException(e.getMessage());
        }
    }
}
