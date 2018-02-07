package lia.Monitor.JiniClient.CommonGUI;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.StringTokenizer;

import lia.util.ntp.NTPDate;

/**
 * read proc information. 
 * This is used by monClientStats to export this data with ApMon
 */
public class ProcReader {

    class cmdExec {

    	public String full_cmd;
    	public Process pro;
    	String osname;
    	String exehome = "";
    	private long timeout = 60 * 1000; // 1 min
    	
    	protected LinkedList streams = null;
    	protected LinkedList streamsReal = null;
    	
    	protected boolean isError = false;

    	public cmdExec() {
    	
    		osname = System.getProperty("os.name");
    		exehome = System.getProperty("user.dir");
    		String tot = System.getProperty("iperf.timeout");
    		double dd = -1.0;
    		try {
    			dd = Double.parseDouble(tot);
    		} catch (Exception e) {
    			dd = -1.0;
    		}
    		if (dd >= 0.0) timeout = (long)(dd * 1000);
    		streams = new LinkedList();
    		streamsReal = new LinkedList();
    	}

    	public void setCmd(String cmd) {
    		osname = System.getProperty("os.name");
    		full_cmd = cmd; // local
    	}
    	
    	public void setTimeout(long timeout) {
    		
    		this.timeout = timeout;
    	}

    	public BufferedReader procOutput(String cmd) {
    		try {

    			if (osname.startsWith("Linux") || osname.startsWith("Mac")) {
    				pro =
    					Runtime.getRuntime().exec(
    						new String[] { "/bin/sh", "-c", cmd });
    			} else if (osname.startsWith("Windows")) {
    				pro = Runtime.getRuntime().exec(exehome + cmd);
    			}

    			InputStream out = pro.getInputStream();
    			BufferedReader br = new BufferedReader(new InputStreamReader(out));
    			BufferedReader err = new BufferedReader(new InputStreamReader(pro.getErrorStream()));
    			
    			String buffer = "";
    			String ret = "";
    			while((buffer = err.readLine())!= null) {
    				ret += buffer+"\n'";
    			}
    			
    			if (ret.length() != 0){
//    				System.out.println(ret);
					br.close();
					err.close();
					pro.destroy();
    				return null;
    			}
    			
    			return br;

    		} catch (Exception e) {
    			System.out.println("FAILED to execute cmd = " + exehome + cmd);
    			Thread.currentThread().interrupt();
    		}

    		return null;
    	}

    	public BufferedReader exeHomeOutput(String cmd) {

    		try {

    			pro =
    				Runtime.getRuntime().exec(
    					new String[] { "/bin/sh", "-c", exehome + cmd});
//    			System.out.println("/bin/sh -c "+exehome + cmd);
    			InputStream out = pro.getInputStream();
    			BufferedReader br = new BufferedReader(new InputStreamReader(out));

    			BufferedReader err = new BufferedReader(new InputStreamReader(pro.getErrorStream()));
    			
    			String buffer = "";
    			String ret = "";
    			while((buffer = err.readLine())!= null) {
    				ret += buffer+"\n'";
    			}
    			
    			if (ret.length() != 0){
//    				System.out.println(ret);
					br.close();
					err.close();
					pro.destroy();
    				return null;
    			}
    			
    			err.close();
    			return br;

    		} catch (Exception e) {
    			System.out.println("FAILED to execute cmd = " + exehome + cmd);
    			Thread.currentThread().interrupt();
    		}

    		return null;
    	}

    	public void stopModule() {

    		if (this.pro != null)
    			this.pro.destroy();

    	}

    	public BufferedReader readProc(String filePath) {

    		try {
    			return new BufferedReader(new FileReader(filePath));
    		} catch (Exception e) {

    			return null;
    		}
    	}
    	
    	public boolean isError() {
    		
    		return isError;
    	}
    	
    	public String executeCommand(String command, String expect) {
    		
    		StreamGobbler output = null;
    		StreamGobbler error = null;
    		
    		try
    		{            
    			String osName = System.getProperty("os.name" );
    			Process proc = null;

    			if (osName.indexOf("Win") != -1) {
    				proc = Runtime.getRuntime().exec(command);
    			} else if (osName.indexOf("Linux") != -1 || osName.indexOf("Mac") != -1) {
    				String[] cmd = new String[3];
    				cmd[0] = "/bin/sh";
    				cmd[1] = "-c";
    				cmd[2] = command;
    				proc = Runtime.getRuntime().exec(cmd);
    			} else {
    				isError = true;
    				return null; 
    			}
    			
    			error = getStreamGobbler();
    			output = getStreamGobbler();
    			
    			// any error message?
    			error.setInputStream(proc.getErrorStream());            
    			
    			// any output?
    			output.setInputStream(proc.getInputStream());
    			
    			String out = "";
    			
    			// any error???
    				long startTime = new Date().getTime();
    				while (true) {
    					out = error.getOutput();
    					try {
    						if (!out.equals("") && proc.exitValue() != 0) {
    							isError = true;
    							break;
    						}
    					} catch (IllegalThreadStateException ex) { }
    					if (expect != null) {
    						out = output.getOutput();
    						if (out != "" && out.indexOf(expect) != -1) {
    							isError = false;
    							break;
    						}
    					}
    					long endTime = new Date().getTime();
    					if (endTime - startTime > timeout) {
    						isError = true;
    						break;
    					}
    					Thread.sleep(100);
    				}
    			
    			proc.destroy();
    			proc.waitFor();

    			if (out.equals(""))
    				out = output.getOutput();
    			
//    			String ret = "";
//    			
//    			if (!error.getOutput().equals(""))
//    				ret = error.getOutput();
//    			
//    			ret = output.getOutput();
    			
    			error.stopIt();
    			output.stopIt();
    			
    			addStreamGobbler(error);
    			addStreamGobbler(output);
    			
    			error =  null;
    			output = null;
    			
    			return out;
    			
    		} catch (Exception e) {
    			e.printStackTrace();
    			
    			if (error != null) {
    				addStreamGobbler(error);
    				error.stopIt();
    				error = null;
    			}
    			
    			if (output != null) {
    				addStreamGobbler(output);
    				output.stopIt();
    				output = null;
    			}
    			isError = true;
    			return "";
    		}
    	}
    	

