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

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.ejb.CreateException;
import javax.ejb.EntityBean;
import javax.ejb.FinderException;
import javax.ejb.RemoveException;

import org.apache.log4j.Logger;
import org.dcm4chex.archive.common.FileStatus;
import org.dcm4chex.archive.ejb.interfaces.FileDTO;
import org.dcm4chex.archive.ejb.interfaces.FileSystemLocal;
import org.dcm4chex.archive.ejb.interfaces.InstanceLocal;
import org.dcm4chex.archive.ejb.interfaces.MD5;

/**
 * @author <a href="mailto:gunter@tiani.com">Gunter Zeilinger</a>
 * @version $Revision: 18249 $ $Date: 2014-02-21 16:18:22 +0000 (Fri, 21 Feb 2014) $
 * 
 * @ejb.bean name="File" type="CMP" view-type="local" primkey-field="pk"
 * 	         local-jndi-name="ejb/File"
 * @jboss.container-configuration name="Instance Per Transaction CMP 2.x EntityBean"
 * @ejb.persistence table-name="files"
 * @ejb.transaction type="Required"
 * @jboss.entity-command name="hsqldb-fetch-key"
 * @jboss.audit-created-time field-name="createdTime"
 * 
 * @ejb.finder signature="java.util.Collection findFilesToCompress(long fspk, java.lang.String cuid, java.sql.Timestamp before, int limit)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findFilesToCompress(long fspk, java.lang.String cuid, java.sql.Timestamp before, int limit)"
 *             query="SELECT OBJECT(f) FROM File AS f WHERE f.fileStatus = 0 AND f.fileTsuid IN ('1.2.840.10008.1.2','1.2.840.10008.1.2.1','1.2.840.10008.1.2.2') AND f.fileSystem.pk = ?1 AND f.instance.sopCuid = ?2 AND f.createdTime < ?3 LIMIT ?4"
 *             strategy="on-find" eager-load-group="*"
 * @ejb.finder signature="java.util.Collection findToCheckMd5(java.lang.String dirPath, java.sql.Timestamp before, int limit)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findToCheckMd5(java.lang.String dirPath, java.sql.Timestamp before, int limit)"
 *             query="SELECT OBJECT(f) FROM File AS f WHERE f.fileSystem.directoryPath = ?1 AND f.fileMd5Field IS NOT NULL AND (f.timeOfLastMd5Check IS NULL OR f.timeOfLastMd5Check < ?2) LIMIT ?3"
 *             strategy="on-find" eager-load-group="*"
 * @ejb.finder signature="java.util.Collection findByStatusAndFileSystem(java.lang.String dirPath, int status, java.sql.Timestamp notBefore, java.sql.Timestamp before, int limit)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findByStatusAndFileSystem(java.lang.String dirPath, int status, java.sql.Timestamp notBefore, java.sql.Timestamp before, int limit)"
 *             query="SELECT OBJECT(f) FROM File AS f WHERE f.fileSystem.directoryPath = ?1 AND f.fileStatus = ?2 AND f.createdTime >= ?3 AND f.createdTime < ?4 LIMIT ?5"
 *             strategy="on-find" eager-load-group="*"
 * @ejb.finder signature="java.util.Collection findByFileSystem(java.lang.String dirPath, int offset, int limit)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findByFileSystem(java.lang.String dirPath, int offset, int limit)"
 *             query="SELECT OBJECT(f) FROM File AS f WHERE f.fileSystem.directoryPath = ?1 OFFSET ?2 LIMIT ?3"
 *             strategy="on-find" eager-load-group="*"
 * @ejb.finder signature="java.util.Collection findFilesToLossyCompress(java.lang.String fsGroupId, java.lang.String cuid, java.lang.String sourceAET, java.sql.Timestamp before, int limit)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findFilesToLossyCompress(java.lang.String fsGroupId, java.lang.String cuid, java.lang.String sourceAET, java.sql.Timestamp before, int limit)"
 *             query="SELECT OBJECT(f) FROM File AS f WHERE f.fileStatus = 0 AND f.fileTsuid NOT IN ('1.2.840.10008.1.2.4.50','1.2.840.10008.1.2.4.51','1.2.840.10008.1.2.4.81','1.2.840.10008.1.2.4.91') AND f.fileSystem.status IN (0,1) AND f.fileSystem.groupID = ?1 AND f.instance.sopCuid = ?2 AND f.instance.series.sourceAET = ?3 AND f.createdTime < ?4 LIMIT ?5"
 *             strategy="on-find" eager-load-group="*"
 * @ejb.finder signature="java.util.Collection findFilesToLossyCompress(java.lang.String fsGroupId, java.lang.String cuid, java.lang.String bodyPart, java.lang.String sourceAET, java.sql.Timestamp before, int limit)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findFilesToLossyCompress(java.lang.String fsGroupId, java.lang.String cuid, java.lang.String bodyPart, java.lang.String sourceAET, java.sql.Timestamp before, int limit)"
 *             query="SELECT OBJECT(f) FROM File AS f WHERE f.fileStatus = 0 AND f.fileTsuid NOT IN ('1.2.840.10008.1.2.4.50','1.2.840.10008.1.2.4.51','1.2.840.10008.1.2.4.81','1.2.840.10008.1.2.4.91') AND f.fileSystem.status IN (0,1) AND f.fileSystem.groupID = ?1 AND f.instance.sopCuid = ?2 AND f.instance.series.bodyPartExamined = ?3 AND f.instance.series.sourceAET = ?4 AND f.createdTime < ?5 LIMIT ?6"
 *             strategy="on-find" eager-load-group="*"
 * @ejb.finder signature="java.util.Collection findFilesToLossyCompressWithExternalRetrieveAET(java.lang.String fsGroupId, java.lang.String retrieveAET, java.lang.String cuid, java.lang.String sourceAET, java.sql.Timestamp before, int limit)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findFilesToLossyCompressWithExternalRetrieveAET(java.lang.String fsGroupId, java.lang.String retrieveAET, java.lang.String cuid, java.lang.String sourceAET, java.sql.Timestamp before, int limit)"
 *             query="SELECT OBJECT(f) FROM File AS f WHERE f.fileStatus = 0 AND f.fileTsuid NOT IN ('1.2.840.10008.1.2.4.50','1.2.840.10008.1.2.4.51','1.2.840.10008.1.2.4.81','1.2.840.10008.1.2.4.91') AND f.fileSystem.status IN (0,1) AND f.fileSystem.groupID = ?1 AND f.instance.externalRetrieveAET = ?2 AND f.instance.sopCuid = ?3 AND f.instance.series.sourceAET = ?4 AND f.createdTime < ?5 LIMIT ?6"
 *             strategy="on-find" eager-load-group="*"
 * @ejb.finder signature="java.util.Collection findFilesToLossyCompressWithExternalRetrieveAET(java.lang.String fsGroupId, java.lang.String retrieveAET, java.lang.String cuid, java.lang.String bodyPart, java.lang.String sourceAET, java.sql.Timestamp before, int limit)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findFilesToLossyCompressWithExternalRetrieveAET(java.lang.String fsGroupId, java.lang.String retrieveAET, java.lang.String cuid, java.lang.String bodyPart, java.lang.String sourceAET, java.sql.Timestamp before, int limit)"
 *             query="SELECT OBJECT(f) FROM File AS f WHERE f.fileStatus = 0 AND f.fileTsuid NOT IN ('1.2.840.10008.1.2.4.50','1.2.840.10008.1.2.4.51','1.2.840.10008.1.2.4.81','1.2.840.10008.1.2.4.91') AND f.fileSystem.status IN (0,1) AND f.fileSystem.groupID = ?1 AND f.instance.externalRetrieveAET = ?2 AND f.instance.sopCuid = ?3 AND f.instance.series.bodyPartExamined = ?4 AND f.instance.series.sourceAET = ?5 AND f.createdTime < ?6 LIMIT ?7"
 *             strategy="on-find" eager-load-group="*"
 * @ejb.finder signature="java.util.Collection findFilesToLossyCompressWithCopyOnOtherFileSystemGroup(java.lang.String fsGroupId, java.lang.String otherFSGroupId, java.lang.String cuid, java.lang.String sourceAET, java.sql.Timestamp before, int limit)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findFilesToLossyCompressWithCopyOnOtherFileSystemGroup(java.lang.String fsGroupId, java.lang.String otherFSGroupId, java.lang.String cuid, java.lang.String sourceAET, java.sql.Timestamp before, int limit)"
 *             query="SELECT DISTINCT OBJECT(f) FROM File AS f, IN (f.instance.files) AS f2 WHERE f.fileStatus = 0 AND f.fileTsuid NOT IN ('1.2.840.10008.1.2.4.50','1.2.840.10008.1.2.4.51','1.2.840.10008.1.2.4.81','1.2.840.10008.1.2.4.91') AND f.fileSystem.status IN (0,1) AND f.fileSystem.groupID = ?1 AND f2.fileSystem.groupID = ?2 AND f.instance.sopCuid = ?3 AND f.instance.series.sourceAET = ?4 AND f.createdTime < ?5 LIMIT ?6"
 *             strategy="on-find" eager-load-group="*"
 * @ejb.finder signature="java.util.Collection findFilesToLossyCompressWithCopyOnOtherFileSystemGroup(java.lang.String fsGroupId, java.lang.String otherFSGroupId, java.lang.String cuid, java.lang.String bodyPart, java.lang.String sourceAET, java.sql.Timestamp before, int limit)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findFilesToLossyCompressWithCopyOnOtherFileSystemGroup(java.lang.String fsGroupId, java.lang.String otherFSGroupId, java.lang.String cuid, java.lang.String bodyPart, java.lang.String sourceAET, java.sql.Timestamp before, int limit)"
 *             query="SELECT DISTINCT OBJECT(f) FROM File AS f, IN (f.instance.files) AS f2 WHERE f.fileStatus = 0 AND f.fileTsuid NOT IN ('1.2.840.10008.1.2.4.50','1.2.840.10008.1.2.4.51','1.2.840.10008.1.2.4.81','1.2.840.10008.1.2.4.91') AND f.fileSystem.status IN (0,1) AND f.fileSystem.groupID = ?1 AND f2.fileSystem.groupID = ?2 AND f.instance.sopCuid = ?3 AND f.instance.series.bodyPartExamined = ?4 AND f.instance.series.sourceAET = ?5 AND f.createdTime < ?6 LIMIT ?7"
 *             strategy="on-find" eager-load-group="*"
 * @ejb.finder signature="java.util.Collection findToSyncArchived(java.lang.String fsPath, int limit)"
 *             query="" transaction-type="Supports"
 * @jboss.query signature="java.util.Collection findToSyncArchived(java.lang.String fsPath, int limit)"
 *             query="SELECT OBJECT(f) FROM File AS f WHERE f.fileStatus = 2 AND f.instance.archived = false AND f.fileSystem.directoryPath = ?1 LIMIT ?2"
 *             strategy="on-find" eager-load-group="*"
 * @ejb.finder signature="java.util.Collection findFilesOfTarFile(java.lang.String fsId, java.lang.String tarFilename)"
 *             query="SELECT OBJECT(f) FROM File AS f WHERE f.fileSystem.directoryPath = ?1 AND f.filePath LIKE ?2" 
 *             transaction-type="Supports"
 *
 * @jboss.query 
 *      signature="java.util.Set ejbSelectGeneric(java.lang.String jbossQl, java.lang.Object[] args)"
 *  dynamic="true"
 *  strategy="on-load"
 *  page-size="20"
 *  eager-load-group="*"
 *  
 * @jboss.query 
 *      signature="java.sql.Timestamp ejbSelectGenericTime(java.lang.String jbossQl, java.lang.Object[] args)"
 *  dynamic="true"
 *
 */
