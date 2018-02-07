package lia.util.ABPing.ConfigGenApp;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Vector;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

/**
 * This class should be a helper for generating ABPingFarmConfig files
 */
public class ConfigGenerator extends JPanel 
			implements ActionListener, MouseListener, MouseMotionListener {
	JPopupMenu fileMenu;
	JPopupMenu nodeMenu;
	JPopupMenu edgeMenu;
	NodeProperties nodeProp;
	JFrame frame;
	
	private int LEFT_BUTTON = MouseEvent.BUTTON1;
	private int RIGHT_BUTTON = MouseEvent.BUTTON3;
	private int MIDDLE_BUTTON = MouseEvent.BUTTON2;
	
	private int LINK_HIGHLIGHT_DISTANCE = 1;
	
	Vector nodes;
	Vector edges;
	Point tableDelta;
	
	Node selNode;
	Edge selEdge;
	Node pickedNode;
	Edge pickedEdge;
	boolean dragged;
	
	boolean movingTable;
	Point oldTableDelta;
	
	Point mousePos;
	
	ConfigGenerator(JFrame frame){
		super();
		this.frame = frame;
		
		buildFileMenu();
		buildNodeMenu();
		buildEdgeMenu();
		buildMainBoard();
		nodeProp = new NodeProperties(frame);
		nodes = new Vector();
		edges = new Vector();
		mousePos = new Point();
		tableDelta = new Point();
		oldTableDelta = new Point();
	}
	
	void buildFileMenu(){
		fileMenu = new JPopupMenu("File");

		JMenuItem newNode = new JMenuItem("Add node");
		fileMenu.add(newNode); 
		newNode.setActionCommand("newNode");
		newNode.addActionListener(this);

		fileMenu.addSeparator();

		JMenuItem openCfg = new JMenuItem("Open config");
		fileMenu.add(openCfg); 
		openCfg.setActionCommand("openCfg");
		openCfg.addActionListener(this);
		
		JMenuItem saveCfg = new JMenuItem("Save config");
		fileMenu.add(saveCfg);
		saveCfg.setActionCommand("saveCfg");
		saveCfg.addActionListener(this);

		JMenuItem exportConf = new JMenuItem("Export config");
		fileMenu.add(exportConf);
		exportConf.setActionCommand("exportConf");
		exportConf.addActionListener(this);

		fileMenu.addSeparator();

		JMenuItem exit = new JMenuItem("Exit");
		fileMenu.add(exit);
		exit.setActionCommand("exit");
		exit.addActionListener(this);
	}
	
	void buildNodeMenu(){
		nodeMenu = new JPopupMenu("Node");
		
		JMenuItem editNode = new JMenuItem("Edit node");
		nodeMenu.add(editNode); 
		editNode.setActionCommand("editNode");
		editNode.addActionListener(this);
		
		JMenuItem delNode = new JMenuItem("Delete node");
		nodeMenu.add(delNode); 
		delNode.setActionCommand("delNode");
		delNode.addActionListener(this);
	}

	void buildEdgeMenu(){
		edgeMenu = new JPopupMenu("Edge");
		
		JMenuItem delEdge = new JMenuItem("Delete edge");
		edgeMenu.add(delEdge); 
		delEdge.setActionCommand("delEdge");
		delEdge.addActionListener(this);
	}

	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();
		if(cmd.equals("exit")) {
			System.exit(0);
		}else if(cmd.equals("newNode")){
			nodeProp.setData("", "", "");
			nodeProp.setVisible(true);
			if(nodeProp.saveData){
				Node n = new Node();
				n.nick = nodeProp.getNick();
				n.hostname = nodeProp.getHostname();
				n.ipAddress = nodeProp.getIpAddress();
				n.pos.setLocation(mousePos);
				nodes.add(n);
			}
			repaint();
		}else if(cmd.equals("editNode")){
			pickedNode = pickedEdge.a;
			nodeProp.setData(pickedNode.nick, pickedNode.hostname, pickedNode.ipAddress);
			nodeProp.setVisible(true);
			if(nodeProp.saveData){
				pickedNode.nick = nodeProp.getNick();
				pickedNode.hostname = nodeProp.getHostname();
				pickedNode.ipAddress = nodeProp.getIpAddress();
				repaint();
			}
			pickedNode = null;
			pickedEdge = null;
			selNode = null;
			selEdge = null;
		}else if(cmd.equals("delNode")){
			pickedNode = pickedEdge.a;
			if(JOptionPane.showConfirmDialog(this, "Do you really want to delete node "
							+pickedNode.nick+" ?", "Confirm", JOptionPane.YES_NO_OPTION)
					== JOptionPane.YES_OPTION){
				nodes.remove(pickedNode);
				// remove edges containing this node on any side
				for(int i=0; i<edges.size(); i++){
					Edge m = (Edge) edges.get(i);
					if((m.a == pickedNode) || (m.b == pickedNode)){
						edges.remove(i);
						i--;
					}
				}
				repaint();
			}
			pickedNode = null;
			pickedEdge = null;
			selNode = null;
			selEdge = null;
		}else if(cmd.equals("delEdge")){
			if(JOptionPane.showConfirmDialog(this, "Do you really want to delete edge\n"
							+"between "+pickedEdge.a.nick+" and "+pickedEdge.b.nick
							+" ?", "Confirm", JOptionPane.YES_NO_OPTION)
					== JOptionPane.YES_OPTION){
				edges.remove(pickedEdge);
			}
			selEdge = null;
			pickedEdge = null;
			repaint();
		}else if(cmd.equals("saveCfg")){
			JFileChooser chooser = new JFileChooser();			
			if(chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION){
				System.out.println("Saving config to "+chooser.getSelectedFile().getName()+"...");
				try{
					ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(chooser.getSelectedFile()));
					oos.writeObject(nodes);
					oos.writeObject(edges);
					oos.close();
					System.out.println("Success!");
				}catch(Exception ex){
					ex.printStackTrace();
					JOptionPane.showMessageDialog(this, "Error saving config... check console.");
				}
			}
		}else if(cmd.equals("openCfg")){
			JFileChooser chooser = new JFileChooser();			
			if(chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION){
				System.out.println("Opening config from "+chooser.getSelectedFile().getName()+"...");
				edges = null;
				nodes = null;
				pickedNode = null; pickedEdge = null; selEdge = null; selNode = null;
				try{
					ObjectInputStream ois = new ObjectInputStream(new FileInputStream(chooser.getSelectedFile()));
					nodes = (Vector) ois.readObject();
					edges = (Vector) ois.readObject();
					ois.close();
					System.out.println("Success!");
				}catch(Exception ex){
					ex.printStackTrace();
					JOptionPane.showMessageDialog(this, "Error loading config... check console.");
				}
				repaint();
			}
		}else if(cmd.equals("exportConf")){
			JFileChooser chooser = new JFileChooser();			
			if(chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION){
				System.out.println("Exporting config to "+chooser.getSelectedFile().getName()+"...");
				try{
					FileWriter fw = new FileWriter(chooser.getSelectedFile());
//					Writer fw = new PrintWriter(System.out);
					StringBuilder peers = new StringBuilder();
					for(int i=0; i<nodes.size(); i++){
						Node n = (Node) nodes.get(i);
						fw.write("# "+n.nick+"\n");
						peers.delete(0, peers.length());
						for(int j=0; j<edges.size(); j++){
							Edge edg = (Edge) edges.get(j);
							if(edg.a == n){
								peers.append(" "+edg.b.hostname);
							}else if(edg.b == n){
								peers.append(" "+edg.a.hostname);
							}
						}
						fw.write(n.hostname+peers.toString()+"\n");
						fw.write(n.ipAddress+peers.toString()+"\n");
						fw.write("\n");
					}
					fw.close();
					System.out.println("Success!");
				}catch(Exception ex){
					ex.printStackTrace();
					JOptionPane.showMessageDialog(this, "Error exporting config... check console.");
				}
			}
		}
	}
	
	void buildMainBoard(){
		setPreferredSize(new Dimension(800, 600));
		setBackground(Color.WHITE);
		addMouseListener(this);
	}
	
	public void mouseClicked(MouseEvent e) {
		if(e.getButton() == RIGHT_BUTTON){
			if(selNode != null){
				nodeMenu.show(e.getComponent(), e.getX(), e.getY());
			}else if(selEdge != null){
				edgeMenu.show(e.getComponent(), e.getX(), e.getY());
			}else{
				fileMenu.show(e.getComponent(), e.getX(), e.getY());
			}
		}
		selNode = null;
		selEdge = null;
		repaint();
	}

	public void mouseEntered(MouseEvent e) {
	}

	public void mouseExited(MouseEvent e) {
	}

	public void mousePressed(MouseEvent e) {
		selNode = null; selEdge = null;
		pickedEdge = null; pickedNode = null;
		mousePos.setLocation(e.getPoint());
		selNode = findSelNode(mousePos);
		dragged = false;
		if(selNode == null){
			selEdge = findSelEdge(mousePos);		
		}else
			selEdge = null;
		
		pickedNode = null;
		if(e.getButton() == LEFT_BUTTON){
			if(selNode != null){
				pickedNode = selNode;
				addMouseMotionListener(this);
			}
		}else if(e.getButton() == RIGHT_BUTTON){
			if(selNode != null){
				pickedEdge = new Edge(selNode, new Node());
				pickedEdge.b.pos.setLocation(mousePos);
				addMouseMotionListener(this);
			}
			if(selEdge != null)
				pickedEdge = selEdge;
		}else if(e.getButton() == MIDDLE_BUTTON){
			oldTableDelta.setLocation(tableDelta);
			movingTable = true;
			addMouseMotionListener(this);
		}
		repaint();
	}

	public void mouseReleased(MouseEvent e) {
		removeMouseMotionListener(this);
		if((pickedEdge != null) && (selNode != null) && (selNode != pickedEdge.a)){
			pickedEdge.b = selNode;
			if(! existsEdgeLike(pickedEdge))
				edges.add(pickedEdge);
		}
		if(dragged){
			movingTable = false;
			dragged = false;
			pickedEdge = null;
			selNode = null;
			selEdge = null;
			pickedNode = null;
			repaint();			
		}
	}

	public void mouseDragged(MouseEvent e) {
		if(pickedNode != null){
			pickedNode.pos.setLocation(e.getPoint());
			dragged = true;
		}
		if(pickedEdge != null){
			selNode = findSelNode(e.getPoint());
			pickedEdge.b.pos.setLocation(e.getPoint());
			dragged = true;
		}
		if(movingTable){
			dragged = true;
			Point mouse = e.getPoint();
			int dx = mouse.x - mousePos.x;
			int dy = mouse.y - mousePos.y;
			mousePos.setLocation(mouse);
			for(int i=0; i<nodes.size(); i++){
				Node n = (Node) nodes.get(i);
				n.pos.translate(dx, dy);
			}
		}
		repaint();
	}

	public void mouseMoved(MouseEvent e) {
	}

	Node findSelNode(Point p){
		for(int i=0; i<nodes.size(); i++){
			Node n = (Node) nodes.get(i);
			if((Math.abs(p.x - n.pos.x) < n.width/2)
					&& (Math.abs(p.y - n.pos.y) < n.height/2))
				return n;
		}
		return null;
	}
	
	Edge findSelEdge(Point p){
		for(int i=0; i<edges.size(); i++){
			Edge e = (Edge) edges.get(i);
			Point p1 = e.a.pos;
			Point p2 = e.b.pos;
			if(p1.distance(p) + p2.distance(p) < p1.distance(p2) + LINK_HIGHLIGHT_DISTANCE)
				return e;
		}
		return null;
	}
	
	boolean existsEdgeLike(Edge e){
		for(int i=0; i<edges.size(); i++){
			Edge m = (Edge) edges.get(i);
			if(((m.a == e.a) && (m.b == e.b)) || ((m.a == e.b) && (m.b == e.a)))
				return true;
		}
		return false;
	}
	
	public void paint(Graphics g) {
		super.paint(g);
		for(int i=0; i<edges.size(); i++){
			Edge e = (Edge) edges.get(i);
			e.paint(g, selEdge == e);
		}
		if(pickedEdge != null){
			pickedEdge.paint(g, true);
		}
		for(int i=0; i<nodes.size(); i++){
			Node n = (Node) nodes.get(i);
			n.paint(g, selNode == n);
		}
	}	

	public static void main(String[] args) {
		JFrame frame = new JFrame("ABPing Config Generator");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(new BorderLayout());
		ConfigGenerator board = new ConfigGenerator(frame);
		//JScrollPane scrollPane = new JScrollPane(board);
		frame.getContentPane().add(/*scrollPane*/ board, BorderLayout.CENTER);
//		frame.getContentPane().setSize(600, 400);
//		frame.setSize(600, 400);
		
		frame.pack();
		frame.setVisible(true);
	}

}


