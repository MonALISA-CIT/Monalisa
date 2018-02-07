package lia.Monitor.JiniClient.CommonGUI;

import java.awt.Color;
import java.util.Iterator;
import java.util.Vector;

import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.util.ntp.NTPDate;
import net.jini.core.lookup.ServiceID;

public class StatisticsMonitor {


	/** constants to reference the values contained in array */
    public static final int VALUE_FARMS = 0;
    public static final int VALUE_INSTANT_SEC_BYTE_IN = 1;
    public static final int VALUE_INSTANT_SEC_BYTE_OUT = 2;
    public static final int VALUE_INSTANT_SEC_MSG_IN = 3;
    public static final int VALUE_INSTANT_SEC_MSG_OUT = 4;

    /** array of statistics value that know update */
	public DefaultStatisticsValue[] values = new DefaultStatisticsValue[] {
			new DefaultStatisticsValue("Available Services","Services","services",new Color(255, 216,0, 160), new FarmsProducer(),new Color(155, 116, 0, 160)),
			new RxBStatistics("Instant bytes rate IN","RxB","bytes/s",new Color(255, 0, 0, 160), new RxBProducer(),new Color(155, 0, 0, 160)),
			new RateStatistics("Instant bytes rate OUT","TxB","bytes/s",new Color( 0, 255, 0, 160), new TxBProducer(),new Color( 0, 155, 0, 160)),
			new RateStatistics("Instant msg rate IN","RxM","messages/s",new Color(255, 144, 0, 160), new RxMProducer(),new Color(155, 44, 0, 160)),
			new RateStatistics("Instant msg rate OUT","TxM","messages/s",new Color(0, 255, 246, 160), new TxMProducer(),new Color(0, 155, 146, 160))
	};

	/** types of statistics this monitor can provide */
    private static final int STATISTICS_FARMS = 0;
    private static final int STATISTICS_INSTANT_SEC_BYTE = 1;
    private static final int STATISTICS_INSTANT_SEC_MSG = 2;
    private static final int STATISTICS_UPTIME = 3;
    private static final int STATISTICS_TOTAL_BYTE = 4;
    private static final int STATISTICS_TOTAL_MSG = 5;
    public static final int nTotalTypes = 6;
    private int nCurrentStatType = 0;
    public static final int nTotalMaxTypes = 3;
    private static final int SPECIAL_STATISTICS_OFFSET = 100;
    private static final int SPECIAL_STATISTICS_FARMS_MAX = 100;
    private static final int SPECIAL_STATISTICS_INSTANT_SEC_BYTE_MAX = 101;
    private static final int SPECIAL_STATISTICS_INSTANT_SEC_MSG_MAX = 102;
    private static final int SPECIAL_STATISTICS_TOTAL_BYTE_CONF = 103;
    private static final int SPECIAL_STATISTICS_CURRENT_PROXY = 104;
    private static final int SPECIAL_STATISTICS_IO_ERRORS = 105;
    
    //reference to snodes through monitor
    public SerMonitorBase monitor;
	private String clientName;
	/** last time a forced update wass called, neccessary if no new data */
	long nLastForcedUpdate=-1;
	/** minimum time till next measurement */
	public final static long TIME_UNIT = 2000;
	/** maximum time till next measurement */
	public final static long MAX_TIME_UNIT = 20000; 


	/** last time in values were added to plot; statistics window param */
	private long nLastInValueTime = -1; 
	/** last time out values were added to plot; statistics window param */
	private long nLastOutValueTime = -1;
	
	interface myValue {
		public void add(myValue other);
		public long getLong();
		public float getFloat();
		public void setLong(long val);
		public void setFloat(float val);
		public myValue dupl();
		public void set(myValue other);
		/** 
		 * compares current value with the one given as parameter<br>
		 * if current value greather than other, return 1,
		 * if equal, return 0,
		 * if smaller in value, return -1.
		 */
		public int compare(myValue other);
	}
	
	class myLongValue implements myValue {
		private long value;
		
		public myLongValue() {
			value=0;
		}
		public myLongValue( long v) {
			value = v;
		}

