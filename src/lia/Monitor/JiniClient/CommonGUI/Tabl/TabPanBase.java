package lia.Monitor.JiniClient.CommonGUI.Tabl;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

import lia.Monitor.JiniClient.CommonGUI.SerMonitorBase;
import lia.Monitor.JiniClient.CommonGUI.graphical;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.monitor.LocalDataFarmClient;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.Monitor.monitor.monPredicate;
import lia.Monitor.tcpClient.MLSerClient;
import net.jini.core.lookup.ServiceID;

public class TabPanBase extends JPanel implements graphical, LocalDataFarmClient, ItemListener {

    /**
     * 
     */
    private static final long serialVersionUID = -5240498996087690970L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(TabPanBase.class.getName());

    protected class ColumnProperties {

        public ColumnProperties(String codename, String type, String name) {
            sCodeName = codename;
            sType = type;
            sName = name;
        }

        public ColumnProperties(String codename, String type, String name, int w, int minw) {
            this(codename, type, name);
            nWidth = w;
            nMinWidth = minw;
        }

        public ColumnProperties(String codename, String type, String name, int w, int minw, TableCellRenderer cellrend) {
            this(codename, type, name, w, minw);
            oRenderer = cellrend;
        }

        /** short name of column */
        private final String sCodeName;
        /** type of column */
        private final String sType;
        /** nice name of column */
        private final String sName;
        private int nMinWidth = -1;
        private int nWidth = -1;
        /** width of column is fixed */
        private boolean bFixed = false;
        private TableCellRenderer oRenderer = null;
        private monPredicate predicate = null;
        private JCheckBoxMenuItem menuitem = null;
        private boolean bVisible = true;
        private TableColumn tabcol = null;
        private TableColumn totalsTC = null;
        /** total value for this column, recomputed at each refresh */
        private Object totalValue = null;
        /** sorting mode: 0 = no sorting, 1 = ascending, 2 = descending */
        private int nSortMode = 0;

        public String getCodeName() {
            return sCodeName;
        }

        public String getName() {
            return sName;
        }

        public String getType() {
            return sType;
        }

        public int getPreferredWidth() {
            return nWidth;
        }

        public int getMinWidth() {
            return nMinWidth;
        }

        public void setMinWidth(int w) {
            nMinWidth = w;
        }

        public TableCellRenderer getCellRenderer() {
            return oRenderer;
        }

        public boolean isFixed() {
            return bFixed;
        }

        public void setFixed(boolean value) {
            bFixed = value;
        }

        public void setPredicate(monPredicate p) {
            predicate = p;
        }

        public monPredicate getPredicate() {
            return predicate;
        }

        public JCheckBoxMenuItem getMenuItem() {
            return menuitem;
        }

        public void setMenuItem(JCheckBoxMenuItem menuitem) {
            this.menuitem = menuitem;
        }

        public boolean isVisible() {
            return bVisible;
        }

        public void setVisible(boolean visible) {
            //			System.out.println("setting column "+getCodeName()+" "+(visible?"":"in")+"visible");
            bVisible = visible;
        }

        public TableColumn getTableColumn() {
            return tabcol;
        }

        public void setTableColumn(TableColumn tabcol) {
            this.tabcol = tabcol;
        }

        public TableColumn getTotalsTableColumn() {
            return totalsTC;
        }

        public void setTotalsTableColumn(TableColumn tabcol) {
            this.totalsTC = tabcol;
        }

        @Override
        public String toString() {
            return sCodeName;
        }

        public int getSortMode() {
            return nSortMode;
        }

        public void setSortMode(int sort) {
            if ((sort >= 0) && (sort <= 2)) {
                nSortMode = sort;
            }
        }

        public Object getTotalValue() {
            return totalValue;
        }

        public void setTotalValue(Object tv) {
            totalValue = tv;
        }
    }

    protected String[] acceptg = {};// "Load5", "CPU_usr", "TotalIO_Rate_IN","TotalIO_Rate_OUT", "FreeDsk" };
    protected String[] addColNames = {};// "<html>\n <b>Load</b><p> mean", "<html>\n <b>CPU_usr</b><p> mean", "<html>\n <b>RateIN [KB/s] </b> <p> mean/total", "<html>\n <b> RateOUT [KB/s] </b><p> mean/total" ,"<html>\n <b> Free Disk [GB] </b><p> Total/Max" };
    //used to compute a column minimal width, it coresponds to 
    //addColNames transformed in regular text
    protected String[] addColNamesText = {};// "Load", "CPU_usr", "RateIN [KB/s]  ", "RateOUT [KB/s] " ,"Free Disk [GB]" };

    /** totals table */
    //	protected JTable tTotals;
    //	protected Vector acolNames ;
    //	protected Vector acolTypes;
    Vector crtNodes;
    JPanel cmdPan;
    SerMonitorBase monitor;
    protected JTable table;
    private JTable tableTotals;
    MyTableModel myModel;
    protected volatile Map<ServiceID, rcNode> nodes;
    protected volatile Vector<rcNode> vnodes;
    //protected rcRenderer myre;
    protected rcRendererTotals myreb;
    protected rcRendererNode myren;
    protected rcRendererProgressBar myrepb;
    rcHeaderRenderer myhre;
    rcHeaderRenderer3 myhre3;
    Border raisedBorder;
    long last_update;
    protected NumberFormat nf;
    public int last_size = 0;
    int last_col_num = 0;
    //	protected int fixed_col_num = 0;/** the number of first columns that have fixed width*/
    ImageIcon arrow_downImg, arrow_upImg, transparentImg;
    Font commonFont;
    public TableSorter sorter;
    boolean bIsVisible = false; //panel is visible or not
    private RefreshThread tRefresh;
    //	private boolean bJustBecomeVisible=false; //panel was hidden and first time when visible, used to refresh nodes

    static final Color firstColor = Color.red;
    static final Color secondColor = Color.green;
    static final Color thirdColor = Color.blue;
    static boolean showColor = Boolean.valueOf(System.getProperty("lia.Monitor.tab.colors", "true")).booleanValue();

    public TabPanBase() {
        super();
        //		acolNames = new Vector ();
        //		acolNames.add ( "<html>\n  <p> <b>Regional Center</b> <p> <i>[select to access] </i>" ); 
        //		acolNames.add ( "<html>\n \n <p> <b>Local Time</b>" );
        //		acolNames.add ( "<html>\n \n <p> <b>MonaLisa<br> Version</b>" );
        //		acolNames.add ( "<html>\n \n <p> <b>Group</b>" );
        //		acolNames.add ( "<html>\n  <b> Free Nodes </b> <p> Load [0 -> 0.25]" );
        last_update = 0;
        nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(1);
        //		ginit();

        crtNodes = new Vector();

        commonFont = new Font("Lucinda Grande", Font.PLAIN, 11);//this.getFont();

        /**
         * TODO: ATTENTION, protected field of DefaultTableCellRenderer modified!
         * <br>this modifies the border for default renderer for any table.<br>
         * A method to change only for this table would be to create another default renderer,
         * in constructor to set the new border, and then, associate it with every column.<br>
         * This should be easy to do, but only if neccessary.
         */
        rcRendererDefault.setNoFocusBorder();
        myreb = new rcRendererTotals();
        myren = new rcRendererNode();
        myhre3 = new rcHeaderRenderer3();
        //		myrepb = new rcRendererProgressBar();
    }

