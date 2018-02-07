/*
 * Created on Mar 20, 2010
 */
package lia.net.topology;

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author ramiro 
 */
public abstract class GenericEntity implements Serializable, Comparable<GenericEntity> {

    private static final ConcurrentMap<UUID, Object> entityMap = new ConcurrentHashMap<UUID, Object>();

    /**
     * 
     */
    private static final long serialVersionUID = 853264402747964783L;

    /**
     * Unique device ID
     */
    protected final UUID id;

    /**
     * Device name
     */
    protected final String name;

    /**
     * Computes the UUID based on name
     * 
     * @param name
     */
    GenericEntity(String name) throws TopologyException {
        this(name, UUID.nameUUIDFromBytes(name.getBytes()));
    }

    GenericEntity(String name, UUID id) throws TopologyException {

        if (id == null) {
            throw new NullPointerException("Null device ID");
        }

        if (name == null) {
            throw new NullPointerException("Null device name");
        }

        this.id = id;
        this.name = name;
        Object ge = entityMap.putIfAbsent(this.id(), this);
        if (ge != null) {
            throw new TopologyException("Exception for: " + getClass().getName() + " " + name + ":" + id +" There is already a value associated with this id: " + ge);
        }
    }

    /**
     * 
     * @param <T>
     * @param name
     * @param type
     * @return
     */
    public static <T extends GenericEntity> T entityForName(final String name, Class<T> type) {
        return entityForID(UUID.nameUUIDFromBytes(name.getBytes()), type);
    }

    /**
     * 
     * @param <T>
     * @param id
     * @param type
     * @return
     */
    public static <T extends GenericEntity> T entityForID(final UUID id, Class<T> type) {
        Object o = entityMap.get(id);
        if (o == null) {
            return null;
        }

        return type.cast(o);
    }

    public static Object clearIDFromCache(final UUID id) {
        return entityMap.remove(id);
    }

    public static <T extends GenericEntity> void clearIDFromCache(T[] ents) {
        for(T v: ents) {
            clearIDFromCache(v.id());
        }
    }

    public static void clearIDFromCache(final UUID[] ids) {
        for(UUID id: ids) {
            clearIDFromCache(id);
        }
    }

    public String name() {
        return name;
    }

    public UUID id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof GenericEntity) {
            return this.id().equals(((GenericEntity)o).id());
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return this.id().hashCode();
    }
    
    public int compareTo(GenericEntity ge) {
        return this.id.compareTo(ge.id);
    }

    public String toString() {
        return getClass().getName() + "," + id + "," + name;
    }
}
