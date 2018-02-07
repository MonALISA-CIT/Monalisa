<div class="text">

<script type="text/javascript">

function modifyText(sel){
    tbox = objById(sel.name+"_other");
    
    tbox.value = sel.options[sel.selectedIndex].value;
}

function doSave(){
    var p = '<<:pred js:>>';

    p = p.substring(0, p.lastIndexOf('/')) + '/'+document.form1.tmin_other.value+'/'+document.form1.tmax_other.value+p.substring(p.lastIndexOf('/'));

    try{
	window.opener.document.getElementById("predicate_<<:pred_id js:>>").value = p;
    }
    catch (ex){
	return true;
    }
    
    try{
	window.close();
    }
    catch (ex){
	alert('Predicate is:\n'+p);
    }
    
    return false;
}

function init(){
    var tmin = '<<:tmin js:>>';
    var tmax = '<<:tmax js:>>';
    
    if (tmin.length>0){
	document.form1.tmin_other.value=tmin;
	
	for (i=0; i<document.form1.tmin.options.length; i++){
	    if (document.form1.tmin.options[i].value == tmin){
		document.form1.tmin.selectedIndex = i;
		break;
	    }
	}
    }

    if (tmax.length>0){
	document.form1.tmax_other.value=tmax;
	
	for (i=0; i<document.form1.tmax.options.length; i++){
	    if (document.form1.tmax.options[i].value == tmax){
		document.form1.tmax.selectedIndex = i;
		break;
	    }
	}
    }
}

</script>
<br><br>
<form action="pick_pred.jsp" method="POST" name="form1" onSubmit="return doSave();">
<input type="hidden" name="pred" value="<<:pred esc:>>">
<input type="hidden" name="pred_id" value="<<:pred_id esc:>>">
<h2>Selected series</h2>
<h3 style="color: red"><b><<:pred esc:>></b></h3>

<br>
<b>Please choose a time interval</b>:<br>
<table border=0 cellspacing=5 cellpadding=0 class="text">
<tr>
    <td align=left>Starting time:</td>
    <td>
<select id="tmin" name="tmin" class="input_select" onChange="modifyText(this);">
    <option value="-1">Now</option>
    <option value="-3600000" selected>1 hour ago</option>
    <option value="-7200000">2 hours ago</option>
    <option value="-10800000">3 hours ago</option>
    <option value="-21600000">6 hours ago</option>
    <option value="-43200000">12 hours ago</option>
    <option value="-86400000">1 day ago</option>
    <option value="-172800000">2 days ago</option>
    <option value="-604800000">1 week ago</option>
    <option value="-1209600000">2 weeks ago</option>
    <option value="-2592000000">1 month ago</option>
    <option value="-5270400000">2 months ago</option>
    <option value="-7862400000">3 months ago</option>
    <option value="-10454400000">4 months ago</option>
    <option value="-15724800000">6 months ago</option>
    <option value="-31536000000">1 year ago</option>
</select>
    </td>
    <td>
	or other timestamp: <input type="text" name="tmin_other" id="tmin_other" value="" class="input_text" style="text-align:left">
    </td>
</tr>
<tr>
    <td align=left>Ending time:</td>
    <td>
<select name="tmax" class="input_select" id="tmax" onChange="modifyText(this);">
    <option value="-1">Now</option>
    <option value="-3600000">1 hour ago</option>
    <option value="-7200000">2 hours ago</option>
    <option value="-10800000">3 hours ago</option>
    <option value="-21600000">6 hours ago</option>
    <option value="-43200000">12 hours ago</option>
    <option value="-86400000">1 day ago</option>
    <option value="-172800000">2 days ago</option>
    <option value="-604800000">1 week ago</option>
    <option value="-1209600000">2 weeks ago</option>
    <option value="-2592000000">1 month ago</option>
    <option value="-5270400000">2 months ago</option>
    <option value="-7862400000">3 months ago</option>
    <option value="-10454400000">4 months ago</option>
    <option value="-15724800000">6 months ago</option>
    <option value="-31536000000">1 year ago</option>
</select>
    </td>
    <td>
	or other timestamp: <input type="text" name="tmax_other" id="tmax_other" value="" class="input_text" style="text-align:left">
    </td>
<tr>
    <td colspan=3 align=right>
	<input type="submit" class="input_submit" value="Continue...">
    </td>
</tr>
</table>

</div>

<script type="text/javascript">
    init();
    modifyText(objById("tmin"));
    modifyText(objById("tmax"));
</script>
