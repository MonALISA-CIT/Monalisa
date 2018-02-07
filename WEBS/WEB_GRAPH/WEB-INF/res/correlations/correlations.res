<script type="text/javascript">
    bDivFurat = true;
</script>

<form action="Correlations" method="post" name="form1">
<input type=hidden name="page" value="<<:page esc:>>">

<table width="790" cellspacing="0" cellpadding="0" border="0" class="text">
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
		    <td class="hist_menu" style="border-right: 0px;">&nbsp;</td>
		</tr>
	    </table>
	</td>
    </tr>
    <tr>
        <td style="border-bottom: solid 1px #B5B5BD; padding-bottom: 5px; font-family: Verdana, Arial" bgcolor="#F0F0F0">
	    <table width=100% cellspacing="0" cellpadding="0">
		<tr>
		    <td>
			<div id="div_series" style="display: none"><div>
			    <table border=0 cellspacing=0 cellpadding=3 width=100% style="border-bottom: 2px solid #FFFFFF;">
				<tr>
				    <td align=left class="text">
					<<:series:>>
				    </td>
				</tr>
		    	    </table>
			</div></div>
		    </td>
		</tr>
		<tr>	
		    <td>
			<div id="div_options" style="display: none;"><div>
				    <!-- INTERVAL SELECTION -->
				    
			    <table border=0 cellspacing=0 cellpadding=0><tr><td>
				
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
	    <option value="189216000000">last 6 years</option>
	    <option value="283996800000">last 9 years</option>
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
	<input type="text" name="interval_date_low" id="min" value="" size="15" class="input_text">
	&nbsp;&nbsp;-&nbsp;&nbsp;
	</td>
	<td valign=bottom nowrap>
	    <a href="javascript: void(0);" onclick="nd(); pickDate(this, document.getElementById('max'), calendarObjForFormMax);" onmouseover="return overlib('Select end date');" onmouseout="return nd();"><img src="/img/cal.gif" border=0 hspace=2 vspace=2 align="center"></a>
	</td>
	<td valign=bottom nowrap>
	<input type="text" name="interval_date_high" id="max" value="" size="15" class="input_text">

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
	    setCalendarFineGranularity();
	    init_form();
	    -->
	</script>
				
				    </td></tr></table>
				<!-- /INTERVAL SELECTION -->
			    <table border=0 cellspacing=0 cellpadding=3 width=100% class="text" style="border-bottom: 2px solid #FFFFFF">
				<tr>
				<tr>
				    <<:options:>>
				</tr>
			    </table>
			</div></div>
		    </td>
		</tr>
		<tr>
		    <td width=100%>
			<table width=100% border=0 cellspacing=0 cellpadding=3 class=text>
			    <tr>
				<td align=right valign=bottom width=100%>
				    <input type=submit name="submit_plot" value="Plot" class="input_submit">
				</td>
			    </tr>
			</table>
		    </td>
		</tr>
	    </table>
	</td>
    </tr>
</table>
<<:hidden_parameters:>>
</form>

<div align=center style="padding-top:10px">
<table border=0 cellspacing=0 cellpadding=0>
    <tr>
	<td align=left>
	    <<:map:>><img src="display?image=<<:image esc:>>" usemap="#<<:image esc:>>" border=0>
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
		<table border=0 cellspacing=0 cellpadding=2 class="table_content">
		    <tr>
			<td align=center class="table_title"><b>Y-values statistics</b></td>
		    </tr>
		    <tr>
			<td>
			    <table cellspacing=1 cellpadding=2 width="330">
			        <tr height=25>
				    <td class="table_header" align=left><b>Series name</b></td>
				    <td class="table_header" align=right><b>Min</b></td>
				    <td class="table_header" align=right><b>Max</b></td>
				    <td class="table_header" align=right><b>Avg</b></td>
				</tr>

				<<:statistics:>>
			    </table>
			</td>
		    </tr>
		</table>
	    </div></div>
	</td>
    </tr>
</table>
</div>

<script type="text/javascript">
    checkDivs(['div_series', 'div_options', 'div_stats']);
</script>
