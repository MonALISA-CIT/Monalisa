/**
 *	XSL Stylesheet fragments reside here 
 */
package lia.util.Pathload.server.stylesheets;

/**
 * Helper class for loading XSL Stylesheets.
 * Including one stylesheet in another means that
 * all stylesheets must be in the same directory or
 * a known directory path relative to each other.
 * 
 * Because the stylesheets directory is inaccesible
 * from WEB-INF/classes/lia/util/Pathload , all
 * stylesheets reside here. A Classloader is used to
 * get the current directory for loading the 
 * stylesheet.
 * 
 * @author heri
 *
 */
public class StylesheetLoader {

}