    	public String executeCommand(String command, String expect, int howManyTimes) {
    		
    		StreamGobbler output = null;
    		StreamGobbler error = null;
    		int nr = 0; // how many times the expect string occured
    		
    		try
    		{            
    			String osName = System.getProperty("os.name" );
    			Process proc = null;

    			if (osName.indexOf("Win") != -1) {
    				proc = Runtime.getRuntime().exec(command);
    			} else if (osName.indexOf("Linux") != -1 || osName.indexOf("Mac") != -1) {
    				String[] cmd = new String[3];
    				cmd[0] = "/bin/sh";
    				cmd[1] = "-c";
    				cmd[2] = command;
    				proc = Runtime.getRuntime().exec(cmd);
    			} else {
    				isError = true;
    				return null; 
    			}
    			
    			error = getStreamGobbler();
    			output = getStreamGobbler();
    			
    			error.setInputStream(proc.getErrorStream());            
    			
    			output.setInputStream(proc.getInputStream());
    			
    			String out = "";
    			
    			long startTime = new Date().getTime();
    			while (true) {
    				out = error.getOutput();
    				try {
    					if (!out.equals("") && proc.exitValue() != 0) {
    						isError = true;
    						break;
    					}
    				} catch (IllegalThreadStateException ex) { }
    				if (expect != null) {
    					out = output.getOutput();
    					if (out != "" && out.indexOf(expect) != -1) {
    						nr = getStringOccurences(out, expect);
    						if (nr >= howManyTimes) {
    							isError = false;
    							break;
    						}
    					}
    				}
    				long endTime = new Date().getTime();
    				if (endTime - startTime > timeout) {
    					isError = true;
    					break;
    				}
    				Thread.sleep(100);
    			}
    			
    			proc.destroy();
    			proc.waitFor();

    			if (out.equals(""))
    				out = output.getOutput();
    			
    			error.stopIt();
    			output.stopIt();
    			
    			addStreamGobbler(error);
    			addStreamGobbler(output);
    			
    			error =  null;
    			output = null;
    			
    			return out;
    			
    		} catch (Exception e) {
    			e.printStackTrace();
    			
    			if (error != null) {
    				addStreamGobbler(error);
    				error.stopIt();
    				error = null;
    			}
    			
    			if (output != null) {
    				addStreamGobbler(output);
    				output.stopIt();
    				output = null;
    			}
    			isError = true;
    			return "";
    		}
    	}
    	
    	protected int getStringOccurences(String text, String token) {
    		
    		if (text.indexOf(token) < 0) return 0;
    		int nr = 0;
    		String str = text;
    		while (str.indexOf(token) >= 0) {
    			str = str.substring(str.indexOf(token)+token.length());
    			nr++;
    		}
    		return nr;
    	}
    	
    	// cipsm ->  new execute command - it shows the output exactly as it is, by lines
    	public String executeCommandReality(String command, String expect) {

    		StreamRealGobbler error = null;
    		StreamRealGobbler output = null;
    		try
    		{            
    			String osName = System.getProperty("os.name" );
    			Process proc = null;

    			if (osName.indexOf("Win") != -1) {
    				proc = Runtime.getRuntime().exec(command);
    			} else if (osName.indexOf("Linux") != -1) {
    				String[] cmd = new String[3];
    				cmd[0] = "/bin/sh";
    				cmd[1] = "-c";
    				cmd[2] = command;
    				proc = Runtime.getRuntime().exec(cmd);
    			} else {
    				isError = true;
    				return null; 
    			}
    			
    			error = getStreamRealGobbler();
    			output = getStreamRealGobbler();
    			
    			// any error message?
    			error.setInputStream(proc.getErrorStream());            
    			
    			// any output?
    			output.setInputStream(proc.getInputStream());
    			
    			String out = "";
    			
    			// any error???
    			long startTime = new Date().getTime();
    			while (true) {
    				out = error.forceAllOutput();
    				try {
    					if (!out.equals("") && proc.exitValue() != 0) {
    						isError = true;
    						break;
    					}
    				} catch (IllegalThreadStateException ex) { }
    				if (expect != null) {
    					out = output.forceAllOutput();
    					if (out != "" && out.indexOf(expect) != -1) {
    						isError = false;
    						break;
    					}
    				}
    				long endTime = new Date().getTime();
    				if (endTime - startTime > timeout) {
    					isError = true;
    					break;
    				}
    				Thread.sleep(100);
    			}
    			
    			proc.destroy();
    			proc.waitFor();
    			
    			if (out.equals(""))
    				out = output.forceAllOutput();
    			
//    			String ret = "";
//    			
//    			if (!error.getOutput().equals(""))
//    				ret = error.forceAllOutput();
//    			
//    			ret = output.forceAllOutput();
    			
    			error.stopIt();
    			output.stopIt();
    			
    			addStreamRealGobbler(error);
    			addStreamRealGobbler(output);
    			
    			error = null;
    			output = null;
    			
    			return out;
    			
    		} catch (Exception e) {
    			e.printStackTrace();
    			
    			if (error != null) {
    				addStreamRealGobbler(error);
    				error.stopIt();
    				error = null;
    			}
    			
    			if (output != null) {
    				addStreamRealGobbler(output);
    				output.stopIt();
    				output = null;
    			}
    			isError = true;
    			
    			return "";
    		}
    	}
    	
