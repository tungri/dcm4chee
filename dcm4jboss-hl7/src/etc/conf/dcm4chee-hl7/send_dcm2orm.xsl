<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:include href="common_send.xsl"/>
    <xsl:template match="/">
         <xsl:apply-templates select="dataset"/>       
        <xsl:apply-templates select="/dicomfile/dataset"/>       
    </xsl:template>    
    <xsl:template match="dataset">
        <xsl:call-template name="MSH">
            <xsl:with-param name="msgType" select="'ORM^O01'"/>
        </xsl:call-template>
        <xsl:apply-templates select="." mode="dcm2PID" />
        <xsl:apply-templates select="." mode="dcm2PV1" />
        <xsl:apply-templates select="." mode="dcm2ORC" />
        <xsl:variable name="reqProcCode">
            <xsl:call-template name="codeOrDescr">
                <xsl:with-param name="descrTag" select="attr[@tag='00321060']"/> <!-- Requested Procedure Description -->
                <xsl:with-param name="codeTag" select="attr[@tag='00321064']"/> <!-- Requested Procedure Code Sequence -->
            </xsl:call-template>
        </xsl:variable>
        <xsl:variable name="schedProtocolCode">
            <xsl:call-template name="codeOrDescr">
                <xsl:with-param name="descrTag" select="attr[@tag='00400100']/item/attr[@tag='00400007']"/> <!-- Scheduled Procedure Step Description -->
                <xsl:with-param name="codeTag" select="attr[@tag='00400100']/item/attr[@tag='00400008']"/> <!-- Scheduled Protocol Code Sequence -->
            </xsl:call-template>
        </xsl:variable>
        <xsl:apply-templates select="." mode="dcm2OBR" >
            <xsl:with-param name="placerField1" select="attr[@tag='00080050']"/> <!-- Accession Number -->
            <xsl:with-param name="placerField2" select="attr[@tag='00401001']"/> <!-- Requested Procedure ID -->
            <xsl:with-param name="clinicalInfo" select="attr[@tag='00102000']"/> <!-- Medical Alerts -->
            <xsl:with-param name="orderingProvider" select="attr[@tag='00321032']"/> <!-- Requesting Physician -->
            <xsl:with-param name="requestedProcedureCode" select="$reqProcCode"/> <!-- Requesting Physician -->
            <xsl:with-param name="transportationMode" select="attr[@tag='00401004']"/> <!-- Patient Transport Arrangements -->
            <xsl:with-param name="diagnService" select="attr[@tag='00400100']/item/attr[@tag='00080060']"/> <!-- Modality -->
            <xsl:with-param name="fillerField1" select="attr[@tag='00400100']/item/attr[@tag='00400009']"/> <!-- Scheduled Procedure Step ID-->
            <xsl:with-param name="universalServiceId" select="$schedProtocolCode"/> <!-- Scheduled Protocol Code / Descr -->
        </xsl:apply-templates>
        <xsl:apply-templates select="." mode="dcm2ZDS" />
    </xsl:template>
</xsl:stylesheet>