		/** adds value contained in other object only if of same type */
		public void add(myValue other) {
			if ( other instanceof myLongValue )
				value += ((myLongValue)other).value;
		}
		public float getFloat() {
			return value;
		}
		public long getLong() {
			return value;
		}
		public void setFloat(float val) {
			this.value = (long)val;
		}
		public void setLong(long val) {
			this.value = val;
		}
		public myValue dupl() {
			return (myValue)(new myLongValue(value));
		}
		public void set(myValue other) {
			value = other.getLong();
		}
		public String toString() {
			return ""+value;
		}
		public int compare(myValue other) {
			if ( value>other.getLong() )
				return 1;
			else if ( value<other.getLong() )
				return -1;
			else
				return 0;
		}
	}
	
	class myFloatValue implements myValue {
		private float value;
		
		public myFloatValue() {
			value=0;
		}
		public myFloatValue(float val) {
			value=val;
		}
		
		/** adds value contained in other object only if of same type */
		public void add(myValue other) {
			if ( other instanceof myFloatValue )
				value += ((myFloatValue)other).value;
		}

		public float getFloat() {
			return value;
		}
		public long getLong() {
			return (long)value;
		}
		public void setFloat(float val) {
			value=val;
		}
		public void setLong(long val) {
			value=val;
		}
		public myValue dupl() {
			return (myValue)(new myFloatValue(value));
		}
		public void set(myValue other) {
			value = other.getFloat();
		}
		public String toString() {
			return ""+(((float)((int)(value*100)))/100);
		}
		public int compare(myValue other) {
			if ( value>other.getFloat() )
				return 1;
			else if ( value<other.getFloat() )
				return -1;
			else
				return 0;
		}
	}
	
//	class ValueProducerException extends Exception {
//		private static final long serialVersionUID = 1L;
//		public ValueProducerException(String reason) {
//			super(reason);
//		}
//	}
	/**
	 * produces a set of values and stores them in the received as params
	 * myValues objects
	 * @author mluc
	 */
	interface ValueProducer {
		public myValue get(int index) /*throws ValueProducerException*/;
		public int size();
	}
	
	class RxBProducer implements ValueProducer {
//		ValueProducerException vpe = new ValueProducerException("exception producing RxB");
		myLongValue byteIn=new myLongValue();
		myLongValue byteConfIn=new myLongValue();
		public int size() { return 2;}

		/** 
		 * values are set when index=0 so that byteIn and byteConfIn have similar values
		 * @see lia.Monitor.JiniClient.CommonGUI.StatisticsMonitor.ValueProducer#set(lia.Monitor.JiniClient.CommonGUI.StatisticsMonitor.myValue, int)
		 */
		public myValue get(int index) {
			if ( index == 0 ) {
				if ( monitor!=null && monitor.getTmClient()!=null ) {
					byteIn.setLong( monitor.getTmClient().getInByteCounterValue());
					byteConfIn.setLong( monitor.getTmClient().getInByteConfCounterValue());
					return byteIn;
				};
			} else if ( index == 1 ) {
				return byteConfIn;
			}
			byteIn.setLong(0);
			byteConfIn.setLong(0);
			return byteIn;
//					throw vpe;
		}
	}
	
	class TxBProducer implements ValueProducer {
		myLongValue byteOut=new myLongValue();
		public int size() { return 1;}
		public myValue get(int index) {
			if ( index == 0 ) {
				if ( monitor!=null && monitor.getTmClient()!=null ) {
					byteOut.setLong( monitor.getTmClient().getOutByteCounterValue());
					return byteOut;
				}
			};
			byteOut.setLong(0);
			return byteOut;
		}
	}
	
	class RxMProducer implements ValueProducer {
		myLongValue msgIn=new myLongValue();
		public int size() { return 1;}
		public myValue get(int index) {
			if ( index == 0 ) {
				if ( monitor!=null && monitor.getTmClient()!=null ) {
					msgIn.setLong( monitor.getTmClient().getInMsgCounterValue());
					return msgIn;
				}
			};
			msgIn.setLong(0);
			return msgIn;
		}
	}
	
	class TxMProducer implements ValueProducer {
		myLongValue msgOut=new myLongValue();
		public int size() { return 1;}
		public myValue get(int index) {
			if ( index == 0 ) {
				if ( monitor!=null && monitor.getTmClient()!=null ) {
					msgOut.setLong( monitor.getTmClient().getOutMsgCounterValue());
					return msgOut;
				}
			};
			msgOut.setLong(0);
			return msgOut;
		}
	}

