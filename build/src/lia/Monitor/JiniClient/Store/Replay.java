package lia.Monitor.JiniClient.Store;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.StringTokenizer;

import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.TransparentStoreFast;
import lia.Monitor.monitor.Result;

/**
 * @author costing
 *
 */
public class Replay {

	/**
	 * @param args
	 * @throws Exception
	 */
	@SuppressWarnings("deprecation")
	public static void main(final String args[]) throws Exception {

		System.setProperty("lia.Monitor.Store.Fast.BatchProcessor.BUFFER_SIZE", "10");
		System.setProperty("lia.Monitor.Store.Fast.BatchProcessor.DROP_ZEROES", "100");
		
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

		TransparentStoreFast store = (TransparentStoreFast) TransparentStoreFactory.getStore();
		store.setCleanHash(false);

		String sLine = null;

		Date d = new Date();

		boolean bDateChanged = true;

		int oldh = -1;

		while ((sLine = br.readLine()) != null) {
			// System.err.println("* Line = "+sLine.trim());

			sLine = lia.web.utils.Formatare.replace(sLine, "  ", "\t");

			StringTokenizer st = new StringTokenizer(sLine.trim(), "\t");

			if (!st.hasMoreTokens()) {
				System.err.println("--- no token");
				continue;
			}

			String s1 = st.nextToken().trim();

			if (!st.hasMoreTokens()) {
				// System.err.println("Decoding the date");
				// extract the data from the file name

				StringTokenizer st2 = new StringTokenizer(s1, "_.");

				String t2 = null, t3 = null, t4 = null, t5 = null;

				while (st2.hasMoreTokens()) {
					t2 = t3;
					t3 = t4;
					t4 = t5;
					t5 = st2.nextToken();

					if (t5.equals("log")) {
						int y = Integer.parseInt(t2);
						int m = Integer.parseInt(t3);
						int day = Integer.parseInt(t4);

						d.setYear(y - 1900);
						d.setMonth(m - 1);
						d.setDate(day);

						break;
					}
				}

				System.err.println("the new day : " + d);

				bDateChanged = true;
				oldh = -1;

				t2 = null;
				t3 = null;
				t4 = null;
				t5 = null;
				st2 = null;
			} else {

				StringTokenizer st2 = new StringTokenizer(s1, ":");

				int h = Integer.parseInt(st2.nextToken());
				int m = Integer.parseInt(st2.nextToken());
				int s = Integer.parseInt(st2.nextToken());

				if (bDateChanged && h > 0) {
					// System.err.println("ignoring");
					continue;
				}

				bDateChanged = false;

				d.setHours(h);
				d.setMinutes(m);
				d.setSeconds(s);

				if (h != oldh) {
					System.err.println("  " + h + " : " + d);
					oldh = h;
				}

				// String farm , String cluster,String NodeName, String Module, String[] Param_name

				try {
					Result r = new Result(st.nextToken().trim(), st.nextToken().trim(), st.nextToken().trim(), null, null);
					r.time = d.getTime();
					r.addSet(st.nextToken().trim(), Double.parseDouble(st.nextToken().trim()));

					// System.err.println("Adding : "+r.time+" :
					// "+r.FarmName+"/"+r.ClusterName+"/"+r.NodeName+"/"+r.param_name[0]+"/"+r.param[0]);

					store.addData(r);
				} catch (Exception e) {
					// ignore Strings, only insert the double values
				}

				st2 = null;
			}

			s1 = null;
			st = null;
			sLine = null;
		}

	}

}
