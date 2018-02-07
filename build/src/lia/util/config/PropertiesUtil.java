/*
 * $Id: PropertiesUtil.java 6878 2010-10-12 20:20:16Z ramiro $
 * Created on Nov 19, 2003
 */
package lia.util.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import lia.util.Utils;

/**
 * Helper class to add/modify/remove properties from ml.properties-like files. It
 * uses a temporary file to do this.
 * 
 * @author ramiro
 */
public class PropertiesUtil {

    /**
     * @param f  - Property file
     * @param tmpDir
     *            - where to place the tmp file used to "merge" the new Property File
     * @param property
     *            - Property to search for, add or replace
     * @param value
     *            - What value to assign to property
     * @param comment
     *            - Comment to add if property not found
     * @param overwriteIfFound
     * @param addifNotFound
     * @return true if already found
     * @throws Exception
     *             -mostly I/O error, or one of parameters is null
     */
    public static final boolean addModifyPropertyFile(final File f, final File tmpDir, final String property, final String value, final String comment, final boolean overwriteIfFound, final boolean addifNotFound) throws Exception {
        String line_separator = "\n";

        try {
            line_separator = System.getProperty("line.separator");
        } catch (Throwable t) {
            line_separator = "\n";
        }

        // START CHECKING PARAMS
        if (f == null) {
            throw new Exception("File Argument cannot be null!");
        }
        if (!f.exists()) {
            throw new Exception("File " + f.getPath() + "does not exists!");
        }
        if (!f.canRead() || !f.canWrite()) {
            throw new Exception("File " + f.getPath() + " must have R/W access");
        }

        if (property == null || property.length() == 0) {
            throw new Exception("Property parameter must be a String with .length() > 0");
        }
        // END CHECKING PARAMS

        boolean alreadyAssigned = false;
        if (alreadyAssigned && !overwriteIfFound) {
            return true;
        }

        File tmpF = null;
        try {
            tmpF = File.createTempFile("props", "tmpf", tmpDir);
            tmpF.deleteOnExit();
        } catch (Throwable t) {
            tmpF = null;
            if (tmpDir != null) {// try in /tmp
                try {
                    tmpF = File.createTempFile("props", "tmpf", null);
                    tmpF.deleteOnExit();
                } catch (Throwable t1) {
                    tmpF = null;
                }
            }
            if (tmpF == null) {// cannot create TMP File!
                throw new Exception(" Cannot create tmp file: tmpF =" + tmpF + "\n Exception: " + t);
            }
        }

        FileReader fr = null;
        BufferedReader br = null; 
        FileWriter fw = null;
        BufferedWriter bw = null;
        boolean propFound = false;

        try {
            fr = new FileReader(f);
            br= new BufferedReader(fr);
            fw = new FileWriter(tmpF);
            bw= new BufferedWriter(fw);

            // Let's Dance
            for (;;) {
                String lin = br.readLine();
                if (lin == null)
                    break;

                // coment lines...or blank lines
                if (lin.matches("^(\\s)*#.*") || lin.matches("^(\\s)*$")) {
                    if (lin.matches("^(\\s)*#+" + property + "(\\s)*=.*$")) {
                        if (!alreadyAssigned) {
                            bw.write(property + "=" + ((value == null || value.length() == 0) ? "" : value) + line_separator);
                            propFound = true;
                        }
                    } else {
                        bw.write(lin + line_separator);
                    }
                    continue;
                }

                if (lin.matches("^(\\s)*" + property + "(\\s)*=.*$")) {
                    if (propFound) {
                        continue;
                    }
                    propFound = true;
                    if (overwriteIfFound) {
                        bw.write(property + "=" + ((value == null || value.length() == 0) ? "" : value) + line_separator);
                    } else {
                        bw.write(lin + line_separator);
                    }
                } else if (lin.matches("^(\\s)*" + property + "(\\s)*$")) {// no "=" after property
                    propFound = true;
                    if (overwriteIfFound) {
                        if (!alreadyAssigned) {
                            bw.write(property + "=" + ((value == null || value.length() == 0) ? "" : value) + line_separator);
                        }
                    }
                } else {// normal Property
                    bw.write(lin + line_separator);
                }
            }// end for()

            if (!propFound) {
                if (addifNotFound) {
                    if (comment != null) {
                        bw.write(line_separator + "#" + comment + line_separator);
                    }
                    bw.write(property + "=" + ((value == null || value.length() == 0) ? "" : value) + line_separator);
                }
            }
            // CLEAN-UP
            try {
                bw.flush();
            } catch (Throwable ignore) {
            }
        } finally {
            Utils.closeIgnoringException(fr);
            Utils.closeIgnoringException(fw);
            Utils.closeIgnoringException(br);
            Utils.closeIgnoringException(bw);
        }


        try {
            tmpF.renameTo(f);
            tmpF.delete();
        } catch (Throwable e) {
        }// deleteOnExit()

        return propFound;
    }

    public static final void main(String[] args) throws Exception {
        // if ( addModifyPropertyFile(new File("/home/ramiro/ml.properties"), "MonaLisa.LAT", null, true) ) {
        String comment = "# Test Comment\n" + "# on two lines";
        if (addModifyPropertyFile(new File("/home/ramiro/ml_env.VRVS"), new File("/home/ramiro"), "VRVS_HOME", "\"${HOME}/VRVS\"", comment, true, true)) {
            System.out.println("\n\n Property FOUND! ");
        } else {
            System.out.println("\n\n Property NOT found! ");
        }
    }
}
