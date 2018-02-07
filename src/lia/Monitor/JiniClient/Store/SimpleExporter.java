package lia.Monitor.JiniClient.Store;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;

import lia.Monitor.Store.Cache;
import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.TransparentStoreFast;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.monPredicate;
import lia.web.utils.Formatare;

public class SimpleExporter extends Thread {

	public static void main(String args[]) {

		if (!Boolean.valueOf(AppConfig.getProperty("lia.Monitor.SimpleExporter.enabled", "false")).booleanValue())
			return;

		try {

			int iPort = Integer.parseInt(AppConfig.getProperty("lia.Monitor.SimpleExporter.port", "9100"));

			ServerSocket ss = new ServerSocket(iPort);

			while (true) {
				try {
					Socket s = ss.accept();
					(new SimpleExporter(s)).start();
				} catch (Exception e) {
					System.err.println("SimpleExporter: exception initializing communication: " + e + " (" + e.getMessage() + ")");
					e.printStackTrace();
				}
			}

		} catch (Throwable e) {
			System.err.println("SimpleExporter: exception initializing: " + e + " (" + e.getMessage() + ")");
			e.printStackTrace();
		}

	}

	final BufferedReader	br;

	final PrintWriter		pw;

	public SimpleExporter(final Socket s) throws IOException {
		br = new BufferedReader(new InputStreamReader(s.getInputStream()));
		pw = new PrintWriter(s.getOutputStream());
	}

	public void run() {
		try {

			final String sMode = br.readLine();

			if (sMode==null){
				br.close();
				pw.close();
				return;
			}
			
			final Vector v = new Vector();

			String sLine;

			while ((sLine = br.readLine()) != null && sLine.length() > 0)
				v.add(Formatare.toPred(sLine));

			if (v.size() == 0) {
				br.close();
				pw.close();
				return;
			}

			Vector vRez = new Vector();

			if (sMode.equals("L")) {
				for (int i = 0; i < v.size(); i++) {
					final monPredicate pred = (monPredicate) v.get(i);

					final Vector vTemp = Cache.getLastValues(pred);

					//System.err.println("Last values count for : "+TransparentStoreFast.predToString(pred)+" : "+vTemp.size());

					vRez.addAll(vTemp);
				}
			}

			if (sMode.equals("H")) {
				//System.err.println("History requests");

				final TransparentStoreFast store = (TransparentStoreFast) TransparentStoreFactory.getStore();

				final monPredicate[] mp = new monPredicate[v.size()];

				for (int i = 0; i < v.size(); i++)
					mp[i] = (monPredicate) v.get(i);

				vRez = store.getResults(mp);
			}

			//System.err.println("Results final count: "+vRez.size());

			for (int i = 0; i < vRez.size(); i++) {
				final Object o = vRez.get(i);

				if (o instanceof Result) {
					final Result r = (Result) o;

					pw.println(r.time + "/" + r.FarmName + "/" + r.ClusterName + "/" + r.NodeName + "/" + r.param_name[0] + "/" + r.param[0]);
				}
			}

			pw.flush();
			pw.close();
			br.close();
		} catch (Throwable t) {
			System.err.println("SimpleExporter: exception communicating: " + t + " (" + t.getMessage() + ")");
			t.printStackTrace();
		}
	}

}
