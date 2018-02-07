package lia.app.proc;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;

import lia.app.AppUtils;
import lia.util.Utils;

public class AppProc implements lia.app.AppInt {

    String sFile = null;

    Properties prop = new Properties();

    public static final String sConfigOptions = "########### Required parameters : #####\n" + "#proc=/path/to/proc  (default is /proc)\n"
            + "#######################################\n\n";

    volatile String sProc = "/proc";

    public boolean start() {
        return true;
    }

    public boolean stop() {
        return true;
    }

    public boolean restart() {
        return true;
    }

    public int status() {
        File f = new File(sProc + "/uptime");

        if (f.exists() && f.canRead()) {
            return AppUtils.APP_STATUS_RUNNING;
        }
        return AppUtils.APP_STATUS_UNKNOWN;
    }

    String vsDirs[] = {
            "acpi", "asound", "bus", "driver", "ide", "net", "scsi", "sys", // proc folders

            "apm", "cmdline", "cpuinfo", "devices", "dma",
            "filesystems", // /proc files
            "interrupts", "iomem", "ioports", "loadavg", "meminfo", "modules", "mounts", "mtrr", "partitions", "pci", "stat", "swaps", "uptime",
            "version"
    };

    public String info() {
        // xml with the version & stuff
        StringBuilder sb = new StringBuilder();
        sb.append("<config app=\"Proc\">\n");
        sb.append("<file name=\"proc\">\n");

        int line = 0;
        for (int i = 0; i < vsDirs.length; i++) {
            line = buildXML(sb, new File(sProc + "/" + vsDirs[i]), line);
        }

        sb.append("</file>\n");
        sb.append("</config>\n");

        return sb.toString();
    }

    private int buildXML(StringBuilder sb, File fPath, int line) {
        if (!fPath.exists())
            return line;

        try {
            if (fPath.isDirectory()) {
                File[] vsFiles = fPath.listFiles();

                for (int i = 0; i < vsFiles.length; i++) {
                    line++;
                    File f = vsFiles[i];

                    if (f.isDirectory()) {
                        sb.append("<section name=\"" + AppUtils.enc(f.getName()) + "\" value=\"\" line=\"" + line + "\" read=\""
                                + (f.canRead() ? "true" : "false") + "\" write=\"" + (f.canWrite() ? "true" : "false") + "\">\n");
                        line = buildXML(sb, f, line);
                        sb.append("</section>\n");
                    }

                    if (f.isFile()) {
                        String sValue = getFileContents(f);
                        if (sValue != null)
                            sValue = AppUtils.enc(sValue);

                        sb.append("<key name=\"" + AppUtils.enc(f.getName()) + "\" value=\"" + (sValue == null ? "" : sValue) + "\" line=\"" + line
                                + "\" read=\"" + ((f.canRead() && sValue != null) ? "true" : "false") + "\" write=\""
                                + (f.canWrite() ? "true" : "false") + "\"/>\n");
                    }
                }
            } else {
                line++;
                String sValue = getFileContents(fPath);
                if (sValue != null)
                    sValue = AppUtils.enc(sValue);

                sb.append("<key name=\"" + AppUtils.enc(fPath.getName()) + "\" value=\"" + (sValue == null ? "" : sValue) + "\" line=\"" + line
                        + "\" read=\"" + ((fPath.canRead() && sValue != null) ? "true" : "false") + "\" write=\""
                        + (fPath.canWrite() ? "true" : "false") + "\"/>\n");
            }
        } catch (Exception e) {
            // ignore
        }

        return line;
    }

    private String getFileContents(File f) {
        StringBuilder sb = new StringBuilder();

        FileReader fr = null;

        try {
            fr = new FileReader(f);

            char vc[] = new char[10240];
            int r = 0;

            do {
                r = fr.read(vc, 0, vc.length);
                sb.append(vc, 0, r);
            } while (r == vc.length);
        } catch (Exception e) {
            return null;
        } finally {
            Utils.closeIgnoringException(fr);
        }

        return sb.toString();
    }

