/*
 * Generated by XDoclet - Do not edit!
 */
package org.dcm4chex.archive.ejb.interfaces;

/**
 * Local interface for FileSystem.
 * @xdoclet-generated at ${TODAY}
 * @copyright The XDoclet Team
 * @author XDoclet
 * @version 2.18.0-SNAPSHOT
 */
public interface FileSystemLocal
   extends javax.ejb.EJBLocalObject
{

   public int countFiles(  ) throws javax.ejb.FinderException;

   public int countPrivateFiles(  ) throws javax.ejb.FinderException;

   public java.lang.String asString(  ) ;

   /**
    * Auto-generated Primary Key
    */
   public java.lang.Long getPk(  ) ;

   public java.lang.String getDirectoryPath(  ) ;

   public void setDirectoryPath( java.lang.String dirpath ) ;

   public java.lang.String getGroupID(  ) ;

   public void setGroupID( java.lang.String id ) ;

   public java.lang.String getRetrieveAET(  ) ;

   public void setRetrieveAET( java.lang.String aet ) ;

   public int getAvailability(  ) ;

   public void setAvailability( int availability ) ;

   public int getAvailabilitySafe(  ) ;

   public int getStatus(  ) ;

   public void setStatus( int status ) ;

   public java.lang.String getUserInfo(  ) ;

   public void setUserInfo( java.lang.String info ) ;

   public org.dcm4chex.archive.ejb.interfaces.FileSystemLocal getNextFileSystem(  ) ;

   public void setNextFileSystem( org.dcm4chex.archive.ejb.interfaces.FileSystemLocal filesystem ) ;

   public java.util.Collection getPreviousFileSystems(  ) ;

   public void setPreviousFileSystems( java.util.Collection previous ) ;

   public void fromDTO( org.dcm4chex.archive.ejb.interfaces.FileSystemDTO dto ) ;

   public org.dcm4chex.archive.ejb.interfaces.FileSystemDTO toDTO(  ) ;

}