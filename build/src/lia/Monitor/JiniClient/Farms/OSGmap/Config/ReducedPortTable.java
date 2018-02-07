package lia.Monitor.JiniClient.Farms.OSGmap.Config;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseEvent;

import javax.swing.DefaultCellEditor;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

public class ReducedPortTable extends JPanel {

	String inputPort = "";
	String outputPort = "";
	String inputPower = "";
	String outputPower = "";
	JTable table;
	
	protected String[] columnToolTips = {
		    null,
		    "The ID of the port",
		    "The current power level"};
	
	protected String[] columnNames = {
			"",
			"Port ID",
			"Power"
	};
	
//	static final Color oddColor = new Color(200, 130, 220);
	static final Color oddColor = Color.white;
	static final Color evenColor = new Color(230, 100, 120, 100);
	
	ConfigFrame parent;
	
	public ReducedPortTable(ConfigFrame parent) {
		
		super();
		this.parent = parent;
		table = new JTable(new PortTableModel()) {
		    protected JTableHeader createDefaultTableHeader() {
		        return new JTableHeader(columnModel) {
		            public String getToolTipText(MouseEvent e) {
		                java.awt.Point p = e.getPoint();
		                int index = columnModel.getColumnIndexAtX(p.x);
		                int realIndex = 
		                        columnModel.getColumn(index).getModelIndex();
		                return columnToolTips[realIndex];
		            }
		        };
		    }
		};
		for (int i=0; i<3; i++) {
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
	
	public void setInput(String port, String state, String power, int signalType) {
		
		if (port != null)
			this.inputPort = port;
		else
			this.inputPort = "";
		if (power != null)
			this.inputPower = power;
		else
			this.inputPower = "";
		repaint();
	}
	
	public void setOutput(String port, String state, String power) {
		
		if (port != null)
			this.outputPort = port;
		else
			this.outputPort = "";
		if (power != null)
			this.outputPower = power;
		else
			this.outputPower = "";
	}
	
	class PortTableModel extends DefaultTableModel {

		public int getRowCount() {
			return 2;
		}

		public int getColumnCount() {
			return 3;
		}
		
		public String getColumnName(int col) {
			
			return columnNames[col];
		}

		public Object getValueAt(int rowIndex, int columnIndex) {
			
			if (rowIndex < 0 || rowIndex > 1) return "";
			if (columnIndex < 0 || columnIndex > 4) return "";
			switch (columnIndex) {
			case 0: 	if (rowIndex == 0) return "Input";
					return "Output";
			case 1: if (rowIndex == 0) return inputPort;
					return outputPort;
			case 2: if (rowIndex == 0) return inputPower;
					return outputPower;
			}
			return null;
		}
		
	    public boolean isCellEditable(int row, int col) {
	        //Note that the data/cell address is constant,
	        //no matter where the cell appears onscreen.
			return false;
	    }
		
		public void setValueAt(Object value, int row, int col) {
			
			fireTableCellUpdated(0, col);
			fireTableCellUpdated(1, col);
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
//				if (column % 2 == 0) 
//					setBackground(oddColor);
//				else
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
	
	public static void main(String args[]) {
		
		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(new BorderLayout());
		ReducedPortTable table = new ReducedPortTable(null);
		frame.getContentPane().add(table, BorderLayout.CENTER);
		frame.setSize(500, 500);
		table.setInput("t", "GOOD", "p1", 0);
//		table.setOutput("p", "GOOD", "p2");
		frame.setVisible(true);
	}
	
} // end of class PortTable
