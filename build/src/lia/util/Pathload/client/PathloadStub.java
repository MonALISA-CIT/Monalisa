/**
 * 
 */
package lia.util.Pathload.client;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lia.util.Pathload.util.PathloadClient;
import lia.util.Pathload.util.PathloadException;

/**
 * This stub Class is used to connect to the main servlet.
 * [CODE-REVIEW]
 * PeerInfo
 * 
 * @author heri
 *
 */
public class PathloadStub implements PathloadClient {
    /**
     * Logging component
     */
    private static final Logger logger = Logger.getLogger(PathloadStub.class.getName());

    /**
     * The URL of the Pathload Servlet
     *
    public final static String PATHLOAD_CONFIG_URL = AppConfig.getProperty(
    		"lia.util.Pathload.client.PathloadConnector");
    		*/

    /**
     * Reserved for future use. 
     * Pathload message version to the servlet
     */
    public final static String PATHLOAD_MESSAGE_VER = "6";

    /**
     * The URL is created only once, this is the url of the
     * Configuration Servlet
     */
    private URL url;

    /**
     * A part of the info paramters in the Servlet request
     * stays the same, this is that part
     */
    private final String data;

    /**
     * Regex Patterns should not be compiled each time
     */
    private final Pattern pattern;

    /**
     * Default constructor
     * 
     * @param hostname	Hostname of peer
     * @param ipAddress	Host address
     * @param farmName	Farm Name
     * @param farmGroups	Farm Groups of peer
     * @throws NullPointerException If on of the above paramers is null. 
     */
    public PathloadStub(String hostname, String ipAddress, String farmName, String[] farmGroups) {
        if ((hostname == null) || (ipAddress == null) || (farmName == null) || (farmGroups == null)) {
            throw new NullPointerException("Incomplete data received. "
                    + "hostname, ipAddress, farmName and farmGroups may " + "not be null.");
        }

        data = encodePeerInfo(hostname, ipAddress, farmName, farmGroups);

        String sPattern = "Ver: \\[((.)*)\\] " + "Status: \\[((.)*)\\] " + "Token: \\[((.)*)\\] "
                + "Group: \\[((.)*)\\] " + "Msg: \\[((.)*)\\]";
        pattern = Pattern.compile(sPattern, Pattern.UNIX_LINES | Pattern.DOTALL);
    }

    /**
     * 
     * @param newUrl
     * @return
     */
    public boolean setUrl(String newUrl) {
        boolean bResult = false;
        if (newUrl == null) {
            return bResult;
        }

        try {
            url = new URL(newUrl);
            bResult = true;
        } catch (MalformedURLException e) {
            logger.log(Level.SEVERE, "[monPathload] PathloadStub URL is malformed. " + newUrl);
        } catch (NullPointerException e) {
            logger.log(Level.SEVERE,
                    "[monPathload] PathloadStub URL is null. Did you forget to define lia.util.Pathload.client.PathloadConnector ?");
        }

        return bResult;
    }

    /**	 
     * 
     * Post a request and read a the response of the Pathload Servlet
     * 
     <code>
     	URL url = new URL("http://myHost.com/servlet/myServlet"); 
     	StringBuilder postData = new StringBuilder();         
     
     	postData.append("parm0="); 
     	postData.append(URLEncoder.encode("parm0-value")); 
     	postData.append("&"); 
     
     	postData.append("parm1="); 
     	postData.append(URLEncoder.encode("parm1-value")); 
     	postData.append("&"); 
     
     	postData.append("parm2="); 
     	postData.append(URLEncoder.encode("parm2-value")); 
     
     	StringBuilder result = post(url, postData);
     </code>
     *
     * 
     * @param url
     * @param data
     * @return			The Servlets answer as a String
     * @throws IOException
     * @throws MalformedURLException
     */
    private String post(URL url, String data) throws IOException, MalformedURLException {
        if (url == null) {
            logger.log(Level.SEVERE,
                    "[monPathload] PathloadStub URL is null. Did you forget to define lia.util.Pathload.client.PathloadConnector ?");
            return null;
        }
        if (data == null) {
            logger.log(Level.SEVERE, "[monPathload] PathloadStub PostData is null. Are you sure?");
            return null;
        }

        StringBuilder result = new StringBuilder();

        DataOutputStream streamOut = null;
        BufferedReader streamIn = null;

        String str = null;
        try {
            URLConnection conn = url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);

            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            streamOut = new DataOutputStream(new java.io.BufferedOutputStream(conn.getOutputStream()));

            streamOut.writeBytes(data);
            streamOut.flush();
            streamOut.close();
            streamOut = null;

            streamIn = new BufferedReader(new InputStreamReader(conn.getInputStream()));

            while ((str = streamIn.readLine()) != null) {
                result.append(str);
                result.append("\n");
            }
            streamIn.close();

        } catch (MalformedURLException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        } finally {
            try {
                if (streamIn != null) {
                    streamIn.close();
                }
            } catch (Exception e) {
            }
            try {
                if (streamOut != null) {
                    streamOut.close();
                }
            } catch (Exception e) {
            }
        }

