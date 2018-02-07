// DHTML Color Picker
// Programming by Ulyses
// ColorJack.com

function $(v){
    return(document.getElementById(v));
}

function $S(v) {
    return($(v).style);
}

function agent(v) {
    return(Math.max(navigator.userAgent.toLowerCase().indexOf(v),0));
}

function toggle_picker(v){
    $S(v).display=($S(v).display=='none'?'block':'none');
    sourceNode = "";
}

function within(v,a,z){
    return((v>=a && v<=z) ? true:false);
}

function XY(e,v) {
    var z=agent('msie') ? [event.clientX+document.body.scrollLeft,event.clientY+document.body.scrollTop]:[e.pageX,e.pageY];
    return(z[zero(v)]);
}

function XYwin(v) {
    var z=agent('msie')?[document.body.clientHeight ,document.body.clientWidth]:[window.innerHeight, window.innerWidth];
    return(!isNaN(v)?z[v]:z); 
}

function zero(v) {
    v=parseInt(v);
    return(!isNaN(v)?v:0);
}

/* PLUGIN */

var maxValue={'h':360,'s':100,'v':100}, HSV={0:360,1:100,2:100};
var hSV=165, wSV=162, hH=163, slideHSV={0:360,1:100,2:100}, zINDEX=15, stop=1;
var sourceNode = ""; var  initialColor = [0,0,0];

//onmousedown="HSVslide('SVslide','plugin',event)"
function HSVslide(d,o,e) {
    function tXY(e) {
	tY=XY(e,1)-top;
	tX=XY(e)-left; 
    }
    
    function mkHSV(a,b,c) {
	return(Math.min(a,Math.max(0,Math.ceil((parseInt(c)/b)*a))));
    }
    
    /**
    * check if a is in (0, b) interval
    * if not then returns b or the minimum limit
    */
    function ckHSV(a,b) {
	if(within(a,0,b)) return(a); 
	else if(a>b) return(b); 
	else if(a<0) return('-'+oo);
    }

    function drag(e) { 
	if(!stop){ 
	    if(d!='drag') tXY(e);
    
	    if(d=='SVslide'){
		ds.left=ckHSV(tX-oo,wSV)+'px';
		ds.top=ckHSV(tY-oo,wSV)+'px';
	
		slideHSV[1]=mkHSV(100, wSV, ds.left);
		slideHSV[2]=100-mkHSV(100, wSV, ds.top);
	
		HSVupdate(null, sourceNode);
	    }
	    else if(d=='Hslide'){
		var ck=ckHSV(tY-oo,hH), j, r='hsv', z={};
		ds.top=(ck-5)+'px'; 
		slideHSV[0]=mkHSV(360,hH,ck);
		
		for(var i=0; i<=r.length-1; i++) {
		    j=r.substr(i,1); 
		    z[i]= (j == 'h') ? maxValue[j] - mkHSV(maxValue[j],hH,ck) : HSV[i];
		}

		HSVupdate(z, sourceNode);
		
		$S('SV').backgroundColor = '#'+hsv2hex([HSV[0],100,100]);
	    }
	    else if(d=='drag'){
		ds.left=XY(e)+oX-eX+'px';
		ds.top=XY(e,1)+oY-eY+'px';
	    }
	}
    }

    if(stop){
	stop=''; 

	var ds = $S(d!='drag' ? d : o);

	if(d=='drag') {
	    var oX=parseInt(ds.left), oY=parseInt(ds.top), eX=XY(e), eY=XY(e,1); 
	    $S(o).zIndex=zINDEX++; 
	}
	else {
	    var left=($(o).offsetLeft+10), top=($(o).offsetTop+22), tX, tY, oo=(d=='Hslide') ? 2:4;
	    
	    if(d=='SVslide'){
		slideHSV[0]=HSV[0];
	    }
	}

	document.onmousemove=drag; 
	document.onmouseup=function(){ stop=1; document.onmousemove=''; document.onmouseup=''; }; 
	drag(e);
    }
}

function HSVupdate(v, source) {
    v=hsv2hex(HSV = v ? v : slideHSV);
    
    $('plugHEX').innerHTML=v;
    $S('plugCUR').background='#'+v;
    $S('color_image_'+source).background='#'+v;
    $S('input_image_'+source).background='#'+v;    
    $('tier_color_'+source).value='#'+v;
    
    return(v);

}

