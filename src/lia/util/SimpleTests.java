/*
 * Created on Aug 30, 2010
 */
package lia.util;

import lia.Monitor.monitor.UnsignedLong;


/**
 *
 * @author ramiro
 */
public class SimpleTests {

    /**
     * @param args
     */
    public static void main(String[] args) {
//        UnsignedLong ul = UnsignedLong.valueOf("-1");
//        UnsignedLong ul = UnsignedLong.valueOf("18446744073709551615");
        UnsignedLong ul = UnsignedLong.valueOf("2");
        
//        UnsignedLong ul = UnsignedLong.valueOf("18446744073709551616");
        double d = Double.longBitsToDouble(ul.longValue());
        UnsignedLong rl = UnsignedLong.valueOf(Double.doubleToRawLongBits(d));
        System.out.println(" UL.longValue() " + ul.longValue() + " UL: " + ul + " d = " + d + " rl: " + rl); 
    }

}
