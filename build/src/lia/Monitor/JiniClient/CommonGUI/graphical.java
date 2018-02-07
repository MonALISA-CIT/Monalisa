package lia.Monitor.JiniClient.CommonGUI; 
import java.util.Map;
import java.util.Vector;

import net.jini.core.lookup.ServiceID;

public interface graphical 
{
 public void updateNode( rcNode node);
 public void gupdate();
 public void setNodes ( Map<ServiceID, rcNode> nodes , Vector<rcNode> vnodes);
 public void setSerMonitor ( SerMonitorBase ms) ;
 public void setMaxFlowData ( rcNode n, Vector v ) ;
 public void new_global_param( String name);
}