public abstract class FileBean implements EntityBean {

    private static final Logger log = Logger.getLogger(FileBean.class);

    /**
     * Auto-generated Primary Key
     * 
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
     * @ejb.persistence column-name="md5_check_time"
     */
    public abstract java.sql.Timestamp getTimeOfLastMd5Check();

    /**
     * @ejb.interface-method
     */
    public abstract void setTimeOfLastMd5Check(java.sql.Timestamp time);

    /**
     * File Path (relative path to Directory).
     * 
     * @ejb.interface-method
     * @ejb.persistence column-name="filepath"
     */
    public abstract String getFilePath();

    /**
     * @ejb.interface-method
     */
    public abstract void setFilePath(String path);

    /**
     * Transfer Syntax UID
     * 
     * @ejb.interface-method
     * @ejb.persistence column-name="file_tsuid"
     */
    public abstract String getFileTsuid();

    /**
     * @ejb.interface-method
     */
    public abstract void setFileTsuid(String tsuid);

    /**
     * MD5 checksum as hex string
     * 
     * @ejb.interface-method
     * @ejb.persistence column-name="file_md5"
     */
    public abstract String getFileMd5Field();

    public abstract void setFileMd5Field(String md5);

    /**
	 * MD5 checksum of the original file (as it was received, before
	 * compression) as hex string
	 * 
     * @ejb.interface-method
	 * @ejb.persistence column-name="orig_md5"
	 */
    public abstract String getOrigMd5Field();

