package lia.util.ABPing.ConfigGenApp;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.InetAddress;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class NodeProperties extends JDialog {
	boolean saveData = false;
	
	JPanel board;
	JTextField tfNick;
	JTextField tfHostname;
	JTextField tfIPAddress;
	
	NodeProperties(JFrame parent){
		super(parent, true);
		setTitle("Node properties");
		buildBoard();
		buildFields();
	}

	void buildBoard(){
		setSize(400, 180);
		setLocation(100, 100);
		board = new JPanel();
		board.setPreferredSize(new Dimension(400, 300));
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(board, BorderLayout.CENTER);
	}
	
	void buildFields(){
		tfNick = new JTextField(15);
		tfHostname = new JTextField(15);
		tfIPAddress = new JTextField(15);
		JLabel lNick = new JLabel("Nick", JLabel.RIGHT);
		JLabel lHostname = new JLabel("Hostname", JLabel.RIGHT);
		JButton getIPButton = new JButton("Get IP");
		getIPButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				String ipAddress = "";
				try{
					ipAddress = InetAddress.getByName(tfHostname.getText()).getHostAddress();
				}catch(Exception ex){
					ex.printStackTrace();
					ipAddress = "UNKNOWN";
				}
				tfIPAddress.setText(ipAddress);
			}
		});
		JButton okButton = new JButton("OK");
		okButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				saveData = true;
				setVisible(false);
			}
		});
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				saveData = false;
				setVisible(false);
			}
		});
		
		JPanel fp = new JPanel();
		GridLayout lay = new GridLayout(3, 2);
		lay.setHgap(5); lay.setVgap(5);
		fp.setLayout(lay);
		fp.add(lNick); fp.add(tfNick);
		fp.add(lHostname); fp.add(tfHostname);
		fp.add(getIPButton); fp.add(tfIPAddress);

		JPanel ffp = new JPanel();
		ffp.setLayout(new FlowLayout());
		ffp.add(fp);

		JPanel bp = new JPanel();
		bp.setLayout(new FlowLayout());
		bp.add(okButton); bp.add(cancelButton);
		
		board.setLayout(new BoxLayout(board, BoxLayout.Y_AXIS));
		board.add(ffp); board.add(bp);
	}
	
	String getNick(){
		return tfNick.getText();
	}
	
	String getHostname(){
		return tfHostname.getText();
	}
	
	String getIpAddress(){
		return tfIPAddress.getText();
	}
	void setData(String nick, String hostname, String ipAddr){
		tfNick.setText(nick);
		tfHostname.setText(hostname);
		tfIPAddress.setText(ipAddr);
		saveData = false;
	}
}
