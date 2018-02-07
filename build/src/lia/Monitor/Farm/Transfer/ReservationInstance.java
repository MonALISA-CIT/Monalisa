package lia.Monitor.Farm.Transfer;

import java.util.List;
import java.util.logging.Logger;

import lia.Monitor.monitor.Result;
import lia.util.ntp.NTPDate;

/**
 * Hold and control a reservation instance.
 * 
 * @author catac
 */
class ReservationInstance implements ProtocolInstance {
    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(ReservationInstance.class.getName());

    private final String rsvID; // reservation ID
    private final long bw; // bandwidth allocated to this reservation, in bits/s
    private final long period; // period for which this reservation is made, in millis
    private long startTime; // moment when this reservation has started

    volatile boolean active; // if start()-ed, then it's active - bandwidth is reported

    /** 
     * Initialize a reservation instance.
     * @param reservationID The reservation ID.
     * @param bandwidth the bandwidth allocated to this reservation.
     * @param periodSeconds the period for which the reservation will be active
     */
    public ReservationInstance(String reservationID, long bandwidth, long periodSeconds) {
        this.rsvID = reservationID;
        this.bw = bandwidth;
        this.period = periodSeconds * 1000;
        active = false;
    }

    @Override
    public boolean start() {
        active = true;
        startTime = NTPDate.currentTimeMillis();
        return true;
    }

    @Override
    public boolean stop() {
        active = false;
        return true;
    }

    @Override
    public boolean checkStatus(List lResults) {
        long now = NTPDate.currentTimeMillis();
        long timeLeft = (startTime + period) - now;
        if (timeLeft <= 0) {
            logger.info("Period for reservation " + rsvID + " has finished (" + (period / 1000) + " sec).");
            active = false; // if it's active for more than the requested period
        }
        Result r = new Result(TransferUtils.farmName, "RSV_Transfers", rsvID, TransferUtils.resultsModuleName,
                new String[] { "bandwidth_Mb", "period", "timeleft" }, new double[] {
                        active ? bw / 1000.0 / 1000.0 : 0.0, period / 1000, timeLeft / 1000 });
        r.time = now;
        lResults.add(r);
        if (!active) {
            logger.info("Cleaning up reservation " + rsvID);
        }
        return active;
    }
}