    	public String executeCommandReality(String command, String expect, int howManyTimes) {

    		StreamRealGobbler error = null;
    		StreamRealGobbler output = null;
    		try
    		{            
    			String osName = System.getProperty("os.name" );
    			Process proc = null;

    			if (osName.indexOf("Win") != -1) {
    				proc = Runtime.getRuntime().exec(command);
    			} else if (osName.indexOf("Linux") != -1) {
    				String[] cmd = new String[3];
    				cmd[0] = "/bin/sh";
    				cmd[1] = "-c";
    				cmd[2] = command;
    				proc = Runtime.getRuntime().exec(cmd);
    			} else {
    				isError = true;
    				return null; 
    			}
    			
    			error = getStreamRealGobbler();
    			output = getStreamRealGobbler();
    			
    			error.setInputStream(proc.getErrorStream());            
    			
    			output.setInputStream(proc.getInputStream());
    			
    			String out = "";
    			
    			long startTime = new Date().getTime();
    			while (true) {
    				out = error.forceAllOutput();
    				try {
    					if (!out.equals("") && proc.exitValue() != 0) {
    						isError = true;
    						break;
    					}
    				} catch (IllegalThreadStateException ex) { }
    				if (expect != null) {
    					out = output.forceAllOutput();
    					if (out != "" && out.indexOf(expect) != -1) {
    						int nr = getStringOccurences(out, expect);
    						if (nr >= howManyTimes) {
    							isError = false;
    							break;
    						}
    					}
    				}
    				long endTime = new Date().getTime();
    				if (endTime - startTime > timeout) {
    					isError = true;
    					break;
    				}
    				Thread.sleep(100);
    			}
    			
    			proc.destroy();
    			proc.waitFor();
    			
    			if (out.equals(""))
    				out = output.forceAllOutput();
    			
//    			String ret = "";
//    			
//    			if (!error.getOutput().equals(""))
//    				ret = error.forceAllOutput();
//    			
//    			ret = output.forceAllOutput();
    			
    			error.stopIt();
    			output.stopIt();
    			
    			addStreamRealGobbler(error);
    			addStreamRealGobbler(output);
    			
    			error = null;
    			output = null;
    			
    			return out;
    			
    		} catch (Exception e) {
    			e.printStackTrace();
    			
    			if (error != null) {
    				addStreamRealGobbler(error);
    				error.stopIt();
    				error = null;
    			}
    			
    			if (output != null) {
    				addStreamRealGobbler(output);
    				output.stopIt();
    				output = null;
    			}
    			isError = true;
    			
    			return "";
    		}
    	}
    	
    	public StreamGobbler getStreamGobbler() {
    		
    		synchronized (streams) {
    			if (streams.size() == 0) {
    				StreamGobbler stream = new StreamGobbler(null);
    				stream.start();
    				return stream;
    			}
    			return (StreamGobbler)streams.removeFirst();
    		}
    	}
    	
    	public void addStreamGobbler(StreamGobbler stream) {
    		
    		synchronized (streams) {
    			streams.addLast(stream);
    		}
    	}
    	
    	public StreamRealGobbler getStreamRealGobbler() {
    		
    		synchronized (streamsReal) {
    			if (streamsReal.size() == 0) {
    				StreamRealGobbler stream = new StreamRealGobbler(null);
    				stream.start();
    				return stream;
    			}
    			return (StreamRealGobbler)streamsReal.removeFirst();
    		}
    	}
    	
    	public void addStreamRealGobbler(StreamRealGobbler stream) {
    		
    		synchronized (streamsReal) {
    			streamsReal.addLast(stream);
    		}
    	}
    	
    	class StreamGobbler extends Thread {
    		
    		InputStream is;
    		String output = "";
    		boolean stop = false;
    		boolean doneReading = false;
    		
    		public StreamGobbler(InputStream is) {
    		
    			super("Stream Gobler");
    			this.is = is;
    		}
    		
    		public void setInputStream(InputStream is) {
    			
    			this.is = is;
    			output = "";
    			stop = false;
    			synchronized (this) {
    				doneReading = false;
    				notify();
    			}
    		}
    		
    		public String getOutput() {
    			
    			return output;
    		}
    		
    		public synchronized String forceAllOutput() {
    			
    			if (!doneReading)
    				return "";
    			doneReading = false;
    			return output;
    		}
    		
    		public void stopIt() {
    			
    			stop = true;
    		}
    		
    		public void run() {
    			
    			while (true) {
    				
    				synchronized (this) {
    					while (is == null) {
    						try {
    							wait();
    						} catch (Exception e) { }
    					}
    				}
    				
    				try {
    					InputStreamReader isr = new InputStreamReader(is);
    					BufferedReader br = new BufferedReader(isr);
    					String line=null;
    					while (!stop && (line = br.readLine()) != null) {
    						output += line;    
    					}
    					synchronized (this) {
    						doneReading = true;
    					}
    					is.close();
    				} catch (Exception ioe) {
    					output = "";
    				}
    				is = null;
    			}
    		}
    	}

    	class StreamRealGobbler extends Thread {
    		
    		InputStream is;
    		String output = "";
    		boolean stop = false;
    		boolean doneReading = false;
    		
    		public StreamRealGobbler(InputStream is) {
    			
    			super("Stream Real Gobler");
    			this.is = is;
    		}
    		
    		public void setInputStream(InputStream is) {
    			
    			this.is = is;
    			output = "";
    			stop = false;
    			synchronized (this) {
    				doneReading = false;
    				notify();
    			}
    		}
    		
    		public String getOutput() {
    			
    			return output;
    		}
    		
    		public synchronized String forceAllOutput() {
    			
    			if (!doneReading)
    				return "";
    			return output;
    		}
    		
    		public void stopIt() {
    			
    			stop = true;
    		}
    		
    		public void run() {
    			
    			while (true) {
    				
    				synchronized (this) {
    					while (is == null) {
    						try {
    							wait();
    						} catch (Exception e) { }
    					}
    				}
    				
    				try {
    					InputStreamReader isr = new InputStreamReader(is);
    					BufferedReader br = new BufferedReader(isr);
    					String line=null;
    					while (!stop && (line = br.readLine()) != null) {
//    						System.out.println(line);
    						output += line+"\n";    
    					}
    					synchronized (this) {
    						doneReading = true;
    					}
    				} catch (Exception ioe) {
    					output = "";
    				}
    				is = null;
    			}
    		}
    	}
    	
    }

    
    class Parser {
    	private StringTokenizer st = null;
    	private StringTokenizer auxSt = null;
    	
