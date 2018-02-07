<script type="text/javascript" src="/js/window/prototype.js"> </script>
<script type="text/javascript" src="/js/window/window.js"> </script> 
<script type="text/javascript" src="/js/window/windowutils.js"> </script> 
<link href="/js/window/default.css" rel="stylesheet" type="text/css"></link>
<link href="/js/window/mac_os_x.css" rel="stylesheet" type="text/css"></link> 
<table cellspacing=0 cellpadding=5 class="table_content">
    <tr height=25>
	<td style="background-color: #F0F0F0;border-right: 1px solid #C0D5FF;"><a href="/admin.jsp" class="menu_link_active"><b>Site Administration</b></a></td>
	<td style="border-right: 1px solid #C0D5FF;"><a href="/admin/map_admin.jsp" class="menu_link_active"><b>Map labels Administration</b></a></td>	
	<td><a href="/admin/relations.jsp" class="menu_link_active"><b>Map Relations Administration</b></a></td>	
	<td style="border-left: 1px solid #C0D5FF;"><a href="/admin/linux.jsp" class="menu_link_active"><b>VoBox Linux Flavour</b></a></td>	    
    </tr>
</table>
<br />

<table cellspacing="0" cellpadding="5" border="0">
    <tr>
	<td valign="top"><<:services:>></td>
	<td valign="top" style="padding-left: 20px;"><<:banned:>></td>	
	<td valign="top" style="padding-left: 20px;"><<:bannedip:>></td>
    </tr>
    <tr>
	<td colspan="3"  valign="top">
	    <br clear=all>
	    <a href="admin.jsp?clear_all=true" onClick="return confirm('Are you sure ?!');" class="link">Clear all farm names</a><br>
	    <font size=-2>(no data will be lost, but all the farms will have to be rediscovered in order for them to appear on the map or in the menus again; this happens when a farm is restarted or when the repository is restarted)</font>
	</td>
    </tr>
</table>