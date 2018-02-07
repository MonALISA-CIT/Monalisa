package lia.Monitor.JiniClient.Farms.OpticalSwitch.Config;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
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

import lia.net.topology.opticalswitch.AFOXOSPort;
import lia.net.topology.opticalswitch.OSPort;

public class ReducedPortTable extends JPanel {

	String inputPort = "";
	String outputPort = "";
	JTable table;
	
	protected String[] columnToolTips = {
		    null,
		    "The ID of the port"};
	
	protected String[] columnNames = {
			"",
			"Port ID"
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
		for (int i=0; i<2; i++) {
			TableColumn col = table.getColumnModel().getColumn(i);
			col.setCellRenderer(new MyLabelRenderer());
			col.setCellEditor(new MyLabelEditor());
		}
		// Disable auto resizing
//		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		// Set the first visible column to 100 pixels wide
//		table.getColumnModel().getColumn(0).setPreferredWidth(40);
		table.getColumnModel().getColumn(0).setMaxWidth(70);
		setLayout(new BorderLayout());
		JTableHeader header = table.getTableHeader();
	    // Add header in NORTH slot
	    add(header, BorderLayout.NORTH);
		add(table,BorderLayout.CENTER);
	}
	
	private final String getOSPortLabel(OSPort p) {
		StringBuilder b = new StringBuilder();
//		if (p instanceof AFOXOSPort) {
//			final AFOXOSPort a = (AFOXOSPort)p;
//			if (a.getSerialNumber() != null) {
//				b.append("RFID: ").append(a.getSerialNumber()).append("<br\\>");
//			}
//		} 
		b.append(p.toString());
		return b.toString();
	}
	
	public void setInput(OSPort port) {
		
		if (port != null)
			this.inputPort = getOSPortLabel(port);
		else
			this.inputPort = "";
		repaint();
	}
	
	public void setOutput(OSPort port) {
		
		if (port != null)
			this.outputPort = getOSPortLabel(port);
		else
			this.outputPort = "";
	}
	
	class PortTableModel extends DefaultTableModel {

		public int getRowCount() {
			return 2;
		}

		public int getColumnCount() {
			return 2;
		}
		
		public String getColumnName(int col) {
			
			return columnNames[col];
		}

		public Object getValueAt(int rowIndex, int columnIndex) {
			
			if (rowIndex < 0 || rowIndex > 1) return "";
			if (columnIndex < 0 || columnIndex > 3) return "";
			switch (columnIndex) {
			case 0: 	if (rowIndex == 0) return "Input";
					return "Output";
			case 1: if (rowIndex == 0) return inputPort;
					return outputPort;
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
					setBackground(evenColor);
            }
			setText((String)value);
			if (column == 0) {
				int h = getHeight();
				Dimension d = new Dimension(40, h);
				setSize(d);
				setMaximumSize(d);
				setPreferredSize(d);
			}
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
