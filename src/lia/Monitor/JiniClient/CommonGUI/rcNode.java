package lia.Monitor.JiniClient.CommonGUI;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.swing.ImageIcon;

import lia.Monitor.Agents.OpticalPath.OpticalSwitchInfo;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwConfig;
import lia.Monitor.JiniClient.Farms.OSGmap.Config.OSMonitorControl;
import lia.Monitor.JiniClient.Farms.OSGmap.Ortho.OpticalConnectivityToolTip;
import lia.Monitor.JiniClient.Farms.OpticalSwitch.Config.OpticalSwitchMonitorControl;
import lia.Monitor.JiniClient.Farms.OpticalSwitch.Ortho.OpticalSwitchConnectivityToolTip;
import lia.Monitor.monitor.DataStore;
import lia.Monitor.monitor.Gresult;
import lia.Monitor.monitor.MonaLisaEntry;
import lia.Monitor.monitor.OSLink;
import lia.Monitor.monitor.Result;
import lia.Monitor.tcpClient.tClient;
import lia.net.topology.GenericEntity;
import lia.net.topology.Link;
import lia.net.topology.Port;
import lia.net.topology.TopologyNotifier;
import lia.net.topology.ciena.CienaHost;
import lia.net.topology.ciena.CienaPort;
import lia.net.topology.force10.Force10Host;
import lia.net.topology.force10.Force10Port;
import lia.net.topology.host.ComputerHost;
import lia.net.topology.host.ComputerPort;
import lia.net.topology.opticalswitch.OSPort;
import lia.net.topology.opticalswitch.OpticalSwitch;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;

public class rcNode implements TopologyNotifier {
	public int myID ;
	public ServiceID sid ;
	public MonaLisaEntry mlentry;
	public Entry[] attrs ;
	public String IPaddress;
	public String LAT;
	public String LONG;
	public String CITY ;
	
	public String szOpticalSwitch_Name; //a farm can have only one OS, if not, PLEASE remove this!
	
	public int x, y;
	public int osgX, osgY;
	public int ix,iy;
	public tClient client;
	public DataStore dataStore;
	public int errorCount;
	public String UnitName;
	public String shortName;
	public Rectangle limits ;
	public volatile boolean fixed;
	public boolean selected;
	public Hashtable conn;
	public Hashtable wconn;
	public long time;				// time of last result
	public double BestEstimate ;
	public rcNode Predecessor;
	public ImageIcon icon;
	public Hashtable haux = new Hashtable();
	public Hashtable<String, Gresult> global_param = new Hashtable<String, Gresult>();
	public volatile boolean bHiddenOnMap = false; //hides node on mmap and globe
	public volatile boolean isLayoutHandled = false;
	private OpticalConnectivityToolTip opticalToolTip = null;
	private OpticalSwitchInfo result = null;
	private OSwConfig newResult = null; // the new type of result
	
	public ServiceID fakeSid = null;
	public Rectangle osgLimits = null;
	
	private GenericEntity osResult = null;
	private OpticalSwitchConnectivityToolTip opticalSwitchToolTip = null;
	
	public Map<ServiceID, rcNode> snodes;
	
	public rcNode() {
		
	}
	
	private rcNode findNeighbour(rcNode no){
		rcNode nq = null ;
		
		for ( Enumeration e = conn.keys() ; e.hasMoreElements(); ) {
			rcNode nx = ( rcNode) e.nextElement () ;
			if ( nx.IPaddress.equals ( no.IPaddress) ) {nq = nx ; break ; }
		}
		return nq;	
	}
	
