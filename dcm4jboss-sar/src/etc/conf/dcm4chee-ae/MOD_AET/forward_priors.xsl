<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
  <xsl:output method="xml"/>

  <!--
   The following parameters are made available by the application:
   source-aet   - AET of the Storage SCU from which the series was received
   retrieve-aet - AET of the Query Retrieve SCP from which the series can be retrieved
   year  - The current year
   month - The current month (1=Jan, 2=Feb ..)
   date  - The current day of the month
   day   - The current day of the week (0=Sun, 1=Mon ..)
   hour  - The current hour of the day
   
   These parameters may be to define rules that depend on the source or retrieve AET
   or on the current date or time.

   Also all attributes of 'destination' element in forward.xsl are available here as parameters!
   The default 'destination' attributes are:
   aet...........Destination AET
   priority......Dicom Priority of C-MOVE
   delay.........Delay of scheduled move request

   includePrior..Forward also prior studies of patient regarding this stylesheet (can have any value as hint)
   level.........If 'INSTANCE' each instance instead of series are in <priors>! Only applicable if includePrior is set.
   availability..Minimum availability of studies that are used as priors. Only applicable if includePrior is set. 
                 (default: NEARLINE)
   retrAETs......List of retrieve AETs separated with '\'. Only applicable if includePrior is set. 
                 (default: retrieveAET of stored Series, Use NONE or empty string to disable this restriction)
   modalities....List of modalities separated with '\'. Only applicable if includePrior is set. 
                 (default: Disable restriction of modalities)

   An example of the xml input:
   
<?xml version="1.0" encoding="UTF-8"?>
<forward>
    <seriesStored>
        <dataset>
            <attr tag="00081111" vr="SQ" pos="-1" len="-1">
                <item id="1" pos="94" len="-1">
                    <attr tag="00081150" vr="UI" pos="102" vm="1" len="24">1.2.840.10008.3.1.2.3.3</attr>
                    <attr tag="00081155" vr="UI" pos="134" vm="1" len="54"
                        >1.2.40.0.13.1.1.10.231.160.236.20101027154544698.32922</attr>
                    <attr tag="00404019" vr="SQ" pos="-1" len="-1"/>
                </item>
            </attr>
            <attr tag="00081115" vr="SQ" pos="-1" len="-1">
                <item id="1" pos="-1" len="-1">
                    <attr tag="00081199" vr="SQ" pos="-1" len="-1">
                        <item id="1" pos="-1" len="-1">
                            <attr tag="00080054" vr="AE" pos="-1" vm="1" len="9">DCM4CHEE</attr>
                            <attr tag="00080056" vr="CS" pos="-1" vm="1" len="6">ONLINE</attr>
                            <attr tag="00081150" vr="UI" pos="-1" vm="1" len="29"
                                >1.2.840.10008.5.1.4.1.1.88.11</attr>
                            <attr tag="00081155" vr="UI" pos="-1" vm="1" len="64"
                                >1.2.40.0.13.0.11.111.2.2010000498.16297.20101112152444.6816957.1</attr>
                        </item>
                    </attr>
                    <attr tag="0020000E" vr="UI" pos="-1" vm="1" len="62"
                        >1.2.40.0.13.0.11.111.2.2010000498.16297.20101112152444.6816957</attr>
                </item>
            </attr>
            <attr tag="0020000D" vr="UI" pos="-1" vm="1" len="54"
                >1.2.40.0.13.0.11.111.2.2010000498.16297.20101112152444</attr>
        </dataset>
    </seriesStored>
    <priors>
        <dataset>
            <attr tag="00080005" vr="CS" pos="0" vm="1" len="10">ISO_IR 100</attr>
            <attr tag="00080020" vr="DA" pos="-1" vm="1" len="8">20080814</attr>
            <attr tag="00080021" vr="DA" pos="18" vm="1" len="8">20080814</attr>
            <attr tag="00080030" vr="TM" pos="-1" vm="1" len="10">115059.000</attr>
            <attr tag="00080031" vr="TM" pos="34" vm="1" len="10">115059.000</attr>
            <attr tag="00080050" vr="SH" pos="-1" vm="1" len="16">0808111507014642</attr>
            <attr tag="00080054" vr="AE" pos="-1" vm="1" len="9">DCM4CHEE</attr>
            <attr tag="00080056" vr="CS" pos="-1" vm="1" len="6">ONLINE</attr>
            <attr tag="00080060" vr="CS" pos="52" vm="1" len="2">RF</attr>
            <attr tag="00080061" vr="CS" pos="-1" vm="1" len="2">RF</attr>
            <attr tag="00081010" vr="SH" pos="62" vm="1" len="6">SCANLX</attr>
            <attr tag="00081030" vr="LO" pos="-1" vm="1" len="60">DURCHLEUCHTUNG.GASTRO -
                INTESTINALTRAKT.VIDEOCINEMATOGRAPHIE</attr>
            <attr tag="0008103E" vr="LO" pos="76" vm="1" len="60">DURCHLEUCHTUNG.GASTRO -
                INTESTINALTRAKT.VIDEOCINEMATOGRAPHIE</attr>
            <attr tag="00081111" vr="SQ" pos="-1" len="-1">
                <item id="1" pos="156" len="-1">
                    <attr tag="00081150" vr="UI" pos="164" vm="1" len="24">1.2.840.10008.3.1.2.3.3</attr>
                    <attr tag="00081155" vr="UI" pos="196" vm="1" len="54"
                        >1.2.40.0.13.1.1.172.25.12.190.20081028094914997.32850</attr>
                </item>
            </attr>
            <attr tag="00100010" vr="PN" pos="-1" vm="1" len="10">RAAB^HILDE</attr>
            <attr tag="00100020" vr="LO" pos="-1" vm="1" len="6">18225 </attr>
            <attr tag="00100021" vr="LO" pos="-1" vm="1" len="10">MY_ISSUER </attr>
            <attr tag="00100030" vr="DA" pos="-1" vm="1" len="8">19171101</attr>
            <attr tag="00100040" vr="CS" pos="-1" vm="1" len="2">F </attr>
            <attr tag="0020000D" vr="UI" pos="-1" vm="1" len="58"
                >1.2.40.0.13.0.11.6686.2.10018945743.109595.20080811150647</attr>
            <attr tag="0020000E" vr="UI" pos="274" vm="1" len="58"
                >1.2.826.0.1.3680043.2.138.172.21.35.127.20080809.200429.74</attr>
            <attr tag="00201206" vr="IS" pos="-1" vm="1" len="2">10</attr>
            <attr tag="00201208" vr="IS" pos="-1" vm="1" len="3">209</attr>
            <attr tag="00201209" vr="IS" pos="-1" vm="1" len="2">33</attr>
            <attr tag="0032000A" vr="CS" pos="-1" vm="0" len="0"/>
            <attr tag="00400244" vr="DA" pos="340" vm="1" len="8">20080814</attr>
            <attr tag="00400245" vr="TM" pos="356" vm="1" len="10">115059.000</attr>
            <attr tag="00430010" vr="LO" pos="-1" vm="1" len="15">dcm4che/archive</attr>
            <attr tag="00431010" vr="OB" pos="-1" vm="1" len="8">0\0\0\0\0\0\1\242</attr>
            <attr tag="00431011" vr="OB" pos="-1" vm="1" len="8">0\0\0\0\0\0\2\199</attr>
            <attr tag="00431012" vr="OB" pos="-1" vm="1" len="8">0\0\0\0\0\0\8\152</attr>
            <attr tag="00431014" vr="AE" pos="-1" vm="1" len="6">DCMSND</attr>
            <attr tag="00880130" vr="SH" pos="-1" vm="0" len="0"/>
            <attr tag="00880140" vr="UI" pos="-1" vm="0" len="0"/>
        </dataset>
        ...
        <dataset>
           ...
        </dataset>
    </priors>
