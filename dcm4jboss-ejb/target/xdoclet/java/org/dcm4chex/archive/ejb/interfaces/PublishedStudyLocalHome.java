/*
 * Generated by XDoclet - Do not edit!
 */
package org.dcm4chex.archive.ejb.interfaces;

/**
 * Local home interface for PublishedStudy.
 * @xdoclet-generated at ${TODAY}
 * @copyright The XDoclet Team
 * @author XDoclet
 * @version 2.18.0-SNAPSHOT
 */
public interface PublishedStudyLocalHome
   extends javax.ejb.EJBLocalHome
{
   public static final String COMP_NAME="java:comp/env/ejb/PublishedStudyLocal";
   public static final String JNDI_NAME="ejb/PublishedStudy";

   public org.dcm4chex.archive.ejb.interfaces.PublishedStudyLocal create(org.dcm4chex.archive.ejb.interfaces.StudyLocal study , java.lang.String docUID , java.lang.String docEntryUID , java.lang.String repoUID)
      throws javax.ejb.CreateException;

   public java.util.Collection findByStudyPkAndStatus(long studyPk, int status)
      throws javax.ejb.FinderException;

   public java.util.Collection findByStudyIUID(java.lang.String suid)
      throws javax.ejb.FinderException;

   public java.util.Collection findByStudyIUIDAndStatus(java.lang.String suid, int status)
      throws javax.ejb.FinderException;

   public org.dcm4chex.archive.ejb.interfaces.PublishedStudyLocal findByPrimaryKey(java.lang.Long pk)
      throws javax.ejb.FinderException;

}
