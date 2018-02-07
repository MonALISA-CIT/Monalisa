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
			
			<<:com_interval_start:>>
			    <td colspan=4 align=left style="font-family:Helvetica;font-size:10px">
				Interval selection : <select name=interval.min><<:opt_intervalmin:>></select>&nbsp;&nbsp;-&nbsp;&nbsp;<select name=interval.max><<:opt_intervalmax:>></select>
			    </td>
			<<:com_interval_end:>>
			
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
	<td align=center><b>Running jobs history</b></td>
	<td align=center><b>Current running jobs</b></td>
    </tr>
    <tr>
	<td><img width=400 height=300 src="display?legend=false&width=400&height=300&img=true&page=globalview1&interval.min=<<:interval.min enc:>>&interval.max=<<:interval.max enc:>>"></td>
	<td><img width=400 height=300 src="display?legend=false&width=400&height=300&img=true&page=globalview2"></td>
    </tr>

    <tr><td colspan=2>&nbsp;</td></tr>
</table>
<br>
<br>

<<:continut:>>
