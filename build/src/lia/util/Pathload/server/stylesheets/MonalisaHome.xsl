<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
	
<!-- ####################################################
     ###
     ###       Main Monalisa Config Page
     ###
     ###       This resembles the main monalisa HTML framework
     ###       Servlet content is mainly written at line 307
     ###	   Left menu starts at line 158
     ###     
 -->	
	
<xsl:import href="PathloadStatus.xsl"/>
<xsl:import href="PathloadSetup.xsl"/>
<xsl:import href="PathloadHistory.xsl"/>
	
<!-- Main HTML Thingie	
-->
<xsl:template match="/">
<html>
	<head>
		<title>MonALISA</title>

		<meta http-equiv="Content-Type"
			content="text/html; charset=iso-8859-1" />

		<link rel="StyleSheet" type="text/css" href="stylesheets/style.css" title="my_css" />
		<link rel="SHORTCUT ICON" href="http://monalisa.cacr,caltech.edu/logo.ico" />
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
										<a href="http://monalisa.cacr,caltech.edu/monalisa.htm" class="blue10a">HOME</a>
										&#160;&#160;
									</td>
									<td width="6">
										&#160;
									</td>
									<td class="horiz_menu_opt" align="center">
										&#160;&#160;
										<a href="http://monalisa.cacr,caltech.edu/monalisa__Interactive_Clients.htm" class="blue10a">CLIENTS</a>
										&#160;&#160;
									</td>
									<td width="5">
										&#160;
									</td>
									<td class="horiz_menu_opt" align="center">
										&#160;&#160;
										<a href="http://monalisa.cacr,caltech.edu/monalisa__Repositories.htm" class="blue10a">REPOSITORIES</a>
										&#160;&#160;
									</td>
									<td width="5">
										&#160;
									</td>
									<td class="horiz_menu_opt" align="center"> 										
										&#160;&#160;
										<a href="http://monalisa.cacr,caltech.edu/monalisa__Download.htm" class="blue10a">DOWNLOADS</a>
										&#160;&#160;
									</td>
									<td width="6">
										&#160;
									</td>
									<td class="horiz_menu_opt" align="center">
										&#160;&#160;
										<a href="http://monalisa.cacr,caltech.edu/monalisa__Looking_Glass.htm" class="blue10a">LOOKING&#160;GLASS</a>
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
																											<a href="http://monalisa.cacr,caltech.edu/monalisa.htm" title="MONitoring Agents using a Large Integrated Services Arhitecture" class="nodeSel">
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
</xsl:stylesheet>