	class FarmsProducer implements ValueProducer {
		private myLongValue nClients = new myLongValue();
		private myLongValue nNodes = new myLongValue();
		private myLongValue nParams = new myLongValue();
		/** variable used only for vrvs client, but put here 'cause it should be updated with the rest... */
		private myLongValue nVrvsUsers = new myLongValue();
		public myValue get(int index) {
			if ( index == 0 ) {
				try {
					//first update services count
					if ( monitor!=null && monitor.snodes!=null ) {
						int nC = monitor.snodes.size();
						int nN = 0;
						int nP = 0;
						int nV = 0;
						for(final ServiceID sid : monitor.snodes.keySet()){
							MFarm conf = monitor.getConfiguration(sid);
							if(conf != null){
								int crtNodes = 0;
								for(Iterator cit=conf.getClusters().iterator(); cit.hasNext(); ){
									MCluster cluster = (MCluster) cit.next();
									Vector v = cluster.getNodes();
									//count also the vrvs clients
									if ( cluster.getName().equals("Users") )
										nV += v.size();
									crtNodes += v.size();
									for ( int i=0; i<v.size(); i++)
										nP += ((MNode)v.get(i)).getParameterList().size();
								}
								nN += crtNodes;
								//nP += conf.getParameterList().size();
							}
						}
						nClients.setLong(nC);
						nNodes.setLong(nN);
						nParams.setLong(nP);
						nVrvsUsers.setLong(nV);
						return nClients;
					}
				} catch(Exception ex) {
					//ex.printStackTrace();
					//error in enumeration, no problem, use last values
				}
			} else if (index == 1)
				return nNodes;
			else if ( index == 2)
				return nParams;
			else if ( index == 3)
				return nVrvsUsers;
	    	nClients.setLong(0);
			nNodes.setLong(0);
			nParams.setLong(0);
			nVrvsUsers.setLong(0);
	    	return nClients;
		}
		public int size() {
			return 4;
		}
		
	}

	/**
	 * contains a value that is monitored
	 * @author mluc
	 *
	 */
	class DefaultStatisticsValue {
		/** last update of value */
		protected long lLastTime = -1;
		protected String sName;
		protected String sShortName;
		protected String sUnit;
		/** graph color for this value */
		protected Color color;
		/** graph color for max value */
		protected Color max_color;
		/** representative value return by producer.get(0) */
		protected myValue value = null;
		/** max value from all values gathered in time */
		protected myValue max_value = null;
		/** data producer */
		protected ValueProducer producer;
		/** additional data returned by producer */
		protected myValue[] advalues = null;
		public DefaultStatisticsValue( String name, String short_name, String unit, Color color, ValueProducer p) {
			sName = name;
			sShortName = short_name;
			sUnit = unit;
			this.color = color;
			producer = p;
			value = p.get(0).dupl();
			max_value = value.dupl();
			if ( p.size()>1 ) {
				advalues = new myValue[p.size()-1];
				for ( int i=0; i<p.size()-1; i++) {
					advalues[i] = p.get(i+1).dupl();
				}
			}
			max_color = color;
		}
		public DefaultStatisticsValue( String name, String short_name, String unit, Color color, ValueProducer p, Color max_color) {
			this(name, short_name, unit, color, p);
			this.max_color = max_color;
		}
		/**
		 * just update the internal values, with no regard to the moment; it
		 * remains as an old one
		 * @author mluc
		 * @since May 11, 2006
		 */
		private void update() {
        	value.set(producer.get(0));
        	if ( value.compare(max_value) > 0 )
        		max_value.set(value);
        	for ( int i=1; i<producer.size(); i++)
        		advalues[i-1].set(producer.get(i));
		}
		/**
		 * update value in respect with the new moment
		 * @param newTime the current time; if -1 get from currentTimeMillis
		 */
		public void update(long newTime) {
        	if ( newTime == -1 )
        		newTime = NTPDate.currentTimeMillis();
        	//same time, no rate to compute
        	//or worst, smaller, so would result in negative rate
        	if ( lLastTime==newTime )
        		return;
        	update();
    		lLastTime = newTime;
		}
		public String toString() {
			return sName;
		}
		/** formatted value */
//		public abstract String getText();
		public Color getColor() {
			return color;
		}
		/** return the value of statistics */
		public myValue getValue() { return value; }
		/** return the max value of statistics obtained in time */
		public myValue getMaxValue() { return max_value; }
		public Color getMaxColor() { return max_color; }
		/** returns the i-th value of this statistics object from its producer 
		 * returns null if index is greather than producer allows. 
		 */
		public myValue getValue( int i) { 
			if ( i==0 )
				return value;
			else if ( i>0 && i<producer.size() ) //this checks that advalues is not null, and i is greather than 0
				return advalues[i-1];
			return null;
		}
	}

