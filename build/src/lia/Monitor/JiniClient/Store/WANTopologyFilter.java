/**
 * Aug 3, 2006 - 6:16:22 PM
 * mluc - MonALISA1
 * 
 * v1.0 - draws a map of nodes and links using a radial graph algorithm
 * release: 7 august 06
 * ToDo: 
 * 	- draw switches as rectangular entities
 *  - draw more than one link between two nodes
 *  - order nodes so that they are always drawn together, the same for switches and routers
 *  - create map of nodes and links to be able to show history information
 *  - set link's length according to types of nodes that it connects
 *  - set a maximal value corresponding to the maximal color based on the measured values,
 *  not on the declared capacity of links
 *  - the same for minimum
 *  - draw legend
 * v1.01 - updates:
 * 	- change to png to have no compression loss, image is at least three times bigger
 *  - change switch image to a round rectangle area
 *  - updated shadows for all network devices after a long struggle
 *  - sets a maximal value corresponding to the maximal color based on the measured values,
 *  not on the declared capacity of links
 *  - the same for minimum
 *  - draw several links between two nodes, first as a straight link and the others curved
 * release: 14 august 06
 * v1.02
 */
package lia.Monitor.JiniClient.Store;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

import lia.Monitor.JiniClient.CommonGUI.Sphere.SphereTexture;
import lia.Monitor.monitor.ExtendedResult;
import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;
import lia.web.utils.Formatare;

import org.jfree.chart.ChartUtilities;

/**
 * @author mluc
 *
 */
public class WANTopologyFilter implements Filter {

//	public static String sWANsIdentifier = "WAN_";
//	public static String sLANsIdentifier = "LAN_";
//	public static String sHOSTsIdentifier = "_HOSTS";

	public WANTopologyFilter() {
		doTimer();
	}
	
	public WANTopologyFilter(boolean bNothing) {
		
	}

	public void doTimer() {
		new Timer().schedule(new TimerTask() {
			public void run() {
				try {
					long lStart = NTPDate.currentTimeMillis();
					generateGraph(mygraph, 500, 500);
					long lEnd = NTPDate.currentTimeMillis();
					System.out.println("[WANTopologyFilter] Generating graph took "+(lEnd-lStart)+" miliseconds");
				} catch (Exception ex) {
					System.out.println("[WANTopologyFilter] Generating graph exception ");
					ex.printStackTrace();
				}
			}
		}, 60000, 10000);
	}

	public synchronized final Object filterData(final Object o) {
		if (o instanceof Result || o instanceof ExtendedResult)
			filterResult((Result) o);
		else if (o instanceof Collection) {
			Collection c = (Collection) o;
			Iterator it = c.iterator();
			Object ot;

			while (it.hasNext()) {
				ot = it.next();

				if (ot instanceof Result || ot instanceof ExtendedResult)
					filterResult((Result) ot);
			}
		}
		//will not store anything for the moment
		//generate the map

		return null;
	}

	MyGraph mygraph = new MyGraph();

	private static class MyGraph {

		public class graphLink {
			graphNode source;
			graphNode destination;
			/** some links have name */
			String name;
			/** value from source to destination */
			double value_IN;
			/** value from destination to source */
			double value_OUT;
			/** capacity of link expressed in Mbps */
			public double speed;
			private graphLink() {}
			public graphLink( graphNode src, graphNode dest) {
				source = src;
				destination = dest;
				value_IN = 0;
				value_OUT = 0;
			}
			public boolean equals( String src, String dest, String n) {
				if ( source.equals(src) && destination.equals(dest) && (name!=null && n!=null && name.equals(n) || name==null && n==null ))
					return true;
				return false;
			}
		}
		public class graphNode {
			String name;
			int type;
			graphNode parent;
			public graphNode(String n, String t, graphNode p) {
				name = n;
				type = getType(t);
				parent = p;
			}
			public boolean equals(Object obj) {
				if ( obj == null )
					return false;
				if ( name.equals(obj.toString()) )
					return true;
				return false;
			}
			
			public String toString() {
				return name;
			}
            @Override
            public int hashCode() {
                return name.hashCode();
            }
		}
		public graphNode findNode(String name) {
			if ( name == null )
				return null;
			for ( int i=0; i<nodes.size(); i++)
				if ( nodes.get(i).equals(name) )
					return (graphNode)nodes.get(i);
			return null;
		}
		public graphLink findLink(String src, String dest, String n) {
			for ( int i=0; i<links.size(); i++) {
				graphLink link = (graphLink)links.get(i);
				if ( link.equals(src,dest, n) )
					return link;
			}
			return null;
		}
		/** contains node name and node type: router, switch, host */
		ArrayList nodes=new ArrayList();
		/** contains link source->destination and value */
		ArrayList links = new ArrayList();
		public void updateNode( String name, String type, String parent) {
			graphNode node = findNode(name);
			if ( node==null ) {
				graphNode p = findNode(parent);
				node = new graphNode(name, type, p);
//				if ( p!=null )
//				if ( getType(type)==NODE_SWITCH )
//				p.lansCount++;
//				else if ( getType(type)==NODE_HOST )
//				p.hostsCount++;
				nodes.add(node);
			}
		}

		public void updateLink( String source, String destination, String linkName, int what, double value) {
			graphLink link = findLink(source, destination, linkName);
			if ( link == null ) {
				link = new graphLink(findNode(source), findNode(destination));
				links.add(link);
			}
			link.name = linkName;
			if (what == LINK_VALUE_IN )
				link.value_IN = value;
			else if ( what == LINK_VALUE_OUT )
				link.value_OUT = value;
			else if ( what == LINK_SPEED )
				link.speed = value;
		}

		public static final int NODE_ROUTER=3;
		public static final int NODE_SWITCH=2;
		public static final int NODE_HOST=1;
		public static final int LINK_VALUE_IN=1;
		public static final int LINK_VALUE_OUT=2;
		public static final int LINK_SPEED=3;
		public int getType(String type) {
//			String sType1 = nodes.get(nodeName).toString();
			int nType1 = (type.equals("router")?3:type.equals("switch")?2:1);
			return nType1;
		}
	}

	/**
	 * searches for wan,lan and host information in result and
	 * adds them to the graph and regenerates the image...
	 * @author mluc
	 * @since Aug 3, 2006
	 * @param r
	 */
	private void filterResult(final Result r) {
		//first find the network device name from cluster name
		Pattern p = Pattern.compile("(LAN|HOST|WAN)s_on_(.*)");
//		System.out.println("[WANTopologyFilter] Cluster name for new result is "+r.ClusterName);
		Matcher m = p.matcher(r.ClusterName);
		if ( m.matches() ) {
			String devIP = m.group(2);
			String destType = m.group(1);
			String linkName = "";
			String destName = r.NodeName;
			System.out.println("[WANTopologyFilter] [debug] Processing result for router "+devIP+" with destination type "+destType);
			//parse the parameters received to gather data: there should be data for two links: IN and OUT
			//be sure to have the nodes
			mygraph.updateNode(devIP, "router", null);
			if ( destType.equals("LAN") )
				mygraph.updateNode(destName, "switch", devIP);
			else if ( destType.equals("HOST") )
				mygraph.updateNode(destName, "host", devIP);
			else if ( destType.equals("WAN") ) {
				//get link and router name
				int nLastUnderscore = -1;
//				String routerName="";
				if ( (nLastUnderscore=destName.lastIndexOf('_'))!=-1 ) {
					linkName = destName.substring(0, nLastUnderscore);
					destName = destName.substring(nLastUnderscore+1);
				} else {
					linkName = destName;
					destName = ViewNodeInfo.PREFIX_UNKNOWN_ROUTER+devIP;
				}
				//the nodename should respect this format: <link name>_<router name/ip>
				mygraph.updateNode(destName, "router", devIP);
			};
			//get the data and update the links
			for ( int i=0; i<r.param_name.length; i++) {
				if ( r.param_name[i].equals("IN")) {
					mygraph.updateLink( devIP, destName, linkName, MyGraph.LINK_VALUE_IN, r.param[i]);
				} else if ( r.param_name[i].equals("OUT")) {
					//from host/lan/wan to router 
					mygraph.updateLink( devIP, destName, linkName, MyGraph.LINK_VALUE_OUT, r.param[i]);
				} else if ( r.param_name[i].equals("SPEED")) {
					//the speed parameter is the same for both links
					mygraph.updateLink( devIP, destName, linkName, MyGraph.LINK_SPEED, r.param[i]);
				}
			}
		}
	}

	/**
	 * graph node class for visualisation
	 * @author mluc
	 *
	 */
	private static class ViewNodeInfo implements Comparable {
		public final static String PREFIX_UNKNOWN_ROUTER = "from ";
		/** real type of node */
		int type;
		/** real name of node */
		String name;
		//view info
		int posX;
		int posY;
		/** radius of circle that contains the information to be drawn for this node */
		int radius;
		double radialPosX, radialPosY;
		/** links that enter or leave this node */
		int countLinks;
		/** list of children in radial graph algorithm */
		ArrayList children;
		/** parent node in radial graph algorithm */
		ViewNodeInfo parent;
		/** recommended link length for children */
		int recLinkLength;
		/** current angle for the current child nod of each node */
		float curAngleHosts;
		/** angle between two children */
		float delAngleHosts;
		/** current angle for the current child nod of each node */
		float curAngleLans;
		/** angle between two children */
		float delAngleLans;
		/** weight of node; higher value, greater importance */
		public float weight=0;
		/** the virtual parent as determined by the radial algorithm */
		ViewNodeInfo viewParent;
		public void clone(MyGraph.graphNode node) {
			ViewNodeInfo vnode = this;
			vnode.type = node.type;
			vnode.name = node.name;
			weight = 0;
			posX = posY = 0;
			radius = 40;
			countLinks = 0;
			children = new ArrayList();
			radialPosX =0; radialPosY = 0;
		}
		public ViewNodeInfo(MyGraph.graphNode node) {
			this.clone(node);
		}
		//TODO: this is not a good way of defining the node because several 
		//nodes can have the same name, another way should be found
		public int compareTo(Object arg0) {
			return this.toString().compareTo(arg0.toString());
		}
		public String toString() {
			return name;
		}
		/**
		 * @author mluc
		 * @since Aug 7, 2006
		 * @return
		 */
		public boolean hasHostChildren() {
			for ( int i=0; i<children.size(); i++)
				if ( ((ViewNodeInfo)children.get(i)).type==MyGraph.NODE_HOST )
					return true;
			return false;
		}
	}
	/** contains informations for links related to graphical drawing */
	private static class ViewLinkInfo {
		/** weight of link; higher value, greater importance of link */
		public float weight=-1;
		public static final float FACTOR_HOST = 1;
		public static final float FACTOR_SWITCH = 2;
		public static final float FACTOR_ROUTER = 3;
		/** 
		 * if there are several links on the same two nodes, the weight of each
		 * link will be considered as an factor lower than one of the computed
		 * weight if there were only one link
		 * not needed any more, see {@link #computeFactorMultiLinks(int)}
		 */
		public static final float FACTOR_MULTI_LINKS = 0.6f;
		/** 
		 * sets the links selected for MST; selects only one as valid in MST
		 * from a pair of links (=two links that have same connecting nodes)
		 */
		boolean selectedMST = false;
		/** the source view node */
		ViewNodeInfo viewSrc;
		/** the destination view node */
		ViewNodeInfo viewDst;
		/** data for link: speed===capacity */
		double speed;
		/** data for link: value IN (from source to destination)*/
		double value_IN;
		/** data for link: value */
		double value_OUT;
		/** name of link if the case (usually only for WAN links) */
		String name;
		/** 
		 * if there are several links between two nodes, it is usefull to have
		 * a way of differentiating them so that they can be drawn
		 */
		int order=1;
		/** 
		 * copies only the information related to link, not to nodes
		 * @author mluc
		 * @since Aug 6, 2006
		 * @param l real link
		 */
		public void clone( MyGraph.graphLink l) {
			speed = l.speed;
			value_IN = l.value_IN;
			value_OUT = l.value_OUT;
			name = l.name;
			weight = -1;
		}
		public ViewLinkInfo( MyGraph.graphLink l, ViewNodeInfo src, ViewNodeInfo dst) {
			clone(l);
			viewSrc = src;
			viewDst = dst;
		}
		public String toString() {
			return (name!=null?"link "+name+" ":"")+"["+viewSrc+" <-> "+viewDst+"]";
		}
		public String toString(boolean bIN) {
			if ( bIN )
				return (name!=null?"link "+name+" ":"")+"["+viewSrc+" -> "+viewDst+"]";
			else
				return (name!=null?"link "+name+" ":"")+"["+viewDst+" -> "+viewSrc+"]";
		}
		/**
		 * computes a factor based on the logarithmical function to apply to
		 * the width of the link to make a difference between one or two links
		 * or more between the same two nodes. If the number of links increases,
		 * the factor increases more slowly
		 * @author mluc
		 * @since Aug 9, 2006
		 * @param count
		 * @return
		 */
		public float computeFactorMultiLinks(int count) {
			return 1+(float)Math.log(count);
		}
	}

	/**
	 * creats a copy of the graph's nodes and link to view nodes and links
	 * so that the modification of graph doesn't affect the visualisation
	 * That's better than to use a hash to relate real nodes to visual information
	 * for them.
	 * TODO: using clone functions, another function can be made to only update the
	 * missing information in visualisation graph.
	 * @author mluc
	 * @since Aug 6, 2006
	 * @param nodesSrc original graph's nodes
	 * @param linksSrc original graph's links
	 * @param nodesDst visualisation node that will contain produced data, not obtained
	 * @param linksDst visualisation link that will contain produced data, not obtained
	 */
	private void copyGraph(ArrayList nodesSrc, ArrayList linksSrc, ArrayList nodesDst, ArrayList linksDst) {
		if ( nodesSrc==null || linksSrc==null || nodesDst==null || linksDst==null )
			return;
		nodesDst.clear();
		linksDst.clear();
		//creates view nodes based on real nodes
		for ( int i=0; i<nodesSrc.size(); i++) {
			ViewNodeInfo vnode = new ViewNodeInfo((MyGraph.graphNode)nodesSrc.get(i));
			nodesDst.add(vnode);
		}
		//creates view links, not forgetting to reffer to view nodes, not real nodes
		for ( int i=0; i<linksSrc.size(); i++) {
			MyGraph.graphLink l = (MyGraph.graphLink)linksSrc.get(i);
			//identify the real node, and then, based on index, select the view node
			int index = nodesSrc.indexOf(l.source);
			ViewNodeInfo vnSrc = null;
			if ( index>=0 && index<nodesDst.size() )
				vnSrc = (ViewNodeInfo)nodesDst.get(index);
			index = nodesSrc.indexOf(l.destination);
			ViewNodeInfo vnDst = null;
			if ( index>=0 && index<nodesDst.size() )
				vnDst = (ViewNodeInfo)nodesDst.get(index);
			ViewLinkInfo vlink = new ViewLinkInfo( l, vnSrc, vnDst);
			linksDst.add(vlink);
		}
	}

	/**
	 * counts all links that enter or leave each view node.
	 * it compares the provided node with both ends of a link using "==" (address)
	 * @author mluc
	 * @since Aug 6, 2006
	 * @param nodes
	 * @param links
	 */
	public void countLinks(ArrayList nodes, ArrayList links) {
		for ( int i=0; i<links.size(); i++ ) {
			ViewLinkInfo l = ((ViewLinkInfo)links.get(i));
			//for each link, increment the count for the two nodes that compose it
			if ( l.viewSrc!=null )
				l.viewSrc.countLinks++;
			if ( l.viewDst!=null )
				l.viewDst.countLinks++;
		}
	}

	public int findMaxRadius(ViewNodeInfo root, int level) {
		int maxRadius = level;
		for ( int i=0; i<root.children.size(); i++) {
			int radius = findMaxRadius( (ViewNodeInfo)root.children.get(i), level+1);
			if(radius > maxRadius)
				maxRadius = radius;
		};
		return maxRadius;
	}

