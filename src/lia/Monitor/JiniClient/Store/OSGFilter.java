package lia.Monitor.JiniClient.Store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import lia.Monitor.monitor.AppConfig;

/**
 * @author costing
 *
 */
public final class OSGFilter extends GenericAgregatorFilter {

	private static final HashMap<String, List<String>>	INTERESTING_DATA	= new HashMap<String, List<String>>();
	private static final HashSet<String>  IGNORE_NODES;
	
	static {
		INTERESTING_DATA.put("osgVO_JOBS", new ArrayList<String>());
		
		if (AppConfig.getb("osg_filter.ignore_no_vo", false)){
			IGNORE_NODES = new HashSet<String>();
			IGNORE_NODES.add("NO_VO");
		}
		else{
			IGNORE_NODES = null;
		}
	}

	private static final long		FLUSH_TIME			= 3 * 60 * 1000;	// 3 minutes rates

	private static final String		TOTALS				= "_TOTALS_";

	@Override
	public Map<String, Pattern> getRegexClusters() {
		return null;
	}

	@Override
	public Map<String, List<String>> getInterestingData() {
		return INTERESTING_DATA;
	}

	@Override
	public String getTotalsName() {
		return TOTALS;
	}

	@Override
	public long getRunInterval() {
		return FLUSH_TIME;
	}

	@Override
	protected boolean getProduceTotalsPerFarms() {
		return true;
	}

	@Override
	protected int getHistoryNonZeroSkip(){
		// ignore up to 2 zero values that come after a non-zero one (6 minutes)
		return 2;
	}
	
	@Override
	protected boolean getFillGaps() {
		// fill intervals up to getHistoryNonZeroSkip()x flush time with the last known non-zero value
		return true;
	}

	@Override
	public String getParameterPrefix(){
		return "T";
	}
	
	@Override
	protected Set<String> getIgnoreNodes(){
		return IGNORE_NODES;
	}
}
