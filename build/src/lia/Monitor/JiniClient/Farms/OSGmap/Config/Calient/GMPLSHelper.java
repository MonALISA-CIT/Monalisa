package lia.Monitor.JiniClient.Farms.OSGmap.Config.Calient;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;

/**
 * Utility class to keep variables that otherwise would fog the code.
 */
public class GMPLSHelper {

	public static final String modify = format("Click to modify the parameters.");

	public static final String npHelp = format("Each NP port must be configured with IP parameters.");
	public static final String eqptIDTT = format("This parameter specifies the card whose address is to be set.");
	public static final String ipTT = format("This parameter specifies the node's ip address.(<i>optional</i>)");
	public static final String maskTT = format("This parameter specifies the node's network mask. (<i>optional</i>)");
	public static final String gwTT = format("This parameter specifies the node's gateway address. (<i>optional</i>)");
	
	public static final String ospfHelp = format("Calient uses Open Shortest Path First (OSPF) for routing. An area is used in OSPF to group a set of network devices "+
			"together to exchange link-state topology and other optical resource availability information for path computation.");
	public static final String routerIDTT = format("This parameter specifies the router ID (<i>optional</i>).");
	public static final String areaIDTT = format("This parameter specifies the OSPF area to which the router belongs (<i>optional</i>).");
	
	public static final String rsvpHelp = format("Calient uses ReSerVation Protocol (RSVP) for signaling. RSVP automates the path establishment process.");
	public static final String msgRetryInvlTT = format("This parameter specifies the message retry interval attemtps"+
		" of the same RSVP message. The interval is specifies between 100 and 60,000 milliseconds (<i>optional</i>).");
	public static final String ntfRetryInvlTT = format("This parameter specifies the notification retry interval with which a notify"+
		" packet is sent out until a Notify ACK is received. The interval is specified between 500 (<i>default</i>) and 60,000 milliseconds.");
	public static final String grInvlTT = format("This parameter specifies the graceful interval to refresh the states "+
		"to the neighbor. The interval is specifies between 1 second and 47 days (<i>default</i>).");
	public static final String grcvInvlTT = format("This parameter specifies the graceful recovery interval to announce the time "+
		"after which neighbor nodes refresh state. The interval is between 60 to 360  seconds. Default is <i>90 seconds</i>.");
	
	public static final String ctrlHelp = format("The control channels use the IP parameters configured on the NP ports tou route IP packets. The Link Management Protocol "+
			"(LMP) is responsible for sending and receiving keep-alive messages (hello) across the control channel to ensure that the control channel is operational. <br> "+
			"The control channels provide the communications pathway for nodes in the network to discover each other in a redundant configuration. Once a control channel is "+
			"provisioned, the Hello protocol is started and hello packets are sent periodically on each control channel to the neighboring nodes. The hello packets are used to monitor "+
			"the health of control channels.");
	public static final String ctrlName = format("This parameter specifies the name of the control channel. The name consists of up to 35 alphanumeric characters, "+
			"including special characters such as periods (.) and underscores (_). No spaces allowed. For example, P1_P2_CC1. ctrlch must be specified.");
	public static final String ctrlList = format("Here you can see the current activated control channels.");
	public static final String ctrlAdd = format("Click to add a new control channel.");
	public static final String ctrlDel = format("Click to delete the current selected control channel (from the list above).");
	public static final String ctrlRemoteIP = format("This parameter specifies the IP address of the remote end (that is, the NP port) of the control channel. (<i>must be specifies</i>)");
	public static final String ctrlRemoteRid = format("This parameter specifies the remote peer router ID. (<i>must be specifies</i>)");
	public static final String ctrlEqpt = format("This parameter specifies the NP port with which the control channel associates. (<i>must be specifies</i>)");
	public static final String ctrlAdj = format("This parameter specifies the name of the adjacency with which the control channel associates (<i>optional</i>).");
	public static final String ctrlHello = format("This parameter specifies the time interval between the hello packets that are sent over the interface. The value is "+
			"expressed in milliseconds. Setting the interval to 0 effectively disables the LMP hello. Default is <i>500 ms</i>.");
	public static final String ctrlHelloMin = format("This parameter specifies the minimum hello interval. The minimum value is 250 and is expressed in milliseconds. "+
			"The default value is <i>300 ms</i>.");
	public static final String ctrlHelloMax = format("This parameter specifies the maximum hello interval. The maximum value is 65535 and is expressed in milliseconds. "+
			"The default value is <i>5000 ms</i>.");
	public static final String ctrlDeadInvl = format("This parameter specifies the length of time that the hello packets have not been seen before its neighbor declares the "+
			"interface down. The value is expressed in milliseconds. The default value <i>2,000 ms</i>.");
	public static final String ctrlDeadInvlMin = format("This parameter specifies the minimum dead interval. The minimum value is 750 and is expressed in milliseconds. "+
			"The default is <i>1,200 ms</i>.");
	public static final String ctrlDeadInvlMax = format("This parameter specifies the maximum dead interval. The maximum value is 65,535 and is expressed in "+
			"milliseconds. The default <i>20,000 ms</i>.");
	
