package lia.util.ABPing.ConfigGenApp;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.io.Serializable;

public class Node implements Serializable{
	String nick;
	String hostname;
	String ipAddress;
	Point pos;
	int width;
	int height;
	
	private static Font font = new Font("Arial", Font.PLAIN, 11);
	
	Node(){
		pos = new Point();
	}
	
	void paint(Graphics g, boolean isSelected){
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		width = fm.stringWidth(nick) + 10;
		height = fm.getHeight() + 10;
		int width2 = width/2;
		int height2 = height/2;
		
		g.setColor(isSelected ? Color.GREEN : Color.ORANGE);
		g.fillRect(pos.x - width2, pos.y - height2, width, height);
		g.setColor(Color.BLACK);
		g.drawRect(pos.x - width2, pos.y - height2, width, height);
		g.drawString(nick, pos.x - width2 + 5, pos.y + height2 - 8);
	}
}
