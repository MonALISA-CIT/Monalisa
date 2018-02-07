/*
 * Get host parameters for a Mac OS X
 */
package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.DataArray;
import lia.util.Utils;
import lia.util.ntp.NTPDate;

/**
 * Mac OS X host monitoring, parsing output from "top", "netstat" and "uptime".
 * 
 * Based on the idea from LISA.
 * 
 * @author costing
 * @since 2008-06-16, ML 1.8.7
 */
public class MacHostPropertiesMonitor {
	
	private static final Logger logger = Logger.getLogger(MacHostPropertiesMonitor.class.getName());

	private static final long FIRST_STARTED = NTPDate.currentTimeMillis();
	
	/**
	 * Thread that keeps updating top values
	 */
	static UpdaterThread updater = null;

	/**
	 * Last values
	 */
	static final DataArray lastData = new DataArray(50);
	
	private static synchronized void ensureThreadStarted(){
		if (updater == null){
			updater = new UpdaterThread();
			updater.start();
		}
	}
	
	/**
	 * Get the double values in an array
	 * 
	 * @return a DataArray with the double values
	 */
	public static DataArray getData(){
		ensureThreadStarted();
		
		final DataArray da;
		
		synchronized (lastData){
			da = new DataArray(lastData);
		}

		return da;
	}
	
	/**
	 * Stop the background monitoring thread
	 */
	public static void stopMonitoringThread() {
		updater.setStop();
		updater = null;
	}
	
	private static final class UpdaterThread extends Thread {
		private boolean bStop = false;
		
		/**
		 * Set thread name
		 */
		public UpdaterThread(){
			super("MacHostProperties updater");
			setDaemon(true);
		}
		
		/**
		 * Signal thread to stop
		 */
		public void setStop(){
			bStop = true;
			updater.interrupt();
		}
		
		public void run(){
			while (!bStop){
				final long lStart = System.currentTimeMillis();
				
				new Thread(){
					public void run(){
						updateSockets(lastData);
						updateUptime(lastData);
					}
				}.start();
				
				update();
				
				final long lDiff = System.currentTimeMillis() - lStart;
				
				if (lDiff < 60000){
					try{
						Thread.sleep(60000-lDiff);
					}
					catch (Exception e){
						// ignore
					}
				}
			}
		}
	}
	
	private static int getSeconds(final String sHHMM){
		try{
			if (sHHMM.indexOf(':')>0){
				String sHours = sHHMM.substring(0, sHHMM.indexOf(':'));
				String sMin = sHHMM.substring(sHHMM.indexOf(':')+1);
			
				if (sHours.length()>1 && sHours.startsWith("0"))
					sHours = sHours.substring(1);

				if (sMin.length()>1 && sMin.startsWith("0"))
					sMin = sMin.substring(1);

				final int hours = Integer.parseInt(sHours);
				final int min = Integer.parseInt(sMin);
			
				return (hours*60+min)*60;
			}

			return Integer.parseInt(sHHMM)*60;
		}
		catch (Exception _){
			return 0;
		}
	}

	/**
	 * Parse the output of "uptime" to find out when the system was started
	 * 
	 * @param data
	 */
	static void updateUptime(final DataArray data){
		double uptime = 0;
		
		try{
			final Tokenizer t = new Tokenizer(Utils.getOutput("/usr/bin/uptime"), " \t,m");
			
			// 10:26  up 8 days, 14:15, 4 users, load averages: 0.06 0.11 0.16
			final String s1 = t.nextToken(2);
			final String s2 = t.nextToken();
			final String s3 = t.nextToken();
			
			if (s2.startsWith("day")){
				uptime = Integer.parseInt(s1)*24*60*60 + getSeconds(s3);
			}
			else
				uptime = getSeconds(s1);
		}
		catch (Exception e){
			uptime = (double) (NTPDate.currentTimeMillis()-FIRST_STARTED) / 1000;
		}
		
		synchronized (data){
			data.setParam("uptime", uptime/(60*60*24));
		}
	}
	
