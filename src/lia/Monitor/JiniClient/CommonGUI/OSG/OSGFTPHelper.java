package lia.Monitor.JiniClient.CommonGUI.OSG;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Gmap.GraphTopology;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.Buffer;
import lia.Monitor.tcpClient.MLSerClient;
import lia.Monitor.tcpClient.ResultProcesserInterface;
import net.jini.core.lookup.ServiceID;
import plot.math.MLSeries;

/**
 * Class that handles the current Ftp transfers.
 */
public class OSGFTPHelper implements LocalDataFarmClient, ResultProcesserInterface {

	public Vector currentNodes; // the current rcNodes
	volatile Map<ServiceID, rcNode> hnodes;
	long realtimeStart = - 3 * 60 * 60 * 1000; // last 3 minutes
	HashMap domainNames; // for each domain there must be a corresponding rcNode, so once found keep it and use it
	
	HashMap ftpInputs; // map rcNode -> { VO name, domain name, rcNode, ftp input value }
	HashMap ftpOutputs; // map rcNode -> { VO name, domain name, rcNode, ftp output value }
	HashMap ftpRateIns; // map rcNode -> { VO name, domain name, rcNode, ftp rate input value }
	HashMap ftpRateOuts; // map rcNode -> { VO name, domain name, rcNode, ftp rate output value }
	HashMap ftpSeries;
	
	/** constants */
	public static final byte FTP_INPUT = 0;
	public static final byte FTP_OUTPUT = 1;
	public static final byte FTP_INOUT = 2;
	public static final byte FTP_INPUT_RATE = 3;
	public static final byte FTP_OUTPUT_RATE = 4;
	public static final byte FTP_INOUT_RATE = 5;
	
	private OSGPanel owner;
	private Buffer buffer;
	
	OSGComputer osgC;
	
	/** Constructor */
	public OSGFTPHelper(OSGPanel owner) {
		
		this.owner = owner;
		currentNodes = new Vector();
		domainNames = new HashMap();
		ftpInputs = new HashMap();
		ftpOutputs = new HashMap();
		ftpRateIns = new HashMap();
		ftpRateOuts = new HashMap();
		ftpSeries = new HashMap();
		
		osgC = new OSGComputer();
		
		buffer = new Buffer(this, "ML (Result Buffer for result of OSGFTPHelper)");
		buffer.start();
	}
	
	/** once a new node is found send a predicate for osg vo's*/
	public void addNode(rcNode n) {
		
		monPredicate pred = new monPredicate("*", "osgVO_IO", "*", realtimeStart, -1, null, null);
		n.client.addLocalClient(this, pred);
		currentNodes.add(n);
	}
	
