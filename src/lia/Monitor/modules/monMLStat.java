package lia.Monitor.modules;

import java.io.BufferedReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.DataCache.ProxyWorker;
import lia.Monitor.Farm.Conf.ConfVerifier;
import lia.Monitor.Store.Cache;
import lia.Monitor.Store.TransparentStoreFactory;
import lia.Monitor.Store.TransparentStoreFast;
import lia.Monitor.Store.Fast.DB;
import lia.Monitor.Store.Fast.TempMemWriterInterface;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.MonitoringModule;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.cmdExec;
import lia.util.StringFactory;
import lia.util.ntp.NTPDate;

public class monMLStat extends cmdExec implements MonitoringModule, AppConfigChangeListener {

    private static final long serialVersionUID = 6125086913979118969L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(monMLStat.class.getName());

    final static public String ModuleName = "monMLStat";

    private static String[] ResTypes = { "memstore_size", // 0
    "memstore_guideline", // 1
    "memstore_upperlimit", // 2
    "memstore_hours", // 3
    "store_status", // 4
    "store_type", // 5
    "store_driver", // 6
    "embedded_store_size", // 7
    "stringcache_hit_ratio", // 8
    "stringcache_ignore_ratio", // 9
    "stringcache_accesses", // 10
    "stringcache_size", // 11
    "cache_size", // 12
    "cache_timeout", // 13
    "ntpoffset", // 14
    "ntpstatus", // 15
    "conf_verif_wqsize",// 16
    "conf_verif_take_rate",// 17
    "conf_last_ser_size", //18
    "conf_last_ser_dt" //19
    };

    final static public String OsName = "linux";

    private TransparentStoreFast tsf = null;

    private TempMemWriterInterface tmw = null;

    private double lastStoreSize;

    private long lastStoreSizeTime;

    private long CALC_SIZE_DELAY = 20 * 60 * 1000; // 20 minutes

    private long lastRun = 0;
    private long lastConfTimeoutTakeCount = 0;

    public monMLStat() {
        super(ModuleName);

        info.ResTypes = ResTypes;
        info.name = ModuleName;
        isRepetitive = true;
        lastStoreSizeTime = 0;
        lastStoreSize = 0;

        try {
            tsf = (TransparentStoreFast) TransparentStoreFactory.getStore();
            tmw = tsf.getTempMemWriter();
        } catch (Exception e) {
            logger.log(Level.WARNING, "monMLStat cannot execute because the store cannot be used", e);
        }

        reloadConfig();
        AppConfig.addNotifier(this);
    }

    public String[] ResTypes() {
        return ResTypes;
    }

    public String getOsName() {
        return OsName;
    }

    public Object doProcess() throws Exception {
        if (tsf == null) return null;

        long now = NTPDate.currentTimeMillis();
        final Result res = new Result(Node.getFarmName(), Node.getClusterName(), Node.getName(), ModuleName, ResTypes);

        final long dt = now - lastRun;
        
        if (tmw != null) {
            res.param[0] = tmw.getSize();
            res.param[1] = tmw.getLimit();
            res.param[2] = tmw.getHardLimit();
            res.param[3] = (double) tmw.getTotalTime() / (double) (1000L * 60L * 60L);
        } else {
            res.param[0] = res.param[1] = res.param[2] = res.param[3] = -1d;
        }

        int status = 0;

        if (TransparentStoreFactory.isMemoryStoreOnly()) status = 1;

        if (tsf.rfl != null) {
            status += 2; // log to file

            if (!tsf.rfl.isActive()) // log to file deactivated, probably because of disk errors
            status += 4;
        }

        if (DB.getErrorCount() > 50) // the switch to memory was because of database exceptions
        status += 8;

        res.param[4] = status;

        String s = TransparentStoreFactory.getStoreType();

        if (s == null)
            res.param[5] = -1;
        else if (s.equals("memory-only")) // store-type
            res.param[5] = 0;
        else if (s.equals("external"))
            res.param[5] = 1;
        else if (s.equals("mckoi"))
            res.param[5] = 2;
        else if (s.equals("emysqldb"))
            res.param[5] = 3;
        else if (s.equals("epgsqldb"))
            res.param[5] = 4;
        else
            res.param[5] = 999;

        s = TransparentStoreFactory.getDriverString();
        if (s == null)
            res.param[6] = -1;
        else if (s.equals("com.mckoi.JDBCDriver"))
            res.param[6] = 2;
        else if (s.equals("com.mysql.jdbc.Driver"))
            res.param[6] = 3;
        else if (s.equals("org.postgresql.Driver"))
            res.param[6] = 4;
        else
            res.param[6] = 999;

        updatePersistentStoreSize((int) res.param[5], now);
        res.param[7] = lastStoreSize;
        res.param[8] = StringFactory.getHitRatio();
        res.param[9] = StringFactory.getIgnoreRatio();
        if(dt > 0) {
            res.param[10] = (StringFactory.getAccessCount()*1000D) / dt;
        } else {
            res.param[10] = 0;
        }
        
        StringFactory.resetHitCounters();

        res.param[11] = StringFactory.getCacheSize();

        res.param[12] = Cache.size();
        res.param[13] = Cache.getTimeout();

        res.param[14] = NTPDate.ntpOffset();
        res.param[15] = NTPDate.isSynchronized() ? 0 : 1;
        
        ConfVerifier confVerifier = ConfVerifier.getInstance();
        res.param[16] = confVerifier.getWaitQueueSize();
        final long confTimeoutTakeCount = confVerifier.getTakeCount();
        
        if(dt > 0) {
            res.param[17] = ((confTimeoutTakeCount - lastConfTimeoutTakeCount)*1000D)/dt;
        }

        res.param[18] = ProxyWorker.getLastConfSize();
        res.param[19] = ProxyWorker.getLastConfDelta();
        
        lastConfTimeoutTakeCount = confTimeoutTakeCount;
        
        res.time = now;
        lastRun = now;
        return res;
    }

