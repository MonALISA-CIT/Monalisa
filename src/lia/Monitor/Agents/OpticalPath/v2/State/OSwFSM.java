package lia.Monitor.Agents.OpticalPath.v2.State;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optical Switch Finite State Machine 
 */
public class OSwFSM {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(OSwFSM.class.getName());

    private static volatile boolean fsmInited;
    private static OSwFSM thisInstance;
    public OSwConfig oswConfig;

    private final Object lock = new Object();

    private static final boolean PORT_POWER_TRANSITIONS[][];
    private static final boolean CROSS_CONNS_TRANSITIONS[][];

    static {

        /*
         * Possible transitions for Optical Power on Optica Ports 
         */
        int powerStatesCount = OSwPort.POWER_STATE_NAMES.length;
        boolean TMP_PORT_POWER_TRANSITIONS[][] = new boolean[powerStatesCount][powerStatesCount];

        /*
         * Define only the valid transitions ... [ Current PortPower State ][ New PortPower State ] = true
         * The possible states for an OSwPort are:
         *         "UNKLIGHT",
         *         "LIGHTOK",
         *         "NOLIGHT",
         *         "NOLIGHT_SIMULATED",
         *         "LIGHTERR",
         *         "LIGHTERR_SIMULATED",
         *         "WFORLIGHT1",
         *         "WFORLIGHT2"
         */

        //UNKLIGHT
        TMP_PORT_POWER_TRANSITIONS[OSwPort.UNKLIGHT][OSwPort.UNKLIGHT] = true;
        TMP_PORT_POWER_TRANSITIONS[OSwPort.UNKLIGHT][OSwPort.LIGHTOK] = true;
        TMP_PORT_POWER_TRANSITIONS[OSwPort.UNKLIGHT][OSwPort.NOLIGHT] = true;
        TMP_PORT_POWER_TRANSITIONS[OSwPort.UNKLIGHT][OSwPort.NOLIGHT_SIMULATED] = true;

        //LIGHTOK
        TMP_PORT_POWER_TRANSITIONS[OSwPort.LIGHTOK][OSwPort.UNKLIGHT] = true;
        TMP_PORT_POWER_TRANSITIONS[OSwPort.LIGHTOK][OSwPort.LIGHTOK] = true;
        TMP_PORT_POWER_TRANSITIONS[OSwPort.LIGHTOK][OSwPort.NOLIGHT] = true;
        TMP_PORT_POWER_TRANSITIONS[OSwPort.LIGHTOK][OSwPort.NOLIGHT_SIMULATED] = true;
        TMP_PORT_POWER_TRANSITIONS[OSwPort.LIGHTOK][OSwPort.LIGHTERR] = true;
        TMP_PORT_POWER_TRANSITIONS[OSwPort.LIGHTOK][OSwPort.LIGHTERR_SIMULATED] = true;
        TMP_PORT_POWER_TRANSITIONS[OSwPort.LIGHTOK][OSwPort.WFORLIGHT1] = true;

        //for NOLIGHT
        TMP_PORT_POWER_TRANSITIONS[OSwPort.NOLIGHT][OSwPort.UNKLIGHT] = true;
        TMP_PORT_POWER_TRANSITIONS[OSwPort.NOLIGHT][OSwPort.LIGHTOK] = true;
        TMP_PORT_POWER_TRANSITIONS[OSwPort.NOLIGHT][OSwPort.NOLIGHT] = true;
        TMP_PORT_POWER_TRANSITIONS[OSwPort.NOLIGHT][OSwPort.NOLIGHT_SIMULATED] = true;
        TMP_PORT_POWER_TRANSITIONS[OSwPort.NOLIGHT][OSwPort.WFORLIGHT1] = true;

        //NOLIGHT_SIMULATED
        TMP_PORT_POWER_TRANSITIONS[OSwPort.NOLIGHT_SIMULATED][OSwPort.UNKLIGHT] = true;

        //LIGHTERR
        TMP_PORT_POWER_TRANSITIONS[OSwPort.LIGHTERR][OSwPort.UNKLIGHT] = true;

        //LIGHTERR_SIMULATED
        TMP_PORT_POWER_TRANSITIONS[OSwPort.LIGHTERR_SIMULATED][OSwPort.UNKLIGHT] = true;

        //WFORLIGHT1
        TMP_PORT_POWER_TRANSITIONS[OSwPort.WFORLIGHT1][OSwPort.UNKLIGHT] = true;
        TMP_PORT_POWER_TRANSITIONS[OSwPort.WFORLIGHT1][OSwPort.WFORLIGHT2] = true;

        //WFORLIGHT2
        TMP_PORT_POWER_TRANSITIONS[OSwPort.WFORLIGHT2][OSwPort.UNKLIGHT] = true;
        TMP_PORT_POWER_TRANSITIONS[OSwPort.WFORLIGHT2][OSwPort.LIGHTOK] = true;
        TMP_PORT_POWER_TRANSITIONS[OSwPort.WFORLIGHT2][OSwPort.LIGHTERR] = true;
        TMP_PORT_POWER_TRANSITIONS[OSwPort.WFORLIGHT2][OSwPort.LIGHTERR_SIMULATED] = true;

        //just assign it...
        PORT_POWER_TRANSITIONS = TMP_PORT_POWER_TRANSITIONS;

        /*
         * Possible transitions for Optical Cross Connects 
         */
        int cconnStatesCount = OSwCrossConn.CCONNS_STATE_NAMES.length;
        boolean TMP_CROSS_CONNS_TRANSITIONS[][] = new boolean[cconnStatesCount][cconnStatesCount];

        /*
         * Define only the valid transitions ... [ Current PortPower State ][ New PortPower State ] = true
         * The possible states for an OSwPort are:
         *         "UNKNOWN_STATUS",
         *         "CCONNOK",
         *         "CCONNERR",
         *         "WFORCCONN1",
         *         "WFORCCONN2",
         *         "WFORFREE1",
         *         "WFORFREE2"
         */

        //UNKNOWN_STATUS
        TMP_CROSS_CONNS_TRANSITIONS[OSwCrossConn.UNKNOWN_STATUS][OSwCrossConn.UNKNOWN_STATUS] = true;
        TMP_CROSS_CONNS_TRANSITIONS[OSwCrossConn.UNKNOWN_STATUS][OSwCrossConn.CCONNOK] = true;
        TMP_CROSS_CONNS_TRANSITIONS[OSwCrossConn.UNKNOWN_STATUS][OSwCrossConn.CCONNERR] = true;
        TMP_CROSS_CONNS_TRANSITIONS[OSwCrossConn.UNKNOWN_STATUS][OSwCrossConn.WFORCCONN1] = true;

        //CCONNOK
        TMP_CROSS_CONNS_TRANSITIONS[OSwCrossConn.CCONNOK][OSwCrossConn.CCONNOK] = true;
        TMP_CROSS_CONNS_TRANSITIONS[OSwCrossConn.CCONNOK][OSwCrossConn.CCONNERR] = true;
        TMP_CROSS_CONNS_TRANSITIONS[OSwCrossConn.CCONNOK][OSwCrossConn.WFORFREE1] = true;

        //CCONNERR
        TMP_CROSS_CONNS_TRANSITIONS[OSwCrossConn.CCONNERR][OSwCrossConn.CCONNOK] = true;
        TMP_CROSS_CONNS_TRANSITIONS[OSwCrossConn.CCONNERR][OSwCrossConn.CCONNERR] = true;
        TMP_CROSS_CONNS_TRANSITIONS[OSwCrossConn.CCONNERR][OSwCrossConn.WFORFREE1] = true;

        //WFORCCONN1
        TMP_CROSS_CONNS_TRANSITIONS[OSwCrossConn.WFORCCONN1][OSwCrossConn.WFORCCONN2] = true;
        TMP_CROSS_CONNS_TRANSITIONS[OSwCrossConn.WFORCCONN1][OSwCrossConn.CCONNERR] = true;
        TMP_CROSS_CONNS_TRANSITIONS[OSwCrossConn.WFORCCONN1][OSwCrossConn.WFORFREE1] = true;

        //WFORCCONN2
        TMP_CROSS_CONNS_TRANSITIONS[OSwCrossConn.WFORCCONN2][OSwCrossConn.CCONNOK] = true;
        TMP_CROSS_CONNS_TRANSITIONS[OSwCrossConn.WFORCCONN2][OSwCrossConn.CCONNERR] = true;
        TMP_CROSS_CONNS_TRANSITIONS[OSwCrossConn.WFORCCONN2][OSwCrossConn.WFORFREE1] = true;

        //WFORFREE1
        TMP_CROSS_CONNS_TRANSITIONS[OSwCrossConn.WFORFREE1][OSwCrossConn.WFORFREE2] = true;

        //just assign it...
        CROSS_CONNS_TRANSITIONS = TMP_CROSS_CONNS_TRANSITIONS;
    };

