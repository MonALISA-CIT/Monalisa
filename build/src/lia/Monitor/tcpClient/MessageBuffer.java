package lia.Monitor.tcpClient;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.MonMessageClientsProxy;
import lia.Monitor.monitor.tcpConn;

/**
 * Buffer the messages to be sent over the tcp connection, to avoid blocking the
 * client
 * 
 * @author cipsm, catac
 */
final class MessageBuffer implements Runnable {
	protected static final Logger logger = Logger.getLogger(ConnMessageMux.class.getName());

	private final tcpConn conn;
	private final BlockingQueue<MonMessageClientsProxy> queue;
	final ConnMessageMux master;
	
	public MessageBuffer(final ConnMessageMux master, tcpConn conn) {
		this.conn = conn;
		this.master = master;
		queue = new LinkedBlockingQueue<MonMessageClientsProxy>();
	}

	public void sendMsg(MonMessageClientsProxy mess) {
		queue.offer(mess);
	}

	@Override
	public void run() {
		final Thread thisThread = Thread.currentThread();
		final String initName = thisThread.getName();
		final String myName = " MessageBuffer " + conn.getEndPointAddress() + ":" + conn.getEndPointPort();
		final AtomicBoolean active = master.active;

		try {
			thisThread.setName(initName + " - " + myName);

			try {
				final BlockingQueue<MonMessageClientsProxy> queue = this.queue;
				final tcpConn conn = this.conn;
				while (active.get()) {
					final MonMessageClientsProxy mess = queue.poll(10, TimeUnit.SECONDS);
					if (mess == null) {
						continue;
					}
					conn.sendMsg(mess);
				}

			} catch (InterruptedException ie) {
				if (active.get()) {
					logger.log(Level.SEVERE,
					        "\n\n [ SEVERE ] [ ConnMessageMux ] [ MsgBuff ] [ HANDLED ] Caught InterruptedException processing message. Active:" + active
					                + ". Cause ", ie);
				}
			} catch (Throwable t) {
				logger.log(Level.WARNING, "\n\n [ EXCEPTION ] [ ConnMessageMux ] [ MsgBuff ] Unable to get message from processing queue. Active:"
				        + active + ". Cause:", t);
			}
		} finally {
			master.closeProxyConnection();
			logger.log(Level.INFO, " [ MessageBuffer ] ( " + myName + " ) exits main loop. Active: " + active.get());
		}
	}

} // end of class MessageBuffer

