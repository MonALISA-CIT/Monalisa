/*
 * $Id: GenericUDPResult.java 6865 2010-10-10 10:03:16Z ramiro $
 */
package lia.Monitor.monitor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *
 * @author ramiro
 */
public class GenericUDPResult {

	public long rtime = 0;
	public final long senderID;
	public String clusterName;
	public String nodeName;
	public final List<Object> paramValues;
	// this was added to keep track of params order ...
	public final List<String> paramNames;

	public GenericUDPResult(final int initialSize, final Long srcID) {
		senderID = srcID != null ? srcID.longValue() : -1;
		paramValues = new ArrayList<Object>(initialSize);
		paramNames = new ArrayList<String>(initialSize);
	}

	public void addParam(final String name, final Object value) {
		paramNames.add(name);
		paramValues.add(value);
	}

	// Just for Debugging
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append(" [ ").append(new Date(rtime)).append(" ] ");
		sb.append("Cluster:").append(clusterName).append("\t");
		sb.append("Node:").append(nodeName).append("\t");

		if (paramValues != null && paramValues.size() > 0) {
			final int len = paramValues.size();
			for (int i = 0; i < len; i++)
				sb.append(paramNames.get(i)).append(" = ").append(paramValues.get(i)).append("\t");
		}
		else
			sb.append(" No PARAMS!");
		return sb.toString();
	}
}