    public static final OSwFSM getInstance() {
        if (!fsmInited) {
            synchronized (OSwFSM.class) {
                thisInstance = new OSwFSM();
                fsmInited = true;
            }
        }
        return thisInstance;
    }

    public final void changeConfig(final OSwConfig newConfig) {

        synchronized (lock) {
            if (this.oswConfig == null) {
                this.oswConfig = newConfig;
                logger.log(Level.INFO, " [ OSwFSM ] Setting new config .... " + this.oswConfig.getExtendedStatus());
            }
        }

    }

    public final void changeState(final OSwCrossConn oscc, short newState) throws InvalidStateTransitionException {
        StringBuilder sb = null;

        //TODO - when this seems to work fine make this synchronized section smaller ( only if() should be sync )
        //     - for the moment it is better to sync the logger also ......
        synchronized (lock) {
            if (logger.isLoggable(Level.FINE)) {
                sb = new StringBuilder();
                sb.append("\n [ OSwFSM ] Changing state for CrossConnect: [ ").append(oscc).append(" ]");
                sb.append("\nto newState ").append(OSwCrossConn.decodeState(newState)).append("\n");
            }

            try {
                if (CROSS_CONNS_TRANSITIONS[oscc.state][newState]) {
                    if (oscc.state != newState) {
                        oscc.state = newState;
                        //TODO - notify config change ?!?
                    }
                } else {
                    throw new InvalidStateTransitionException("Invalid transition from current port power state: "
                            + oscc.getDecodedState() + " to new state: " + OSwCrossConn.decodeState(newState));
                }
            } finally {
                if (logger.isLoggable(Level.FINE)) {
                    if (logger.isLoggable(Level.FINER)) {
                        sb.append("\n [ OSwFSM ] CrossConnect state after transaction [ ").append(oscc).append(" ]");
                    }
                    logger.log(Level.FINE, sb.toString());
                }
            }
        }//end sync
    }

