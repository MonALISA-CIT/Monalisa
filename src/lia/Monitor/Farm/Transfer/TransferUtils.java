package lia.Monitor.Farm.Transfer;

import java.net.InetAddress;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;

/**
 * Various tools and utilities for the AppTransfer package
 * 
 * @author catac
 */
public class TransferUtils {
    /** Logger used by this class */
    private final static Logger logger = Logger.getLogger(TransferUtils.class.getName());

    /** Suffixes for the bandwidth values */
    public static final String[] bwSuffixes = { "B", "K", "M", "G", "T", "X" };

    /** Number formatter for doubles */
    private static NumberFormat nfDouble = NumberFormat.getInstance();

    /** Maximum bandwidth */
    public static final long MAX_BANDWIDTH = 1L * 1000 * 1000 * 1000 * 1000;

    /** Monitoring results look like being published by this module */
    public final static String resultsModuleName = "monAppTransfer";

    /** Name of this ML service */
    public final static String farmName;

    static {
        // prepare the number formatter
        nfDouble.setMinimumFractionDigits(0);
        nfDouble.setMaximumFractionDigits(2);

        // prepare the farm name
        String name = AppConfig.getProperty("MonALISA_ServiceName");
        if (name == null) {
            try {
                name = InetAddress.getLocalHost().toString();
            } catch (Throwable e) {
                name = "localhost";
            }
        }
        farmName = name;
    }

    /**
     * Try parsing the given bandwidth string having an optional suffix (B, K, M, G).
     * Note that suffixes multiply by 1000 not by 1024
     * 
     * @param value the bandwidth string (in (K/M/G) bits per second) of the form 10G
     * @return the bandwidth value, in bps.
     */
    public static long parseBKMGps(String value) {
        if ((value == null) || (value.length() == 0)) {
            logger.warning("Empty bandwidth property. Assuming infinite");
            return MAX_BANDWIDTH;
        }
        long mul = 1;
        char suffix = Character.toUpperCase(value.charAt(value.length() - 1));
        String val = value.substring(0, value.length() - 1);
        switch (suffix) {
        case 'T':
            mul = 1000 * 1000 * 1000 * 1000;
            break;
        case 'G':
            mul = 1000 * 1000 * 1000;
            break;
        case 'M':
            mul = 1000 * 1000;
            break;
        case 'K':
            mul = 1000;
            break;
        case 'B':
            mul = 1;
            break;
        default:
            val = value; // take the full string, including the suffix
        }
        try {
            return (long) (mul * Double.parseDouble(val));
        } catch (NumberFormatException nfe) {
            logger.warning("Cannot parse bandwidth property '" + value + "'. Assuming infinite.");
        }
        return MAX_BANDWIDTH;
    }

    /**
     * Convert bits into bytes
     * 
     * @param bits speed, in bits per second
     * @return speed, in bytes/second
     */
    public static long bitsToBytes(long bits) {
        return bits / 8;
    }

    /**
     * Convert bytes into bits
     * 
     * @param bytes speed, in bytes/second
     * @return speed, in bps
     */
    public static long bytesToBits(long bytes) {
        return bytes * 8;
    }

    private static String prettyNetSpeed(long value, long divisor) {
        int idx = 0;
        double val = value;
        while ((val >= divisor) && (idx < (bwSuffixes.length - 1))) {
            val /= divisor;
            idx++;
        }
        return nfDouble.format(val) + " " + (idx > 0 ? bwSuffixes[idx] : "");
    }

    /**
     * Pretty print the given speed in t/g/m/k/ bps 
     * @param value the speed in bits per second
     * @return the corresponding speed in t/g/m/k/ bps
     */
    public static String prettyBitsSpeed(long value) {
        return prettyNetSpeed(value, 1000).toLowerCase() + "bps";
    }

    /**
     * Pretty print the given speed in T/G/M/K/ B/s 
     * @param value the speed in bytes per second
     * @return the corresponding speed in T/G/M/K/ B/s
     */
    public static String prettyBytesSpeed(long value) {
        return prettyNetSpeed(value, 1024) + "B/s";
    }