function loadSV(source) { var z='';

    for(var i=hSV; i>=0; i--) z+="<div style=\"BACKGROUND: #"+hsv2hex([Math.round((360/hSV)*i), 100, 100])+";\"><br /><\/div>";
    
    $('Hmodel').innerHTML=z;
    
}

/* CONVERSIONS */

function toHex(v) { v=Math.round(Math.min(Math.max(0,v),255)); return("0123456789ABCDEF".charAt((v-v%16)/16)+"0123456789ABCDEF".charAt(v%16)); }
function rgb2hex(r) { return(toHex(r[0])+toHex(r[1])+toHex(r[2])); }
function hsv2hex(h) { return(rgb2hex(hsv2rgb(h))); }	

function hsv2rgb(r) { // easyrgb.com/math.php?MATH=M21#text21

    var R,B,G,S=r[1]/100,V=r[2]/100,H=r[0]/360;

    if(S>0) { if(H>=1) H=0;

        H=6*H; F=H-Math.floor(H);
        A=Math.round(255*V*(1.0-S));
        B=Math.round(255*V*(1.0-(S*F)));
        C=Math.round(255*V*(1.0-(S*(1.0-F))));
        V=Math.round(255*V); 

        switch(Math.floor(H)) {

            case 0: R=V; G=C; B=A; break;
            case 1: R=B; G=V; B=A; break;
            case 2: R=A; G=V; B=C; break;
            case 3: R=A; G=B; B=V; break;
            case 4: R=C; G=A; B=V; break;
            case 5: R=V; G=A; B=B; break;

        }

        return([R?R:0,G?G:0,B?B:0]);

    }
    else return([(V=Math.round(V*255)),V,V]);

}

function HSVobject (hue, saturation, value) {
    // Object definition.
    this.h = hue; this.s = saturation; this.v = value;
    this.validate = function () {
	if (this.h <= 0) {this.h = 0;}
	if (this.s <= 0) {this.s = 0;}
	if (this.v <= 0) {this.v = 0;}
	if (this.h > 360) {this.h = 360;}
	if (this.s > 100) {this.s = 100;}
	if (this.v > 100) {this.v = 100;}
    };
}

function RGBobject (red, green, blue) {
    // Object definition.
    this.r = red; this.g = green; this.b = blue;
    this.validate = function () {
	if (this.r <= 0) {this.r = 0;}
	if (this.g <= 0) {this.g = 0;}
	if (this.b <= 0) {this.b = 0;}
	if (this.r > 255) {this.r = 255;}
	if (this.g > 255) {this.g = 255;}
	if (this.b > 255) {this.b = 255;}
    };
}

function hexify(number) {
    var digits = '0123456789ABCDEF';
    var lsd = number % 16;
    var msd = (number - lsd) / 16;
    var hexified = digits.charAt(msd) + digits.charAt(lsd);
    return hexified;
}

function decimalize(hexNumber) {
    var digits = '0123456789ABCDEF';
    return ((digits.indexOf(hexNumber.charAt(0).toUpperCase()) * 16) + digits.indexOf(hexNumber.charAt(1).toUpperCase()));
}

function hex2RGB (colorString) {
    var RGB = new RGBobject();
    RGB.r = decimalize(colorString.substring(1,3));
    RGB.g = decimalize(colorString.substring(3,5));
    RGB.b = decimalize(colorString.substring(5,7));
    
    return RGB;
}

function RGB2HSV (RGB) {
    var HSV = new HSVobject();

    r = RGB.r / 255; g = RGB.g / 255; b = RGB.b / 255; // Scale to unity.

    var minVal = Math.min(r, g, b);
    var maxVal = Math.max(r, g, b);
    var delta = maxVal - minVal;

    HSV.v = maxVal;

    if (delta == 0) {
	HSV.h = 0;
	HSV.s = 0;
    } else {
	HSV.s = delta / maxVal;
	var del_R = (((maxVal - r) / 6) + (delta / 2)) / delta;
	var del_G = (((maxVal - g) / 6) + (delta / 2)) / delta;
	var del_B = (((maxVal - b) / 6) + (delta / 2)) / delta;

	if (r == maxVal) {HSV.h = del_B - del_G;}
	else if (g == maxVal) {HSV.h = (1 / 3) + del_R - del_B;}
	else if (b == maxVal) {HSV.h = (2 / 3) + del_G - del_R;}
	
	if (HSV.h < 0) {HSV.h += 1;}
	if (HSV.h > 1) {HSV.h -= 1;}
    }
    HSV.h *= 360;
    HSV.s *= 100;
    HSV.v *= 100;

    return HSV;
}

