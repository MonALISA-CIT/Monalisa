package lia.Monitor.JiniClient.CommonGUI.Groups;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;
import java.util.Enumeration;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.ILink;

public class LinkStatisticsDialog extends JDialog {

	static NumberFormat nf = NumberFormat.getInstance();
	
	static {
		nf.setMinimumFractionDigits(2);
		nf.setMaximumFractionDigits(2);
	}
	
	public LinkStatisticsDialog(JFrame owner, rcNode node, Icon icon) {
		
		super(owner, node.client.farm.name, true);
		
		Font font1 = new Font("Arial", Font.BOLD, 12);
		Font font2 = new Font("Arial", Font.PLAIN, 10);
		
		JPanel p = new JPanel();
		p.setLayout(new GridLayout(0, 7));
		JLabel l = new JLabel("Parameter");
		l.setFont(font1);
		l.setForeground(Color.blue);
		l.setBorder(BorderFactory.createRaisedBevelBorder());
		p.add(l);
		l = new JLabel("IP");
		l.setFont(font1);
		l.setForeground(Color.blue);
		l.setBorder(BorderFactory.createRaisedBevelBorder());
		p.add(l);
		l = new JLabel("Lat");
		l.setFont(font1);
		l.setForeground(Color.blue);
		l.setBorder(BorderFactory.createRaisedBevelBorder());
		p.add(l);
		l = new JLabel("Long");
		l.setFont(font1);
		l.setForeground(Color.blue);
		l.setBorder(BorderFactory.createRaisedBevelBorder());
		p.add(l);
		l = new JLabel("Speed");
		l.setFont(font1);
		l.setForeground(Color.blue);
		l.setBorder(BorderFactory.createRaisedBevelBorder());
		p.add(l);
		l = new JLabel("Peer Quality");
		l.setFont(font1);
		l.setForeground(Color.blue);
		l.setBorder(BorderFactory.createRaisedBevelBorder());
		p.add(l);
		l = new JLabel("Inet Quality");
		l.setFont(font1);
		l.setForeground(Color.blue);
		l.setBorder(BorderFactory.createRaisedBevelBorder());
		p.add(l);
		for (Enumeration e1 = node.wconn.keys(); e1.hasMoreElements(); ) {
			String key = (String)e1.nextElement();
		    Object objLink = node.wconn.get(key);
		    if ( !(objLink instanceof ILink) )
		        continue;
			ILink res = (ILink) objLink;
			if (res == null) continue;
			l = new JLabel(key);
			l.setFont(font2);
			l.setForeground(Color.black);
			l.setBorder(BorderFactory.createRaisedBevelBorder());
			p.add(l);
			if (res.toIP != null)
				l = new JLabel(res.toIP);
			else 
				l = new JLabel("unknown");
			l.setFont(font2);
			l.setForeground(Color.red);
			l.setBorder(BorderFactory.createRaisedBevelBorder());
			p.add(l);
			l = new JLabel(""+nf.format(res.toLAT));
			l.setFont(font2);
			l.setForeground(Color.red);
			l.setBorder(BorderFactory.createRaisedBevelBorder());
			p.add(l);
			l = new JLabel(""+nf.format(res.toLONG));
			l.setFont(font2);
			l.setForeground(Color.red);
			l.setBorder(BorderFactory.createRaisedBevelBorder());
			p.add(l);
			l = new JLabel(""+nf.format(res.speed));
			l.setFont(font2);
			l.setForeground(Color.red);
			l.setBorder(BorderFactory.createRaisedBevelBorder());
			p.add(l);
			String q = "";
			if (res.peersQuality != null && res.peersQuality.length != 0) q = ""+nf.format(res.peersQuality[0]);
			l = new JLabel(q);
			l.setFont(font2);
			l.setForeground(Color.red);
			l.setBorder(BorderFactory.createRaisedBevelBorder());
			p.add(l);
			q = "";
			if (res.inetQuality != null && res.inetQuality.length != 0) q = ""+nf.format(res.inetQuality[0]);
			l = new JLabel(q);
			l.setFont(font2);
			l.setForeground(Color.red);
			l.setBorder(BorderFactory.createRaisedBevelBorder());
			p.add(l);
		}
		JPanel p1 = new JPanel();
		JButton button = new JButton("Close");
		button.setFont(font1);
		button.setIcon(icon);
		button.setBorder(BorderFactory.createRaisedBevelBorder());
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setVisible(false);
			}
		});
		p1.setLayout(new FlowLayout());
		p1.add(button);
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(p, BorderLayout.CENTER);
		getContentPane().add(p1, BorderLayout.SOUTH);
		pack();
		setLocationRelativeTo(owner);
		setVisible(true);
	}
}
