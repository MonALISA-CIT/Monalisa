package lia.Monitor.JiniClient.Farms;

import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniClient.CommonGUI.MainBase;
import lia.Monitor.JiniClient.CommonGUI.SplashWindow;
import lia.Monitor.JiniClient.CommonGUI.Groups.GroupsPanel;
import lia.Monitor.JiniClient.CommonGUI.Jogl.FarmsJoglPanel;
import lia.Monitor.JiniClient.CommonGUI.Jogl.JoglPanel;
import lia.Monitor.JiniClient.CommonGUI.Jogl.Grids.GridsJoglPanel;
import lia.Monitor.JiniClient.CommonGUI.OSG.OSGPanel;
import lia.Monitor.JiniClient.CommonGUI.Topology.GNetTopoPan;
import lia.Monitor.JiniClient.Farms.CienaMap.CienaMapPan;
import lia.Monitor.JiniClient.Farms.Gmap.GmapPan;
import lia.Monitor.JiniClient.Farms.Histograms.LoadHistoPan;
import lia.Monitor.JiniClient.Farms.Histograms.Traff;
import lia.Monitor.JiniClient.Farms.Histograms.VoJobs;
import lia.Monitor.JiniClient.Farms.Mmap.Mmap;
import lia.Monitor.JiniClient.Farms.OpticalSwitch.OpticalSwitchPan;
import lia.Monitor.JiniClient.Farms.Tabl.TabPan;
import lia.Monitor.monitor.AppConfig;
import lia.util.ntp.NTPDate;

/**
 * This launches the Farms Client
 */
public class Main extends MainBase {

    /**
     * hope is the version since the beginning
     */
    private static final long serialVersionUID = -8028582988168335623L;

