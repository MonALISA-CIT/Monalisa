<script type="text/javascript">

//Add more fields dynamically.
function addField(area, field,limit) {
    //Prevent older browsers from getting any further.
    if(!document.getElementById) return; 
    
    var field_area = document.getElementById(area);
    
    //Get all the input fields in the given area.
    var all_inputs = field_area.getElementsByTagName("input"); 
    
    //Find the count of the last element of the list. It will be in the format '<field><number>'. If the 
    //field given in the argument is 'friend_' the last id will be 'friend_4'.
    var last_item = all_inputs.length - 1;
    var last = all_inputs[last_item].id;
    var count = Number(last.split("_")[1]) + 1;
    
    if(count >=2){
	document.getElementById("div_predicate_function").style.display = "inline";
    }
    else{
	document.getElementById("div_predicate_function").style.display = "none";    
    }
    
    //If the maximum number of elements have been reached, exit the function.
    //		If the given limit is lower than 0, infinite number of fields can be created.
    if(count > limit && limit > 0) return;
    
    if(document.createElement) { //W3C Dom method.
	var li = document.createElement("li");
	li.id = "li"+field+count;
	li.name = "li"+field+count;
	
	var imgLink = document.createElement("span");
	imgLink.innerHTML = '<a href="javascript:void(0)" onclick="removeElement(\''+area+'\',\'li'+field+count+'\')"><img align="center" src="/img/arrows_simple/arrow_x.png" border="0"></a>';
	imgLink.id = 'img_'+field+count;
	imgLink.name = 'img_'+field+count;
	
	li.appendChild(imgLink);
	
	var input = document.createElement("input");
	input.id = field+count;
	input.name = field+count;
	input.setAttribute("class", "input_submit");	
	input.size = 80;
	input.type = "text"; //Type of field - can be any valid input type like text,file,checkbox etc.
	li.appendChild(input);
	field_area.appendChild(li);
	
	var link = document.createElement("span");
	link.id = "link_"+field+count
	link.name = "link_"+field+count
	link.innerHTML = ' <a href="javascript: void(0)" onclick="choosePred('+count+');" class="link">Choose predicate</a>';
	
	li.appendChild(link);
	
	field_area.appendChild(li);
    } else { //Older Method
	field_area.innerHTML += "<li><input name='"+(field+count)+"' id='"+(field+count)+"' type='text' class='input_text' size='80'/></li>";
    }
}

function removeElement(area, field){
    var d = document.getElementById(field);
    d.parentNode.removeChild(d);


    var field_area = document.getElementById(area);
    var all_inputs = field_area.getElementsByTagName("input"); 
    var count = all_inputs.length;

    if(count >=2){
	document.getElementById("div_predicate_function").style.display = "inline";
    }
    else{
	document.getElementById("div_predicate_function").style.display = "none";    
    }

}

function setPredicates(area){
    var field_area = document.getElementById(area);
    var all_inputs = field_area.getElementsByTagName("input");
    
    var sPredicates = "";
    
    var predCount = all_inputs.length;
    
    for(i=0; i< predCount; i++){
	var predElem = all_inputs[i];
	sPredicates += ((i==0) ? "" : ",") + predElem.value;
    }
    
    document.forms["step3"].elements["predicates"].value=sPredicates;
}

function showVariables(){
    var elementCiudat = document.forms["step3"].elements["predicate2"];
    alert(elementCiudat.value);
    alert(elementCiudat.name);
//    alert(document.getElementById('myDiv').innerHTML)
//    alert(document.getElementById("predicate1").value);
    return false;
}

function validateForm(){
    var alias = document.getElementById("alias").value;
    var pred1 = document.getElementById("predicate_1").value;
    
    if(alias.length == 0){
	alert("Please insert a Title!");
	return false;
    }    
    
    if(pred1.length == 0){
	alert("You must insert at least one predicate");
	return false;
    }
    
    return true;
}

function choosePred(id){
    var pred = document.getElementById('predicate_'+id).value;

    window.open('/correlations/pick_pred.jsp?pred_id='+id+'&pred_start='+escape(pred), 'pred_window1', 'menubar=0, resizable=1, scrollbars=1, width=800,height=600');
    
    return false;
}

