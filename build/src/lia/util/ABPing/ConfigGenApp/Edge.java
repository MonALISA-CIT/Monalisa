package lia.util.ABPing.ConfigGenApp;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.io.Serializable;


public class Edge implements Serializable{
	Node a;
	Node b;
	
	Edge(Node n1, Node n2){
		a = n1;
		b = n2;
	}
	
	void paint(Graphics g, boolean isSelected){
		Point p1 = a.pos;
		Point p2 = b.pos;
		g.setColor(isSelected ? Color.RED : Color.BLUE);
		g.drawLine(p1.x, p1.y, p2.x, p2.y);
	}
	
	
}
