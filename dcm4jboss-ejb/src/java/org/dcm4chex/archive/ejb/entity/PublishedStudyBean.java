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

package org.dcm4chex.archive.ejb.entity;

import javax.ejb.CreateException;
import javax.ejb.EntityBean;

import org.apache.log4j.Logger;
import org.dcm4chex.archive.common.PublishedStudyStatus;
import org.dcm4chex.archive.ejb.interfaces.StudyLocal;

/**
 * @author <a href="mailto:franz.willer@gmail.com">Franz Willer</a>
 * @version $Revision: $ $Date:  $
 * @since 12.06.2013
 * 
 * @ejb.bean name="PublishedStudy"
 *          type="CMP"
 *          view-type="local"
 *          local-jndi-name="ejb/PublishedStudy"
 *          primkey-field="pk"
 * @jboss.container-configuration name="Instance Per Transaction CMP 2.x EntityBean"
 * @ejb.transaction type="Required"
 * @ejb.persistence table-name="published_study"
 * @jboss.entity-command name="hsqldb-fetch-key"
 * @jboss.audit-created-time field-name="createdTime"
 * @jboss.audit-updated-time field-name="updatedTime"
 * 
 * @ejb.finder signature="java.util.Collection findByStudyPkAndStatus(long studyPk, int status)"
 *             query="SELECT OBJECT(o) FROM PublishedStudy o WHERE o.study.pk=?1 AND o.status=?2"
 *             transaction-type="Supports"
 * @ejb.finder signature="java.util.Collection findByStudyIUID(java.lang.String suid)"
 *             query="SELECT OBJECT(o) FROM PublishedStudy o WHERE o.study.studyIuid=?1"
 *             transaction-type="Supports"
 * @ejb.finder signature="java.util.Collection findByStudyIUIDAndStatus(java.lang.String suid, int status)"
 *             query="SELECT OBJECT(o) FROM PublishedStudy o WHERE o.study.studyIuid=?1 AND o.status=?2"
 *             transaction-type="Supports"
 */
public abstract class PublishedStudyBean implements EntityBean {

    private static final Logger log = Logger
            .getLogger(PublishedStudyBean.class);

    /**
     * @ejb.interface-method
     * @ejb.pk-field
     * @ejb.persistence column-name="pk"
     * @jboss.persistence auto-increment="true"
     */
    public abstract Long getPk();

    public abstract void setPk(Long pk);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="created_time"
     */
    public abstract java.sql.Timestamp getCreatedTime();
    public abstract void setCreatedTime(java.sql.Timestamp time);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="updated_time"
     */
    public abstract java.sql.Timestamp getUpdatedTime();
    /**
     * @ejb.interface-method
     */
    public abstract void setUpdatedTime(java.sql.Timestamp time);

    /**
     * @ejb.interface-method
     * @ejb.relation name="study-published"
     *               role-name="published-of-study"
     *               cascade-delete="yes"
     *               target-ejb="Study"
     *               target-role-name="study-of-published"
     *               target-multiple="yes" 
     * @jboss.relation fk-column="study_fk" related-pk-field="pk"
     */
    public abstract StudyLocal getStudy();
    /**
     * @ejb.interface-method
     */
    public abstract void setStudy(StudyLocal study);


    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="doc_uid"
     */
    public abstract String getDocumentUID();
    /**
     * @ejb.interface-method
     */
    public abstract void setDocumentUID(String uid);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="docentry_uid"
     */
    public abstract String getDocumentEntryUID();
    /**
     * @ejb.interface-method
     */
    public abstract void setDocumentEntryUID(String uid);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="repository_uid"
     */
    public abstract String getRepositoryUID();
    /**
     * @ejb.interface-method
     */
    public abstract void setRepositoryUID(String uid);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="status"
     */
    public abstract int getStatus();
    /**
     * @ejb.interface-method
     */
    public abstract void setStatus(int status);

    /**
     * @ejb.create-method
     */
    public Long ejbCreate(StudyLocal study, String docUID, String docEntryUID, String repoUID)
            throws CreateException {
        setDocumentUID(docUID);
        setDocumentEntryUID(docEntryUID);
        setRepositoryUID(repoUID);
        setStatus(PublishedStudyStatus.STUDY_COMPLETE);
        return null;
    }

    public void ejbPostCreate(StudyLocal study, String docUID, String docEntryUID, String repoUID)
            throws CreateException {
        setStudy(study);
    }

    /**
     * @ejb.interface-method
     */
    public String asString() {
        StudyLocal study = getStudy();
        return "PublishedStudy[" 
        	+ (study == null ? "null" : study.asString())
        	+ " as " + getDocumentUID()
        	+ "(DocumentEntry.UID:" + getDocumentEntryUID()
        	+ ")]"; 
    }
}
