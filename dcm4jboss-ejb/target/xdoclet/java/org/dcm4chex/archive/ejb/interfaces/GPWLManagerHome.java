/*
 * Generated by XDoclet - Do not edit!
 */
package org.dcm4chex.archive.ejb.interfaces;

/**
 * Home interface for GPWLManager.
 * @xdoclet-generated at ${TODAY}
 * @copyright The XDoclet Team
 * @author XDoclet
 * @version 2.18.0-SNAPSHOT
 */
public interface GPWLManagerHome
   extends javax.ejb.EJBHome
{
   public static final String COMP_NAME="java:comp/env/ejb/GPWLManager";
   public static final String JNDI_NAME="ejb/GPWLManager";

   public org.dcm4chex.archive.ejb.interfaces.GPWLManager create()
      throws javax.ejb.CreateException,java.rmi.RemoteException;

}
