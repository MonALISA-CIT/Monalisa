package lia.Monitor.monitor;

import java.io.ObjectInputFilter;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @since 2026-06-08
 */
public class MLObjectFilter implements ObjectInputFilter {

	static final Set<String> seenClasses = new TreeSet<>();

	private static final Logger logger = Logger.getLogger(MLObjectFilter.class.getName());

	static final Set<String> ALLOWED_CLASS_NAMES = Set.of("[B", "[D",
			"[Ljava.lang.Object;", "[Ljava.lang.String;", "[Ljava.util.Map$Entry;", "[Lnet.jini.core.entry.Entry;", "[Llia.Monitor.monitor.MFarm;",
			"java.lang.Double", "java.lang.Integer", "java.lang.Number", "java.util.Hashtable", "java.util.Vector",
			"lia.Monitor.JiniSerFarmMon.NoImplProxy",
			"lia.Monitor.monitor.ABPingEntry", "lia.Monitor.monitor.EMsg", "lia.Monitor.monitor.ExtendedSiteInfoEntry", "lia.Monitor.monitor.GenericMLEntry", "lia.Monitor.monitor.MCluster",
			"lia.Monitor.monitor.MFarm", "lia.Monitor.monitor.MLControlEntry", "lia.Monitor.monitor.MNode", "lia.Monitor.monitor.MonMessageClientsProxy",
			"lia.Monitor.monitor.MonaLisaEntry", "lia.Monitor.monitor.Result", "lia.Monitor.monitor.SiteInfoEntry",
			"lia.Monitor.monitor.cmonMessage", "lia.Monitor.monitor.eResult", "lia.Monitor.monitor.monMessage", "lia.Monitor.monitor.monPredicate",
			"lia.util.UUID",
			"net.jini.core.lookup.ServiceID", "net.jini.core.lookup.ServiceItem", "net.jini.entry.AbstractEntry", "net.jini.lookup.entry.Name");

	@Override
	public Status checkInput(FilterInfo filterInfo) {
		final String name = filterInfo.serialClass() != null ? filterInfo.serialClass().getName() : null;

		if (name == null)
			return Status.UNDECIDED;

		if (ALLOWED_CLASS_NAMES.contains(name))
			return Status.ALLOWED;

		logger.log(Level.WARNING, "Unrecognized class name: " + name);
		return Status.REJECTED;
	}
}
