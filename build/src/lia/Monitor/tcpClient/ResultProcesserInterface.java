package lia.Monitor.tcpClient;


/**
 * Interface that can be used in conjunction with the Buffer class so that the classes that aspect a lot of results comming can make use of them
 * in order not to block the tmClient.
 */
public interface ResultProcesserInterface {

	public void process(MLSerClient client, Object result);
}
