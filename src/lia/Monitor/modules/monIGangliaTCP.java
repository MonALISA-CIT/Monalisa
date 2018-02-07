package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.util.ntp.NTPDate;

/**
 * Interface to Ganglia using gmon
 */

public class monIGangliaTCP extends cmdExec implements MonitoringModule {

    /**
     * 
     */
    private static final long serialVersionUID = -7876870552558441292L;

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(monIGangliaTCP.class.getName());

    /**
     * The name of the monitoring parameters to be "extracted" from the Ganglia
     * report
     */

    static String[] metric = { "cpu_num", "cpu_user", "cpu_system", "cpu_nice", "bytes_in", "bytes_out", "load_five",
            "load_one", "load_fifteen", "proc_run", "mem_free", "mem_shared", "mem_cached", "mem_buffers", "mem_total",
            "disk_free", "disk_total" };

    static public String ModuleName = "monIGangliaTCP";
    /**
     * Rename them into :
     */

    static String[] tmetric = { "NoCPUs", "CPU_usr", "CPU_sys", "CPU_nice", "TotalIO_Rate_IN", "TotalIO_Rate_OUT",
            "Load5", "Load1", "Load15", "proc_run", "MEM_free", "MEM_shared", "MEM_cached", "MEM_buffers", "MEM_total",
            "DISK_free", "DISK_total" };

    String cmd;
    TcpCmd tc = null;

    int port = 8649;
    ResultTimeWatcher rtw = null;
    String host = "127.0.0.1";
    private static boolean shouldOverwriteTime = false;
    private boolean shouldLogWrongHostsTime = true;
    private String wrongHostsTimeLogFile = null;

    private long TIME_FRAME = 5 * 60 * 1000;//5 min
    private long LOG_DELAY = 60 * 60 * 1000;//1 hour
    private int socketTimeout = 20 * 1000;//20s

    static {
        try {
            shouldOverwriteTime = Boolean.valueOf(
                    AppConfig.getProperty("lia.Monitor.modules.monIGangliaTCP.shouldOverwriteTime", "false"))
                    .booleanValue();
        } catch (Throwable t) {
            logger.log(Level.WARNING, " Got exc parsing lia.Monitor.modules.monIGangliaTCP.shouldOverwriteTime", t);
            shouldOverwriteTime = false;
        }
    }

    public monIGangliaTCP() {
        super("monIGangliaTCP");
        info.ResTypes = tmetric;
        isRepetitive = true;
        canSuspend = false;
    }

