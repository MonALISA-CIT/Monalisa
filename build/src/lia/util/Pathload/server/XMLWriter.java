/**
 * 
 */
package lia.util.Pathload.server;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import lia.util.Pathload.server.stylesheets.StylesheetLoader;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * This is a test class for dumpin' the internal status od
 * a class that implements XMLWritable to a string.
 * 
 * @author heri
 *
 */
public class XMLWriter {
	
	/**
	 * Transforms an XMLWritable class into a XML string
	 * 
	 * @param thing		The root node of the XML
	 * @return			String representation
	 */
	public static String getStringFromXMLDocument(XMLWritable thing) {		
		String result = null;
		if ( thing == null ) return null;
		
		try {
			DocumentBuilderFactory builderFactory =
				DocumentBuilderFactory.newInstance();
			
			DocumentBuilder builder =
				builderFactory.newDocumentBuilder();
			
			Document document = builder.newDocument();
			
			Element pathloadServletElement = 
				document.createElement("pathloadServlet");
			document.appendChild(pathloadServletElement);
			
			pathloadServletElement.appendChild(thing.getXML(document));
						
			
			TransformerFactory transformerFactory =
				TransformerFactory.newInstance();
			
			Transformer transformer =
				transformerFactory.newTransformer();
			
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			
			ByteArrayOutputStream ba = new ByteArrayOutputStream();
			transformer.transform(new DOMSource( document ), 
					new StreamResult(ba));
			ba.flush();
			ba.close();
			result = ba.toString();			
			
		} catch ( ParserConfigurationException parserException ) {
			parserException.printStackTrace();
		} catch ( TransformerConfigurationException transformerException ) {
			transformerException.printStackTrace();			
		} catch ( TransformerException transformerException ) {
			transformerException.printStackTrace();
		} catch ( IOException e ) {
			e.printStackTrace();
		}
		
		return result;		
	}
	
	/**
	 * Transform a XMLWritable class into an XHTML String using the
	 * specified xslt
	 * 
	 * @param xsltFilename	The XSLT File to use for transformation
	 * @param thing			The XMLWritable class to convert to HTML	
	 * @return				XHTML converted String
	 */
	public static String getHTMLStringFromXMLDocument(String xsltFilename, XMLWritable thing) {
		String result = null;
		if ( xsltFilename == null ) return null;
		if ( thing == null ) return null;
		
		try {			
			DocumentBuilderFactory builderFactory =
				DocumentBuilderFactory.newInstance();
			
			DocumentBuilder builder =
				builderFactory.newDocumentBuilder();
			
			Document document = builder.newDocument();
			
			Element pathloadServletElement = 
				document.createElement("pathloadServlet");
			document.appendChild(pathloadServletElement);
			
			pathloadServletElement.appendChild(thing.getXML(document));

			File xsltFile = ResourceLoader.getResourceAsFile(StylesheetLoader.class, 
					xsltFilename);
			
	        Source xsltSource = new StreamSource(xsltFile);
	        
			TransformerFactory transformerFactory =
				TransformerFactory.newInstance();
			
			Templates cachedXSLT = transformerFactory.newTemplates(xsltSource);
			
			Transformer transformer =
				cachedXSLT.newTransformer();
			
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			
			ByteArrayOutputStream ba = new ByteArrayOutputStream();
			transformer.transform(new DOMSource( document ), 
					new StreamResult(ba));
			ba.flush();
			ba.close();
			result = ba.toString();			
		} catch ( ResourceMissingException resourceMissingException) {
			result = resourceMissingException.getMessage();
		} catch ( ParserConfigurationException parserException ) {
			result = parserException.getMessage();
		} catch ( TransformerConfigurationException transformerException ) {
			result = transformerException.getMessage();			
		} catch ( TransformerException transformerException ) {
			result = transformerException.getMessage();
		} catch ( IOException e ) {
			e.printStackTrace();
		}		
		
		return result;
	}

	/**
	 * Parse an XML File and use the XSL to transform it to HTML
	 * TODO: TOTALLY REWRITE ME!
	 * 
	 * @param xsltFile		The XSLT File to use for transformation
	 * @param xmlFile		The XML File to use for transformation
	 * @return				HTML Result
	 */
	public static String getHTMLStringFromXMLDocument(File xsltFile, File xmlFile) {
		String result = null;
		if ( xsltFile == null ) return null;
		if ( xmlFile == null ) return null;
		
		try {									
	        Source xsltSource = new StreamSource(xsltFile);
	        Source xmlSource = new StreamSource(xmlFile);
	        
			TransformerFactory transformerFactory =
				TransformerFactory.newInstance();
			
			Templates cachedXSLT = transformerFactory.newTemplates(xsltSource);
			
			Transformer transformer =
				cachedXSLT.newTransformer();
			
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			
			ByteArrayOutputStream ba = new ByteArrayOutputStream();
			transformer.transform(xmlSource , 
					new StreamResult(ba));
			ba.flush();
			ba.close();
			result = ba.toString();			
			
		} catch ( TransformerConfigurationException transformerException ) {
			result = transformerException.getMessage();			
		} catch ( TransformerException transformerException ) {
			result = transformerException.getMessage();
		} catch ( IOException e ) {
			e.printStackTrace();
		}		
		
		return result;
	}	

}