	class RateStatistics extends DefaultStatisticsValue {
		/** value obtained in last measurement interval */
		/** rate obtained in last measurement interval */
		protected myValue rate = null;
		/** total value since most recent start */
		protected myValue total;
		/** most recent start time: the most recent moment a connection to the proxy was made */
		private long StartTime=-1;
		/**
		 * contains all total values for previous connections without the 
		 * current one
		 */
		protected myValue totalOld;
		/** must initialize all local variables */
		public RateStatistics(String name, String short_name, String unit, Color color, ValueProducer p) {
			super(name, short_name, unit, color, p);
			total = new myLongValue();
			totalOld = new myLongValue();
			value = new myLongValue();
			rate = new myFloatValue();
		}
		public RateStatistics(String name, String short_name, String unit, Color color, ValueProducer p, Color max_color) {
			this(name, short_name, unit, color, p);
			this.max_color=max_color;
		}
	    /**
	     * corrects some values if connection was reset: a new proxy connection is
	     * available.
	     */
	    protected void checkStartTime() {
	        if ( monitor!=null && monitor.getTmClient()!=null && monitor.getTmClient().conn!=null ) 
	        { 
	        	long start_time = monitor.getTmClient().getStartTime();
	        	//sTime==-1 or sTime==old_start_time
	        	if ( StartTime != start_time ) {
	        		StartTime = start_time;
	        		lLastTime = StartTime;//is equivalent to -1 probably, IT'S NOT!!!
	        		newStartTime();
	        	};
	        };
	    }
		/**
		 * updates variables when a new start time is available: a new connection
		 * has been made to a proxy
		 * @author mluc
		 * @date May 5, 2006
		 */
		protected void newStartTime() {
    		totalOld.add(total);
    		total.setLong(0);
		}
		/**
		 * do not update any value
		 */
//		public void update() {
//        	value.set(producer.get(0));
//        	for ( int i=1; i<producer.size(); i++)
//        		advalues[i-1].set(producer.get(i));
//		}
		public void update(long newTime) {
			checkStartTime();
        	if ( newTime == -1 )
        		newTime = NTPDate.currentTimeMillis();
        	//same time, no rate to compute
        	//or worst, smaller, so would result in negative rate
        	if ( lLastTime==newTime )
        		return;
			//obtain total value for Rx for the most recent connection to proxy
			//rate is going to be recomputed, so we can use it
			long offset = producer.get(0).getLong();
			if ( offset!=0 ) {
	    		value.setLong(offset-total.getLong());
	    		total.setLong(offset);
	        	if ( lLastTime!=-1 && lLastTime<newTime ) {
	        		//TODO: should add the previous rate? 50-50% perhaps?
	        		rate.setFloat( (float)(value.getLong()*1000)/(float)(newTime-lLastTime) );
	        	} else {
	        		rate.setFloat( 0);
	        	}
	        	if ( rate.compare(max_value) > 0 )
	        		max_value.set(rate);
	        	for ( int i=1; i<producer.size(); i++)
	        		advalues[i-1].set(producer.get(i));
			} else {
	    		value.setLong(0);
	    		total.setLong(0);
        		rate.setFloat(0);
	        	for ( int i=1; i<producer.size(); i++)
	        		advalues[i-1].setLong(0);
			}
    		lLastTime = newTime;
//    		System.out.println(sName+" rate="+rate.getFloat());
		}
		public myValue getValue() {
			return rate;
		}
		public myValue getValue(int i) {
			switch (i) {
				case 1: return new myLongValue(getTotal());
				default:
					return getValue();
			}
		}
		public long getTotal() {
			return total.getLong()+totalOld.getLong();
		}
	}
	
