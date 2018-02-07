package lia.Monitor.monitor;

/**
 * 
 * Defines a Predicate to select data from the input stream and from the DB. The <code>monPredicate</code> class
 * provides basic filtering functionality for data.
 * <p>
 * An object of this type implements <code>java.io.Serializable</code> because it is send using RMI or another
 * protocol(WSDL??) to a <code>lia.Monitor.monitor.DataStore</code>.
 * </p>
 * 
 * @see lia.Monitor.monitor.DataStore#Register
 */
public class monPredicate implements java.io.Serializable {

    /**
     * it should be the same since first release of MonALISA ( FLAMES ) :) 
     * added @ ML 1.2.54
     */
    private static final long serialVersionUID = 1990241519608596646L;

    /**
     * It should be the name of the Farm. It can be an regular expression.
     */
    public String Farm;

    /**
     * It should be the name of the Cluster. It can be an regular expression.
     */
    public String Cluster;

    /**
     * It should be the name of the Node. It can be an regular expression.
     */
    public String Node;

    /**
     * This vector stores names of the functions for basic filtering. If this parameter is <code>null</code> it is
     * ignored and also <code>constraints</code> parameter from <code>monPredicate</code>.
     * <p>
     * The names must be the same as the ones found in <code>lia.Monitor.monitor.MonModuleInfo.ResTypes</code>
     * </p>
     * 
     * @see lia.Monitor.monitor.MonModuleInfo#ResTypes
     * @see monPredicate#constraints
     */
    public String[] parameters;

    /**
     * Provides basic constraints for functions passed in <code>parameters</code>. It can be <code>null</code>.
     * <p>
     * If <code>parameters</code> is <code>null</code> it is ignored.
     * </p>
     * <p>
     * Basic logical operations like <code>AND</code>, <code>OR</code> are supported for the <i>Filtering
     * constraints</i>.
     * </p>
     * <code>constraints</code> can be basically of <b>two</b> types:
     * <ul>
     * <li><i>Filtering constraints</i> <br>
     * Logical constraints for the corresponding column in <code>parameters</code> </li>
     * <li><i>Grouping Functions</i> <br>
     * <code>AVG</code>, <code>MIN</code> and <code>MAX</code> are supported until now. It can be only one
     * function. </li>
     * </ul>
     * <p>
     * There can be either a <i>Grouping Function</i> either a <i>Filtering Constraint</i>.If an element in the<code>constraints</code>
     * starts with a:
     * <ul>
     * <li> <i>Grouping Function</i> like <code>AVG</code>, <code>MIN</code>, <code>MAX</code> this must be the
     * only function (you cannot have another <i>Grouping Function</i> or <i>Filtering Constraint</i> applied to the
     * corresponding <code>parameter</code>) </li>
     * 
     * <li> <i>Filtering Constraint</i> (&gt,&lt,=) than you can specify either float values, either <i>Grouping
     * Function</i> for the right operand. <b>Only the right operand can be specified!</b> The left is assumed to be
     * the corresponding function in <code>parameters</code>. There can be more than one <i>Filtering constrains</i>
     * separated by logical operators like <code>AND</code> or <code>OR</code>(<code>and</code> and
     * <code>or</code> will also work). A recursive &quot;definition&quot; for <i>Filtering Constraint</i> will be:
     * <br>
     * <b><i>Filtering Constraint</i> := ((<i>Filtering Constraint</i>)(<code>AND</code>|<code>OR</code>)?)+</b>
     * </li>
     * </ul>
     * </p>
     * <p>
     * <b>Examples:</b> <br>
     * <ul>
     * <li><i>Filtering constraints</i> <br>
     * &gt 2.3 <code>AND</code> &lt 2.5 <code>AND</code> &lt <code>AVG</code> <br>
     * Filters values greater than 2.3 and less than 2.5 and greater than average(<i>Grouping Function</i>
     * <code>AVG</code> is used) value for the corresponding function in <code>parameters</code> <br>
     * </li>
     * <li><i>Grouping Functions</i> <br>
     * <code>AVG</code> - calculates the average value for the corresponding function in <code>parameters</code>
     * <br>
     * <code>MIN</code> - calculates the minimum for the corresponding function in <code>parameters</code> <br>
     * <code>MAX</code> - calculates the maximum for the corresponding function in <code>parameters</code> </li>
     * </ul>
     * <p>
     * The <i>Grouping Function</i> are calculated between <code>tmin</code> and <code>tmax</code>
     * </p>
     * 
     * @see monPredicate#parameters
     */
    public String[] constraints;

