package lia.Monitor.JiniClient.CommonGUI;

import java.awt.BorderLayout;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

/**
 * A class used to informally represent the string results that are obtained when plotting real-time.
 */
public class StringResultPanel extends JPanel {

	public EnhancedTablePanel table = null; // the results will be represented in this table
	public String[][] results = null;

	public StringResultPanel() {
		
		super();
		
		setLayout(new BorderLayout());
		table = new EnhancedTablePanel();
		add(table, BorderLayout.CENTER);
	}
	
	public synchronized void addStringResult(String time, String farmName, String clusterName, String nodeName, String module, String paramName, String value, boolean realtime) {
		
		if (time == null || time.length() == 0) time = "???";
		if (farmName == null || farmName.length() == 0) farmName = "???";
		if (clusterName == null || clusterName.length() == 0) clusterName = "???";
		if (nodeName == null || nodeName.length() == 0) nodeName = "???";
		if (module == null || module.length() == 0) module = "???";
		if (paramName == null || paramName.length() == 0) paramName = "???";
		if (value == null || value.length() == 0) value = "???";
		String ss = "???";
		
		if (results == null) { // new result
			results = new String[1][7];
			results[0][0] = time;
			results[0][1] = farmName;
			results[0][2] = clusterName;
			results[0][3] = nodeName;
			results[0][4] = module;
			results[0][5] = paramName;
			results[0][6] = value;
			table.setData(results);
			return;
		}
		if (realtime) {
			for (int i=0; i<results.length; i++) { // already added result
				if (results[i] == null || results[i].length < 6 || results[i][1] == null || results[i][2] == null || results[i][3] == null || results[i][4] == null || results[i][5] == null)
					continue;
				if ((results[i][1].equals(farmName) || results[i][1].equals(ss)) && (results[i][2].equals(clusterName) || results[i][2].equals(ss)) && 
						(results[i][3].equals(nodeName) || results[i][3].equals(ss)) && (results[i][4].equals(module) || results[i][4].equals(ss)) &&
						(results[i][5].equals(paramName) || results[i][5].equals(ss))) {
					if (results[i][1].equals(ss) && !farmName.equals(ss)) results[i][1] = farmName;
					if (results[i][2].equals(ss) && !clusterName.equals(ss)) results[i][2] = clusterName;
					if (results[i][3].equals(ss) && !nodeName.equals(ss)) results[i][3] = nodeName;
					if (results[i][4].equals(ss) && !module.equals(ss)) results[i][4] = module;
					if (results[i][5].equals(ss) && !paramName.equals(ss)) results[i][5] = paramName;
					results[i][6] = value;
					results[i][0] = time;
					table.setData(results);
					return;
				}
			}
		}
		// new result
		String tmpResults[][] = new String[results.length+1][7];
		for (int i=0; i<results.length; i++)
			System.arraycopy(results[i], 0, tmpResults[i], 0, 7);
		tmpResults[results.length][0] = time;
		tmpResults[results.length][1] = farmName;
		tmpResults[results.length][2] = clusterName;
		tmpResults[results.length][3] = nodeName;
		tmpResults[results.length][4] = module;
		tmpResults[results.length][5] = paramName;
		tmpResults[results.length][6] = value;
		results = tmpResults;
		table.setData(results);
	}
	
	public synchronized void removeFarm(rcNode node) {
		
		if (results == null) return;
		Vector<String[]> tmp = new Vector<String[]>();
		for (int i=0; i<results.length; i++) {
			if (!results[i][1].equals(node.client.farm.getName()))
				tmp.add(new String[] { results[i][0], results[i][1], results[i][2], results[i][3], results[i][4], results[i][5], results[i][6]} );
		}
		String[][] tmpResults = new String[tmp.size()][7];
		for (int i=0; i<tmp.size(); i++) {
			String[] res = tmp.get(i);
			for (int j=0; j<7; j++) tmpResults[i][j] = res[j];
		}
		results = tmpResults;
		table.setData(results);
	}
	
	public static void main(String args[]) {
		
		JFrame test = new JFrame();
		test.getContentPane().setLayout(new BorderLayout());
		StringResultPanel frame = new StringResultPanel();
		test.getContentPane().add(frame, BorderLayout.CENTER);
		test.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		test.setSize(500, 500);
		test.setVisible(true);
		for (int i=0; i<50; i++) {
			frame.addStringResult(""+i, "farm_"+i, "cluster_"+i, "node_"+i, "module_"+i, "param_"+i, "value_"+i, true);
			try {
				Thread.sleep(100);
			} catch (Exception e) { }
		}
	}
}
