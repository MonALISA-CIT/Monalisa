package lia.Monitor.JiniClient.CommonGUI;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.net.URL;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

public class EnhancedTablePanel extends EnhancedJPanel {

	public final static int SORT_UP = 0;
	public final static int SORT_DOWN = 1;
	public final static int SORT_NO = 2;
	
	static final Color lightBlue = new Color(130, 200, 250);
	static final Color lightBlue1 = new Color(210, 240, 250);
	static final Color lightWhite = new Color(200, 200, 200);
	static final Color lightWhite1 = new Color(150, 150, 200);
	static final Color selectedColor = new Color(15, 70, 180);
	
	protected JTable table = null;
	protected Object[][] data = null;
	int itsRow =0;
	int itsColumn = 0;
	
	JScrollPane scrollPane = null;
	
	Vector sortingList = new Vector();
	
	final static Object lock = new Object();
	
	public EnhancedTablePanel() {
		
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
		
		for (int i=0; i<7; i++) {
			TableColumn col = table.getColumnModel().getColumn(i);
			col.setHeaderRenderer(new MyTableHeaderRenderer(i));
		}
		
		table.getTableHeader().addMouseListener(new HeaderMouseAdapter());
		
		scrollPane = new JScrollPane(table);
		scrollPane.getViewport().setOpaque(false);
		scrollPane.setOpaque(false);
		table.setPreferredScrollableViewportSize(new Dimension(50, 150));
		
		setLayout(new BorderLayout());
		add(scrollPane, BorderLayout.CENTER);
	}
	
	public void setSorting(int columnPoz) {
		
		if (columnPoz < 0 || columnPoz > 6) return;
		
		boolean found = false;
		for (int i=0; i<sortingList.size(); i++) {
			int el[] = (int[])sortingList.get(i);
			if (el[0] == columnPoz) {
				int type = el[1];
				type++;
				if (type != 2)  el[1] = type;
				else sortingList.remove(i);
				found = true;
				break;
			}
		}
		if (!found) {
			sortingList.add(new int[] { columnPoz, 0 });
		}
		sortAllRows();
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
		sortAllRows();
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
				return "Time";
			if (column == 1)
				return "FarmName";
			if (column == 2)
				return "ClusterName";
			if (column == 3)
				return "NodeName";
			if (column == 4)
				return "Module";
			if (column == 5)
				return "ParamName";
			if (column == 6)
				return "ParamValue";
			return "";
		}
		
