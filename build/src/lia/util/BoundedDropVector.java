package lia.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Vector;

import lia.Monitor.monitor.AppConfig;

/**
 *
 * This class should provide a bounded-size helper class for java.util.Vector.
 * It allows a maximum size of elements,  
 */
public class BoundedDropVector extends Vector<Object> {
	private static final long	serialVersionUID	= -3265833119504859375L;

	/**
	 * default size in case nothing else specified
	 */
	public static final int		MAX_SIZE;

	public static final boolean	USE_ONLY_MAX_SIZE;

	private transient DropEvent	de					= null;

	/**
	 * Only maxSize elements...
	 */
	private int					maxSize;

	static {
		int maxsize;

		try {
			maxsize = Integer.valueOf(AppConfig.getProperty("lia.util.BoundedDropVector.MAX_SIZE", "50000")).intValue();
		} catch (Throwable t) {
			maxsize = 500;
		}

		MAX_SIZE = maxsize;

		boolean only;

		try {
			only = Boolean.valueOf(AppConfig.getProperty("lia.util.BoundedDropVector.USE_ONLY_MAX_SIZE", "false")).booleanValue();
		} catch (Throwable t) {
			only = false;
		}

		USE_ONLY_MAX_SIZE = only;
	}

	public BoundedDropVector() {
		this(MAX_SIZE, null);
	}

	public BoundedDropVector(final int iMaxSize) {
		this(iMaxSize, null);
	}

	public BoundedDropVector(final int iMaxSize, final DropEvent dropEvent) {
		if (USE_ONLY_MAX_SIZE || iMaxSize <= 0) {
			this.maxSize = MAX_SIZE;
		}
		else {
			this.maxSize = iMaxSize;
		}

		this.de = dropEvent;
	}

	public synchronized boolean add(final Object o) {
		if (size() > maxSize) {
			if (de != null)
				de.notifyDrop();
			
			return false;
		}
		
		return super.add(o);
	}

	public synchronized boolean addAll(final int index, final Collection<?> c) {
		if (c.size() + size() <= maxSize) {
			return super.addAll(index, c);
		}

		final Object[] a = c.toArray();
		final Object nv[] = new Object[maxSize - size()];
			
		System.arraycopy(a, 0, nv, 0, nv.length);
			
		if (de != null)
			de.notifyDrop();
			
		return addAll(index, Arrays.asList(nv));
	}

	public synchronized boolean addAll(final Collection<?> c) {
		if (c.size() + size() <= maxSize) {
			return super.addAll(c);
		}
		
		final Object[] a = c.toArray();
		final Object nv[] = new Object[maxSize - size()];
			
		System.arraycopy(a, 0, nv, 0, nv.length);
			
		if (de != null)
			de.notifyDrop();
			
		return addAll(Arrays.asList(nv));
	}

	public synchronized void addElement(final Object obj) {
		if (size() > maxSize) {
			if (de != null)
				de.notifyDrop();
			return;
		}
		
		super.addElement(obj);
	}

	public int maxSize() {
		return maxSize;
	}

}
