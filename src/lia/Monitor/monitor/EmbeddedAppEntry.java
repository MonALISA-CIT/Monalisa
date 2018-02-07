/*
 * $Id: EmbeddedAppEntry.java 6865 2010-10-10 10:03:16Z ramiro $
 */

package lia.Monitor.monitor;

public class EmbeddedAppEntry extends net.jini.entry.AbstractEntry {
    
    private static final long serialVersionUID = 5252927747820058909L;
    
    public String Name = null;
	public String ConfigFile = null;
	public Integer State = null;
	
	public EmbeddedAppEntry() {
	}

	public EmbeddedAppEntry(String Name, String ConfigFile, Integer State) {
		this.Name = Name;
		this.ConfigFile = ConfigFile;
		this.State  = State;
	}
	public EmbeddedAppEntry(String Name, String ConfigFile) {
		this.Name = Name;
		this.ConfigFile = ConfigFile;
		this.State  = null;
	}
	public boolean equals (EmbeddedAppEntry e){
		if(this.Name!=null && this.ConfigFile!=null  && this.State!=null && (this.Name.compareTo(e.Name) == 0) &&  (this.ConfigFile.compareTo(e.ConfigFile) == 0) && (this.State.equals(e.State)) )
			return true;
		return false;	
	}
}
