/*
 * @(#)Format.java 1.0 02/16/2005
 * 
 * Copyright 2005 California Institute of Technology
 */

package lia.util.security.authz;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * This class provides all sorts of usefull formatting functions.
 * 
 * @author Costin Grigoras <costing@cs.pub.ro>
 * @version 1.0 02/16/2005
 * @see monalisa.core.Core
 * @see monalisa.modules.PullModule
 * @see monalisa.modules.PushModule
 * @see monalisa.modules.StorageModule
 * @since ML 2.0.0
 */
public class Format {

    /**
     * Generate a HTML-safe string, escaping all the illegal characters like
     * <code>&amp;, &lt;, &gt;</code>.
     * 
     * @param line
     *            the string to be made safe
     * @return the HTML-safe conversion of the original string
     */
    public static final String htmlEscape(String line) {
        char[] vc = line.toCharArray();
        int l = vc.length;

        StringBuilder sb = new StringBuilder(l);

        for (int i = 0; i < l; i++) {
            char c = vc[i];
            switch (c) {
            case '&':
                sb.append("&amp;");
                break;
            case '<':
                sb.append("&lt;");
                break;
            case '>':
                sb.append("&gt;");
                break;
            case '\"':
                sb.append("&quot;");
                break;
            default:
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /**
     * Generate a SQL-safe string, escaping all the illegal characters like
     * <code>\, ', ", \n</code>.
     * 
     * @param s
     *            the string to be made safe
     * @return the SQL-safe conversion of the original string
     */
    public static final String sqlEscape(String s) {
        char[] vc = s.toCharArray();
        int l = vc.length;

        StringBuilder sb = new StringBuilder(l + 30);

        for (int i = 0; i < l; i++) {
            char c = vc[i];
            switch (c) {
            case '\\':
                sb.append("\\\\");
                break;
            case '\'':
                sb.append("\'\'");
                break;
            case '\"':
                sb.append("\\\"");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case (char) 0:
                sb.append("\\0");
                break;
            default:
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /**
     * Generate a JavaScript-safe string, escaping all the illegal characters
     * like <code>\, ', ", \n</code>.
     * 
     * @param s
     *            the string to be made safe
     * @return the JavaScript-safe conversion of the original string
     */
    public static final String jsEscape(String s) {
        char[] vc = s.toCharArray();
        int l = vc.length;

        StringBuilder sb = new StringBuilder(l + 30);

        for (int i = 0; i < l; i++) {
            char c = vc[i];
            switch (c) {
            case '\\':
                sb.append("\\\\");
                break;
            case '\'':
                sb.append("\\\'");
                break;
            case '\"':
                sb.append("\\\"");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case (char) 0:
                sb.append("\\0");
                break;
            default:
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /**
     * Replace all the occurences of <code>sWhat</code> in the original
     * <code>sOrig</code> string with <code>sWith</code>. It is a much
     * faster implementation than <code>String.replace()</code> because it
     * doesn't involve regular expressions.
     * 
     * @param sOrig
     *            the original String
     * @param sWhat
     *            the string to be replaced
     * @param sWith
     *            the string to be put in place of <code>sWhat</code>
     * @return the string that has all the occurences of <cod>sWhat</code>
     *         replaced with <code>sWith</code>
     */
    public static final String replace(String sOrig, String sWhat, String sWith) {
        final int l = sWhat.length();

        final StringBuilder sb = new StringBuilder(sOrig.length());

        int i = 0;
        int j;

        while ((j = sOrig.indexOf(sWhat, i)) >= 0) {
            sb.append(sOrig.substring(i, j)).append(sWith);
            i = j + l;
        }

        sb.append(sOrig.substring(i));

        return sb.toString();
    }

    /**
     * Pattern to check if a string is safe enough
     */
    private static final Pattern SAFE_PATTERN = Pattern.compile("^[-\\]\\[/()\\$\\{\\}a-zA-Z0-9_:;,\\.@ ]*$");

    /**
     * Translate the original string in a URL-safe encoding.
     * 
     * @param s
     *            the original string
     * @return the URL-encoded translation
     */
    public static final String encode(String s) {
        try {
            Matcher m = SAFE_PATTERN.matcher(s);
            if (m.matches())
                return s;
            return URLEncoder.encode(s, "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    /**
     * Reverse transformation of URL-passed strings.
     * 
     * @param s
     *            the URL-encoded string
     * @return the extracted String
     */
    public static final String decode(String s) {
        try {
            return URLDecoder.decode(s, "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    /**
     * Convert an <code>Object</code> to a <code>byte[]</code>. Usefull for
     * sending objects over the network connections or saving objects in a file
     * and so on.
     * 
     * @param o
     *            the object to be serialized
     * @return the byte[] image of this object
     */
    public static final byte[] serializeToBytes(Object o, boolean compress) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(100000);

            final OutputStream os;

            if (compress) {
                os = new GZIPOutputStream(baos);
            } else {
                os = baos;
            }

            ObjectOutputStream oos = new ObjectOutputStream(os);

            oos.writeObject(o);
            oos.flush();
            oos.close();

            os.flush();

            baos.flush();
            baos.close();

            byte[] vb = baos.toByteArray();
            return vb;
        } catch (Throwable t) {
            System.err.println(t);
            t.printStackTrace();
            return null;
        }
    }

    public static final byte[] serializeToBytes(Object o) {
        return serializeToBytes(o, false);
    }

    /**
     * Convert an <code>Object</code> to a ASCII <code>String</code>. The
     * result is guaranteed to contain only visible characters (letters, digits
     * ...). Very usefull for saving objects in plain text files, database text
     * fields and so on. Null parameters are specially encoded, so you can
     * safely convert any values to String.
     * 
     * @param o
     *            the <code>Object</code> to be serialized
     * @return a <code>String</code> representation of this object
     * @see #deserializeFromString
     */
    public static final String serializeToString(Object o) {
        if (o == null)
            return "n";

        if (o instanceof String) {
            String s = (String) o;

            if (s.matches("^[-._a-zA-Z0-9]*$"))
                return "s" + s;
        }

        try {
            byte vb[] = serializeToBytes(o, false);
            char[] vc = new char[vb.length * 2];

            for (int i = 0; i < vb.length; i++) {
                int k = vb[i];

                k += 128;
                vc[i * 2] = (char) ((k / 16) + 'A');
                vc[i * 2 + 1] = (char) ((k % 16) + 'A');
            }

            String s = new String(vc);

            return "b" + s;
        } catch (Throwable t) {
            System.err.println(t);
            t.printStackTrace();
            return null;
        }
    }

    /**
     * Convert a <code>byte[]</code> object image back to a Java
     * <code>Object</code>.
     * 
     * @param from
     *            the object image
     * @return the Object or <code>null</code> if there was an error
     */
    public static final Serializable deserializeFromBytes(byte[] from, boolean compressed) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(from);
            InputStream is;
            if (compressed) {
                is = new GZIPInputStream(bais);
            } else {
                is = bais;
            }

            ObjectInputStream ois = new ObjectInputStream(is);
            return (Serializable) ois.readObject();
        } catch (Throwable t) {
            System.err.println(getStackAsString(t));
            return null;
        }
    }

    public static final Serializable deserializeFromBytes(byte[] from) {
        return deserializeFromBytes(from, false);
    }

    /**
     * Get the original Java <code>Object</code> from a <code>String</code>
     * image of it.
     * 
     * @param s
     *            the <code>String</code> image of the object
     * @return the Java <code>Object</code> or <code>null</code> if there
     *         was an error decoding or if the originally encoded object was
     *         actually <code>null</code>.
     */
    public static final Serializable deserializeFromString(String s) {
        if (s.equals("n"))
            return null;

        if (s.startsWith("s"))
            return s.substring(1);

        try {
            if (!s.startsWith("b"))
                return null;

            s = s.substring(1);

            if (s.length() > 0) {
                final int l = s.length() / 2;

                final byte[] vb2 = new byte[l];
                final char[] vc2 = s.toCharArray();

                int k;

                for (int i = 0; i < l; i++) {
                    k = (vc2[i * 2] - 'A') * 16 + (vc2[i * 2 + 1] - 'A');
                    vb2[i] = (byte) k;
                    vb2[i] -= 128;
                }

                return deserializeFromBytes(vb2, false);
            }

            return null;
        } catch (Throwable t) {
            System.err.println(t);
            t.printStackTrace();
            return null;
        }
    }

    public static int toInt(byte[] buf, int off) {
        int lg = (buf[off] & 0xff) << 24;
        lg |= (buf[off + 1] & 0xff) << 16;
        lg |= (buf[off + 2] & 0xff) << 8;
        lg |= (buf[off + 3] & 0xff);
        return lg;
    } // toInt

    public static long toLong(byte[] buf, int off) {
        long lg = (long) (buf[off] & 0xff) << 56;
        lg |= (long) (buf[off + 1] & 0xff) << 48;
        lg |= (long) (buf[off + 2] & 0xff) << 40;
        lg |= (long) (buf[off + 3] & 0xff) << 32;
        lg |= (long) (buf[off + 4] & 0xff) << 24;
        lg |= (long) (buf[off + 5] & 0xff) << 16;
        lg |= (long) (buf[off + 6] & 0xff) << 8;
        lg |= (long) (buf[off + 7] & 0xff);
        return lg;
    } // toLong

    /* name@group */
    // \p{Print}
    // private static final Pattern NAME_AT_GROUP =
    // Pattern.compile("^(\\w+)(@(\\w+))?$");
    private static final Pattern NAME_AT_GROUP = Pattern.compile("^([\\p{Print}&&[^@]]+)(@([\\p{Print}&&[^@]]+))?$");

    /**
     * @param nameAtGroup
     * @return {name,group} or {} if string does not match "Name@Group" regex
     * 
     */
    public static String[] parseNameAtGroup(String nameAtGroup) {
        Matcher m = NAME_AT_GROUP.matcher(nameAtGroup);
        if (m.matches()) {
            return new String[] { m.group(1), m.group(3) };
        }

        return new String[] { "", "" };
    }

    /**
     * @param msg
     * @return the compressed message
     */
    public static byte[] gzip(byte[] msg) throws IOException {

        // Create the compressor with highest level of compression
        Deflater compressor = new Deflater();
        compressor.setLevel(Deflater.BEST_COMPRESSION);

        // Give the compressor the data to compress
        compressor.setInput(msg);
        compressor.finish();

        // Create an expandable byte array to hold the compressed data.
        // You cannot use an array that's the same size as the orginal because
        // there is no guarantee that the compressed data will be smaller than
        // the uncompressed data.
        ByteArrayOutputStream bos = new ByteArrayOutputStream(msg.length);
        // Compress the data
        byte[] buf = new byte[1024];
        while (!compressor.finished()) {
            int count = compressor.deflate(buf);
            bos.write(buf, 0, count);
        }
        bos.flush();
        bos.close();

        // Get the compressed data
        return bos.toByteArray();
    }

    public static String getStackAsString(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        try {
            pw.flush();
            pw.close();
            sw.flush();
            sw.close();
        } catch (IOException e1) {
        }
        return sw.toString();
    }
}
