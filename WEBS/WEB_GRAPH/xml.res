<script type="text/javascript">
    var clientsHTML = 
	'<div align=left style="padding-left:10px;padding-top:10px;">'+
	'  A few free Atom/RSS readers:<br>'+
	'  <ul>'+
	'    <li>Multi-platform'+
	'      <ul>'+
	'        <li><a target=_blank href="http://sage.mozdev.org/">Sage</a> plugin for <a target=_blank href="http://www.mozilla.org/">Firefox</a></li>'+
	'        <li><a target=_blank href="http://forumzilla.mozdev.org/">ForumZilla</a> plugin for <a target=_blank href="http://www.mozilla.org/">Thunderbird</a></li>'+
	'        <li><a target=_blank href="http://www.cincomsmalltalk.com/BottomFeeder/">BottomFeeder</a></li>'+
	'        <li><a target=_blank href="http://project5.freezope.org/pears/index.html/">Pears</a> (Perl)</li>'+
	'        <li><a target=_blank href="http://www.rssowl.org/">RSSowl</a></li>'+
	'      </ul>'+
	'    <br clear=all></li>'+
	'    <li>Linux</li>'+
	'      <ul>'+
	'        <li><a target=_blank href="http://akregator.kde.org/">Akregator</a> (KDE app)</li>'+
	'      </ul>'+
	'    <br clear=all></li>'+
	'    <li>Mac'+
	'      <ul>'+
	'        <li><a target=_blank href="http://www.apple.com/macosx/features/safari/">AppleSyndication</a></li>'+
	'        <li><a target=_blank href="http://www.utsire.com/shrook/">Shrook</a></li>'+
	'      </ul>'+
	'    <br clear=all></li>'+
	'    <li>Web-based'+
	'      <ul>'+
	'        <li><a target=_blank href="http://www.newsgator.com/">newsgator.com</a></li>'+
	'      </ul>'+
	'    <br clear=all></li>'+
	'    <li>Windows'+
	'      <ul>'+
	'        <li><a target=_blank href="http://www.brindys.com/winrss/iukmenu.html">WinRSS</a></li>'+
	'        <li><a target=_blank href="http://www.sharpreader.net/">SharpReader</a></li>'+
	'        <li><a target=_blank href="http://www.blogbot.com/">Blogbot</a></li>'+
	'        <li><a target=_blank href="http://www.usablelabs.com/productBlogExpress.html">BlogExpress</a></li>'+
	'      </ul>'+
	'    <br clear=all></li>'+
	'  </ul>'+
	'  Many other clients are listed on <a target=_blank href="http://www.atomenabled.org/everyone/atomenabled/index.php?c=5">atomenabled.org</a> '+
	'  and <a target=_blank href="http://www.google.com/search?q=atom+client&ie=UTF-8&oe=UTF-8">Google</a> also knows about a few.'+
	'</div>';
</script>

