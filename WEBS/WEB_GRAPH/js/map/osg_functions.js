/**
* MonAlisa map functions
* last version : 4-06-2007
*/

/*
* Center coordinates for continents
*Europe, America N, America S, Asie
*/
var centers = [{'lat': 50.643, 'long': 12.853, 'zoom': 4}, { 'lat':28.098, 'long':-101.777, 'zoom': 4}, {'lat':-10.660, 'long':-55.898, 'zoom' : 4}, { 'lat':28.381, 'long':86.308, 'zoom':4},  { 'lat':45.583, 'long':-18.281, 'zoom':2}];
var map, manager;
//default values for center
var centerLatitude = 50.643, centerLongitude = 12.853;
//default value for zoom
var startZoom = 4;

var vRelations = new Array();
var vXrootd = new Array();

function importanceOrder (marker, b){
    return GOverlay.getZIndex(marker.getPoint().lat()) + marker.importance*1000000;
}

var iCreationOrder = 0;

/**
* creates a normal Marker based on the information received in a array "point"
* the "point" information is received from a xml file
*/
function createMarker(pointData) {
	var latlng = new GLatLng(pointData["latitude"], pointData["longitude"]);
	var icon = new GIcon(false, pointData["image"]);

	icon.iconSize = new GSize(15, 15);
	//pozitia la icoana(6, 20)
	icon.iconAnchor = new GPoint(7-parseInt(pointData["iconx"]), 7-parseInt(pointData["icony"]));
	icon.infoWindowAnchor = new GPoint(25, 7);

	var marker = new GMarker(latlng, {icon:icon,zIndexProcess:importanceOrder});

	var timeouts = []; 

	GEvent.addListener(marker, "click", function() {
		timeouts.push(setTimeout(function(){marker.openInfoWindowHtml(pointData["label"])}, 250));
	});

	GEvent.addListener(marker, "dblclick", function(){
	    for(var i=timeouts.length-2;i<timeouts.length;i++)
		clearTimeout(timeouts[i]);
		
      		 window.open(pointData["link"], "window_"+pointData["label"]);
	});
	
	marker.importance = iCreationOrder++;

	return marker;
}

/**
* creates a labeled Marker based on the information received in a array "point"
* the "point" information is received from a xml file
*/
function createLabeledMarker(pointData) {
	var latlng = new GLatLng(pointData["latitude"], pointData["longitude"]);
	var icon = new GIcon(false, pointData["image"]);

	//icon.image = 'http://labs.google.com/ridefinder/images/mm_20_green.png';
	
	icon.iconSize = new GSize(15, 15);
	//poztia la icoana
	icon.iconAnchor = new GPoint(7-parseInt(pointData["iconx"]), 7-parseInt(pointData["icony"]));
	icon.infoWindowAnchor = new GPoint(25, 7);
	
	opts = {
		"icon": icon,
		"clickable": true,
		"draggable": false,
		"labelText": pointData["abbr"],
		"zIndexProcess":importanceOrder,
		"labelOffset": new GSize(parseInt(pointData["labelx"])+parseInt(pointData["iconx"]), parseInt(pointData["labely"])+parseInt(pointData["icony"])) // 5, -25
	};
	                                  
	var marker = new LabeledMarker(latlng, opts);

	var timeouts = []; 

	GEvent.addListener(marker, "click", function() {
		timeouts.push(setTimeout( function(){marker.openInfoWindowHtml(pointData["label"])}, 250));
	});

	GEvent.addListener(marker, "dblclick", function(){
	    for(var i=timeouts.length-2;i<timeouts.length;i++)
		clearTimeout(timeouts[i]);

 	     window.open(pointData["link"], "window_"+pointData["label"]);		

//	    e.cancelBubble = true;
        
//    	    if (e.stopPropagation) e.stopPropagation(); 
	});	

    
    
	return marker;
}



