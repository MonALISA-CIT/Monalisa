package lia.util;

import java.util.ArrayList;
import java.util.List;

import lia.Monitor.Store.Cache;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.TimestampedResult;
import lia.web.utils.DoubleFormat;

/**
 * @author costing
 * @since 2008-07-16
 */
public class StatusPercentage {

	/**
	 * What is the default maximum allowed gap size.
	 */
	public static final long DEFAULT_MIN_GAP_SIZE = 1000*60*5;
	
	/**
	 * If we have a point after a gap (or an initial point) we will apply this
	 * default measurement interval instead of the real time difference between points.
	 */
	public static final long DEFAULT_MEASUREMENT_INTERVAL = 1000*60;
	
	/**
	 * Data start time
	 */
	private long lStart;
	
	/**
	 * Data end time
	 */
	private long lEnd;
	
	/**
	 * How big were the gaps in total
	 */
	private long lGapTotalTime;
	
	/**
	 * Percentage of the time that is covered by data (exclude gaps)
	 */
	private double dataAvailabilityPercentage;
	
	/**
	 * What is the percentage of time, from available data, while the status was "OK" (1)
	 */
	private double upPercentage;
	
	/**
	 * What is the percentage of time while the status was "OK" (1), considering that during
	 * gaps the service was always OK.
	 */
	private double upCompensated;

	/**
	 * How many points were found
	 */
	private int iCount;
	
	/**
	 * What is gap size that we should consider
	 */
	private long lGapSize = DEFAULT_MIN_GAP_SIZE;
	
	/**
	 * What is the measurement interval used to generate this series
	 */
	private long lMeasurementInterval = DEFAULT_MEASUREMENT_INTERVAL;
	
	/**
	 * Data to analyze
	 */
	private List<TimestampedResult> lResults;
	
	/**
	 * Create the statistics from a list of Result objects
	 * 
	 * @param l data to analyze
	 */
	public StatusPercentage(final List<TimestampedResult> l){
		this.lResults = l;
	}

	private void reset(){
		iCount = 0;
		
		lStart = lEnd = -1;
		
		dataAvailabilityPercentage = upPercentage = upCompensated = -1;
		
		lGapTotalTime = 0;
	}
	
	/**
	 * Set the gap size for this data set.
	 * 
	 * @param newGapSize interval, in milliseconds
	 */
	public void setGapSize(final long newGapSize){
		this.lGapSize = newGapSize; 
	}
	
	/**
	 * Set the measurement interval for this data set.
	 * You can set this to 0 to disable going back in time for first point or after a gap.
	 * 
	 * @param newMeasurementInterval interval, in milliseconds
	 */
	public void setMeasurementInterval(final long newMeasurementInterval){
		this.lMeasurementInterval = newMeasurementInterval;
	}

	/**
	 * Analyze the data array and produce the statistics
	 */
	public void analyze(){
		if (lResults==null || lResults.size()==0){
			reset();
			return;
		}
		
		analyze(0, lResults.size(), lResults.get(0).getTime()-lMeasurementInterval, lResults.get(lResults.size()-1).getTime());
	}
	
	/**
	 * Analyze a portion of the data array
	 * 
	 * @param iStart position in the array to start from (at least)
	 * @param iEnd position in the array to end at (at most)
	 * @param tStart start time
	 * @param tEnd end time
	 * @return last index from the array that was before iEnd and having time less or equal to tEnd
	 */
	public int analyze(final int iStart, final int iEnd, final long tStart, final long tEnd){
		reset();
	
		if (iStart<0 || lResults==null || lResults.size()==0 || iStart>=lResults.size())
			return lResults==null ? 0 : lResults.size();
		
		long lPrevTime = -1;
		double dPrevValue = -1;

		if (iStart>0){
			final Result r = (Result) lResults.get(iStart-1);
			
			lPrevTime = r.time;
			dPrevValue = r.param[0];
		}
		
		// total time for which we have data
		long lDataTotalTime = 0;
		
		double dSumData = 0;
		
		int i = iStart;
		
		for (; i<iEnd; i++){
			final Result r = (Result) lResults.get(i);
			
			final long lTime = r.time;
			
			if (lTime < tStart || lTime==lPrevTime)
				continue;
			
			if (lTime > tEnd){
				lEnd = tEnd;
				
				if (lTime - lPrevTime < lGapSize){
					double dMedValue = r.param[0] + (dPrevValue - r.param[0]) * (lTime-tEnd) / (lTime - lPrevTime);

					dSumData += (tEnd - lPrevTime)*(dPrevValue+dMedValue)/2;
					
					lDataTotalTime += tEnd - lPrevTime;	
				}
				else{
					lGapTotalTime += tEnd - lPrevTime;
				}
				
				break;
			}
			
			iCount++;
			
			final double dValue = r.param[0];
			
			if (iCount==1){
				// at the first real point we do some magic to stretch the start
		
				if (lPrevTime<0 || lTime - lPrevTime>lGapSize){
					// nothing before
					lPrevTime = lTime - lMeasurementInterval;
					dPrevValue = dValue;
				}
				
				if (lPrevTime<tStart){
					dPrevValue = dValue + (dPrevValue - dValue)*(lTime-tStart)/(lTime-lPrevTime);
					lPrevTime  = tStart;
				}
				
				lStart = lPrevTime;
			}
			else
			if (lTime-lPrevTime>lGapSize){
				// not the first point, with a gap
				lGapTotalTime += lTime - lPrevTime - lMeasurementInterval;
				
				lPrevTime = lTime - lMeasurementInterval;
				dPrevValue = dValue;
			}
			
			lDataTotalTime += lTime - lPrevTime;
			
			dSumData += (lTime - lPrevTime)*(dPrevValue+dValue)/2;		
			
			lEnd = lPrevTime = lTime;
			dPrevValue = dValue;
		}

		if (lStart>0 && lEnd - lStart>0){
			dataAvailabilityPercentage = lDataTotalTime * 100d / (lEnd - lStart);
			upPercentage = dSumData * 100 / lDataTotalTime;
			upCompensated = (dSumData + lGapTotalTime) * 100d / (lEnd - lStart);
		}
		
		return i;
	}
	
