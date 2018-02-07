<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	version="1.0">

<!-- ####################################################
     ###
     ###       Pathload Setup Page
     ###
     ###       This parses the XML response of the Pathload
     ###	   Setup Servlet
     ###     
 -->	

	<xsl:template match="pathloadServlet">
		<xsl:apply-templates select="SetupManager" />
	</xsl:template>

	<!-- Parse the SetupManager ignore rest
	-->
	<xsl:template match="SetupManager">
		<xsl:if test="count(//Message)">
			<table width="100%">		
				<xsl:for-each select="//Message">
				<xsl:sort select="type" />
					<tr>
						<xsl:choose>
							<xsl:when test="compare(type, 'error')">							
								<td><b><xsl:value-of select="message"/></b></td>
							</xsl:when>
							<xsl:otherwise>
								<td><i><xsl:value-of select="message"/></i></td>
							</xsl:otherwise>
						</xsl:choose>
					</tr>		
				</xsl:for-each>
			</table>				
		</xsl:if>
		<form action="PathloadSetup" method="post">
			<table>
				<tr>
					<td>Peer minWaitingTime:</td>
					<td width="100px"></td>
					<td width="10px">
						<xsl:element name="input">
							<xsl:attribute name="type">text</xsl:attribute>
							<xsl:attribute name="name">minWaitingTime</xsl:attribute>
							<xsl:attribute name="value">
								<xsl:value-of select="round(number(minWaitingTime) div 1000)" />
							</xsl:attribute>
						</xsl:element>
					</td>
				</tr>
				<tr>
					<td>Peer maxAgingTime:</td>
					<td width="100px"></td>				
					<td width="10px">
						<xsl:element name="input">
							<xsl:attribute name="type">text</xsl:attribute>
							<xsl:attribute name="name">maxAgingTime</xsl:attribute>
							<xsl:attribute name="value">
								<xsl:value-of select="round(number(maxAgingTime) div 1000)" />
							</xsl:attribute>
						</xsl:element>
					</td>
				</tr>
				<tr>
					<td>Peer maxDeadPeerCount:</td>
					<td width="100px"></td>				
					<td width="10px">
						<xsl:element name="input">
							<xsl:attribute name="type">text</xsl:attribute>
							<xsl:attribute name="name">maxDeadPeerCount</xsl:attribute>
							<xsl:attribute name="value">
								<xsl:value-of select="maxDeadPeerCount" />
							</xsl:attribute>
						</xsl:element>
					</td>
				</tr>
				<tr>
					<td>Token maxTokenAgingTime:</td>
					<td width="100px"></td>				
					<td width="10px">
						<xsl:element name="input">
							<xsl:attribute name="type">text</xsl:attribute>
							<xsl:attribute name="name">maxTokenAgingTime</xsl:attribute>
							<xsl:attribute name="value">								
								<xsl:value-of select="round(number(maxTokenAgingTime) div 1000)" />
							</xsl:attribute>
						</xsl:element>
					</td>
				</tr>						
				<tr>
					<td></td>
					<td></td>
					<td width="10px"><input type="submit" value="Submit" /></td>
				</tr>
			</table>
		</form>
	</xsl:template>
</xsl:stylesheet>