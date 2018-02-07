<script language="javascript">

function insertRelation(source){
    var destSite = document.getElementById('destination_'+source).value;
    var tier = document.getElementById('tier_'+source).value;
    var tierColor = document.getElementById('tier_color_'+source).value;

    if(destSite == source){
	alert('Source must be different from destination!');
	return false;
    }
    
    if(destSite == '-1'){
	alert('Please select the destination!');
	return false;
    }
        
    if(tier <=0 || tier > 3){
	alert('Please enter the correct tier!');
	return false;
    }

    if(!tierColor.match(/^#([0-9A-Za-z]){6}$/)){
	alert('Please enter a correct color');
	return false;
    }
    
    //alert(source + ' - '+destSite + ' - '+tier+ ' - '+tierColor);

    var url = '/admin/relations_action.jsp?source='+encodeURIComponent(source)+
				    '&dest='+encodeURIComponent(destSite)+
				    '&tier='+encodeURIComponent(tier)+
				    '&color='+encodeURIComponent(tierColor);
//    alert(url);

    new Ajax.Request(url, {
	    method: 'post',
	    onComplete: function(transport) {
	    	    
		if(transport.status == "200"){
		    if(transport.responseText.indexOf('Error') > 0){
		        alert("Error: .Please refresh the page and try again!");
		    }
		    else{
		        alert("Update successful");
		    }
		}
		else{
		    alert("Error");
		}
	    }
	    }
	);

    return false;    
}

</script>

<div id="plugin" onmousedown="HSVslide('drag','plugin',event)" style="TOP: 140px; LEFT: 430px; Z-INDEX: 20; DISPLAY: none; background-color: #C3C7CC; border: solid 1px #9FBCD1">
 <div id="plugCUR"></div><div id="plugHEX" onmousedown="stop=0; setTimeout('stop=1',100);">FFFFFF</div><div id="plugCLOSE" onmousedown="toggle_picker('plugin')">X</div><br>
 <div id="SV" onmousedown="HSVslide('SVslide','plugin',event)" title="Saturation + Value">
  <div id="SVslide" style="TOP: -4px; LEFT: -4px;"><br /></div>
 </div>
 <form id="H" onmousedown="HSVslide('Hslide','plugin',event)" title="Hue">

  <div id="Hslide" style="TOP: -7px; LEFT: -8px;"><br /></div>
  <div id="Hmodel"></div>
 </form>
</div>

<script src="/js/colors/picker.js"></script>
<table cellspacing=0 cellpadding=5 class="table_content">
    <tr height=25>
	<td ><a href="/admin.jsp" class="menu_link_active"><b>Site Administration</b></a></td>
	<td style="border-left: 1px solid #C0D5FF;"><a href="/admin/map_admin.jsp" class="menu_link_active"><b>Map labels Administration</b></a></td>	
	<td style="background-color: #F0F0F0;border-left: 1px solid #C0D5FF;"><a href="/admin/relations.jsp" class="menu_link_active"><b>Map Relations Administration</b></a></td>		
	<td style="border-left: 1px solid #C0D5FF;"><a href="/admin/linux.jsp" class="menu_link_active"><b>VoBox Linux Flavour</b></a></td>	    
    </tr>
</table>
<br>
<table cellspacing=0 cellpadding=2 class="table_content">
    <tr height=25>
	<td class="table_title"><b>Site relations Administration</b></td>
    </tr>
    <tr>
	<td>
	    <table cellspacing=1 cellpadding=2 width="100%">
		<tr height=25 class="table_header">
		    <td class="table_header">Source</td>
		    <td class="table_header">Destination</td>
		    <td class="table_header" colspan="2">Color</td>		    
		    <td class="table_header">Tier</td>		    		    
		    <td class="table_header">&nbsp;</td>
		</tr>
		<<:continut:>>
	    </table>
	</td>
    </tr>
</table>
