package lia.util;

/**
 * This class implemets a circular array list
 * 
 * Taken from:
 * http://www.enterprisedeveloper.com/developers/javaspecialist/Issue027.html
 */

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;

public class CircularArrayList<T> extends AbstractList<T> implements Serializable {
	private static final long	serialVersionUID	= -2135082822729725411L;

	protected Object[]			elementData;

	// head points to the first logical element in the array, and
	// tail points to the element following the last.  This means
	// that the list is empty when head == tail.  It also means
	// that the elementData array has to have an extra space in it.
	private int					head				= 0, tail = 0;

	// Strictly speaking, we don't need to keep a handle to size,
	// as it can be calculated programmatically, but keeping it
	// makes the algorithms faster.
	private int					size				= 0;

	public CircularArrayList() {
		this(10);
	}

	public CircularArrayList(int size) {
		elementData = new Object[size];
	}

	public CircularArrayList(Collection<? extends T> c) {
		tail = c.size();
		elementData = new Object[c.size()];
		c.toArray(elementData);
	}

	// The convert() method takes a logical index (as if head was
	// always 0) and calculates the index within elementData
	private int convert(int index) {
		return (index + head) % elementData.length;
	}

	public boolean isEmpty() {
		return head == tail; // or size == 0
	}

	// We use this method to ensure that the capacity of the
	// list will suffice for the number of elements we want to
	// insert.  If it is too small, we make a new, bigger array
	// and copy the old elements in.
	public void ensureCapacity(int minCapacity) {
		int oldCapacity = elementData.length;
		if (minCapacity > oldCapacity) {
			int newCapacity = (oldCapacity * 3) / 2 + 1;
			if (newCapacity < minCapacity)
				newCapacity = minCapacity;
			Object newData[] = new Object[newCapacity];
			toArray(newData);
			tail = size;
			head = 0;
			elementData = newData;
		}
	}

	public int size() {
		// the size can also be worked out each time as:
		// (tail + elementData.length - head) % elementData.length
		return size;
	}

	public boolean contains(Object elem) {
		return indexOf(elem) >= 0;
	}

	public int indexOf(Object elem) {
		if (elem == null) {
			for (int i = 0; i < size; i++)
				if (elementData[convert(i)] == null)
					return i;
		} else {
			for (int i = 0; i < size; i++)
				if (elem.equals(elementData[convert(i)]))
					return i;
		}
		return -1;
	}

	public int lastIndexOf(Object elem) {
		if (elem == null) {
			for (int i = size - 1; i >= 0; i--)
				if (elementData[convert(i)] == null)
					return i;
		} else {
			for (int i = size - 1; i >= 0; i--)
				if (elem.equals(elementData[convert(i)]))
					return i;
		}
		return -1;
	}

	public Object[] toArray() {
		return toArray(new Object[size]);
	}

	public Object[] toArray(Object a[]) {
		if (a.length < size)
			a = (Object[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);
		if (head < tail) {
			System.arraycopy(elementData, head, a, 0, tail - head);
		} else {
			System.arraycopy(elementData, head, a, 0, elementData.length - head);
			System.arraycopy(elementData, 0, a, elementData.length - head, tail);
		}
		if (a.length > size)
			a[convert(size)] = null;
		return a;
	}

	private void rangeCheck(int index) {
		if (index >= size || index < 0)
			throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
	}

	public T get(int index) {
		rangeCheck(index);
		return (T)elementData[convert(index)];
	}

	public T set(int index, T element) {
		modCount++;
		rangeCheck(index);
		T oldValue = (T)elementData[convert(index)];
		elementData[convert(index)] = element;
		return oldValue;
	}

	public boolean add(T o) {
		modCount++;
		// We have to have at least one empty space
		ensureCapacity(size + 1 + 1);
		elementData[tail] = o;
		tail = (tail + 1) % elementData.length;
		size++;
		return true;
	}

	public void removeHead(int index) {
		modCount++;
		rangeCheck(index);

		for (int i = 0; i <= index; i++) {
			elementData[head] = null;
			head = (head + 1) % elementData.length;
			size--;
		}
	}

	// This method is the main reason we re-wrote the class.
	// It is optimized for removing first and last elements
	// but also allows you to remove in the middle of the list.
	public T remove(int index) {
		modCount++;
		rangeCheck(index);
		int pos = convert(index);
		// an interesting application of try/finally is to avoid
		// having to use local variables
		try {
			return (T)elementData[pos];
		} finally {
			elementData[pos] = null; // Let gc do its work
			// optimized for FIFO access, i.e. adding to back and
			// removing from front
			if (pos == head) {
				head = (head + 1) % elementData.length;
			} else if (pos == tail) {
				tail = (tail - 1 + elementData.length) % elementData.length;
			} else {
				if (pos > head && pos > tail) { // tail/head/pos
					System.arraycopy(elementData, head, elementData, head + 1, pos - head);
					head = (head + 1) % elementData.length;
				} else {
					System.arraycopy(elementData, pos + 1, elementData, pos, tail - pos - 1);
					tail = (tail - 1 + elementData.length) % elementData.length;
				}
			}
			size--;
		}
	}

	public void clear() {
		modCount++;
		// Let gc do its work
		for (int i = head; i != tail; i = (i + 1) % elementData.length)
			elementData[i] = null;
		head = tail = size = 0;
	}

	public boolean addAll(Collection<? extends T> c) {
		modCount++;
		int numNew = c.size();
		// We have to have at least one empty space
		ensureCapacity(size + numNew + 1);
		Iterator<? extends T> e = c.iterator();
		for (int i = 0; i < numNew; i++) {
			elementData[tail] = e.next();
			tail = (tail + 1) % elementData.length;
			size++;
		}
		return numNew != 0;
	}

	public void add(int index, T element) {
		modCount++;
		rangeCheck(index);
		// We have to have at least one empty space
		ensureCapacity(size + 1 + 1);

		int p = convert(index);
		//There are 2 cases: h/p/t and t/h/p
		// h/p/t is the standard impl as an ArrayList

		if (p == tail) {
			add(element);
			return;
		}

		if (p < tail) {
			System.arraycopy(elementData, p, elementData, p + 1, tail - p);
		} else {//t/h/p
			T le = (T)elementData[elementData.length - 1];
			System.arraycopy(elementData, p, elementData, p + 1, elementData.length - p - 1);
			System.arraycopy(elementData, 0, elementData, 1, tail);
			elementData[0] = le;
		}

		elementData[p] = element;
		tail = (tail + 1) % elementData.length;
		size++;

		//    throw new UnsupportedOperationException(
		//      "This method left as an exercise to the reader ;-)");
	}

	public boolean addAll(int index, Collection<? extends T> c) {
		throw new UnsupportedOperationException("This method left as an exercise to the reader ;-)");
	}

	private void writeObject(ObjectOutputStream s) throws IOException {
		s.writeInt(size);
		for (int i = head; i != tail; i = (i + 1) % elementData.length)
			s.writeObject(elementData[i]);
	}

	private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
		// Read in size of list and allocate array
		head = 0;
		size = tail = s.readInt();
		elementData = new Object[tail];
		// Read in all elements in the proper order.
		for (int i = 0; i < tail; i++)
			elementData[i] = s.readObject();
	}

	public static final void main(String[] args) {

	}
}