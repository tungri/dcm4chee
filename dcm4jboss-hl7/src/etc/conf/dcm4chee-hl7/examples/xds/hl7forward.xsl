<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" indent="no"/>
<!-- !!! WARNING !!! 
Erroneous transformations may result in unintended 
changes of original data, including wrong patient 
assignments.
-->
  <!-- 
 $Revision: 985 $ 
 $Date: 2012-04-16 17:14:59 +0200 (Mon, 16 Apr 2012) $
 $Author: awpek $ 
 $LastChangedBy: awpek $ 
 $LastChangedDate: 2012-04-16 17:14:59 +0200 (Mon, 16 Apr 2012) $
 -->

  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="/hl7/MSH/field[7]">
    <field>ADT<component>A06</component></field>
  </xsl:template>

  <xsl:template match="/hl7/PID/field[3]">
        <field><xsl:value-of select="text()" /><component/>
          <component/>
          <component><subcomponent>1.2.40.0.13.1.1.1.0.111.1.0.1</subcomponent>
            <subcomponent>ISO</subcomponent>
          </component>
        </field>
  </xsl:template>
  
</xsl:stylesheet>
