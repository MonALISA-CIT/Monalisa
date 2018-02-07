package lia.Monitor.Store;

/**
 * A standard exception-like class.
 * 
 */
public class StoreException extends Exception {
	private static final long	serialVersionUID	= 1422535059584506074L;
	
	/**
	 * 
	 */
	public StoreException(){super();}
	
	/**
	 * @param description
	 */
	public StoreException(String description){super(description);}
	
	/**
	 * @param description
	 * @param cause
	 */
	public StoreException(String description, Throwable cause){super(description, cause);}
	
	/**
	 * @param cause
	 */
	public StoreException(Throwable cause){super(cause);}
}

