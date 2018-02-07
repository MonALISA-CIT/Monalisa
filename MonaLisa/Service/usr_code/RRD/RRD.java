import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import lia.Monitor.DataCache.DataSelect;
import lia.Monitor.monitor.ExtResult;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.dbStore;
import lia.Monitor.monitor.monPredicate;
import lia.util.DynamicThreadPoll.SchJob;

public class RRD extends SchJob implements MonitoringModule, dbStore {

    private MonModuleInfo mmi = null;
    private MNode mn = null;
    
    private String sRRDPath = null;
    private String sRRDFile = null;
    
    public  MonModuleInfo init( MNode node, String args ){
	mn = node;
	
	mmi = new MonModuleInfo();
	mmi.setName("RRD");
	mmi.setState(0);

	String sError = null;
	try{
	    Properties p = new Properties();
	    p.load(new FileInputStream(args));
	    
	    sRRDPath = p.getProperty("rrdpath");
	    sRRDFile = p.getProperty("rrdfile");

	    File f = new File(sRRDPath+"/rrdtool");
	    if (!f.isFile()){
		throw new IOException("rrdtool cannot be found in the '"+sRRDPath+"' folder");
	    }
	    
	    f = new File(sRRDFile);
	    if (!f.isFile() || !f.canRead()){
		throw new IOException("cannot read from data file '"+sRRDFile+"'");
	    }
	    
	    Vector v = getRRDOutput("fetch", "AVERAGE");
	    
	    String s = ((String)v.elementAt(0)).trim();
	    StringTokenizer st = new StringTokenizer(s, " \t");
	    
	    v = new Vector();
	    
	    while (st.hasMoreTokens()) {
		v.addElement(st.nextToken());
	    }
	    
	    String vs[] = new String[v.size()];
	    
	    for (int i=0; i<v.size(); i++)
		vs[i] = (String) v.elementAt(i);
	
	    mmi.setResType(vs);
	}
	catch (Exception e){
	    sError = e.getMessage();
	}
	
	if ( sError != null ){
	    mmi.addErrorCount();
	    mmi.setState(1);	// error
	    mmi.setErrorDesc(sError);
	    return mmi;
	}
	
	mmi.lastMeasurement = System.currentTimeMillis();
		
	return mmi;
    }
    
    private Vector getRRDOutput(String sCommand, String sParams) throws Exception {
	Vector v = new Vector();
	
	v.addElement(sRRDPath+"/rrdtool");
	v.addElement(sCommand);
	v.addElement(sRRDFile);
	
	if (sParams!=null && sParams.length()>0){
	    StringTokenizer st = new StringTokenizer(sParams, " ");
	    while (st.hasMoreTokens()){
		v.addElement(st.nextToken());
	    }
	}
	
	String vs[] = new String[v.size()];
	
	for (int i=0; i<v.size(); i++)
	    vs[i] = (String) v.elementAt(i);
	
	Vector vr = getOutput(vs);
	
	if (vr!=null && vr.size()>0){
	    String sl = (String) vr.elementAt(0);
	    
	    if (sl.startsWith("RRDtool ") || sl.startsWith("ERROR: ")){
		throw new IOException("Error while executing : "+v.toString());
	    }
	}
	else{
	    throw new IOException("null output from : "+v.toString());
	}
	
	return vr;
    }
    
    private Vector getOutput(String vs[]) throws Exception {
	Runtime rt = Runtime.getRuntime();
	    
	Process pr = rt.exec(vs);
	    
	InputStream is = pr.getInputStream();
	BufferedReader br = new BufferedReader(new InputStreamReader(is));
	    
	Vector v = new Vector();
	
	String s = null;
	while ( (s = br.readLine()) != null ){
	    v.addElement(s);
	}
	
	pr.waitFor();
	
	return v;
    }
    
    // MonitoringModule
    
    public String[] ResTypes() {
	return mmi.getResType();
    }
    
    public String   getOsName(){
	return "Linux";
    }
    
