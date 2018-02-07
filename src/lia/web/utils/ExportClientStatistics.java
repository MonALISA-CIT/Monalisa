package lia.web.utils;

import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.modules.monClientStats;

/**
 * @author costing
 *
 */
public class ExportClientStatistics extends ExportStatistics {

    /**
     * @param smb
     */
    public ExportClientStatistics(SerMonitorBase smb){
    
	init("Client", 0, ":"+System.getProperty("os.name")+":"+System.getProperty("java.version"));
	    
//	addMonitoringModule(new monProcLoad());
//	addMonitoringModule(new monProcStat());
//	addMonitoringModule(new monProcIO());
	monClientStats clientStats = new monClientStats();
	clientStats.setSMB(smb);
	addMonitoringModule(clientStats);
    }
    
}
