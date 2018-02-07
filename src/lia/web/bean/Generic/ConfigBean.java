package lia.web.bean.Generic;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import lia.Monitor.monitor.AppConfig;

/**
 *
 */
public class ConfigBean implements Serializable {

	/**
	 * Eclipse suggestion
	 */
	private static final long serialVersionUID = -2573711835497267684L;
	private static List<WriterConfig> lConfig = new LinkedList<WriterConfig>();

	static {
		int nr = Integer.parseInt(AppConfig.getProperty(
				"lia.Monitor.Store.TransparentStoreFast.web_writes", "0"));

		for (int i = 0; i < nr; i++) {
			try {
				WriterConfig wc = new WriterConfig(Integer.parseInt(AppConfig
						.getProperty(
								"lia.Monitor.Store.TransparentStoreFast.writer_"
										+ i + ".total_time", "0")), Integer
						.parseInt(AppConfig.getProperty(
								"lia.Monitor.Store.TransparentStoreFast.writer_"
										+ i + ".samples", "0")), AppConfig
						.getProperty(
								"lia.Monitor.Store.TransparentStoreFast.writer_"
										+ i + ".table_name", ""), AppConfig
						.getProperty(
								"lia.Monitor.Store.TransparentStoreFast.writer_"
										+ i + ".descr", ""), Integer
						.parseInt(AppConfig.getProperty(
								"lia.Monitor.Store.TransparentStoreFast.writer_"
										+ i + ".writemode", "0")));

				if (wc.iTotalTime > 0)
					lConfig.add(wc);
			} catch (NumberFormatException e) {
				// ignore
			}
		}

		Collections.sort(lConfig);
	}

	/**
	 * @return all writers
	 */
	public static final List<WriterConfig> getConfig() {
		return lConfig;
	}

	/**
	 * @param sTable
	 * @return the object corresponding to this table
	 */
	public static final WriterConfig getConfig(final String sTable) {
		if (sTable == null)
			return null;

		for (int i = 0; i < lConfig.size(); i++) {
			final WriterConfig wc = lConfig.get(i);

			if (wc != null && wc.sTableName != null
					&& wc.sTableName.equals(sTable))
				return wc;
		}

		return null;
	}

}
