

package lia.Monitor.ClientsFarmProxy.Monitor;

import lia.Monitor.monitor.AppConfig;

public class HostPropertiesMonitor {
	
	static {
		System.load(AppConfig.getProperty("lia.Monitor.ClientsFarmProxy.monLibrary","/home/monalisa/SRC/CVS/MSRC/MonaLisa/ProxyService/lib/libsystem.so"));
	} // static
	
	public native String getMacAddresses();
	
	public native void update();
	
	public native String getCpuUsage();

	public native String getCpuUSR();
	
	public native String getCpuSYS();
	
	public native String getCpuNICE();
	
	public native String getCpuIDLE();
	
	public native String getPagesIn();
	
	public native String getPagesOut();
	
	public native String getMemUsage();
	
	public native String getMemUsed();
	
	public native String getMemFree();
	
	public native String getDiskIO();
	
	public native String getDiskTotal();
	
	public native String getDiskUsed();
	
	public native String getDiskFree();
	
	public native String getNoProcesses();
	
	public native String getLoad1();
	
	public native String getLoad5();
	
	public native String getLoad15();
	
	public native String[] getNetInterfaces();

	public native String getNetIn(String ifName);
	
	public native String getNetOut(String ifName);
	
} // end of class HostPropertiesMonitor

