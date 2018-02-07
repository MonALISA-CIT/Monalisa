<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<!--  Test Stylesheet, do not use! -->
	
<!-- Main HTML Thingie	
-->
<xsl:template match="/">
<html>
	<head>
		<title>MonALISA</title>

		<meta http-equiv="Content-Type"
			content="text/html; charset=iso-8859-1" />

		<link rel="StyleSheet" type="text/css" href="stylesheets/style.css" title="my_css" />
		<link rel="SHORTCUT ICON" href="http://monalisa.cacr.caltech.edu/logo.ico" />
		<link rel="StyleSheet" href="stylesheets/dtree.css" type="text/css" />
		<script type="text/javascript" src="stylesheets/menu_move.js" />
		<script type="text/javascript" src="stylesheets/validate.js" />
	</head>
	<body topmargin="10" bgcolor="#ffffff">
		<table align="left" cellpadding="0" cellspacing="0" width="100%">
			<tbody>
				<tr>
					<td colspan="2">
						<table class="td_bordered" bgcolor="#ffffff" height="90" width="100%">
							<tbody>
								<tr>
									<td>
										<img src="images/space.gif" border="0" height="1" width="1" />
									</td>
								</tr>
								<tr>
									<td width="145">
										<table class="td_bordered" align="left" bgcolor="#053887" height="75" width="100%">
											<tbody>
												<tr>
													<td class="bigMonalisa" align="center" valign="top">
														<img src="images/sigla.jpg" border="0" /><br />
														<span class="white14">Welcome</span>
													</td>
												</tr>
											</tbody>
										</table>
									</td>
									<td align="center" width="100%">
										<table align="center" border="0" cellpadding="0" cellspacing="0" height="75" width="100%">
											<tbody>
												<tr>
													<td class="banner_background_left" align="right">
														<img src="images/ml_main_logo_small_left.gif" />
													</td>
													<td class="banner_background_right" align="left">
														<img src="images/ml_main_logo_small_right.gif" />
													</td>
												</tr>
											</tbody>
										</table>
									</td>
									<td width="100%">
										<img src="images/space.gif" border="0" height="1" width="1" />
									</td>
								</tr>
								<tr>
									<td>
										<img src="images/space.gif" border="0" height="1" width="1" />
									</td>
								</tr>
							</tbody>
						</table>
					</td>
				</tr>
				<tr>
					<td align="left" bgcolor="#ffffff" height="27" valign="top" width="250">
						<table border="0" cellpadding="0" cellspacing="0" width="250">
						</table>
					</td>
					<td align="left" height="27" valign="bottom" width="100%">
						<table align="left" border="0" cellpadding="0" cellspacing="0" height="20" width="600">
							<tbody>
								<tr align="center" valign="left">
									<td class="horiz_menu_opt" align="center">										
										&#160;&#160;									
										<a href="http://monalisa.cacr.caltech.edu/monalisa.htm" class="blue10a">HOME</a>
										&#160;&#160;
									</td>
									<td width="6">
										&#160;
									</td>
									<td class="horiz_menu_opt" align="center">
										&#160;&#160;
										<a href="http://monalisa.cacr.caltech.edu/monalisa__Interactive_Clients.htm" class="blue10a">CLIENTS</a>
										&#160;&#160;
									</td>
									<td width="5">
										&#160;
									</td>
									<td class="horiz_menu_opt" align="center">
										&#160;&#160;
										<a href="http://monalisa.cacr.caltech.edu/monalisa__Repositories.htm" class="blue10a">REPOSITORIES</a>
										&#160;&#160;
									</td>
									<td width="5">
										&#160;
									</td>
									<td class="horiz_menu_opt" align="center"> 										
										&#160;&#160;
										<a href="http://monalisa.cacr.caltech.edu/monalisa__Download.htm" class="blue10a">DOWNLOADS</a>
										&#160;&#160;
									</td>
									<td width="6">
										&#160;
									</td>
									<td class="horiz_menu_opt" align="center">
										&#160;&#160;
										<a href="http://monalisa.cacr.caltech.edu/monalisa__Looking_Glass.htm" class="blue10a">LOOKING&#160;GLASS</a>
										&#160;&#160;
									</td>
								</tr>
							</tbody>
						</table>
					</td>
				</tr>

				<tr align="left" valign="top">
					<td colspan="2" class="td_bordered" bgcolor="#ffffff" height="20" width="100%">
						<table border="0" cellpadding="0" cellspacing="0" width="100%">
							<tbody>
								<tr align="left" valign="top">
									<td class="right_bordered" height="100%" width="250">
										<table border="0" cellpadding="0" cellspacing="0" height="100%" width="217">
											<tbody>
												<tr>
													<td align="left" valign="top">
														<!-- put dynamic menu -->
														<table border="0" cellpadding="0" cellspacing="0" height="100%">
															<tbody>
																<tr>
																	<td height="1">
																		<img src="images/spacer.html" id="menuSpacer" name="menuSpacer" border="0" height="1" width="1" />
																	</td>
																</tr>
																<tr>
																	<td valign="top">
																		<!-- start left part === menu -->
																		<table valign="top" border="0" cellpadding="0" cellspacing="10" width="250">
																			<tbody>
																				<tr>
																					<td>
																						<div class="dtree">
																							<!-- begin menu insertion -->
																							<table class="nice" border="0" cellpadding="0" cellspacing="0">
																								<tbody>
																									<tr>
																										<td>
																											<img src="images/globe.gif" align="center"/>
																										</td>
																										<td>
																											<a href="http://monalisa.cacr.caltech.edu/monalisa.htm" title="MONitoring Agents using a Large Integrated Services Arhitecture" class="nodeSel">
																											MonALISA PathloadConfig
																											</a>
																										</td>
																									</tr>
																									<tr>
																									</tr>
																									<tr>
																										<td class="vert_line">
																											<a href="#">
																												<img src="images/join.gif" />
																											</a>
																										</td>
																										<td>
																											<table class="nice" border="0" cellpadding="0" cellspacing="0">
																													<tr>
																														<td>
																															<img src="images/page.gif" align="absmiddle" />
																														</td>
																														<td>
																															<a href="PathloadStatus" class="nodeBig">
																																Pathload Cache Status
																															</a>
																														</td>
																													</tr>
																											</table>
																										</td>
																									</tr>																																																
																									<tr>
																										<td class="vert_line">
																											<a href="#">
																												<img src="images/join.gif" />
																											</a>
																										</td>
																										<td>
																											<table class="nice" border="0" cellpadding="0" cellspacing="0">
																												<tbody>
																													<tr>
																														<td>
																															<img src="images/page.gif" align="absmiddle" />
																														</td>
																														<td>
																															<a href="PathloadSetup" class="nodeBig">
																															Pathload Setup
																															</a>
																														</td>
																													</tr>
																												</tbody>
																											</table>
																										</td>
																									</tr>
																									<tr>
																										<td class="vert_line" valign="top">
																											<a href="#">
																												<img src="images/join.gif" />
																											</a>
																										</td>
																										<td>
																											<table class="nice" border="0" cellpadding="0" cellspacing="0">
																												<tbody>
																													<tr>
																														<td>
																															<img src="images/page.gif" align="center" />
																														</td>
																														<td>
																															<a href="PathloadHistory">
																															Pathload History
																															</a>
																														</td>
																													</tr>
																												</tbody>
																											</table>
																										</td>
																									</tr>																									
