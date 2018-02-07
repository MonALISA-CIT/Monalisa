package lia.app.apache;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lia.app.AppUtils;

public class AppApache implements lia.app.AppInt {

    String     propFile = null;
    Properties prop = new Properties();
    
    public static final String[] vsConfigFiles = {"httpd.conf"};
    public static final String sConfigOptions =
	"########### Required parameters :\n"+
	"#apachectl=/path/to/apachectl\n"+
	"#httpd.conf=/path/to/httpd.conf\n"+
	"#################################\n\n";

    public String getName(){
	return "lia.app.apache.AppApache";
    }
    
    public String getConfigFile(){
	return propFile;
    }

    public boolean init(String sPropFile){
	propFile = sPropFile;
    
	try{
	    AppUtils.getConfig(prop, propFile);
	    return true;
	}
	catch (Exception e){
	    System.err.println("error loading props : "+e+" ("+e.getMessage()+")");
	    return false;
	}
    }
    
    public boolean start(){
	String sCmd = prop.getProperty("apachectl");
	
	String s = AppUtils.getOutput(new String[]{sCmd, "start"});
	
	return (s.indexOf("apachectl start: httpd started")>=0);
    }
    
    public boolean stop(){
	String sCmd = prop.getProperty("apachectl");
	
	String s = AppUtils.getOutput(new String[]{sCmd, "stop"});
	
	return (s.indexOf("apachectl stop: httpd stopped")>=0);
    }
    
    public boolean restart(){
	stop();
	
	return start();
    }

    public String exec(String sCommand){
	String sCmd = prop.getProperty("apachectl");
	
	return AppUtils.getOutput(new String[]{sCmd, sCommand});
    }
    
    public boolean update(String vs[]){
	HashMap hmFiles = new HashMap();
    
	try{
	    for (int o=0; o<vs.length; o++){
		String sUpdate = vs[o];
	
	        StringTokenizer st = new StringTokenizer(sUpdate, " ");
		int i;
	    
    	        String sFile    = AppUtils.dec(st.nextToken());
		int    iLine    = Integer.parseInt(st.nextToken());
		String sPrev    = st.nextToken();
		String sCommand = st.nextToken();
	        String sParams  = null;
		
		sPrev = AppUtils.dec(sPrev);
		
		while (st.hasMoreTokens()){
		    sParams = (sParams==null?"":sParams+" ")+AppUtils.dec(st.nextToken());
		}
	    
		boolean bOk = false;
		for (i=0; i<vsConfigFiles.length; i++)
		    if (vsConfigFiles[i].equals(sFile)){
			bOk = true;
			break;
		    }
	
		if (!bOk)
		    return false;
	
		Vector vConfig = hmFiles.get(sFile)!=null ? (Vector)hmFiles.get(sFile) : getConfig(sFile);
	
		updateConfig(vConfig, 0, iLine, sPrev, sCommand, sParams);
		
		hmFiles.put(sFile, vConfig);
	    }
	}
	catch (Exception e){
	    return false;
	}
	
	Iterator it = hmFiles.keySet().iterator();
	
	while (it.hasNext()){
	    String sFile = (String) it.next();
	    
	    if (!writeConfig(sFile, (Vector)hmFiles.get(sFile)))
		return false;
	}
	
	return true;
    }
    
    public boolean update(String sUpdate){
	return update(new String[] {sUpdate});
    }
    
    private final boolean writeConfig(String sFile, Vector vConfig){
	String sFileName = prop.getProperty(sFile);
	
	if (sFileName==null) return false;
	
	try{
	    PrintWriter pw = new PrintWriter(new FileWriter(sFileName));
	    
	    for (int i=0; i<vConfig.size(); i++){
		pw.println(vConfig.elementAt(i));
	    }
	    
	    pw.flush();
	    pw.close();
	}
	catch (Exception e){
	    return false;
	}
    
	return true;
    }
    
