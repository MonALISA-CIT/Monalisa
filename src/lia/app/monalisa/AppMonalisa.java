package lia.app.monalisa;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

import lia.app.AppUtils;

public class AppMonalisa implements lia.app.AppInt {

    String     propFile = null;
    Properties prop = new Properties();
    
    public static final String sDefaultMonalisaHome = "../..";
    
    public static final String sConfigOptions =
	"########### Required parameters : ######\n"+
	"#ml_home=/path/to/monalisa (default "+sDefaultMonalisaHome+")\n"+
	"########################################\n\n";

    public String getName(){
	return "lia.app.monalisa.AppMonalisa";
    }
    
    public String getConfigFile(){
	return propFile;
    }
    
    
    private String 
	sDir      = null,
	sConfDir  = null,
	sConfFile = null;

    public boolean init(String sPropFile){
	propFile = sPropFile;
    
	try{
	    AppUtils.getConfig(prop, propFile);
	}
	catch (Exception e){
	    System.err.println("error loading props : "+e+" ("+e.getMessage()+")");
	    return false;
	}
	
	sDir = prop.getProperty("ml_home", sDefaultMonalisaHome);
	if (!sDir.endsWith("/")) sDir += "/";
	sDir += "Service/CMD";
	
	sConfDir  = AppUtils.getOutput(new String[] {"bash", "-c", "cd "+sDir+" &>/dev/null; . ./ml_env &>/dev/null; echo -n $FARM_HOME"});
	sConfFile = AppUtils.getOutput(new String[] {"bash", "-c", "cd "+sDir+" &>/dev/null; . ./ml_env &>/dev/null; echo -n $FARM_CONF_FILE"});
	
	return true;
    }
    
    public boolean start(){
	String s = AppUtils.getOutput(new String[]{sDir+"/ML_SER", "start"});
	
	return true;
    }
    
    public boolean stop(){
	String s = AppUtils.getOutput(new String[]{sDir+"/ML_SER", "stop"});
	
	return true;
    }
    
    public boolean restart(){
	String s = AppUtils.getOutput(new String[]{sDir+"/ML_SER", "restart"});
	
	return true;
    }

    public String exec(String sCommand){
	return AppUtils.getOutput(new String[]{sDir+"/ML_SER", sCommand});
    }
    
    public boolean update(String vs[]){
	if (vs==null || vs.length<=0) return true;
	
	for (int i=0; i<vs.length; i++){
	    String s = vs[i];
	    
	    StringTokenizer st = new StringTokenizer(s, " ");
	    
	    try{
		String sFile = AppUtils.dec(st.nextToken());
		int iLine    = Integer.parseInt(st.nextToken());
		String sName = AppUtils.dec(st.nextToken());
		String sCMD  = AppUtils.dec(st.nextToken());
		
		String sValue = null;

		while (st.hasMoreTokens()){
		    sValue = (sValue==null?"":sValue+" ")+AppUtils.dec(st.nextToken());
		}
		
		// make sure we have correct parameters
		if (!sCMD.equals("update") && !sCMD.equals("rename") && !sCMD.equals("insert") && !sCMD.equals("insertsection") && !sCMD.equals("delete")) {
		    System.err.println("unknown command : "+sCMD);
		    return false;
		}
		
		if (!sFile.equals("ml_env") && !sFile.equals("ml.properties") && !sConfFile.endsWith("/"+sFile)){
		    System.err.println("unknown file name : "+sCMD);
		    return false;
		}
		
		if (sFile.equals("ml_env")){
		    return updatePropFile(sDir+"/ml_env", iLine, sName, sCMD, sValue);
		}
		
		if (sFile.equals("ml.properties")){
		    return updatePropFile(sConfDir+"/ml.properties", iLine, sName, sCMD, sValue);
		}
		
		return updateConfFile(iLine, sName, sCMD, sValue);
	    }
	    catch (Exception e){
		return false;
	    }
	}
    
	return true;
    }
    
    private static final boolean updatePropFile(String sFile, int iLine, String sName, String sCMD, String sNewValue){
	Vector v = AppUtils.getLines(AppUtils.getFileContents(sFile));
	
	if (v==null) return false;
	
	iLine --;
	
	if (iLine<0 || iLine>=v.size()) return false;
	
	String s = (String) v.elementAt(iLine);
	s = s.trim();
		
	if (s.length()>0 && !s.startsWith("#") && s.indexOf("=")>0){
	    String sKey   = s.substring(0, s.indexOf("=")).replace('\t', ' ').trim();
	    String sValue = s.substring(s.indexOf("=")+1).trim();
	    
	    if (sKey.equals(sName)){
		// ok, what's to be done ?
		
		if (sCMD.equals("delete")){	
		    v.remove(iLine);
		}
		
		if (sCMD.equals("update")){
		    v.set(iLine, sKey+"="+sNewValue);
		}
		
		if (sCMD.equals("rename")){
		    v.set(iLine, sNewValue+"="+sValue);
		}
		
		if (sCMD.equals("insert")){
		    v.add(iLine+1, sNewValue+(sNewValue.indexOf("=")>0?"":"="));
		}
		
		if (sCMD.equals("insertsection")){
		    //this command has no effect
		}
		
		return AppUtils.saveFile(sFile, v);
	    }
	    else {
		return false;
	    }
	}
    
	return false;
    }
    