	/** build the tree containing RadialGraphNodes and compute maxRadius. */
	public int buildRadialTree(ViewNodeInfo root, ArrayList links){
		//stack of nodes to be processed starting with root
		ArrayList stack = new ArrayList();
		stack.add(root);
		//current position in stack
		int index=0;
		while ( index < stack.size() ) {
			//for each node in stack, set its children
			ViewNodeInfo curNode = (ViewNodeInfo)stack.get(index);
			index++;
			for ( int i=0; i<links.size(); i++ ) {
				ViewLinkInfo l = ((ViewLinkInfo)links.get(i));
				if ( !l.selectedMST )
					continue;
				if ( l.viewSrc == curNode || l.viewDst == curNode ) {
					ViewNodeInfo other_node = l.viewSrc;
					if ( other_node==curNode )
						other_node = l.viewDst;
					//if a valid mst tree link is found, and the node at the other end
					//is valid, then add it to the list of children of root
					//only if not already treated, although, being a MST it should
					//not have been treated
					if ( other_node!=null && !stack.contains(other_node) ) {
						other_node.parent = curNode;
						curNode.children.add(other_node);
						stack.add(other_node);
					}
				}
			};
		}
		return findMaxRadius(root, 0);
	}

	/** assign the position for each node within a -1,1 maximum square */
	void assignPositions(ViewNodeInfo root, double x, double y, double radius, double minAngle, double maxAngle) {
		root.radialPosX = x;
		root.radialPosY = y;
		double sumWeight = 0;
		for(int i=0; i<root.children.size(); i++){
			ViewNodeInfo rgn = (ViewNodeInfo) root.children.get(i);
			sumWeight += rgn.weight;
		}
		double delayAngle = maxAngle - minAngle;
		double crtAngle = minAngle;
		for(int i=0; i<root.children.size(); i++){
			ViewNodeInfo rgn = (ViewNodeInfo) root.children.get(i);
			double thisAngle = rgn.weight / sumWeight * delayAngle;
			double nextAngle = crtAngle + thisAngle;
			double angle = (crtAngle + nextAngle) / 2.0;
			double nextx = x + radius * Math.cos(angle);
			double nexty = y + radius * Math.sin(angle);
			double nextMinA = angle - Math.PI + Math.PI/6.0;
			double nextMaxA = angle + Math.PI - Math.PI/6.0;
			assignPositions(rgn, nextx, nexty, radius, nextMinA, nextMaxA);
			crtAngle = nextAngle;
		}
	}

	public static class Drawing {
		Graphics2D g;
		Color color;
		float stroke;
		public final int Radius = 20;
		public int maxNodeWidth;
		public int maxNodeHeight;

		public Drawing(Graphics2D g) {
			this.g = g;

//			createShadow(Radius*2, Radius*2);
//			createBump(Radius*2, Radius*2);
//			createRectBump(Radius*2, Radius*2, 4, 4, (Radius-4)*2, (Radius-4)*2);
			createRouter();
			createSwitch();
			createLan();
			createHost();
			
			maxNodeWidth = Radius*2;
			if ( lanWidth> maxNodeWidth )
				maxNodeWidth = lanWidth;
			maxNodeHeight = Radius*2;
			if ( lanHeight> maxNodeHeight )
				maxNodeHeight = lanHeight;
		}
		/**
		 * constructs tooltip for the provided link.
		 * MUST be called after the drawing of the link
		 * @author mluc
		 * @since Aug 7, 2006
		 * @param link
		 * @return
		 */
		public String tooltip( ViewLinkInfo link) {
			if ( link.viewSrc==null || link.viewDst==null )
				return "";

			String sHREF = "";
			String sCaption = "";
			String sHistory = "";
//			sHREF = WWmap1.formatLabel(sHREF, link, null, null);

			StringBuilder sResult=new StringBuilder();
			sResult.append("<area shape=\"poly\" href=\""+sHREF+"\" coords=\"");
//			for (int j=0; j<link.vMap.size(); j++){
//			sbMap.append(link.vMap.get(j).toString()+",");
//			}
			sResult.append("\" ");
			sCaption = Formatare.tagProcess(sCaption);
			sResult.append("onmouseover=\"return overlib('<iframe src=\'"+sHistory+"\' border=0 width=330 height=220 scrolling=no></iframe>', CAPTION, '"+sCaption+"',CENTER, WIDTH, 330, HAUTO, VAUTO );\" onmouseout=\"return nd();\"");
			sResult.append("/>");
			return sResult.toString();
		}

		double log_max_val=0;
		double log_min_val=0;
		/**
		 * sets the minimal and maximal values for data on the links
		 * @author mluc
		 * @since Aug 10, 2006
		 * @param max_val
		 * @param min_val
		 */
		public void setMaxMinVal(double max_val, double min_val) {
			log_max_val = Math.log(max_val)/log10;
			if ( min_val<1 )
				min_val = 1;
			log_min_val = Math.log(min_val)/log10;
		}
		private static final double log10 = Math.log(10);
		/**
		 * computes visual parameters for a link based on measured values
		 * @author mluc
		 * @since Aug 10, 2006
		 * @param val
		 * @param cap
		 */
		private void computeParams( double cap, double val) {
			if ( cap> 9000 )
				stroke = 6;
			else if ( cap > 900 )
				stroke = 4;
			else stroke = 2;
			//compute color based on value of link
			//for color scale is used a logarithmical scale with 10 as base
			double log_val = Math.log(val);
			log_val /= log10;
			if ( log_val<log_min_val )
				log_val = log_min_val; //l.value is less than 1
			if ( log_val>log_max_val )
				log_val = log_max_val;
			int r_start = 102, g_start = 204, b_start = 255;
			int r_end = 1, g_end = 3, b_end = 204;
			int r, g, b;
			double aux = 0;
			double diff = (log_max_val-log_min_val);
			if ( diff>0 )
				aux = (log_val-log_min_val)/diff;
			//max val should be log10(10000) = 4
			r = r_start+(int)((r_end-r_start)*aux);
			r = (r<0?0:r>255?255:r);
			g = g_start+(int)((g_end-g_start)*aux);
			g = (g<0?0:g>255?255:g);
			b = b_start+(int)((b_end-b_start)*aux);
			b = (b<0?0:b>255?255:b);
			color = new Color(r,g,b);
//			System.out.println("[WANTopologyFilter] [debug] value is "+val+" color is "+color);
		}

		public void draw( ViewLinkInfo link) {
			if ( link.viewSrc==null || link.viewDst==null )
				return;
			//compute type of link, if router-router, then show name
			boolean bShowLinkName = false;
			if ( link.viewSrc.type==MyGraph.NODE_ROUTER && link.viewDst.type==MyGraph.NODE_ROUTER )
				bShowLinkName = true;
			
			int dd = 6;//deplacement from the center of the link to a parallel line
			int linkDeviation=40*(link.order/2);

			boolean bDrawLabel = false;
			//draw the link name only for one of the two links, alternatively
			if ( bShowLinkName )
				if ( link.order%2 != 0 )
					bDrawLabel = true;
				else
					bDrawLabel = false;

			//for src->dst link
			int klong1 = link.viewSrc.posX;
			int klong2 = link.viewDst.posX;
			int klat1 = link.viewSrc.posY;
			int klat2 = link.viewDst.posY;

			computeParams(link.speed, link.value_IN);
			Color darker = color.darker().darker();
			if ( link.order==1 ) {
				drawLink(klat1+1, klong1+1, klat2+1, klong2+1, darker, stroke, link.name/*link.toString(true)*/, dd, false, false, true);
				drawLink(klat1+1, klong1-1, klat2+1, klong2-1, darker, stroke, link.name, dd, false, false, true);
				drawLink(klat1, klong1, klat2, klong2, color, stroke, link.name, dd, true, bDrawLabel, true);
			} else {
				boolean bChoosePlusSolution = (link.order%2==0?true:false);
				drawArcLink( klong1+1, klat1+1, klong2+1, klat2+1, linkDeviation, bChoosePlusSolution, darker, stroke, link.name/*link.toString(true)*/, dd, false, false);
				drawArcLink( klong1-1, klat1+1, klong2-1, klat2+1, linkDeviation, bChoosePlusSolution, darker, stroke, link.name, dd, false, false);
				drawArcLink(klong1, klat1, klong2, klat2, linkDeviation, bChoosePlusSolution, color, stroke, link.name, dd, true, bDrawLabel);
			}

			if ( bShowLinkName )
				if ( link.order%2 == 0 )
					bDrawLabel = true;
				else
					bDrawLabel = false;

			//for dst->src link
			klong1 = link.viewDst.posX;
			klong2 = link.viewSrc.posX;
			klat1 = link.viewDst.posY;
			klat2 = link.viewSrc.posY;

			computeParams(link.speed, link.value_OUT);
			darker = color.darker().darker();
			if ( link.order==1 ) {
				drawLink(klat1+1, klong1+1, klat2+1, klong2+1, darker, stroke, link.name/*toString(false)*/, dd, false, false, true);
				drawLink(klat1+1, klong1-1, klat2+1, klong2-1, darker, stroke, link.name/*toString(false)*/, dd, false, false, true);
				drawLink(klat1, klong1, klat2, klong2, color, stroke, link.name/*toString(false)*/, dd, true, bShowLinkName, true);
			} else {
				boolean bChoosePlusSolution = (link.order%2==0?true:false);
				drawArcLink( klong1+1, klat1+1, klong2+1, klat2+1, linkDeviation, bChoosePlusSolution, darker, stroke, link.name/*toString(false)*/, dd, false, false);
				drawArcLink( klong1-1, klat1+1, klong2-1, klat2+1, linkDeviation, bChoosePlusSolution, darker, stroke, link.name, dd, false, false);
				drawArcLink(klong1, klat1, klong2, klat2, linkDeviation, bChoosePlusSolution, color, stroke, link.name, dd, true, bDrawLabel);
			}
		}
		
		private ArrayList drawArcLink(int x2, int y2, int x1, int y1, int linkDeviation, boolean bChoosePlusSolution, Color linkColor, float linkStroke, String label, int dd, boolean bSavePoints, boolean bDrawLabel) {
			//function parameters
//			int x1, y1, x2, y2;
//			int linkDeviation;//dR
//			boolean bChoosePlusSolution;
			//init... TO BE REMOVED
//			x1=y1=x2=y2=linkDeviation=0;
//			bChoosePlusSolution = true;
			//end function parameters
			ArrayList al = new ArrayList();
			//first computes circle data: xc, yc and Radius
			//compute angle between the two points
//			double angle;
			double R, xc, yc;
			int dR = linkDeviation;
			/** lenght of line connecting the two points */
			double base;
			base = Math.sqrt((x2-x1)*(x2-x1)+(y2-y1)*(y2-y1));
			double halfbase;
			halfbase = base*.5;
			R = ((double)(halfbase*halfbase+dR*dR))*.5/dR;
			/** cosinus of angle/2 (cos half angle)*/
//			double cosha = (R-dR)/R;
//			double angle_virt = Math.acos(cosha)*2;
			/** x and y coordinates of center of the shortest line connecting the two initial points */
//			double xM, yM;
//			xM = (double)(x1+x2)*.5;
//			yM = (double)(y1+y2)*.5;
			/** fraction of xc that is yc */
			double f = (double)(x2-x1)/(double)(y2-y1);
			/** yc = A - f*xc */
			double A = (double)(x2*x2-x1*x1+y2*y2-y1*y1)*0.5/(double)(y2-y1);//(y1+y2)*.5+(x2-x1)*(x2-x1)*.5/(y2-y1);
			double a, b, c;
			a = 1+f*f;
			b = (x1+f*(A-y1));
			c = x1*x1+(A-y1)*(A-y1)-R*R;
//			System.out.println("[WANTopologyFilter] [debug] delta=b^2-ac="+(b*b-a*c));
//			double xplus, xminus;
//			xplus = (b+Math.sqrt(b*b-a*c))/a;
//			xminus = (b-Math.sqrt(b*b-a*c))/a;
//			double yplus, yminus;
//			yplus = A - f*xplus;
//			yminus = A - f*xminus;
//			System.out.println("[WANTopologyFilter] [debug] [xplus,yplus]==("+xplus+","+yplus+")");
//			System.out.println("[WANTopologyFilter] [debug] [xminus,yminus]==("+xminus+","+yminus+")");
			if (bChoosePlusSolution )
				xc = (b+Math.sqrt(b*b-a*c))/a;
			else
				xc = (b-Math.sqrt(b*b-a*c))/a;
			yc = A-f*xc;
			//compute angles of each point (start and end) to x axis relative to center
			//compute the should be sin and cos
			double sin1 = (y1-yc)/R;
			double cos1 = (x1-xc)/R;
//			double angle1 = computeAngle(sin1, cos1);
			double sin2 = (y2-yc)/R;
			double cos2 = (x2-xc)/R;
//			double angle2 = computeAngle(sin2, cos2);
			//now find the real angle
			double angle;// = angle1-angle2;
			//another way to compute the angle:
			//sin(a) = sin(a1-a2) = sin(a1)cos(a2) - sin(a2)cos(a1) = sin1*cos2 - sin2*cos1
			//cos(a) = cos(a1-a2) = cos(a1)cos(a2) + sin(a1)sin(a2) = cos1*cos2 + sin1*sin2
			double sina = sin2*cos1 - sin1*cos2;
			double cosa = cos1*cos2 + sin1*sin2;
			double alt_angle = computeAngle(sina, cosa);
			angle = alt_angle;
//			if ( bDebug ) System.out.println("[WANTopologyFilter] [debug] alternative angle="+alt_angle+" "+(alt_angle*180/Math.PI));
//			if ( bDebug ) System.out.println("[WANTopologyFilter] [debug] Radius="+R+" angle="+angle+" "+(angle*180/Math.PI)+" angle1="+(angle1*180/Math.PI)+" angle2="+(angle2*180/Math.PI));
//			if ( bDebug ) System.out.println("[WANTopologyFilter] [debug] [xc,yc]=("+xc+","+yc+")");
//			System.out.println("[WANTopologyFilter] [debug] [x1,y1]=("+x1+","+y1+")");
//			System.out.println("[WANTopologyFilter] [debug] [x2,y2]=("+x2+","+y2+")");
//			if ( bDebug ) System.out.println("[WANTopologyFilter] [debug] [x,y]=("+x1+","+y1+")");
//			if ( bDebug ) System.out.println("[WANTopologyFilter] [debug] [x,y]=("+x2+","+y2+")");
			//compute small angle between the points that will represent the link
			//the minimum number of points is 5
			//the minimum degreeIncrement should be 5
			//the number of points and the degree angle must take into consideration the length of the arc
			/** the number of pixels a line should have */
			int pixelsIncrement = 10;
			//this includes the starting and ending points
			double arcLength = Math.abs(angle)*R;
			int nrPoints = (int)(arcLength/pixelsIncrement);
			if ( nrPoints*pixelsIncrement < (int)arcLength )
				nrPoints++;
			if ( nrPoints<5 )
				nrPoints = 5;
			double degreeIncrement = angle/nrPoints;
//			if ( bDebug ) System.out.println("[WANTopologyFilter] [debug] Number of points is "+nrPoints+" degreeInc="+degreeIncrement);
			
			//now do the real drawing
			double cosda = Math.cos(degreeIncrement);
			double sinda = Math.sin(degreeIncrement);
			double x, y, xant, yant;
			boolean bDrawArrow=false;
			boolean bSegDrawLabel=false;
			//compute the segment on which to draw arrow and label
			int specialSegment = 3*nrPoints/4;
			//apply an algorithm that computes the next point based on the last one
			x= x1; y = y1;
			for ( int i=0; i<nrPoints-1; i++ ) {
				xant = x;
				yant = y;
				x = xc + (xant-xc)*cosda - (yant-yc)*sinda;
				y = yc + (xant-xc)*sinda + (yant-yc)*cosda;
//				if ( bDebug ) System.out.println("[WANTopologyFilter] [debug] [x,y]=("+x+","+y+")");
				if ( i == specialSegment ) {
					bDrawArrow=true;
					if ( bDrawLabel ) bSegDrawLabel = true;
				} else {
					bDrawArrow=false;
					bSegDrawLabel = false;
				}
				drawLink(  (int)y, (int)x, (int)yant, (int)xant, linkColor, linkStroke, label, dd, bSavePoints, bSegDrawLabel, bDrawArrow);
			}
			xant=x;
			yant=y;
			x=x2; y=y2;
			drawLink( (int)y, (int)x, (int)yant, (int)xant, linkColor, linkStroke, label, dd, bSavePoints, bSegDrawLabel, bDrawArrow);
			return al;
		}

