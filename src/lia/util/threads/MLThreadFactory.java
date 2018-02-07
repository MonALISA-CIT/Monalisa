/*
 * Created on Feb 2, 2011
 */
package lia.util.threads;

import java.util.Date;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * It just sets the name of the threads and makes them daemon threads
 * 
 * @author ramiro
 */
public final class MLThreadFactory implements ThreadFactory {

	private final AtomicLong SEQ = new AtomicLong(1);

	private final String name;

	MLThreadFactory(final String name) {
		if (name.startsWith("lia.")) {
			this.name = name.substring("lia.".length());
		}
		else {
			this.name = name;
		}
	}

	@Override
	public Thread newThread(Runnable r) {
		final Thread t = new Thread(r, "(ML ThP) [ " + name + " ] Worker " + SEQ.getAndIncrement() + ", started: " + new Date());
		t.setDaemon(true);
		return t;
	}
}