package lia.Monitor.modules;

/**
 * Exception that includes an error code to be reported in a module.
 *
 */
public class ModuleException extends Exception {
	/** The code that identifies the error. */
	int errCode;
	
	public ModuleException(String s, int code) {
		super(s);
		this.errCode = code;
	}
	
	public int getCode() {
		return errCode;
	}
}