    private final int updateConfig(Vector v, int iCurrentLine, int iLine, String sOrigName, String sCommand, String sParams){
	//System.err.println("update : "+iLine+" ("+iCurrentLine+") : "+sCommand+" : "+sParams);
	for (; iCurrentLine<v.size(); iCurrentLine++){
	    String s = (String)v.get(iCurrentLine);
	    String sLine = s;
	    
	    s = s.replace('\t', ' ');
	    s = s.trim();
	    
	    if (s.startsWith("#")) continue;
	    if (s.length()<=0) continue;
	    
	    String sCurrentName = s;
	    if (sCurrentName.startsWith("<") && sCurrentName.endsWith(">")){
		sCurrentName = sCurrentName.substring(1, sCurrentName.length()-1).trim();
	    }
	    if (sCurrentName.indexOf(" ")>0)
		sCurrentName = sCurrentName.substring(0, sCurrentName.indexOf(" ")).trim();

	    if (sCurrentName.indexOf("\t")>0)
		sCurrentName = sCurrentName.substring(0, sCurrentName.indexOf("\t")).trim();

	    if (iCurrentLine==iLine){
		if (!sOrigName.equals(sCurrentName)){
		    throw new java.util.NoSuchElementException("");
		}
		
		if (sCommand.equals("insert") && sParams!=null && sParams.length()>0){
		    v.insertElementAt(sParams, iCurrentLine+1);
		    continue;
		}
		
		if (sCommand.equals("insertsection") && sParams!=null && sParams.length()>0){
		    sParams = sParams.trim();
		    
		    int i = sParams.indexOf(" ");
		    int j = sParams.indexOf("\t");
		    
		    int min = -1;
		    if (i>0) min=i;
		    if (j>0 && j<min) min=j;
		    
		    if (min<0){
			v.insertElementAt("</"+sParams+">", iCurrentLine+1);
			v.insertElementAt("<"+sParams+">", iCurrentLine+1);
		    }
		    else{
			String sName = sParams.substring(0, min);
			
			v.insertElementAt("</"+sName+">", iCurrentLine+1);
			v.insertElementAt("<"+sParams+">", iCurrentLine+1);
		    }
		    
		    continue;
		}
		
		if (sCommand.equals("update")){
		    int i = 0;
		    
		    while (i<sLine.length() && (sLine.charAt(i)==' ' || sLine.charAt(i)=='\t' || sLine.charAt(i)=='<')) i++;
		    int iPrefix = i;
		    while (i<sLine.length() && sLine.charAt(i)!=' ' && sLine.charAt(i)!='\t' && sLine.charAt(i)!='>') i++;
		    
		    sLine = sLine.substring(iPrefix, i);
		    
		    if (sParams!=null && sParams.length()>0)
			sLine += " "+sParams;
		    
		    if (s.startsWith("<") && s.endsWith(">"))
			sLine = "<"+sLine+">";
			
		    v.setElementAt(sLine, iCurrentLine);
		    continue;
		}
		if (sCommand.equals("rename") && sParams!=null && sParams.length()>0){
		    int i = 0;
		    
		    if (s.startsWith("<"))
			continue;	// cannot rename sections
		    
		    while (i<sLine.length() && (sLine.charAt(i)==' ' || sLine.charAt(i)=='\t' || sLine.charAt(i)=='<')) i++;
		    int iPrefix = i;
		    while (i<sLine.length() && sLine.charAt(i)!=' ' && sLine.charAt(i)!='\t' && sLine.charAt(i)!='>') i++;
		    
		    sLine = sLine.substring(0, iPrefix) + sParams + sLine.substring(i);
		    v.setElementAt(sLine, iCurrentLine);
		    continue;
		}
		if (sCommand.equals("delete")){
		    if (!s.startsWith("<")){
		        v.removeElementAt(iCurrentLine);
			iLine--;
		        continue;
		    }
		    else{
			int newLine = updateConfig(v, iCurrentLine+1, iLine, sOrigName, sCommand, sParams);
			
			for (; newLine>=iCurrentLine; newLine--){
			    v.removeElementAt(newLine);
			}
			
			iLine--;
			continue;
		    }
		}
	    }
	    
	    if (s.startsWith("</")){
		//System.err.println("returning : "+iCurrentLine);
		return iCurrentLine;
	    }
	    
	    if (s.startsWith("<")){
		iCurrentLine = updateConfig(v, iCurrentLine+1, iLine, sOrigName, sCommand, sParams);
	    }
	}
	
	return iCurrentLine;
    }

