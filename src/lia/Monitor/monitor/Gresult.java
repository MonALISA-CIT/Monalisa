package lia.Monitor.monitor;

public class Gresult implements java.io.Serializable, TimestampedResult {
	/**
	 * Copied from the output of `serialver`
	 */
	private static final long serialVersionUID = 8084475295190806293L;
	
   public long   time;
   public String ClusterName;
   public String FarmName;
   public String Module;

   public int TotalNodes ;
   public int Nodes ;
   public double mean;
   public double max ;
   public double min;
   public double sum ;
   public int nbin ;
   public int[]  hist ;
   
 

public Gresult ( String farm , String cluster, String Module) {
  this.ClusterName=cluster;
  this.FarmName=farm;
  this.Module = Module;
}

public Gresult() { } 

public long getTime() {
	return time;
}

}

