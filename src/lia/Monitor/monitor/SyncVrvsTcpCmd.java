package lia.Monitor.monitor;

import java.io.BufferedReader;

import lia.Monitor.modules.VrvsUtil;
import lia.util.DynamicThreadPoll.SchJob;

public abstract class SyncVrvsTcpCmd extends SchJob implements java.io.Serializable {

	public MNode Node;
	public String TaskName;
	public MonModuleInfo info;
	public static boolean isRepetitive = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.debug", "false")).booleanValue();
	
	private boolean debug ;

	public SyncVrvsTcpCmd(String TaskName) {
		this.TaskName = TaskName;
		info = new MonModuleInfo();
	}

	public SyncVrvsTcpCmd() {
	}
	
	public boolean isRepetitive() {
		return isRepetitive;
	}

	public MonModuleInfo init(MNode Node, String param) {
		this.Node = Node;
		return info;
	}

	public MNode getNode() {
		return Node;
	}

	public String getClusterName() {
		return Node.getClusterName();
	}
	public String getFarmName() {
		return Node.getFarmName();
	}
	public String getTaskName() {
		return TaskName;
	}

	public BufferedReader procOutput(int port, String cmd) {
		return VrvsUtil.syncTcpCmd(Node.getName(), port, cmd);
	}
	
	public boolean stop() {
		return true;
	}

}