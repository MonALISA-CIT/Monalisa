package lia.Monitor.JiniClient.Farms.Tabl;

import java.awt.Color;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.Vector;

import lia.Monitor.JiniClient.CommonGUI.rcNode;
import lia.Monitor.JiniClient.CommonGUI.Tabl.TabPanBase;
import lia.Monitor.JiniClient.CommonGUI.Tabl.TableSorter;
import lia.Monitor.monitor.ExtendedSiteInfoEntry;
import lia.Monitor.monitor.Gresult;
import lia.Monitor.monitor.monPredicate;

public class TabPan extends TabPanBase {

    public TabPan() {
        super();
        Vector vColumns = new Vector();

        ColumnProperties colP;
        monPredicate monP;
        colP = new ColumnProperties("FarmName", "text", "<html>\n  <p> <b>Regional Center</b> <p> <i>[select to access] </i>", 130, 120, myren);
        colP.setFixed(true);
        vColumns.add(colP);
        /**
         * VERY IMPORTANT
         * there is no need to create and add the columns to table, because they will automatically be
         * created by the table, because they appear in the model.
         */
        colP = new ColumnProperties("Hostname", "text", "<html><b>Hostname</b>", 130, 120);
        colP.setFixed(true);
        vColumns.add(colP);
        colP = new ColumnProperties("LocalTime", "time", "<html><b>Local Time</b>", 90, 90);
        colP.setFixed(true);
        vColumns.add(colP);
        colP = new ColumnProperties("MLVer", "version", "<html><b>MonaLisa<br>Version</b>", 85, 85);
        colP.setFixed(true);
        vColumns.add(colP);
        colP = new ColumnProperties("JavaVer", "version", "<html><b>Java VM<br>Version</b>", 85, 85);
        colP.setFixed(true);
        vColumns.add(colP);
        colP = new ColumnProperties("Group", "text", "<html><b>Group</b>", 70, 70);
        colP.setFixed(true);
        vColumns.add(colP);
        colP = new ColumnProperties("MLUptime", "uptime", "<html><b>ML UpTime</b>", 90, 90);
        vColumns.add(colP);
        rcRendererBg1Label myrebg1 = new rcRendererBg1Label(50, new Color(255, 255, 255), 2000, new Color(119, 189, 255));
        colP = new ColumnProperties("TotalParams", "number", "<html><b>Total<br> Params</b>", 70, 70, myrebg1);
        colP.setFixed(true);
        vColumns.add(colP);

        // add columns that are based on predicates
        rcRendererBg2Label myrebg2 = new rcRendererBg2Label(2, new Color(225, 255, 225), 7, new Color(160, 255, 160), 20, new Color(220, 120, 120), 40, new Color(255, 160, 160), new Color(252, 255, 192));
        monP = new monPredicate("*", "MonaLisa", "localhost", -1, -1, new String[] {
            "MLCPUTime"
        }, null);
        // the code name of the column is strongly related to the name of the parameter and the panel's name
        colP = new ColumnProperties("TabPan>MLCPUTime", "number", "<html><b>ML CPUTime</b><br><i>[%]</i>", 100, 84, myrebg2);
        colP.setPredicate(monP);
        vColumns.add(colP);
        rcRendererBg2Label myrebg3 = new rcRendererBg2Label(0, new Color(225, 255, 225), 0.25, new Color(160, 255, 160), 0.75, new Color(255, 220, 220), 2, new Color(255, 180, 180), new Color(252, 255, 192));
        monP = new monPredicate("*", "MonaLisa", "localhost", -1, -1, new String[] {
            "Load5"
        }, null);
        // the code name of the column is strongly related to the name of the parameter and the panel's name
        colP = new ColumnProperties("TabPan>Load5", "number", "<html><b>Master<br> Load</b>", 70, 70, myrebg3);
        colP.setPredicate(monP);
        vColumns.add(colP);
        rcRendererBg1Label myrebg4 = new rcRendererBg1Label(0, new Color(240, 255, 255), 100, new Color(119, 255, 255));
        monP = new monPredicate("*", "MonaLisa", "localhost", -1, -1, new String[] {
            "CollectedValuesRate"
        }, null);
        colP = new ColumnProperties("TabPan>CollectedValuesRate", "number", "<html><b>Collected params<br> Rate</b> <i>[params/s]</i>", 130, 130, myrebg4);
        colP.setPredicate(monP);
        vColumns.add(colP);
        rcRendererBg1Label myrebg5 = new rcRendererBg1Label(10, new Color(198, 210, 209), 500, new Color(184, 221, 207));
        monP = new monPredicate("*", "MonaLisa", "localhost", -1, -1, new String[] {
            "embedded_store_size"
        }, null);
        colP = new ColumnProperties("TabPan>embedded_store_size", "number", "<html><b>Database<br> size</b> <i>[MB]</i>", 80, 80, myrebg5);
        colP.setPredicate(monP);
        vColumns.add(colP);

        ginit(new rcTableModel(vColumns));
    }

