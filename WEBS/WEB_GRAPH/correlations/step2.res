<script language="javascript">

function populate(s, data, initial){
    if (!s){
	return;
    }
    
    var len = data.length;
    s.options.length = 0;
    
    s.options[0] = new Option(initial, '');
    
    for (i = 1; i < len/2; i++){
        s.options[i] = new Option(data[2*i], data[2*i-1]);
    }
    
    s.selectedIndex = 0;
    
    if (len > 0){
        s.disabled = false;
        s.focus();
    }
    else {
        s.disabled = true;
    }
}

function change_type(s){
    var id = get_value(s);
    
    if (id){
	var types = nav[id];
	
	if (types){
    	    populate(s.form.histogram_type, types, 'Choose type');
    	    return;
	}
    }
    
    populate(s.form.histogram_type, [], 'Choose type');
}

function get_value(s){
    var index = s.selectedIndex;
    return s.options[index].value;
}

var nav={ "history":[], "scatter" : [], "histogram" : ["types", "frequency", "Frequency", "relative", "Relative", "scale_to_1", "Scale to 1"] , we : 'rule'};

function changeRange(){
    var autorange = document.getElementById("autorange");
    
    var minx = document.getElementById("minx");
    var maxx = document.getElementById("maxx");
    var miny = document.getElementById("miny");
    var maxy = document.getElementById("maxy");
    
    if(autorange.checked == true){
	minx.disabled = true;
	maxx.disabled = true;	
	miny.disabled = true;
	maxy.disabled = true;
    }
    else{
	minx.disabled = false;
	maxx.disabled = false;	
	miny.disabled = false;
	maxy.disabled = false;
    }
}

function checkAutorange(){
    var autorange = document.getElementById("autorange");
    
    var minx = document.getElementById("minx");
    var maxx = document.getElementById("maxx");
    var miny = document.getElementById("miny");
    var maxy = document.getElementById("maxy");

    if(autorange.checked == true){
	minx.disabled = true;
	maxx.disabled = true;	
	miny.disabled = true;
	maxy.disabled = true;
    }
}

function validateForm(){
    var alias = document.getElementById("alias").value;
    var xlabel = document.getElementById("xlabel").value;
    var ylabel = document.getElementById("ylabel").value;
    
    if(alias.length == 0){
	alert("Please insert a Title");
	return false;
    }

    if(xlabel.length == 0){
	alert("Please insert a X Label");
	return false;
    }
    
    if(ylabel.length == 0){
	alert("Please insert a Y label");
	return false;
    }
    
    return true;
}