    	public Parser() {
    	}
    	
    	public void parse(String text) {
    		st = new StringTokenizer(text);
    	}
    	
    	public void parseAux(String text) {
    		auxSt = new StringTokenizer(text);
    	}
    	
    	public void parseFromFile(final String fileName) {
    		try {
    			final BufferedReader reader = new BufferedReader(new FileReader(fileName));
    			String line;
			final StringBuilder sb = new StringBuilder();
			
    			while ((line = reader.readLine()) != null)
    				sb.append(line).append("\n");
				
    			st = new StringTokenizer(sb.toString());
			
			reader.close();
    		}
		catch (Exception e) {
    			st = null;
    		}
    	}
    	
    	public String nextLine() {
    		if (st == null) return null;
    		try {
    			return st.nextToken("\n");
    		} catch (Exception e) {
    			return null;
    		}
    	}
    	
    	public String nextAuxLine() {
    		if (auxSt == null) return null;
    		try {
    			return auxSt.nextToken("\n");
    		} catch (Exception e) {
    			return null;
    		}
    	}
    	
    	public String nextToken() {
    		if (st == null) return "";
    		try {
    			return st.nextToken();
    		} catch (Exception e) {
    			return null;
    		}
    	}
    	
    	public String nextToken(String token) {
    		if (st == null) return "";
    		try {
    			return st.nextToken(token);
    		} catch (Exception e) {
    			return null;
    		}
    	}
    	
    	public String nextAuxToken() {
    		if (auxSt == null) return "";
    		try {
    			return auxSt.nextToken();
    		} catch (Exception e) {
    			return null;
    		}
    	}
    	
    	public String nextAuxToken(String token) {
    		if (auxSt == null) return "";
    		try {
    			return auxSt.nextToken(token);
    		} catch (Exception e) {
    			return null;
    		}
    	}
    	
    	public String getTextAfterToken(String text, String start) {
    		if (text.indexOf(start) == -1) return null;
    		return text.substring(text.indexOf(start)+start.length());
    	}
    	
    	public String getTextBeforeToken(String text, String end) {
    		if (text.indexOf(end) == -1) return text;
    		return text.substring(0, text.indexOf(end));
    	}
    	
    	public String getTextBetween(String text, String start, String end) {
    		if (text.indexOf(start) == -1) return null;
    		text = text.substring(text.indexOf(start)+start.length());
    		if (text.lastIndexOf(end) == -1) return text;
    		return text.substring(0, text.lastIndexOf(end));
    	}
    	
    	public String[] listFiles(String directory) {
    		String[] fileList = null;
    		try {
    			File dir = new File(directory);
    			if (!dir.isDirectory()) return null;
    			File[] list = dir.listFiles();
    			if (list == null) return null;
    			fileList = new String[list.length];
    			for (int i=0; i<list.length; i++)
    				fileList[i] = list[i].getName();
    		} catch (Exception e) {
    			return null;
    		}
    		return fileList;
    	}
    	
    } // end of class Parser

// start of ProcReader class
	private cmdExec exec = null;
	private Parser parser = null;
	private String[] netInterfaces = null;
	private String hwAddress = null;
	private String cpuUsr = null;
	private long dcpuUsr = 0;
	private String cpuNice = null;
	private long dcpuNice = 0;
	private String cpuSys = null;
	private long dcpuSys = 0;
	private String cpuIdle = null;
	private long dcpuIdle = 0;
	private String cpuUsage = null;
	private String pagesIn = null;
	private long dpagesIn = 0;
	private String pagesOut = null;
	private long dpagesOut = 0;
	private String memTotal = null;
	private String memUsage = null;
	private String memUsed = null;
	private String memFree = null;
	private String diskIO = null;
//	private long ddiskIO = 0;
	private String diskTotal = null;
	private String diskUsed = null;
	private String diskFree = null;
	private String diskUsage = null;
	private String processesNo = null;
	private String load1 = null;
	private String load5 = null;
	private String load15 = null;
	private Hashtable netIn = null;
	private Hashtable dnetIn = null;
	private Hashtable netOut = null;
	private Hashtable dnetOut = null;
	
	private long lastCall = 0;
	
	public ProcReader() {
		
		netIn = new Hashtable();
		dnetIn = new Hashtable();
		netOut = new Hashtable();
		dnetOut = new Hashtable();
		exec  = new cmdExec();
		parser = new Parser();
		update();
	}
	
	private void addNetInterface(String netInterface) {
		
		if (netInterface == null || netInterface.equals(""))
			return;
		netInterface = netInterface.trim();
		if (netInterfaces == null) {
			netInterfaces = new String[1];
			netInterfaces[0] = netInterface;
			return;
		}
		for (int i=0; i<netInterfaces.length; i++)
			if (netInterface.equals(netInterfaces[i])) return;
		String[] tmpNetInterfaces = new String[netInterfaces.length+1];
		System.arraycopy(netInterfaces, 0, tmpNetInterfaces, 0, netInterfaces.length);
		tmpNetInterfaces[netInterfaces.length] = netInterface;
		netInterfaces = tmpNetInterfaces;
	}
	
