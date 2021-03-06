/*
 * Generated by XDoclet - Do not edit!
 */
package org.dcm4chex.archive.ejb.entity;

/**
 * CMP layer for Series.
 * @xdoclet-generated at ${TODAY}
 * @copyright The XDoclet Team
 * @author XDoclet
 * @version 2.18.0-SNAPSHOT
 */
public abstract class SeriesCMP
   extends org.dcm4chex.archive.ejb.entity.SeriesBean
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

   public abstract java.sql.Timestamp getCreatedTime() ;

   public abstract void setCreatedTime( java.sql.Timestamp createdTime ) ;

   public abstract java.sql.Timestamp getUpdatedTime() ;

   public abstract void setUpdatedTime( java.sql.Timestamp updatedTime ) ;

   public abstract java.lang.String getSeriesIuid() ;

   public abstract void setSeriesIuid( java.lang.String seriesIuid ) ;

   public abstract java.lang.String getSeriesNumber() ;

   public abstract void setSeriesNumber( java.lang.String seriesNumber ) ;

   public abstract java.lang.String getModality() ;

   public abstract void setModality( java.lang.String modality ) ;

   public abstract java.lang.String getBodyPartExamined() ;

   public abstract void setBodyPartExamined( java.lang.String bodyPartExamined ) ;

   public abstract java.lang.String getLaterality() ;

   public abstract void setLaterality( java.lang.String laterality ) ;

   public abstract java.lang.String getSeriesDescription() ;

   public abstract void setSeriesDescription( java.lang.String seriesDescription ) ;

   public abstract java.lang.String getInstitutionalDepartmentName() ;

   public abstract void setInstitutionalDepartmentName( java.lang.String institutionalDepartmentName ) ;

   public abstract java.lang.String getInstitutionName() ;

   public abstract void setInstitutionName( java.lang.String institutionName ) ;

   public abstract java.lang.String getStationName() ;

   public abstract void setStationName( java.lang.String stationName ) ;

   public abstract java.lang.String getPerformingPhysicianName() ;

   public abstract void setPerformingPhysicianName( java.lang.String performingPhysicianName ) ;

   public abstract java.lang.String getPerformingPhysicianFamilyNameSoundex() ;

   public abstract void setPerformingPhysicianFamilyNameSoundex( java.lang.String performingPhysicianFamilyNameSoundex ) ;

   public abstract java.lang.String getPerformingPhysicianGivenNameSoundex() ;

   public abstract void setPerformingPhysicianGivenNameSoundex( java.lang.String performingPhysicianGivenNameSoundex ) ;

   public abstract java.lang.String getPerformingPhysicianIdeographicName() ;

   public abstract void setPerformingPhysicianIdeographicName( java.lang.String performingPhysicianIdeographicName ) ;

   public abstract java.lang.String getPerformingPhysicianPhoneticName() ;

   public abstract void setPerformingPhysicianPhoneticName( java.lang.String performingPhysicianPhoneticName ) ;

   public abstract java.sql.Timestamp getPpsStartDateTime() ;

   public abstract void setPpsStartDateTime( java.sql.Timestamp ppsStartDateTime ) ;

   public abstract java.lang.String getPpsIuid() ;

   public abstract void setPpsIuid( java.lang.String ppsIuid ) ;

   public abstract java.lang.String getSeriesCustomAttribute1() ;

   public abstract void setSeriesCustomAttribute1( java.lang.String seriesCustomAttribute1 ) ;

   public abstract java.lang.String getSeriesCustomAttribute2() ;

   public abstract void setSeriesCustomAttribute2( java.lang.String seriesCustomAttribute2 ) ;

   public abstract java.lang.String getSeriesCustomAttribute3() ;

   public abstract void setSeriesCustomAttribute3( java.lang.String seriesCustomAttribute3 ) ;

   public abstract int getNumberOfSeriesRelatedInstances() ;

   public abstract void setNumberOfSeriesRelatedInstances( int numberOfSeriesRelatedInstances ) ;

   public abstract byte[] getEncodedAttributes() ;

   public abstract void setEncodedAttributes( byte[] encodedAttributes ) ;

   public abstract java.lang.String getFilesetIuid() ;

   public abstract void setFilesetIuid( java.lang.String filesetIuid ) ;

   public abstract java.lang.String getFilesetId() ;

   public abstract void setFilesetId( java.lang.String filesetId ) ;

   public abstract java.lang.String getSourceAET() ;

   public abstract void setSourceAET( java.lang.String sourceAET ) ;

   public abstract java.lang.String getExternalRetrieveAET() ;

   public abstract void setExternalRetrieveAET( java.lang.String externalRetrieveAET ) ;

   public abstract java.lang.String getRetrieveAETs() ;

   public abstract void setRetrieveAETs( java.lang.String retrieveAETs ) ;

   public abstract int getAvailability() ;

   public abstract void setAvailability( int availability ) ;

   public abstract int getSeriesStatus() ;

   public abstract void setSeriesStatus( int seriesStatus ) ;

}
