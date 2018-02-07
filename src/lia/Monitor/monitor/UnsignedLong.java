/*
 * Created on Aug 30, 2010
 */
package lia.Monitor.monitor;

import java.io.Serializable;
import java.math.BigInteger;

/**
 * 64bit unsigned long. MAX value is: 18446744073709551615 = 2^64 - 1
 * 
 * The values are always positive. Internally the value is kept on a long.</br></br> 
 * All *Value() functions may return <b>negative values</b>!
 * 
 * @author ramiro
 */
public class UnsignedLong extends Number implements Serializable, Comparable<UnsignedLong> {

    /**
     * 
     */
    private static final long serialVersionUID = 835768384765688469L;

    /**
     * MAX_VALUE accepted
     */
    private static final BigInteger MAX_VALUE = new BigInteger("18446744073709551615");

    private final long value;

    private UnsignedLong(final long value) {
        this.value = value;
    }

    /**
     * @param value
     * @return
     */
    public static final UnsignedLong valueOf(final String value) {
        return valueOf(new BigInteger(value));
    }

    public static UnsignedLong valueOf(BigInteger bi) {
        if (bi.compareTo(BigInteger.ZERO) < 0) {
            throw new NumberFormatException("Negative values not allowed: " + bi + " < 0");
        }

        if (bi.compareTo(MAX_VALUE) > 0) {
            throw new NumberFormatException("The value is too big for an unsigned 64bit value: " + bi + " > " + MAX_VALUE);
        }

        return new UnsignedLong(bi.longValue());
    }

    public static UnsignedLong valueOf(long value) {
        return new UnsignedLong(value);
    }

    @Override
    public int intValue() {
        return (int) value;
    }

    @Override
    public long longValue() {
        return value;
    }

    @Override
    public float floatValue() {
        return value;
    }

    /**
     * @return - <b>Double.longBitsToDouble(value)</b>
     */
    @Override
    public double doubleValue() {
        return Double.longBitsToDouble(value);
    }

    @Override
    public String toString() {
        if ((value > 0) && (value < Long.MAX_VALUE)) {
            return Long.toString(value);
        }
        byte[] bytes = new byte[8];
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) ((value >> ((7 - i) * 8)) & 0xFF);
        }
        BigInteger i64 = new BigInteger(1, bytes);
        return i64.toString();
    }

    public int compareTo(UnsignedLong o) {
        long other = o.value;
        for (int i = 63; i >= 0; i--) {
            if (((value >> i) & 1) != ((other >> i) & 1)) {
                if (((value >> i) & 1) != 0) {
                    return 1;
                }
                return -1;
            }
        }
        return 0;
    }

    @Override
    public int hashCode() {
        return 31 + (int) (value ^ (value >>> 32));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        return (value == ((UnsignedLong) obj).value);
    }

}
