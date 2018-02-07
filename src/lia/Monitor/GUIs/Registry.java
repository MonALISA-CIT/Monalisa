package lia.Monitor.GUIs;

import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A central registry for all the measurement units. Units can be asigned to
 * specific parameters/functions or to some regexp that match some set of 
 * parameters/functions. The exact matches have priority over the regexp ones.
 * NOTE: original version in ML2....
 * 
 * @author Costin Grigoras <Costin.Grigoras@cern.ch>
 * @version 1.0 02/16/2005
 */
public final class Registry {

    /** The exact match list (map param -> map ( module , Unit ) )*/
    private final HashMap hmExactParameters;
    /** The regexp match list (map paramregex -> map ( module, Unit ) ) */
    private final HashMap hmRegexpParameters;

    private static final class RegexpUnitEntry {

        Pattern p;
        Unit u;

        public RegexpUnitEntry(Pattern p, Unit u) {
            this.p = p;
            this.u = u;
        }
    }

    /**
     * Initialize the <code>Registry</code> with the global options. 
     */
    public Registry() {
        hmExactParameters = new HashMap();
        hmRegexpParameters = new HashMap();
    }

    /**
     * Register an exact parameter. The exact parameters have priority over all the regexp ones.
     *
     * @param sModule the name of the module to associate this <code>Unit</code> to.
     * @param sParam the name of the parameter/function to associate this <code>Unit</code> to.
     * @param unit the measurement unit definition
     */
    public synchronized void registerExactParameter(final String sModule, final String sParam, final Unit unit) {

        HashMap map = (HashMap) hmExactParameters.get(sParam);
        if (map == null) {
            map = new HashMap();
            hmExactParameters.put(sParam, map);
        }
        Unit uTemp = (Unit) map.get(sModule);
        if ((uTemp == null) || !uTemp.equals(unit)) {
            map.put(sModule, unit);
        }
    }

    /**
     * Register a regexp parameter. The exact parameters have priority over all the regexp ones.
     *
     * @param sModule the name of the module to associate this <code>Unit</code> to.
     * @param sParam the regexp that associates the given <code>Unit</code> to a group of parameters/functions.
     *        If a regexp cannot be compiled out of this string then an exact parameter is built instead.
     * @param unit the measurement unit definition
     */
    public synchronized void registerRegexpParameter(final String sModule, final String sParam, final Unit unit) {
        try {
            HashMap map = (HashMap) hmRegexpParameters.get(sParam);
            if (map == null) {
                map = new HashMap();
                hmRegexpParameters.put(sParam, map);
            }

            final RegexpUnitEntry rue = (RegexpUnitEntry) map.get(sModule);

            if ((rue == null) || !rue.u.equals(unit)) {
                final Pattern p = Pattern.compile(sParam);
                map.put(sModule, new RegexpUnitEntry(p, unit));
            }
        } catch (PatternSyntaxException pse) {
            System.err.println("Registry: pattern compilation exception for '" + sParam + "' : " + pse + " ("
                    + pse.getMessage() + ")");
            pse.printStackTrace();
            registerExactParameter(sModule, sParam, unit);
        }
    }

    /** Unregister this exact parameter association */
    public synchronized void unregisterExactParameter(final String sModule, final String sParam) {
        HashMap map = (HashMap) hmExactParameters.remove(sParam);
        if (map != null) {
            map.remove(sModule);
        }
    }

    /** Unregister this regexp parameter association */
    public synchronized void unregisterRegexpParameter(final String sModule, final String sParam) {
        try {
            HashMap map = (HashMap) hmRegexpParameters.remove(sParam);
            if (map != null) {
                map.remove(sModule);
            }
        } catch (PatternSyntaxException e) {
            unregisterExactParameter(sModule, sParam);
        }
    }

