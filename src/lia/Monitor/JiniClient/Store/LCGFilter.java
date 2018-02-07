package lia.Monitor.JiniClient.Store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author costing
 *
 */
public final class LCGFilter extends GenericAgregatorFilter {

	private static final HashMap<String, List<String>> INTERESTING_DATA = new HashMap<String, List<String>>();
	static {
		INTERESTING_DATA.put("LcgVO_IO_SE", new ArrayList<String>());

		INTERESTING_DATA.put("LcgVO_JOBS_CE", new ArrayList<String>());

		INTERESTING_DATA.put("LcgVO_JOBS_CE_Rates", new ArrayList<String>());
	}

	private static final long FLUSH_TIME = 3 * 60 * 1000; // 3 minutes rates

	private static final String TOTALS = "_TOTALS_";

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

}
