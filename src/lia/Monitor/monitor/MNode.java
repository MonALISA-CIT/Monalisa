/*
 * $Id: MNode.java 7319 2012-10-12 10:51:16Z ramiro $
 */
package lia.Monitor.monitor;

import java.io.ObjectStreamException;
import java.io.StreamCorruptedException;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Vector;

import lia.util.StringFactory;

/**
 * 
 * @author Iosif Legrand
 * @author ramiro
 * 
 */
public class MNode implements java.io.Serializable, Comparable<MNode>, SortedVectorNotifier {

    private static final long serialVersionUID = -368474281465651265L;
    public String name; // node name
    public String name_short; // nickname for graphics
    public MCluster cluster;
    public MFarm farm;
    public String ipAddress;
    public Vector<String> moduleList;
    private Vector<String> parameterList;
    public int error_count = 0;
    public long lastMeasurement;
    public int state;
    private static final Field parameterListField;
    private static final Field moduleListField;
    

    static {

        Field fieldTmp = null;
        try {
            fieldTmp = MNode.class.getDeclaredField("parameterList");
            fieldTmp.setAccessible(true);
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException("Unable to access parameterList field ... nothing will work");
        }

        parameterListField = fieldTmp;

        fieldTmp = null;
        try {
            fieldTmp = MNode.class.getDeclaredField("moduleList");
            fieldTmp.setAccessible(true);
        } catch (Throwable t) {
            t.printStackTrace();
            throw new RuntimeException("Unable to access moduleList field ... nothing will work");
        }

        moduleListField = fieldTmp;
    }

    public MNode(final String name, final String ipAddress, final MCluster cluster, final MFarm farm) {
        this.name = name;
        this.cluster = cluster;
        this.farm = farm;
        this.ipAddress = ipAddress;
        moduleList = new SortedVector<String>();
        parameterList = new SortedVector<String>(this);
    }

    public MNode(final String tname, final MCluster cluster, final MFarm farm) {
        this(tname, null, cluster, farm);
    }

    public MNode() {
        this(null, null, null);
    }

    public static final MNode fromMNode(final MNode node, final MCluster cluster) {
        final MNode retNode = new MNode(node.name, node.ipAddress, cluster, cluster.farm);
        synchronized (node.parameterList) {
            retNode.parameterList.addAll(node.parameterList);
        }
        return retNode;
    }

    public Vector<String> getParameterList() {
        return parameterList;
    }

    public Vector<String> getModuleList() {
        return moduleList;
    }

    /**
     * returns the name value; if the flag rcFrame.MNode.overridePoundSign is set, then cuts text from first #
     */
    @Override
	public String toString() {
        String val = AppConfig.getProperty("rcFrame.MNode.overridePoundSign", "false");
        if (val != null && (val.equals("1") || val.toLowerCase().equals("true"))) {
            // cut all after #, if such exists
            int nPos = name.indexOf('#');
            if (nPos != -1) {
                return name.substring(0, nPos);
            }
        }
        return name;
    }

    public final MNode newStrippedInstance() {
        final MNode n = new MNode(name, ipAddress, null, null);
        n.parameterList.addAll(parameterList);
        return n;
    }
    
    public String toNickName() {
        if (name_short != null) {
            return name_short;
        }
        return name;
    }

    public String getFarmName() {
        if (farm == null) {
            return "??";
        }
        return farm.getName();
    }

    public String getClusterName() {
        if (cluster == null) {
            return "??";
        }
        return cluster.getName();
    }

    public String getName() {
        return name;
    }

    public String getIPaddress() {
        return ipAddress;
    }

    public String getKey(final String module) {
        if (module == null || !moduleList.contains(module)) {
            return null;
        }
        return farm.getName() + "_" + cluster.getName() + "_" + name + "_" + module;
    }

    public MFarm getFarm() {
        return farm;
    }

    public MCluster getCluster() {
        return cluster;
    }

    public void addModule(final String module) {
        if (module != null) {
            moduleList.add(module);
        }
    }

    public void removeModule(final String module) {
        if (module != null) {
            moduleList.remove(module);
        }
    }

    public void addParameters(final String[] params) {
        if (params != null) {
            final int len = params.length;
            for (int i = 0; i < len; i++) {
                parameterList.add(params[i]);
            }
        }
    }

    public int getErrorCount() {
        return error_count;
    }

    /**
     * @throws NullPointer exception if otherMNode == null, or this.name == null, 
     * or otherMNode.name == null
     * 
     * @return name.compareTo(((MNode)otherMNode).name);
     */
    @Override
	public int compareTo(final MNode otherMNode) {
        return this.name.compareTo(otherMNode.name);
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
        MNode other = (MNode) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }

    private Object readResolve() throws ObjectStreamException {
        try {
            if(MFarm.useStringFactory) {
                final SortedVector<String> replacementVectorParam = new SortedVector<String>(this);
                for(final Iterator<String> it = parameterList.iterator(); it.hasNext(); ) {
                    replacementVectorParam.add(StringFactory.get(it.next()));
                }
                parameterListField.set(this, replacementVectorParam);

                final SortedVector<String> replacementVectorModule = new SortedVector<String>();
                for(final Iterator<String> it = moduleList.iterator(); it.hasNext(); ) {
                    replacementVectorModule.add(StringFactory.get(it.next()));
                }
                moduleListField.set(this, replacementVectorModule);
            } else {
                parameterListField.set(this, new SortedVector<String>(parameterList, this));
                moduleListField.set(this, new SortedVector<String>(moduleList));
            }
        } catch (Throwable t) {
            throw new StreamCorruptedException(t.getMessage());
        }
        return this;
    }

    @Override
	public void elementAdded(Object o) {
        if (cluster != null && o != null) {
            cluster.paramAdded((String) o);
        }
    }

    @Override
	public void elementRemoved(Object o) {
        if (cluster != null && o != null) {
            cluster.paramRemoved((String) o);
        }
    }

    public String addParamIfAbsent(String paramName) {
        return ((SortedVector<String>) parameterList).addIfAbsent(paramName);
    }
}