    /**
     * Join the given properties using the provided equal and pair concatenator chars
     * @param prop list of properties to join
     * @param equalSign used in propName<<equalSign>>propValue
     * @param moreChar used between multiple pairs name=value
     * @return the joined string
     */
    public static String joinProperties(Properties prop, char equalSign, char moreChar) {
        StringBuilder sbRes = new StringBuilder();
        for (Object element : prop.entrySet()) {
            Map.Entry me = (Map.Entry) element;
            if (sbRes.length() > 0) {
                sbRes.append(moreChar);
            }
            sbRes.append(me.getKey()).append(equalSign).append(me.getValue());
        }
        return sbRes.toString();
    }

    /**
     * Split a string containing several concatenated properties with the form
     * name=value<<separator>>name=value in the corresponding Properties object
     * @param propString the string with concatenated properties
     * @param separator the separator between the properties in string
     * @return the corresponding Properties 
     */
    public static Properties splitString(String propString, String separator) {
        Properties props = new Properties();
        StringTokenizer stk = new StringTokenizer(propString, separator);
        while (stk.hasMoreTokens()) {
            String line = stk.nextToken().trim();
            if ((line.length() == 0) || line.startsWith("#")) {
                continue;
            }
            int idxEq = line.indexOf('=');
            if (idxEq == -1) {
                continue; // ignore ill-formated lines
            }
            props.setProperty(line.substring(0, idxEq).trim(), line.substring(idxEq + 1).trim());
        }
        return props;
    }

    /**
     * Parse a comma-separated string of x (number) and x-y (intervals), like
     * 5 or 1,2,3 or 3,5-7,9 
     * @param sequence the string to be parsed
     * @return the list with all individual Integer numbers.
     */
    public static List parseSequence(String sequence) {
        TreeSet res = new TreeSet();
        List val = new ArrayList();
        StringTokenizer stkSeq = new StringTokenizer(sequence.trim(), ",");
        while (stkSeq.hasMoreTokens()) {
            val.clear();
            String subSeq = stkSeq.nextToken().trim();
            StringTokenizer stkInt = new StringTokenizer(subSeq, "-");
            while (stkInt.hasMoreTokens()) {
                String nr = stkInt.nextToken().trim();
                if (nr.length() > 0) {
                    try {
                        val.add(Integer.valueOf(nr));
                    } catch (NumberFormatException nfe) {
                        logger.warning("Not a number in sequence '" + sequence + "'. Ignoring '" + nr + "'.");
                    }
                }
            }
            if (val.size() > 2) {
                logger.warning("Too many '-' in sequence '" + sequence + "'. Ignoring '" + subSeq + "'.");
                continue;
            }
            if (val.size() == 0) {
                logger.warning("Too few elements in sequence '" + sequence + ". Ignoring '" + subSeq + "'.");
                continue;
            }
            Integer iLow = (Integer) val.get(0);
            res.add(iLow);
            if (val.size() == 2) {
                Integer iHigh = (Integer) val.get(1);
                res.add(iHigh);
                int low = iLow.intValue();
                int high = iHigh.intValue();
                int dir = sign(high - low);
                low += dir;
                while (low != high) {
                    res.add(Integer.valueOf(low));
                    low += dir;
                }
            }
        }
        return new ArrayList(res);
    }

    /** Get the sign of the given value */
    private static int sign(int value) {
        return value > 0 ? 1 : value < 0 ? -1 : 0;
    }

    /**
     * Parse a property that should contain a long value
     * @param value the property's value, as string
     * @param name the property's name
     * @param defValue default value
     * @return the result
     */
    public static long parseLongValueProperty(String value, String name, long defValue) {
        long res = defValue;
        try {
            res = Long.parseLong(value);
        } catch (NumberFormatException nfe) {
            logger.warning("Cannot parse " + name + " property '" + value + "'. Assuming " + defValue);
        }
        return res;
    }

}
