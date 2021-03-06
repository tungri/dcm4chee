/*
 * Generated by XDoclet - Do not edit!
 */
package org.dcm4chex.archive.ejb.interfaces;

/**
 * Remote interface for UPSManager.
 * @xdoclet-generated at ${TODAY}
 * @copyright The XDoclet Team
 * @author XDoclet
 * @version 2.18.0-SNAPSHOT
 */
public interface UPSManager
   extends javax.ejb.EJBObject
{

   public void createUPS( org.dcm4che.data.Dataset ds,org.dcm4chex.archive.common.PatientMatching matching )
      throws org.dcm4che.net.DcmServiceException, java.rmi.RemoteException;

   public org.dcm4che.data.Dataset getUPS( java.lang.String uid )
      throws org.dcm4che.net.DcmServiceException, java.rmi.RemoteException;

   public int getUPSState( java.lang.String uid )
      throws org.dcm4che.net.DcmServiceException, java.rmi.RemoteException;

   public boolean updateMatchingUPS( org.dcm4che.data.Dataset ds )
      throws javax.ejb.FinderException, java.rmi.RemoteException;

   public void updateUPS( java.lang.String iuid,org.dcm4che.data.Dataset ds )
      throws org.dcm4che.net.DcmServiceException, java.rmi.RemoteException;

   public java.util.Collection getReceivingAETs( java.lang.String iuid )
      throws org.dcm4che.net.DcmServiceException, java.rmi.RemoteException;

   public void setRequestUPSCancelInfo( java.lang.String iuid,org.dcm4che.data.Dataset info )
      throws org.dcm4che.net.DcmServiceException, java.rmi.RemoteException;

   public org.dcm4che.data.Dataset changeUPSState( java.lang.String iuid,int newState,java.lang.String tuid )
      throws org.dcm4che.net.DcmServiceException, java.rmi.RemoteException;

   public org.dcm4che.data.Dataset getUPSStateReport( java.lang.String iuid )
      throws javax.ejb.FinderException, java.rmi.RemoteException;

   public boolean subscribeReceiveUPSEventReports( java.lang.String iuid,java.lang.String aet,boolean deletionLock )
      throws org.dcm4che.net.DcmServiceException, java.rmi.RemoteException;

   public java.util.Collection subscribeGlobally( java.lang.String aet,boolean dellock )
      throws org.dcm4che.net.DcmServiceException, java.rmi.RemoteException;

   public void unsubscribeReceiveUPSEventReports( java.lang.String iuid,java.lang.String aet )
      throws org.dcm4che.net.DcmServiceException, java.rmi.RemoteException;

   public void unsubscribeGlobally( java.lang.String aet )
      throws org.dcm4che.net.DcmServiceException, java.rmi.RemoteException;

   public void suspendGlobalSubscription( java.lang.String aet )
      throws org.dcm4che.net.DcmServiceException, java.rmi.RemoteException;

}
