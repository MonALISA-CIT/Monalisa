/*
 * Created on Mar 22, 2010
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.net.topology;

/**
 * 
 * @author ramiro
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class NetDevice<S extends Enum<S>> extends GenericEntityWithState<S>{

    /**
     * 
     */
    private static final long serialVersionUID = -9108891640848039385L;

    public NetDevice(String name, S initialState) throws TopologyException {
        super(name, initialState);
    }

}
