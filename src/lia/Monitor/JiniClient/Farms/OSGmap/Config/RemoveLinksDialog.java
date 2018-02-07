package lia.Monitor.JiniClient.Farms.OSGmap.Config;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import lia.Monitor.JiniClient.Farms.OSGmap.OSGmapPan;

public class RemoveLinksDialog extends JDialog implements ActionListener {

	protected JButton closeButton;
	protected JButton chooseButton;
	protected OSGmapPan osPan;
	protected Color firstColor = new Color(255, 162, 153);
	Font f = new Font("Arial", Font.PLAIN, 10);
	
	public RemoveLinksDialog(final JFrame owner, final Hashtable links, final OSGmapPan osPan) {
		
		super(owner, "Remove links", true);
		this.osPan = osPan;
		getContentPane().setLayout(new BorderLayout());
		Graphics g = getGraphicsConfiguration().createCompatibleImage(10, 10).getGraphics();
		FontMetrics fm = g.getFontMetrics(f);
		final JPanel p = new JPanel();
		p.setBackground(Color.white);
		p.setLayout(new GridLayout(0, 1));
		int i = 0;
		int max = 0;
		for (Enumeration en = links.keys(); en.hasMoreElements(); ) {
			final String linkID = (String)en.nextElement();
			final JPanel lp = new JPanel();
			i++;
			if (i % 2 == 1)
				lp.setBackground(firstColor);
			else
				lp.setOpaque(false);
			lp.setLayout(new GridLayout(0, 3));
			lp.setBorder(BorderFactory.createEtchedBorder());
			final String detail[] = (String[])links.get(linkID);
			JLabel l = new JLabel(detail[1]);
			l.setFont(f);
			int w = fm.stringWidth(detail[1]);
			if (w > max) max = w;
			JPanel labelPan = new JPanel();
			labelPan.setLayout(new BoxLayout(labelPan, BoxLayout.X_AXIS));
			labelPan.add(Box.createHorizontalStrut(5));
			labelPan.add(l);
			lp.add(labelPan);
			JButton details = new JButton("Details");
			details.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					JOptionPane.showMessageDialog(owner, detail[0]);
				} 
			});
			details.setFont(f);
			JPanel detPan = new JPanel();
			detPan.setLayout(new FlowLayout());
			detPan.add(details);
			lp.add(detPan);
			JButton remove = new JButton("Remove");
			remove.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					new Thread(new Runnable() {
						public void run() {
							if (osPan == null || osPan.currentControl == null) return;
							OSMonitorControl c = osPan.grapPan.getDelFirstMonitor();
							if (c != null) osPan.currentControl = c;
							if (osPan.currentControl.deletePath(linkID)) {
								links.remove(linkID);
								if (links.size() == 0) { // no more links to delete, close this window
									setVisible(false);
									dispose();
									if (osPan == null) return;
									osPan.enterCmd();
									return;
								}
								p.remove(lp);
								setSize(getWidth(), 50 * links.size() + 50);
								p.revalidate();
								p.repaint();
							}
						}
					}).start();
				}
			});
			remove.setFont(f);
			JPanel remPan = new JPanel();
			remPan.setLayout(new FlowLayout());
			remPan.add(remove);
			lp.add(remPan);
			p.add(lp);
		}
		JPanel cancelPan = new JPanel();
		i++;
		if (i %2 == 1)
			cancelPan.setBackground(firstColor);
		else
			cancelPan.setOpaque(false);
		cancelPan.setOpaque(false);
		cancelPan.setLayout(new FlowLayout());
		closeButton = new JButton("Close");
		closeButton.setFont(f);
		closeButton.addActionListener(this);
		chooseButton = new JButton("Graphicaly choose the links");
		chooseButton.addActionListener(this);
		chooseButton.setFont(f);
		cancelPan.add(closeButton);
		cancelPan.add(chooseButton);
		p.add(cancelPan);
		getContentPane().add(p, BorderLayout.CENTER);
		setSize(max + 200, 50 * links.size() + 50);
		setResizable(false);
		setVisible(true);
	}

	public void actionPerformed(ActionEvent e) {
		
		Object source = e.getSource();
		if (source.equals(closeButton)) {
			setVisible(false);
			dispose();
			return;
		}
		if (source.equals(chooseButton)) {
			setVisible(false);
			dispose();
			
			osPan.enterGUIRemoveLinks();
			return;
		}
	}
	
	public static void main(String args[]) {
		
		JFrame f = new JFrame();
		f.setSize(100, 100);
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.setVisible(true);
		Hashtable links = new Hashtable();
		for (int i=0; i<10; i++) links.put("test_"+i, new String[] { "details for test_"+i, "test-test1 test2-test"+i});
		RemoveLinksDialog d = new RemoveLinksDialog(f, links, null);
	}
	
} // end of class RemoveLinksDialog

