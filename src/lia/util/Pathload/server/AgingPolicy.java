/**
 * 
 */
package lia.util.Pathload.server;


/**
 * This Comparator sorts two Peer objects after their lastTokenTime
 * attribute.
 * 
 * @author heri
 *
 */
public class AgingPolicy implements Policy {

	/**
	 *  AgingPolicy must pe Serializable so it needs a serialVersionUID 
	 */
	private static final long serialVersionUID = 525245414871199340L;

	/** 
	 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
	 */
	public int compare(Object arg0, Object arg1) {
		int result;
		Peer p1 = (Peer) arg0;
		Peer p2 = (Peer) arg1;
		
		if (p1.getLastTokenTime() > p2.getLastTokenTime()) {
			result =  1;			
		} else if (p1.getLastTokenTime() == p2.getLastTokenTime()) {
			result = 0;
		} else {
			result = -1;
		}
		return result;
	}

}
