package lia.app;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Properties;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.util.MLProcess;

public class AppUtils {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(AppUtils.class.getName());

    public static final int APP_STATUS_STOPPED = 0, APP_STATUS_RUNNING = 1, APP_STATUS_UNKNOWN = 2;

    static String Control_HOME = null;
    static String MonaLisa_HOME = "../..";

    static {
        MonaLisa_HOME = AppConfig.getProperty("MonaLisa_HOME", MonaLisa_HOME);
        Control_HOME = MonaLisa_HOME + "/Control";
    }

    public static final String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    public static final String dec(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    public static final String getOutput(String s) {
        Vector v = new Vector();

        String sCurrent = "";
        boolean b = false;
        boolean start = true;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '"') {
                if (start) {
                    b = true;
                }
                if (!start && b) {
                    v.add(sCurrent);
                    sCurrent = "";
                    b = false;
                    start = true;
                }
                if (!start && !b) {
                    logger.log(Level.WARNING, " AppUtils: error parsing '" + s + "' at char " + i);
                }
            } else {
                if (((c == ' ') || (c == '\t') || (c == '\r') || (c == '\n')) && !b) {
                    if (sCurrent.length() > 0) {
                        v.add(sCurrent);
                    }
                    sCurrent = "";
                    b = false;
                    start = true;
                } else {
                    sCurrent += c;
                    start = false;
                }
            }
        }

        if (sCurrent.length() > 0) {
            if (b) {
                logger.log(Level.WARNING, " AppUtils: error parsing : " + s + " : string not closed");
            } else {
                v.add(sCurrent);
            }
        }

        //System.out.println("Parsed : "+v.toString());

        String vs[] = new String[v.size()];

        for (int i = 0; i < v.size(); i++) {
            vs[i] = (String) v.get(i);
        }

        return getOutput(vs);
    }

    public static final String getOutput(String vsCommand[]) {
        try {
            Process p = MLProcess.exec(vsCommand);
            InputStream is = p.getInputStream();

            try {
                OutputStream os = p.getOutputStream();
                os.close();
            } catch (Exception e) {
                logger.log(Level.WARNING, "getOutput : cannot close the output stream", e);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buff = new byte[1024];
            int count = 0;

            while ((count = is.read(buff)) > 0) {
                //System.err.println("-- getOutput: read some data
                // ("+count+")");
                baos.write(buff, 0, count);
            }

            p.waitFor();

            return baos.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static final String getFileContents(String sFileName) {
        try {
            FileReader fr = new FileReader(sFileName);

            StringBuilder sb = new StringBuilder();

            char[] buf = new char[1024];
            int count = 0;

            do {
                count = fr.read(buf);

                sb.append(buf, 0, count);
            } while (count == buf.length);

            fr.close();

            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static final Vector getLines(String s) {
        if (s == null) {
            return null;
        }

        Vector v = new Vector();

        BufferedReader br = new BufferedReader(new StringReader(s));
        String str = null;

        try {
            while ((str = br.readLine()) != null) {
                v.add(str);
            }
        } catch (Exception e) {
        }

        return v;
    }

    public static final void getConfig(Properties prop, String sFile) {
        try {
            prop.clear();

            prop.load(new FileInputStream(Control_HOME + "/conf/" + sFile));
        } catch (Exception e) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "AppUtils: Cannot read from : " + sFile, e);
            }
        }
    }

    public static final boolean saveFile(String sFile, Vector vsValues) {
        if (vsValues == null) {
            return false;
        }

        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < vsValues.size(); i++) {
                sb.append((String) vsValues.elementAt(i) + "\n");
            }

            return saveFile(sFile, sb.toString());
        } catch (Exception e) {
            return false;
        }
    }

    public static final boolean saveFile(String sFile, String sValues) {
        try {
            FileWriter fw = new FileWriter(sFile);

            fw.write(sValues, 0, sValues.length());

            fw.flush();
            fw.close();
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "AppUtils : cannot write to : " + sFile, e);
            return false;
        }
    }

    public static final boolean updateConfig(String sFile, String sValues) {
        return saveFile(Control_HOME + "/conf/" + sFile, sValues);
    }

}