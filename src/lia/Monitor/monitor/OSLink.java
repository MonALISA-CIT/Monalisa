package lia.Monitor.monitor;

import java.awt.Color;
import java.util.HashMap;
import java.util.Hashtable;

import lia.Monitor.Agents.OpticalPath.OSPort;
import lia.Monitor.Agents.OpticalPath.OpticalLink;
import lia.Monitor.Agents.OpticalPath.v2.State.OSwPort;
import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.net.topology.Link;
import lia.net.topology.Port;
import lia.net.topology.Port.PortType;
import lia.util.ntp.NTPDate;

public class OSLink implements java.io.Serializable  {

    public OSPort szSourcePort = null;
    public OSwPort szSourceNewPort = null;
    public Port szSourceOSPort = null;
    public Port szDestinationOSPort = null;
    public String szDestPort;
    public String szSourceName;
    public String szDestName;
    public rcNode rcSource = null;
    public rcNode rcDest=null;
    public double fromLAT;
    public double toLAT;
    public double fromLONG;
    public double toLONG;
	public String linkID; 
    public OpticalLink opReference=null;
    public volatile Link opLink = null;
	
    public int flags=0;//special flags to set up this link's purpose
    //several flags can be concatenated, so they must be power of 2 or so
    public static final int OSLINK_FLAG_NORMAL = 0;
    public static final int OSLINK_FLAG_FAKE_NODE = 1;
    /**
     CONNECTED			=	1;
     ML_CONN			=	2;//must be connected
     FREE				=	4;//must be connected
     DISCONNECTED			=	8;
     */
    /**
     * pay attention, values are also defined in {@link lia.Monitor.Agents.OpticalPath.OpticalLink}
     */
    public static final int STATE_CONNECTED = 1;
    public static final int STATE_TRANSFERING = 2;
    public static final int STATE_FREE = 4;
    public static final int STATE_DISCONNECTED = 8;
    public static final int STATE_FAIL                =   16;
    public static final int STATE_IDLE                =   32;//must be CONNECTED and it was previously ML_CONN
    public static final int STATE_UNKNOWN             =   64;//it was not made by ml_path ! --> shouldbe named admin

    public int nState=0;
    public static final Color OSLINK_COLOR_CONNECTED = Color.ORANGE; 
    public static final Color OSLINK_COLOR_TRANSFERING = Color.MAGENTA;
	public static final Color OSLINK_COLOR_TRANSFERING_ML = new Color(0, 141, 45);
    public static final Color OSLINK_COLOR_FREE = Color.YELLOW; 
    public static final Color OSLINK_COLOR_FREE_2 = new Color(24,154,235); 
    public static final Color OSLINK_COLOR_FAIL = Color.RED;
	public static final Color OSLINK_COLOR_DISCONNECTED = Color.GRAY;
	public static final Color OSLINK_COLOR_IDLE = Color.pink;
	public static final Color OSLINK_COLOR_ADMIN = OSLINK_COLOR_TRANSFERING;
    public static final Color OSLINK_COLOR_UNKNOWN = Color.LIGHT_GRAY; 
    
	static final Color lastColor = Color.green;
	public static Hashtable transferringColors = new Hashtable();
	static int cwhere = 0;
	
    public Color getStateColor()
    {
		if ((nState & 16) == 16) 
			return OSLINK_COLOR_DISCONNECTED;
        switch ( nState ) {
        	case 1:
        	    return OSLINK_COLOR_CONNECTED;
        	case 2: case 3:
        	    return OSLINK_COLOR_TRANSFERING;
        	case 4: case 5:
        	    return OSLINK_COLOR_FREE;
        	case 8:
        	    return OSLINK_COLOR_DISCONNECTED;
         }
		if ((nState & 32) == 32)
			return OSLINK_COLOR_IDLE;
		if ((nState & 64) == 64)
			return OSLINK_COLOR_ADMIN;
        return OSLINK_COLOR_UNKNOWN;
    }
	
