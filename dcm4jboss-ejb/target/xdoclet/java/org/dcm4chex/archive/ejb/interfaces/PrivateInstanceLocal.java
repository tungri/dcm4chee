/*
 * Generated by XDoclet - Do not edit!
 */
package org.dcm4chex.archive.ejb.interfaces;

/**
 * Local interface for PrivateInstance.
 * @xdoclet-generated at ${TODAY}
 * @copyright The XDoclet Team
 * @author XDoclet
 * @version 2.18.0-SNAPSHOT
 */
public interface PrivateInstanceLocal
   extends javax.ejb.EJBLocalObject
{
   /**
    * Auto-generated Primary Key
    */
   public java.lang.Long getPk(  ) ;

   public java.sql.Timestamp getCreatedTime(  ) ;

   public int getPrivateType(  ) ;

   public java.lang.String getSopIuid(  ) ;

   public org.dcm4che.data.Dataset getAttributes(  ) ;

   public void setAttributes( org.dcm4che.data.Dataset ds ) ;

   public org.dcm4chex.archive.ejb.interfaces.PrivateSeriesLocal getSeries(  ) ;

   public java.util.Collection getFiles(  ) ;

}