    public final void changeState(final OSwPort port, short newState) throws InvalidStateTransitionException {
        StringBuilder sb = null;

        //TODO - when this seems to work fine make this synchronized section smaller ( only if() should be sync )
        //     - for the moment it is better to sync the logger also ......
        synchronized (lock) {
            if (logger.isLoggable(Level.FINE)) {
                sb = new StringBuilder();
                sb.append("\n [ OSwFSM ] Changing state for Port: [ ").append(port.getExtendedState()).append(" ]");
                sb.append("\nto newState ").append(OSwPort.decodePortPowerState(newState)).append("\n");
            }
            try {
                if (PORT_POWER_TRANSITIONS[port.powerState][newState]) {
                    if (port.powerState != newState) {
                        port.powerState = newState;
                        //TODO - notify config change ?!?
                    }
                } else {
                    throw new InvalidStateTransitionException("Invalid transition from current port power state: "
                            + port.getDecodedPortPowerState() + " to new state: "
                            + OSwPort.decodePortPowerState(newState));
                }
            } finally {
                if (logger.isLoggable(Level.FINE)) {
                    if (logger.isLoggable(Level.FINER)) {
                        sb.append("\n [ OSwFSM ] Port extended state after transaction [ ")
                                .append(port.getExtendedState()).append(" ]");
                    }
                    logger.log(Level.FINE, sb.toString());
                }
            }
        }//end sync

    }

}
