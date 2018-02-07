/*
 * $Id: MFarm.java 7144 2011-03-28 13:10:34Z costing $
 */
package lia.Monitor.monitor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.StreamCorruptedException;
import java.lang.reflect.Field;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicLong;

import lia.util.StringFactory;

/**
 * 
 * @author Iosif Legrand
 * @author ramiro
 * 
 */
public class MFarm implements java.io.Serializable, SortedVectorNotifier {

    private static final long serialVersionUID = 3255034191993653203L;

    public final String name;

    private Vector<MCluster> clusterList;

    private Vector<String> parameterList;

    private Vector<String> moduleList;

    Vector<String> avModules;

    int moduleCount = 0;

    private static final Field clusterListField;
    private static final Field moduleListField;
    private static final Field parameterListField;

    private transient AtomicLong addedClusters;
    private transient AtomicLong removedClusters;
    private transient AtomicLong addedNodes;
    private transient AtomicLong removedNodes;
    private transient AtomicLong addedParams;
    private transient AtomicLong removedParams;

    private transient AtomicLong modCount;

    private final transient Object confLock = new Object();
    
    static final boolean useStringFactory = AppConfig.getb("lia.Monitor.monitor.MFarm.useStringFactory", true);
    
    static {
        Field fieldTmp = null;
        try {
            fieldTmp = MFarm.class.getDeclaredField("clusterList");
            fieldTmp.setAccessible(true);
        } catch(Throwable t) {
            t.printStackTrace();
            throw new RuntimeException("Unable to access clusterList field ... nothing will work");
        }

        clusterListField = fieldTmp;

        fieldTmp = null;
        try {
            fieldTmp = MFarm.class.getDeclaredField("moduleList");
            fieldTmp.setAccessible(true);
        } catch(Throwable t) {
            t.printStackTrace();
            throw new RuntimeException("Unable to access moduleList field ... nothing will work");
        }
        
        moduleListField = fieldTmp;
        
        fieldTmp = null;
        try {
            fieldTmp = MFarm.class.getDeclaredField("parameterList");
            fieldTmp.setAccessible(true);
        } catch(Throwable t) {
            t.printStackTrace();
            throw new RuntimeException("Unable to access parameterList field ... nothing will work");
        }
        
        parameterListField = fieldTmp;

    }


    public MFarm(final String name) {
        this.name = name;
        initCounters();
        clusterList = new SortedVector<MCluster>(this);
        parameterList = new SortedVector<String>();
        moduleList = new SortedVector<String>();
        avModules = null;
    }

    private void initCounters() {
        addedClusters = new AtomicLong(0L);
        removedClusters = new AtomicLong(0L);
        addedNodes = new AtomicLong(0L);
        removedNodes = new AtomicLong(0L);
        addedParams = new AtomicLong(0L);
        removedParams = new AtomicLong(0L);
        modCount = new AtomicLong();
    }

    public Object getConfLock() {
        return confLock;
    }
    
    public Vector<MCluster> getClusters() {
        return clusterList;
    }

    public Vector<MNode> getNodes() {
        Vector<MNode> sum = new Vector<MNode>();
        for (int i = 0; i < clusterList.size(); i++) {
            sum.addAll(clusterList.elementAt(i).getNodes());
        }
        return sum;
    }

    public MCluster getCluster(String cn) {
        synchronized(clusterList) {
            final int idx = clusterList.indexOf(new MCluster(cn, null));
            if(idx >= 0) {
                return clusterList.elementAt(idx);
            }
        }
        return null;
    }

    public void setAvModules(Vector<String> avModules) {
        this.avModules = avModules;
    }

    public Vector<String> getAvModules() {
        return avModules;
    }

    public Vector<String> getParameterList() {
        parameterList.clear();
        
        for (final MCluster cluster: clusterList) {
            MFarm.VAND(parameterList, cluster.getParameterList());
        }
        
        return parameterList;
    }

    public Vector<String> getModuleList() {
        moduleList.clear();
        for (Enumeration<MCluster> e = clusterList.elements(); e.hasMoreElements();) {
            MFarm.VAND(moduleList, e.nextElement().getModuleList());
        }
        return moduleList;
    }

    public String getName() {
        return name;
    }

