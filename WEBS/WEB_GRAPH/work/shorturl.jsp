<%@ page import="lia.Monitor.Store.Fast.DB,lazyj.*"%><%!
    private static synchronized final int getId(final String path){
	if (path==null || path.length()==0 || !path.startsWith("/") || path.startsWith("/?") || path.startsWith("/index.jsp?"))
	    return -1;
	
	final DB db = new DB("SELECT id FROM shorturl WHERE path='"+Format.escSQL(path)+"';");
	
	if (!db.moveNext()){
	    db.query("SELECT max(id) FROM shorturl;");
	    
	    int newid = 1;
	    
	    if (db.moveNext()){
		newid = db.geti(1) + 1;
	    }
	    
	    if (db.syncUpdateQuery("INSERT INTO shorturl (id, path) VALUES ("+newid+", '"+Format.escSQL(path)+"');"))
		return newid;
	    else
		return -1;
	}
	else{
	    final int id = db.geti(1);
	    
	    db.asyncUpdate("UPDATE shorturl SET lastrequested=extract(epoch from now())::int, requestcount=requestcount+1 WHERE id="+id);
	    
	    return id;
	}
    }
%><%
    final RequestWrapper rw = new RequestWrapper(request);
    
    out.println(getId(rw.gets("path")));
%>