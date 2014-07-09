<?xml version="1.0" encoding="UTF-8"?>
  <!--
  The following parameters are made available by the application:
  today  - The current day in format yyyyMMdd
  year   - The current year
  month  - The current month (1=Jan, 2=Feb ..)
  date   - The current day of the month
  day    - The current day of the week (0=Sun, 1=Mon ..)
  hour   - The current hour of the day
  -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml"/>
  <xsl:param name="today"/>
  <xsl:param name="year"/>
  <xsl:param name="month"/>
  <xsl:param name="date"/> 
  <xsl:param name="day"/>
  <xsl:param name="hour"/>
  <xsl:template match="/dataset">
    <exports>
      
      <!-- Export objects of all performed procedure steps with Performed Procedure Step Start Date is current date to media -->
      <xsl:if test="attr[@tag='00400244']=$today">
         <export code="113019" designator="99DCM4CHE" meaning="For Media Export"/>
      </xsl:if>   
      
      <!-- Export objects of procedure steps with given LOINC code to
      Research Collection -->
      <xsl:variable name="item" select="attr[@tag='00081032']/item"/>
      <xsl:variable name="code" select="$item/attr[@tag='00080100']"/>
      <xsl:variable name="designator" select="$item/attr[@tag='00080102']"/>
      <xsl:if test="$code='37441-3' and $designator='LN'">
        <export code="TCE007" designator="IHERADTF"
          meaning="For Research Collection Export"
          disposition="Chest High Resolution CT w/o Contrast"/>
      </xsl:if>
     
    </exports>
  </xsl:template>

</xsl:stylesheet>