    public abstract void setOrigMd5Field(String md5);

    /**
     * @ejb.interface-method
     * @ejb.persistence column-name="file_status"
     */
    public abstract int getFileStatus();

    /**
     * @ejb.interface-method
     */
    public abstract void setFileStatus(int status);

    /**
     * MD5 checksum in binary format
     * 
     * @ejb.interface-method
     */
    public byte[] getFileMd5() {
        return MD5.toBytes(getFileMd5Field());
    }

    /**
     * @ejb.interface-method
     */
    public void setFileMd5(byte[] md5) {
        setFileMd5Field(MD5.toString(md5));
    }

    /**
     * Original MD5 checksum in binary format
     * 
     * @ejb.interface-method
     */
    public byte[] getOrigMd5() {
        return MD5.toBytes(getOrigMd5Field());
    }

    /**
     * @ejb.interface-method
     */
    public void setOrigMd5(byte[] md5) {
        setOrigMd5Field(MD5.toString(md5));
    }

    /**
     * File Size
     * 
     * @ejb.interface-method
     * @ejb.persistence column-name="file_size"
     */
    public abstract long getFileSize();

    /**
     * @ejb.interface-method
     */
    public abstract void setFileSize(long size);

    /**
     * @ejb.interface-method
     * @ejb.relation name="instance-files"
     * 	             role-name="files-of-instance"
     *               cascade-delete="yes"
     * @jboss.relation fk-column="instance_fk"
     * 	               related-pk-field="pk"
     */
    public abstract void setInstance(InstanceLocal inst);