	public synchronized void update() {
		
		long newCall = NTPDate.currentTimeMillis();
		double diffCall = (newCall - lastCall) / 1000.0; // in seconds
		
		String str = null;
		String output = "";
		String line = "";
		
		if (hwAddress == null) {
			output= exec.executeCommandReality("ifconfig -a", "b");
			if (exec.isError())
				output = null;
			
			if (output != null && output.length() != 0) {
				netInterfaces = null;
				parser.parse(output);
				line = parser.nextLine();
				hwAddress = null;
				while (line != null) {
					if (line.startsWith(" ") || line.startsWith("\t")) {
						line = parser.nextLine();
						continue;
					}
					if (line == null) break;
					parser.parseAux(line);
					// get the name
					String netName = parser.nextAuxToken(" \t\n");
					if (netName != null && !netName.equals("")) {
						addNetInterface(netName);
					}
					// get the hw address
					//			str = parser.nextAuxToken("HWaddr");
					str = parser.getTextAfterToken(line, "HWaddr ");
					if (str != null) {
						hwAddress = str;
					}
					line = parser.nextLine();
				}
			}
		}
		
		pagesIn = pagesOut = null;
		cpuUsage = cpuUsr = cpuIdle = cpuNice = cpuSys = diskIO = null;
		parser.parseFromFile("/proc/stat");
		line = parser.nextLine();
		while (line != null) {
			if (line.startsWith("page")) {
				line = parser.getTextAfterToken(line, "page ");
				parser.parseAux(line);
				pagesIn = parser.nextAuxToken();
				pagesOut = parser.nextAuxToken();
				long dpIn = 0, dpOut = 0;
				try {
					dpIn = Long.parseLong(pagesIn);
				} catch (Exception e) {
					dpIn = -1;
				}
				try {
					dpOut = Long.parseLong(pagesOut);
				} catch (Exception e) {
					dpOut = -1;
				}
				if (dpIn >= 0) {
					pagesIn = ""+((dpIn - dpagesIn) / diffCall);
					dpagesIn = dpIn;
				}
				if (dpOut >= 0) {
					pagesOut = ""+((dpOut - dpagesOut) / diffCall);
					dpagesOut = dpOut;
				}
			}
			if (line.startsWith("cpu") && cpuUsr == null) {
				line = parser.getTextAfterToken(line, "cpu ");
				parser.parseAux(line);
				long dcUsr = 0, dcSys = 0, dcNice = 0, dcIdle = 0;
				line = parser.nextAuxToken(); // cpu usr
				try {
					dcUsr = Long.parseLong(line);
				} catch (Exception e) {
					dcUsr = -1;
				}
				line = parser.nextAuxToken(); // cpu nice
				try {
					dcNice = Long.parseLong(line);
				} catch (Exception e) {
					dcNice = -1;
				}
				line = parser.nextAuxToken(); // cpu sys
				try {
					dcSys = Long.parseLong(line);
				} catch (Exception e) {
					dcSys = -1;
				}
				line = parser.nextAuxToken(); // cpu idle
				try {
					dcIdle = Long.parseLong(line);
				} catch (Exception e) {
					dcIdle = -1;
				}
				double tmpUsr = (dcUsr - dcpuUsr) / diffCall;
				double tmpSys = (dcSys - dcpuSys) / diffCall;
				double tmpIdle = (dcIdle - dcpuIdle) / diffCall;
				double tmpNice = (dcNice - dcpuNice) / diffCall;
				if (tmpUsr >= 0.0 && tmpSys >= 0.0 && tmpIdle >= 0.0 && tmpNice >= 0.0) {
					dcpuUsr = dcUsr;
					dcpuSys = dcSys;
					dcpuNice = dcNice;
					dcpuIdle = dcIdle;
					double dcTotalP = tmpUsr+tmpSys + tmpNice;
					double dcTotal = dcTotalP + tmpIdle;
					cpuUsr = ""+(100.0 * tmpUsr / dcTotal);
					cpuSys = ""+(100.0 * tmpSys / dcTotal);
					cpuNice = ""+(100.0 * tmpNice / dcTotal);
					cpuIdle = ""+(100.0 * tmpIdle / dcTotal);
					cpuUsage = ""+(100.0 * dcTotalP / dcTotal);
				}
			}
			line = parser.nextLine();
		}
	
// the iostat part is disabled
/*		output = exec.executeCommandReality(System.getProperty("user.home")+"/iostat -k", "L");
		if (exec.isError())
			output = null;
		if (output != null && !output.equals("")) {
			parser.parse(output);
			line = parser.nextLine();
			while (line != null && line.indexOf("avg-cpu") == -1)
				line = parser.nextLine();
			if (line != null && cpuUsr == null) {
				str = parser.nextToken(" \t\n");
				if (str != null) cpuUsr = str;
				str = parser.nextToken(" \t\n");
				if (str != null) cpuNice = str;
				str = parser.nextToken(" \t\n");
				if (str != null) cpuSys = str;
				str = parser.nextToken(" \t\n");
				str = parser.nextToken(" \t\n");
				if (str != null) cpuIdle = str;
				double dcUsr = 0.0, dcSys = 0.0, dcNice = 0.0, dcIdle = 0.0, d = -1.0;
				try {
					d = Double.parseDouble(cpuUsr);
				} catch (Exception e) {
					d = -1.0;
				}
				if (d >= 0.0)
					dcUsr = d;
				try {
					d = Double.parseDouble(cpuSys);
				} catch (Exception e) {
					d = -1.0;
				}
				if (d >= 0.0)
					dcSys = d;
				try {
					d = Double.parseDouble(cpuIdle);
				} catch (Exception e) {
					d = -1.0;
				}
				if (d >= 0.0)
					dcIdle = d;
				try {
					d = Double.parseDouble(cpuNice);
				} catch (Exception e) {
					d = -1.0;
				}
				if (d >= 0.0)
					dcNice = d;
				if ((dcUsr + dcSys + dcNice + dcIdle) != 0.0)
					cpuUsage = ""+(dcUsr + dcSys + dcNice);
				str = parser.nextToken(" \t\n");
				while (str != null && str.indexOf("Device:") == -1) str = parser.nextToken(" \t\n");
				if (str != null) {
					for (int i=0; i<5 && str != null; i++) str = parser.nextToken(" \t\n");
					long blkRead = 0, blkWrite = 0; 
					while (true) {
						str = parser.nextToken(" \t\n");
						if (str == null) break;
						str = parser.nextToken(" \t\n"); // skip tps
						str = parser.nextToken(" \t\n"); // skip KB read / sec
						str = parser.nextToken(" \t\n"); // skip KB write / sec
						str = parser.nextToken(" \t\n"); // blk read / sec
						long l = 0;
						try {
							l = Long.parseLong(str);
						} catch (Exception e) {
							l = -1;
						}
						if (l >= 0)
							blkRead += l;
						str = parser.nextToken(" \t\n"); // blk written / sec
						l = 0;
						try {
							l = Long.parseLong(str);
						} catch (Exception e) {
							l = -1;
						}
						if (l >= 0.0)
							blkWrite += l;
					}
					diskIO = ""+((blkRead + blkWrite - ddiskIO) / diffCall);
					ddiskIO = blkRead + blkWrite;
				}
			} else if (line != null){
				str = parser.nextToken(" \t\n");
				while (str != null && str.indexOf("Device:") == -1) str = parser.nextToken(" \t\n");
				if (str != null) {
					for (int i=0; i<5 && str != null; i++) str = parser.nextToken(" \t\n");
					long blkRead = 0, blkWrite = 0; 
					while (true) {
						str = parser.nextToken(" \t\n");
						if (str == null) break;
						str = parser.nextToken(" \t\n"); // skip tps
						str = parser.nextToken(" \t\n"); // skip KB read / sec
						str = parser.nextToken(" \t\n"); // skip KB write / sec
						str = parser.nextToken(" \t\n"); // blk read / sec
						long l = 0;
						try {
							l = Long.parseLong(str);
						} catch (Exception e) {
							l = -1;
						}
						if (l >= 0)
							blkRead += l;
						str = parser.nextToken(" \t\n"); // blk written / sec
						l = 0;
						try {
							l = Long.parseLong(str);
						} catch (Exception e) {
							l = -1;
						}
						if (l >= 0)
							blkWrite += l;
					}
					diskIO = ""+((blkRead + blkWrite - ddiskIO) / diffCall);
					ddiskIO = blkRead + blkWrite;
				}
			}
		}
*/		
		memTotal = memUsage = memFree = memUsed = null;
		parser.parseFromFile("/proc/meminfo");
		line = parser.nextLine();
		double dmemTotal = 0.0, dmemFree = 0.0;
		while (line != null) {
			if (line.startsWith("MemTotal")) {
				line = parser.getTextAfterToken(line, "MemTotal:");
				parser.parseAux(line);
				memTotal = parser.nextAuxToken();
				double d = 0.0;
				try {
					d = Double.parseDouble(memTotal);
				} catch (Exception e) {
					d = -1.0;
				}
				if (d >= 0.0) 
					dmemTotal = d;
			}
			if (line.startsWith("MemFree")) {
				line = parser.getTextAfterToken(line, "MemFree:");
				parser.parseAux(line);
				memFree = parser.nextAuxToken();
				double d = 0.0;
				try {
					d = Double.parseDouble(memFree); 
				} catch (Exception e) {
					d = -1.0;
				}
				if (d >= 0.0)
					dmemFree = d;
			}
			line = parser.nextLine();
		}
		memFree = ""+(dmemFree / 1024.0);
		memUsed = ""+(dmemTotal / 1024.0);
		memUsage = ""+(100.0 * (dmemTotal - dmemFree) / dmemTotal);
		memTotal = "" + (dmemTotal / 1024.0);
		output = exec.executeCommandReality("df -B 1024", "o");
		if (exec.isError())
			output = null;
		double size = 0.0, used = 0.0, available = 0.0, usage = 0.0;
		if (output != null && output != "") {
			parser.parse(output);
			line = parser.nextToken(" \t\n");
			int nr = 0;
			for (int i=0; i<6 && line != null; i++) line = parser.nextToken(" \t\n");
			while (true) {
				line = parser.nextToken(" \t\n");
				if (line == null) break;
				line = parser.nextToken(" \t\n"); // size
				double d = 0.0;
				try {
					d = Double.parseDouble(line);
				} catch (Exception e) {
					d = -1.0;
				}
				if (d < 0.0) break;
				size += d;
				line = parser.nextToken(" \t\n"); // used
				d = 0.0;
				try {
					d = Double.parseDouble(line);
				} catch (Exception e) {
					d = -1.0;
				}
				if (d < 0.0) break;
				used += d;
				line = parser.nextToken(" \t\n"); // available
				d = 0.0;
				try {
					d = Double.parseDouble(line);
				} catch (Exception e) {
					d = -1.0;
				}
				if (d < 0.0) break;
				available += d;
				line = parser.nextToken(" \t\n"); // usage
				line = parser.getTextBeforeToken(line, "%");
				d = 0.0;
				try {
					d = Double.parseDouble(line);
				} catch (Exception e) {
					d = -1.0;
				}
				if (d < 0.0) break;
				usage += d;
				nr++;
				line = parser.nextToken(" \t\n");
				if (line == null) break;
			}
			diskTotal = ""+(size / (1024.0 * 1024.0)); // total size (GB)
			diskUsed = ""+(used / (1024.0 * 1024.0)); // used size (GB)
			diskFree = ""+(available / (1024.0 * 1024.0)); // free size (GB)
			diskUsage = ""+(usage * 1.0 / nr); // usage (%)
		} else { // read from /proc/ide
			String files[] = parser.listFiles("/proc/ide");
			if (files != null &&files.length != 0) 
				for (int i=0; i<files.length; i++) 
					if (files[i].startsWith("hd")) {
						parser.parseFromFile("/proc/ide/"+files[i]+"/capacity");
						line = parser.nextLine();
						double  d = 0.0;
						try {
							d = Double.parseDouble(line);
						} catch (Exception e) {
							d = -1.0;
						}
						if (d >= 0.0)
							size += d;
					}
			diskTotal = ""+(size / (1024.0 * 1024.0)); // disk total (GB)
			diskFree = diskTotal; 
		}
		
		String[] files = parser.listFiles("/proc");
		processesNo = null;
		if (files != null && files.length != 0) {
			int nr = 0;
			for (int i=0; i<files.length; i++) {
				char[] chars = files[i].toCharArray();
				boolean isProc = true;
				for (int j=0; j<chars.length; j++)
					if (!Character.isDigit(chars[j])) {
						isProc = false;
						break;
					}
				if (isProc)
					nr++;
			}
			processesNo = ""+nr;
		}
		
		parser.parseFromFile("/proc/loadavg");
		load1 = load5 = load15 = null;
		line = parser.nextToken(" \t\n"); // load1
		if (line != null) {
			double d = 0.0;
			try {
				d = Double.parseDouble(line);
			} catch (Exception e) {
				d = -1.0;
			}
			if (d >= 0.0)
				load1 = ""+d;
			line = parser.nextToken(" \t\n"); // load5
			d = 0.0;
			try {
				d = Double.parseDouble(line);
			} catch (Exception e) {
				d = -1.0;
			}
			if (d >= 0.0)
				load5 = ""+d;
			d = 0.0;
			try {
				d = Double.parseDouble(line);
			} catch (Exception e) {
				d = -1.0;
			}
			if (d >= 0.0)
				load15 = ""+d;
		}
		
		parser.parseFromFile("/proc/net/dev");
		if (netInterfaces == null) {
			while (true) {
				line = parser.nextToken(":\n\t ");
				if (line == null) break;
				if (line.startsWith("eth") || line.startsWith("lo")) {
					addNetInterface(line);
					String name = line;
					line = parser.nextToken(" \t\n"); // bytes received
					long d = 0;
					long oldReceived = 0;
					if (dnetIn.containsKey(name)) {
						try {
							oldReceived = ((Long)dnetIn.get(name)).longValue();
						} catch (Exception e) {
							oldReceived = -1;
						}
						try {
							d = Long.parseLong(line);
						} catch (Exception e) {
							d = -1;
						}
						if (oldReceived >= 0 && d >= 0) {
							double in = (d - oldReceived) / diffCall;
							in = in / (1000.0 * 1000.0);
							oldReceived = d;
							netIn.put(name, ""+in);
							dnetIn.put(name, Long.valueOf(oldReceived));
						}
					} else {
						d = 0;
						try {
							d = Long.parseLong(line);
						} catch (Exception e) {
							d = -1;
						}
						if (d >= 0) {
							netIn.put(name, ""+d);
							dnetIn.put(name, Long.valueOf(d));
						}
					}
					line = parser.nextToken(" \t\n"); // packets received
					for (int i=0; i<6; i++) line = parser.nextToken(" \t\n");
					line = parser.nextToken(" \t\n"); // bytes sent 
					d = 0;
					long oldSent = 0;
					if (dnetOut.containsKey(name)) {
						try {
							oldSent = ((Long)dnetOut.get(name)).longValue();
						} catch (Exception e) {
							oldSent = -1;
						}
						try {
							d = Long.parseLong(line);
						} catch (Exception e) {
							d = -1;
						}
						if (oldSent >= 0&& d >= 0) {
							double out = (d - oldSent) / diffCall;
							out = out / (1000.0 * 1000.0);
							oldSent = d;
							netOut.put(name, ""+out);
							dnetOut.put(name, Long.valueOf(oldSent));
						}
					} else {
						d = 0;
						try {
							d = Long.parseLong(line);
						} catch (Exception e) {
							d = -1;
						}
						if (d >= 0) {
							netOut.put(name, ""+d);
							dnetOut.put(name, Long.valueOf(d));
						}
					}
					line = parser.nextToken(" \t\n"); // packets sent
				}
			}
		} else {
			while (true) {
				line = parser.nextToken(":\n\t ");
				if (line == null) break;
				boolean found = false;
				for (int i=0; i<netInterfaces.length; i++)
					if (line.equals(netInterfaces[i])) {
						found = true;
						break;
					}
				if (found) {
					String name = line;
					line = parser.nextToken(" \t\n:"); // bytes received
					long d = 0;
					long oldReceived = 0;
					if (dnetIn.containsKey(name)) {
						try {
							oldReceived = ((Long)dnetIn.get(name)).longValue();
						} catch (Exception e) {
							oldReceived = -1;
						}
						try {
							d = Long.parseLong(line);
						} catch (Exception e) {
							d = -1;
						}
						if (oldReceived >= 0 && d >= 0) {
							double in = (d - oldReceived) / diffCall;
							in = in / (1000.0 * 1000.0);
							oldReceived = d;
							netIn.put(name, ""+in);
							dnetIn.put(name, Long.valueOf(oldReceived));
						}
					} else {
						d = 0;
						try {
							d = Long.parseLong(line);
						} catch (Exception e) {
							d = -1;
						}
						if (d >= 0) {
							netIn.put(name, ""+d);
							dnetIn.put(name, Long.valueOf(d));
						}
					}
					line = parser.nextToken(" \t\n"); // packets received
					for (int i=0; i<6; i++) line = parser.nextToken(" \t\n");
					line = parser.nextToken(" \t\n"); // bytes sent 
					d = 0;
					long oldSent = 0;
					if (dnetOut.containsKey(name)) {
						try {
							oldSent = ((Long)dnetOut.get(name)).longValue();
						} catch (Exception e) {
							oldSent = -1;
						}
						try {
							d = Long.parseLong(line);
						} catch (Exception e) {
							d = -1;
						}
						if (oldSent >= 0 && d >= 0) {
							double out = (d - oldSent) / diffCall;
							out = out / (1000.0 * 1000.0);
							oldSent = d;
							netOut.put(name, ""+out);
							dnetOut.put(name, Long.valueOf(oldSent));
						}
					} else {
						d = 0;
						try {
							d = Long.parseLong(line);
						} catch (Exception e) {
							d = -1;
						}
						if (d >= 0) {
							netOut.put(name, ""+d);
							dnetOut.put(name, Long.valueOf(d));
						}
					}
					line = parser.nextToken(" \t\n"); // packets sent
				}
			}
		}
		
		lastCall = newCall;
	}
	