    public static <T> void VAND(final Vector<T> V1, final Vector<T> V2) {
        if(V1 == null || V2 == null || V2.size()==0) {
            return;
        }

        if(V1 instanceof SortedVector && V2 instanceof SortedVector) {
        	V1.ensureCapacity(V1.size() + V2.size());
        	
            for(final T value: V2){
                if(value != null) {
                    V1.add(value);
                }
            }
            //stupid null values!!! don't know from where, but they are there!!!
            //V1.addAll(V2);
        } else {
            System.err.println(" [ MFarm ] VAND ... works only with lia.Monitor.monitor.SortedVector V1: " + V1.getClass() + " V2: " + V2.getClass());
        }
    }

    public MCluster addClusterIfAbsent(final MCluster cluster) {
        final Object retV = ((SortedVector<MCluster>)clusterList).addIfAbsent(cluster);
        return (MCluster) retV;
    }

    public String getKey(MNode n, String module) {
        String ip = n.getIPaddress();
        if (ip == null) return null;
        return ip + "&" + module;
    }

    public String toString() {
        return name;
    }

    public boolean removeCluster(MCluster cluster) {
        return clusterList.remove(cluster);
    }

    /**
     * the remove is atomic
     * @param clusterName
     * @return
     */
    public boolean removeCluster(String clusterName) {
        return clusterList.remove(new MCluster(clusterName, this));
    }

    private Object readResolve() throws ObjectStreamException {
        try {
            if (MFarm.useStringFactory) {
                final SortedVector<String> replacementVectorParam = new SortedVector<String>();
                for (final Iterator<String> it = parameterList.iterator(); it.hasNext();) {
                    replacementVectorParam.add(StringFactory.get(it.next()));
                }
                parameterListField.set(this, replacementVectorParam);
                
                final SortedVector<MCluster> replacementVectorCluster = new SortedVector<MCluster>(this);
                for (final Iterator<MCluster> it = clusterList.iterator(); it.hasNext();) {
                    final MCluster cluster = it.next();
                    cluster.name = StringFactory.get(cluster.name);
                    replacementVectorCluster.add(cluster);
                }
                clusterListField.set(this, replacementVectorCluster);
                
                
                final SortedVector<String> replacementVectorModule = new SortedVector<String>();
                for(final Iterator<String> it = moduleList.iterator(); it.hasNext(); ) {
                    replacementVectorModule.add(StringFactory.get(it.next()));
                }
                moduleListField.set(this, replacementVectorModule);

            } else {
                parameterListField.set(this, new SortedVector<String>(parameterList));
                clusterListField.set(this, new SortedVector<MCluster>(clusterList, this));
                moduleListField.set(this, new SortedVector<String>(moduleList));
            }
        }catch(Throwable t) {
            throw new StreamCorruptedException(t.getMessage());
        }
        return this;
    }

    final public void elementAdded(Object o) {
        modCount.incrementAndGet();
        addedClusters.incrementAndGet();
    }

    final public void elementRemoved(Object o) {
        modCount.incrementAndGet();
        final MCluster c = (MCluster)o;
        if(c != null) {
            c.getNodes().clear();
            removedClusters.incrementAndGet();
        }
    }

    final void nodeAdded(final MNode n) {
        modCount.incrementAndGet();
        addedNodes.incrementAndGet();
    }

    final void nodeRemoved(final MNode n) {
        modCount.incrementAndGet();
        removedNodes.incrementAndGet();
    }

    final void paramAdded(final String param) {
        modCount.incrementAndGet();
        addedParams.incrementAndGet();
    }

    final void paramRemoved(final String param) {
        modCount.incrementAndGet();
        removedParams.incrementAndGet();
    }

    public final long getTotalAddedParams() {
        return addedParams.get();
    }

    public final long getTotalRemovedParams() {
        return removedParams.get();
    }

    public final long getTotalAddedNodes() {
        return addedNodes.get();
    }

    public final long getTotalRemovedNodes() {
        return removedNodes.get();
    }

    public final long getTotalAddedClusters() {
        return addedClusters.get();
    }

    public final long getTotalRemovedClusters() {
        return removedClusters.get();
    }

    public final long modCount() {
        return modCount.get();
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        // our "pseudo-constructor"
        in.defaultReadObject();
        initCounters(); 
    }
}

