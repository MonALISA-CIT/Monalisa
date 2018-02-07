import lia.Monitor.Store.Fast.DB;
import lia.web.bean.Generic.ConfigBean;
import lia.web.bean.Generic.WriterConfig;

import java.util.Iterator;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ShowReceivedValues {

    public static void main(String args[]){
    
	if (args.length>0){
	    String s = args[0];
	    if (!s.startsWith("file:") && !s.startsWith("http://") && !s.startsWith("ftp://"))
		s = "file:"+s;
		
	    System.setProperty("lia.Monitor.ConfigURL", s);
	}
	else{
	    // default App.properties location
	    System.setProperty("lia.Monitor.ConfigURL", "file:../../Clients/JStoreClient/conf/App.properties");
	}
	
	System.out.println();
	System.out.println("Please choose the depth level : ");
	System.out.println("1 - Farms");
	System.out.println("2 - Farms, Clusters");
	System.out.println("3 - Farms, Clusters, Nodes");
	System.out.println("4 - Farms, Clusters, Nodes, Parameters");
	System.out.println("5 - Farms, Clusters, Nodes, Parameters + last known value");
	System.out.println();
	System.out.println("Your choice (4) : ");
	
	int level = 4;
	
	try{
	    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
	    
	    level = Integer.parseInt(br.readLine().trim());
	    
	    if (level<1 || level>5) level=4;
	}
	catch (Exception e){
	}
	
	
	WriterConfig wc = (WriterConfig) ConfigBean.getConfig().get(0);
	
	System.out.println("Configuration determined from "+wc.sTableName+" :\n");
	
	DB db = new DB("SELECT distinct mfarm FROM "+wc.sTableName+" ORDER BY mfarm ASC;");
    
	while (db.moveNext()){
	    System.out.println("Farm : "+quote(db.gets("mfarm", null)));
	    
	    if (level==1) continue;
	    
	    DB db2 = new DB("SELECT distinct mcluster FROM "+wc.sTableName+" WHERE mfarm"+eq(quote(db.gets("mfarm", null)))+" ORDER BY mcluster ASC;");
	    
	    while (db2.moveNext()){
		System.out.println("  Cluster : "+quote(db2.gets("mcluster", null)));
		
		if (level==2) continue;
		
		DB db3 = new DB("SELECT distinct mnode FROM "+wc.sTableName+" WHERE mfarm"+eq(quote(db.gets("mfarm", null)))+" AND mcluster"+eq(quote(db2.gets("mcluster", null)))+" ORDER BY mnode ASC;");
		
		while (db3.moveNext()){
		    System.out.println("    Node : "+quote(db3.gets("mnode", null)));
		    
		    if (level==3) continue;
		    
		    DB db4 = new DB("SELECT distinct mfunction FROM "+wc.sTableName+" WHERE mfarm"+eq(quote(db.gets("mfarm", null)))+" AND mcluster"+eq(quote(db2.gets("mcluster", null)))+" AND mnode"+eq(quote(db3.gets("mnode", null)))+" ORDER BY mfunction ASC;");
		    
		    while (db4.moveNext()){
		    	String s = quote(db4.gets("mfunction", null));
			if (s==null) s="null";
			while (s.length()<15)
			    s+=" ";
			
			System.out.print("      Parameter : "+s);
		    
			if (level==4) {
			    System.out.println();
			    continue;
			}
		    
			DB db5 = new DB("SELECT mval FROM "+wc.sTableName+" WHERE mfarm"+eq(quote(db.gets("mfarm", null)))+" AND mcluster"+eq(quote(db2.gets("mcluster", null)))+" AND mnode"+eq(quote(db3.gets("mnode", null)))+" AND mfunction"+eq(quote(db4.gets("mfunction", null)))+" ORDER BY rectime DESC LIMIT 1;");
			
			double d = db5.getd("mval");
			d = Math.floor(d*1000) / 1000;
		    
		    
			System.out.println("\tlast value : "+d);
		    }
		}
	    }
	    System.out.println();
	}
    
    }
    
    private static final String quote(String s){
	if (s==null) return null;
	else return "'"+s+"'";
    }
    
    private static final String eq(String v){
	if (v==null) return " is null";
	else return "="+v;
    }
    
}
