package lia.web.servlets.lg;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;




public class Statistics_daemon_int1 extends HttpServlet {

   
    String sResDir = "";
    String sConfDir = "";
    
    
    
    //------------------------------------------------------- SERVLET -------------------------------------
	public static void copy(String source, String dest) throws IOException {
		FileChannel in = null, out = null;
		try {          
			in = new FileInputStream(source).getChannel();
			out = new FileOutputStream(dest).getChannel();
		
			long size = in.size();
			MappedByteBuffer buf = in.map(FileChannel.MapMode.READ_ONLY, 0, size);
		
			out.write(buf);
		
		} finally {
			if (in != null)          in.close();
			if (out != null)     out.close();
		}
	}
    
    public static Object objSync = new Object();
    
    private void copyUrl2File( String sUrl, String sDir, String sFileName)
    {
    	synchronized( objSync) {
	try{
		URL url = new URL(sUrl);
		BufferedReader br = new BufferedReader( new InputStreamReader(url.openStream()));
		BufferedWriter bw = new BufferedWriter(new FileWriter(sDir+sFileName, false));
		String sLine;
		int nFoundImage=-1;
		while( (sLine=br.readLine())!=null ) {
			nFoundImage = sLine.indexOf("display?image=");
			if ( nFoundImage!=-1 ) {
				String sImageName;
				int nLastChar = sLine.indexOf( "\" ", nFoundImage);
				if ( nLastChar!=-1 ) {
					sImageName = sLine.substring( nFoundImage+14, nLastChar);
					System.out.println("[copyUrl2File 1] for "+sFileName+" image name found is: "+sImageName);
					copy( System.getProperty("java.io.tmpdir") + "/" + sImageName, sDir + sImageName);
					System.out.println("[copyUrl2File 2] initial line; "+sLine);
					sLine = sLine.substring( 0, nFoundImage) + sLine.substring(nFoundImage+14);
					System.out.println("[copyUrl2File 3] altered line: "+sLine);
				}
			}
			bw.write(sLine);
			bw.newLine();
		}
		br.close();
		bw.flush();
		bw.close();
	} catch (Exception ex) {
		ex.printStackTrace();
	}
	};
    }
    

