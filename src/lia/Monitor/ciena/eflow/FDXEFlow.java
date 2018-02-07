/*
 * Created on Dec 4, 2011
 */
package lia.Monitor.ciena.eflow;

public final class FDXEFlow {
    
    public final String name;
    public final EFlowStats inFlow;
    public final EFlowStats outFlow;
    
    public FDXEFlow(final String name, final EFlowStats inFlow, final EFlowStats outFlow) {
        this.name = name;
        this.inFlow = inFlow;
        this.outFlow = outFlow;
    }
}