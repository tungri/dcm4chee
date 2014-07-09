<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  version="1.0">
  <xsl:output method="xml" indent="yes" />

  <xsl:param name="date">20100804</xsl:param>
  <xsl:param name="time">093000.000</xsl:param>

  <xsl:template match="/dataset">
    <dataset>
      <!-- Private Worklist Item Sequence (0043,0020) -->
      <attr tag="00430020" vr="SQ">
        <xsl:if test="normalize-space(attr[@tag=00400252])='COMPLETED'">
          <xsl:call-template name="wkitem" />
        </xsl:if>
      </attr>
    </dataset>
  </xsl:template>

  <xsl:template name="wkitem">
    <item>
      <!-- Specific Character Set -->
      <xsl:copy-of select="attr[@tag='00080005']" />

      <!-- SOP Class UID -->
      <attr tag="00080016" vr="UI">1.2.840.10008.5.1.4.34.4.1</attr>

      <!-- SOP Instance UID (0008,0018) will be created by the application -->

      <!-- Transaction UID-->
      <attr tag="00081195" vr="UI" />

      <!-- Scheduled Procedure Step Priority -->
      <attr tag="00741200" vr="CS">MEDIUM</attr>

      <!-- Procedure Step Label -->
      <attr tag="00741204" vr="LO">Sample Procedure Step Label</attr>

      <!-- Worklist Label -->
      <attr tag="00741202" vr="LO">Sample Worklist Label</attr>

      <!-- Scheduled Processing Parameters Sequence -->
      <attr tag="00741210" vr="SQ" />

      <!-- Scheduled Processing Applications Code Sequence -->
      <attr tag="00404004" vr="SQ" />

      <!-- Scheduled Station Name Code Sequence -->
      <attr tag="00404025" vr="SQ" />

      <!-- Scheduled Station Class Code Sequence -->
      <attr tag="00404026" vr="SQ" />

      <!-- Scheduled Station Geographic Location Code Sequence -->
      <attr tag="00404027" vr="SQ" />

      <!-- Scheduled Human Performers Sequence -->
      <attr tag="00404034" vr="SQ" />

      <!-- Scheduled Procedure Step Start Date and Time -->
      <attr tag="00404005" vr="DT">
        <xsl:value-of select="$date" />
        <xsl:value-of select="$time" />
      </attr>

      <!-- Scheduled Workitem Code Sequence -->
      <attr tag="00404018" vr="SQ">
        <item>

          <!-- Code Value -->
          <attr tag="00080100" vr="SH">110005</attr>

          <!-- Coding Scheme Designator -->
          <attr tag="00080102" vr="SH">DCM</attr>

          <!-- Code Meaning -->
          <attr tag="00080104" vr="LO">Interpretation</attr>

        </item>
      </attr>

      <!-- Comments on the Scheduled Procedure Step -->
      <attr tag="00400400" vr="LT" />

      <!-- Input Availability Flag
      <attr tag="00404020" vr="CS">COMPLETE</attr>
      -->

      <xsl:variable name="studyiuid">
        <xsl:copy-of select="attr[@tag='00400270']/item/attr[@tag='0020000D']" />
      </xsl:variable>

      <!-- Input Availability Flag -->
      <attr tag="00404020" vr="CS">COMPLETE</attr>

      <!-- Input Information Sequence -->
      <attr tag="00404021" vr="SQ">
        <xsl:apply-templates select="attr[@tag='00400340']/item"
          mode="refseries">
          <xsl:with-param name="studyiuid" select="$studyiuid" />
        </xsl:apply-templates>
      </attr>

      <!-- Study Instance UID -->
      <attr tag="0020000D" vr="UI">
        <xsl:value-of select="$studyiuid" />
      </attr>

      <!--
        Patient's Name (0010,0010) will be supplemented from Patient
        Record in DB
      -->

      <!-- Patient ID -->
      <xsl:copy-of select="attr[@tag='00100020']" />

      <!-- Issuer of Patient ID -->
      <xsl:copy-of select="attr[@tag='00100021']" />

      <!--
        Patient's Birth Date (0010, 0030) will be supplemented from
        Patient Record in DB
      -->

      <!--
        Patient's Sex (0010, 0040) will be supplemented from Patient
        Record in DB
      -->

      <!-- Admission ID -->
      <attr tag="00380010" vr="LO" />

      <!-- Issuer of Admission ID Sequence -->
      <attr tag="00380014" vr="SQ" />

      <!-- Admitting Diagnoses Description -->
      <attr tag="00081080" vr="LO" />

      <!-- Admitting Diagnoses Code Sequence -->
      <attr tag="00081084" vr="SQ" />

      <!-- Referenced Request Sequence -->
      <attr tag="0040A370" vr="SQ">
        <xsl:apply-templates
          select="attr[@tag='00400270']/item[string(attr[@tag='00401001'])]"
          mode="request" />
      </attr>

      <!-- Related Procedure Step Sequence -->
      <attr tag="00741220" vr="SQ" />

      <!-- Unified Procedure Step State -->
      <attr tag="00741000" vr="CS">SCHEDULED</attr>

      <!-- Unified Procedure Step Progress Information Sequence  -->
      <attr tag="00741002" vr="SQ" />

      <!-- UPS Performed Procedure Sequence -->
      <attr tag="00741216" vr="SQ" />
    </item>
  </xsl:template>

  <xsl:template match="item" mode="request">
    <xsl:variable name="rpid">
      <xsl:value-of select="string(attr[@tag='00401001'])" />
    </xsl:variable>
    <xsl:if test="not(preceding-sibling::*[attr[@tag=00401001]=$rpid])">
      <item>
        <!-- Accession Number -->
        <xsl:copy-of select="attr[@tag='00080050']" />
        <!-- Referenced Study Sequence -->
        <xsl:copy-of select="attr[@tag='00081110']" />
        <!-- Study Instance UID -->
        <xsl:copy-of select="attr[@tag='0020000D']" />
        <!-- Requesting Physician -->
        <xsl:copy-of select="attr[@tag='00321032']" />
        <!-- Requesting Service -->
        <xsl:copy-of select="attr[@tag='00321033']" />
        <!-- Requested Procedure Description -->
        <xsl:copy-of select="attr[@tag='00321060']" />
        <!-- Requested Procedure Code Sequence -->
        <xsl:copy-of select="attr[@tag='00321064']" />
        <!-- Requested Procedure ID -->
        <xsl:copy-of select="attr[@tag='00401001']" />
        <!-- Placer Order Number/Imaging Service Request -->
        <xsl:copy-of select="attr[@tag='00402016']" />
        <!-- Filler Order Number/Imaging Service Request -->
        <xsl:copy-of select="attr[@tag='00402017']" />
      </item>
    </xsl:if>
  </xsl:template>

  <xsl:template match="item" mode="refseries">
    <xsl:param name="studyiuid"/>
    <item>
      <!-- Study Instance UID -->
      <attr tag="0020000D" vr="UI">
        <xsl:value-of select="$studyiuid" />
      </attr>
      <!-- Series Instance UID -->
      <attr tag="0020000E" vr="UI">
        <xsl:value-of select="attr[@tag='0020000E']" />
      </attr>
      <!-- Retrieve AE Title -->
      <attr tag="00080054" vr="AE">
        <xsl:value-of select="attr[@tag='00080054']" />
      </attr>
      <!-- Retrieve Location UID -->
      <attr tag="0040E011" vr="UI"/>
      <!-- Retrieve URI -->
      <attr tag="0040E010" vr="UT"/>
      <!-- Storage Media File-Set ID -->
      <attr tag="00880130" vr="SH"/>
      <!-- Storage Media File-Set UID -->
      <attr tag="00880140" vr="UI"/>
      <!-- Referenced SOP Sequence -->
      <attr tag="00081199" vr="SQ">
        <xsl:apply-templates select="attr[@tag='00081140']/item"
          mode="refsop" />
        <xsl:apply-templates select="attr[@tag='00400220']/item"
          mode="refsop" />
      </attr>
    </item>
  </xsl:template>
  <xsl:template match="item" mode="refsop">
    <item>
      <!-- Referenced SOP Class UID -->
      <xsl:copy-of select="attr[@tag='00081150']" />
      <!-- Referenced SOP Instance UID -->
      <xsl:copy-of select="attr[@tag='00081155']" />
    </item>
  </xsl:template>
</xsl:stylesheet>