    /**
     * @ejb.interface-method
     */
    public abstract InstanceLocal getInstance();

    /**
     * @ejb.interface-method
     * @ejb.relation name="filesystem-files"
     * 	             role-name="files-of-filesystem"
     *               target-role-name="filesystem-of-file"
     *               target-ejb="FileSystem"
     *               target-multiple="yes"
     * @jboss.relation fk-column="filesystem_fk"
     * 	               related-pk-field="pk"
     */
    public abstract void setFileSystem(FileSystemLocal fs);

    /**
     * @ejb.interface-method
     */
    public abstract FileSystemLocal getFileSystem();

    /**
     * @ejb.interface-method
     */
    public boolean isRedundant() {
        InstanceLocal inst = getInstance();
        return inst == null || inst.getFiles().size() > 1;
    }

    /**
     * @ejb.interface-method
     */
    public FileDTO getFileDTO() {
        FileSystemLocal fs = getFileSystem();
        FileDTO retval = new FileDTO();
        retval.setPk(getPk().longValue());
        retval.setRetrieveAET(fs.getRetrieveAET());
        retval.setFileSystemPk(fs.getPk().longValue());
        retval.setFileSystemGroupID(fs.getGroupID());
        retval.setDirectoryPath(fs.getDirectoryPath());
        retval.setAvailability(fs.getAvailability());
        retval.setUserInfo(fs.getUserInfo());
        retval.setFilePath(getFilePath());
        retval.setFileTsuid(getFileTsuid());
        retval.setFileSize(getFileSize());
        retval.setFileMd5(getFileMd5());
        retval.setOrigMd5(getOrigMd5());
        retval.setFileStatus(getFileStatus());
        
        InstanceLocal inst = getInstance();
        if (inst != null) {
            retval.setSopInstanceUID(inst.getSopIuid());
            retval.setSopClassUID(inst.getSopCuid());
            retval.setExternalRetrieveAET(inst.getExternalRetrieveAET());
        }
        return retval;
    }

