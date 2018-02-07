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
public class ConfigFileGeneratorNew {

    public static void main(String[] args) {
        //creates a file based on jars found in directory given as parameter
        String sDir = System.getProperty("extra_images_location");
        System.out.println("Reading system property extra_images_location=\""+sDir+"\"");
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
                    String sCurrentDirInJar = "/";//current directory in this jar, starts from / because any file's dir in jar has as prefix a /
                    String sFileDir, sFileName;
                    StringBuilder sNewFileDir;
                    int nPos=-1;
	                for( Enumeration en=jarFile.entries(); en.hasMoreElements(); ) {
	                    JarEntry jarEntry = (JarEntry)en.nextElement();
	                    if ( !jarEntry.isDirectory() && !jarEntry.getName().startsWith("META-INF")) {
                            //System.out.println("current dir: "+sCurrentDirInJar);
                            //get file in jar's name and parse it to find directory name and file name
                            nPos = jarEntry.getName().lastIndexOf('/');
                            if ( nPos!=-1 ) {
                                sFileDir = jarEntry.getName().substring(0, nPos+1);//put also the /
                                sFileName = jarEntry.getName().substring(nPos+1);//this can't be after string because is not directory
                            } else {
                                sFileDir = "";
                                sFileName = jarEntry.getName();
                            }
                            if ( !sFileDir.startsWith("/") )
                                sFileDir = "/"+sFileDir;
                            //check current Directory name compared to this file directory name
                            //form new file name
                            //get comon, root part of currentDir and FileDir
                            int startPos = 0;//at 0 there is an /
                            int nextPosCD, nextPosFD;
                            boolean bOk = true;
                            while ( bOk ) {
                                nextPosCD  = sCurrentDirInJar.indexOf( '/', startPos+1);
                                //System.out.println(sCurrentDirInJar+".indexOf( "+startPos+"+1, '/')="+nextPosCD);
//                                if ( nextPosCD==-1 ) {//this should not be the case because the path should end with /
//                                    nextPosCD = sCurrentDirInJar.length();
//                                    bOk = false;
//                                };
                                nextPosFD = sFileDir.indexOf( '/', startPos+1);
                                //System.out.println(sFileDir+".indexOf( "+startPos+"+1, '/')="+nextPosFD);
//                                if ( nextPosFD==-1 ) {//this should not be the case because the path should end with /
//                                    nextPosFD = sFileDir.length();
//                                    bOk = false;
//                                };
                                //detect difference between directories names
//                                if ( nextPosCD!=-1 && nextPosFD!=-1 ) {
//                                    System.out.println("sCurrentDirInJar.substring(startPos, nextPosCD)="+sCurrentDirInJar.substring(startPos, nextPosCD)+"\nsFileDir.substring(startPos, nextPosFD)="+sFileDir.substring(startPos, nextPosFD));
//                                }
                                if ( nextPosCD==-1 || nextPosFD==-1 || nextPosCD!=nextPosFD || !sCurrentDirInJar.substring(startPos, nextPosCD).equals(sFileDir.substring(startPos, nextPosFD)) )
                                    bOk = false;
                                else
                                    startPos = nextPosCD;// == nextPosFD
                            }//the root starts at 0 and ends at startPos including it (see directory format)
                            //String sRootDir = sCurrentDirInJar.substring(0, startPos+1);//to include 
                            //the character at startPos, that is /
                            //the substring function can be called on any of sCurrentDir or sFileDir
                            //2. check to see the new path to be written and set the new current directory
                            sNewFileDir=new StringBuilder();
                            //add ../ for each remaining directory in current directory path
                            nextPosCD = startPos;
                            bOk = true;
                            while( bOk ) {
                                nextPosCD = sCurrentDirInJar.indexOf( '/', nextPosCD+1);
                                if ( nextPosCD==-1 )
                                    bOk = false;
                                else
                                    sNewFileDir.append("../");
                            };
                            //append remaining path from file directory
                            sNewFileDir.append(sFileDir.substring(startPos+1));//can be empty
                            //do a trick: if obtained new path is greather than initial path, use that instead
                            if ( sNewFileDir.length() > sFileDir.length() )
                                sNewFileDir = new StringBuilder(sFileDir);
                            //System.out.println("new file dir = "+sNewFileDir);
                            //update current path, use file dir object as it is not used by sNewFileDir
                            sCurrentDirInJar = sFileDir;
                            //append file name
                            sNewFileDir.append(sFileName);
	                        sFiles+=sNewFileDir+"\n";
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
