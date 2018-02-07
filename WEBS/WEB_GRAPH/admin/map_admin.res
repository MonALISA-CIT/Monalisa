<script type="text/javascript" src="/js/window/prototype.js"> </script>
<script type="text/javascript">
    function submitLabelsData(label){
	var labelx = document.getElementById(label+'_labelx');
	var labely = document.getElementById(label+'_labely');
	var iconx = document.getElementById(label+'_iconx');
	var icony = document.getElementById(label+'_icony');
	var url = '/admin/map_admin_action.jsp?name='+encodeURIComponent(label)+
				    '&labelx='+encodeURIComponent(labelx.value)+
				    '&labely='+encodeURIComponent(labely.value)+
				    '&iconx='+encodeURIComponent(iconx.value)+
				    '&icony='+encodeURIComponent(icony.value);
	//alert(url);
	
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
    }
</script>
<table cellspacing=0 cellpadding=5 class="table_content">
    <tr height=25>
	<td ><a href="/admin.jsp" class="menu_link_active"><b>Site Administration</b></a></td>
	<td style="background-color: #F0F0F0;border-left: 1px solid #C0D5FF;;border-right: 1px solid #C0D5FF"><a href="/admin/map_admin.jsp" class="menu_link_active"><b>Map labels Administration</b></a></td>	
	<td><a href="/admin/relations.jsp" class="menu_link_active"><b>Map Relations Administration</b></a></td>
	<td style="border-left: 1px solid #C0D5FF;"><a href="/admin/linux.jsp" class="menu_link_active"><b>VoBox Linux Flavour</b></a></td>	    
    </tr>
</table>
<br />
<table cellspacing=0 cellpadding=2 class="table_content">
    <tr height=25>
	<td class="table_title"><b>Map labels Administration</b></td>
    </tr>
    <tr>
	<td>
	    <table cellspacing=1 cellpadding=2 width="100%">
		<tr height=25 class="table_header">
		    <td class="table_header">Farm Name</td>
		    <td class="table_header">LabelX</td>
		    <td class="table_header">LabelY</td>		    
		    <td class="table_header">IconX</td>
		    <td class="table_header">IconY</td>
		    <td class="table_header">Modify</td>		    
		</tr>
		<<:continut:>>
	    </table>
	</td>
    </tr>
</table>
