package lia.util.security;

import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Vector;

import javax.net.ssl.X509TrustManager;

/**
 * @author
 */
public class FarmMonitorTrustManager implements X509TrustManager {
    
    KeyStore ks = null;

    public FarmMonitorTrustManager() {
        this(null);
    }
    
	public FarmMonitorTrustManager(KeyStore ks) {
		this.ks = ks;
	}

	/**
	 * @see javax.net.ssl.X509TrustManager#checkClientTrusted(X509Certificate[], String)
	 */
	public void checkClientTrusted(X509Certificate[] arg0, String arg1)
		throws CertificateException {
	    if (arg0 == null || arg0.length == 0) throw new CertificateException();
	    try {
		    if (ks.getCertificateAlias(arg0[0]) == null) throw new CertificateException();
	    }catch(Throwable t){
	        throw new CertificateException(t.getMessage());
	    }
	}

	/**
	 * @see javax.net.ssl.X509TrustManager#checkServerTrusted(X509Certificate[], String)
	 */
	public void checkServerTrusted(X509Certificate[] arg0, String arg1)
		throws CertificateException {
	}

	/**
	 * @see javax.net.ssl.X509TrustManager#getAcceptedIssuers()
	 */
	public X509Certificate[] getAcceptedIssuers() {
        if ( ks == null ) {
            return null;
        }
        
        try {
            Vector vxcerts = new Vector();
            
            for ( Enumeration en = ks.aliases(); en.hasMoreElements(); ) {
                String alias = (String)en.nextElement();
                if ( !ks.isKeyEntry(alias) ) {
                    vxcerts.add(ks.getCertificate(alias));
                }
            }
            
            if ( vxcerts.size() > 0 ) {
            	return (X509Certificate[])vxcerts.toArray(new X509Certificate[vxcerts.size()]);
            }
        } catch ( Throwable tt ){
            
        }
        return null;
	}

}
