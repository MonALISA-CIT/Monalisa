/*
 * Created on Aug 13, 2007
 * 
 * $Id: SortedVector.java 6865 2010-10-10 10:03:16Z ramiro $
 *
 */
package lia.Monitor.monitor;

import java.io.ObjectStreamException;
import java.io.StreamCorruptedException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Vector;

/**
 * 
 * Helper class to keep ML configuration (MFarm/MCluster/MNode) classes sorted
 * It can be considered a Set ... no duplicates allowed
 * 
 * @author ramiro
 * 
 */
public class SortedVector<E> extends Vector<E> {


    private static final long serialVersionUID = -7825320207264922110L;

    private Comparator<?> comparator;

    private final SortedVectorNotifier notifier;
    
    public SortedVector() {
        this(null, null, null);
    }

    public SortedVector(SortedVectorNotifier notifier) {
        this(null, null, notifier);
    }
    
    public SortedVector(Comparator<? extends E> comparator) {
        this(null, comparator, null);
    }

    public SortedVector(Comparator<? extends E> comparator, SortedVectorNotifier notifier) {
        this(null, comparator, notifier);
    }

    public SortedVector(Collection<? extends E> c) {
        this(c, null, null);
    }

    public SortedVector(Collection<? extends E> c, SortedVectorNotifier notifier) {
        this(c, null, notifier);
    }

    public SortedVector(Collection<? extends E> c, Comparator<? extends E> comparator, SortedVectorNotifier notifier) {
        super(10, 10);
        this.notifier = notifier;
        this.comparator = comparator;
        if(c == null) return;
        addAll(c);
    }

    public void add(int index, E element) {
        add(element);
    }

    public synchronized boolean add(E e) {
        final int idx = indexOf(e);
        if(idx < 0) {
            //ADD ok
            super.insertElementAt(e, -idx-1);
            if(notifier != null) {
                notifier.elementAdded(e);
            }
            return true;
        }
        return false;
    }

    public synchronized E addIfAbsent(final E e) {
        final int idx = indexOf(e);
        if(idx < 0) {
            //ADD ok
            super.insertElementAt(e, -idx-1);
            if(notifier != null) {
                notifier.elementAdded(e);
            }
            return e;
        }
        return get(idx);
    }
    
    public void addElement(E obj) {
        add(obj);
    }

    @Override
    public synchronized int indexOf(Object o, int index) {
        return internalBinarySearch(elementData, index, elementCount, o, comparator);
    }

    @Override
    public boolean contains(Object o) {
        // TODO Auto-generated method stub
        return (indexOf(o) >= 0);
    }

    @Override
    public int indexOf(Object o) {
        // TODO Auto-generated method stub
        return indexOf(o, 0);
    }

    public void insertElementAt(E obj, int index) {
        add(obj);
    }

    public int lastIndexOf(Object o) {
        return lastIndexOf(o, elementCount);
    }

    public synchronized int lastIndexOf(Object o, int index) {
        return internalBinarySearch(elementData, 0, index, o, comparator);
    }

    public synchronized boolean remove(Object o) {
        final int idx = indexOf(o);
        if(idx < 0) {
            return false;
        }
        return (remove(idx) != null);
    }

    public synchronized E set(int index, E element) {
        final E oldElement = get(index);
        add(element);
        return oldElement;
    }

    @Override
    public synchronized boolean addAll(Collection<? extends E> c) {
        boolean ret = true;
        for(Iterator<? extends E> it = c.iterator(); it.hasNext();) {
            ret = ( ret && this.add(it.next()) );
        }
        return ret;
    }

    public synchronized boolean addAll(int index, Collection<? extends E> c) {
        return addAll(c);
    }

    public synchronized void setElementAt(E obj, int index) {
        final int idx = indexOf(obj);
        if(idx == index) {
            super.setElementAt(obj, index);
        }
    }

    public boolean removeElement(Object obj) {
        return remove(obj);
    }


    public void clear() {
        removeAllElements();
    }

    @Override
    public synchronized E remove(int index) {
        final E o = super.remove(index);
        if(o != null && notifier != null) {
            notifier.elementRemoved(o);
        }
        return o;
    }

    @Override
    public synchronized boolean removeAll(Collection<?> c) {
        boolean ret = true;
        for(final Iterator<?> i = c.iterator(); i.hasNext();) {
            ret = ( ret && remove(i.next()) );
        }
        
        return ret;
    }

    public synchronized void removeAllElements() {
        for(int i = elementCount - 1; i>=0; i--) {
            remove(i);
        }
    }

    public synchronized void removeElementAt(int index) {
        remove(index);
    }

    /**
     * Copied from Java6 .... not found in Java4/5
     * @param a
     * @param fromIndex
     * @param toIndex
     * @param key
     * @return
     */
    private static <T> int internalBinarySearch(T[] a, int fromIndex, int toIndex, T key) {
        int low = fromIndex;
        int high = toIndex - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
        Comparable<T> midVal = (Comparable<T>)a[mid];
        int cmp = midVal.compareTo(key);

        if (cmp < 0)
            low = mid + 1;
        else if (cmp > 0)
            high = mid - 1;
        else
            return mid; // key found
        }
        return -(low + 1);  // key not found.
    }

    /**
     * Copied from Java6 .... not found in Java4/5
     * @param <T>
     * @param a
     * @param fromIndex
     * @param toIndex
     * @param key
     * @param c
     * @return
     */
    private static final int internalBinarySearch(Object[] a, int fromIndex, int toIndex, Object key, Comparator c) {
        if (c == null) {
            return internalBinarySearch(a, fromIndex, toIndex, key);
        }
        int low = fromIndex;
        int high = toIndex - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            Object midVal = a[mid];
            int cmp = c.compare(midVal, key);

            if (cmp < 0)
                low = mid + 1;
            else if (cmp > 0)
                high = mid - 1;
            else
                return mid; // key found
        }
        return -(low + 1);  // key not found.
    }

    private Object writeReplace() throws ObjectStreamException {
        try {
            synchronized(this) {
                return new Vector<E>(this);
            }
        }catch(Throwable t) {
            t.printStackTrace();
            throw new StreamCorruptedException(t.getMessage());
        }
    }

}