		/**
		 * given a sinus and a cosinus computes the correct angle that 
		 * can obtain these values
		 * @author mluc
		 * @since Aug 11, 2006
		 * @param sin1
		 * @param cos1
		 * @return radian angle
		 */
		private double computeAngle(double sin, double cos) {
			//compute the should be sin and cos
//			double halfPI = Math.PI/2;
			double angle = Math.asin(sin);
			if ( cos<0 )
				if ( angle>0 )
					angle = Math.PI - angle;
				else
					angle = -Math.PI - angle;
			
			
//			//get the angle between [0;�/2]
//			if ( angle < 0 ) angle = -angle;
//			//find the right angle
//			if ( Math.abs(Math.sin(angle)-sin)>0.001 || Math.abs(Math.cos(angle)-cos)>0.001 ) {
//				//search in [�/2,�]
//				angle+=halfPI;
//				if ( Math.abs(Math.sin(angle)-sin)>0.001 || Math.abs(Math.cos(angle)-cos)>0.001 ) {
//					//search in [�,3�/2]
//					angle+=halfPI;
//					if ( Math.abs(Math.sin(angle)-sin)>0.001 || Math.abs(Math.cos(angle)-cos)>0.001 ) {
//						//it must be in [3�/2,2�]
//						angle+=halfPI;
//					}
//				}
//			}
			return angle;
		}
		/**
		 * draws a link returning the set of points that bounds this link
		 * @author mluc
		 * @since Aug 7, 2006
		 * @param klat2
		 * @param klong2
		 * @param klat1
		 * @param klong1
		 * @param linkColor
		 * @param linkStroke
		 * @param label
		 * @param dd
		 * @param bPlotText
		 * @param link
		 * @return
		 */
		private ArrayList drawLink(int klat2, int klong2, int klat1, int klong1, 
				Color linkColor, float linkStroke, String label, int dd, boolean bSavePoints, boolean bDrawLabel, boolean bDrawArrow) {
			ArrayList vMap = null;
			if ( bSavePoints )
				vMap = new ArrayList();
			/*
			 * dd : 
			 * - variable used to compute the displacement for directional link
			 * from the undirected direction that is an central line,
			 * - makes the line to be drawn above the undirected line
			 * - generates some variables to retain the new coordonates 
			 */
			int dx = ( klong1 - klong2 ); int dy = klat1 - klat2;
			float l = (float) Math.sqrt ( dx*dx+dy*dy );
			float dir_x  = dx /l ;
			float dir_y  = dy /l;

			//compute the deviation from the ideal line connecting the two points
			int x1p = klong1 - (int) (dd * dir_y);
			int x2p = klong2 - (int) (dd * dir_y);
			int y1p = klat1 + (int) (dd * dir_x);
			int y2p = klat2 + (int) (dd * dir_x);

			//compute the acctual points in respect to the stroke
			float w = 1 + linkStroke/2;

			int x11p = x1p - (int) (w * dir_y);
			int x12p = x1p + (int) (w * dir_y);

			int x21p = x2p - (int) (w * dir_y);
			int x22p = x2p + (int) (w * dir_y);

			int y11p = y1p + (int) (w * dir_x);
			int y12p = y1p - (int) (w * dir_x);

			int y21p = y2p + (int) (w * dir_x);
			int y22p = y2p - (int) (w * dir_x);

			//save the points
			if ( bSavePoints ) {
				vMap.add(Integer.valueOf(x11p));
				vMap.add(Integer.valueOf(y11p));
				vMap.add(Integer.valueOf(x21p));
				vMap.add(Integer.valueOf(y21p));
				vMap.add(Integer.valueOf(x22p));
				vMap.add(Integer.valueOf(y22p));
				vMap.add(Integer.valueOf(x12p));
				vMap.add(Integer.valueOf(y12p));
			}

			//compute the coordinates for arrow that indicates the direction of the link
			//positioned at 1/4 of the link's length, closer to the source
			int xv1 = (x1p + x2p) / 2;
			int yv1 = (y1p + y2p) / 2;
			int xv = (xv1 + x2p) / 2;
			int yv = (yv1 + y2p) / 2;

			//start drawing
			((Graphics2D)g).setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);


			g.setColor(linkColor);
			/*
			g.fillArc(klong1 - dd / 2, klat1 - dd / 2, dd, dd, 0, 360);
			g.fillArc(klong2 - dd / 2, klat2 - dd / 2, dd, dd, 0, 360);
			 */

			//draw the line
			Stroke oldStroke = ((Graphics2D)g).getStroke();
			((Graphics2D)g).setStroke( new BasicStroke(linkStroke));
			g.drawLine(x1p, y1p, x2p, y2p);
			((Graphics2D)g).setStroke(oldStroke);

			if ( bDrawArrow ) {
				float arrow = 2;
				float aa = (float) (dd + linkStroke/2) / (float) 2.0;
	
				//draw the arrow
				int[] axv =
				{
						(int) (xv - aa * dir_x + arrow  * aa * dir_y),
						(int) (xv - aa * dir_x - arrow  * aa * dir_y),
						(int) (xv              + arrow * aa * dir_x),
						(int) (xv - aa * dir_x + arrow  * aa * dir_y)};
				int[] ayv =
				{
						(int) (yv - aa * dir_y - arrow * aa * dir_x),
						(int) (yv - aa * dir_y + arrow * aa * dir_x),
						(int) (yv              + arrow * aa * dir_y),
						(int) (yv - aa * dir_y - arrow * aa * dir_x)};
				Polygon p = new Polygon(axv, ayv, 4);
				g.fillPolygon(p);
			};

			((Graphics2D)g).setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

			//draw the label
			if ( bDrawLabel && label!=null && label.length()>0 ) {
				FontMetrics fm = g.getFontMetrics();
				int wl = fm.stringWidth(label) + 1;
				int hl = fm.getHeight() + 1;
	
				int off = 2;
				int xl = xv;
				int yl = yv ;
	
				if ( dir_x >= 0 && dir_y < 0 )  { xl = xl + off ; yl = yl +hl ; }
				if ( dir_x <= 0 && dir_y > 0 ) { xl = xl - wl -off ; yl = yl- off ;}
				if ( dir_x > 0 && dir_y >= 0 ) { xl = xl -wl -off ; yl = yl + hl ; }
				if ( dir_x < 0 && dir_y < 0 )   { xl = xl +off  ; yl = yl - off ; }

				drawText(xl, yl, linkColor, label, true);
			};
			return vMap;
		}
		
		private void drawText( int x, int y, Color cText, String sText, boolean bBorder) {
			if ( bBorder ) {
				Color borderColor;
				int min_val = 50;
				//if color is too dark, make it brighter
				if ( cText.getRed() < min_val && cText.getGreen()< min_val 
						|| cText.getRed() < min_val && cText.getBlue()< min_val 
						|| cText.getGreen() < min_val && cText.getBlue()< min_val )
					borderColor = cText.brighter();
				else
					borderColor = cText.darker().darker();
				g.setColor(borderColor);
			    for (int i=-1; i<=1; i++)
				for (int j=-1; j<=1; j++)
				    g.drawString(sText, x+i, y+j);
			}
		    g.setColor(cText);
		    g.drawString(sText, x, y);
		}

		/**
		 * constructs tooltip for the provided node.
		 * @author mluc
		 * @since Aug 7, 2006
		 * @param node
		 * @return
		 */
		public String tooltip( ViewNodeInfo node) {
			String sHREF = "";//pgets(prop, node.realname+".href", pgets(prop, "href_"+node.realname, sDefaultHREF));
			String sCaption = "";
			String sHistory = "";

//			sTooltip = escHtml(sTooltip);
			//System.out.println(sTooltip);
//			sHREF = replace(sHREF, "$NAME", node.realname);

			sCaption = Formatare.tagProcess(sCaption);
			String result;
			result = "<area shape=\"circle\" href=\""+sHREF+"\" coords=\""+node.posX+","+node.posY+","+node.radius+"\" "
			+"onmouseover=\"return overlib('<iframe src=\'"+sHistory+"\' border=0 width=330 height=220 scrolling=no></iframe>', CAPTION, '"+sCaption+"',CENTER, WIDTH, 330, HAUTO, VAUTO );\" onmouseout=\"return nd();\""
			+"/>";
			return result;
		}

//		private Image shadowImage;
//		private Image bumpImage;
//		private Image bumpRectImage;
		private Image routerImage;
		private Image switchImage;
		private Image lanImage;
		private Image hostImage;
//		private ConvolveOp blurOp;
		final private Color shadowColor = new Color(0.0f, 0.0f, 0.0f, 0.3f);
//		private Color routerColor = new Color(0, 170, 249); // default color 4 routers 
		public Color nodataColor = Color.ORANGE;
		public final int lanWidth = 80;
		public final int lanHeight = 40;


		public Image toImage(BufferedImage bufferedImage) {
			return Toolkit.getDefaultToolkit().createImage(bufferedImage.getSource());
		}

		private BufferedImage createShadowMask(BufferedImage image) {
			BufferedImage mask = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
			for (int x = 0; x < image.getWidth(); x++) {
				for (int y = 0; y < image.getHeight(); y++) {
					int argb = image.getRGB(x, y);
					argb = (int) ((argb >> 24 & 0xFF) * 0.5) << 24 | shadowColor.getRGB() & 0x00FFFFFF;
					mask.setRGB(x, y, argb);
				}
			}
			return mask;
		}

		private ConvolveOp getBlurOp(int size) {
			float[] data = new float[size * size];
			float value = 1 / (float) (size * size);
			for (int i = 0; i < data.length; i++) {
				data[i] = value;
			}
			return new ConvolveOp(new Kernel(size, size, data));
		}

		private ConvolveOp getAnotherBlurOp(int size) {
			float[] data = new float[size * size];
			int sum=0;
			int r = size/2+size%2;
			for ( int i=0; i<size; i++) {
				for (int j=0; j<size; j++) {
					int index = i*size+j;
					if ( i >= r )
						if ( j >= r )
							data[index]=size-j-1+size-i-1;
						else
							data[index]=j+size-i-1;
					else
						if ( j >= r )
							data[index]=size-j-1+i;
						else
							data[index]=j+i;
//					System.out.print(" "+(int)data[index]);
					sum += data[index];
				};
//				System.out.println("");
			};
//			float value = 1 / (float) (size * size);
			for (int i = 0; i < data.length; i++) {
				data[i]/=sum;
			}
			return new ConvolveOp(new Kernel(size, size, data));
		}

//		private void createShadow(int w, int h) {
//			//BufferedImage bshadow = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(w+14, h+14, Transparency.TRANSLUCENT);
//			BufferedImage bshadow = new BufferedImage(w+10,h+10,BufferedImage.TYPE_INT_ARGB);
//			Graphics2D g = bshadow.createGraphics();
//			g.setColor(Color.cyan);
//			g.fillOval(3,3, w, h);
//			if (blurOp == null)
//				blurOp = getBlurOp(7);
//			blurOp.filter(createShadowMask(bshadow), bshadow);
//			shadowImage = toImage(bshadow);
//		}

//		private void createBump(int w, int h) {
//			try{
//				//BufferedImage bbump = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(w, h, Transparency.TRANSLUCENT);
//				BufferedImage bbump = new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
//				Graphics2D g = bbump.createGraphics();
//				Rectangle rect = new Rectangle(0, 0, w-2, h-2);
//				//System.out.println("[createBump] before setTexture");
//				SphereTexture.setTexture(g, rect);
//				//System.out.println("[createBump] after setTexture");
//				g.fillOval(1, 1, w-2, h-2);
//				bumpImage = toImage(bbump);
//			}catch(Exception e){
//				e.printStackTrace();
//			}
//		}

//		private void createRectBump(int maxw, int maxh, int offsetx, int offsety, int width, int height) {
//			try{
//				//BufferedImage bbump = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(w, h, Transparency.TRANSLUCENT);
//				BufferedImage bbump = new BufferedImage(maxw,maxh,BufferedImage.TYPE_INT_ARGB);
//				Graphics2D g = bbump.createGraphics();
//				Rectangle rect = new Rectangle(0, 0, width, height);
//				//System.out.println("[createBump] before setTexture");
//				SphereTexture.setTexture(g, rect);
//				//System.out.println("[createBump] after setTexture");
//				g.fillRect(offsetx, offsety, width, height);
////				g.setColor(Color.BLACK);
////				g.drawRect(offsetx, offsety, width, height);
//				bumpRectImage = toImage(bbump);
//			}catch(Exception e){
//				e.printStackTrace();
//			}
//		}

		private void createRouter() {
			final int routerRadius = 20;
			int width = routerRadius*2;
			int height = routerRadius*2;
			int shadow_offset = 4;
			final int shadowRadius=3;//its important not to be too big, because when creating the convolution operator
			//a color of an pixel gets divided by (2*shadowRadius+1)*(2*shadowRadius+1)
			int doubleShadowRadius = 2*shadowRadius;
			BufferedImage bi = new BufferedImage(width+shadow_offset+doubleShadowRadius, height+shadow_offset+doubleShadowRadius, BufferedImage.TYPE_INT_ARGB);
			Graphics2D gRouter = bi.createGraphics();
			//apply shadow
			BufferedImage bshadow = new BufferedImage(width+doubleShadowRadius,height+doubleShadowRadius,BufferedImage.TYPE_INT_ARGB);
			Graphics2D gShadow = bshadow.createGraphics();
			gShadow.setColor(Color.cyan);
			gShadow.fillOval(shadowRadius,shadowRadius, width-1, height-1);
//			//create the matrix for blur as blurRadius values around the point
			ConvolveOp shadowOp = getAnotherBlurOp(shadowRadius*2+1);
			shadowOp.filter(createShadowMask(bshadow), bshadow);
			gRouter.drawImage( bshadow, shadow_offset-shadowRadius, shadow_offset-shadowRadius, null);
            /*
             * fills the gradient paint starting with the outer color
             * at the upper left corner and ending with the inner color
             * near the lower right corner of rect1
             */ 
            //host image
			BufferedImage bi2 = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			bi2.setRGB(0, 0, width, height, NetworkDevices.rgbArrayRouter, 0, width);
			gRouter.drawImage( bi2, 0, 0, width, height, null);
			//apply bump
			int border=1;
			Rectangle rect = new Rectangle(0, 0, width-2*border, height-2*border);
			SphereTexture.setTexture(gRouter, rect);
			gRouter.fillOval(border, border, width-2*border, height-2*border);
			routerImage = toImage(bi);
		}
		
