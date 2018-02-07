<html>
    <head>
	<title><<:title:>></title>
	<link type="text/css" rel="StyleSheet" href="/style.css" />
	<LINK REL="SHORTCUT ICON" HREF="/favicon.gif" type="image/gif">
	<LINK REL="ICON" HREF="/favicon.gif" type="image/gif">
	<script type="text/javascript" src="/overlib/overlib.js"></script>
	<script type="text/javascript" src="/overlib/overlib_crossframe.js"></script>
	
	<script type="text/javascript" src="/js/window/prototype.js"> </script>
	<script type="text/javascript" src="/js/window/window.js"> </script> 
	<script type="text/javascript" src="/js/window/windowutils.js"> </script> 
	<!-- Add this to have a specific theme--> 
	<link href="/js/window/default.css" rel="stylesheet" type="text/css"></link>
	<link href="/js/window/mac_os_x.css" rel="stylesheet" type="text/css"></link> 
	
<style>
a.link:link,
a.link:active,
a.link:visited{
	font-family: Verdana, Arial, Helvetica, sans-serif;
	font-size: 11px;
	font-weight: bold;
	color: #3A6C9A;
	text-decoration: none
}

a.link:hover{
	font-family: Verdana, Arial, Helvetica, sans-serif;
	font-size: 11px;
	font-weight: bold;
	color: #3A6C9A;
	text-decoration: underline
}                                                


input.input_text{
	    font-family: Verdana, Arial, Tahome;
	    font-size: 11px;
	    font-weight: normal;
	    text-decoration: none;
	    text-align: left;
	    border: solid 1px #CCCC99;
	    background: #FFFFFF;
}

input.input_submit{
	    font-family: Verdana, Arial, Tahome;
	    font-size: 11px;
	    color: #4F4F3B;
	    font-weight: normal;
	    text-decoration: none;
	    border: solid 1px #CCCC99;
	    background: #F6F6F6;
}

	    
select.input_select{
	    font-family: Verdana, Arial, Tahome;
	    font-size: 11px;
	    color: #4F4F3B;
	    font-weight: normal;
	    text-decoration: none;
	    border: solid 1px #CCCC99;
	    background: #F6F6F6;
}
</style>
    </head>
    <body>
	<div id="overDiv" style="position:absolute; visibility:hidden; z-index:1000;"></div>
	<script language=javascript>
	    //window.focus();
	
	    function deleteId(id){
		if (confirm('Are you sure you want to delete annotation #'+id+' ?')){
		    window.open('annotation.jsp?del_id='+id, 'ann_edit', 'toolbar=0,width=430,height=350,scrollbars=1,resizable=1,titlebar=1');
		}
	    }
	    
	    function editId(id){
		window.open('annotation.jsp?a_id='+id+'&series_names=<<:sSeriesNames:>>&groups=<<:sGroups:>>', 'ann_edit', 'toolbar=0,width=500,height=550,scrollbars=1,resizable=1,titlebar=1');
	    }
	</script>
    
	<br>
	<a href="#" onClick="editId(0); return false;" class=alternate><img src="/img/filenew.png" border=0></a>&nbsp;<a href="#" onClick="editId(0); return false;" class=alternate><b>New annotation</b></a><br>
	<br>
	<table border=0 cellspacing=0 cellpadding=2 bgcolor=#DDDDDD class=alternate>
	    <tr>
		<td>
		    <table border=0 cellspacing=1 cellpadding=2 class="alternate" bgcolor=#9D9AAF>
			<<:filter:>>
			<<:continut:>>		
		    </table>
		</td>
	    </tr>
	    <tr>
		<td>
		    <<:totalmatch:>> matched your search.
		</td>
	    </tr>
	</table>
    </body>
</html>
