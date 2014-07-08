<?xml version="1.0" encoding="UTF-8"?>
<!--
 XML Format:
 <prefetch>
     <hl7>
     ...
     </hl7>
     <dataset>
     ....
     </dataset>
     ...
 </prefetch>
 -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:output method="xml" indent="yes"/>
    <xsl:include href="common.xsl"/>
    <xsl:template match="/prefetch">
        <prefetch>
            <xsl:choose>
                <xsl:when test="string(hl7/OBR[1]/field[44]/text())='TEST_KO'">
                    <xsl:apply-templates select="dataset[attr[@tag='00080060']='KO']"/>
                </xsl:when>
                <xsl:when test="string(hl7/OBR[1]/field[44]/text())='TEST_LAST3_CR'">
                    <xsl:apply-templates select="dataset[attr[@tag='00080060']='CR']">
                        <xsl:sort select="attr[@tag='00080020']" order="descending"/>
                        <xsl:sort select="attr[@tag='00080021']" order="descending"/>
                        <xsl:sort select="attr[@tag='00080030']" order="descending"/>
                        <xsl:sort select="attr[@tag='00080031']" order="descending"/>
                        <xsl:with-param name="max" select="5"/>
                        <xsl:with-param name="reason" select="'TEST_LAST3_CR'"/>
                    </xsl:apply-templates>
                </xsl:when>
                <xsl:when test="string(hl7/OBR[1]/field[44]/text())='TEST_DATERANGE'">
                    <xsl:apply-templates
                        select="dataset[attr[@tag='00080020'] &gt;20080101 and attr[@tag='00080020'] &lt; 20081212]"
                    />
                </xsl:when>
                <xsl:when test="string(hl7/OBR[1]/field[44]/text())='MAX_WITH_KEEP_STUDY'">
                    <xsl:apply-templates select="dataset" mode="keep_study">
                        <xsl:sort select="attr[@tag='0020000D']" order="ascending"/>
                        <xsl:with-param name="max" select="2"/>
                        <xsl:with-param name="reason" select="'ALL: MAX_WITH_KEEP_STUDY'"/>
                    </xsl:apply-templates>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:apply-templates select="dataset">
                        <xsl:with-param name="max" select="-1"/>
                        <xsl:with-param name="reason" select="'default (ALL)'"/>
                    </xsl:apply-templates>
                </xsl:otherwise>
            </xsl:choose>
        </prefetch>
    </xsl:template>
    
    <xsl:template match="dataset">
        <xsl:param name="max" select="999"/>
        <xsl:param name="reason"/>
        <xsl:if test="$max = -1 or position() &lt;= $max">
            <xsl:call-template name="schedule">
                <xsl:with-param name="seriesUID" select="attr[@tag='0020000E']"/>
                <xsl:with-param name="scheduleAt" select="/prefetch/hl7/ORC[1]/field[9]"/>
                <xsl:with-param name="reason" select="$reason"/>
            </xsl:call-template>
        </xsl:if>
    </xsl:template>
    
    <xsl:template match="dataset" mode="keep_study">
        <xsl:param name="max" select="999"/>
        <xsl:param name="reason"/>
        <xsl:if test="position() &lt;= $max">
            <xsl:call-template name="schedule">
                <xsl:with-param name="seriesUID" select="attr[@tag='0020000E']"/>
                <xsl:with-param name="scheduleAt" select="/prefetch/hl7/ORC[1]/field[9]"/>
                <xsl:with-param name="reason" select="$reason"/>
            </xsl:call-template>
        </xsl:if>
        <xsl:if test="position() = $max">
            <xsl:apply-templates select="following-sibling::dataset" mode="study_iuid">
                <xsl:with-param name="study_iuid" select="attr[@tag='0020000D']"/>
                <xsl:with-param name="reason" select="$reason"/>
            </xsl:apply-templates>
        </xsl:if>
    </xsl:template>
    
    <xsl:template match="dataset" mode="study_iuid">
        <xsl:param name="study_iuid" select="xxx"/>
        <xsl:param name="reason"/>
        <xsl:if test="attr[@tag='0020000D'] = $study_iuid">
            <xsl:call-template name="schedule">
                <xsl:with-param name="seriesUID" select="attr[@tag='0020000E']"/>
                <xsl:with-param name="scheduleAt" select="/prefetch/hl7/ORC[1]/field[9]"/>
                <xsl:with-param name="reason" select="$reason"/>
            </xsl:call-template>
        </xsl:if>
    </xsl:template>

    
    <xsl:template name="schedule">
        <xsl:param name="seriesUID"/>
        <xsl:param name="scheduleAt"/>
        <xsl:param name="reason"/>
        <schedule>
            <xsl:attribute name="seriesIUID">
                <xsl:value-of select="$seriesUID"/>
            </xsl:attribute>
            <xsl:if test="string-length(string($scheduleAt)) > 0">
                <xsl:attribute name="scheduleAt">
                    <xsl:value-of select="$scheduleAt"/>
                </xsl:attribute>
            </xsl:if>
            <xsl:if test="string-length(string($reason)) > 0">
                <xsl:attribute name="reason">
                    <xsl:value-of select="$reason"/>
                </xsl:attribute>
            </xsl:if>
        </schedule>
    </xsl:template>
</xsl:stylesheet>
