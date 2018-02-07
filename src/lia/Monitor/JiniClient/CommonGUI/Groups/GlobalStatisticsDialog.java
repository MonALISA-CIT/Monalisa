package lia.Monitor.JiniClient.CommonGUI.Groups;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class GlobalStatisticsDialog extends JDialog{

	public GlobalStatisticsDialog(JFrame owner, String text, Icon icon) {
		
		super(owner, "Global Statistics", true);
		
		Font font1 = new Font("Arial", Font.BOLD, 12);
		
		JPanel p = new JPanel();
		p.setLayout(new BorderLayout());
		JLabel l = new JLabel(text);
		l.setFont(font1);
		l.setForeground(Color.red);
		l.setBorder(BorderFactory.createRaisedBevelBorder());
		p.add(l, BorderLayout.CENTER);
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
