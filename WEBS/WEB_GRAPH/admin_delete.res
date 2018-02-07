Are you sure you want to <%= sName!=null ? "delete" : "ban"%> <b><%= sName!=null ? sName : sBan%></b> ? <br>
<a href="<%= JSP%>?<%= sName!=null ? "delete" : "ban"%>=<%= Formatare.encode(sName!=null ? sName : sBan)%>&ip=<%= Formatare.encode(sIP)%>&sure=yes"><b>YES</b></a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
<a href=<%= JSP%>><b>NO</b></a>