	class RxBStatistics extends RateStatistics {
		/** @see lia.Monitor.JiniClient.CommonGUI.StatisticsMonitor.RateStatistics#totalOld */
		private myLongValue totalConfOld = new myLongValue();
//		private myLongValue totalConf = new myLongValue();
		public RxBStatistics(String name, String short_name, String unit, Color color, RxBProducer p) {
			super(name, short_name, unit, color, p);
		}
		public RxBStatistics(String name, String short_name, String unit, Color color, RxBProducer p, Color max_color) {
			this(name, short_name, unit, color, p);
			this.max_color = max_color;
		}
		protected void newStartTime() {
			super.newStartTime();
    		totalConfOld.add(advalues[0]);
    		advalues[0].setLong(0);
		}
		public void update(long newTime) {
			long lStart=lLastTime;
			super.update(newTime);
//			System.out.println("New RxB: "+rate.getLong()+" b/s; "+value.getLong()+" bytes transfered in "+((lLastTime-lStart)/1000)+" seconds");
		}
		public long getTotalConf() {
			return advalues[0].getLong()+totalConfOld.getLong();
		}
	}
	
	public StatisticsMonitor(SerMonitorBase monitor, String clientName) {
        this.monitor=monitor;
        this.clientName = clientName;
//		BackgroundWorker.schedule( new TimerTask() {
//			public void run() {
//		        Thread.currentThread().setName(" ( ML ) - StatisticsMonitor - update statistics Thread");
//		        update();
//			}
//		}, 0, 1000);
    }
    
    public void changeType(boolean bInverse)
    {
    	int dir = (bInverse?nTotalTypes-1:1);
    	nCurrentStatType = ((nCurrentStatType+dir)%nTotalTypes);
    }
    
