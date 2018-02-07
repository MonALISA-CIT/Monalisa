/*
 * Created on Mar 3, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI;

/**
 * @author mluc
 *
 * defines constants used for combobox in Farms.<bR>
 * The combo is updated and used by implementing the grafical interface.<br>
 * The combo choses between different types of loads and determins different
 * reprezentations for the farms on Globe map, World map and G map.
 */
public abstract class MFilter2Constants 
{
	public static final String[] acceptg = { "CPU_usr", "TotalIO_Rate_IN", "Load5", "FreeDsk" };
	public static final String cpuDes =
		"<html>\n <b>CPU ::</b>       <font color=#ffaaaa>Usr</font>  <font color=blue>Sys</font> <font color=green>Idl</font> <font color=red>Err</font>";
	public static final String ioDes =
		"<html>\n <b>IO ratio :: </b>  <font color=#ffaaaa>IN</font> / <font color=blue>OUT</font> ";
	public static final String loadDes =
		"<html>\n <b>Load :: </b>  <font color=#ffaaaa> [>1.0]</font> <font color=blue>[0.5->1]</font><font color=green> [0->0.5] </font>  ";
	public static final String diskDes =
		"<html>\n <b>Disk Space :: </b>  <font color=#ffaaaa>Used</font> / <font color=green>Free</font> ";
	public static final String[] MenuAccept = { cpuDes, ioDes, loadDes, diskDes };

}
