package lia.util.security;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;
import javax.security.auth.x500.X500PrivateCredential;

import lia.util.MLProcess;

import org.globus.gsi.OpenSSLKey;
import org.globus.gsi.bc.BouncyCastleOpenSSLKey;

/**
 * @date Sep 27, 2004 Login class
 */
public class MLLogin {

    /** The Logger */
    private static final Logger logger = Logger.getLogger(MLLogin.class.getName());

    private Subject subject = null;

    private java.security.cert.CertPath certPath = null;

    private X500PrivateCredential privateCredential;

    private javax.security.auth.x500.X500Principal principal;

    public MLLogin() {
        this.subject = new Subject();
    }

    public void login(String privateKeyFile, String optionalPKPwd, String certsFile) throws LoginException,
            CertificateException, IOException, InvalidKeyException {

        InputStream privateKeyIS = new BufferedInputStream(new FileInputStream(privateKeyFile));
        /*
         * DataInputStream dis = new DataInputStream(privateKey_fis); byte[]
         * bytes = new byte[dis.available()]; dis.readFully(bytes);
         * ByteArrayInputStream privateKey_bais = new
         * ByteArrayInputStream(bytes); dis.close(); privateKey_fis.close();
         */

        InputStream certsIS = new BufferedInputStream(new FileInputStream(certsFile));
        /*
         * dis = new DataInputStream(certs_fis); bytes = new
         * byte[dis.available()]; dis.readFully(bytes); ByteArrayInputStream
         * certs_bais = new ByteArrayInputStream(bytes); dis.close();
         * certs_fis.close();
         */

        this.login(privateKeyIS, optionalPKPwd, certsIS);

    }

    /**
     * @param privateKeyB -
     *                  a ByteArrayInputStream to read private key from
     * @param certsIS -
     *                  a ByteArrayInputStream to read certificate chain from
     * @throws Exception
     */
    public void login(InputStream privateKeyB, String optionalPKPwd, InputStream certsIS) throws LoginException,
            CertificateException, IOException, InvalidKeyException {

        // loading CertificateChain
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        Collection c = cf.generateCertificates(certsIS);
        Certificate[] certs = (Certificate[]) c.toArray(new Certificate[c.toArray().length]);

        if (c.size() == 1) {
            logger.log(Level.FINE, "MLLogin: reading certificates chain: 1 certificate in chain.");
        } else {
            logger.log(Level.FINE, "MLLogin: reading certificates chain: " + c.size() + "certificates in chain.");
        }

        if ((certs == null) || (certs.length == 0) || !(certs[0] instanceof X509Certificate)) {
            throw new LoginException("Unable to get X.509 certificate chain" + certs);
        } else {
            LinkedList certList = new LinkedList();
            for (Certificate cert : certs) {
                certList.add(cert);
            }
            CertificateFactory certF = CertificateFactory.getInstance("X.509");
            this.certPath = certF.generateCertPath(certList);

        }

        /* Get principal and keys */

        //first certificate is EEC
        X509Certificate certificate = (X509Certificate) certs[0];
        this.principal = new javax.security.auth.x500.X500Principal(certificate.getSubjectDN().getName());

        PrivateKey privateKey = null;
        // loading PrivateKey
        try {
            OpenSSLKey sslkey = new BouncyCastleOpenSSLKey(privateKeyB);

            if (sslkey.isEncrypted()) {
                // if encrypted use the optionalPKPwd parameter
                sslkey.decrypt(optionalPKPwd);
            }

            privateKey = sslkey.getPrivateKey();

        } catch (IOException e) {
            System.err.println("Failed to load key: " + e.getMessage());

        } catch (GeneralSecurityException e1) {
            System.err.println("Error: Wrong pass phrase");
        }

        this.privateCredential = new X500PrivateCredential(certificate, privateKey);

        privateKey = null;

        this.subject.getPrincipals().add(this.principal);
        this.subject.getPublicCredentials().add(this.certPath);
        this.subject.getPrivateCredentials().add(this.privateCredential);
        this.subject.setReadOnly();

        //clean up

        try {

            privateKeyB.close();
            certsIS.close();
        } catch (Throwable t) {
        } finally {
            privateKeyB = null;
            certsIS = null;
        }

    }//login

    public void login(String[] privateKeyExecutable, String optionalPKPassword, String[] certsExecutable)
            throws LoginException, CertificateException, IOException, InvalidKeyException {

        Process proc = MLProcess.exec(privateKeyExecutable, 5000);

        try {
            if (proc.waitFor() != 0) {
                throw new LoginException("PrivateKey script returned a non-zero value:" + proc.exitValue());
            }

        } catch (InterruptedException e) {
            throw new LoginException("Exception during read private key from file" + e);
        }

        //read err stream and check if we have an error
        ByteArrayInputStream inErr = fullStream(proc.getErrorStream());
        if (inErr.available() != 0) {
            DataInputStream dis = new DataInputStream(inErr);
            byte[] bytes = new byte[dis.available()];
            dis.readFully(bytes);
            throw new LoginException("Err stream after executing" + privateKeyExecutable + ": " + new String(bytes));
        }
        inErr = null;

        ByteArrayInputStream inPK = fullStream(proc.getInputStream());

        proc.destroy();

        proc = MLProcess.exec(certsExecutable);

        try {
            if (proc.waitFor() != 0) {
                throw new LoginException("PrivateKey script returned a non-zero value:" + proc.exitValue());
            }

        } catch (InterruptedException e) {
            throw new LoginException("Exception during read private key from file" + e);
        }

        //read err stream and check if we have an error
        ByteArrayInputStream inErr1 = fullStream(proc.getErrorStream());
        if (inErr1.available() != 0) {
            DataInputStream dis = new DataInputStream(inErr1);
            byte[] bytes = new byte[dis.available()];
            dis.readFully(bytes);
            throw new LoginException("Err stream after executing " + certsExecutable + ": " + new String(bytes));
        }
        inErr1 = null;

        ByteArrayInputStream inCerts = fullStream(proc.getInputStream());

        proc = null;

        this.login(inPK, optionalPKPassword, inCerts);

    }

    /**
     * clean the current user credentials
     */
    public void logout() {
        this.privateCredential = null;
        this.certPath = null;
        this.principal = null;
        this.subject = null;
    }

    /**
     * @return the Subject object
     * @throws LoginException
     */
    public Subject getSubject() throws LoginException {

        if (subject == null) {
            throw new LoginException("Subject is not initialized");
        }

        return this.subject;
    }

    private static ByteArrayInputStream fullStream(InputStream is) throws IOException {

        DataInputStream dis = new DataInputStream(is);
        byte[] bytes = new byte[dis.available()];
        dis.readFully(bytes);
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        dis.close();
        return bais;
    }

    //DEBUG
    public static void main(String args[]) {

        // Execute a command with an argument that contains a space
        String[] commands = new String[] { "/bin/bash",
                "/home/adi/ML_NEW/MSRC/MonaLisa/Service/CMD/getCertsChain.sh.ui" };
        String[] commands1 = new String[] { "/bin/bash",
                "/home/adi/ML_NEW/MSRC/MonaLisa/Service/CMD/getPrivateKey.sh.ui" };

        MLLogin auth = new MLLogin();

        Subject subject = null;
        try {
            auth.login(commands1, null, commands);
            subject = auth.getSubject();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (LoginException e) {
            e.printStackTrace();
        } catch (InvalidKeyException i) {
            i.printStackTrace();
        } finally {
            if (subject != null) {
                System.out.println("Login succesfull" + subject.toString());
            } else {
                System.out.println("Login failed, subject = null");
            }
        }

    }
}