	public double connPerformance ( rcNode no ) {
//		This reaturs the PingBandwidth from this node to no  
		Result r = (Result) conn.get( no ) ;
		if ( r != null ) {
			return r.param[0];
		}
		
		rcNode nq = findNeighbour(no);
		
		if ( nq == null ) return -1;
		
		Result r1 = (Result) conn.get( nq ) ;
		if ( r1 != null ) {
			return r1.param[0];	// both old Ping and ABPing return in param[0] the "quality"
		}
		return -1;
		
	}  
	public double connRTT ( rcNode no ) {
//		This resturs the RTT value from this node to the no
		rcNode nq =  findNeighbour(no);
		if(nq == null)
			return -1;
		Result r = (Result) conn.get( nq ) ;
		if ( r == null || r.param_name == null || r.param == null) return -1;
		if ( r.param.length < 3 ) return -1;
		if(r.param_name[0].equals("PingBandwidth"))
			return r.param[2];
		if(r.param_name[0].equals("RTime"))
			return r.param[1];
		return -1;
	}
	public double connLP ( rcNode no ) {
//		This resturs the lost Packaes value from this node to the no
		rcNode nq =  findNeighbour(no);
		if(nq == null)
			return -1;
		Result r = (Result) conn.get( nq ) ;
		if ( r == null || r.param_name == null || r.param == null) return -1;
		if ( r.param.length < 2 ) return -1;
		if(r.param_name[0].equals("PingBandwidth"))
			return r.param[1];
		if(r.param_name[0].equals("RTime"))
			return r.param[3];
		return -1;
	}
	
	public void setOpticalConnectivityToolTip(OpticalConnectivityToolTip t) {
		this.opticalToolTip = t;
	}
	
	public void setOpticalSwitchConnectivityToolTip(OpticalSwitchConnectivityToolTip t) {
		this.opticalSwitchToolTip = t;
	}
	
	public OpticalSwitchInfo getOpticalSwitchInfo() {
		return result;
	}
	
	public OSwConfig getOSwConfig() {
		return newResult;
	}
	
	public GenericEntity getOpticalSwitch() {
		return osResult;
	}
	
	public void newOpticalResult(OpticalSwitchInfo result) {
		
		this.result = result;
		if (opticalToolTip != null) {
			opticalToolTip.updateResult(result);
		}
		if (client != null && client.osControl != null) {
			client.osControl.setOpticalSwitchInfo(result);
		} else
			if (client != null) {
				client.redoOS(result, true);
			}
		OSMonitorControl.setOpticalSwitchInfo(result.name, result);
	}

	public void newOpticalResult(OSwConfig result) {
		
		this.newResult = result;
		if (opticalToolTip != null) {
			opticalToolTip.updateResult(result);
		}
		if (client != null && client.osControl != null) {
			client.osControl.setOpticalSwitchInfo(result);
		} else
			if (client != null) {
				client.redoOS(result, true);
			}
		OSMonitorControl.setOpticalSwitchInfo(result.name, result);
	}

	
	public void setShortName(){
		shortName = UnitName;
		if(shortName.length() >= 12){
			shortName = shortName.substring(0, 10) + "...";
		}
	}
	
	@Override
    public String toString() {
		
		if (mlentry == null) return "";
		return mlentry.Name;
	}

