package lia.util.nagios;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lia.Monitor.monitor.Result;

/**
 *
 * @author ramiro
 */
public class MLNagiosResultsProducer {

    public static List<Result> getMLresults(final Map<String, Set<NagiosEventInterval>> nagiosEventsMap,
            final Map<String, String> linksServiceNameMap,
            final Map<String, String> linksNodeNameMap,
            final Map<String, String> linksParamNameMap,
            final long sTime,
            final long endTime, final long resultsDelay) {

        List<Result> resultsList = new LinkedList<Result>();

//        for (final Iterator<Map.Entry<String, Set<NagiosEventInterval>>> it = nagiosEventsMap.entrySet().iterator(); it.hasNext();) {
//            final Map.Entry<String, Set<NagiosEventInterval>> entry = it.next();
//
//            final String nLinkName = entry.getKey();
//            if (!nLinkName.equals("GVA1 GVA2 LINK")) {
//                continue;
//            }
//            final Set<NagiosEventInterval> nEventsSet = entry.getValue();
//
//            long cTime = sTime;
//
//            if (cTime <= endTime) {
//
//                for (final Iterator<NagiosEventInterval> downIterator = nEventsSet.iterator(); downIterator.hasNext();) {
//                    final NagiosEventInterval cNEI = downIterator.next();
//
//                    while (cTime < cNEI.startEvent.time && cTime <= endTime) {
//
//                        //add up
//                        resultsList.addAll(getResults(
//                                linksServiceNameMap.get(nLinkName),
//                                linksNodeNameMap.get(nLinkName),
//                                linksParamNameMap.get(nLinkName),
//                                1D, cTime));
//
//                        cTime += resultsDelay;
//                    }
//
//                    if (cTime >= cNEI.endEvent.time && cTime <= endTime) {
//                        //add up
//                        resultsList.addAll(getResults(
//                                linksServiceNameMap.get(nLinkName),
//                                linksNodeNameMap.get(nLinkName),
//                                linksParamNameMap.get(nLinkName),
//                                1D, cTime));
//                        System.out.println("\n\n NagiosEventInterval too short; ignoring it: " + cNEI);
//                    } else {
//                        while (cTime < cNEI.endEvent.time && cTime <= endTime) {
//                            //add down
//                            resultsList.addAll(getResults(
//                                    linksServiceNameMap.get(nLinkName),
//                                    linksNodeNameMap.get(nLinkName),
//                                    linksParamNameMap.get(nLinkName),
//                                    2D, cTime));
//                            cTime += resultsDelay;
//                        }
//                    }
//
//                    if (cTime > endTime) {
//                        break;
//                    }
//                }
//
//                while (cTime <= endTime) {
//                    //add up
//                    resultsList.addAll(getResults(
//                            linksServiceNameMap.get(nLinkName),
//                            linksNodeNameMap.get(nLinkName),
//                            linksParamNameMap.get(nLinkName),
//                            1D, cTime));
//
//                    cTime += resultsDelay;
//                }
//            }
//        }

        //special case for GVA1-GVA2 ( no downtime )
        long cTime = sTime;
        while (cTime <= endTime) {
            //add up
            resultsList.addAll(getResults(
                    "GVA_USLHCNET",
                    "192.65.196.253",
                    "GVA-NY_OperStatus",
                    1D, cTime));

            cTime += resultsDelay;
        }

        return resultsList;
    }

    private static final Collection<Result> getResults(final String serviceName, final String nodeName, final String param, final double value, final long time) {
        List<Result> al = new LinkedList<Result>();

        Result r = new Result();

        r.FarmName = serviceName;
        r.ClusterName = (!serviceName.endsWith("_CDS") ? "WAN_Stats" : "SonetIntf_Status");
        r.NodeName = nodeName;
        r.time = time + (long) Math.abs(5D * Math.random() * 1000D);
        r.addSet(param, value);

        if (value > 1D || value < 1D) {
            System.out.println(r);
        }

        al.add(r);

        Result ar = new Result();
        ar.FarmName = "_TOTALS_";
        ar.ClusterName = r.ClusterName;
        ar.NodeName = "_INDIVIDUAL_";
        ar.time = r.time;
        ar.addSet(param, value);

        al.add(ar);

        ar = new Result();
        ar.FarmName = "_TOTALS_";
        ar.ClusterName = r.ClusterName;
        ar.NodeName = "_AGGREGATED_";
        ar.time = r.time;
        ar.addSet(param, value);
        al.add(ar);

        return al;
    }
}
