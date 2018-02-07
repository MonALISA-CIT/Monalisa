<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<!-- ####################################################
     ###
     ###       Pathload Status Page
     ###
     ###       This parses the XML response of the Pathload
     ###	   Status Servlet
     ###     
 -->	
	
	<!-- Main HTML Thingie	
	-->
	<xsl:template match="ConfigurationManager">
				<script type="text/javascript">
				
					var count = 0;
				
					function createMyTable(sNrRows) { 
						var tbody = document.getElementById("stats_tbody");
						var nrRows = parseInt(sNrRows);
						var newrow, newcell, text;
						
						for (i = 0; i &lt; nrRows; i++) {			
							newrow = document.createElement("tr");						
						   	for (j=0; j &lt; nrRows+1; j++) {
						   		newcell = document.createElement("td");
						   		if ( i+1 == j ) {
						   			text = document.createTextNode("-");
						   			newcell.appendChild(text);
						   		}
						   		newrow.appendChild(newcell);
						   	}
						   	tbody.appendChild(newrow);
						}
					}
					
					function addMasterPeer(peerName) {
						var tbody  = document.getElementById("stats_tbody");
						var tbodyRows = tbody.getElementsByTagName("tr");
						var currentRow = tbodyRows[count++];
						var currentColums = currentRow.getElementsByTagName("td");
						
						var text = document.createTextNode(peerName);
						currentColums[0].appendChild(text);
					}
					
					function addRemainingPeer(peerName) {
						var thead  = document.getElementById("stats_head");
						var theadCols = thead.getElementsByTagName("td");						
						var tbody, tbodyRows, tbodyCols, currentRow, text;
						var currentCol = 0;
						
						
						for ( i = 1; i &lt; theadCols.length ; i++) {
						   text = theadCols[i].innerHTML;							
						   if (text == peerName) {
						      currentCol = i;
						      break;
						   }
						}
						
						if (currentCol != 0) {
							tbody  = document.getElementById("stats_tbody");
							tbodyRows = tbody.getElementsByTagName("tr");
							currentRow = tbodyRows[count-1];
							tbodyCols = currentRow.getElementsByTagName("td");
							text = document.createTextNode("o");
							tbodyCols[i].appendChild(text);
						}
					}
					
					function fillRemainingTable() {
						var tbody = document.getElementById("stats_tbody");
						var tbodyRows = tbody.getElementsByTagName("tr");
						var tbodyCols, innerHTML, text;
						
						for (i = 0; i &lt; tbodyRows.length; i++) {			
							tbodyCols = tbodyRows[i].getElementsByTagName("td");
						   	for (j=0; j &lt; tbodyCols.length; j++) {
						   		innerHTML = tbodyCols[j].innerHTML;
								if (innerHTML.length == 0) {
									text = document.createTextNode("x");
									tbodyCols[j].appendChild(text);
								}
						   	}
						}
					}
				</script>
				<h2>Pathload PeerCache Status</h2>
				<xsl:apply-templates select="//peerCache" />
	</xsl:template>

	<!-- Parse the peerCache ignore rest
	-->
	<xsl:template match="peerCache">
		<table>
			<tr>
				<td>Current peers count:</td>
				<td><xsl:value-of select="count(currentPeers/peer)" /></td>
			</tr>
			<tr>
				<td>Nr. of queued peers:</td>
				<td><xsl:value-of select="count(queuedPeers/peerInfo)" /></td>
			</tr>
			<tr>
				<td>Token Status:</td>
				<td>
					<xsl:choose>
						<xsl:when test="count(//token)">
							<xsl:apply-templates select="//token" />
						</xsl:when>
						<xsl:otherwise>
							No token has been released yet!
						</xsl:otherwise>
					</xsl:choose>
				</td>
			</tr>
			<tr>
				<td>Forced Peers</td>
				<td><xsl:apply-templates select="forcedPeerContainer" /></td>
			</tr>
			<tr>
				<td>Queued Peers</td>
				<td><xsl:apply-templates select="queuedPeers" /></td>
			</tr>
			<tr>
				<td>Current Peers</td>
				<td><xsl:apply-templates select="currentPeers" /></td>
			</tr>
			<tr>
				<td>PeerCache Round <br /> Statistics</td>
				<td>
					<table id="stats_table">
						<thead id="stats_head">
							<tr>
								<td></td>
								<xsl:for-each select="//peer">
									<xsl:sort select="peerInfo/farmName" />
									<td><xsl:value-of select="peerInfo/farmName" /></td>
								</xsl:for-each> 
							</tr>
						</thead> <!-- I've added the table header -->
						<tbody id="stats_tbody">
						</tbody>
					</table>
 
					<script type="text/javascript">
						createMyTable("<xsl:value-of select="count(//peer)"/>");
						<xsl:for-each select="//peer">
						<xsl:sort select="peerInfo/farmName" />
						addMasterPeer("<xsl:value-of select="peerInfo/farmName" />");
							<xsl:for-each select="reamainigPeers//peerName">
							<xsl:sort select="." />
						addRemainingPeer("<xsl:value-of select="." />");
							</xsl:for-each>
						</xsl:for-each>
						fillRemainingTable();
					</script>		
				</td>
			</tr>
		</table>
	</xsl:template>

	<!-- Define the forcedPeerCacheContainer
	-->
	<xsl:template match="forcedPeerContainer">
		<xsl:choose>
			<xsl:when test="count(peerGroup)">
				<table>
					<tr>
						<td></td>
						<td>Hostname</td>
						<td>Ip</td>
						<td>FarmName</td>
						<td>FarmGroups</td>
					</tr>			
					<xsl:for-each select="peerGroup">
						<tr>
							<td>Src</td>
							<td><xsl:value-of select="src/peerInfo/hostname" /></td>
							<td><xsl:value-of select="src/peerInfo/ipAddress" /></td>
							<td><xsl:value-of select="src/peerInfo/farmName" /></td>
							<td>
								<xsl:for-each
									select="src/peerInfo/farmGroups">
									<xsl:value-of select="group" />
								</xsl:for-each>
							</td>
						</tr>
						<tr>
							<td>Dest</td>
							<td><xsl:value-of select="dest/peerInfo/hostname" /></td>
							<td><xsl:value-of select="dest/peerInfo/ipAddress" /></td>
							<td><xsl:value-of select="dest/peerInfo/farmName" /></td>
							<td>
								<xsl:for-each
									select="dest/peerInfo/farmGroups">
									<xsl:value-of select="group" />
								</xsl:for-each>
							</td>
						</tr>
					</xsl:for-each>			
				</table><!-- Forced Peers Table -->
			</xsl:when>
			<xsl:otherwise>
				<table>
					<tr><td>No forced peers currently available.</td></tr>
				</table>				
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>

	<!-- Define the queuedPeers
	-->
	<xsl:template match="queuedPeers">
		<table>
			<tr>
				<xsl:for-each select="peerInfo">
					<td><xsl:value-of select="hostname" /></td>
				</xsl:for-each>
			</tr>
		</table>
	</xsl:template>

	<!-- Define the currentPeers
	-->
	<xsl:template match="currentPeers">
		<table>
			<tr>
				<xsl:for-each select="peer">
					<td><xsl:value-of select="peerInfo/hostname" /></td>
				</xsl:for-each>
			</tr>
		</table>
	</xsl:template>
	
	<!-- Token Statistics
	 -->
	 <xsl:template match="token">
	 	<table>
	 		<tr>
	 			<td>Owner: <xsl:value-of select="owner/peerInfo/farmName" /></td>
	 			<td>Src: <xsl:value-of select="srcHost/peerInfo/farmName" /></td>
	 			<td>Dest: <xsl:value-of select="destHost/peerInfo/farmName" /></td>
	 		</tr>
	 		<tr>
	 			<td colspan="3">
	 				<script type="text/javascript">
	 					var d = new Date(<xsl:value-of select="lastAcessTime" />);
	 					document.write(d.toString());
	 				</script>
	 			</td>
	 		</tr>
	 	</table>
	 </xsl:template>
</xsl:stylesheet>