    public Color getStateColor2() {
		if ((nState & 16) == 16) 
			return OSLINK_COLOR_FAIL;
		if ((nState & 2) == 2) {
			return OSLINK_COLOR_TRANSFERING_ML;
		}
		if ((nState & 4) == 4) 
			return OSLINK_COLOR_FREE_2;
		if ((nState & 8) == 8)
			return OSLINK_COLOR_DISCONNECTED;
		if ((nState & 32) == 32)
			return OSLINK_COLOR_IDLE;
		if ((nState & 64) == 64)
			return OSLINK_COLOR_ADMIN;
		if ((nState & 1) == 1)
			return OSLINK_COLOR_CONNECTED;
        return OSLINK_COLOR_UNKNOWN;
    }

    public int getState() {
        return nState;
    }

    //TODO: ... other aditional info to come
    public long time = System.currentTimeMillis(); //elapsed time since last update
    
    public OSLink( rcNode creator, HashMap hAttrs) {
        rcSource = creator;
        szSourcePort = (OSPort)hAttrs.get( "SourcePort");
        szDestPort = (String)hAttrs.get( "DestinationPort");
        szDestName =(String) hAttrs.get( "DestinationName");
    }
    
    public OSLink( rcNode creator, HashMap hAttrs, rcNode dest) {
        this( creator, hAttrs);
        rcDest = dest;
        fromLONG = failsafeParseFloat( rcSource.LONG, -111.15f);
        fromLAT = failsafeParseFloat( rcSource.LAT, -21.22f);
        toLONG = failsafeParseFloat( rcDest.LONG, -111.15f);
        toLAT = failsafeParseFloat( rcDest.LAT, -21.22f);
    }
    
    public OSLink( rcNode creator, rcNode dest, OSPort srcPort, String dstPort, String srcName, String dstName, String linkID) {
        rcSource = creator;
        rcDest = dest;
        szSourcePort = srcPort;
        szDestPort = dstPort;
        szSourceName = srcName;
        szDestName = dstName;
		this.linkID = linkID;
        if ( rcSource!=null ) {
	        fromLONG = failsafeParseFloat( rcSource.LONG, -111.15f);
	        fromLAT = failsafeParseFloat( rcSource.LAT, -21.22f);
        };
        if ( rcDest!=null ) {
	        toLONG = failsafeParseFloat( rcDest.LONG, -111.15f);
	        toLAT = failsafeParseFloat( rcDest.LAT, -21.22f);
        };
    }

    public OSLink(rcNode creator, rcNode dest, OSwPort srcPort, String dstPort, String srcName, String dstName, String linkID) {
        rcSource = creator;
        rcDest = dest;
        szSourceNewPort = srcPort;
        szDestPort = dstPort;
        szSourceName = srcName;
        szDestName = dstName;
		this.linkID = linkID;
        if ( rcSource!=null ) {
	        fromLONG = failsafeParseFloat( rcSource.LONG, -111.15f);
	        fromLAT = failsafeParseFloat( rcSource.LAT, -21.22f);
        };
        if ( rcDest!=null ) {
	        toLONG = failsafeParseFloat( rcDest.LONG, -111.15f);
	        toLAT = failsafeParseFloat( rcDest.LAT, -21.22f);
        };
    }

    public OSLink( rcNode creator, rcNode dest, Port srcPort, String dstPort, String srcName, String dstName, String linkID) {
        rcSource = creator;
        rcDest = dest;
        szSourceOSPort = srcPort;
        szDestPort = dstPort;
        szSourceName = srcName;
        szDestName = dstName;
		this.linkID = linkID;
        if ( rcSource!=null ) {
	        fromLONG = failsafeParseFloat( rcSource.LONG, -111.15f);
	        fromLAT = failsafeParseFloat( rcSource.LAT, -21.22f);
        };
        if ( rcDest!=null ) {
	        toLONG = failsafeParseFloat( rcDest.LONG, -111.15f);
	        toLAT = failsafeParseFloat( rcDest.LAT, -21.22f);
        };
    }
    
