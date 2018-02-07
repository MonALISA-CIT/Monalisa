package lia.util.security.authz;

import java.io.Serializable;
import java.util.Date;

import lia.Monitor.monitor.AuthZSI;

/**
 * AuthZService proxy used for registration in LUSs
 * @author adim
 * @version Sep 7, 2005 1:45:18 PM
 */
public class AuthZJiniProxy implements Serializable, AuthZSI {
    
    /**
     * <code>serialVersionUID</code>
     */
    private static final long serialVersionUID = 3175057330491908615L;
    
	public Long rTime;
    public Date rDate;
}