/*
 * Created on Mar 18, 2010
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package com.telescent.afox;

import java.util.concurrent.TimeUnit;

import com.telescent.afox.msg.AFOXFullUpdateRequestMsg;
import com.telescent.afox.msg.AFOXFullUpdateReturnMsg;

/**
 * 
 * @author ramiro
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class TestSym {

//  public static final String AFOX_TEST_HOST = "198.32.44.55";
//    public static final String AFOX_TEST_HOST = "127.0.0.1";
    public static final String AFOX_TEST_HOST = "137.138.42.61";
//    public static final String AFOX_TEST_HOST = "75.83.18.29";
    
    public static final int AFOX_TEST_PORT = 34567;
    private static AFOXConnection afoxConn;
    
    public static final void main(String[] args) throws Exception {
        afoxConn = new AFOXConnection(AFOX_TEST_HOST, AFOX_TEST_PORT);
        final AFOXFullUpdateRequestMsg reqMsg = new AFOXFullUpdateRequestMsg("", 0, 0);
        
        final byte[] respBMsg = afoxConn.sendAndReceive(reqMsg.ToFlatSer(), 5, TimeUnit.SECONDS);
        
        final AFOXFullUpdateReturnMsg respMsg = AFOXFullUpdateReturnMsg.DeSerialize(respBMsg);
        //final OpticalSwitch os = MLTranslation.fromFullUpdateReturnMsg(respMsg);
        System.out.println("Got "+respMsg);
    }
}
