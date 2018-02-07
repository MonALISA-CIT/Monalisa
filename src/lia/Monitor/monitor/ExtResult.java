package lia.Monitor.monitor;


import java.util.Date;


public class ExtResult implements java.io.Serializable, TimestampedResult {
   /**
	 * copied from the output of `serialver`
	 */
	private static final long serialVersionUID = 1672183981569272576L;
	
   public String[]  param_name;
   public long   time;
   public double[] param;
   public String NodeName;
   public String ClusterName;
   public String FarmName;
   public String Module;
   public Object extra;
   
   public Result getResult(){
        Result r = new Result(FarmName, ClusterName, NodeName, Module, param_name);
	
	for (int i=0; i<param.length; i++){
	    r.param[i] = param[i];
	}
	r.time = time;
	return r;
   }
 

public ExtResult ( String farm , String cluster,String NodeName, String Module, String[]  Param_name) {
  this.NodeName =NodeName ;
  this.ClusterName=cluster;
  this.FarmName=farm;
  this.Module = Module;
  param_name = Param_name;
  if ( param_name != null ) {
    param= new double [param_name.length];
  }
}

public ExtResult() { } 

public String toString() {
  String ans = " --> \t" + NodeName +"\t "+ClusterName+"\t"+FarmName+"\tTime = " + new Date(time) ;
  for ( int i=0; i < param_name.length ; i++ ) {
     ans += "     * "+ param_name[i] + " = \t" + param[i];
  }
  return ans;
}


public int getIndex( String f ) {
 int index = -1 ;
 for ( int i=0; i < param_name.length ; i ++ ) {
   if ( f.equals( param_name[i] )) {
     index = i;
     break;
   }
 }
  return index;
}

public void addSet( String c , double v )
{
  int N ;
  if ( param == null ) N = 0;
  else N = param.length;

  String [] nparam_name = new String [N+1];
  double [] nparam      = new double[N+1];
  for ( int i=0; i < N; i++ ) {
    nparam_name[i] = param_name[i];
    nparam[i] = param[i];
  }
  nparam_name[N] = c;
  nparam[N] = v;
  N ++;

 param = null;
 param_name = null;
 param = nparam;
 param_name = nparam_name;

}

public long getTime() {
	return time;
}


}

