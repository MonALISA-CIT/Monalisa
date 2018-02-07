
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;

/**
 This a simple Data Writer which can be dynamically loaded into
 MonaLisa. All the collected results are also passed to this class.

*/

public  class DataWriter implements lia.Monitor.monitor.DataReceiver  {

public DataWriter() {
}

public void addResult ( Result r ) {
 System.out.println ( "DataWriter received R ==" + r );
}

public void addResult ( eResult r ) {
 System.out.println ( "DataWriter received eR ==" + r );
}

public void updateConfig(lia.Monitor.monitor.MFarm f) {
}


}

