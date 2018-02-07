package lia.Monitor.monitor;

/**
 * Helper class for compressed messages. It is build at the source!
 */
public class cmonMessage implements java.io.Serializable {

    /**
     * The <b>decompressed</b> size for <code>cbuff</code>. It is set at the source!
     * 
     * <pre>
     * Usually <code>dSize</code> &lt; <code>cbuff.length()</code>
     * </pre>   
     */
    public int dSize;

    /**
     * The <b>compressed</b>. It is set at the source! 
     */
    public byte[] cbuff;

    /**
     *
     *  Constructs an emty compressed message
     *  Same as cmonMessage(0, null)
     * 
     */
    public cmonMessage(){}
    
    /**
     * 
     * @param dSize - The <b>decompressed</b> size of <code>cbuff</code>
     * @param cbuff - The <b>compressed</b> buffer
     */
    public cmonMessage(int dSize, byte[] cbuff){
        this.dSize = dSize;
        this.cbuff = cbuff;
    }
}