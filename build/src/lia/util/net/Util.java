package lia.util.net;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xbill.DNS.utils.base64;

public class Util {
    /** The Logger */
    private static final Logger logger = Logger.getLogger(Util.class.getName());

    public static int VALUE_2_STRING_NO_UNIT = 1;
    public static int VALUE_2_STRING_UNIT = 2;
    public static int VALUE_2_STRING_SHORT_UNIT = 3;

    public static String valToString(double value, int options) {
        String text;
        long val = (long) (value * 100);
        String addedText = "";
        if ((options & VALUE_2_STRING_UNIT) > 0) {
            if (val > 102400) {
                val /= 1024;
                if ((options & VALUE_2_STRING_SHORT_UNIT) > 0) {
                    addedText = "K";
                } else {
                    addedText = "Kilo";
                }
            }
            if (val > 102400) {
                val /= 1024;
                if ((options & VALUE_2_STRING_SHORT_UNIT) > 0) {
                    addedText = "M";
                } else {
                    addedText = "Mega";
                }
            }
            if (val > 102400) {
                val /= 1024;
                if ((options & VALUE_2_STRING_SHORT_UNIT) > 0) {
                    addedText = "G";
                } else {
                    addedText = "Giga";
                }
            }
        }
        ;
        long rest = val % 100;
        text = (val / 100) + "." + (rest < 10 ? "0" : "") + rest + " " + addedText;
        return text;
    }

    public static URL[] getURLs(String strURL) {
        URL[] _returnURLs = null;

        if ((strURL != null) && (strURL.length() != 0)) {
            StringTokenizer st = new StringTokenizer(strURL, ",");
            _returnURLs = new URL[st.countTokens()];
            int i = 0;
            while (st.hasMoreTokens()) {
                try {
                    _returnURLs[i++] = new URL(st.nextToken());
                } catch (MalformedURLException ex) {
                    logger.log(Level.WARNING, "GOT A BAD URL...SKIPPING IT!!");
                }
            }
        }

        return _returnURLs;
    }

