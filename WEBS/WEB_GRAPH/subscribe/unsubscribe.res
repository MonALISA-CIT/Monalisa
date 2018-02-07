<script language="javascript">
function isValidEmail(email){
    /*se foloseste de o expresie regulata*/
    var expression = /^(\w+([+\-\.]\w+)*)@((?:\w+\.)*\w[\w-]{0,66})\.([a-z]{2,6}(?:\.[a-z]{2})?)$/
    
    if (!expression.test(email)){
	return false;
    }
    
    return true;
}

function verifyForm(){
    var email = document.getElementById("email").value;
    
    if(!isValidEmail(email)){
	alert("Incorrect email adress");
	return false;
    }  
    
    return true;
}
</script>
<table cellspacing=0 cellpadding=2 class="table_content" align="left" height="500" style="border: 0px">
    <tr height=25>
	<td bgcolor="#FFFFFF" class="table_title"><b>Unsubscribe to an event</b></td>
    </tr>
    <form name="subscribe" method="post" action="/subscribe/unsubscribe.jsp" onsubmit="return verifyForm();">
    <input type="hidden" name="set" value="<<:set:>>">
    <input type="hidden" name="site" value="<<:site:>>">
    <tr>
	<td valign="top">
	    <table cellspacing=1 cellpadding=5>
		<tr>
		    <td colspan="2" class="error" align="center"><<:error:>></td>
		</tr>
		<tr class="table_row">
		    <td class="table_row"><b>Event:</b></td>
		    <td class="table_row"><div class="error"><b><<:event:>></b></div></td>
		</tr>
		<tr class="table_row">
		    <td class="table_row"><b>Your email:</b></td>
		    <td class="table_row"><input type="text" id="email" name="email" class="input_text" size="50" value="<<:email esc:>>"></td>
		</tr>
		<tr>
		    <td>&nbsp;</td>
		    <td><input type="submit" name="buton" class="input_text" value="Submit"></td>
		</tr>
	    </table>
	</td>
    </tr>
    </form>
</table>