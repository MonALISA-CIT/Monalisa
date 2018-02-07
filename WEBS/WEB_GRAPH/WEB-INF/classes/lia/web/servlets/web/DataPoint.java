/**
 * 
 */
package lia.web.servlets.web;

/**
 * @author costing
 * @since Jul 25, 2007
 */
final class DataPoint {
	/**
	 * Position on X axis
	 */
	public double x;
	
	/**
	 * Position on Y axis (value)
	 */
	public double y;
	
	/**
	 * Constructor that initializes the fields
	 * 
	 * @param x
	 * @param y
	 */
	public DataPoint(final double x, final double y){
		this.x = x;
		this.y = y;
	}
}
