/**
 * 
 */
package lia.util.Pathload.server;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Every extraction policy used for the PeerCache 
 * must implement the Policy interface.
 * The policy is a serializable Comparator class.
 * 
 * @author heri
 *
 */
public interface Policy extends Comparator, Serializable {

}
