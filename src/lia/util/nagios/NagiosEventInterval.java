package lia.util.nagios;

import java.util.concurrent.TimeUnit;

/**
 *
 * Ordered by startEvent
 *
 * @author ramiro
 */
public class NagiosEventInterval implements Comparable {

    public final NagiosLogEntry startEvent;
    public final NagiosLogEntry endEvent;

    public NagiosEventInterval(final NagiosLogEntry startEvent, final NagiosLogEntry endEvent) {
        super();
        if (startEvent == null || endEvent == null) {
            throw new NullPointerException(" startEvent and endEvent cannot be null ");
        }
        this.startEvent = startEvent;
        this.endEvent = endEvent;
    }

    boolean isInEvent(final long time) {
        return startEvent.time <= time && time < endEvent.time;
    }

    public String toString() {
        return " DT: " + TimeUnit.MILLISECONDS.toMinutes(endEvent.time - startEvent.time) + " minutes; startEvent: " + startEvent + "; endEvent: " + endEvent;
    }

    public int compareTo(Object o) {
        return startEvent.compareTo(((NagiosEventInterval) o).startEvent);
    }
}
