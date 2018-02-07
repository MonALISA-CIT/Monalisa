package lia.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.ntp.NTPDate;

/** 
 * Helper class for managing sets arrays of data. It provides useful methods to do
 * multiple operations, at once, on all elements in the data array.
 * 
 * @author catac
 */
public class DataArray implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(DataArray.class.getName());

    /** Parameter name - value mapping */
    private HashMap<String, Value> hmParams = null;

    /**
     * Keep a double value and allow operations on it.
     * 
     * @author costing
     * @since Apr 24, 2008
     */
    private static final class Value implements Comparable<Value>, Cloneable {
        private double value;

        /**
         * Simple constructor, initialize the value with 0
         */
        public Value() {
            this.value = 0;
        }

        /**
         * Copy constructor
         * 
         * @param d
         */
        public Value(final double d) {
            this.value = d;
        }

        /**
         * Add a quantity
         * 
         * @param d quantity
         */
        public void add(final double d) {
            this.value += d;
        }

        /**
         * Subtract a quantity
         * 
         * @param d quantity
         */
        @SuppressWarnings("unused")
        public void subtract(final double d) {
            this.value -= d;
        }

        /**
         * Divide by something
         * 
         * @param d divider
         */
        public void div(final double d) {
            this.value /= d;
        }

        /**
         * Get the current value
         * 
         * @return value
         */
        public double get() {
            return this.value;
        }

        /**
         * Set the value
         * 
         * @param d
         */
        public void set(final double d) {
            this.value = d;
        }

        @Override
        public boolean equals(final Object o) {
            return compareTo((Value) o) == 0;
        }

        @Override
        public int hashCode() {
            return (int) this.value;
        }

        @Override
        public int compareTo(final Value vOther) {
            if (vOther == null) {
                return 1;
            }

            if (this.value < vOther.value) {
                return -1;
            }

            if (this.value > vOther.value) {
                return 1;
            }

            return 0;
        }

        @Override
        public Value clone() {
            return new Value(value);
        }
    }

    /** build an empty data array */
    public DataArray() {
        // do nothing
    }

    /**
     * Build an empty data array with enough memory preallocated for this many elements
     * 
     * @param size
     */
    public DataArray(final int size) {
        if (size > 0) {
            this.hmParams = new HashMap<String, Value>(((size + 1) * 4) / 3);
        }
    }

    /** 
     * build a data array similar to the given one 
     * @param source array to copy 
     */
    public DataArray(final DataArray source) {
        this(source, null);
    }

    /**
     * Copy all the parameters from the given array, adding a suffix to each parameter name
     * 
     * @param source values to copy
     * @param suffix suffix to add to each name
     */
    public DataArray(final DataArray source, final String suffix) {
        this(source.size());

        setAsDataArray(source, suffix);
    }

    /** 
     * build a data array with the given parameter names 
     * @param params parameter names
     */
    public DataArray(final String[] params) {
        this(params.length);

        for (int i = params.length - 1; i >= 0; i--) {
            this.hmParams.put(params[i], new Value());
        }
    }

    /**
     * Initialize the data array with the parameters of this Result
     * 
     * @param r initial values
     * @see #setAsResult(Result)
     */
    public DataArray(final Result r) {
        this(r.param_name.length);

        copyResult(r);
    }

    /**
     * Clear all the internal structures
     */
    public void clear() {
        if (this.hmParams != null) {
            this.hmParams.clear();
        }
    }

    /**
     * Is this parameter held in this array?
     * 
     * @param name
     * @return true if there is a value associated with this parameter name
     */
    public boolean containsKey(final String name) {
        return this.hmParams == null ? false : this.hmParams.containsKey(name);
    }

    private Value getValue(final String name) {
        return this.hmParams == null ? null : (Value) this.hmParams.get(name);
    }

    /**
     * Make sure the internal map is allocated before actually using it to store something.
     * 
     * @return true if the map was already allocated, false if it was allocated on this call
     */
    private boolean ensureAllocated() {
        if (this.hmParams == null) {
            this.hmParams = new HashMap<String, Value>(8);
            return false;
        }

        return true;
    }

    /**
     * Make sure the internal map is allocated before actually using it to store something.
     * If the map has to be initialized, allocate enough memory so that it is able to hold 
     * the specified number of elements without needing to rehash.  
     * 
     * @param size desired number of elements for a newly allocated map
     * @return true if the map was already allocated, false if it was allocated on this call
     */
    private boolean ensureAllocated(final int size) {
        if (this.hmParams == null) {
            this.hmParams = new HashMap<String, Value>(((size + 1) * 4) / 3);
            return false;
        }

        return true;
    }

    private Value getOrCreate(final String name) {
        Value v = ensureAllocated() ? (Value) this.hmParams.get(name) : null;

        if (v == null) {
            v = new Value();
            this.hmParams.put(StringFactory.get(name), v);
        }

        return v;
    }

    /** Remove the given parameter from this data array 
     * @param name parameter to remove 
     */
    public void removeParam(final String name) {
        if (this.hmParams != null) {
            this.hmParams.remove(name);
        }
    }

    /** 
     * Set the value for the given parameter; if it doesn't exists, it creates it.
     *  
     * @param name name of the parameter 
     * @param value its value
     */
    public void setParam(final String name, final double value) {
        final Value v = getOrCreate(name);

        v.set(value);
    }

    /** 
     * Get the value for the given parameter.
     *  
     * @param name name of the parameter
     * @return the value, if it exists, or 0 if we don't have this parameter 
     */
    public double getParam(final String name) {
        if ((this.hmParams == null) || (this.hmParams.size() == 0)) {
            return 0;
        }

        final Value v = this.hmParams.get(name);

        return v != null ? v.get() : 0;
    }

    /** 
     * add the given value to the named parameter 
     * @param name parameter to search
     * @param value value to add
     */
    public void addToParam(final String name, final double value) {
        final Value v = getOrCreate(name);

        v.add(value);
    }

    /** 
     * add my values to the ones in the given DataArray 
     * @param other 
     */
    public void addToDataArray(final DataArray other) {
        if ((this.hmParams == null) || (this.hmParams.size() == 0)) {
            return;
        }

        for (Map.Entry<String, Value> me : this.hmParams.entrySet()) {
            final String sName = me.getKey();
            final Value v = me.getValue();

            other.addToParam(sName, v.get());
        }
    }

    /** 
     * Subtract from my values the ones in the other DataArray. Results go in the result DataArray.
     *  
     * @param other array to subtract
     * @param result destination of the values
     */
    public void subDataArrayTo(final DataArray other, final DataArray result) {
        if ((this.hmParams == null) || (this.hmParams.size() == 0)) {
            return;
        }

        for (Map.Entry<String, Value> me : this.hmParams.entrySet()) {
            final String sName = me.getKey();
            final Value v = me.getValue();

            result.setParam(sName, v.get() - other.getParam(sName));
        }
    }

    /** 
     * put in the given DataArray the minimum between my values and the ones in the given DataArray 
     * @param other 
     */
    public void minToDataArray(final DataArray other) {
        if ((this.hmParams == null) || (this.hmParams.size() == 0)) {
            return;
        }

        for (Map.Entry<String, Value> me : this.hmParams.entrySet()) {
            final String sName = me.getKey();
            final Value v = me.getValue();

            final Value vOther = other.getValue(sName);

            if (vOther != null) {
                other.setParam(sName, Math.min(v.get(), vOther.get()));
            } else {
                other.setParam(sName, v.get());
            }
        }
    }

    /** 
     * put in the given DataArray the maximum between my values and the ones in the given DataArray 
     * @param other 
     */
    public void maxToDataArray(final DataArray other) {
        if ((this.hmParams == null) || (this.hmParams.size() == 0)) {
            return;
        }

        for (final Map.Entry<String, Value> me : this.hmParams.entrySet()) {
            final String sName = me.getKey();
            final Value v = me.getValue();

            final Value vOther = other.getValue(sName);

            if (vOther != null) {
                other.setParam(sName, Math.max(v.get(), vOther.get()));
            } else {
                other.setParam(sName, v.get());
            }
        }
    }

    /**
     * make this data array exactly the same as the given DataArray
     * @param other structure to copy
     */
    public void setAsDataArray(final DataArray other) {
        setAsDataArray(other, null);
    }

    /** 
     * make this data array exactly the same as the given DataArray 
     * @param other structure to copy
     * @param sSuffix suffix to add to the names
     */
    public void setAsDataArray(final DataArray other, final String sSuffix) {
        clear();

        if ((other.hmParams == null) || (other.hmParams.size() == 0)) {
            return;
        }

        ensureAllocated(other.hmParams.size());

        final boolean bSuffix = (sSuffix != null) && (sSuffix.length() > 0);

        for (final Map.Entry<String, Value> me : other.hmParams.entrySet()) {

            final String sName = me.getKey();
            final Value v = me.getValue();

            this.hmParams.put(bSuffix ? StringFactory.get(sName + sSuffix) : sName, v.clone());
        }
    }

    /** 
     * make this data array exactly the same as the given Result in terms of parameters
     * @param r values to copy 
     */
    public void setAsResult(final Result r) {
        clear();

        if ((r == null) || (r.param == null) || (r.param.length == 0)) {
            return;
        }

        ensureAllocated(r.param.length);

        copyResult(r);
    }

    private void copyResult(final Result r) {
        for (int i = r.param_name.length - 1; i >= 0; i--) {
            setParam(r.param_name[i], r.param[i]);
        }
    }

    /** 
     * sets all parameters to the zero value 
     */
    public void setToZero() {
        if ((this.hmParams == null) || (this.hmParams.size() == 0)) {
            return;
        }

        for (final Value v : this.hmParams.values()) {
            v.set(0);
        }
    }

    /** 
     * Divide all the parameters with the given value - this is helpful for computing rates. 
     * If the value to divide by is ~0 then all the internal values are in fact reset to zero.
     * 
     * @param value value to divide by
     */
    public void divParams(final double value) {
        if ((this.hmParams == null) || (this.hmParams.size() == 0)) {
            return;
        }

        if (Math.abs(value) < 1e-10) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Reseting values to zero because the argument value is :" + value);
            }

            setToZero();
            return;
        }

        for (final Value v : this.hmParams.values()) {
            v.div(value);
        }
    }

    /** 
     * perform a square root on all parameters 
     */
    public void sqrtParams() {
        if ((this.hmParams == null) || (this.hmParams.size() == 0)) {
            return;
        }

        for (final Value v : this.hmParams.values()) {
            if (v.get() > 0) {
                v.set(Math.sqrt(v.get()));
            } else {
                v.set(0);
            }
        }
    }

    /** 
     * for each param i do : (Xi - Xi_med)^2 --> result_i 
     * @param med 
     * @param result 
     */
    public void sqDifToDataArray(final DataArray med, final DataArray result) {
        if ((this.hmParams == null) || (this.hmParams.size() == 0)) {
            return;
        }

        for (final Map.Entry<String, Value> me : this.hmParams.entrySet()) {
            final String sName = me.getKey();

            final Value vMed = med.getValue(sName);

            if (vMed != null) {
                final Value v = me.getValue();

                final double diff = v.get() - vMed.get();

                result.setParam(sName, diff * diff);
            }
        }
    }

    /** 
     * check if all the elements from the DataArray are zero
     * @return true if all the values are zero, false if any if !=0 
     */
    public boolean isZero() {
        if ((this.hmParams == null) || (this.hmParams.size() == 0)) {
            return true;
        }

        for (final Value v : this.hmParams.values()) {
            if (v.get() != 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if any of the values in this array is negative
     * 
     * @return true if any of the values is negative
     */
    public boolean hasNegatives() {
        if ((this.hmParams == null) || (this.hmParams.size() == 0)) {
            return false;
        }

        for (final Value v : this.hmParams.values()) {
            if (v.get() < 0) {
                return true;
            }
        }

        return false;
    }

    /** 
     * check if this DataArray holds any elements
     * @return true if there is no value kept 
     */
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Get the number of parameters kept inside
     * 
     * @return number of parameters
     */
    public int size() {
        return this.hmParams == null ? 0 : this.hmParams.size();
    }

    /** 
     * check if this DataArray equals to the given data Array 
     */
    @Override
    public boolean equals(final Object o) {
        if ((o == null) || !(o instanceof DataArray)) {
            return false;
        }

        final DataArray other = (DataArray) o;

        if (size() != other.size()) {
            return false;
        }

        // if the size is identical and we have no values in our map, it means there is nothing in the other as well
        if ((this.hmParams == null) || (this.hmParams.size() == 0)) {
            return true;
        }

        for (final Map.Entry<String, Value> me : this.hmParams.entrySet()) {
            final String sName = me.getKey();
            final Value v = me.getValue();

            final Value vOther = other.getValue(sName);

            if (!v.equals(vOther)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        return this.hmParams == null ? 0 : this.hmParams.keySet().hashCode();
    }

    /** 
     * Build a Result from the given DataArray into the appropriate cluster/node; 
     * also returns the result.
     * If the given DataArray is null, it returns a expire result for that node
     * If nodeName is null, it returns the expire result for that cluster
     * 
     * We use monXDRUDP as "sender" module to enable the automatic expiry of the
     * produced data.
     * @param vrez Target of the generated objects
     * @param farmName Farm field
     * @param clusterName Cluster field
     * @param nodeName Node field
     * @param da parameter
     * @return the added Result object
     */
    public static Result addRezFromDA(final Vector<Object> vrez, final String farmName, final String clusterName,
            final String nodeName, final DataArray da) {
        if (nodeName == null) {
            eResult er = new eResult(farmName, clusterName, null, "monXDRUDP", null);
            er.time = NTPDate.currentTimeMillis();
            vrez.add(er);
            return null;
        }

        if (da == null) {
            eResult er = new eResult(farmName, clusterName, nodeName, "monXDRUDP", null);
            er.time = NTPDate.currentTimeMillis();
            vrez.add(er);
            return null;
        }

        if (da.size() != 0) {
            final Result rez = new Result(farmName, clusterName, nodeName, "monXDRUDP", da.getParameters());

            for (int i = rez.param_name.length - 1; i >= 0; i--) {
                rez.param[i] = da.getParam(rez.param_name[i]);
            }

            rez.time = NTPDate.currentTimeMillis();
            vrez.add(rez);
            return rez;
        }

        return null;
    }

    /**
     * Get all the parameter names, unsorted (hash key set).
     * 
     * @return parameter names
     */
    public String[] getParameters() {
        return (this.hmParams == null) || (this.hmParams.size() == 0) ? new String[0] : this.hmParams.keySet().toArray(
                new String[this.hmParams.size()]);
    }

    /**
     * Get all the parameter names, sorted alphabetically
     * 
     * @return parameter names
     */
    public String[] getSortedParameters() {
        if ((this.hmParams == null) || (this.hmParams.size() == 0)) {
            return new String[0];
        }

        final ArrayList<String> al = new ArrayList<String>(this.hmParams.keySet());

        Collections.sort(al);

        return al.toArray(new String[al.size()]);
    }

    /**
     * Get the set of parameter names
     * 
     * @return Set on Strings
     */
    public Set<String> parameterSet() {
        return (this.hmParams == null) || (this.hmParams.size() == 0) ? new HashSet<String>(1) : this.hmParams.keySet();
    }

    /**
     * Get all the parameters that don't show up in the other array
     * 
     * @param other
     * @return Set of parameter names that are missing from the other array
     */
    public Set<String> diffParameters(final DataArray other) {
        if ((this.hmParams == null) || (this.hmParams.size() == 0)) {
            return new HashSet<String>(1);
        }

        final HashSet<String> hs = new HashSet<String>(this.hmParams.keySet());

        if ((other.hmParams == null) || (other.hmParams.size() == 0)) {
            return hs;
        }

        hs.removeAll(other.hmParams.keySet());

        return hs;
    }

    /**
     * Get the common parameters between the two arrays
     * 
     * @param other
     * @return common parameters set
     */
    public Set<String> commonParameters(final DataArray other) {
        if ((this.hmParams == null) || (this.hmParams.size() == 0) || (other.hmParams == null)
                || (other.hmParams.size() == 0)) {
            return new HashSet<String>(1);
        }

        final HashSet<String> hs = new HashSet<String>(this.hmParams.keySet());

        hs.retainAll(other.hmParams.keySet());

        return hs;
    }

    /**
     * Copy the values from the other data array into our own structure
     * 
     * @param other
     */
    public void copyValues(final DataArray other) {
        if ((other.hmParams == null) || (other.hmParams.size() == 0)) {
            return;
        }

        ensureAllocated(other.hmParams.size());

        for (final Map.Entry<String, Value> me : other.hmParams.entrySet()) {
            final String sName = me.getKey();
            final Value v = me.getValue();

            this.hmParams.put(sName, v.clone());
        }
    }

    /** get a string representation of this DataArray */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DataArray:");

        if ((this.hmParams == null) || (this.hmParams.size() == 0)) {
            sb.append("(empty)");
            return sb.toString();
        }

        for (final Map.Entry<String, Value> me : this.hmParams.entrySet()) {
            final String sName = me.getKey();
            final Value v = me.getValue();

            sb.append(sName).append('=').append(v.get()).append('\t');
        }

        return sb.toString();
    }

    private static final Map<Integer, AtomicInteger> tmSizeBunches = Collections
            .synchronizedMap(new TreeMap<Integer, AtomicInteger>());

    private static boolean bCollectStatistics = false;

    /**
     * Enable or disable the statistics collecting
     * 
     * @param bStatistics true to enable the collection, false to disable it
     * @see #getStats()
     * @see #resetStats()
     */
    public static void setCollectStatistics(final boolean bStatistics) {
        if (bCollectStatistics && !bStatistics) {
            resetStats();
        }

        bCollectStatistics = bStatistics;
    }

    /**
     * Get the statistics collection flag
     * 
     * @return statistics collection flag
     * @see #setCollectStatistics(boolean)
     */
    public static boolean getCollectStatistics() {
        return bCollectStatistics;
    }

    /**
     * Get the size distribution statistics
     * 
     * @return map of size->count
     */
    public static Map<Integer, AtomicInteger> getStats() {
        synchronized (tmSizeBunches) {
            return new TreeMap<Integer, AtomicInteger>(tmSizeBunches);
        }
    }

    /**
     * Clear the statistics
     */
    public static void resetStats() {
        tmSizeBunches.clear();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#finalize()
     */
    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        if (bCollectStatistics) {
            final Integer size = Integer.valueOf(this.hmParams == null ? -1 : this.hmParams.size());

            AtomicInteger ai = tmSizeBunches.get(size);

            if (ai == null) {
                ai = new AtomicInteger(0);
                tmSizeBunches.put(size, ai);
            }

            ai.incrementAndGet();
        }
    }

    /**
     * Debug method
     * 
     * @param args
     */
    public static void main(final String[] args) {
        DataArray da = new DataArray();

        da.divParams(120d);

        da.setParam("size", 134255);
        da.setParam("count", 1111);

        da.divParams(120d);

        System.err.println(da);
    }
}
