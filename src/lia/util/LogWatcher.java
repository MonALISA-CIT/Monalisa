package lia.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class that allows a file (usually a log) to be read incrementally. It only
 * reads the lines that were added to the log after it starts to monitor the 
 * file.
 */
public class LogWatcher {

    private static final Logger logger = Logger.getLogger(LogWatcher.class.getName());

    /** We try to compute the MD5 digest for the first NBYTES_MD5 from the file. */
    static final int NBYTES_MD5 = 512;

    /** The MD5 key as a string. */
    static final String MD5Key = "SomeMD5Key";

    /** The MD5 key as a byte array. */
    byte[] baMD5Key = null;

    /** The name of the file to be watched. */
    String filename = null;

    /** The canonical path of the file. */
    String path = null;

    /** The size of the file, in bytes, when it was read the last time. */
    long lastFileSize;

    byte[] lastMD5Digest;

    int actualNBytesMD5;

    boolean firstTime;

    public LogWatcher(String theFilename) {
        firstTime = true;
        this.filename = theFilename;
        //System.out.println("### LogWatcher " + theFilename);
        try {
            File file = new File(theFilename);
            path = file.getCanonicalPath();
            lastFileSize = file.length();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error opening file" + path);
            filename = path = null;
        }

        baMD5Key = MD5Key.getBytes();
    }

    /**
     * Reads the file and returns the bytes that were added to it since the
     * last read operation. If the log file was rotated, returns the whole
     * contents of the new file.
     */
    public BufferedReader getNewChunk() {
        File file = new File(filename);
        long newFileSize = file.length();
        logger.log(Level.FINE, "[LogWatcher] ### getNewChunck(): file size " + newFileSize);
        if (haveNewLog(file)) {
            logger.log(Level.INFO, "[LogWatcher] New log file: " + filename);
            actualNBytesMD5 = (int) ((newFileSize > NBYTES_MD5) ? NBYTES_MD5 : newFileSize);
            try {
                lastMD5Digest = computeDigest(file, actualNBytesMD5);
                lastFileSize = newFileSize;
                if (!firstTime) {
                    return new BufferedReader(new FileReader(file));
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "[LogWatcher] Error computing MD5 digest for " + path, e);
                lastMD5Digest = null;
            }
        }

        if (firstTime == true) {
            firstTime = false;
        }
        BufferedReader br = null;
        try {
            FileInputStream fis = new FileInputStream(filename);
            fis.skip(lastFileSize);
            lastFileSize = newFileSize;
            br = new BufferedReader(new InputStreamReader(fis));
        } catch (Exception e) {
            logger.log(Level.WARNING, "[LogWatcher] Error reading log file ", e);
        }
        return br;
    }

    /**
     * Verifies if the log file was rotated.
     */
    protected boolean haveNewLog(File file) {
        //quick check:
        if (firstTime || (file.length() < lastFileSize)) {
            return true;
        }

        /* see if this file has the same MD5 digest as the old one */
        byte[] newMD5Digest = null;
        try {
            newMD5Digest = computeDigest(file, actualNBytesMD5);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error computing MD5 digest", e);
            lastMD5Digest = null;
        }
        String newS = new String(newMD5Digest);
        String oldS = new String(lastMD5Digest);
        if (newS.equals(oldS)) {
            return false;
        } else {
            //logger.warning("### MD5 differ!");
            return true;
        }
    }

    /**
     * Computes the MD5 digest for the first nBytes bytes of the file given as
     * parameter.
     */
    protected byte[] computeDigest(File file, int nBytes) throws IOException, NoSuchAlgorithmException {
        FileInputStream is = new FileInputStream(file);
        byte[] buf = new byte[nBytes];
        int numRead = is.read(buf, 0, nBytes);
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.update(buf);
        return md5.digest(baMD5Key);
    }

    public String getFilename() {
        return this.filename;
    }

    public void setFilename(String theFilename) {
        this.filename = theFilename;
    }
}