    @Override
    public MonModuleInfo init(MNode Node, String arg1) {
        this.Node = Node;
        info.ResTypes = tmetric;
        String sport = "8649"; // default Ganglia port

        if (arg1 != null) {
            sport = arg1;
        }

        boolean portSetted = false;
        try {
            if (arg1 != null) {
                String[] args = arg1.split("((\\s)*;(\\s)*)|(\\s)*,(\\s)*");
                if ((args != null) && (args.length > 0)) {
                    for (String arg : args) {
                        if (arg.indexOf("gPort") != -1) {
                            try {
                                port = Integer.parseInt(arg.split("(\\s)*=(\\s)*")[1]);
                                portSetted = true;
                            } catch (Throwable t) {
                                port = 8649;
                            }
                        } else if (arg.indexOf("shouldOverwriteTime") != -1) {
                            try {
                                shouldOverwriteTime = Boolean.valueOf(arg.split("(\\s)*=(\\s)*")[1]).booleanValue();
                            } catch (Throwable t) {
                                shouldOverwriteTime = false;
                            }
                        } else if (arg.indexOf("socketTimeout") != -1) {
                            try {
                                socketTimeout = Integer.valueOf(arg.split("(\\s)*=(\\s)*")[1]).intValue();
                            } catch (Throwable t) {
                                socketTimeout = 20 * 1000;
                            }
                        } else if (arg.indexOf("CanSuspend") != -1) {
                            try {
                                canSuspend = Boolean.valueOf(arg.split("(\\s)*=(\\s)*")[1]).booleanValue();
                            } catch (Throwable t) {
                                canSuspend = false;
                            }
                        } else if (arg.indexOf("shouldLogWrongHostsTime") != -1) {
                            try {
                                shouldLogWrongHostsTime = Boolean.valueOf(arg.split("(\\s)*=(\\s)*")[1]).booleanValue();
                            } catch (Throwable t) {
                                shouldLogWrongHostsTime = true;
                            }
                        } else if (arg.indexOf("TIME_FRAME") != -1) {
                            try {
                                TIME_FRAME = Long.valueOf(arg.split("(\\s)*=(\\s)*")[1]).longValue() * 1000;
                            } catch (Throwable t) {
                                TIME_FRAME = 5 * 60 * 1000;///5 min
                            }
                        } else if (arg.indexOf("LOG_DELAY") != -1) {
                            try {
                                LOG_DELAY = Long.valueOf(arg.split("(\\s)*=(\\s)*")[1]).longValue() * 1000;
                            } catch (Throwable t) {
                                LOG_DELAY = 60 * 60 * 1000;//every hour
                            }
                        } else if (arg.indexOf("WrongHostsTimeLogFile") != -1) {
                            try {
                                wrongHostsTimeLogFile = arg.split("(\\s)*=(\\s)*")[1];
                            } catch (Throwable t) {
                                wrongHostsTimeLogFile = null;
                            }

                            if (wrongHostsTimeLogFile != null) {
                                wrongHostsTimeLogFile = wrongHostsTimeLogFile.trim();
                                if (wrongHostsTimeLogFile.length() == 0) {
                                    wrongHostsTimeLogFile = null;
                                }
                            }

                        }//if(WrongHostsTimeLogFile)
                    }//for(args)
                }//if
            }
        } catch (Exception e) {
            port = 8649;
        }
        if (!portSetted) {
            try {
                port = Integer.parseInt(sport);
            } catch (Throwable t) {
                port = 8649;
            }
        }
        host = Node.getIPaddress();
        cmd = "telnet " + Node.getIPaddress() + " " + port;

        logger.log(Level.INFO, "monIGangliaTCP ... shouldOverwriteTime  = " + shouldOverwriteTime);

        if (shouldLogWrongHostsTime) {
            logger.log(Level.INFO, "monIGangliaTCP ... shouldLogWrongHostsTime  = " + shouldLogWrongHostsTime);
            if (wrongHostsTimeLogFile == null) {
                if (wrongHostsTimeLogFile == null) {
                    String FarmHOME = AppConfig.getProperty("lia.Monitor.Farm.HOME", null);
                    if (FarmHOME != null) {
                        wrongHostsTimeLogFile = FarmHOME + "/WrongHostsTime.log";
                    }
                }
                if (wrongHostsTimeLogFile == null) {
                    logger.log(Level.WARNING, "monIGangliaTCP ... wrongHostsTimeLogFile == null ... will not log");
                } else {
                    logger.log(Level.WARNING, "monIGangliaTCP ... wrongHostsTimeLogFile = " + wrongHostsTimeLogFile);
                    logger.log(Level.WARNING, "monIGangliaTCP ... TIME_FRAME = " + TIME_FRAME + " LOG_DELAY = "
                            + LOG_DELAY);
                    rtw = new ResultTimeWatcher(wrongHostsTimeLogFile, TIME_FRAME, LOG_DELAY);
                    rtw.start();
                }
            }
        }
        info.name = ModuleName;
        return info;
    }

    @Override
    public Object doProcess() throws Exception {

        //   BufferedReader buff1 = procOutput ( cmd );
        //   BufferedReader buff1 = TcpCmd.TcpCmd ( host, port, "" );
        tc = new TcpCmd(host, port, "");
        BufferedReader buff1 = tc.execute(socketTimeout);

        if (buff1 == null) {
            tc.cleanup();
            tc = null;
            if (pro != null) {
                pro.destroy();
                pro = null;
            }
            throw new Exception(" Ganglia output is null for " + Node.name);
        }

        return Parse(buff1);

    }