    public Object   doProcess() throws Exception {
	if (mmi.getState()!=0){
	    throw new IOException("cannot read from rrd");
	}
	
	long lLastMeasurement = mmi.lastMeasurement;
	mmi.lastMeasurement = System.currentTimeMillis();
	Vector v = getRRDOutput("fetch", "AVERAGE -s "+(lLastMeasurement/1000));
	
	String[] vs = ResTypes();
	
	Vector vr = new Vector();
	
	for (int i=0; i<v.size(); i++){
	    String s = (String) v.elementAt(i);
	    
	    if (s.matches("^[1-9][0-9]{9,10}: .*$")){
		StringTokenizer st = new StringTokenizer(s, " :");
		
		String sTime = st.nextToken();
		
		long l = Long.parseLong(sTime) * 1000;
		
		if ( l < lLastMeasurement ) {
		    continue;
		}
		
		ExtResult er   = new ExtResult();
		er.FarmName    = getFarmName();
		er.ClusterName = getClusterName();
		er.NodeName    = mn.getName();
		er.Module      = mmi.getName();
		er.time        = l;
		
		for (int j=0; j<vs.length; j++){
		    String sValue = st.nextToken();
		    
		    if (!sValue.equals("nan")){
			double d = Double.parseDouble(sValue);
			
			er.addSet(vs[j], d);
		    }
		}
		
		if (er.param_name==null || er.param_name.length<=0){
		    continue;
		}
		
		vr.addElement(er);
	    }
	}
	return vr;
    }

    public MNode getNode(){
	return mn;
    }
 
    public String getClusterName(){
	return mn.getClusterName();
    }
    
    public String getFarmName(){
	return mn.getFarmName();
    }

    public boolean isRepetitive(){
	return true;
    }

    public String getTaskName(){
	return mmi.getName();
    }
    
    public MonModuleInfo getInfo(){
	return mmi;
    }
    
    // dbStore interface implementation

    public java.util.Vector select ( monPredicate p ) {
	try{
		long lStart = p.tmin;
	    long lEnd   = p.tmax;
	    if (lStart <=0 ){
		lStart = System.currentTimeMillis() + lStart;
		lEnd   = System.currentTimeMillis() + lEnd;
		
		if (lEnd < lStart)
		    lEnd = lStart;
	    }
	    
	    Vector vr = new Vector();
	    
	    String[] vs = ResTypes();
	    boolean vb[] = new boolean[vs.length];
	    boolean bExit = true;
	    
	    // first check that at least one result type matches the given predicate, if not quit
	    
	    for (int i=0; i<vs.length; i++){
		Result r      = new Result();
		r.FarmName    = getFarmName();
		r.ClusterName = getClusterName();
		r.NodeName    = mn.getName();
		r.Module      = mmi.getName();
		r.time        = lStart;
		r.addSet(vs[i], (double) 1.0);
		
		if (DataSelect.matchResult(r, p) != null){
		    vb[i] = true;
		    bExit = false;
		}
		else {
		    vb[i] = false;
		}
	    }
	    
	    if (bExit){
		// the given predicate does not match any function we have, so just quit
		return vr;
	    }
	    
	    Vector v = getRRDOutput("fetch", "AVERAGE -s "+(lStart/1000)+" -e "+(lEnd/1000));

	    for (int i=0; i<v.size(); i++){
		String s = (String) v.elementAt(i);
	    
	        if (s.matches("^[1-9][0-9]{9,10}: .*$")){
		    StringTokenizer st = new StringTokenizer(s, " :");
		
	    	    String sTime = st.nextToken();
		
		    long l = Long.parseLong(sTime) * 1000;
		
		    if ( l < lStart ) {
			continue;
		    }
		    
		    if ( l > lEnd ){
			break;
		    }
		
		    for (int j=0; j<vs.length; j++){
			String sValue = st.nextToken();
		    
			if (vb[j]==true && !sValue.toLowerCase().equals("nan")){
			    double d = Double.parseDouble(sValue);
		    
			    Result r      = new Result();
			    r.FarmName    = getFarmName();
			    r.ClusterName = getClusterName();
			    r.NodeName    = mn.getName();
			    r.Module      = mmi.getName();
			    r.time        = l;
			    r.addSet(vs[j], d);
			
			    if (DataSelect.matchResult(r, p) != null){
				vr.addElement(r);
			    }
			}
		    }
		}
	    }
	    return vr;
	} catch (Exception e){
	    return null;
	}
    }
    
    
    public static final void main(String[] args) {
        String host = "bigmac.fnal.gov";
        String ad = null;

        RRD aa = new RRD();
        MonModuleInfo info = aa.init(new MNode(host, ad, null, null), "alice01.rogrid.pub.ro,ui.rogrid.pub.ro");

        try {
            for (;;) {
                Object bb = aa.doProcess();

                if (bb instanceof Vector) {
                    Vector v = (Vector) bb;
                    if (v != null) {
                        System.out.println(" Received a Vector having " + v.size() + " results");
                        for (int i = 0; i < v.size(); i++) {
                            System.out.println(v.elementAt(i));
                        }

                    }
                }
                try { Thread.sleep(30*1000); }catch(Exception e1){}
            }
        } catch (Exception e) {
            System.out.println(" failed to process ");
        }

    }
}
