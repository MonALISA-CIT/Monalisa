/**
 * 
 */
package lia.util.Pathload.server;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * A class that can be described by an XML file must
 * implement this interface.
 * 
 * @author heri
 *
 */
public interface XMLWritable {
	
	/**
	 *	Transform the current class into an XML Element.
	 * 
	 * @param document	The main document to attach to.
	 * @return			The new Element to attach.
	 */
	public Element getXML(Document document);
}