    /**
     * Get the measurement unit for this parameter/function. First an exact match is tried,
     * if no match is found then we try to match all the registered regexps. First one that
     * matches will be returned.
     * 
     * @param sParam the parameter/function you want to get the <code>Unit</code> for.
     * @return the measurement unit, or <code>null</code> if no match could be found.
     */
    public synchronized Unit getUnit(final String sParam, final String[] sModules) {
        if ((sParam == null) || (sModules == null) || (sModules.length == 0)) {
            return null;
        }
        HashMap map = (HashMap) hmExactParameters.get(sParam);
        if (map != null) {
            for (String sModule : sModules) {
                if (map.containsKey(sModule)) {
                    return (Unit) map.get(sModule);
                }
            }
        }

        for (Iterator it = hmRegexpParameters.keySet().iterator(); it.hasNext();) {
            map = (HashMap) hmRegexpParameters.get(it.next());
            for (String sModule : sModules) {
                if (map.containsKey(sModule)) {
                    RegexpUnitEntry rue = (RegexpUnitEntry) map.get(sModule);
                    if (rue.p.matcher(sParam).matches()) {
                        return rue.u;
                    }
                }
            }
        }

        map = (HashMap) hmExactParameters.get(sParam);
        if (map != null) {
            if (map.containsKey("*")) {
                return (Unit) map.get("*");
            }
        }

        for (Iterator it = hmRegexpParameters.keySet().iterator(); it.hasNext();) {
            map = (HashMap) hmRegexpParameters.get(it.next());
            if (map.containsKey("*")) {
                RegexpUnitEntry rue = (RegexpUnitEntry) map.get("*");
                if (rue.p.matcher(sParam).matches()) {
                    return rue.u;
                }
            }
        }

        return null;
    }

