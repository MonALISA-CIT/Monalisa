package lia.util.Pathload.server;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;


/**
 * This is used to access files described by an URL.
 * 
 * @author heri
 *
 */
public class ResourceLoader {
	
	/**
	 * Returns requested Resource as a URL.
	 * 
	 * @param requestingClass	the java.lang.Class object of the class 
	 * 							that is attempting to load the resource
	 * @param resource			a String describing the full or partial 
	 * 							URL of the resource to loads
	 * @return					requested Resource as a URL.
	 * @throws ResourceMissingException
	 */
	public static URL getResourceAsURL(Class requestingClass, String resource) throws ResourceMissingException {
		URL resourceURL = null;
		
		try {
			resourceURL = new URL(resource);
		} catch (MalformedURLException murle) {			
			resourceURL = requestingClass.getResource(resource);
			
			if (resourceURL == null) {
				String resourceRelativeToClasspath = null;
				if (resource.startsWith("/"))
					resourceRelativeToClasspath = resource;
				else
					resourceRelativeToClasspath = '/' + requestingClass.getPackage().getName().replace('.', '/') + '/' + resource;
				throw new ResourceMissingException("Resource " + resource + " not found in classpath: " + resourceRelativeToClasspath);
			}
		}
		return resourceURL;
	}
	
	/**
	 * Returns requested Resource as a URL.toString();
	 * 
	 * @param requestingClass	the java.lang.Class object of the class 
	 * 							that is attempting to load the resource
	 * @param resource			a String describing the full or partial 
	 * 							URL of the resource to loads
	 * @return					requested Resource as a String.
	 * @throws ResourceMissingException
	 */
	public static String getResourceAsURLString(Class requestingClass, String resource) throws ResourceMissingException {
		return getResourceAsURL(requestingClass, resource).toString();
	}
	
	/**
	 * Returns requested Resource as a File.
	 * 
	 * @param requestingClass	the java.lang.Class object of the class 
	 * 							that is attempting to load the resource
	 * @param resource			a String describing the full or partial 
	 * 							URL of the resource to loads
	 * @return					requested Resource as a File.
	 * @throws ResourceMissingException
	 */
	public static File getResourceAsFile(Class requestingClass, String resource) throws ResourceMissingException {
		return new File(getResourceAsFileString(requestingClass, resource));
	}
	
	/**
	 * Returns requested Resource as a File string.
	 *  
	 * @param requestingClass	the java.lang.Class object of the class 
	 * 							that is attempting to load the resource
	 * @param resource			a String describing the full or partial 
	 * 							URL of the resource to loads
	 * @return					requested Resource as a File string
	 * @throws ResourceMissingException
	 */
	public static String getResourceAsFileString(Class requestingClass, String resource) throws ResourceMissingException {
		try {
			return URLDecoder.decode(getResourceAsURL(requestingClass, resource).getFile(),"UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
}
