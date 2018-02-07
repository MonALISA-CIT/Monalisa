package lia.util.dataStruct;

public class DoubleLinkedListNode implements DoubleLinkedListNodeInt {

	/** previous element in the list */
	DoubleLinkedListNodeInt next;
	
	/** next element in the list */
	DoubleLinkedListNodeInt prev;
	
	/** the list that owns this element */
	DoubleLinkedList list;
	
	public DoubleLinkedListNode() {
		next = null;
		prev = null;
		list = null;
	}
	
	public DoubleLinkedListNodeInt getNext() {
		return next;
	}

	public DoubleLinkedListNodeInt getPrev() {
		return prev;
	}

	public void setNext(DoubleLinkedListNodeInt next) {
		this.next = next;
	}

	public void setPrev(DoubleLinkedListNodeInt prev) {
		this.prev = prev;
	}
	
	public boolean hasNext(){
		return next != list.tail; 
	}
	
	public boolean hasPrev(){
		return prev != list.head;
	}
	
	public void setOwnerList(DoubleLinkedList list) {
		this.list = list;
	}

	public void remove() {
		synchronized (list) {
			prev.setNext(next);
			next.setPrev(prev);
			this.prev = null;
			this.next = null;
			list.size--;
		}
	}
}