	private void checkPorts(String name, Port p[]) {
        List<Link> linksList = new ArrayList<Link>();

		if (p != null) {
			for (int i=0; i<p.length; i++){
				if (p[i].outgoingLink() != null) {
					Link opLink = p[i].outgoingLink();
					linksList.add(opLink);						
					//check to see if link already exists, based on above attributes, or create it otherwise
    				//this should find duplicate fake nodes that share the same name...
    				String key = OSLink.ToString(name,
    						opLink.sourcePort(), opLink.destinationPort().device().name(), opLink.destinationPort().name());
    				OSLink osLink = (OSLink)wconn.get(key); 
    				if ( osLink == null ) {
    					//set destination based on name
    					//check first for its destination
    					Port d = opLink.destinationPort();
    					GenericEntity owner = d.device();
    					try {
    						for(final rcNode node : snodes.values()) {
    							if (node.getOpticalSwitch() != null && owner.equals(node.getOpticalSwitch())) { // found the rcNode
    								
    								osLink = new OSLink( this, node, opLink.sourcePort(), d.name(), name, owner.name(), opLink.name());
    								osLink.szDestinationOSPort = d;
    								wconn.put(key, osLink);
    								osLink.setLink( opLink);
    								System.err.println( new Date() + " [ " + name + " ] NEW OS LINK: " + key);
    								break;
    							}
    						}
    					} catch(Exception ex) {
    						ex.printStackTrace();
    					}
    				}
    				if ( osLink!=null ) {
    					//optical link exists, so update it's data
//    					osLink.updateState( opLink.state);
    				}
				}
			}
		}
		//second, remove old links from wconn if not any more in map
		try {
			Vector keys = new Vector();
			
			for( Enumeration en=wconn.keys(); en.hasMoreElements(); ) {
				Object k = en.nextElement();
				OSLink osLink = (OSLink)wconn.get(k);
				//iterrate through map values to search for an optical link with reference
				boolean isFound = false;
				if (p != null) {
				    if(osLink.opLink == null || (/*linksList.size() > 0 &&*/ !linksList.contains(osLink.opLink))) {
				        keys.add(k);
                        System.err.println( new Date() + " [ " + name + " ] Remove OS LINK: " + k);
				    }
//					for (int i=0; i<p.length; i++){
//						if (p[i].outgoingLink() != null) {
//							Link opLink = (Link)p[i].outgoingLink();
//							if (osLink.opLink.equals(opLink)) {
//								isFound = true;
//								break;
//							}
//						}
//					}
				}
//				if (!isFound)
//					keys.add(k);
			}
			for (int i=0; i<keys.size(); i++)
				wconn.remove(keys.get(i));
		} catch ( Exception ex) {
			ex.printStackTrace();
		}
	}
	
	
    @Override
    public void newEntity(GenericEntity newEntity) {
    	
    	this.osResult = newEntity;
		if (opticalSwitchToolTip != null) {
			opticalSwitchToolTip.updateResult(newEntity);
		}
		
		if (client != null && client.opticalSwitchControl != null) {
			client.opticalSwitchControl.setOpticalSwitchInfo(newEntity);
		} else
			if (client != null) {
				client.redoOS(newEntity, true);
			}
		OpticalSwitchMonitorControl.setOpticalSwitchInfo(newEntity.name(), newEntity);
		
		// add the possible links...
		if (newEntity instanceof OpticalSwitch) {
			OSPort p[] = ((OpticalSwitch)newEntity).getPorts();
			checkPorts(newEntity.name(), p);
		}
		
		if (newEntity instanceof ComputerHost) {
			ComputerPort p[] = ((ComputerHost)newEntity).getPorts();
			checkPorts(newEntity.name(), p);
		}
		
		if (newEntity instanceof CienaHost) {
			CienaPort p[] = ((CienaHost)newEntity).getPorts();
			checkPorts(newEntity.name(), p);
		}
		
		if (newEntity instanceof Force10Host) {
			Force10Port p[] = ((Force10Host)newEntity).getPorts();
			checkPorts(newEntity.name(), p);
		}
    }

    @Override
    public void updateEntity(GenericEntity oldEntity, GenericEntity newEntity) {
    	// add the possible links...
		if (newEntity instanceof OpticalSwitch) {
			OSPort p[] = ((OpticalSwitch)newEntity).getPorts();
			checkPorts(newEntity.name(), p);
		}
		
		if (newEntity instanceof ComputerHost) {
			ComputerPort p[] = ((ComputerHost)newEntity).getPorts();
			checkPorts(newEntity.name(), p);
		}
		
		if (newEntity instanceof CienaHost) {
			CienaPort p[] = ((CienaHost)newEntity).getPorts();
			checkPorts(newEntity.name(), p);
		}
		
		if (newEntity instanceof Force10Host) {
			Force10Port p[] = ((Force10Host)newEntity).getPorts();
			checkPorts(newEntity.name(), p);
		}
    }
	
}
