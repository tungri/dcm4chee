/*
 * Generated by XDoclet - Do not edit!
 */
package org.dcm4chex.archive.ejb.interfaces;

/**
 * Remote interface for AEManager.
 * @xdoclet-generated at ${TODAY}
 * @copyright The XDoclet Team
 * @author XDoclet
 * @version 2.18.0-SNAPSHOT
 */
public interface AEManager
   extends javax.ejb.EJBObject
{

   public int getCacheSize(  )
      throws java.rmi.RemoteException;

   public int getMaxCacheSize(  )
      throws java.rmi.RemoteException;

   public void setMaxCacheSize( int maxCacheSize )
      throws java.rmi.RemoteException;

   public void clearCache(  )
      throws java.rmi.RemoteException;

   public org.dcm4chex.archive.ejb.interfaces.AEDTO findByPrimaryKey( long aePk )
      throws javax.ejb.FinderException, java.rmi.RemoteException;

   public org.dcm4chex.archive.ejb.interfaces.AEDTO findByAET( java.lang.String aet )
      throws javax.ejb.FinderException, org.dcm4chex.archive.exceptions.UnknownAETException, java.rmi.RemoteException;

   public java.util.Collection findByHostName( java.lang.String hostName )
      throws javax.ejb.FinderException, java.rmi.RemoteException;

   public java.util.Collection findAll(  )
      throws javax.ejb.FinderException, java.rmi.RemoteException;

   public void updateAE( org.dcm4chex.archive.ejb.interfaces.AEDTO modAE )
      throws javax.ejb.FinderException, java.rmi.RemoteException;

   public void newAE( org.dcm4chex.archive.ejb.interfaces.AEDTO newAE )
      throws javax.ejb.CreateException, java.rmi.RemoteException;

   public void removeAE( long aePk )
      throws java.lang.Exception, java.rmi.RemoteException;

}
