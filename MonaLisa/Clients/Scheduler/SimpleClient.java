import lia.Monitor.JiniClient.Store.Main;

import lia.Monitor.monitor.monPredicate;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.AccountingResult;

import lia.Monitor.monitor.DataReceiver;

import lia.Monitor.monitor.MFarm;

import lia.Monitor.DataCache.DataSelect;

import lia.Monitor.Store.TransparentStoreFast;
import lia.Monitor.Store.TransparentStoreFactory;

import java.util.Vector;

public class SimpleClient {

    public static void main(String args[]){
	// start the repository service
	Main jClient = new Main();
	
	// register a MyDataReceiver object to receive any new information
	jClient.addDataReceiver(new MyDataReceiver());
	
	try {
	    Thread.sleep(60*1000);
	}catch(Throwable t) {
	}
		
	//Uncomment this if you would like to stop the client when main() exits
	//jClient.stopIt();
    }
    
    
    private static class MyDataReceiver implements DataReceiver {
	
	private Vector vWatchFor;		// the predicates for the data i'm interested in
	
	private TransparentStoreFast store;	// to access history data
    
	public MyDataReceiver(){
	    vWatchFor = new Vector();
	
	    // i'm interested in any Load and cluster usage (free nodes / busy nodes) data
	    //                                  /- farm name, * = any farm
	    //				       /    /- cluster name, * = any cluster
	    //                                /    /     /- node name, * = any node
	    //				     /	  /     /     /-   (-1, -1) = (min, max) time mean any new data
	    //				    /	 /     /     /    	/- the actual parameter
	    vWatchFor.add(new monPredicate("*", "*", "*", -1, -1, new String[]{"Load5"}, null));
	    vWatchFor.add(new monPredicate("*", "*", "*", -1, -1, new String[]{"Load_05"}, null));
	    vWatchFor.add(new monPredicate("*", "*", "*", -1, -1, new String[]{"Load_15"}, null));
	    
	    try{
	        store = (TransparentStoreFast) TransparentStoreFactory.getStore(true);
	    }
	    catch (lia.Monitor.Store.StoreException e){
		System.err.println("Cannot instantiate the store ... not good ...");
		store = null;
	    }
	}
	
	public void addResult(Result r){
	    for (int i=0; i<vWatchFor.size(); i++){
		monPredicate pred = (monPredicate) vWatchFor.get(i);
		
		// extract from "r" only the data i'm interested in
		Result rTemp = DataSelect.matchResult(r, pred);
		
		// if "r" contains something interesting then display this information
		// and the history values for the last 30 minutes
		if (rTemp!=null){
		    System.err.println("Data received : "+rTemp.FarmName+"/"+rTemp.ClusterName+"/"+rTemp.NodeName+" : "+rTemp.param_name[0]+"="+rTemp.param[0]);
		    
		    monPredicate pTemp = new monPredicate(
			rTemp.FarmName!=null ? rTemp.FarmName : "*", 
			rTemp.ClusterName!=null ? rTemp.ClusterName : "*", 
			rTemp.NodeName!=null ? rTemp.NodeName : "*", 
			-30*60*1000, -1, 
			new String[]{rTemp.param_name[0]}, 
			null
		    );
		    
		    // extract history information that matches this predicate
	    	    Vector vData = store.getResults(pTemp);

		    synchronized (vWatchFor){	// do not overlap the output, addResult(Result) might be called from separate threads
		        System.err.println("\t\tLast 30 minutes values : "+vData.size());
		    
			double d = 0;
		    	for (int j=0; j<vData.size(); j++){
			    Result rt = (Result) vData.get(j);
			    d += rt.param[0];
			}
		    
			System.err.println("\t\tLast 30 minutes average for this data : "+(vData.size()>0 ? ""+(d/vData.size()) : "NA")+"\n");
		    }
		}
	    }
	}
	
	public void addResult(eResult er){
	}
	
	public void addResult(ExtResult er){
	}
	
	public void addResult(AccountingResult ar){
	}
	
	public void updateConfig(MFarm f){
	}
	    
    }

}
