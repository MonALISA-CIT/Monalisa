package lia.Monitor.JiniClient.Farms.OpticalSwitch.Config;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;

import javax.swing.DefaultCellEditor;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

public class ConnectionTable extends JPanel {

	String connectionState = "";
	String insertionLoss = "";
	String switchTime = "";
	String duration = "";
	JTable table;
	
	protected String[] columnNames = {
			"Connection State",
			"Insertion Loss",
			"Switch Time",
			"Duration"
	};
	
//	static final Color oddColor = new Color(200, 130, 220);
	static final Color oddColor = Color.white;
	static final Color evenColor = new Color(230, 100, 120);
	
	ConfigFrame parent;
	
	public ConnectionTable(ConfigFrame parent) {
		
		super();
		this.parent = parent;
		table = new JTable(new PortTableModel()); 
		for (int i=0; i<4; i++) {
			TableColumn col = table.getColumnModel().getColumn(i);
			col.setCellRenderer(new MyLabelRenderer());
			col.setCellEditor(new MyLabelEditor());
		}
		setLayout(new BorderLayout());
		JTableHeader header = table.getTableHeader();
	    // Add header in NORTH slot
	    add(header, BorderLayout.NORTH);
		add(table,BorderLayout.CENTER);
	}
	
	public void setConnection(String state, String loss, String swTime, String duration) {
		
		if (state != null)
			this.connectionState = state;
		else
			this.connectionState = "";
		if (loss != null)
			this.insertionLoss = loss;
		else
			this.insertionLoss = "";
		if (swTime != null)
			this.switchTime = swTime;
		else 
			this.switchTime = "";
		if (duration != null)
			this.duration = duration;
		else
			this.duration = "";
		repaint();
	}
	
	class PortTableModel extends DefaultTableModel {

		public int getRowCount() {
			return 1;
		}

		public int getColumnCount() {
			return 4;
		}
		
		public String getColumnName(int col) {
			
			return columnNames[col];
		}

		public Object getValueAt(int rowIndex, int columnIndex) {
			
			if (rowIndex < 0 || rowIndex > 1) return "";
			if (columnIndex < 0 || columnIndex > 4) return "";
			switch (columnIndex) {
			case 0: 	return connectionState;
			case 1: return insertionLoss;
			case 2: return switchTime;
			case 3: return duration;
			}
			return null;
		}
		
	    public boolean isCellEditable(int row, int col) {
	        //Note that the data/cell address is constant,
	        //no matter where the cell appears onscreen.
			return false;
	    }
	}
	
	public class MyLabelRenderer extends JTextField implements TableCellRenderer {
		
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
			
            if (isSelected) {
                setForeground(table.getSelectionForeground());
                super.setBackground(table.getSelectionBackground());
            } else {
                setForeground(table.getForeground());
				if (column % 2 == 0) 
					setBackground(oddColor);
				else
					setBackground(evenColor);
            }
			setText((String)value);
			setHorizontalAlignment(SwingConstants.CENTER);
            return this;
        }
	}
	
	public class MyLabelEditor extends DefaultCellEditor {
		public MyLabelEditor() {
			super(new JTextField(""));
		}
	}
	
} // end of class PortTable