    public static final String szDefaultShowPanels = "globe,wmap,table,load,wan,jogl,groups,osg,vo3d";
    public static final String szAllPanels = ",globe,wmap,gmap,table,load,wan,jogl,vojobs,topology,groups,osgmap,osg,vo3d,ciena,opticalswitch,";
    private final String showPanels = AppConfig.getProperty("lia.Monitor.showPanels", szDefaultShowPanels);
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    public Main(SplashWindow spw, String clientName) {
        super(clientName);

        int progress = 0;
        String[] props_tmp = showPanels.split(",");
        int pLen = 0;
        int i;
        for (i = 0; i < props_tmp.length; i++) {
            if (szAllPanels.indexOf("," + props_tmp[i] + ",") >= 0) {
                pLen++;
            }
        }
        String[] props = new String[pLen];
        int j;
        for (i = 0, j = 0; (i < props_tmp.length) && (j < pLen); i++) {
            if (szAllPanels.indexOf("," + props_tmp[i] + ",") >= 0) {
                props[j] = props_tmp[i];
                j++;
            }
        }
        ;
        if (showPanels.indexOf("globe") >= 0) {
            pLen += 1;
        }
        pLen++; // for displaying some progress before starting service monitor
        pLen += 2; // for starting service monitor
        int pDelta = 100 / pLen; // progress delta

        progress = pDelta;
        spw.setStatus("Starting Service Monitor...", progress);
        this.nStartCreateMonitorTime = NTPDate.currentTimeMillis();
        setSerMonitor(new FarmsSerMonitor(this, Main.class));
        progress += pDelta * 2;

        /**
         * piece of code to respect the order as in lia.Monitor.showPanels
         * property
         */
        for (i = 0; i < props.length; i++) {
            if (props[i].equals("wmap")) {
                // World Map
                spw.setStatus("Loading WorldMap Panel...", progress);
                addGraphical(new Mmap(), "WMap", "World Map", "wmap");
                progress += pDelta;
            } else if (props[i].equals("gmap")) {
                // GMap (removed -> is shown by default)
                spw.setStatus("Loading Graph Panel...", progress);
                addGraphical(new GmapPan(), "GMap", "Graph Map", "gmap");
                progress += pDelta;
            } else if (props[i].equals("table")) {
                // Table
                spw.setStatus("Loading Table Panel...", progress);
                addGraphical((panelTabl = new TabPan()), "TabPan", "RC Table", "table");
                progress += pDelta;
            } else if (props[i].equals("load")) {
                // Load
                spw.setStatus("Loading Load Distribution Panel...", progress);
                addGraphical(new LoadHistoPan(), "Load", "Load Distribution", "load");
                progress += pDelta;
            } else if (props[i].equals("wan")) {
                // WAN
                spw.setStatus("Loading WAN Traffic Panel...", progress);
                Traff traff = new Traff();
                addGraphical(traff, "WAN", "WAN Traffic", "wan");
                // this hack will probably never be fixed ...
                ((FarmsSerMonitor) (monitor)).addTraff(traff);
                progress += pDelta;
            } else if (props[i].equals("vojobs")) {
                // VO JOBS
                spw.setStatus("Loading VO Jobs Panel...", progress/* 90 */);
                addGraphical(new VoJobs(), "VO JOBS", "Grid3 Vo Jobs", "vojobs");
                progress += pDelta;
            } else if (props[i].equals("jogl")) {
                // Jogl panel
                try {
                    if (!bGridsClient) {
                        spw.setStatus("Loading Jogl Panel...", progress);
                        try {
                            JoglPanel jogl = new FarmsJoglPanel();
                            jogl.init();
                            addGraphical(jogl, "3D Map", "Jogl 3D map", "3dmap");
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, "Unable to load Jogl 3D map. Cause: ", t);
                        }
                    } else {
                        spw.setStatus("Loading Grids Jogl Panel...", progress);
                        try {
                            JoglPanel jogl = new GridsJoglPanel();
                            jogl.init();
                            addGraphical(jogl, "3D Map", "Jogl 3D map", "3dmap");
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, "Unable to load Grids Jogl Panel. Cause: ", t);
                        }
                    }
                } catch (UnsatisfiedLinkError ule) {
                    logger.log(Level.SEVERE,
                            "JOGL NOT OK. java.library.path=" + System.getProperty("java.library.path") + "\n Cause:",
                            ule);
                } catch (Throwable t) {
                    logger.log(Level.SEVERE,
                            "JOGL NOT OK. java.library.path=" + System.getProperty("java.library.path") + "\n Cause:",
                            t);
                }
                progress += pDelta;
            } else if (props[i].equals("topology")) {
                spw.setStatus("Loading Network Topology Panel...", progress);
                addGraphical(new GNetTopoPan(), "Topology", "Network Topology", "topology");
                progress += pDelta;
                topologyShown = true;
            } else if (props[i].equals("groups")) {
                spw.setStatus("Loading Groups Panel...", progress);
                GroupsPanel panel = new GroupsPanel();
                panel.init();
                addGraphical(panel, "Groups", "Groups", "groups");
                progress += pDelta;
            } else if (props[i].equals("osgmap")) {
                spw.setStatus("Loading OS GMap Panel...", progress);
                // only sets availability of osgmap panel
                ((FarmsSerMonitor) monitor).bHasOSGmapPanel = true;
                // OSGmapPan panel = new OSGmapPan();
                // addGraphical(panel, "OS GMap", "Optical Switch GMap",
                // "osgmap");
                progress += pDelta;
            } else if (props[i].equals("osg")) {
                spw.setStatus("Loading Grid Graph...", progress);
                OSGPanel panel = new OSGPanel();
                addGraphical(panel, "GridGraph", "GridGraph", "osg", "png");
                progress += pDelta;
            } else if (props[i].equals("vo3d")) {
                spw.setStatus("Loading VoJobs 3D...", progress);
                lia.Monitor.JiniClient.Farms.Plot3D.VoJobs panel = new lia.Monitor.JiniClient.Farms.Plot3D.VoJobs();
                addGraphical(panel, "VO3D", "VoJobs 3D Panel", "3dview", "png");
                progress += pDelta;
            } else if (props[i].equals("ciena")) {
                spw.setStatus("Loading CIENA...", progress);
                CienaMapPan panel = new CienaMapPan();
                ((FarmsSerMonitor) monitor).bHasCienaPanel = true;
                addGraphical(panel, "CIENA", "CIENA Panel", "ciena", "png");
                progress += pDelta;
            } else if (props[i].equals("opticalswitch")) {
                spw.setStatus("Loading OpticalSwitch...", progress);
                OpticalSwitchPan panel = OpticalSwitchPan.getInstance();
                ((FarmsSerMonitor) monitor).bHasOpticalSwitchPanel = true;
                addGraphical(panel, "TopoMap", "Optical Switch Map", "optical_switch");
                progress += pDelta;
            }
        }
        spw.setStatus("Starting...", progress);
        init();
    }

    public static void main(String[] args) {
        boolean gridmap_client = AppConfig.getProperty("ml_client.mainWindow.gridsClient", "false").equals("true");
        String clientName = "Farms Client";
        if (gridmap_client) {
            clientName = "GridMap Client";
        }
        SplashWindow spw = SplashWindow.splash(clientName);
        spw.setStatus("Initializing...", 0);
        Main m = new Main(spw, clientName);
        spw.finishIt();
        spw = null;
    }
}
