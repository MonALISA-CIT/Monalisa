
package lia.Monitor.AppControlClient;

import java.awt.BorderLayout;
import java.awt.Rectangle;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.TitledBorder;


public class BusyDialog extends JDialog {
	public BusyDialog(JFrame f) {
		//make modal dialog
		super(f, "waiting dialog", false);
		

		JPanel p1 = new JPanel();
		p1.setBorder(new TitledBorder("Waiting for a server response ... "));
		JProgressBar progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);
		p1.add(progressBar);
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(p1, BorderLayout.CENTER);
		//show modal dialog
		setUndecorated(true);

		Rectangle r = f.getBounds();

		setLocation(
				(int) (r.getX() + r.getWidth() / 2 - 150),
				(int) (r.getY() + r.getHeight() / 2 - 50));
		setSize(300, 100);
		setVisible (false);
	}
	
	public BusyDialog(JDialog f) {
		//make modal dialog
		super(f, "waiting dialog", false);
		

		JPanel p1 = new JPanel();
		p1.setBorder(new TitledBorder("Waiting for a server response ... "));
		JProgressBar progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);
		p1.add(progressBar);
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(p1, BorderLayout.CENTER);
		//show modal dialog
		setUndecorated(true);

		Rectangle r = f.getBounds();

		setLocation(
				(int) (r.getX() + r.getWidth() / 2 - 150),
				(int) (r.getY() + r.getHeight() / 2 - 50));
		setSize(300, 100);
		setVisible (false);
	}
	
} // BusyDialog