	/** if a node is no longer available, deregister for results */
	public void deleteNode(rcNode n) {
		
		if (currentNodes.contains(n)) {
			n.client.deleteLocalClient(this);
			currentNodes.remove(n);
		}
		ftpInputs.remove(n);
		ftpOutputs.remove(n);
		ftpRateIns.remove(n);
		ftpRateOuts.remove(n);
		Vector tmp = new Vector();
		
		for (Iterator it = ftpInputs.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry entry = (Map.Entry)it.next();
			Object key = entry.getKey();
			Vector v = (Vector)entry.getValue();
			for (int i=0; i<v.size(); i++) {
				Object[] obj = (Object[])v.get(i);
				if (n.equals(obj[2])) {
					v.remove(i);
					i--;
				}
			}
			if (v.size() == 0) tmp.add(key);
		}
		for (int i=0; i<tmp.size(); i++)
			ftpInputs.remove(tmp.get(i));		
		tmp.clear();
		
		for (Iterator it = ftpOutputs.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry entry = (Map.Entry)it.next();
			Object key = entry.getKey();
			Vector v = (Vector)entry.getValue();
			for (int i=0; i<v.size(); i++) {
				Object[] obj = (Object[])v.get(i);
				if (n.equals(obj[2])) {
					v.remove(i);
					i--;
				}
			}
			if (v.size() == 0) tmp.add(key);
		}
		for (int i=0; i<tmp.size(); i++)
			ftpOutputs.remove(tmp.get(i));
		tmp.clear();
		
		for (Iterator it = ftpRateIns.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry entry = (Map.Entry)it.next();
			Object key = entry.getKey();
			Vector v = (Vector)entry.getValue();
			for (int i=0; i<v.size(); i++) {
				Object[] obj = (Object[])v.get(i);
				if (n.equals(obj[2])) {
					v.remove(i);
					i--;
				}
			}
			if (v.size() == 0) tmp.add(key);
		}
		for (int i=0; i<tmp.size(); i++)
			ftpRateIns.remove(tmp.get(i));
		tmp.clear();
		
		for (Iterator it = ftpRateOuts.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry entry = (Map.Entry)it.next();
			Object key = entry.getKey();
			Vector v = (Vector)entry.getValue();
			for (int i=0; i<v.size(); i++) {
				Object[] obj = (Object[])v.get(i);
				if (n.equals(obj[2])) {
					v.remove(i);
					i--;
				}
			}
			if (v.size() == 0) tmp.add(key);
		}
		for (int i=0; i<tmp.size(); i++)
			ftpRateOuts.remove(tmp.get(i));
		tmp.clear();
		for (Iterator it = domainNames.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry entry = (Map.Entry)it.next();
			Object key = entry.getKey();
			if (n.equals(entry.getValue())) 
				tmp.add(key);
		}
		for (int i=0; i<tmp.size(); i++)
			domainNames.remove(tmp.get(i));
	}
	
	/** set the current nodes */
	public synchronized void setNodes(Map<ServiceID, rcNode> hnodes) {
		
		this.hnodes = hnodes;
	}
	
	/** called when a new result appear */
	public void newFarmResult(MLSerClient client, Object res) {
		
		buffer.newFarmResult(client, res);
	}
	
	public void process(MLSerClient client, Object res) {
		
		if (res instanceof Result) {
			rcNode n = null;
			synchronized (this) {
				n = (rcNode)hnodes.get(client.tClientID);
			}
			if (n != null)
				process(n, (Result)res);
		} else if (res instanceof Vector) {
			Vector v = (Vector)res;
			for (int i=0; i<v.size(); i++)
				process(client, v.get(i));
		}
	}
	
