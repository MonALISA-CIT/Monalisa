package lia.Monitor.JiniClient.ReflRouter;

import lia.util.ntp.NTPDate;

/**
 * This class holds a parameter's values history.
 * Note this is not thread safe!
 * 
 * @author catac 
 */
public class HistoryParam {
	/** Values of this parameter, hold in a circular array */
	private double [] values;
	
	/** helper for the 'values' circular array */
	private int head, tail;
	
	/** number of elements in the array */
	private int count;

	/** Last time when values were added. */
	private long lastUpdateTime;

	/** After how long this param expires, if not updated */
	private long expireInterval;
	
	/** The history length */
	public HistoryParam(int length, long valuesTimeout) {
		values = new double[length];
		head = 0; tail = 0;
		count = 0;
		lastUpdateTime = 0;
		expireInterval = valuesTimeout;
	}
	
	/** 
	 * Add a new value to the history list 
	 * @param value the value to add to this parameter's history 
	 */
	public void addValue(double value) {
		values[head] = value;
		head = (head + 1) % values.length;
		if(head == tail)
			tail = (tail + 1) % values.length;
		else
			count++;
		lastUpdateTime = NTPDate.currentTimeMillis();
	}
	
	/** Discard all the values in history */
	public void invalidate(){
		tail = head;
		count = 0;
		lastUpdateTime = 0;
	}
	
	/** Get the number of values in this history */
	public int size(){
		return count;
	}
	
	/** Return true if this parameter wasn't update for more than the expiration period */
	public boolean isExpired(){
		return getUpdateDelay() > expireInterval;
	}
	
	/** Return true if this parameter is valid, i.e. has enough values and it's not expired */
	public boolean isValid(int cnt){
		return count >= cnt && ! isExpired();
	}
	
	/** Get the delay since last update */
	public long getUpdateDelay(){
		return NTPDate.currentTimeMillis() - lastUpdateTime;
	}
	
	/** Get the last value for this param */
	public double getLastValue(){
		int i = head - 1;
		if(i < 0) i += values.length;
		return values[i];
	}
	
	/** Get the average of the last cnt values */
	public double getAvgValue(final int cnt){
		final int n = Math.min(cnt, count);
		if(n == 0)
			return 0;
		double avg = 0;
		for(int i=1; i<=n; i++){
			int j = head - i;
			if(j < 0) j += values.length;
			avg += values[j];
		}
		return avg / n;
	}
	
	/** Get a smoothed average (with min and max removed from the set) of the last cnt values */
	public double getSmoothAvg(final int cnt){
		final int n = Math.min(cnt, count);
		if(n <= 2)
			return getAvgValue(n);
		double sum = 0;
		double min = Double.MAX_VALUE;
		double max = Double.MIN_VALUE;
		for(int i=1; i<=n; i++){
			int j = head - i;
			if(j < 0) j += values.length;
			final double val = values[j];
			sum += val;
			min = Math.min(min, val);
			max = Math.max(max, val);
		}
		final double avg = (sum - min - max) / (n-2);
		return avg;
	}
	
	/** Get the standard deviation of the last cnt values */
	public double getStdDev(final int cnt){
		final int n = Math.min(cnt, count);
		if(n == 0)
			return 0;
		final double avg = getAvgValue(n);
		double sqSum = 0;
		for(int i=1; i<=n; i++){
			int j = head - i;
			if(j < 0) j += values.length;
			final double term = values[j] - avg;
			sqSum += term * term;
		}
		sqSum /= n;
		return Math.sqrt(sqSum);
	}
}
