import java.io.FileReader ;
import java.io.BufferedReader ;
import java.util.Vector;
import java.rmi.RMISecurityManager ;

import net.jini.core.discovery.LookupLocator;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lookup.ServiceDiscoveryManager;
import net.jini.discovery.DiscoveryGroupManagement;
import net.jini.lease.LeaseRenewalManager;
import net.jini.core.entry.Entry;
import net.jini.lookup.entry.Name;

import lia.Monitor.monitor.DataStore;
import lia.Monitor.monitor.MonaLisaEntry;
import lia.Monitor.monitor.SiteInfoEntry;
import lia.Monitor.monitor.ExtendedSiteInfoEntry;

/**
* This program locates the Monalisa services in the lookup locators given in the
* locators.conf file (one per line) and shows the attributes of the services.
*/

public class FindMLServices {

    private String lookupLocators[];
    public static final int MAX_SERVICES_TO_DISCOVER = 1000;
    
    public FindMLServices () {}

    /** 
    * Get locators from the "locators.conf" file
    */	
    private void setLocators () throws Exception {
	String line;
	Vector readLines = new Vector();
	BufferedReader br = new BufferedReader (new FileReader ("locators.conf"));
	
	while ( (line=br.readLine())!=null) {
	    if (!line.equals(""))
		readLines.add (line);
	} //while
	
	lookupLocators = new String [readLines.size()];
	
	for (int i=0;i<readLines.size();i++) {
	    lookupLocators[i] = (String) readLines.elementAt(i);
	} //for
	
    } //setLocators


    /**
    *	Find MLServices and show its attributes
    */	
    private void getMLService () throws Exception {
    
	LookupDiscoveryManager ldm = null;
	ServiceDiscoveryManager sdm = null;
	setLocators ();
    
	/* find the Lookup Locators; locators given are discovered in unicast mode */
	LookupLocator locators[] = new LookupLocator [lookupLocators.length];
	for (int i=0;i<lookupLocators.length;i++) {
		locators[i] = new LookupLocator("jini://"+lookupLocators[i]);		
	} //for
	
	/* get the security manager */
	if (System.getSecurityManager()==null) {
	    System.setSecurityManager (new RMISecurityManager());
	} //if
	
	/* set the Lookup Discovery Manager */
         ldm = new LookupDiscoveryManager (DiscoveryGroupManagement.ALL_GROUPS,
								 locators,
								 null);
	/* wait for LookupDiscoveryManager threads to initialize */
        Thread.sleep (5000);

	/* get the Lookup Locators found and show their name and port */
	ServiceRegistrar[] reg = ldm.getRegistrars();
	System.out.println ("FindMLServices ===> found "+reg.length+" LUSs");
	for (int i=0;i<reg.length;i++) {
	    LookupLocator ll = reg[i].getLocator();
	    System.out.println (" =======> "+ll.getHost()+":"+ll.getPort());    
	}
	
	/* find Monalisa services in the specified locators */
	sdm = new ServiceDiscoveryManager (ldm, new LeaseRenewalManager());								 
	
	/* wait for threads to be initialized */	
	Thread.sleep (1000);
	
	/* the template for getting the Monalisa services */
	ServiceTemplate template = new ServiceTemplate (null,
							new Class[] {lia.Monitor.monitor.DataStore.class},
							null);

	ServiceItem[] si = sdm.lookup (template, MAX_SERVICES_TO_DISCOVER, null);

	Thread.sleep(1000);

	/* show service attributes */
        System.out.println ("\n -------> Service attributes: <-------");

	for (int i=0;i<si.length;i++) {
	    Entry[] atribute = si[i].attributeSets;

	    System.out.println ("\n");
	    for (int j=0;j<atribute.length;j++) {
		if (atribute[j] instanceof Name) {
		    System.out.println (" Service Name ===> "+((Name)atribute[j]).name);
		}
		if (atribute[j] instanceof lia.Monitor.monitor.MonaLisaEntry) {
		    System.out.println ("\tGroup ===> "+((MonaLisaEntry)atribute[j]).Group);
		    System.out.println ("\tLocation ===> "+((MonaLisaEntry)atribute[j]).Location);
		    System.out.println ("\tCountry ===> "+((MonaLisaEntry)atribute[j]).Country);
		    System.out.println ("\tLAT ===> "+((MonaLisaEntry)atribute[j]).LAT);
		    System.out.println ("\tLONG ===> "+((MonaLisaEntry)atribute[j]).LONG);
		    
		} 
		if (atribute[j] instanceof lia.Monitor.monitor.SiteInfoEntry) {
		    System.out.println ("\tMLPort ===> "+((SiteInfoEntry)atribute[j]).ML_PORT.intValue());
		    if (((SiteInfoEntry)atribute[j]).REGISTRY_PORT!=null)
			System.out.println ("\tRegistryPort ===> "+((SiteInfoEntry)atribute[j]).REGISTRY_PORT.intValue());
		    if (((SiteInfoEntry)atribute[j]).WS_PORT!=null)
			    System.out.println ("\tMLWebServicePort ===> "+((SiteInfoEntry)atribute[j]).WS_PORT.intValue());
		    if (((SiteInfoEntry)atribute[j]).WEB_CODEBASE_PORT!=null)			    
			System.out.println ("\tWeb_Codebase_Port ===> "+((SiteInfoEntry)atribute[j]).WEB_CODEBASE_PORT.intValue());
		    System.out.println ("\tIPAddress ===> "+((SiteInfoEntry)atribute[j]).IPAddress);
		    System.out.println ("\tShortName ===> "+((SiteInfoEntry)atribute[j]).UnitName);
		    
		}
		if (atribute[j] instanceof lia.Monitor.monitor.ExtendedSiteInfoEntry) {
		    System.out.println ("\tLocalContactName ===> "+((ExtendedSiteInfoEntry)atribute[j]).localContactName);
		    System.out.println ("\tLocalContactEMail ===> "+((ExtendedSiteInfoEntry)atribute[j]).localContactEMail);		    		    
		    System.out.println ("\tJVM Version ===> "+((ExtendedSiteInfoEntry)atribute[j]).JVM_VERSION);		    
		    System.out.println ("\tLibc Version ===> "+((ExtendedSiteInfoEntry)atribute[j]).LIBC_VERSION);		    		    		    
		}
	    }//for
	} //for
	
    } //getMLService


    public static void main (String args[]) {

    	    FindMLServices fMLS = new FindMLServices();
	
	    try {
		fMLS.getMLService();
	    } catch (Exception e){
		e.printStackTrace();
	    }
	
    }


} //findMLServices

