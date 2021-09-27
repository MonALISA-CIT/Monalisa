package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.util.DynamicThreadPoll.SchJob;

/**
 * Setting up command:
 * <code>
 * stty -F /dev/ttyUSB0 115200 -echo min 100 time 2 -hupcl brkint ignpar -icrnl -opost -onlcr -isig -icanon
 * </code><br>
 * <br>
 * Example udev rule (/etc/udev/rules.d/arduino.rules):<br>
 * <br>
 * <code>
 * #Arduino Nano Auto Tool Changer (CH340 Bus IC)
* SUBSYSTEMS=="usb",KERNEL=="ttyUSB*",ATTRS{idVendor}=="1a86",ATTRS{idProduct}=="7523",SYMLINK+="zbot_atc",GROUP="dialout",MODE="0666",RUN+="/usr/bin/stty -F /dev/%k 115200 -echo min 100 time 2 -hupcl brkint ignpar -icrnl -opost -onlcr -isig -icanon"
 *</code>
 *
 * @author costing
 * @since Sep 26, 2021
 */
public class monTempSensor extends SchJob implements MonitoringModule {
	private static final long serialVersionUID = 1L;

	private MonModuleInfo mmi = null;
	private MNode mn = null;

	private final double vdData[] = new double[2];

	@Override
	public MonModuleInfo init(final MNode node, final String args) {
		mn = node;

		mmi = new MonModuleInfo();
		mmi.setName("monTempSensor");
		mmi.setState(0);
		mmi.setResType(new String[] { "Temperature", "Humidity" });

		mmi.lastMeasurement = System.currentTimeMillis();

		vdData[0] = Double.NaN;
		vdData[1] = Double.NaN;

		return mmi;
	}

	// MonitoringModule

	@Override
	public String[] ResTypes() {
		return mmi.getResType();
	}

	@Override
	public String getOsName() {
		return "Linux";
	}

	@Override
	public Object doProcess() throws Exception {
		if (!isConnected())
			return null;

		if (!read())
			return null;

		final Result er = new Result();
		er.FarmName = getFarmName();
		er.ClusterName = getClusterName();
		er.NodeName = mn.getName();
		er.Module = mmi.getName();
		er.time = System.currentTimeMillis();
		er.param_name = mmi.getResType();
		er.param = new double[] { vdData[0], vdData[1] };

		return er;
	}

	@Override
	public MNode getNode() {
		return mn;
	}

	@Override
	public String getClusterName() {
		return mn.getClusterName();
	}

	@Override
	public String getFarmName() {
		return mn.getFarmName();
	}

	@Override
	public boolean isRepetitive() {
		return true;
	}

	@Override
	public String getTaskName() {
		return mmi.getName();
	}

	@Override
	public MonModuleInfo getInfo() {
		return mmi;
	}

	private BufferedReader br = null;
	private PrintWriter pw = null;

	private boolean isConnected() {
		if (br != null)
			return true;

		for (int i = 0; i < 10; i++) {
			final File f = new File("/dev/ttyUSB" + i);

			if (f.exists() && f.canRead()) {
				try {
					br = new BufferedReader(new FileReader(f));

					pw = new PrintWriter(new FileWriter(f));

					pw.println("v");
					pw.flush();

					// give it time to answer
					Thread.sleep(100);

					if (br.ready()) {
						final String line = br.readLine();

						if ("{OfficeTemperatureSensor}".equals(line))
							return true;
					}

					close();
				}
				catch (@SuppressWarnings("unused") final IOException | InterruptedException ioe) {
					// ignore
				}
			}
		}

		return false;
	}

	private void close() {
		if (pw != null) {
			pw.close();
			pw = null;
		}

		if (br != null) {
			try {
				br.close();
			}
			catch (final IOException e) {
				e.printStackTrace();
			}
			br = null;
		}
	}

	private boolean read() {
		try {
			pw.println("p");
			pw.flush();

			String line = br.readLine();

			if (line == null) {
				close();
				return false;
			}

			if (line.startsWith("{") && line.endsWith("}") && line.indexOf(' ') > 1) {
				line = line.substring(1, line.length() - 1);

				final int idx = line.indexOf(' ');

				vdData[0] = Double.parseDouble(line.substring(0, idx));
				vdData[1] = Double.parseDouble(line.substring(idx + 1));

				return true;
			}
		}
		catch (@SuppressWarnings("unused") final IOException ioe) {
			close();
		}

		return false;
	}

	/**
	 * Debug method
	 *
	 * @param args
	 */
	public static void main(final String[] args) {
		final monTempSensor sensor = new monTempSensor();

		sensor.init(new MNode("localhost", null, null), "");

		while (true) {
			try {
				final Object o = sensor.doProcess();

				System.err.println(o);

				Thread.sleep(5000);
			}
			catch (final Exception e) {
				e.printStackTrace();

				return;
			}
		}
	}
}
