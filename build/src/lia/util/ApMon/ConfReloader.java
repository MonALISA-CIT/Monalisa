package lia.util.ApMon;

import java.io.File;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Vector;


/**
 * Separate thread which periodically checks the configuration file/URLs 
 * for changes.
 */
public class ConfReloader extends Thread {

    private ApMon apm;

    private boolean hasToRun = true;

    ConfReloader (ApMon apm) {
	this.apm = apm;
    }

    public void run() {

	try {
	    Thread.sleep(apm.getCrtRecheckInterval() * 1000);
	} catch (Exception exc) {}

	while (hasToRun) {
	    Enumeration e = apm.confResources.keys();
	    boolean resourceChanged = false;
	    
	    try {
		while (e.hasMoreElements()) {
		    Object obj = e.nextElement();
		
		    Long lastModified = (Long)apm.confResources.get(obj);
		    if (obj instanceof File) {
			File f = (File)obj;
			if (apm.VERBOSE)
			    System.out.println("[" + new Date() + 
					       "] [Checking for modifications for " + 
					       f.getCanonicalPath() + "]");
			long lmt = f.lastModified();	
			if (lmt > lastModified.longValue()) {
			    if (apm.VERBOSE)
				System.out.println("[File " + f.getCanonicalPath() 
						   + " modified]");
			    resourceChanged = true;
			    break;
			    //confResources.put(f, Long.valueOf(lmt));
			}
		    }
    
		    if (obj instanceof URL) {
			URL u = (URL)obj;
			long lmt = 0;
		    
			if (apm.VERBOSE)
			    System.out.println("[Checking for modifications for " +
				   u + "]");

			URLConnection urlConn = u.openConnection();
			lmt = urlConn.getLastModified();
		    
			if (lmt > lastModified.longValue() || lmt == 0) {
			    if (apm.VERBOSE)
				System.out.println("[Location " + u + 
					       " modified]");
			    resourceChanged = true;
			    break;
			}
		    }
		} // while

		/* if any resource has changed we have to recheck all the 
		   others, otherwise some destinations might be ommitted */
		if (resourceChanged) {
		    if (apm.initType == ApMon.FILE_INIT) {
			apm.initialize((String)apm.initSource, false);
		    }
		    
		    if (apm.initType == ApMon.LIST_INIT) {
			apm.initialize((Vector)apm.initSource, false);
		    }
		}
		apm.setCrtRecheckInterval(apm.getRecheckInterval());
	    } catch (Exception exc) {
		apm.setCrtRecheckInterval(10 * apm.getRecheckInterval());
	    }
		
	    try {
		Thread.sleep(apm.getCrtRecheckInterval() * 1000);
	    } catch (Exception exc) {}

	}
    }

    void stopIt() {
	hasToRun = false;
    }
}