    public static final String getJavaVersion(ExtendedSiteInfoEntry esie) {
        if(esie != null && esie.JVM_VERSION != null) {
            final String[] splitTks = esie.JVM_VERSION.split("(\\s)+");
            return splitTks[splitTks.length - 1];
        }
        return "N/A";
    }

    public static final String getJavaVersion(rcNode n) {
        if (n != null && n.client != null && n.client.esie != null) {
            return getJavaVersion(n.client.esie);
        }
        return "N/A";
    }

    class rcTableModel extends MyTableModel/* implements TableTotalsInterface */{

        public rcTableModel(Vector vColumns) {
            super(vColumns);
        }

        public int getRowCount() {
            if (nodes == null)
                return 0;
            return nodes.size();
        }

        /**
         * gets the value that will be rendered on the table from the internal structure
         * based on two indexes: row and col that hopefully uniquely identify an element
         * in the structure.<br>
         * Ougth to be rewrited in derived classes.
         * 
         * @param row
         * @param col
         * @return the object value
         */
        public Object getValueAt(int row, int col) {
            if (row < 0)
                return getColumnType(col);
            rcNode n = null;
            try {
                n = vnodes.elementAt(row);
            } catch (Exception ex) {
                n = null;
            }
            // return node if column is negative
            if (col == -1)
                return n;
            if (col < 0)
                return null;
            Object obj;
            String codename = getColumnCodeName(col);
            if ( /* col ==0 */codename.equals("FarmName"))
                return n == null ? null : n.UnitName;
            if ( /* col ==0 */codename.equals("Hostname"))
                return n == null || n.client == null ? "???" : n.client.hostName;
            if ( /* col == 1 */codename.equals("LocalTime"))
                return n == null || n.client == null ? "???" : n.client.localTime;
            if ( /* col == 2 */codename.equals("MLVer"))
                return n == null || n.client == null ? "???" : n.client.mlVersion;
            if ( /* col == 2 */codename.equals("JavaVer"))
                return getJavaVersion(n);
            if ( /* col == 3 */codename.equals("Group"))
                return (n != null && n.client != null && n.client.mle != null) ? n.client.mle.Group : "???";
            if ( /* col == 4 */codename.equals("FreeNodesBar")) {
                /**
                 * compute value and percent and return them as a well formed string:
                 * ^[0-9]+ ([0-9]+%)$
                 */
                String sPBtext = "N/A";

                Gresult ld = (n == null || n.global_param == null ? null : (Gresult) n.global_param.get("Load5"));
                /*
                 * if (ld == null ) {
                 * ld = (n==null || n.global_param == null?null:(Gresult) n.global_param.get("Load1" ));
                 * if( ld!=null && ld.ClusterName.indexOf("PBS")==-1 && ld.ClusterName.indexOf("Condor")==-1 )
                 * ld=null;
                 * }
                 */
                if (ld != null) {
                    double fn = ld.hist[0];
                    double per = fn * 100 / ld.TotalNodes;
                    int nfn = (int) fn;
                    sPBtext = "" + nfn + " (" + (int) per + "%)";
                }
                return sPBtext;
            }
            if ( /* col == 5 */codename.equals("MLUptime"))
                return n == null || n.client == null ? "N/A" : n.client.uptime;
            if ( /* col == 6 */codename.equals("TotalParams"))
                return n == null || n.client == null ? "N/A" : "" + n.client.total_params;
            if (codename.startsWith("TabPan>")) {
                if (n == null || n.client == null || (obj = n.haux.get(codename)) == null)
                    return "N/A";
                String retS = obj.toString();
                // TODO: REMOVE THIS HACK!!!!!!! in a subsequent release
                // if ( codename.equals( "TabPan>MLCPUTime" ) ) {
                // double d=0;
                // try {
                // d = Double.parseDouble(retS);
                // } catch (Exception ex) {
                // d=0;
                // }
                // if ( d > 10 )
                // return "N/A";
                // }
                // TODO: END HACK
                int nPos;
                if ((nPos = retS.indexOf(".")) > 0)
                    retS = retS.substring(0, (nPos + 3 > retS.length() ? retS.length() : nPos + 3));
                return retS;
            }
            if (col < /* acolNames *//* vColumns.size() */getColumnCount()) {
                String key = getColumnName(col);/* acolNames.elementAt (col ) */// ((ColumnProperties)vColumns.get(col)).getName()
                                                                                // ;

                int f = -1;
                for (int k = 0; k < acceptg.length; k++) {
                    if (addColNames[k].equals(key)) {
                        f = k;
                        break;
                    }
                }

                if (f < 0)
                    return "N/A";

                Gresult gr = (n == null || n.global_param == null ? null : (Gresult) n.global_param.get(acceptg[f]));
                if (gr == null) {
                    return "Unknown";
                }
                // table.setRowHeight ( row,40);
                if (addColNames[f].indexOf("Rate") != -1) {
                    return "" + nf.format(gr.mean) + " / " + nf.format(gr.sum);
                } else if (addColNames[f].indexOf("Disk") != -1) {
                    return "" + nf.format(gr.sum) + " / " + nf.format(gr.max);
                } else {
                    return "" + nf.format(gr.mean);
                }
            }

            return null;
        }

