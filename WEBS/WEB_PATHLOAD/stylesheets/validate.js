
/***************************************************************************************************************/
/****************************** DreamWeaver automatically generated functions **********************************/
/***************************************************************************************************************/
function MM_swapImgRestore() { //v3.0
  var i,x,a=document.MM_sr; for(i=0;a&&i<a.length&&(x=a[i])&&x.oSrc;i++) x.src=x.oSrc;
}

function MM_preloadImages() { //v3.0
  var d=document; if(d.images){ if(!d.MM_p) d.MM_p=new Array();
    var i,j=d.MM_p.length,a=MM_preloadImages.arguments; for(i=0; i<a.length; i++)
    if (a[i].indexOf("#")!=0){ d.MM_p[j]=new Image; d.MM_p[j++].src=a[i];}}
}

function MM_findObj(n, d) { //v4.01
  var p,i,x;  if(!d) d=document; if((p=n.indexOf("?"))>0&&parent.frames.length) {
    d=parent.frames[n.substring(p+1)].document; n=n.substring(0,p);}
  if(!(x=d[n])&&d.all) x=d.all[n]; for (i=0;!x&&i<d.forms.length;i++) x=d.forms[i][n];
  for(i=0;!x&&d.layers&&i<d.layers.length;i++) x=MM_findObj(n,d.layers[i].document);
  if(!x && d.getElementById) x=d.getElementById(n); return x;
}

function MM_swapImage() { //v3.0
  var i,j=0,x,a=MM_swapImage.arguments; document.MM_sr=new Array; for(i=0;i<(a.length-2);i+=3)
   if ((x=MM_findObj(a[i]))!=null){document.MM_sr[j++]=x; if(!x.oSrc) x.oSrc=x.src; x.src=a[i+2];}
}
/***************************************************************************************************************/
/**************************** end DreamWeaver automatically generated functions ********************************/
/***************************************************************************************************************/

/***************************************************************************************************************/
/*************************************** functii pentru gestionarea ferestrelor ********************************/
/***************************************************************************************************************/
function preloadWindowImages()
{
	MM_preloadImages('images/biluta_sus_over.gif','images/biluta_sus.gif','images/biluta_jos_over.gif','images/biluta_jos.gif');
}

var state = new Array(); // "max"=maximized, "min"=minimized
function minmaxWindow(id)
{
	if ( state[id] == "max" ) {//window is maximized
		state[id] = "min";//then get it minimized
		//alert(document.getElementById("Continut"+id));
		//document.getElementById("Continut"+id).style.height = "0";
		if ( document.getElementById("Continut"+id) != null )
			if ( document.all )
				document.getElementById("Continut"+id).style.display = "none";
			else
				document.getElementById("Continut"+id).style.visibility = "hidden";
		if ( document.getElementById("Biluta"+id) != null ) {
			document.getElementById("Biluta"+id).alt = "Maximizeaza fereastra";
			document.getElementById("Biluta"+id).src = "images/biluta_jos.gif";
		};
	} else {
		state[id] = "max";
		//document.getElementById("Continut"+id).style.height = "72px";
		if ( document.getElementById("Continut"+id) != null )
			if ( document.all )
				document.getElementById("Continut"+id).style.display = "";
			else
				document.getElementById("Continut"+id).style.visibility = "visible";//document.all?"visible":"show";
		if ( document.getElementById("Biluta"+id) != null ) {
			document.getElementById("Biluta"+id).alt = "Minimizeaza fereastra";
			document.getElementById("Biluta"+id).src = "images/biluta_sus.gif";
		};
	}
}
function swapImg(id)
{
	src = document.getElementById("Biluta"+id).src;
	if ( src.lastIndexOf("_over.gif") == -1 && src.lastIndexOf(".gif") != -1 ) {
		src = src.substr(0,src.lastIndexOf(".gif"))+"_over.gif";
		//alert(src);
		document.getElementById("Biluta"+id).src = src;
	};
}
function swapImgRestore(id)
{
	src = document.getElementById("Biluta"+id).src;
	if ( src.lastIndexOf("_over.gif") != -1 ) {
		src = src.replace("_over.gif",".gif");
		//alert(src);
		document.getElementById("Biluta"+id).src = src;
	};
}
/***************************************************************************************************************/
/************************************* end functii pentru gestionarea ferestrelor ******************************/
/***************************************************************************************************************/