</script>
<table cellspacing="0" cellpadding="0" border="0" align="left">
<tr><td><<:info:>></td></tr>
<tr><td><<:charts:>></td></tr>
<tr><td>
<table cellpadding="5" cellspacing="1" border="0" align="left">
    <form name="step2" action="/correlations/correlations_action.jsp" method="post">
    <input type="hidden" name="prop" value="<<:prop esc:>>">
    <input type="hidden" name="step" value="2">
    <input type="hidden" name="chart" value="<<:title esc:>>">    
    <tr>
	<td colspan="2" align="left"><h2><<:action esc:>> chart</h2></td>
    </tr>
    <tr>
	<td colspan="2" align="left"><font class="grey">Fields marked with bold font are required</font></td>
    </tr>
    <tr>
	<td class="correlations" align="right"><b>Title</b>:</td>
	<td class="correlations" nowrap><input type="text" name="alias" value="<<:alias esc:>>" id="alias" class="input_text" size="80"><a href="javascript:void(0);" style="cursor: help;" onmouseover="return overlib('Subchart title / description');" onmouseout="return nd();"><img src="/img/qm.gif" border="0"></a></td>
    </tr>
    <tr>
	<td class="correlations" align="right"><b>X Label</b>:</td>
	<td class="correlations"><input type="text" name="xlabel" value="<<:xlabel esc:>>"  id="xlabel" class="input_text" size="60"><a href="javascript:void(0);" style="cursor: help;" onmouseover="return overlib('X axis label');" onmouseout="return nd();"><img src="/img/qm.gif" border="0"></a></td>
    </tr>
    <tr>
	<td class="correlations" align="right"><b>Y Label</b>:</td>
	<td class="correlations"><input type="text" name="ylabel" value="<<:ylabel esc:>>" id="ylabel" class="input_text" size="60"><a href="javascript:void(0);" style="cursor: help;" onmouseover="return overlib('Y axis label');" onmouseout="return nd();"><img src="/img/qm.gif" border="0"></a></td>
    </tr>
    <tr>
	<td class="correlations" align="right"><b>Type</b>:</td>
	<td class="correlations">
	    <select name="type" clas="input_select" onchange="change_type(this);" id="type">
		<option value="history" <<:history esc:>>>History</option>
		<option value="scatter" <<:scatter esc:>>>Scatter</option>
		<option value="histogram" <<:histogram esc:>>>Histogram</option>
	    </select>
	    <select name="histogram_type" clas="input_select" id="histogram_type">
		<option value="">Choose type</option>
	    </select>
	    <a href="javascript:void(0);" style="cursor: help;" onmouseover="return overlib('Choose the chart type:<br><ul><li><b>History</b>: evolution in time for the selected series</li><li><b>Histogram</b>: Y values distribution</li><li><b>Scatter</b>: correlated pairs of series (Y values from first correlated with the Y values of the second, in time)</li></ul>');" onmouseout="return nd();"><img src="/img/qm.gif" border="0"></a>
	</td>
    </tr>
    <tr>
	<td class="correlations" align="right"><b>Autorange</b>:</td>
	<td class="correlations">
	    <table cellspacing="0" cellpadding="2" border="0">
		<tr>
		    <td align="left" valign="middle" style="border-right: solid 1px #FFFFFF"><input type="checkbox" name="autorange" id="autorange" value="true" class="input_submit" onclick="changeRange();" <<:autorange esc:>>></td>
		    <td>
			<table cellspacing="0" cellpadding="2" border="0">
			    <tr>
				<td class="text">
				    Min X: <input type="text" name="minx" id="minx" value="<<:minx esc:>>" class="input_text" disabled><br>
				    Max X: <input type="text" name="maxx" id="maxx" value="<<:maxx esc:>>" class="input_text" disabled>
				</td>
				<td class="text">
				    Min Y: <input type="text" name="miny" id="miny" value="<<:miny esc:>>" class="input_text" disabled><br>
				    Max Y: <input type="text" name="maxy" id="maxy" value="<<:maxy esc:>>" class="input_text" disabled>
				</td>
				<td valign="middle"><a href="javascript:void(0);" style="cursor: help;" onmouseover="return overlib('Uncheck this option and give some values to each input box to override the axis span. The default is to choose the appropriate limits to fit all the data that is displayed.');" onmouseout="return nd();"><img src="/img/qm.gif" border="0"></a></td>
			    </tr>
			</table>
		    </td>
		</tr>
	    </table>
	</td>
    </tr>
    <tr>
	<td class="correlations" align="right"></td>
	<td class="correlations" valign="middle">
	    <input class="input_submit" value="Submit" onclick="return validateForm();" type="submit"> <<:com_back_start:>><a href="javascript: void(0)" onclick="document.forms['backFrom'].submit(); return false;" class="input_submit">Back</a> <<:com_back_end:>>
	</td>
    </tr>
    </form>
    <form name="backFrom" action="/correlations/correlations.jsp" method="get">
	<input type="hidden" name="prop" value="<<:prop esc:>>">
	<input type="hidden" name="step" value="2">
    </form>
</table>
</td></tr></table>
<script language="javascript">
populate(document.forms["step2"].histogram_type, nav[get_value(document.forms["step2"].type)], '<<:histogram_initial_value js:>>');
//checkAutorange();
changeRange();
</script>
