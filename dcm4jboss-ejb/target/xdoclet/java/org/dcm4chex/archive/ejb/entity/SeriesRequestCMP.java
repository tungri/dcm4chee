/*
 * Generated by XDoclet - Do not edit!
 */
package org.dcm4chex.archive.ejb.entity;

/**
 * CMP layer for SeriesRequest.
 * @xdoclet-generated at ${TODAY}
 * @copyright The XDoclet Team
 * @author XDoclet
 * @version 2.18.0-SNAPSHOT
 */
public abstract class SeriesRequestCMP
   extends org.dcm4chex.archive.ejb.entity.SeriesRequestBean
   implements javax.ejb.EntityBean
{

   public void ejbLoad() 
   {
   }

   public void ejbStore() 
   {
   }

   public void ejbActivate() 
   {
   }

   public void ejbPassivate() 
   {

   }

   public void setEntityContext(javax.ejb.EntityContext ctx) 
   {
      super.setEntityContext(ctx);
   }

   public void unsetEntityContext() 
   {
      super.unsetEntityContext();
   }

   public void ejbRemove() throws javax.ejb.RemoveException
   {
      super.ejbRemove();

   }

   public abstract java.lang.Long getPk() ;

   public abstract void setPk( java.lang.Long pk ) ;

   public abstract java.lang.String getAccessionNumber() ;

   public abstract void setAccessionNumber( java.lang.String accessionNumber ) ;

   public abstract java.lang.String getStudyIuid() ;

   public abstract void setStudyIuid( java.lang.String studyIuid ) ;

   public abstract java.lang.String getRequestedProcedureId() ;

   public abstract void setRequestedProcedureId( java.lang.String requestedProcedureId ) ;

   public abstract java.lang.String getSpsId() ;

   public abstract void setSpsId( java.lang.String spsId ) ;

   public abstract java.lang.String getRequestingService() ;

   public abstract void setRequestingService( java.lang.String requestingService ) ;

   public abstract java.lang.String getRequestingPhysician() ;

   public abstract void setRequestingPhysician( java.lang.String requestingPhysician ) ;

   public abstract java.lang.String getRequestingPhysicianFamilyNameSoundex() ;

   public abstract void setRequestingPhysicianFamilyNameSoundex( java.lang.String requestingPhysicianFamilyNameSoundex ) ;

   public abstract java.lang.String getRequestingPhysicianGivenNameSoundex() ;

   public abstract void setRequestingPhysicianGivenNameSoundex( java.lang.String requestingPhysicianGivenNameSoundex ) ;

   public abstract java.lang.String getRequestingPhysicianIdeographicName() ;

   public abstract void setRequestingPhysicianIdeographicName( java.lang.String requestingPhysicianIdeographicName ) ;

   public abstract java.lang.String getRequestingPhysicianPhoneticName() ;

   public abstract void setRequestingPhysicianPhoneticName( java.lang.String requestingPhysicianPhoneticName ) ;

}
