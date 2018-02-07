	<html>
	<head>
	<script type="text/javascript" src="/overlib/overlib.js"></script>
	<script type="text/javascript" src="/overlib/overlib_crossframe.js"></script>
	<title>MonALISA WEB Repository</title>
	<style>
	    a:link 		{font-family: verdana, arial, sans-serif; font-size: 11px; text-decoration: none; font-weight: normal; color: #0000cc;}
	    a:hover 		{font-family: verdana, arial, sans-serif; font-size: 11px; text-decoration: none; font-weight: normal; color: #ff6600;}
	    a:visited 		{font-family: verdana, arial, sans-serif; font-size: 11px; text-decoration: none; font-weight: normal; color: #0000cc;}
	    a:visited:hover 	{font-family: verdana, arial, sans-serif; font-size: 11px; text-decoration: none; font-weight: normal; color: #ff6600;}
	    a:visited:active	{font-family: verdana, arial, sans-serif; font-size: 11px; text-decoration: none; font-weight: normal; color: #0000cc;}
	    a:active 		{font-family: verdana, arial, sans-serif; font-size: 11px; text-decoration: none; font-weight: normal; color: #0000cc;}
	</style>
	
	<script language=JavaScript>
	    ol_hauto=0;
	    ol_vauto=0;
	    //ol_width=300;
	    //ol_height=230;
	    ol_offsetx=0;
	    ol_offsety=160;
	    ol_frame=parent;
	</script>
	                            
	
	<SCRIPT language="JavaScript1.2" type="text/JavaScript">
	var IMG_WIDTH=800;
	var IMG_HEIGHT=IMG_WIDTH/2;
	
	var posX=-1, posY=-1, theimage;
	
	var docLayers = (document.layers) ? true:false;
	var docId = (document.getElementById) ? true:false;
	var docAll = (document.all) ? true:false;
	var docbitK = (docLayers) ? "document.layers['":(docId) ? "document.getElementById('":(docAll) ? "document.all['":"document."
	var docbitendK = (docLayers) ? "']":(docId) ? "')":(docAll) ? "']":""
	var stylebitK = (docLayers) ? "":".style"
	var showbitK = (docLayers) ? "show":"visible"
	var hidebitK = (docLayers) ? "hide":"hidden"
	var ns6=document.getElementById&&!document.all;
	var sit_home="http://pcalimonitor.pccil.cern.ch:8889/";
	
	function init()
	{
	    if ( parent.posX && parent.posX!=-1 )
		posX = parent.posX;
	    if ( parent.posY && parent.posY!=-1 )
		posY = parent.posY;
	    if (!(document.getElementById || document.all || document.layers)) return;
	
		//document.getElement.oncontextmenu = oncontextmenu;
		document.getElementById("Map2D").onmousedown = checkIt; 
		//document.images[i].onmouseup = checkIt; {return false};
	}

function checkIt(e)
{
	var but=0;//mouse button pressed: 0 is none, 1 is left, -1 is right
	if (window.Event) {
		but = (e.which == 3)?-1:but;
		but = (e.which == 1)?1:but;
	} else {
	    but = (window.event.button == 2)?-1:but;
	    but = (window.event.button == 1)?1:but;
	}
        if(!(posX==undefined || posY==undefined) && posX!=-1 && posY!=-1 )
	    parent.position_zoom( but,posX,posY,findPosX(document.getElementById('Map2D')),findPosY(document.getElementById('Map2D')));
	return false;
}
	
	function findPosX(obj)
	{
	var curleft = 0;
	if (obj.offsetParent)
	{
		while (obj.offsetParent)
		{
		curleft += obj.offsetLeft
		obj = obj.offsetParent;
		}
	}
	else if (obj.x)
		curleft += obj.x;
	return curleft;
	}
	function findPosY(obj)
	{
	var curtop = 0;
	if (obj.offsetParent)
	{
		while (obj.offsetParent)
		{
		curtop += obj.offsetTop
		obj = obj.offsetParent;
		}
	}
	else if (obj.y)
		curtop += obj.y;
	return curtop;
	}
	
	//Collection of functions to get mouse position and place the images
	function doCapture(e) 
	{	
		aux = getMouseXPos(e);
		if ( ! (aux == undefined ) )
			posX = aux;
		aux = getMouseYPos(e);
		if ( ! (aux == undefined ) )
			posY = aux;
		image = document.getElementById("Map2D");
		image_x = findPosX(image);
		image_y = findPosY(image);
	}
	// Get the horizontal position of the mouse
	function getMouseXPos(e) {
		if (document.layers||ns6)
			return parseInt(e.pageX);
		return (parseInt(event.clientX) + parseInt(document.body.scrollLeft));
	}
	// Get the vartical position of the mouse
	function getMouseYPos(e) {
		if (document.layers||ns6)
			return parseInt(e.pageY)
		return (parseInt(event.clientY) + parseInt(document.body.scrollTop))
	}
	
	function positionID(x,y,id) {
		eval(docbitK + id + docbitendK + stylebitK + ".left = " + x);
		eval(docbitK + id + docbitendK + stylebitK + ".top = " + y);
	}
	function showID(id) {
		eval(docbitK + id + docbitendK + stylebitK + ".visibility = '" + showbitK + "'");
	}
	function hideID(id) {
		eval(docbitK + id + docbitendK + stylebitK + ".visibility = '" + hidebitK + "'");
	}
	
	function capturebegin(){
		//Let the browser know when the mouse moves
    		document.onmousemove = doCapture;
		if (!document.all) {//if anything else but IE
		    document.captureEvents(Event.MOUSEMOVE);
		};
	}

	


	function startUp()
	{
		capturebegin();
		init();
	}
	</script>


	<style><!--
	.lupa {
	position:absolute;
	top:0;
	left:0;
	visibility:hidden;
	}
	//-->
	</style>
	
	<style type="text/css">
	<!--body {   margin-top: 0px;  margin-right: 0px;  margin-bottom: 0px;  margin-left: 0px}
	-->
	</style>


	</head>

	<body onLoad="javascript:startUp();" oncontextmenu="return false;"> <!--onLoad="javascript:startUp();"-->
	<div id="overDiv" style="position:absolute; visibility:hidden; z-index:1000;"></div>
	<map name=imgmap><<:map:>></map>
	<img src="http://pcalimonitor.cern.ch/display?image=<<:image:>>" id="Map2D" name="Map2D" usemap=#imgmap border=0 class="disabledMenu">
	<div id="mesaje"></div>

	</body>
	</html>
