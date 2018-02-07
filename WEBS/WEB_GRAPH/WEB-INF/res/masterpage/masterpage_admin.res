<html>
<head>
<script type="text/javascript" src="/overlib/overlib.js"></script>
<title><<:title esc:>> - Monalisa Administration Page</title>
<link rel="stylesheet" href="/js/grid/tabber.css" TYPE="text/css" MEDIA="screen">
<link rel="stylesheet" href="/style/style.css" TYPE="text/css" MEDIA="screen">
<link rel="stylesheet" href="/style/admin.css" TYPE="text/css" MEDIA="screen">
<LINK REL="SHORTCUT ICON" HREF="/favicon.gif" type="image/gif">
<LINK REL="ICON" HREF="/favicon.gif" type="image/gif">

<link type="text/css" rel="StyleSheet" href="/css/bluecurve/bluecurve.css" />

<script type="text/javascript" src="/js/window/prototype.js"> </script>
<script type="text/javascript" src="/js/scriptaculous/scriptaculous.js"></script>

<script type="text/javascript" src="/js/window/window.js"> </script> 
<script type="text/javascript" src="/js/window/windowutils.js"> </script> 

<script language=javascript src="/js/common.js"></script>
<script language=javascript src="/js/colors.js"></script>

<script src="/js/sorttable.js"></script>

		
<!-- Add this to have a specific theme--> 
<link href="/js/window/default.css" rel="stylesheet" type="text/css"></link>
<link href="/js/window/mac_os_x.css" rel="stylesheet" type="text/css"></link> 

<link href="/js/colors/plugin.css" rel="stylesheet" type="text/css"></link> 
</head>
<body style="padding-left: 20px; background-color: #F0F0F0"">
<table width="100%" cellspacing="0" cellpadding="0" border="0" bgcolor="#F0F0F0" id="test">
    <tr>
	<td>
	    <table width="100%" cellspacing="0" cellpadding="0" border="0">
		<tr bgcolor="#FFFFFF">
		    <td align="left" width="207"><a target="_blank" href="#"><img src="/images/logo_left.jpg" border="0" with="124" height="74"></a></td>
		    <td align="center" style="font-family: Tahoma, Verdana, Arial; font-size: 25px; color: #0A92D0; font-weight: bold">MonALISA Repository</td>
		    <td align="right" width="207"><a target="_blank" href="http://monalisa.caltech.edu/"><img src="/images/mona.gif" border="0"></a></td>		    
		</tr>
		<tr>
		    <td height="4" bgcolor="#9FBCD1" colspan="3"><img height="1" width="1"></td>
		</tr>
		<tr>
		    <td height="2" bgcolor="#DFFFFF" colspan="3"><img height="1" width="1"></td>
		</tr>					
		<tr>
		    <td height="4" bgcolor="#FFFFFF" colspan="3"><img height="1" width="1"></td>
		</tr>
		<tr>
		    <td height="25" bgcolor="#E7EBF1" colspan="3" style="padding-left: 20px" class="text" valign="middle">
	    		<table cellspacing="0" cellpadding="3" width=100%>
	    		    <tr>
		    		<td nowrap align="center" valign="middle"><a href="/admin.jsp" class="menu_link<<:class_administration esc:>>">ML services</a></td>
		    		<td nowrap width="3" align="center" valign="middle"><img src="/images/separator_menu.gif" width="1"></td>
    		    		<td nowrap align="center" valign="middle"><a href="/abping?function=ColorSitesListNew" class="menu_link<<:class_farm esc:>>">Series colors</a></td>
    		    		<td nowrap width="3" align="center" valign="middle"><img src="/images/separator_menu.gif" width="1"></td>
		    		<td nowrap><a href="/services.jsp" class="menu_link<<:class_siteservices esc:>>">Site services</a></td>
    		    		<td nowrap width="3" align="center" valign="middle"><img src="/images/separator_menu.gif" width="1"></td>
		    		<td nowrap><a href="/lpm/lpm_manager.jsp" class="menu_link<<:class_lpm esc:>>">LPM</a></td>
		    		<td nowrap width="3" align="center" valign="middle"><img src="/images/separator_menu.gif" width="1"></td>
		    		<td nowrap><a href="/admin/job_types.jsp" class="menu_link<<:class_jobtypes esc:>>"><b>Job Types</b></a></td>
		    		<td nowrap width="3" align="center" valign="middle"><img src="/images/separator_menu.gif" width="1"></td>
				<td nowrap><a href="/admin/selist.jsp" class="menu_link<<:class_selist esc:>>"><b>SE list</b></a></td>
		    		<td nowrap width="3" align="center" valign="middle"><img src="/images/separator_menu.gif" width="1"></td>
		    		<td nowrap><a href="/admin/ltm.jsp" class="menu_link<<:class_ltm esc:>>">LTM</a></td>
		    		<td nowrap width="3" align="center" valign="middle"><img src="/images/separator_menu.gif" width="1"></td>
		    		<td nowrap><a href="/pledged_new.jsp" class="menu_link<<:class_pledged esc:>>">Sites grouping</a></td>
		    		<td nowrap width="3" align="center" valign="middle"><img src="/images/separator_menu.gif" width="1"></td>
		    		<td nowrap><a href="/pledged_future.jsp" class="menu_link<<:class_pledged_future esc:>>">Pledged resources</a></td>
		    		<td nowrap width="3" align="center" valign="middle"><img src="/images/separator_menu.gif" width="1"></td>
		    		<td nowrap><a href="/admin/packagemanager.jsp" class="menu_link<<:class_packagemanager esc:>>">AliEn Packages</a></td>
		    		<td nowrap width="3" align="center" valign="middle"><img src="/images/separator_menu.gif" width="1"></td>
		    		<td nowrap><a href="/admin/plugin.jsp" class="menu_link<<:class_plugin esc:>>">FF Plugin</a></td>
		    		<td nowrap width="3" align="center" valign="middle"><img src="/images/separator_menu.gif" width="1"></td>
		    		<td nowrap><a href="/dump_cache.jsp" class="menu_link<<:class_dump_cache esc:>>">Last values dump</a></td>
		    		<td width=100% align=right><a href="/" class="menu_link">Back</a></td>
			    </tr>
			</table>
		    </td>
		</tr>	
		<tr>
		    <td height="2" bgcolor="#9FBCD1" colspan="3"><img height="1" width="1"></td>
		</tr>							
		<tr>
		    <td height="2" bgcolor="#DFFFFF" colspan="3"><img height="1" width="1"></td>
		</tr>							
	    </table>
	</td>
    </tr>
    <tr><td height="10"></td></tr>
    <tr>
        <td bgcolor="#FFFFFF" style="padding: 10px;" height="500" valign="top">
    	    <<:continut:>>
    	</td>
    </tr>
</table>	
</body>
</html>
