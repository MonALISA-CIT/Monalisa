<script type="text/javascript">
    var dAltHidden = objById('d_alternate_hidden');
    
    bDivFurat = true;
</script>

<table border=0 cellspacing=0 cellpadding=0>
<tr>
<td align=center>
<form action="display" method="post" name="form1">
    <input type=hidden name="page" value="<<:page esc:>>">
    <<:extra_fields:>>
    <table width="790" cellspacing="0" cellpadding="0" border="0" class="text">
	<tr>
		<td height="13" background="/img/dynamic/header.jpg"><img width=1 height=1></td>
	</tr>
	<tr>
		<td>
			<table width="100%" cellspacing="0" cellpadding="0" border="0">
				<tr>
					<td width="90" class="menu"><span onclick="switchDiv('div_series', true, 0.3);"><a accesskey="s" onclick="switchDiv('div_series', true, 0.3);" href="javascript:void(0);" class="menu_link"><b><u>S</u>eries</b> <img id="div_series_img" src="/img/dynamic/plus.jpg" width="9" height="9" border="0"></a></span></td>
					<td width="90" class="menu"><span onclick="switchDiv('div_options', true, 0.3);"><a accesskey="o" onclick="switchDiv('div_options', true, 0.3);" href="javascript:void(0);" class="menu_link"><b><u>O</u>ptions</b> <img id="div_options_img" src="/img/dynamic/plus.jpg" width="9" height="9" border="0"></a></span></td>
					<script type="text/javascript">
					    if (dAltHidden)
						document.write('<td width="130" class="menu"><span onclick="switchDiv(\'div_alternatives\', true, 0.3);"><a accesskey="a" onclick="switchDiv(\'div_alternatives\', true, 0.3);" href="javascript:void(0);" class="menu_link"><b><u>A</u>lternative Views</b> <img id="div_alternatives_img" src="/img/dynamic/plus.jpg" width="9" height="9" border="0"></a></span></td>');
					</script>
					<td class="menu" style="border-right: 0px;">&nbsp;</td>
				</tr>
			</table>
		</td>
	</tr>
	
	<tr>
	    <td style="border-bottom: solid 1px #697C8B; padding-bottom: 5px; font-family: Verdana, Arial" bgcolor="#F0F0F0">
		<table width=100% cellspacing="0" cellpadding="0">
		    <tr>
			<td><div id="div_alternatives" style="display: none; padding: 0"></div>
			    <script type="text/javascript">
				if (dAltHidden)
				    objById('div_alternatives').innerHTML = dAltHidden.innerHTML;
			    </script>
			</td>
		    </tr>
		    <tr>
			<td>
			    <div id="div_series" style="display: none"><div>
				<table border=0 cellspacing=0 cellpadding=3 width=100% style="border-bottom: 2px solid #FFFFFF;">
				    <tr>
					<td align=right style="padding:0px">
					    (<a class="link" href="JavaScript:checkall();" class="link">select all</a> | <a class="link" href="JavaScript:uncheckall();" class="link">unselect all</a>)
					</td>
				    </tr>
				    <tr>
					<td align=left class="text">
    					    <<:continut:>>
					    <<:com_separate_start:>><hr size=2 color=white noshade><<:separate:>><<:com_separate_end:>>
					</td>
				    </tr>
		    		</table>
			    </div></div>
			</td>
		    </tr>
		    <tr>	
			<td>
		    <div id="div_options" style="display: none;"><div>
			<table border=0 cellspacing=0 cellpadding=3 width=100% class="text" style="border-bottom: 2px solid #FFFFFF">
			    <tr>
				<<:options:>>

				<td align=right nowrap>
				    Image size: 
				    <select name="imgsize" onChange="JavaScript:modify();" class="input_select">
				    </select>
				    <script language=JavaScript>
				        displayImgResOptions(document.form1.imgsize);
				    </script>
				</td>
			    </tr>
			</table>
		    </div><div>
		</td>
	</tr>
	<tr>
	    <td align=right valign=bottom width=100%>
		<input type=submit name="submit_plot" value="Plot" class="input_submit">
	    </td>
	</tr>
    </table>
    </td></tr></table>

    <<:extra:>>
    <a name="jump_here"></a>
    
    <table border=0 cellspacing=0 cellpadding=0 width=790>
	<tr>
	    <td align=center><<:extra_link:>></td>
	    <td align=right><a href="javascript:void(0);" onClick="JavaScript:window.open('/doc/index.jsp?page=<<:page enc:>>', 'docwindow', 'toolbar=0,width=650,height=430,scrollbars=1,resizable=1,titlebar=1'); return false;" class="link" style="cursor:help">What is this about?</a></td>
	</tr>
    </table>
</form>
</td>
</tr>
<tr>
<td>
<table border=0 cellspacing=0 cellpadding=0 width=100%>
<tr>
    <td align=center>
	<table border=0 cellspacing=0 cellpadding=0>
	    <tr>
    		<td valign=top align=center>
        	    <<:map:>><img src="display?image=<<:image enc:>>" usemap="#<<:image esc:>>" border=0>
    		</td>
    	    </tr>
	</table>
	<br clear=all>
    </td>
</tr>
<tr>
    <td align=left>
	<font style="font-family:Helvetica;font-size:12px"><<:description:>></font>
    </td>
</tr>
</table>
</td>
</tr>
</table>
<script type="text/javascript">
    checkDivs(['div_alternatives', 'div_series', 'div_options', 'div_stats']);
</script>
