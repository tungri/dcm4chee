<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:output method="text" encoding="ISO-8859-1" />
    <xsl:param name="messageControlID"></xsl:param>
    <xsl:param name="messageDateTime"></xsl:param>
    <xsl:param name="receivingApplication"></xsl:param>
    <xsl:param name="receivingFacility"></xsl:param>
    <xsl:param name="sendingApplication"></xsl:param>
    <xsl:param name="sendingFacility"></xsl:param>
    
    <xsl:template name="MSH">
        <xsl:param name="msgType"/>
        <xsl:text>MSH|^~\&amp;|</xsl:text>
        <xsl:value-of select="$sendingApplication"/><xsl:text>|</xsl:text>
        <xsl:value-of select="$sendingFacility"/><xsl:text>|</xsl:text>
        <xsl:value-of select="$receivingApplication"/><xsl:text>|</xsl:text>
        <xsl:value-of select="$receivingFacility"/><xsl:text>|</xsl:text>
        <xsl:value-of select="$messageDateTime"/><xsl:text>||</xsl:text>
        <xsl:value-of select="$msgType"/><xsl:text>|</xsl:text>
        <xsl:value-of select="$messageControlID"/><xsl:text>|P|2.3|||AL</xsl:text>
        <xsl:text>&#xd;</xsl:text>
    </xsl:template>
    
    <xsl:template match="dataset" mode="dcm2PID">
        <xsl:text>PID|||</xsl:text>
        <xsl:value-of select="attr[@tag='00100020']" /> <!-- PatientID -->
        <xsl:if test="attr[@tag='00100021']">
            <xsl:text>^^^</xsl:text><xsl:value-of select="attr[@tag='00100021']" /> <!-- Issuer of patient ID -->
        </xsl:if>
        <xsl:text>||</xsl:text>
        <xsl:value-of select="attr[@tag='00100010']" /> <!-- PatientName  (is already in format fn^gn^mn^suffix^prefix^deg -->
        <xsl:text>|</xsl:text>
        <xsl:value-of select="attr[@tag='00101060']" /> <!-- Mothers Birth Name -->
        <xsl:text>|</xsl:text>
        <xsl:value-of select="attr[@tag='00100030']" /> <!-- Patient Birth Date -->
        <xsl:text>|</xsl:text>
        <xsl:value-of select="attr[@tag='00100040']" /> <!-- Patient Sex -->
        <xsl:text>|||||||||||||||||||||</xsl:text>
        <xsl:text>&#xd;</xsl:text>
    </xsl:template>
    
    <xsl:template match="dataset" mode="dcm2PV1">
        <xsl:param name="patientClass" select="'I'"/> <!--required! one of B	(OBSTETRICS), E (EMERGENCY), I (INPATIENT), O (OUTPATIENT), P(PREADMIT), R (RECURRING) -->
        <xsl:param name="ambulatoryStatus" select="''"/> <!-- Default is empty -->
        <xsl:param name="admitDateTime" select="''"/> <!-- Default is empty -->
        <xsl:text>PV1||</xsl:text>
        <xsl:value-of select="$patientClass" />
        <xsl:text>||||||</xsl:text> <!-- 3-7 -->
        <xsl:if test="attr[@tag='00080090']">
            <xsl:text>^</xsl:text><xsl:value-of select="attr[@tag='00080090']" /> <!-- Referring Doctor = Referring Physican Name ; CN (id^fn^gn^..)-->
        </xsl:if>
        <xsl:text>||</xsl:text> 
        <xsl:value-of select="attr[@tag='00080060']" /> <!-- Hospital Service = Modality -->
        <xsl:text>|||||</xsl:text> <!--  11-14 -->
        <xsl:value-of select="$ambulatoryStatus" />
        <xsl:text>||||</xsl:text> <!--  16-18 -->
        <xsl:if test="attr[@tag='00380010']">
            <xsl:value-of select="attr[@tag='00380010']"/>
            <xsl:if test="attr[@tag='00380011']">
                <xsl:text>^^^</xsl:text><xsl:value-of select="attr[@tag='00380011']"/> <!-- Visit Number =Admission ID, Issuer  -->
            </xsl:if>
        </xsl:if>
        <xsl:text>|||||||||||||||||||||||||</xsl:text> <!-- 20-43 -->
        <xsl:value-of select="$admitDateTime" />
        <xsl:text>|||||||</xsl:text> <!-- 46-50 -->
        <xsl:text>&#xd;</xsl:text>
    </xsl:template>

    <xsl:template match="dataset" mode="dcm2ORC">
        <xsl:param name="orderControl" select="'XO'"/> <!-- Default: XO .. Change Order Request -->
        <xsl:param name="orderStatus" /> 
        <xsl:param name="quantity_timing" />
        <xsl:param name="transactionDT" />
        <xsl:param name="orderingProvider" />
        <xsl:param name="orderEffectiveDT" />
        <xsl:param name="orderControlCodeReason" />
        <xsl:param name="enteringOrganisation" />
        <xsl:param name="enteringDevice" />
        <xsl:param name="actionBy" />
        
        <xsl:text>ORC|</xsl:text>
        <xsl:value-of select="$orderControl" />
        <xsl:text>|</xsl:text> 
        <xsl:value-of select="normalize-space(attr[@tag='00402016'])" /> <!-- Placer Order Number -->
        <xsl:text>|</xsl:text> 
        <xsl:value-of select="normalize-space(attr[@tag='00402017'])" /> <!-- Filler Order Number -->
        <xsl:text>||</xsl:text> <!-- 4  placer group number--> 
        <xsl:value-of select="$orderStatus" />
        <xsl:text>||</xsl:text> <!-- 6  response Flag --> 
        <xsl:value-of select="$quantity_timing" />
        <xsl:text>||</xsl:text> <!-- 8 Parent -->
        <xsl:value-of select="$transactionDT" />
        <xsl:text>|||</xsl:text> <!-- 10-11 -->
        <xsl:value-of select="$orderingProvider" />
        <xsl:text>|||</xsl:text> <!-- 13-14 -->
        <xsl:value-of select="$orderEffectiveDT" />
        <xsl:text>|</xsl:text> 
        <xsl:value-of select="$orderControlCodeReason" />
        <xsl:text>|</xsl:text> 
        <xsl:value-of select="$enteringOrganisation" />
        <xsl:text>|</xsl:text> 
        <xsl:value-of select="$enteringDevice" />
        <xsl:text>|</xsl:text> 
        <xsl:value-of select="$actionBy" />
        <xsl:text>&#xd;</xsl:text>
    </xsl:template>

    <xsl:template match="dataset" mode="dcm2OBR">
        <xsl:param name="universalServiceId" /> 
        <xsl:param name="priority" />
        <xsl:param name="observationDT" />
        <xsl:param name="observationEndDT" />
        <xsl:param name="dangerCode" />
        <xsl:param name="clinicalInfo" />
        <xsl:param name="orderingProvider" />
        <xsl:param name="placerField1" />
        <xsl:param name="placerField2" />
        <xsl:param name="fillerField1" />
        <xsl:param name="fillerField2" />
        <xsl:param name="statusChgDT" />
        <xsl:param name="diagnService" />
        <xsl:param name="transportationMode" />
        <xsl:param name="technician" />
        <xsl:param name="requestedProcedureCode" />
        
        <xsl:text>OBR||</xsl:text>
        <xsl:value-of select="normalize-space(attr[@tag='00402016'])" /> <!-- Placer Order Number -->
        <xsl:text>|</xsl:text> 
        <xsl:value-of select="normalize-space(attr[@tag='00402017'])" /> <!-- Filler Order Number -->
        <xsl:text>|</xsl:text>  
        <xsl:value-of select="$universalServiceId" />
        <xsl:text>|</xsl:text>  
        <xsl:value-of select="$priority" />
        <xsl:text>||</xsl:text> <!-- 6 Requested Date/Time (not used) -->
        <xsl:value-of select="$observationDT" />
        <xsl:text>|</xsl:text> 
        <xsl:value-of select="$observationEndDT" />
        <xsl:text>||||</xsl:text> <!-- 9-11 -->
        <xsl:value-of select="$dangerCode" />
        <xsl:text>|</xsl:text> 
        <xsl:value-of select="$clinicalInfo" />
        <xsl:text>|||</xsl:text> <!-- 14-15 --> 
        <xsl:value-of select="$orderingProvider" />
        <xsl:text>||</xsl:text> <!-- 17 --> 
        <xsl:value-of select="normalize-space($placerField1)" />
        <xsl:text>|</xsl:text> 
        <xsl:value-of select="normalize-space($placerField2)" />
        <xsl:text>|</xsl:text> 
        <xsl:value-of select="normalize-space($fillerField1)" />
        <xsl:text>|</xsl:text> 
        <xsl:value-of select="normalize-space($fillerField2)" />
        <xsl:text>|</xsl:text> 
        <xsl:value-of select="$statusChgDT" />
        <xsl:text>||</xsl:text> <!-- 23 --> 
        <xsl:value-of select="$diagnService" />
        <xsl:text>||||||</xsl:text> <!-- 25-29 --> 
        <xsl:value-of select="$transportationMode" />
        <xsl:text>||||</xsl:text> <!-- 31-33 --> 
        <xsl:value-of select="$technician" />
        <xsl:text>||||||</xsl:text> <!-- 35-39 --> 
        <xsl:value-of select="$requestedProcedureCode" />
        <xsl:text>&#xd;</xsl:text>
    </xsl:template>
    
    <xsl:template match="dataset" mode="dcm2ZDS">
        <xsl:param name="applicationId"/>
        <xsl:text>ZDS|</xsl:text>
        <xsl:value-of select="attr[@tag='0020000D']" /> <!-- Study Instance UID -->
        <xsl:text>^</xsl:text>
        <xsl:value-of select="$applicationId" />
        <xsl:text>^Application^DICOM</xsl:text> 
    </xsl:template>
    
    <xsl:template name="attrs2cx">
        <xsl:param name="id"/>
        <xsl:param name="issuer"/>
        <xsl:value-of select="$id"/>
        <xsl:if test="$issuer">
            <xsl:text>^^^</xsl:text><xsl:value-of select="$issuer"/>
        </xsl:if>
    </xsl:template>
    
    <xsl:template name="codeOrDescr">
        <xsl:param name="descrTag"/>
        <xsl:param name="codeTag"/>
            <xsl:choose>
            <xsl:when test="$codeTag">
                <xsl:value-of select="$codeTag/item/attr[@tag='00080100']"/><xsl:text>^</xsl:text>
                <xsl:value-of select="$codeTag/item//attr[@tag='00080104']"/><xsl:text>^</xsl:text>
                <xsl:value-of select="$codeTag/item/attr[@tag='00080102']"/>
            </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="$descrTag"/>
                </xsl:otherwise>
            </xsl:choose>
    </xsl:template>
    
</xsl:stylesheet>
