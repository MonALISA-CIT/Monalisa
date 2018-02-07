package lia.Monitor.modules;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonModuleInfo;
import lia.Monitor.monitor.Result;
import lia.util.Utils;
import lia.util.ntp.NTPDate;
import lia.util.process.ExternalProcesses;

/**
 * @author costing
 * @since Oct 29, 2010
 */
public class MemInfo extends AbstractSchJobMonitoring {

    /**
     * stop complaining :)
     */
    private static final long serialVersionUID = 1L;

    @Override
    public boolean isRepetitive() {
        return true;
    }

    @Override
    public String getTaskName() {
        return "MemInfo";
    }

    @Override
    public Object doProcess() throws Exception {
        final Result r = new Result(node.getFarmName(), node.getClusterName(), node.getName(), getTaskName());

        r.time = NTPDate.currentTimeMillis();

        double mem_actualfree = -1;
        double mem_buffers = -1;
        double mem_cached = -1;
        double mem_free = -1;
        double mem_used = -1;
        double total_mem = -1;
        double total_swap = -1;
        double swap_used = -1;
        double swap_free = -1;

        if (isLinuxOS()) {
            final BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"));

            String sLine;

            while ((sLine = br.readLine()) != null) {
                final StringTokenizer st = new StringTokenizer(sLine, ": \t");

                String s = st.nextToken();

                double value;

                try {
                    value = Double.parseDouble(st.nextToken()) / 1024; // in MB
                } catch (Exception e) {
                    continue;
                }

                if (s.equals("MemFree")) {
                    mem_free = value;
                } else if (s.equals("MemTotal")) {
                    total_mem = value;
                } else if (s.equals("SwapFree")) {
                    swap_free = value;
                } else if (s.equals("SwapTotal")) {
                    total_swap = value;
                } else if (s.equals("Buffers")) {
                    mem_buffers = value;
                } else if (s.equals("Cached")) {
                    mem_cached = value;
                }
            }

            br.close();

            mem_actualfree = (mem_free > 0 ? mem_free : 0) + (mem_buffers > 0 ? mem_buffers : 0)
                    + (mem_cached > 0 ? mem_cached : 0);

            mem_used = total_mem - mem_actualfree;

            swap_used = total_swap - swap_free;
        } else if (isSolarisOS()) {
            try {
                final String prtconf = ExternalProcesses.getCmdOutput("prtconf", true, 30L, TimeUnit.SECONDS);

                final String searchMemorySize = "Memory size:";

                int idx = prtconf.indexOf(searchMemorySize);

                if (idx >= 0) {
                    idx += searchMemorySize.length() + 1;

                    int idx2 = prtconf.indexOf(' ', idx + 1);

                    total_mem = Double.parseDouble(prtconf.substring(idx, idx2).trim());

                    switch (prtconf.charAt(idx2 + 1)) {
                    case 'T':
                        total_mem *= 1024;
                        break;

                    case 'G':
                        total_mem *= 1024 * 1024;
                        break;

                    case 'K':
                        total_mem /= 1024;
                        break;
                    }

                }
            } catch (Throwable t) {
                // ignore
            }

            try {
                final String vmstat = ExternalProcesses.getCmdOutput("vmstat", true, 30L, TimeUnit.SECONDS);
                final BufferedReader br = new BufferedReader(new StringReader(vmstat));

                String sLine = br.readLine();

                if ((sLine != null) && ((sLine = br.readLine()) != null) && ((sLine = br.readLine()) != null)) {
                    StringTokenizer st = new StringTokenizer(sLine);

                    for (int i = 0; i < 3; i++) {
                        st.nextToken();
                    }

                    mem_free = mem_actualfree = Double.parseDouble(st.nextToken()) / 1024;
                    mem_used = total_mem - mem_free;
                }

                br.close();
            } catch (Throwable t) {
                // ignore
            }

            try {
                final String swap = ExternalProcesses.getCmdOutput(Arrays.asList("swap", "-l"), true, 30L,
                        TimeUnit.SECONDS);

                final BufferedReader br = new BufferedReader(new StringReader(swap));

                String sLine = br.readLine();

                if ((sLine != null) && ((sLine = br.readLine()) != null)) {
                    final StringTokenizer st = new StringTokenizer(sLine);

                    for (int i = 0; i < 3; i++) {
                        st.nextToken();
                    }

                    total_swap = Double.parseDouble(st.nextToken()) / 2048; // 512-byte blocks
                    swap_free = Double.parseDouble(st.nextToken()) / 2048;

                    swap_used = total_swap - swap_free;
                }

                br.close();
            } catch (Throwable t) {
                // ignore
            }
        } else if (isMacOS()) {
            // don't know yet how
            return null;
        } else {
            // what else ?
            return null;
        }

        addConditional(r, "total_mem", total_mem);

        addConditional(r, "mem_free", mem_free);

        addConditional(r, "mem_actualfree", mem_actualfree);
        addConditional(r, "mem_buffers", mem_buffers);
        addConditional(r, "mem_cached", mem_cached);
        addConditional(r, "mem_used", mem_used);

        if ((total_mem > 0) && (mem_used >= 0)) {
            addConditional(r, "mem_usage", (mem_used * 100) / total_mem);
        }

        addConditional(r, "total_swap", total_swap);
        addConditional(r, "swap_used", swap_used);
        addConditional(r, "swap_free", swap_free);

        if ((total_swap > 0) && (swap_used >= 0)) {
            addConditional(r, "swap_usage", (swap_used * 100) / total_swap);
        }

        final Vector<Object> ret = new Vector<Object>();

        ret.add(r);

        return ret;
    }

    private static final void addConditional(final Result r, final String key, final double value) {
        if (value >= 0) {
            r.addSet(key, value);
        }
    }

    @Override
    protected MonModuleInfo initArgs(String args) {
        return null;
    }

    /**
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        final MemInfo mi = new MemInfo();
        mi.init(new MNode("localhost", null, null), "");

        Utils.dumpResults(mi.doProcess());
    }

}
