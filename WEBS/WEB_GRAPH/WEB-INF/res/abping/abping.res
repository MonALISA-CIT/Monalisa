<html>
    <head>
	<title>
	    ABPing configuration utility
	</title>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    </head>
	<body bgcolor=#FFFFFF>
	    <form action=abping method=post>
	    <table border=0 cellspacing=0 cellpadding=2 style="font-family:Helvetica,Verdana,Arial;font-size:10px">
		<tr><td>&nbsp;</td><<:header:>></tr>
		<<:continut:>>
		<tr>
		    <td colspan=100% align=center>
			<br clear=all>
			
			<input type=checkbox name=simetric value="1" checked>Symmetric links
			&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
			<input type=submit name=submit value="Update ABPing configuration">
		    </td>
		</tr>
	    </table>
	    <br>
	    
	    </form>
	    <br>

	    <form action=abping method=post>
		<b>Add extra host</b><br>
		IP: <input type=text name="ip" value="" class="input_text"><br>
		FQN: <input type=text name="fqn" value="" class="input_text"><br>
		<input type=submit name=submit value="ADD" class="input_submit">
	    </form>
	</body>
</html>
