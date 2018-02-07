package lia.Monitor.ClientsFarmProxy.AgentsPlatform;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.Vector;

import lia.Monitor.ClientsFarmProxy.FarmWorker;
import lia.Monitor.monitor.monMessage;
import lia.util.ntp.NTPDate;
import net.jini.core.lookup.ServiceID;

public class MessageCache {

	private Vector recentMsg;
	private final File file;

	private final Map<ServiceID, FarmWorker> farmsSIDs;

	public static final int DELTA = 60000;

	public MessageCache(String fileName, Map<ServiceID, FarmWorker> farmsSIDs) {

		this.farmsSIDs = farmsSIDs;
		recentMsg = new Vector();
		file = new File(fileName);

		MsgUpdater mUpdater = new MsgUpdater();
		mUpdater.start();

	} // MessageCache

	public void addMessageToCache(AgentMessage msg) {

		// tre' sa setez timpul mesajului aici
		AMsgCache ac = new AMsgCache(msg);

		recentMsg.add(ac); // put to recentMsg vector;

	} // addMessageToCache

	private void sendErrorMessage(AgentMessage am) {
		// if it is write, then verify if the farm exists ( it's alive) ;
		try {
			am.agentAddrD = am.agentAddrS;
			am.agentGroupD = am.agentGroupS;
			FarmWorker ptW = farmsSIDs.get(AgentsPlatform.serviceIDFromString(am.agentAddrS.substring(am.agentAddrS
				.indexOf("@") + 1)));
			monMessage mm = new monMessage(monMessage.ML_AGENT_ERR_TAG, null, am);
			ptW.sendMsg(mm);
		}
		catch (Exception e) {
			// ignore
		} // try - catch
	} // sendErrorMessage

	class MsgUpdater extends Thread {

		private Vector oldMsg;

		public MsgUpdater() {
			oldMsg = new Vector();
		} // constr. MsgUpdater

		public void run() {
			// citesc in vectorul asta din fisier;
			ObjectInputStream ois = null;

			while (true) {
				try {
					Thread.sleep(20000);
				}
				catch (InterruptedException e) {
					// ignore
				}

				try {
					ois = new ObjectInputStream(new FileInputStream(file));
				}
				catch (Exception e) {
					e.printStackTrace();
					// continue;
					ois = null;
				}

				if (ois != null) {
					Object o = null;

					try {
						o = ois.readObject();
					}
					catch (Exception e) {
						e.printStackTrace();
						o = null;
						continue;
					} // try - catch
					finally{
						try {
							ois.close();
						}
						catch (Exception e) {
							e.printStackTrace();
						}
					}

					Vector v = (Vector) o;

					if (v.size() == 0 && recentMsg.size() == 0)
						continue;

					if (o != null) {
						// Vector v = (Vector) o;
						oldMsg = v;

						// procesez fiecare mesaj in parte;
						for (int i = 0; i < oldMsg.size(); i++) {
							AMsgCache msg = (AMsgCache) oldMsg.elementAt(i);
							boolean rez = processMsg(msg);
							if (rez == true) { // message resolved or
								// expired
								oldMsg.remove(i);
							} // if
						} // for

						// FINISH :)
					} // if
				}

				// adaug la vector ala care a mai ramas;
				synchronized (recentMsg) {
					oldMsg.addAll(recentMsg);
					recentMsg.clear();
				} // synchronized

				if (oldMsg.size() > 0)
					System.out.println("Call wite messaje to file  " + oldMsg.size());

				// scriu tot iar in fisier;
				writeVectorToFile(oldMsg);
			} // while

		} // run function

		public void writeVectorToFile(Object o) {

			// System.out.println("WriteMessageToFile");
			ObjectOutputStream oos = null;
			try {
				oos = new ObjectOutputStream(new FileOutputStream(file));
				oos.writeObject(o);
			}
			catch (Exception e) {
				e.printStackTrace();
			} // try - catch
			finally{
				if (oos!=null)
					try{
						oos.close();
					}
					catch (IOException ioe){
						// ignore
					}
			}

		} // writeVectorToFile

		public boolean processMsg(AMsgCache msg) {

			// try to send the message ...
			AgentMessage agentMsg = msg.getMsg();
			String agentAddrD = agentMsg.agentAddrD;
			String farmid = agentAddrD.substring(agentAddrD.indexOf("@") + 1);

			FarmWorker pW = farmsSIDs.get(AgentsPlatform.serviceIDFromString(farmid));
			if (pW != null) {
				monMessage mm = new monMessage("agents", null, agentMsg);
				try {
					pW.sendMsg(mm);
				}
				catch (Exception e) {
					msgChecker(msg);
				} // try - catch
			}
			else {
				msgChecker(msg);
			} // if - else

			return true;
		} // processMsg

		private void msgChecker(AMsgCache msg) {
			if ((NTPDate.currentTimeMillis() - msg.getTimeMem().longValue()) < DELTA) {
				recentMsg.add(msg);
			}
			else { // see if the sent message was of Ack type ; if so, send
					// error to the sender ;
				AgentMessage agentMsg = (AgentMessage) msg.getMsg();
				sendErrorMessage(agentMsg);
				// // if
			} // if - else
		} // msgChecker

	} // MsgUpdater

} // class MessageCache
