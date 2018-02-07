<html>
    <head>
	<title>MonaLisa repository for Alice - Edit documentation page</title>
	<link type="text/css" rel="StyleSheet" href="/style/style.css" />
    </head>
<body>
<script language="javascript" type="text/javascript" src="/js/tiny_mce/tiny_mce.js"></script>
<script language="javascript">
tinyMCE.init({
    mode: "exact",
    elements : "content",	
    theme : "advanced",
    plugins : "table,save,advhr,advimage,advlink,emotions,iespell,insertdatetime,preview,zoom,flash,searchreplace,print,contextmenu",
    theme_advanced_buttons1_add_before : "save,separator",
    theme_advanced_buttons1_add : "fontselect,fontsizeselect",
    theme_advanced_buttons2_add : "separator,insertdate,inserttime,preview,zoom,separator,forecolor,backcolor",
    theme_advanced_buttons2_add_before: "cut,copy,paste,separator,search,replace,separator",
    theme_advanced_buttons3_add_before : "tablecontrols,separator",
    theme_advanced_buttons3_add : "emotions,iespell,flash,advhr,separator,print",
    theme_advanced_toolbar_location : "top",
    theme_advanced_toolbar_align : "left",
    plugin_insertdate_dateFormat : "%Y-%m-%d",
    plugin_insertdate_timeFormat : "%H:%M:%S",
    extended_valid_elements : "a[name|href|target|title|onclick],img[class|src|border=0|alt|title|hspace|vspace|width|height|align|onmouseover|onmouseout|name],hr[class|width|size|noshade],font[face|size|color|style],span[class|align|style]"
});
</script>
<br>
<form name=editfrm action=edit.jsp method=post>
<input type=hidden name=page value="<<:page:>>">
<table width=100% border=0 cellspacing=0 cellpadding=0 height=100%>
    <tr>
	<td align=center height="350">
	    <textarea name=content id="content" style="width:570px;height:340px"><<:continut esc:>></textarea>
	</td>
    </tr>
    <tr>
	<td valign=top align="center">
	    <table border=0 cellspacing=0 cellpadding="3" width="100%" style="border: solid 1px #CCCCCC; background-color: #F0F0EE; width: 570px">
	        <tr>
		    <td align=center>
			<input type=submit name=submit value="Save" class="input_submit">
		    </td>
		</tr>
	    </table>
	</td>
    </tr>
</table>
</form>
</body>
</html>
