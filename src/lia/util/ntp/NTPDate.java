/*
 * $Id: NTPDate.java 7419 2013-10-16 12:56:15Z ramiro $
 */
package lia.util.ntp;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;

/**
 * 
 * Constructs a Date object sync'ed with the time servers and having GMT time
 * 
 * @author Costin Grigoras <Costin.Grigoras@cern.ch>
 */
public class NTPDate extends Date {

	/**
	 * Eclipse suggestion :)
	 */
	private static final long serialVersionUID = 8363930011000237062L;

	private static final Logger logger = Logger.getLogger(NTPDate.class.getName());

	private static final AtomicLong lNTPOffset = new AtomicLong(0L);

	private static volatile AtomicLong lLastSynchronizationTime = new AtomicLong(0L);

	private static volatile boolean bFirstRunComplete = false;

	private static final long SECOND = 1000;

	private static final long MINUTE = 60 * SECOND;

	private static final long HOUR = 60 * MINUTE;

	public static final boolean isSynchronized() {
		// NTP is assumed to be synchronized if the last successful check was at most 1/2 an hour ago
		return TimeUnit.NANOSECONDS.toMinutes(System.nanoTime() - lLastSynchronizationTime.get()) < 30;
	}

	private static class NTPThread extends Thread {

		private long lastTimeLogged;

		private boolean stopped = false;

		private Thread ownThread = null;

		private static final Object lock = new Object();

		public NTPThread() {
			super(" (ML) NTPThread");
			lastTimeLogged = 0;
			stopped = false;
		}

		public void stopIt() {

			stopped = true;
			synchronized (lock) {
				while (ownThread == null) {
					try {
						lock.wait();
					}
					catch (Exception ex) {
					}
				}
			}
			try {
				this.interrupt();
			}
			catch (Exception ex) {
			}
		}

		@Override
		public void run() {
			logger.log(Level.INFO, "[ NTPDate ] thread started. Waiting for initial sync to finish ...");
			synchronized (lock) {
				ownThread = this;
				lock.notifyAll();
			}

			while (!stopped) {
				final boolean bFirstRunComplete = NTPDate.bFirstRunComplete;
				long lsleep = 10 * SECOND;
				boolean bOK = false;

				try {
					final NTPClient cl = new NTPClient();
					final long temp = cl.NTPConnect();

					if (cl.NTPok()) {
						bOK = true;
						final long lOld = lNTPOffset.get();

						final long diff = Math.abs(lOld - temp);

						if (bFirstRunComplete && (diff > (10 * SECOND))) {
							// if the time difference is too big then go in that direction in small steps

							int factor = 10;

							if (diff > (24 * HOUR)) {
								logger.log(Level.WARNING,
										"Something is wrong with the time on this machine. Current time : "
												+ new Date() + " (" + System.currentTimeMillis()
												+ "), old NTP offset : " + lNTPOffset + ", new NTP offset : " + temp
												+ ", valid responses : " + cl.getValidServersCount());
								factor = 10000;
							}
							else
								if (diff > (1 * HOUR)) {
									factor = 1000;
								}
								else
									if (diff > (5 * MINUTE)) {
										factor = 100;
									}

							lNTPOffset.set((long) (((lNTPOffset.get() * (double) (factor - 1)) + temp) / factor));
						}
						else {
							lNTPOffset.set(temp);
						}

						lLastSynchronizationTime.set(System.nanoTime());

						long lDiff = Math.abs(lNTPOffset.get() - lOld);

						if (lDiff < 15) {
							lsleep = 15 * MINUTE;
						}
						else
							if (lDiff < 50) {
								lsleep = 10 * MINUTE;
							}
							else
								if (lDiff < 100) {
									lsleep = 5 * MINUTE;
								}
								else
									if (lDiff < 250) {
										lsleep = 2 * MINUTE;
									}
									else
										if (lDiff < 1000) {
											lsleep = 1 * MINUTE;
										}
										else {
											lsleep = 10 * SECOND;
										}

						if (bFirstRunComplete) {
							final long now = NTPDate.currentTimeMillis();
							if ((lastTimeLogged + (12 * HOUR)) < now) {
								logger.log(Level.INFO,
										"recalculated offset is : " + lNTPOffset + " ms, " + cl.getValidServersCount()
												+ " NTP replies, now=" + new Date(now) + ", next NTP sync in "
												+ (lsleep / SECOND) + " sec");
								lastTimeLogged = now;
							}
							else
								if (logger.isLoggable(Level.FINE)) {
									logger.log(Level.FINE,
											"recalculated offset is : " + lNTPOffset + " ms, " + cl.getValidServersCount()
													+ " NTP replies, now=" + new Date(now) + ", next NTP sync in "
													+ (lsleep / SECOND) + " sec");
								}
						}
					}
					else {
						logger.log(Level.WARNING, "Cannot determine the offset, falling back to the previous value ("
								+ lNTPOffset + ").");
					}

				}
				catch (Throwable t) {
					logger.log(Level.WARNING, "NTPThread got exc", t);
				}
				finally {
					if (!bFirstRunComplete) {
						logger.log(Level.INFO, "[ NTPDate ] Initial NTP sync finished. Status: "
								+ ((bOK) ? "OK" : "Unable to sync."));
						NTPDate.bFirstRunComplete = true;
					}

					// sleep whatever happens, you'll get tired soon
					try {
						if ((lsleep <= 0) || (TimeUnit.MILLISECONDS.toMinutes(lsleep) < 2)) {
							lsleep = TimeUnit.MINUTES.toMillis(2);
						}
						sleep(lsleep);
					}
					catch (InterruptedException ie) {
						// otherwise the socket will be closed unexpectedly
						Thread.interrupted();
					}
					catch (Throwable tsleep) {
						logger.log(Level.WARNING, "NTPThread sleep() exception. Cause", tsleep);
					}
				}
			}
		}

	}