	/** deal with the actual result */
	private synchronized void process(rcNode node, Result res) {
		
		if (res.param_name == null || res.param == null) return;
		if (res.param_name == null || res.param_name.length == 0) return;
		
		boolean topologyChanged = false;
		
		HashMap voSeries = (HashMap)ftpSeries.get(node);
		if(voSeries==null){
			voSeries = new HashMap();
		}
		
		HashMap series = (HashMap)voSeries.get(res.NodeName);
		if(series==null){
			series = new HashMap();
		}

		for (int i=0; i<res.param_name.length; i++) {
			
			if (res.param_name[i].startsWith("ftpInput_")) {
				String domain = res.param_name[i].substring(9);
				rcNode n = (rcNode)domainNames.get(domain);
				if (n != null && !currentNodes.contains(n)) n = null;
				if (n == null) {
					n = getDomainNode(domain);
					if (n != null) domainNames.put(domain, n);
				}
				if (n != null) {
					Vector v = null;
					if (ftpInputs.containsKey(node)) v = (Vector)ftpInputs.get(node);
					else {
						v = new Vector();
						ftpInputs.put(node, v);
					}
					for (int k=0; k<v.size(); k++) {
						Object[] obj = (Object[])v.get(k);
						if (obj[0].equals(res.NodeName) && obj[1].equals(domain)) {
							v.remove(k);
							k--;
						}
					}
					v.add(new Object[] { res.NodeName, domain, n, Double.valueOf(res.param[i]) });
					topologyChanged = true;
				}
			} else if (res.param_name[i].startsWith("ftpOutput_")) {
				String domain = res.param_name[i].substring(10);
				rcNode n = (rcNode)domainNames.get(domain);
				if (n != null && !currentNodes.contains(n)) n = null;
				if (n == null) {
					n = getDomainNode(domain);
					if (n != null) domainNames.put(domain, n);
				}
				if (n != null) {
					Vector v = null;
					if (ftpOutputs.containsKey(node)) v = (Vector)ftpOutputs.get(node);
					else {
						v = new Vector();
						ftpOutputs.put(node, v);
					}
					for (int k=0; k<v.size(); k++) {
						Object[] obj = (Object[])v.get(k);
						if (obj[0].equals(res.NodeName) && obj[1].equals(domain)) {
							v.remove(k);
							k--;
						}
					}
					v.add(new Object[] { res.NodeName, domain, n, Double.valueOf(res.param[i]) });
					topologyChanged = true;
				}
			} else if (res.param_name[i].startsWith("ftpRateIn_")) {
				String domain = res.param_name[i].substring(10);
				rcNode n = (rcNode)domainNames.get(domain);
				if (n != null && !currentNodes.contains(n)) n = null;
				if (n == null) {
					n = getDomainNode(domain);
					if (n != null) domainNames.put(domain, n);
				}
				if (n != null) {
					Vector v = null;
					if (ftpRateIns.containsKey(node)) v = (Vector)ftpRateIns.get(node);
					else {
						v = new Vector();
						ftpRateIns.put(node, v);
					}
					for (int k=0; k<v.size(); k++) {
						Object[] obj = (Object[])v.get(k);
						if (obj[0].equals(res.NodeName) && obj[1].equals(domain)) {
							v.remove(k);
							k--;
						}
					}
					v.add(new Object[] { res.NodeName, domain, n, Double.valueOf(res.param[i]) });
					ftpRateIns.put(node,v);
					
					MLSeries s = (MLSeries)series.get(domain+"_in");
					if(s==null){
						s = new MLSeries(res.NodeName+"_"+domain);
					}
					
					s.add(res.time, res.param[i]);
					osgC.updateSeries(s, -realtimeStart);
					series.put(domain+"_in", s);
					
					topologyChanged = true;
				}
			} else if (res.param_name[i].startsWith("ftpRateOut_")) {
				String domain = res.param_name[i].substring(11);
				rcNode n = (rcNode)domainNames.get(domain);
				if (n != null && !currentNodes.contains(n)) n = null;
				if (n == null) {
					n = getDomainNode(domain);
					if (n != null) domainNames.put(domain, n);
				}
				if (n != null) {
					Vector v = null;
					if (ftpRateOuts.containsKey(node)) v = (Vector)ftpRateOuts.get(node);
					else {
						v = new Vector();
						ftpRateOuts.put(node, v);
					}
					for (int k=0; k<v.size(); k++) {
						Object[] obj = (Object[])v.get(k);
						if (obj[0].equals(res.NodeName) && obj[1].equals(domain)) {
							v.remove(k);
							k--;
						}
					}
					v.add(new Object[] { res.NodeName, domain, n, Double.valueOf(res.param[i]) });
					ftpRateOuts.put(node,v);
					
					MLSeries s = (MLSeries)series.get(domain+"_out");
					if(s==null){
						s = new MLSeries(res.NodeName+"_"+domain);
					}
					
					s.add(res.time, res.param[i]);
					osgC.updateSeries(s, -realtimeStart);
					series.put(domain+"_out", s);
					
					topologyChanged = true;
				}
			}
		}
		voSeries.put(res.NodeName, series);
		ftpSeries.put(node,voSeries);
		if (topologyChanged) {
			owner.redoTopology();
		}
	}