        public Object getTotalValueAt(int col) {
            String defVal = "-";
            if (col < 0)
                return defVal;
            String codename = getColumnCodeName(col);
            int ncount = vnodes.size();
            if (ncount == 0)
                return defVal;
            if (codename.equals("FarmName"))
                return "TOTALS";
            if (codename.equals("Hostname"))
                return "" + ncount;
            if (codename.equals("LocalTime"))
                return defVal;
            if (codename.equals("MLVer"))
                return defVal;
            if (codename.equals("JavaVer"))
                return defVal;
            if (codename.equals("Group")) {
                // count groups
                try {
                    HashSet uniqueSet = new HashSet();
                    for (int i = 0; i < ncount; i++) {
                        rcNode n = vnodes.get(i);
                        String groups;
                        if (n != null && n.client != null && n.client.mle != null && (groups = n.client.mle.Group) != null) {
                            StringTokenizer stk = new StringTokenizer(groups, ",");
                            while (stk.hasMoreTokens()) {
                                String group = stk.nextToken();
                                uniqueSet.add(group);
                            }
                        }
                    }
                    return "" + uniqueSet.size();
                } catch (Exception ex) {
                    // error counting, should keep previous value
                    return null;
                }
            }
            if (codename.equals("FreeNodesBar")) {
                return defVal;
            }
            if (codename.equals("MLUptime")) {
                // return total up time for all services in table
                try {
                    int years, days, hours, mins, secs;
                    years = days = hours = mins = secs = 0;
                    boolean bValue = false;
                    for (int i = 0; i < ncount; i++) {
                        rcNode n = (rcNode) vnodes.get(i);
                        String uptime;
                        if (n != null && n.client != null && (uptime = n.client.uptime) != null) {
                            int hour = 0, min = 0, sec = 0, day = 0;
                            int nP1, nP1ant = uptime.length();
                            nP1 = uptime.lastIndexOf(':', nP1ant);
                            if (nP1 != -1) {
                                try {
                                    sec = (int) TableSorter.getNumber(uptime.substring(nP1 + 1));
                                } catch (NumberFormatException nfex) {
                                    sec = 0;
                                }
                                ;
                                nP1ant = nP1 - 1;
                                nP1 = uptime.lastIndexOf(':', nP1ant);
                                if (nP1 != -1) {
                                    try {
                                        min = (int) TableSorter.getNumber(uptime.substring(nP1 + 1, nP1ant + 1));// Integer.parseInt(s1.substring(nP1+1,
                                                                                                                 // nP1ant+1));
                                    } catch (NumberFormatException nfex) {
                                        min = 0;
                                    }
                                    ;
                                    nP1ant = nP1 - 1;
                                    nP1 = uptime.lastIndexOf(' ', nP1ant);
                                    try {
                                        hour = (int) TableSorter.getNumber(uptime.substring(nP1 + 1, nP1ant + 1));// Integer.parseInt(s1.substring(nP1+1,
                                                                                                                  // nP1ant+1));
                                        nP1 = uptime.indexOf(' ');
                                        if (nP1 != -1)
                                            day = (int) TableSorter.getNumber(uptime.substring(0, nP1));// Integer.parseInt(s1.substring(
                                                                                                        // 0, nP1));
                                    } catch (NumberFormatException nfex) {
                                    }
                                    ;
                                }
                                ;
                            }
                            ;
                            if (sec + min + hour + day > 0) {
                                // add current gathered days, hours, minutes, seconds
                                secs += sec;
                                mins += (secs / 60);
                                secs %= 60;
                                mins += min;
                                hours += (mins / 60);
                                mins %= 60;
                                hours += hour;
                                days += (hours / 24);
                                hours %= 24;
                                days += day;

                                years += (days / 365);
                                days %= 365;

                                bValue = true;
                            }
                        }// end if n!=null
                    }// end for
                    if (!bValue)
                        return defVal;
                    // compute mean value
                    days += years % ncount * 365;
                    years /= ncount;
                    hours += days % ncount * 24;
                    days /= ncount;
                    mins += hours % ncount * 60;
                    hours /= ncount;
                    secs += mins % ncount * 60;
                    mins /= ncount;
                    secs /= ncount;
                    return (years > 0 ? (years == 1 ? "one year " : years + " years ") : "") + days + " days " + hours + "h" + mins + "m" + secs + "s";
                } catch (Exception ex) {
                    // error counting, should keep previous value
                    return null;
                }
            }
            ;
            if (codename.equals("TotalParams")) {
                // count total of total params
                try {
                    int ttp = 0;
                    for (int i = 0; i < ncount; i++) {
                        rcNode n = vnodes.get(i);
                        if (n != null && n.client != null)
                            ttp += n.client.total_params;
                    }
                    return "" + ttp;
                } catch (Exception ex) {
                    // error counting, should keep previous value
                    return null;
                }
            }
            if (codename.startsWith("TabPan>")) {
                if (codename.equals("TabPan>CollectedValuesRate") || codename.equals("TabPan>embedded_store_size") || codename.equals("TabPan>MLCPUTime") || codename.equals("TabPan>Load5")) {
                    try {
                        Object obj;
                        double total = 0;
                        double val;
                        boolean bVal = false;
                        for (int i = 0; i < ncount; i++) {
                            rcNode n = vnodes.get(i);
                            if (n == null || n.client == null || (obj = n.haux.get(codename)) == null)
                                continue;
                            String retS = obj.toString();
                            try {
                                val = TableSorter.getNumber(retS);
                                bVal = true;
                            } catch (Exception ex) {
                                val = 0;
                            }
                            total += val;
                        }
                        if (codename.equals("TabPan>MLCPUTime") || codename.equals("TabPan>Load5")) {
                            total /= ncount;
                        }
                        total = ((double) ((int) (total * 100))) / 100.0;
                        if (bVal)
                            return "" + total;
                        return defVal;
                    } catch (Exception ex) {
                        // error counting, should keep previous value
                        return null;
                    }
                }
                return defVal;
            }
            if (col < getColumnCount()) {
                // String key = (String) getColumnName(col);/*acolNames.elementAt (col )*/
                // //((ColumnProperties)vColumns.get(col)).getName() ;
                //
                // int f =-1;
                // for ( int k=0; k < acceptg.length; k++ ) {
                // if ( addColNames[k].equals(key) ) {
                // f=k;
                // break;
                // }
                // }
                //
                // if ( f < 0 ) return "N/A" ;
                //
                // Gresult gr = (n==null || n.global_param == null?null:(Gresult) n.global_param.get (acceptg[f] ));
                // if ( gr == null ) {
                // return "Unknown";
                // }
                // // table.setRowHeight ( row,40);
                // if (addColNames[f].indexOf("Rate") != -1 ) {
                // return "" + nf.format(gr.mean) + " / "+nf.format(gr.sum);
                // } else if ( addColNames[f].indexOf("Disk") != -1 ) {
                // return "" + nf.format(gr.sum)+ " / "+nf.format(gr.max);
                // } else {
                // return "" + nf.format(gr.mean);
                // }
                return defVal;
            }

            return null;
        }

        public String getTotalsToolTip(int col) {
            String codename = getColumnCodeName(col);
            if (codename.equals("FarmName"))
                return "Service Name";
            if (codename.equals("Hostname"))
                return "Total number of services visible in Table";
            if (codename.equals("LocalTime"))
                return null;
            if (codename.equals("MLVer"))
                return null;
            if (codename.equals("Group"))
                return "Total number of groups visible in Table";
            if (codename.equals("FreeNodesBar"))
                return null;
            if (codename.equals("MLUptime"))
                return "Mean uptime for all visible services in Table";
            if (codename.equals("TotalParams"))
                return "Total number of parameters for sevices visible in Table";
            if (codename.equals("TabPan>CollectedValuesRate"))
                return "Total throughput rate for MonALISA system composed of services visible in Table";
            if (codename.equals("TabPan>embedded_store_size"))
                return "Total embedded database size for all services visible in Table";
            if (codename.equals("TabPan>MLCPUTime"))
                return "Mean value for MonALISA Service CPU time for all services visible in Table";
            if (codename.equals("TabPan>Load5"))
                return "Mean value for MonALISA Service Load 5 for all services visible in Table";
            return null;
        }

    }
}
