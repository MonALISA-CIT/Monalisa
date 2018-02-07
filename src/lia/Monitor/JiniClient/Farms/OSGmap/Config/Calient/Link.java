package lia.Monitor.JiniClient.Farms.OSGmap.Config.Calient;


/**
 * A class used to sustain information regarding one specific TE link (see Calient GMPLS manual for more info).
 */
public class Link {

	public String name;
	public String localIP;
	public String remoteIP;
	public String localRID;
	public String remoteRID;
	public String linkType;
	public String adj;
	public String wdmAdj;
	public String localIFIndex;
	public String remoteIF;
	public String wdmRemoteIF;
	public boolean fltDetect;
	public String metric;
	public String lmpVerify;
	public String port;
	
	public Link(String name) {
		this.name = name;
	}
	
	public void set(String localIP, String remoteIP, String localRid, String remoteRid, String linkType, String adj, String wdmAdj, String localIFIndex, String remoteIF, 
			String wdmRemoteIF, boolean fltDetect, String metric, String lmpVerify, String port) {
		
		this.localIP = localIP;
		this.remoteIP = remoteIP;
		this.localRID = localRid;
		this.remoteRID = remoteRid;
		this.linkType = linkType;
		this.adj = adj;
		this.wdmAdj = wdmAdj;
		this.localIFIndex = localIFIndex;
		this.remoteIF = remoteIF;
		this.wdmRemoteIF = wdmRemoteIF;
		this.fltDetect = fltDetect;
		this.metric = metric;
		this.lmpVerify = lmpVerify;
		this.port = port;
	}
	
} // end of class Link
