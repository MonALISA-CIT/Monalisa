package lia.Monitor.JiniClient.Store;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import lia.Monitor.monitor.Result;

/**
 * @author costing
 * @since forever
 */
public class DirectInsert extends TimerTask implements DataProducer {

	private final Vector<Result>	vBuffer;

	private final String	sProgram;

	private final Timer	timer;

	private final boolean	bOnlyPositives;

	/**
	 * @param sProgram program to execute
	 * @param lInterval interval, in millis, between the calls
	 * @param bOnlyPositives whether or not to exclude the zero values
	 */
	public DirectInsert(final String sProgram, final long lInterval, final boolean bOnlyPositives) {
		this.sProgram = sProgram;
		this.bOnlyPositives = bOnlyPositives;

		vBuffer = new Vector<Result>();
		timer = new Timer(true);
		timer.scheduleAtFixedRate(this, 0, lInterval);
	}

	@Override
	public void run() {
		int iLine = 0;
		String sLine = null;
		StringTokenizer st2;
		Result r;

		try {
			StringTokenizer st = new StringTokenizer(getProgramOutput(sProgram), "\n");

			double d;

			while (st.hasMoreTokens()) {
				iLine++;

				sLine = st.nextToken();

				try {
					st2 = new StringTokenizer(sLine, "\t");

					r = new Result(st2.nextToken(), // farm
					st2.nextToken(), // cluster
					st2.nextToken(), // node
					null, // module
					null // param_name
					);

					String sFunc = st2.nextToken();
					d = Double.parseDouble(st2.nextToken());

					if (bOnlyPositives && d <= 0D)
						continue;

					r.addSet(sFunc, // function
					d);

					r.time = Long.parseLong(st2.nextToken());

					vBuffer.add(r);
				} catch (Exception e) {
					System.err.println("DirectInsert(" + sProgram + ") ignoring exception at input line : " + iLine);
					System.err.println("The line was : '" + sLine + "'");
					e.printStackTrace();
				}
			}

			System.err.println("DirectInsert(" + sProgram + ") : " + vBuffer.size());
		} catch (Exception e) {
			System.err.println("DirectInsert(" + sProgram + ") caught exception at input line : " + iLine);
			System.err.println("The line was : '" + sLine + "'");
			e.printStackTrace();
		}
	}

	@Override
	public Vector<Object> getResults() {
		final Vector<Object> vTemp;

		synchronized (vBuffer) {
			vTemp = new Vector<Object>(vBuffer.size());
			vTemp.addAll(vBuffer);
			vBuffer.clear();
		}

		return vTemp;
	}

	/**
	 * Execute the program and get the output from it
	 * 
	 * @param sProgramPath
	 * @return the output of the given command
	 */
	public static final String getProgramOutput(final String sProgramPath) {
		BufferedReader br = null;

		try {
			Runtime rt = Runtime.getRuntime();

			String comanda[] = new String[1];
			comanda[0] = sProgramPath;

			Process child = null;
			child = rt.exec(comanda);

			OutputStream child_out = child.getOutputStream();
			child_out.close();

			br = new BufferedReader(new InputStreamReader(child.getInputStream()));
			final StringBuilder sb = new StringBuilder(20000);
			final char cbuff[] = new char[10240];
			int iCount = 0;
			do {
				iCount = br.read(cbuff);
				if (iCount > 0)
					sb.append(cbuff, 0, iCount);
			} while (iCount > 0);

			br.close();
			br = null;

			child.waitFor();

			return sb.toString();
		} catch (IOException ioe) {
			System.err.println(ioe.toString());
			ioe.printStackTrace();
			return "";
		} catch (InterruptedException ie) {
			return "";
		} finally {
			if (br != null)
				try {
					br.close();
				} catch (IOException e1) {
					// ignore this
				}
		}
	}

}