var geoKMLFiles = [	
    'http://maps.google.com/maps/ms?ie=UTF8&hl=en&msa=0&output=nl&msid=106869281552388441478.0000011286a432ec0c354',
    'http://maps.google.com/maps/ms?ie=UTF8&hl=en&msa=0&output=nl&msid=106869281552388441478.0000011284e692752e782'
];

function gotoCoordinates(iContinent){
    map.setZoom(centers[iContinent]["zoom"]);
    map.panTo(new GLatLng(centers[iContinent]["lat"], centers[iContinent]["long"]));
}

function setDefaultCoordinates(){
    Set_Cookie("gmap_center", "true", 300*24*60*60);
    Set_Cookie("gmap_lat" , map.getCenter().lat(), 300*24*60*60); 
    Set_Cookie("gmap_lng", map.getCenter().lng(), 300*24*60*60);
    Set_Cookie("gmap_zoom", map.getZoom(), 300*24*60*60);
    
    Set_Cookie("googlemap_lines", document.getElementById("lines").checked ? "true" : "false");
    Set_Cookie("googlemap_relations", document.getElementById("relations").checked ? "true" : "false");
    
    alert("Your settings have been saved. The next visit will take you directly to the current view.");
}

function init() {
	handleResize();
	
    
	map = new GMap2(document.getElementById("map"));
	map.addControl(new GLargeMapControl());
	

	
	//setting the default coordinates
	//check if we have something in the cookies
	if(Get_Cookie("gmap_center") == "true"){
	    var cookieLat = parseFloat(Get_Cookie("gmap_lat"));
	    var cookieLng = parseFloat(Get_Cookie("gmap_lng"));
	    var cookieZoom = parseInt(Get_Cookie("gmap_zoom"));

	    map.setCenter(new GLatLng(cookieLat, cookieLng), cookieZoom);	    
	}
	else{
	    map.setCenter(new GLatLng(centers[continent]["lat"], centers[continent]["long"]), centers[continent]["zoom"]);
	}
	
	map.addControl(new GMapTypeControl());
	map.addControl(new GScaleControl());
	map.setMapType(G_SATELLITE_MAP);
	map.enableScrollWheelZoom();
	map.enableDoubleClickZoom();

	for (iKML=0; iKML<geoKMLFiles.length; iKML++){
	    //alert(geoKMLFiles[iKML]);
	    map.addOverlay(new GGeoXml(geoKMLFiles[iKML]));
	}
	
        var side_bar_html = "";
        var gmarkers = [];
        var htmls = [];
        var i = 0;
	var batch = [];
	var batch1 = [];

	manager = new GMarkerManager(map);

        GDownloadUrl("osg_data.jsp?relations=true&lines=true"+mapExtraOptions, function(data, responseCode){
            var xml = GXml.parse(data);
            var markers = xml.documentElement.getElementsByTagName("marker");

            for (var i = 0; i < markers.length; i++) {
                    // obtain the attribues of each marker

                var lat = markers[i].getAttribute("lat");
                var lng = markers[i].getAttribute("lng");
                var html = markers[i].getAttribute("html");
                var abbr  = markers[i].getAttribute("abbr");
                var name = markers[i].getAttribute("name");
                var image = markers[i].getAttribute("image");
                var link = markers[i].getAttribute("link")
                var labelx = markers[i].getAttribute("labelx")
                var labely = markers[i].getAttribute("labely")
                var iconx = markers[i].getAttribute("iconx")
                var icony = markers[i].getAttribute("icony")

                var vMarker = {'abbr' : abbr, 'name': name, 'label' : html, 'latitude' : lat, 'longitude': lng,
                                'image': image, 'link': link, 'labelx': labelx, 'labely': labely, 'iconx' : iconx, 'icony': icony};

                if(lng  != "N/A" && lat != "N/A"){
                    lng = parseFloat(lng);
                    lat = parseFloat(lat);

                    //map.addOverlay(createLabeledMarker(vMa1;3Brker));
            	    batch.push(createLabeledMarker(vMarker));
                    batch1.push(createMarker(vMarker));
                }
            }

            manager.addMarkers(batch, 4);
            manager.addMarkers(batch1, 1, 3);

            manager.refresh();
            
            var lines = xml.documentElement.getElementsByTagName("line_xrootd");
                // read each line

                for (var a = 0; a < lines.length; a++) {
                    //get any line attributes
                    var colour = lines[a].getAttribute("colour");
                    var width  = parseFloat(lines[a].getAttribute("width"));

                    // read each point on that line
                    var points = lines[a].getElementsByTagName("point");
                    var pts = [];

                    for (var i = 0; i < points.length; i++) {
                        pts[i] = new GLatLng(parseFloat(points[i].getAttribute("lat")), parseFloat(points[i].getAttribute("lng")));
                }

                //map.addOverlay(new GPolyline(pts,colour,width));
                vXrootd.push(new GPolyline(pts,colour,width));
            }


                // ========= Now process the polylines ===========
        	var lines = xml.documentElement.getElementsByTagName("line_relations");
                    // read each line

                for (var a = 0; a < lines.length; a++) {
                //get any line attributes
                    var colour = lines[a].getAttribute("colour");
                    var width  = parseFloat(lines[a].getAttribute("width"));

                    // read each point on that line
                    var points = lines[a].getElementsByTagName("point");
                    var pts = [];

                    for (var i = 0; i < points.length; i++) {
                        //alert(points[i].getAttribute("lat")+" -- "+points[i].getAttribute("lng"));
                        pts[i] = new GLatLng(parseFloat(points[i].getAttribute("lat")), parseFloat(points[i].getAttribute("lng")));
                    }
            	    
            	//   map.addOverlay(new GPolyline(pts,colour,width));
            	vRelations.push(new GPolyline(pts,colour,width));
            	
    	}

	    initLines();
	});
	

}


