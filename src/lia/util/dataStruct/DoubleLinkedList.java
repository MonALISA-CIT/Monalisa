package lia.util.dataStruct;

/**
 * A double linked list that exposes the ListNode to the user. 
 * Note that the implementation is synchronized;
 * 
 * @author Catalin Cirstoiu
 */
public class DoubleLinkedList {
	public DoubleLinkedListNodeInt head;
	public DoubleLinkedListNodeInt tail;
	public int size;

	/** create an empty list */
	public DoubleLinkedList() {
		head = new DoubleLinkedListNode();
		tail = new DoubleLinkedListNode();
		head.setNext(tail);
		tail.setPrev(head);
		size = 0;
	}
	
	/** check if the list is empty */
	public synchronized boolean isEmpty() {
		return head.getNext() == tail;
	}
	
	/** get the size of the list */
	public synchronized int getSize() {
		return this.size;
	}
	
	/** add the given node to the begining of the list */
	public synchronized void addFirst(DoubleLinkedListNodeInt node) {
		node.setNext(head.getNext());
		node.setPrev(head);
		node.getNext().setPrev(node);
		head.setNext(node);
		node.setOwnerList(this);
		size++;
	}
	
	/** add the given node to the end of the list */
	public synchronized void addLast(DoubleLinkedListNodeInt node) {
		node.setNext(tail);
		node.setPrev(tail.getPrev());
		node.getPrev().setNext(node);
		tail.setPrev(node);
		node.setOwnerList(this);
		size++;
	}
	
	/** 
	 * get the first node from the list; 
	 * node is not removed; 
	 * @returns null if list is empty 
	 */
	public synchronized DoubleLinkedListNodeInt getFirst() {
		if(isEmpty())
			return null;
		return head.getNext();
	}
	
	/** 
	 * remove the first node from the list; 
	 * @returns the removed node or null if list is empty.
	 */
	public synchronized DoubleLinkedListNodeInt removeFirst() {
		DoubleLinkedListNodeInt node = getFirst();
		if(node != null){
			node.remove();
		}
		return node;
	}
	
	/** 
	 * get the last node from the list; 
	 * node is not removed; 
	 * @returns null if list is empty 
	 */
	public synchronized DoubleLinkedListNodeInt getLast() {
		if(isEmpty())
			return null;
		return tail.getPrev();
	}
	
	/** 
	 * remove the last node from the list; 
	 * @returns the removed node or null if list is empty.
	 */
	public synchronized DoubleLinkedListNodeInt removeLast() {
		DoubleLinkedListNodeInt node = getLast();
		if(node != null){
			node.remove();
		}
		return node;
	}
}
