/**
 * 
 */
package lia.util.Pathload.server;

import java.util.Iterator;
import java.util.Vector;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * This is a managed container of holding couples of peers
 * that have priority in beeing the next Token holder and the
 * next destination host.
 * This class implements XMLWritable so its contents can be at
 * any time dumped to an XML.
 *  
 * TODO: Comment Code	
 * @author heri
 *
 */
public class ForcedPeerContainer implements XMLWritable {
	
	private Vector container;
	
	public ForcedPeerContainer() {
		container = new Vector();
	}
	
	public boolean add(PeerGroup p) {
		if (p == null) return false;
		if (container.contains(p)) return false;
		
		container.add(p);
		return true;
	}
	
	public boolean remove(PeerInfo p) {
		boolean bResult = false;
		if (p == null) return false;
		
		Vector vec = new Vector();
		for (Iterator it = container.iterator(); it.hasNext(); ) {
			PeerGroup pg = (PeerGroup) it.next();
			if ((pg.getSrc().equals(p)) ||
					(pg.getDest().equals(p))) {
				vec.add(pg);
			}
		}
		
		if (!vec.isEmpty()) {
			bResult = container.removeAll(vec);			
		}
		
		return bResult;
	}
	
	public boolean contains(PeerInfo p) {
		boolean bResult = false;
		if (p == null) return false;

		for (Iterator it = container.iterator(); it.hasNext(); ) {
			PeerGroup pg = (PeerGroup) it.next();
			if ((pg.getSrc().equals(p)) ||
					(pg.getDest().equals(p))) {
				bResult = true;
				break;
			}
		}
		
		return bResult;
	}
	
	public PeerInfo getNextSrcHost() {
		if (container.isEmpty()) return null;
		
		PeerGroup pg = (PeerGroup) container.get(0);
		return pg.getSrc();
	}
	
	public PeerInfo getNextDestHost(PeerInfo src) {
		PeerInfo dest = null;		
		if (src == null) return null;
		
		for (Iterator it = container.iterator(); it.hasNext(); ) {
			PeerGroup pg = (PeerGroup) it.next();
			if (pg.getSrc().equals(src)) {
				dest = pg.getDest();
				it.remove();
				break;
			}
		}
		return dest;
	}
	
	public boolean isEmpty() {
		return container.isEmpty();
	}
	
	public int size() {
		return container.size();
	}
	
	public Element getXML(Document document) {
		Element forcedPeerContainerElement = 
			document.createElement("forcedPeerContainer");				

		Element temp;
		for (Iterator it = container.iterator(); it.hasNext() ; ) {
			PeerGroup pg = (PeerGroup) it.next();
			temp = pg.getXML(document);
			forcedPeerContainerElement.appendChild(temp);
		}
		
		return forcedPeerContainerElement;
	}	
}