	/**
	 * convert a string to a double value. If there would be an exception
	 * return the failsafe value
	 * @param value initial value
	 * @param failsafe failsafe value
	 * @return final value
	 */
	public static float failsafeParseFloat(String value, float failsafe){
		try {
			return Float.parseFloat(value);
		} catch ( Throwable t  ){  
			return failsafe;
		}
	}
    
    /**
     * updates link's data<br>
     * called on creation also
     * @param r
     * @return if state value has changed
     */
    public boolean updateData( Result r)
    {
        //some result checking...
        //set last update time
        time = NTPDate.currentTimeMillis();
        int prevState = nState;
        //get state as int
        nState = (int)r.param[r.getIndex("State")];
        return (prevState!=nState);
    }
    
    /**
     * updates link's data<br>
     * called on creation also
     * @param state OpticalLink.state
     * @return if state value has changed
     */
    public boolean updateState( Integer state)
    {
        //some result checking...
        //set last update time
        time = NTPDate.currentTimeMillis();
        int prevState = nState;
        //get state as int
        nState = state.intValue();
        return (prevState!=nState);
    }
    
    public boolean updateState(int state) {
        time = NTPDate.currentTimeMillis();
        int prevState = nState;
        //get state as int
        nState = state;
        return (prevState!=nState);
    }
    
	public boolean updateStateV2(int fiberState, int powerState, String linkID) {

		time = NTPDate.currentTimeMillis();
        int prevState = nState;
		
		if (fiberState == OSwPort.FIBER) {
			switch (powerState) {
			case OSwPort.LIGHTERR : nState = STATE_FAIL; break;
			case OSwPort.LIGHTERR_SIMULATED : nState = STATE_FAIL; break;
			case OSwPort.LIGHTOK: {
				if (linkID == null) {
					nState = STATE_UNKNOWN; break;
				} 
				nState = STATE_TRANSFERING; break;
			}
			case OSwPort.NOLIGHT: nState = STATE_FREE; break;
			case OSwPort.NOLIGHT_SIMULATED: nState = STATE_FREE; break;
			case OSwPort.UNKLIGHT: nState = STATE_FREE; break;
			case OSwPort.WFORLIGHT1: nState = STATE_IDLE; break;
			case OSwPort.WFORLIGHT2: nState = STATE_IDLE; break;
			}
		} else {
			nState = STATE_DISCONNECTED;
		}
        return (prevState!=nState);
	}
	