</script>
<table cellspacing="0" cellpadding="0" border="0" align="left">
<tr><td><<:info:>></td></tr>
<tr><td><<:charts:>></td></tr>
<tr><td><<:series:>></td></tr>
<tr><td>
<table cellpadding="7" cellspacing="1" border="0" align="left">
    <form name="step3" action="/correlations/correlations_action.jsp" method="POST">
    <input type="hidden" name="step" value="3">
    <input type="hidden" name="prop" value="<<:prop esc:>>">
    <input type="hidden" name="chart" value="<<:chart esc:>>">
    <input type="hidden" name="serie" value="<<:title esc:>>">
    <input type="hidden" name="predicates" value="">
    <tr>
	<td colspan="2" align="left"><h2><<:action esc:>> series</h2></td>
    </tr>
    <tr>
	<td colspan="2" align="left"><font class="grey">Fields marked with bold font are required</font></td>
    </tr>
    <tr>
	<td class="correlations"><b>Name</b>:</td>
	<td class="correlations" nowrap style="padding-left: 72px"><input type="text" name="alias" id="alias" value="<<:alias esc:>>" class="input_text" size="80"><a href="javascript:void(0);" style="cursor: help;" onmouseover="return overlib('Series name, will be displayed as it is in the legend');" onmouseout="return nd();"><img src="/img/qm.gif" border="0"></a></td>
    </tr>
    <tr>
	<td class="correlations" valign="top" nowrap ><b>Predicates</b>:<br>
	    <a href="javascript: void(0);" class="link" onclick="addField('predicates_area','predicate_',10);" title="Add another predicate">Add predicate</a>
	    <a href="javascript:void(0);" style="cursor: help;" onmouseover="return overlib('Usually you only need to specify one predicate here. A predicate defines the data cut, both as semantics and time. You can create a derivate data series as a combination of existing raw monitoring data series.');" onmouseout="return nd();"><img src="/img/qm.gif" border="0"></a>
	</td>
	<td valign="top" class="correlations" nowrap>
	    <table cellpadding="2" cellspacing="0" border="0" align="left" class="text">
		<tr>
		    <td align="left">
			<ol id="predicates_area">
			    <li>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<input type="text" name="predicate_1" id="predicate_1" class="input_text" size="80" value="<<:predicate_1 esc:>>"/>&nbsp;<a href="javascript: void(0)" onclick="return choosePred(1);" class="link">Choose Predicate</a></li>
			    <<:predicates_list:>>
			</ol>
			<div id="div_predicate_function" style="padding-top: 10px; padding-left:60px; display: <<:predicate_display esc:>>" id="predicate_function">
			  Aggregating function  <select class="input_select" name="function">
				<option value="sum" <<:sum esc:>>>Sum</option>
				<option value="avg" <<:avg esc:>>>Avg</option>
				<option value="diff" <<:diff esc:>>>Diff</option>
			    </select>
			    <a href="javascript:void(0);" style="cursor: help;" onmouseover="return overlib('Select the function to be applied to several raw series to obtain the desired result:<br><b>Sum</b>: sum up Y values in time<br><b>Avg</b>: average Y values in time<br><b>Diff</b>: make the difference between <b>two</b> data series.');" onmouseout="return nd();"><img src="/img/qm.gif" border="0"></a>
			</div>
		    </td>
		</tr>
	    </table>
	</td>
    </tr>
    <tr>
	<td class="correlations" align="right"></td>
	<td class="correlations">
	    <input type="submit" value="Submit" class="input_submit" onclick="setPredicates('predicates_area'); return(validateForm());">
	    <<:com_back_start:>><a href="javascript: void(0)" class="input_submit" onclick="document.forms['backForm'].submit(); return false;">Back</a><<:com_back_end:>>
	</td>
    </tr>
    </form>
    <form name="backForm" action="/correlations/correlations.jsp" method="get">
	<input type="hidden" name="step" value="3">
	<input type="hidden" name="prop" value="<<:prop esc:>>">
	<input type="hidden" name="chart" value="<<:chart esc:>>">
    </form>
</table>
</td></tr></table>