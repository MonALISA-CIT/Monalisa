/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package lia.util.nagios;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author ramiro
 */
public class NagiosUtils {

    //possible link status
    public static final short LINK_UNK = 0;    //unkown
    public static final short LINK_OK = 1;     //link is up
    public static final short LINK_DOWN = 2;   //link is down
    private final static SimpleDateFormat dateParser = new SimpleDateFormat("yy-MM-dd HH:mm:ss");

    public final static String getLinkStatus(short linkStatus) {
        switch (linkStatus) {
            case LINK_OK:
                return "LINK_OK";
            case LINK_DOWN:
                return "LINK_DOWN";
        }
        return "LINK_UNK";
    }

    public static final Map<String, Set<NagiosLogEntry>> getNagiosLogEntries(final long startDate, final long endDate,
            final String[] logFiles, final String nagiosLogDir) throws Exception {
        if (logFiles == null) {
            throw new NullPointerException("Log files cannot be null");
        }

        final int fLen = logFiles.length;

        final Map<String, Set<NagiosLogEntry>> nagiosLogsMap = new TreeMap<String, Set<NagiosLogEntry>>();

        for (int i = 0; i < fLen; i++) {
            Reader fr = null;
            BufferedReader br = null;
            try {
                fr = new FileReader(nagiosLogDir + File.separator + logFiles[i]);
                br = new BufferedReader(fr);

                String line = null;
                while ((line = br.readLine()) != null) {
                    if (line.indexOf("SERVICE NOTIF") > 0 && line.indexOf("LINK") > 0 && line.indexOf("dann") > 0) {

                        final NagiosLogEntry nagiosLogEntry = new NagiosLogEntry(line);

                        Set<NagiosLogEntry> evSet = nagiosLogsMap.get(nagiosLogEntry.linkName);

                        if (evSet == null) {
                            evSet = new TreeSet<NagiosLogEntry>();
                            nagiosLogsMap.put(nagiosLogEntry.linkName, evSet);
                        }

                        if (startDate > 0 && nagiosLogEntry.time < startDate) {
                            continue;
                        }

                        if (endDate > 0 && nagiosLogEntry.time > endDate) {
                            continue;
                        }

                        evSet.add(nagiosLogEntry);
                    }
                }
            } finally {
                if (br != null) {
                    try {
                        br.close();
                    } catch (Throwable ign) {
                    }
                }
                if (fr != null) {
                    try {
                        fr.close();
                    } catch (Throwable ign) {
                    }
                }
            }
        }//for()

        return nagiosLogsMap;
    }

    public static final Map<String, Set<NagiosEventInterval>> getNagiosDownIntervals(final long startDate, final long endDate, final long maxAllowedDownTime,
            final String[] logFiles, final String nagiosLogDir) throws Exception {

        final Map<String, Set<NagiosLogEntry>> nagiosLogsMap = getNagiosLogEntries(startDate, endDate, logFiles, nagiosLogDir);
        final Map<String, Set<NagiosEventInterval>> nagiosDownIntervals = new TreeMap<String, Set<NagiosEventInterval>>();

        for (Iterator<Map.Entry<String, Set<NagiosLogEntry>>> it = nagiosLogsMap.entrySet().iterator(); it.hasNext();) {
            final Map.Entry<String, Set<NagiosLogEntry>> entry = it.next();

            final String linkName = entry.getKey();
            final Set linkEvents = entry.getValue();

            NagiosLogEntry startNagiosEvent = null;
            NagiosLogEntry previousNagiosEvent = null;


            for (Iterator<NagiosLogEntry> itl = linkEvents.iterator(); itl.hasNext();) {
                final NagiosLogEntry cNagiosEvent = itl.next();

                switch (cNagiosEvent.linkStatus) {
                    case NagiosUtils.LINK_DOWN: {
                        if (startNagiosEvent == null) {
                            startNagiosEvent = cNagiosEvent;
                        } else {
                            // another notif for the same link
//                            System.out.println(" Same down notif for link: " + linkName +
//                                    "\n Current event: " + cNagiosEvent +
//                                    "\n Previous Event: " + previousNagiosEvent +
//                                    "\n Start Event: " + startNagiosEvent);
                        }
                        break;
                    }
                    case NagiosUtils.LINK_OK: {
                        if (startNagiosEvent == null) {
                            System.out.println("\n\n\n !!!!!!!!!!!!! IGNORING LINK OK EVENT .... NO LINK DOWN BEFORE for Link: " + linkName + " event: " + startNagiosEvent + " !!!!!!!!!!! \n\n\n");
                        } else {

                            boolean ignore = false;
                            if (maxAllowedDownTime > 0 && cNagiosEvent.time - startNagiosEvent.time > maxAllowedDownTime) {
                                ignore = true;
                                System.out.println("\n\n [ EVENT FINISHED " + ((ignore) ? "BUT IGNORED" : "") + " ] [ " + linkName + " ] " +
                                        "[ " + new Date(startNagiosEvent.time) + " -> " + new Date(cNagiosEvent.time) + " ] " +
                                        " DT: " + TimeUnit.MILLISECONDS.toMinutes(cNagiosEvent.time - startNagiosEvent.time) +
                                        "\n StartEvent: " + startNagiosEvent +
                                        "\n EndEvent: " + cNagiosEvent + "\n");

                            }


                            if (!ignore) {
                                final NagiosEventInterval nei = new NagiosEventInterval(startNagiosEvent, cNagiosEvent);

                                Set<NagiosEventInterval> nIntervalSet = nagiosDownIntervals.get(linkName);
                                if (nIntervalSet == null) {
                                    nIntervalSet = new TreeSet();
                                    nagiosDownIntervals.put(linkName, nIntervalSet);
                                }

                                nIntervalSet.add(nei);
                            }
                            startNagiosEvent = null;
                        }
                        break;
                    }
                    default:
                        System.err.println();
                }

                previousNagiosEvent = cNagiosEvent;
            }

        }

        return nagiosDownIntervals;
    }

    public static final long getImportDate(final String sDate) throws ParseException {
        Date tmpDate = null;
        if (sDate != null) {
            tmpDate = dateParser.parse(sDate);
        }

        if (tmpDate != null) {
            return tmpDate.getTime();
        }

        return -1L;
    }

    public final static Map<String, String> parseServiceNodeNameProp(final String property) {
        final String[] tks = property.split("(\\s)*;(\\s)*");

        Map<String, String> retMap = new TreeMap<String, String>();

        for (int i = 0; i < tks.length; i++) {
            final String[] maps = tks[i].split("(\\s)*:(\\s)*");
            retMap.put(maps[0], maps[1]);
        }

        return retMap;
    }

    public static final void main(String[] args) throws Exception {
        Date d = dateParser.parse("2008-07-15 0:0:0");
        System.out.println("Date: " + d+ " ms: " + d.getTime() + " s: " + d.getTime()/1000);
    }
}
