package lia.Monitor.JiniClient.Farms.OSGmap.Config;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseEvent;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

public class PortTable extends JPanel {

	static String[] signalTypes = new String[] { "1550", "1310", "Unknown" };
	
	String inputPort = "";
	String outputPort = "";
	String inputState = "";
	String outputState = "";
	String inputPower = "";
	String outputPower = "";
	int signalType = 2;
	
	JTable table;
	
	protected String[] columnToolTips = {
		    null,
		    "The ID of the port",
		    "The current state",
		    "The current power level",
		    "The current signal type"};
	
	protected String[] columnNames = {
			"",
			"Port ID",
			"State",
			"Power",
			"Signal Type"
	};
	
//	static final Color oddColor = new Color(200, 130, 220);
	static final Color oddColor = Color.white;
	static final Color evenColor = new Color(230, 100, 120);
	
	ConfigFrame parent;
	
	public PortTable(ConfigFrame parent) {
		
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
		TableColumn col = table.getColumnModel().getColumn(4);
	    col.setCellEditor(new MyComboBoxEditor(signalTypes));
		col.setCellRenderer(new MyComboBoxRenderer(signalTypes));
		for (int i=0; i<4; i++) {
			col = table.getColumnModel().getColumn(i);
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
		if (state != null)
			this.inputState = state;
		else
			this.inputState = "";
		if (power != null)
			this.inputPower = power;
		else
			this.inputPower = "";
		if (signalType >= 0 && signalType < signalTypes.length)
			this.signalType = signalType;
		else
			this.signalType = 2;
		repaint();
	}
	
	public void setOutput(String port, String state, String power) {
		
		if (port != null)
			this.outputPort = port;
		else
			this.outputPort = "";
		if (state != null)
			this.outputState = state;
		else
			this.outputState = "";
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
			return 5;
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
			case 2: if (rowIndex == 0) return inputState;
					return outputState;
			case 3: if (rowIndex == 0) return inputPower;
					return outputPower;
			case 4: if (rowIndex == 0) return signalTypes[signalType];
			}
			return null;
		}
		
	    public boolean isCellEditable(int row, int col) {
	        //Note that the data/cell address is constant,
	        //no matter where the cell appears onscreen.
			if (col == 4) {
				return !(inputPort == null || inputPort.length() == 0);
			}
			return false;
	    }
		
		public void setValueAt(Object value, int row, int col) {
			
			if (col == 4) {
				String val = (String)value;
				int poz = -1;
				for (int i=0; i<signalTypes.length; i++) {
					if (val.equals(signalTypes[i])) {
						poz = i;
						break;
					}
				}
				if (poz == -1) poz = 2;
				if (signalType != poz && parent != null) {
					if (inputPort != null && inputPort.length() != 0)
						parent.changePortState(inputPort, signalTypes[poz]);
					if (outputPort != null && outputPort.length() != 0)
						parent.changePortState(outputPort, signalTypes[poz]);
				}
				signalType = poz;
			}
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
	
	public class MyComboBoxRenderer extends JComboBox implements TableCellRenderer {
        public MyComboBoxRenderer(String[] items) {
            super(items);
        }
    
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
			
            if (isSelected) {
                setForeground(table.getSelectionForeground());
                super.setBackground(table.getSelectionBackground());
            } else {
                setForeground(table.getForeground());
                setBackground(table.getBackground());
            }
            // Select the current value
			if (row == 0) {
				if (inputPort == null || inputPort.length() == 0)
					setEnabled(false);
				else
					setEnabled(true);
			} 
			else {
				if (outputPort == null || outputPort.length() == 0)
					return null;
			}
            setSelectedItem(signalTypes[signalType]);
            return this;
        }
    }
	
	public class MyComboBoxEditor extends DefaultCellEditor {
        public MyComboBoxEditor(String[] items) {
            super(new JComboBox(items));
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
		PortTable table = new PortTable(null);
		frame.getContentPane().add(table, BorderLayout.CENTER);
		frame.setSize(500, 500);
		table.setInput("t", "GOOD", "p1", 0);
//		table.setOutput("p", "GOOD", "p2");
		frame.setVisible(true);
	}
	
} // end of class PortTable
