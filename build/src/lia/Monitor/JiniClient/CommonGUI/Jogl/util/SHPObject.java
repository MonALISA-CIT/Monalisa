/*
 * Created on Jul 12, 2005 by Lucian Musat
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl.util;

/**
 * SHPObject - represents on shape (without attributes) read from the .shp file.
 */
public class SHPObject {
    // Shape types (nSHPType)
    public static final int SHPT_NULL = 0;
    public static final int SHPT_POINT = 1;
    public static final int SHPT_ARC = 3;
    public static final int SHPT_POLYGON = 5;
    public static final int SHPT_MULTIPOINT = 8;
    public static final int SHPT_POINTZ = 11;
    public static final int SHPT_ARCZ = 13;
    public static final int SHPT_POLYGONZ = 15;
    public static final int SHPT_MULTIPOINTZ = 18;
    public static final int SHPT_POINTM = 21;
    public static final int SHPT_ARCM = 23;
    public static final int SHPT_POLYGONM = 25;
    public static final int SHPT_MULTIPOINTM = 28;
    public static final int SHPT_MULTIPATCH = 31;
    // Part types - everything but SHPT_MULTIPATCH just uses
    // SHPP_RING.
    public static final int SHPP_TRISTRIP = 0;
    public static final int SHPP_TRIFAN = 1;
    public static final int SHPP_OUTERRING = 2;
    public static final int SHPP_INNERRING = 3;
    public static final int SHPP_FIRSTRING = 4;
    public static final int SHPP_RING = 5;
    
    public int nSHPType;
    public int nShapeId; /* -1 is unknown/unassigned */
    public int nParts;
    public int[] panPartStart;
    public int[] panPartType;
    public int nVertices;
    public double[] padfX;
    public double[] padfY;
    public double[] padfZ;
    public double[] padfM;
    public double dfXMin;
    public double dfYMin;
    public double dfZMin;
    public double dfMMin;
    public double dfXMax;
    public double dfYMax;
    public double dfZMax;
    public double dfMMax;
}