	/**
	 * Update socket-related statistics
	 * @param data socket values
	 */
	static void updateSockets(final DataArray data){
		try{
			final String s = Utils.getOutput("/usr/sbin/netstat -a -n -f inet");
			
			BufferedReader br = new BufferedReader(new StringReader(s));
			
			String sLine;
			
			final DataArray dTemp = new DataArray();
			
			synchronized (data){
				final Iterator it = data.parameterSet().iterator();
				
				// initialize all socket counters to 0, in case some types don't show up in the current status 
				
				while (it.hasNext()){
					final String sParam = (String) it.next();

					if (sParam.startsWith("sockets_"))
						dTemp.setParam(sParam, 0);
				}
			}
			
			// tcp4       0      0  alimacx01.cern.c.57615 monalisa.cern.ch.http  CLOSE_WAIT
			while ( (sLine=br.readLine())!=null ){
				final Tokenizer t = new Tokenizer(sLine);
				
				final String proto = t.nextToken();
				
				if (proto.startsWith("tcp")){
					dTemp.addToParam("sockets_tcp", 1);
					
					final String sState = t.nextToken(4);
					
					if (sState.length()>0)
						dTemp.addToParam("sockets_tcp_"+sState, 1);
				}
				
				if (proto.startsWith("udp")){
					dTemp.addToParam("sockets_udp", 1);
				}
			}
			
			synchronized(data){
				data.copyValues(dTemp);
			}
		}
		catch (Exception e){
			logger.log(Level.WARNING, "Cannot parse netstat output", e);
		}
	}
	
	/**
	 * get the values from "top"
	 */
	static void update() {
		try{
			parseTop(lastData);
		}
		catch (Exception _){
			logger.log(Level.WARNING, "Exception parsing output", _);
		}
	}
	
	/**
	 * @param sValue
	 * @return the value of this, with any non-numerical characters at the beginning/end skipped
	 */
	static double toDouble(final String sValue){
		try{
			String s = sValue.trim();

			while (!Character.isDigit(s.charAt(0)))
				s = s.substring(1);
			
			while (!Character.isDigit(s.charAt(s.length()-1)))
				s = s.substring(0, s.length()-1);
			
			return Double.parseDouble(s);
		}
		catch (Exception e){
			return -1;
		}
	}

	private static final double K = 1024;
	private static final double M = K*K;
	private static final double G = K*M;
	private static final double T = K*G;
	private static final double X = K*T;
	
	/**
	 * @param sValue something like 44G
	 * @return the value, in bytes
	 */
	static double toDoubleWithSize(final String sValue){
		char cOld = 0, c;
		
		String s = sValue;
		
		while ( Character.isLetter(c=s.charAt(s.length()-1)) ){
			s = s.substring(0, s.length()-1);
			cOld = c;
		}
		
		double d = toDouble(s);
		
		switch (cOld){
			case 'X': d*=X; break;
			case 'T': d*=T; break;
			case 'G': d*=G; break;
			case 'M': d*=M; break;
			case 'K': d*=K;
		}
		
		return d;
	}
	
	private static class Tokenizer extends StringTokenizer {
		/**
		 * @param str
		 */
		public Tokenizer(String str) {
			super(str);
		}
		
		/**
		 * @param str
		 * @param delim
		 */
		public Tokenizer(final String str, final String delim){
			super(str, delim);
		}

		/**
		 * Jump over
		 * 
		 * @param count
		 */
		public void jump(final int count){
			for (int i=0; i<count; i++)
				nextToken();
		}
		
		/**
		 * Get the next token.
		 * @return next token, or empty string if there is no token (no exception thrown in case of errors!)
		 */
		public String nextToken(){
			if (hasMoreTokens())
				return super.nextToken();
			
			return "";
		}
		
		/**
		 * Get the next token jumping over a number of elements
		 * 
		 * @param jumpOver
		 * @return next token after so many jumped over
		 */
		public String nextToken(final int jumpOver){
			jump(jumpOver);
			return nextToken();
		}
		
		/**
		 * @return next token as double value
		 */
		public double nextDouble(){
			return toDouble(nextToken());
		}
		
		/**
		 * @param jumpOver
		 * @return next token after so many jumped over, as double
		 */
		public double nextDouble(final int jumpOver){
			return toDouble(nextToken(jumpOver));
		}
		
		/**
		 * @return next token as a double size
		 */
		public double nextSize(){
			return toDoubleWithSize(nextToken());
		}
		
