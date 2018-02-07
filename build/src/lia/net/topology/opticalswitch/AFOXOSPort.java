/*
 * Created on Mar 23, 2010
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.net.topology.opticalswitch;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import lia.net.topology.TopologyException;
import lia.net.topology.Port;
import lia.net.topology.agents.conf.AFOXRawPort;



public class AFOXOSPort extends OSPort {

    /**
     * Builder class for {@link AFOXOSPort} 
     * @author ramiro
     */
    public static final class Builder<P extends Port<?,?>> {
        private final String portName;
        private final OpticalSwitch device;
        private final PortType portType;
        private final P remotePort;
        private OSPortState state = OSPortState.UP;
        private int row;
        private int column;
        private int connectorNum;
        private String customerName;
        private boolean pendingExists;
        private final OSPort pendingPort;
        private AFOXRawPort afoxRawPort;
        private String serialNumber;
        
        public Builder(final String portName, PortType portType, OpticalSwitch device, P remotePort, OSPort pendingPort) {
            this.portName = portName;
            this.device = device;
            this.portType = portType;
            this.remotePort = remotePort;
            this.pendingPort = pendingPort;
        }

        /**
         * 
         * @param row sets the row number for this port
         * @return
         */
        public Builder<P> row(final int row) {
            this.row = row;
            return this;
        }

        /**
         * 
         * @param column sets the column number for this port 
         * @return
         */
        public Builder<P> column(final int column) {
            this.column = column;
            return this;
        }
        
        public Builder<P> serialNumber(final String serialNumber) {
            this.serialNumber = serialNumber;
            return this;
        }
        
        public Builder<P> afoxRawPort(final AFOXRawPort afoxRawPort) {
            this.afoxRawPort = afoxRawPort;
            return this;
        }
        
        /**
         * 
         * @param connectorNum sets the connector number for this port
         * @return
         */
        public Builder<P> connectorNum(final int connectorNum) {
            this.connectorNum = connectorNum;
            return this;
        }
        
        /**
         * 
         * @param customerName sets the customer name for this port
         * @return
         */
        public Builder<P> customerName(final String customerName) {
            this.customerName = customerName;
            return this;
        }

        /**
         * 
         * @param pendingExists are there any pending operations on this port
         * @return
         */
        public Builder<P> pendingExists(final boolean pendingExists) {
            this.pendingExists = pendingExists;
            return this;
        }
        
        /**
         * Builds a new {@link AFOXOSPort} 
         * @return the port
         * @throws TopologyException 
         */
        public AFOXOSPort build() throws TopologyException {
            return new AFOXOSPort(this);
        }
        
    }
    /**
     * 
     */
    private static final long serialVersionUID = 7986688576132457257L;

    protected final transient AFOXRawPort afoxRawPort;
    
    /**
     * row for this port
     */
    protected final int row;
    
    /**
     * column for this port
     */
    protected final int column;
    
    /**
     * Input or Output connector number
     */
    protected final int connectorNum;
    
    /**
     * Customer assignable name to this connector
     */
    protected final String customerName;
    
    /**
     *   Are there any pending commands
     */
    protected final AtomicBoolean pendingExists = new AtomicBoolean(false);

    protected final String serialNumber;
    
    protected final AtomicReference<OSPort> pendingPort;
    protected AFOXOSPort(Builder<?> builder) throws TopologyException {
        super(builder.portName, builder.device, builder.portType, builder.state, builder.remotePort);
        this.row = builder.row;
        this.afoxRawPort = builder.afoxRawPort;
        this.column = builder.column;
        this.customerName = builder.customerName;
        this.connectorNum = builder.connectorNum;
        this.pendingExists.set(builder.pendingExists);
        this.serialNumber = builder.serialNumber;
        this.pendingPort = new AtomicReference<OSPort>(builder.pendingPort);
    }
    
    public String getSerialNumber() { return serialNumber; }
    
    public int getRow() { return row; }
    public int getColumn() { return column; }
    public boolean pendingExists() { return this.pendingExists.get(); }
    public int getConnectorNum() { return connectorNum; }
    public void setPending(boolean bValue) { this.pendingExists.set(bValue); }
    public String toString() {
    	StringBuilder b = new StringBuilder();
    	b.append("AFOXPort[").append(row).append(":").append(column).append("] - Connector[").append(connectorNum);
    	b.append("] Customer[").append(customerName).append("] PendingPort[").append(pendingPort).append("]");
    	return b.toString();
    }
    
    public AFOXRawPort rawPort() {
        return afoxRawPort;
    }
}
