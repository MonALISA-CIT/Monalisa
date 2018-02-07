package lia.Monitor.JiniClient.VRVS3D.GlobePan;

import lia.Monitor.JiniClient.CommonGUI.NodeToolTipText;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.VRVS3D.JoptPan;

public class VrvsNodeToolTip extends NodeToolTipText {

	/**
	 * returns a long tooltip for this vrvs rcNode
	 * @param node the vrvs node
	 * @return the tooltip
	 */
	public static String getToolTip(rcNode node, JoptPan optPan, NodesGroup nodesGroup) {
		String text = node.UnitName;
		if(node.LAT != null) text += " : LAT " + (node.LAT.startsWith("-") ? (node.LAT.substring(1) + " S") : (node.LAT + " N"));
		if(node.LONG != null) text += ", LONG " + (node.LONG.startsWith("-") ? (node.LONG.substring(1) + " W") : (node.LONG + " E"));
		if(node.CITY != null) text += ", CITY " + node.CITY;
		if(node.IPaddress != null) text += ", IP " + node.IPaddress;
		double val = nodesGroup.getNodeValue(node);
		if(val != -1)
		  text += "  [ " + optPan.cbNodeOpts.getSelectedItem() + " = "
			   + optPan.csNodes.formatter.format(val) + " "
			   + optPan.csNodes.units + " ]";
		return text;
	}
	
	/**
	 * return a short tooltip for this vrvs rcNode
	 * @param node the vrvs node
	 * @return the short tooltip
	 */
	public static String getShortToolTip(rcNode node, JoptPan optPan, NodesGroup nodesGroup) {
		String text = node.UnitName;
		double val = nodesGroup.getNodeValue(node);
		if(val != -1)
		  text += "  [ " + optPan.csNodes.formatter.format(val)
			   + optPan.csNodes.units + "]";
		return text;
	}
}
