package lia.Monitor.GUIs;

/**
 * Defines a measurement unit for a parameter/function.
 * NOTE: original version in ML2....
 * 
 * @author Costin Grigoras <costing@cs.pub.ro>
 * @version 1.0 02/16/2005
 */
public class Unit {
	
	/** The data type definitions. */
	public static final byte
	TYPE_UNKNOWN = 0,
	TYPE_SCALAR  = 1,
	TYPE_PERCENT = 2,
	TYPE_BIT     = 3,
	TYPE_BYTE    = 4,
	TYPE_TEXT    = 5,
	TYPE_CONFIG  = 6,
	TYPE_IMAGE   = 7,
	TYPE_TIME    = 8,
	TYPE_TEMPERATURE = 9
	;
	
	/** Constants for the most used measurement units */
	public static final long
	KILO_BIT  = 1000L,
	KILO_BYTE = 1024L,
	MEGA_BIT  = KILO_BIT*1000L,
	MEGA_BYTE = KILO_BYTE*1024L,
	GIGA_BIT  = MEGA_BIT*1000L,
	GIGA_BYTE = MEGA_BYTE*1024L,
	TERA_BIT  = GIGA_BIT*1000L,
	TERA_BYTE = GIGA_BYTE*1024L,
	PETA_BIT  = TERA_BIT*1000L,
	PETA_BYTE = TERA_BYTE*1024L,
	EXA_BIT   = PETA_BIT*1000L,
	EXA_BYTE  = PETA_BYTE*1024L,
	PER_MILLI = 1L,
	PER_SECOND= PER_MILLI*1000L,
	PER_MINUTE= PER_SECOND*60L,
	PER_HOUR  = PER_MINUTE*60L,
	PER_DAY   = PER_HOUR*24L,
	PER_WEEK  = PER_DAY*7L,
	PER_YEAR  = PER_DAY*365L;
	
	/**  One of the <i>TYPE_*</i> constants. */
	public byte iType;
	
	/**
	 * The time base of this unit.<br>
	 * <br>
	 * ==0 - the value has no time-based meaning<br>
	 * &gt;0 - the value has a base of N millis<br>
	 * &lt;0  - the value has a base of 1/(-N) millis<br>
	 * <br>
	 * For example Mbps should have <code>lTimeMultiplier=1000</code><br>
	 */
	public long lTimeMultiplier;
	
	/**
	 * The multiplier of this unit.<br>
	 *<br>
	 * ==0 - the value cannot be represented as divisions or multiples of a larger value<br>
	 * &gt;0  - the value is a multiple of a base value<br>
	 * &lt;0  - the value is a division of a base value<br>
	 * <br>
	 * For example :<br>
	 * <li>Mbps should have <code>lUnitMultplier=1000*1000</code></li>
	 * <li>disk space MB should have <code>lUnitMultiplier=1024*1024</code></li>
	 */
	public long lUnitMultiplier;
	
	/**
	 * Initialize this unit with the default values.<br>
	 * <br>
	 * <li>type = unknown</li>
	 * <li>time base = 1000, eg 1 second</li>
	 * <li>multiplier = 1</li>
	 * <li>incremental = false</li>
	 * <li>transient = false</li>
	 * <li>transient history = false</li>
	 * <li>user-defined unit description = <code>null</code></li>
	 * <li>log the values = false</li>
	 * <li>real-time threshold = 15 minutes</li>
	 * <li>other attributes = <code>null</code></li>
	 */
	public Unit(){
		this(
				TYPE_UNKNOWN,	// type: unknown
				1000, 		// second-based
				1 
		);
	}
	
	/**
	 * Initialize this unit with the full set of options.
	 *
	 * @param iType data type
	 * @param lTimeMultiplier time multiplier
	 * @param lUnitMultiplier value multiplier
	 * @param bIncremental incremental flag
	 * @param bTransient transient flag
	 * @param bTransientHistory transient history flag
	 * @param sUnit user-defined string definition of this unit, can be <code>null</code>
	 * @param bLog log values flag
	 * @param lRealTime real-time definition of this data
	 * @param htAttributes extended attributes
	 */
	public Unit(byte iType, long lTimeMultiplier, long lUnitMultiplier){
		this.iType             = iType;
		this.lTimeMultiplier   = lTimeMultiplier;
		this.lUnitMultiplier   = lUnitMultiplier;
	}
	
	/** A string representation of this <code>Unit</code>, usefull for debugging. */
	public String toString(){
		String sRez = "";
		
		if (iType!=TYPE_TIME){
			final long l = iType==TYPE_BIT ? 1000 : 1024;
			
			if (lUnitMultiplier==l) sRez="K";
			if (lUnitMultiplier==l*l) sRez="M";
			if (lUnitMultiplier==l*l*l) sRez="G";
			if (lUnitMultiplier==l*l*l*l) sRez="T";
			if (lUnitMultiplier==l*l*l*l*l) sRez="P";
			if (lUnitMultiplier==l*l*l*l*l*l) sRez="X";
			
			if (iType==TYPE_BIT) 	 sRez += "b";
			if (iType==TYPE_BYTE)	 sRez += "B";
			
			if (iType==TYPE_PERCENT) sRez += "%";
			if (iType == TYPE_TEMPERATURE) sRez += "\u2103";
		}
		else{
			if (lUnitMultiplier==1)	 	sRez += "ms";
			if (lUnitMultiplier==-1000) 	sRez += "ns";
			if (lUnitMultiplier==PER_SECOND)	sRez += "sec";
			if (lUnitMultiplier==PER_MINUTE)	sRez += "min";
			if (lUnitMultiplier==PER_HOUR)	sRez += "h";
		}
		
		if (lTimeMultiplier==1)	 	sRez += "pms";
		if (lTimeMultiplier==-1000) 	sRez += "pns";
		if (lTimeMultiplier==PER_SECOND)sRez += "ps";
		if (lTimeMultiplier==PER_MINUTE)sRez += "pm";
		if (lTimeMultiplier==PER_HOUR)	sRez += "ph";
		
		return sRez;
	}
	
	/**
	 * Auxiliary method that is used to verify that the parameter with this unit can be plotted toghether with the parameter with another unit or not
	 * @param newUnit
	 * @return
	 */
	public boolean match(Unit newUnit) {
		if (newUnit == null) return false;
		if (iType != newUnit.iType) return false; // if the two are not using the same type than cannot match
		if (lTimeMultiplier == 0l && newUnit.lTimeMultiplier != 0l) return false;
		if (lTimeMultiplier != 0l && newUnit.lTimeMultiplier == 0l) return false;
		return true;
	}
	
} // end of class Unit
