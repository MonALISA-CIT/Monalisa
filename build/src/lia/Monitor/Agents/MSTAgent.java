
package lia.Monitor.Agents;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import lia.Monitor.ClientsFarmProxy.AgentsPlatform.AgentMessage;
import lia.Monitor.ClientsFarmProxy.AgentsPlatform.MSTAgentsMonitorClient;
import lia.Monitor.DataCache.AgentsCommunication;
import lia.util.ntp.NTPDate;


public class MSTAgent extends Agent {
	
	// messages types
	public final static int INITIATE_MSG=0 ;
	
	public final static int TEST_MSG = 1 ;
	public final static int TEST_ACCEPT=2 ;
	public final static int TEST_DENY = 3 ;
	
	public final static int GOT_BEST = 5 ;
	public final static int CHANGE_ROOT = 6 ;
	public final static int CONNECT_MSG = 7 ;
	public final static int NOEDGE_MSG = 8;
	public final static int TERMINATED_INT  = 9;
	public final static int TERMINATED_TRUE = 10;
	public final static int PUBLISH_REZ = 11;
	public final static int NEW_ITERATION = 12 ;
	
	public final static int SLEEPING_STATE =0 ;
	public final static int FINDING_STATE = 1 ;
	public final static int FOUND_STATE = 2 ;
	public final static int TERMINATED  = 3;
	public final static int PUBLISHING  = 3;
	
	String fragmentID ;
	int level ;
	
	String metric ;
	
	String father;
	Vector children;
	
	Vector waitingMessages;
	
	MSTBestEdge best = null ;
	Vector bestPath = new Vector();
	Hashtable connectEvents = new Hashtable();
	Vector connectedRequest = new Vector();

	Hashtable testedEdges ;
	int receivedBest = 0;
	
	private int state = 0;
	private int agentsNr = -1;
	private int messageID = -1;
	private int receivedTerMsg = 0;

	class CheckerMsg extends Thread {
		
		public void run() {
			while (true) {
				try {
					Thread.sleep(30000);
				} catch (Exception e) {}
				
				Vector v = new Vector();
				synchronized (waitingMessages) {
					v.addAll (waitingMessages);
					waitingMessages.clear();
				}	
				while (v.size()>0) {
					AgentMessage am = (AgentMessage)v.remove(0);
					processMsg(am);
				} // for
				
			} // while
		} // run
		
	} // CheckerMsg
	
	public MSTAgent(String agentName, String agentGroup, String farmID, String metric) {
		super (agentName,  agentGroup, farmID);
		level = 0;
		children = new Vector();
		waitingMessages = new Vector();
		testedEdges = new Hashtable();
		this.metric = metric ;
		this.father = null;
	} // TestAgent
	
	public void init(AgentsCommunication comm) {
		this.agentComm = comm;
	} // init
	
	public synchronized void processErrorMsg (Object msg) {
		System.out.println ("ERROR MESSAGE : "+msg);
	} // processErrorMsg
	
