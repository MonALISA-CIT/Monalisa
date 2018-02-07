
package lia.Monitor.Agents.IDS;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author adim
 * Jan 13, 2005
 * 
 */
public class IDSAlert implements Serializable{

private String ip;
private int priority;

   
    /**
     * @param ip
     * @param priority
     */
    public IDSAlert(String ip, int priority) {
        this.ip = ip;
        this.priority = priority;
    }
    
/**
*	@param encoded The alert encoded as: [IP:<ip>/Priority:<pr>]
*/
public IDSAlert(String encoded) throws Exception {
    
         Pattern pattern = Pattern.compile("\\[IP:([0-9\\.]+)/Priority:([0-9]+)\\]");
        Matcher matcher = pattern.matcher(encoded);
        if (matcher.find()) {
            this.ip = matcher.group(1);
	    this.priority=Integer.parseInt( matcher.group(2) );	    
        }
	else
	    throw new Exception ("Invalid encoded alert");

}  
    
/**
 * @return Returns the ip.
 */
public String getIp() {
    return this.ip;
}
/**
 * @param ip The ip to set.
 */
public void setIp(String ip) {
    this.ip = ip;
}
/**
 * @return Returns the priority.
 */
public int getPriority() {
    return this.priority;
}
/**
 * @param priority The priority to set.
 */
public void setPriority(int priority) {
    this.priority = priority;
}

public String toString() {
    return "[IP:"+this.ip+"/Priority:"+this.priority+"]";
}
}
