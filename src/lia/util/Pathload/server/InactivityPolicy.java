package lia.util.Pathload.server;


/**
 * This Comparator sorts two Peers after their inactiveTime attribute.
 * 
 * @author heri
 *
 */
public class InactivityPolicy implements Policy {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8079440604253276849L;

	public int compare(Object arg0, Object arg1) {
		int result;
		Peer p1 = (Peer) arg0;
		Peer p2 = (Peer) arg1;
		
		if (p1.getInactiveTime() > p2.getInactiveTime()) {
			result =  1;			
		} else if (p1.getInactiveTime() == p2.getInactiveTime()) {
			result = 0;
		} else {
			result = -1;
		}
		return result;
	}

}