		private void createLan() {
			BufferedImage bi = new BufferedImage(lanWidth, lanHeight, BufferedImage.TYPE_INT_ARGB);
			bi.setRGB(0, 0, lanWidth, lanHeight, NetworkDevices.rgbArrayLan, 0, lanWidth);
			lanImage = toImage(bi);
		}
		private void createSwitch() {
			final int switchRadius = 20;
			int width = switchRadius*2;
			int height = switchRadius*2;
			int spaceX = 3;
			int spaceY = 3;
			int small_width = width - 2*spaceX;
			int small_height = height - 2*spaceY;
			int shadow_offset = 4;
			final int shadowRadius=3;//its important not to be too big, because when creating the convolution operator
			//a color of an pixel gets divided by (2*shadowRadius+1)*(2*shadowRadius+1)
			int doubleShadowRadius = 2*shadowRadius;
			BufferedImage bi = new BufferedImage(small_width+shadow_offset+doubleShadowRadius, small_height+shadow_offset+doubleShadowRadius, BufferedImage.TYPE_INT_ARGB);
			Graphics2D gLan = bi.createGraphics();
			//apply shadow
			BufferedImage bshadow = new BufferedImage(small_width+doubleShadowRadius,small_height+doubleShadowRadius,BufferedImage.TYPE_INT_ARGB);
			Graphics2D gShadow = bshadow.createGraphics();
			gShadow.setColor(Color.cyan);
			gShadow.fillRect(shadowRadius,shadowRadius, small_width-1, small_height-1);
//			//create the matrix for blur as blurRadius values around the point
			ConvolveOp shadowOp = getBlurOp(shadowRadius*2+1);
			shadowOp.filter(createShadowMask(bshadow), bshadow);
			gLan.drawImage( bshadow, spaceX+shadow_offset-shadowRadius, spaceY+shadow_offset-shadowRadius, null);
			//draw the lan
			BufferedImage bi2 = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			bi2.setRGB(0, 0, width, height, NetworkDevices.rgbArraySwitch, 0, width);
			gLan.drawImage( bi2, 0, 0, width, height, null);
			//draw the bumping
			final int border = 1;//one pixel for the border of the drawing
			//Radius*2, Radius*2, 4, 4, (Radius-4)*2, (Radius-4)*2
			//int maxw, int maxh, int offsetx, int offsety, int width, int height
			int ss_width = small_width-2*border;
			int ss_height = small_height-2*border;
			BufferedImage bbump = new BufferedImage( ss_width,ss_height,BufferedImage.TYPE_INT_ARGB);
			Graphics2D gBump = bbump.createGraphics();
			Rectangle rect = new Rectangle(0, 0, ss_width, ss_height);
			SphereTexture.setTexture(gBump, rect);
			gBump.fillRect( 0, 0, ss_width, ss_height);
			gLan.drawImage(bbump, spaceX+border, spaceY+border, null);
			switchImage = toImage(bi);
		}
		static final int HostRadius = 13;
		private void createHost() {
			int width = HostRadius*2;
			int height = HostRadius*2;
			int shadow_offset = 3;
			final int shadowRadius=2;//its important not to be too big, because when creating the convolution operator
			//a color of an pixel gets divided by (2*shadowRadius+1)*(2*shadowRadius+1)
			int doubleShadowRadius = 2*shadowRadius;
			BufferedImage bi = new BufferedImage(width+shadow_offset+doubleShadowRadius, height+shadow_offset+doubleShadowRadius, BufferedImage.TYPE_INT_ARGB);
			Graphics2D gHost = bi.createGraphics();
			//radial gradient
//			Ellipse2D shadowEl = new Ellipse2D.Float(shadow_offset, shadow_offset, width, height);
//            RadialGradientPaint gp = new RadialGradientPaint(new Rectangle(0,0,width,height),new Color(120,120,120,100),new Color(35,35,35,100));
//            gHost.setPaint(gp);
//            gHost.fill(shadowEl);
			//apply shadow
			BufferedImage bshadow = new BufferedImage(width+doubleShadowRadius,height+doubleShadowRadius,BufferedImage.TYPE_INT_ARGB);
			Graphics2D gShadow = bshadow.createGraphics();
			gShadow.setColor(Color.cyan);
			gShadow.fillOval(shadowRadius,shadowRadius, width-1, height-1);
//			//create the matrix for blur as blurRadius values around the point
			ConvolveOp shadowOp = getAnotherBlurOp(shadowRadius*2+1);
			shadowOp.filter(createShadowMask(bshadow), bshadow);
			gHost.drawImage( bshadow, shadow_offset-shadowRadius, shadow_offset-shadowRadius, null);
            /*
             * fills the gradient paint starting with the outer color
             * at the upper left corner and ending with the inner color
             * near the lower right corner of rect1
             */ 
            //host image
			BufferedImage bi2 = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			bi2.setRGB(0, 0, width, height, NetworkDevices.rgbArrayHost, 0, width);
			gHost.drawImage( bi2, 0, 0, width, height, null);
			//apply bump
//			Rectangle rect = new Rectangle(0, 0, width-2, height-2);
//			SphereTexture.setTexture(g, rect);
//			gHost.fillOval(1, 1, width-2, height-2);
			hostImage = toImage(bi);
		}