    public void doGet(final HttpServletRequest req,HttpServletResponse resp) throws IOException {
	
    	
	//PrintWriter pw = new PrintWriter(new OutputStreamWriter(resp.getOutputStream()));
	ServletContext sc = getServletContext();

    	sResDir = sc.getRealPath("/");
    	if (!sResDir.endsWith("/"))
    		sResDir += "/";
	sResDir += "LookingGlass/files/";

	
	int interval=1;
		
	try {
                interval=Integer.parseInt(req.getParameter("interval"));
        } catch (Exception ex) { interval=1; };

	
	if(interval==1){
	
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_services1&displaysum=true&sum=1&res_path=small_res&interval.min=86400000&dont_cache=true", sResDir, "view-1d-services.html");
	    //copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_nodes&displaysum=true&sum=1&res_path=small_res&interval.min=86400000", sResDir, "view-1d-nodes.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_clients1&displaysum=true&sum=1&res_path=small_res&interval.min=86400000&dont_cache=true", sResDir, "view-1d-clients.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_param_vrvs1&interval.min=86400000&res_path=new_res&dont_cache=true", sResDir, "view-1d-params.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=group_param&res_path=new_res&dont_cache=true", sResDir, "view005.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_pages11&displaysum=true&sum=1&res_path=new_res&interval.min=86400000&skipfactor=1&skipnull=1&gap_if_no_data.area=false&dont_cache=true", sResDir, "view-1d-pages.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=msg&displaysum=true&sum=1&res_path=new_res&interval.min=86400000&dont_cache=true", sResDir, "view-1d-messages.html");
	}
	
	if(interval==2){
	    
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_services1&displaysum=true&sum=1&res_path=small_res&interval.min=604800000&dont_cache=true", sResDir, "view-1w-services.html");
	    //copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_nodes&displaysum=true&sum=1&res_path=small_res&interval.min=604800000", sResDir, "view-1w-nodes.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_clients1&displaysum=true&sum=1&res_path=small_res&interval.min=604800000&dont_cache=true", sResDir, "view-1w-clients.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_param_vrvs1&interval.min=604800000&res_path=new_res&dont_cache=true", sResDir, "view-1w-params.html");
	    //copyUrl2File("http://monalisa2.cern.ch:8888/display?page=group_param&res_path=new_res", sResDir, "view005.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_pages11&displaysum=true&sum=1&res_path=new_res&interval.min=604800000&skipfactor=1&skipnull=1&gap_if_no_data.area=false&dont_cache=true", sResDir, "view-1w-pages.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=msg&displaysum=true&sum=1&res_path=new_res&interval.min=604800000&dont_cache=true", sResDir, "view-1w-messages.html");
	}
	
	if(interval==3){
	   
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_services1&displaysum=true&sum=1&res_path=small_res&interval.min=2419200000&dont_cache=true", sResDir, "view-1m-services.html");
	    //copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_nodes&displaysum=true&sum=1&res_path=small_res&interval.min=2419200000", sResDir, "view-1m-nodes.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_clients1&displaysum=true&sum=1&res_path=small_res&interval.min=2419200000&dont_cache=true", sResDir, "view-1m-clients.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_param_vrvs1&interval.min=2419200000&res_path=new_res&dont_cache=true", sResDir, "view-1m-params.html");
	    //copyUrl2File("http://monalisa2.cern.ch:8888/display?page=group_param&res_path=new_res", sResDir, "view005.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_pages11&displaysum=true&sum=1&res_path=new_res&interval.min=2419200000&skipfactor=1&skipnull=1&gap_if_no_data.area=false&dont_cache=true", sResDir, "view-1m-pages.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=msg&displaysum=true&sum=1&res_path=new_res&interval.min=2419200000&dont_cache=true", sResDir, "view-1m-messages.html");
	}
	
	
	if(interval==4){
	    
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_services1&displaysum=true&sum=1&res_path=small_res&interval.min=7257600000&dont_cache=true", sResDir, "view-3m-services.html");
	    //copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_nodes&displaysum=true&sum=1&res_path=small_res&interval.min=7257600000", sResDir, "view-3m-nodes.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_clients1&displaysum=true&sum=1&res_path=small_res&interval.min=7257600000&dont_cache=true", sResDir, "view-3m-clients.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_param_vrvs1&interval.min=7257600000&res_path=new_res&dont_cache=true", sResDir, "view-3m-params.html");
	    //copyUrl2File("http://monalisa2.cern.ch:8888/display?page=group_param&res_path=new_res", sResDir, "view005.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_pages11&displaysum=true&sum=1&res_path=new_res&interval.min=7257600000&skipfactor=1&skipnull=1&gap_if_no_data.area=false&dont_cache=true", sResDir, "view-3m-pages.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=msg&displaysum=true&sum=1&res_path=new_res&interval.min=7257600000&dont_cache=true", sResDir, "view-3m-messages.html");
	}    
	  
	if(interval==5){
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_services1&displaysum=true&sum=1&res_path=small_res&interval.min=12096000000&dont_cache=true", sResDir, "view-5m-services.html");
	    //copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_nodes&displaysum=true&sum=1&res_path=small_res&interval.min=7257600000", sResDir, "view-3m-nodes.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_clients1&displaysum=true&sum=1&res_path=small_res&interval.min=12096000000&dont_cache=true", sResDir, "view-5m-clients.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_param_vrvs1&interval.min=12096000000&res_path=new_res&dont_cache=true", sResDir, "view-5m-params.html");
	    //copyUrl2File("http://monalisa2.cern.ch:8888/display?page=group_param&res_path=new_res", sResDir, "view005.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=total_pages11&displaysum=true&sum=1&res_path=new_res&interval.min=12096000000&skipfactor=1&skipnull=1&gap_if_no_data.area=false&dont_cache=true", sResDir, "view-5m-pages.html");
	    copyUrl2File("http://monalisa2.cern.ch:8888/display?page=msg&displaysum=true&sum=1&res_path=new_res&interval.min=12096000000&dont_cache=true", sResDir, "view-5m-messages.html");	
	    
	    
	
	}
	
	
    }	

    	
}
