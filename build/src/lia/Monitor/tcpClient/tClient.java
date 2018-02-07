
package lia.Monitor.tcpClient;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Vector;
import java.util.logging.Level;

import lia.Monitor.Agents.OpticalPath.OpticalSwitchInfo;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwConfig;
import lia.Monitor.JiniClient.CommonGUI.IpAddrCache;
import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.Farms.OSGmap.Config.OSMonitorControl;
import lia.Monitor.JiniClient.Farms.OpticalSwitch.Config.OpticalSwitchMonitorControl;
import lia.Monitor.ciena.circuits.topo.CircuitsHolder;
import lia.Monitor.ciena.circuits.topo.tl1.TL1CDCICircuitsHolder;
import lia.Monitor.ciena.osrp.tl1.OsrpTL1Topo;
import lia.Monitor.ciena.osrp.topo.OsrpTopoHolder;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.ExtendedSiteInfoEntry;
import lia.Monitor.monitor.GenericMLEntry;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.Monitor.monitor.MonaLisaEntry;
import lia.Monitor.monitor.SiteInfoEntry;
import lia.Monitor.monitor.monPredicate;
import lia.net.topology.GenericEntity;
import lia.util.Utils;
import net.jini.core.lookup.ServiceID;

public class tClient extends MLSerClient{

	private final Object syncConf = new Object();
	private final Object syncActive = new Object();
	protected int remoteControlPort = 0;
	protected int remoteRegistryPort = 1099;
	protected int port = 0;
	protected String ipAddress;
	public MonaLisaEntry mle;
	protected SiteInfoEntry sie;
	public ExtendedSiteInfoEntry esie;
	
	public tRCFrame trcframe;
	/** variables that regard os monitoring */
	public OSMonitorControl osControl = null;
	OpticalSwitchInfo oldResult = null;
	OSwConfig oldNewResult = null; // for the new type of result a new result object
	public OpticalSwitchInfo OSInfo = null;
	public OSwConfig OSNewInfo = null;
	public ArrayList portMap = null;
	/**stores the total number of parameters for this farm*/

	public OpticalSwitchMonitorControl opticalSwitchControl = null;
	public GenericEntity opticalSwitchInfo = null;
	
	static OSAdminDispatcher osdispatcher = null;
	static {
		osdispatcher = new OSAdminDispatcher();
		executor.execute(osdispatcher);
	}
	
	public tClient(String name, String ipAddress, String hostName, 
	        int port, int remoteRegistryPort, int ControlPort,
	        MonaLisaEntry mle, SiteInfoEntry sie, ExtendedSiteInfoEntry esie, 
	        ConnMessageMux tclient, ServiceID tClientID) throws Exception {

		super(name, IpAddrCache.getInetAddress(ipAddress), hostName, tclient, tClientID);
		
		this.remoteRegistryPort = remoteRegistryPort;
		this.remoteControlPort = ControlPort;
		this.ipAddress = ipAddress;
		this.port = port;
		this.mle = mle;
		this.sie = sie;
		this.esie = esie;
	}

	public String getIPAddress() {
		
		return ipAddress;
	}

	public String getHostName() {
		
		return hostName;
	}

	public int getPort() {
		
		return port;
	}
	
	public void checkFirstConfiguration () {
		trcframe = new tRCFrame(FarmName, null, this,
				address, remoteRegistryPort, remoteControlPort,
				FarmName + "@" + hostName + ":" + port,
				mle, sie, esie);
		trcframe.setSerMonitorBase((SerMonitorBase)msgMux.jiniClient);
		trcframe.setUptime(uptime);
		trcframe.setLocalTime(localTime);
		trcframe.setMLVersion(mlVersion);
		trcframe.setBuildNr(buildNr);
	    MonMessageClientsProxy configuration = msgMux.knownConfigurations.get (tClientID) ;
		if (configuration!=null) {
			newConfig ((MFarm) configuration.result);
		} //if
	} //checkFirstConfiguration
	
