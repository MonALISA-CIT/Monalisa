<script type="text/javascript">
    var sLines = '<<:bLines:>>';
    var sRelations = '<<:bRelations:>>';

    var continent = <<:continent:>>;
    
    var heightOffset = <<:hoffset:>>;
    var heightSubstract = <<:hsub:>>;
    var widthOffset = <<:woffset:>>;
    var widthSubstract = <<:wsub:>>;
    
    var mapExtraOptions = "&flags=<<:mapflags enc:>>";
    
    function initLines(){
      try{
	bLines = false;
	bRelations = false;

	var sLinesCookie = Get_Cookie('googlemap_lines');
	var sRelationsCookie = Get_Cookie('googlemap_relations');

	if (sLinesCookie=='true')
	    bLines=true;
	else
	if (sLinesCookie=='false')
	    bLines=false;
	else
	if (sLines=='true')
	    bLines = true;
	else
	if (sLines=='false')
	    bLines=false;

	if (sRelationsCookie=='true')
	    bRelations=true;
	else
	if (sRelationsCookie=='false')
	    bRelations=false;
	else
	if (sRelations=='true')
	    bRelations = true;
	else
	if (sRelations=='false')
	    bRelations=false;

	document.getElementById('lines').checked=bLines;
	//document.getElementById('relations').checked=bRelations;

	if (bLines){
	    manageLines(0);
	}

	if (bRelations){
	    //manageLines(1);
	}	
      }
      catch (ex){
        alert(ex);
      }
    }
</script>
<script src="http://maps.google.com/maps?file=api&amp;v=2.109&amp;key=<<:map_key:>>" type="text/javascript"></script>
<script src="/js/map/labeled_marker.js" type="text/javascript"></script>
<script src="/js/map/map_functions.js" type="text/javascript"></script>
<table width="100%" height="100%" cellspacing=0 cellpadding=0 border=0>
    <tr>
	<td>
	    <div id="harta_centru">
    	    <div id="content" align="left">
	        <div id="map-wrapper">
		    <div id="map"></div>
		</div>
	    </div>
	    </div>
	</td>
    </tr>
    <tr>
	<td align="center">
	    <table align=center cellspacing="5" class="small" width=100%>
		<tr class="text" style="color:#000000">
		    <td nowrap width=40%>&nbsp;</td>
    		    <td nowrap width="12" align="center" valign="middle"><img src="/js/map/green.png" width="15" height="15"></td>
    		    <td nowrap onMouseOver="overlib('Some jobs are running on the site')" onMouseOut="nd()">Running jobs &nbsp;</td>
    		    <td nowrap width="12" align="center" valign="middle"><img src="/js/map/orange.png" width="15" height="15"></td>
    		    <td nowrap onMouseOver="overlib('Jobs seem to be running on site but MonaLisa is not running')" onMouseOut="nd()">Running jobs but no ML info &nbsp;</td>
    		    <td nowrap width="12" align="center" valign="middle"><img src="/js/map/yellow.png" width="15" height="15"> </td>
    		    <td nowrap onMouseOver="overlib('JobAgents are submitted to the batch queueing system but are not executed')" onMouseOut="nd()">Site service problem(s) prevents job execution &nbsp;</td>
    		    <td nowrap width="12" align="center" valign="middle"><img src="/js/map/blue.png" width="15" height="15"> </td>
    		    <td nowrap onMouseOver="overlib('No jobs in the queue matche the advertised resources')" onMouseOut="nd()">No jobs match the site resources &nbsp;</td>
    		    <td nowrap width="12" align="center" valign="middle"><img src="/js/map/red.png" width="15" height="15"></td>
    		    <td nowrap onMouseOver="overlib('MonaLisa service is not running on the VoBox and also no jobs seem to be running there')" onMouseOut="nd()" >ML service down &amp; no running jobs </td>
    		    <td nowrap width=40% align=right>
    			<a target=_blank class="link" href="http://www.gorissen.info/Pierre/maps/googleMapLocationv3.php"><u>Find your location</u></a>
    		    </td>
		</tr>
	    </table>
	</td>
    </tr>
    <tr>
	<td align="center" class="text" nowrap>
	    <span onclick="switchDiv('div_map_options', true, 0.3);"><a  onclick="switchDiv('div_map_options', true, 0.3);return false;" href="javascript:void(0);" class="menu_link"><b>Map options</b> <img id="div_map_options_img" src="/img/dynamic/plus.jpg" width="9" height="9" border="0"></a></span>
    	    <div id="div_map_options" style="display: none">
	        <div>
	    	    <input type="checkbox" name="lines" id="lines" value="1" <<:lines_checked:>> onclick="manageLines(0)"><label for="lines">Show xrootd transfers</label>
	    	    <!--
		    <input type="checkbox" name="relations" id="relations" value="1" <<:relations_checked:>> onclick="manageLines(1)"><label for="relations">Show site relations</label>
		    -->
		    <br />
		    Jump to: 
		    <input type="submit" name="buton1" value="Europe" class="input_submit" onclick="gotoCoordinates(0);return false;">
		    <input type="submit" name="buton2" value="North America" class="input_submit" onclick="gotoCoordinates(1);return false;">	    
		    <input type="submit" name="buton3" value="South America" class="input_submit" onclick="gotoCoordinates(2);return false;">
		    <input type="submit" name="buton4" value="Asia" class="input_submit" onclick="gotoCoordinates(3);return false;">
		    <input type="submit" name="buton5" value="World" class="input_submit" onclick="gotoCoordinates(4);return false;">
		    
		    &nbsp;&nbsp;&nbsp;&nbsp;
		    
		    <input type="submit" name="buton0" value="Save position and options" class="input_submit" onclick="setDefaultCoordinates();return false;" style="font-weight:bold">
	    	</div>
	    </div>
	</td>
    </tr>
</table>
