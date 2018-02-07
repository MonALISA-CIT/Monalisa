<script language="javascript">
    function isDecimalNumber(number){
	/*se foloseste de o expresie regulata*/
	var expression =  /^\d+$/
    
	if(!expression.test(number))
	    return false;
    
	return true;
    }

    function validateForm(){
	var title = document.getElementById("title").value;    
	var width = document.getElementById("width").value;    	
	var height = document.getElementById("height").value;
	
	if(title.length == 0){
	    alert("Please insert a Title");
	    return false;
	}	    

	if(width.length == 0 || !isDecimalNumber(width)){
	    alert("Please insert a correct width!");
	    return false;
	}	    

	if(height.length == 0 || !isDecimalNumber(height)){
	    alert("Please insert a correct height!");
	    return false;
	}	    
	
	return true;
    }
</script>
<table cellpadding="7" cellspacing="0" border="0" align="left">
    <form name="step1" action="/correlations/correlations_action.jsp" method="post">
    <input type="hidden" name="step" value="1">
    <input type="hidden" name="prop" value="<<:prop esc:>>">
    <tr>
	<td colspan="2" align="left"><h2>Global Chart Settings</h2></td>
    </tr>
    <tr>
	<td colspan="2" align="left"><font class="grey">Fields marked with bold font are required</font></td>
    </tr>
    <tr>
	<td class="correlations" align="right"><b>Title</b>:</td>
	<td class="correlations"><input type="text" name="title" id="title" value="<<:title esc:>>" class="input_text" size="80"><a href="javascript:void(0);" style="cursor: help;" onmouseover="return overlib('Chart title');" onmouseout="return nd();"><img src="/img/qm.gif" border="0"></a></td>
    </tr>
    <tr>
	<td class="correlations" align="right"><b>Width</b>:</td>
	<td class="correlations"><input type="text" name="width" id="width" value="<<:width esc:>>" class="input_text"><a href="javascript:void(0);" style="cursor: help;" onmouseover="return overlib('Chart width in px');" onmouseout="return nd();"><img src="/img/qm.gif" border="0"></a></td>
    </tr>
    <tr>
	<td class="correlations" align="right"><b>Height</b>:</td>
	<td class="correlations"><input type="text" name="height" id="height" value="<<:height esc:>>" class="input_text"><a href="javascript:void(0);" style="cursor: help;" onmouseover="return overlib('Chart height in px');" onmouseout="return nd();"><img src="/img/qm.gif" border="0"></a></td>
    </tr>
    <tr>
	<td class="correlations" align="right">Common Y axis:</td>
	<td class="correlations"><input type="checkbox" name="samerange" value="true" class="input_submit" <<:samerange esc:>>><a href="javascript:void(0);" style="cursor: help;" onmouseover="return overlib('If you have several plots on the same chart this defines how they are arranged: common X axis (unchecked, default) or common Y axis (checked)');" onmouseout="return nd();"><img src="/img/qm.gif" border="0"></a></td>
    </tr>
    <tr>
	<td class="correlations" align="right">Drop Y Above:</td>
	<td class="correlations"><input type="text" name="dropy_above" value="<<:dropyabove esc:>>" class="input_text"><a href="javascript:void(0);" style="cursor: help;" onmouseover="return overlib('Ignore Y values that are above this threshold (empty = disabled)');" onmouseout="return nd();"><img src="/img/qm.gif" border="0"></a></td>
    </tr>
    <tr>
	<td class="correlations" align="right">Drop Y Below:</td>
	<td class="correlations"><input type="text" name="dropy_below" value="<<:dropybelow esc:>>" class="input_text"><a href="javascript:void(0);" style="cursor: help;" onmouseover="return overlib('Ignore Y values that are below this threshold (empty = disabled)');" onmouseout="return nd();"><img src="/img/qm.gif" border="0"></a></td>
    </tr>
    <tr>
	<td class="correlations" align="right"></td>
	<td class="correlations">
	    <input type="submit" class="input_submit" onclick="return validateForm();" value="Submit">&nbsp;
	    <<:com_back_start:>><a href="javascript: void(0)" class="input_submit" onclick="document.forms['backForm'].submit(); return false;">Back</a><<:com_back_end:>>
	</td>
    </tr>
    </form>
    <form name="backForm" action="/correlations/correlations.jsp" method="get">
        <input type="hidden" name="step" value="2">
        <input type="hidden" name="prop" value="<<:prop esc:>>">
    </form>
</table>