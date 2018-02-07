package lia.Monitor.JiniClient.Farms.OSGmap.Ortho;
import java.awt.BorderLayout;
import java.util.Vector;

import javax.swing.JFrame;

import lia.Monitor.Agents.OpticalPath.OSPort;
import lia.Monitor.Agents.OpticalPath.OpticalCrossConnectLink;

public class Test {

	public static void main(String args[]) {
		
		Vector v = new Vector();
		for (int i=0; i<5; i++) {
			OpticalCrossConnectLink link = new OpticalCrossConnectLink(new OSPort("x"+i*10, OSPort.OUTPUT_PORT), new OSPort("c"+(4+i), OSPort.INPUT_PORT), Integer.valueOf(1));
			v.add(link);
		}
		JFrame frame = new JFrame("Test");
		OpticalPanel panel = new OpticalPanel();
		frame.getContentPane().setLayout(new BorderLayout());
		frame.getContentPane().add(panel, BorderLayout.CENTER);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(400, 400);
		frame.setVisible(true);
		panel.update(16, v);
//		Vector v1 = panel.getOrthogonalLayout().getOrthogonalPaths();
//		for (int i=0; i<v1.size(); i++) {
//			System.out.println(v1.get(i));
//		}
	}
}
