package lia.Monitor.JiniClient.Store;

import java.net.ServerSocket;
import java.util.StringTokenizer;

import org.apache.catalina.startup.Bootstrap;

import lia.Monitor.monitor.AppConfig;

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
			StringTokenizer ports = new StringTokenizer(AppConfig.getProperty("lia.Repository.tomcat_port", ""));

			while (ports.hasMoreTokens()) {
				int port = Integer.parseInt(ports.nextToken());

				System.err.println("Checking availability of port " + port);

				try (ServerSocket ss = new ServerSocket(port)) {
					System.err.println("  Port " + port + " is available");
				}
			}
		}
		catch (final Throwable t) {
			System.err.println("Exception checking the TCP ports for Tomcat, will exit now because: " + t.getMessage());
			t.printStackTrace();
			System.exit(1);
		}

		try {
			sleep(1000 * 5);
		}
		catch (@SuppressWarnings("unused") Exception e) {
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