    @Override
    protected void processPredicateOrFilterResult(MonMessageClientsProxy msg) {
        if (msg.ident instanceof String) {
            if(((String)msg.ident).equals("OsrpTopoFilter")) {
                final long sTime = System.currentTimeMillis();
                try {
                    final OsrpTL1Topo[] topos = (OsrpTL1Topo[])Utils.readObject((byte[])(((Vector)msg.result).get(0)));
                    OsrpTopoHolder.notifyTL1Responses(topos);
                }catch(Throwable t) {
                    logger.log(Level.WARNING, " [ MLSerClient ] Exception parsing OsrpTopoFilter result", t);
                } finally {
                    if(logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, " [ MLSerClient ] [ topos ] DT = " + (System.currentTimeMillis()  - sTime) + " ms");
                    }
                }
                return;
            }
            
            if(((String)msg.ident).equals("CienaSNCFilter")) {
                final long sTime = System.currentTimeMillis();
                try {
                    final TL1CDCICircuitsHolder[] topos = (TL1CDCICircuitsHolder[])Utils.readCompressedObject((byte[])(((Vector)msg.result).get(0)));
                    CircuitsHolder.getInstance().notifyTL1Responses(topos);
                }catch(Throwable t) {
                    logger.log(Level.WARNING, " [ MLSerClient ] Exception parsing CienaSNCFilter result", t);
                } finally {
                    if(logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, " [ MLSerClient ] [ CienaSNCFilter ] DT = " + (System.currentTimeMillis()  - sTime) + " ms");
                    }
                }
                return;
            }
        }
        
        super.processPredicateOrFilterResult(msg);
    }
    
	@Override
    public void newConfig(MFarm nfarm) {
		if(logger.isLoggable(Level.FINE))
		    logger.log(Level.FINE, "tClient ====> new configuration for farm ====>  " + nfarm.name + " sid="+tClientID) ;		
		synchronized (syncConf) {
			if (farm == null) {
				farm = nfarm;
				if (trcframe != null && !conf_added) {
					trcframe.addFarm(farm);
					conf_added = true;
				}
			} else {
				farm = nfarm;
				if (trcframe != null) {
					if (conf_added) {
						trcframe.updateFarm(farm, false);
					} else {
						trcframe.addFarm(farm);
						conf_added = true;
					}
				}
			}
		} //sync
	}
	
	public ExtendedSiteInfoEntry getExtendedSiteInfoEntry() {
		return esie;
	}

	public void portMapChanged(ArrayList ports) {
		if (osControl != null) {
			// register this module for results
			osControl.setPortList(ports);
			portMap = ports;
		}
	}
	
	public boolean isVisible() {
	    synchronized(syncActive){
	        if(active && trcframe == null)
	            checkFirstConfiguration();
			if(trcframe != null)
				return trcframe.isVisible();
			return false;
	    }
	}

	public void setVisible(boolean b) {
	    synchronized(syncActive){
	        if(active && trcframe == null)
	            checkFirstConfiguration();
	        if(trcframe != null) {
				trcframe.setVisible(b);
	        }
	    }
	}
	
	@Override
    public void postSetLocalTime(String time){
		if (trcframe != null)
			trcframe.setLocalTime(time);	
	}
	
	@Override
    public void postSetUptime(String uptime) {
		if (trcframe != null)
			trcframe.setUptime(uptime);
	}

	@Override
    public void postSetMLVersion(String version) {
		if (trcframe != null){
			trcframe.setMLVersion(mlVersion);
			trcframe.setBuildNr(buildNr);
		}
	}

	public void setActive(boolean active) {
		tRCFrame tmp = null;
		synchronized(syncActive){
			this.active = active ;
			if(active == false && trcframe != null){
	//			System.out.println("disposing frame for "+myName);
				tmp = trcframe;
				trcframe = null;
			}
		}
		if (tmp != null) {
			tmp.stopIt();
			tmp.dispose();
		}
	} //setActive

	public void stopIt() {
		if (osControl != null) {
			osControl.windowClosed();
			deleteLocalClient(osControl);
			if (address != null) {
				final String key = address.getHostAddress()+":"+remoteRegistryPort;
				SerMonitorBase.osControlModules.remove(key);
			}
			osControl.stopIt();
			osControl = null;
		}
	}
	
	/**
     * checks list of osgroups received as startup parameter to see if function parameter
     * is an os group
     * @param group_name name of group to test
     * @return boolean value to indicate apartenence
     */
    public static boolean isOSgroup( String group_name) 
    {
        String sListOfOSGroups = AppConfig.getProperty("lia.Monitor.osgroup","OSwitch,AFOX");
        String []osGroups = sListOfOSGroups.split(",");
        String []oss = group_name.split(",");
        if (osGroups == null || osGroups.length == 0 || oss == null || oss.length == 0)
        	return false;
        for ( int i=0; i<osGroups.length; i++) {
        	for ( int j = 0; j<oss.length; j++)
        		if ( oss[j].equals(osGroups[i]) )
        			return true;
        }
        return false;
    }

	boolean isOSSwitch() {
		
		if (mle == null || mle.Group == null) return false;
		String group = mle.Group;
		String p[] = group.split(",");
		if (p == null) return false;
		for (int i=0; i<p.length; i++)
			if (isOSgroup(p[i]) ) return true;
		return false;
	}
	
	public void redoOS(OpticalSwitchInfo result, boolean checkNew) {
		if (!isOSSwitch()) return;
		if (checkNew) {
			osdispatcher.addOSAdminThread(this, new OSAdminThread(osdispatcher, this, result));
		} else {
			osdispatcher.addOSAdminThread(this, new OSAdminThread(osdispatcher, this));
		}
	}
	
	public void redoOS(OSwConfig result, boolean checkNew) {
		if (!isOSSwitch()) return;
		if (checkNew) {
			osdispatcher.addOSAdminThread(this, new OSAdminThread(osdispatcher, this, result));
		} else {
			osdispatcher.addOSAdminThread(this, new OSAdminThread(osdispatcher, this));
		}
	}
	
	public void redoOS(GenericEntity result, boolean checkNew) {
		if (checkNew) {
			osdispatcher.addOSAdminThread(this, new OSAdminThread(osdispatcher, this, result));
		} else {
			osdispatcher.addOSAdminThread(this, new OSAdminThread(osdispatcher, this));
		}
	}
	
	static class OSAdminThread extends Thread {
		
		private tClient client;
		private OpticalSwitchInfo result = null;
		private OSwConfig newResult = null; // the new type of possible result
		private GenericEntity osResult = null;
		private OSAdminDispatcher dispatcher;
		
		public OSAdminThread(OSAdminDispatcher dispatcher, tClient client) {
			super("(ML) OSAdminThread @"+client.hostName);
			this.client = client;
			this.dispatcher = dispatcher;
		}
		
		public OSAdminThread(OSAdminDispatcher dispatcher, tClient client, OpticalSwitchInfo result) {
			this(dispatcher, client);
			this.result = result;
		}

		public OSAdminThread(OSAdminDispatcher dispatcher, tClient client, OSwConfig result) {
			this(dispatcher, client);
			this.newResult = result;
		}

		public OSAdminThread(OSAdminDispatcher dispatcher, tClient client, GenericEntity result) {
			this(dispatcher, client);
			this.osResult = result;
		}
		
		private void checkNewResult() {
			
			if (result == null && newResult == null) return;
			if (result != null && client.oldResult != null && (result.isAlive == client.oldResult.isAlive)) return;
			if (newResult != null && client.oldNewResult != null && (newResult.isAlive == client.oldNewResult.isAlive)) return;
			
			if (result != null)
				client.oldResult = client.OSInfo = result;
			if (newResult != null)
				client.oldNewResult = client.OSNewInfo = newResult;
			
			if ((client.OSInfo != null && !client.OSInfo.isAlive) || (client.OSNewInfo != null && !client.OSNewInfo.isAlive)) {
				if (client.address != null) {
					final String key = client.address.getHostAddress()+":"+client.remoteRegistryPort;
					SerMonitorBase.osControlModules.remove(key);
				}
				if (client.osControl != null) {
					client.deleteLocalClient(client.osControl);
					client.osControl.windowClosed();
					client.osControl = null;
				}
				if (client.trcframe != null)
					client.trcframe.osAdmin.setVisible(false);
				return;
			}

		}
		
		private void checkOSResult() {

			if (osResult == null) return;

			client.opticalSwitchInfo = osResult;

			if (client.opticalSwitchInfo != null) {
				if (client.address != null) {
					final String key = client.address.getHostAddress()+":"+client.remoteRegistryPort;
					SerMonitorBase.osControlModules.remove(key);
				}
				if (client.opticalSwitchControl != null) {
					client.deleteLocalClient(client.opticalSwitchControl);
					client.opticalSwitchControl.windowClosed();
					client.opticalSwitchControl = null;
				}
				if (client.trcframe != null)
					client.trcframe.osAdmin.setVisible(false);
				return;
			}

		}
		
		@Override
        public void run() {
			
			if (AppConfig.getProperty("lia.Monitor.OSGmap.debug", "false").equals("true")) {
				logger.warning("admin called for "+client.FarmName+"  "+client.address.getHostAddress()+":"+client.remoteRegistryPort);
			}
			
			checkNewResult();
			checkOSResult();
			
			if (osResult == null) {
				try {
					if (client.address != null) {
						final String key = client.address.getHostAddress()+":"+client.remoteRegistryPort;
						if ((client.OSInfo != null && !client.OSInfo.isAlive) || (client.OSNewInfo != null && !client.OSNewInfo.isAlive)) {
							if (client.address != null) {
								SerMonitorBase.osControlModules.remove(key);
							}
							if (client.osControl != null) {
								client.deleteLocalClient(client.osControl);
								client.osControl.windowClosed();
								client.osControl = null;
							}
							if (client.trcframe != null && client.trcframe.osAdmin != null)
								client.trcframe.osAdmin.setVisible(false);
							if (dispatcher != null) dispatcher.removeOSAdminThread(client);
							return;
						}
						if (SerMonitorBase.osControlModules.containsKey(key)) {
							if (AppConfig.getb("lia.Monitor.OSGmap.debug", false)) {
								logger.warning("osControlModules contains key "+key);
							}
							if (dispatcher != null) dispatcher.removeOSAdminThread(client);
							return;
						}
						if (client.osControl != null) {
							client.deleteLocalClient(client.osControl);
							client.osControl.windowClosed();
						}
						client.osControl = null;
						if ((client.OSInfo == null && client.OSNewInfo == null)|| (client.OSInfo != null && client.OSInfo.isAlive) || (client.OSNewInfo != null && client.OSNewInfo.isAlive)) {
							if (AppConfig.getProperty("lia.Monitor.OSGmap.debug", "false").equals("true"))
								logger.warning("Retrying connect with "+client.address.getHostAddress()+":"+client.remoteRegistryPort);
							client.osControl = OSMonitorControl.connectTo(client, client.address.getHostAddress(), client.remoteRegistryPort);
							if (AppConfig.getProperty("lia.Monitor.OSGmap.debug", "false").equals("true"))
								logger.warning("osControl for "+client.FarmName+" - "+client.address.getHostAddress()+":"+client.remoteRegistryPort+" is "+(client.osControl!=null));
						}
						if (client.osControl != null) {
							// register this module for results
							monPredicate p = new monPredicate(client.FarmName, "OS_Ports", "*", -60000,-1, new String[] { "Port-Power" }, null);
							client.addLocalClient(client.osControl, p);
							SerMonitorBase.osControlModules.put(key, client.osControl);
							if (client.OSInfo != null)
								client.osControl.setName(client.OSInfo.name);
							else if (client.OSNewInfo != null)
								client.osControl.setName(client.OSNewInfo.name);
							else if (client.mle != null)
								client.osControl.setName(client.mle.Name);
							else
								client.osControl.setName(client.hostName);
							if (client.OSInfo != null) {
								client.osControl.setOpticalSwitchInfo(client.OSInfo);
							} else if (client.OSNewInfo != null)
								client.osControl.setOpticalSwitchInfo(client.OSNewInfo);
							GenericMLEntry gmle = client.getGMLEntry();
							if (gmle.hash != null && gmle.hash.containsKey("OS_PortMap")) {
								ArrayList list = (ArrayList)gmle.hash.get("OS_PortMap");
								client.osControl.setPortList(list);
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				try {
					if (client.address != null) {
						String key = client.address.getHostAddress()+":"+client.remoteRegistryPort;
						if (client.opticalSwitchInfo == null) {
							if (client.address != null) {
								if (SerMonitorBase.osControlModules.containsKey(key))
									SerMonitorBase.osControlModules.remove(key);
							}
							if (client.opticalSwitchControl != null) {
								client.deleteLocalClient(client.opticalSwitchControl);
								client.opticalSwitchControl.windowClosed();
								client.opticalSwitchControl = null;
							}
							if (client.trcframe != null && client.trcframe.osAdmin != null)
								client.trcframe.osAdmin.setVisible(false);
							if (dispatcher != null) dispatcher.removeOSAdminThread(client);
							return;
						}
						if (SerMonitorBase.osControlModules.containsKey(key)) {
							if (AppConfig.getProperty("lia.Monitor.OSGmap.debug", "false").equals("true")) {
								logger.warning("osControlModules contains key "+key);
							}
							if (dispatcher != null) dispatcher.removeOSAdminThread(client);
							return;
						}
						if (client.opticalSwitchControl != null) {
							client.deleteLocalClient(client.opticalSwitchControl);
							client.opticalSwitchControl.windowClosed();
						}
						client.opticalSwitchControl = null;
						if (client.opticalSwitchInfo != null) {
							if (AppConfig.getProperty("lia.Monitor.OSGmap.debug", "false").equals("true"))
								logger.warning("Retrying connect with "+client.address.getHostAddress()+":"+client.remoteRegistryPort);
							client.opticalSwitchControl = OpticalSwitchMonitorControl.connectTo(client, client.address.getHostAddress(), client.remoteRegistryPort);
							if (AppConfig.getProperty("lia.Monitor.OSGmap.debug", "false").equals("true"))
								logger.warning("osControl for "+client.FarmName+" - "+client.address.getHostAddress()+":"+client.remoteRegistryPort+" is "+(client.osControl!=null));
						}
						if (client.opticalSwitchControl != null) {
							// register this module for results
							monPredicate p = new monPredicate(client.FarmName, "OS_Ports", "*", -60000,-1, new String[] { "Port-Power" }, null);
							client.addLocalClient(client.opticalSwitchControl, p);
							SerMonitorBase.osControlModules.put(key, client.opticalSwitchControl);
							if (client.opticalSwitchInfo != null)
								client.opticalSwitchControl.setName(client.opticalSwitchInfo.name());
							if (client.osControl != null)
								client.osControl.windowClosed();
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			if (osResult == null) {
				if (client.trcframe != null && client.trcframe.osAdmin != null) {
					client.trcframe.osAdmin.setVisible(client.osControl != null);
				}
				if (dispatcher != null) dispatcher.removeOSAdminThread(client);
			}
			
			if (osResult != null) {
				if (client.trcframe != null && client.trcframe.osAdmin != null) {
					client.trcframe.osAdmin.setVisible(client.opticalSwitchControl != null);
				}
				if (dispatcher != null) dispatcher.removeOSAdminThread(client);
			}
		}
	}
	
	static class OSAdminDispatcher implements Runnable {
		
		private Hashtable<tClient, OSAdminThread>  hash = null; // current running threads
		private Hashtable<tClient, LinkedList<OSAdminThread>> list = null; // current available threads
		final static Object lock = new Object();
		
		public OSAdminDispatcher() {
			hash = new Hashtable<tClient, OSAdminThread>();
			list = new Hashtable<tClient, LinkedList<OSAdminThread>>();
		}
		
		public void addOSAdminThread(tClient client, OSAdminThread thread) {
			
			synchronized (lock) {
				if (list.containsKey(client)) {
					LinkedList<OSAdminThread> ll = list.get(client);
					ll.addLast(thread);
				} else {
					LinkedList<OSAdminThread> ll = new LinkedList<OSAdminThread>();
					ll.addLast(thread);
					list.put(client, ll);
				}
				if (!hash.containsKey(client)) // we do not have any thread running for that client
					lock.notifyAll(); // wake up the dispatcher
			}
		}
		
		public void removeOSAdminThread(tClient client) {
			
			synchronized (lock) {
				hash.remove(client); // mark it as already executed
				if (list.containsKey(client)) // is there another waiting ?
					lock.notifyAll(); // signal the dispatcher to process it
			}
		}
		
		@Override
        public void run() {
			while (true) {
				synchronized (lock) {
					try {
						lock.wait();
					} catch (Exception ex) { }
					// parse the entire list of waiting clients
					for (Enumeration<tClient> en = list.keys(); en.hasMoreElements(); ) {
						tClient client = en.nextElement();
						if (!hash.containsKey(client)) {
							LinkedList<OSAdminThread> ll = list.get(client);
							OSAdminThread thread = ll.removeFirst();
							if (ll.size() == 0) list.remove(client); // no point in keeping an empty list
							hash.put(client, thread); // mark it as running
							executor.execute(thread);
						}
					}
				}
			}
		}
	}

}