	/**
	 * Get the first timestamp for which the statistics apply
	 * 
	 * @return timestamp, in milliseconds. Can be negative if there was no point to analyze
	 */
	public long getStartTime(){
		return lStart;
	}
	
	/**
	 * Get the last timestamp for which the statistics apply
	 * 
	 * @return timestamp, in milliseconds. Can be negative if there was no point to analyze
	 */
	public long getEndTime(){
		return lEnd;
	}	

	/**
	 * Get the total length of the gaps in the analyzed data
	 * 
	 * @return
	 */
	public long getGapTotalTime(){
		return lGapTotalTime;
	}
	
	/**
	 * Get the percentage of the time for which we had 
	 * 
	 * @return
	 */
	public double getDataAvailabilityPercentage(){
		return dataAvailabilityPercentage;
	}
	
	/**
	 * Get the percentage of the time in which the value was 1. This only takes into account the
	 * intervals on which we had data, ignoring the gaps.
	 * 
	 * @return percentage of availability. Can be negative if we don't have any statistics.
	 * @see #getUpCompensated()
	 */
	public double getUpPercentage(){
		return upPercentage;
	}

	/**
	 * Get the percentage of the time in which the value was either 1 or missing. 
	 * This function considers that during the gap periods the service was up.
	 * 
	 * @return percentage of availability. Can be negative if we don't have any statistics.
	 * @see #getUpPercentage()
	 */
	public double getUpCompensated(){
		return upCompensated;
	}
	
	public String toString(){
		return "Between "+lStart+" - "+lEnd+" ("+(lEnd-lStart)+"):\n"+
				"- "+iCount+" points analyzed\n"+
				"- "+DoubleFormat.point(dataAvailabilityPercentage)+"% of the time the data was available ("+lGapSize+" gap threshold, total gap length: "+lGapTotalTime+")\n"+
				"- "+DoubleFormat.point(upPercentage)+"% of the time the status was 1 (gaps ignored)\n"+
				"- "+DoubleFormat.point(upCompensated)+"% of the time the status was 1 or data was missing\n"
				;
	}
	
	/**
	 * Debug method
	 * 
	 * @param args
	 */
	public static void main(final String[] args) {
		final ArrayList al = new ArrayList();
		
		final StatusPercentage sp = new StatusPercentage(al);
		
		//sp.setMeasurementInterval(0);
		
		final long[] vlTimes    = new   long[]{1000000, 1500000, 1550000, 1900000};
		final double[] vdValues = new double[]{      0,       0,       1,       1};
		
		
		for (int i=0; i<vlTimes.length; i++){
			final Result r = new Result();
			r.time = vlTimes[i];
			r.addSet("y", vdValues[i]);
			al.add(r);
		}
		
		int iPrev = 0;
		
		long lBin = 60000;
		
		for (long l = 950000; l<2000000; l+=lBin){
			System.err.println("Starting from "+iPrev);
			iPrev = sp.analyze(iPrev, al.size(), l, l+lBin);
			System.err.println(l+" : "+sp);
		}
		
		sp.analyze();
		System.err.println("Overall statistics: "+sp);
	}
}