<div align=left style="font-face:Verdana,Helvetica,Arial,sans-serif;font-size:12px;padding-left:20px">
You can subscribe to one or more <a target=_blank href="http://en.wikipedia.org/wiki/Atom_(standard)" class="link">Atom</a> feeds or by email to be notified when something (usually bad thing) happens.<br>
To unsubscribe from email alerts please follow the <a href="/subscribe/unsubscribe_all.jsp" class="link">Unsubscribe</a> link .<br><br>
If you need a client here is a <a href="#" onClick="nd(); showCenteredWindow(clientsHTML, 'Atom clients'); return false;" class="link">list</a> to start from.<br>
<br>
Atom feeds publish the same information as you can see in the <a target=_blank href="/annotations.jsp" class="linkb">annotations</a>.<br>
<br>
<a href="/atom.jsp" class="linkb"><img src="/images/xml.gif" border=0></a>&nbsp;<a class="linkb" href="/atom.jsp"><b>All events</b></a>(<a class="linkb" href="/atom.jsp"><b>XML</b></a>, <a class="linkb" href="/subscribe/subscribe.jsp"><b>Email</b></a>)
<ul>
    <li><a href="/atom.jsp?set=1"><img src="/images/xml.gif" border=0></a>&nbsp;<a class="linkb" href="/atom.jsp?set=1"><b>Operational log</b></a> (<a class="linkb" href="/atom.jsp?set=1"><b>XML</b></a>)</li>
    <li><a href="/atom.jsp?set=2"><img src="/images/xml.gif" border=0></a>&nbsp;<a class="linkb" href="/atom.jsp?set=2"><b>TaskQueue events</b></a> (<a class="linkb" href="/atom.jsp?set=2"><b>XML</b></a>)</li>
    <li><a href="/atom.jsp?set=3"><img src="/images/xml.gif" border=0></a>&nbsp;<a class="linkb" href="/atom.jsp?set=3"><b>FTD/FTS log</b></a> (<a class="linkb" href="/atom.jsp?set=3"><b>XML</b></a>)</li>
    <li><a href="/atom.jsp?set=7"><img src="/images/xml.gif" border=0></a>&nbsp;<a class="linkb" href="/atom.jsp?set=7"><b>Central services</b></a> (<a class="linkb" href="/atom.jsp?set=7"><b>XML</b></a>, <a class="linkb" href="/subscribe/subscribe.jsp?set=7"><b>Email</b></a>)</li>
    <li><a href="/atom.jsp?set=6"><img src="/images/xml.gif" border=0></a>&nbsp;<a class="linkb" href="/atom.jsp?set=6"><b>Site services</b></a> (<a class="linkb" href="/atom.jsp?set=6"><b>XML</b></a>, <a class="linkb" href="/subscribe/subscribe.jsp?set=6"><b>Email</b></a>) 
	(all sites, or <span onclick="switchDiv('div_series', true, 0.3);">choose one from the <a href="javascript:void(0);" class="menu_link"><b>list</b> </a> below <img id="div_series_img" src="/img/dynamic/plus.jpg" width="9" height="9" border="0"></span>)<br>
    <div id="div_series" style="display: none">
	<div>
	    <table border=0 cellspacing=3 cellpadding=2 style="padding-left:20px">
		<<:services:>>
	    </table>
	</div>
    </div>
    </li>
    <li><a href="/atom.jsp?set=8"><img src="/images/xml.gif" border=0></a>&nbsp;<a href="/atom.jsp?set=8" class="linkb"><b>Proxies</b></a> (<a class="linkb" href="/atom.jsp?set=8"><b>XML</b></a>, <a class="linkb" href="/subscribe/subscribe.jsp?set=8"><b>Email</b></a>)
    (all sites, or <span onclick="switchDiv('div_proxies', true, 0.3);">choose one from the <a href="javascript:void(0);" class="menu_link"><b>list</b> </a> below <img id="div_proxies_img" src="/img/dynamic/plus.jpg" width="9" height="9" border="0"></span>)
    <div id="div_proxies" style="display: none">
	<div>
	    <table border=0 cellspacing=3 cellpadding=2 style="padding-left:20px">
		<<:proxies:>>
	    </table>
	</div>
    </div>
    </li>
    <li><a href="/atom.jsp?set=12"><img src="/images/xml.gif" border=0></a>&nbsp;<a href="/atom.jsp?set=12" class="linkb"><b>SAM tests</b></a> (<a class="linkb" href="/atom.jsp?set=12"><b>XML</b></a>, <a class="linkb" href="/subscribe/subscribe.jsp?set=12"><b>Email</b></a>)
    (all sites, or <span onclick="switchDiv('div_samtests', true, 0.3);">choose one from the <a href="javascript:void(0);" class="menu_link"><b>list</b> </a> below <img id="div_samtests_img" src="/img/dynamic/plus.jpg" width="9" height="9" border="0"></span>)
    <div id="div_samtests" style="display: none">
	<div>
	    <table border=0 cellspacing=3 cellpadding=2 style="padding-left:20px">
		<<:samtests:>>
	    </table>
	</div>
    </div>
    </li>
    <li><a href="/atom.jsp?set=10"><img src="/images/xml.gif" border=0></a>&nbsp;<a class="linkb" href="/atom.jsp?set=10"><b>Storage elements</b></a> (<a class="linkb" href="/atom.jsp?set=10"><b>XML</b></a>, <a class="linkb" href="/subscribe/subscribe.jsp?set=10"><b>Email</b></a>) 
    (all sites, or <span onclick="switchDiv('div_storages', true, 0.3);">choose one from the <a href="javascript:void(0);" class="menu_link"><b>list</b> </a> below <img id="div_storages_img" src="/img/dynamic/plus.jpg" width="9" height="9" border="0"></span>)
    <div id="div_storages" style="display: none">
	<div>
	    <table border=0 cellspacing=3 cellpadding=2 style="padding-left:20px">
		<<:storages:>>
	    </table>
	</div>
    </div>
    </li>
</ul>
</div>