		public int getColumnCount() {
			return 7;
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
	
	public class MyMouseAdapter extends MouseMotionAdapter implements MouseListener {
		
		public void mouseMoved(MouseEvent e) {
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
	
	public class HeaderMouseAdapter extends MouseAdapter {
		
		public void mouseClicked(MouseEvent e) {
			setSorting(table.columnAtPoint(e.getPoint()));
		}
	}
	
	class EnhancedTableCellRendered extends DefaultTableCellRenderer {
		
		public EnhancedTableCellRendered() {
			setHorizontalAlignment(SwingConstants.CENTER);
			// This call is needed because DefaultTableCellRenderer calls setBorder()
			// in its constructor, which is executed after updateUI()
			setBorder(UIManager.getBorder("TableHeader.cellBorder"));
		}
		
		public void updateUI() {
			super.updateUI();
			setBorder(UIManager.getBorder("TableHeader.cellBorder"));
		}
		
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			if(row == itsRow && column == itsColumn) {
				this.setBackground(lightBlue);
				this.setForeground(selectedColor);
			} else {
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
	
	public class MyTableHeaderRenderer extends JLabel implements TableCellRenderer {
		
		private int columnPoz;
		
		public MyTableHeaderRenderer(int columnPoz) {
			this.columnPoz = columnPoz;
			setBorder(BorderFactory.createRaisedBevelBorder());
		}
		
        // This method is called each time a column header
        // using this renderer needs to be rendered.
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowIndex, int vColIndex) {
            // 'value' is column header value of column 'vColIndex'
            // rowIndex is always -1
            // isSelected is always false
            // hasFocus is always false
            // Configure the component with the specified value
			setHorizontalAlignment(SwingConstants.HORIZONTAL);
            setText(value.toString());
            // Set tool tip if desired
            setToolTipText((String)value);
			boolean found = false;
			for (int i=0; i<sortingList.size(); i++) {
				int el[] = (int[])sortingList.get(i);
				if (el[0] == columnPoz) {
					if (el[1] == SORT_DOWN) 
						setIcon(getArrowDown());
					else if (el[1] == SORT_UP)
						setIcon(getArrowUp());
					else setIcon(null);
					found = true;
					break;
				}
			}
			if (!found)
				setIcon(null);
            // Since the renderer is a component, return itself
            return this;
        }
    
        // The following methods override the defaults for performance reasons
        public void validate() {}
        public void revalidate() {}
        protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {}
        public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {}
    }
	
	private Icon arrowUpIcon = null;
	private Icon arrowDownIcon = null;
	
	Icon getArrowUp() {
		
		if (arrowUpIcon  != null) return arrowUpIcon;
		try {
			URL url = getClass().getResource("/lia/images/arrow_up.gif");
			arrowUpIcon = new ImageIcon(url);
		} catch (Exception ex) { 
			arrowUpIcon = null;
		}
		return arrowUpIcon;
	}
	
	Icon getArrowDown() {
		
		if (arrowDownIcon  != null) return arrowDownIcon;
		try {
			URL url = getClass().getResource("/lia/images/arrow_down.gif");
			arrowDownIcon = new ImageIcon(url);
		} catch (Exception ex) { 
			arrowDownIcon = null;
		}
		return arrowDownIcon;
	}
	
	/**	Regardless of sort order (ascending or descending), null values always appear last. 
	 * colIndex specifies a column in model.
	 */
    public void sortAllRows() {
		
		synchronized (lock) {
			if (data == null) return;
			Vector data = new Vector();
			for (int i=0; i<this.data.length; i++) {
				Vector row = new Vector();
				for (int j=0; j<this.data[i].length; j++)
					row.add(this.data[i][j]);
				data.add(row);
			}
			Collections.sort(data, new ColumnSorter());
			// now overright the data...
			for (int i=0; i<this.data.length && i < data.size(); i++) {
				Vector row = (Vector)data.get(i);
				for (int j=0; j<row.size() && j<this.data[i].length; j++)
					this.data[i][j] = row.get(j);
			}
		}
    }
    
    // This comparator is used to sort vectors of data
    public class ColumnSorter implements Comparator {

		int[][] sortingOrder = null;
		
        ColumnSorter() {
			
			if (sortingList.size() == 0) { // hmm, still sort after time at least....
				sortingOrder = new int[][] { { 0, SORT_DOWN } };
				return;
			}
			sortingOrder = new int[sortingList.size()][2];
			for (int i=0; i<sortingList.size(); i++) {
				int el[] = (int[])sortingList.get(i);
				System.arraycopy(el, 0, sortingOrder[i], 0, 2);
			}
        }
		
        public int compare(Object a, Object b) {
			/* so we have two vectors which must be sorted according to the sortingList */
            Vector v1 = (Vector)a;
            Vector v2 = (Vector)b;
			for (int i=0; i<sortingOrder.length; i++) {
				Object o1 = v1.get(sortingOrder[i][0]);
				Object o2 = v2.get(sortingOrder[i][0]);
				// Treat empty strains like nulls
				if (o1 instanceof String && ((String)o1).length() == 0) {
					o1 = null;
				}
				if (o2 instanceof String && ((String)o2).length() == 0) {
					o2 = null;
				}
				// Sort nulls so they appear last, regardless
				// of sort order
				if (o1 == null && o2 == null) { // sort by next column
					continue;
				} else if (o1 == null) {
					return 1;
				} else if (o2 == null) {
					return -1;
				} else if (o1 instanceof Comparable) {
					if (sortingOrder[i][1] == SORT_DOWN) {
						int ret = ((Comparable)o1).compareTo(o2);
						if (ret != 0) return ret;
					} 
					int ret = ((Comparable)o2).compareTo(o1);
					if (ret != 0) return ret;
				} else {
					if (sortingOrder[i][1] == SORT_DOWN) {
						int ret = o1.toString().compareTo(o2.toString());
						if (ret != 0) return ret;
					} 
					int ret = o2.toString().compareTo(o1.toString());
					if (ret != 0) return ret;
				}
			}
			return 0; // in the end if no other method work return equals
        }
    }
	
	public static void main(String args[]) {
		
		JFrame frame = new JFrame("test");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(new BorderLayout());
		
		EnhancedTablePanel panel = new EnhancedTablePanel();
		String[][] data = new String[][] {
				{ "10", "farm1", "cluster1", "node1", "module1", "p1", "v1" },
				{ "20", "farm2", "clister2", "node2", "module2", "p2", "v2" },
				{ "11", "farm4", "cluster6", "node1", "module1", "p1", "v1" },
				{ "22", "farm3", "clister7", "node2", "module2", "p2", "v2" },
				{ "13", "farm0", "cluster3", "node1", "module1", "p1", "v1" },
				{ "21", "farm1", "clister4", "node2", "module2", "p2", "v2" }
		};
		panel.setData(data);
		frame.getContentPane().add(panel, BorderLayout.CENTER);
		frame.setSize(new Dimension(300, 300));
		frame.setVisible(true);
	}
}
