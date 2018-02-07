package lia.web.bean.Generic;

import java.io.Serializable;

/**
 * Database writer
 */
public class WriterConfig implements Serializable, Comparable<WriterConfig> {

	/**
	 * Eclipse suggestion
	 */
	private static final long serialVersionUID = -6453500490687273923L;
	
	/**
	 * Total table time
	 */
	public int iTotalTime;
	
	/**
	 * Number of samples (for averaged values)
	 */
	public int iSamples;
	
	/**
	 * Base table name
	 */
	public String sTableName;
	
	/**
	 * Nice name to display instead of the above
	 */
	public String sDisplayName;
	
	/**
	 * Write mode
	 */
	public int iWriteMode;

	/**
	 * 
	 */
	public WriterConfig() {
		iTotalTime = 0;
		iSamples = 0;
		sTableName = null;
		sDisplayName = null;
		iWriteMode = 0;
	}

	/**
	 * @param _iTotalTime
	 * @param _iSamples
	 * @param _sTableName
	 * @param _sDisplayName
	 * @param _iWriteMode
	 */
	public WriterConfig(int _iTotalTime, int _iSamples, String _sTableName,
			String _sDisplayName, int _iWriteMode) {
		iTotalTime = _iTotalTime;
		iSamples = _iSamples;
		sTableName = _sTableName;
		sDisplayName = _sDisplayName;
		iWriteMode = _iWriteMode;
	}

	public int compareTo(WriterConfig wc) {
		if (iTotalTime > wc.iTotalTime)
			return 1;
		if (iTotalTime < wc.iTotalTime)
			return -1;

		if (iSamples > wc.iSamples)
			return 1;
		if (iSamples < wc.iSamples)
			return -1;

		if (iWriteMode == 3 || iWriteMode == 4)
			return 1; // in memory tables have priority
		if (iWriteMode < 2)
			return 1; // then small averaged tables

		return sTableName.compareTo(wc.sTableName); // when everything else
													// fails ... sort by name
													// ... :D
	}

}
