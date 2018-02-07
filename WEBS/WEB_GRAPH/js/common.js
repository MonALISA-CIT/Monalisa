function IE(event,id){
    if (event.button == "2" || event.button == "3"){
	s(id);

        return false;
    }
}

function NS(e,id){
    if (document.layers || (document.getElementById && !document.all)){
	if (e.which == "2" || e.which == "3"){
	    s(id);

            return false;
        }
    }
}

function objById( id ){
    if (document.getElementById)
        var returnVar = document.getElementById(id);
    else if (document.all)
        var returnVar = document.all[id];
    else if (document.layers)
        var returnVar = document.layers[id];

    return returnVar;
}


function reverse(id){
    var ch = objById(id);

    ch.checked = !ch.checked;
}

function changeCheckState(fields, newState){
    if (fields){
        if (fields.length && fields.length>0){
            for (i=0; i<fields.length; i++)
                fields[i].checked = newState;
	}
        else{
            try{
                fields.checked = newState;
            }
            catch (Ex){
            }
        }
    }
}

function checkall(){
    changeCheckState(document.form1.plot_series, true);
}

function uncheckall(){
    changeCheckState(document.form1.plot_series, false);
}

function modify(){
    document.form1.submit();
}

function s(id){
    uncheckall();
    reverse(id);
    modify();
}

function so(id){
    s(id);
}

function switchToAlternate(page){
    if (page.indexOf("CLEAR")==0){
        page = page.substring(5);
        uncheckall();
    }
    
    if (page.indexOf("CLEAN")==0){
	page = page.substring(5);
	document.location="display?page="+page;
    }

    document.form1.page.value=page;
    document.form1.submit();
}

function checkallmodules(){
    changeCheckState(document.form1.modules, true);
}
	    
function uncheckallmodules(){
    changeCheckState(document.form1.modules, false);
}

function matchName(s1, s2){
    var re = new RegExp('^(\\d+\\. )?'+s1+'$');
    
    return s2.match(re);
}

var imgResolutions = '1280x700,1024x600,800x550';
var imgResDefault  = '1024x600';
    
function displayImgResOptions(obj){
    if (obj.options){
	var res = imgResolutions.split(',');
	    
	var i = 0;
	    
	while (i<res.length){
	    obj.options[i] = new Option(res[i], res[i]);
		
	    if (res[i] == imgResDefault)
	        obj.options[i].selected = true;
		    
	    i++;
	}
    }
}

function setStartPage(){
    Set_Cookie('lastval_div_indexmap', buri, 365, '/', '', '');
    
    alert('Current page was set as your start page for the AliEn Repository');
}

function showBookmarkWindow(){
    var bookmark_esc = bookmark.replace(/&/g,"&amp;");

    showCenteredWindow(
	'<div align=left style=\'padding-left: 10px; padding-right: 10px; padding-top: 10px\'>'+
	'The <a href="'+bookmark+'">link</a> to the current page is:'+
	'<br><br>'+
	'<textarea id=url_area wrap=soft style=\'width:100%; height:100px; font-family: Arial,Verdana,Helvetica,sans; font-size: 10px;\'></textarea>'+
	'<br><a href="JavaScript:shorten()">Short URL</a>'+
	'</div>'+
	'<br><br>'+
	'<a href="JavaScript:setStartPage();">Make this page your start page</a>',
	'Link to this page'
    );
    
    objById('url_area').value = bookmark;
    
    return false;
}

var surl;

function shortenAction(){
    r = surl.response.replace(/^\s+|\s+$/g, "");
    
    if (/^\d+$/.test(r)){
	objById('url_area').value = bookmark.substring(0, bookmark.lastIndexOf(buri))+'?'+r;
    }
}

function shorten(){
    surl = new sack();
    surl.method='GET';
    surl.setVar('path', buri);
    surl.requestFile = '/work/shorturl.jsp';
    surl.onCompletion = function() { shortenAction(); };
    surl.runAJAX();
}

