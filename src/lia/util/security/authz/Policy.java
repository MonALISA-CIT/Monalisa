/*
 * @(#)Configuration.java 1.0 02/16/2005
 * 
 * Copyright 2005 California Institute of Technology
 */

package lia.util.security.authz;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.util.DateFileWatchdog;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import com.bluecast.xml.Piccolo;

public class Policy implements Observer {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(Policy.class.getName());

    /* read-only policy s list */
    private TreeMap<String, GroupACL> lGroupsRO;
    /* temp list */
    private final TreeMap<String, GroupACL> lGroupsW;
    private final ReentrantLock exchangeLock = new ReentrantLock();
    /** The policy file name. */
    private String sFile = null;
    private DateFileWatchdog dfw;

    /**
     * The only constructor for this class.
     * 
     * @param sConfigFile
     *            the configuration file name
     * @throws SAXException
     */
    private Policy(String sPolicyFile) throws IOException, SAXException {
        sFile = sPolicyFile;
        lGroupsW = new TreeMap<String, GroupACL>();
        loadGroupsPolicy();

        try {
            dfw = DateFileWatchdog.getInstance(this.sFile, 5 * 1000);
        } catch (Exception e) {// I don;t like Exception, please be more
            // explicit when throwing an Exception :P
            throw new IOException(e.getMessage());
        }
        dfw.addObserver(this);
    }

    static Policy me = null;

    public static synchronized Policy getInstance(String sPolicyFile) throws IOException, SAXException {
        if (me == null) {
            me = new Policy(sPolicyFile);
        }
        return me;
    }

    /**
     * Read the contents of the file in the memory for independent parsing
     * relative to changes in the file contents.
     * 
     * @return the <code>String</code> with the whole content of the file
     */
    private String getFileContents(String sLFile) throws IOException {
        final StringBuilder sb = new StringBuilder();

        final BufferedReader br = new BufferedReader(new FileReader(sLFile));
        String sLine;

        try {
            while ((sLine = br.readLine()) != null) {
                sb.append(sLine).append('\n');
            }
        } catch (IOException ioe) {
        } finally {
            try {
                br.close();
            } catch (IOException ioe2) {
            }
        }

        // logger.log(Level.INFO,sb.toString());
        return sb.toString();
    }

    public void loadGroupsPolicy() throws SAXException, IOException {
        // prepare to load the policies
        lGroupsW.clear();
        Piccolo p = new Piccolo();
        // parser
        p.setContentHandler(new ConfigContentHandler());
        p.parse(new InputSource(new StringReader(getFileContents(sFile))));
        if (logger.isLoggable(Level.INFO)) {
            /*
             * logger.log(Level.INFO, "Policy file succesfully loaded:" +
             * lGroupsRO);
             */
            StringBuilder sb = new StringBuilder();
            for (GroupACL c : lGroupsRO.values()) {
                sb.append("\nGroup:" + c);
            }
            logger.log(Level.INFO, sb.toString());
        }

    }

    /**
     * The actual parser of the XML configuration file.
     */
    class ConfigContentHandler implements org.xml.sax.ContentHandler {
        String sKey = "";
        // String sParent = "";

        GroupACL grCrtGroup = null;
        AclEntry crtAclEntry = null;

        // MLPermission mlPermission;

        @Override
        public void endDocument() {
            // finnaly, exchange the newly created list
            exchangeLock.lock(); // block until condition holds
            try {
                // not necessary but sometimes this helps the GC
                lGroupsRO = null;
                // update reference to the newly created list
                lGroupsRO = lGroupsW;
            } finally {
                exchangeLock.unlock();
            }

        }

        @Override
        public void characters(char[] ch, int start, int length) {
        }

        @Override
        public void endElement(java.lang.String uri, java.lang.String localName, java.lang.String qName) {
        }

        @Override
        public void endPrefixMapping(java.lang.String prefix) {
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) {
        }

        @Override
        public void processingInstruction(java.lang.String target, java.lang.String data) {
        }

        @Override
        public void setDocumentLocator(Locator locator) {
        }

        @Override
        public void skippedEntity(java.lang.String name) {
        }

        @Override
        public void startDocument() {
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) {

            sKey = localName;

            if (sKey.equals("group")) {
                // i.e <group name="GROUP2" credentials_type="X509">
                String sGrName = Format.decode(atts.getValue("name"));

                if (lGroupsW.containsKey(sGrName)) {
                    grCrtGroup = lGroupsW.get(sGrName);
                } else {
                    grCrtGroup = new GroupACL(sGrName);
                    lGroupsW.put(sGrName, grCrtGroup);
                }

            }
            if (sKey.equals("subject")) {

                String sSubjectName = Format.decode(atts.getValue("name"));
                crtAclEntry = new AclEntry(sSubjectName);
                grCrtGroup.addEntry(crtAclEntry);
                System.out.println();
            }

        }

        @Override
        public void startPrefixMapping(@SuppressWarnings("unused") java.lang.String prefix,
                @SuppressWarnings("unused") java.lang.String uri) {
        }
    }

    // get the the policy chain, may be null
    public TreeMap<String, GroupACL> getGroupsPolicy() {
        return this.lGroupsRO;
    }

    public TreeMap<String, Boolean> getSubjectPermissions(String subject) {

        if ((subject == null) || (subject.length() == 0)) {
            return null;
        }
        TreeMap<String, Boolean> overallPermissions = new TreeMap<String, Boolean>();
        Boolean userGroupPermission;

        TreeMap<String, GroupACL> groupACLs = this.lGroupsRO;

        // filter the acl entries refferubg
        for (Map.Entry<String, GroupACL> entry : groupACLs.entrySet() /*
                                                                         * String
                                                                         * group :
                                                                         * groupACLs.keySet()
                                                                         */) {
            // chain.getPermissions(subject)
            // userGroupPermission =
            // groupACLs.get(group).getSubjectPermissions(subject);
            userGroupPermission = entry.getValue().getSubjectPermissions(subject);
            overallPermissions.put(entry.getKey(), userGroupPermission);
        }
        return overallPermissions;
    }

    /**
     * @see java.util.Observer#update(java.util.Observable, java.lang.Object)
     */
    @Override
    public void update(Observable o, Object arg) {

        if ((dfw != null) && (o != null) && o.equals(dfw)) {
            try {
                System.out.print("Reload policy.......");
                loadGroupsPolicy();
            } catch (SAXException e) {
                System.err.print("Failed!\n");
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "Failed to reload policy file (parsing error)", e);
                }
            } catch (IOException e) {
                System.out.print("Failed!\n");
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "Failed to reload policy file (I/O error)", e);
                }
            }
            System.out.print("OK!\n");
        }
    }

}
