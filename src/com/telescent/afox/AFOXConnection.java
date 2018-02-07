package com.telescent.afox;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import com.telescent.afox.utils.FlatSerialize;

/**
 * @author ramiro To change the template for this generated type comment go to Window - Preferences - Java - Code
 *         Generation - Code and Comments
 */
public class AFOXConnection {

    private final SocketAddress sa;
    
    private Socket s;

    private BufferedInputStream bis;

    private BufferedOutputStream bos;

    public AFOXConnection(final String address, final int port) throws UnknownHostException, IOException {
        this.sa = new InetSocketAddress(InetAddress.getByName(address), port);
    }

    private synchronized void newConnection() throws IOException {
        boolean bInited = false;
        try {
            final Socket tmpSocket = new Socket();
            tmpSocket.setSoTimeout(40 * 1000);
            tmpSocket.connect(this.sa, 20 * 1000);
            this.s = tmpSocket;
            this.bis = new BufferedInputStream(s.getInputStream());
            this.bos = new BufferedOutputStream(s.getOutputStream());
            bInited = true;
        } finally {
            if (!bInited) {
                close();
            }
        }
    }
    
	private final static void reverse(byte b[]) {
		byte t;
		if (b==null) return;
		int l = b.length;
		int m = l/2;
		for (int i=0; i<m; i++) {
			t = b[i];
			b[i] = b[l-i-1];
			b[l-i-1] = t;
		}
	}
    
    public synchronized byte[] sendAndReceive(byte[] buff, long sleep, TimeUnit unit) throws IOException {
        try {
            close();
            newConnection();
            
            final byte[] bHSize = FlatSerialize.intToByteArray(buff.length);
            bos.write(bHSize);
            bos.flush();
            bos.write(buff);
            bos.flush();

            try {
                if(sleep > 0) {
                    unit.sleep(sleep);
                }
            }catch(Throwable _) {};
            byte[] bSize = new byte[4];
            // wait for response
            int bLen = bis.read(bSize);
            if (bLen < 0) {
                throw new IOException("AFOX EndOfStream before finished reading");
            }
            reverse(bSize);

            System.out.print(bSize.length+": (");
            for (int i=0; i<bSize.length; i++) {
            	System.out.print(bSize[i]+" ");
            }
            System.out.println(")");
            
            int pktLen = FlatSerialize.intFromByteArray(bSize);
            if (pktLen > 0) {
                byte[] payload = new byte[pktLen];
                bis.read(payload);
                return payload;
            }
            
            throw new IOException("Negative size value for serialized data");

        } finally {
            close();
        }

    }

    public synchronized void close() {
//        closeIgnoreExc(s);
        closeIgnoreExc(bis);
        closeIgnoreExc(bos);
        this.s = null;
        this.bis = null;
        this.bos = null;
    }

    private final static void closeIgnoreExc(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ign) {
            }
            ;
        }
    }
}
