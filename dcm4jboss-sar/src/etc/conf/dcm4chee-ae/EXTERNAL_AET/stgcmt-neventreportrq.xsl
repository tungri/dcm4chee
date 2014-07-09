<?xml version="1.0" encoding="UTF-8"?>
<!-- 
Sample N-EVENT-REPORT-RQ attribute coercion for insert/overwrite of Retrieve AET. 
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml" indent="no" />
  <!-- overwritten by application with actual values -->
  <xsl:param name="calling" select="'STGCMT_SCP'" />
  <xsl:param name="called" select="'DCM4CHEE'" />
  <xsl:template match="/dataset">
    <dataset>
      <!-- (00008,0054) AE Retrieve AET -->
      <attr tag="00080054" vr="AE">RETRIEVE_AET</attr>
      <!-- (00008,1199) SQ Referenced SOP Sequence -->
      <attr tag="00081199" vr="SQ">
        <xsl:apply-templates select="attr[@tag='00081199']/item" />
      </attr>
    </dataset>
  </xsl:template>
  <xsl:template match="item">
    <item>
      <!-- (00008,0054) AE Retrieve AET -->
      <attr tag="00080054" vr="AE"/>
      <!-- (00008,1150) UI Referenced SOP Class UID -->
      <xsl:copy-of select="attr[@tag='00081150']" />
      <!-- (00008,1155) UI Referenced SOP Instance UID -->
      <xsl:copy-of select="attr[@tag='00081155']" />
    </item>
  </xsl:template>
</xsl:stylesheet>
