<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet version="1.0" 
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
					 			xmlns:dc ="http://purl.org/dc/elements/1.1/"
  					 		xmlns:dct="http://purl.org/dc/terms/">

	<!-- ================================================================= -->
	
	<xsl:template match="/root">
		 <xsl:apply-templates select="simpledc"/>
	</xsl:template>

	<!-- ================================================================= -->
	
	<xsl:template match="@*|node()">
		 <xsl:copy>
			  <xsl:apply-templates select="@*|node()"/>
		 </xsl:copy>
	</xsl:template>

	<!-- ================================================================= -->
	
	<xsl:template match="simpledc">
		<simpledc xmlns:dc ="http://purl.org/dc/elements/1.1/" xmlns:dct="http://purl.org/dc/terms/">
			<xsl:apply-templates select="dc:title"/>
			<xsl:apply-templates select="dc:creator"/>
			<xsl:apply-templates select="dc:subject"/>
			<xsl:apply-templates select="dc:description"/>
			<xsl:apply-templates select="dc:publisher"/>
			<xsl:apply-templates select="dc:contributor"/>
			<xsl:apply-templates select="dc:date"/>
			<xsl:apply-templates select="dc:type"/>
			<xsl:apply-templates select="dc:format"/>
			
			<xsl:choose>
				<xsl:when test="not(dc:identifier)">
					<dc:identifier><xsl:value-of select="/root/env/uuid"/></dc:identifier>
				</xsl:when>
				<xsl:otherwise>
					<xsl:apply-templates select="dc:identifier"/>
				</xsl:otherwise>
			</xsl:choose>
			
			<xsl:apply-templates select="dc:source"/>
			<xsl:apply-templates select="dc:language"/>
			<xsl:apply-templates select="dc:relation"/>
			<xsl:apply-templates select="dc:coverage"/>
			<xsl:apply-templates select="dc:rights"/>

			<xsl:apply-templates select="dct:abstract"/>
			<dct:modified><xsl:value-of select="/root/env/changeDate"/></dct:modified>
			<xsl:apply-templates select="dct:spatial"/>
			<xsl:apply-templates select="dct:audience"/>
			<xsl:apply-templates select="dct:accrualMethod"/>
			<xsl:apply-templates select="dct:accrualPeriodicity"/>
			<xsl:apply-templates select="dct:accrualPolicy"/>
			<xsl:apply-templates select="dct:instructionalMethod"/>
			<xsl:apply-templates select="dct:provenance"/>
			<xsl:apply-templates select="dct:rightsHolder"/>
		</simpledc>
	</xsl:template>

	<!-- ================================================================= -->

	<xsl:template match="dc:identifier">
		 <xsl:copy><xsl:value-of select="/root/env/uuid"/></xsl:copy>
	</xsl:template>

	<!-- ================================================================= -->

</xsl:stylesheet>