    /**
     * @ejb.interface-method
     */
    public String asString() {
        return prompt();
    }

    private String prompt() {
        return "File[pk=" + getPk() + ", filepath=" + getFilePath()
                + ", tsuid=" + getFileTsuid() + ", filesystem->"
                + getFileSystem() + ", inst->" + getInstance() + "]";
    }

    /**
     * Create file.
     * 
     * @ejb.create-method
     */
    public Long ejbCreate(String path, String tsuid, long size, byte[] md5, byte[] origMd5,
    		int status, InstanceLocal instance, FileSystemLocal filesystem)
            throws CreateException {
        setFilePath(path);
        setFileTsuid(tsuid);
        setFileSize(size);
        setFileMd5(md5);
        setOrigMd5(origMd5);
        setFileStatus(status);
        return null;
    }

    public void ejbPostCreate(String path, String tsuid, long size, byte[] md5, byte[] origMd5,
    		int status, InstanceLocal instance, FileSystemLocal filesystem)
            throws CreateException {
        setInstance(instance);
        setFileSystem(filesystem);
        log.info("Created " + prompt());
    }

    public void ejbRemove() throws RemoveException {
        log.info("Deleting " + prompt());
    }
    
    /**
     * @ejb.select query=""
     *  transaction-type="Supports"
     */ 
    public abstract Set ejbSelectGeneric(String jbossQl, Object[] args)
                throws FinderException;
    
    /**
     * @ejb.select query=""
     *  transaction-type="Supports"
     */ 
    public abstract Timestamp ejbSelectGenericTime(String jbossQl, Object[] args)
                throws FinderException;
    
    /**    
     * @ejb.home-method
     */
    public Collection ejbHomeSelectByStatusAndFileSystem(List dirPath, int status, 
            Timestamp notBefore, Timestamp before, int limit) throws FinderException {
        StringBuilder jbossQl = new StringBuilder()
        .append("SELECT OBJECT(f) FROM File AS f WHERE f.fileStatus = ?1")
        .append(" AND f.createdTime >= ?2 AND f.createdTime < ?3 AND f.fileSystem.directoryPath IN (");
        Object[] args = new Object[dirPath.size()+4];
        args[0] = status; args[1] = notBefore; args[2] = before;
        int idx = 3;
        for (int i = 1, len = dirPath.size(); i < len; i++) {
            args[idx++] =dirPath.get(i);
            jbossQl.append("?").append(idx).append(", ");
        }
        args[idx++] = dirPath.get(0);
        jbossQl.append("?").append(idx).append(")");
        args[idx++] = limit;
        jbossQl.append(" LIMIT ?").append(idx);
        return ejbSelectGeneric(jbossQl.toString(), args);
    }
    /**    
     * @ejb.home-method
     */
    public Timestamp ejbHomeMinCreatedTimeOnFsWithFileStatus(List dirPath, int status) throws FinderException {
        Object[] args = new Object[dirPath.size()+1];
        StringBuilder jbossQl = new StringBuilder()
        .append("SELECT MIN(f.createdTime) FROM File f WHERE f.fileSystem.directoryPath IN (?1");
        args[0] = dirPath.get(0);
        int i = 1;
        for (int len = dirPath.size() ; i < len ; ) {
            args[i] = dirPath.get(i);
            jbossQl.append(",?").append(++i);
        }
        args[i] = status;
        jbossQl.append(") AND f.fileStatus = ?").append(++i);
        return ejbSelectGenericTime(jbossQl.toString(), args);
    }

}
