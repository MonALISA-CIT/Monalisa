/*
 * Created on Mar 22, 2010
 */
package lia.net.topology;

import java.util.EnumSet;


/**
 *
 * @author ramiro
 */
public interface State<S extends Enum<S>> {
    public EnumSet<S> state();
}