    private String getStatistics(int type)
    {
    	switch( type ) {
			case STATISTICS_INSTANT_SEC_BYTE :
				return "<b>Instant bytes rate</b> <font color=red>Rx:</font> "+valToString(values[VALUE_INSTANT_SEC_BYTE_IN].getValue().getFloat(), VALUE_2_STRING_SHORT_UNIT)+"B/s  <font color=green>Tx:</font>  "+valToString(values[VALUE_INSTANT_SEC_BYTE_OUT].getValue().getFloat(), VALUE_2_STRING_SHORT_UNIT)+"B/s";
			case SPECIAL_STATISTICS_INSTANT_SEC_BYTE_MAX :
				return "<font color=red>Max Rx:</font> "+valToString(values[VALUE_INSTANT_SEC_BYTE_IN].getMaxValue().getFloat(), VALUE_2_STRING_SHORT_UNIT)+"B/s  <font color=green>Max Tx:</font>  "+valToString(values[VALUE_INSTANT_SEC_BYTE_OUT].getMaxValue().getFloat(), VALUE_2_STRING_SHORT_UNIT)+"B/s";
			case STATISTICS_INSTANT_SEC_MSG :
				return "<b>Instant messages rate</b> <font color=red>Rx:</font> "+valToString(values[VALUE_INSTANT_SEC_MSG_IN].getValue().getFloat(), VALUE_2_STRING_NO_UNIT)+" msg/s  <font color=green>Tx:</font>  "+valToString(values[VALUE_INSTANT_SEC_MSG_OUT].getValue().getFloat(), VALUE_2_STRING_NO_UNIT)+" msg/s";
			case SPECIAL_STATISTICS_INSTANT_SEC_MSG_MAX :
				return "<font color=red>Max Rx:</font> "+valToString(values[VALUE_INSTANT_SEC_MSG_IN].getMaxValue().getFloat(), VALUE_2_STRING_NO_UNIT)+" msg/s  <font color=green>Max Tx:</font>  "+valToString(values[VALUE_INSTANT_SEC_MSG_OUT].getMaxValue().getFloat(), VALUE_2_STRING_NO_UNIT)+" msg/s";
			case STATISTICS_UPTIME :
				return "<b>Uptime</b> <i>"+getUptime()+"</i>";
			case STATISTICS_TOTAL_BYTE :
				return "<b>Total bytes</b> <font color=red>Rx:</font> "+valToString(((RateStatistics)values[VALUE_INSTANT_SEC_BYTE_IN]).getTotal(), VALUE_2_STRING_UNIT)+"bytes  <font color=green>Tx:</font>  "+valToString(((RateStatistics)values[VALUE_INSTANT_SEC_BYTE_OUT]).getTotal(), VALUE_2_STRING_UNIT)+"bytes";
			case STATISTICS_TOTAL_MSG :
				return "<b>Total messages</b> <font color=red>Rx:</font> "+((RateStatistics)values[VALUE_INSTANT_SEC_MSG_IN]).getTotal()+" msg  <font color=green>Tx:</font>  "+((RateStatistics)values[VALUE_INSTANT_SEC_MSG_OUT]).getTotal()+" msg";
			case SPECIAL_STATISTICS_TOTAL_BYTE_CONF:
				long totalB = ((RateStatistics)values[VALUE_INSTANT_SEC_BYTE_IN]).getTotal();
				long totalCB = ((RxBStatistics)values[VALUE_INSTANT_SEC_BYTE_IN]).getTotalConf();
				return  "<b>Total cofiguration bytes IN</b> "+valToString(totalCB, VALUE_2_STRING_UNIT)+"bytes  representing "+(totalB>0?totalCB*100/totalB:0)+"% of total bytes IN";
			case SPECIAL_STATISTICS_CURRENT_PROXY:
				return  "<i>Current active connection to proxy</i>: <b>"+(monitor!=null && monitor.getTmClient()!=null?monitor.getTmClient().getFullAddress():"none")+"</b>";
			case SPECIAL_STATISTICS_IO_ERRORS:
				return  "<b>I/O Errors:</b> <b><font color='#004474'>"+(monitor!=null?monitor.getFailedConns():0)+"</font></b> reconnection"+((monitor!=null?monitor.getFailedConns():0)!=1?"s":"")+" to proxy, <b><font color='#004474'>"+(monitor!=null?monitor.getInvalidMsgCount():0)+"</font></b> unknown message"+((monitor!=null?monitor.getInvalidMsgCount():0)!=1?"s":"")+" from proxy,<br>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; Proxy messages buffer status: "+monitor.getProxyMsgBufStatus();
			case SPECIAL_STATISTICS_FARMS_MAX:
				return "Max "+(clientName.compareTo("VRVS Client")==0?"reflectors":"services")+" count: <b>"+values[VALUE_FARMS].getMaxValue()+"</b>";
			default:
			case STATISTICS_FARMS :
				String text = "", item=null;
				DefaultStatisticsValue fs = values[VALUE_FARMS];
				if ( clientName.compareTo("VRVS Client")==0 ) {
					item = " reflector";
					text = "<b>"+fs.getValue(3)+"</b> user"+ (fs.getValue(3).getLong()!=1?"s":"")+", ";
				} else if ( clientName.compareTo("Farms Client")==0 || clientName.equals("GridMap Client") )
				    item = " service";
				text += "<b>"+fs.getValue()+"</b>" + item + (fs.getValue().getLong()!=1?"s":"")+", "
					+"<b>"+fs.getValue(1)+"</b>"+" node"+(fs.getValue(1).getLong()!=1?"s":"")+", "
					+"<b>"+fs.getValue(2)+"</b>"+" param"+(fs.getValue(2).getLong()!=1?"s":"")+" ";
				return text;
		}
    }
    
    public String getAllStatistics() {
    	StringBuilder sTotal= new StringBuilder("<html><table cellspacing=1 cellpadding=0 border=0>");
    	for ( int i=0; i< nTotalTypes; i++) {
    		sTotal.append("<tr>");
    		if ( i<nTotalMaxTypes )
    			sTotal.append("<td>"+getStatistics(i)+"</td><td width=20>&nbsp;</td><td>"+getStatistics(SPECIAL_STATISTICS_OFFSET+i)+"</td>");
    		else
    			sTotal.append("<td colspan=3>"+getStatistics(i)+"</td>");
    		sTotal.append("</tr>");
    	}
    	sTotal.append("<tr><td colspan=3>"+getStatistics(SPECIAL_STATISTICS_TOTAL_BYTE_CONF)+"</td></tr>");
    	sTotal.append("<tr><td colspan=3>"+getStatistics(SPECIAL_STATISTICS_IO_ERRORS)+"</td></tr>");
    	sTotal.append("<tr><td colspan=3>"+getStatistics(SPECIAL_STATISTICS_CURRENT_PROXY)+"</td></tr>");
    	sTotal.append("</table></html>");
    	return sTotal.toString();
    }
    
    
    public String getStatistics()
    {
    	return "<html>"+getStatistics(nCurrentStatType);
    }

