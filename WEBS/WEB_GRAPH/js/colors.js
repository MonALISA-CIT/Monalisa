function cifra(i){
	if (i<10) {
	    return ''+i;
	}
	else {
	    if (i==10) return 'A';
	    if (i==11) return 'B';
	    if (i==12) return 'C';
	    if (i==13) return 'D';
	    if (i==14) return 'E';
	    if (i==15) return 'F';
	}
	
	return 'X';
}
    
function hexa(i){
	if (i<0) return '00';
	if (i>255) return 'FF';
    
	return cifra(Math.floor(i/16))+cifra(Math.floor(i%16));
}
    
function getColor(r,g,b){
	return hexa(r)+hexa(g)+hexa(b);
}
    
function patratel(r,g,b){
	var color=getColor(r,g,b);
	
	return '<td bgcolor=#'+color+' width=8 height=8';
}
    
var gri=10;
    
function sel(r,g,b, param, style, sid, sBox){
	showColor('#'+getColor(r,g,b), param, style, sid, sBox);
}
    
function showPopup(r,g,b, param, style, sid, sBox){
	var text='<table border=0 cellspacing=1 cellpadding=0><tr>';
	
	for (k=gri; k>1; k--){
	    var r2 = r-k*255/gri;
	    var g2 = g-k*255/gri;
	    var b2 = b-k*255/gri;
	
	    text+=patratel(r2,g2,b2)+' onmouseover=\'javascript:sel('+r2+','+g2+','+b2+', "'+param+'","'+style+'","'+sid+'", "'+sBox+'");\' onclick=\'javascript:nd(0);\'></td>';
	}

	for (k=0; k<gri-2; k++){
	    var r2 = r+k*255/gri;
	    var g2 = g+k*255/gri;
	    var b2 = b+k*255/gri;
	    
	    text+=patratel(r2,g2,b2)+' onmouseover=\'javascript:sel('+r2+','+g2+','+b2+', "'+param+'","'+style+'","'+sid+'", "'+sBox+'");\' onclick=\'javascript:nd(0);\'></td>';
	}
	
	overlib(text, STICKY, CENTER, CAPTION, 'Pick one');
}
    
function celula(r,g,b, param, style, sid, sBox){
	document.write(patratel(r,g,b));
	document.write(' onclick="return showPopup('+r+','+g+','+b+', \''+param+'\',\''+style+'\' ,\''+sid+'\',\''+sBox+'\');"></td>');
}

function createScript(sid, sBox){
		var sScript = '<script type="text/javascript">\n'+
						'acasa_user_id = \''+sid+'\';\n'+
						'acasa_box = \''+sBox+'\'\n'+
						'acasa_width= \''+document.getElementById('width').value+'\'\n'+
						'acasa_height = \''+document.getElementById('height').value+'\'\n'+
						'acasa_fontcolor = \''+document.getElementById('fontcolor').value+'\'\n'+
						'acasa_bordercolor = \''+document.getElementById('bordercolor').value+'\'\n'+
						'acasa_headerfontcolor = \''+document.getElementById('headerfontcolor').value+'\'\n'+
						'acasa_headerbackgroundcolor = \''+document.getElementById('headerbackgroundcolor').value+'\'\n';
		if(sBox == 'weather')
			sScript += 'acasa_params = \'&town='+document.getElementById('town').value+'\'\n';
		else
			sScript += 'acasa_params = \'\'\n';
		
		sScript +=		'<\/script>\n'+
						'<script type="text/javascript"\n'+
						 ' src="http://webcontent.acasa.ro/showcontent.js">\n'+
						'<\/script>\n'+
						'<div align="right"><font style="font-family: Verdana; font-size: 11px; color: #999999">oferit de </font><a href="http://www.acasa.ro"><font style="font-family: Verdana; font-size: 11px; color: #999999">Acasa.ro</font></a></div>';
	 	
	 	document.getElementById("script").value = sScript;
	 	
	}

      
