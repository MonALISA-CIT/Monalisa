/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package lia.util.nagios;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import lia.Monitor.Store.Fast.BatchProcessor;
import lia.Monitor.Store.Fast.DBWriter4;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;

/**
 *
 * @author ramiro
 */
public class MLNagiosImporter {

    private static final DBWriter4 rawDataSaver;


    static {
        AppConfig.getProperty("just init");
        DBWriter4 tmpSaver = null;
        try {
            tmpSaver = new DBWriter4(-1, -1, "wan_rawstatus", DBWriter4.DB4_MODE_RAW);
        } catch (Throwable t) {
            System.err.println(" [ NagiosLogsParser ] Unable to init DBWriter4. Cause: ");
            t.printStackTrace();
        }

        rawDataSaver = tmpSaver;

    }

    public static final void printHelp() {
        System.err.println(" MLNagiosImporter -Dlia.Monitor.ConfigURL=<configFile>");
    }

    public static final void main(String[] args) throws Exception {
        final String nsLogDir = AppConfig.getProperty("nagiosLogDir");

        if (nsLogDir == null) {
            System.err.println("no nagiosLogDir defined");
            System.exit(-1);
        }

        final String NAGIOS_FILENAME_PREFIX = AppConfig.getProperty("nagiosLogPrefix");
        final String NAGIOS_FILENAME_SUFFIX = AppConfig.getProperty("nagiosLogSuffix");

        NagiosLogsParser nlp = NagiosLogsParser.getInstance(
                nsLogDir, NAGIOS_FILENAME_PREFIX, NAGIOS_FILENAME_SUFFIX,
                AppConfig.getProperty("startDate"), AppConfig.getProperty("endDate"), TimeUnit.MINUTES.toMillis(AppConfig.getl("maxDowntimeTolerance")));

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n Event map:\n\n");

        final Map<String, Set<NagiosEventInterval>> nagiosIntervalsMap = nlp.getDownIntervalMap();

        for (Iterator<Map.Entry<String, Set<NagiosEventInterval>>> it = nagiosIntervalsMap.entrySet().iterator(); it.hasNext();) {
            final Map.Entry<String, Set<NagiosEventInterval>> entry = it.next();

            final String linkName = entry.getKey();
            final Set<NagiosEventInterval> nIntervalSet = entry.getValue();

            NagiosEventInterval firstNev = null;
            NagiosEventInterval lastNev = null;

            sb.append("\n ==============================");
            sb.append("\n There are ").append(nIntervalSet.size()).append(" events for the link: ").append(linkName);
            sb.append("\n ==============================");

            for (Iterator<NagiosEventInterval> itev = nIntervalSet.iterator(); itev.hasNext();) {
                final NagiosEventInterval nev = itev.next();
                if (firstNev == null) {
                    firstNev = nev;
                }
                lastNev = nev;
                sb.append("\n ").append(nev);
            }
            sb.append("\n ==============================");
            sb.append("\n There were ").append(nIntervalSet.size()).append(" events for the link: ").append(linkName).append("; firstEvent: ").append(firstNev).append("; lastEvent: ").append(lastNev);
            sb.append("\n ==============================");
        }


        sb.append("\n\n Nagios Links: ").append(nagiosIntervalsMap.keySet()).append("\n\n");

        final String linksServiceNameProp = AppConfig.getProperty("links_service_map");
        final String linksNodeNameProp = AppConfig.getProperty("links_nodes_map");
        final String linksParamNameProp = AppConfig.getProperty("links_params_map");

        final long sTime = NagiosUtils.getImportDate(AppConfig.getProperty("startDate"));
        final long endTime = NagiosUtils.getImportDate(AppConfig.getProperty("endDate"));
        final long resultsDelay = TimeUnit.SECONDS.toMillis(AppConfig.getl("resultsDelay"));

        final Map<String, String> linksServiceNameMap = NagiosUtils.parseServiceNodeNameProp(linksServiceNameProp);
        final Map<String, String> linksNodeNameMap = NagiosUtils.parseServiceNodeNameProp(linksNodeNameProp);
        final Map<String, String> linksParamNameMap = NagiosUtils.parseServiceNodeNameProp(linksParamNameProp);

        List<Result> resList = MLNagiosResultsProducer.getMLresults(nagiosIntervalsMap, linksServiceNameMap, linksNodeNameMap, linksParamNameMap, sTime, endTime, resultsDelay);

        final int resLen = resList.size();
        System.out.println("There are: " + resLen + " results to be inserted between: \n " + new Date(resList.get(0).time) + " -> " + new Date(resList.get(resLen - 1).time));
        int i = 0;
//        System.out.println("\n\n\n Results:\n\n" + resList );
        for (Result r : resList) {
            i++;
            if (i % 1000 == 0) {
                System.out.println(" Inserted: " + i + " results / " + resLen + " ( " + (double) i * 100D / (double) resLen + "% )");
                try {
                    for (;;) {
                        final int bqs = BatchProcessor.getBatchQuerySize();
                        final int bqs1 = BatchProcessor.getBatchV1Size();
                        final int bqs2 = BatchProcessor.getBatchV2Size();
                        final int bqs3 = BatchProcessor.getBatchV3Size();
                        System.out.println("Batch processor stat: bqs: " + bqs + "; bqs1: " + bqs1 + "; bqs2: " + bqs2 + "; bqs3: " + bqs3);
                        if(bqs == 0 && bqs1 == 0 && bqs2 == 0 && bqs3 == 0) {
                            break;
                        }
                        Thread.sleep(1000);
                    }
                } catch (Throwable t) {
                }
            }
//            rawDataSaver.addSample(r);
        }


        System.out.println("WAITING FINISHHHHH!!!!! waiting for batch processor");
        for (;;) {
            final int bqs = BatchProcessor.getBatchQuerySize();
            final int bqs1 = BatchProcessor.getBatchV1Size();
            final int bqs2 = BatchProcessor.getBatchV2Size();
            final int bqs3 = BatchProcessor.getBatchV3Size();

            if (bqs > 0 || bqs1 > 0 || bqs2 > 0 || bqs3 > 0) {
                System.out.println("WAITING FINISHHHHH!!!!! waiting for batch processor bqs: " + bqs + "; bqs1: " + bqs1 + "; bqs2: " + bqs2 + "; bqs3: " + bqs3);
                try {
                    Thread.sleep(1000);
                } catch (Throwable t) {
                }
            } else {
                break;
            }

        }

            final int bqs = BatchProcessor.getBatchQuerySize();
            final int bqs1 = BatchProcessor.getBatchV1Size();
            final int bqs2 = BatchProcessor.getBatchV2Size();
            final int bqs3 = BatchProcessor.getBatchV3Size();
            System.out.println("END!!! !!!!! waiting for batch processor bqs: " + bqs + "; bqs1: " + bqs1 + "; bqs2: " + bqs2 + "; bqs3: " + bqs3);
    }
}