	private final String getUptime(){
		long start=monitor.lStartTime;
		long end=NTPDate.currentTimeMillis();
		long l = end - start;
		l/=1000;
		//long s = l%60; 
		l/=60;
		long m = l%60; l/=60;
		long h = l%24; l/=24;
		long d = l;
		return (d>0?d+" day"+(d!=1 ? "s" : "")+", ":"")+(d>0||h>0?h+"h, ":"")+m+"min";
	}
	
    public static int VALUE_2_STRING_NO_UNIT = 1;
    public static int VALUE_2_STRING_UNIT = 2;
    public static int VALUE_2_STRING_SHORT_UNIT = 3;
    
    public static String valToString( double value, int options ) {
        String text;
        long val=(long)(value*100);
        String addedText="";
        if ( (options&VALUE_2_STRING_UNIT)>0 ) {
	        if ( val>102400 ) {
	            val/=1024;
	            if ( (options&VALUE_2_STRING_SHORT_UNIT)>0 )
	            	addedText="K";
	            else
	            	addedText="Kilo";
	        }
	        if ( val>102400 ) {
	            val/=1024;
	            if ( (options&VALUE_2_STRING_SHORT_UNIT)>0 )
	            	addedText="M";
	            else
	            	addedText="Mega";
	        }
	        if ( val>102400 ) {
	            val/=1024;
	            if ( (options&VALUE_2_STRING_SHORT_UNIT)>0 )
	            	addedText="G";
	            else
	            	addedText="Giga";
	        }
        };       
        long rest = val%100;
        text = (val/100)+"."+(rest<10?"0":"")+rest+" "+addedText;
        return text;
    }


    /**
	 * updates all values that haven't been updated for more than {@link MAX_TIME_UNIT}<br>
	 * should be run at a time interval of {@link TIME_UNIT}
	 * @author mluc
	 * @version May 8, 2006
	 */
	public void forceCheckUpdate() {
		long nTime = NTPDate.currentTimeMillis();
		if ( nLastForcedUpdate==-1 )
			nLastForcedUpdate=nTime;
		if ( nTime - nLastForcedUpdate >= TIME_UNIT ) {
			for ( int i=0; i<values.length; i++) {
				if ( values[i].lLastTime==-1 || nTime - values[i].lLastTime >= MAX_TIME_UNIT ) {
//					System.out.println("max timeout: force updating "+values[i].sShortName);
					values[i].update(nTime);
				};
			}
			nLastForcedUpdate = nTime;
		};
	}
	
	/**
	 * updates the statistics window with data if none inserted so far;
	 * and also updates data if timeout
	 * @author mluc
	 * @since May 12, 2006
	 */
	public void updateDataAndWindow() {
		try {
			newOutValue(false);
			newInValue(true);
			values[StatisticsMonitor.VALUE_FARMS].update(-1);
	    	forceCheckUpdate();
			if ( monitor!=null && monitor.main!=null ) {
				monitor.main.jlbStatistics.setText(getStatistics());
				if ( monitor.main.frmStatistics!=null ) {
					long nTime = NTPDate.currentTimeMillis();
					//becaouse out values are small, it should update quicker
					monitor.main.frmStatistics.graph.newValue( StatisticsMonitor.VALUE_FARMS, nTime, values[StatisticsMonitor.VALUE_FARMS].getValue().getLong());
					monitor.main.frmStatistics.graph.newSingleValue( VALUE_FARMS, values[StatisticsMonitor.VALUE_FARMS].getMaxValue().getLong());
					String text = getAllStatistics();
					monitor.main.frmStatistics.jlbAll.setText(text);
				};
			}
		} catch(Exception ex) {
			//don't report it, ignore
		}
	}