    /**
     * <p>
     * It can be positive, negative, or zero.
     * <ul>
     * <li> <code>tmin</code> &gt; 0 is used to express an absolute time in milliseconds since January 1, 1970,
     * 00:00:00 GMT. Possible values for <code>tmax</code> are:
     * <ul>
     * <li> If <code>tmax</code> &gt; 0 then it means that this <code>monPredicate</code> is used to filter some
     * stored values between two given absolute times [<code>tmin</code>, <code>tmax</code>]. The values are
     * considered to be <code>DataStore</code> times. The <code>Result</code> is sent to the <code>Client</code>
     * only <b>once</b>. </li>
     * <li> If <code>tmax</code> = 0 then it means that this <code>monPredicate</code> is used to filter some stored
     * values between <code>tmin<code> and the local time
     * on <code>DataStore</code>(present time). The <code>Result</code> is sent
     * to the <code>Client</code> only <b>once</b>.
     * </li>
     * <li>
     * If <code>tmax</code> &lt; 0 then it means that this <code>monPredicate</code>
     * is used to filter some stored values between <code>tmin<code> and the local time
     * on <code>DataStore</code>. The difference between this case and the previous one
     * is that the <code>DataStore</code> must provide filtered data based on 
     * this <code>monPredicate</code> to the <code>Client</code> <b>periodically</b>.
     * </li>
     * </ul>
     * </li>
     *
     * <li>
     * <code>tmin</code> &lt; 0 represents an relative time to the local time
     * on the <code>DataStore</code> where this <code>monPredicate</code>
     * is used to filter data.
     * Possible values for <code>tmax</code> are:
     * <ul>
     * <li>
     * If <code>tmax</code> = 0 then it means that this <code>monPredicate</code>
     * is used to filter some stored values between local time - <code>tmin<code>
     * and the local time([-<code>tmin</code>, 0]) on <code>DataStore</code>
     * (0 is the present time on <code>DataStore</code>).
     * The <code>Result</code> is sent to the <code>Client</code> only <b>once</b>.
     * </li>
     * <li>
     * If <code>tmax</code> &lt 0 is the same case as previous.
     * The difference between this case and the previous one is that the
     * <code>DataStore</code> must provide filtered data based on 
     * this <code>monPredicate</code> to the <code>Client</code> <b>periodically</b>.
     * </li>
     * </ul>
     * <li>
     * <code>tmin</code> = 0. 
     * <code>monPredicate</code> does no time filtering on <code>DataStore</code>.
     * In this case the <i>Filtering constraints</i> from {@link monPredicate#constraints}
     * are applied only on received data, but the <i>Grouping Functions</i> applies
     * to the <b>entire</b> set of data.
     * <ul>
     * <li>
     * If <code>tmax</code> = 0 the <code>Result</code> is sent to the
     * <code>Client</code> only <b>once</b>.
     * </li>
     * <li>
     * If <code>tmax</code> &lt; 0 the <code>Result</code> is sent to the
     * <code>Client</code> <b>periodically</b>.
     * </li>
     * </ul>
     * </li>
     * </ul>
     * </p>
     * <p>
     * The <i>Grouping Functions</i> applies to entire set of data on <code>DataStore</code>
     * only when <code>tmin = 0</code> and <code>tmax = 0</code>, in all other cases are applied
     * on the specified time interval.
     * </p>
     * 
     * @see monPredicate#constraints
     */
    public long tmin;

    /**
     * <p>
     * It can be positive, negative, or zero. See {@link monPredicate#tmin}
     * </p>
     * 
     * @see monPredicate#constraints
     */
    public long tmax;

    /**
     * The <code>id</code> for this <code>monPredicate</code>. It should be unique for a couple (<code>Client</code>,
     * <code>monPredicate</code>). It is assigned by the <code>Client</code>.
     */
    public int id;

    /**
     * Should be a RT predicate ... will try to  get ONLY the lastValue from Cache
     * @since ML 1.5.0
     */
    public boolean bLastVals;
    
    /**
     * 
     * It initializes a <code>monPredicate</code>. The parameters are used for filtering the data.
     * 
     * @param farm
     *            The name of the farm.
     * @param cluster
     *            The name of the cluster.
     * @param node
     *            The name of the node.
     * @param tmin
     *            Starting time (in milliseconds). It can be positive or negative.
     * @param tmax
     *            Ending time (in milliseconds). It can be positive or negative.
     * @param parameters
     *            Names of the functions(module parameters) that are filtered. Same names as from
     *            <code>lia.Monitor.monitor.ModuleInfo.ResTypes</code> .
     * @param constrains
     *            constrains that this <code>monPredicate</code> provides.
     * 
     */
    public monPredicate(String farm, String cluster, String node, long tmin, long tmax, String[] parameters, String[] constraints) {
        Farm = farm;
        Cluster = cluster;
        Node = node;
        this.parameters = parameters;
        this.constraints = constraints;
        this.tmin = tmin;
        this.tmax = tmax;
        bLastVals = false;
    }

    /**
     * 
     * It initializes an empty <code>monPredicate</code>.
     * 
     */
    public monPredicate() {
        bLastVals = false;
    }
    
    /**
     * @since ML 1.2.54
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("[ ").append(id).append(" ] OnlyLastVal: ").append(bLastVals);
        sb.append(" ---> ").append(Farm).append("/").append(Cluster).append("/").append(Node);
        sb.append("\ttmin = ").append(tmin).append("\ttmax = ").append(tmax).append("\t[");
        if(parameters == null) {
            sb.append(" null");
        } else {
            for(int i=0; i<parameters.length; i++) {
                sb.append(" ").append(parameters[i]);
                if(i+1 < parameters.length) {
                    sb.append(" |");
                }
            }
        }
        sb.append(" ] [");
        if(constraints == null) {
            sb.append(" null");
        } else {
            for(int i=0; i<constraints.length; i++) {
                sb.append(" ").append(constraints[i]);
                if(i+1 < constraints.length) {
                    sb.append(" |");
                }
            }
        }
        sb.append(" ]");
        
        return sb.toString();
    }
}
