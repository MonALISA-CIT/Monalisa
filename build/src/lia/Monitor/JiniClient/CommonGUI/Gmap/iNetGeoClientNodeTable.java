package lia.Monitor.JiniClient.CommonGUI.Gmap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import lia.Monitor.JiniClient.CommonGUI.EnhancedJPanel;

public class iNetGeoClientNodeTable extends EnhancedJPanel {

	static final Color lightBlue = new Color(130, 200, 250);
	private static final Color lightBlue1 = new Color(210, 240, 250);
	static final Color lightWhite = new Color(200, 200, 200);
	static final Color lightWhite1 = new Color(150, 150, 200);
	static final Color selectedColor = new Color(15, 70, 180);

	protected JTable table = null;
	protected Object[][] data = null;
	int itsRow =0;
    int itsColumn = 0;
	
	JScrollPane scrollPane = null;
	
	public static final Object lock = new Object();
	
	public iNetGeoClientNodeTable() {
		
		super();
		setFocusable(false);
		setOpaque(true);
		
		table = new JTable(new EnhancedTableModel());
		table.setEnabled(false);
		table.setOpaque(false);
		table.setDefaultRenderer(Object.class, new EnhancedTableCellRendered());
		
		MyMouseAdapter aMouseAda = new MyMouseAdapter();
        table.addMouseMotionListener(aMouseAda);
        table.addMouseListener(aMouseAda);
		table.setRowHeight(18);
		
		scrollPane = new JScrollPane(table);
		scrollPane.getViewport().setOpaque(false);
		scrollPane.setOpaque(false);
		table.setPreferredScrollableViewportSize(new Dimension(50, 150));
		
		setLayout(new BorderLayout());
		add(scrollPane, BorderLayout.CENTER);
	}
	
	public  void setData(Object[][] data) {

		synchronized (lock) {
			this.data = data;
		}
		if (data == null)
			table.setEnabled(false);
		else
			table.setEnabled(true);
		repaint();
		table.repaint();
		table.revalidate();
	}
	
	class EnhancedTableModel extends AbstractTableModel {

		public int getRowCount() {
			
			synchronized (lock) {
				if (data == null) return 0;
			}
			
			table.getTableHeader().setMinimumSize(new Dimension((int)table.getTableHeader().getMinimumSize().getWidth(), 24));
			table.getTableHeader().setMaximumSize(new Dimension((int)table.getTableHeader().getMaximumSize().getWidth(), 24));
			table.getTableHeader().setPreferredSize(new Dimension((int)table.getTableHeader().getPreferredSize().getWidth(), 24));
			table.getTableHeader().setBackground(lightBlue1);
			table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));
			
			synchronized (lock) {
				return data.length;
			}
		}
		
		public String getColumnName(int column) {
		
			if (column == 0)
				return "URL";
			if (column == 1)
				return "String";
			return "";
		}

		public int getColumnCount() {
			
			return 2;
		}

		public Object getValueAt(int rowIndex, int columnIndex) {
			
			synchronized (lock) {
				if (data == null) return null;
				if (rowIndex >= data.length)
					return "";
				if (columnIndex >= data[rowIndex].length)
					return "";
				return data[rowIndex][columnIndex];
			}
		}

		public boolean isCellEditable(int row, int column) {
			
		    return false;
		  }
	}
	
	public class    MyMouseAdapter extends MouseMotionAdapter implements MouseListener
	    {

	        public void mouseMoved(MouseEvent e)
	        {
	            itsRow = table.rowAtPoint(e.getPoint());
	            itsColumn = table.columnAtPoint(e.getPoint());
	            table.repaint();
	        }

			public void mouseClicked(MouseEvent e) {
			}

			public void mousePressed(MouseEvent e) {
			}

			public void mouseReleased(MouseEvent e) {
			}

			public void mouseEntered(MouseEvent e) {
				itsRow = itsColumn = -1;
				table.repaint();
			}

			public void mouseExited(MouseEvent e) {
				itsRow = itsColumn = -1;
				table.repaint();
			}
	    }
	
	
	class EnhancedTableCellRendered extends DefaultTableCellRenderer {

		public EnhancedTableCellRendered() {
			
			setHorizontalAlignment(SwingConstants.LEFT);

	        // This call is needed because DefaultTableCellRenderer calls setBorder()
	        // in its constructor, which is executed after updateUI()
	        setBorder(UIManager.getBorder("TableHeader.cellBorder"));
		}

		public void updateUI()
	    {
	        super.updateUI();
	        setBorder(UIManager.getBorder("TableHeader.cellBorder"));
	    }

		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			
			if(row == itsRow && column == itsColumn)
		       {
		          this.setBackground(lightBlue);
		          this.setForeground(selectedColor);
		       }
		       else
		       {
		       	if (row%2== 0)
		          this.setBackground(lightWhite);
		       	else
		       		this.setBackground(lightWhite1);
		          this.setForeground(Color.darkGray);
		       }
		        this.setText(value.toString());
			this.setToolTipText(value.toString());
		    return this;		
		    }
	}

}
