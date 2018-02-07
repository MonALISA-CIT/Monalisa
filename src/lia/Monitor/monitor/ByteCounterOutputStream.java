/*
 * Created on Feb 16, 2006 12:27:39 AM
 * Filename: ByteCounterInputStream.java
 *
 */
package lia.Monitor.monitor;

import java.io.IOException;
import java.io.OutputStream;

public class ByteCounterOutputStream extends OutputStream {

    private long counter = 0;
    private OutputStream out;
    public ByteCounterOutputStream(OutputStream out)
    {
        this.out = out;
    }
    public long getCounter() { return counter; }
    public void resetCounter() { counter=0; }
	public void write(int b) throws IOException {
		out.write(b);
        counter++;
	}
}
