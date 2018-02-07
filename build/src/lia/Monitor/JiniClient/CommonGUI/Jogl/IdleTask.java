package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.util.TimerTask;

import lia.util.ntp.NTPDate;

/*
 * Created on 08.05.2004 16:59:57
 * Filename: IdleTask.java
 *
 */
/**
 * @author Luc
 *
 * IdleTask
 * triggered when app is idle<br>
 * checks to see what textures become visible and should be loaded and what should be
 * unloaded
 */
public class IdleTask extends TimerTask 
{
	public void run() {
        Thread.currentThread().setName(" ( ML ) - JOGL - IdleTask Timer Thread");
		//canvas.display();
		long currentIdleTime = NTPDate.currentTimeMillis();
//		System.out.println("startIdleTime="+Main.globals.startIdleTime+
//				" idleTime="+(currentIdleTime-Main.globals.startIdleTime)+
//				" min_idle_time="+Globals.IDLE_TIME+
//				" eyePos="+Main.globals.EyePosition[2]);
		if ( JoglPanel.globals.startIdleTime != -1 &&
			currentIdleTime-JoglPanel.globals.startIdleTime>=Globals.IDLE_TIME && 
			JoglPanel.globals.bIsIdle 
			/*Main.globals.EyePosition[2] <= Globals.CHANGE_RES_DEPTH*/ ) {
			//TODO: check the level of detail,
			//find textures in view frustum
			//set to load neccessary textures
		    //System.out.println("redraw textures, idle tzup!");
			JoglPanel.globals.bIsIdle = false;
			try {
			    Texture.zoomChanged();
			} catch ( Exception ex ) {
			    System.out.println("Error changing texture on zoom: ");
			    ex.printStackTrace();
			}
		};
	}
}
