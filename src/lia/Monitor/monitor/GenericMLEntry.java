/*
 * $Id: GenericMLEntry.java 7116 2011-03-05 13:56:41Z ramiro $
 */

package lia.Monitor.monitor;

import java.util.Hashtable;

import net.jini.entry.AbstractEntry;

public class GenericMLEntry extends AbstractEntry {
    
    private static final long serialVersionUID = -8598777707091520326L;
    
    /** Place any number of Attributes here */
    public Hashtable hash;

    public GenericMLEntry() {
        hash = new Hashtable();
    }
}
