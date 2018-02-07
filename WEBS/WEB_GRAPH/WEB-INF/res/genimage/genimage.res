<form name=form1 action=display method=post>
    <input type=hidden name=page value="<<:page:>>">
</form>
<map name=imgmap>
<<:map:>>
</map>
<table width=800 cellspacing=0 cellpadding=0>
<tr><th><font style="font-family:Verdana,Helvetica,Arial;font-size:16px"><<:title:>></font></th></tr>
<tr><td><img src="display?image=<<:image:>>" usemap=#imgmap border=0></td></tr>
<tr><td align=left>
    <br>
    <br>
    <font style="font-family:Verdana,Helvetica,Arial;font-size:10px">
	<<:description:>>
    </font>
</td></tr>
<tr><td align=right>
    <br>
    <br>
    <table border=1 cellspacing=0 cellpadding=2 width=180 style="font-family:Verdana,Helvetica,Arial;font-size:10px">
    <tr><th colspan=2>Legend:</th></tr>
    <tr>
        <td bgcolor=#00FF00 width=40>&nbsp;</td>
	<td>Short delay</td>
    </tr>
    <tr>
        <td bgcolor=#FFFF00>&nbsp;</td>
        <td>Long delay</td>
    </tr>
    <tr>
        <td bgcolor=orange>&nbsp;</td>
        <td>Unknown delay</td>
    </tr>
    <tr>
	<td bgcolor=FF2EBD>&nbsp;</td>
	<td>Actual data path</td>
    </tr>
    <tr>
        <td><hr size=4 noshade></td>
        <td>Large bandwidth</td>
    </tr>
    <tr>
        <td><hr size=1 noshade></td>
        <td>Small bandwidth</td>
    </tr>
    <tr>
        <td nowrap>123 (45)</td>
        <td nowrap>Bandwidth: 123, Delay: 45</td>
    </tr>
    </table>
</td></tr>
</table>