    public int status(){
	Vector v = getConfig("httpd.conf");
    
	if (v==null) return AppUtils.APP_STATUS_UNKNOWN;
	
	Pattern p = Pattern.compile("^Port\\s+([0-9]+)$");
	
	for (int i=0; i<v.size(); i++){
	    String s = (String) v.get(i);
	    
	    Matcher m = p.matcher(s);
	    
	    if (m.matches()){
		s = m.group(1);
		
		try{
		    Socket sock = new Socket("localhost", Integer.parseInt(s));
		    sock.setSoTimeout(10000);
		    PrintWriter pw = new PrintWriter(new OutputStreamWriter(sock.getOutputStream()));
		    BufferedReader br = new BufferedReader(new InputStreamReader(sock.getInputStream()));
		    
		    pw.println("GET / HTTP/1.0");
		    pw.println();
		    pw.flush();
		    
		    s = br.readLine();
		    
		    if (s.startsWith("HTTP/")) return AppUtils.APP_STATUS_RUNNING;
		    else return AppUtils.APP_STATUS_UNKNOWN;
		}
		catch (Exception e){
		    return AppUtils.APP_STATUS_STOPPED;
		}
	    }
	}
    
	return AppUtils.APP_STATUS_UNKNOWN;
    }
    
    public String info(){
	Vector v = getConfig("httpd.conf");
	
	StringBuilder sb = new StringBuilder();
	sb.append("<config app=\"Apache\">\n");
	sb.append("<file name=\"httpd.conf\">\n");
	
	if (v!=null){
	    configToXML(v, sb, 0);
	}
	
	sb.append("</file>\n");
	sb.append("</config>\n");
	return sb.toString();
    }
    
    private final int configToXML(Vector v, StringBuilder sb, int line){
	for (;line<v.size();line++){
	    String s = (String)v.get(line);
	    
	    s = s.replace('\t', ' ');
	    s = s.trim();
	    
	    if (s.startsWith("#")) continue;
	    if (s.length()<=0) continue;
	    
	    if (s.startsWith("<") && s.endsWith(">")){
		s = s.substring(1, s.length()-1);
		
		if (s.startsWith("/"))
		    return line;
		
		String sKey = s;
		String sValue = "";
		int i = s.indexOf(" ");
		
		if (i>0){
		    sValue = sKey.substring(i).trim();
		    sKey   = sKey.substring(0, i).trim();
		}
		
		sb.append("<section name=\""+AppUtils.enc(sKey)+"\" value=\""+AppUtils.enc(sValue)+"\" line=\""+line+"\" read=\"true\" write=\"true\">\n");
		
		line = configToXML(v, sb, line+1);
		
		sb.append("</section>\n");
	    }
	    else{
		String sKey = s;
		String sValue = "";
		int i = s.indexOf(" ");
		
		if (i>0){
		    sValue = sKey.substring(i).trim();
		    sKey   = sKey.substring(0, i).trim();
		}
		
		sb.append("<key name=\""+AppUtils.enc(sKey)+"\" value=\""+AppUtils.enc(sValue)+"\" line=\""+line+"\" read=\"true\" write=\"true\"/>\n");
	    }
	}
	
	return line;
    }
        
    private final Vector getConfig(String sConfigFile){
	try{
	    FileInputStream fis = new FileInputStream(prop.getProperty("httpd.conf"));
	    
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    byte[] buff = new byte[1024];
	    int count = 0;
	
	    while ( (count=fis.read(buff))>0 ){
		baos.write(buff, 0, count);
	    }
	    
	    return AppUtils.getLines(baos.toString());
	}
	catch (Exception e){
	    System.out.println("AppApache : cannot read httpd.conf from : "+prop.getProperty("httpd.conf"));
	    return null;
	}
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
