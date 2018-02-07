import lia.web.bean.Generic.ConfigBean;
import lia.web.bean.Generic.WriterConfig;

import java.util.Iterator;

public class ShowStoreConfig {

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
    
	Iterator it = ConfigBean.getConfig().iterator();
	
	System.out.println("\nTable name          \tWrite mode\tInterval  \tSamples\n");
	
	while (it.hasNext()){
	    WriterConfig wc = (WriterConfig) it.next();
	
	    String sn = wc.sTableName;
	    
	    while (sn.length()<20)
		sn+=" ";
	
	    String sm = "mediated  ";
	    if (wc.iWriteMode==1) sm="unmediated";
	    if (wc.iWriteMode==2) sm="objects   ";
	    
	    String st = secondsToStringInterval(wc.iTotalTime);
	    while (st.length()<10)
		st+=" ";
	
	    System.out.print(sn+"\t"+sm+"\t"+st);
	    if (wc.iWriteMode==0) System.out.print("\t"+wc.iSamples);
	    else                  System.out.print("\t--");
	    System.out.println();
	}
	
    }
    
    private static final String secondsToStringInterval(int i){
	String s = i+" sec";
	
	if (i>=60){
	    i /= 60;
	    s = i+" min";
	    
	    if (i>=60){
	        i /= 60;
		s = i+" hour";
		if (i>1) s+="s";
		    
		if (i>=24){
		    i /= 24;
		    s = i+" day";
		    if (i>1) s+="s";
			
		    if (i>=30){
			i /= 30;
			s = i+" month";
			if (i>1) s+="s";
			    
			if (i>=12){
			    i /= 12;
			    s = i+" year";
			    if (i>1) s+="s";
			}
		    }
	        }
	    }
	}
	
	return s;
    }
    
}
