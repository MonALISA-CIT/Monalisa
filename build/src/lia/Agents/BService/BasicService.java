/*
 * $Id: BasicService.java 7266 2012-06-25 23:18:35Z ramiro $
 */
package lia.Agents.BService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.JiniSerFarmMon.MLLUSHelper;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MLJiniManagersProvider;
import lia.util.JiniConfigProvider;
import lia.util.MLProcess;
import lia.util.MLSignalHandler;
import lia.util.Utils;
import lia.util.ntp.NTPDate;
import net.jini.config.Configuration;
import net.jini.config.ConfigurationException;
import net.jini.core.discovery.LookupLocator;
import net.jini.core.entry.Entry;
import net.jini.core.lookup.ServiceID;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistrar;
import net.jini.core.lookup.ServiceTemplate;
import net.jini.discovery.DiscoveryEvent;
import net.jini.discovery.DiscoveryListener;
import net.jini.discovery.LookupDiscoveryManager;
import net.jini.lease.LeaseRenewalManager;
import net.jini.lookup.JoinManager;
import net.jini.lookup.ServiceDiscoveryManager;
import net.jini.lookup.ServiceIDListener;

import com.sun.jini.tool.ClassServer;


/**
 * @author Iosif Legrand
 * @author ramiro
 * @since a very long time .....
 */
public abstract class BasicService implements Remote, ServiceIDListener, DiscoveryListener {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(BasicService.class.getName());

    /** THE LookupDiscoveryManager */
    private volatile LookupDiscoveryManager LDM;

    /** THE JoinManager */
    private volatile JoinManager JMNGR;

    /** THE ServiceDiscoveryManager */
    private volatile ServiceDiscoveryManager SDM;

    protected volatile ServiceID mySid;

    private final ServiceID myOldSid;

    private static final AtomicReference<ServiceItem> serviceItemAtomicReference = new AtomicReference<ServiceItem>();

    protected ServiceTemplate myTemplate = null;

    protected Object proxy;

    protected static String WebAddress = "N/A";

    private static final String urlFS = AppConfig.getProperty("lia.Monitor.serviceidfile", "${lia.Monitor.Farm.HOME}" + File.separator + ".ml.sid");

    private static final AtomicLong restartMgrCount = new AtomicLong(0);

    // in seconds
    private static long errorTime = Long.valueOf(AppConfig.getProperty("lia.Monitor.serviceid.errortime", "10").trim()).longValue() * 1000;

    String[] groups;

    protected static void stopJVM(String cause) {

        try {
            try {
                System.out.println(cause);
                logger.log(Level.SEVERE, cause);
                System.out.flush();
                System.err.flush();
            } catch (Throwable ignore) {
                // if I cannot write ... do not throw an exception ...
            }

            try {
                MLSignalHandler mlsh = MLSignalHandler.getInstance();
                if (mlsh != null) {
                    mlsh.shutdownNow(1);
                }
            } catch (Throwable ignore) {
                // if I cannot write ... do not throw an exception ...
            }
        } finally {
            // always exit !!!!
            System.exit(1);
        }
    }

    public Object getProxy() {
        return (proxy);
    }

    /**
     * @throws RemoteException  
     */
    public BasicService() throws RemoteException {
        ServiceID localSid = null;

        try {
            localSid = read();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ BasicService ] [ HANDLED ] Exception reading the SIDFile: " + urlFS, t);
        }