	/** it's very important to have a method to select one of the farms that deals with a given domain */
	public rcNode getDomainNode(String domain) {

		String farmName = null;
		String domains = AppConfig.getProperty("lia.monitor.osg_domain_farms", "");
		if (domains != null && domains.length() != 0) {
			String dds[] = domains.split(",");
			if (dds != null) {
				for (int i=0; i<dds.length; i++) {
					String ds[] = dds[i].split(":");
					if (ds != null && ds.length > 1 && ds[0].equals(domain)) {
						farmName = ds[1];
						break;
					}
				}
			}
		}
		if (farmName != null && farmName.length() != 0) {
			for (int i=0; i<currentNodes.size(); i++) {
				rcNode n = (rcNode)currentNodes.get(i);
				if (n.client.getFarmName().equals(farmName)) return n;
			}
			return null;
		}

		TreeMap set = new TreeMap();
		
		for (int i=0; i<currentNodes.size(); i++) {
			rcNode n = (rcNode)currentNodes.get(i);
			String hostName = null;
			if (n != null && n.client != null)
				hostName = n.client.getHostName();
			if (hostName != null && hostName.length() != 0) {
				if (hostName.endsWith(domain)) {
					set.put(n.client.getFarmName()+"/"+hostName, n);
				}
			}
		}
		if (set.size() != 0)
			return (rcNode)set.get(set.firstKey());
		return null;
	}
	
	/** construct the actual graphtopology object */
	public synchronized GraphTopology constructTopology(byte type) {
		
		ExtendedGraphTopology gt = new ExtendedGraphTopology();
		
		// first add all the nodes
		for (int i=0; i<currentNodes.size(); i++) {
			rcNode n = (rcNode)currentNodes.get(i);
			gt.add(n);
			Vector res = null;
			Vector res1 = null;
			HashMap ss = (HashMap)ftpSeries.get(n);
			double ftpValue = 0.0;
			
			if (type == FTP_INPUT) 
				res = (Vector)ftpInputs.get(n);
			else if (type == FTP_OUTPUT)
				res = (Vector)ftpOutputs.get(n);
			else if (type == FTP_INPUT_RATE) 
				res = (Vector)ftpRateIns.get(n);
			else if (type == FTP_OUTPUT_RATE)
				res = (Vector)ftpRateOuts.get(n);
			else if (type == FTP_INOUT) {
				res = (Vector)ftpInputs.get(n);
				res1 = (Vector)ftpOutputs.get(n);
			} else if (type == FTP_INOUT_RATE) {
				res = (Vector)ftpRateIns.get(n);
				res1 = (Vector)ftpRateOuts.get(n);
			}
			if (res != null && res1 != null) {
				HashMap h = new HashMap();
				for (int k = 0; k<res.size(); k++) {
					Object[] p = (Object[])res.get(k);
					rcNode dest = (rcNode)p[2];
					MLSeries series = (MLSeries) ((HashMap)ss.get((String)p[0])).get((String)p[1]+"_in");
					if(type == FTP_INOUT)
						ftpValue = osgC.getOSGIntegral(series, 1, owner.historyTime);
					else if(type == FTP_INOUT_RATE)
						ftpValue = osgC.getOSGMean(series, owner.historyTime);
					//System.out.println((String)p[1]+"_in -> "+ftpValue);
					if (h.containsKey(dest)) {
						Object[] pp = (Object[])h.get(dest);
						double v = ((Double)pp[0]).doubleValue();
						v += ((Double)p[3]).doubleValue();
						h.put(dest, new Double[] { Double.valueOf(v), (Double)pp[1]});
					} else{
						//h.put(dest, new Double[] { (Double)p[3], Double.valueOf(0) });
						h.put(dest, new Double[] { Double.valueOf(ftpValue), Double.valueOf(0) });
					}
				}
				for (int k=0; k<res1.size(); k++) {
					Object[] p = (Object[])res1.get(k);
					rcNode dest = (rcNode)p[2];
					MLSeries series = (MLSeries) ((HashMap)ss.get((String)p[0])).get((String)p[1]+"_out");
					if(type == FTP_INOUT)
						ftpValue = osgC.getOSGIntegral(series, 1, owner.historyTime);
					else if(type == FTP_INOUT_RATE)
						ftpValue = osgC.getOSGMean(series, owner.historyTime);
					//System.out.println((String)p[1]+"_out -> "+ftpValue);
					if (h.containsKey(dest)) {
						Object[] pp = (Object[])h.get(dest);
						double v = ((Double)pp[1]).doubleValue();
						v += ((Double)p[3]).doubleValue();
						h.put(dest, new Double[] { (Double)pp[0], Double.valueOf(v) });
					} else{
						h.put(dest, new Double[] { Double.valueOf(0), Double.valueOf(ftpValue) });
						//h.put(dest, new Double[] { Double.valueOf(0), (Double)p[3] });
					}
				}
				for (Iterator it = h.entrySet().iterator(); it.hasNext(); ) {
					Map.Entry entry = (Map.Entry)it.next();
					rcNode dest = (rcNode)entry.getKey();
					Double[] pp = (Double[])entry.getValue();
					double avg = (pp[0].doubleValue()+pp[1].doubleValue())/2;
					if(pp[0].doubleValue()!=0.0 || pp[1].doubleValue()!=0.0)
						gt.add(n, dest, avg, OSGCanvas.df.format(pp[0].doubleValue())+"/"+OSGCanvas.df.format(pp[1].doubleValue()));
				}
			} else if (res != null) {
				HashMap h = new HashMap();
				for (int k=0; k<res.size(); k++) {
					Object[] p = (Object[])res.get(k);
					rcNode dest = (rcNode)p[2];
					MLSeries series = (MLSeries) ((HashMap)ss.get((String)p[0])).get((String)p[1]+"_in");
					if (series == null){
						series = (MLSeries) ((HashMap)ss.get((String)p[0])).get((String)p[1]+"_out");
					}
					if(type == FTP_INOUT)
						ftpValue = osgC.getOSGIntegral(series, 1, owner.historyTime);
					else if(type == FTP_INOUT_RATE)
						ftpValue = osgC.getOSGMean(series, owner.historyTime);
					//System.out.println((String)p[1]+" -> "+ftpValue);
					if (h.containsKey(dest)) {
						double v = ((Double)h.get(dest)).doubleValue();
						v += ((Double)p[3]).doubleValue();
						h.put(dest, Double.valueOf(v));
					} else {
						//h.put(dest, p[3]);
						h.put(dest, Double.valueOf(ftpValue));
					}
				}
				for (Iterator it = h.entrySet().iterator(); it.hasNext(); ) {
					Map.Entry entry = (Map.Entry)it.next();
					rcNode dest = (rcNode)entry.getKey();
					double v = ((Double)entry.getValue()).doubleValue();
					gt.add(n, dest, v);
				}
			}
		}
		return gt;
	}
	
