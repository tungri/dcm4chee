<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="xml" indent="yes"/>
  <xsl:variable name="configured_issuer">MYISSUER</xsl:variable>
  <xsl:variable name="hl7_pid_assig_auth">
     <xsl:value-of select="string(/hl7/PID/field[3]/component[3]/text())"/>
  </xsl:variable>
  <xsl:template match="@*|node()">
     <xsl:copy>
       <xsl:apply-templates select="@*|node()"/>
     </xsl:copy>
  </xsl:template>
  <xsl:template match="/hl7/PID/field[3]/component[3]">
    <xsl:choose>
       <xsl:when test="string-length($hl7_pid_assig_auth) > 0">
        <component>
         <xsl:value-of select="$hl7_pid_assig_auth"/>
        </component>
       </xsl:when>
       <xsl:otherwise>
        <component>
        <xsl:value-of select="$configured_issuer"/>
        </component>
       </xsl:otherwise>
   </xsl:choose>
  </xsl:template>
</xsl:stylesheet>