    private void updatePersistentStoreSize(int sType, long now) {
        if (lastStoreSizeTime + CALC_SIZE_DELAY < now) {
            long sTime = System.currentTimeMillis();
            try {
                String Farm_home = AppConfig.getProperty("lia.Monitor.Farm.HOME", null);
                if (Farm_home == null || Farm_home.length() == 0) {
                    logger.log(Level.WARNING, "myMon Could not determine Farm_HOME");
                    lastStoreSize = 0;
                    return;
                }

                String cmd = "du -sm ";
                switch (sType) {
                    case 3: {
                        cmd += Farm_home + "/emysqldb";
                        break;
                    }

                    case 4: {
                        String sPG_PATH = AppConfig.getGlobalEnvProperty("PGSQL_PATH", null);
                        if (sPG_PATH == null) {
                            cmd += Farm_home + "/pgsql";
                        } else {
                            cmd += sPG_PATH + "/pgsql";
                        }
                        break;
                    }
                    default: {
                        lastStoreSize = 0;
                        return;
                    }
                }

                if (cmd != null) {
                    BufferedReader br = null;
                    br = procOutput(cmd);
                    String line = br.readLine();
                    lastStoreSize = Double.valueOf(line.split("\\s+")[0]).doubleValue();
                    br.close();
                } else {
                    lastStoreSize = 0;
                }

            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " [monMLStat] [HANDLED] Got exc trying to determine the size of the DB", t);
                }
                lastStoreSize = 0;
            } finally {
                lastStoreSizeTime = now;
                cleanup();
            }

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " embedded_store_size [ " + lastStoreSize + " ] ... it took: [ " + (System.currentTimeMillis() - sTime) + " ] ms");
            }

        }//if
    }

    public MonModuleInfo getInfo() {
        return info;
    }

    private void reloadConfig() {
        try {
            CALC_SIZE_DELAY = Long.valueOf(AppConfig.getProperty("lia.Monitor.modules.monMLStat.CALC_SIZE_DELAY", "20")).longValue() * 60 * 1000;
        } catch (Throwable t) {
            CALC_SIZE_DELAY = 20 * 60 * 1000;
        }

        if (CALC_SIZE_DELAY < 60 * 1000) {
            CALC_SIZE_DELAY = 60 * 1000;
        }

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, " CALC_SIZE_DELAY = " + CALC_SIZE_DELAY / (1000 * 60) + " min");
        }
    }

    public void notifyAppConfigChanged() {
        reloadConfig();
    }

    static public void main(String[] args) {
        monMLStat aa = new monMLStat();

        try {
            Object cb = aa.doProcess();

            System.out.println(cb);

            Thread.sleep(3000);
            Object bb = aa.doProcess();

            System.out.println(bb);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
