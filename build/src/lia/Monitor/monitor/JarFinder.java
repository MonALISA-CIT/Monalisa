package lia.Monitor.monitor;

import java.util.Enumeration;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Finds all the classes that are packed in the same jar file
 * and implement the desired interface
 */

public class JarFinder {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(JarFinder.class.getName());

    Vector<String> modules;

    /**
     * Constructor
     * 
     * @param toSearch
     *            - desired interface
     * @param jarFile
     *            - the jar file
     */
    public JarFinder(String toSearch, String jarFile) {
        this.toSearch = toSearch;
        this.jarFile = jarFile;
        modules = new Vector<String>();
    }

    // Checks if the class implements the interface
    private boolean isImplementingInterface(Object o) {
        Class c = o.getClass();
        Class[] inter = c.getInterfaces();

        String iname;
        if (inter != null && inter.length > 0) {
            for (int i = 0; i < inter.length; i++) {
                iname = inter[i].getName();
                if (toSearch.equals(iname)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isImplementingInterface(Class c) {
        Class[] inter = c.getInterfaces();

        String iname;
        if (inter != null && inter.length > 0) {
            for (int i = 0; i < inter.length; i++) {
                iname = inter[i].getName();
                if (toSearch.equals(iname)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Gets a handle to the appropiate Class and performs checking
    private String check(String s) {
        try {
            String s1 = s.replace('/', '.');
            String s2 = s1.substring(0, s.lastIndexOf(".class"));
            String s3 = s2.substring(s.lastIndexOf("/") + 1);

            Class c = Class.forName(s2);
            if (!c.isInterface()) {
                if (isImplementingInterface(c)) {
                    return s3;
                } else {
                    return null;
                }
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Exception checking class" + s, t);
            }
        }
        return null;
    }

    // gets the classes inside the jar, unpack it and check it
    public Vector<String> searchForClasses(String dir) {
        try {
            JarFile jf = new JarFile(this.jarFile);
            Enumeration<JarEntry> e = jf.entries();
            while (e.hasMoreElements()) {
                JarEntry je = e.nextElement();
                String s = je.toString();
                if ((s.indexOf(dir) != -1) && (s.indexOf(".class") != -1)) {
                    String mod = check(s);
                    if (mod != null)
                        modules.add(mod);
                }
            }
        } catch (Throwable t) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Exception searching for classes", t);
            }
        }
        return modules;

    }

    // The interface to be searched
    private String toSearch;

    // The jar file
    private String jarFile;

}
