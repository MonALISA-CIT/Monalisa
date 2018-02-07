package lia.Monitor.Farm.ABPing;

import java.io.Serializable;

/**
 * ABPeerInfo stores info about a peer node
 * 
 */
public class ABPeerInfo implements Serializable{	
	/**
     * 
     */
    private static final long serialVersionUID = 3269959878482500387L;
    
    public String sender;	// packet original sender name 
	public String retFrom;	// packet returning from ...

	public long seqNr;		// packet sequence number
	public long awaitedSeqNr; // awaited packet seq. nr.
	public long timeDiff;	// time delta (time spent at peer)
	public long sendTime;	// the moment when the packet was sent

	public double loss;     // number of lost packages
	public double delay;    // ?! for now it's rtt/2...
	public double rtt;      // round trip time 
	public double jitter;   // differences in rtt times
	
	public double quality;	// overall representation of loss,delay?,rtt,jitter
	
	ABPeerInfo(String name, String peer){
		this.sender = name;
		this.retFrom = peer;
	}
	
	public static double QUAL_COEF = 0.1;
	
	public void computeQuality(){
		quality = 1000 / (1 + rtt + 7*loss + 8*jitter) * QUAL_COEF + (1 - QUAL_COEF) * quality;
		// correction if peer isn't active
		if(rtt == 0 && jitter == 0 && loss > 3) quality = 0;
	}
	
	public String toString() {
		return /*"sender= " + sender + */ "peer= " + retFrom + " seq= " + seqNr 
			+ " awSeq= " + awaitedSeqNr + " loss= " + loss 
			+ " rtt= " + rtt + " jitter= " + jitter + " qual=" + quality;  
	}
}