<tr>
																										<td class="vert_line" valign="top">
																											<a href="#">
																												<img src="images/joinbottom.gif" />
																											</a>
																										</td>
																										<td>
																											<table class="nice" border="0" cellpadding="0" cellspacing="0">
																												<tbody>
																													<tr>
																														<td>
																															<img src="images/page.gif" align="center" />
																														</td>
																														<td>
																															<a href="doc/index.html" target="_blank">
																															PathloadConfig JavaDoc
																															</a>
																														</td>
																													</tr>
																												</tbody>
																											</table>
																										</td>
																									</tr>																																																		
																								</tbody>
																							</table>
																							<!-- end menu insertion -->
																						</div>
																					</td>
																				</tr>
																				<tr>
																					<td class="nice" align="center" valign="top">
																						Last update by:
																						<a href="mailto:Iosif.Legrand@cern.ch">cil</a>
																						on
																						<br />
																						June 21st, 2005
																					</td>
																				</tr>
																			</tbody>
																		</table>
																		<!-- end left part === menu -->
																	</td>
																</tr>
															</tbody>
														</table>
														<!-- scripts here -->
														<!--end dynamic menu -->
													</td>
												</tr>
												<tr>
													<td align="center" valign="top">
														<!-- links for search engines -->
													</td>
												</tr>
											</tbody>
										</table>
									</td>
									<td valign="top" width="100%">
										<table align="center" border="0" cellpadding="10" cellspacing="0" width="100%">
											<tbody>
												<tr>
													<td class="content" valign="top">
														<div align="justify">
														<!-- INSERT TEXT HERE -->	
															<xsl:apply-templates select="//ConfigurationManager|//SetupManager|//Logs" />
														</div> <!-- END MAIN DIV -->
													</td>
												</tr>
											</tbody>
										</table>
									</td>
								</tr>
							</tbody>
						</table>
					</td>
				</tr>
			</tbody>
		</table>
	</body>
</html>
</xsl:template>

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
	 
	<!-- Parse the SetupManager ignore rest
	-->
	<xsl:template match="SetupManager">
		<xsl:if test="count(//Message)">
			<table width="100%" align="center">		
				<xsl:for-each select="//Message">
				<xsl:sort select="type" />
					<tr>
						<xsl:choose>
							<xsl:when test="type = 'error'">
								<td><span style="COLOR: red; FONT-WEIGHT: bold"><xsl:value-of select="message"/></span></td>
							</xsl:when>
							<xsl:otherwise>
								<td><span style="COLOR: green; FONT-WEIGHT: bold"><xsl:value-of select="message"/></span></td>
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
					<td>Logger capacity:</td>
					<td width="100px"></td>
					<td  width="10px">
						<xsl:element name="input">
							<xsl:attribute name="type">text</xsl:attribute>
							<xsl:attribute name="name">loggerCapacity</xsl:attribute>
							<xsl:attribute name="value">								
								<xsl:value-of select="loggerCapacity" />
							</xsl:attribute>
						</xsl:element>						
					</td>
				</tr>
				<tr>
					<td>Logger Level:</td>
					<td width="100px"></td>
					<td  width="10px">
						<xsl:element name="select">
							<xsl:attribute name="name">loggerLevel</xsl:attribute>
							<xsl:element name="option">
								<xsl:attribute name="value">								
									<xsl:value-of select="loggerLevel" />
								</xsl:attribute>
								Current Level - <xsl:value-of select="loggerLevel" />
							</xsl:element>			
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

