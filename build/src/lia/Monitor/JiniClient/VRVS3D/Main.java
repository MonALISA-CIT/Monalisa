package lia.Monitor.JiniClient.VRVS3D;

import lia.Monitor.JiniClient.CommonGUI.MainBase;
import lia.Monitor.JiniClient.CommonGUI.SplashWindow;
import lia.Monitor.JiniClient.CommonGUI.Groups.GroupsPanel;
import lia.Monitor.JiniClient.CommonGUI.Jogl.JoglPanel;
import lia.Monitor.JiniClient.CommonGUI.Jogl.VrvsJoglPanel;
import lia.Monitor.JiniClient.CommonGUI.Topology.GNetTopoPan;
import lia.Monitor.JiniClient.VRVS3D.Gmap.GmapPan;
import lia.Monitor.JiniClient.VRVS3D.Histograms.Load;
import lia.Monitor.JiniClient.VRVS3D.Histograms.SClients;
import lia.Monitor.JiniClient.VRVS3D.Histograms.Traff;
import lia.Monitor.JiniClient.VRVS3D.Mmap.Mmap;
import lia.Monitor.JiniClient.VRVS3D.Tabl.TabPan;
import lia.Monitor.monitor.AppConfig;

/**
 * This launches the VRVS3D Client
 */
public class Main extends MainBase {

	private final String showPanels = AppConfig.getProperty("lia.Monitor.showPanels",
			"globe,wmap,load,wan,clients,jogl,table,groups");
	
	public Main(SplashWindow spw, String clientName){
		super(clientName);

		int progress = 0;
        String[] props = showPanels.split(",");
        int pLen = props.length;
        if(showPanels.indexOf("globe")>=0)
        	pLen += 1;
        pLen++;	 // for displaying some progress before starting service monitor
        pLen+=2; // for starting service monitor
        int pDelta = 100 / pLen; // progress delta
        
        progress = pDelta;
        spw.setStatus("Starting Service Monitor...", progress);
		setSerMonitor(new VrvsSerMonitor(this, Main.class));
        progress += pDelta * 2;

        /**
         * piece of code to respect the order as in lia.Monitor.showPanels property
         */
/*        boolean hasJava3D = false;
        GlobePan gp = null;
        for( int i=0; i<props.length; i++)
        	if ( props[i].equals("globe") ) {
        		// GlobePan
	            spw.setStatus("Loading Globe Panel...", progress);
	            System.out.print(" Probbing for Java3D ..... ");
				try {
					gp = new GlobePan();
					hasJava3D = true;
					System.out.println(" OK! " );
				} catch ( NoClassDefFoundError nd ) {
					System.out.println(" NOT OK! Is java3D installed propery on your computer?");
					nd.printStackTrace();
				} catch ( Throwable t ) {
					System.out.println(" NOT OK! Got General Exception:" );
					t.printStackTrace();
				}
				progress += 2 * pDelta;
				break;
        	};
*/	        for( int i=0; i<props.length; i++)
/*	        	if ( props[i].equals("globe") ) {
					try {
						if ( hasJava3D ) {
							addGraphical(gp,"Globe", "3D Globe", "globe");
							System.out.println(">Java3d .... OK");            
						} else {
							StringBuilder sb = new StringBuilder();
							sb.append("You do not have have Java 3D Runtime installed on your coputer!\n");
							sb.append("Please install it from http://java.sun.com/products/java-media/3D/ for Windows or Solaris\n");
							sb.append("or\n");
							sb.append("http://www.blackdown.org/java-linux/jdk1.2-status/java-3d-status.html for Linux\n\n");
							sb.append("The MonALISA client will start now without 3D panels !");
							JOptionPane.showMessageDialog(spw.container, sb.toString());
							System.out.println(" Please install Java 3D ! " );
						}
					} catch ( Throwable t ) {
						t.printStackTrace();
					}
	        	} else */if ( props[i].equals("wmap") ) {
		        	// World Map
		            spw.setStatus("Loading WorldMap Panel...", progress);
		            addGraphical(new Mmap(), "WMap", "World Map", "wmap");
		            progress += pDelta;
	        	} else if ( props[i].equals("gmap") ) {
		        	// GMap
			        spw.setStatus("Loading Graph Panel...", progress);
			        addGraphical(new GmapPan(), "GMap", "Graph Map", "gmap");
		            progress += pDelta;
		        } else if ( props[i].equals("table") ) {
		        	// Table
		            spw.setStatus("Loading Table Panel...", progress);
		            addGraphical((panelTabl = new TabPan()), "TabPan", "RC Table", "table");
		            progress += pDelta;
		        } else if ( props[i].equals("load") ) {
		        	// Load
		        	spw.setStatus("Loading Load Distribution Panel...", progress);
		        	addGraphical(new Load(), "Load", "Load Distribution", "load");
		        	progress += pDelta;
		        } else if ( props[i].equals("wan") ) {
		    		// WAN
		        	spw.setStatus("Loading IO Traffic Panel...", progress);
					addGraphical(new Traff(), "IO Traffic", "IO Traffic", "wan");
		        	progress += pDelta;
		        } else if ( props[i].equals("clients") ) {
		    		// VRVS Clients
					spw.setStatus("Loading Clients Panel...", progress);
					addGraphical(new SClients(), "Clients", "VRVS Clients", "vojobs");
		        	progress += pDelta;
		        } else if ( props[i].equals("topology") ) {
					spw.setStatus("Loading Network Topology Panel...", progress);
					addGraphical(new GNetTopoPan(), "Topology", "Network Topology", "topology");
		        	progress += pDelta;
		        	topologyShown = true;
		        } 
		        else if ( props[i].equals("jogl") ) {
		        	// Jogl panel
		            try {
			            spw.setStatus("Loading Jogl Panel...", progress);
			        	JoglPanel joglPanel = new VrvsJoglPanel();
			            joglPanel.init();
			            addGraphical( joglPanel, "3D Map", "Jogl 3D map", "3dmap");
		            }catch(Exception ex) {
		                System.out.println("JoGL... NOK");
		                ex.printStackTrace();
		            }
		            progress += pDelta;
		        }
		        else if (props[i].equals("groups")) {
		        	spw.setStatus("Loading Groups Panel...", progress);
		        	GroupsPanel panel = new GroupsPanel();
		        	panel.init();
		        	addGraphical(panel, "Groups", "Groups", "groups");
		        	progress += pDelta;
		        }
	        spw.setStatus("Starting...", progress);
		init();
	}
	
	public static void main(String[] args) {
		SplashWindow spw = SplashWindow.splash("EVO Client");
		spw.setStatus("Initializing...", 0);
		Main m = new Main(spw, "VRVS Client");
		spw.finishIt();
		spw = null;
	}
}
