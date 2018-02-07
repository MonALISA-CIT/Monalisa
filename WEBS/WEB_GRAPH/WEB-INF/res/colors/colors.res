<SCRIPT LANGUAGE="JavaScript">
function showColor(val) {
document.colorform.hexval.value = val;
}
function showSitename(val) {
document.colorform.sitename.value = val;
}
function showSite(val,color,shape) {
    showSitename(val);
    showColor(color);
    
    if (shape == '')
	shape = 'o';
	
    for (i=0; i<document.colorform.shape.options.length; i++){
	if (document.colorform.shape.options[i].value == shape)
	    document.colorform.shape.selectedIndex = i;
    }
}
</script>

<div style="text" align="center">
<table border=0 cellspacing=0 cellpadding=0><tr><td>
<table border=0 cellspacing=1 cellpadding=0 bgcolor="#000000">
<tr><td align=center colspan=14 bgcolor=white style="font-weight:bold;font-family:Arial;font-size:8px">Click on a color</td></tr>
<script language="JavaScript">
    <!--
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
    
    function sel(r,g,b){
	showColor('#'+getColor(r,g,b));
    }
    
    function showPopup(r,g,b){
	var text='<table border=0 cellspacing=1 cellpadding=0><tr>';
	
	for (k=gri; k>1; k--){
	    var r2 = r-k*255/gri;
	    var g2 = g-k*255/gri;
	    var b2 = b-k*255/gri;
	
	    text+=patratel(r2,g2,b2)+' onmouseover=\'javascript:sel('+r2+','+g2+','+b2+');\' onclick=\'javascript:nd();\'></td>';
	}

	for (k=0; k<gri-2; k++){
	    var r2 = r+k*255/gri;
	    var g2 = g+k*255/gri;
	    var b2 = b+k*255/gri;
	    
	    text+=patratel(r2,g2,b2)+' onmouseover=\'javascript:sel('+r2+','+g2+','+b2+');\' onclick=\'javascript:nd();\'></td>';
	}
	
	overlib(text, STICKY, CENTER, CAPTION, 'Pick one');
    }
    
    function celula(r,g,b){
	document.write(patratel(r,g,b));
	document.write(' onclick="return showPopup('+r+','+g+','+b+');"></td>');
    }
    
    var linii=7;
    var coloane=7;
    
    for (i=0; i<linii; i++){
	document.write('<tr>');

	for (j=0; j<coloane; j++){
	    var r=255;
	    var g=j*(255/coloane);
	    var b=i*(255/linii);
	    
	    celula(r,g,b);
	}

	for (j=0; j<coloane; j++){
	    var r=255-j*(255/coloane);
	    var g=255;
	    var b=i*(255/linii);
	    
	    celula(r,g,b);
	}
	
	document.write('</tr>');
    }
    
    for (i=0; i<linii; i++){
	document.write('<tr>');

	for (j=0; j<coloane; j++){
	    var r=255-i*(255/linii);
	    var g=j*(255/coloane);
	    var b=255;
	    
	    celula(r,g,b);
	}

	for (j=0; j<coloane; j++){
	    var r=255-j*(255/coloane)/2-i*(255/linii)/2;
	    var g=255-j*(255/coloane)/2-i*(255/linii);
	    var b=255-j*(255/coloane)-i*(255/linii)/2;
	    
	    celula(r,g,b);
	}
	
	document.write('</tr>');
    }
    
    -->
</script>
</table>

</td><td width=20>&nbsp;</td>
<td nowrap class="text" align="left">
    <table class="text" align="left">
	<form name=colorform method=POST action='abping'>
        <input type="hidden" name="button" value="DoIt!">
        <input type="hidden" name="newstyle" value="yes">
	<tr>
	    <td colspan="2" align="center"><b>Apparence Configuration</b></td>
	</tr>
	<tr>
	    <td align="right">Color:</td>
	    <td><input type=text name=hexval size=10 class="input_text" style="text-align: left"></td>
	</tr>
	<tr>
	    <td align="right">Shape:</td>
	    <td><select name=shape class="input_select">
        <option value='.'>Very small circle</option>
        <option value='@'>Small circle</option>
        <option value='o'>Circle</option>
        <option value='0'>Larger circle</option>
        <option value='O'>Very large circle</option>
        <option value='^'>Triangle up</option>
        <option value='v'>Triangle down</option>
        <option value='|'>Vertical line</option>
        <option value='-'>Horizontal line</option>
        <option value='#'>Rectangle</option>
        <option value='+'>Larger rectangle</option>
        <option value='='>Very large rectangle</option>
        <option value='*'>Weird star-like shape</option>
      </select>
    	    </td>
	</tr>
	<tr>
	    <td align="right">Series name:</td>
	    <td><input type=text name=sitename size=15 class="input_text" style="text-align: left"></td>
	</tr>
	<tr>
	    <td></td>
	    <td><input type="submit" name="submit_button" value="Update" class="input_submit"></td>
	</tr>

     </form>
</table>
</td></tr></table>

<<:continut:>>

</div>
