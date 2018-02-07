package lia.Monitor.monitor;

import java.util.Date;

/**
 *  Base class for numeric monitoring values. For generic monitoring values {@link eResult} class should be used.
 *  
 *  Inside MonALISA framework all the monitoring values are identified by the tupple
 *  (<code>FarmName, ClusterName, NodeName, ParamName</code>) and the <code>time</code> 
 *  
 *  The relationship between <code>FarmName</code> and <code>ClusterName</code> is "1-to-many",
 *  as it is for the relation between <code>ClusterName</code> and <code>NodeName</code>
 *  
 *  This will map any <code>Result</code> to the following structure
 *  <pre>
 *  <code>
 *  
 *  FarmName-|
 *           |--ClusterName_1
 *           |     |
 *           |     |---NodeName_1 <-->| param_name[1..n]
 *           |     |                  | param[1..n]
 *           |     |
 *           |     |---NodeName_i
 *           |
 *           |--ClusterName_n
 *           |     |
 *           |     |---NodeName_k
 *           |
 *  </code>
 *  </pre>
 *
 *  Every <code>NodeName</code> can have one or more parameters. The array <code>param_name[]</code>
 *  stores the parameter names which should be mapped 1-to-1 to <code>param[]</code> values.
 *  
 *  
 *  @see eResult 
 */
public class Result implements java.io.Serializable, TimestampedResult {

    /**
     * @since first version of MonALISA
     */
    private static final long serialVersionUID = -1024910831654949683L;

    /**
     * Parameter names for this Result. It should have the same length as <code>param</code>
     */
    public String[] param_name;

    /**
     * Parameter values for this Result. It should have the same length as <code>param_name</code>
     */
    public double[] param;
    
    /**
     * Time for this Result in milliseconds since epoch. 
     */
    public long time;

    /**
     * Node name
     */
    public String NodeName;

    /**
     * Cluster name
     */
    public String ClusterName;

    /**
     * Farm/Service name
     */
    public String FarmName;

    /**
     * The module that generated this Result
     */
    public String Module;

    public Result(final String farm, final String cluster, final String nodeName, final String module) {
        this(farm, cluster, nodeName, module, null, null);
    }

    /**
     * 
     * @param farm - FarmName for the Result
     * @param cluster - ClusteName for the Result
     * @param nodeName - NodeName for the Result
     * @param module - Module Name
     * @param Param_name - Will set the param_name and allocates <code>param</code>
     */
    public Result(final String farm, final String cluster, final String nodeName, final String module, final String[] Param_name) {
        this(farm, cluster, nodeName, module, Param_name, null);
    }

    /**
     * @param farm - FarmName for the Result
     * @param cluster - ClusteName for the Result
     * @param nodeName - NodeName for the Result
     * @param module - Module Name
     * @param Param_name - Will set the param_name and allocates <code>param</code>
     * @param paramValues - initial values for each parameter
     */
    public Result(final String farm, final String cluster, final String nodeName, final String module, final String[] Param_name, final double[] paramValues) {
        this.NodeName = nodeName;
        this.ClusterName = cluster;
        this.FarmName = farm;
        this.Module = module;
        param_name = Param_name;
        if (param_name != null) {
            param = new double[param_name.length];
            if(paramValues != null) {
                System.arraycopy(paramValues, 0, param, 0, param.length);
            }
        }
    }
    
    /**
     * Empty constructor
     */
    public Result() {
    	// nothing
    }

	public String toString() {
        final StringBuilder sb = new StringBuilder(512);

        sb.append(" --> \t").append(NodeName);
        sb.append("\t ").append(ClusterName);
        sb.append("\t ").append(FarmName);
        sb.append("\t Time = [ ").append(time).append("/").append(new Date(time)).append(" ]");

        if (param_name == null) {
            sb.append("    param_name is null");
            if (param == null) {
                sb.append("    and param is also null");
            } else {
                sb.append("    and param [ length = ").append(param.length).append(" ] is NOT");
                for (int i = 0; i < param.length; i++) {
                    sb.append("     * param[ ").append(i).append(" ] = ").append(param[i]).append("\t");
                }
            }
            return sb.toString();
        }

        int len = param_name.length;

        if (len != param.length) {// this should not happen
            sb.append("\n !!!! param.len [ ").append(param.length).append(" ] != param_name.len [ ").append(len).append(" ] !!! \n");
            if (len > param.length) {
                len = param.length;
            }
        }

        for (int i = 0; i < len; i++) {
            sb.append("     * ").append(param_name[i]).append(" = \t").append(param[i]);
        }

        return sb.toString();
    }

    /**
     * 
     * @param f - The parameter name to look for
     * @return - The index of the parameter if found, or -1 if not found. If f is null or <code>param_name</code> is null returns -1.
     */
    public int getIndex(final String f) {
        if (f == null || param_name == null || param_name.length==0) 
        	return -1;
        
        final int N = param_name.length;
        
        for (int i = 0; i < N; i++) {
            if (f.equals(param_name[i])) {
                return i;
            }
        }
        
        return -1;
    }

    /**
     * Add a new parameter/value pair to this Result
     * 
     * @param c parameter name
     * @param v value
     */
    public void addSet(final String c, final double v) {
        if (param == null || param.length==0) {
            param = new double[]{v};
            param_name = new String[]{c};
            return;
        }
        
        final int N = param.length;

        final String[] nparam_name = new String[N + 1];
        final double[] nparam = new double[N + 1];
        
        System.arraycopy(param_name, 0, nparam_name, 0, N);
        System.arraycopy(param, 0, nparam, 0, N);
        
        nparam_name[N] = c;
        nparam[N] = v;

        param = nparam;
        param_name = nparam_name;
    }

	public long getTime() {
		return time;
	}

}
