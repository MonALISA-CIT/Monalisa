var IMG_WIDTH=800;
var IMG_HEIGHT=IMG_WIDTH/2;
var d3d=0;
var a3d=90;
var dx3d=.1;

var posX=-1, posY=-1, lastX, lastY, theimage;
//theimage = "lupa.gif" //the 2nd image to be displayed
//Browser checking and syntax variables
var docLayers = (document.layers) ? true:false;
var docId = (document.getElementById) ? true:false;
var docAll = (document.all) ? true:false;
var docbitK = (docLayers) ? "document.layers['":(docId) ? "document.getElementById('":(docAll) ? "document.all['":"document."
var docbitendK = (docLayers) ? "']":(docId) ? "')":(docAll) ? "']":""
var stylebitK = (docLayers) ? "":".style"
var showbitK = (docLayers) ? "show":"visible"
var hidebitK = (docLayers) ? "hide":"hidden"
var ns6=document.getElementById&&!document.all;
var sit_home="http://pcalice295.cern.ch:8180/";
//Variables used in script
	
var MAX_Y=8;
var MAX_X=16;
var width=5.6;
var zoom_factor=1;//poate varia intre 0.01 si 1
var height=width/2;
var x=-1.53;//-16;
var y=6;//8;
var image2;
var pred=1;
var show_links=1;
var show_speciallinks=0;
	
var click_action=1;//action for mouse left click on image

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
    
function showLinks(){
    if(document.getElementById("links_checkbox").checked)
	show_links=1;
    else
        show_links=0;

    zoom(0);
}

function showSpecialLinks(){
    if(document.getElementById("speciallinks_checkbox").checked)
	show_speciallinks=1;
    else
        show_speciallinks=0;

    zoom(0);
}

function map_changed(){
    dimensiune_obj = document.getElementById("map");
    valoare=dimensiune_obj.options[dimensiune_obj.selectedIndex].value;
    
    if(valoare.charAt(0)==2){
        width=32;
        height=width/2;
        x=-16;
        y=8;
        zoom(0);
    }
    
    if(valoare.charAt(0)==3){
	width=5.6;
        height=width/2;
        x=-1.53;
        y=6;
        zoom(0);
    }
    
    if(valoare.charAt(0)==1){
        width=4.91;
	height=width/2;
        x=-11;
        y=3.9;
        zoom(0);
    }
    
    if(valoare.charAt(0)==4){
        width=10.6;
        height=width/2;
        x=-11;
        y=0.95;
        zoom(0);
    }
    
    if(valoare.charAt(0)==5){
        width=10.6;
        height=width/2;
        x=3.54;
        y=4.8;
        zoom(0);
    }
}
 
function windowChanged(){
    var winW;
    if(document.layers || ns6)
        winW=window.innerWidth;
    else{
        winW=document.body.offsetWidth;
    }
    
    IMG_WIDTH=winW - 300;
    if (IMG_WIDTH < 400)
	IMG_WIDTH = 400;
    
    IMG_HEIGHT=IMG_WIDTH*.5;
    //document.getElementById('test').width=0.97*winW;
    
    document.getElementById('Map2D').width=winW - 280;
    
    if(document.getElementById('Map2D').width < 400)
	document.getElementById('Map2D').width = 400;
    
    document.getElementById('Map2D').height=document.getElementById('Map2D').width/2;
    document.getElementById('adaptive').selected=true;
    zoom(0);
}
   
function dimensiune_changed(){
    dimensiune_obj = document.getElementById("dimensiune");
    valoare=dimensiune_obj.options[dimensiune_obj.selectedIndex].value;
    new_w=0;
    i=0;
    for ( ; i< valoare.length; i++) {
	car = valoare.charAt(i);
	if  ( car>='0' && car <= '9' ) {
	    new_w=new_w*10+(car-'0');
	} else if ( car=='x' ) {
	    i++;//pass over'x'
	    break;
	}
	//else ignore
    }

    new_h=0;
    for ( ; i< valoare.length; i++) {
	car = valoare.charAt(i);
	if  ( car>='0' && car <= '9' )
	    new_h=new_h*10+(car-'0');
	    //else ignore
    }

    if ( new_w>0 && new_h>0 ) {
        IMG_WIDTH=new_w;
        IMG_HEIGHT=IMG_WIDTH*.5;
        document.getElementById("Map2D").width=new_w;
        document.getElementById("Map2D").height=new_w*.5;
        zoom(0);
    }
}
	
function findPosX(obj){
    var curleft = 0;
    if (obj.offsetParent){
        while (obj.offsetParent){
	    curleft += obj.offsetLeft
	    obj = obj.offsetParent;
	}
    }
    else 
        if (obj.x)
	    curleft += obj.x;
    return curleft;
}

function findPosY(obj){
    var curtop = 0;
    if (obj.offsetParent){
	while (obj.offsetParent){
	    curtop += obj.offsetTop
	    obj = obj.offsetParent;
	}
    }
    else 
	if (obj.y)
    	    curtop += obj.y;
    return curtop;
}

