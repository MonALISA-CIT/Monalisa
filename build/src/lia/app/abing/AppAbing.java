package lia.app.abing;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import lia.Monitor.monitor.AppConfig;
import lia.app.AppUtils;

public class AppAbing implements lia.app.AppInt {

    String sFile = null;
    Properties prop = new Properties();
    
    public static final String sConfigOptions =
	"########### Required parameters : ############\n"+
	"#parameters=parameters to be passed to abw_rfl\n"+
	"##############################################\n\n";
	
    String sParameters = "";
    
    String MonaLisa_home = AppConfig.getProperty("MonaLisa_HOME", "../..");
	    
    public boolean start(){
	String vsCommand[];
    
	vsCommand = new String[]{
	    "/bin/bash", 
	    "-c", 
	    MonaLisa_home+"/Control/bin/abw_rfl "+(sParameters!=null ? sParameters : "") + " &>/dev/null </dev/null &"
	};
    
	AppUtils.getOutput(vsCommand);
    
	return true;
    }
    
    public boolean stop(){
	AppUtils.getOutput(new String[]{"/bin/killall", "abw_rfl"});
    
	return true;
    }
    
    public boolean restart(){
	stop();
	
	return start();
    }
    
    public int     status(){
	//return AppUtils.APP_STATUS_RUNNING;
	String sRunning = AppUtils.getOutput(new String[]{"/bin/sh", "-c", "pstree -u `id -u -n` | grep abw_rfl"});
	
	if (sRunning==null || sRunning.indexOf("abw_rfl")<0)
	    return AppUtils.APP_STATUS_STOPPED ;
	else
	    return AppUtils.APP_STATUS_RUNNING ;
    }
    
    public String  info(){
	// xml with the version & stuff
	StringBuilder sb = new StringBuilder();
	sb.append("<config app=\"Abing\">\n");
	sb.append("<file name=\"Abing\">\n");

	sb.append("<key name=\"parameters\" value=\""+AppUtils.enc(sParameters)+"\" line=\"1\" read=\"true\" write=\"true\"/>");

	sb.append("</file>\n");
	sb.append("</config>\n");
	
	return sb.toString();
    }
    
    public String  exec(String sCmd){
	return "exec has no meaning for the Abing module";
    }
    
    public boolean update(String sUpdate){
	return update(new String[]{sUpdate});
    }
    
    public boolean update(String vs[]){
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
		
		if (!sFile.equals("Abing")){
		    return false;
		}
		
		if (
		    sCommand.equals("rename") ||
		    sCommand.equals("insert") ||
		    sCommand.equals("delete") ||
		    sCommand.equals("insertsection")
		){
		    System.out.println("abing: command "+sCommand+" is ingnored");
		    return true;	// this commands have no sense for /proc file system module
		}
		
		if (!sCommand.equals("update")){
		    System.out.println("abing : command is not 'update'");
		    return false;	// the only valid command
		}
		
		while (st.hasMoreTokens()){
		    sParams = (sParams==null?"":sParams+" ")+AppUtils.dec(st.nextToken());
		}
		
		updateConfiguration("parameters="+sParams);
	    }
	    return true;
	}
	catch (Exception e){
	    System.out.println("abing : exception : "+e+" ("+e.getMessage()+")");
	    e.printStackTrace();
	    return false;
	}
    }
    
    public String  getConfiguration(){
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
	return AppUtils.updateConfig(sFile, s) && init(sFile);
    }
    
    public boolean bFirstRun = true;

    public boolean init(String sPropFile){
	while (MonaLisa_home.endsWith("/"))
	    MonaLisa_home = MonaLisa_home.substring(0, MonaLisa_home.length()-1);
    
	sFile = sPropFile;
	AppUtils.getConfig(prop, sFile);
	
	if (prop.getProperty("parameters")!=null && prop.getProperty("parameters").length()>0){
	    sParameters = prop.getProperty("parameters");
	}
	else{
	    sParameters = "";
	}
	
	if (bFirstRun){
	    extractFiles();
	    bFirstRun = false;
	}
	
	return true;
    }
    
    public String  getName(){
	return "lia.app.abing.AppAbing";
    }
    
    public String  getConfigFile(){
	return sFile;
    }

    private void extractFiles(){
	try{
	    try{
		(new File(MonaLisa_home+"/Control/bin")).mkdirs();
	    }
	    catch (Exception e){
	    }
	
	    // hope this never changes :)
	    JarFile jf = new JarFile(MonaLisa_home+"/Control/lib/abing.jar");
            Enumeration e = jf.entries();
            byte[] buff = new byte[1024];
            
            while ( e.hasMoreElements() ) {
                JarEntry je = (JarEntry) e.nextElement();
                String s = je.toString();
                
                if ( s.endsWith("abw_rfl") ) {
		    try{
			(new File(MonaLisa_home+"/Control/bin/abw_rfl")).delete();
		    }
		    catch (Exception ee){
		    }
		
            	    BufferedInputStream bis = new BufferedInputStream(jf.getInputStream(je));
                    FileOutputStream fos = new FileOutputStream(MonaLisa_home+"/Control/bin/abw_rfl");
                    
                    try {
                        for ( ; ; ) {
                    	    int bNO = bis.read(buff);
                            if ( bNO == -1 ) 
				break;
                    	    fos.write(buff, 0, bNO);
                        }
                    }
		    catch ( Throwable t ){
                    } 
                    
                    bis.close();
                    fos.close();
		    
		    AppUtils.getOutput(
			new String[]{
			    "/usr/bin/chmod", 
			    "a+x", 
			    MonaLisa_home+"/Control/bin/abw_rfl"
			}
		    );
		    
		    break;
                }
            }
	}
	catch (Exception e){
	    System.err.println("abing: cannot extract: "+e+" ("+e.getMessage()+")");
	    e.printStackTrace();
	}
    }

}