function manageLines(iType){
    var sLine = iType == 0 ? "lines" : "relations";
    
    if(document.getElementById(sLine).checked == true)
	drawLines(iType);
    else
	removeLines(iType);
}

function drawLines(iType){
    var vLines = iType == 0 ? vXrootd : vRelations;

    for (var a = 0; a < vLines.length; a++) {
    	    map.addOverlay(vLines[a]);
    }
}

function removeLines(iType){
    var vLines = iType == 0 ? vXrootd : vRelations;

    for (var a = 0; a < vLines.length; a++) {
    	    map.removeOverlay(vLines[a]);
    }
}

/**
* get window height
*/
function windowHeight() {
	// Standard browsers (Mozilla, Safari, etc.)
	if (self.innerHeight)
		return self.innerHeight;
	// IE 6
	if (document.documentElement && document.documentElement.clientHeight)
		return document.documentElement.clientHeight;
	// IE 5
	if (document.body)
		return document.body.clientHeight;
	// Just in case. 
	return 0;
}

/**
* get window width
*/
function windowWidth() {
	// Standard browsers (Mozilla, Safari, etc.)
	if (self.innerWidth)
		return self.innerWidth;
	// IE 6
	if (document.documentElement && document.documentElement.clientHeight)
		return document.documentElement.clientWidth;
	// IE 5
	if (document.body)
		return document.body.clientWidth;
	// Just in case. 
	return 0;
}

/**
* resize of the window for the map to fit the screen
*/
function handleResize() {
	var height = windowHeight() - heightSubstract;
	document.getElementById('map').style.height = height + 'px';
	document.getElementById('harta_centru').style.height = (height+heightOffset) + 'px';
	document.getElementById('harta_centru').style.width = (windowWidth()-(widthSubstract-widthOffset)) + 'px';
	document.getElementById('map').style.width = (windowWidth()-widthSubstract) + 'px';
}

window.onresize = handleResize;
window.onload = init;
window.onunload = GUnload;
