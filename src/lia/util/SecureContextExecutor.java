package lia.util;

import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.Subject;

import lia.Monitor.JiniSerFarmMon.MLLogin;
import lia.Monitor.monitor.AppConfig;

public class SecureContextExecutor {

    /** Logger used by this class */
    private static final Logger logger = Logger.getLogger(SecureContextExecutor.class.getName());

    /** if useSecureLUSs is set register in a secure fashion in LUSs */
    public boolean bUseSecureLUSs = false;

    /** Subject used to do various actions in a privilleged manner */
    private Subject subject = null;

    private static SecureContextExecutor _theInstance = null;

    /** 
     * First time when this class is created, it will try to load the 
     * credentials based on the properties given by user in the configuration file. 
     */
    private SecureContextExecutor() throws Exception {
        bUseSecureLUSs = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.useSecureLUSs", "false")).booleanValue();
        if (bUseSecureLUSs) {
            loadJiniRegistrationCredentials();
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "[SEC_REGISTRATION] Using credentials to register in LUSs: " + subject);
            }
        }
    }

    /** get the single instance of this class */
    public synchronized static SecureContextExecutor getInstance() throws Exception {
        if (_theInstance == null) {
            _theInstance = new SecureContextExecutor();
        }
        return _theInstance;
    }

    /** 
     * If secure mode was established successfully the given action will be
     * executed within an access control context, given by the loaded credentials.
     * Otherwise, the action will be simply run.
     */
    public Object execute(final PrivilegedExceptionAction action) throws Exception {
        if (subject != null) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Executing the given task withn the access control context...");
            }

            return Subject.doAsPrivileged(subject, new PrivilegedExceptionAction() {
                @Override
                public Object run() throws Exception {
                    return action.run();
                };
            }, null);
        } else { // no credentials supplied, no AccessControlContext supplied
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "Simply executing the given task (NO ACCESS CONTROL CONTEXT)...");
            }
            return action.run();
        }
    }

    /**
     * Load the credentials (subject) needed to perform Jini registration/update actions. 
     */
    private void loadJiniRegistrationCredentials() throws Exception {
        try {
            /*
             * set trustStore to empty string to accept any certificate in a
             * SSL session (we are a client and don't authenticate the
             * server (LUS)
             */
            System.setProperty("net.jini.jeri.ssl.trust_any", "true");

            // get source for credential
            String credentialSource = AppConfig.getProperty("lia.Monitor.login.sourceForKeys", "scripts");
            MLLogin serviceCredentials = new MLLogin();

            if (credentialSource.equalsIgnoreCase("files")) {
                // gather private key and certificate chain from files
                String privateKeyPath = AppConfig.getProperty("lia.Monitor.login.privateKeyFile",
                        "/etc/grid-security/hostkey.pem");
                if ((privateKeyPath == null) || (privateKeyPath.length() == 0)) {
                    throw new Exception(
                            "Cannot find [lia.Monitor.login.privateKeyFile] entry in ml.properties oe entry is null");
                }

                String certChainPath = AppConfig.getProperty("lia.Monitor.login.certChainFile",
                        "/etc/grid-security/hostcert.pem");

                if ((certChainPath == null) || (certChainPath.length() == 0)) {
                    throw new Exception(
                            "[SEC_REGISTRATION] Cannot find [lia.Monitor.login.certChainFile] entry in ml.properties");
                }

                // use relative path?
                boolean useRelativePath = Boolean.valueOf(
                        AppConfig.getProperty("lia.Monitor.login.relativePath", "true")).booleanValue();

                if (useRelativePath) {
                    String FarmHOME = AppConfig.getProperty("lia.Monitor.Farm.HOME", null);
                    certChainPath = FarmHOME + "/" + certChainPath;
                    privateKeyPath = FarmHOME + "/" + privateKeyPath;
                }

                logger.log(Level.FINEST, "[SEC_REGISTRATION] Loading credentials from files\n\t" + privateKeyPath
                        + "\n\t" + certChainPath);
                /*
                 * create subject
                 */
                serviceCredentials.login(privateKeyPath, null, certChainPath);

            } else if (credentialSource.equalsIgnoreCase("scripts")) {

                StringBuilder scriptsPath = new StringBuilder();

                String farmHome;
                if ((farmHome = System.getProperty("MonaLisa_HOME", null)) == null) {
                    throw new Exception("Cannot find [MonalisaHOME] property");
                }
                scriptsPath.append(farmHome);
                scriptsPath.append("/Service/CMD/");

                String privateKeyScript = AppConfig.getProperty("lia.Monitor.login.privateKeyScript",
                        "getPrivateKey.sh");
                if ((privateKeyScript == null) || (privateKeyScript.length() == 0)) {
                    throw new Exception(
                            "[SEC_REGISTRATION] Cannot find [lia.Monitor.login.privateKeyScript] entry in ml.properties");
                }

                String certChainScript = AppConfig.getProperty("lia.Monitor.login.certChainScript", "getCertsChain.sh");
                if ((certChainScript == null) || (certChainScript.length() == 0)) {
                    throw new Exception(
                            "[SEC_REGISTRATION] Cannot find [lia.Monitor.login.certChainScript] entry in ml.properties");
                }

                boolean useSudo = Boolean.valueOf(AppConfig.getProperty("lia.Monitor.login.useSudoScripts", "true"))
                        .booleanValue();

                List privateKeyCmd = new ArrayList();
                List certsChainCmd = new ArrayList();
                privateKeyCmd.add(scriptsPath.toString() + privateKeyScript);
                certsChainCmd.add(scriptsPath.toString() + certChainScript);

                if (useSudo) {
                    // insert "sudo" at the head if we are using sudo to run
                    // scripts
                    privateKeyCmd.add(0, "sudo");
                    certsChainCmd.add(0, "sudo");
                }

                String[] privateKeyCommand = (String[]) privateKeyCmd.toArray(new String[privateKeyCmd.size()]);
                String[] certsChainCommand = (String[]) certsChainCmd.toArray(new String[certsChainCmd.size()]);

                serviceCredentials.login(privateKeyCommand, null, certsChainCommand);

            } else {
                throw new Exception("Credential source cannot be:" + credentialSource);
            }

            // init the service in the authenticated context
            subject = serviceCredentials.getSubject();
            logger.log(Level.FINER, "[SEC_REGISTRATION] Credentials successfully loaded: " + subject);
            bUseSecureLUSs = true;
        } catch (Throwable t) {
            subject = null;
            String msg = "[SEC_REGISTRATION] FAILED to load credentials";
            logger.log(Level.SEVERE, msg, t);
            throw new Exception(msg, t);
        }
    }
}
