<%@ page import="java.util.*,java.io.*,java.text.SimpleDateFormat,java.awt.Color,lia.web.utils.*,lia.Monitor.Store.Fast.DB,lia.web.servlets.web.Utils"%><%
    lia.web.servlets.web.Utils.logRequest("START /annotation.jsp", 0, request);

    if (request.getParameter("del_id")!=null){
	try{
	    int del_id = Integer.parseInt(request.getParameter("del_id"));
	    
	    DB db = new DB();
	    
	    db.query("DELETE FROM annotations WHERE a_id="+del_id+";");
	}
	catch (Exception e){
	}
	
	%>
	    <html><body>
		<script language=javascript>
		    opener.location.reload(true);
		    self.close();
		</script>
	    </body></html>
	<%
	
	return;
    }

    final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm");
    
    final long lNow = System.currentTimeMillis();

    int id = -1;
    
    try{
	id = Integer.parseInt(request.getParameter("a_id"));
    }
    catch (Exception e){
    }	
    
    String sSeriesNames = request.getParameter("series_names");
    
    if (sSeriesNames==null)
	sSeriesNames="";

    TreeSet tsNames = new TreeSet();
    
    StringTokenizer st = new StringTokenizer(sSeriesNames, ",");
    
    while (st.hasMoreTokens()){
	tsNames.add(st.nextToken());
    }

    String sGroups = request.getParameter("groups");
    
    if (sGroups==null)
	sGroups = "";
	
    final TreeSet tsGroups = new TreeSet();
    
    st = new StringTokenizer(sGroups, ",");
    
    while (st.hasMoreTokens()){
	try{
	    tsGroups.add(new Integer(Integer.parseInt(st.nextToken())));
	}
	catch (Exception e){
	}
    }

    Annotation a;
    
    if (id>0){
	a = new Annotation(id);
    }
    else{
	a = new Annotation();

	a.to = lNow;	
	a.from = lNow - 1000*60*60;
	
	a.groups.addAll(tsGroups);
    }

    boolean bOperationDone = request.getParameter("submit")!=null && request.getParameter("a_text")!=null;

    if (bOperationDone){
	a.text = request.getParameter("a_text");
	a.setDescription(request.getParameter("a_description"));
	a.color = ServletExtension.getColor(request.getParameter("a_color"), Color.BLUE);
	a.textColor = ServletExtension.getColor(request.getParameter("a_textcolor"), Color.BLUE);
	
	if (request.getParameter("chk_continues")!=null){
	    a.to = lNow + 1000*60*60*24*1000;	// 1000 days in the future. this should do it :)
	}
	else{
	    a.to = sdf.parse(request.getParameter("a_to")).getTime();
	}
	
	a.from = sdf.parse(request.getParameter("a_from")).getTime();
	
	a.groups.clear();
	a.services.clear();
	
	String sValues[] = request.getParameterValues("a_services");
	
	for (int i=0; sValues!=null && i<sValues.length; i++){
	    String s = sValues[i];
	    
	    if (s!=null){
		if (s.length()==0){
		    a.services.clear();
		    break;
		}
	    
		a.services.add(s);
	    }
	}
	
	sValues = request.getParameterValues("a_groups");
	
	for (int i=0; sValues!=null && i<sValues.length; i++){
	    String s = sValues[i];
	    
	    if (s!=null){
		if (s.length()==0){
		    a.groups.clear();
		    break;
		}
		
		try{
		    a.groups.add(new Integer(Integer.parseInt(s)));
		}
		catch (Exception e){
		}
	    }
	}
	
	a.updateDatabase();
	
	CacheServlet.clearCache();
    }
%>

