/**
 * 
 */
package lia.util.dns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;

import org.xbill.DNS.RRset;
import org.xbill.DNS.Record;


/**
 * Modified round-robin algorithm that takes into account some weights.
 * 
 * @author costing
 * @since Jun 22, 2007
 */
public class WeightedRRSet extends RRset {

	/**
	 * The mapping between Record object and their weights (Double).
	 */
	protected final Hashtable<Record, Double> htRecords = new Hashtable<Record, Double>();
	
	/**
	 * Default constructor
	 */
	public WeightedRRSet() {
		super();
	}
	
	/* (non-Javadoc)
	 * @see org.xbill.DNS.RRset#rrs()
	 */
	public Iterator rrs() {
		return rrs(true);
	}
	
	private static final Random rand = new Random();
	
	/* (non-Javadoc)
	 * @see org.xbill.DNS.RRset#rrs(boolean)
	 */
	public Iterator rrs(final boolean cycle) {
		ArrayList l;
		
		synchronized (htRecords) {
			l = new ArrayList(htRecords.keySet());

			if (cycle) {
				Collections.sort(l, new Comparator() {
					public int compare(Object arg0, Object arg1) {
						Record r1 = (Record) arg0;
						Record r2 = (Record) arg1;

						double w1 = ((Double) htRecords.get(r1)).doubleValue();
						double w2 = ((Double) htRecords.get(r2)).doubleValue();

						double total = w1 + w2;
						
						// TODO the weight-based random order is not quite correct ... should be fixed
						
						if (rand.nextDouble()*total < w1)
							return -1;
						else
							return 1;
					}
				});
			}
		}
		
		return l.iterator();
	}

	/* (non-Javadoc)
	 * @see org.xbill.DNS.RRset#sigs()
	 */
	public Iterator sigs() {
		return rrs(false);
	}
	
	/* (non-Javadoc)
	 * @see org.xbill.DNS.RRset#first()
	 */
	public Record first() {
		synchronized (htRecords){
			if (htRecords.size() == 0)
				throw new IllegalStateException("rrset is empty");
		
			return (Record) htRecords.keySet().iterator().next();
		}
	}

	/* (non-Javadoc)
	 * @see org.xbill.DNS.RRset#addRR(org.xbill.DNS.Record)
	 */
	public void addRR(final Record record){
		addRR(record, 1d);
	}
	
	/**
	 * @param record record to add
	 * @param weight record weight (on an arbitrary scale)
	 */
	public void addRR(final Record record, final double weight){
		htRecords.put(record, Double.valueOf(weight));
	}
	
	/* (non-Javadoc)
	 * @see org.xbill.DNS.RRset#deleteRR(org.xbill.DNS.Record)
	 */
	public void deleteRR(final Record record){
		htRecords.remove(record);
	}
	
	/* (non-Javadoc)
	 * @see org.xbill.DNS.RRset#clear()
	 */
	public void clear(){
		htRecords.clear();
	}

	/* (non-Javadoc)
	 * @see org.xbill.DNS.RRset#size()
	 */
	public int size(){
		return htRecords.size();
	}
	
}
