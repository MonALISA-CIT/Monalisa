package lia.Monitor.JiniClient.Store;

import org.apache.catalina.startup.Bootstrap;

/**
 * @author costing
 *
 */
public class TomcatStarter extends Thread {

	/**
	 * 
	 */
	public static void startTomcat() {
		(new TomcatStarter()).start();
	}

	/**
	 * 
	 */
	public TomcatStarter() {
		super("(ML) Tomcat Starter Main Thread");
	}

	@Override
	public void run() {
		try {
			sleep(1000 * 5);
		}
		catch (Exception e) {
			// improbable interruption here
		}

		try {
			Bootstrap.main(new String[] { "start" });
		}
		catch (Throwable t) {
			System.err.println("Cannot start Tomcat: " + t);
		}
	}

	/**
	 * @param args
	 */
	public static void main(String args[]) {
		startTomcat();
	}

}