	public Color getPortColor(int fiberState, int powerState, String linkID) {

		int nState = 0;
		if (fiberState == OSwPort.FIBER) {
			switch (powerState) {
			case OSwPort.LIGHTERR : nState = STATE_FAIL; break;
			case OSwPort.LIGHTERR_SIMULATED : nState = STATE_FAIL; break;
			case OSwPort.LIGHTOK: {
				if (linkID == null) {
					nState = STATE_UNKNOWN; break;
				} 
				nState = STATE_TRANSFERING; break;
			}
			case OSwPort.NOLIGHT: nState = STATE_FREE; break;
			case OSwPort.NOLIGHT_SIMULATED: nState = STATE_FREE; break;
			case OSwPort.UNKLIGHT: nState = STATE_FREE; break;
			case OSwPort.WFORLIGHT1: nState = STATE_IDLE; break;
			case OSwPort.WFORLIGHT2: nState = STATE_IDLE; break;
			}
		} else {
			nState = STATE_DISCONNECTED;
		}
		
		if ((nState & 16) == 16) 
			return OSLINK_COLOR_FAIL;
		if ((nState & 2) == 2) {
			return OSLINK_COLOR_TRANSFERING_ML;
		}
		if ((nState & 4) == 4) 
			return OSLINK_COLOR_FREE_2;
		if ((nState & 8) == 8)
			return OSLINK_COLOR_DISCONNECTED;
		if ((nState & 32) == 32)
			return OSLINK_COLOR_IDLE;
		if ((nState & 64) == 64)
			return OSLINK_COLOR_ADMIN;
		if ((nState & 1) == 1)
			return OSLINK_COLOR_CONNECTED;
        return OSLINK_COLOR_UNKNOWN;
	}
	
    
	public String toString() {
		
		if (szSourcePort != null) {
			if (szSourcePort.type.shortValue() == OSPort.OUTPUT_PORT)
				return " OSLINK "+szSourceName+":"+szSourcePort.name+ " -> "+szDestName+(szDestPort!=null?":"+szDestPort:"");
			return " OSLINK "+szDestName+(szDestPort != null ? ":"+szDestPort:"")+" -> "+szSourceName+":"+szSourcePort.name;
		}
		if (szSourceNewPort != null) {
			if (szSourceNewPort.type == OSwPort.OUTPUT_PORT)
				return " OSLINK "+szSourceName+":"+szSourceNewPort.name+ " -> "+szDestName+(szDestPort!=null?":"+szDestPort:"");
			return " OSLINK "+szDestName+(szDestPort != null ? ":"+szDestPort:"")+" -> "+szSourceName+":"+szSourceNewPort.name;
		}
		if (szSourceOSPort != null) {
			if (szSourceOSPort.type() == PortType.OUTPUT_PORT)
				return " OSLINK "+szSourceName+":"+szSourceOSPort.name()+ " -> "+szDestName+(szDestPort!=null?":"+szDestPort:"");
			return " OSLINK "+szDestName+(szDestPort != null ? ":"+szDestPort:"")+" -> "+szSourceName+":"+szSourceOSPort.name();
		}
		return "  OSLINK unknown";
	}

	public static String ToString( String sName, OSPort sPort, String dName, String dPort) {
		if (sPort.type.shortValue() == OSPort.OUTPUT_PORT)
			return " OSLINK "+sName+":"+sPort.name+ " -> "+dName+(dPort!=null?":"+dPort:"");
		return " OSLINK "+dName+(dPort!=null?":"+dPort:"")+" -> "+sName+":"+sPort.name;
	}

	public static String ToString( String sName, OSwPort sPort, String dName, String dPort) {
		if (sPort.type == OSwPort.OUTPUT_PORT)
			return " OSLINK "+sName+":"+sPort.name+ " -> "+dName+(dPort!=null?":"+dPort:"");
		return " OSLINK "+dName+(dPort!=null?":"+dPort:"")+" -> "+sName+":"+sPort.name;
	}

	public static String ToString( String sName, Port sPort, String dName, String dPort) {
		if (sPort.type() ==PortType.OUTPUT_PORT)
			return " OSLINK "+sName+":"+sPort.name()+ " -> "+dName+(dPort!=null?":"+dPort:"");
		return " OSLINK "+dName+(dPort!=null?":"+dPort:"")+" -> "+sName+":"+sPort.name();
	}
	
	public void setFlags(int new_flags) {
	    flags = new_flags;
	}
	
	public boolean checkFlag( int flag) {
	    return (flags&flag)>0?true:false;
	}
	
	public boolean checkState( int val) {
	    return (nState&val)>0?true:false;
	}
	
    public OpticalLink getOpReference() {
        return opReference;
    }
    
    public void setOpReference(OpticalLink opLink) {
        this.opReference = opLink;
    }
    
    public void setLink(Link opLink) {
    	this.opLink = opLink;
    }
    
    public boolean equals(Object other) {
    	if ( ! (other instanceof OSLink ) )
    		return false;
        if ( this.toString().equals( ((OSLink)other).toString()) )
                return true;
        return false;
    }
    
    public int hashCode() {
    	return toString().hashCode();
    }
    
    //********* for faster link checking we use an algorithm based on dirty flags ************/
    
    private boolean isDirty = false;
    
    public final void setDirty(final boolean dirty) {
    	this.isDirty = dirty;
    }
    
    public final boolean isDirty() {
    	return isDirty;
    }
    
}
