package lia.Monitor.JiniClient.CommonGUI.Tabl;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JTable;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Tabl.TabPanBase.ColumnProperties;
import lia.Monitor.JiniClient.CommonGUI.Tabl.TabPanBase.MyTableModel;

public class TableSorter extends AbstractTableModel//TableMap
        implements TableModelListener {
    /**
     * 
     */
    private static final long serialVersionUID = -2121419638353093443L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(TableSorter.class.getName());

    int indexes[];

    public class SortingColumnsClass {
        /** 
         * contains real positions as values for view columns 
         * change from view position to model position
         * model always has columns ordered: 0 1 2 3 4, while view can have: 2 0 1 3 4
         * index is model column, value is view column
         */
        private int[] model2view;
        /** 
         * index is view column, value is model column
         * this time, the smaller indexes a model column it has, the greater the importance for sorting
         */
        private int[] view2model;

        public SortingColumnsClass() {
            model2view = new int[0];
            view2model = new int[0];
        }

        private void rebuild_view2model() {
            if (view2model.length < model2view.length) {
                view2model = enlarge_array(model2view.length, view2model, true);
            }
            for (int view_col = 0; view_col < view2model.length; view_col++) {
                view2model[view_col] = -1;
                for (int model_col = 0; model_col < model2view.length; model_col++) {
                    if (model2view[model_col] == view_col) {
                        view2model[view_col] = model_col;
                        break;
                    }
                    //it should never be -1
                }
            }
        }

        /**
         * switches between two positions in real_pos array
         * @author mluc
         * @since May 11, 2006
         * @param position1
         * @param position2
         */
        public void xchange(int position1, int position2) {
            if (position1 == position2) {
                return;
            }
            int nCols = getColumnCount();
            int max = nCols;
            if (position1 > max) {
                max = position1;
            }
            if (position2 > max) {
                max = position2;
            }
            if (max > model2view.length) {
                model2view = enlarge_array(max, model2view, true);
            }
            int aux;
            aux = model2view[position1];
            model2view[position1] = model2view[position2];
            model2view[position2] = aux;
            rebuild_view2model();
            //			System.out.println("column "+position1+" moved to "+model2view[position1]+" and column "+position2+" moved to "+model2view[position2]);
        }

        /**
         * grows the array to the specified new size by copying the old values
         * and setting to a default value the rest.
         * @author mluc
         * @since May 11, 2006
         * @param new_size
         * @param old_array
         * @param bIncDefVal increment default value if true; means that values
         * that are added to vector are set with the value of position; if false
         * all aditional values are set to zero
         * @return the resulting new array
         */
        private int[] enlarge_array(int new_size, int[] old_array, boolean bIncDefVal) {
            int[] aux = new int[new_size];
            for (int i = 0; i < old_array.length; i++) {
                aux[i] = old_array[i];
            }
            for (int i = old_array.length; i < new_size; i++) {
                if (bIncDefVal) {
                    aux[i] = i;
                } else {
                    aux[i] = 0;
                }
            }
            return aux;
        }

        /**
         * decreases an array
         * @author mluc
         * @since Jun 24, 2006
         * @param new_size
         * @param old_array
         * @param bIncDefVal
         * @return
         */
        private int[] decrease_array(int new_size, int[] old_array) {
            int[] aux = new int[new_size];
            for (int i = 0; i < new_size; i++) {
                aux[i] = old_array[i];
            }
            return aux;
        }

        /**
         * returns the real column that coresponds to this visible one
         * that means, returns view column for the provided model column
         * @author mluc
         * @since May 10, 2006
         * @param position
         * @return another position, hopefully the right one
         */
        public int get_real_column(int position) {
            int correct_pos = position;
            //			System.out.println("real_pos.length="+real_pos.length);
            if ((position < model2view.length) && (position >= 0)) {
                correct_pos = model2view[position];
            }
            return correct_pos;
        }

        public int get_view_column(int rp) {
            for (int i = 0; i < model2view.length; i++) {
                if (model2view[i] == rp) {
                    return i;
                }
            }
            return rp;
        }

        /**
         * same as get_view_column
         * @author mluc
         * @since Jun 28, 2006
         * @param view_col
         * @return
         */
        public int get_model_column(int view_col) {
            if (view_col < view2model.length) {
                return view2model[view_col];
            }
            view2model = enlarge_array(view_col, view2model, true);
            return view_col;
        }

        public int size() {
            return model2view.length;
        }

        public void moveToLast(int from) {
            int nCols = getColumnCount();
            if (from >= nCols) {
                return;
            }
            if (nCols > model2view.length) {
                model2view = enlarge_array(nCols, model2view, true);
            }
            int aux = model2view[from];
            for (int i = 0; i < nCols; i++) {
                if (model2view[i] > aux) {
                    model2view[i]--;
                }
            }
            model2view[from] = nCols - 1;
            rebuild_view2model();
        }

        /**
         * translates all columns between from and to to the left or right
         * and moves column from-to
         * @author mluc
         * @since Jun 28, 2006
         * @param from position from where to go
         * @param to position where to go
         */
        public void move(int from, int to) {
            if (from == to) {
                return;
            }
            int nCols = getColumnCount();
            int max = nCols;
            if (from > max) {
                max = from;
            }
            if (to > max) {
                max = to;
            }
            if (max > model2view.length) {
                model2view = enlarge_array(max, model2view, true);
            }
            int aux = model2view[from];
            int index = from;
            int increment = to > from ? 1 : -1;
            do {
                if (index != to) {
                    model2view[index] = model2view[index + increment];
                    index += increment;
                }
            } while (index != to);
            model2view[to] = aux;
            rebuild_view2model();
        }
    }

    public SortingColumnsClass sortingColumns = new SortingColumnsClass();

    protected MyTableModel model;

    public TableSorter(MyTableModel model) {
        indexes = new int[0]; // for consistency
        setModel(model);
    }

    public void setModel(MyTableModel model) {
        this.model = model;
        reallocateIndexes();
    }

    @Override
    public String getColumnName(int aColumn) {
        return model.getColumnName(aColumn);
    }

    @Override
    public Class getColumnClass(int aColumn) {
        return model.getColumnClass(aColumn);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;//model.isCellEditable(row, column); 
    }

    /**
     * parses a string and obtains the first double in the string
     * @param text
     * @return
     */
    public static double getNumber(String text) throws NumberFormatException {
        double number = 0.0;
        double fracNumber = 0.1;
        double sign = 1.0;
        boolean bFrac = false;//indicates if we're working before the dot, or after ( ==true)
        boolean bSign = true;//indicates if a sign is expected
        boolean bContainsNumber = false;
        int i = 0;
        while ((i < text.length()) && (text.charAt(i) != '-') && (text.charAt(i) != '+')
                && ((text.charAt(i) < '0') || (text.charAt(i) > '9')) && (text.charAt(i) != '.')) {
            i++;
        }
        while ((i < text.length())
                && ((text.charAt(i) == '-') || (text.charAt(i) == '+')
                        || ((text.charAt(i) >= '0') && (text.charAt(i) <= '9')) || (text.charAt(i) == '.'))) {
            if (text.charAt(i) == '.') {
                if (bFrac) {
                    break;
                }
                bFrac = true;
            } else if ((text.charAt(i) == '-') || (text.charAt(i) == '+')) {
                if (bSign) {
                    if (text.charAt(i) == '-') {
                        sign = -1.0;
                    }
                    bSign = false;
                } else {
                    break;//invalid caracter, so stop
                }
            } else {// between '0' and '9'
                bSign = false;//can't have a sign because we already have a digit
                if (!bFrac) {
                    number = ((number * 10.0) + text.charAt(i)) - '0';
                } else {
                    number += fracNumber * (text.charAt(i) - '0');
                    fracNumber /= 10.0;
                }
                bContainsNumber = true;
            }
            ;
            i++;
        }
        number *= sign;
        if (!bContainsNumber) {
            throw new NumberFormatException("doesn't contains a number.");
        }
        return number;
    }

    public int compareRowsByColumn(int row1, int row2, int column) {
        //Class type = model.getColumnClass(column);
        String sType = model.getColumnType(column);//getValueAt( -1, column);//only the underlying model can return a valid value for -1 row
        // Check for nulls.

        //        Object o1 = data.getValueAt(row1, column);
        //        Object o2 = data.getValueAt(row2, column); 
        String s1, s2;
        s1 = (String) model.getValueAt(row1, column);
        s2 = (String) model.getValueAt(row2, column);
        //System.out.println("comparing "+s1+" with "+s2);

        // If both values are null, return 0.
        if ((s1 == null) && (s2 == null)) {
            return 0;
        } else if (s1 == null) {
            return -1;
        } else if (s2 == null) {
            return 1;
        }

        //System.out.println("type="+sType+" s1= "+s1+" and s2= "+s2);

        if (sType.compareTo("number") == 0) {
            double d1 = -1, d2 = -1;
            try {
                d1 = getNumber(s1);
            } catch (Exception ex) {
            }
            ;
            try {
                d2 = getNumber(s2);
            } catch (Exception ex) {
            }
            ;
            if (d1 < d2) {
                return -1;
            } else if (d1 > d2) {
                return 1;
            }
            return 0;
        } else if (sType.compareTo("text") == 0) {
            s1 = s1.toLowerCase();
            s2 = s2.toLowerCase();
            int result = s1.compareTo(s2);

            if (result < 0) {
                return -1;
            } else if (result > 0) {
                return 1;
            }
            return 0;
        } else if (sType.compareTo("time") == 0) {
            int h1 = 0, h2 = 0, m1 = 0, m2 = 0;
            int nP1, nP2;
            s1 = s1.trim();
            nP1 = s1.indexOf(':');
            if (nP1 != -1) {
                try {
                    h1 = (int) getNumber(s1.substring(0, nP1));//Integer.parseInt(s1.substring(0,nP1)); 
                    m1 = (int) getNumber(s1.substring(nP1 + 1));//Integer.parseInt(s1.substring(nP1+1)); 
                } catch (NumberFormatException nfex) {
                }
                ;
            }
            ;
            s2 = s2.trim();
            nP2 = s2.indexOf(':');
            if (nP2 != -1) {
                try {
                    h2 = (int) getNumber(s2.substring(0, nP2));//Integer.parseInt(s2.substring(0,nP2)); 
                    m2 = (int) getNumber(s2.substring(nP2 + 1));//Integer.parseInt(s2.substring(nP2+1)); 
                } catch (NumberFormatException nfex) {
                }
                ;
            }
            ;

            if (h1 < h2) {
                return -1;
            } else if (h1 > h2) {
                return 1;
            } else if (m1 < m2) {
                return -1;
            } else if (m1 > m2) {
                return 1;
            }
            return 0;
        } else if (sType.compareTo("uptime") == 0) {
            int hour1 = 0, hour2 = 0, min1 = 0, min2 = 0, sec1 = 0, sec2 = 0, day1 = 0, day2 = 0;
            int nP1, nP2, nP1ant = s1.length(), nP2ant = s2.length();
            nP1 = s1.lastIndexOf(':', nP1ant);
            if (nP1 != -1) {
                try {
                    sec1 = (int) getNumber(s1.substring(nP1 + 1));//Integer.parseInt(s1.substring(nP1+1)); 
                } catch (NumberFormatException nfex) {
                    sec1 = 0;
                }
                ;
                nP1ant = nP1 - 1;
                nP1 = s1.lastIndexOf(':', nP1ant);
                if (nP1 != -1) {
                    try {
                        min1 = (int) getNumber(s1.substring(nP1 + 1, nP1ant + 1));//Integer.parseInt(s1.substring(nP1+1, nP1ant+1)); 
                    } catch (NumberFormatException nfex) {
                        min1 = 0;
                    }
                    ;
                    nP1ant = nP1 - 1;
                    nP1 = s1.lastIndexOf(' ', nP1ant);
                    try {
                        hour1 = (int) getNumber(s1.substring(nP1 + 1, nP1ant + 1));//Integer.parseInt(s1.substring(nP1+1, nP1ant+1)); 
                        nP1 = s1.indexOf(' ');
                        if (nP1 != -1) {
                            day1 = (int) getNumber(s1.substring(0, nP1));//Integer.parseInt(s1.substring( 0, nP1));
                        }
                    } catch (NumberFormatException nfex) {
                    }
                    ;
                }
                ;
            }
            ;
            nP2 = s2.lastIndexOf(':', nP2ant);
            if (nP2 != -1) {
                try {
                    sec2 = (int) getNumber(s2.substring(nP2 + 1));//Integer.parseInt(s2.substring(nP2+1)); 
                } catch (NumberFormatException nfex) {
                    sec2 = 0;
                }
                ;
                nP2ant = nP2 - 1;
                nP2 = s2.lastIndexOf(':', nP2ant);
                if (nP2 != -1) {
                    try {
                        min2 = (int) getNumber(s2.substring(nP2 + 1, nP2ant + 1));//Integer.parseInt(s2.substring(nP2+1, nP2ant+1)); 
                    } catch (NumberFormatException nfex) {
                        min2 = 0;
                    }
                    ;
                    nP2ant = nP2 - 1;
                    nP2 = s2.lastIndexOf(' ', nP2ant);
                    try {
                        hour2 = (int) getNumber(s2.substring(nP2 + 1, nP2ant + 1));//Integer.parseInt(s2.substring(nP2+1, nP2ant+1)); 
                        nP2 = s2.indexOf(' ');
                        if (nP2 != -1) {
                            day2 = (int) getNumber(s2.substring(0, nP2));//Integer.parseInt(s2.substring( 0, nP2)); 
                        }
                    } catch (NumberFormatException nfex) {
                    }
                    ;
                }
                ;
            }
            ;

            if (day1 < day2) {
                return -1;
            } else if (day1 > day2) {
                return 1;
            } else if (hour1 < hour2) {
                return -1;
            } else if (hour1 > hour2) {
                return 1;
            } else if (min1 < min2) {
                return -1;
            } else if (min1 > min2) {
                return 1;
            } else if (sec1 < sec2) {
                return -1;
            } else if (sec1 > sec2) {
                return 1;
            }
            return 0;
        } else if (sType.compareTo("version") == 0) {
            String[] nums1 = s1.split("\\.");
            String[] nums2 = s2.split("\\.");
            int n1, n2;
            int i;
            //System.out.println("nums1.length= "+nums1.length+" and nums2.length= "+nums2.length);
            for (i = 0; (i < nums1.length) && (i < nums2.length); i++) {
                n1 = 0;
                try {
                    n1 = (int) getNumber(nums1[i]);//Integer.parseInt(nums1[i]); 
                } catch (NumberFormatException nfex) {
                    n1 = 0;
                }
                ;
                n2 = 0;
                try {
                    n2 = (int) getNumber(nums2[i]);//Integer.parseInt(nums2[i]);
                } catch (NumberFormatException nfex) {
                    n2 = 0;
                }
                ;
                //System.out.println("n1= "+n1+" and n2= "+n2);
                if (n1 < n2) {
                    return -1;
                } else if (n1 > n2) {
                    return 1;
                }
            }
            if (nums1.length < nums2.length) {
                return -1;
            } else if (nums1.length > nums2.length) {
                return 1;
            }
            return 0;
        } else if (sType.compareTo("(xxx%") == 0) {
            int nVal1 = -1, nVal2 = -1;
            int startN = s1.indexOf('(');
            if (startN != -1) {
                int endN = s1.indexOf('%', startN + 1);
                if (endN != -1) {
                    try {
                        nVal1 = (int) getNumber(s1.substring(startN + 1, endN));//Integer.parseInt(s1.substring( startN+1, endN));
                    } catch (NumberFormatException nfex) {
                        nVal1 = 0;
                    }
                }
            }
            startN = s2.indexOf('(');
            if (startN != -1) {
                int endN = s2.indexOf('%', startN + 1);
                if (endN != -1) {
                    try {
                        nVal2 = (int) getNumber(s2.substring(startN + 1, endN));//Integer.parseInt(s2.substring( startN+1, endN));
                    } catch (NumberFormatException nfex) {
                        nVal2 = 0;
                    }
                }
            }
            if (nVal1 < nVal2) {
                return -1;
            } else if (nVal1 > nVal2) {
                return 1;
            }
            return 0;
        } else {
            int result = s1.compareTo(s2);

            if (result < 0) {
                return -1;
            } else if (result > 0) {
                return 1;
            }
            return 0;
        }
    }

    public int compare(int row1, int row2) {
        for (int i = 0; i < getColumnCount(); i++) {
            int sortMode = getSort(sortingColumns.get_model_column(i));
            if (sortMode != 0) {
                boolean ascending = (sortMode == 1 ? true : false);
                int result = compareRowsByColumn(row1, row2, sortingColumns.get_model_column(i));
                if (result != 0) {
                    return ascending ? result : -result;
                }
            }
            ;
        }
        return 0;
    }

    public void reallocateIndexes() {
        int rowCount = getRowCount();

        // Set up a new array of indexes with the right number of elements
        // for the new data model.
        if (rowCount != indexes.length) {
            indexes = new int[rowCount];
        }

        // Initialise with the identity mapping.
        for (int row = 0; row < rowCount; row++) {
            indexes[row] = row;
        }
    }

    @Override
    public void tableChanged(TableModelEvent e) {
        //    	System.out.println("table changed called from: ");
        //    	Thread.currentThread().dumpStack();
        //        reallocateIndexes();
        sort(/*this*/);
        //        tableChanged(e);
    }

    public void checkModel() {
        if (indexes.length != /*model.*/getRowCount()) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Sorter not informed of a change in model.");
            }
        }
    }

    public void sort() {
        if (!model.shouldSort()) {
            return;
        }
        checkModel();
        reallocateIndexes();///?

        shuttlesort(indexes.clone(), indexes, 0, indexes.length);
    }

    public void n2sort() {
        for (int i = 0; i < getRowCount(); i++) {
            for (int j = i + 1; j < getRowCount(); j++) {
                if (compare(indexes[i], indexes[j]) == -1) {
                    swap(i, j);
                }
            }
        }
    }

    // This is a home-grown implementation which we have not had time
    // to research - it may perform poorly in some circumstances. It
    // requires twice the space of an in-place algorithm and makes
    // NlogN assigments shuttling the values between the two
    // arrays. The number of compares appears to vary between N-1 and
    // NlogN depending on the initial order but the main reason for
    // using it here is that, unlike qsort, it is stable.
    public void shuttlesort(int from[], int to[], int low, int high) {
        if ((high - low) < 2) {
            return;
        }
        int middle = (low + high) / 2;
        shuttlesort(to, from, low, middle);
        shuttlesort(to, from, middle, high);

        int p = low;
        int q = middle;

        /* This is an optional short-cut; at each recursive call,
        check to see if the elements in this subset are already
        ordered.  If so, no further comparisons are needed; the
        sub-array can just be copied.  The array must be copied rather
        than assigned otherwise sister calls in the recursion might
        get out of sinc.  When the number of elements is three they
        are partitioned so that the first set, [low, mid), has one
        element and and the second, [mid, high), has two. We skip the
        optimisation when the number of elements is three or less as
        the first compare in the normal merge will produce the same
        sequence of steps. This optimisation seems to be worthwhile
        for partially ordered lists but some analysis is needed to
        find out how the performance drops to Nlog(N) as the initial
        order diminishes - it may drop very quickly.  */

        if (((high - low) >= 4) && (compare(from[middle - 1], from[middle]) <= 0)) {
            for (int i = low; i < high; i++) {
                to[i] = from[i];
            }
            return;
        }

        // A normal merge. 

        for (int i = low; i < high; i++) {
            if ((q >= high) || ((p < middle) && (compare(from[p], from[q]) <= 0))) {
                to[i] = from[p++];
            } else {
                to[i] = from[q++];
            }
        }
    }

    public void swap(int i, int j) {
        int tmp = indexes[i];
        indexes[i] = indexes[j];
        indexes[j] = tmp;
    }

    // The mapping only affects the contents of the data rows.
    // Pass all requests to these rows through the mapping array: "indexes".

    @Override
    public Object getValueAt(int aRow, int aColumn) {
        if (aRow < 0) {
            return model.getValueAt(aRow, aColumn);
        }
        checkModel();
        if (aRow >= indexes.length) {
            reallocateIndexes();
        }
        //        int realColumn = sortingColumns.get_real_column(aColumn);
        //        int realRow = aRow;
        //        if ( aRow>=0 )
        //        	realRow = indexes[aRow];
        return model.getValueAt( /*realRow, realColumn);//,*/indexes[aRow], aColumn);
    }

    public void sortByColumn(int column) {
        ColumnProperties colP = model.getColumnInfo(column);
        colP.setSortMode((colP.getSortMode() + 1) % 3);
        fireTableDataChanged();

    }

    // There is no-where else to put this. 
    // Add a mouse listener to the Table to trigger a table sort 
    // when a column heading is clicked in the JTable. 
    public void addMouseListenerToHeaderInTable(JTable table) {
        final TableSorter sorter = this;
        final JTable tableView = table;
        tableView.setColumnSelectionAllowed(false);
        MouseAdapter listMouseListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                TableColumnModel columnModel = tableView.getColumnModel();
                int viewColumn = columnModel.getColumnIndexAtX(e.getX());
                int column = viewColumn;
                //                column = columnModel.getColumn(viewColumn).getModelIndex();
                column = tableView.convertColumnIndexToModel(column);
                //                int column = viewColumn;//tableView.convertColumnIndexToModel(viewColumn); 
                if ((e.getClickCount() == 1) && (column != -1)) {
                    //                    int shiftPressed = e.getModifiers()&InputEvent.SHIFT_MASK; 
                    //                    boolean ascending = (shiftPressed == 0);
                    sorter.sortByColumn(column);//, ascending); 
                    tableView.getTableHeader().repaint();
                }
            }
        };
        JTableHeader th = tableView.getTableHeader();
        th.addMouseListener(listMouseListener);
    }

    public void addColumnMoveInHeaderInTable(JTable table, final JTable tableTotals) {
        final JTable tableView = table;
        TableColumnModelListener tableColumnModelListener = new TableColumnModelListener() {
            @Override
            public void columnMarginChanged(ChangeEvent e) {
                //        		System.out.println("column margin changed "+e.getSource());
            }

            @Override
            public void columnSelectionChanged(ListSelectionEvent e) {
            }

            @Override
            public void columnAdded(TableColumnModelEvent e) {
            }

            @Override
            public void columnRemoved(TableColumnModelEvent e) {
            }

            /**
             * we get notified when the columns are dragged in other positions so, rearange the sorting columns
             */
            @Override
            public void columnMoved(TableColumnModelEvent e) {
                if (e.getFromIndex() != e.getToIndex()) {
                    //        			System.out.println("model: column moved from "+tableView.convertColumnIndexToModel(e.getFromIndex())+" to "+tableView.convertColumnIndexToModel(e.getToIndex()));
                    sortingColumns.xchange(tableView.convertColumnIndexToModel(e.getFromIndex()),
                            tableView.convertColumnIndexToModel(e.getToIndex()));
                    tableTotals.moveColumn(e.getFromIndex(), e.getToIndex());
                    //        			System.out.println("view: column moved from "+e.getFromIndex()+" to "+e.getToIndex());
                    //        	        sort(/*this*/);
                    /*super.*///tableChanged(new TableModelEvent(this));
                    fireTableDataChanged();
                }
                ;
            }
        };
        tableView.getColumnModel().addColumnModelListener(tableColumnModelListener);
    }

    /**
     * returns a node as provided by getValueAt with column being -1,
     * and the row is transmuted to real value (sorted one)
     * @author mluc
     * @since May 11, 2006
     * @param row
     * @return valid rcNode or null
     */
    public rcNode getNode(int row) {
        return (rcNode) getValueAt(row, -1);
    }

    @Override
    public int getColumnCount() {
        return model.getColumnCount();
    }

    @Override
    public int getRowCount() {
        return model.getRowCount();
    }

    /**
     * finds the sorting mode for the visible column.
     * It first finds the model column, and then queries it for sorting mode.
     * @author mluc
     * @since Jun 25, 2006
     * @param col
     * @return
     */
    public int getSort(int col) {
        ColumnProperties colP = model.getColumnInfo(col);
        if (colP != null) {
            return colP.getSortMode();
        }
        return 0;
    }
}
