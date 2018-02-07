<script type="text/javascript">
    var dAltHidden = objById('d_alternate_hidden');
    
    bDivFurat = true;
</script>
<script type="text/javascript" src="/js/htmlsuite/calendar.js"> </script> 
<table border=0 cellspacing=0 cellpadding=0>
<tr>
<td align=center>
<form action="display" method="post" name="form1">
    <input type=hidden name="page" value="<<:page esc:>>">
    <<:extra_fields:>>
    <table width="790" cellspacing="0" cellpadding="0" border="0" class="text">
<!--	<tr>
		<td height="13" background="/img/dynamic/header.jpg"><img width=1 height=1></td>
	</tr>
-->	
	<tr>
	    <td height="3" bgcolor="#F0F0F0" style="border-top: solid 1px #B5B5BD"><img height="1" width="1"></td>
	</tr>
	<tr>
	    <td height="2" bgcolor="#FFFFFF"><img height="1" width="1"></td>
	</tr>
	<tr>
		<td>
			<table width="100%" cellspacing="0" cellpadding="0" border="0">
				<tr>
					<td width="90" class="hist_menu"><span onclick="switchDiv('div_series', true, 0.3);"><a accesskey="s" onclick="switchDiv('div_series', true, 0.3);" href="javascript:void(0);" class="menu_link"><b><u>S</u>eries</b> <img id="div_series_img" src="/img/dynamic/plus.jpg" width="9" height="9" border="0"></a></span></td>
					<td width="90" class="hist_menu"><span onclick="switchDiv('div_options', true, 0.3);"><a accesskey="o" onclick="switchDiv('div_options', true, 0.3);" href="javascript:void(0);" class="menu_link"><b><u>O</u>ptions</b> <img id="div_options_img" src="/img/dynamic/plus.jpg" width="9" height="9" border="0"></a></span></td>
					<script type="text/javascript">
					    if (dAltHidden)
						document.write('<td width="130" class="hist_menu"><span onclick="switchDiv(\'div_alternatives\', true, 0.3);"><a accesskey="a" onclick="switchDiv(\'div_alternatives\', true, 0.3);" href="javascript:void(0);" class="menu_link"><b><u>A</u>lternative Views</b> <img id="div_alternatives_img" src="/img/dynamic/plus.jpg" width="9" height="9" border="0"></a></span></td>');
					</script>
					<td class="hist_menu" style="border-right: 0px;">&nbsp;</td>
				</tr>
			</table>
		</td>
	</tr>
	
	<tr>
	    <td style="border-bottom: solid 1px #B5B5BD; padding-bottom: 5px; font-family: Verdana, Arial" bgcolor="#F0F0F0">
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
					    (<a class="link" href="JavaScript:checkall();" class="link">check all</a> | <a class="link" href="JavaScript:uncheckall();" class="link">uncheck all</a>)
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
				<<:com_log_start:>>
				<td align=left nowrap valign=absmiddle>Logarithmic scale
				    <select name="log" onChange="JavaScript:modify();" class="input_select">
					<option value="0" <<:log_0 esc:>>>disabled</option>
					<option value="1" <<:log_1 esc:>>>enabled</option>
				    </select>
				</td>
				<<:com_log_end:>>
				<<:com_err_start:>>
				<td align=left nowrap valign=absmiddle>Min/Max
				    <select name="err" onChange="JavaScript:modify();" class="input_select">
					<option value="0" <<:err_0 esc:>>>disabled</option>
					<option value="1" <<:err_1 esc:>>>enabled</option>
				    </select>
				</td>
				<<:com_err_end:>>
				<<:com_sum_start:>>
				<td align=left nowrap valign=absmiddle style="font-family:Helvetica;font-size:10px" onMouseOver="overlib('Applies only to the selected series')" onMouseOut="nd()" onClick="nd()">Sum series
				    <select name="sum" onChange="JavaScript:modify();" class="input_select">
					<option value="0" <<:sum_0 esc:>>>disabled</option>
					<option value="1" <<:sum_1 esc:>>>enabled</option>
				    </select>
				</td>
				<<:com_sum_end:>>
				<<:com_int_start:>>
				<td align=left nowrap valign=absmiddle style="font-family:Helvetica;font-size:10px">Integral series
				    <select name="int" onChange="JavaScript:modify();" class="input_select">
					<option value="0" <<:int_0 esc:>>>disabled</option>
					<option value="1" <<:int_1 esc:>>>enabled</option>
				    </select>
				</td>
				<<:com_int_end:>>
			
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
		<td width=100%>
		    <table width=100% border=0 cellspacing=0 cellpadding=3 class=text>
			<tr>
			    <td nowrap  valign=bottom>

				<!-- INTERVAL SELECTION -->
				
	Interval selection:
	<select name="quick_interval" onChange="javascript:quick_jump();" class="input_select">
	    <option value="-1">- choose -</option>
	    <option value="3600000">last hour</option>
	    <option value="86400000">last day</option>
	    <option value="604800000">last week</option>
	    <option value="2628000000">last month</option>
	    <option value="5256000000">2 months</option>
	    <option value="7884000000">3 months</option>
	    <option value="10512000000">4 months</option>
	    <option value="15768000000">6 months</option>
	    <option value="31536000000">last year</option>
	    <option value="63072000000">last 2 years</option>
	    <option value="94608000000">last 3 years</option>
	    <option value="126144000000">last 4 years</option>
	    <option value="157680000000">last 5 years</option>
	</select>
	
	&nbsp;&nbsp;or&nbsp;&nbsp;
	
	</td>
	<td valign=bottom nowrap>
	    <a href="javascript:move_back();" onmouseover="return overlib('Previous interval');" onmouseout="return nd();" class="linkb"  style="font-size: 14px;">&laquo;</a>&nbsp;
	</td>
	<td valign=bottom nowrap>
	    <a href="javascript:void(0)" onclick="nd(); pickDate(this, document.getElementById('min'), calendarObjForFormMin);" onmouseover="return overlib('Select start date');" onmouseout="return nd();"><img src="/img/cal.gif" border=0 hspace=2 vspace=2 align="center"></a>
	</td>
	<td valign=bottom nowrap>
	<input type="text" name="interval_date_low" id="min" value="" onclick="" size="14" readonly class="input_text">
	&nbsp;&nbsp;-&nbsp;&nbsp;
	</td>
	<td valign=bottom nowrap>
	    <a href="javascript: void(0);" onclick="nd(); pickDate(this, document.getElementById('max'), calendarObjForFormMax);" onmouseover="return overlib('Select end date');" onmouseout="return nd();"><img src="/img/cal.gif" border=0 hspace=2 vspace=2 align="center"></a>
	</td>
	<td valign=bottom nowrap>
	<input type="text" name="interval_date_high" id="max" size=15 readonly class="input_text">

	<input type=hidden name="interval.min" id="interval.min" value="<<:interval.min esc:>>">
	<input type=hidden name="interval.max" id="interval.max" value="<<:interval.max esc:>>">

	&nbsp;
	</td>
	<td valign=bottom nowrap>
	    <a href="javascript: void(0)" onclick="move_next();" onmouseover="return overlib('Next interval');" onmouseout="return nd();" class="linkb" style="font-size: 14px;">&raquo;</a>
	</td>
	<script language="JavaScript">
	    <!--
	    var now = new Date('<<:current_date_time js:>>');
	    -->
	</script>
	<script language="JavaScript" src="/js/htmlsuite/calendar_set.js">
	</script>
	
				
				<!-- /INTERVAL SELECTION -->


			    </td>
			    <td align=right valign=bottom width=100%>
				<input type=submit name="submit_plot" value="Plot" class="input_submit">
			    </td>
			</tr>
		    </table>
		</td>
		</tr>
		<<:extra:>>
	    </table>
	</td>
	</tr>
    
    <a name="jump_here"></a>
    
    <script language=JavaScript>
	function openAnnotations(){
	    window.open('/annotations.jsp?series_names=<<:annotation_series enc:>>&groups=<<:annotation.groups enc:>>', 'annwindow', 'toolbar=0,width=900,height=600,scrollbars=1,resizable=1,titlebar=1');
	    return false;
	}
    </script>
    
    <table border=0 cellspacing=0 cellpadding=0 width=790>
	<tr>
	    <td align=left><a href="javascript:void(0);" onClick="return openAnnotations();" class="link" accesskey="n">A<u>n</u>notations</a></td>
	    <td align=center><div class="text"><<:extra_link:>></div></td>
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
	<span onclick="switchDiv('div_stats', true, 0.3);"><a accesskey="t" onclick="switchDiv('div_stats', true, 0.3);" href="javascript:void(0);" class="menu_link"><b>S<u>t</u>atistics</b> <img id="div_stats_img" src="/img/dynamic/plus.jpg" width="9" height="9" border="0"></a></span></td>
    </td>
</tr>
<tr>
    <td align=center>
	<div id="div_stats" style="display: none"><div>
	    		<table border=0 cellspacing=0 cellpadding=0>
			    <tr><<:statistics:>></tr>
			</table>
	</div></div>
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