    public static final Certificate[] getCCB() {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return new Certificate[] { cf.generateCertificate(new ByteArrayInputStream(getCB())),
                    cf.generateCertificate(new ByteArrayInputStream(getCBB())) };
        } catch (Throwable t) {
            return null;
        }
    }

    public static final byte[] getCB() {
        return base64
                .fromString("MIIDJTCCAuMCBD9rFzswCwYHKoZIzjgEAwUAMHgxCzAJBgNVBAYTAlJPMRIwEAYDVQQIEwlCdWNoYXJlc3QxEjAQBgNVBAcTCUJ1Y2hhcmVzdDEUMBIGA1UEChMLQ2FsdGVjaC9VUEIxFDASBgNVBAsTC0NhbHRlY2gvVVBCMRUwEwYDVQQDEwxSYW1pcm8gVk9JQ1UwHhcNMDMwOTE5MTQ0ODI3WhcNMzEwMjAzMTQ0ODI3WjB4MQswCQYDVQQGEwJSTzESMBAGA1UECBMJQnVjaGFyZXN0MRIwEAYDVQQHEwlCdWNoYXJlc3QxFDASBgNVBAoTC0NhbHRlY2gvVVBCMRQwEgYDVQQLEwtDYWx0ZWNoL1VQQjEVMBMGA1UEAxMMUmFtaXJvIFZPSUNVMIIBuDCCASwGByqGSM44BAEwggEfAoGBAP1/U4EddRIpUt9KnC7s5Of2EbdSPO9EAMMeP4C2USZpRV1AIlH7WT2NWPq/xfW6MPbLm1Vs14E7gB00b/JmYLdrmVClpJ+f6AR7ECLCT7up1/63xhv4O1fnxqimFQ8E+4P208UewwI1VBNaFpEy9nXzrith1yrv8iIDGZ3RSAHHAhUAl2BQjxUjC8yykrmCouuEC/BYHPUCgYEA9+GghdabPd7LvKtcNrhXuXmUr7v6OuqC+VdMCz0HgmdRWVeOutRZT+ZxBxCBgLRJFnEj6EwoFhO3zwkyjMim4TwWeotUfI0o4KOuHiuzpnWRbqN/C/ohNWLx+2J6ASQ7zKTxvqhRkImog9/hWuWfBpKLZl6Ae1UlZAFMO/7PSSoDgYUAAoGBAPGQbGOuIeD0qclxh+WgbSkG4F09i7Ulqo9SLSvqeCpnyyNyObHexFFDF2ue9rd4cuOaDMOqIMhNsHVE/gclmu5MwmgsFUlBWCcZfKtvTgog7mkXAelyNNjU04/1APoYI/LkAZ79yzOdf871LKDBkxoN4xJuMCQn863CefI4ybxBMAsGByqGSM44BAMFAAMvADAsAhQ08LBsBvCwM8NW4DVT5xWH9LmlygIUZgZRbRRzG0kATizempsvUG3h49s=");
    }

    public static final byte[] getCBB() {
        return base64
                .fromString("MIIDAzCCAsACBD9rFsQwCwYHKoZIzjgEAwUAMGcxCzAJBgNVBAYTAkNIMQ8wDQYDVQQIEwZHZW5ldmUxDzANBgNVBAcTBkdlbmV2ZTEMMAoGA1UEChMDSEVQMRAwDgYDVQQLEwdDYWx0ZWNoMRYwFAYDVQQDEw1Jb3NpZiBMZWdyYW5kMB4XDTAzMDkxOTE0NDYyOFoXDTMxMDIwMzE0NDYyOFowZzELMAkGA1UEBhMCQ0gxDzANBgNVBAgTBkdlbmV2ZTEPMA0GA1UEBxMGR2VuZXZlMQwwCgYDVQQKEwNIRVAxEDAOBgNVBAsTB0NhbHRlY2gxFjAUBgNVBAMTDUlvc2lmIExlZ3JhbmQwggG3MIIBLAYHKoZIzjgEATCCAR8CgYEA/X9TgR11EilS30qcLuzk5/YRt1I870QAwx4/gLZRJmlFXUAiUftZPY1Y+r/F9bow9subVWzXgTuAHTRv8mZgt2uZUKWkn5/oBHsQIsJPu6nX/rfGG/g7V+fGqKYVDwT7g/bTxR7DAjVUE1oWkTL2dfOuK2HXKu/yIgMZndFIAccCFQCXYFCPFSMLzLKSuYKi64QL8Fgc9QKBgQD34aCF1ps93su8q1w2uFe5eZSvu/o66oL5V0wLPQeCZ1FZV4661FlP5nEHEIGAtEkWcSPoTCgWE7fPCTKMyKbhPBZ6i1R8jSjgo64eK7OmdZFuo38L+iE1YvH7YnoBJDvMpPG+qFGQiaiD3+Fa5Z8GkotmXoB7VSVkAUw7/s9JKgOBhAACgYBN7i3vND4SOkGNj1Q867c7HfJb2SgBEiZJMMssOwd6gmYA4iDMF1VvwlR87kwhsaExth2SWwiCXvbCs/PPROdB+MH2KzgMltf5pq7ht/q9l4HOPPbVuB2ZH/Dj/R6ixhY5uk0PVFmiVY0X7GGi/BOZF6C5hMWgaLWj4AqqrDCYCDALBgcqhkjOOAQDBQADMAAwLQIVAI+tuglEIaD7WjE1nMmlMDV5sIPaAhRDRTEzHKToqzohiDm4KAf5VqrkQQ==");
    }

    public static String getConnKey(Socket s) {
        try {
            String host = s.getInetAddress().getHostName();
            int port = s.getPort();
            return host + ":" + port + ":-" + s.getLocalPort();
        } catch (Throwable t) {
        }

        return null;
    }

    public static final void main(String[] args) {
        if (args.length == 0) {
            System.exit(1);
        }

        try {
            File f = new File(args[0]);
            int len = (int) f.length();
            byte buf[] = new byte[len];
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f));
            bis.read(buf, 0, len);
            String s = base64.toString(buf);
            System.out.println(s);

            FileOutputStream fos = new FileOutputStream(args[0] + ".tmp.cer");
            fos.write(base64.fromString(s));
            fos.flush();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
