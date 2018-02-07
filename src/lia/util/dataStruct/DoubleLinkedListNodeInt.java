package lia.util.dataStruct;

public interface DoubleLinkedListNodeInt {
	
	/** get the next element from the list */
	public DoubleLinkedListNodeInt getNext();
	
	/** get the previous element from the list */
	public DoubleLinkedListNodeInt getPrev();
	
	/** set the given Node as the next element */
	public void setNext(DoubleLinkedListNodeInt next);
	
	/** set the given Node as the previous element */
	public void setPrev(DoubleLinkedListNodeInt prev);
	
	/** returns true if this element has a next element */
	public boolean hasNext();
	
	/** returns false if this element has a previous element */
	public boolean hasPrev();
	
	/** set the list that owns this node */
	public void setOwnerList(DoubleLinkedList list);

	/** remove this element from the list */
	public void remove();
	
}
