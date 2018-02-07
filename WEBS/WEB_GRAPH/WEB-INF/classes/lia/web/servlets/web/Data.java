/**
 * 
 */
package lia.web.servlets.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;

/**
 * @author costing
 * @since Jul 25, 2007
 */
public final class Data {
	private static final Logger	logger = Logger.getLogger(Data.class.getName());
	
	/**
	 * Series name
	 */
	public String sName;
	
	/**
	 * Data points
	 */
	public List<DataPoint> data;
	
	/**
	 * Create the data series
	 * 
	 * @param sName
	 */
	public Data(final String sName){
		this.sName = sName;
		data = new ArrayList<DataPoint>();
	}
	
	/**
	 * Add a point
	 * 
	 * @param x
	 * @param y
	 */
	public void add(final double x, final double y){
		data.add(new DataPoint(x,y));
	}
	
	/**
	 * Get the data as a TimeSeries (X -&gt; epoch time)
	 * 
	 * @return data as a TimeSeries
	 */
	public TimeSeries toTimeseries(){
		final TimeSeries ts = new TimeSeries(sName, Second.class);
		
		for (DataPoint dp: data)
			ts.addOrUpdate(new Second(new Date((long) dp.x)), dp.y);
		
		logger.log(Level.FINE, "Returning time series: "+sName+" : "+ts.getItemCount()+" points");
		
		return ts;
	}
	
	/**
	 * Get the array of Y values
	 * 
	 * @return array of Y values
	 */
	public double[] getDataArray(){
		final double[] vd = new double[data.size()];
		
		int iCnt = 0; 
		
		for (DataPoint dp: data)
			vd[iCnt++] = dp.y;
	
		return vd;
	}
	
	/**
	 * Get the data in a format easy to put in a DefaultXYDataset structure
	 * 
	 * @return an array of [2][data.size()]
	 */
	public double[][] getXYDataArray(){
		final double[][] vd = new double[2][data.size()];
		
		int iCnt = 0;
		
		for (DataPoint dp: data){
			vd[0][iCnt] = dp.x;
			vd[1][iCnt++] = dp.y;
		}
		
		return vd;
	}
	
	/**
	 * Get the minimum value on the X axis
	 * 
	 * @return minimum value on the X axis
	 */
	public Double getMinX(){
		return data.size()>0 ? new Double(data.get(0).x) : null;
	}
	
	/**
	 * Get the max value on the X axis
	 * 
	 * @return max value on the X axis
	 */
	public Double getMaxX(){
		return data.size()>0 ? new Double(data.get(data.size()-1).x) : null;
	}
	
	private int iLastIdx = -1;
	
	/**
	 * Get the Y value that is closest to the given X point
	 * 
	 * @param x
	 * @param dMaxDiff
	 * @return the Y value
	 */
	public Double getYValue(final double x, final double dMaxDiff){
		if (data.size()==0)
			return null;
		
		int i = 0;
		
		final int size = data.size();
		
		if (iLastIdx>=0 && iLastIdx<size && data.get(iLastIdx).x <= x){
			i = iLastIdx;
		}
		
		for (; i<size && data.get(i).x<x; i++){
			// everything is in the loop definition
		}
		
		boolean bLast = false;
		
		if (i==size){
			bLast = true;
			i--;
		}

		final double diff = Math.abs(data.get(i).x - x);
		
		if (i==0 || bLast){
			return diff <= dMaxDiff ? new Double(data.get(i).y) : null;
		}
		
		iLastIdx = i;
		
		// if it's somewhere in the middle the loop stopped because the X value is larger than what we search for
		final double diffPrev = Math.abs(data.get(i-1).x - x);
		
		if (diffPrev > dMaxDiff){
			return diff > dMaxDiff ? null : new Double(data.get(i).y);
		}
		else{
			if (diff > dMaxDiff)
				return new Double(data.get(i-1).y);
			else
				return new Double( (diff*data.get(i-1).y + diffPrev*data.get(i).y) / (diffPrev+diff) );
		}			
	}
	
	@Override
	public String toString(){
		return sName+":"+data.size();
	}
	
	/**
	 * Get a dump of the values
	 * 
	 * @return a dump of the values in this data set
	 */
	public String printValues(){
		final StringBuilder sb = new StringBuilder();
		
		sb.append("Data series: ").append(sName).append("\n");
		
		for (DataPoint dp: data){
			sb.append("  ").append(dp.x).append(": ").append(dp.y).append("\n");
		}
		
		return sb.toString();
	}
	
	/**
	 * Create a new Data set with the correlated values:<br>
	 * Xnew = Y from the current data set<br>
	 * Ynew = Y from the d2 data set, for the same X (+/- lCompactInterval apart)
	 * 
	 * @param d2
	 * @param dMaxDiff
	 * @return the correlated data set, good to be used in a scatter plot
	 */
	public Data getCorrelated(final Data d2, final double dMaxDiff){
		final Data d = new Data(sName + "-" + d2.sName);
		
		for (DataPoint dp: data){
			Double y = d2.getYValue(dp.x, dMaxDiff);
			
			if (y!=null)
				d.add(dp.y, y.doubleValue());
		}
		
		return d;
	}
	
	/**
	 * Create a new Data set, as the difference between the current set and d2.
	 * 
	 * @param d2
	 * @param dMaxDiff
	 * @return the difference data set
	 */
	public Data getDiff(final Data d2, final double dMaxDiff){
		final Data d = new Data(sName);
		
		for (DataPoint dp: data){
			Double y = d2.getYValue(dp.x, dMaxDiff);
			
			if (y!=null)
				d.add(dp.x, dp.y - y.doubleValue());
		}
		
		return d;
	}
	
