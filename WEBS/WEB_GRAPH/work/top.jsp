<%@ page import="alien.jobs.*,alien.catalogue.*,lia.Monitor.monitor.*,lia.Monitor.Store.Fast.*,lia.Monitor.Store.*,java.util.*" %><%
    response.setContentType("text/plain");
    
    TempMemWriter3 tmw = (TempMemWriter3) ((TransparentStoreFast) TransparentStoreFactory.getStore()).getTempMemWriter();
    
    DataSplitter ds = tmw.getDataSplitter(new monPredicate[]{new monPredicate("*", "*", "*", -1000000000, -1, null, new String[]{"*"})});
    
    final Map m = ds.getMap();
    
    final List<String> keys = new ArrayList<String>( (Set<String>) m.keySet());
    
    Collections.sort(keys, new Comparator<String>(){
	public int compare(String s1, String s2){
	    Vector v1 = (Vector) m.get(s1);
	    Vector v2 = (Vector) m.get(s2);
	    
	    return v2.size() - v1.size();
	}
    }
    );
    
    for (int i=0; i<10000 && i<keys.size(); i++){
	String s = keys.get(i);
	
	out.println(s+" - "+((Vector)m.get(s)).size());
    }
%>