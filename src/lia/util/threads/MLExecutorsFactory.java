/*
 * Created on Aug 27, 2007
 */
package lia.util.threads;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;

/**
 * Helper class which accepts different style of executors
 * It may be configured using AppConfig ...
 *
 * @author ramiro
 */
public final class MLExecutorsFactory {

	/** K: the executor name, V: the executor itself */
	private static final Map<String, MLScheduledThreadPoolExecutor> executorsMap = new TreeMap<String, MLScheduledThreadPoolExecutor>();

	private static final Map<String, ThreadPoolExecutor> cachedExecutorsMap = new TreeMap<String, ThreadPoolExecutor>();

	/**
	 * The default number of core threads for scheduled executors.
	 */
	public static final int DEFAULT_CORE_THREADS_COUNT = 10;

	/**
	 * The default number of maximum threads in the cached thread pools
	 */
	public static final int DEFAULT_MAX_THREADS_COUNT = 50;

	/**
	 * Default timeout for both core and extra threads in both scheduled executors and cached thread pools
	 */
	public static final long DEFAULT_TIMEOUT_MINUTES = 1;

	static {
		reloadExecutorsParam();

		AppConfig.addNotifier(new AppConfigChangeListener() {

			@Override
			public void notifyAppConfigChanged() {
				reloadExecutorsParam();
			}

		});
	}

	private static final void reloadExecutorsParam() {
		synchronized (executorsMap) {
			for (final Map.Entry<String, MLScheduledThreadPoolExecutor> entry : executorsMap.entrySet()) {
				final String executorName = entry.getKey();
				final MLScheduledThreadPoolExecutor executor = entry.getValue();

				final int coreThreads = AppConfig.geti(executorName + ".CORE_POOL_THREADS_COUNT", -1);

				// set it only if explicitly defined in the configuration
				if (coreThreads > 0)
					executor.setCorePoolSize(coreThreads);

				final long timeout = AppConfig.getl(executorName + ".TIMEOUT", -1);

				if (timeout >= 1)
					executor.setKeepAliveTime(timeout, TimeUnit.MINUTES);
			}
		}

		synchronized (cachedExecutorsMap) {
			for (final Map.Entry<String, ThreadPoolExecutor> entry : cachedExecutorsMap.entrySet()) {
				final String executorName = entry.getKey();

				final ThreadPoolExecutor executor = entry.getValue();

				final int coreThreads = AppConfig.geti(executorName + ".CORE_POOL_THREADS_COUNT", -1);

				if (coreThreads > 0)
					executor.setCorePoolSize(coreThreads);

				final int maxThreads = AppConfig.geti(executorName + ".MAX_POOL_THREADS_COUNT", -1);

				if (maxThreads > 0 && maxThreads >= coreThreads)
					executor.setMaximumPoolSize(maxThreads);

				final long timeout = AppConfig.getl(executorName + ".TIMEOUT", -1);

				if (timeout >= 1)
					executor.setKeepAliveTime(timeout, TimeUnit.MINUTES);
			}
		}
	}

	static final class RejectingQueue extends LinkedBlockingQueue<Runnable> {
		private static final long serialVersionUID = 1L;

		@Override
		public boolean offer(final Runnable r) {
			if (size() <= 1)
				return super.offer(r);

			return false;
		}
	}

	static final class MyHandler implements RejectedExecutionHandler {
		@Override
		public void rejectedExecution(final Runnable r, final ThreadPoolExecutor executor) {
			try {
				executor.getQueue().put(r);
			} catch (@SuppressWarnings("unused") final InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}

		}
	}

	/**
	 * @param name
	 * @return the fully cached thread pool for this name
	 */
	public static final ThreadPoolExecutor getCachedThreadPool(final String name) {
		return getCachedThreadPool(name, 0, DEFAULT_MAX_THREADS_COUNT, DEFAULT_TIMEOUT_MINUTES);
	}