	/**
	 * Create a sum series
	 * 
	 * @param dc
	 * @param dMaxDiff
	 * @return the sum series
	 */
	public Data getSum(final Collection<Data> dc, final double dMaxDiff){
		return getSumOrAvg(dc, dMaxDiff, false);
	}
	
	/**
	 * Create an average series
	 * 
	 * @param dc
	 * @param dMaxDiff
	 * @return the average series
	 */
	public Data getAvg(final Collection<Data> dc, final double dMaxDiff){
		return getSumOrAvg(dc, dMaxDiff, true);
	}
	
	private Data getSumOrAvg(final Collection<Data> dc, final double dMaxDiff, final boolean bAvg){
		if (dc==null || dc.size()==0)
			return this;
		
		final Data dRet = new Data(sName);

		double xMin = Double.MAX_VALUE;
		double xMax = 0;

		Double dMin = getMinX();
		
		if (dMin!=null){
			xMin = dMin.doubleValue();
			xMax = getMaxX().doubleValue();
		}
		
		for (Data d: dc){
			dMin = d.getMinX();
			
			if (dMin!=null){
				xMin = Math.min(xMin, dMin.doubleValue());
				xMax = Math.max(xMax, d.getMaxX().doubleValue());
			}
		}
		
		if (xMin > xMax){
			// quick exit because we have nothing to sum up actually
			return dRet;
		}
		
		for (; xMin<=xMax; xMin+=dMaxDiff){
			double dY = 0;
			int iCnt = 0;
			
			Double y = getYValue(xMin, dMaxDiff);
			
			if (y!=null){
				dY = y.doubleValue();
				iCnt = 1;
			}
			
			for (Data d: dc){
				y = d.getYValue(xMin, dMaxDiff);
				
				if (y!=null){
					dY += y.doubleValue();
					iCnt ++;
				}
			}
			
			if (iCnt>0){
				dRet.add(xMin, bAvg ? dY/iCnt : dY);
			}
		}
		
		return dRet;
	}
	
	/**
	 * Debug method
	 * 
	 * @param args
	 */
	public static void main(final String args[]){
		final Data d = new Data("something");
		
		d.add(1, 1);
		d.add(2, 2);
		d.add(4, 4);
		d.add(6, 6);
		d.add(9, 9);
		d.add(15, 15);
		
		final Data d2 = new Data("second series");
		
		d2.add(1.9, 5);
		d2.add(2, 7);
		d2.add(3, 1);
		d2.add(4, 2);
		d2.add(5, 3);
		d2.add(6, 4);
		d2.add(7, 5);
		d2.add(8, 1);
		d2.add(9.5, 2);
		
		final double dMaxDiff = 1;
		
		System.err.println("---------- Test getYValue ---------");
		
		System.err.println("-1 : "+d.getYValue(-1, dMaxDiff));
		System.err.println("0 : "+d.getYValue(0, dMaxDiff));
		System.err.println("0.5 : "+d.getYValue(0.5, dMaxDiff));
		System.err.println("1 : "+d.getYValue(1, dMaxDiff));
		System.err.println("1.8 : "+d.getYValue(1.8, dMaxDiff));
		System.err.println("2 : "+d.getYValue(2, dMaxDiff));
		System.err.println("2.1 : "+d.getYValue(2.1, dMaxDiff));
		System.err.println("2.9 : "+d.getYValue(2.9, dMaxDiff));
		System.err.println("3 : "+d.getYValue(3, dMaxDiff));
		System.err.println("3.1 : "+d.getYValue(3.1, dMaxDiff));
		System.err.println("4.9 : "+d.getYValue(4.9, dMaxDiff));
		System.err.println("5 : "+d.getYValue(5, dMaxDiff));
		System.err.println("7 : "+d.getYValue(7, dMaxDiff));
		System.err.println("8 : "+d.getYValue(8, dMaxDiff));
		System.err.println("9 : "+d.getYValue(9, dMaxDiff));
		System.err.println("10 : "+d.getYValue(10, dMaxDiff));
		System.err.println("11 : "+d.getYValue(11, dMaxDiff));
		System.err.println("12 : "+d.getYValue(12, dMaxDiff));
		System.err.println("13 : "+d.getYValue(13, dMaxDiff));
		System.err.println("14 : "+d.getYValue(14, dMaxDiff));
		System.err.println("15 : "+d.getYValue(15, dMaxDiff));
		System.err.println("16 : "+d.getYValue(16, dMaxDiff));
		System.err.println("17 : "+d.getYValue(17, dMaxDiff));

		System.err.println("---------- Values dump ----------");
		
		System.err.println(d.printValues());
		System.err.println(d2.printValues());
		
		System.err.println("---------- Test scatter ---------");
		
		
		final Data dScatter = d.getCorrelated(d2, dMaxDiff);
		
		System.err.println(dScatter.printValues());
		
		System.err.println("---------- Test diff ----------");
		
		final Data dDiff = d.getDiff(d2, dMaxDiff);
		
		System.err.println(dDiff.printValues());
		
		System.err.println("---------- Test add -----------");
		
		final ArrayList<Data> dc = new ArrayList<Data>(2);
		dc.add(d2);
		
		final Data dSum = d.getSum(dc, dMaxDiff);
		
		System.err.println(dSum.printValues());
	}
}