		/**
		 * @param jumpOver
		 * @return next token after so many jumped over, as double size
		 */
		public double nextSize(final int jumpOver){
			return toDoubleWithSize(nextToken(jumpOver));
		}
	}
	
	private static void parseTop(final DataArray data) throws IOException {
		double dTime = System.currentTimeMillis();
		
		// almost one minute, should be an accurate enough sampling of the system status
		final String toParse = Utils.getOutput("/usr/bin/top -cd -l2 -n0 -F -R -X -s58 -S");
		
		dTime = (System.currentTimeMillis() - dTime)/1000;
		
		final int lastTimeIDX = toParse.lastIndexOf("Time: ");
		
		if (lastTimeIDX<0){
			logger.log(Level.WARNING, "The 'top' command didn't return the expected output");
			return;
		}
		
		final String sExtract = toParse.substring(lastTimeIDX);
		final BufferedReader br = new BufferedReader(new StringReader(sExtract));
		
		synchronized (data) {
			// Time: 2008/06/16 16:45:33. Threads: 302. Procs: 70, 2 running, 68 sleeping.
			Tokenizer st = new Tokenizer(br.readLine());

			data.setParam("threads", st.nextDouble(4));
			data.setParam("processes", st.nextDouble(1));
			data.setParam("processes_R", st.nextDouble());
			data.setParam("processes_S", st.nextDouble(1));

			// LoadAvg: 0.05, 0.03, 0.00. CPU: 0.27% user, 0.90% sys, 98.83% idle.
			st = new Tokenizer(br.readLine());

			data.setParam("load1", st.nextDouble(1));
			data.setParam("load5", st.nextDouble());
			data.setParam("load15", st.nextDouble());

			data.setParam("cpu_usr", st.nextDouble(1));
			data.setParam("cpu_sys", st.nextDouble(1));
			final double cpu_idle = st.nextDouble(1);
			data.setParam("cpu_idle", cpu_idle);
			data.setParam("cpu_usage", (100 - cpu_idle) / 100d);

			// PhysMem: 907M wired, 394M active, 165M inactive, 1456M used, 6736M free.
			st = new Tokenizer(br.readLine());

			data.setParam("mem_wired", st.nextSize(1) / M);
			data.setParam("mem_active", st.nextSize(1) / M);
			final double mem_inactive = st.nextSize(1);
			data.setParam("mem_inactive", mem_inactive / M);
			final double mem_used = st.nextSize(1);
			data.setParam("mem_used", mem_used / M);
			final double mem_free = st.nextSize(1);
			data.setParam("mem_free", mem_free / M);

			data.setParam("mem_usage", mem_used * 100 / (mem_used + mem_free));
			data.setParam("total_mem", (mem_used + mem_free) / M);

			// VirtMem: 44G , 0 pageins, 0 pageouts.
			st = new Tokenizer(br.readLine());

			data.setParam("virtmem", st.nextSize(1) / M);
			
			data.setParam("swap_in_R", st.nextDouble(1) / dTime);
			data.setParam("swap_out_R", st.nextDouble(1) / dTime);

			// Swap:   64M total,     0 used,   64M free.  Purgeable:   11M           0 purges
			st = new Tokenizer(br.readLine());
			final double total_swap = st.nextSize(1);
			data.setParam("total_swap", total_swap/M);
			final double swap_used = st.nextSize(1);
			data.setParam("swap_used", swap_used/M);
			data.setParam("swap_free", st.nextSize(1)/M);
			data.setParam("swap_usage", total_swap>0 ? swap_used*100/total_swap : 0);
			data.setParam("swap_purgeable", st.nextSize(2)/M);
			
			// Networks: packets = 19 in, 29 out, data = 2514 in, 4353 out.
			st = new Tokenizer(br.readLine());
			data.setParam("eth0_in_R", (st.nextSize(9) / K) / dTime);
			data.setParam("eth0_out_R", (st.nextSize(1) / K) / dTime);

			// Disks: operations = 0 in, 17 out, data = 0 in, 1060K out.
			st = new Tokenizer(br.readLine());
			data.setParam("blocks_in_R", (st.nextSize(9) / K) / dTime);
			data.setParam("blocks_out_R", (st.nextSize(1) / K) / dTime);
		}
	}
}