	/**
	 * @param name
	 * @param defaultCoreThreads
	 * @param defaultMaxThreads
	 * @param defaultTimeout
	 * @return a fully cached executor thread
	 */
	public static final ThreadPoolExecutor getCachedThreadPool(final String name, final int defaultCoreThreads, final int defaultMaxThreads, final long defaultTimeout) {
		ThreadPoolExecutor executor = null;

		synchronized (cachedExecutorsMap) {
			executor = cachedExecutorsMap.get(name);

			if (executor == null) {
				int coreThreads = AppConfig.geti(name + ".CORE_POOL_THREADS_COUNT", defaultCoreThreads < 0 ? DEFAULT_CORE_THREADS_COUNT : defaultCoreThreads);
				if (coreThreads < 0)
					coreThreads = defaultCoreThreads;

				int maxThreads = AppConfig.geti(name + ".MAX_POOL_THREADS_COUNT", Math.max(coreThreads, defaultMaxThreads <= 0 ? DEFAULT_MAX_THREADS_COUNT : defaultMaxThreads));

				if (maxThreads < coreThreads)
					maxThreads = coreThreads;

				if (maxThreads <= 0)
					maxThreads = DEFAULT_MAX_THREADS_COUNT;

				long timeout = AppConfig.getl(name + ".TIMEOUT", defaultTimeout < 1 ? DEFAULT_TIMEOUT_MINUTES : defaultTimeout);

				if (timeout < 1)
					timeout = DEFAULT_TIMEOUT_MINUTES;

				executor = new ThreadPoolExecutor(coreThreads, maxThreads, timeout, TimeUnit.MINUTES, new RejectingQueue(), new MLThreadFactory(name), new MyHandler());
				executor.allowCoreThreadTimeOut(true);

				cachedExecutorsMap.put(name, executor);
			}
		}

		return executor;
	}

	public static final MLScheduledThreadPoolExecutor getScheduledExecutorService(final String name, final int defaultCoreThreads, final int defaultMaxThreads, final long defaultTimeout)
			throws Exception {
		MLScheduledThreadPoolExecutor executor = null;

		synchronized (executorsMap) {
			executor = executorsMap.get(name);
			if (executor == null) {
				int coreThreads = AppConfig.geti(name + ".CORE_POOL_THREADS_COUNT", defaultCoreThreads < 0 ? DEFAULT_CORE_THREADS_COUNT : defaultCoreThreads);
				if (coreThreads < 0)
					coreThreads = defaultCoreThreads;

				long timeout = AppConfig.getl(name + ".TIMEOUT", defaultTimeout < 1 ? DEFAULT_TIMEOUT_MINUTES : defaultTimeout);

				if (timeout < 1)
					timeout = DEFAULT_TIMEOUT_MINUTES;

				executor = new MLScheduledThreadPoolExecutor(name, coreThreads, timeout, TimeUnit.MINUTES);

				executor.setKeepAliveTime(timeout, TimeUnit.MINUTES);

				executorsMap.put(name, executor);
			} // if
		}

		return executor;
	}

	public static final ScheduledExecutorService getScheduledExecutorService(final String name) throws Exception {
		return getScheduledExecutorService(name, DEFAULT_CORE_THREADS_COUNT, DEFAULT_MAX_THREADS_COUNT, DEFAULT_TIMEOUT_MINUTES);
	}

	public static Map<String, MLScheduledThreadPoolExecutor> getExecutors() {
		synchronized (executorsMap) {
			return new HashMap<String, MLScheduledThreadPoolExecutor>(executorsMap);
		}
	}

	private static final class SafeRunnable implements Runnable {
		private final String name;
		private final Logger logger;
		private final Runnable command;

		/**
		 * @param command 
		 * @param name
		 * @param logger
		 */
		public SafeRunnable(final Runnable command, final String name, final Logger logger) {
			this.name = name;
			this.logger = logger;
			this.command = command;
		}

		@Override
		public void run() {
			try {
				command.run();
			} catch (final Throwable t) {
				if (logger != null)
					logger.log(Level.WARNING, " [ HANDLED ] ThPool worker " + name + " got exception ", t);
			}
		}

	}

	/**
	 * @param command
	 * @param name
	 * @param logger
	 * @return
	 */
	public static final Runnable safeRunnable(final Runnable command, final String name, final Logger logger) {
		return new SafeRunnable(command, name, logger);
	}
}