function HSV2RGB (HSV, RGB) {
    var h = HSV.h / 360; var s = HSV.s / 100; var v = HSV.v / 100;
    if (s == 0) {
	RGB.r = v * 255;
	RGB.g = v * 255;
	RGB.v = v * 255;
    } else {
	var_h = h * 6;
	var_i = Math.floor(var_h);
	var_1 = v * (1 - s);
	var_2 = v * (1 - s * (var_h - var_i));
	var_3 = v * (1 - s * (1 - (var_h - var_i)));
	
	if (var_i == 0) {var_r = v; var_g = var_3; var_b = var_1}
	else if (var_i == 1) {var_r = var_2; var_g = v; var_b = var_1}
	else if (var_i == 2) {var_r = var_1; var_g = v; var_b = var_3}
	else if (var_i == 3) {var_r = var_1; var_g = var_2; var_b = v}
	else if (var_i == 4) {var_r = var_3; var_g = var_1; var_b = v}
	else {var_r = v; var_g = var_1; var_b = var_2};
	
	RGB.r = var_r * 255;
	RGB.g = var_g * 255;
	RGB.b = var_b * 255;
    }
}


function HSV2RGB (HSV) {
    var h = HSV.h / 360; var s = HSV.s / 100; var v = HSV.v / 100;
    if (s == 0) {
	RGB.r = v * 255;
	RGB.g = v * 255;
	RGB.v = v * 255;
    } else {
	var_h = h * 6;
	var_i = Math.floor(var_h);
	var_1 = v * (1 - s);
	var_2 = v * (1 - s * (var_h - var_i));
	var_3 = v * (1 - s * (1 - (var_h - var_i)));
	
	if (var_i == 0) {var_r = v; var_g = var_3; var_b = var_1}
	else if (var_i == 1) {var_r = var_2; var_g = v; var_b = var_1}
	else if (var_i == 2) {var_r = var_1; var_g = v; var_b = var_3}
	else if (var_i == 3) {var_r = var_1; var_g = var_2; var_b = v}
	else if (var_i == 4) {var_r = var_3; var_g = var_1; var_b = v}
	else {var_r = v; var_g = var_1; var_b = var_2};
	
	RGB.r = var_r * 255;
	RGB.g = var_g * 255;
	RGB.b = var_b * 255;
    }
}


/* LOAD */
function load_picker(source){
    var currentColor = $('tier_color_'+source).value;
    
    if(currentColor == '#'){
	currentColor = '#FFFFFF';
    }

    var HSV_currentColor = RGB2HSV(hex2RGB(currentColor));
    
    loadSV(source);
    
    if(agent('msie')){
	$S('plugin').left = ((XYwin()[1]/2)-220+document.body.scrollLeft)+'px'
	$S('plugin').top = (XYwin()[0]-350+document.body.scrollTop)+'px'
    }
    else{
	$S('plugin').left = ((XYwin()[1]/2)-220+window.pageXOffset)+'px'
	$S('plugin').top = (XYwin()[0]-350+window.pageYOffset)+'px'
    }
    
    
    
    $S('SVslide').left =HSV_currentColor.s * 162/100 - 4;
    $S('SVslide').top = 165 - (HSV_currentColor.v * 165/100 + 3);
//    $S('SVslide').top = 163 - 4;
//    $S('SVslide').left = 165 - 4;
    
    $S('Hslide').top =  163 - (HSV_currentColor.h*163/360 + 4);
//   $S('Hslide').top =  163 - 4;
    
    $S('plugCUR').background = currentColor;
    $('plugHEX').innerHTML = currentColor;
    
    $S('SV').backgroundColor = currentColor;

    $S('plugin').display='block';
    
        
    sourceNode = source;
    
    if(currentColor == '#FFFFFF'){
	HSV_currentColor = RGB2HSV(hex2RGB('#0000FF'));
	$S('SV').backgroundColor = '#0000FF';
    }
    
    initialColor = [HSV_currentColor.h, HSV_currentColor.s, HSV_currentColor.v];
    HSV[0] = HSV_currentColor.h;
//    alert(initialColor);    

    return false;
}


