/*
 * Created on Sep 18, 2010
 */
package lia.util.update;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;

/**
 * @author ramiro
 */
public class UpdaterUtils {

    private static final Logger logger = Logger.getLogger(UpdaterUtils.class.getName());

    static final String HEXES = "0123456789abcdef";

    // 64K works ok with the disk buffer size
    private static final int DISK_BUFFER_SIZE = AppConfig.geti("lia.util.update.DISK_BUFFER_SIZE", 64 * 1024);

    /**
     * Helper function which returns a full path for the local FS based on an URL
     * 
     * @param prefix
     * @param url
     * @return the full path based on the specified URL
     */
    public static final File getLocalDestinationForURL(final String prefix, final URL url) {
        final String path = url.getPath();
        if (path == null) {
            throw new NullPointerException("Null path for URL: " + url);
        }
        if (prefix == null) {
            throw new NullPointerException("Prefix cannot be null!");
        }

        final String protocol = url.getProtocol();
        final String host = url.getHost();
        final int port = url.getPort();

        final StringBuilder sb = new StringBuilder(prefix);

        if (!prefix.endsWith(File.separator)) {
            sb.append(File.separatorChar);
        }
        sb.append(protocol).append(File.separatorChar);

        if (host != null) {
            sb.append(host);
        }

        if (port > 0) {
            sb.append("_").append(port);
        }

        path.replace('/', File.separatorChar);
        sb.append(path);

        return new File(sb.toString());
    }

    public static final void closeIgnoringException(Closeable c) {
        if (c == null) {
            return;
        }

        try {
            c.close();
        } catch (Throwable ignore) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ UpdaterUtils ] [ closeIgnoringException ] exception closing: " + c
                        + " Cause: ", ignore);
            }
        }
    }

    public static final byte[] computeDigest(final File file, final MessageDigest digest) throws IOException {
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        digest.reset();
        try {
            fis = new FileInputStream(file);
            bis = new BufferedInputStream(fis);
            final byte buff[] = new byte[DISK_BUFFER_SIZE];
            for (;;) {
                final int len = bis.read(buff);
                if (len < 0) {
                    break;
                }
                digest.update(buff, 0, len);
            }
        } finally {
            closeIgnoringException(fis);
            closeIgnoringException(bis);
        }
        return digest.digest();
    }

    public static final String toHexString(final byte[] raw) {
        if (raw == null) {
            return null;
        }
        final StringBuilder hex = new StringBuilder(2 * raw.length);
        for (final byte b : raw) {
            hex.append(HEXES.charAt((b & 0xF0) >> 4)).append(HEXES.charAt((b & 0x0F)));
        }
        return hex.toString();
    }

    public static String getOption(String[] args, String option, String defaultValue) {
        final String rVal = getOption(args, option);
        return (rVal == null) ? defaultValue : rVal;
    }

    public static String getOption(String[] args, String option) {

        for (int i = 0; i < args.length; i++) {
            if (option.equals(args[i])) {
                if (args.length > (i + 1)) {
                    return (args[i + 1].startsWith("-")) ? "" : args[i + 1];
                }

                return "";
            }
        }

        return null;
    }

    public static void copyFile2File(File source, File destination) throws IOException {
        // Create channel on the source
        FileChannel srcChannel = null;
        FileChannel dstChannel = null;
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            setRWOwnerOnly(destination);

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

            setRWOwnerOnly(destination);
        } finally {
            closeIgnoringException(srcChannel);
            closeIgnoringException(dstChannel);
            closeIgnoringException(fis);
            closeIgnoringException(fos);
        }

    }

    public static final boolean setRWOwnerOnly(final File f) {
        try {
            boolean bRet = true;
            if (f.exists()) {
                // reset first
                bRet = bRet && f.setReadable(false, false);
                bRet = bRet && f.setWritable(false, false);

                // set the "right" permissions
                // TODO we may check but may fail without other meaningful notice...
                bRet = bRet && f.setReadable(true, false);
                bRet = bRet && f.setWritable(true, true);
                return bRet;
            }

            return false;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "AppRemoteURLUpdater unable to set RWOwnerOnly", t);
        }

        return false;
    }

}
