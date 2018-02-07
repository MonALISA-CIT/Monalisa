<div class="text">
<form action=dump_cache.jsp method=get>
    Filter by predicate : <input type=text name=pred value="<<:pred:>>" size=50 class="input_text" style="text-align: left"> <input type=submit name=submit value=Filter class="input_submit">
</form>
</div>
<table cellspacing=0 cellpadding=2 class="table_content">
    <tr height=25>
	<td class="table_title"><b>Last values dump</b></td>
    </tr>
    <tr>
	<td>
	    <table cellspacing=1 cellpadding=2>
    		<tr height=25 class="table_header">
            	    <td class="table_header">Farm</td>
            	    <td class="table_header">Cluster</td>
            	    <td class="table_header">Node</td>
            	    <td class="table_header">Parameter</td>
            	    <td class="table_header">Value</td>
            	    <td class="table_header">Time</td>
    		</tr>
		<<:continut:>>
		<tr class="table_header">
		    <th nowrap colspan=4>TOTAL</th>
		    <th>&nbsp;<<:total:>>&nbsp;</td>
		    <td>&nbsp;</td>
		</tr>
		<<:com_average_start:>>
		<tr class="table_header">
		    <th nowrap colspan=4>AVERAGE</th>
		    <th>&nbsp;<<:average:>>&nbsp;</td>
		    <td>&nbsp;</td>
		</tr>    
		<<:com_average_end:>>
		<tr class="table_header">
		    <th nowrap colspan=4>Count</th>
		    <th>&nbsp;<<:count:>>&nbsp;</td>
		    <td>&nbsp;</td>
		</tr>
	    </table>
	</td>
    </tr>
</table>
