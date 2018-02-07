/*
 * $Id: MCluster.java 7319 2012-10-12 10:51:16Z ramiro $
 */

package lia.Monitor.monitor;

import java.io.ObjectStreamException;
import java.io.StreamCorruptedException;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Vector;

import lia.util.StringFactory;

/**
 * @author Iosif Legrand
 * @author ramiro
 */
public class MCluster implements java.io.Serializable, Comparable<MCluster>, SortedVectorNotifier {

    private static final long serialVersionUID = 1335998674425297994L;

    public String name;

    private Vector<String> parameterList;

    private Vector<String> moduleList;

    private Vector<MNode> nodeList;

    public String externalModule;

    public String externalParam;

    public String externalNode;

    final MFarm farm;

    private static final Field nodeListField;

    private static final Field moduleListField;

    private static final Field parameterListField;

    static {

        Field fieldTMP = null;

        try {
            fieldTMP = MCluster.class.getDeclaredField("nodeList");
            fieldTMP.setAccessible(true);
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException("Unable to access nodeList field ... nothing will work");
        }
        nodeListField = fieldTMP;

        fieldTMP = null;
        try {
            fieldTMP = MCluster.class.getDeclaredField("moduleList");
            fieldTMP.setAccessible(true);
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException("Unable to access moduleList field ... nothing will work");
        }

        moduleListField = fieldTMP;

        fieldTMP = null;
        try {
            fieldTMP = MCluster.class.getDeclaredField("parameterList");
            fieldTMP.setAccessible(true);
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException("Unable to access parameterList field ... nothing will work");
        }

        parameterListField = fieldTMP;

    }

    public MCluster(final String name, final MFarm farm) {
        this.name = name;
        this.farm = farm;
        parameterList = new SortedVector<String>();
        moduleList = new SortedVector<String>();
        nodeList = new SortedVector<MNode>(this);
    }

    public Vector<MNode> getNodes() {
        return nodeList;
    }

    public final MCluster newStrippedInstance() {
        MCluster c = new MCluster(name, null);
        final int nls = nodeList.size();
        for (int i = 0; i < nls; i++) {
            c.nodeList.add(nodeList.get(i).newStrippedInstance());
        }
        return c;
    }

    public static final MCluster fromMCluster(final MCluster cluster, final MFarm farm) {
        final MCluster retCluster = new MCluster(cluster.name, farm);

        synchronized (cluster.nodeList) {
            final int size = cluster.nodeList.size();
            for (int i = 0; i < size; i++) {
                retCluster.addNodeIfAbsent(MNode.fromMNode(cluster.nodeList.get(i), retCluster));
            }
        }

        return retCluster;
    }

    public MNode getNode(final String node) {
    	if (node==null)
    		return null;
    	
        synchronized (nodeList) {
        	for (final MNode mnode: nodeList){
        		if (node.equals(mnode.name)){
        			return mnode;
        		}
        	}
        }
        
        return null;
    }

    /**
     * @return the last computed parameter list
     * @see #updateModulesAndParameters()
     */
    public Vector<String> getParameterList() {
    	synchronized (parameterList){
    		return parameterList;
    	}
    }

    /**
     * @return the last computed module list
     * @see #updateModulesAndParameters()
     */
    public Vector<String> getModuleList() {
    	synchronized (parameterList) {
    		return moduleList;
		}
    }
    
    /**
     * Same as {@link #getParameterList()} + {@link #getModuleList()}
     */
    public void updateModulesAndParameters(){
    	synchronized (parameterList) {
    		parameterList.clear();
    		moduleList.clear();
    		
            synchronized (nodeList) {
                for (final MNode node: nodeList){
                    MFarm.VAND(parameterList, node.getParameterList());
                    MFarm.VAND(moduleList, node.getModuleList());
                }
            }    		
		}
    }

    @Override
	public String toString() {
        return name;
    }

    public String getFarmName() {
        return farm.getName();
    }

    public String getName() {
        return name;
    }

    public MFarm getFarm() {
        return farm;
    }

    public boolean removeNode(final MNode node) {
        return nodeList.remove(node);
    }

    public boolean removeNode(final String nodeName) {
        return nodeList.remove(new MNode(nodeName, null, null));
    }

    /**
     * @throws NullPointerException if otherMCluster == null, or this.name == null, or otherMCluster.name == null
     * @return name.compareTo(((MCluster)otherCluster).name);
     */
    @Override
	public int compareTo(final MCluster otherMCluster) {
        return name.compareTo(otherMCluster.name);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MCluster other = (MCluster) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }

    private Object readResolve() throws ObjectStreamException {
        try {
            if (MFarm.useStringFactory) {
                final SortedVector<String> replacementVectorParam = new SortedVector<String>();
                for (final Iterator<String> it = parameterList.iterator(); it.hasNext();) {
                    replacementVectorParam.add(StringFactory.get(it.next()));
                }
                parameterListField.set(this, replacementVectorParam);

                final SortedVector<MNode> replacementVectorNode = new SortedVector<MNode>();
                for (final Iterator<MNode> it = nodeList.iterator(); it.hasNext();) {
                    final MNode node = it.next();
                    node.name = StringFactory.get(node.name);
                    replacementVectorNode.add(node);
                }
                nodeListField.set(this, replacementVectorNode);

                final SortedVector<String> replacementVectorModule = new SortedVector<String>();
                for (final Iterator<String> it = moduleList.iterator(); it.hasNext();) {
                    replacementVectorModule.add(StringFactory.get(it.next()));
                }
                moduleListField.set(this, replacementVectorModule);

            } else {
                parameterListField.set(this, new SortedVector<String>(parameterList));
                nodeListField.set(this, new SortedVector<MNode>(nodeList));
                moduleListField.set(this, new SortedVector<String>(moduleList));
            }

        } catch (Throwable t) {
            throw new StreamCorruptedException(t.getMessage());
        }
        return this;
    }

    @Override
	public void elementAdded(final Object o) {
        if (farm != null) {
            farm.nodeAdded((MNode) o);
        }
    }

    @Override
	public void elementRemoved(final Object o) {
        if (farm != null) {
            final MNode n = (MNode) o;
            if (n != null) {
                n.getParameterList().clear();
                farm.nodeRemoved((MNode) o);
            }
        }
    }

    void paramAdded(final String param) {
        if (farm != null) {
            farm.paramAdded(param);
        }
    }

    void paramRemoved(final String param) {
        if (farm != null) {
            farm.paramRemoved(param);
        }
    }

    public MNode addNodeIfAbsent(final MNode n) {
        return ((SortedVector<MNode>) nodeList).addIfAbsent(n);
    }
}
