package lia.Monitor.AppControlClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class XMLFileParse extends DefaultHandler {

	private Tree tree = null;
	private String text = "";
	private String stringToParse = null;
	
	public XMLFileParse (String stringToParse) {
		this.stringToParse = stringToParse ;
	} //constructor

	public void setStringToParse (String stringToParse) {
		this.stringToParse = stringToParse ;
//		System.out.println (stringToParse);
	} //setStringToParse

	public Tree parse () {
		if (stringToParse != null) {
			tree = null;
			parseXmlFile(stringToParse, this, false);
			return tree;
			
		} else {
			return null;	
		}
		
	} // parse
		
	public void startElement(
				String namespaceURI,
				String localName,
				String qName,
				Attributes atts) {

				if ("".equals(namespaceURI))
					localName = qName;

				if (tree == null) {
					tree = new Tree(null, localName);
				} else {
					tree = tree.setChild(localName);
				}	

				// set the tree atributes
				for (int i = 0; i < atts.getLength(); i++) {
					String attName = null;
					if ("".equals(namespaceURI))
						attName = atts.getQName(i);
					else
						attName = atts.getLocalName(i);
					String attValue = atts.getValue(i);
					tree.setAttribute(attName, attValue);					
				}
			} //startElement


			public void characters(char[] ch, int start, int length) {

				text += String.valueOf(ch, start, length);
				
			} //characters

	
			public void endElement(
				String namespaceURI,
				String localName,
				String qName) {

				if ("".equals(namespaceURI))
					localName = qName;

				tree.text = text;				
    
				if (tree.parent != null)
					tree = tree.parent;
				text = ""; // clear to prepare for gathering text in next element	
			} //endElement

	
			public void error(SAXParseException e) throws SAXException {
				throw e;
			} //error

	
			public void fatalError(SAXParseException e) throws SAXException {
				throw e;
			} //fatalError

	
			public void warning(SAXParseException e) throws SAXException {
				throw e;
			} // warning


	public static void parseXmlFile(
		String parseString,
		DefaultHandler handler,
		boolean validating) {
		try {

			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setValidating(validating);

			SAXParser parser = factory.newSAXParser();
		//	parser.setContentHandler(handler);
		
			ByteArrayInputStream bais = new ByteArrayInputStream (parseString.getBytes());

			parser.parse(bais, handler);
			
		} catch (SAXException e) {
			e.printStackTrace () ;
		} catch (ParserConfigurationException e) {
			e.printStackTrace () ;
		} catch (IOException e) {
			e.printStackTrace () ;
		}
	} //parseXmlFile

}