function position_zoom(zoomdir,lastx, lasty, imagex, imagey){
    //lastX=lastx;
    //lastY=lasty;
    posX=lastx;
    posY=lasty;
    click_action=zoomdir;
    image = document.getElementById("Map2D");
    //text="<br>x ant="+x+" and y ant="+y;
    if ( click_action==1 || click_action==-1 ) {
	imageX = imagex;//findPosX(image);
	imageY = imagey;//findPosY(image);
	if ( d3d>0 ) {
	    //clicked on 3d map, so change coordinates
	    //gresit!!!...
	    ctg=1/Math.tan(a3d*Math.PI/180);
	    extraX = (lasty-0.66*IMG_HEIGHT)*ctg;
	    lastx = (lastx+extraX)/(IMG_WIDTH+2*extraX)*IMG_WIDTH;
	    while ( lastx < imageX )
    	        lastx+=IMG_WIDTH;
	    while ( lastx > imageX+IMG_WIDTH )
    	        lastx-=IMG_WIDTH;
	}
	//document.getElementById("debug").innerHTML="x="+x+" y="+y+" w="+width+" h="+height+" lastX="+lastx+" lastY="+lasty+" imageX="+imageX+"imageY="+imageY;
	x += (lastx - imageX)*width/IMG_WIDTH - width*.5;
	y += -(lasty - imageY)*height/IMG_HEIGHT + height*.5;
    }
    zoom(click_action);
}

function zoom(dir){
    dir=(dir?(dir<0?-1:1):0);
    //limit zoom out
    if(width==32 && dir<0)
	return;

    if ( width*(dir?(dir<0?(1+zoom_factor):1/(1+zoom_factor)):1)>2*MAX_X && dir<0 ){
    	//return;
	var stop_zoom_out=1;
    }

    //limit zoom in
    if (width<MAX_X/16 && dir >0)
	return;

    //text+="<br>width="+width+" and height="+height;
    //text+="<br>x="+x+" and y="+y;
    //compute new widhts
    ww = width*(dir?(dir<0?(1+zoom_factor):1/(1+zoom_factor)):1);
    hh = height*(dir?(dir<0?(1+zoom_factor):1/(1+zoom_factor)):1);
    //text+="<br>new width="+ww+" and new height="+hh;
    //recompute new position
	
    //text+="<br>new x="+x+" and new y="+y;
    //set new widths
    if(stop_zoom_out==1){
	width=32;
	height=16;
    }
    else{
	x+=(width-ww)*.5;
	y+=(hh-height)*.5;
	width=ww;
	height=hh;
    }
    //height checking to limit map
    if ( y > MAX_Y )
	y = MAX_Y;
    if ( y < -MAX_Y+height )
	y = -MAX_Y+height;
    //text+="<br>new y after correction="+y;
    //document.getElementById("mesaje").innerHTML=text;
    setImageForLoad();
    //document.getElementById("Map2D").src="http://wn1.rogrid.pub.ro:8080/Map2D?page=wmap&w="+width+"&h="+height+"&x="+x+"&y="+y+"&d3d="+(d3d+0)+"&a3d="+a3d+"&dx3d="+dx3d;
}

function setImageForLoad(){
    hideID("div_tools");
    showID("div_load");
		
    document.getElementById("Map2D").src="/FarmMap?page=wmap1&w="+width+"&h="+height+"&x="+x+"&y="+y+"&d3d="+(d3d+0)+"&a3d="+a3d+"&dx3d="+dx3d+"&_W="+IMG_WIDTH+"&p="+pred+"&ckShowLinks="+show_links+"&ckShowSpecialLinks="+show_speciallinks;
    hideID("div_load");
    showID("div_tools");
    //checkLoad();
}
	
function move(dirx, diry){
    //miscarea se face cu un sfert din poza pe directia de miscare
    dirx=(dirx!=0?(dirx<0?-1:1):0);
    diry=(diry!=0?(diry<0?-1:1):0);
    //text="";
    //text+="<br>move before x="+x+" and y="+y;
    x+=dirx*width*zoom_factor/4;
    y+=diry*height*zoom_factor/4;
    //text+="<br>move after x="+x+" and y="+y;
    //height checking to limit map
    if ( y > MAX_Y )
	y = MAX_Y;
    if ( y < -MAX_Y+height )
	y = -MAX_Y+height;
    //text+="<br>move after2 x="+x+" and y="+y;
    //document.getElementById("mesaje").innerHTML=text;
    setImageForLoad();
}

function rotate(dir){
    step=10;
    if ( dir>0 ) {
	if ( 90-a3d>=step )
	    a3d+=step;
	else {
	    a3d=90;
	    d3d=0;
	}
    }
    else 
    if ( dir<0 ) {
	if ( 90-a3d<6*step ) {
	    d3d=1;
	    a3d-=step;
	}
    }

    document.getElementById("angle3d").value=a3d;
    zoom(0);
}
	
function checkLoad(){
    image = document.getElementById("Map2D");
    if ( image.width==IMG_WIDTH ) {
	//image.src=image2.src;
	hideID("div_load");
	showID("div_tools");
	return;
    }

    setTimeout("checkLoad();",200);
}

function startUp(){
    zoom(0);
    hideID("load_legend");
    showID("total_jobs_legend");
    //showID("jobs_vo_legend");
    //capturebegin();
}

function toggleBox(szDivID, iState) // 1 visible, 0 hidden
{
    if(document.layers)	   //NN4+
    {
	document.layers[szDivID].visibility = iState ? "show" : "hide";
    }
    else if(document.getElementById)	  //gecko(NN6) + IE 5+
    {
	var obj = document.getElementById(szDivID);
        obj.style.visibility = iState ? "visible" : "hidden";
    }
    else if(document.all)	// IE 4
    {
	document.all[szDivID].style.visibility = iState ? "visible" : "hidden";
    }
}

function changeWidth(){
    var winW;
    if(document.layers || ns6)
        winW=window.innerWidth;
    else{
        winW=document.body.offsetWidth;
    }
    IMG_WIDTH=winW - 280;//780;
    IMG_HEIGHT=IMG_WIDTH/2;
    document.getElementById('test').width=0.97*winW;
    document.getElementById('Map2D').width=winW - 280;
    document.getElementById('Map2D').height=(winW - 280)/2;
    //alert(document.getElementById('Map2D').width);
}
