/*
 * Created on Mar 23, 2010
 */
package lia.net.topology;

/**
 * 
 * @author ramiro
 */
public interface TopologyNotifier {
    public void newEntity(GenericEntity newEntity);
    public void updateEntity(GenericEntity oldEntity, GenericEntity newEntity);
}
