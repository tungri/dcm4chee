<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml" indent="no"/>
  <xsl:template match="/dataset">
    <dataset>
      <!-- (0040,0270) SQ #-1 Scheduled Step Attribute Sequence -->
      <attr tag="00400270" vr="SQ">
        <item>
          <!-- NEVER overwrite Study Instance UID! (see issue WEB-958) -->
          <!-- <xsl:copy-of select="attr[@tag='0020000D']"/> -->
          
          <!-- Patient Height -->
          <xsl:copy-of select="attr[@tag='00101020']"/>
          <!-- Patient Weight -->
          <xsl:copy-of select="attr[@tag='00101030']"/>
          <!-- Referring Physican -->
          <xsl:copy-of select="attr[@tag='00080090']"/>
          <!-- Requesting Physican -->
          <xsl:copy-of select="attr[@tag='00321032']"/>
          <!-- Requesting Service -->
          <xsl:copy-of select="attr[@tag='00321033']"/>
          <!-- Requested Procedure Description -->
          <xsl:copy-of select="attr[@tag='00321060']"/>
          <!-- Requested Procedure Code Sequence -->
          <xsl:copy-of select="attr[@tag='00321064']"/>
          <!-- Requested Procedure ID -->
          <xsl:copy-of select="attr[@tag='00401001']"/>
          <!-- Reason for the Requested Procedure -->
          <xsl:copy-of select="attr[@tag='00401002']"/>
          <!-- Scheduled Modality Type -->
          <xsl:copy-of select="attr[@tag='00400100']/item/attr[@tag='00080060']"/>
          <!-- Scheduled Station Name -->
          <xsl:copy-of select="attr[@tag='00400100']/item/attr[@tag='00400010']"/>
          <!-- Requested Procedure Comment -->
          <xsl:copy-of select="attr[@tag='00400100']/item/attr[@tag='00401400']"/>
        </item>
      </attr>
    </dataset>
  </xsl:template>
</xsl:stylesheet>