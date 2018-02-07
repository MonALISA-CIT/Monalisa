package lia.web.servlets.web;

/**
 * @author costing
 * @since 2010-09-30
 */
public interface Condition {	
	/**
	 * Check if the condition matches a numeric value 
	 * 
	 * @param d
	 * @return true if matches
	 */
	boolean matches(double d);
	
	/**
	 * Check if the condition matches a string
	 * 
	 * @param s
	 * @return true if matches
	 */
	boolean matches(String s);

	/**
	 * Check if the condition matches an arbitrary object
	 * 
	 * @param o
	 * @return true if matches
	 */
	boolean matches(Object o);
}
