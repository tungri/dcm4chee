<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml"/>

  <!--
   The following parameters are made available by the application:
   source-aet       - AET of the Storage SCU from which the series was received
   retrieve-aet     - AET of the Query Retrieve SCP from which the series can be retrieved
   ext-retrieve-aet - external AET (e.g. central archive) of the Query Retrieve SCP from which the series can be retrieved
   archived         - All referenced instances are archived (e.g. HSM)
   year  - The current year
   month - The current month (1=Jan, 2=Feb ..)
   date  - The current day of the month
   day   - The current day of the week (0=Sun, 1=Mon ..)
   hour  - The current hour of the day
   
   These parameters may be to define rules that depend on the source or retrieve AET
   or on the current date or time.
   
   An example of the parameters that are made available to this stylesheet is as follows:
   <xsl:param name="source-aet">DCMSND</xsl:param>
   <xsl:param name="retrieve-aet">DCM4CHEE</xsl:param>
   <xsl:param name="ext-retrieve-aet">CENTRAL</xsl:param>
   <xsl:param name="archived">true</xsl:param>
   <xsl:param name="month">4</xsl:param>
   <xsl:param name="date">30</xsl:param> 
   <xsl:param name="day">1</xsl:param>
   <xsl:param name="hour">15</xsl:param>
  -->
  <xsl:param name="source-aet"/>
  <xsl:param name="retrieve-aet"/>
  <xsl:param name="ext-retrieve-aet"/>
  <xsl:param name="archived"/>
  <xsl:param name="year"/>
  <xsl:param name="month"/>
  <xsl:param name="date"/> 
  <xsl:param name="day"/>
  <xsl:param name="hour"/>

  <xsl:template match="/dataset">
    <destinations>
      <!-- Forward all Series to LONG_TERM outside business hours (7-19) after one week -->
      <destination aet="LONG_TERM" delay="1w!7-19"/>

      <!-- Forward Series with specified Referring Phyisican with low priority
        to PHYSICAN_DOE  after 3 days -->
      <xsl:if test="attr[@tag='00080090']='Doe^John'">
        <destination aet="PHYSICAN_DOE" priority="low" delay="3d"/>
      </xsl:if>
      
      <!-- Forward Magnetic Resonance Series with high priority 
        to MR_WORKSTATION immediately -->
      <xsl:if test="attr[@tag='00080060']='MR'">
        <destination aet="MR_WORKSTATION" priority="high"/>
      </xsl:if>
      
      <!-- Forward Series requested by Neuro Surgery to NEURO_SURGERY immediately -->
      <xsl:if test="attr[tag='00400275']/item/attr[@tag='00321033']='Neuro Surgery'">
        <destination aet="NEURO_SURGERY"/>
      </xsl:if>
      
      <!-- Forward Series with Requested Procedure Code '12345' to FWD_PRIOR with prior studies/series selected by forward_priors.xsl -->
      <xsl:if test="attr[tag='00400275']/item/attr[@tag=00321064]='12345'">
	      <destination aet="FWD_PRIOR" includePrior="true"/> 
	      <!-- Attributes of destination element are available in forward_priors.xsl as xsl parameters!
	        Default parameters for including prior studies:
   				includePrior..Forward also prior studies of patient regarding forward-priors.xsl (can have any value as hint)
   				level.........Use 'INSTANCE' to get header attributes for each prior instance instead of series.
   				              (add SOP Instance UID (0008,0018) in forward-priors.xsl to get C-MOVE on instance level)
   				notOlderThan..Use only studies that are created within given intervall (e.g.: 12m ;d..days, w..weeks, m..months)
   				availability..Worst availability of studies that are used as priors. (default: NEARLINE)
   				retrAETs......List of retrieve AETs separated with '\'. (default: retrieveAET of stored Series; NONE will disable this restriction)
                modalities....List of modalities separated with '\'. Only applicable if includePrior is set. 
                              (default: Disable restriction of modalities)
	       -->
      </xsl:if>
      
      <!-- Forward to CENTRAL only if external RetrieveAET is not CENTRAL
      <xsl:if test="$ext-retrieve-aet!='CENTRAL'">
        <destination aet="CENTRAL" priority="high"/>
      </xsl:if>
       -->
      
    </destinations>
  </xsl:template>

</xsl:stylesheet>