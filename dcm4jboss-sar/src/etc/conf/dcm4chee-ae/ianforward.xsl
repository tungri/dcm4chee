<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="xml" indent="yes"/>

<!--
  The output must contain a <destination aet="DEST" > element for each
  destination.
-->

  <xsl:template match="/">
    <!-- Send ANY IAN to these destination AETs -->
    <destination aet="DEST_1"/>
    <!-- destination aet="DEST_2"/ -->
  </xsl:template>
 
</xsl:stylesheet>