</forward>
  -->
  <xsl:param name="source-aet"/>
  <xsl:param name="retrieve-aet"/>
  <xsl:param name="year"/>
  <xsl:param name="month"/>
  <xsl:param name="date"/> 
  <xsl:param name="day"/>
  <xsl:param name="hour"/>

  <xsl:template match="/forward">
	<forwards>
		<xsl:apply-templates select="seriesStored"/>
		<xsl:apply-templates select="priors"/>
	</forwards>
  </xsl:template>

  <xsl:template match="seriesStored">
	<!-- Define forward of seriesStored referenced objects. Default: seriesStored is forwarded completely.
		<xsl:apply-templates select="dataset" mode="stored"/>
      -->
  </xsl:template>

  <xsl:template match="priors">
	<xsl:apply-templates select="dataset" mode="prior"/>
  </xsl:template>

  <xsl:template match="dataset" mode="prior">
	<forward>
	   <xsl:attribute name="studyIUID"><xsl:value-of select="attr[@tag='0020000D']" /></xsl:attribute>
	   <xsl:attribute name="seriesIUID"><xsl:value-of select="attr[@tag='0020000E']" /></xsl:attribute>
       <!-- Uncomment if forward.xsl includePrior level is INSTANCE! 
         <xsl:attribute name="iuid"><xsl:value-of select="attr[@tag='00080018']" /></xsl:attribute> 
         -->
      </forward>
  </xsl:template>
  <xsl:template match="dataset" mode="stored">
	<forward>
	   <xsl:attribute name="studyIUID"><xsl:value-of select="attr[@tag='0020000D']" /></xsl:attribute>
	   <xsl:attribute name="seriesIUID"><xsl:value-of select="attr[@tag='0020000E']" /></xsl:attribute>
         <!-- <xsl:attribute name="iuid"><xsl:value-of select="attr[@tag='00080018']" /></xsl:attribute> -->
      </forward>
  </xsl:template>

</xsl:stylesheet>