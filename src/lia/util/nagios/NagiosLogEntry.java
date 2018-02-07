package lia.util.nagios;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 *
 *
 * @author ramiro
 */
public class NagiosLogEntry implements Comparable {

    /**
     * Timestamp of the event ( in millis since 1970 )
     */
    public final long time;

    /**
     * Entry in the log file; used mainly for debug
     */
    public final String line;

    /**
     * Device name which reported the event
     */
    public final String device;

    /**
     * Link name (interface) on which the event occured
     */
    public final String linkName;

    /**
     * Status of the link for this event
     */
    public final short linkStatus;

    public NagiosLogEntry(final String line) throws Exception {
        super();
        if (line == null) {
            throw new NullPointerException(" line cannot be null ");
        }

        this.line = line;
        final int idx1 = line.indexOf("[");
        final int idx2 = line.indexOf("]");
        long ntime = -1;
        if (idx1 >= 0 && idx2 >= 0 && idx1 < idx2) {
            ntime = Long.parseLong(line.substring(idx1 + 1, idx2));
            ntime = TimeUnit.SECONDS.toMillis(ntime);
        }
        if (ntime <= 0) {
            throw new Exception(" Cannot create NagiosLogEntry from line: " + line);
        }

        this.time = ntime;
        String[] tks = line.split(";");

        device = tks[1].trim();

        final int idxLink = tks[2].indexOf("LINK STATUS");

        linkName = tks[2].substring(0, idxLink).trim();

        if (line.indexOf("DOWN") > 0 || line.indexOf("flapping") > 0 || line.indexOf("WARNING") > 0 || line.indexOf("CRITICAL") > 0) {
            linkStatus = NagiosUtils.LINK_DOWN;
        } else if (tks[5].equals("Link is UP")) {
            linkStatus = NagiosUtils.LINK_OK;
        } else {
            linkStatus = NagiosUtils.LINK_UNK;
        }
        if (this.linkStatus == NagiosUtils.LINK_UNK) {
            System.err.println("\n\n UNKOWN LINK STATUS for line: " + line + "\n\n");
            System.out.println("\n\n UNKOWN LINK STATUS for line: " + line + "\n\n");
            System.out.flush();
            System.err.flush();
        }
    }

    public boolean equals(Object o) {
        if (o instanceof NagiosLogEntry) {
            return this.line.equals(((NagiosLogEntry) o).line);
        }

        return false;
    }

    public int hashCode() {
        return this.line.hashCode();
    }

    public int compareTo(Object o) {
        final NagiosLogEntry entry = (NagiosLogEntry) o;

        if (this.time < entry.time) {
            return -1;
        }

        if (this.time > entry.time) {
            return 1;
        }

        return this.line.compareTo(entry.line);
    }

    public String toString() {
//        return " [ " + new Date(time).toString() + " ] dev:" + device + "; linkName:" + linkName + "; status: " + NagiosLogsParser.getLinkStatus(linkStatus) + "; line:" + line;
        return " [ " + new Date(time).toString() + " ] dev:" + device + "; linkName:" + linkName + "; status: " + NagiosUtils.getLinkStatus(linkStatus);
    }
}
