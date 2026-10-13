package lia.Monitor.modules;

import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.DataCache.Cache;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.util.DynamicThreadPoll.SchJob;
import lia.util.ntp.NTPDate;

/**
 * Result generator with random values to test transport capabilities of the monitoring framework
 *
 * @author costing
 * @since 2026-10-13
 */
public class monResultGenerator extends SchJob implements MonitoringModule {
	private static final long serialVersionUID = 1908364258237134523L;

	static final Logger logger = Logger.getLogger(monResultGenerator.class.getName());

	private MonModuleInfo mmi = null;

	MNode mn = null;

	private long lLastCall = 0;

	AtomicLong insertedResults = new AtomicLong();

	String rateForHumans = "1000";

	double targetResultRatePerMillisecond = 1;

	private final ScheduledExecutorService service = Executors.newScheduledThreadPool(1);

	@Override
	public MonModuleInfo init(final MNode node, final String args) {
		mn = node;

		mmi = new MonModuleInfo();
		mmi.setName("monResultGenerator");
		mmi.setState(0);

		lLastCall = NTPDate.currentTimeMillis();
		mmi.lastMeasurement = lLastCall;

		try {
			final double rate = Double.parseDouble(args != null && !args.isBlank() ? args : rateForHumans);

			logger.log(Level.INFO, "Initialized with a target rate of " + rate + " Hz");

			if (rate > 0) {
				rateForHumans = String.valueOf(rate) + "Hz";

				targetResultRatePerMillisecond = rate / 1000;
			}
		}
		catch (final Exception e) {
			logger.log(Level.WARNING, "Exception parsing the argument (`" + args + "`) to a rate, keeping a target of " + (targetResultRatePerMillisecond * 1000) + " Hz", e);
		}

		final Runnable task = new Runnable() {
			private long lastExecutedTask = System.currentTimeMillis();

			private final String[] params = new String[] { rateForHumans };

			@Override
			public void run() {
				try {
					final long now = System.currentTimeMillis();
					final long diff = now - lastExecutedTask;

					if (diff <= 0)
						return;

					final long results = (long) Math.floor(targetResultRatePerMillisecond * diff);

					if (results <= 0)
						return;

					final Vector<Result> v = new Vector<>((int) results);

					for (long i = 0; i < results; i++) {
						final Result r = new Result(getFarmName(), getClusterName(), mn.getName(), null, params, new double[] { ThreadLocalRandom.current().nextDouble() });

						r.time = now;

						v.add(r);
					}

					Cache.getInstance().notifyInternalResults(v);

					insertedResults.addAndGet(results);

					lastExecutedTask = now;
				}
				catch (final Exception e) {
					logger.log(Level.WARNING, "Exception running the task", e);
				}
			}
		};

		final long runOnceEvery = targetResultRatePerMillisecond >= 1 ? 1 : 10;

		service.scheduleWithFixedDelay(task, 10, runOnceEvery, TimeUnit.MILLISECONDS);

		return mmi;
	}

	// MonitoringModule

	@Override
	public String[] ResTypes() {
		return mmi.getResType();
	}

	@Override
	public String getOsName() {
		return "*";
	}

	@Override
	public Object doProcess() throws Exception {
		final long now = NTPDate.currentTimeMillis();

		if (now <= lLastCall)
			return null;

		final long diffMillis = now - lLastCall;
		final long results = insertedResults.getAndSet(0);

		lLastCall = now;

		final Result r = new Result();
		r.FarmName = getFarmName();
		r.ClusterName = getClusterName();
		r.NodeName = mn.getName();
		r.Module = mmi.getName();
		r.time = lLastCall;

		r.addSet("parameters_R", results * 1000d / diffMillis);

		final Vector<Object> vReturn = new Vector<>(1);
		vReturn.add(r);

		return vReturn;
	}

	@Override
	public MNode getNode() {
		return mn;
	}

	@Override
	public String getClusterName() {
		return mn.getClusterName();
	}

	@Override
	public String getFarmName() {
		return mn.getFarmName();
	}

	@Override
	public boolean isRepetitive() {
		return true;
	}

	@Override
	public String getTaskName() {
		return mmi.getName();
	}

	@Override
	public MonModuleInfo getInfo() {
		return mmi;
	}

	/**
	 * Test case for the module
	 *
	 * @param args
	 * @throws Exception
	 */
	public static void main(final String args[]) throws Exception {
		final String host = "localhost"; // args[0] ;
		final monResultGenerator aa = new monResultGenerator();
		aa.init(new MNode(host, "localhost", null, null), (args != null && args.length > 0) ? args[0] : "0.1");

		while (true) {
			final Object cb = aa.doProcess();

			System.out.println(cb.toString());

			Thread.sleep(1000 * 15);
		}
	}
}