    /**
     * Here all known mappings parameter - unit should be registered....
     */
    public void init() {

        /////////////////////////////
        // Ganglia Modules ( Ramiro )
        /////////////////////////////

        //monIGangliaTCP
        registerRegexpParameter("monIGangliaTCP", "CPU_.+", new Unit(Unit.TYPE_PERCENT, 0l, 0l));
        registerRegexpParameter("monIGangliaTCP", "DISK_.+", new Unit(Unit.TYPE_BYTE, 0l, Unit.GIGA_BYTE));
        registerRegexpParameter("monIGangliaTCP", "MEM_.+", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        registerRegexpParameter("monIGangliaTCP", "TotalIO_Rate_.+", new Unit(Unit.TYPE_BIT, Unit.PER_SECOND,
                Unit.MEGA_BIT));

        //monMcastGanglia
        registerRegexpParameter("monMcastGanglia", "CPU_.+", new Unit(Unit.TYPE_PERCENT, 0l, 0l));
        registerRegexpParameter("monMcastGanglia", "DISK_.+", new Unit(Unit.TYPE_BYTE, 0l, Unit.GIGA_BYTE));
        registerRegexpParameter("monMcastGanglia", "MEM_.+", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        registerRegexpParameter("monMcastGanglia", "TotalIO_Rate_.+", new Unit(Unit.TYPE_BIT, Unit.PER_SECOND,
                Unit.MEGA_BIT));

        //monIGangliaFilteredTCP
        registerRegexpParameter("monIGangliaFilteredTCP", "CPU_.+", new Unit(Unit.TYPE_PERCENT, 0l, 0l));
        registerRegexpParameter("monIGangliaFilteredTCP", "DISK_.+", new Unit(Unit.TYPE_BYTE, 0l, Unit.GIGA_BYTE));
        registerRegexpParameter("monIGangliaFilteredTCP", "MEM_.+", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        registerRegexpParameter("monIGangliaFilteredTCP", "TotalIO_Rate_.+", new Unit(Unit.TYPE_BIT, Unit.PER_SECOND,
                Unit.MEGA_BIT));

        //////////////////////
        // END Ganglia Modules
        //////////////////////

        ///// OSG_VO Modules /////
        registerExactParameter("monOsgVoJobs", "CPUTime", new Unit(Unit.TYPE_TIME, 0l, Unit.PER_SECOND));
        registerExactParameter("monOsgVoJobs", "CPUTimeCondorHist", new Unit(Unit.TYPE_TIME, 0l, Unit.PER_SECOND));
        registerExactParameter("monOsgVoJobs", "RunTime", new Unit(Unit.TYPE_TIME, 0l, Unit.PER_MINUTE));
        registerExactParameter("monOsgVoJobs", "WallClockTime", new Unit(Unit.TYPE_TIME, 0l, Unit.PER_SECOND));
        registerExactParameter("monOsgVoJobs", "VIRT_MEM_free", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        registerExactParameter("monOsgVoJobs", "MEM_total", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        registerExactParameter("monOsgVoJobs", "Size", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        registerExactParameter("monOsgVoJobs", "DiskUsage", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));

        registerRegexpParameter("monOsgVoJobs", "ExecTime_.+", new Unit(Unit.TYPE_TIME, 0l, Unit.PER_MILLI));
        registerRegexpParameter("monOsgVoJobs", "TotalProcessingTime", new Unit(Unit.TYPE_TIME, 0l, Unit.PER_MILLI));

        registerRegexpParameter("monOsgVO_IO", "(ftpRateIn|ftpRateOut).*", new Unit(Unit.TYPE_BYTE, Unit.PER_SECOND,
                Unit.KILO_BYTE));
        registerRegexpParameter("monOsgVO_IO", "(ftpInput|ftpOutput).*", new Unit(Unit.TYPE_BYTE, 0l, Unit.KILO_BYTE));
        ///// End OSG_VO Modules /////

        //		registerExactParameter("monPathload", "AwBandwidth_Low", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        //		registerExactParameter("monPathload", "AwBandwidth_High", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        //		registerExactParameter("monPathload", "MegaBytesReceived", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        //		registerExactParameter("monPathload", "MeasurementDuration", new Unit(Unit.TYPE_TIME, 0l, Unit.PER_SECOND));

        ///// PN Modules /////
        registerExactParameter("monPN_PBS", "VIRT_MEM_free", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        registerExactParameter("monPN_PBS", "MEM_total", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        registerExactParameter("monPN_PBS", "Size", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));

        registerExactParameter("monPN_Condor", "VIRT_MEM_free", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        registerExactParameter("monPN_Condor", "VIRT_MEM_total", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        registerExactParameter("monPN_Condor", "MEM_total", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));

        registerExactParameter("monPN_LSF", "MEM_free", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        registerExactParameter("monPN_LSF", "MEM_total", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        registerExactParameter("monPN_LSF", "SWAP_free", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        registerExactParameter("monPN_LSF", "SWAP_total", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        ///// End PN Modules /////

        registerRegexpParameter("monProcIO", ".+_(IN|OUT)", new Unit(Unit.TYPE_BIT, Unit.PER_SECOND, Unit.MEGA_BIT));
        registerRegexpParameter("monProcLoad", "(Load5|Load10|Load15)", new Unit(Unit.TYPE_UNKNOWN, 0l, 0l));
        registerRegexpParameter("monProcStat", "CPU_.+", new Unit(Unit.TYPE_PERCENT, 0l, 0l));
        registerRegexpParameter("monProcStat", "(Page_.+|Swap_.+)", new Unit(Unit.TYPE_BYTE, Unit.PER_SECOND,
                Unit.MEGA_BYTE));
        registerRegexpParameter("monDiskIOStat", "(ReadMBps|WriteMBps)", new Unit(Unit.TYPE_BYTE, Unit.PER_SECOND,
                Unit.MEGA_BYTE));
        registerRegexpParameter("monDiskIOStat", "(IOUtil)", new Unit(Unit.TYPE_PERCENT, 0l, 0l));
        registerExactParameter("monMLStat", "embedded_store_size", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        registerRegexpParameter("snmp_Disk_v2", ".+_(FreeDsk|UsedDsk)", new Unit(Unit.TYPE_BYTE, 0l, Unit.GIGA_BYTE));
        registerRegexpParameter("snmp_Disk", "(FreeDsk|UsedDsk)", new Unit(Unit.TYPE_BYTE, 0l, Unit.GIGA_BYTE));
        registerRegexpParameter("monRRD", ".+_(IN|OUT)", new Unit(Unit.TYPE_BIT, Unit.PER_SECOND, Unit.MEGA_BIT));
        registerRegexpParameter("monVO_IO", "(ftpInput|ftpOutput).*", new Unit(Unit.TYPE_BYTE, 0l, Unit.KILO_BYTE));
        registerRegexpParameter("monVO_IO", "(ftpRateIn|ftpRateOut).*", new Unit(Unit.TYPE_BYTE, Unit.PER_SECOND,
                Unit.KILO_BYTE));
        registerRegexpParameter("monVOgsiftpIO", "(ftpInput|ftpOutput).*", new Unit(Unit.TYPE_BYTE, 0l, Unit.KILO_BYTE));
        registerRegexpParameter("monVOgsiftpIO", "(ftpRateIn|ftpRateOut).*", new Unit(Unit.TYPE_BYTE, Unit.PER_SECOND,
                Unit.KILO_BYTE));
        registerExactParameter("snmp_CatSwitch", "uptime", new Unit(Unit.TYPE_TIME, 0l, Unit.PER_SECOND));
        registerRegexpParameter("snmp_CatSwitch", ".+_(IN|OUT|SPEED)", new Unit(Unit.TYPE_BIT, Unit.PER_SECOND,
                Unit.MEGA_BIT));
        registerRegexpParameter("snmp_CatSwitch", ".+Temperature", new Unit(Unit.TYPE_TEMPERATURE, 0l, 0l));
        registerRegexpParameter("snmp_CPU_v2", "CPU_.+", new Unit(Unit.TYPE_PERCENT, 0l, 0l));
        registerRegexpParameter("snmp_CPU", "CPU_.+", new Unit(Unit.TYPE_PERCENT, 0l, 0l));
        registerRegexpParameter("snmp_IOpp", ".+_(IN|OUT)", new Unit(Unit.TYPE_BIT, Unit.PER_SECOND, Unit.MEGA_BIT));
        registerRegexpParameter("snmp_IOpp_v2", ".+_(IN|OUT|SPEED)", new Unit(Unit.TYPE_BIT, Unit.PER_SECOND,
                Unit.MEGA_BIT));
        registerRegexpParameter("snmp_Load", "(Load5|Load10|Load15)", new Unit(Unit.TYPE_UNKNOWN, 0l, 0l));

        // ApMon background monitoring
        registerRegexpParameter("monXDRUDP", "cpu_time|run_time", new Unit(Unit.TYPE_TIME, 0l, Unit.PER_SECOND));
        registerRegexpParameter("monXDRUDP", ".+_usage|cpu_usr|cpu_sys|cpu_nice|cpu_idle", new Unit(Unit.TYPE_PERCENT,
                0l, 0l));
        registerRegexpParameter("monXDRUDP", "virtualmem|rss", new Unit(Unit.TYPE_BYTE, 0l, Unit.KILO_BYTE));
        registerRegexpParameter("monXDRUDP", "workdir_size|disk_total|disk_used|disk_free", new Unit(Unit.TYPE_BYTE,
                0l, Unit.MEGA_BYTE));
        registerRegexpParameter("monXDRUDP", "load5|load10|load15", new Unit(Unit.TYPE_UNKNOWN, 0l, 0l));
        registerRegexpParameter("monXDRUDP", "total_mem|mem_used|mem_free|total_swap|swap_used|swap_free", new Unit(
                Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        registerRegexpParameter("monXDRUDP", "eth._in|eth._out", new Unit(Unit.TYPE_BYTE, Unit.PER_SECOND,
                Unit.KILO_BYTE));
        registerRegexpParameter("monXDRUDP", "uptime", new Unit(Unit.TYPE_TIME, 0l, Unit.PER_DAY));

        //from MLSensor
        registerRegexpParameter("monXDRUDP", ".+_(ReadMBps|WriteMBps)", new Unit(Unit.TYPE_BYTE, Unit.PER_SECOND,
                Unit.MEGA_BYTE));
        registerRegexpParameter("monXDRUDP", ".+_(IOUtil)", new Unit(Unit.TYPE_PERCENT, 0l, 0l));
        registerRegexpParameter("monXDRUDP", "(Page_.+|Swap_.+)", new Unit(Unit.TYPE_BYTE, Unit.PER_SECOND,
                Unit.MEGA_BYTE));
        registerRegexpParameter("monXDRUDP", "CPU_.+", new Unit(Unit.TYPE_PERCENT, 0l, 0l));
        registerRegexpParameter("monXDRUDP", ".+_(IN|OUT)", new Unit(Unit.TYPE_BIT, Unit.PER_SECOND, Unit.MEGA_BIT));
        registerRegexpParameter("monXDRUDP", ".+_(COLLS|ERRS)", new Unit(Unit.TYPE_SCALAR, 0L, 0L));

        //Ciena
        registerRegexpParameter("monCienaEthIO", ".+_MLRate", new Unit(Unit.TYPE_BIT, Unit.PER_SECOND, Unit.MEGA_BIT));
        registerRegexpParameter("monCienaEthIO", ".+_(RATE)", new Unit(Unit.TYPE_BIT, Unit.PER_SECOND, Unit.MEGA_BIT));
        registerRegexpParameter("monCienaEflow", "Rate", new Unit(Unit.TYPE_BIT, Unit.PER_SECOND, Unit.MEGA_BIT));
        registerRegexpParameter("monCienaEflow", ".+_IN|.+_OUT",
                new Unit(Unit.TYPE_BIT, Unit.PER_SECOND, Unit.MEGA_BIT));

        // LISAv2
        registerRegexpParameter("*", "Cpu(Usage|Usr|Sys|Nice|Idle|IoWait|Int|SoftInt|Steal)", new Unit(
                Unit.TYPE_PERCENT, 0l, 0l));
        registerExactParameter("*", "MemUsage", new Unit(Unit.TYPE_PERCENT, 0l, 0l));
        registerExactParameter("*", "DiskUsage", new Unit(Unit.TYPE_PERCENT, 0l, 0l));
        registerRegexpParameter("*", "Mem(Used|Free)", new Unit(Unit.TYPE_BYTE, 0l, Unit.MEGA_BYTE));
        registerRegexpParameter("*", "Disk(Used|Free|Total)", new Unit(Unit.TYPE_BYTE, 0l, Unit.GIGA_BYTE));
        registerExactParameter("*", "DiskIO", new Unit(Unit.TYPE_BYTE, Unit.PER_SECOND, Unit.KILO_BYTE));
        registerRegexpParameter("*", "(In|Out)_.*", new Unit(Unit.TYPE_BIT, Unit.PER_SECOND, Unit.MEGA_BIT));

        // FDT via LISA
        registerRegexpParameter("monXDRUDP", "DISK_READ|DISK_WRITE", new Unit(Unit.TYPE_BYTE, Unit.PER_SECOND,
                Unit.MEGA_BYTE));
        registerRegexpParameter("monXDRUDP", "NET_IN|NET_OUT", new Unit(Unit.TYPE_BIT, Unit.PER_SECOND, Unit.MEGA_BIT));

        //OLIMPS/Floodlight monitor
        registerRegexpParameter("OlimpsFLFilter", ".+_(in|out)",
                new Unit(Unit.TYPE_BIT, Unit.PER_SECOND, Unit.MEGA_BIT));

    }
} // end of class Registry