		/**
		 * draws nodes taking into account their type: router, switch or host
		 * @author mluc
		 * @since Aug 7, 2006
		 * @param node
		 */
		public void draw( ViewNodeInfo node) {
			
			int fontsize = 12;

			int R = Radius;
			int x = node.posX;
			int y = node.posY;
			int xLabelOffset = R;
			int yLabelOffset = R;

			boolean bHasHostChildren = node.hasHostChildren();
			if ( node.type==MyGraph.NODE_SWITCH && !bHasHostChildren ) {
				//for network lan, setup some parameters differently
				xLabelOffset = lanWidth*1/4;
				yLabelOffset = -lanHeight/2;
			}
			
			g.setFont(new Font("Arial", Font.PLAIN, fontsize));

			int labelx = x+xLabelOffset;
			int labely = y+yLabelOffset;

			g.setColor(new Color(100, 100, 100));
			//g.setColor(Color.BLACK);

			if ( node.name!=null && !node.name.startsWith(ViewNodeInfo.PREFIX_UNKNOWN_ROUTER) ) {
				for (int i=-1; i<=1; i++)
					for (int j=-1; j<=1; j++)
						g.drawString(node.name, labelx+i, labely+j);
	
				g.setColor(Color.WHITE);

				g.drawString(node.name, labelx, labely);
			}

			if ( node.type==MyGraph.NODE_ROUTER ) {
				// paint as router
//				Stroke oldStroke = ((Graphics2D)g).getStroke();
//				((Graphics2D)g).setStroke( new BasicStroke(2f));

//				g.setColor(new Color(100, 100, 100));
//				g.drawOval(x-R-1, y-R-1, 2*R+2, 2*R+2);
//				g.setColor(routerColor);
//				g.fillOval(x-R, y-R, 2*R, 2*R);
//				g.drawImage(shadowImage,x-R+2,y-R+2,null);
				g.drawImage(routerImage,x-R,y-R,null);
//				g.drawImage(bumpImage, x-R, y-R, null);


//				if( farmrouters.contains(n.name) ){
//				if( (n.data==null || n.data.size() == 0) && n.alternate==null)
//				g.setColor(Color.RED);
//				else
//				g.setColor(Color.YELLOW);
//				}
//				else

//				g.setColor(Color.WHITE);
//				g.drawOval(x-R, y-R, 2*R, 2*R);

//				((Graphics2D)g).setStroke(oldStroke);

//				g.setColor(Color.WHITE);
//				g.drawLine(x-1, y-1, x-R+3, y-R+3);
//				g.drawLine(x-1, y+1, x-R+3, y+R-3);
//				g.drawLine(x+1, y-1, x+R-3, y-R+3);
//				g.drawLine(x+1, y+1, x+R-3, y+R-3);
			} else if ( node.type==MyGraph.NODE_SWITCH ) {
				if ( bHasHostChildren ) {
//					g.drawImage(shadowImage,x-R+2,y-R+2,null);
					g.drawImage(switchImage,x-R,y-R,null);
//					g.drawImage(bumpRectImage, x-R, y-R, null);
				} else {
					//draw a network
					g.drawImage(lanImage,x-lanWidth/2,y-lanHeight/2,null);
				}
			} else {
				g.drawImage(hostImage, x-HostRadius, y-HostRadius, null);
				
//				g.drawImage(shadowImage,x-R+2,y-R+2,null);
//				g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//				// paint in default color
//				g.setColor(nodataColor);
//				g.fillOval(x-R, y-R, 2*R, 2*R);
//				//g.setColor(n.alternate==null ? Color.RED : Color.ORANGE);
//				g.setColor(Color.BLACK);
//				g.drawOval(x-R, y-R, 2*R, 2*R);
//				g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
//				g.drawImage(bumpImage, x-R, y-R, null);
			}
		}

	}

	/**
	 * generates an image based on informations stored in graph
	 * this should be called sync with the function that modifies the graph (filterResult)
	 * @author mluc
	 * @since Aug 3, 2006
	 */
	private void generateGraph(MyGraph agraph, int imgWidth, int imgHeight) {
		/**
		 * Layout algorithm
		 * ================
		 * Summary:
		 * 	from current graph first generate a MST (minimum spanning tree) and
		 * 	then use it to generate a new radial graph.
		 * Description:
		 * 1. in the current graph add weight information on links based on current
		 * type of nodes connected by a link, and number of other links that serve
		 * 1.1 the weight is computed as the product of the number of the other 
		 * links that serve the two nodes and a value determined by the weakest
		 * type of the two nodes. This value ought to be: H=1, S=2, R=3. 
		 * one of the two nodes that define the link
		 * 2. mark/select the links that would provide a MST based on greater weight
		 * 3. based on the weight of links, compute weight of nodes as sum
		 * 4. choose the heaviest node as the root of graph to be drawn
		 * 5. create the radial graph
		 * 6. draw the graph
		 */
		if ( agraph.nodes.size() == 0 )
			return;
		ArrayList linksInfo = new ArrayList();
		ArrayList nodesInfo = new ArrayList();
		//--------------------------------------------------------------------------
		//should duplicate existing graph... the only connection with the real graph
		//--------------------------------------------------------------------------
		//solution:
		//create two lists: one of ViewNodeInfo that contains also the node's initial info
		//and another one with ViewLinkInfo that contains references to ViewNodeInfo as 
		//nodes
		//and so, only this one has to be sync
		copyGraph(agraph.nodes, agraph.links, nodesInfo, linksInfo);

		int nodesCount = nodesInfo.size();
		System.out.println("[WANTopologyFilter] [debug] found "+nodesCount+" nodes");
		int linksCount = linksInfo.size();
		System.out.println("[WANTopologyFilter] [debug] found "+linksCount+" links");
		//1. weight computation
		countLinks(nodesInfo, linksInfo);
		//also compute maximal and minimal value for all links
		double max_val=0, min_val=Double.MAX_VALUE;
		/** 
		 * in order not to be affected by unidirectional links, if a link has its
		 * pairs, then the weight is distributed to all links and with an factor
		 * greater than 1
		 */
		for ( int i=0; i<linksCount; i++) {
			ViewLinkInfo vl = (ViewLinkInfo)linksInfo.get(i);
			//save max and min val
			if ( vl.value_IN>max_val ) max_val= vl.value_IN;
			if ( vl.value_OUT>max_val ) max_val= vl.value_OUT;
			if ( vl.value_IN<min_val ) min_val = vl.value_IN;
			if ( vl.value_OUT<min_val ) min_val = vl.value_OUT;
			//if this link has already a computed weight for the link as pair
			//of another link, skip it
			if ( vl.weight>=0 )
				continue;
			ArrayList pairs=new ArrayList();
			int curOrder = vl.order;
			for (int j=i+1; j<linksCount; j++) {
				ViewLinkInfo auxl = (ViewLinkInfo)linksInfo.get(j);
				if ( auxl.viewSrc==vl.viewDst && auxl.viewDst==vl.viewSrc 
						|| auxl.viewSrc==vl.viewSrc && auxl.viewDst==vl.viewDst ) {
					auxl.order = ++curOrder;
					pairs.add(auxl);
				}
			}
			//count links for the two nodes that compose this link
			float weight=0;
			weight += (vl.viewSrc!=null?vl.viewSrc.countLinks:0);
			weight += (vl.viewDst!=null?vl.viewDst.countLinks:0);
			//because the count considered link l twice, it should be removed
			//and also because this link should not be considered at all...
			//and, if it has pair, decrement it twice
			weight -= (pairs.size()+1);
			//if pair link, divide the weight by the number of pairs/distribute the weight
			if ( pairs.size()>0 ) weight = weight*vl.computeFactorMultiLinks(pairs.size())/(pairs.size()+1);
			//consider also the type of nodes that are connected by this link
			if ( vl.viewSrc!=null && vl.viewDst!=null ) {
				int type = vl.viewSrc.type>vl.viewDst.type?vl.viewDst.type:vl.viewSrc.type;
				if ( type == MyGraph.NODE_HOST )
					weight *= ViewLinkInfo.FACTOR_HOST;
				else if ( type == MyGraph.NODE_SWITCH )
					weight *= ViewLinkInfo.FACTOR_SWITCH;
				else if ( type == MyGraph.NODE_ROUTER )
					weight *= ViewLinkInfo.FACTOR_ROUTER;
			}
			//set weight in link info, for both links if the case
			vl.weight = weight;
			System.out.println("[WANTopologyFilter] [debug] weight of "+vl+" is "+vl.weight+" order is "+vl.order);
			for ( int j=0; j<pairs.size(); j++) {
				ViewLinkInfo apair = ((ViewLinkInfo)pairs.get(j));
				apair.weight = weight;
				System.out.println("[WANTopologyFilter] [debug] weight of "+apair+" is "+apair.weight+" order is "+apair.order);
			}
		}
		//check the minimal value to be smaller than maximal value
		if ( min_val > max_val )
			min_val = max_val;
		System.out.println("[WANTopologyFilter] [debug] max link value is "+max_val+" and min val is "+min_val);
		//2. apply MST on weighted links
		//2.1. order links by weight (is this the best solution to choose the tree?)
		boolean bChange=false;
		do {
			bChange=false;
			for (int i=0; i<linksCount-1; i++) {
				ViewLinkInfo vl1 = (ViewLinkInfo)linksInfo.get(i);
				ViewLinkInfo vl2 = (ViewLinkInfo)linksInfo.get(i+1);
				if ( vl1.weight<vl2.weight ) {
					Object l1 = linksInfo.remove(i);
					linksInfo.add(i+1, l1);
					bChange=true;
				}
			}
		} while (bChange);
		//set selected links based on MST algorithm
		//supposable all nodes are in only one tree
		TreeSet selNodes = new TreeSet();
//		ArrayList selNodes = new ArrayList();
		for ( int i=0; i<linksCount; i++) {
			ViewLinkInfo vl = (ViewLinkInfo)linksInfo.get(i);
			if ( (vl.viewSrc!=null && !selNodes.contains(vl.viewSrc)) || (vl.viewDst!=null && !selNodes.contains(vl.viewDst)) ) {
				vl.selectedMST = true;
				if ( vl.viewSrc!=null )
					selNodes.add(vl.viewSrc);
				if ( vl.viewDst!=null )
					selNodes.add(vl.viewDst);
			}
		}
		//3. compute weight of nodes
		for ( int i=0; i<linksCount; i++) {
			ViewLinkInfo vl = (ViewLinkInfo)linksInfo.get(i);
			if ( vl.viewSrc!=null )
				vl.viewSrc.weight += vl.weight;
			if ( vl.viewDst!=null )
				vl.viewDst.weight += vl.weight;
		};
		//4. find root as first found node with heaviest weight starting with the heaviest link
		//this will work only if there is only one MST tree and all nodes are connected to it
		ViewNodeInfo root = null;
		float max_weight = 0;
		for ( int i=0; i<linksCount; i++) {
			ViewLinkInfo vl = (ViewLinkInfo)linksInfo.get(i);
			if ( vl.viewSrc!=null ) {
				if ( vl.viewSrc.weight>max_weight ) {
					max_weight = vl.viewSrc.weight;
					root = vl.viewSrc;
				}
			}
			if ( vl.viewDst!=null ) {
				if ( vl.viewDst.weight>max_weight ) {
					max_weight = vl.viewDst.weight;
					root = vl.viewDst;
				}
			}
		};
		//5. construct positioning of nodes based on a radial graph algorithm
		int maxRadius = buildRadialTree(root, linksInfo);
		double radius = 1.0 / (1+maxRadius);
		assignPositions(root, 0, 0, radius, 0, 2*Math.PI);
		//6. drawing stage
		//construct the bounding rectangle that contains the nodes
		Rectangle2D.Double area = null;
		for( int i=0; i<nodesCount; i++){
			ViewNodeInfo vn = (ViewNodeInfo) nodesInfo.get(i);
			System.out.println("[WANTopologyFilter] [debug] node "+vn+" is "+vn.type);
			if(area == null) 
				area = new Rectangle2D.Double(vn.radialPosX, vn.radialPosY, 0, 0);
			else
				area.add(vn.radialPosX, vn.radialPosY);
		}
		//the actual drawing
		BufferedImage img= new BufferedImage( imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2D = (Graphics2D)img.getGraphics();
		g2D.setColor(Color.WHITE);
		g2D.fillRect(0,0, img.getWidth(), img.getHeight());
		Drawing d = new Drawing(g2D);
		//set max and min values for links
		d.setMaxMinVal(max_val, min_val);
		//compute image positions of nodes based on radial area and the real image size
		//max node parameters are values that determine the area a node will 
		//be drawn in
		int maxNodeWidth = d.maxNodeWidth;
		int maxNodeHeight = d.maxNodeHeight;
		int garbageX = maxNodeWidth%2;
		int garbageY = maxNodeHeight%2;
		int offsetX = maxNodeWidth/2 + garbageX;
		int offsetY = maxNodeHeight/2 + garbageY;
		//for each coordinate, an offset will be used so that the left margin
		//of a node to appear in the image, taking in consideration that the
		//posx and posy are computed for center. The offset is maxNodeWidth/2
		double factorX = (imgWidth-maxNodeWidth-2*garbageX)/area.width;
		double factorY = (imgHeight-maxNodeHeight-2*garbageY)/area.height;
		for( int i=0; i<nodesCount; i++){
			ViewNodeInfo vn = (ViewNodeInfo) nodesInfo.get(i);
			vn.posX = offsetX + (int)(factorX*(vn.radialPosX-area.getMinX()));
			vn.posY = offsetY + (int)(factorY*(area.getMaxY() - vn.radialPosY));
		};
		for (int i=0; i<linksCount; i++) {
			ViewLinkInfo vl = (ViewLinkInfo)linksInfo.get(i);
			ViewNodeInfo infoSrc = vl.viewSrc;
			ViewNodeInfo infoDst = vl.viewDst;
			if ( infoSrc!=null && infoDst!=null && (infoSrc.posX!=0 || infoSrc.posY!=0) && (infoDst.posX!=0 || infoDst.posY!=0) ) {
				//draw link
				//compute width of link based on capacity of link
				//compute color based on value of link
				//for color scale is used a logarithmical scale with 10 as base
				d.draw(vl);
			}
		}
		for ( int i=0; i<nodesCount; i++) {
			ViewNodeInfo vn = (ViewNodeInfo) nodesInfo.get(i);
			//draw node
//			g2D.setColor(Color.BLUE);
//			g2D.fillOval(vn.posX-10, vn.posY-6, 20, 12);
//			g2D.setColor(Color.BLACK);
//			g2D.drawString( vn.name, vn.posX+4, vn.posY+6);
			d.draw(vn);
		};
		//save image
		try{
			System.out.print("[WANTopologyFilter] Writing image in temp directory... ");
			boolean bJPEG=false;
			double fJPEGcompression= 0.6D;
			File tempFile = new File(new File(System.getProperty("java.io.tmpdir")), "WanTop-000"+ (bJPEG?".jpg":".png"));
//			File tempFile = File.createTempFile("WanTop-", bJPEG?".jpg":".png", new File(System.getProperty("java.io.tmpdir")));
			OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile));

			if (bJPEG)
				ChartUtilities.writeBufferedImageAsJPEG(out, (float) fJPEGcompression, img);
			else       
				ChartUtilities.writeBufferedImageAsPNG(out, img);

			out.flush();
			out.close();

//			lia.web.servlets.web.display.registerImageForDeletion(tempFile.getName(), getCacheTimeout());

			System.out.println("[WANTopologyFilter] DONE");
			System.out.println("[WANTopologyFilter] tempFile: "+tempFile.getName());
		}
		catch (Exception e){
			System.err.println("[WANTopologyFilter] Exception creating the image : "+e+"("+e.getMessage()+")");
			e.printStackTrace();
		}
	}

	public static void dumpImage() {
		JFrame frame = new JFrame("test");
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		FileDialog fd = new FileDialog(frame, "KickJava.com", FileDialog.LOAD); 
		fd.setDirectory("MonALISA");
		fd.show(); 
		String filename = fd.getDirectory()+fd.getFile(); 
		fd.dispose();
		System.out.println("filename: "+filename);
		try {
			ImageIcon icon = new ImageIcon(filename);
			int w = icon.getIconWidth();
			int h = icon.getIconHeight();
			BufferedImage bi;
			bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D imageGraphics = bi.createGraphics();
			imageGraphics.drawImage(icon.getImage(), 0, 0, null);
			System.out.println("new int[]{");
			StringBuilder buf = new StringBuilder();
			for (int y=0; y<h; y++)
				for (int x=0; x<w; x++) {
					buf.append((x==0&&y==0?"":","));
					if ( buf.length()>=80 ) {
						System.out.println(buf);
						buf = new StringBuilder();
					}
					buf.append(bi.getRGB(x, y));
				}
			System.out.println(buf);
			System.out.println("};");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		frame.hide();
		frame.dispose();
	}

	public static void main(String args[]) {
		//load the router image and get the bytes
//		dumpImage();

		WANTopologyFilter wanFilter = new WANTopologyFilter(true);
		System.out.println("Temporary directory is: "+System.getProperty("java.io.tmpdir"));
		ExtendedResult eRes = new ExtendedResult();
		eRes.FarmName = "mr-smith";
		eRes.ClusterName="HOSTs_on_mr-smith";
		eRes.NodeName="mickyt";
		eRes.param_name = new String[] {"SPEED","IN","OUT"};
		eRes.param = new double[] { 100, 5400, 3265};
		wanFilter.filterData( eRes);
		//2nd host
		eRes.NodeName="neo";
		eRes.param_name = new String[] {"SPEED","IN","OUT"};
		eRes.param = new double[] { 100, 3265, 5400};
		wanFilter.filterData( eRes);
		//3rd host
		eRes.NodeName="trinity";
		eRes.param_name = new String[] {"SPEED","IN","OUT"};
		eRes.param = new double[] { 100, 3265, 1254};
		wanFilter.filterData( eRes);
		//1st lan
		eRes.ClusterName="LANs_on_mr-smith";
		eRes.NodeName="rogrid";
		eRes.param_name = new String[] {"SPEED","IN","OUT"};
		eRes.param = new double[] { 1000, 985, 111};
		wanFilter.filterData( eRes);
		//2nd lan
		eRes.NodeName="cern.ch";
		eRes.param_name = new String[] {"SPEED","IN","OUT"};
		eRes.param = new double[] { 1000, 95, 7302};
		wanFilter.filterData( eRes);
		//1st wan
		eRes.ClusterName="WANs_on_mr-smith";
		eRes.NodeName="private1_caltech";
		eRes.param_name = new String[] {"SPEED","IN","OUT"};
		eRes.param = new double[] { 10000, 9085, 11};
		wanFilter.filterData( eRes);
		//a second wan to the same router
		eRes.NodeName="private2_caltech";
		eRes.param_name = new String[] {"SPEED","IN","OUT"};
		eRes.param = new double[] { 1000, 785, 101};
		wanFilter.filterData( eRes);
		//a third wan to the same router
		eRes.NodeName="private3_caltech";
		eRes.param_name = new String[] {"SPEED","IN","OUT"};
		eRes.param = new double[] { 1000, 999, 1};
		wanFilter.filterData( eRes);
		//a fourth wan to the same router
		eRes.NodeName="private4_caltech";
		eRes.param_name = new String[] {"SPEED","IN","OUT"};
		eRes.param = new double[] { 10000, 9999, 0};
		wanFilter.filterData( eRes);
		//another router
		//1st host
		eRes.ClusterName="HOSTs_on_caltech";
		eRes.NodeName="vinci1";
		eRes.param_name = new String[] {"SPEED","IN","OUT"};
		eRes.param = new double[] { 1000, 85, 11};
		wanFilter.filterData( eRes);
		//2nd host
		eRes.NodeName="vinci2";
		eRes.param_name = new String[] {"SPEED","IN","OUT"};
		eRes.param = new double[] { 1000, 85, 11};
		wanFilter.filterData( eRes);
		//3rd host
		eRes.NodeName="vinci3";
		eRes.param_name = new String[] {"SPEED","IN","OUT"};
		eRes.param = new double[] { 1000, 85, 11};
		wanFilter.filterData( eRes);
		//a lan
		eRes.ClusterName="HOSTs_on_cern.ch";
		eRes.NodeName="pccil";
		eRes.param_name = new String[] {"SPEED","IN","OUT"};
		eRes.param = new double[] { 1000, 85, 11};
		wanFilter.filterData( eRes);
		try {
			long lStart = NTPDate.currentTimeMillis();
			wanFilter.generateGraph(wanFilter.mygraph, 800, 600);
			long lEnd = NTPDate.currentTimeMillis();
			System.out.println("[WANTopologyFilter] Generating graph took "+(lEnd-lStart)+" miliseconds");
		} catch (Exception ex) {
			System.out.println("[WANTopologyFilter] Generating graph exception ");
			ex.printStackTrace();
		}
		System.exit(0);
	}
}
final class NetworkDevices {
	public static final int[] rgbArrayRouter = new int[]{
		0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,-1,-1,-1,-1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
		0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,-1,-1,-1,-1,-1,-13254657,-13254657,-13254657,-13254657,
		-1,-1,-1,-1,-1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,-1,-1,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-1,-1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
		0,0,0,-1,-1,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-1,-1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,-1,-1,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-1,-1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,-1,-1,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-1,-1,0,0,
		0,0,0,0,0,0,0,0,0,0,-1,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-1,-1,0,0,0,0,0,0,0,0,0,0,-1,-13254657,-13254657,
		-13254657,-1,-16777216,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-1,0,0,0,0,0,0,0,0,0,-1,-13254657,-13254657,-13254657,-1,-1,-1,-16777216,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-8553091,-8553091,-8553091,-8553091,
		-8553091,-8553091,-8553091,-13254657,-13254657,-13254657,-1,0,0,0,0,0,0,0,-1,-1,
		-13254657,-13254657,-13254657,-13254657,-1,-1,-1,-16777216,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-1,-1,-1,-1,-1,-1,-13254657,-13254657,-13254657,-1,-1,0,0,0,
		0,0,0,-1,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-1,-1,-1,-16777216,
		-13254657,-13254657,-13254657,-13254657,-16777216,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-1,-1,-1,-1,-13254657,
		-13254657,-13254657,-13254657,-1,0,0,0,0,0,-1,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-1,-1,-1,-16777216,-13254657,-13254657,-16777216,
		-1,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-1,-1,-1,-1,-1,-13254657,-13254657,-13254657,-13254657,-13254657,-1,0,0,0,0,-1,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-1,-1,-1,-16777216,-16777216,-1,-1,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-1,-1,-1,-16777216,-1,-1,-13254657,-13254657,-13254657,-13254657,
		-13254657,-1,0,0,0,-1,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-1,-1,-1,-16777216,-1,-1,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-1,-1,-1,-16777216,-16777216,-1,
		-1,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-1,0,0,-1,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-1,-1,-1,-1,-1,-13254657,-13254657,-13254657,-13254657,
		-13254657,-1,-1,-1,-16777216,-13254657,-13254657,-16777216,-1,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-1,0,0,-1,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-1,-1,-1,-1,-13254657,-13254657,-13254657,-13254657,-1,-1,-1,
		-16777216,-13254657,-13254657,-13254657,-13254657,-16777216,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-1,0,0,-1,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-1,-1,-1,-1,-1,-1,-13254657,-13254657,-13254657,-1,-1,-1,-16777216,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-1,0,0,-1,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-8553091,-8553091,-8553091,-8553091,
		-8553091,-8553091,-8553091,-13254657,-13254657,-1,-1,-1,-16777216,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-1,-1,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-1,-16777216,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-1,-1,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-1,-1,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-1,-1,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-16777216,-1,-13254657,-13254657,-13254657,
		-8553091,-8553091,-8553091,-8553091,-8553091,-8553091,-8553091,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-1,0,-1,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-16777216,-1,-1,-1,-13254657,
		-13254657,-1,-1,-1,-1,-1,-1,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-1,0,-1,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-16777216,-1,-1,-1,-13254657,-13254657,-13254657,-1,-1,-1,-1,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-1,0,0,-1,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-16777216,-13254657,-13254657,-13254657,
		-13254657,-16777216,-1,-1,-1,-13254657,-13254657,-13254657,-13254657,-1,-1,-1,-1,
		-1,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-1,0,0,-1,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-1,-16777216,-13254657,-13254657,-16777216,-1,-1,-1,
		-13254657,-13254657,-13254657,-13254657,-13254657,-1,-1,-16777216,-1,-1,-1,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-1,0,0,-1,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-1,-1,-16777216,-16777216,-1,-1,-1,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-1,-1,-16777216,-16777216,-1,-1,-1,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-1,0,0,0,-1,
		-13254657,-13254657,-13254657,-13254657,-13254657,-1,-1,-16777216,-1,-1,-1,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-1,-16777216,-13254657,
		-13254657,-16777216,-1,-1,-1,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-1,0,0,0,0,-1,-13254657,-13254657,-13254657,-13254657,-13254657,
		-1,-1,-1,-1,-1,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-16777216,-13254657,-13254657,-13254657,-13254657,-16777216,-1,-1,-1,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-1,0,0,0,0,0,-1,-13254657,
		-13254657,-13254657,-13254657,-1,-1,-1,-1,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-16777216,-1,-1,-1,-13254657,-13254657,-13254657,-13254657,
		-13254657,-1,0,0,0,0,0,0,-1,-1,-13254657,-13254657,-13254657,-1,-1,-1,-1,-1,-1,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-16777216,-1,-1,-1,-13254657,-13254657,
		-13254657,-1,-1,0,0,0,0,0,0,0,-1,-13254657,-13254657,-13254657,-8553091,-8553091,
		-8553091,-8553091,-8553091,-8553091,-8553091,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-16777216,-1,-13254657,-13254657,-13254657,-13254657,-1,0,0,
		0,0,0,0,0,0,0,-1,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-1,0,0,0,0,0,0,0,0,0,0,-1,-1,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-1,-1,0,0,0,0,0,0,0,0,0,0,0,-1,-1,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-1,-1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,-1,-1,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-1,-1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
		-1,-1,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-1,-1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,-1,-1,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-1,-1,0,0,0,0,0,0,0,0,0,0,0,0,
		0,0,0,0,0,0,0,0,0,0,0,-1,-1,-1,-1,-1,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-1,-1,-1,-1,-1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
		0,-1,-1,-1,-1,-1,-1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
};
	public static final int []rgbArraySwitch = new int[]{
		0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
		0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
		0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
		0,0,0,100663295,637271039,-2097873665,-135726849,-136186113,-136251649,-136251649,
		-136251649,-136251649,-136251649,-136251649,-136251649,-136251649,-136251649,-136251649,
		-136251649,-136251649,-136251649,-136251649,-136251649,-136251649,-136251649,-136251649,
		-136251649,-136251649,-136251649,-136251649,-136251649,-136251649,-136186113,-135792385,
		-2098005249,637272063,100663295,0,0,0,0,0,0,369098751,-590593,-6758657,-12073473,
		-13648385,-13779457,-13713921,-13713921,-13713921,-13713921,-13713921,-13713921,
		-13713921,-13713921,-13713921,-13713921,-13713921,-13713921,-13713921,-13713921,
		-13713921,-13713921,-13713921,-13713921,-13713921,-13713921,-13713921,-13713921,
		-13779457,-13648385,-12270337,-6955521,-787457,369098751,0,0,0,0,0,0,-2114257153,
		-6627329,-14370049,-13517313,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13451521,-14435585,-7414785,-2114388737,
		0,0,0,0,0,0,-1443585,-12007681,-13517313,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-16777216,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13451521,-12532737,-1574913,0,0,0,0,0,0,-136055041,-13188865,-13320193,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-1,-16777216,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13779457,-136251649,0,0,0,0,0,0,-136055041,-13123329,
		-13320193,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-1,-1,-16777216,
		-13254657,-13254657,-13254657,-13254657,-13713921,-136251649,0,0,0,0,0,0,-136055041,
		-13123329,-13320193,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,
		-1,-1,-1,-16777216,-13254657,-13254657,-13254657,-13713921,-136251649,0,0,0,0,0,
		0,-136055041,-13123329,-13320193,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-16777216,-13254657,-13254657,-13713921,
		-136251649,0,0,0,0,0,0,-136055041,-13123329,-13320193,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-8553091,-13254657,
		-13254657,-13254657,-13713921,-136251649,0,0,0,0,0,0,-136055041,-13123329,-13320193,
		-13254657,-13254657,-13254657,-13254657,-13254657,-16777216,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-1,-1,-8553091,-13254657,
		-13254657,-13254657,-13254657,-13713921,-136251649,0,0,0,0,0,0,-136055041,-13123329,
		-13320193,-13254657,-13254657,-13254657,-13254657,-16777216,-1,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-1,-8553091,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13713921,-136251649,0,0,0,0,0,0,-136055041,
		-13123329,-13320193,-13254657,-13254657,-13254657,-16777216,-1,-1,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-8553091,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13713921,-136251649,0,0,0,0,0,0,-136055041,
		-13123329,-13320193,-13254657,-13254657,-16777216,-1,-1,-1,-16777216,-16777216,-16777216,
		-16777216,-16777216,-16777216,-16777216,-16777216,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13713921,-136251649,0,0,0,0,0,0,-136055041,
		-13123329,-13320193,-13254657,-16777216,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13713921,-136251649,
		0,0,0,0,0,0,-136055041,-13123329,-13320193,-13254657,-13254657,-8553091,-1,-1,-1,
		-1,-1,-1,-1,-1,-1,-1,-1,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13713921,-136251649,0,0,0,0,0,0,-136055041,-13123329,-13320193,-13254657,
		-13254657,-13254657,-8553091,-1,-1,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-16777216,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13713921,-136251649,0,0,0,0,0,0,-136055041,-13123329,-13320193,
		-13254657,-13254657,-13254657,-13254657,-8553091,-1,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-1,-16777216,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13713921,-136251649,0,0,0,0,0,0,-136055041,-13123329,
		-13320193,-13254657,-13254657,-13254657,-13254657,-13254657,-8553091,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-1,-1,-16777216,-13254657,
		-13254657,-13254657,-13254657,-13713921,-136251649,0,0,0,0,0,0,-136055041,-13123329,
		-13320193,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-16777216,
		-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-16777216,-1,-1,-1,-16777216,
		-13254657,-13254657,-13254657,-13713921,-136251649,0,0,0,0,0,0,-136055041,-13123329,
		-13320193,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-1,-1,-1,-1,
		-1,-1,-1,-1,-1,-1,-1,-1,-16777216,-13254657,-13254657,-13713921,-136251649,0,0,0,
		0,0,0,-136055041,-13123329,-13320193,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-8553091,-13254657,-13254657,-13254657,
		-13713921,-136251649,0,0,0,0,0,0,-136055041,-13123329,-13320193,-13254657,-13254657,
		-13254657,-13254657,-13254657,-16777216,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-1,-1,-8553091,-13254657,-13254657,-13254657,
		-13254657,-13713921,-136251649,0,0,0,0,0,0,-136055041,-13123329,-13320193,-13254657,
		-13254657,-13254657,-13254657,-16777216,-1,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-1,-8553091,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13713921,-136251649,0,0,0,0,0,0,-136055041,-13123329,-13320193,
		-13254657,-13254657,-13254657,-16777216,-1,-1,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-8553091,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13713921,-136251649,0,0,0,0,0,0,-136055041,-13123329,-13320193,
		-13254657,-13254657,-16777216,-1,-1,-1,-16777216,-16777216,-16777216,-16777216,-16777216,
		-16777216,-16777216,-16777216,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13713921,-136251649,0,0,0,0,0,0,-136055041,-13123329,-13320193,
		-13254657,-16777216,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13713921,-136251649,0,0,0,0,0,0,-136055041,
		-13123329,-13320193,-13254657,-13254657,-8553091,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13713921,
		-136251649,0,0,0,0,0,0,-136055041,-13123329,-13320193,-13254657,-13254657,-13254657,
		-8553091,-1,-1,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13713921,-136251649,0,0,0,0,0,0,-136055041,-13188865,-13320193,-13254657,-13254657,
		-13254657,-13254657,-8553091,-1,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13779457,-136251649,0,0,0,0,0,0,-136055041,-13123329,-13320193,
		-13254657,-13254657,-13254657,-13254657,-13254657,-8553091,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13779457,-136251649,0,0,0,0,0,0,-1246721,
		-11351809,-13648385,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,
		-13254657,-13254657,-13254657,-13254657,-13254657,-13254657,-13517057,-12139009,
		-1443585,0,0,0,0,0,0,-2114126081,-5708801,-14107649,-13713921,-13451521,-13451521,
		-13451521,-13451521,-13451521,-13451521,-13451521,-13451521,-13451521,-13451521,
		-13451521,-13451521,-13451521,-13451521,-13451521,-13451521,-13451521,-13451521,
		-13451521,-13451521,-13451521,-13451521,-13451521,-13451521,-13451521,-13451521,
		-13713921,-14238977,-6233601,-2114257153,0,0,0,0,0,0,369098751,-393729,-5511937,
		-10761217,-11811073,-11745281,-11745281,-11745281,-11745281,-11745281,-11745281,
		-11745281,-11745281,-11745281,-11745281,-11745281,-11745281,-11745281,-11745281,
		-11745281,-11745281,-11745281,-11745281,-11745281,-11745281,-11745281,-11745281,
		-11745281,-11745281,-11876609,-10958081,-5839873,-459265,369098751,0,0,0,0,0,0,100663295,
		637534207,-2097348609,-135398657,-135726849,-135661313,-135661313,-135661313,-135661313,
		-135661313,-135661313,-135661313,-135661313,-135661313,-135661313,-135661313,-135661313,
		-135661313,-135661313,-135661313,-135661313,-135661313,-135661313,-135661313,-135661313,
		-135661313,-135661313,-135661313,-135661313,-135726849,-135464449,-2097348865,637534207,
		100663295,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
		0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
		0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
		0,0,0,0,0,0,0,0
		};
	public static final int [] rgbArrayLan = new int[]{
		0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,33554431,50331647,67108863,67108863,
		218103807,738197503,1308622847,1811939327,-1996488705,-1577058305,-1191182337,-855638017,
		-570425345,-335544321,-150994945,-16777217,-1,-65794,-16843010,-34212363,-1973791,
		-2105377,-394759,-201326593,-822149378,-1191248130,-1761673474,1828584957,1023410175,
		335544319,83886079,67108863,50331647,33554431,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
		0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,50331647,150994943,285212671,503316479,
		1174405119,1996488703,-1593835521,-889192449,-268435457,-1,-1,-1,-1,-1,-1,-1,-1,
		-1,-1,-65794,-65794,-65794,-986896,-2894893,-2565928,-2236963,-2763307,-1052689,
		-263173,-65794,-65794,-65794,-1,-218103809,-822083585,-1577058305,1962934271,1107296255,
		436207615,234881023,134217727,33554431,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
		0,0,0,0,0,0,0,0,0,0,0,0,0,0,33554431,184549375,419430399,1040187391,-2046820353,
		-1157627905,-385875969,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-65794,-65794,-131587,
		-131587,-1381654,-5197391,-4803147,-723724,-197380,-1315861,-3421237,-2763307,-1250068,
		-65794,-65794,-65794,-65794,-1,-1,-1,-1,-486539265,-1358954497,2046820351,939524095,
		402653183,184549375,33554431,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
		0,0,0,0,100663295,419430399,989855743,-1996488705,-1006632961,-167772161,-1,-1,-1,
		-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-65794,-65794,-131587,-328966,-1645598,-5263955,
		-7566453,-6053213,-3816251,-986896,-1,-1447447,-3026479,-1907998,-789517,-65794,
		-65794,-65794,-1,-1,-1,-1,-1,-1,-184549377,-1040187393,-1996488705,989855743,419430399,
		100663295,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,184549375,771751935,
		1895825407,-1157627905,-268435457,-1,-1,-1,-1,-65794,-131330,-131330,-131330,-131330,
		-131330,-131330,-131330,-131330,-131330,-131330,-131330,-131330,-131330,-131330,
		-197122,-262915,-328709,-526088,-1115911,-2960687,-6581101,-6382696,-6908268,-5987420,
		-2829100,-460552,-65794,-1579033,-2763307,-1579033,-657931,-65794,-65794,-65794,
		-1,-1,-1,-1,-1,-1,-1,-268435457,-1140850689,1912602623,771751935,184549375,0,0,0,
		0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,117440511,905969663,-2046820353,-788529153,
		-16777217,-1,-1,-1,-1,-65794,-65794,-1313803,-1968904,-1968904,-2034698,-2166026,
		-2166026,-2166026,-2166026,-2166026,-2166026,-2166026,-2166026,-2166026,-2166026,
		-2166026,-2166026,-2231562,-2428427,-3019278,-4003086,-4593682,-7947845,-9991286,
		-6446696,-5724514,-5723998,-3487031,-1907998,-197380,-263173,-1907998,-2697514,-1315861,
		-328966,-65794,-65794,-65794,-1,-1,-1,-1,-1,-1,-1,-16777217,-788529153,-2046820353,
		905969663,117440511,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,50331647,788529151,-2097152001,
		-620756993,-1,-1,-1,-1,-1,-1,-65794,-65794,-1,-7358015,-8994095,-9456190,-11297107,
		-11493717,-11493719,-11493719,-11493719,-11493719,-11493719,-11493719,-11493719,
		-11493719,-11493719,-11493719,-11493719,-11493719,-11493721,-11493726,-11559525,
		-11361636,-10768734,-11166061,-10253956,-6775928,-3751504,-2303280,-3487034,-3552822,
		-1381654,-1,-460552,-2565928,-3092529,-723724,-131587,-65794,-1,-1,-1,-1,-1,-1,-1,
		-1,-1,-1,-620756993,-2097152001,788529151,50331647,0,0,0,0,0,0,0,0,0,0,0,0,0,0,33554431,
		301989887,1543503871,-754974721,-100663297,-1,-1,-1,-1,-1,-1,-65794,-65794,-131845,
		-264201,-9920342,-5123115,-263710,-132397,-132656,-132656,-132656,-132656,-132656,
		-132656,-132399,-132656,-132656,-132912,-198448,-264241,-330034,-395827,-461619,
		-527668,-659255,-659255,-724277,-724020,-1579327,-4408926,-6644847,-4276294,-3158068,
		-4079167,-3092274,-657931,-855567,-4343367,-3417895,-525316,-590852,-525059,-459522,
		-393730,-393730,-328194,-65793,-1,-1,-1,-1,-1,-100663297,-754974721,1543503871,301989887,
		33554431,0,0,0,0,0,0,0,0,0,0,0,50331647,788529151,-1744830465,-335544321,-1,-1,-1,
		-1,-1,-1,-1,-65794,-65794,-131845,-328972,-132622,-10446176,-4136233,-986925,-1118777,
		-1184570,-1184570,-1184570,-1119034,-1118777,-1118777,-1118777,-1119034,-1184570,
		-1250363,-1382205,-1645120,-1908291,-2237512,-2632269,-3027026,-3355991,-3619162,
		-3750747,-3882333,-3882589,-3553358,-6967126,-10911100,-4804696,-4014154,-4539731,
		-4868440,-4079953,-7696761,-8013635,-5578259,-4661016,-3675922,-3150864,-2888206,
		-2953999,-2822670,-524802,-65794,-1,-1,-1,-1,-1,-1,-335544321,-1744830465,788529151,
		50331647,0,0,0,0,0,0,0,0,0,67108863,1191182335,-956301313,-100663297,-1,-1,-1,-1,
		-1,-1,-1,-65794,-65794,-131587,-328966,-526351,-198416,-10512224,-4530728,-1577768,
		-1579571,-1645366,-1645366,-1645364,-1579568,-1579305,-1579299,-1644829,-1645083,
		-1710621,-1776675,-1908264,-2105644,-2303023,-2566195,-2895160,-3224125,-3487296,
		-3684675,-3816518,-3947847,-4013641,-4013383,-6766398,-6957608,-1054508,-5396076,
		-6119031,-7040132,-6251387,-6116713,-8542047,-9198423,-6176827,-6045754,-5649968,
		-5059373,-5584947,-8539202,-2360833,-131587,-65794,-1,-1,-1,-1,-1,-1,-100663297,
		-956301313,1191182335,67108863,0,0,0,0,0,0,0,50331647,1426063359,-620756993,-1,-1,
		-1,-1,-1,-1,-1,-1,-65794,-131587,-131587,-197380,-394759,-855310,-329741,-10512221,
		-4530723,-1840931,-1908264,-1974053,-2039585,-1776669,-1513497,-1250325,-1118482,
		-1184275,-657931,-1052689,-1052946,-1184275,-1184532,-1316118,-1447447,-1579033,
		-1645083,-1776669,-1907998,-1974048,-2237220,-2829357,-3289394,-6767419,-7222578,
		-395042,-1513790,-4474469,-7105925,-2895695,-1973299,-2566195,-2434341,-1447704,
		-1974820,-462612,-2064,-2303271,-9004383,-3346957,-197380,-131587,-65794,-1,-1,-1,
		-1,-1,-1,-1,-620756993,1426063359,50331647,0,0,0,0,0,50331647,1409286143,-469762049,
		-1,-1,-1,-1,-1,-65794,-65794,-65794,-592394,-986896,-723724,-131587,-328966,-460552,
		-921360,-329995,-9658197,-4530720,-1972768,-2105634,-1645083,-1381911,-1118739,-987153,
		-921103,-1184275,-1579034,-1052690,-1184532,-920846,-854282,-920075,-985867,-1051660,
		-1051660,-1117453,-1183246,-1249039,-1249040,-1446417,-1840919,-2234135,-6502706,
		-7289143,-1316141,-3948120,-3882327,-3750735,-3553346,-2895150,-1710876,-2171941,
		-2237221,-3750978,-3751497,-2633792,-5198427,-7560028,-3682608,-1184531,-131587,
		-65794,-65794,-1,-1,-1,-1,-1,-1,-1,-469762049,1409286143,50331647,0,0,0,33554431,
		1090519039,-771751937,-1,-1,-1,-1,-65794,-65794,-65794,-592138,-1316118,-2039844,
		-2303274,-2039587,-1184276,-855310,-460552,-986896,-329994,-8803918,-4530717,-2170142,
		-1776412,-1250325,-1118482,-921103,-1118482,-1579033,-2039843,-2237225,-2369326,
		-2171944,-1906970,-2561555,-2888720,-3085329,-3414292,-3676693,-3808022,-3873558,
		-3873558,-3939094,-4201752,-4792346,-7550507,-11818312,-7090228,-1316139,-3948108,
		-3882306,-3026736,-1974048,-2171170,-1513497,-2894894,-3421498,-2237740,-1513762,
		-1974313,-1447971,-2106926,-4145735,-3158583,-657931,-197380,-65794,-65794,-1,-1,
		-1,-1,-1,-1,-1,-771751937,1090519039,33554431,0,0,369098751,-1325400065,-1,-1,-1,
		-65794,-65794,-65794,-592138,-1052690,-2105636,-3158070,-1974313,-987676,-2040363,
		-2763825,-2105635,-1184275,-855310,-263431,-8410187,-4268316,-2104092,-1513497,-855567,
		-1250068,-1513240,-1908255,-2960689,-2171690,-1250589,-1119005,-1580069,-3355706,
		-6442573,-8410190,-8013372,-8472123,-9457993,-9457997,-9458000,-9458002,-9458002,
		-9458004,-9392214,-9853026,-9785950,-4726325,-1250605,-3882308,-2960943,-1447447,
		-1907997,-3025965,-3289652,-2302759,-1512986,-2039331,-2763052,-2762795,-3289138,
		-5131857,-4211531,-5067617,-3026222,-263173,-131587,-65794,-65794,-1,-1,-1,-1,-1,
		-1,-1,-1325400065,369098751,0,33554431,2113929215,-1,-1,-1,-65794,-65794,-592138,
		-1447447,-2171171,-2368551,-2368810,-1381917,-1776674,-2434346,-1644830,-1908260,
		-3421499,-4672083,-1250069,-1,-8673102,-4267802,-2039070,-1447447,-1513240,-2434085,
		-2895150,-2500395,-1513245,-1776415,-2500138,-2499880,-2762796,-3421497,-4149087,
		-6188154,-3882820,-1711659,-1250860,-1118770,-1119035,-1185084,-1250876,-1251133,
		-1316670,-1382720,-1843012,-1907520,-2237246,-3750723,-2500392,-2171429,-5263962,
		-2696742,-1775378,-2301468,-3091240,-3157033,-2893349,-2630949,-5263702,-9408401,
		-6513765,-5790305,-4144960,-1644826,-657931,-65794,-65794,-65794,-1,-1,-1,-1,-1,
		-1,-1,2113929215,33554431,503316479,-687865857,-1,-1,-65794,-65794,-131844,-3881789,
		-3354672,-2697000,-1710105,-1776155,-2565414,-2960171,-2499363,-3552053,-7368303,
		-5395803,-6909823,-3882306,-198665,-9525841,-3610129,-2303013,-2435115,-3750460,
		-3223085,-1972762,-1907226,-2696484,-3090986,-2893862,-2104349,-6315618,-8684418,
		-6974837,-6909046,-5855835,-3355701,-2763570,-2961214,-2895431,-2895440,-2961233,
		-3027026,-3093076,-3224662,-3356247,-3487574,-3553357,-3618883,-2829361,-3158586,
		-6515326,-2369846,-3223341,-3222308,-3287841,-3090719,-2959654,-3355707,-6449016,
		-8224382,-5130563,-4209978,-4013374,-3750203,-2105377,-1052689,-131587,-65794,-65794,
		-1,-1,-1,-1,-1,-1,-687865857,503316479,1795162111,-1,-1,-1,-65794,-131587,-328967,
		-7239045,-2171175,-2104344,-2761758,-3156515,-3025441,-2893859,-2368291,-5330014,
		-8816265,-6776420,-6315616,-5592664,-4146760,-11039594,-3085585,-2368806,-4079943,
		-3356999,-2237480,-2696481,-3222307,-3353378,-3090464,-2827810,-3026745,-7500929,
		-7631987,-5328457,-4934218,-4276548,-3289909,-2763307,-2829614,-2829357,-2829621,
		-2763836,-2763841,-2829635,-2895428,-3027270,-3158852,-3290176,-3421502,-2697779,
		-2961208,-8159891,-5659500,-4145482,-4013115,-3157289,-3354413,-3815740,-4145744,
		-6580602,-7894903,-3747876,-3024927,-3618098,-3026222,-3224116,-3026479,-1907998,
		-263173,-65794,-65794,-1,-1,-1,-1,-1,-1,1795162111,-1509949441,-1,-1,-65794,-131587,
		-263176,-592402,-7042445,-4673379,-3487290,-3025961,-3353635,-3025184,-3223341,-3421503,
		-5923188,-7763835,-5130046,-4209460,-4473411,-4803919,-6904152,-4274745,-1381653,
		-3290168,-5593965,-4475482,-3684670,-3420975,-3157029,-3223082,-3486776,-4080211,
		-7567495,-6841955,-3879721,-3419947,-3223857,-3421237,-2894893,-2960686,-2500392,
		-2105634,-2303013,-2368808,-2368811,-2434605,-2631984,-2763569,-2960947,-3224121,
		-2631986,-2763833,-6909822,-4605515,-6315358,-6644578,-3224117,-3882052,-4210760,
		-4013900,-6514551,-8026233,-3090203,-1050881,-2038548,-2959652,-3223086,-3421236,
		-4671561,-2565928,-197380,-65794,-65794,-1,-1,-1,-1,-1,-1509949441,-704972038,-65794,
		-1,-65794,-197380,-460556,-987162,-7567756,-6053483,-4934997,-5460820,-3355188,-3552568,
		-4144451,-3882055,-5988722,-8026750,-3748392,-2366738,-3353895,-3552563,-3159351,
		-3685181,-3223858,-5263961,-5593187,-4868946,-6447716,-5066061,-3486778,-3882050,
		-4079173,-4079952,-7698822,-6775906,-2563604,-1774604,-2630687,-2828583,-2828843,
		-3026480,-3421238,-2500392,-1842462,-1776669,-1776669,-1907998,-2039841,-2302756,
		-2566186,-2960948,-2566193,-2829626,-6054775,-2303278,-4999236,-6249563,-3816515,
		-3882572,-4014414,-4014415,-6514551,-8026490,-3353377,-919811,-526084,-920324,-2893083,
		-4736578,-5856093,-4868685,-328970,-131588,-65794,-1,-1,-1,-1,-65794,-704972038,
		-135533589,-986896,-657931,-263430,-328966,-658192,-1447456,-5922934,-3815746,-6117718,
		-7433323,-3816000,-3948361,-4276554,-3750726,-5857136,-8092799,-4011564,-1248774,
		-1117189,-1841170,-3288615,-3420463,-5921111,-8750993,-4211797,-3289139,-6709596,
		-5920598,-3947589,-4014155,-4079433,-3948368,-7633030,-6973284,-2761496,-854018,
		-722947,-1578253,-3419945,-3947064,-4802891,-4408132,-1776412,-1447704,-1316118,
		-1316118,-1513497,-1776669,-2171428,-2697776,-2500399,-2895418,-6515329,-2830405,
		-3618104,-3684155,-3882312,-4080724,-4080725,-4080469,-6449016,-7960695,-3287582,
		-854019,-328964,-788998,-2234637,-5327943,-7040111,-4539723,-592145,-197382,-65794,
		-65794,-1,-197380,-592138,-986896,-135533589,-136388898,-1842205,-1315861,-789517,
		-657931,-986900,-1842214,-6055037,-2435902,-3289392,-5394256,-4079687,-4014414,-4343122,
		-3816780,-5857393,-8092799,-4077358,-1446155,-394243,-591107,-1971723,-4341303,-7105385,
		-10133158,-4870761,-2304052,-3683897,-4407877,-3948364,-4146260,-4146002,-4014676,
		-7633029,-6973284,-2827290,-788485,-262915,-656897,-3156254,-5262665,-6316131,-4868686,
		-1776413,-1315861,-1052689,-986896,-1118482,-1381911,-1842463,-2500397,-2434606,
		-2698295,-6383998,-5000032,-6906218,-2961722,-3487816,-4278622,-4212830,-4212830,
		-6712446,-8223608,-2959130,-590849,-328449,-985863,-2301202,-5262406,-7368818,-3815998,
		-855571,-394759,-131587,-131587,-263173,-657931,-1250068,-1842205,-136388898,-707801137,
		-2829100,-2171170,-1381654,-1118482,-1316122,-2171435,-6121088,-4343391,-4801871,
		-3553084,-3619398,-4146520,-4343900,-3817812,-5988724,-7960699,-3945513,-1183238,
		-262914,-723207,-1839881,-4472376,-7697009,-10001316,-4936813,-6380652,-4999506,
		-3224379,-3685709,-4343901,-4277851,-4146524,-7830408,-6775646,-2432531,-525569,
		-262915,-854532,-3222049,-5460045,-6185057,-4737355,-1645083,-1052689,-723981,-658188,
		-723981,-1052689,-1513497,-2368808,-2368814,-2829626,-6384257,-3553616,-5065561,
		-3225155,-3225419,-4213093,-4476521,-4213606,-5857913,-8948112,-5065028,-1906964,
		-1248777,-854275,-2103567,-5525065,-7237489,-4144959,-1184275,-526345,-263173,-263173,
		-657931,-1250068,-2105377,-2829100,-707801137,-1513897021,-3815995,-3026479,-2171170,
		-1579033,-1710623,-2434607,-5660538,-5065827,-6906479,-3422019,-3356743,-4278880,
		-4410467,-3818589,-6055291,-8882313,-4340784,-1314567,-328193,-723206,-1840395,-4406840,
		-8092279,-10725041,-4344677,-5525859,-5066073,-3224897,-3620434,-4410470,-4344677,
		-3884130,-7765134,-8157815,-3683113,-1117446,-656642,-854018,-3222048,-5460045,-6579558,
		-3947581,-1513240,-789774,-526602,-395016,-460552,-657931,-1052689,-2039841,-2368556,
		-2961473,-7502483,-4804715,-3620180,-2764870,-3423574,-4345970,-3951211,-4279917,
		-3950956,-5793158,-9672870,-6907748,-3749426,-2893085,-2169100,-5393735,-5395285,
		-4276546,-2105634,-657931,-329223,-526345,-1118482,-2039584,-3026479,-3815995,-1513897021,
		1790490808,-4802890,-4210753,-3158065,-2237220,-2105638,-2697779,-6581382,-3883096,
		-3751245,-3290950,-2765127,-3753058,-4542060,-4345450,-4739694,-7633805,-7237491,
		-4078392,-2433307,-1314825,-1445378,-4998977,-6710628,-9935265,-6844292,-4080729,
		-3356745,-2567230,-3160919,-3950697,-4410989,-4279402,-4542832,-7502481,-7764095,
		-4802371,-2827808,-1511429,-3024155,-5393995,-5724250,-3552823,-1184275,-723724,
		-460552,-328966,-328966,-460552,-789517,-1710876,-2368813,-3092810,-8489384,-5923718,
		-6778506,-6910087,-7435907,-7502481,-5859471,-4017785,-5333125,-7765910,-9736853,
		-7434607,-4408389,-5526098,-4735547,-6380890,-5263698,-1973791,-4868683,-921360,
		-657931,-986896,-1842205,-3026479,-4210753,-4802890,1790490808,497789867,-693655641,
		-5395027,-4276546,-3223858,-2697518,-3026745,-7304591,-6515326,-4673122,-3357262,
		-5068396,-5858952,-4017010,-3951213,-3556716,-5464971,-10660025,-8684419,-5263182,
		-4143927,-2498066,-5919821,-5328978,-4671561,-7961732,-7041409,-5199467,-5265258,
		-7107464,-6582674,-4280696,-3425388,-4675456,-8160674,-10658729,-7171436,-4802889,
		-4406841,-4406579,-6249561,-4013632,-3158322,-2829100,-657931,-526602,-1052689,-986896,
		-921103,-986896,-1842205,-2763833,-2697800,-4935538,-7240097,-8094368,-7369345,-3618623,
		-4210758,-7304325,-9344943,-8816263,-4668715,-6181956,-5460561,-526347,-3684410,
		-5395026,-7368816,-6250335,-197380,-6184543,-3026480,-1315861,-1710619,-2829100,
		-4210753,-5395027,-693655641,497789867,25132927,2124324510,-6447715,-5592406,-4408132,
		-3487293,-3421762,-7765658,-5792384,-7436431,-9147041,-6645868,-6975095,-7634844,
		-5794196,-6977173,-7238009,-8617589,-6249822,-3355449,-5066322,-5394255,-7565680,
		-6711148,-1973798,-5263190,-5329760,-8291221,-7896198,-5066322,-6053736,-7634843,
		-8424875,-7566719,-6182475,-7498595,-3947327,-2171434,-4868432,-6513253,-8553092,
		-4671565,-2434347,-4868687,-1513245,-986902,-3552831,-5197914,-5131864,-4210763,
		-5197659,-2829633,-3355985,-3750748,-5527670,-9803947,-2828872,-66076,-1316131,-2763056,
		-5394774,-7696492,-6313543,-3419427,-4539974,-1579290,-1,-789518,-2039589,-1776420,
		-1710888,-7368827,-2631992,-2039851,-2960689,-4276546,-5592406,-6447715,2107547294,
		25132927,0,379362460,-1315794286,-6908266,-5789785,-4539982,-3948110,-4737633,-7831452,
		-8883106,-5131865,-2302759,-2302499,-4276806,-8752022,-9604746,-4602407,-4997169,
		-5591896,-658201,-1118758,-3289926,-6381937,-5131872,-329505,-5000545,-4671326,-4868961,
		-2500669,-1250083,-1973286,-3947845,-7961471,-8222572,-4273185,-4801343,-3684934,
		-263707,-460833,-2698048,-4737116,-1842487,-2566210,-6447990,-1908535,-1052970,-1053230,
		-855856,-987187,-1184821,-1776695,-2369085,-3093064,-3619156,-3882333,-4342883,-6645375,
		-5329518,-658220,-329246,-1447459,-2631213,-4802377,-5065806,-2565932,-4671309,-4342347,
		-2434610,-2434616,-4079445,-5790065,-3553108,-2566210,-3158337,-4408137,-5855579,
		-6974059,-1332637295,362256279,0,0,46120895,1116770448,-745961079,-7105645,-5855841,
		-4868700,-4277085,-4408420,-9211552,-5395303,-1184550,-657942,-1315865,-2368293,
		-4933962,-7367525,-3815225,-3816009,-4605790,-855604,-299,-329263,-197677,-4408419,
		-6316411,-2960975,-6184825,-6316411,-1974080,-329506,-986911,-1841956,-4341828,-6249568,
		-3552323,-3882069,-4803432,-1579327,-299,-460848,-3553112,-6250618,-3355991,-1513529,
		-1184816,-921386,-790060,-855598,-1118768,-1513776,-2171699,-2829625,-3290437,-3684945,
		-3882332,-3750747,-5000811,-7171718,-3684697,-263461,-395036,-526617,-2829369,-7566207,
		-789798,-2763590,-3750486,-3553112,-2763598,-2237256,-3224405,-3684947,-4671317,
		-6052961,-7303025,-779712890,1083018637,25132927,0,0,25132927,95597234,1502120072,
		-427522940,-7171699,-5987176,-5066599,-4474469,-3553112,-6184825,-7829903,-3816275,
		-592415,-460567,-1447460,-2566197,-4145235,-3750485,-3882333,-4540262,-4671847,-4737640,
		-4869226,-4277090,-3684954,-3619418,-3750747,-4342883,-6645375,-4934762,-1052978,
		-329249,-1052966,-921383,-4803170,-4408676,-3751004,-4145761,-4474469,-4145504,-3421783,
		-2763855,-2369096,-1711157,-1250341,-921116,-789530,-789788,-921373,-1250079,-1710883,
		-2303018,-2895156,-3290432,-3619405,-3882585,-4014175,-3751004,-5592947,-7237511,
		-2895183,-3882073,-7632267,-5132140,-299,-395056,-921398,-1381948,-3751004,-3553369,
		-4276830,-5066079,-6316393,-7631991,-478117760,1417970820,46120895,0,0,0,25132927,
		64279764,161192859,1636074628,-494631805,-7105908,-6053229,-5264236,-4540518,-4868969,
		-4013662,-6776961,-7895439,-3158352,-197672,-295,-4605797,-6184825,-3553626,-4211554,
		-4079711,-3816797,-4211297,-4145504,-4145504,-4145761,-4145761,-4211297,-4145761,
		-4737640,-7632268,-4079454,-263470,-2829390,-7500682,-4277090,-4145504,-4014175,
		-4013918,-3684954,-3290197,-2763848,-2171704,-1513766,-1118488,-789777,-657933,-592140,
		-657933,-855312,-1250069,-1710877,-2237221,-2763567,-3224122,-3553350,-3816529,-3948379,
		-4145761,-3751004,-6250618,-4211040,-1710912,-921398,-3948126,-3685211,-3750747,
		-3816540,-4408676,-4737633,-5395298,-6645100,-7829627,-595821445,1451130494,58687359,
		0,0,0,0,0,46120895,95597234,279422887,1619955339,-662075003,-40726133,-6185070,-5527147,
		-5066861,-4606311,-4803433,-4211040,-7303304,-6645375,-6447996,-6053240,-4737897,
		-4079966,-4014168,-4013909,-3948115,-3882578,-3882576,-3882574,-3882573,-3948367,
		-4013907,-4014170,-4014175,-3685211,-5527154,-7763853,-6184825,-3487576,-4014173,
		-3948118,-3816529,-3553613,-3290183,-2829886,-2303281,-1776420,-1184536,-789775,
		-592138,-460552,-394759,-395016,-526345,-723724,-1052689,-1513240,-2039584,-2500393,
		-2960947,-3355710,-3619145,-3816532,-3882333,-4277347,-4145504,-3751004,-3816540,
		-4277090,-4342882,-4474465,-4671839,-5066335,-5790051,-6908527,-91978621,-881165702,
		1283292541,110400660,25132927,0,0,0,0,0,25132927,64279764,163100856,397126571,1452840341,
		-1131376756,-174943859,-6447980,-5790314,-5395562,-5066604,-4737897,-4869226,-4737640,
		-4540262,-4408676,-4014172,-3882577,-3750983,-3618881,-3553086,-3487293,-3421498,
		-3421497,-3487290,-3553341,-3619137,-3750727,-3816528,-3882329,-3751004,-3553626,
		-3487833,-3685208,-3750735,-3553605,-3355708,-3026998,-2697776,-2237224,-1710879,
		-1184533,-789774,-526345,-394759,-263173,-197380,-197380,-263173,-394759,-526345,
		-789774,-1184532,-1645083,-2171427,-2697773,-3092534,-3421505,-3684939,-3882324,
		-4013913,-4145499,-4277338,-4474200,-4671574,-5000536,-5526877,-6250597,-7303026,
		-276791168,-1568965765,948011393,128164771,46120895,25132927,0,0,0,0,0,25132927,
		46120895,95597234,213169332,481407409,1151772326,-1785227883,-393178994,-57108589,
		-6185065,-5790056,-5395560,-5066856,-4803686,-4540513,-4277338,-4079695,-3750467,
		-3487290,-3224116,-3026737,-2763564,-2631978,-2697771,-2763564,-2895150,-3026737,
		-3158583,-3289916,-3421764,-3487563,-3487567,-3422030,-3355975,-3158589,-2960947,
		-2631979,-2303013,-1907998,-1447704,-1052689,-723724,-460552,-328966,-197380,-131844,
		-131587,-131587,-131587,-197380,-263173,-460552,-657931,-987153,-1513240,-2105377,
		-2566186,-3026739,-3421499,-3816260,-4079434,-4408398,-4737361,-5066323,-5526872,
		-6184801,-6908524,-74938489,-612401538,1853915264,529502095,145731503,64279764,25132927,
		0,0,0,0,0,0,0,25132927,64279764,113229759,263369394,498379691,917613740,1889442715,
		-1214999663,-292318832,-6842733,-6382184,-5987429,-5592672,-5263707,-4868947,-4474187,
		-4079426,-3618874,-3158065,-2763564,-2434599,-2236963,-2105634,-2039841,-2105634,
		-2303013,-2434600,-2566187,-2697775,-2763571,-2763573,-2763573,-2566191,-2368809,
		-2105378,-1776669,-1381911,-1052946,-789517,-526345,-394759,-197380,-197380,-197380,
		-131587,-131587,-197380,-263173,-328966,-394759,-592138,-855310,-1184275,-1579290,
		-2105377,-2697514,-3289908,-3816252,-4276804,-4803148,-5263699,-5790042,-6447972,
		-7105902,-7763832,-478183553,-1753251969,1149798536,296529068,145731503,64279764,
		46120895,25132927,0,0,0,0,0,0,0,25132927,46120895,64279764,128164771,246263213,448113579,
		732868270,1152297642,1989974682,-1265528945,-460485235,-24146290,-7040110,-6711145,
		-6250593,-5790042,-5329234,-4737354,-4144960,-3618616,-3158065,-2763307,-2368806,
		-2236963,-2039841,-2039584,-2039841,-2105377,-2105635,-2171428,-2105635,-2105378,
		-1842463,-1579290,-1381654,-1118739,-921103,-723724,-592138,-460809,-394759,-394759,
		-526345,-526345,-657931,-723724,-789517,-921103,-1118482,-1315861,-1710619,-1973791,
		-2368549,-2894893,-3421237,-4013374,-4671561,-5329491,-5921628,-6513765,-7171438,
		-7698039,-25067135,-646087813,-1736672132,1283950471,429825694,213169332,128164771,
		64279764,46120895,25132927,0,0,0,0,0,0,0,0,0,25132927,46120895,64279764,113229759,
		195931565,330675637,498379691,699511217,1017620391,1603966871,-1802991737,-863927935,
		-209748097,-8289919,-7960954,-7500403,-6908523,-6316129,-5723992,-5131855,-4605511,
		-4079167,-3684409,-3289908,-3026736,-2829100,-2631721,-2565928,-2434342,-2303013,
		-2171427,-2039584,-1776669,-1644826,-1513497,-1381654,-1315861,-1381654,-1315861,
		-1381654,-1447447,-1579033,-1710619,-1842205,-2039584,-2236963,-2500135,-2829100,
		-3158065,-3552823,-4013374,-4539718,-5131855,-5789785,-6381922,-7039852,-7697782,
		-8224126,-8684677,-243960459,-1032489611,2121824376,1048937861,429825694,195931565,
		145731503,77569951,64279764,46120895,25132927,0,0,0,0,0,0,0,0,0,0,0,25132927,25132927,
		64279764,77569951,145731503,213169332,296529068,380875699,464432814,565028518,848729750,
		1333953154,-1821150349,-965709969,-177114767,-9013642,-8618884,-8224126,-7697782,
		-7105645,-6645094,-6184543,-5658199,-5197648,-4802890,-4539718,-4276546,-4013374,
		-3881788,-3618616,-3487030,-3224115,-3092272,-3026479,-2960686,-2894893,-2960686,
		-3026479,-3158065,-3355444,-3487030,-3750202,-3947581,-4210753,-4473925,-4802890,
		-5197648,-5658199,-6184543,-6645094,-7171438,-7763575,-8224126,-8684677,-9145228,
		-193957776,-1016238739,-1922208403,1114994037,562528127,245013146,145731503,113229759,
		77569951,64279764,46120895,25132927,25132927,0,0,0,0,0,0,0,0,0,0,0,0,0,0,25132927,
		46120895,64279764,77569951,113229759,145731503,163100856,178628005,178628005,195931565,
		327978124,494894975,997356146,2070571626,-1352046231,-496276629,-9474193,-9276814,
		-8947849,-8618884,-8158333,-7895161,-7566196,-7105645,-6842473,-6513765,-6316386,
		-6184543,-5921371,-5723992,-5592406,-5460820,-5395027,-5395027,-5460820,-5592406,
		-5723992,-5921371,-6184543,-6381922,-6579301,-6908266,-7171438,-7566196,-7895161,
		-8224126,-8618884,-8947849,-9276814,-9539986,-496342422,-1368889240,2053728617,963472749,
		444231792,226459519,77569951,64279764,64279764,64279764,46120895,25132927,25132927,
		0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,25132927,46120895,46120895,64279764,64279764,
		64279764,64279764,64279764,64279764,46120895,58687359,141520751,242644598,426470251,
		1097295719,1936156519,-1637324696,-915904408,-278172821,-9605779,-9474193,-9277071,
		-9145228,-8947849,-8816263,-8684677,-8553091,-8355712,-8289919,-8158333,-8158333,
		-8158333,-8158333,-8289919,-8355712,-8553091,-8684677,-8816263,-8947849,-9145228,
		-9342607,-9474193,-9605779,-278172821,-915904408,-1637324696,1936156519,1097295719,
		426470251,225143659,141520751,46120895,25132927,25132927,25132927,25132927,25132927,
		25132927,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,25132927,25132927,25132927,
		25132927,25132927,25132927,25132927,25132927,0,0,0,0,25132927,37699391,58687359,
		58687359,208958580,728196967,1298688104,1801938791,-2006423448,-1586927255,-1201117080,
		-865506967,-580294295,-345347478,-160732309,-26514581,-9671572,-9671572,-26514581,
		-160732309,-345347478,-580294295,-865506967,-1201117080,-1587058841,-2006423448,
		1801938791,1298688104,728196967,208958580,58687359,58687359,37699391,25132927,0,
		0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0
		};
	public static final int[] rgbArrayHost = new int[]{
			0,0,0,0,0,0,0,0,0,0,-11842741,-11842741,-11842741,-11842741,-11842741,-11842741,
			0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,-11842741,-11842741,-11842741,-6974059,-1,-1,-1,
			-1,-6974059,-11842741,-11842741,-11842741,0,0,0,0,0,0,0,0,0,0,0,0,0,-11842741,-6974059,
			-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-6974059,-11842741,0,0,0,0,0,0,0,0,0,0,-11842741,-11842741,
			-6974059,-1,-855567,-4540491,-5330784,-2896709,-5266020,-11437955,-4927789,-1,-1,
			-1,-1,-6974059,-11842741,-11842741,0,0,0,0,0,0,0,-11842741,-6974059,-1,-328966,-4210753,
			-6382184,-4408655,-2040366,-1974573,-1777451,-3093309,-6184291,-5066061,-394759,
			-1,-1,-1,-1,-6974059,-11842741,0,0,0,0,0,0,-11842741,-328966,-3684409,-6579302,-5000273,
			-2368811,-2236968,-2697518,-2829105,-2565932,-3289912,-4014152,-6055032,-4342340,
			-1,-1,-1,-1,-1,-11842741,0,0,0,0,0,-11842741,-328966,-6250851,-6183256,-2828070,
			-2433571,-3025707,-3025708,-3091244,-2696485,-3486516,-9079435,-7368818,-8357010,
			-6842731,-328966,-1,-1,-1,-1,-6974059,-11842741,0,0,0,-11842741,-6974059,-1052689,
			-7173512,-2106673,-3222309,-3287843,-3287843,-3288100,-3024930,-2433828,-5198688,
			-8553607,-6250075,-5065804,-5460563,-6974059,-3092272,-65794,-1,-1,-1,-6974059,-11842741,
			0,0,-11842741,-1,-921103,-7699081,-5396846,-3817033,-3420978,-3419943,-2959135,-3354671,
			-3684932,-5922674,-8224385,-4801080,-4472632,-4736838,-3487031,-5460820,-5066062,
			-657931,-1,-1,-1,-11842741,0,0,-11842741,-1,-921103,-7370628,-6053738,-4934482,-5592149,
			-3289651,-3618363,-4210500,-3816262,-5791087,-8355715,-3814184,-1642759,-3090977,
			-4341820,-3684410,-4013375,-5987164,-1052689,-1,-1,-11842741,0,-11842741,-6974059,
			-1,-986896,-6514808,-3092535,-5788755,-8025716,-3947582,-3750726,-4145483,-3750983,
			-5791086,-8355715,-4011564,-1248519,-1051911,-1380618,-3682860,-4671043,-5132113,
			-4868683,-1,-1,-6974059,-11842741,-11842741,-1,-1,-986895,-6844288,-2501954,-3025965,
			-4604738,-4145222,-3882829,-4211794,-3817037,-5791086,-8355715,-4077356,-1182982,
			-526085,-788998,-1905929,-5064770,-7040110,-4539718,-1,-1,-1,-11842741,-11842741,
			-1,-1,-986895,-6712446,-3686231,-4933971,-3421243,-3487554,-4080726,-4278362,-3883605,
			-5791346,-8289921,-3946026,-1117190,-328707,-788998,-2103566,-4999234,-6974060,-4605511,
			-1,-1,-1,-11842741,-11842741,-1,-1,-986895,-6449787,-4277338,-8089467,-3553861,-3224899,
			-4147038,-4410211,-4015710,-5857912,-8750471,-4209200,-1182982,-591363,-920327,-1972237,
			-5196355,-7632247,-3684409,-1,-1,-1,-11842741,-11842741,-1,-1,-986639,-6975874,-3291728,
			-3553866,-3488073,-3159625,-4081764,-4476780,-4345194,-4542573,-8357270,-7171439,
			-3683634,-2235671,-1314568,-1774602,-5459528,-7303282,-4868683,-1,-1,-1,-11842741,
			-11842741,-6974059,-1,-986896,-7436423,-6317946,-5067878,-3422800,-3752278,-4937848,
			-4083056,-4411762,-4017006,-4214646,-10199735,-8618883,-4671301,-4012083,-2563858,
			-5525577,-5526871,-4342339,-3289651,-1,-6974059,-11842741,0,-11842741,-1,-657931,
			-7896461,-5792376,-7501963,-8357272,-7961731,-7895941,-7306138,-4807043,-6056845,
			-8357008,-8617334,-6908006,-3684410,-5461078,-5393995,-6907490,-6382179,-1184276,
			-6710887,-1,-11842741,0,0,-11842741,-1,-1,-789517,-4936031,-8028564,-7303283,-2236705,
			-1776153,-5395029,-9541285,-10131347,-3812631,-4800043,-5591890,-1,-2105377,-4145217,
			-6513509,-6118749,-328966,-5855578,-3552823,-11842741,0,0,-11842741,-6974059,-1,
			-1,-1052690,-7960954,-3881530,-986896,-1381654,-1184274,-2499876,-5723219,-7498595,
			-3551272,-4342337,-3092529,-1,-1,-789517,-921103,-2565928,-7237231,-2302756,-11842741,
			0,0,0,-11842741,-6974059,-1,-1,-328966,-3684409,-7566196,-2302756,-855310,-1250068,
			-1250069,-3026480,-5460821,-2894893,-4276546,-5329234,-3487030,-3158065,-4737097,
			-5987164,-2500135,-11842741,0,0,0,0,0,-11842741,-1,-1,-1,-1,-921103,-5197648,-5723992,
			-1513240,-855310,-460552,-1644826,-8553091,-263173,-1579033,-3223858,-3355444,-2236963,
			-1184275,-11842741,0,0,0,0,0,0,-11842741,-6974059,-1,-1,-1,-1,-65794,-2039584,-5855578,
			-4276546,-4079167,-7895161,-4671304,-131587,-197380,-394759,-789517,-1,-6974059,
			-11842741,0,0,0,0,0,0,0,-11842741,-11842741,-6974059,-1,-1,-1,-1,-197380,-6316129,
			-4671304,-1118482,-197380,-1,-1,-1,-6974059,-11842741,-11842741,0,0,0,0,0,0,0,0,
			0,0,-11842741,-6974059,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-6974059,-11842741,0,0,0,0,
			0,0,0,0,0,0,0,0,0,-11842741,-11842741,-11842741,-6974059,-1,-1,-1,-1,-6974059,-11842741,
			-11842741,-11842741,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,-11842741,-11842741,-11842741,
			-11842741,-11842741,-11842741,0,0,0,0,0,0,0,0,0,0
			};
}
