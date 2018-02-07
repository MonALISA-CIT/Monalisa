package lia.Monitor.monitor;


public class ExtendedResult extends Result implements Comparable<Result>{
	private static final long serialVersionUID = 2008995010099502604L;
	public double min;
    public double max;
    
    public ExtendedResult() { 
    	// nothing
    }
	
    public String toString(){
        return time+": "+FarmName+"/"+ClusterName+"/"+NodeName+"/"+param_name[0]+" : "+param[0]+" ("+min+","+max+")";
    }
    
    public int compareTo(final Result r){
	    final long diff = time-r.time;
	    
	    if (diff<0) return -1;
	    if (diff>0) return 1;
	
	    return 0;
    }
}