function showLink(title, url){
    document.write('<table width="100%" cellspacing="0" cellpadding="0" border="0" bgcolor="#FFF3F3">');
    document.write('<tr>');
    document.write('<td width="5" height="5" align="left" valign="bottom"><img src="/images/center_top_left.jpg" width="5" height="5" border="0"></td>');
    document.write('<td height="5" width="100%" style="border-top: solid 1px #b5b5bd"><img width="1" height="1"></td>');
    document.write('<td width="5" height="5" align="right" valign="bottom"><img src="/images/center_top_right.jpg" width="5" height="5" border="0"></td>');
    document.write('</tr>');
    document.write('<tr>');
    document.write('<td width="5" style="border-left: solid 1px #b5b5bd"><img height="1" width="1"></td>');
    document.write('<td class="text" width="100%">');
    
    if (document.all || window.sidebar){
        document.write(
    	    '<b>This page: <a class="linkb" title="'+title+'" rel="sidebar" href="#" class="linkmenublue" onclick="return bookmarksite(\''+title+'\', \''+url+'\');"">bookmark</a>, '+
    	    '<a class="linkb" href="javascript:void(0);" onClick="return showBookmarkWindow();">URL</a>'
    	);
    }
    else{
        document.write('<a class="linkb" title="'+title+'" class="linkmenublue" href="'+url+'">Current page</a>');
    }

    document.write('</td>');
    document.write('<td width="5" style="border-right: solid 1px #b5b5bd"><img height="1" width="1"></td>');
    document.write('</tr>');
    document.write('<tr>');
    document.write('<td width="5" height="5" align="left"><img src="/images/center_bottom_left.jpg" width="5" height="5" border="0"></td>');
    document.write('<td height="5" width="100%" style="border-bottom: solid 1px #b5b5bd"><img width="1" height="1"></td>');
    document.write('<td width="5" height="5" align="right"><img src="/images/center_bottom_right.jpg" width="5" height="5" border="0"></td>	');
    document.write('</tr>');
    document.write('</table>');
    document.write('<div class="spacer"><!-- // --></div>');
}

function bookmarksite(title, url){
    if (document.all && !window.opera){
        window.external.AddFavorite(url, title);
    }
    else
    if (window.sidebar && sidebar.addPanel){
        sidebar.addPanel(title, url, "");
    }
    else{
	this.title = title;
    }
    
    return false;
}

function Set_Cookie( name, value, expires, path, domain, secure ){
    // set time, it's in milliseconds
    var today = new Date();
    today.setTime( today.getTime() );

    /*
	if the expires variable is set, make the correct 
	expires time, the current script below will set 
	it for x number of days, to make it for hours, 
	delete * 24, for minutes, delete * 60 * 24
    */
    if ( expires ){
	expires = expires * 1000 * 60 * 60 * 24;
    }

    var expires_date = new Date( today.getTime() + (expires) );

    document.cookie = name + "=" +escape( value ) +
	( ( expires ) ? ";expires=" + expires_date.toGMTString() : "" ) + 
	( ( path ) ? ";path=" + path : "" ) + 
	( ( domain ) ? ";domain=" + domain : "" ) +
	( ( secure ) ? ";secure" : "" );
}

// this function gets the cookie, if it exists
function Get_Cookie( name ) {
    var start = document.cookie.indexOf( name + "=" );
    var len = start + name.length + 1;
    if ( ( !start ) && ( name != document.cookie.substring( 0, name.length ) ) ){
	return null;
    }

    if ( start == -1 ) return null;
    var end = document.cookie.indexOf( ";", len );
    if ( end == -1 ) end = document.cookie.length;
    return unescape( document.cookie.substring( len, end ) );
}

function checkDivs(dynamicDivs){
    for (var i=0; i<dynamicDivs.length; i++){
        var lastval = Get_Cookie('lastval_'+dynamicDivs[i]);

        if (lastval == 1)
	    switchDiv(dynamicDivs[i]);
    }
}

function switchDiv(name, isEffect, d){
    if (!d)
        d = 0.3;
    
    var vDiv = objById(name);
	
    var closed = vDiv.style.display == 'none';

    if (isEffect){
        new Effect.toggle($(name), 'blind', {duration: d});

        Set_Cookie('lastval_'+name, closed ? '1' : '0', 30, '/', '', '');
    }
    else{
        objById(name).style.display = closed ? '' : 'none';
    }

    var img = objById(name+'_img');
	
    if (img)
        img.src = closed ? "/img/dynamic/minus.jpg" : "/img/dynamic/plus.jpg";
}

function cancelAutoReload(){
    if (autoReloadArmed){
	clearTimeout(autoReloadTimeout);
	autoReloadArmed = false;
    }
}

function rearmAutoReload(){
    if (!autoReloadConfigured){
	autoReloadTimeout = window.setTimeout("modify()", refreshAfter);
	autoReloadArmed = true;
    }
}

function setMinWidth(obj, newWidth){
    if (obj.clientWidth < newWidth){
	obj.style.width = newWidth+'px';
    }
}

function setWidth(obj, newWidth){
    obj.style.width = newWidth;
}

function focusText(obj, newWidth){
    setMinWidth(obj, newWidth);
    
    obj.style.background = 'yellow';
}

function blurText(obj){
    setWidth(obj, '100%');
    
    obj.style.background = '#F6F6F6';
}
