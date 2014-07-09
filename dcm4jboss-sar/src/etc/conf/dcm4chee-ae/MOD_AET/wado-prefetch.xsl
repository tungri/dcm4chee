<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml"/>

  <!--
   	Output format:
   		<wado-prefetches>
   			<prefetch wadourl="" [exportPath=""] />
   			...
   		</wado-prefetches>

	wadourl: WADO URL without objectUID.(will be added for every image of the series)
	exportPath: optional path for export. {0} will be replaced with SOP Instance UID.
	  
    The following parameters are made available by the application:
    source-aet   - AET of the Storage SCU from which the series was received
    retrieve-aet - AET of the Query Retrieve SCP from which the series can be retrieved
    wado-baseurl - BASE URL for WADO request (Format: http://<host>:<port>/wado?requestType=WADO
    export-path  - Base directory path to wich images are exported 
   
    These parameters may be to define rules that depend on the source or retrieve AET.
   
    An example of the parameters that are made available to this stylesheet is as follows:
    <xsl:param name="source-aet">DCMSND</xsl:param>
    <xsl:param name="retrieve-aet">DCM4CHEE</xsl:param>
  -->
  <xsl:param name="source-aet"/>
  <xsl:param name="retrieve-aet"/>
  <xsl:param name="wado-baseurl">http://localhost:8080/wado?requestType=WADO</xsl:param>
  <xsl:param name="export-path">exported</xsl:param>

  <xsl:template match="/dataset">
	<xsl:param name="study-uid" select="attr[@tag='0020000D']"/>
	<xsl:param name="series-uid" select="attr[@tag='0020000E']"/>
    <wado-prefetches>
      <!-- Prefetch images with special width and height of Series with specified Referring Phyisican -->
      <xsl:if test="attr[@tag='00080090']='Doe^John'">
        <prefetch>
		    <xsl:attribute name="wadourl">
		      <xsl:value-of select="$wado-baseurl"/>
		      <xsl:text>&amp;studyUID=</xsl:text><xsl:value-of select="$study-uid"/>
		      <xsl:text>&amp;seriesUID=</xsl:text><xsl:value-of select="$series-uid"/>
     	      <xsl:text>&amp;rows=64</xsl:text>
			  <xsl:text>&amp;columns=64</xsl:text>
	      	  <xsl:text>&amp;imageQuality=70</xsl:text>
		    </xsl:attribute>
        </prefetch>
      </xsl:if>
      <!-- Prefetch and export images witch are received from modality 'DCMSND'  -->
      <xsl:if test="$source-aet='DCMSND'">
        <prefetch>
		    <xsl:attribute name="wadourl">
		      <xsl:value-of select="$wado-baseurl"/>
		      <xsl:text>&amp;studyUID=</xsl:text><xsl:value-of select="$study-uid"/>
		      <xsl:text>&amp;seriesUID=</xsl:text><xsl:value-of select="$series-uid"/>
     	      <xsl:text>&amp;rows=256</xsl:text>
			  <xsl:text>&amp;columns=256</xsl:text>
	      	  <xsl:text>&amp;imageQuality=70</xsl:text>
		    </xsl:attribute>
		    <xsl:attribute name="exportPath">
		      <xsl:value-of select="$export-path"/>
		      <xsl:text>/</xsl:text><xsl:value-of select="$study-uid"/>
		      <xsl:text>/</xsl:text><xsl:value-of select="$series-uid"/>
		      <xsl:text>/{0}.jpg</xsl:text>
		    </xsl:attribute>
        </prefetch>
      </xsl:if>
            
    </wado-prefetches>
  </xsl:template>

</xsl:stylesheet>