package lia.Monitor.Farm.ABPing;

public class PeerInfo {
	String peerHost;				// hostname of the peer
	ABPingFastReply pinger;
	Integer uid;					// unique id for the peer
	int port;
    
	long sendTime;					// time when last packet was sent to this peer
	
	private double rttSamples[];	// brute data received from pinging peers
	private double medRTTSamples[];	// medium rtt, result of computeRTT()
	private int lostPackets;
	
	double crtRTT;
	double crtJitter;
	double crtPacketLoss;
	double crtRTime;
	
	private int crt;				// current pos in the rttSamples[] circular buffer
		
	// constructor
	PeerInfo(ABPingFastReply pinger, String peerHost, int uid){
		this.peerHost = peerHost;
		this.pinger = pinger;
		this.uid = Integer.valueOf(uid);
		setConfig();
	}
		
	/**
	 * adjust sizes of rttSamples[], jitterSamples[] according to RTT_SAMPLES
	 * only if needed
	 */
	void setConfig(){
		if((rttSamples == null) || (rttSamples.length != pinger.RTT_SAMPLES)){
			rttSamples = new double[pinger.RTT_SAMPLES];
			medRTTSamples = new double[pinger.RTT_SAMPLES];
			for(int i = 0; i < rttSamples.length; i++)
				medRTTSamples[i] = rttSamples[i] = 0;
			lostPackets = 0;
			crt = 0;
		}
	}
		
	// add new RTT data
	void addRTT(double rtt){
		rttSamples[crt] = rtt;
		
		computeRTT();
		computeJitter();
		receivePacket();
		computePacketLoss();	
		crt = (1+crt) % rttSamples.length;
	}
	
	// computes RTT for last received data
	void computeRTT(){
		double sum = 0;
		double min = Double.MAX_VALUE;
		double max = Double.MIN_VALUE;
		
		for(int i = 0; i < rttSamples.length; i++){
			sum += rttSamples[i];
			min = (min < rttSamples[i] ? min : rttSamples[i]);
			max = (max > rttSamples[i] ? max : rttSamples[i]);
		}
		crtRTT = (sum - min - max)/(rttSamples.length - 2);
		medRTTSamples[crt] = crtRTT;
	}
	
	// computes the jitter in %
	void computeJitter(){
		double avg = 0;
		for(int i = 0; i < medRTTSamples.length; i++)
			avg += medRTTSamples[i];
		avg /= medRTTSamples.length;
		double delta = 0;
		if(Math.abs(avg) < 5){
			crtJitter = 0;			
			return;
		}
		for(int i = 0; i < rttSamples.length; i++)
			delta += Math.abs(medRTTSamples[i] - avg);
		crtJitter = delta/((medRTTSamples.length - 1) * avg);
	}
	
	// send packet is called when a packet is sent over the network
	void sendPacket(){
		lostPackets++;
		if(lostPackets > pinger.PKT_LOSS_MEM)
			lostPackets = pinger.PKT_LOSS_MEM;
	}
	
	// called when a reply packet is received
	void receivePacket(){
		lostPackets -= 2;
		if(lostPackets < 0)
			lostPackets = 0;
	}
	
	// computes packet loss in %
	void computePacketLoss(){
		crtPacketLoss = ((double)lostPackets)/pinger.PKT_LOSS_MEM;
		computeRTime();
	}
	
	// get the overall RTime
	void computeRTime(){
		crtRTime = pinger.OVERALL_COEF +
				pinger.RTT_COEF * crtRTT +
				pinger.JITTER_COEF * crtJitter +
				pinger.PKT_LOSS_COEF * crtPacketLoss;
	}
	
	public String toString(){
		return peerHost + " crt=" + crt + " rtt=" + crtRTT + " jitter=" + crtJitter + 
				" pktLoss=" + crtPacketLoss + " RTime=" + crtRTime;
	}
}