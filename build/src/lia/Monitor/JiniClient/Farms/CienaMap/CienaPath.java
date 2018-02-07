package lia.Monitor.JiniClient.Farms.CienaMap;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;

import lia.Monitor.ciena.circuits.topo.CDCICircuitsHolder;
import lia.Monitor.ciena.circuits.topo.CircuitsHolder;
import lia.Monitor.ciena.circuits.topo.XConn;

/**
 * Represents the graphical properties of a circuit path
 * @author cipsm
 *
 */
public class CienaPath {

	// the name of the circuit
	String sncName;
	
	String source;
	String destination;
	
	// the path (sw names)
	LinkedList path;

	private boolean pathCompleted = false;
	
	public int rate = 0;
	
	public class PathNode {
		public final String swName;
		public final String inPort;
		public final String outPort;
		public PathNode(String swName, String inPort, String outPort) {
			this.swName = swName;
			this.inPort = inPort;
			this.outPort = outPort;
		}
	}
	
	public CienaPath(String sncName, String source, String destination) {
		this.sncName = sncName;
		this.source = source;
		this.destination = destination;
		path = new LinkedList();
		pathCompleted = false;
	}
	
	public void setRate(int rate) {
		this.rate = rate;
	}
	
	public LinkedList getPath() { 
		return path;
	}
	
	public boolean pathCompleted() {
		return pathCompleted;
	}
	
	public boolean traverses(String sw1, String sw2) {
		if (path.size() < 2) return false;
		Iterator it1 = path.iterator();
		Iterator it2 = path.iterator();
		it2.next();
		while (it2.hasNext()) {
			String s1 = ((PathNode)it1.next()).swName;
			String s2 = ((PathNode)it2.next()).swName;
			if (s1.equals(sw1) && s2.equals(sw2)) return true;
		}
		return false;
	}
	
	private final CDCICircuitsHolder getCDCICircuitHolder(String name) {
		CircuitsHolder h = CircuitsHolder.getInstance();
		CDCICircuitsHolder hh[] = h.getAllCDCICircuits();
		if (hh == null) return null;
		for (int i=0; i<hh.length; i++) {
			if (hh[i].swName.equals(name)) return hh[i];
		}
		return null;
	}
	
	public void redoPath(Hashtable nodes) {
		path.clear();
		if (!nodes.containsKey(source)) return;
//		CircuitsHolder holder = CircuitsHolder.getInstance();
		CienaNode node = (CienaNode)nodes.get(source);
		String name = source;
		LinkedList candidates = new LinkedList();
		while (true) {
			CDCICircuitsHolder cdci = getCDCICircuitHolder(name);
			if (cdci == null) break;
			String input = null;
			String output = null;
			// try to find something about the current nodes, in and out ports...
			for (Iterator it = cdci.xconnMap.keySet().iterator(); it.hasNext(); ) {
				XConn conn = (XConn)cdci.xconnMap.get(it.next());
				if (conn.circuitName.equals(sncName)) { // we found the info
					input = conn.fromEndpoint;
					output = conn.toEndpoint;
					break;
				}
			}
			PathNode n = new PathNode(name, input, output);
			path.addLast(n);
			candidates.addLast(name);
			if (name.equals(destination)) {
				pathCompleted = true;
				break; // we reached the final point in out path..
			}
			// now try to find the next node to advance to....
			boolean found = false;
			for (Iterator it = node.osrpLtpsMap.keySet().iterator(); it.hasNext(); ) {
				CienaLTP link = (CienaLTP)node.osrpLtpsMap.get(it.next());
				if (link != null && link.rmtName != null && nodes.containsKey(link.rmtName) && !candidates.contains(link.rmtName)) {
					// check that link...
					CienaNode nr = (CienaNode)nodes.get(link.rmtName);
					if (link.rmtName.equals(destination)) {
						node = nr;
						name = nr.UnitName;
						found = true;
						break;
					}
					CDCICircuitsHolder cdcir = getCDCICircuitHolder(nr.UnitName);
					if (cdcir == null) continue;
					for (Iterator it1 = cdcir.xconnMap.keySet().iterator(); it1.hasNext(); ) {
						XConn conn = (XConn)cdcir.xconnMap.get(it1.next());
						if (conn.circuitName.equals(sncName)) { // we found the info, the next candidate
							node = nr;
							name = nr.UnitName;
							found = true;
							break;
						}
					}
				}
			}
			if (!found) break;
		}
	}
	
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append(sncName).append(" ");
//		buf.append(source).append(" ");
		for (Iterator it = path.iterator(); it.hasNext(); ) {
			PathNode n = (PathNode)it.next();
			buf.append(" [").append(n.swName);
			if (n.inPort != null || n.outPort != null) {
				buf.append(" (");
				if (n.inPort != null) {
					buf.append(n.inPort);
					if (n.outPort != null) buf.append(",");
				}
				if (n.outPort != null) buf.append(n.outPort);
				buf.append(")");
			}
			buf.append("]");
		}
		buf.append(" rate=").append(rate);
		return buf.toString();
	}
	
} // end of class CienaPath