	public synchronized String getMacAddress() {
		
		if (hwAddress != null) hwAddress = hwAddress.trim();
		return hwAddress;
	}
	
	public synchronized String getCPUUsage() {
		
		if (cpuUsage != null) cpuUsage = cpuUsage.trim();
		return cpuUsage;
	}
	
	public synchronized String getCPUUsr() {
		
		if (cpuUsr != null) cpuUsr = cpuUsr.trim();
		return cpuUsr;
	}
	
	public synchronized String getCPUSys() {
		
		if (cpuSys != null) cpuSys = cpuSys.trim();
		return cpuSys;
	}
	
	public synchronized String getCPUNice() {
		
		if (cpuNice != null) cpuNice = cpuNice.trim();
		return cpuNice;
	}
	
	public synchronized String getCPUIdle() {
		
		if (cpuIdle != null) cpuIdle = cpuIdle.trim();
		return cpuIdle;
	}
	
	public synchronized String getPagesIn() {
		
		if (pagesIn != null) pagesIn = pagesIn.trim();
		return pagesIn;
	}
	
	public synchronized String getPagesOut() {
		
		if (pagesOut != null) pagesOut = pagesOut.trim();
		return pagesOut;
	}
	
	public synchronized String getMemUsage() {
		
		if (memUsage != null) memUsage = memUsage.trim();
		return memUsage;
	}
	