	public static final String adjHelp = format("The neighbor adjacencies carry GMPLS and other management traffic between this switch and other elements in "+
			"the network. They are supported by a list of control channels that start and end between two nodes of the network. Once provisioned, neighbor adjacency "+
			"(that is, communications path) is established between two nodes.");
	public static final String adjList = format("Here you can see the current activated adjancecies.");
	public static final String adjAdd = format("Click to add a new adjancecy.");
	public static final String adjDel = format("Click to delete the current selected adjancecy (from the list above).");
	public static final String adjCtrls = format("Here you can see the current control channels selected as beeing served by this adjancency");
	public static final String adjAddCtrl = format("Click to add a new control channel.");
	public static final String adjDelCtrl = format("Click to delete the current control channel from this adjancency.");
	public static final String adjLocalRid = format("This parameter specified the local router IP address.");
	public static final String adjName = format("This parameter specifies the name of the adjacency. The name consists of up to 10 alphanumeric characters, including "+
			"special characters such as periods (.) and underscores (_). No spaces allowed. For example, P1_P2_TN. adj must be <i>specified</i>.");
	public static final String adjCtrlCh = format("This parameter specifies the name of the adjacency. The name consists of up to 10 alphanumeric characters, including "+
			"special characters such as periods (.) and underscores (_). No spaces allowed. For example, P1_P2_TN. <i>must be specified</i>.");
	public static final String adjRemoteRid = format("This parameter specifies the peer router IP address. <i>must be specified</i>.");
	public static final String adjOspfArea = format("This parameter specifies the peer router IP address. remoterid must be specified.<i>optional</i>");
	public static final String adjMetric = format("This parameter specifies the administrative cost (that is, number of hops) associated with the adjacency. Default is 1. "+
			"A high value (for example, 250) ensures that regular packets are not forwarded along the control plane. However, if there is no other route, the switch control "+
			"plane may be used for transmission of packets. <i>optional</i>.");
	public static final String adjOspfAdj = format("This parameter specifies the OSPF adjacency flag.");
	public static final String adjType = format("This parameter specifies the adjacency type.");
	public static final String adjIndex = format("This parameter specifies the index number of the current neighbor adjacency.");
	public static final String adjRsvpRR = format("This parameter specifies the refresh reduction flag.<i>optional</i>");
	public static final String adjRsvpGR = format("This parameter specifies the graceful restart flag.<i>optional</i>");
	public static final String adjNtfProc = format("This parameter specifies processing notification.");
	
	public static final String linkHelp = format("A Traffic Engineering (TE) link is a  logical  link that maps the information about certain physical resources and their "+
			"properties for the purpose of path computation. A collection of data links (ports) connect two nodes in a network. If some of a TE link.");
	public static final String linkName = format("This parameter specifies the name of the TE link. <i>must be specified</i>.");
	public static final String linkList = format("Here you can see the current activated TE Links.");
	public static final String linkAdd = format("Click to add a new TE Link.");
	public static final String linkDel = format("Click to delete the current selected TE Link (from the list above).");
	public static final String linkType = format("This parameter specifies the type of TE link. Options are: <ul> <li> Numbered means that both the local and remote "+
			"IP addresses are required. <li>Unnumbered means that Remote TE IF index is used instead of IP addresses.</ul><br> <i>must be specified</i>.");
	public static final String linkLocalIP = format("This parameter specifies the local IP address to which the TE link associates. localip must be specified for the "+
			"Numbered type but is optional for the Unnumbered type.");
	public static final String linkRemoteIP = format("This parameter specifies the remote IP address to which the TE link associates. remoteip must be specified for the "+
			"Numbered type but is optional for the Unnumbered type.");
	public static final String linkLocalRid = format("This parameter specified the local router IP address.");
	public static final String linkRemoteRid = format("This parameter specifies the remote router IP address.");
	public static final String linkAdj = format("This parameter specifies the name of the adjacency with which this link associates.<i>optional</i>");
	public static final String linkWdmAdj = format("This parameter specifies the name of the WDM adjacency where LMP non-hello packets for the TE Link are sent. <i>optional</i>");
	public static final String linkLocalIf = format("This parameter specifies the local TE interface index (that is, the local TE link IP address).");
	public static final String linkRemoteIf = format("This parameter specifies the remote TE interface index (that is, the remote TE link IP address). This parameter "+
			"must be specified for the Unnumbered type but is optional for the Numbered type.");
	public static final String linkWDMRemoteIf = format("This parameter specifies the WDM remote TE interface index.");
	public static final String linkFltDetect = format("This parameter specifies the LMP fault detection. When enabled, the LMP starts the fault isolation procedures.");
	public static final String linkMetric = format("This parameter specifies the administrative cost associated with the TE link. This value is used to compute paths in "+
			"the network. metric is optional. If not specified, the default value 1 is used.");
	public static final String linkLMPVerify = format("This parameter specifies the LMP verify interval. The value is expressed in milliseconds.<i>optional</i>");
	public static final String linkPort = format("This represent the port belonging to the TE link.");
	
	private static FontMetrics fm =null;
	private static final int length = 300;
	
	public static String format(String text) {
		
		if (fm == null) {
			fm = (new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)).createGraphics().getFontMetrics(new Font("Arial", Font.PLAIN, 12));
		}
		
		StringBuilder buf = new StringBuilder();
		buf.append("<html>");
		if (text != null && text.length() != 0) {
			String words[] = text.split(" ");
			StringBuilder row = new StringBuilder();
			for (int i=0; i<words.length; i++) {
				if (i != 0 && row.length() != 0)
					row.append(" ");
				row.append(words[i]);
				if (words[i].equals("<br>") || fm.stringWidth(row.toString()) > length) {
					buf.append("<br>");
					row = new StringBuilder();
					row.append(" ").append(words[i]);
				} else
					if (i != 0) buf.append(" ");
				buf.append(words[i]);
			}
		}
		buf.append("</html>");
		return buf.toString();
	}
	
} // end of class GMPLSHelper