	/**
	 * inserts two new out values in graph if the time since last insertion is
	 * greather than {@link TIME_UNIT}.<br>
	 * if no update done for, lets say, TIME_UNIT*2.5, then, for TIME_UNIT*2 there will be
	 * the old value, and for 0.5*TIME_UNIT there will be the new value computed
	 * for the entire interval TIME_UNIT*2.5
	 * @param bForced indicates that a new point should be forced in graph, but 
	 * without updating (recomputing) value
	 */
	public void newOutValue(boolean bForced) {
		long nTime = NTPDate.currentTimeMillis();
		if ( nLastOutValueTime!=-1 && nTime-nLastOutValueTime<StatisticsMonitor.TIME_UNIT )
			return;
		if ( !bForced || nLastInValueTime==-1 ) {
			nLastOutValueTime = nTime;
			values[StatisticsMonitor.VALUE_INSTANT_SEC_BYTE_OUT].update(nTime);
			values[StatisticsMonitor.VALUE_INSTANT_SEC_MSG_OUT].update(nTime);
		};
		if ( monitor!=null && monitor.main!=null && monitor.main.frmStatistics!=null && monitor.main.frmStatistics.graph!=null ) { 
			monitor.main.frmStatistics.graph.newValue( StatisticsMonitor.VALUE_INSTANT_SEC_BYTE_OUT, nTime, values[StatisticsMonitor.VALUE_INSTANT_SEC_BYTE_OUT].getValue().getFloat());
			monitor.main.frmStatistics.graph.newValue( StatisticsMonitor.VALUE_INSTANT_SEC_MSG_OUT, nTime, values[StatisticsMonitor.VALUE_INSTANT_SEC_MSG_OUT].getValue().getFloat());
			monitor.main.frmStatistics.graph.newSingleValue( StatisticsMonitor.VALUE_INSTANT_SEC_BYTE_OUT, values[StatisticsMonitor.VALUE_INSTANT_SEC_BYTE_OUT].getMaxValue().getFloat());
			monitor.main.frmStatistics.graph.newSingleValue( StatisticsMonitor.VALUE_INSTANT_SEC_MSG_OUT, values[StatisticsMonitor.VALUE_INSTANT_SEC_MSG_OUT].getMaxValue().getFloat());
		};
	}

	/**
	 * @see #newOutValue
	 * @author mluc
	 * @since May 11, 2006
	 * @param bForced
	 */
	public void newInValue(boolean bForced) {
		long nTime = NTPDate.currentTimeMillis();
		if ( nLastInValueTime!=-1 && nTime-nLastInValueTime<StatisticsMonitor.TIME_UNIT )
			return;
		if ( !bForced || nLastInValueTime==-1 ) {
			nLastInValueTime = nTime;
//			System.out.println("newInValue nTime="+nTime);
			values[StatisticsMonitor.VALUE_INSTANT_SEC_BYTE_IN].update(nTime);
			values[StatisticsMonitor.VALUE_INSTANT_SEC_MSG_IN].update(nTime);
		}
		if ( monitor!=null && monitor.main!=null && monitor.main.frmStatistics!=null && monitor.main.frmStatistics.graph!=null ) { 
			monitor.main.frmStatistics.graph.newValue( StatisticsMonitor.VALUE_INSTANT_SEC_BYTE_IN, nTime, values[StatisticsMonitor.VALUE_INSTANT_SEC_BYTE_IN].getValue().getFloat());
			monitor.main.frmStatistics.graph.newValue( StatisticsMonitor.VALUE_INSTANT_SEC_MSG_IN, nTime, values[StatisticsMonitor.VALUE_INSTANT_SEC_MSG_IN].getValue().getFloat());
			monitor.main.frmStatistics.graph.newSingleValue( StatisticsMonitor.VALUE_INSTANT_SEC_BYTE_IN, values[StatisticsMonitor.VALUE_INSTANT_SEC_BYTE_IN].getMaxValue().getFloat());
			monitor.main.frmStatistics.graph.newSingleValue( StatisticsMonitor.VALUE_INSTANT_SEC_MSG_IN, values[StatisticsMonitor.VALUE_INSTANT_SEC_MSG_IN].getMaxValue().getFloat());
		};
	}
	
}
