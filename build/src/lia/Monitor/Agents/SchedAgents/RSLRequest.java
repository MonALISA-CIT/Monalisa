package lia.Monitor.Agents.SchedAgents;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RSLRequest extends UserRequest {

    /**
     * 
     */
    private static final long serialVersionUID = 1945923057084640528L;

    /** The Logger */
    private static final Logger logger = Logger.getLogger(RSLRequest.class.getName());

    /*
    String executable = null;
    
    String directory = null;
    
    String arguments = null;
    
    String count;
    
    double maxWallTime;
    
    double maxCpuTime;
    
    double minMemory; 
    
    double maxMemory;
    */

    Hashtable parameters = null;

    public RSLRequest(BufferedReader in) {
        super();
        parameters = new Hashtable();

        String line;
        try {
            while ((line = in.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(line, " (");
                while (st.hasMoreTokens()) {
                    String item = st.nextToken();

                    int pos1 = item.indexOf('=');
                    int pos2 = item.indexOf(')');
                    String paramName = item.substring(0, pos1);
                    String paramValue = item.substring(pos1 + 1, pos2);

                    parameters.put(paramName, paramValue);
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "RSLRequest could not be initialized");
        }

    }

    public RSLRequest(FileReader fr) {
        this(new BufferedReader(fr));
    }

    @Override
    public String toString() {
        String s = "";
        Enumeration params = parameters.keys();
        while (params.hasMoreElements()) {
            String paramName = (String) params.nextElement();
            String paramValue = (String) parameters.get(paramName);
            s += ("(" + paramName + "=" + paramValue + ")");
        }
        return s;
    }
}
