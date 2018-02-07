/**
 * 
 */
package lia.web.servlets.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lia.web.utils.DoubleFormat;
import lia.web.utils.Page;

/**
 * @author costing
 * @since Sep 12, 2007
 */
public class Statistics {

	private LinkedHashMap<String, List<Data>> seriesMapping;
	
	/**
	 * Simple constructor
	 */
	public Statistics(){
		seriesMapping = new LinkedHashMap<String, List<Data>>();
	}

	/**
	 * Add one data series
	 * 
	 * @param sChart
	 * @param d
	 */
	public void addData(final String sChart, final Data d){
		List<Data> chart = seriesMapping.get(sChart);
		
		if (chart==null){
			chart = new ArrayList<Data>();
			seriesMapping.put(sChart, chart);
		}
		
		chart.add(d);
	}
	
	/**
	 * Dump the statistics in the correlations page
	 * 
	 * @param p
	 * @param sResDir
	 */
	public void write(final Page p, final String sResDir){
		final Page pChart = new Page(sResDir+"correlations/statistics.res");
		final Page pLine = new Page(sResDir+"correlations/statistics_line.res");
		
		for (Map.Entry<String, List<Data>> chartEntry: seriesMapping.entrySet()){
			pChart.modify("name", chartEntry.getKey());
			
			for (Data d: chartEntry.getValue()){
				pLine.modify("name", d.sName);
				// ......
				
				final double[] data = d.getDataArray();
				double dMin = 1;
				double dMax = -1;
				
				double dAvg = 0;
				
				for (double value: data){
					if (dMin > dMax){
						dMin = dMax = value;
					}
					else{
						if (dMin>value) dMin = value;
						if (dMax<value) dMax = value;
					}
					
					dAvg += value;
				}
				
				dAvg /= data.length;
				
				pLine.modify("min", DoubleFormat.point(dMin));
				pLine.modify("max", DoubleFormat.point(dMax));
				pLine.modify("avg", DoubleFormat.point(dAvg));
				
				// ......
				pChart.append(pLine);
			}
			
			p.append("statistics", pChart);
		}
	}
}
