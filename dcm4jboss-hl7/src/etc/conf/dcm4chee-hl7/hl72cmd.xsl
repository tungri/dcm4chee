<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:output method="text"/>
    <xsl:template match="/hl7">
    	<xsl:choose>
    		<xsl:when test="PID">
    			<xsl:text>hl7cmd </xsl:text>
            	   <xsl:value-of select="PID/field[3]/text()"/>
                </xsl:when>
            <xsl:otherwise>
            	<xsl:text>NONE</xsl:text>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>
</xsl:stylesheet>