	public synchronized String getMemUsed() {
		
		if (memUsed != null) memUsed = memUsed.trim();
		return memUsed;
	}
	
	public synchronized String getMemFree() {
		
		if (memFree != null) memFree = memFree.trim();
		return memFree;
	}
	
	public synchronized String getMemTotal() {
		
		if (memTotal != null) memTotal = memTotal.trim();
		return memTotal;
	}
	
	public synchronized String getDiskIO() {
		
		if (diskIO != null) diskIO = diskIO.trim();
		return diskIO;
	}
	
	public synchronized String getDiskTotal() {
		
		if (diskTotal != null) diskTotal = diskTotal.trim();
		return diskTotal;
	}
	
	public synchronized String getDiskUsed() {
		
		if (diskUsed != null) diskUsed = diskUsed.trim();
		return diskUsed;
	}
	
	public synchronized String getDiskFree() {
		
		if (diskFree != null) diskFree = diskFree.trim();
		return diskFree;
	}
	
	public synchronized String getDiskUsage() {
		
		if (diskUsage != null) diskUsage = diskUsage.trim();
		return diskUsage;
	}
	
	public synchronized String getNoProcesses() {
		
		if (processesNo != null) processesNo = processesNo.trim();
		return processesNo;
	}
	
	public synchronized String getLoad1() {
		
		if (load1 != null) load1 = load1.trim();
		return load1;
	}
	
