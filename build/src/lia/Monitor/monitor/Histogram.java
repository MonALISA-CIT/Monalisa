package lia.Monitor.monitor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Very simplistic implementation of a histogram. Only for integer keys.
 * 
 * @author costing
 */
public class Histogram implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private TreeMap<Integer, AtomicInteger> values = null;
	
	private int minLimit = Integer.MIN_VALUE;
	private int maxLimit = Integer.MAX_VALUE;
	
	private AtomicInteger belowMin = null;
	private AtomicInteger aboveMax = null;
	
	private String name = null;
	
	/**
	 * 
	 */
	public Histogram(){
		// nothing yet
	}
	
	/**
	 * @return the current max limit
	 */
	public int getMaxLimit() {
		return maxLimit;
	}
	
	/**
	 * @return the current min limit
	 */
	public int getMinLimit() {
		return minLimit;
	}
	
	/**
	 * Set the min cut of this histogram
	 * 
	 * @param newLimit
	 * @return previous min value
	 */
	public int setMinLimit(final int newLimit){
		final int oldlimit = minLimit;
		
		minLimit = newLimit;
		
		if (values!=null){
			final Iterator<Map.Entry<Integer, AtomicInteger>> it = values.entrySet().iterator();
			
			while (it.hasNext()){
				final Map.Entry<Integer, AtomicInteger> me = it.next();
				
				final int i = me.getKey().intValue();
				
				if (i < minLimit ){
					add(i, me.getValue().intValue());
					it.remove();
				}
				else
					break;
			}
		}
		
		return oldlimit;
	}

	/**
	 * Set the max cut of this histogram
	 * 
	 * @param newLimit
	 * @return previous max value
	 */
	public int setMaxLimit(final int newLimit){
		final int oldlimit = maxLimit;
		
		maxLimit = newLimit;
		
		if (values!=null){
			final Iterator<Map.Entry<Integer, AtomicInteger>> it = values.entrySet().iterator();
			
			while (it.hasNext()){
				final Map.Entry<Integer, AtomicInteger> me = it.next();
				
				final int i = me.getKey().intValue();
				
				if (i > maxLimit ){
					add(i, me.getValue().intValue());
					it.remove();
				}
			}
		}
		
		return oldlimit;
	}
	
	/**
	 * Set the prefix for reading and writing to Result objects
	 * 
	 * @param newName
	 * @return the old prefix
	 */
	public String setName(final String newName){
		final String oldName = name;
		
		name = newName;
		
		return oldName;
	}
	
	/**
	 * @return the histogram name
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Add a new value in the histogram
	 * 
	 * @param value
	 */
	public void add(final int value){
		add(value, 1);
	}
	
	/**
	 * Add a new value in the histogram
	 * 
	 * @param value
	 * @param quantity 
	 */
	public void add(final int value, final int quantity){
		AtomicInteger target = null;
		
		if (value < minLimit){
			target = belowMin;
			
			if (target==null){
				belowMin = new AtomicInteger(quantity);
				return;
			}
		}
		else
		if (value > maxLimit){
			target = aboveMax;
			
			if (target==null){
				aboveMax = new AtomicInteger(quantity);
				return;
			}
		}
		else{
			final Integer v = Integer.valueOf(value);
			
			if (values==null){
				values = new TreeMap<Integer, AtomicInteger>();
			}
			
			target = values.get(v);
			
			if (target==null){
				target = new AtomicInteger(quantity);
				values.put(v, target);
				return;
			}
		}
		
		target.addAndGet(quantity);
	}
	
	/**
	 * Add the first (only in some cases) value from this Result
	 * 
	 * @param r
	 */
	public void add(final Result r){
		add(r, 0);
	}
	
	/**
	 * Add the value from this Result at the given index
	 * 
	 * @param r
	 * @param index
	 */
	public void add(final Result r, final int index){
		if (r!=null && r.param!=null && r.param.length>index && index>=0){
			final double d = r.param[index];
			
			final int i = (int) Math.round(d);
			
			add(i);
		}
	}
	
	/**
	 * Add the contents of another histogram to itself
	 * 
	 * @param other
	 */
	public void add(final Histogram other){
		if (other.belowMin!=null){
			add(other.minLimit-1, other.belowMin.intValue());
		}
		
		if (other.values!=null){
			for (Map.Entry<Integer, AtomicInteger> me: other.values.entrySet())
				add(me.getKey().intValue(), me.getValue().intValue());
		}
		
		if (other.aboveMax!=null){
			add(other.maxLimit+1, other.aboveMax.intValue());
		}		
	}
	
	/**
	 * Fill the given Result with the values
	 * 
	 * @param r
	 */
	public void fill(final Result r){
		rebin();
		
		if (belowMin!=null){
			r.addSet(name!=null ? name+"_below_"+minLimit : "below_"+minLimit, belowMin.doubleValue());
		}
		
		if (aboveMax!=null){
			r.addSet(name!=null ? name+"_above_"+maxLimit : "above_"+maxLimit, aboveMax.doubleValue());
		}
		
		if (values!=null){
			for (final Map.Entry<Integer, AtomicInteger> me: values.entrySet()){
				r.addSet(name!=null ? name+"_"+me.getKey() : String.valueOf(me.getKey()), me.getValue().doubleValue());
			}
		}
		
		r.addSet(name!=null ? name+"_count" : "count", count());
		
		final double dAvg = avg();
		
		if (!Double.isNaN(dAvg))
			r.addSet(name!=null ? name+"_avg" : "avg", dAvg);
	}
	
	/**
	 * @param r Result to fill from
	 * @see #setName(String)
	 */
	public void parse(final Result r){
		if (r==null || r.param==null || r.param_name==null || r.param.length==0 || r.param.length != r.param_name.length)
			return;
		
		for (int i=0; i<r.param.length; i++){
			final String key = r.param_name[i];
			
			if (name==null){
				parseValue(key, i);
			}
			else{
				if (key.startsWith(name+"_")){
					parseValue(key.substring(name.length()+1), r.param[i]);
				}
			}
		}
	}
	
	private void parseValue(final String key, final double v){
		if (key.startsWith("below_") || key.startsWith("above_")){
			try{
				int m = Integer.parseInt(key.substring(6));
				
				if (key.startsWith("b")){
					setMinLimit(m);
					add(m-1, (int) Math.round(v));
				}
				else{
					setMaxLimit(m);
					add(m+1, (int) Math.round(v));
				}
			}
			catch (Throwable t){
				// ignore
			}
		}
		else{
			try{
				int m = Integer.parseInt(key);
				
				add(m, (int) Math.round(v));
			}
			catch (Throwable t){
				// ignore
			}
		}
	}
	
	/**
	 * clear the histogram values
	 */
	public void clearValues(){
		belowMin = null;
		aboveMax = null;
		values = null;
	}
	
	@Override
	public String toString(){
		rebin();
		
		final StringBuilder sb = new StringBuilder();
		
		if (name!=null)
			sb.append(name).append(": ");
		else
			sb.append("<unnamed>: ");
		
		boolean first = true;
		
		if (belowMin!=null){
			sb.append("below ").append(minLimit).append(": ").append(belowMin.intValue());
			first = false;
		}

		if (values!=null){
			for (Map.Entry<Integer, AtomicInteger> me: values.entrySet()){
				if (!first)
					sb.append(", ");
				else
					first = false;
				
				sb.append(me.getKey()).append(": ").append(me.getValue());
			}
		}

		if (aboveMax!=null){
			if (!first)
				sb.append(", ");
			
			sb.append("above ").append(maxLimit).append(": ").append(aboveMax.intValue());
		}

		return sb.toString();
	}
	
	private int nrBins = -1;
	
	/**
	 * Rebin again at the same binning as the last call of {@link #rebin(int)}
	 */
	public void rebin(){
		if (nrBins>0)
			rebin(nrBins);
	}
	
	/**
	 * @param bins
	 * @return true if the structure has changed
	 */
	public boolean rebin(final int bins){
		if (bins<1)
			return false;
		
		if (values==null || values.size()<=bins)
			return false;
		
		nrBins = bins;
		
		final ArrayList<Map.Entry<Integer, AtomicInteger>> old = new ArrayList<Map.Entry<Integer, AtomicInteger>>(values.entrySet());
		
		values = null;
		
		int min = old.get(0).getKey().intValue();
		final int max = old.get(old.size()-1).getKey().intValue();
		final int step = (max - min + 1) / bins;
		
		for (final Map.Entry<Integer, AtomicInteger> me: old){
			final int k = me.getKey().intValue();
			final int v = me.getValue().intValue();
			
			while (k>min+step)
				min += step;
			
			add(min+step/2, v);
		}
			
		return true;
	}
	
	/**
	 * @return number of points
	 */
	public int count(){
		int count = 0;
		
		if (belowMin!=null)
			count += belowMin.intValue();
		
		if (aboveMax!=null)
			count += aboveMax.intValue();
		
		if (values!=null){
			for (final AtomicInteger ai: values.values())
				count += ai.intValue(); 
		}
		
		return count;
	}
	
	/**
	 * @return the average value of all points, including the special below min and above max (-1 / +1 respectively)
	 */
	public double avg(){
		return avg(true);
	}
	
	/**
	 * @param includeOverLimits flag to include or not the below min (assumed value = minLimit - 1) and above max
	 *   (assumed value = maxLimit + 1) 
	 * @return the average value of all points
	 */
	public double avg(final boolean includeOverLimits){
		long count = 0;
		
		long sum = 0;
		
		if (includeOverLimits){
			if (belowMin!=null){	// ? is this a good estimation ?
				count += belowMin.intValue();
				sum += (minLimit - 1) * belowMin.intValue();
			}
		
			if (aboveMax!=null){
				count += aboveMax.intValue();
				sum += (maxLimit + 1) * aboveMax.intValue();
			}
		}
		
		if (values!=null){
			for (final Map.Entry<Integer, AtomicInteger> me: values.entrySet()){
				count += me.getValue().intValue();
			
				sum += me.getKey().intValue() * me.getValue().intValue();
			}
		}
		
		if (count==0)
			return Double.NaN;
		
		return (double) sum / count;
	}
	
	/**
	 * Get the sum(value*number) of the points, including the special below min and above max (-1 / +1 respectively)
	 * 
	 * @return the volume
	 */
	public long volume(){
		return volume(true);
	}
	
	/**
	 * Get the sum(value*number) of the points.
	 * 
	 * @param includeOverLimits includeOverLimits flag to include or not the below min (assumed value = minLimit - 1) and above max
	 *   (assumed value = maxLimit + 1) 
	 * @return the volume
	 */
	public long volume(final boolean includeOverLimits){
		long sum = 0;
		
		if (includeOverLimits){
			if (belowMin!=null){	// ? is this a good estimation ?
				sum += (minLimit - 1) * belowMin.intValue();
			}
		
			if (aboveMax!=null){
				sum += (maxLimit + 1) * aboveMax.intValue();
			}
		}
		
		if (values!=null){
			for (final Map.Entry<Integer, AtomicInteger> me: values.entrySet()){
				int value = me.getKey().intValue();
				int count = me.getValue().intValue();
				
//				System.err.println("value : "+value+", count : "+count);
				
				sum += value * count;
			}
		}
		
		return sum;		
	}
	
	/**
	 * @return the number of bins (including the two special ones, below min and above max, if present)
	 */
	public int size(){
		int size = values!=null ? values.size() : 0;
		
		if (belowMin!=null) size++;
		if (aboveMax!=null) size++;
		
		return size;
	}
	
	/**
	 * development and debugging
	 * @param args
	 */
	public static void main(String[] args) {
		Histogram h = new Histogram();
		
		Histogram h2 = new Histogram();
		
		for (int i=0; i<10; i++){
			h.add(i);
			
			h2.add(i/2);
		}
		
		System.err.println(h2.count());
		System.err.println(h2.size());
		System.err.println(h2.volume());
	}
}
