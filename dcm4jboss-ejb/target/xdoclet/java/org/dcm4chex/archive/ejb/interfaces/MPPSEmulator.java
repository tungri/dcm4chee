/*
 * Generated by XDoclet - Do not edit!
 */
package org.dcm4chex.archive.ejb.interfaces;

/**
 * Remote interface for MPPSEmulator.
 * @xdoclet-generated at ${TODAY}
 * @copyright The XDoclet Team
 * @author XDoclet
 * @version 2.18.0-SNAPSHOT
 */
public interface MPPSEmulator
   extends javax.ejb.EJBObject
{

   public java.util.Collection getStudiesWithMissingMPPS( java.lang.String sourceAET,long delay )
      throws javax.ejb.FinderException, java.rmi.RemoteException;

   public org.dcm4che.data.Dataset[] generateMPPS( java.lang.Long studyPk,boolean ignoreReqAttrIfNoStudyAccNo )
      throws javax.ejb.FinderException, java.rmi.RemoteException;

}
