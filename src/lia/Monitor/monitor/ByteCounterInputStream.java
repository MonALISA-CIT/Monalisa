/*
 * Created on Feb 16, 2006 12:27:39 AM
 * Filename: ByteCounterInputStream.java
 *
 */
package lia.Monitor.monitor;

import java.io.IOException;
import java.io.InputStream;

public class ByteCounterInputStream extends InputStream {

    private long counter = 0;
    private InputStream in;
    public ByteCounterInputStream(InputStream in)
    {
        this.in = in;
    }
    public int read() throws IOException {
        int val = in.read();
//        System.out.println("read byte "+val);
        counter++;
        return val;
    }

    public long getCounter() { return counter; }
    public void resetCounter() { counter=0; }
}
