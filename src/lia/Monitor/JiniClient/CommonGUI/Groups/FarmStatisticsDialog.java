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
import lia.Monitor.monitor.Gresult;

public class FarmStatisticsDialog extends JDialog {

	static NumberFormat nf = NumberFormat.getInstance();
	
	static {
		nf.setMinimumFractionDigits(2);
		nf.setMaximumFractionDigits(2);
	}
	
	public FarmStatisticsDialog(JFrame owner, rcNode node, Icon icon) {
		
		super(owner, node.client.farm.name, true);
		
		Font font1 = new Font("Arial", Font.BOLD, 12);
		Font font2 = new Font("Arial", Font.PLAIN, 10);
		
		JPanel p = new JPanel();
		p.setLayout(new GridLayout(0, 4));
		JLabel l = new JLabel("Parameter");
		l.setFont(font1);
		l.setForeground(Color.blue);
		l.setBorder(BorderFactory.createRaisedBevelBorder());
		p.add(l);
		l = new JLabel("Avg");
		l.setFont(font1);
		l.setForeground(Color.blue);
		l.setBorder(BorderFactory.createRaisedBevelBorder());
		p.add(l);
		l = new JLabel("Min");
		l.setFont(font1);
		l.setForeground(Color.blue);
		l.setBorder(BorderFactory.createRaisedBevelBorder());
		p.add(l);
		l = new JLabel("Max");
		l.setFont(font1);
		l.setForeground(Color.blue);
		l.setBorder(BorderFactory.createRaisedBevelBorder());
		p.add(l);
		for (Enumeration e1 = node.global_param.keys(); e1.hasMoreElements(); ) {
			String key = (String)e1.nextElement();
			Gresult res = (Gresult)node.global_param.get(key);
			if (res == null) continue;
			l = new JLabel(key);
			l.setFont(font2);
			l.setForeground(Color.black);
			l.setBorder(BorderFactory.createRaisedBevelBorder());
			p.add(l);
			l = new JLabel(""+nf.format(res.mean));
			l.setFont(font2);
			l.setForeground(Color.red);
			l.setBorder(BorderFactory.createRaisedBevelBorder());
			p.add(l);
			l = new JLabel(""+nf.format(res.min));
			l.setFont(font2);
			l.setForeground(Color.red);
			l.setBorder(BorderFactory.createRaisedBevelBorder());
			p.add(l);
			l = new JLabel(""+nf.format(res.max));
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
