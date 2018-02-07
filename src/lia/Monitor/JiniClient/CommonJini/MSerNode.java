package lia.Monitor.JiniClient.CommonJini;

import lia.Monitor.monitor.MonaLisaEntry;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;

public class MSerNode {

    public ServiceID sid ;
    public MonaLisaEntry mlentry;
    public  Entry[] attrs ;
    public String IPaddress;

}
