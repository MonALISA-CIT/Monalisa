package lia.util.Pathload.server.manager;

import lia.util.Pathload.server.PathloadLogger;
import lia.util.Pathload.server.XMLWritable;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * 
 * @author heri
 *
 */
public class HistoryManager implements XMLWritable {

	private PathloadLogger log;
	
	public HistoryManager() {
		log = PathloadLogger.getInstance();
	}

	public Element getXML(Document document) {
		return log.getXML(document);
	}
}
