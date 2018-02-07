package lia.Monitor.JiniClient.CommonGUI.Jogl.util;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/*
 * Created on Mar 16, 2005
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */

/**
 *
 * Mar 16, 2005 - 5:02:41 PM
 */
public class ConfigFileGenerator {

    public static void main(String[] args) {
        //creates a file based on jars found in directory given as parameter
        String sDir = System.getProperty("extra_images_location");
        File fDir = new File(sDir);
        if ( fDir.isDirectory() ) {
            File[] jars = fDir.listFiles( new FileFilter() {
                public boolean accept(File pathname) {
                    //return only jar files
                    if ( pathname.isFile() && pathname.getName().endsWith(".jar") )
                        return true;
                    return false;
                }
            });
            if ( jars==null ) {
                System.out.println("No jars found in directory "+sDir);
                return;//no jars found
            };
            //open file to write into
            PrintWriter pw = null;
            try {
                pw = new PrintWriter(new FileOutputStream(sDir+"/config"));
            } catch (Exception ex) {
                System.out.println("Could not open file to write to.");
                ex.printStackTrace();
                return;
            };
            try {
	            for( int i=0; i<jars.length; i++) {
	                JarFile jarFile = new JarFile(jars[i]);
	                System.out.println("Parsing jar "+jars[i].getName());
					String sFiles="";
					int noFiles = 0;
	                for( Enumeration en=jarFile.entries(); en.hasMoreElements(); ) {
	                    JarEntry jarEntry = (JarEntry)en.nextElement();
	                    if ( !jarEntry.isDirectory() && !jarEntry.getName().startsWith("META-INF")) {
	                        sFiles+=jarEntry.getName()+"\n";
	                        noFiles++;
	                    };
	                }
	                //for cached jars
	                //pw.println(jars[i].getName()+" 15/09/2005-13:00:00 "+noFiles);
	                //for downloadable  jars
	                pw.println(jars[i].getName()+" "+jars[i].length()+" "+noFiles);
	                pw.println(sFiles);
	            };
            } catch (Exception ex) {
                //error on generating config file
                System.out.println("An error has occured while generating config files from jars in directory "+sDir);
                ex.printStackTrace();
            }
            pw.flush();
            pw.close();
        }
        System.out.println("app ended. Bug report at mluc@cs.pub.ro");
    }
}
