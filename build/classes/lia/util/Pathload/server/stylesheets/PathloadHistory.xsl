<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<!-- ####################################################
     ###
     ###       Pathload History Page
     ###
     ###       This parses the XML response of the Pathload
     ###	   History Servlet
     ###     
 -->
	<xsl:template match="Logs">
		<h2>Log History</h2>
		<xsl:variable name="logLevel" select="@LogLevel" />
		<xsl:for-each select="//LogRound">
			<table>
				<tr>
					<td colspan="2">
						<span style="font-weight: bold">
							Round Nr. <xsl:value-of select="@id" />
						</span>
						&#160;&#160;
						(Log Level <xsl:value-of select="$logLevel" />)
					</td>
				</tr>
				<xsl:for-each select="message">
						<tr>
							<td>
								[<xsl:value-of select="@index" />]
							</td>
							<td>
								<xsl:value-of select="@level" />
							</td>
							<td>
								<xsl:value-of select="." />
							</td>
						</tr>
				</xsl:for-each>
			</table>
		</xsl:for-each>
	</xsl:template>

</xsl:stylesheet>