/*
    This servlet should reside in REPOSITORY/tomcat/webapps/ROOT/WEB-INF/classes
    
    To activate this servlet add the following lines to REPOSITORY/tomcat/webapps/ROOT/WEB-INF/web.xml
    
	  <servlet>
	      <servlet-name>SampleServlet</servlet-name>
	      <display-name>SampleServlet</display-name>
	      <servlet-class>SampleServlet</servlet-class>
	      <load-on-startup>1</load-on-startup>
    	  </servlet>
	  
	  <servlet-mapping>
	      <servlet-name>SampleServlet</servlet-name>
	      <url-pattern>/SampleServlet</url-pattern>
	  </servlet-mapping>
	  
    After each change / recompile of this class you have to restart the repository.
*/

import lia.Monitor.monitor.monPredicate;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.ExtendedResult;

import lia.Monitor.Store.TransparentStoreFast;
import lia.Monitor.Store.TransparentStoreFactory;

import java.util.Vector;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;

import lia.Monitor.Store.Cache;

import java.util.Date;

public class SampleServlet extends HttpServlet {

    // Through this object you will access the history data
    private TransparentStoreFast store;

    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
	resp.setContentType("text/html");
    
	PrintWriter pw = new PrintWriter(new OutputStreamWriter(resp.getOutputStream()));
    
	try{
	    // get an instance of the store
	    store = (TransparentStoreFast) TransparentStoreFactory.getStore();
	}
	catch (Exception e){
	    return;
	}
    
	// You need to use a "predicate" to query the database
	// Every string can be:
	// - a fully qualified name ("GLORIAD")
	// - a partial name ("GLORI%", "%AD")
	// - wildcard ("*") meaning any string
	
        monPredicate p = new monPredicate(
	    "GLORIAD",			// Farm
	    "WAN",			// Cluster
	    "*",			// Node (any)
	    -30*60*1000, -1, 		// start time, end time, in millis, if negative then they are relative to the current time
	    new String[]{"Russia_IN"}, 	// list of functions, leave this parameter null for every function
	    null
	);
	
	pw.println("<html><head><title>Sample servlet</title></head><body bgcolor=white>");

	pw.println("Real-time data:<br><br>");
	// Get the last received value from the Cache
	Object o = Cache.getLastValue(p);
	
	Result r = (o!=null && (o instanceof Result)) ? (Result) o : null;
	
	if (r!=null){
	    pw.println(
		(new Date(r.time)).toString()+ " : "+
	        r.FarmName+" / "+
	        r.ClusterName+" / "+
    	        r.NodeName+" / "+
		r.param_name[0]+"="+r.param[0]+
		"<br>"
	    );
	}
	
	pw.println("<br><br>History info:<br><br>");
	// Retrieve the history information from the database
	Vector vData = store.getResults(new monPredicate[]{p});
	for (int i=0; i<vData.size(); i++){
	    ExtendedResult er = (ExtendedResult) vData.get(i);
	
	    pw.println(
		(new Date(er.time)).toString()+ " : "+
		er.FarmName+" / "+
		er.ClusterName+" / "+
		er.NodeName+" / "+
		er.param_name[0]+"="+er.param[0]+" (min:"+er.min+", max:"+er.max+")"+
		"<br>"
	    );
	}
	
	pw.println("</body></html>");
	
	pw.flush();
    }
    
}