	static volatile Thread ntpt = null;

	static {
		boolean bStartNTP = true;

		try {
			bStartNTP = Boolean.valueOf(AppConfig.getProperty("lia.util.ntp.enabled", "true")).booleanValue();
		}
		catch (Throwable t) {
		}

		lNTPOffset.set(0L);
		if (bStartNTP) {
			logger.log(Level.CONFIG,
					"Using NTP to calculate time. To disable it you can set lia.util.ntp.enabled=false (not recommended).");
			bFirstRunComplete = false;
			startThread();
		}
		else {
			logger.log(Level.CONFIG,
					"NTP is disabled. We recommend setting lia.util.ntp.enabled=true unless there is a firewall in place.");
			bFirstRunComplete = true;
		}
	}

	public static void startThread() {
		if (ntpt != null) {
			stopThread();
		}

		ntpt = new NTPThread();
		ntpt.setDaemon(true);
		ntpt.start();
	}

	public static void stopThread() {
		try {
			if (ntpt != null)
				((NTPThread) ntpt).stopIt();
		}
		catch (Exception e) {
		}

		ntpt = null;
	}

	public static final long ntpOffset() {
		while (!bFirstRunComplete) {
			try {
				Thread.sleep(10);
			}
			catch (Exception e) {
			}
		}

		return lNTPOffset.get();
	}

	/**
	 * Set the time offset to the correct clock
	 * 
	 * @param delta difference in milliseconds to apply to the <code>System.currentTimeMillis()</code>
	 * @return the previous value of the offset
	 */
	public static final long setNTPOffset(final long delta) {
		stopThread();
		bFirstRunComplete = true;
		return lNTPOffset.getAndSet(delta);
	}

	public NTPDate() { // compensate for the GMT offset, return the new date in GMT time
		// make sure to call ntpOffset() BEFORE System.currentTimeMillis !!!!
		super(currentTimeMillis());
	}

	public static final long currentTimeMillis() {
		return ntpOffset() + System.currentTimeMillis();
	}

	@Override
	public String toString() {
		DateFormat formatter = null;

		formatter = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US);
		formatter.setTimeZone(TimeZone.getTimeZone("GMT"));

		return formatter.format(this);
	}

	public NTPDate(long l) {
		super(ntpOffset() + l);
	}

}
