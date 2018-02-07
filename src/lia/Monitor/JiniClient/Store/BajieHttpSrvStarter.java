package lia.Monitor.JiniClient.Store;

import com.BajieSoft.HttpSrv.jzHttpSrv;

/**
 * Start the Bajie Http Server. This is an alternative to Tomcat Starter.
 * 
 * @author catac
 */
public class BajieHttpSrvStarter extends Thread {
	
	/**
	 * 
	 */
	public static void startBajie(){
		(new BajieHttpSrvStarter()).start();
	}
	
	@Override
	public void run(){
		try{
		    sleep(1000*15);
		}
		catch (Exception e){
			// improbable interruption here
		}
		try{
			jzHttpSrv.main(new String[] {"-noui"});
		}catch(Throwable t){
			System.err.println("Error starting the Bajie Http Server:");
			t.printStackTrace();
		}
	}
}