	public synchronized String getLoad5() {
		
		if (load5 != null) load5 = load5.trim();
		return load5;
	}
	
	public synchronized String getLoad15() {
		
		if (load15 != null) load15 = load15.trim();
		return load15;
	}
	
	public synchronized String[] getNetInterfaces() {
		return netInterfaces;
	}
	
	public synchronized String getNetIn(String netInterface) {
		
		if (netIn.containsKey(netInterface)) {
			String str = (String)netIn.get(netInterface);
			if (str != null) str = str.trim();
			return str;
		}
		return null;
	}
	
	public synchronized String getNetOut(String netInterface) {
		
		if (netOut.containsKey(netInterface)) {
			String str = (String)netOut.get(netInterface);
			if (str != null) str = str.trim();
			return str;
		}
		return null;
	}
	
	public static void main(String[] args) {
		
		ProcReader reader = new ProcReader();
		while (true) {
			reader.update();
			System.out.println("");
			System.out.println("CPU Sys: "+reader.getCPUSys());
			System.out.println("CPU Usr: "+reader.getCPUUsr());
			System.out.println("CPU Nice: "+reader.getCPUNice());
			System.out.println("CPU Idle: "+reader.getCPUIdle());
			System.out.println("CPU Usage: "+reader.getCPUUsage());
			System.out.println("");
			System.out.println("Pages in: "+reader.getPagesIn());
			System.out.println("Pages out: "+reader.getPagesOut());
			System.out.println("");
			System.out.println("Mem usage: "+reader.getMemUsage());
			System.out.println("Mem used: "+reader.getMemUsed());
			System.out.println("Mem free: "+reader.getMemFree());
			System.out.println("Mem total: "+reader.getMemTotal());
			System.out.println("");
			System.out.println("Disk total: "+reader.getDiskTotal());
			System.out.println("Disk used: "+reader.getDiskUsed());
			System.out.println("Disk free: "+reader.getDiskFree());
			System.out.println("Disk usage: "+reader.getDiskUsage());
			System.out.println("Disk IO: "+reader.getDiskIO());
			System.out.println("");
			System.out.println("Processes: "+reader.getNoProcesses());
			System.out.println("Load1: "+reader.getLoad1());
			System.out.println("Load5: "+reader.getLoad5());
			System.out.println("Load15: "+reader.getLoad15());
			System.out.println("");
			System.out.println("MAC: "+reader.getMacAddress());
			System.out.println("Net IFS");
			String netIfs[] = reader.getNetInterfaces();
			if (netIfs != null) {
				for (int i=0; i<netIfs.length; i++)
					System.out.print(netIfs[i]+" ");
				System.out.println("");
				System.out.println("Net in");
				for (int i=0; i<netIfs.length; i++)
					System.out.print(reader.getNetIn(netIfs[i])+" ");
				System.out.println("");
				System.out.println("Net out");
				for (int i=0; i<netIfs.length; i++)
					System.out.print(reader.getNetOut(netIfs[i])+" ");
				System.out.println("");
			}
			System.out.println("");
			try {
				Thread.sleep(1000);
			} catch (Exception e) {
			}
		}
	}



    
}