function showColor(val, param, style, sid, sBox) {
	document.getElementById(param).value = val;
		
	if(style == 1){
		if(frames['acasa_frame'].document.styleSheets[0].cssRules)
			frames['acasa_frame'].document.styleSheets[0].cssRules[0].style.color = document.getElementById(param).value;
		else
			frames['acasa_frame'].document.styleSheets[0].rules[0].style.color = document.getElementById(param).value;
	}
	
	if(style == 2){
		if(frames['acasa_frame'].document.styleSheets[0].cssRules)
			frames['acasa_frame'].document.styleSheets[0].cssRules[1].style.borderColor = document.getElementById(param).value;
		else
			frames['acasa_frame'].document.styleSheets[0].rules[1].style.borderColor = document.getElementById(param).value;
	}
	
	if(style == 3){
		if(frames['acasa_frame'].document.styleSheets[0].cssRules)
			frames['acasa_frame'].document.styleSheets[0].cssRules[2].style.color = document.getElementById(param).value;
		else
			frames['acasa_frame'].document.styleSheets[0].rules[2].style.color = document.getElementById(param).value;
	}
	
	if(style == 4){
		if(frames['acasa_frame'].document.styleSheets[0].cssRules)
			frames['acasa_frame'].document.styleSheets[0].cssRules[2].style.backgroundColor = document.getElementById(param).value;
		else
			frames['acasa_frame'].document.styleSheets[0].rules[2].style.backgroundColor = document.getElementById(param).value;
	}
	
	createScript(sid, sBox);	
}	

function showWidth(){
	document.getElementById("acasa_frame").width = document.getElementById('width').value;
}

function showHeight(){
	document.getElementById("acasa_frame").height = document.getElementById('height').value;
}

function showSrc(iSid, sBox){
	var sfColor = document.getElementById('fontcolor').value;
	var sbColor = document.getElementById('bordercolor').value;
	var shfColor = document.getElementById('headerfontcolor').value;
	var shbColor = document.getElementById('headerbackgroundcolor').value;
	
	document.getElementById("acasa_frame").src = 'http://webcontent.acasa.ro/content?box='+sBox+'&op=show&sid='+iSid+'&town='+document.getElementById('town').value+'&fcolor='+escape(sfColor)+'&bcolor='+escape(sbColor)+'&hfcolor='+escape(shfColor)+'&hbcolor='+escape(shbColor);
	
	document.getElementById("acasa_frame").width = document.getElementById('width').value;
	document.getElementById("acasa_frame").height = document.getElementById('height').value;
}

function clearColors(){
	var sCssStyles = frames['acasa_frame'].document.styleSheets[0];

	var sfColor = document.getElementById('fontcolor').value;
	var sbColor = document.getElementById('bordercolor').value;
	var shfColor = document.getElementById('headerfontcolor').value;
	var shbColor = document.getElementById('headerbackgroundcolor').value;
	
	if(sCssStyles.cssRules){
		frames['acasa_frame'].document.styleSheets[0].cssRules[0].style.color = sfColor;	
		frames['acasa_frame'].document.styleSheets[0].cssRules[0].style.borderColor = sbColor;
		frames['acasa_frame'].document.styleSheets[0].cssRules[2].style.color = shfColor;
		frames['acasa_frame'].document.styleSheets[0].cssRules[2].style.backgroundColor = shbColor;

	}else{
		frames['acasa_frame'].document.styleSheets[0].rules[0].style.color = sfColor;
		frames['acasa_frame'].document.styleSheets[0].rules[1].style.borderColor = sbColor;
		frames['acasa_frame'].document.styleSheets[0].rules[2].style.color = shfColor;
		frames['acasa_frame'].document.styleSheets[0].rules[2].style.backgroundColor = shbColor;
	}
}

function finalDraw(param, style, sid, sBox){
	var linii=7;
    var coloane=7;
    
    for (i=0; i<linii; i++){
		document.write('<tr>');

		for (j=0; j<coloane; j++){
		    var r=255;
		    var g=j*(255/coloane);
		    var b=i*(255/linii);
		    
		    celula(r,g,b, param, style, sid, sBox);
		}

		for (j=0; j<coloane; j++){
		    var r=255-j*(255/coloane);
		    var g=255;
		    var b=i*(255/linii);
		    
		    celula(r,g,b, param, style, sid, sBox);
		}
	
		document.write('</tr>');
    }
    
    for (i=0; i<linii; i++){
		document.write('<tr>');

		for (j=0; j<coloane; j++){
		    var r=255-i*(255/linii);
		    var g=j*(255/coloane);
		    var b=255;
		    
		    celula(r,g,b, param, style, sid, sBox);
		}

		for (j=0; j<coloane; j++){
		    var r=255-j*(255/coloane)/2-i*(255/linii)/2;
		    var g=255-j*(255/coloane)/2-i*(255/linii);
		    var b=255-j*(255/coloane)-i*(255/linii)/2;
		    
		    celula(r,g,b, param , style, sid, sBox);
		}
	
		document.write('</tr>');
	}
	
 }