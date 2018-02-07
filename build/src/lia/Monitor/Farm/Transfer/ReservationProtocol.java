package lia.Monitor.Farm.Transfer;

import java.util.Properties;
import java.util.logging.Logger;

/**
 * Support for handling the Reservation Transfer Protocol. This protocol doesn't
 * effectively transfer files - its purpose is to "reserve" a given amount of bandwidth
 * between two end-points - so that it can be freely available to the user.
 * 
 * This is simulated by pretending to do a transfer of the requested speed (bandwidth).
 * 
 * Configuration parameters:
 * -- none --
 *
 * @author catac
 */
public class ReservationProtocol extends TransferProtocol {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ReservationProtocol.class.getName());

    /**
     * Initialize the Reservation Transfer Protocol
     * @param appTransfer the AppTransfer that created this protocol.
     */
    public ReservationProtocol() {
        super("rsv");
    }

    @Override
    public String startInstance(Properties props) {
        StringBuilder sbRes = new StringBuilder("-ERR Failed to make reservation. ");
        String reservationID = props.getProperty("requestID");
        String bw = props.getProperty("bandwidth");
        String period = props.getProperty("period");

        if (reservationID == null) {
            sbRes.append("requestID missing");
        } else if (htInstances.get(reservationID) != null) {
            sbRes.append("reservation ").append(reservationID).append(" already created!");
        } else if (bw == null) {
            sbRes.append("bandwidth missing");
        } else if (period == null) {
            sbRes.append("period missing");
        } else {
            long lBw = -1;
            if (bw != null) {
                lBw = TransferUtils.parseBKMGps(bw);
            }
            long lPeriod = -1;
            try {
                lPeriod = Long.parseLong(period);
            } catch (NumberFormatException nfe) {
                sbRes.append("value for period is not a number: ").append(period);
            }

            if (lBw < 0) {
                sbRes.append("bandwidth cannot be negative: ").append(lBw);
            } else if (lPeriod < 0) {
                sbRes.append("period cannot be negative: ").append(lPeriod);
            } else {
                ReservationInstance rsvInstance = new ReservationInstance(reservationID, lBw, lPeriod);
                if (rsvInstance.start()) {
                    htInstances.put(reservationID, rsvInstance);
                    sbRes.setLength(0); // reset the response buffer
                    sbRes.append("+OK Reservation ").append(reservationID).append(" created");
                } else {
                    sbRes.append("Cannot create reservation. See service logs.");
                }
            }
        }
        return sbRes.toString();
    }

    @Override
    public String stopInstance(Properties props) {
        StringBuilder sbRes = new StringBuilder("-ERR Failed to terminate reservation. ");
        String reservationID = props.getProperty("requestID");
        if (reservationID == null) {
            sbRes.append("requestID missing");
        } else if (htInstances.get(reservationID) == null) {
            sbRes.append("reservation ").append(reservationID).append(" not existing!");
        } else {
            ReservationInstance rsvInstance = (ReservationInstance) htInstances.get(reservationID);
            if (rsvInstance.stop()) {
                sbRes.setLength(0);
                sbRes.append("+OK Reservation ").append(reservationID).append(" finished.");
            } else {
                sbRes.append("Cannot finish transfer. See service logs.");
            }
        }
        return sbRes.toString();
    }

    @Override
    public void updateConfig() {
        // no local config
    }

    @Override
    public String getProtocolUsage() {
        StringBuilder sb = new StringBuilder("ReservationProtocol:\n");
        sb.append("rsv start&requestID=string&bandwidth=number&period=number\n");
        sb.append("\tstart a new bandwidth reservation, with the given parameters\n");
        sb.append("rsv stop&requestID=string\n");
        sb.append("\tstop immediately the reservation with the given requestID\n");
        sb.append("rsv help\n");
        sb.append("\treturn this help");
        sb.append("Parameters:\n");
        sb.append("\trequestID\t-a string representing the request ID\n");
        sb.append("\tbandwidth\t-number, bandwidth to be reserved, in in bps (bits/second)\n");
        sb.append("\tperiod\t-number, length of this bandwidth reservation, in seconds");
        return sb.toString();
    }
}