    protected void ginit(MyTableModel rcTableModel) {
        //		last_col_num = vColumns.size();//acolNames.size();
        myModel = rcTableModel;//new rcTableModel();
        //		rcTableModel.setColumns(vColumns);
        tRefresh = new RefreshThread();
        sorter = new TableSorter(myModel);
        table = new JTable(sorter);
        table.getTableHeader().setFont(commonFont);
        tableTotals = new JTable(rcTableModel.getTotalsModel());
        sorter.addColumnMoveInHeaderInTable(table, tableTotals);//obvious: sorter gets notified when column changes in table are reported
        if (commonFont != null) {
            table.setFont(commonFont);
            tableTotals.setFont(commonFont);
        }
        ;
        sorter.addMouseListenerToHeaderInTable(table);//more than obvious: gets notified when mouse in table's header is doing an action
        table.setPreferredScrollableViewportSize(new Dimension(600, 600));
        sorter.addTableModelListener(sorter);//as the help says, it gets notified when the model changes

        arrow_downImg = createImageIcon("lia/images/arrow_down2.gif", "");
        arrow_upImg = createImageIcon("lia/images/arrow_up2.gif", "");
        //		int []pixels = new int[10*10];
        transparentImg = createImageIcon("lia/images/arrow_no2.gif", "");//new ImageIcon(Toolkit.getDefaultToolkit().createImage( new MemoryImageSource(10, 10, pixels, 0, 10)));

        JScrollPane scrollPane = new JScrollPane(table);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        //		table.setAutoResizeMode ( JTable.AUTO_RESIZE_ALL_COLUMNS);

        setLayout(new BorderLayout());

        add(scrollPane, BorderLayout.CENTER);
        table.setBackground(Color.white);

        //set default height for any row in the table
        //this height should be enough for any text in the table
        //a more advanced method would be to set the height as height_of_used_font + 2*2 (vertical spacing)
        table.setRowHeight(20);

        //insert fixed table for totals
        tableTotals.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tableTotals.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        JScrollPane fixedScroll = new JScrollPane(tableTotals) {
            /**
             * 
             */
            private static final long serialVersionUID = 1659918706455910857L;

            @Override
            public void setColumnHeaderView(Component view) {
            } // work around
        };

        fixedScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        //		fixedScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        JScrollBar bar = fixedScroll.getVerticalScrollBar();
        JScrollBar dummyBar = new JScrollBar() {
            /**
             * 
             */
            private static final long serialVersionUID = -6667640611590653631L;

            @Override
            public void paint(Graphics g) {
            }
        };
        dummyBar.setPreferredSize(bar.getPreferredSize());
        fixedScroll.setVerticalScrollBar(dummyBar);

        final JScrollBar bar1 = scrollPane.getHorizontalScrollBar();
        JScrollBar bar2 = fixedScroll.getHorizontalScrollBar();
        bar2.addAdjustmentListener(new AdjustmentListener() {
            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
                bar1.setValue(e.getValue());
            }
        });
        tableTotals.setPreferredScrollableViewportSize(new Dimension(600, tableTotals.getRowHeight()));
        add(fixedScroll, BorderLayout.SOUTH);

        //moved from this position to setSerMonitor because of the need of acces
        //of SerMonitorBase variable
        //not the case any more
        customizeColumns();

