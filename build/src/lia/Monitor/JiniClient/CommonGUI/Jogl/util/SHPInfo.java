/*
 * Created on Jul 12, 2005 by Lucian Musat
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl.util;

import java.io.RandomAccessFile;

public class SHPInfo {
    public RandomAccessFile fSHP;
    public RandomAccessFile fSHX;
    public int nShapeType; /* SHPT_* */
    public int nFileSize; /* SHP file */
    public int nRecords;
    public int nMaxRecords;
    public int[] panRecOffset;
    public int[] panRecSize;
    public double adBoundsMin[] = new double[4];
    public double adBoundsMax[] = new double[4];
    public boolean bUpdated;
    public byte[] pabyRec;
    public int nBufSize;
}
