package lia.Monitor.JiniClient.CommonGUI.OSG;

import java.util.Date;

import plot.math.MLMathFactory;
import plot.math.MLSeries;

public class OSGComputer {

	MLMathFactory mmf;
	long currentTime;
	
	public OSGComputer(){
		mmf = new MLMathFactory();
		currentTime = (new Date()).getTime();
	}
	
	public MLSeries getRestrictedTimeSeries(MLSeries series, long timeInterval){
		currentTime = (new Date()).getTime();
		if(series == null || series.size() == 0) return null;
		MLSeries s = new MLSeries(series.name());
		
		try{	
			for (int i = series.size()-1; i>=0; i--){
				long stime = ((Long)series.getXValue(i)).longValue();
				if( Math.abs(currentTime - stime) <= timeInterval ){
					s.add(stime, ((Double)series.getYValue(i)).doubleValue());
				}
				else break;
			}
		} catch(NullPointerException e){};
		return s;
	}
	
	public double getOSGIntegral(MLSeries series, int baseInterval, long timeInterval){
		MLSeries restrictedSeries = getRestrictedTimeSeries(series, timeInterval);
		if(restrictedSeries == null)
			return 0.0;
		return mmf.getIntegral(restrictedSeries, baseInterval);
	}
	
	public double getOSGMean(MLSeries series, long timeInterval){
		MLSeries restrictedSeries = getRestrictedTimeSeries(series, timeInterval);
		if(restrictedSeries == null)
			return 0.0;
		return mmf.getMean(restrictedSeries);
	}
	
	public void updateSeries(MLSeries series, long timeInterval){
		currentTime = (new Date()).getTime();
		try{
			for (int i = 0; i < series.size()-1; i++){
				long stime = ((Long)series.getXValue(i)).longValue();
				if(Math.abs(currentTime - stime)>timeInterval){
					series.data().remove(Long.valueOf(stime));
				}
				else break;
			}
		}catch(NullPointerException e){};
		
	}
	
}
