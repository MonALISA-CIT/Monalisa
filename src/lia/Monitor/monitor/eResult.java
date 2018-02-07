package lia.Monitor.monitor;

import java.util.Date;

/**
 * Base class for generic monitoring values. For numeric monitoring values {@link eResult} class should be used.
 * The structure is the same as it is in {@link Result}
 * 
 * The only difference is that the <code>param</code> can hold any type of data(e.g <code>String</code>),
 * not just <code>double</code> values.
 * 
 * <b>WARNING</b> The values stored in <code>param</code> MUST implement <code>java.io.Serializable</code>
 * 
 * This class is also used to dynamically remove Clusters, Nodes and Parameters from
 * internal configuration using the following algorithm:
 * <pre>
 * <code>
 *      if (NodeName == null) then
 *          remove ClusterName;
 *      else if (param == null || this.param_name == null)
 *          remove NodeName;
 *      else
 *          for index in [0 ... param_name.length-1]
 *              if ( param[i] == null )
 *                  remove param_name[i];
 * <code>
 * </pre>
 * 
 * This can be used by the monitoring modules to notify the data collector that
 * the configuration exported by the module has been changed.
 * 
 * @see Result 
 */
public class eResult implements java.io.Serializable, TimestampedResult {

    /**
     * @since ML 1.5.8
     */
    private static final long serialVersionUID = 8196232765921613152L;

    public String[] param_name;

    public long time;

    public Object[] param;

    public String NodeName;

    public String ClusterName;

    public String FarmName;

    public String Module;

    public eResult(final String farm, final String cluster, final String NodeName, final String Module, final String[] Param_name) {
        this.NodeName = NodeName;
        this.ClusterName = cluster;
        this.FarmName = farm;
        this.Module = Module;
        param_name = Param_name;
        if (param_name != null) {
            param = new Object[param_name.length];
        }
    }

    public eResult() {
    	// empty
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder(512);
        sb.append(" --> \t").append(NodeName).append("\t").append(ClusterName).append("\t").append(FarmName);
        sb.append("\tTime = [ ").append(time).append("/").append(new Date(time)).append(" ]");
        
        if (param_name == null || param == null) {
            sb.append("\tnull params");
        } else {
            int len = param_name.length;
            
            if(len != param.length) {//this should not happen
                sb.append("\n !!!! param.len [ ").append(param.length).append(" ] != param_name.len [ ").append(len).append(" ] !!! \n");
                if(len > param.length) {
                    len = param.length;
                }
            }
            
            for (int i = 0; i < len; i++) {
                sb.append("     * ").append(param_name[i]).append(" = \t").append(param[i]);
            }
        }
        
        return sb.toString();
    }

    /**
     * Get the location of a parameter name
     * 
     * @param f
     * @return index of the parameter name, -1 if it cannot be found
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
     * Add a new parameter/value pair to this eResult
     * 
     * @param c parameter name
     * @param v value
     */
    public void addSet(final String c, final Object v) {
        if (param == null || param.length==0) {
            param = new Object[]{v};
            param_name = new String[]{c};
            return;
        }
        
        final int N = param.length;

        final String[] nparam_name = new String[N + 1];
        final Object[] nparam = new Object[N + 1];
        
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