        myOldSid = localSid;
    }// BasicService()

    abstract public Entry[] getAttributes();

    private static final void appendToStatusBuffer(StringBuilder sb, String entryToAppend) {
        sb.append("\n [ ").append(new Date(NTPDate.currentTimeMillis())).append(" ] => ").append(entryToAppend).append(" <= \n");
    }

    /**
     * @param proxy
     * @return The status of restartting Jini managers
     * @throws Exception
     */
    protected String restartJiniManagers(Object proxy) {
        restartMgrCount.incrementAndGet();
        // Do not let other Threads to use the managers
        MLJiniManagersProvider.setManagers(null, null, null);

        StringBuilder sb = new StringBuilder(4096);
        appendToStatusBuffer(sb, "restartJiniManagers() STATUS:");
        try {
            if (JMNGR != null) {
                appendToStatusBuffer(sb, "JoinManager calling terminate()");
                try {
                    JMNGR.terminate();
                } catch (Throwable t) {
                    appendToStatusBuffer(sb, " Got exc jmngr.terminate()\n" + Utils.getStackTrace(t));
                }
                appendToStatusBuffer(sb, "JoinManager terminate()-d");
                JMNGR = null;
            }

            if (LDM != null) {
                appendToStatusBuffer(sb, "LDM calling terminate()");
                try {
                    LDM.terminate();
                } catch (Throwable t) {
                    appendToStatusBuffer(sb, "Got exc ldm.terminate()\n" + Utils.getStackTrace(t));
                }
                appendToStatusBuffer(sb, "LDM calling terminate()-d");
                LDM = null;
            }

            if (SDM != null) {
                appendToStatusBuffer(sb, "SDM calling terminate()");
                try {
                    SDM.terminate();
                } catch (Throwable t) {
                    appendToStatusBuffer(sb, "Got exc sdm.terminate()\n" + Utils.getStackTrace(t));
                }
                appendToStatusBuffer(sb, "SDM calling terminate()-d");
                SDM = null;
            }

            appendToStatusBuffer(sb, "Trying to sleep() for a few seconds ...");

            try {
                Thread.sleep(2 * 1000);
            } catch (Exception e) {
            }
            appendToStatusBuffer(sb, " sleep() finished ... Trying init()");

            try {
                init(proxy);
            } catch (Throwable t) {
                appendToStatusBuffer(sb, "Got exception in init(proxy) \n" + Utils.getStackTrace(t));
            }
        } catch (Throwable t) {
            appendToStatusBuffer(sb, "restartJiniManagers() got Exception \n" + Utils.getStackTrace(t));
        }

        MLJiniManagersProvider.setManagers(LDM, SDM, JMNGR);
        appendToStatusBuffer(sb, "END Init MLJiniHelpers!!");

        return sb.toString();
    }

    private String initBasicJiniManagers(Configuration config) throws IOException {
        StringBuilder sb = new StringBuilder();
        LookupLocator[] luss = Utils.getLUDSs(AppConfig.getProperty("lia.Monitor.LUSs"));
        sb.append("\n\nUsing LUSs: ");
        if (luss == null || luss.length == 0) {// normaly we should have DEFAULT_LUSs
            stopJVM(" [ BasicService ] [ initBasicJiniManagers ] Unable to determine lookup hosts LUSs[] == null!? Please check that the property lia.Monitor.LUSs is defined in your ml.properties file");
        } else {
            for (int i = 0; i < luss.length; i++) {
                final LookupLocator locator = luss[i];
                sb.append("\nLUS " + i + ": " + locator.getHost());
            }
        }
        String mgroup = AppConfig.getProperty("lia.Monitor.group", "test");

        if (mgroup == null || mgroup.length() == 0) {
            mgroup = "test";
        }

        sb.append("\nUsing group(s): " + mgroup + " [ ");
        groups = Utils.getSplittedListFields(mgroup);

        for (int gsi = 0; gsi < groups.length; gsi++) {
            sb.append(groups[gsi]);
            sb.append((gsi < groups.length - 1) ? "," : " ] \n");
        }

        logger.log(Level.INFO, sb.toString());
        try {
            LDM = new LookupDiscoveryManager(groups, luss, this, config);
        } catch (ConfigurationException cex) {
            logger.log(Level.WARNING, "Got LDM Config Exception ex", cex);
            LDM = null;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Got LDM General Config Exception ex", t);
            LDM = null;
        }

        if (LDM == null) {
            logger.log(Level.WARNING, "Cannot init LookupDiscoveryManager using configuration " + config + " ... Using <null> config");
            LDM = new LookupDiscoveryManager(groups, luss, this);
        }

        try {
            try {
                SDM = new ServiceDiscoveryManager(LDM, new LeaseRenewalManager(), config);
            } catch (ConfigurationException cex) {
                logger.log(Level.WARNING, "Got SDM Config Exception ex", cex);
                SDM = null;
            } catch (Throwable t1) {
                SDM = null;
            }
        } catch (Throwable t) {
            appendToStatusBuffer(sb, "Got exception in Init MLJiniHelpers \n\n " + Utils.getStackTrace(t));
        }

        MLJiniManagersProvider.setManagers(LDM, SDM, null);

        return sb.toString();
    }

    public static final long getRestartMgrCount() {
        return restartMgrCount.get();
    }

    public void init(Object proxy) throws IOException {

        logger.log(Level.INFO, "[ BasicService ] Started registration in the LUSs...");

        initBasicJiniManagers(JiniConfigProvider.getUserDefinedConfig());

        Entry[] attrSet = getAttributes();
        verifyValidGroups();

        ServiceID sid = null;

        if (myOldSid != null || mySid != null) {
            if (myOldSid == null) {
                sid = mySid;
            } else {
                sid = myOldSid;
            }

        }

        try {
            if (sid == null) {
                logger.log(Level.INFO, "Subscribing for a new SID...");
                JMNGR = new JoinManager(proxy, attrSet, this, LDM, null, JiniConfigProvider.getUserDefinedConfig());
            } else {
                logger.log(Level.INFO, "Subscribing for a previous known SID ... [ " + sid + " ]");
                JMNGR = new JoinManager(proxy, attrSet, sid, LDM, null, JiniConfigProvider.getUserDefinedConfig());
            }
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Exception creating JoinManager", t);
            return;
        }

        for (int i = 0; i < 100; i++) {
            if (mySid != null)
                break;
            if (sid != null) {
                ServiceItem[] sis = MLLUSHelper.getInstance().getServiceItemBySID(sid);
                if (sis != null && sis.length > 0) {
                    if (sis[0].serviceID.equals(myOldSid)) {
                        mySid = sid;
                        serviceItemAtomicReference.set(sis[0]);
                        break;
                    }
                }
            }
            try {
                Thread.sleep(1000);
            } catch (Throwable t) {
            }

            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, ".....watting for sid ...");
            }
        }

        if (mySid == null) {

            stopJVM("\n\n Unable to register the ML service in the Lookup Discover Services (LUS-s)!" + "\n You need only outgoing TCP connectivity for these ports/systems:\n" + "\n monalisa.cern.ch  and monalisa.cacr.caltech.edu TCP ports: 80, 4160, 8765, 6001, 6002, 6003"
                    + "\n monalisa2.cern.ch, monalisa.caltech.edu TCP ports: 6001, 6002, 6003\n" + "\n If the HTTP connectivity at your site (TCP port 80 to monalisa.cern.ch and monalisa.cacr.caltech.edu) is using an http proxy"
                    + "\n you can specify the host/port for the proxy in Service/CMD/ml_env configuration file" + "\n Please uncomment the following property JAVA_OPTS=\"-Dhttp.proxyHost=x.x.x.x -Dhttp.proxyPort=3128\" \n"
                    + "\n Please also check the Service Installation Guide from MonALISA web site: http://monalisa.caltech.edu/monalisa__Documentation__Service_Installation_Guide.html\n" + "\n If you still have problems, please remove the old logs (*.log),"
                    + "\n try to start the service again and send an archive with the log files to support@monalisa.cern.ch.\n Thank you!\n" + "\n ML Service will stop now!\n" + "\n\n");

        }

        for (int i = 0; i < 100; i++) {
            ServiceItem[] sis = MLLUSHelper.getInstance().getServiceItemBySID(mySid);
            if (sis != null && sis.length > 0) {
                if (sis[0].serviceID.equals(mySid)) {
                    serviceItemAtomicReference.set(sis[0]);
                    break;
                }
            }
            try {
                Thread.sleep(1000);
            } catch (Exception e) {

            }
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, ".....watting for ServiceItem ...");
            }
        }
        
        logger.log(Level.INFO, "Service Registered! SID =" + mySid + "\n");
    }

    public static final ServiceItem getServiceItem() {
        return serviceItemAtomicReference.get();
    }

    @Override
    public void discarded(DiscoveryEvent e) {
        logger.log(Level.INFO, " LUS(s) Service(s) discared " + e);
    }

    @Override
    public void discovered(DiscoveryEvent e) {
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, " LUS Service discovered " + e.toString());
        }
        verifyValidGroups();
    }

    @Override
    public void serviceIDNotify(ServiceID serviceID) {
        mySid = serviceID;
        logger.log(Level.INFO, "\n [ seriviceIDNotify ] Received ID= " + mySid);

        if (urlFS != null) {
            try {
                save();
            } catch (Throwable t) {
                logger.log(Level.WARNING, " [ BasicService ]  Unable to save SID ", t);
            }
        }
    }

    public void verifyValidGroups() {
        try {
            boolean shouldVerifyValidGroups = false;
            try {
                shouldVerifyValidGroups = AppConfig.getb("lia.Agents.BService.BasicService.shouldVerifyValidGroups", false);
            } catch (Throwable t) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, " [ BasicService ] [ verifyValidGroups ] got exception looking for lia.Agents.BService.BasicService.shouldVerifyValidGroups", t);
                }
                shouldVerifyValidGroups = false;
            }

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, " [ BasicService ] [ verifyValidGroups ] shouldVerifyValidGroups: " + shouldVerifyValidGroups);
            }

            if (!shouldVerifyValidGroups)
                return;

            ServiceRegistrar[] srs = LDM.getRegistrars();
            LookupLocator[] lls = LDM.getLocators();

            if (srs == null || lls == null)
                return;

            StringBuilder sb = new StringBuilder();
            if (logger.isLoggable(Level.FINER)) {
                sb.append("\n\n************** Verify REGISTRATION ***********");
            }

            for (int i = 0; i < srs.length; i++) {
                ServiceRegistrar srx = srs[i];
                LookupLocator ll = null;
                try {
                    ll = srx.getLocator();
                    if (logger.isLoggable(Level.FINER)) {
                        sb.append("\n ---> I=" + i + "  LUS  =" + ll + " ");
                    }
                } catch (Throwable t) {
                    logger.log(Level.WARNING, " ---> I=" + i + " ERROR getting Registry ", t);
                }

                boolean grok = false;
                try {
                    String[] gr = srx.getGroups();
                    for (int j = 0; j < gr.length; j++) {
                        if (logger.isLoggable(Level.FINER)) {
                            sb.append(" [ " + gr[j] + " ] ");
                        }
                        for (int k = 0; k < groups.length; k++) {
                            if ((groups[k].equals(gr[j])))
                                grok = true;
                        }
                    }
                } catch (Throwable t) {
                    // This is in general a remoteException
                    // It is very possible to get this if the network goes down
                    // while verifying
                    logger.log(Level.WARNING, " [ BasicService ] General Exception. Failed to get groups! ", t);
                    grok = true;
                }
                sb.append("Gr Match=" + grok);

                if (grok == false) {
                    LookupLocator[] lldel = new LookupLocator[1];
                    if (ll == null)
                        return;
                    LookupLocator lx = getFixBug(ll, lls);
                    if (lx != null) {
                        lldel[0] = lx;
                        LDM.removeLocators(lldel);
                        LDM.discard(srx);
                        logger.log(Level.INFO, " Remove LU=" + lx);
                    }
                }
            }

            if (logger.isLoggable(Level.FINER)) {
                sb.append("\n**********************************************");
                logger.log(Level.FINER, sb.toString());
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ BasicService ] [ verifyValidGroups ] General Exception verifyValidGroups()", t);
        }
    }

    LookupLocator getFixBug(LookupLocator ll, LookupLocator[] lls) {
        try {
            for (int i = 0; i < lls.length; i++) {
                if (lls[i].equals(ll))
                    return ll;
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "=========================>Exception getFixBug()!!! " + ll.toString(), t);
            return null;
        }
        logger.log(Level.WARNING, "=========================>ERROR getFixBug!!! " + ll.toString());
        return null;
    }

    private ServiceID read() throws Exception {

        File SIDFile = null;
        ServiceID sid = null;

        try {
            try {
                SIDFile = new File(urlFS);

                if (!SIDFile.exists() || !SIDFile.canRead() || !SIDFile.canWrite() || !SIDFile.isFile()) {
                    logger.log(Level.INFO, "[ BasicService ] [ HANDLED ] Unable to read SIDFile [ " + urlFS + "] [ Exists: " + SIDFile.exists() + " isFile: " + SIDFile.isFile() + " canRead: " + SIDFile.canRead() + " canWrite: " + SIDFile.canWrite() + " ]");
                    return null;
                }

            } catch (Throwable t) {
                logger.log(Level.INFO, "[ BasicService ] [ HANDLED ] Got exception probing for SID File: " + urlFS, t);
                return null;
            }

            FileInputStream fis = null;
            ObjectInputStream ois = null;

            try {
                long lastModified = SIDFile.lastModified();

                fis = new FileInputStream(urlFS);

                ois = new ObjectInputStream(fis);

                sid = (ServiceID) ois.readObject();
                String iNodeF = (String) ois.readObject();
                Date lastWrite = (Date) ois.readObject();

                // Write i-node info
                Process p = MLProcess.exec(new String[] {
                        "ls", "-i", urlFS
                });
                InputStream is = p.getInputStream();
                String line = null;
                String iNode = null;
                BufferedReader br = null;

                try {
                    br = new BufferedReader(new InputStreamReader(is));
                    while ((line = br.readLine()) != null) {
                        if (line.indexOf(urlFS) != -1) {
                            String[] splitLine = line.split("\\s+");
                            iNode = splitLine[0];
                        }
                    }
                    p.waitFor();
                } finally {
                    if (is != null) {
                        is.close();
                    }
                    if (br != null) {
                        br.close();
                    }
                }

                long lastSIDWrite = lastWrite.getTime();
                if (lastSIDWrite == lastModified || (lastSIDWrite < lastModified && lastSIDWrite + errorTime > lastModified) || (lastSIDWrite > lastModified && lastSIDWrite - errorTime < lastModified)) {// it's
                    // OK
                    if (iNodeF != null && iNode != null && iNode.equals(iNodeF)) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE, "Got SID [ " + sid + " ] " + "from file [ " + urlFS + " ] \n" + "[ fTime = " + new Date(lastModified) + " SIDTime = " + new Date(lastSIDWrite) + " iNode = " + iNode + " ]");
                        }
                    } else {
                        sid = null;
                    }
                } else {
                    logger.log(Level.INFO, "Different SID time. [ fTime = " + new Date(lastModified) + " SIDTime = " + new Date(lastSIDWrite) + " ]");
                    sid = null;
                }
            } catch (Throwable t) {
                logger.log(Level.INFO, "Got exception trying to read SID ", t);
                sid = null;
            } finally {
                try {
                    if (ois != null) {
                        ois.close();
                    }
                } catch (Throwable t1) {
                }
                try {
                    if (fis != null) {
                        fis.close();
                    }
                } catch (Throwable t1) {
                }
            }

        } finally {
            if (sid == null) {
                try {
                    if (SIDFile != null && SIDFile.exists()) {
                        SIDFile.delete();
                    }
                } catch (Throwable t) {
                }
            }
        }

        return sid;
    }

    private void save() throws Exception {

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Trying to save() [ " + mySid + " to " + urlFS + " ] ");
        }
        if (urlFS == null)
            return;

        if (mySid == null)
            throw new Exception(" mysid == null ");

        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        boolean WRstatus = false;
        try {
            fos = new FileOutputStream(urlFS);
            oos = new ObjectOutputStream(fos);

            oos.writeObject(mySid);
            oos.flush();

            Process p = MLProcess.exec(new String[] {
                    "/bin/sh", "-c", "ls -i " + urlFS
            });
            InputStream is = null;
            BufferedReader br = null;
            String line = null;
            String iNode = null;

            try {
                is = p.getInputStream();
                br = new BufferedReader(new InputStreamReader(is));
                while ((line = br.readLine()) != null) {
                    if (line.indexOf(urlFS) != -1) {
                        String[] splitLine = line.split("\\s+");
                        iNode = splitLine[0];
                    }
                }
                p.waitFor();
            } finally {
                if (is != null) {
                    is.close();
                }

                if (br != null) {
                    br.close();
                }
            }

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "iNode = " + iNode);
            }
            if (iNode != null) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Writing iNode " + iNode);
                }
                oos.writeObject(iNode);
            }
            oos.writeObject(new Date(System.currentTimeMillis()));
            oos.flush();
            WRstatus = true;
        } catch (Throwable t) {
            logger.log(Level.INFO, "Cannot write SID to file", t);
            WRstatus = false;
        } finally {
            try {
                if (oos != null) {
                    oos.close();
                }
            } catch (Throwable t1) {
            }
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (Throwable t1) {
            }
        }

        File SIDFile = null;
        try {
            SIDFile = new File(urlFS);
            if (!SIDFile.exists() || !SIDFile.canRead() || !SIDFile.canWrite() || !SIDFile.isFile()) {
                logger.log(Level.INFO, "Problems writing SIDFile [ " + urlFS + "] [" + "[ Exists: " + SIDFile.exists() + " isFile: " + SIDFile.isFile() + " canRead: " + SIDFile.canRead() + " canWrite: " + SIDFile.canWrite() + " ]");
                return;
            }
        } catch (Throwable t) {
            logger.log(Level.INFO, "Got exception while probing for SID File", t);
            return;
        }

        if (!WRstatus) {
            boolean status = false;
            try {
                status = SIDFile.delete();
            } catch (Throwable t) {
            }
            logger.log(Level.FINE, "WRStatus == false ...trying to invalidate SIDFile [ " + urlFS + " ] DLT_STATUS = " + status);
        } else {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "Write SID ... ok!!");
            }
        }
    }

    protected static String startWeb(String jdir) {

        String webadd1 = null;
        int webPort = AppConfig.geti("lia.Monitor.webserver_port", 8488);

        String webhost = null;

        try {
            webhost = InetAddress.getLocalHost().getHostAddress();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "\n\n [ startWeb ] Failed to get HOST address \n", t);
        }

        logger.log(Level.INFO, "[ startWeb ] HOST = " + webhost);

        String forceIP = AppConfig.getProperty("lia.Monitor.useIPaddress");

        if (forceIP != null) {
            webhost = forceIP;

            logger.log(Level.INFO, "[ startWeb ] HOST FORCED TO " + webhost);
        }

        logger.log(Level.INFO, "[ startWeb ] Using HOST address: " + webhost);

        webadd1 = "http://" + webhost + ":" + webPort + "/farm_mon_dl.jar";
        WebAddress = "http://" + webhost + ":" + webPort;

        try {
            new ClassServer(webPort, jdir, true, true).start();
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[ startWeb ] Failed to start WEB SERVER ", t);
            webadd1 = null;
        }

        return webadd1;
    }

    /**
     * @throws java.rmi.RemoteException  
     */
    public Object getAdmin() throws java.rmi.RemoteException {
        // if(admin == null)
        // admin = new BasicAdministrator(this); //ToDo
        // return(admin);
        return null;
    }
}