/***************************************************************************************************************/
/***************************************** functii de validare formulare ***************************************/
/***************************************************************************************************************/
	function isValidEmail(str) 
	{
		if ( str == "" ) return false;
		return (str.indexOf(".") > 2) && (str.indexOf("@") > 0);
	}	
	function isValidEmail2( elemName, errortext) 
	{
		if ( !isValidEmail(strtrim(document.getElementById(elemName).value)) ) {
			alert(errortext);
			document.getElementById(elemName).select();
			document.getElementById(elemName).focus();
			return false;
		}
		return true;
	}	
	function isValidMultipleEmail(str) 
	{
		regexp = /\s*\w+@\w+.\w+(\s*,\w+@\w+.\w+)*\s*/;
		rez = regexp.exec(str);
		if ( rez == null ) return false;
		return rez[0]==str;
	}
	function isWordList(str,separator)
	{
		regexp = new RegExp("[ a-zA-Z-]+("+separator+"[ a-zA-Z-]+)*");
		rez = regexp.exec(str);
		//alert(str+' <> '+str.length);
		//alert(rez.index+' '+rez.lastIndex);
		//alert(rez[0]);
		if ( rez == null ) return false;
		return rez[0]==str;	//rez.index==0 && rez.lastIndex==str.length;
	}
	function isWebLink( str)
	{
		regexp = /((http:\/\/)|)\w+\.\w+(\.\w+)+((:\d+)|)/
		rez = regexp.exec(str);
		//alert(rez);
		if ( rez == null ) return false;
		return rez[0]==str;
	}	
	function isFile( str)
	{
		regexp1 = /\w:(\\[^\\\/:\*\?\"<>\|]+)+/		//matches a regular Windows file path
		regexp2 = /\\w+(\\[^\\\/:\*\?\"<>\|]+)+/		//matches a regular Windows share file path
		regexp3 = /(\/[^\\\/:\*\?\"<>\|]+)+/		//matches a regular linux file path ?
		rez = regexp1.exec(str);
		//alert(rez);
		if ( rez == null || rez[0]!=str ) {
			rez = regexp2.exec(str);
			if ( rez == null || rez[0]!=str ) {
				rez = regexp3.exec(str);
				if ( rez == null || rez[0]!=str )
					return false;
				return true;
			}
			return true;
		}
		return true;
	}	
	function isMoney(elemName, errortext, isMandatory, isNegative)
	{
		if ( isMandatory || ( !isMandatory && document.getElementById(elemName).value!="" ) )
			if ( parseFloat(document.getElementById(elemName).value) != document.getElementById(elemName).value 
					|| (isNegative?false:(parseFloat(document.getElementById(elemName).value)<0)) ) {
					alert(errortext);
					document.getElementById(elemName).focus();
					document.getElementById(elemName).select();
					return false;
			}
		return true;
	}
	function isInteger(elemName, errortext, isMandatory, isNegative)
	{
		if ( isMandatory || ( !isMandatory && document.getElementById(elemName).value!="" ) )
			if ( parseInt(document.getElementById(elemName).value) != document.getElementById(elemName).value 
					|| (isNegative?false:(parseInt(document.getElementById(elemName).value)<0)) ) {
					alert(errortext);
					document.getElementById(elemName).focus();
					document.getElementById(elemName).select();
					return false;
			}
		return true;
	}
	function isValidText( elemName, errortext, maxlength, errortext2)
	{
		if ( strtrim(document.getElementById(elemName).value)=="" )  {
			alert(errortext);
			document.getElementById(elemName).select();
			document.getElementById(elemName).focus();
			return false;
		}
		if ( maxlength != null )
			if ( document.getElementById(elemName).value.length > maxlength )  {
				alert(errortext2);
				document.getElementById(elemName).select();
				document.getElementById(elemName).focus();
				return false;
			}
		return true;
	}
	function isEmptyText( elemName)
	{
		if ( strtrim(document.getElementById(elemName).value)=="" )
			return true;
		return false;
	}
	function isEqual( elemName1, elemName2, errortext)
	{
		if ( document.getElementById(elemName1).value != document.getElementById(elemName2).value ) {
			alert(errortext);
			document.getElementById(elemName2).select();
			document.getElementById(elemName2).focus();
			return false;
		}
		return true;
	}
	//trims a text to left and to right
	function strtrim(text) {
		//Match spaces at beginning and end of text and replace
		//with null strings
		return text.replace(/^\s+/,'').replace(/\s+$/,'');
	}
/***************************************************************************************************************/
/*************************************** end functii de validare formulare *************************************/
/***************************************************************************************************************/

/***************************************************************************************************************/
/***************************************** functii de gestionare cookies ***************************************/
/***************************************************************************************************************/
	// Create a cookie with the specified name and value.
	// The cookie expires at the end of the 20th century.
	function SetCookie(sName, sValue, yper)
	{
		date = new Date();
		if ( yper != null )
			date.setFullYear(date.getYear()+yper);
		document.cookie = sName + "=" + escape(sValue) + "; expires=" + date.toGMTString();
	}
	// Retrieve the value of the cookie with the specified name.
	function GetCookie(sName)
	{
	  // cookies are separated by semicolons
	  var aCookie = document.cookie.split("; ");
	  for (var i=0; i < aCookie.length; i++)
	  {
		// a name/value pair (a crumb) is separated by an equal sign
		var aCrumb = aCookie[i].split("=");
		if (sName == aCrumb[0]) 
		  return unescape(aCrumb[1]);
	  }
	
	  // a cookie with the requested name does not exist
	  return null;
	}
/***************************************************************************************************************/
/*************************************** end functii de gestionare cookies *************************************/
/***************************************************************************************************************/