        tRefresh.start();
    }

    class RefreshThread extends Thread {
        /** a change in table is expected */
        private final Object objTableChange = new Object();

        @Override
        public void run() {
            boolean run = true;
            while (run) {
                synchronized (objTableChange) {
                    try {
                        objTableChange.wait(4000);
                    } catch (InterruptedException ex) {
                        System.out.println("Interrupted exception, should stop???");
                        ex.printStackTrace();
                    }
                }
                if (bIsVisible) {
                    refreshTableInfo();
                    //	            	sorter.sort();
                }
                ;
            }
            ;
        }

        public void tableChange() {
            synchronized (objTableChange) {
                objTableChange.notify();
            }
        }
    }

    /**
     * updates the table to reflect the new information that may have appeared/come
     * is called from a background thread at a fix inteval (4 seconds) or each time the panel
     * becomes visible. 
     * It doesn't have to be synchronized as the call to show the panel updates the variable bIsVisible only
     * after the first refreshTableInfo has been run, that means that the timer will not be able to run the method
     * at the same time as the setVisble method.
     * But, as it may happed that the timer is executing refresh, the user can make the panel not visible and then visible
     * again, before the refresh ends, there can be 2 simultaneous calls of the refresh function. So, it needs to
     * be synchronized.
     *
     */
    public synchronized void refreshTableInfo() {
        //        System.out.println("table is visible");
        int cur_size = 0;
        if ((nodes != null) && (nodes.size() > 0)) {
            cur_size = nodes.size();
        }
        if ((cur_size == last_size) && (cur_size == 0)) {
            return;
        }

        //			if ( last_size < cur_size ){ 
        //				myModel.fireTableRowsInserted(0, cur_size-1);
        //			} else if ( last_size > cur_size )
        //				myModel.fireTableRowsDeleted( cur_size, last_size);
        //			myModel.fireTableRowsUpdated(0, cur_size-1);
        sorter.fireTableDataChanged();
        table.repaint();
        int table_col_num = myModel.getColumnCount();//should be the same with table.getColumnCount()
        if ((last_size < cur_size) || (last_col_num < table_col_num)) {
            //check number of nodes, if greater, recompute columns widths
            //recompute for each column
            //System.out.println("last cols="+last_col_num+" table cols="+table_col_num);
            //			    int start_col = 0;//fixed_col_num;
            int width;
            //if ( last_col_num < table_col_num )
            // start_col = last_col_num;
            //			    last_col_num = table_col_num;
            try {
                ColumnProperties colP;
                TableColumn tc;
                //			        for ( int col = start_col; col<table_col_num; col++) {
                for (int col = 0; col < myModel.getColumnCount(); col++) {
                    //			        	System.out.println("visible column: "+col +" visible count: "+myModel.getVisibleColCount());
                    colP = myModel.getColumnInfo(col);
                    if (!colP.isFixed() /*&& colP.isVisible()*/) {//colP is always visible
                        tc = colP.getTableColumn();
                        int max_width = tc.getWidth();
                        //for each cell on column
                        for (int row = 0; row < nodes.size(); row++) {
                            width = table.getFontMetrics(commonFont).stringWidth((String) myModel.getValueAt(row, col)) + 10;
                            if (width > max_width) {
                                max_width = width;
                            }
                        }
                        ;
                        if (max_width > tc.getWidth()) {
                            tc.setPreferredWidth(max_width);
                            //				            	TableColumn ttc = colP.getTotalsTableColumn();
                            //				            	if ( ttc!=null )
                            //				            		ttc.setPreferredWidth(max_width);
                        }
                    }
                    ;
                }
                ;
            } catch (Exception ex) {
                System.out.println("Exception in TabPanBase > gupdate @ recomputing column widths.");
                ex.printStackTrace();
            }
        }
        ;
        last_size = cur_size;

        //last_update = now;
        //}
        updateNodes();

        updateTotals();

        //         if ( table!=null && table.getTableHeader()!=null && table.getTableHeader().getDefaultRenderer()!=null )
        //     		System.out.println("default renderer "+table.getTableHeader().getDefaultRenderer().getClass().getName());
    }

    /**
     * updates total values for visible columns using getTotalValueAt function call 
     * @author mluc
     * @since Jun 27, 2006
     */
    private void updateTotals() {
        ColumnProperties colP;
        try {
            boolean bNew = false;
            int colcount = myModel.getColumnCount();
            for (int i = 0; i < colcount; i++) {
                Object sNew = myModel.getTotalValueAt(i);
                if (sNew != null) {
                    bNew = true;
                    colP = myModel.getColumnInfo(i);
                    colP.setTotalValue(sNew);
                    //					System.out.println("set total value for "+colP.getCodeName()+" to "+sNew);
                }
                ;
            }
            if (bNew) {
                ((AbstractTableModel) tableTotals.getModel()).fireTableDataChanged();
            }
        } catch (Exception ex) {
            //no exception??? hmm
        }
    }

    /**
     * registers predicates for new nodes or
     * deletes rezidual nodes
     *
     */
    public void updateNodes() {
        if (vnodes == null) {
            return;
        }

        //        int NRC = vnodes.size();
        ColumnProperties colP;
        // check if there are new nodes
        for (int i = 0; i < vnodes.size(); i++) {
            rcNode n = vnodes.get(i);
            if (!crtNodes.contains(n)) {
                // n is new
                boolean added = false;
                for (int k = 0; k < crtNodes.size(); k++) {
                    rcNode on = (rcNode) crtNodes.get(k);
                    if (on.UnitName.compareToIgnoreCase(n.UnitName) >= 0) {
                        crtNodes.add(k, n);
                        added = true;
                        break;
                    }
                }
                if (!added) {
                    crtNodes.add(n);
                }
                //TODO: check if register for all predicates? also for invisible ones?
                int colcount = myModel.getColumnCount();
                for (int j = 0; j < colcount; j++) {
                    colP = myModel.getColumnInfo(j);
                    if (colP.getPredicate() != null) {
                        //set predicate that will produce data
                        try {
                            n.client.addLocalClient(this, colP.getPredicate());
                        } catch (Throwable t) {
                            logger.log(Level.WARNING, "Failed to addLocalClient", t);
                        }
                    }
                }
                //System.out.println("Table Panel > Registered for data from "+n.UnitName);
            }
        }
        // check if there are removed nodes
        for (int i = 0; i < crtNodes.size(); i++) {
            rcNode n = (rcNode) crtNodes.get(i);
            if (!vnodes.contains(n)) {
                n.client.deleteLocalClient(this);//unregistres panel to receive data from removed farm
                crtNodes.remove(i);
                //System.out.println("Table Panel > Unregistered for data from "+n.UnitName);
                i--;
            }
        }
    }

    @Override
    public void setVisible(boolean bVisible) {
        super.setVisible(bVisible);
        //	    System.out.println("<mluc> <setVisible TabPanBase func> "+bVisible);
        //if there is no available bandwidth, it should not request data for as long as invisible
        //and it should request when visible...???
        bIsVisible = bVisible;
        if (bVisible) {
            //	        refreshTableInfo();
            //	        updateTotals();
            tRefresh.tableChange();
        }
        ;
    }

    public void customizeColumn(ColumnProperties colP) {
        //table.getColumnModel().getColumn(table.getColumnModel().getColumnIndex(colP.getName())).setHeaderRenderer(myhre2);
        TableColumn tc = table.getColumn(colP.getName());
        tc.setHeaderRenderer(myhre3);
        colP.setTableColumn(tc);
        if (colP.getPreferredWidth() != -1) {
            tc.setPreferredWidth(colP.getPreferredWidth());
        }
        if (colP.getMinWidth() != -1) {
            tc.setMinWidth(colP.getMinWidth());
        }
        if (colP.getCellRenderer() != null) {
            tc.setCellRenderer(colP.getCellRenderer());
        }
        if ((colP.getPredicate() != null) && (vnodes != null)) {
            //set predicate that will produce data
            for (int j = 0; j < vnodes.size(); j++) {
                vnodes.get(j).client.addLocalClient(this, colP.getPredicate());
            }
        }
        //customize the totals table
        final TableColumn ttc = tableTotals.getColumn(colP.getName());
        if (ttc != null) {
            colP.setTotalsTableColumn(ttc);
            if (colP.getPreferredWidth() != -1) {
                ttc.setPreferredWidth(colP.getPreferredWidth());
            }
            if (colP.getMinWidth() != -1) {
                ttc.setMinWidth(colP.getMinWidth());
            }
            ttc.setCellRenderer(myreb);
            //			System.out.println("here");
            tc.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent e) {
                    //					TableColumn tableColumn= (TableColumn)e.getSource();
                    //					int index= table.getColumnModel().getColumnIndex(tableColumn.getHeaderValue());
                    //					System.out.println("property change event "+e.getPropertyName()+" for "+index);
                    if (e.getPropertyName().equals("width")) {
                        //						System.out.println("Column Changed: " + index +  ", new value: " + e.getNewValue() );
                        try {
                            if (e.getNewValue() instanceof Integer) {
                                int new_width = Integer.parseInt(e.getNewValue().toString());
                                if (new_width != ttc.getWidth()) {
                                    //									System.out.println("set new width to "+new_width);
                                    //									ttc.setWidth(new_width);
                                    ttc.setPreferredWidth(new_width);
                                }
                            }
                            ;
                        } catch (Exception ex) {
                            //not a number... ignore it
                        }
                    }
                }
            });
        }
    }

    public void customizeColumns() {
        //        String sTrue = "true";
        //        Preferences prefs =null;
        //        if ( monitor!=null && monitor.main.panelTablMenu!=null )
        //        	prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
        ColumnProperties colP;
        int colcount = myModel.getColumnCount();
        for (int i = 0; i < colcount; i++) {
            colP = myModel.getColumnInfo(i);
            customizeColumn(colP/*, i*/);
            addColumnToMenu(colP);
            //            if ( prefs!=null ) {
            //            	boolean bCheckCol = sTrue.equals(prefs.get( "ToolbarMenu.TableSubMenu."+colP.getCodeName(), sTrue));
            //            	if ( !bCheckCol )
            //            		tc.
        }
        //SerMonitorBase.monitor.main.panelTablMenu.add("a");
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
        Object obj = e.getSource();
        if (obj instanceof JCheckBoxMenuItem) {
            ColumnProperties colP = null;
            int index = 0;
            int hiddencols = 0;
            //first search the column properties for this object
            for (; index < myModel.getColumnCount(); index++) {
                colP = myModel.getColumnInfo(index);
                if (colP.getMenuItem().equals(obj)) {
                    break;
                }
                if (!colP.isVisible()) {
                    hiddencols++;
                }
            }
            if (colP != null) {
                //    			System.out.println("header renderer: "+colP.getTableColumn().getHeaderRenderer());

                int nPositionState = e.getStateChange();
                String sShow = "false";
                boolean bShouldShow = false;
                if (nPositionState == ItemEvent.SELECTED) {
                    sShow = "true";
                    bShouldShow = true;
                } else { // nPositionState == ItemEvent.DESELECTED
                }
                Preferences prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
                prefs.put("ToolbarMenu.TableSubMenu." + colP.getCodeName(), sShow);
                try {
                    if ( /*colP.isVisible() && */!bShouldShow) {
                        TableColumn tc = colP.getTableColumn();//table.getColumn( colP.getName());
                        table.removeColumn(tc);
                        tableTotals.removeColumn(colP.getTotalsTableColumn());
                        colP.setSortMode(0);
                        //			            colP.setVisible(bShouldShow);
                        tRefresh.tableChange();
                    } else if ( /*!colP.isVisible() && */bShouldShow) {
                        TableColumn tc = colP.getTableColumn();//new TableColumn(index);
                        table.addColumn(tc);
                        //find index based on number of hidden columns to it
                        int position = myModel.getColumnIndex(colP.getCodeName());
                        sorter.sortingColumns.moveToLast(position);
                        //		            	table.moveColumn(table.getColumnCount()-1, position);
                        //			            colP.setVisible(bShouldShow);
                        TableColumn ttc = colP.getTotalsTableColumn();
                        tableTotals.addColumn(ttc);
                        //		            	tableTotals.moveColumn(tableTotals.getColumnCount()-1, position);
                        tRefresh.tableChange();
                    }
                } catch (IllegalArgumentException iaex) {
                    iaex.printStackTrace();
                }
            }
            ;
        }
    }

    public void addColumnToMenu(ColumnProperties colP) {
        String sTrue = "true";
        Preferences prefs = null;
        if ((monitor != null) && (monitor.main.panelTablMenu != null)) {
            prefs = Preferences.userNodeForPackage(monitor.mainClientClass);
        }
        if ((prefs != null) && (colP.getMenuItem() == null)) {
            boolean bCheckCol = sTrue.equals(prefs.get("ToolbarMenu.TableSubMenu." + colP.getCodeName(), sTrue));
            //            colP.setVisible(bCheckCol);
            if (!bCheckCol) {
                table.removeColumn(colP.getTableColumn());
                tableTotals.removeColumn(colP.getTotalsTableColumn());
                colP.setSortMode(0);
            }
            JCheckBoxMenuItem mi = new JCheckBoxMenuItem(colP.getName().replaceAll("<br>", "")/*getCodeName()*/,
                    bCheckCol);
            colP.setMenuItem(mi);
            monitor.main.panelTablMenu.add(mi);
            mi.addItemListener(this);
        }
        ;
    }

    public void addColumnsToMenu() {
        ColumnProperties colP;
        for (int i = 0; i < myModel.getColumnCount(); i++) {
            colP = myModel.getColumnInfo(i);
            addColumnToMenu(colP);
        }
    }

    /** Returns an ImageIcon, or null if the path was invalid. */
    protected ImageIcon createImageIcon(String path, String description) {
        ClassLoader cl = this.getClass().getClassLoader();
        java.net.URL imgURL = cl.getResource(path);
        if (imgURL != null) {
            //        	System.out.println("img url: "+imgURL);
            return new ImageIcon(imgURL, description);
        }
        return null;
    }

    @Override
    public void new_global_param(String mod) {
        int k = -1;
        if (mod == null) {
            return;
        }
        for (int i = 0; i < acceptg.length; i++) {
            if (mod.equals(acceptg[i])) {
                k = i;
                break;
            }
        }
        if (k < 0) {
            return;
        }

        int width = table.getFontMetrics(table.getFont().deriveFont(Font.BOLD)).stringWidth(addColNamesText[k]) + 10/*aditional spacing*/+ 10/*icon_width*/+ 4/*icon_text_gap*/;
        ColumnProperties colProps = new ColumnProperties(addColNamesText[k], "number", addColNames[k]);
        colProps.setMinWidth(width);
        myModel.addColumn(colProps);
        //acolNames.add ( addColNames[k] ) ;
        //acolTypes.add( "number");
        //		int index = myModel.getVisibleColCount()-1;
        //		TableColumn tc = new TableColumn();// index/*acolNames*/);//,10, null, null );
        /**
         * VERY IMPORTANT!!!
         * 		this is used, i think, only to refresh the table and to set the renderer, because the model
         * 		already knows about the added column
         */
        //		table.addColumn( tc ) ;
        customizeColumn(colProps/*, index*/);
        addColumnToMenu(colProps);

        //        TableColumn ttc = colProps.getTotalsTableColumn();
        //        tableTotals.addColumn(ttc);

        //tc.setCellRenderer( myre );
        //tc.setHeaderRenderer(myhre2);
        /*		if ( width >  xtable.getColumnModel().getColumn(col).getWidth() ) {
        			xtable.getTableHeader().getColumnModel().getColumn(col).setPreferredWidth( width );
        			xtable.getColumnModel().getColumn(col).setPreferredWidth( width);
        		}
        */
        //tc.setMinWidth( width);
        //tc.sizeWidthToFit();
        //table.doLayout();
        //table.revalidate() ; 

    }

    /**
     * change only <b>noFocusBorder</b> hardcodded in DefaultTableCellRenderer 
     *
     * Sep 24, 2004 - 8:42:01 PM
     */
    static class rcRendererDefault extends DefaultTableCellRenderer {
        /**
         * 
         */
        private static final long serialVersionUID = -5665551107238585570L;

        public static void setNoFocusBorder() {
            noFocusBorder = new EmptyBorder(1, 3, 1, 3);
        }
    }

    /**
     * renders a string in bold and spaced from left and right margins with 3 pixels
     *
     * Sep 23, 2004 - 3:27:57 PM
     */
    class rcRendererNode extends DefaultTableCellRenderer {
        /**
         * 
         */
        private static final long serialVersionUID = 191712036949653054L;

        public rcRendererNode() {
            if (commonFont != null) {
                setFont(commonFont);
            }
            if (getFont().getStyle() != Font.BOLD) {
                setFont(getFont().deriveFont(Font.BOLD));
            }
            setBorder(BorderFactory.createLineBorder(Color.gray));
            setHorizontalAlignment(JLabel.CENTER);
        }

        /**
         * rewrite cell renderer function to set custom font
         */
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            //if user clicked in cell, show node window for that cell
            //and deselect row so that there would be no problems
            //col is supposed to be 0
            if (isSelected && (row >= 0) && (row < table.getModel().getRowCount())) {//((TableSorter)table.getModel()).indexes.length ) {
                rcNode node = null;
                try {
                    if (table.getModel() instanceof TableSorter) {
                        node = ((TableSorter) table.getModel()).getNode(row);//vnodes.get(((TableSorter)table.getModel()).indexes[row]);
                    }
                } catch (Exception ex) {
                    node = null;
                }
                if (node != null) {
                    node.client.setVisible(true);
                }
                ;
                table.getSelectionModel().clearSelection();
                isSelected = false;
            }
            ;

            setValue(value);

            return this;
        }
    }

    class rcRendererTotals extends DefaultTableCellRenderer {
        /**
         * 
         */
        private static final long serialVersionUID = 7879681360751202521L;

        public rcRendererTotals() {
            if (commonFont != null) {
                setFont(commonFont);
            }
            if (getFont().getStyle() != Font.BOLD) {
                setFont(getFont().deriveFont(Font.BOLD));
            }
            setBorder(BorderFactory.createLineBorder(Color.gray));
            setHorizontalAlignment(JLabel.CENTER);
        }

        /**
         * rewrite cell renderer function to set custom font
         */
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {

            setValue(value);

            setToolTipText(myModel.getTotalsToolTip(table.convertColumnIndexToModel(column)));
            return this;
        }
    }

    /**
     * renders a string in bold and with border black
     */
    //    class rcRendererBoldBorder extends DefaultTableCellRenderer {
    //        public rcRendererBoldBorder() {
    //            setFont(getFont().deriveFont(Font.BOLD));
    //            setBorder(BorderFactory.createLineBorder(Color.black));
    //        }
    //        
    //        /**
    //         * rewrite cell renderer function to set custom font
    //         */
    //        public Component getTableCellRendererComponent(JTable table, Object value,
    //                boolean isSelected, boolean hasFocus, int row, int column) {
    //            //if user clicked in cell, show node window for that cell
    //            //and deselect row so that there would be no problems
    //            //col is supposed to be 0
    //            if ( isSelected && row>=0 && row < sorter.indexes.length ) {
    //	            rcNode node = null;
    //	            try {
    //	                node = (rcNode) vnodes.get(sorter.indexes[row]);
    //	            } catch (Exception ex) {
    //	                node = null;
    //	            }
    //				if ( node !=null) {
    //					node.client.setVisible(true);
    //				};
    //				table.getSelectionModel().clearSelection();
    //				isSelected = false;
    //            };
    //            
    //            setValue(value); 
    //            
    //            return this;
    //        }
    //    }

    public abstract class MyTableModel extends AbstractTableModel {

        /**
         * 
         */
        private static final long serialVersionUID = 146897842126040551L;
        private final Vector vColumns;

        public MyTableModel(Vector vCols) {
            vColumns = vCols;
        }

        /**
         * @author mluc
         * @since Jun 27, 2006
         * @param column
         * @return
         */
        public abstract String getTotalsToolTip(int col);

        /**
         * constructs an inner class that has one row with the totals
         * for the columns in parent model
         * @author mluc
         * @since Jun 26, 2006
         * @return a new object for totals values
         */
        public TableModel getTotalsModel() {
            return new AbstractTableModel() {

                /**
                 * 
                 */
                private static final long serialVersionUID = -228542352433867826L;

                @Override
                public int getColumnCount() {
                    //		    		System.out.println("MyTotalsTableModel column count called. Value is "+getVisibleColCount());
                    //		    		Thread.dumpStack();
                    return MyTableModel.this.getColumnCount();//getVisibleColCount();
                }

                @Override
                public int getRowCount() {
                    return 1;
                }

                @Override
                public Object getValueAt(int rowIndex, int columnIndex) {
                    //					int realcol = getRealColumn(columnIndex);
                    //					if ( realcol < 0 )
                    //						return null;
                    return ((ColumnProperties) vColumns.get(columnIndex)).getTotalValue();
                }

                @Override
                public String getColumnName(int col) {
                    //					int realcol = getRealColumn(col);
                    //					if ( realcol < 0 )
                    //						return null;
                    return ((ColumnProperties) vColumns.get(col)).getName();
                }
            };
        }

        @Override
        public int getColumnCount() {
            //    		System.out.println("MyTableModel column count called. Value is "+(vColumns!=null?vColumns.size():0));
            //    		Thread.dumpStack();
            if (vColumns != null) {
                return vColumns.size();
            }
            return 0;
        }

        public ColumnProperties getColumnInfo(int col) {
            if ((col < 0) || (vColumns == null) || (col > vColumns.size())) {
                return null;
            }
            return (ColumnProperties) vColumns.get(col);
        }

        public String getColumnCodeName(int col) {
            if ((col < 0) || (vColumns == null) || (col > vColumns.size())) {
                return null;
            }
            return ((ColumnProperties) vColumns.get(col)).getCodeName();
        }

        public String getColumnType(int col) {
            if ((col < 0) || (vColumns == null) || (col > vColumns.size())) {
                return null;
            }
            return ((ColumnProperties) vColumns.get(col)).getType();
        }

        @Override
        public String getColumnName(int col) {
            if ((col < 0) || (vColumns == null) || (col > vColumns.size())) {
                return null;
            }
            return ((ColumnProperties) vColumns.get(col)).getName();
        }

        public int getColumnIndex(String codename) {
            if ((codename == null) || (vColumns == null)) {
                return -1;
            }
            try {
                for (int i = 0; i < vColumns.size(); i++) {
                    if (((ColumnProperties) vColumns.get(i)).getCodeName().equals(codename)) {
                        return i;
                    }
                }
            } catch (Exception ex) {

            }
            return -1;
        }

        /**
         * adds a new column to the model, the table is yet to be notified
         * @author mluc
         * @since Jun 21, 2006
         * @param newCol
         */
        public void addColumn(ColumnProperties newCol) {
            if ((vColumns != null) && (newCol != null)) {
                vColumns.add(newCol);
            }
        }

        @Override
        public Class getColumnClass(int col) {
            return String.class;
        }

        @Override
        public abstract int getRowCount();

        @Override
        public abstract Object getValueAt(int rowIndex, int columnIndex);

        public Object getVisibleTotalValueAt(int columnIndex) {
            return getTotalValueAt(columnIndex);
        }

        public abstract Object getTotalValueAt(int columnIndex);

        /**
         * finds out if there is at least one column set on sort mode (value greater than 0)
         * @author mluc
         * @since Jun 25, 2006
         * @return
         */
        public boolean shouldSort() {
            ColumnProperties colP;
            for (int i = 0; i < vColumns.size(); i++) {
                colP = ((ColumnProperties) vColumns.get(i));
                if (colP.isVisible() && (colP.getSortMode() > 0)) {
                    return true;
                }
            }
            return false;
        }

    }

    protected class rcRendererBg1Label extends JLabel implements TableCellRenderer {
        /**
         * 
         */
        private static final long serialVersionUID = 194893626184935237L;
        private final Color minColor;
        private final Color maxColor;
        int minVal;
        int maxVal;

        public rcRendererBg1Label(int minVal, Color minColor, int maxVal, Color maxColor) {
            super();
            if (commonFont != null) {
                this.setFont(commonFont);
            }
            if (getFont().getStyle() != Font.PLAIN) {
                setFont(getFont().deriveFont(Font.PLAIN));
            }
            this.minVal = minVal;
            this.minColor = minColor;
            this.maxVal = maxVal;
            this.maxColor = maxColor;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            /**
             * decode value and set value and bg color for label
             */
            String sValue = (String) value;
            //System.out.println("here: "+sValue);
            //first set text for label
            setText(sValue);
            int nValue;
            try {
                nValue = (int) Double.parseDouble(sValue);
            } catch (Exception ex) {
                //            	if ( !sValue.equals("N/A") )
                //            		ex.printStackTrace();
                this.setOpaque(false);
                return this;
            }
            if (nValue <= minVal) {
                this.setBackground(minColor);
            } else if (nValue >= maxVal) {
                this.setBackground(maxColor);
            } else {
                int red, green, blue;
                red = minColor.getRed()
                        + (((maxColor.getRed() - minColor.getRed()) * (nValue - minVal)) / (maxVal - minVal));
                green = minColor.getGreen()
                        + (((maxColor.getGreen() - minColor.getGreen()) * (nValue - minVal)) / (maxVal - minVal));
                blue = minColor.getBlue()
                        + (((maxColor.getBlue() - minColor.getBlue()) * (nValue - minVal)) / (maxVal - minVal));
                this.setBackground(new Color(red, green, blue));
            }
            this.setOpaque(true);
            return this;
        }
    }

    protected class rcRendererBg2Label extends JLabel implements TableCellRenderer {
        /**
         * 
         */
        private static final long serialVersionUID = -8556377819676702028L;
        private final Color infminColor;
        private final Color infmaxColor;
        private final Color medColor;
        private final Color supminColor;
        private final Color supmaxColor;
        double infminVal;
        double infmaxVal;
        double supminVal;
        double supmaxVal;

        public rcRendererBg2Label(double infminVal, Color infminColor, double infmaxVal, Color infmaxColor,
                double supminVal, Color supminColor, double supmaxVal, Color supmaxColor, Color medColor) {
            super();
            if (commonFont != null) {
                this.setFont(commonFont);
            }
            if (getFont().getStyle() != Font.PLAIN) {
                setFont(getFont().deriveFont(Font.PLAIN));
            }
            this.infminVal = infminVal;
            this.infminColor = infminColor;
            this.infmaxVal = infmaxVal;
            this.infmaxColor = infmaxColor;
            this.supminVal = supminVal;
            this.supminColor = supminColor;
            this.supmaxVal = supmaxVal;
            this.supmaxColor = supmaxColor;
            this.medColor = medColor;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            /**
             * decode value and set value and bg color for label
             */
            String sValue = (String) value;
            //System.out.println("here: "+sValue);
            //first set text for label
            setText(sValue);
            double nValue;
            try {
                nValue = Double.parseDouble(sValue);
            } catch (Exception ex) {
                //            	if ( !sValue.equals("N/A") )
                //            		ex.printStackTrace();
                this.setOpaque(false);
                return this;
            }
            this.setOpaque(true);
            if (nValue <= infminVal) {
                this.setBackground(infminColor);
            } else if ((nValue > infminVal) && (nValue < infmaxVal)) {
                int red, green, blue;
                double factor = (nValue - infminVal) / (infmaxVal - infminVal);
                red = (int) (infminColor.getRed() + ((infmaxColor.getRed() - infminColor.getRed()) * factor));
                green = (int) (infminColor.getGreen() + ((infmaxColor.getGreen() - infminColor.getGreen()) * factor));
                blue = (int) (infminColor.getBlue() + ((infmaxColor.getBlue() - infminColor.getBlue()) * factor));
                red = (red > 255 ? 255 : (red < 0 ? 0 : red));
                green = (green > 255 ? 255 : (green < 0 ? 0 : green));
                blue = (blue > 255 ? 255 : (blue < 0 ? 0 : blue));
                this.setBackground(new Color(red, green, blue));
            } else if ((nValue >= supminVal) && (nValue < supmaxVal)) {
                int red, green, blue;
                double factor = (nValue - supminVal) / (supmaxVal - supminVal);
                red = (int) (supminColor.getRed() + ((supmaxColor.getRed() - supminColor.getRed()) * factor));
                green = (int) (supminColor.getGreen() + ((supmaxColor.getGreen() - supminColor.getGreen()) * factor));
                blue = (int) (supminColor.getBlue() + ((supmaxColor.getBlue() - supminColor.getBlue()) * factor));
                red = (red > 255 ? 255 : (red < 0 ? 0 : red));
                green = (green > 255 ? 255 : (green < 0 ? 0 : green));
                blue = (blue > 255 ? 255 : (blue < 0 ? 0 : blue));
                this.setBackground(new Color(red, green, blue));
            } else if (nValue >= supmaxVal) {
                this.setBackground(supmaxColor);
            } else {
                this.setBackground(medColor);
            }
            return this;
        }
    }

    protected class rcRendererProgressBar extends JProgressBar implements TableCellRenderer {
        /**
         * 
         */
        private static final long serialVersionUID = 2180961897554365049L;
        private final Color defaultColor;
        private final Color verylightColor;
        private final Color lightColor;
        private final Color darkColor;
        private final Color verydarkColor;

        public rcRendererProgressBar() {
            super(0, 100);
            //setString("Unknown");
            setStringPainted(true);
            defaultColor = getForeground();
            int r, g, b;
            int surplus = 20;
            r = defaultColor.getRed();
            g = defaultColor.getGreen();
            b = defaultColor.getBlue();
            int r1, g1, b1;
            r1 = r + surplus;
            if (r1 > 255) {
                r1 = 255;
            }
            if (r1 < 0) {
                r1 = 0;
            }
            g1 = g + surplus;
            if (g1 > 255) {
                g1 = 255;
            }
            if (g1 < 0) {
                g1 = 0;
            }
            b1 = b + surplus;
            if (b1 > 255) {
                b1 = 255;
            }
            if (b1 < 0) {
                b1 = 0;
            }
            lightColor = new Color(r1, g1, b1);
            r1 = r + (2 * surplus);
            if (r1 > 255) {
                r1 = 255;
            }
            if (r1 < 0) {
                r1 = 0;
            }
            g1 = g + (2 * surplus);
            if (g1 > 255) {
                g1 = 255;
            }
            if (g1 < 0) {
                g1 = 0;
            }
            b1 = b + (2 * surplus);
            if (b1 > 255) {
                b1 = 255;
            }
            if (b1 < 0) {
                b1 = 0;
            }
            verylightColor = new Color(r1, g1, b1);
            r1 = r - surplus;
            if (r1 > 255) {
                r1 = 255;
            }
            if (r1 < 0) {
                r1 = 0;
            }
            g1 = g - surplus;
            if (g1 > 255) {
                g1 = 255;
            }
            if (g1 < 0) {
                g1 = 0;
            }
            b1 = b - surplus;
            if (b1 > 255) {
                b1 = 255;
            }
            if (b1 < 0) {
                b1 = 0;
            }
            darkColor = new Color(r1, g1, b1);
            r1 = r - (2 * surplus);
            if (r1 > 255) {
                r1 = 255;
            }
            if (r1 < 0) {
                r1 = 0;
            }
            g1 = g - (2 * surplus);
            if (g1 > 255) {
                g1 = 255;
            }
            if (g1 < 0) {
                g1 = 0;
            }
            b1 = b - (2 * surplus);
            if (b1 > 255) {
                b1 = 255;
            }
            if (b1 < 0) {
                b1 = 0;
            }
            verydarkColor = new Color(r1, g1, b1);
        }

        private Color getForegroundColor(int nVal) {

            if (nVal < 50) {
                int r1 = firstColor.getRed();
                int g1 = firstColor.getGreen();
                int b1 = firstColor.getBlue();
                int r2 = secondColor.getRed();
                int g2 = secondColor.getGreen();
                int b2 = secondColor.getBlue();
                int r = r1 + (((r2 - r1) * nVal) / 50);
                int g = g1 + (((g2 - g1) * nVal) / 50);
                int b = b1 + (((b2 - b1) * nVal) / 50);
                if (r < 0) {
                    r = 0;
                }
                if (r > 255) {
                    r = 255;
                }
                if (g < 0) {
                    g = 0;
                }
                if (g > 255) {
                    g = 255;
                }
                if (b < 0) {
                    b = 0;
                }
                if (b > 255) {
                    b = 255;
                }
                return new Color(r, g, b);
            }
            int r1 = secondColor.getRed();
            int g1 = secondColor.getGreen();
            int b1 = secondColor.getBlue();
            int r2 = thirdColor.getRed();
            int g2 = thirdColor.getGreen();
            int b2 = thirdColor.getBlue();
            int r = r1 + (((r2 - r1) * (nVal - 50)) / 50);
            int g = g1 + (((g2 - g1) * (nVal - 50)) / 50);
            int b = b1 + (((b2 - b1) * (nVal - 50)) / 50);
            if (r < 0) {
                r = 0;
            }
            if (r > 255) {
                r = 255;
            }
            if (g < 0) {
                g = 0;
            }
            if (g > 255) {
                g = 255;
            }
            if (b < 0) {
                b = 0;
            }
            if (b > 255) {
                b = 255;
            }
            return new Color(r, g, b);

        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            /**
             * decode value and set value and text for progressbar
             */
            String sValue = (String) value;
            int nVal = 0;
            if (sValue != null) {
                int startN = sValue.indexOf('(');
                if (startN != -1) {
                    int endN = sValue.indexOf('%', startN + 1);
                    if (endN != -1) {
                        try {
                            nVal = Integer.parseInt(sValue.substring(startN + 1, endN));
                        } catch (NumberFormatException nfex) {
                            nVal = 0;
                        }
                    }
                }
                ;
            }

            if (showColor) {
                if (nVal < 20) {
                    setForeground(verylightColor);
                } else if (nVal < 40) {
                    setForeground(lightColor);
                } else if (nVal < 60) {
                    setForeground(defaultColor);
                } else if (nVal < 80) {
                    setForeground(darkColor);
                } else {
                    setForeground(verydarkColor);
                }
            } else {
                setForeground(getForegroundColor(nVal));
            }
            setValue(nVal);
            setString(sValue);
            return this;
        }

        /*
         * The following methods are overridden as a performance measure to 
         * to prune code-paths are often called in the case of renders
         * but which we know are unnecessary.  Great care should be taken
         * when writing your own renderer to weigh the benefits and 
         * drawbacks of overriding methods like these.
         */

        /**
         * Overridden for performance reasons.
         * See the <a href="#override">Implementation Note</a> 
         * for more information.
         */
        @Override
        public boolean isOpaque() {
            Color back = getBackground();
            Component p = getParent();
            if (p != null) {
                p = p.getParent();
            }
            // p should now be the JTable. 
            boolean colorMatch = (back != null) && (p != null) && back.equals(p.getBackground()) && p.isOpaque();
            return !colorMatch && super.isOpaque();
        }

        /**
         * Overridden for performance reasons.
         * See the <a href="#override">Implementation Note</a> 
         * for more information.
         */
        @Override
        public void validate() {
        }

        /**
         * Overridden for performance reasons.
         * See the <a href="#override">Implementation Note</a> 
         * for more information.
         */
        @Override
        public void revalidate() {
        }

        /**
         * Overridden for performance reasons.
         * See the <a href="#override">Implementation Note</a> 
         * for more information.
         */
        @Override
        public void repaint(long tm, int x, int y, int width, int height) {
        }

        /**
         * Overridden for performance reasons.
         * See the <a href="#override">Implementation Note</a> 
         * for more information.
         */
        @Override
        public void repaint(Rectangle r) {
        }

        /**
         * Overridden for performance reasons.
         * See the <a href="#override">Implementation Note</a> 
         * for more information.
         */
        @Override
        protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
            // Strings get interned...
            if (propertyName.equals("text")) {
                super.firePropertyChange(propertyName, oldValue, newValue);
            }
        }

        /**
         * Overridden for performance reasons.
         * See the <a href="#override">Implementation Note</a> 
         * for more information.
         */
        @Override
        public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
        }
    }

    class rcRenderer extends JLabel implements TableCellRenderer {
        /**
         * 
         */
        private static final long serialVersionUID = -5074633602292707198L;
        Border unselectedBorder = null;
        Border selectedBorder = null;
        boolean isBordered = true;

        public rcRenderer(boolean isBordered) {
            super();
            this.isBordered = isBordered;
            setOpaque(true); //MUST do this for background to show up.
            setPreferredSize(new Dimension(100, 20));
        }

        @Override
        public Component getTableCellRendererComponent(JTable xtable, Object object, boolean isSelected,
                boolean hasFocus, int row, int col) {

            setBackground(Color.blue);
            if (isBordered) {
                if (isSelected) {
                    if (selectedBorder == null) {
                        selectedBorder = BorderFactory.createMatteBorder(2, 5, 2, 5, xtable.getSelectionBackground());
                    }
                    setBorder(selectedBorder);
                } else {
                    if (unselectedBorder == null) {
                        unselectedBorder = BorderFactory.createMatteBorder(2, 5, 2, 5, xtable.getBackground());
                    }
                    setBorder(unselectedBorder);
                }
            }
            /**
             * this is a piece of code that makes the table obey the preferences of a cell component, at least
             * for the height. By default, as the manual says, the height of a table row is 16 pixels. By removing
             * this code, the component may become partially invisible.
             */
            /*
            if ( object instanceof JComponent ) {
            	Dimension dx =((JComponent) object).getPreferredSize();
            	if ( dx.height >  xtable.getRowHeight (row ) ) {
            		xtable.setRowHeight ( row, dx.height );
            	}
            	//if ( dx.width > xtable.getColumnWidth( col ) ) {
            	//    xtable.setColumnWidth ( dx.width ) ;
            	//}
            }
            */
            if ((col == 0) && isSelected) {
                //
                //				JLabel lbs = (JLabel) object ;
                //				String text = lbs.getText();
                //				rcNode node = (rcNode) nodes.get (text ); 				
                rcNode node = null;
                if (xtable.getModel() instanceof TableSorter) {
                    node = ((TableSorter) xtable.getModel()).getNode(row);//vnodes.get(((TableSorter)table.getModel()).indexes[row]);
                }
                if (node != null) {
                    node.client.setVisible(true);
                    xtable.getSelectionModel().clearSelection();
                    //					if (!node.client.isVisible() ) 
                    //					node.client.setVisible(true);
                    isSelected = false;
                }
                ;
            }
            JLabel jlb = null;
            if (object != null) {
                if (object instanceof JLabel) {
                    jlb = /*return*/(JLabel) object;
                    /*
                     * for columns that are already jlabel, turn them bold
                     * --- it applies to column 0 ---
                     */
                    if (commonFont != null) {
                        jlb.setFont(commonFont.deriveFont(Font.BOLD));
                    }
                    return jlb;
                } else if (object instanceof JProgressBar) {
                    if (commonFont != null) {
                        ((JProgressBar) object).setFont(commonFont);
                    }
                    return (JProgressBar) object;
                } else if (object instanceof ImageIcon) {
                    return new JLabel("", (ImageIcon) object, javax.swing.SwingConstants.RIGHT);
                } else if (object instanceof String) {
                    //System.out.println("here: "+object);
                    jlb = new JLabel((String) object);
                } else if (object instanceof Number) {
                    jlb = new JLabel(object.toString());
                }
            }
            /*
             * for a jlabel get its text width and set the width of the column that
             * contains it if neccessary
             */
            if (jlb == null) {
                jlb = new JLabel("Unknown");
            }

            if (commonFont != null) {
                jlb.setFont(commonFont);
            }
            //de scos?
            int width = xtable.getFontMetrics(jlb.getFont()).stringWidth(jlb.getText()) + 6;
            if (width > xtable.getColumnModel().getColumn(col).getWidth()) {
                xtable.getTableHeader().getColumnModel().getColumn(col).setPreferredWidth(width);
                xtable.getColumnModel().getColumn(col).setPreferredWidth(width);
            }
            return jlb;
        }
    }

    class rcHeaderRenderer2 extends DefaultTableCellRenderer {
        /**
         * 
         */
        private static final long serialVersionUID = 6487541342061024293L;

        public rcHeaderRenderer2() {
            setIcon(transparentImg);
            setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
        }

        @Override
        public Component getTableCellRendererComponent(JTable xtable, Object object, boolean isSelected,
                boolean hasFocus, int row, int col) {
            int sortMode = 0;
            if (xtable.getModel() instanceof TableSorter) {
                sortMode = ((TableSorter) xtable.getModel()).getSort(col);
            }
            if (sortMode == 1) {
                setIcon(arrow_upImg);
            } else if (sortMode == 2) {
                setIcon(arrow_downImg);
            } else {
                setIcon(transparentImg);
            }
            setValue(object);
            return this;
        }
    }

    class rcHeaderRenderer3 extends DefaultTableCellRenderer {
        /**
         * 
         */
        private static final long serialVersionUID = 7658965792497979410L;

        public rcHeaderRenderer3() {
            setIcon(transparentImg);
            setBorder(UIManager.getBorder("TableHeader.cellBorder"));
            setVerticalAlignment(JLabel.CENTER);
            setHorizontalAlignment(JLabel.CENTER);
            if (commonFont != null) {
                this.setFont(commonFont);
            }
            if (getFont().getStyle() != Font.BOLD) {
                setFont(getFont().deriveFont(Font.BOLD));
                //			setVerticalTextPosition(JLabel.CENTER);
                //			setHorizontalTextPosition(JLabel.CENTER);
            }
        }

        @Override
        public Component getTableCellRendererComponent(JTable xtable, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            if (xtable != null) {
                JTableHeader header = table.getTableHeader();
                if (header != null) {
                    setForeground(header.getForeground());
                    setBackground(header.getBackground());
                    //					setFont(header.getFont());
                }
            }

            //			setText((value == null) ? "" : value.toString());
            int sortMode = 0;
            if (xtable.getModel() instanceof TableSorter) {

                sortMode = ((TableSorter) xtable.getModel()).getSort(xtable.convertColumnIndexToModel(column));
            }
            if (sortMode == 1) {
                setIcon(arrow_upImg);
            } else if (sortMode == 2) {
                setIcon(arrow_downImg);
            } else {
                setIcon(transparentImg);
            }
            try {
                setValue(value);
            } catch (Throwable t) {
            }
            return this;
        }
    }

    /**
     * BAD implementation for two reasons:<br>
     * 1) for each cell a new JLabel is created, providing an imense overhead, and<br>
     * 2) the cell renderer is derived from JLabel instead of DefaultTableCellRenderer, which is a JLabel
     * optimized for table rendering; any way, because of point 1)  point 2) does nothing
     */
    class rcHeaderRenderer extends JLabel implements TableCellRenderer {

        /**
         * 
         */
        private static final long serialVersionUID = 2857576965161485887L;

        @Override
        public Component getTableCellRendererComponent(JTable xtable, Object object, boolean isSelected,
                boolean hasFocus, int row, int col) {
            if (row != -1) {
                return null;
            }
            if (object instanceof String) {
                try {
                    JLabel jlb = new JLabel((String) object);
                    if (commonFont != null) {
                        jlb.setFont(commonFont);
                    }
                    //jlb.setFont(xtable.getFont().deriveFont(Font.BOLD));
                    int sortMode = 0;
                    if (xtable.getModel() instanceof TableSorter) {
                        sortMode = ((TableSorter) xtable.getModel()).getSort(col);
                    }
                    //use convertColumnIndexToModel because the columns are given for the viewable model, and 
                    //must be converted to the internal model, real and initial position of columns by using this function
                    //xtable.convertColumnIndexToModel(col));
                    if (sortMode == 1) {
                        jlb.setIcon(arrow_upImg);
                        //							if ( arrow_downImg == null )
                    } else if (sortMode == 2) {
                        jlb.setIcon(arrow_downImg);
                    } else {
                        jlb.setIcon(transparentImg);
                    }
                    jlb.setOpaque(true);
                    jlb.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED));
                    //System.out.println(" col="+col+" height="+jlb.getHeight());
                    return jlb;
                } catch (Exception ex) {
                }
                ;
            }
            return new JLabel("");
        }
    }

    @Override
    public void updateNode(rcNode node) {
    }

    @Override
    public synchronized void setNodes(Map<ServiceID, rcNode> nodes, Vector<rcNode> vnodes) {
        this.nodes = nodes;
        this.vnodes = vnodes;
    }

    @Override
    public void gupdate() {

        // table.revalidate() ; //paint() ;
    }

    @Override
    public void setSerMonitor(SerMonitorBase monitor) {
        this.monitor = monitor;
        //customizeColumns();
    }

    @Override
    public void setMaxFlowData(rcNode n, Vector v) {
        //		grapPan.setMaxFlowData(n, v) ; i
    }

    /** this will store the new result in the structures for easy plotting */
    void setResult(MLSerClient client, Result r) {
        if ((r == null) || (r.param_name == null) || (r.param == null)) {
            return;
        }
        rcNode node = nodes.get(client.tClientID);
        for (int i = 0; i < r.param.length; i++) {
            if ((node != null) && (node.haux != null)) {
                node.haux.put("TabPan>" + r.param_name[i], "" + r.param[i]);
                //System.out.println("New Result: TabPan>"+r.param_name[i]+" with value: "+ r.param[i]);
            }
        }
        ;
    }

    /** this is called when a new result is received */
    @Override
    public void newFarmResult(MLSerClient client, Object ro) {
        if (ro == null) {
            return;
        }

        //System.out.println("vojobs result: "+ro); 
        if (ro instanceof Result) {
            Result r = (Result) ro;
            //logger.log(Level.INFO, "VOJobs Result from "+client.farm.name+" = "+r);
            setResult(client, r);
        } else if (ro instanceof eResult) {
            //  System.out.println(" Got eResult " + ro);
        } else if (ro instanceof Vector) {
            Vector vr = (Vector) ro;
            //System.out.println(new Date()+" V["+vr.size()+"] from "+client.farm.name);
            for (int i = 0; i < vr.size(); i++) {
                newFarmResult(client, vr.elementAt(i));
            }
        } else {
            //logger.log(Level.WARNING, "Wrong Result type in VoJob from "+client.farm.name+": " + ro);
            return;
        }
    }

}