	public String constructLinkHelper(rcNode n1, rcNode n2) {
		
		byte type = owner.ftpTransferType;
		Vector res = null;
		Vector res1 = null;
		if (type == FTP_INPUT) 
			res = (Vector)ftpInputs.get(n1);
		else if (type == FTP_OUTPUT)
			res = (Vector)ftpOutputs.get(n1);
		else if (type == FTP_INPUT_RATE) 
			res = (Vector)ftpRateIns.get(n1);
		else if (type == FTP_OUTPUT_RATE)
			res = (Vector)ftpRateOuts.get(n1);
		else if (type == FTP_INOUT) {
			res = (Vector)ftpInputs.get(n1);
			res1 = (Vector)ftpOutputs.get(n1);
		} else if (type == FTP_INOUT_RATE) {
			res = (Vector)ftpRateIns.get(n1);
			res1 = (Vector)ftpRateOuts.get(n1);
		}
		if (res != null && res1 != null) {
			HashMap h = new HashMap();
			String domain = "";
			for (int k=0; k<res.size(); k++) {
				Object[] p = (Object[])res.get(k);
				rcNode dest = (rcNode)p[2];
				if (dest.equals(n2)) {
					String vo = (String)p[0];
					domain = (String)p[1];
					double value = ((Double)p[3]).doubleValue();
					HashMap h1 = null;
					if (h.containsKey(vo))
						h1 = (HashMap)h.get(vo);
					else {
						h1 = new HashMap();
						h.put(vo, h1);
					}
					if (h1.containsKey(domain)) {
						Double[] dd = (Double[])h1.get(domain);
						value += dd[0].doubleValue();
						h1.put(domain, new Double[] { Double.valueOf(value), dd[1] } );
					} else 
						h1.put(domain, new Double[] { Double.valueOf(value), Double.valueOf(0) });
				}
			}
			for (int k=0; k<res1.size(); k++) {
				Object[] p = (Object[])res.get(k);
				rcNode dest = (rcNode)p[2];
				if (dest.equals(n2)) {
					String vo = (String)p[0];
					domain = (String)p[1];
					double value = ((Double)p[3]).doubleValue();
					HashMap h1 = null;
					if (h.containsKey(vo))
						h1 = (HashMap)h.get(vo);
					else {
						h1 = new HashMap();
						h.put(vo, h1);
					}
					if (h1.containsKey(domain)) {
						Double[] dd = (Double[])h1.get(domain);
						value += dd[1].doubleValue();
						h1.put(domain, new Double[] { dd[0], Double.valueOf(value) } );
					} else 
						h1.put(domain, new Double[] { Double.valueOf(0), Double.valueOf(value)});
				}
			}
			if (h.size() == 0) return null;
			StringBuilder buf = new StringBuilder();
			buf.append("<html><body bgcolor=\"#FFFFFF\">");
			buf.append("<div align=center><b>").append(domain).append("</b></div>");
			buf.append("<table BORDER CELLSPACING=0>");
			buf.append("<tr><td><b>VO</b></td><td><b>Type</b></td><td><b>Value</b></td></tr>");
			for (Iterator it1 = h.entrySet().iterator(); it1.hasNext(); ) {
				Map.Entry entry = (Map.Entry)it1.next();
				String vo = (String)entry.getKey();
				HashMap h1 = (HashMap)entry.getValue();
				for (Iterator it2 = h1.entrySet().iterator(); it2.hasNext(); ) {
					Map.Entry entry1 = (Map.Entry)it2.next();
					domain = (String)entry1.getKey();
					Double[] dd = (Double[])entry1.getValue();
					buf.append("<tr><td>").append(vo).append("</td>").append("<td>IN</td><td>");
					buf.append(OSGCanvas.df.format(dd[0])).append("</td></tr>");
					buf.append("<tr><td>").append(vo).append("</td>").append("<td>OUT</td><td>");
					buf.append(OSGCanvas.df.format(dd[1])).append("</td></tr>");
				}
			}
			buf.append("</table></body></html>");
			return buf.toString();
		} else if (res != null) {
			HashMap h = new HashMap();
			String domain = "";
			for (int k=0; k<res.size(); k++) {
				Object[] p = (Object[])res.get(k);
				rcNode dest = (rcNode)p[2];
				if (dest.equals(n2)) {
					String vo = (String)p[0];
					domain = (String)p[1];
					double value = ((Double)p[3]).doubleValue();
					HashMap h1 = null;
					if (h.containsKey(vo))
						h1 = (HashMap)h.get(vo);
					else {
						h1 = new HashMap();
						h.put(vo, h1);
					}
					double oldVal = 0;
					if (h1.containsKey(domain)) oldVal = ((Double)h1.get(domain)).doubleValue();
					value += oldVal;
					h1.put(domain, Double.valueOf(value));
				}
			}
			if (h.size() == 0) return null;
			StringBuilder buf = new StringBuilder();
			buf.append("<html><body bgcolor=\"#FFFFFF\">");
			buf.append("<div align=center><b>").append(domain).append("</b></div>");
			buf.append("<table BORDER CELLSPACING=0>");
			buf.append("<tr><td><b>VO</b></td><td><b>Value</b></td></tr>");
			for (Iterator it1 = h.entrySet().iterator(); it1.hasNext(); ) {
				Map.Entry entry = (Map.Entry)it1.next();
				String vo = (String)entry.getKey();
				HashMap h1 = (HashMap)entry.getValue();
				for (Iterator it2 = h1.entrySet().iterator(); it2.hasNext(); ) {
					Map.Entry entry1 = (Map.Entry)it2.next();
					domain = (String)entry1.getKey();
					double value = ((Double)entry1.getValue()).doubleValue();
					buf.append("<tr><td>").append(vo).append("</td><td>").append(OSGCanvas.df.format(value)).append("</td></tr>");
				}
			}
			buf.append("</table></body></html>");
			return buf.toString();
		}
		return null;
	}
	
} // end of class OSGFTPHelper

