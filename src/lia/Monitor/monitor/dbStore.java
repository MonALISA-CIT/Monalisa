package lia.Monitor.monitor;

import java.util.Vector;

public interface dbStore {
    public Vector<?> select(monPredicate p);
}