	public synchronized void processMsg(Object msg) {
		
		AgentMessage am = (AgentMessage) msg;
		Integer amType = am.messageType;
		
	
System.out.println ("MSTAgent "+am);		
		
		if ( am.agentAddrS.equals("proxy") && am.messageID.intValue() == messageID) { // a message from proxy
			try {				
				agentsNr = (new Integer ((String)am.message)).intValue();
System.out.println (" CONTROL_MSG ====> Received nr. of agents ... :)" + agentsNr);
			}catch (Exception e) {}

			return;
		}
		
		if (amType == null)
			return;
		
		// INITIATE_MSG
		if (amType.intValue()==MSTAgent.INITIATE_MSG) {
			System.out.println("\n\nINITIATE_MSG "+am.agentAddrS);			

			best = null;
			bestPath.clear ();
			receivedBest=0;
			
			// if it is in TERMINATED state ignore other messages
			if (state == MSTAgent.TERMINATED) {
System.out.println ("INITIATE_MSG ===> I am in a TERMINATED state ");				
				if (father !=null) {
					AgentMessage noOne = createMsg (NOEDGE_MSG, 1, 5, "MST@"+father ,"MST",null);
					sendMsg (noOne);
				} // if
				return;
			}//if
			
			state = MSTAgent.FINDING_STATE;
			
			// no MSTAgentMonitorClient to get ABPing measurements
			if (MSTAgentsMonitorClient.getInstance () == null) {
				return;
			} // if
			
			// parse the message to get fragmentID and level;
			String fraglevel = (String) am.message;
			String frag = null;
			int l = 0;
			if (fraglevel == null) { // something wrong with the message; incomplete message ;
				System.out.println ("INITIATE_MSG message doesn't have fragmentID and level setted");
				return;
			} else {
				frag = fraglevel.substring(0, fraglevel.indexOf(":") );
				if (fraglevel.indexOf(":")<0) { // something wrong with the message ; incomplete message ;
					System.out.println ("INITIATE_MSG message doesn't have fragmentID and level setted");
					return;
				} else { // correct message untill now; extract fragmentID and level;
					try {
						l = (new Integer (fraglevel.substring(fraglevel.indexOf(":")+1))).intValue();						
						fragmentID = frag;
							level = l;

								// look in the connected events and add the one as child 
								for (Enumeration en = connectEvents.keys();en.hasMoreElements();) {
									String key = (String)en.nextElement();
									int lev = ((Integer)connectEvents.get (key)).intValue();
									if (level>lev) {
										connectEvents.remove(key);
										children.add (key);
System.out.println ("INITIATE_MSG => added "+key+" as child");
									}
								} // for

								if (connectedRequest.contains(am.agentAddrS.substring(am.agentAddrS.indexOf("@")+1))) {
									//children.add(am.agentAddrS.substring(am.agentAddrS.indexOf("@")+1));
									father = am.agentAddrS.substring(am.agentAddrS.indexOf("@")+1);
System.out.println ("INITIATE_MSG => Changed my father "+father);									
									connectedRequest.clear();	
								}
					} catch (Exception e) {
						System.out.println ("INITIATE_MSG message doesn't have fragmentID and level setted correctly");
						e.printStackTrace ();
					} // try - catch
				} // if - else
			} // if
			
			// send INITIATE_MSG to all my children
			for (int i=0;i<children.size();i++) {
				String addr = "MST@"+(String)children.elementAt(i);
				AgentMessage amess = createMsg (INITIATE_MSG,1,5,addr, "MST",fragmentID+":"+level);
				sendMsg (amess);
			} // for

			// find the best edge
			MSTBestEdge edge = null;
			Vector except = new Vector();
			except.addAll(children);
			if (father != null) {
				except.add (father);
			} // if	
		

				edge = MSTAgentsMonitorClient.getInstance().getBestEdge(metric, except);
				if (edge == null) {
					AgentMessage noOne = createMsg (NOEDGE_MSG, 1, 5, "MST@"+agentInfo.farmID ,"MST",null);
					sendMsg (noOne);
					return;
				} // if
			
			System.out.println ("Best Edge ... "+edge.farmSID);
			AgentMessage test = createMsg (TEST_MSG, 1, 5, "MST@"+edge.farmSID ,"MST", fragmentID+":"+level);
			testedEdges.put (edge.farmSID, edge);    
			sendMsg (test);			    	
		} // INITIATE_MSG
		
		// NOEDGE_MSG
		if (amType.intValue() == NOEDGE_MSG) {
			receivedBest++;
			if ( receivedBest==children.size()+1 ) {
				if (best == null ){
					state = TERMINATED ;
System.out.println ("NOEDGE_MSG ====> I received TERMINATED state");			
					if (father == null) {
						System.out.println ("Radacina .... s-a terminat algoritmul .... ");
						AgentMessage amC = createMsg(0,1,10,null,null,"MST");
						messageID = amC.messageID.intValue();
						sendCtrlMsg (amC, "nr");
						
						// send BCAST interrogation
						AgentMessage amTer = createMsg(TERMINATED_INT,1,10,"bcast:MST","MST","MST");
						sendMsg (amTer);
						
					} else {
						AgentMessage noOne = createMsg (NOEDGE_MSG, 1, 5, "MST@"+father ,"MST",null);
						sendMsg (noOne);
					} // if - else 
				} else {
					if (father != null) {
						AgentMessage bestE = createMsg (GOT_BEST, 1, 5, "MST@"+father,"MST", best.farmSID+":"+best.measure) ;
						sendMsg(bestE);
					} else {
						// send change root
						if (bestPath.size()>0) {
							AgentMessage connect = createMsg (CHANGE_ROOT, 1, 5, "MST@"+bestPath.elementAt(0),"MST",best.farmSID+":"+best.measure);
							sendMsg (connect);
						} else {

							if (connectEvents.containsKey (best.farmSID)) { // am primit connect de la el. o sa-i trimit si eu;
								connectEvents.remove(best.farmSID);
								if (best.farmSID.compareTo(agentInfo.farmID)>0) {
									father = best.farmSID;
									System.out.println ("send connect message to "+ best.farmSID+" Connect_message ===> i become child");								
									AgentMessage bestE = createMsg (CONNECT_MSG, 1, 5, "MST@"+best.farmSID,"MST", Integer.valueOf(level)) ;
									connectedRequest.add(best.farmSID);
									sendMsg(bestE);
								} else {
									level = level+1;
									System.out.println ("send Connect_message"+best.farmSID+" ===> i become father   "+level);
									children.add (best.farmSID);
									AgentMessage bestE = createMsg (CONNECT_MSG, 1, 5, "MST@"+best.farmSID,"MST", Integer.valueOf(level)) ;
									connectedRequest.add(best.farmSID);
									sendMsg(bestE);
									notifyAll();
								} // if - else
							} else { 
								AgentMessage bestE = createMsg (CONNECT_MSG, 1, 5, "MST@"+best.farmSID,"MST", Integer.valueOf(level)) ;
								connectedRequest.add(best.farmSID);
								sendMsg(bestE);
							} // if - else		
						} // if - else
					} // if - else
				} // if - else
			} // if
		} // NOEDGE_MSG
		
		// TERMINATED_INT
		if (amType.intValue()==TERMINATED_INT) {
System.out.println ("TERMINATED_INT");
			if (state == TERMINATED) {
				AgentMessage term = createMsg (TERMINATED_TRUE, 1, 5, am.agentAddrS ,"MST", null) ;
				sendMsg (term) ;
			} 
		} // TERMINATED_INT
		
		if (amType.intValue()==TERMINATED_TRUE) {
			System.out.println ("TERMINATED_TRUE");		
			if (agentsNr == -1) {
				waitingMessages.add(am);
				return;
			} else {
				receivedTerMsg ++;
				if (receivedTerMsg == agentsNr) {
System.out.println ("Toti au ajuns in starea TERMINATED_TRUE");					
					AgentMessage amTer = createMsg(PUBLISH_REZ,1,10,"bcast:MST","MST","MST");
					sendMsg (amTer);
				} // if
			} // if - else
		} // if TERMINATED_TRUE
		
		// PUBLISH_REZ
		if (amType.intValue()==PUBLISH_REZ) {
System.out.println ("PUBLISH_REZ");			
			state = PUBLISHING ;
			agentsNr = -1;
			messageID = -1;
			receivedTerMsg = 0;
			System.out.println ("Publishing the result ....  FATHER: "+father+"\n children :");
			for (int i=0;i<children.size();i++)
				System.out.println ("\t\t"+children.elementAt(i));
			
			father = null;
			children.clear();
			//level=0;
			fragmentID = agentInfo.farmID;
			level = 0 ; // the number of nodes
			
			long time = NTPDate.currentTimeMillis();
			AgentMessage amTer = createMsg(NEW_ITERATION,1,10 , null , null , Long.valueOf(time));
			waitingMessages.add (amTer);
			state = SLEEPING_STATE;

		} // PUBLISH_REZ
		
		
		// NEW_ITERATION
		if (amType.intValue()==NEW_ITERATION) {
			state = SLEEPING_STATE;
			if (NTPDate.currentTimeMillis()-((Long)am.message).longValue()>=60000) {
				notifyAll();
			} else {
				waitingMessages.add (am);
			} // if - else
			//state = SLEEPING_STATE;
		} // NEW_ITERATION
		
		// TEST_MSG
		if (amType.intValue()==TEST_MSG) {
			System.out.println (" TEST_MSG "+am.agentAddrS);
			
			
			String fraglev = (String) am.message ;
			if (fraglev==null || fraglev.indexOf(":")<0 || fraglev.indexOf(":")==fraglev.length()-1) { // incomplete TEST_MSG;
				System.out.println ("\n\n Received incomplete TEST_MSG");
				return;
			} // if
			
			String frag = fraglev.substring(0,fraglev.indexOf(":"));
			try {
				int l = (Integer.valueOf(fraglev.substring(fraglev.indexOf(":")+1))).intValue();
				
				// if i am in a TERMINATED state, i refuse you ;
				if (state == TERMINATED) {
System.out.println ("TEST_MSG ====> but i am in a TERMINATED state ; i deny the new request");					
					AgentMessage testDeny = createMsg (TEST_DENY, 1, 5, am.agentAddrS ,"MST", agentInfo.farmID) ;
					sendMsg (testDeny) ;
					return;
				} // IF
				
				if (fragmentID==null ) { // hasn't received the INITIATE_MSG yet; witing for it;
					waitingMessages.add(am);
					return;
				} // while
				
				if (fragmentID.equals (frag)) { // test me, but i am in the same fragment as you are :D;
					AgentMessage testDeny = createMsg (TEST_DENY, 1, 5, am.agentAddrS ,"MST", agentInfo.farmID) ;
					sendMsg (testDeny) ;
					return;
				} // if
				
				if (level>=l) { // somebody greater then me; accept it;
					AgentMessage testAccept = createMsg (TEST_ACCEPT, 1, 5, am.agentAddrS ,"MST", agentInfo.farmID) ;
					sendMsg (testAccept) ;
					return;
				} // if
				

				if (level<l) { // somebody with a lower level; send wait to it;
					waitingMessages.add (am) ;
					return;
				} // if
				
			} catch (Exception e) {
				e.printStackTrace ();
			} // try - catch
		} // TEST_MSG
		
		if (amType.intValue() == TEST_DENY) { // received TEST_DENY for my testing :( ; trying to find somebody else ....   
			System.out.println (" TEST_DENY "+am.agentAddrS);
			
			// form the except vector ; 
			Vector ex = new Vector();
			if (father!=null) {
				ex.add (father);
			} // if
			for (int i=0;i<children.size();i++) {
				ex.add (children.elementAt(i));
			} // for
			if (am.message!=null) {
			try {
				for (Enumeration en = testedEdges.keys();en.hasMoreElements();) {
					ex.add (en.nextElement());
				}//for	
				} catch (Exception e) {}
			} // if	
			
			// aici tre' iar sa iau cea mai buna latura si sa incerc sa dau test_msg
			MSTBestEdge edge = null;
			edge = MSTAgentsMonitorClient.getInstance().getBestEdge(metric, ex);
			
			if (edge ==null) {
				testedEdges.clear();
				AgentMessage noOne = createMsg (NOEDGE_MSG, 1, 5, "MST@"+agentInfo.farmID ,"MST",null);
				sendMsg (noOne);
				return;
			} else {
				AgentMessage test = createMsg (TEST_MSG, 1, 5, "MST@"+edge.farmSID ,"MST", fragmentID+":"+level);
				testedEdges.put (edge.farmSID, edge);
				sendMsg (test);
			} // if - else
		} // TEST_DENY
		
		// TEST_ACCEPT
		if (amType.intValue() == TEST_ACCEPT) {
			System.out.println ("TEST_ACCEPT "+am.agentAddrS);
			// aici aleg latura asta si trimit mesaj la parinte ca am terminat de ales legatura
			MSTBestEdge edge = null;
			try {
				edge =(MSTBestEdge) testedEdges.remove( (String)am.message);
			} catch (Exception e) {}
			
			if (edge!=null) {
	//			System.out.println ("TEST_ACCEPT : edge!=null");			
				
				receivedBest ++;
				// see the new received edge if it's better;
				if (best == null || ((Double)best.measure).doubleValue()>((Double)edge.measure).doubleValue()) {
					best = edge;
					bestPath.clear();
				} // if
			} // if

				if (receivedBest == children.size()+1 &&  best!=null) {
					state = MSTAgent.FOUND_STATE;
					if (father !=null) {
						//send Best to my parent
						AgentMessage bestE = createMsg (GOT_BEST, 1, 5, "MST@"+father,"MST", best.farmSID+":"+best.measure) ;
						sendMsg(bestE);
					} else {
						// send change root
						if (bestPath.size()>0) {
							AgentMessage connect = createMsg (CHANGE_ROOT, 1, 5, "MST@"+bestPath.elementAt(0),"MST",best.farmSID+":"+best.measure);
							sendMsg (connect);
						} else {

				if (connectEvents.containsKey (best.farmSID)) { // am primit connect de la el. o sa-i trimit si eu;
								connectEvents.remove(best.farmSID);
								if (best.farmSID.compareTo(agentInfo.farmID)>0) {
									father = best.farmSID;
	System.out.println ("send connect message to "+ best.farmSID+" Connect_message ===> i become child");								
									AgentMessage bestE = createMsg (CONNECT_MSG, 1, 5, "MST@"+best.farmSID,"MST", Integer.valueOf(level)) ;
									connectedRequest.add(best.farmSID);
									sendMsg(bestE);
								} else {
									level = level+1;
System.out.println ("send Connect_message"+best.farmSID+" ===> i become father   "+level);
									children.add (best.farmSID);
									AgentMessage bestE = createMsg (CONNECT_MSG, 1, 5, "MST@"+best.farmSID,"MST", Integer.valueOf(level)) ;
									connectedRequest.add(best.farmSID);
									sendMsg(bestE);
									notifyAll();
								} // if - else
				} else { 
						AgentMessage bestE = createMsg (CONNECT_MSG, 1, 5, "MST@"+best.farmSID,"MST", Integer.valueOf(level)) ;
						connectedRequest.add(best.farmSID);
						sendMsg(bestE);
				}		
					//} // if - else

						}
					} // if - else
				}  // if 			
			
		} // TEST_ACCEPT
		
		
		// GOT_BEST
		if (amType.intValue() == GOT_BEST) {
			System.out.println ("GOT_BEST "+am.agentAddrS);
			String farm = null;
			Double val = null;
			try {
				String lat = (String)am.message;
				farm = lat.substring (0,lat.indexOf(":"));
				val = Double.valueOf(lat.substring (lat.indexOf(":")+1));
				receivedBest ++;
				
				MSTBestEdge edge = new MSTBestEdge (farm, val) ;
				if (best==null || ((Double)best.measure).doubleValue()>((Double)edge.measure).doubleValue()) {
						best = edge;
						bestPath.clear ();
						bestPath.add (am.agentAddrS.substring(am.agentAddrS.indexOf("@")+1));
				} // if
						// aici trebuie sa adaug la GOT_Message pe mine si sa trimit mai departe;
						if (receivedBest == children.size()+1 && best!=null) {

							state = MSTAgent.FOUND_STATE;
							if (father!=null) {
								AgentMessage bestE = createMsg (GOT_BEST, 1, 5, "MST@"+father,"MST", best.farmSID+":"+best.measure) ;
								sendMsg(bestE);
							} else {
								if (bestPath.size()>0) {
									AgentMessage bestE = createMsg (CHANGE_ROOT, 1, 5, "MST@"+bestPath.elementAt(0),"MST", best.farmSID+":"+best.measure) ;
									sendMsg(bestE);
								} else {

						if (connectEvents.containsKey (best.farmSID)) { // am primit connect de la el. o sa-i trimit si eu;
								connectEvents.remove(best.farmSID);
								if (best.farmSID.compareTo(agentInfo.farmID)>0) {
									father = best.farmSID;
									AgentMessage bestE = createMsg (CONNECT_MSG, 1, 5, "MST@"+best.farmSID,"MST", Integer.valueOf(level)) ;
							connectedRequest.add (best.farmSID);
System.out.println ("send Connect_message"+best.farmSID+" ===> i become child  "+level);									
									sendMsg(bestE);
								} else {
									level = level+1;
									children.add (best.farmSID);
System.out.println ("send Connect_message"+best.farmSID+" ===> i become father   "+level);									
									AgentMessage bestE = createMsg (CONNECT_MSG, 1, 5, "MST@"+best.farmSID,"MST", Integer.valueOf(level)) ;
									connectedRequest.add (best.farmSID);
									sendMsg(bestE);
									notifyAll();
								}
						} else {
							AgentMessage bestE = createMsg (CONNECT_MSG, 1, 5, "MST@"+best.farmSID,"MST", Integer.valueOf(level)) ;
									connectedRequest.add (best.farmSID);
							sendMsg(bestE);
						}
												
								} // if - else
							} // if - else
						}// if

			   } catch (Exception e) {
					e.printStackTrace ();
				}
		} // GOT_BEST
		
		// CHANGE_ROOT
		if (amType.intValue() == CHANGE_ROOT) {
			System.out.println ("CHANGE_ROOT "+am.agentAddrS);
			if (bestPath.size()>0) {
					AgentMessage bestE = createMsg (CHANGE_ROOT, 1, 5, "MST@"+bestPath.elementAt(0),"MST", best.farmSID+":"+best.measure) ;
					children.add(father);
					children.remove(bestPath.elementAt(0));
					father = (String)bestPath.elementAt(0);
					sendMsg(bestE);
			} else {
					if (connectEvents.containsKey (best.farmSID)) { // am primit connect de la el. o sa-i trimit si eu;
								connectEvents.remove(best.farmSID);
								if (best.farmSID.compareTo(agentInfo.farmID)>0) {
									father = best.farmSID;
System.out.println ("send Connect_message"+best.farmSID+" ===> i become child  "+level);								
									AgentMessage bestE = createMsg (CONNECT_MSG, 1, 5, "MST@"+best.farmSID,"MST", Integer.valueOf(level)) ;
									connectedRequest.add(best.farmSID);
									sendMsg(bestE);
								} else {
									level = level+1;
System.out.println ("send Connect_message "+best.farmSID+" ===> i become father   "+level);									
									children.add (best.farmSID);
									AgentMessage bestE = createMsg (CONNECT_MSG, 1, 5, "MST@"+best.farmSID,"MST", Integer.valueOf(level)) ;
									connectedRequest.add(best.farmSID);
									sendMsg(bestE);
									notifyAll();
								} // if - else
					}  else {
						AgentMessage bestE = createMsg (CONNECT_MSG, 1, 5, "MST@"+best.farmSID,"MST", Integer.valueOf(level)) ;
						connectedRequest.add(best.farmSID);
						sendMsg(bestE);
					} // if - else
			} // if - else
		} // CHANGE_ROOT
		
		// CONNECT_MSG
		if (amType.intValue() == CONNECT_MSG) {
System.out.println ("CONNECT_MSG "+am.agentAddrS);
			try {
				int l = ((Integer)am.message).intValue();
				if (l==level) {
					if (connectedRequest.contains ( am.agentAddrS.substring (am.agentAddrS.indexOf("@")+1))) { //si eu i-am dat connect
						connectedRequest.remove ( am.agentAddrS.substring (am.agentAddrS.indexOf("@")+1));
						if (agentInfo.farmID.compareTo(am.agentAddrS.substring (am.agentAddrS.indexOf("@")+1))>0) { // i become his parent
							children.add (am.agentAddrS.substring (am.agentAddrS.indexOf("@")+1));
							level = level+1;
							
System.out.println ("received Connect_message"+" ===> i become father   "+level);				
//							notifyAll();						
							notifyAll();
						} else { // he becomes my parent
							
							father = am.agentAddrS.substring (am.agentAddrS.indexOf("@")+1);
System.out.println ("received Connect_message"+" ===> i become child  "+level);							
						} // if - else;
					} else { // add it to connected events
						connectEvents.put (am.agentAddrS.substring (am.agentAddrS.indexOf("@")+1),Integer.valueOf(level));
					} // if - else	
				} else  {
                   if (level>l) {
					children.add (am.agentAddrS.substring (am.agentAddrS.indexOf("@")+1));
System.out.println ("received Connect_message. i have i greater level "+" ===> i become his father "+level);					
					notifyAll();
				   }
		//			} // if
				}

			} catch (Exception e) {
				e.printStackTrace();
			} // try - catch 
		} // CONNECT_MSG

	} // processMsg

	public synchronized void doWork () {
		try {
			Thread.sleep (120*1000);
		} catch (Exception e){}

		
		(new CheckerMsg()).start();
		
System.out.println ("MY AGENT NAME ===> "+agentInfo.farmID);		
		//joinMcast (agentInfo.farmID);	

		fragmentID = agentInfo.farmID;

		while (true) {
			
		waitingMessages.clear();	
		// send an INITIATE_MSG 
		AgentMessage init = createMsg ( INITIATE_MSG, 1, 5, "MST@"+agentInfo.farmID,"MST", fragmentID+":"+level );
		sendMsg (init);

		
		try {
			wait();
		} catch (Exception e) {
		 	e.printStackTrace();
		}
		
		} // while
		
	} // do work

    public void newProxyConnection(){};
	
} // class
