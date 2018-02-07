/**
 * 
 */
package lia.util.Pathload.server;

/**
 * 
 * @author heri
 *
 */
public class ResourceMissingException extends Exception {

	/**
	 *  Serialization stuff required 
	 */
	private static final long serialVersionUID = 5583709816404421700L;

	/**
	 * Basic constructor
	 */
	public ResourceMissingException() {
		super();
	}

	/**
	 * Basic constructor
	 * 
	 * @param message	Human readable cause of Exception
	 */
	public ResourceMissingException(String message) {
		super(message);
	}

	/**
	 * Basic constructor
	 * 
	 * @param message	Human readable cause of Exception
	 * @param cause		Throwable cause of Exception
	 */
	public ResourceMissingException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Basic constructor
	 * 
	 * @param cause	Throwable cause of Exception
	 */
	public ResourceMissingException(Throwable cause) {
		super(cause);
	}

}
