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
	var ns = (navigator.appName.indexOf("Netscape") != -1);
	var d = document;
	function getElById( id)
	{
		var el=d.getElementById?d.getElementById(id):d.all?d.all[id]:d.layers[id];
		return el;
	}	
/*
	var menuSpacer = getElById("menuSpacer");
	var startY = findPosY(menuSpacer);
	var incrementY = 10;
	function stayTopLeft()
	{
		var pY = ns ? pageYOffset : document.body.scrollTop;
		var increment = pY - startY - menuSpacer.height;
		if ( increment > 2*incrementY )
			increment /= 2;
		else if ( increment < -2*incrementY )
			increment /= 2;
		menuSpacer.height += increment;
		setTimeout("stayTopLeft()", 100);
	}
	stayTopLeft();
*/
