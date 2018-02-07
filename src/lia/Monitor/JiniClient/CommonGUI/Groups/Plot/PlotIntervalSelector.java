package lia.Monitor.JiniClient.CommonGUI.Groups.Plot;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Calendar;
import java.util.TimeZone;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import lia.util.ntp.NTPDate;

public class PlotIntervalSelector 
						extends JDialog 
						implements ActionListener, ChangeListener {

    private String [] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", 
    		"Jun", "Jul", "Aug", "Sept", "Oct", "Nov", "Dec" };
    private String [] dowNames = {"Sun", "Mon", "Tue", "Wed", "Thu", 
    		"Fri", "Sat"};
    private JButton btnCancel;
    private JButton btnOK;
    private JComboBox cbFixEndDay;
    private JComboBox cbFixEndHour;
    private JComboBox cbFixEndMin;
    private JComboBox cbFixEndSec;
    private JComboBox cbFixStartDay;
    private JComboBox cbFixStartHour;
    private JComboBox cbFixStartMin;
    private JComboBox cbFixStartSec;
    private JComboBox cbRelEndDay;
    private JComboBox cbRelEndHour;
    private JComboBox cbRelEndMin;
    private JComboBox cbRelEndSec;
    private JTabbedPane intervalTabs;
    private JLabel jLabel1;
    private JLabel jLabel10;
    private JLabel jLabel11;
    private JLabel jLabel12;
    private JLabel jLabel14;
    private JLabel jLabel15;
    private JLabel jLabel2;
    private JLabel jLabel3;
    private JLabel jLabel4;
    private JLabel jLabel5;
    private JLabel jLabel6;
    private JLabel jLabel7;
    private JLabel jLabel9;
    private JPanel panFixedTab;
    private JPanel panOkCancel;
    private JPanel panRelativeTab;
    private JPanel panTabs;
    private JSpinner spinRelMin;
    private long minTime;
    private boolean closedOK = false;
    private TimeZone timeZone;
    private long currentTime;
    // End of variables declaration

    private static final String[][] tzs = {
            {"A", "GMT+1"},
            {"ACDT", "GMT+10"} ,
            {"ACST", "GMT+9"},
            {"ADT", "GMT-3"}, 
            {"AEDT", "GMT+11"},
            {"AEST", "GMT+10"},
            {"AKDT", "GMT-8"}, 
            {"AKST", "GMT-9"}, 
            {"AST", "GMT-4"}, 
            {"AWST", "GMT+8"}, 
            {"B", "GMT+2"}, 
            {"BST", "GMT+1"}, 
            {"C", "GMT+3"}, 
            {"CDT", "GMT+10"},
            {"CDTA", "GMT+10"},
            {"CDTN", "GMT-5"},
            {"CEST", "GMT+2"}, 
            {"CET", "GMT+1"}, 
            {"CST", "GMT+9"}, 
            {"CSTA", "GMT+9"},
            {"CSTN", "GMT-6"},
            {"CXT", "GMT+7"}, 
            {"D", "GMT+4"}, 
            {"E", "GMT+5"}, 
            {"EDT", "GMT+11"},
            {"EDTA", "GMT+11"},
            {"EDTN", "GMT-4"},
            {"EEST", "GMT+3"}, 
            {"EET", "GMT+2"}, 
            {"EST", "GMT+10"},
            {"ESTA", "GMT+10"},
            {"ESTN", "GMT-5"},
            {"F", "GMT+6"}, 
            {"G", "GMT+7"}, 
            {"GMT", "GMT"},
            {"H", "GMT+8"}, 
            {"HAA", "GMT-3"}, 
            {"HAC", "GMT-5"}, 
            {"HADT", "GMT-9"}, 
            {"HAE", "GMT-4"}, 
            {"HAP", "GMT-7"}, 
            {"HAR", "GMT-6"}, 
            {"HAST", "GMT-10"}, 
            {"HAT", "GMT-2"}, 
            {"HAY", "GMT-8"}, 
            {"HNA", "GMT-4"}, 
            {"HNC", "GMT-6"}, 
            {"HNE", "GMT-5"}, 
            {"HNP", "GMT-8"}, 
            {"HNR", "GMT-7"}, 
            {"HNT", "GMT-3"}, 
            {"HNY", "GMT-9"}, 
            {"I", "GMT+9"}, 
            {"IST", "GMT+1"}, 
            {"K", "GMT+10"}, 
            {"L", "GMT+11"}, 
            {"M", "GMT+12"}, 
            {"MDT", "GMT-6"}, 
            {"MESZ", "GMT+2"}, 
            {"MEZ", "GMT+1"}, 
            {"MST", "GMT-7"}, 
            {"N", "GMT-1"}, 
            {"NDT", "GMT-2"}, 
            {"NFT", "GMT+11"}, 
            {"NST", "GMT-3"}, 
            {"O", "GMT-2"}, 
            {"P", "GMT-3"}, 
            {"PDT", "GMT-7"}, 
            {"PST", "GMT-8"}, 
            {"Q", "GMT-4"}, 
            {"R", "GMT-5"}, 
            {"S", "GMT-6"}, 
            {"T", "GMT-7"}, 
            {"U", "GMT-8"}, 
            {"UTC", "GMT"}, 
            {"V", "GMT-9"}, 
            {"W", "GMT-10"}, 
            {"WEST", "GMT+1"}, 
            {"WET", "GMT"}, 
            {"WST", "GMT+8"}, 
            {"X", "GMT-11"}, 
            {"Y", "GMT-12"}, 
            {"Z", "GMT"}};

    /** Creates new form PlotIntervalSelector */
    public PlotIntervalSelector(Frame parent, long start, long end, 
    		String localTime, String timeZone) {
        super(parent, true);
        boolean validTime = false;
        int h = 0, m = 0;
        try{
            this.timeZone = TimeZone.getTimeZone(formatTimeZone(timeZone));
        	h = Integer.parseInt(localTime.substring(0, 2));
        	m = Integer.parseInt(localTime.substring(3, 5));
        	validTime = true;
        }catch(Exception ex){
        	this.timeZone = TimeZone.getDefault();
        }
        initComponents();
        Calendar cal = Calendar.getInstance(this.timeZone);
        if(validTime){
        	cal.set(Calendar.HOUR_OF_DAY, h);
        	cal.set(Calendar.MINUTE, m);
        }
        currentTime = cal.getTimeInMillis();
        if(end > currentTime || end < -1)
        	end = currentTime;
        initComboBoxes(start, end);
        addListeners();
        if(end == -1){
        	cbFixEndDay.setSelectedIndex(0);
        }
        setLocationRelativeTo(parent);
    }

    /**
     * this is used to convert a timezone from unix format to gmt+X format
     * that is understood by the Calendar class  
     */
	private String formatTimeZone(String tz) {
		if (tz == null)
			return "GMT";
		for (int i=0; i<tzs.length; i++)
			if (tz.equals(tzs[i][0]))
				return tzs[i][1];
		return tz;
	}
    
    /** 
     * here we initialize all components on this dialog
     */
    private void initComponents() {
        panTabs = new JPanel();
        intervalTabs = new JTabbedPane();
        panFixedTab = new JPanel();
        jLabel2 = new JLabel();
        jLabel3 = new JLabel();
        cbFixStartDay = new JComboBox();
        jLabel9 = new JLabel();
        jLabel10 = new JLabel();
        jLabel11 = new JLabel();
        cbFixEndDay = new JComboBox();
        jLabel14 = new JLabel();
        jLabel15 = new JLabel();
        jLabel12 = new JLabel();
        cbFixStartHour = new JComboBox();
        cbFixStartMin = new JComboBox();
        cbFixEndSec = new JComboBox();
        cbFixEndHour = new JComboBox();
        cbFixEndMin = new JComboBox();
        cbFixStartSec = new JComboBox();
        panRelativeTab = new JPanel();
        spinRelMin = new JSpinner();
        jLabel1 = new JLabel();
        jLabel4 = new JLabel();
        cbRelEndDay = new JComboBox();
        jLabel5 = new JLabel();
        jLabel6 = new JLabel();
        jLabel7 = new JLabel();
        cbRelEndHour = new JComboBox();
        cbRelEndMin = new JComboBox();
        cbRelEndSec = new JComboBox();
        panOkCancel = new JPanel();
        btnOK = new JButton();
        btnCancel = new JButton();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Please select plot interval");
        setModal(true);
        setResizable(false);
        panTabs.setLayout(new java.awt.BorderLayout());

        panTabs.setPreferredSize(new java.awt.Dimension(260, 180));
        intervalTabs.setPreferredSize(new java.awt.Dimension(150, 100));
        panFixedTab.setLayout(null);

        jLabel2.setText("Starting:");
        panFixedTab.add(jLabel2);
        jLabel2.setBounds(20, 20, 60, 15);

        jLabel3.setText("Ending:");
        panFixedTab.add(jLabel3);
        jLabel3.setBounds(20, 90, 50, 15);

        panFixedTab.add(cbFixStartDay);
        cbFixStartDay.setBounds(80, 20, 165, 24);
        
        jLabel9.setText("at");
        panFixedTab.add(jLabel9);
        jLabel9.setBounds(50, 120, 20, 15);

        jLabel10.setText(":");
        panFixedTab.add(jLabel10);
        jLabel10.setBounds(130, 120, 10, 15);

        jLabel11.setText(":");
        panFixedTab.add(jLabel11);
        jLabel11.setBounds(190, 120, 10, 15);

        panFixedTab.add(cbFixEndDay);
        cbFixEndDay.setBounds(80, 90, 165, 24);

        jLabel14.setText(":");
        panFixedTab.add(jLabel14);
        jLabel14.setBounds(130, 50, 10, 15);

        jLabel15.setText(":");
        panFixedTab.add(jLabel15);
        jLabel15.setBounds(190, 50, 10, 15);

        jLabel12.setText("at");
        panFixedTab.add(jLabel12);
        jLabel12.setBounds(50, 50, 20, 15);

        panFixedTab.add(cbFixStartHour);
        cbFixStartHour.setBounds(80, 50, 45, 24);
        
        panFixedTab.add(cbFixStartMin);
        cbFixStartMin.setBounds(140, 50, 45, 24);
        
        panFixedTab.add(cbFixEndSec);
        cbFixEndSec.setBounds(200, 120, 45, 24);
        
        panFixedTab.add(cbFixEndHour);
        cbFixEndHour.setBounds(80, 120, 45, 24);
        
        panFixedTab.add(cbFixEndMin);
        cbFixEndMin.setBounds(140, 120, 45, 24);
        
        panFixedTab.add(cbFixStartSec);
        cbFixStartSec.setBounds(200, 50, 45, 24);
        
        panRelativeTab.setLayout(null);

        panRelativeTab.add(spinRelMin);
        spinRelMin.setBounds(160, 20, 85, 20);

        jLabel1.setText("Number of minutes:");
        panRelativeTab.add(jLabel1);
        jLabel1.setBounds(20, 20, 140, 15);

        jLabel4.setText("Ending:");
        panRelativeTab.add(jLabel4);
        jLabel4.setBounds(20, 90, 50, 15);

        panRelativeTab.add(cbRelEndDay);
        cbRelEndDay.setBounds(80, 90, 165, 24);

        jLabel5.setText("at");
        panRelativeTab.add(jLabel5);
        jLabel5.setBounds(50, 120, 20, 15);

        jLabel6.setText(":");
        panRelativeTab.add(jLabel6);
        jLabel6.setBounds(130, 120, 10, 15);

        jLabel7.setText(":");
        panRelativeTab.add(jLabel7);
        jLabel7.setBounds(190, 120, 10, 15);

        panRelativeTab.add(cbRelEndHour);
        cbRelEndHour.setBounds(80, 120, 45, 24);
        
        panRelativeTab.add(cbRelEndMin);
        cbRelEndMin.setBounds(140, 120, 45, 24);
        
        panRelativeTab.add(cbRelEndSec);
        cbRelEndSec.setBounds(200, 120, 45, 24);
        
        intervalTabs.addTab("Relative", panRelativeTab);
        intervalTabs.addTab("Fixed", panFixedTab);
        
        panTabs.add(intervalTabs, java.awt.BorderLayout.CENTER);

        getContentPane().add(panTabs, java.awt.BorderLayout.CENTER);

        btnOK.setText("OK");
        btnOK.addActionListener(this);
        panOkCancel.add(btnOK);

        btnCancel.setText("Cancel");
        btnCancel.addActionListener(this);
        panOkCancel.add(btnCancel);

        getContentPane().add(panOkCancel, java.awt.BorderLayout.SOUTH);

        pack();
    }

    /**
     * init the comboboxes with start and end moments
     */
    private void initComboBoxes(long start, long end){
        // init hours
        for(int i=0; i<24; i++){
            cbFixEndHour.insertItemAt(""+i, i);
            cbFixStartHour.insertItemAt(""+i, i);
            cbRelEndHour.insertItemAt(""+i, i);
        }
        // init minutes & seconds
        for(int i=0; i<60; i++){
            cbFixEndMin.insertItemAt(""+i, i);
            cbFixStartMin.insertItemAt(""+i, i);
            cbRelEndMin.insertItemAt(""+i, i);
            cbFixEndSec.insertItemAt(""+i, i);
            cbFixStartSec.insertItemAt(""+i, i);
            cbRelEndSec.insertItemAt(""+i, i);
        }
        // init days
        cbFixEndDay.insertItemAt("Now & on", 0);
        cbFixEndDay.insertItemAt("Today", 1);
        cbFixEndDay.insertItemAt("Yesterday", 2);
        
        cbFixStartDay.insertItemAt("Today", 0);
        cbFixStartDay.insertItemAt("Yesterday", 1);
        
        cbRelEndDay.insertItemAt("Now & on", 0);
        cbRelEndDay.insertItemAt("Today", 1);
        cbRelEndDay.insertItemAt("Yesterday", 2);
        
        // setup starting and ending time
        Calendar now = Calendar.getInstance(timeZone);
        now.setTimeInMillis(currentTime);

        Integer value = Integer.valueOf(50); 
        Integer min = Integer.valueOf(0);
        Integer max = Integer.valueOf(2 * 7 * 24 * 60); 
        Integer step = Integer.valueOf(1); 
        
        SpinnerModel spModel = new SpinnerNumberModel(value, min, max, step);
        spinRelMin.setModel(spModel);
        
        now.add(Calendar.DATE, -2);
        for(int i=0; i<5; i++){
        	int m = now.get(Calendar.MONTH);
        	int d = now.get(Calendar.DAY_OF_MONTH);
        	int y = now.get(Calendar.YEAR);
        	int dow = now.get(Calendar.DAY_OF_WEEK) - 1;
        	String day = dowNames[dow]+" - "+monthNames[m]+" "+d+", "+y;
        	cbFixEndDay.insertItemAt(day, cbFixEndDay.getItemCount());
        	cbFixStartDay.insertItemAt(day, cbFixStartDay.getItemCount());
        	cbRelEndDay.insertItemAt(day, cbRelEndDay.getItemCount());
        	now.add(Calendar.DATE, -1);
        }
        now.add(Calendar.DATE, 1);
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        minTime = now.getTimeInMillis();
        cbRelEndDay.setMaximumRowCount(cbRelEndDay.getItemCount());
        cbFixEndDay.setMaximumRowCount(cbFixEndDay.getItemCount());
        cbFixStartDay.setMaximumRowCount(cbFixStartDay.getItemCount());
        
        setEndTime(end);
        setStartTime(start);
    }
    
    private void btnOKActionPerformed(java.awt.event.ActionEvent evt) {
        closedOK = true;
    	setVisible(false);
    }

    private void addListeners(){
        cbFixStartDay.addActionListener(this);
        cbFixStartHour.addActionListener(this);
        cbFixStartMin.addActionListener(this);
        cbFixStartSec.addActionListener(this);
        cbFixEndDay.addActionListener(this);
        cbFixEndHour.addActionListener(this);
        cbFixEndMin.addActionListener(this);
        cbFixEndSec.addActionListener(this);
        cbRelEndDay.addActionListener(this);
        cbRelEndHour.addActionListener(this);
        cbRelEndMin.addActionListener(this);
        cbRelEndSec.addActionListener(this);
        spinRelMin.addChangeListener(this);
    }

    private void removeListeners(){
        cbFixStartDay.removeActionListener(this);
        cbFixStartHour.removeActionListener(this);
        cbFixStartMin.removeActionListener(this);
        cbFixStartSec.removeActionListener(this);
        cbFixEndDay.removeActionListener(this);
        cbFixEndHour.removeActionListener(this);
        cbFixEndMin.removeActionListener(this);
        cbFixEndSec.removeActionListener(this);
        cbRelEndDay.removeActionListener(this);
        cbRelEndHour.removeActionListener(this);
        cbRelEndMin.removeActionListener(this);
        cbRelEndSec.removeActionListener(this);
        spinRelMin.removeChangeListener(this);
    }

    
    public void actionPerformed(ActionEvent e) {
    	removeListeners();
    	Object src = e.getSource();
    	if(src == cbRelEndDay || src == cbFixEndDay){
    		JComboBox cb = (JComboBox) src;
    		int selIdx = cb.getSelectedIndex();
    		//System.out.println("cbEndDay "+selIdx);
    		cbRelEndDay.setSelectedIndex(selIdx);
    		cbFixEndDay.setSelectedIndex(selIdx);
    		if(selIdx == 0){
    			cbRelEndHour.setEnabled(false);
    			cbRelEndMin.setEnabled(false);
    			cbRelEndSec.setEnabled(false);
    			jLabel5.setEnabled(false);
    			jLabel6.setEnabled(false);
    			jLabel7.setEnabled(false);

    			cbFixEndHour.setEnabled(false);
    			cbFixEndMin.setEnabled(false);
    			cbFixEndSec.setEnabled(false);
    			jLabel9.setEnabled(false);
    			jLabel10.setEnabled(false);
    			jLabel11.setEnabled(false);
    			
    		}else{
    			cbRelEndHour.setEnabled(true);
    			cbRelEndMin.setEnabled(true);
    			cbRelEndSec.setEnabled(true);
    			jLabel5.setEnabled(true);
    			jLabel6.setEnabled(true);
    			jLabel7.setEnabled(true);

    			cbFixEndHour.setEnabled(true);
    			cbFixEndMin.setEnabled(true);
    			cbFixEndSec.setEnabled(true);
    			jLabel9.setEnabled(true);
    			jLabel10.setEnabled(true);
    			jLabel11.setEnabled(true);
    		}
    	}

    	if(src == cbFixEndDay || src == cbFixEndHour || 
    			src == cbFixEndMin || src == cbFixEndSec){
    		cbRelEndDay.setSelectedIndex(cbFixEndDay.getSelectedIndex());
    		cbRelEndHour.setSelectedIndex(cbFixEndHour.getSelectedIndex());
    		cbRelEndMin.setSelectedIndex(cbFixEndMin.getSelectedIndex());
    		cbRelEndSec.setSelectedIndex(cbFixEndSec.getSelectedIndex());

    		setEndTime(getEndTime());
    		setStartTime(getFixStartTime());
/*    		long start = getFixStartTime();
    		long end = getEndTime();
    		long realEnd = end;
    		
    		if(end == -1 || end > currentTime)
    			end = currentTime;
    		if(start > end){
    			start = end;
    		}
    		if(realEnd == -1)
    			setEndTime(-1);
    		else
    			setEndTime(end);
    		setStartTime(start);
*/    	}
    	
    	if(src == cbRelEndDay || src == cbRelEndHour || 
    			src == cbRelEndMin || src == cbRelEndSec){
    		cbFixEndDay.setSelectedIndex(cbRelEndDay.getSelectedIndex());
    		cbFixEndHour.setSelectedIndex(cbRelEndHour.getSelectedIndex());
    		cbFixEndMin.setSelectedIndex(cbRelEndMin.getSelectedIndex());
    		cbFixEndSec.setSelectedIndex(cbRelEndSec.getSelectedIndex());
  
    		setEndTime(getEndTime());
    		setStartTime(getRelStartTime());
    		
    		
/*    		long end = getEndTime();
    		if(end > currentTime)
    			setEndTime(currentTime);
    		long start = getRelStartTime();
    		setStartTime(start);
*/    	}
    	
    	if(src == cbFixStartDay || src == cbFixStartHour ||
    			 src == cbFixStartMin || src == cbFixStartSec){
    		setStartTime(getFixStartTime());
    	}
/*    		long startTime = getFixStartTime();
    		long endTime = getEndTime();
    		long realEnd = endTime;
    		if(endTime == -1)
    			endTime = currentTime;
    		if(startTime > endTime)
    			endTime = Math.min(startTime, currentTime);
    		if(startTime > endTime)
    			startTime = endTime;
    		if(realEnd == -1)
    			setEndTime(-1);
    		else
    			setEndTime(endTime);
    		setStartTime(startTime);
    	}
*/    	
    	if(src == btnOK){
    		closedOK = true;
        	setVisible(false);
        	return;
    	}
    	if(src == btnCancel){
    		setVisible(false);
    		return;
    	}
    	addListeners();
	}

	public void stateChanged(ChangeEvent e) {
		Object src = e.getSource();
		removeListeners();
		if(src == spinRelMin){
			//System.out.println("spin changed");
			setStartTime(getRelStartTime());
		}
		addListeners();
	}

    /**
     * set the comboboxes to the given start time
     * @param time
     */
    private void setStartTime(long time){
    	//System.out.println("setStartTime1:"+ new Date(time));

    	long end = getEndTime();
    	//System.out.println("  endTime:"+new Date(end));
    	if(end == -1)
    		end = currentTime;
    	if(time < 0)
    		time = currentTime - 1000 * 60 * 60 * 2;
    	if(time < minTime)
    		time = minTime;
    	if(time > end)
    		time = end;
    	//System.out.println("setStartTime2:"+ new Date(time));
    	
    	int days = getDaysBetween(time, currentTime);
    	cbFixStartDay.setSelectedIndex(days);
    	
    	Calendar cal = Calendar.getInstance(timeZone);
    	cal.setTimeInMillis(time);    	
    	cbFixStartHour.setSelectedIndex(cal.get(Calendar.HOUR_OF_DAY));
    	cbFixStartMin.setSelectedIndex(cal.get(Calendar.MINUTE));
    	cbFixStartSec.setSelectedIndex(cal.get(Calendar.SECOND));
    	spinRelMin.setValue(Integer.valueOf((int)((end - time) / 1000 / 60)));
    }
    
    /**
     * set the comboboxes to the given end time
     * @param time
     */
    private void setEndTime(long time){
    	//System.out.println("setEndTime1:"+new Date(time));
    	long realTime = time;
    	Calendar cal = Calendar.getInstance(timeZone);
    	if(time > currentTime || time < 0)
    		time = currentTime;
    	int days = (realTime == -1 ? -1 : getDaysBetween(time, currentTime));
    	cal.setTimeInMillis(time);
    	//System.out.println("setEndTime2:"+new Date(time));
    	cbFixEndDay.setSelectedIndex((int)days + 1);
    	cbRelEndDay.setSelectedIndex((int)days + 1);

    	cbFixEndHour.setSelectedIndex(cal.get(Calendar.HOUR_OF_DAY));
    	cbRelEndHour.setSelectedIndex(cal.get(Calendar.HOUR_OF_DAY));
    	
    	cbFixEndMin.setSelectedIndex(cal.get(Calendar.MINUTE));
    	cbRelEndMin.setSelectedIndex(cal.get(Calendar.MINUTE));
    	
    	cbFixEndSec.setSelectedIndex(cal.get(Calendar.SECOND));
    	cbRelEndSec.setSelectedIndex(cal.get(Calendar.SECOND));
    }

    /**
     * get the start time from the Fixed comboboxes
     */
    private long getFixStartTime(){
    	//System.out.println("getFixStartTime...");
    	int idxSel = cbFixStartDay.getSelectedIndex();
    	Calendar cal = Calendar.getInstance(timeZone);
    	cal.setTimeInMillis(currentTime);
    	cal.add(Calendar.DATE, -idxSel);
    	String h = (String) cbFixStartHour.getSelectedItem();
    	String m = (String) cbFixStartMin.getSelectedItem();
    	String s = (String) cbFixStartSec.getSelectedItem();
    	//if(h != null)
    		cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(h));
    	//if(m != null)
    		cal.set(Calendar.MINUTE, Integer.parseInt(m));
    	//if(s != null)
    		cal.set(Calendar.SECOND, Integer.parseInt(s));
    	//System.out.println(" ...getFixStartTime:"+new Date(cal.getTimeInMillis()));
    	return cal.getTimeInMillis();
    }
    
    /**
     * get the start time from the spinner (using end time) 
     */
    private long getRelStartTime(){
    	//System.out.println("getRelStartTime...");
    	long end = getEndTime();
    	if(end == -1)
    		end = currentTime;
    	//System.out.println("spin:"+spinRelMin.getValue());
    	int diffMin = ((Integer)spinRelMin.getValue()).intValue();
    	long start = end - diffMin * 1000 * 60;
    	//System.out.println(" ...getRelStartTime:"+new Date(start));
    	return start;
    }
    
    /**
     * get the end time from the comboboxes
     * @return
     */
    public long getEndTime(){
    	//System.out.println("getEndTime...");
    	int idxSel = cbFixEndDay.getSelectedIndex();
    	if(idxSel == 0)
    		return -1;
    	Calendar cal = Calendar.getInstance(timeZone);
    	cal.setTimeInMillis(currentTime);
    	cal.add(Calendar.DATE, -(idxSel-1));
    	String h = (String) cbFixEndHour.getSelectedItem();
    	String m = (String) cbFixEndMin.getSelectedItem();
    	String s = (String) cbFixEndSec.getSelectedItem();
    	//if(h != null)
    		cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(h));
    	//if(m != null)
    		cal.set(Calendar.MINUTE, Integer.parseInt(m));
    	//if(s != null)
    		cal.set(Calendar.SECOND, Integer.parseInt(s));
    	//System.out.println(" ...getEndTime:"+new Date(cal.getTimeInMillis()));
    	return cal.getTimeInMillis();
    }
    
    /**
     *  return the end time
     */
    public long getStartTime(){
    	return getRelStartTime();
    }

    /**
     * return the days between two dates
     * @param from starting date, in millis
     * @param to ending date in millis
     * @return days between
     */
    private int getDaysBetween(long from, long to){
    	Calendar calFrom = Calendar.getInstance(timeZone);
    	calFrom.setTimeInMillis(from);
    	int dFrom = calFrom.get(Calendar.DAY_OF_MONTH);
    	Calendar cal = Calendar.getInstance(timeZone);
    	cal.setTimeInMillis(to);
    	int d = 0;
    	for(int i=0; i<8; i++, d++){
    		int dTo = cal.get(Calendar.DAY_OF_MONTH);
    		if(dFrom == dTo)
    			return d;
    		cal.add(Calendar.DATE, -1);
    	}
    	return d;
    }
    
    /**
     * return the interval length in milliseconds 
     */
    public long getIntervalLength(){
    	long end = getEndTime();
    	long start = getStartTime();
    	if(end == -1){
    		end = currentTime;
    	}
    	return end - start;
    }
    
    /**
     * window closed with OK 
     */
    public boolean closedOK(){
    	return closedOK;
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
    	long t = NTPDate.currentTimeMillis();
        PlotIntervalSelector is = new PlotIntervalSelector(new JFrame(), t - 100000, t+2000000, "21:56", "GMT+01");
        is.setVisible(true);
        is.dispose();
        is = null;
        System.exit(0);
    }
}
