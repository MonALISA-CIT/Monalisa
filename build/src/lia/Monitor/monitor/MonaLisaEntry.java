/*
 * $Id: MonaLisaEntry.java 6865 2010-10-10 10:03:16Z ramiro $
 */

package lia.Monitor.monitor;

public class MonaLisaEntry extends net.jini.entry.AbstractEntry {

    private static final long serialVersionUID = 3027696452812542717L;
    
    public String Name = null;
	public String Group = null;
	public String Location = null;
	public String Country = null;
	public String LAT = null;
	public String LONG = null;
	public String IconUrl = null;
	public String SiteUrl = null;

	public MonaLisaEntry() {
	}

	public MonaLisaEntry(String Name, String Group) {
		this.Name = Name;
		this.Group = Group;
	}

}
