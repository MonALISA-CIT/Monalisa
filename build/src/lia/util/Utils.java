/*
 * $Id: Utils.java 7444 2013-12-21 23:29:53Z ramiro $
 */
package lia.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.ntp.NTPDate;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceItem;

/**
 * There are a lot of "utility" methods in all the ML distro ... They should be
 * here
 * 
 * @author various "artists" aka ML developers
 */
public final class Utils {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(Utils.class.getName());

    /**
     * A list separated by , or ;
     */
    private static final Pattern LIST_SPLIT_PATTERN = Pattern.compile("((\\s)*,(\\s)*)|((\\s)*;(\\s)*)");

    private static final long NANO_REFERENCE = System.nanoTime();

    /**
     * Helper function to get the entire stackTrace() for a Throwable in a
     * String
     * 
     * @param t
     * @return - The same nice(r) output :) as t.printStackTrace()
     */
    public static final String getStackTrace(Throwable t) {
        if (t == null) {
            return "Null Stacktrace??";
        }

        final StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    public static final long nanoNow() {
        return System.nanoTime() - NANO_REFERENCE;
    }

    public static final void closeIgnoringException(final Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Throwable ignore) {
                // ignore
            }
        }
    }

    public static final void closeIgnoringException(final Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (Throwable ignore) {
                // ignore
            }
        }
    }

    public static final void cancelFutureIgnoreException(Future<?> future, boolean mayInterruptIfRunning) {
        if (future != null) {
            try {
                future.cancel(mayInterruptIfRunning);
            } catch (Throwable ignore) {
                // ignore
            }
        }

    }

    public static final String formatDuration(final long duration, TimeUnit unit) {
        return formatDuration(duration, unit, false);
    }

    /**
     * @param duration
     * @param unit
     * @param fractionsOfSecond
     * @return the time span formatted
     */
    public static final String formatDuration(final long duration, TimeUnit unit, boolean fractionsOfSecond) {
        long nanos = TimeUnit.NANOSECONDS.convert(duration, unit);

        if (nanos <= 0L) {
            return "0.0 seconds";
        }

        final StringBuilder sb = new StringBuilder(128);
        final long days = TimeUnit.NANOSECONDS.toDays(nanos);
        boolean bAppend = false;

        if (days > 0) {
            bAppend = true;
            if (days == 1L) {
                sb.append("1 day");
            } else {
                sb.append(days).append(" days");
            }
            nanos -= TimeUnit.DAYS.toNanos(days);
        }

        if (nanos <= 0) {
            return (bAppend) ? sb.toString() : "0.0 seconds";
        }

        final long hours = TimeUnit.NANOSECONDS.toHours(nanos);
        if (hours > 0) {
            if (bAppend) {
                sb.append(" ");
            }
            if (hours == 1L) {
                sb.append("1 hour");
            } else {
                sb.append(hours).append(" hours");
            }
            bAppend = true;
            nanos -= TimeUnit.HOURS.toNanos(hours);
        } else {
            if (bAppend) {
                sb.append(" 0 hours");
            }
        }

        if (nanos <= 0) {
            return (bAppend) ? sb.toString() : "0.0 seconds";
        }

        final long minutes = TimeUnit.NANOSECONDS.toMinutes(nanos);
        if (minutes > 0) {
            if (bAppend) {
                sb.append(" ");
            }
            bAppend = true;
            if (minutes == 1L) {
                sb.append("1 minute");
            } else {
                sb.append(minutes).append(" minutes");
            }
            nanos -= TimeUnit.MINUTES.toNanos(minutes);
        } else {
            if (bAppend) {
                sb.append(" 0 minutes");
            }
        }

        if (nanos <= 0) {
            return (bAppend) ? sb.toString() : "0.0 seconds";
        }

        final long seconds = TimeUnit.NANOSECONDS.toSeconds(nanos);
        if (seconds >= 0) {
            if (bAppend) {
                sb.append(" ");
            }
            bAppend = true;
            sb.append(seconds);
        }

        if (nanos <= 0) {
            return (bAppend) ? sb.append((seconds == 1L) ? " second" : " seconds").toString() : "0.0 seconds";
        }

        if (fractionsOfSecond) {
            if (!bAppend) {
                sb.append("0");
            }
            bAppend = true;
            nanos -= TimeUnit.SECONDS.toNanos(seconds);
            final long millis = TimeUnit.NANOSECONDS.toMillis(nanos);

            sb.append(".").append(millis);
        }

        return (bAppend) ? sb.append(((seconds == 1L) && !fractionsOfSecond) ? " second" : " seconds").toString()
                : "0.0 seconds";
    }

    /**
     * Helper function to get the fileds from a comma separated list
     * "corcodil, veverita , bursuc" ===> {"crocodil", "veverita", "bursuc"}
     * 
     * @param list
     * @return The fields from a comma separated "record" (list)
     */
    public static final String[] getSplittedListFields(final String list) {
        if (list == null) {
            return null;
        }
        String[] pTokens = LIST_SPLIT_PATTERN.split(list);
        if ((pTokens == null) || (pTokens.length == 0)) {
            return null;
        }

        // strip blank fields
        // e.g cioara, ; varza ; capra ===> cioara varza capra
        ArrayList<String> nTokens = new ArrayList<String>(pTokens.length);
        for (String pToken : pTokens) {
            if ((pToken != null) && (pToken.trim().length() > 0)) {
                nTokens.add(pToken.trim());
            }
        }

        return nTokens.toArray(new String[nTokens.size()]);
    }

    /**
     * Parse a list of arguments passed to modules
     * 
     * @param arguments
     *            module arguments, like
     *            "ParamTimeout=120,NodeTimeout=120,ClusterTimeout=120,port=11002"
     * @return parsed list, like { "ParamTimeout" -> "120", "NodeTimeout" ->
     *         "120" ...
     * @see #getSplittedListFields(String)
     */
    public static final Map<String, String> parseOptions(final String arguments) {
        final String[] tokens = getSplittedListFields(arguments);

        final Map<String, String> ret = new TreeMap<String, String>();

        if ((tokens == null) || (tokens.length == 0)) {
            return ret;
        }

        for (final String s : tokens) {
            final int idx = s.indexOf('=');

            if (idx < 0) {
                ret.put(s, s);
            } else {
                ret.put(s.substring(0, idx).trim(), s.substring(idx + 1).trim());
            }
        }

        return ret;
    }

    public static final LookupLocator[] getLUDSs(String lusList) {
        String[] tokens = getSplittedListFields(lusList);

        if ((tokens == null) || (tokens.length < 1)) {
            return null;
        }

        ArrayList<LookupLocator> locators = new ArrayList<LookupLocator>(tokens.length);

        for (String host : tokens) {
            try {
                locators.add(new LookupLocator("jini://" + host));
            } catch (java.net.MalformedURLException e) {
                logger.log(Level.WARNING, "URL format error ! host=" + host + "   \n", e);
            } catch (Throwable t) {
                logger.log(Level.WARNING, "General Exception adding LUS for address " + host, t);
            }
        }

        if (locators.size() > 0) {
            return locators.toArray(new LookupLocator[locators.size()]);
        }

        return null;
    }

    public static final <T> T getEntry(final ServiceItem si, final Class<T> entryClass) {
        if (si == null) {
            return null;
        }
        final Entry[] attrs = si.attributeSets;
        if (attrs == null) {
            return null;
        }
        for (Entry attr : attrs) {
            if (attr.getClass() == entryClass) {
                return entryClass.cast(attr);
            }
        }
        return null;
    }

    /**
     * returns a char corresponding to the hex digit of the supplied integer.
     */
    private static char charFromHexDigit(int digit) {
        if ((digit >= 0) && (digit <= 9)) {
            return (char) (digit + '0');
        }

        return (char) ((digit - 10) + 'a');
    }

    /**
     * produce a hex dump of the given data array
     */
    public static String hexDump(byte[] data, int len) {
        StringBuilder rez = new StringBuilder();
        int count;

        for (int adr = 0; adr < len; adr += 16) {
            // print running offset
            for (int d = 0; d < 8; d++) {
                int digitValue = (adr >> (4 * (7 - d))) % 16;
                rez.append(charFromHexDigit((digitValue >= 0) ? digitValue : (16 + digitValue)));
            }
            rez.append("  ");
            count = Math.min(adr + 16, len) - adr;
            // print hexbytes
            for (int x = 0; x < count; x++) {
                int i = (data[adr + x] >= 0) ? data[adr + x] : (256 + data[adr + x]);
                rez.append(charFromHexDigit(i / 16));
                rez.append(charFromHexDigit(i % 16));
                rez.append(' ');
                if (x == 7) {
                    rez.append(' ');
                }
            }
            // add padding for last line, if needed
            for (int x = 0; x < (16 - count); x++) {
                rez.append("   ");
                if ((x == 7) && (count != 8)) {
                    rez.append(' ');
                }
            }
            // print ascii
            rez.append(" |");
            for (int x = 0; x < count; x++) {
                char v = (char) data[adr + x];
                if ((v >= ' ') && (v < (char) 0x7f)) {
                    rez.append(v);
                } else {
                    rez.append('.');
                }
            }
            rez.append('|');
            rez.append('\n');
        }
        return rez.toString();
    }

    public static final String getPromptLikeBinShCmd(String cmd) {
        return " echo -e '\\n****\\n\\$" + cmd + "'; " + cmd + "; ";
    }

    public static final void appendExternalProcessStatus(String[] procs, StringBuilder sb) {
        appendExternalProcessStatus(procs, null, sb);
    }

    /**
     * @param r
     * @return same result if no negative values were found
     */
    public static final Result filterNegativeValues(final Result r, boolean verbose) {
        final String[] pNames = r.param_name;
        final double[] pValues = r.param;
        final int pNamesLen = pNames.length;

        Result retR = null;

        for (int i = 0; i < pNamesLen; i++) {
            final String pName = pNames[i];
            final double pVal = pValues[i];

            if (pVal < 0) {
                if (retR == null) {
                    retR = new Result(r.FarmName, r.ClusterName, r.NodeName, r.Module);
                    retR.time = r.time;
                    // copy all the values already processed
                    for (int j = 0; j < i; j++) {
                        retR.addSet(pNames[j], pValues[j]);
                    }
                }
                if (verbose) {
                    logger.log(Level.INFO, " [ filterNegativeValues ] filtering " + pName + " val: " + pVal);
                }
            } else {
                if (retR != null) {
                    retR.addSet(pName, pVal);
                }
            }

        }

        return (retR == null) ? r : retR;
    }

    public static final void appendExternalProcessStatus(String[] procs, String[] env, StringBuilder sb) {
        String command = Arrays.toString(procs);

        Process pro = null;
        InputStream out = null;
        InputStream err = null;
        InputStreamReader er = null;
        InputStreamReader in = null;
        BufferedReader brin = null;
        BufferedReader brerr = null;

        final long ntpDate = NTPDate.currentTimeMillis();
        final long sTimeNanos = System.nanoTime();

        try {
            sb.append("\n[ Utils ] START [ ExecProc ] @ System Time: ").append(new Date()).append(" / NTPDate Time: ")
                    .append(new Date(ntpDate));
            pro = MLProcess.exec(procs, env, 3 * 60 * 1000);

            out = pro.getInputStream();
            err = pro.getErrorStream();
            er = new InputStreamReader(err);
            in = new InputStreamReader(out);
            brin = new BufferedReader(in);
            brerr = new BufferedReader(er);

            sb.append("\n\nSTDOUT:\n");
            String line;
            while (brin.ready() && ((line = brin.readLine()) != null)) {
                sb.append(line).append("\n");
            }

            pro.waitFor();

            sb.append("\n\nSTDERR:\n");
            while (brerr.ready() && ((line = brerr.readLine()) != null)) {
                sb.append(line).append("\n");
            }
            long finishDate = NTPDate.currentTimeMillis();
            sb.append("\n[ Utils ] END [ ExecProc ] dt = [ ")
                    .append(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sTimeNanos))
                    .append(" ] ms @ System Time: ").append(new Date()).append(" / NTPDate Time: ")
                    .append(new Date(finishDate));
        } catch (Throwable t) {
            long finishTime = NTPDate.currentTimeMillis();
            sb.append("\n[ Utils ] END [ ExecProc ] dt = [ ")
                    .append(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sTimeNanos))
                    .append(" ] ms @ System Time: ").append(new Date()).append(" / NTPDate Time: ")
                    .append(new Date(finishTime));
            sb.append("\n\nGot Exception executing ext command ").append(command).append("\n")
                    .append(Utils.getStackTrace(t));
        } finally {
            try {
                if (pro != null) {
                    pro.destroy();
                }
            } catch (Throwable ignore) {
                // we could not care less
            }
            closeIgnoringException(brerr);
            closeIgnoringException(brin);
            closeIgnoringException(er);
            closeIgnoringException(err);
            closeIgnoringException(in);
            closeIgnoringException(out);
        }
    }

    public static final void addFileContentToStringBuilder(String fileName, StringBuilder sb) {
        if ((sb == null) || (fileName == null)) {
            return;
        }

        FileReader fr = null;
        BufferedReader br = null;

        try {
            sb.append("\n ####### File CONTENT [ ").append(fileName).append(" #######\n\n");
            fr = new FileReader(fileName);
            br = new BufferedReader(fr);
            for (;;) {
                String lin = br.readLine();
                if (lin == null) {
                    break;
                }
                sb.append(lin + "\n");
            }
            sb.append("\n ####### END File CONTENT for file [ ").append(fileName).append(" ] #######\n");
        } catch (Throwable t) {
            sb.append("\n\n Got Exception appending file content for file [ ").append(fileName).append(" ] :\n");
            sb.append(getStackTrace(t));
        } finally {
            closeIgnoringException(fr);
            closeIgnoringException(br);
        }
    }

    /**
     * @param o
     * @return a byte[] with the serialized version of Object
     */
    public static final byte[] writeObject(Object o) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(o);
        oos.flush();
        byte[] retv = baos.toByteArray();
        oos.close();
        return retv;
    }

    public static final byte[] writeCompressedObject(Object o) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzos = new GZIPOutputStream(baos);
        ObjectOutputStream oos = new ObjectOutputStream(gzos);
        oos.writeObject(o);
        oos.flush();
        oos.close();
        gzos.close();
        baos.close();
        byte[] retv = baos.toByteArray();
        return retv;
    }

    public static final byte[] writeDirectObject(Object o) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        try {
            // oos.writeObject(FOO_SERIALIZABLE);
            oos.flush();
            oos.reset();
            baos.reset();
            oos.writeObject(o);
            oos.flush();
            oos.reset();
            return baos.toByteArray();
        } finally {
            oos.close();
        }
    }

    /**
     * @param o
     * @return a byte[] with the serialized version of Object
     */
    public static final Object readObject(byte[] buff) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(buff);
        ObjectInputStream ois = new ObjectInputStream(bais);
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    public static final <T> T readCompressedObject(byte[] buff, Class<T> classz) throws Exception {
        return classz.cast(readCompressedObject(buff));
    }

    public static final Object readCompressedObject(byte[] buff) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(buff);
        ObjectInputStream ois = new ObjectInputStream(new GZIPInputStream(bais));
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    /**
     * Compare two version numbers of the form "1.6.12"
     * 
     * @param probe
     *            - version to be tested
     * @param base
     *            - against to this version
     * @return - returns -1, 0, 1
     */
    public static int compareVersion(String probe, String base) {
        int[] ivBase = splitVersion(base);
        int[] ivProbe = splitVersion(probe);
        for (int i = 0; i < Math.min(ivBase.length, ivProbe.length); i++) {
            if (ivProbe[i] > ivBase[i]) {
                return 1;
            }
            if (ivProbe[i] < ivBase[i]) {
                return -1;
            }
        }
        return (ivProbe.length > ivBase.length ? 1 : (ivProbe.length < ivBase.length ? -1 : 0));
    }

    private static int[] splitVersion(String ver) {
        String[] svVer = ver.split("[^0-9]");
        int[] ivVer = new int[svVer.length];
        for (int i = 0; i < svVer.length; i++) {
            try {
                ivVer[i] = Integer.parseInt(svVer[i]);
            } catch (Exception ex) {
                ivVer[i] = 0;
            }
        }
        return ivVer;
    }

    /**
     * Get the output from the given system command
     * 
     * @param command
     * @return
     */
    public static final String getOutput(final String command) {
        Process p = null;

        try {
            p = MLProcess.exec(new String[] { "/bin/bash", "-c", command }, 1000 * 100);

            p.getOutputStream().close();

            final BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));

            final StringBuilder sb = new StringBuilder();

            String sLine;

            while ((sLine = br.readLine()) != null) {
                sb.append(sLine).append('\n');
            }

            br.close();

            p.getErrorStream().close();

            return sb.toString();
        } catch (Exception _) {
            logger.log(Level.WARNING, "Cannot execute '" + command + "'", _);
        } finally {
            if (p != null) {
                try {
                    p.waitFor();
                } catch (Exception _) {
                    // ignore
                }

                try {
                    p.destroy();
                } catch (Exception _) {
                    // ignore
                }
            }
        }

        return "";
    }

    public static long copyFile2File(File source, File destination) throws IOException {
        // Create channel on the source
        FileChannel srcChannel = null;
        FileChannel dstChannel = null;

        FileInputStream fis = null;
        FileOutputStream fos = null;

        try {
            fis = new FileInputStream(source);
            fos = new FileOutputStream(destination);

            srcChannel = fis.getChannel();
            dstChannel = fos.getChannel();

            long tr = dstChannel.transferFrom(srcChannel, 0, srcChannel.size());

            final long ss = srcChannel.size();
            final long ds = dstChannel.size();

            if ((ss != ds) || (ss != tr)) {
                throw new IOException("Cannot copy SourceFileSize [ " + ss + " ] DestinationFileSize [ " + ds
                        + " ] Transferred [ " + tr + " ] ");
            }

            // set the update time
            destination.setLastModified(source.lastModified());
            return ds;
        } finally {
            closeIgnoringException(srcChannel);
            closeIgnoringException(dstChannel);
            closeIgnoringException(fis);
            closeIgnoringException(fos);
        }

    }

    /**
     * Debugging method, dump any ML objects to screen
     * 
     * @param o
     *            Result, eResult or a collection of them
     */
    public static void dumpResults(final Object o) {
        if (o == null) {
            System.err.println("null");
        }

        if (o instanceof Collection<?>) {
            for (Object o2 : (Collection<?>) o) {
                dumpResults(o2);
            }
        }

        if (o instanceof Result) {
            final Result r = (Result) o;

            System.err.println("Result: " + r.FarmName + " / " + r.ClusterName + " / " + r.NodeName + " @ " + r.time
                    + " (" + (new Date(r.time) + ")"));

            if ((r.param != null) && (r.param_name != null)) {
                for (int i = 0; i < r.param.length; i++) {
                    System.err.println("  " + r.param_name[i] + "\t=\t" + r.param[i]);
                }
            } else {
                System.err.println("  <null param or param_name>");
            }
        }

        if (o instanceof eResult) {
            final eResult r = (eResult) o;

            System.err.println("eResult: " + r.FarmName + " / " + r.ClusterName + " / " + r.NodeName + " @ " + r.time
                    + " (" + (new Date(r.time) + ")"));

            if ((r.param != null) && (r.param_name != null)) {
                for (int i = 0; i < r.param.length; i++) {
                    System.err.println("  " + r.param_name[i] + "\t=\t " + r.param[i]);
                }
            } else {
                System.err.println("  <null param or param_name>");
            }
        }
    }

    /** Safe check of two object's equality */
    public static boolean equals(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if ((a == null) || (b == null)) {
            return false;
        }
        return a.equals(b);
    }

    public static final void waitUntilInterruptedAndThrowException() throws InterruptedException {
        final Object waitLock = new Object();
        synchronized (waitLock) {
            waitLock.wait();
        }
    }

    /**
     * @return true if wait was interrupted by {@link InterruptedException}, false otherwise
     */
    public static final boolean waitUntilInterruptedAndSwallowException() {
        try {
            waitUntilInterruptedAndThrowException();
        } catch (InterruptedException ie) {
            return true;
        } catch (Throwable t) {
            // OOM - VM Error?
            return false;
        }

        return false;
    }

    // just for testing
    // some outputs
    // args [ 0 ] = cioara, varza, capra ===> [ 3 ] tokens ===> cioara varza
    // capra
    // args [ 1 ] = cioara ; varza , capra ===> [ 3 ] tokens ===> cioara varza
    // capra
    // args [ 2 ] = cioara, ; varza ; capra ===> [ 3 ] tokens ===> cioara varza
    // capra
    // args [ 3 ] = ;;; ; ; ;; , cioara, ; varza ; capra ===> [ 3 ] tokens ===>
    // cioara varza capra
    public static final void main(String args[]) {
        if ((args == null) || (args.length == 0)) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            sb.append("\n");
            sb.append(" args [ ").append(i).append(" ] = ").append(args[i]).append(" ===> ");
            String[] tokens = getSplittedListFields(args[i]);
            if ((tokens == null) || (tokens.length == 0)) {
                sb.append(" !!! No tokens !!!! ");
            } else {
                sb.append(" [ ").append(tokens.length).append(" ] tokens ===> ");
                for (String token : tokens) {
                    sb.append("\t").append(token);
                }
            }// else
        }// for ( i )

        System.out.println(sb.toString());
    }// main()
}
