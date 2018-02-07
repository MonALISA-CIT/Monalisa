<table border=0 cellspacing=5 cellpadding=0 style="font-family:Helvetica,Verdana;font-size:12px">
<tr>
    <td width=100% colspan=4>
	<table border=0 cellspacing=2 cellpadding=4 width=100% style="font-family:Helvetica;font-size:12px" bgcolor=#A4A4B3>
	    <tr>
	        <td bgcolor=#EAEAFF align=center>
	        
<form name=form1 action=temp.jsp method=get>
    <b>Select VoBox : 
    <select name=site onChange="document.form1.submit();" style="font-family:Helvetica,Verdana;font-size:12px">
	<<:sitesel:>>
    </select>
    
    <input type=submit name=form1_submit value="Display" style="font-family:Helvetica,Verdana;font-size:12px">
    </b>
</form>
<br>
<font style="font-size:10px">This page displays information for the VoBoxes that have "sensors" correctly configured.</font>


		</th>
	    </tr>
	</table>
    </td>
</tr>
<tr>
<<:continut:>>
</tr></table>