    private final boolean updateConfFile(int iLine, String sName, String sCMD, String sValue){
	Vector v = AppUtils.getLines(AppUtils.getFileContents(sConfFile));
	
	if (v==null) return false;
	
	iLine --;
	
	if (iLine<0 || iLine>=v.size()) return false;
	
	String s = (String) v.elementAt(iLine);
	s = s.trim();
		
	if (s.length()>0 && !s.startsWith("#")){
	    if (s.equals(sName)){
		// ok, what's to be done ?
		
		if (sCMD.equals("delete")){	
		    v.remove(iLine);
		}
		
		if (sCMD.equals("update")){
		    v.set(iLine, sValue);
		}
		
		if (sCMD.equals("rename")){
		    v.set(iLine, sValue);
		}
		
		if (sCMD.equals("insert")){
		    v.add(iLine+1, sValue);
		}
		
		if (sCMD.equals("insertsection")){
		    v.add(iLine+1, sValue);
		}
		
		return AppUtils.saveFile(sConfFile, v);
	    }
	    else {
		return false;
	    }
	}
    
	return false;
    }
    
    public boolean update(String sUpdate){
	return update(new String[] {sUpdate});
    }
    
    public int status(){
	return AppUtils.APP_STATUS_RUNNING;
    }
    
    public String info(){
	StringBuilder sb = new StringBuilder();
	sb.append("<config app=\"Monalisa\">\n");

	getMLEnv(sb, sDir+"/ml_env");
	getMLEnv(sb, sConfDir+"/ml.properties");
	
	parseConfFile(sb, sConfFile);
    	
	sb.append("</config>\n");
	return sb.toString();
    }
    
    private static final void parseConfFile(StringBuilder sb, String sFile){
	sb.append("<file name=\""+sFile.substring(sFile.lastIndexOf("/")+1)+"\">\n");
	
	try{
	    BufferedReader br = new BufferedReader(new FileReader(sFile));
	    
	    String s = null;
	    int line = 0;
	    
	    while ( (s=br.readLine())!=null ){
		s = s.trim();
		line ++;
		
		if (s.length()>0 && !s.startsWith("#")){
		    sb.append("<key name=\""+AppUtils.enc(s)+"\" value=\""+AppUtils.enc(s)+"\" line=\""+line+"\" read=\"true\" write=\"true\"/>\n");
		}
	    }
	}
	catch (Exception e){
	}
	
	sb.append("</file>\n");
    }
    
    private static final void getMLEnv(StringBuilder sb, String sFile){
	sb.append("<file name=\""+sFile.substring(sFile.lastIndexOf("/")+1)+"\">\n");
    
	try{
	    BufferedReader br = new BufferedReader(new FileReader(sFile));
	    
	    String s = null;
	    int line = 0;
	    
	    while ( (s=br.readLine())!=null ){
		s = s.trim();
		line ++;
		
		if (s.length()>0 && !s.startsWith("#") && s.indexOf("=")>0){
		    String sKey   = s.substring(0, s.indexOf("=")).replace('\t', ' ').trim();
		    String sValue = s.substring(s.indexOf("=")+1).trim();
		    
		    sb.append("<key name=\""+AppUtils.enc(sKey)+"\" value=\""+AppUtils.enc(sValue)+"\" line=\""+line+"\" read=\"true\" write=\"true\"/>\n");
		}
	    }
	}
	catch (Exception e){
	    System.err.println("error : "+e+" ("+e.getMessage()+")");
	    e.printStackTrace();
	}
	
	sb.append("</file>\n");
    }
    
    public String getConfiguration(){
	StringBuilder sb = new StringBuilder();
	
	sb.append(sConfigOptions);
	
	Enumeration e = prop.propertyNames();
	
	while (e.hasMoreElements()){
	    String s = (String) e.nextElement();
	    
	    sb.append(s+"="+prop.getProperty(s)+"\n");
	}
	
	return sb.toString();
    }
    
    public boolean updateConfiguration(String s){
	return AppUtils.updateConfig(propFile, s) && init(propFile);
    }
    
}
