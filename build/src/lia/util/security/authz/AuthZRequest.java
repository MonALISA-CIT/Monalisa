/*
 * Created on Mar 14, 2005
 * 
 */
package lia.util.security.authz;

import java.io.Serializable;

public class AuthZRequest implements Serializable {
    
    private static final long serialVersionUID = 3257008760991068984L;
    
    public static final String[] ALL_GROUPS = null;
    public static final String[] NO_GROUPS = new String[0];
    
    public final String subject;
    public final String[] groups;
    
    public AuthZRequest(String subject, final String[] groups) {
        this.subject = subject;
        this.groups = groups;
    }
    
}