        return result.toString();
    }

    /**
     * Encode a peerInfo information in a request
     *
     * @param hostname	Hostname of peer
     * @param ipAddress	Host address
     * @param farmName	Farm Name
     * @param farmGroups	Farm Groups of peer 
     * @return		The URL string
     */
    private String encodePeerInfo(String hostname, String ipAddress, String farmName, String[] farmGroups) {
        if ((hostname == null) || (ipAddress == null) || (farmName == null) || (farmGroups == null)) {
            return "";
        }

        StringBuilder postData = new StringBuilder();
        postData.append(encode("ver", PathloadStub.PATHLOAD_MESSAGE_VER));
        postData.append(encode("hostname", hostname));
        postData.append(encode("ipAddress", ipAddress));
        postData.append(encode("farmName", farmName));
        if (farmGroups != null) {
            for (String farmGroup : farmGroups) {
                postData.append(encode("farmGroups", farmGroup));
            }
        }

        return postData.toString();
    }

    /**
     * Encode a paramter in URL
     * @param name	Parameter name
     * @param value	Paramter value
     * @return		Encoded String or "" if error
     */
    private String encode(String name, String value) {
        String result;
        if ((name == null) || (value == null)) {
            return "";
        }

        try {
            result = "&" + name + "=" + URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            result = "";
        }

        return result;
    }

    @Override
    public ServletResponse getToken() throws PathloadException {
        ServletResponse response = null;

        try {
            String request = data + encode("action", PathloadClient.ACTION_NAME_GET_TOKEN);
            String servletResponse = post(url, request);
            parseServletOutput(servletResponse);
            response = ServletResponse.fromString(servletResponse);
        } catch (MalformedURLException e) {
            throw new PathloadException(e.getMessage());
        } catch (IOException e) {
            throw new PathloadException(e.getMessage());
        }
        return response;
    }

    @Override
    public ServletResponse refresh() throws PathloadException {
        ServletResponse response = null;
        String servletResponse = null;

        try {
            String request = data + encode("action", PathloadClient.ACTION_NAME_REFRESH);
            servletResponse = post(url, request);
            parseServletOutput(servletResponse);
            response = ServletResponse.fromString(servletResponse);
        } catch (PathloadException e) {
            response = ServletResponse.fromString(servletResponse);
            response.setOutOfSync(true);
        } catch (MalformedURLException e) {
            throw new PathloadException(e.getMessage());
        } catch (IOException e) {
            throw new PathloadException(e.getMessage());
        }

        return response;
    }

    @Override
    public boolean releaseToken(String tokenID) throws PathloadException {
        boolean bResult = false;
        if (tokenID == null) {
            return false;
        }

        try {
            String request = data + encode("action", PathloadClient.ACTION_NAME_RELEASE_TOKEN);
            request += encode("ID", tokenID);
            bResult = parseServletOutput(post(url, request));
        } catch (MalformedURLException e) {
            throw new PathloadException(e.getMessage());
        } catch (IOException e) {
            throw new PathloadException(e.getMessage());
        }

        return bResult;
    }

    @Override
    public boolean redoMeasurement(String tokenID) throws PathloadException {
        boolean bResult = false;
        if (tokenID == null) {
            return false;
        }

        try {
            String request = data + encode("action", PathloadClient.ACTION_NAME_REQUESTREDO);
            request += encode("ID", tokenID);
            bResult = parseServletOutput(post(url, request));
        } catch (MalformedURLException e) {
            throw new PathloadException(e.getMessage());
        } catch (IOException e) {
            throw new PathloadException(e.getMessage());
        }

        return bResult;
    }

    /**
     * Carefull, after a shutdown attempt, the URL will be invalidated.
     * You must run shutdown only once. 
     */
    @Override
    public boolean shutdown() throws PathloadException {
        boolean bResult = false;

        try {
            String request = data + encode("action", PathloadClient.ACTION_NAME_SHUTDOWN);
            bResult = parseServletOutput(post(url, request));
        } catch (MalformedURLException e) {
            throw new PathloadException(e.getMessage());
        } catch (IOException e) {
            throw new PathloadException(e.getMessage());
        }
        url = null;

        return bResult;
    }

    /**
     * Parse output of the PathloadServlet Response and
     * throw PathloadExceptions if necessary
     * 
     * @param output	The output generated by the Servlet
     * @return			Each response has a status flag,
     * 					1 for success, 0 for failure.
     * 					This returns true for success, false otherwise
     * @throws PathloadException
     * 					If the response contains a message and an 
     * 					error status, throw  an Exception with that
     * 					message
     */
    private boolean parseServletOutput(String output) throws PathloadException {
        boolean bResult = false;
        String message = null;

        if (output == null) {
            return false;
        }

        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            String status = matcher.group(4);
            if ((status != null) && (status.length() > 0) && (status.charAt(0) == '1')) {
                bResult = true;
            }
            message = matcher.group(9);
        }

        if ((!bResult) && (message != null) && (message.length() > 0)) {
            throw new PathloadException(message);
        }

        return bResult;
    }
}