    /**
     * @param sCmd  
     */
    public String exec(String sCmd) {
        return "exec has no meaning for the Proc module";
    }

    public boolean update(String sUpdate) {
        return update(new String[] {
            sUpdate
        });
    }

    public boolean update(String vs[]) {
        try {
            for (int o = 0; o < vs.length; o++) {
                String sUpdate = vs[o];

                StringTokenizer st = new StringTokenizer(sUpdate, " ");

                String sFile = AppUtils.dec(st.nextToken());
                int iLine = Integer.parseInt(st.nextToken());
                String sPrev = st.nextToken();
                String sCommand = st.nextToken();
                String sParams = null;

                sPrev = AppUtils.dec(sPrev);

                if (!sFile.equals("proc")) {
                    System.out.println("proc : the file is not 'proc'");
                    return false;
                }

                if (sCommand.equals("rename") || sCommand.equals("insert") || sCommand.equals("delete") || sCommand.equals("insertsection")) {
                    System.out.println("proc : command ingnored");
                    return true; // this commands have no sense for /proc file system module
                }

                if (!sCommand.equals("update")) {
                    System.out.println("proc : command is not 'update'");
                    return false; // the only valid command
                }

                while (st.hasMoreTokens()) {
                    sParams = (sParams == null ? "" : sParams + " ") + AppUtils.dec(st.nextToken());
                }

                int line = 0;
                for (int j = 0; j < vsDirs.length; j++) {
                    line = updateConfig(new File(sProc + "/" + vsDirs[j]), line, iLine, sPrev, sCommand, sParams);
                }
            }
            return true;
        } catch (Exception e) {
            System.out.println("proc : exception : " + e + " (" + e.getMessage() + ")");
            e.printStackTrace();
            return false;
        }
    }

    private int updateConfig(File fPath, int line, int iLine, String sPrev, String sCommand, String sParams) {
        if (!fPath.exists())
            return line;

        if (line > iLine)
            return line;

        try {
            if (fPath.isDirectory()) {
                File[] vsFiles = fPath.listFiles();

                for (int i = 0; i < vsFiles.length; i++) {
                    line++;

                    if (line > iLine)
                        return line;

                    File f = vsFiles[i];

                    if (f.isDirectory()) {
                        line = updateConfig(f, line, iLine, sPrev, sCommand, sParams);
                    }

                    if (f.isFile()) {
                        if (line == iLine && f.getName().equals(sPrev)) {
                            FileWriter fw = new FileWriter(f);
                            fw.write(sParams.toCharArray());
                            fw.flush();
                            fw.close();
                        }
                    }
                }
            } else {
                line++;
                if (line == iLine && fPath.getName().equals(sPrev)) {
                    FileWriter fw = new FileWriter(fPath);
                    fw.write(sParams.toCharArray());
                    fw.flush();
                    fw.close();
                }
            }
        } catch (Exception e) {
            System.out.println("exception while updating : " + e + " (" + e.getMessage() + ")");
            e.printStackTrace();
        }

        return line;
    }

    public String getConfiguration() {
        StringBuilder sb = new StringBuilder();

        sb.append(sConfigOptions);

        Enumeration<?> e = prop.propertyNames();

        while (e.hasMoreElements()) {
            String s = (String) e.nextElement();

            sb.append(s + "=" + prop.getProperty(s) + "\n");
        }

        return sb.toString();
    }

    public boolean updateConfiguration(String s) {
        return AppUtils.updateConfig(sFile, s) && init(sFile);
    }

    public boolean init(String sPropFile) {
        sFile = sPropFile;
        AppUtils.getConfig(prop, sFile);

        if (prop.getProperty("proc") != null && prop.getProperty("proc").length() > 0) {
            sProc = prop.getProperty("proc");
        }
        return true;
    }

    public String getName() {
        return "lia.app.proc.AppProc";
    }

    public String getConfigFile() {
        return sFile;
    }

}
