<div class="text">
<font size="5">Monitored series list</font> (please filter the results to find the series you require)
<form name="form1" action="pick_pred.jsp" method="POST">
<input type="hidden" name="pred_id" value="<<:pred_id esc:>>">
<input type="hidden" name="tmin" value="<<:tmin esc:>>">
<input type="hidden" name="tmax" value="<<:tmax esc:>>">
<table border=0 cellspacing=0 cellpadding=0>
    <tr>
	<td class="table_title" style="text-align: left" colspan="2">
	    <table border=0 cellspacing=0 cellpadding=3 class="text">
		<tr>
		    <td><b>Service:</b></td>
		    <td>
			<select name="filter_farm" class="input_select" onChange="document.form1.submit();">
			    <option value=''>- Not selected -</option>
			    <<:opt_farm:>>
			</select>
		    </td>
		</tr>
		<tr>
		    <td><b>Cluster:</b></td>
		    <td>
		        <select name="filter_cluster" class="input_select" onChange="document.form1.submit();">
			<option value=''>- Not selected -</option>
			<<:opt_cluster:>>
			</select>
		    </td>
		</tr>
		<tr>
		    <td><b>Node</b></td>
		    <td>
			<select name="filter_node" class="input_select" onChange="document.form1.submit();">
			    <option value=''>- Not selected -</option>
			    <<:opt_node:>>
			</select>
		    </td>
		</tr>
		<tr>
		    <td><b>Parameter:</b></td>
		    <td>
			<select name="filter_parameter" class="input_select" onChange="document.form1.submit();">
			    <option value=''>- Not selected -</option>
			    <<:opt_parameter:>>
			</select>
		    </td>
		</tr>
	    </table>
	</td>
	<td class="table_title" align="center" valign="middle">
	    <input type="submit" value="Filter" class="input_submit">
	</td>
    </tr>
    
    <tr class="table_row">
	<td align="left" class="table_row"><b>Matched: <<:totalmatch esc:>> series</b> (max. 500 displayed)</td>
        <td align="right" class="table_row" align="right"><b>Last value</b></td>
	<td align="center" class="table_row">&nbsp;</td>
    </tr>

    
    <<:continut:>>
</table>

</form>
</div>
