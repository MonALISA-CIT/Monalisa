package lia.util.telnet;

public class OSTelnetFactory {

    
    public static final OSTelnet getMonitorInstance(String switchType) throws Exception {
        return getMonitorInstance(OSTelnet.getType(switchType));
    }

    public static final OSTelnet getControlInstance(String switchType) throws Exception {
        return getControlInstance(OSTelnet.getType(switchType));
    }

    public static final OSTelnet getMonitorInstance(int switchType) throws Exception {
        switch(switchType) {
            case OSTelnet.GLIMMERGLASS: {
                return Sys300Telnet.getMonitorInstance();
            }
            case OSTelnet.CALIENT: {
                return CalientTelnet.getMonitorInstance();
            }
        }
        return null;
    }
    
    public static final OSTelnet getControlInstance(int switchType) throws Exception {
        switch(switchType) {
            case OSTelnet.GLIMMERGLASS: {
                return Sys300Telnet.getControlInstance();
            }
            case OSTelnet.CALIENT: {
                return CalientTelnet.getControlInstance();
            }
        }
        return null;
    }

}
