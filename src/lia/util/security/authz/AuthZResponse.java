/*
 * Created on Mar 14, 2005
 * 
 */
package lia.util.security.authz;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import lia.util.ntp.NTPDate;

public class AuthZResponse implements Serializable {
    
    private static final long serialVersionUID = 5122955946530511697L;
    public final String subject;
   // final TreeMap<String, Boolean> groupAuthorization;
    final TreeMap groupAuthorization;
    
    // assertion validatity
    long notAfter;
    long notBefore;

    public AuthZResponse(String subject, TreeMap groupAuthorization) {
        this.subject = subject;
        this.groupAuthorization = groupAuthorization;
        this.notBefore = NTPDate.currentTimeMillis();
        this.notAfter = this.notBefore + 1000 * 60 * 60 * 5; // 5hours
    }

    /**
     * @return
     * <ul>
     * true if this response authorizes the subject in at least one group
     * </ul>
     */
    public boolean isAuthorized() {
        Set es =  groupAuthorization.entrySet();
        for (Iterator iter = es.iterator(); iter.hasNext();) {
            Map.Entry  element = (Map.Entry ) iter.next();
            if( ((Boolean) element.getValue()).booleanValue() == true )
                return true;                
        }
        return false;
    }

    /**
     * @return true if this response authorizes the subject in the supplied
     *         <code>group<code><br>
     * false otherwise
     */
    public boolean isAuthorized(String group) {
        return ((Boolean)groupAuthorization.get(group)).booleanValue();
    }
    
    public String toString() {
        return subject + "->" + groupAuthorization;
    }
}
