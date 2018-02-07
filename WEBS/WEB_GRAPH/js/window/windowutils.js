var defaultWindowWidth = 500;
var defaultWindowHeight = 340;

function showCenteredWindow(continut, titlu){
    return showCenteredWindowSize(continut, titlu, defaultWindowWidth, defaultWindowHeight);
}

var windowArray = new Array();

function showCenteredWindowSize(continut, titlu, w, h){
    var win = new Window('window_id_'+windowArray.length, {className: "mac_os_x", title: titlu, width:w, height:h, showEffect: Element.show, hideEffect: Element.hide}); 
    win.getContent().innerHTML = continut; 
    win.overlayShowEffectOptions = null;
    win.setDestroyOnClose(); 
    win.showCenter();
    windowArray[windowArray.length] = win;
    
    return win;
}

function closeWindow(){
    windowArray[windowArray.length-1].close();
}

function showIframeWindow(url, titlu){
    return showIframeWindowSize(url, titlu, defaultWindowWidth, defaultWindowHeight);
}

function getFrame(url){
    return '<iframe src="'+url+'" border=0 width=100% height=100% frameborder="0" marginwidth="0" marginheight="0" scrolling="auto" align="absmiddle" vspace="0" hspace="0"></iframe>';
}

function showIframeWindowSize(url, titlu, w, h){
    var win = showCenteredWindowSize(getFrame(url), titlu, w, h);
    
    return win;
}

function showIframeWindowLocation(url, titlu, top, left){
    var win = showIframeWindowSize(url, titlu, defaultWindowWidth, defaultWindowHeight);
    
    win._center(top, left);
    
    return win;
}

function showIframeWindowRight(url, titlu){
    var win = showIframeWindowSize(url, titlu, defaultWindowWidth, defaultWindowHeight);

    win._center(null, WindowUtilities.getPageSize().windowWidth - defaultWindowWidth - 100);

    return win;
}

function overlibnz(){
    if (arguments.length>0 && arguments[0].length>0)
	overlib.apply(this, arguments);
}

function overlibIframe(){
    var args = new Array();
    
    args[0] = getFrame(arguments[0]);
    
    for (i=1; i<arguments.length; i++)
	args[i] = arguments[i];

    overlib.apply(this, args);
}
