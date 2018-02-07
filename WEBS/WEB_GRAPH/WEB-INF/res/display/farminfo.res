<form action="display" method="post" name="form1">
    <input type=hidden name="page" value="<<:page esc:>>">
    <<:extra_fields:>>
    <table border=0 cellspacing=0 cellpadding=2 width=800 bgcolor="#778899">
	<tr>
	    <td>
		<table border=0 cellspacing=0 cellpadding=3 width=100% bgcolor="#eeeeee" style="font-family:Helvetica;font-size:10px">
		    <tr>
			<td width=100% colspan=2>
			
			<table width=100% border=0 cellspacing=0 cellpadding=0><tr>
			
			<td align=left nowrap valign=absmiddle style="font-family:Helvetica;font-size:10px"><<:com_tablesel_start:>>Show history for the last
				<select name="pTime" onChange="JavaScript:modify();"><<:opt_ptime:>></select><<:com_tablesel_end:>>
			</td>
			<<:com_interval_start:>>
			    <td colspan=4 align=left style="font-family:Helvetica;font-size:10px">
				Interval selection : <select name=interval.min><<:opt_intervalmin:>></select>&nbsp;&nbsp;-&nbsp;&nbsp;<select name=interval.max><<:opt_intervalmax:>></select>
			    </td>
			<<:com_interval_end:>>
			
			<td align=left nowrap valign=absmiddle style="font-family:Helvetica;font-size:10px">Farm name
			    <select name="farm" onChange="JavaScript:modify();"><<:opt_farm:>></select>
			</td>

		        <td align=right>
		            <input type=submit name="submit_plot" value="Plot">
		        </td>
			
			</tr></table>
		    </tr>
		</table>
	    </td>
	</tr>
    </table>
    
    <<:extra:>>
</form>

<table border=0 cellspacing=0 cellpadding=0>
    <tr>
	<td align=center><b>Current Master CPU Usage</b></td>
	<td align=center><b>Farm load</b></td>
    </tr>
    <tr>
	<td><img width=400 height=180 src="display?width=400&height=180&img=true&page=sample_farminfo_3&pTime=<<:pTime enc:>>&Farms=<<:farm enc:>>&interval.min=<<:interval.min enc:>>&interval.max=<<:interval.max enc:>>"></td>
	<td><img width=400 height=180 src="display?width=400&height=180&img=true&page=sample_farminfo_1&pTime=<<:pTime enc:>>&Farms=<<:farm enc:>>&interval.min=<<:interval.min enc:>>&interval.max=<<:interval.max enc:>>"></td>
    </tr>

    <tr><td colspan=2>&nbsp;</td></tr>

    <tr>
	<td align=center><b>Master Load5</b></td>
	<td align=center><b>Farm traffic</b></td>
    </tr>
    <tr>
	<td><img width=400 height=180 src="display?width=400&height=180&img=true&page=sample_farminfo_2&pTime=<<:pTime enc:>>&Farms=<<:farm enc:>>&interval.min=<<:interval.min enc:>>&interval.max=<<:interval.max enc:>>"></td>
	<td><img width=400 height=180 src="display?width=400&height=180&img=true&page=sample_farminfo_4&pTime=<<:pTime enc:>>&Farms=<<:farm enc:>>&interval.min=<<:interval.min enc:>>&interval.max=<<:interval.max enc:>>"></td>
    </tr>
</table>
<br>
<br>

<<:continut:>>