    public Vector Parse(BufferedReader buff) throws Exception {
        int i1, i2;
        Result rr = null;
        Vector results = new Vector();
        long sTime = System.currentTimeMillis();

        try {
            for (;;) {
            	String lin = null;
                try {
                    lin = buff.readLine();
                    if (lin == null) {
                        break;
                    }
                    if (lin.indexOf("<HOST") != -1) {
                        i1 = lin.indexOf("=");
                        i2 = lin.indexOf("\"", i1 + 2);

                        rr = new Result();
                        rr.NodeName = lin.substring(i1 + 2, i2);
                        rr.ClusterName = Node.getClusterName();
                        rr.FarmName = Node.getFarmName();
                        rr.Module = ModuleName;
                        i1 = lin.indexOf("ED=");
                        i2 = lin.indexOf("\"", i1 + 4);
                        long time = (Long.valueOf(lin.substring(i1 + 4, i2))).longValue();
                        rr.time = time * 1000;

                        if (rtw != null) {
                            rtw.add(rr);
                        }

                        if (shouldOverwriteTime) {
                            rr.time = NTPDate.currentTimeMillis();
                        }

                    } else {
                        if (lin.indexOf("/HOST>") != -1) {
                            //the output from gmond can be broken...
                            //got that in CMS-PIC 
                            if ((rr != null) && (rr.param != null) && (rr.param_name != null) && (rr.param.length > 0)
                                    && (rr.param_name.length > 0) && (rr.param.length == rr.param_name.length)) {
                                results.add(rr);
                            }
                        } else 
                        if (lin.indexOf("<EXTRA_ELEMENT NAME=\"TITLE\" VAL=\"")<0){
                            for (int l = 0; l < metric.length; l++) {

                                if (lin.indexOf(metric[l]) != -1) {
                                    i1 = lin.indexOf("VAL=");
                                    i2 = lin.indexOf("\"", i1 + 5);
                                    String sval = lin.substring(i1 + 5, i2);
                                    double val = (Double.valueOf(lin.substring(i1 + 5, i2))).doubleValue();
                                    //               trasform IO measurments in mb/s !
                                    if (metric[l].indexOf("bytes") != -1) {
                                        val = (val * 8) / 1000000.0;
                                    }
                                    //               converet memory units from KB in MB
                                    if (metric[l].indexOf("mem") != -1) {
                                        val = val / 1000.0;
                                    }

                                    rr.addSet(tmetric[l], val);
                                }
                            }
                        }
                    }
                } catch (IOException ioe) {
                    logger.log(Level.WARNING, "Got I/O Exception parsing output: "+lin, ioe);
                    throw ioe;
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "Got Exception parsing output: "+lin, t);
                }
            }
        } catch (Throwable t) {
            throw new Exception(t);
        } finally {
            try {
                if (pro != null) {
                    pro.destroy();
                    pro = null;
                }
            } catch (Throwable ignoreException) {
            }
            try {
                if (tc != null) {
                    tc.cleanup();
                    tc = null;
                }
            } catch (Throwable ignoreException) {
            }
        }

        if (logger.isLoggable(Level.FINER)) {
            StringBuilder sb = new StringBuilder();
            if ((results != null) && (results.size() > 0)) {
                sb.append(" monIGangliaTCP:- Returning [").append(results.size()).append("] results\n");
                if (logger.isLoggable(Level.FINEST)) {
                    for (int i = 0; i < results.size(); i++) {
                        Object o = results.elementAt(i);
                        sb.append(" [").append(i).append("] =>");
                        if (o == null) {
                            sb.append(" null \n");
                        } else {
                            sb.append(o.toString()).append("\n");
                        }
                    }
                }
            } else {
                sb.append("monIGangliaTCP returning no Results back to ML\n");
            }
            logger.log(Level.FINER, "\n\n ==== Ganglia Results ====\n\n" + sb.toString()
                    + "\n\n ==== END Ganglia Results " + " [ " + (System.currentTimeMillis() - sTime) + " ]====\n\n");
        }
        return results;
    }

    @Override
    public MonModuleInfo getInfo() {
        return info;
    }

    @Override
    public String[] ResTypes() {
        return tmetric;
    }

    @Override
    public String getOsName() {
        return "linux";
    }

    static public void main(String[] args) {

        String host = "localhost";
        String ad = null;
        try {
            ad = InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            System.out.println(" Can not get ip for node " + e);
            System.exit(-1);
        }

        monIGangliaTCP aa = new monIGangliaTCP();
        MonModuleInfo info = aa.init(new MNode(host, ad, null, null), null);

        try {
            Object bb = aa.doProcess();

            if (bb instanceof Vector) {
                System.out.println(" Received a Vector having " + ((Vector) bb).size() + " results");
            }
        } catch (Exception e) {
            System.out.println(" failed to process ");
        }

    }

}
