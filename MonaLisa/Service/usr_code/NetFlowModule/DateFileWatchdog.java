import java.io.File;
import java.util.Observable;

/**
 * This class monitors a specified configuration File for a change ... Uses the
 * last modification time of the file
 */
public class DateFileWatchdog extends Observable implements Runnable {

	private File fileToWatch = null;

	private String cannonicalFilePath = null;

	private long lastModifiedTime;

	private boolean hasToRun;

	private long TIME_TO_SLEEP;

	/**
	 * @param fileName
	 * @param howOften -
	 *            How often to verify for a change ( in millis )
	 * @throws Exception
	 */
	public DateFileWatchdog(String fileName, long howOften) throws Exception {

		this(new File(fileName), howOften);
	}

	/**
	 * @param f
	 * @param howOften -
	 *            How often to verify for a change ( in millis )
	 * @throws Exception
	 */
	public DateFileWatchdog(File f, long howOften) throws Exception {

		if (f == null) {
			throw new Exception("Cannot monitor a null File...");
		}
		cannonicalFilePath = f.getCanonicalPath();
		if (!f.exists()) {
			throw new Exception("The file [ " + cannonicalFilePath
					+ " ] does not exist!");
		}
		if (!f.canRead()) {
			throw new Exception("The file [ " + cannonicalFilePath
					+ " ] has now Read acces!");
		}

		fileToWatch = f;
		lastModifiedTime = fileToWatch.lastModified();
		hasToRun = true;
		TIME_TO_SLEEP = howOften;

		if (TIME_TO_SLEEP <= 0) {
			TIME_TO_SLEEP = 20 * 1000;// every 20s
		}

		// let's get the party started :)!
		(new Thread(this, "( ML ) DateFileWatchdog for " + f)).start();
	}

	public void run() {

		while (hasToRun) {
			try {
				long lmt = fileToWatch.lastModified();
				if (lastModifiedTime != lmt) {
					lastModifiedTime = lmt;
					setChanged();
					notifyObservers();
				}
			} catch (Throwable t) {
				System.out
						.println(" DateFileWatchdog got exception trying to monitor for a file change [ "
								+ cannonicalFilePath + " ]");
				t.printStackTrace();
			}
			try {
				Thread.sleep(TIME_TO_SLEEP);
			} catch (Exception e) {
			}
		}// while()
	}

	public void stopIt() {

		hasToRun = false;
	}
}