<html>
    <head>
	<title>Annotations editor</title>
	<link type="text/css" rel="StyleSheet" href="/style.css" />
	<script type="text/javascript" src="/overlib/overlib.js"></script>
	<script type="text/javascript" src="/overlib/overlib_crossframe.js"></script>
	<script language=javascript src="/js/common.js"></script>
	<script language=javascript src="/js/colors.js"></script>    
    </head>
    <body>
	<div id="overDiv" style="position:absolute; visibility:hidden; z-index:1000;"></div>
	<script language=javascript>
	    window.focus();
	
	    <%
		if (bOperationDone){
		    out.println("opener.location.reload(true);");
		}
	    %>
	
	    function switchChk(){
		var chk = objById("chk_continues");
		
		var a_to = objById("a_to");
		
		if (chk.checked){
		    a_to.disabled = true;
		}
		else{
		    a_to.disabled = false;
		}
	    }
	    
	    var oldBGColor = '';
	    
	    function bgcolor(){
		var obj = objById("td_background");
		
		var color = objById("a_color").value;
		
		if (oldBGColor!=color){
		    obj.style.backgroundColor=color;
		    oldBGColor = color;
		}
	    }

	    var oldFontColor = '';

	    function ftcolor(){
		var obj = objById("font_color");
		
		var color = objById("a_textcolor").value;
		
		if (oldFontColor!=color){
		    obj.style.color = color;
		    oldFontColor = color;
		}
	    }
	    
	    var oldText = '';
	    var oldDescrLength = 0;
	    
	    function setText(){
		bgcolor();
		ftcolor();

		var obj = objById("dtext");
		
		var descr = objById('a_description').value;
		
		var vtext = objById("a_text").value;
		
		if (vtext!=oldText || (descr.length*oldDescrLength==0 && (descr.length!=0 || oldDescrLength!=0))){
		    obj.innerHTML = vtext;
		
		    if (descr.length>0){
			obj.innerHTML = obj.innerHTML + ' <img src=/img/qm.gif border=0 onMouseOver="return showDescription();" onMouseOut="return nd();">';
		    }
		    
		    oldDescrLength = descr.length;
		    oldText = vtext;
		}
		
		window.setTimeout('setText()', 100);
	    }
	    
	    function changeImg (name)
	    {
	      var obj = objById(name);
	    
	      if (obj.name == "up"){
		obj.src = "/img/arrowDw.gif";
	    	obj.name = "down";
	      }else{
	        obj.src = "/img/arrowUp.gif";
	        obj.name = "up";
	      }
	                                          
	      return false;
	    }
	    
	    function viewDiv (name)
	    {
		var obj = objById(name);
	    
	        if (obj.style.display == "inline")
	    	    obj.style.display = "none";
	        else
	            obj.style.display= "inline";	
	        
	        return false;
	    }
	    
	    function showDescription(){
		return overlib(objById('a_description').value);
	    }
	    
	</script>
	<div class=alternate>
	<form action=annotation.jsp method=post>
	    <input type=hidden name=series_names value="<%=sSeriesNames%>">
	    <input type=hidden name=groups value="<%=sGroups%>">
	    <input type=hidden name=a_id value=<%=a.id%>>
	    <fieldset>
	        <legend>Annotation (<%=a.id>0 ? "id="+a.id : "new one"%>)</legend><nobr>
	            Message: <input type=text name=a_text value="<%=a.text!=null ? a.text : ""%>" size=30 id=a_text class=alternate onChange="setText()"><br>
		    Start time: <input type=text name=a_from size=16 maxsize=16 value="<%=sdf.format(new Date(a.from))%>" id=a_from class=alternate> <font size=-2>(dd.mm.yyyy hh:mm)</font><br>
		    End time:   <input type=text name=a_to size=16 maxsize=16 value="<%=sdf.format(new Date(a.to>lNow ? lNow : a.to))%>" <%=a.to>lNow ? "disabled" : ""%> id=a_to class=alternate> <font size=-2>(dd.mm.yyyy hh:mm)</font> / 
		        <label for="chk_continues" style="cursor: pointer; cursor: hand"><input type=checkbox value="-1" name="chk_continues" <%=a.to > lNow ? "checked" : ""%> id="chk_continues" onClick="switchChk()"> continues</label>
		        <br>
		    Colors: background: <input type=text name=a_color value="#<%=Utils.toHex(a.color)%>" size=10 maxlength=7 id=a_color class=alternate> <a href="#" onClick="viewDiv('div_background');return changeImg('img_background');"><img id="img_background" name="up" src="/img/arrowUp.gif" border=0></a> 
			/ 
		        text: <input type=text name=a_textcolor value="#<%=Utils.toHex(a.textColor)%>" size=10 maxlength=7 id=a_textcolor class=alternate> <a href="#" onClick="viewDiv('div_font');return changeImg('img_font');"><img id="img_font" name="up" src="/img/arrowUp.gif" border=0></a><br>
		        <table border=0 cellspacing=0 cellpadding=0><tr><td width=100></td>
		        <td width=130>
		        <div id="div_background" style="display : none">
			    <table border=0 cellspacing=1 cellpadding=0 bgcolor="#000000">
				<tr>
				    <td align=center colspan=14 bgcolor=white style="font-weight:bold;font-face:Arial;font-size:8px">
					Click on a color
				    </td>
				</tr>
				<script language=javascript>
				    finalDraw("a_color", "1", "-1", "");
				</script>
			    </table>
			</div>
			</td>
			<td width=20></td>
		        <td>
		        <div id="div_font" style="display : none">
			    <table border=0 cellspacing=1 cellpadding=0 bgcolor="#000000">
				<tr>
				    <td align=center colspan=14 bgcolor=white style="font-weight:bold;font-face:Arial;font-size:8px">
					Click on a color
				    </td>
				</tr>
				<script language=javascript>
				    finalDraw("a_textcolor", "1", "-1", "");
				</script>
			    </table>
			</div>
			</td>
			</tr></table>

		    </nobr>
	    </fieldset>

	    <fieldset>
		<legend>Output sample</legend>
		<table border=0 cellspacing=5 cellpadding=0 class=alternate bgcolor=white width=100%>
		    <tr>
			<td width=16 bgcolor="#AADDFF" id="td_background" align=left nowrap>&nbsp;</td>
			<td align=left nowrap><font color="#AADDFF" id="font_color"><span id="dtext"></span></font>&nbsp;</td>
		    </tr>
		</table>
	    </fieldset>

	    <fieldset>
	        <legend>Applies to</legend>
	    	<table border=0 cellspacing=0 cellpadding=0 class=alternate>
	    	    <tr>
	    		<td>Series:</td>
	    		<td width=30></td>
			<td>Chart groups:</td>
			<td width=30></td>
			<td>Problem/solution description:</td>
		    </tr>
		    <tr>
			<td valign=top>
			    <select name=a_services multiple class=alternate size=10>
				<option value="" <%=a.services.size()==0 ? "selected" : ""%>>- Chart-wide -</option>
				<%
				    Iterator it = tsNames.iterator();
				    
				    while (it.hasNext()){
					String sSeries = (String) it.next();
					
					out.println("<option value=\""+sSeries+"\" "+(a.services.contains(sSeries)?"selected":"")+">"+sSeries+"</option>");
				    }
				%>
			    </select>
			</td>
			<td></td>
			<td valign=top>
			    <select name=a_groups multiple class=alternate size=10>
				<option value="" <%=a.groups.size()==0 ? "selected" : ""%>>- All -</option>
				
				<%
				    DB db = new DB("SELECT * FROM annotation_groups;");
				    
				    while (db.moveNext()){
					out.println("<option value=\""+db.geti("ag_id")+"\" "+(a.groups.contains(db.geti("ag_id")) ? "selected" : "")+">"+db.gets("ag_name")+"</option>");
				    }
				%>
			    </select>
			</td>
			<td></td>
			<td valign=top><textarea rows=10 cols=30 name=a_description id=a_description><%=a.getDescription()!=null ? a.getDescription() : ""%></textarea></td>
		    </tr>
		</table>
	    </fieldset>
	    
	    
	    <input type=submit name=submit value="<%=a.id==0 ? "Add" : "Update"%> annotation" class=alternate>
	    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
	    <input type=button name=close value="Close window" onClick="window.close()" class=alternate>
	</form>
	</div>
	
	<script language=JavaScript>
	    setText();
	</script>
	
    </body>
</html><%
    lia.web.servlets.web.Utils.logRequest("/annotation.jsp", 1, request);
%>