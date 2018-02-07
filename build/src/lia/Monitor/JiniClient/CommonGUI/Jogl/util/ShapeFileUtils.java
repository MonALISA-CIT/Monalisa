package lia.Monitor.JiniClient.CommonGUI.Jogl.util;

/******************************************************************************
 * Copyright (c) 1999, Frank Warmerdam
 *
 * This software is available under the following "MIT Style" license,
 * or at the option of the licensee under the LGPL (see LICENSE.LGPL).  This
 * option is discussed in more detail in shapelib.html.
 *
 * --
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 ******************************************************************************
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.text.NumberFormat;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

import lia.Monitor.JiniClient.CommonGUI.Jogl.JoglPanel;
import lia.Monitor.monitor.AppConfig;
import lia.util.ntp.NTPDate;

public class ShapeFileUtils {
    public static final boolean DEBUG = AppConfig.getb("lia.Monitor.debug", false);

    public SHPInfo SHPOpen(String sLayer) {
        String sBasename;
        SHPInfo psSHP = null;
        try {
            psSHP = new SHPInfo();
            psSHP.bUpdated = false;
            sBasename = sLayer;
            int nPos = sBasename.lastIndexOf(".");
            if (nPos != -1)
                sBasename = sBasename.substring(0, nPos);
            String sFilename = sBasename;
            nPos = sBasename.lastIndexOf("/");
            if ( nPos!=-1 )
                sFilename = sBasename.substring(nPos+1);
            String sCacheTempDir = null;
            try {
            	sCacheTempDir = JoglPanel.globals.myMapsClassLoader.getCacheDir();
            } catch (Exception ex) {
                try {
                	sCacheTempDir = this.getClass().getClassLoader().getResource(sLayer).getPath();
                	sCacheTempDir = sCacheTempDir.substring(0, sCacheTempDir.length()-sLayer.length());
                } catch (Exception e) {
                	e.printStackTrace();
                	return null;
                }
            }
            InputStream is;
            OutputStream os;
            byte[] buff= new byte[1024];
            int nRead;
            File f;
            f = new File(sCacheTempDir+sFilename+".shp");
            if ( f.length() == 0 ) {
                long lStartTime = NTPDate.currentTimeMillis();
                
//                is = JoglPanel.globals.myMapsClassLoader.getResourceAsStream(sBasename + ".shp");
                is = Thread.currentThread().getContextClassLoader().getResourceAsStream(sBasename + ".shp");
                os = new FileOutputStream(sCacheTempDir+sFilename+".shp");
                while ( (nRead=is.read(buff))!=-1 ) {
                    os.write(buff, 0, nRead);
                }
                os.close();
                is.close();
                if(DEBUG) {
                    long lEndTime = NTPDate.currentTimeMillis();
                    System.out.println("Extract operation for "+sBasename + ".shp"+" took "+(lEndTime-lStartTime)+" miliseconds.");
                }
            }
            f = new File(sCacheTempDir+sFilename+".shx");
            if ( f.length() == 0 ) {//!f.isFile() ) {
                long lStartTime = NTPDate.currentTimeMillis();
//                is = JoglPanel.globals.myMapsClassLoader.getResourceAsStream(sBasename + ".shx");
                is = Thread.currentThread().getContextClassLoader().getResourceAsStream(sBasename + ".shx");
                os = new FileOutputStream(sCacheTempDir+sFilename+".shx");
                while ( (nRead=is.read(buff))!=-1 ) {
                    os.write(buff, 0, nRead);
                }
                os.close();
                is.close();
                if(DEBUG) {
                    long lEndTime = NTPDate.currentTimeMillis();
                    System.out.println("Extract operation for "+sBasename + ".shx"+" took "+(lEndTime-lStartTime)+" miliseconds.");
                }
            }
            psSHP.fSHP = new RandomAccessFile(sCacheTempDir+sFilename + ".shp", "r");
            psSHP.fSHX = new RandomAccessFile(sCacheTempDir+sFilename + ".shx", "r");
            
            byte[] pabyBuf;
            pabyBuf = new byte[100];
            psSHP.fSHP.read(pabyBuf);
            
            // Read the file size from the SHP file.
            psSHP.nFileSize = readBigEndianAsInt( pabyBuf, 24, 4) * 2;//pabyBuf[24] * 256 * 256 * 256 + pabyBuf[25]* 256 * 256 + pabyBuf[26] * 256 + pabyBuf[27])
            
            // / Read SHX file Header info
            psSHP.fSHX.read(pabyBuf);
            //System.out.println("header type: "+Integer.toHexString(pabyBuf[0])+" "+Integer.toHexString(pabyBuf[1])+" "+Integer.toHexString(pabyBuf[2])+" "+Integer.toHexString(pabyBuf[3])+" ");
            if (pabyBuf[0] != 0 || pabyBuf[1] != 0 || pabyBuf[2] != 0x27 || (pabyBuf[3] != 0x0a && pabyBuf[3] != 0x0d))
                throw new Exception("header type: "+Integer.toHexString(pabyBuf[0])+" "+Integer.toHexString(pabyBuf[1])+" "+Integer.toHexString(pabyBuf[2])+" "+Integer.toHexString(pabyBuf[3])+" "+"some exception occured reading the header");
            
            psSHP.nRecords = readBigEndianAsInt( pabyBuf, 24, 4);//pabyBuf[27] + pabyBuf[26] * 256 + pabyBuf[25]* 256 * 256 + pabyBuf[24] * 256 * 256 * 256;
            psSHP.nRecords = (psSHP.nRecords * 2 - 100) / 8;
            psSHP.nShapeType = pabyBuf[32];
            if (psSHP.nRecords < 0 || psSHP.nRecords > 256000000)
                throw new Exception( "this header appears to be corrupt.  Give up.");
            
            // Read the bounds.
//            System.out.print("abcd=");showBits(pabyBuf, 36, 8);
//            System.out.println("-180="+Long.toBinaryString(Double.doubleToLongBits(-180)));
            psSHP.adBoundsMin[0] = readLittleEndianAsDouble(pabyBuf, 36, 8);
            psSHP.adBoundsMin[1] = readLittleEndianAsDouble(pabyBuf, 44, 8);
            psSHP.adBoundsMax[0] = readLittleEndianAsDouble(pabyBuf, 52, 8);
            psSHP.adBoundsMax[1] = readLittleEndianAsDouble(pabyBuf, 60, 8);
            psSHP.adBoundsMin[2] = readLittleEndianAsDouble(pabyBuf, 68, 8);
            psSHP.adBoundsMax[2] = readLittleEndianAsDouble(pabyBuf, 76, 8);
            psSHP.adBoundsMin[3] = readLittleEndianAsDouble(pabyBuf, 84, 8);
            psSHP.adBoundsMax[3] = readLittleEndianAsDouble(pabyBuf, 92, 8);
            
            // Read the .shx file to get the offsets to each record in the .shp
            // file.
            psSHP.nMaxRecords = psSHP.nRecords;
            psSHP.panRecOffset = new int[max(1, psSHP.nMaxRecords)];
            psSHP.panRecSize = new int[max(1, psSHP.nMaxRecords)];
            
            pabyBuf = new byte[8 * max(1, psSHP.nRecords)];
            psSHP.fSHX.read(pabyBuf);
            
            int nOffset, nLength;
            for (int i = 0; i < psSHP.nRecords; i++) {
                nOffset = readBigEndianAsInt(pabyBuf, i * 8, 4);
                nLength = readBigEndianAsInt(pabyBuf, i * 8 + 4, 4);
                psSHP.panRecOffset[i] = nOffset * 2;
                psSHP.panRecSize[i] = nLength * 2;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            psSHP = null;
        } catch (IOException e) {
            e.printStackTrace();
            psSHP = null;
        } catch (Exception e) {
            e.printStackTrace();
            psSHP = null;
        }
        return psSHP;
    }
    
    /**
     * Close the .shp and .shx files.
     * 
     * @param psSHP
     */
    public void SHPClose(SHPInfo psSHP) {
        try {
            psSHP.fSHX.close();
            psSHP.fSHP.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Fetch general information about the shape file.
     */
    public void SHPGetInfo(SHPInfo psSHP, int[] pnEntitiesAndShapeType,
            double[] padfMinBound, double[] padfMaxBound) {
        if (pnEntitiesAndShapeType != null)
            pnEntitiesAndShapeType[0] = psSHP.nRecords;
        if (pnEntitiesAndShapeType != null)
            pnEntitiesAndShapeType[1] = psSHP.nShapeType;
        for (int i = 0; i < 4; i++) {
            if (padfMinBound != null)
                padfMinBound[i] = psSHP.adBoundsMin[i];
            if (padfMaxBound != null)
                padfMaxBound[i] = psSHP.adBoundsMax[i];
        }
    }
    
    /**
     * Recompute the extents of a shape. Automatically done by
     * SHPCreateObject().
     */
    public void SHPComputeExtents(SHPObject psObject) {
        int i;
        // Build extents for this object.
        if (psObject.nVertices > 0) {
            psObject.dfXMin = psObject.dfXMax = psObject.padfX[0];
            psObject.dfYMin = psObject.dfYMax = psObject.padfY[0];
            psObject.dfZMin = psObject.dfZMax = psObject.padfZ[0];
            psObject.dfMMin = psObject.dfMMax = psObject.padfM[0];
        }
        for (i = 0; i < psObject.nVertices; i++) {
            psObject.dfXMin = min(psObject.dfXMin, psObject.padfX[i]);
            psObject.dfYMin = min(psObject.dfYMin, psObject.padfY[i]);
            psObject.dfZMin = min(psObject.dfZMin, psObject.padfZ[i]);
            psObject.dfMMin = min(psObject.dfMMin, psObject.padfM[i]);
            
            psObject.dfXMax = max(psObject.dfXMax, psObject.padfX[i]);
            psObject.dfYMax = max(psObject.dfYMax, psObject.padfY[i]);
            psObject.dfZMax = max(psObject.dfZMax, psObject.padfZ[i]);
            psObject.dfMMax = max(psObject.dfMMax, psObject.padfM[i]);
        }
    }
    
    /**
     * Create a shape object. It should be freed with SHPDestroyObject().
     */
    public SHPObject SHPCreateObject(int nSHPType, int nShapeId, int nParts,
            int[] panPartStart, int[] panPartType, int nVertices,
            double[] padfX, double[] padfY, double[] padfZ, double[] padfM) {
        SHPObject psObject;
        int i;
        boolean bHasM, bHasZ;
        
        psObject = new SHPObject();
        psObject.nSHPType = nSHPType;
        psObject.nShapeId = nShapeId;
        
        // Establish whether this shape type has M, and Z values. */
        if (nSHPType == SHPObject.SHPT_ARCM
                || nSHPType == SHPObject.SHPT_POINTM
                || nSHPType == SHPObject.SHPT_POLYGONM
                || nSHPType == SHPObject.SHPT_MULTIPOINTM) {
            bHasM = true;
            bHasZ = false;
        } else if (nSHPType == SHPObject.SHPT_ARCZ
                || nSHPType == SHPObject.SHPT_POINTZ
                || nSHPType == SHPObject.SHPT_POLYGONZ
                || nSHPType == SHPObject.SHPT_MULTIPOINTZ
                || nSHPType == SHPObject.SHPT_MULTIPATCH) {
            bHasM = true;
            bHasZ = false;
        } else {
            bHasM = false;
            bHasZ = false;
        }
        
        // Capture parts. Note that part type is optional, and
        // defaults to ring.
        if (nSHPType == SHPObject.SHPT_ARC
                || nSHPType == SHPObject.SHPT_POLYGON
                || nSHPType == SHPObject.SHPT_ARCM
                || nSHPType == SHPObject.SHPT_POLYGONM
                || nSHPType == SHPObject.SHPT_ARCZ
                || nSHPType == SHPObject.SHPT_POLYGONZ
                || nSHPType == SHPObject.SHPT_MULTIPATCH) {
            psObject.nParts = max(1, nParts);
            psObject.panPartStart = new int[psObject.nParts];
            psObject.panPartType = new int[psObject.nParts];
            psObject.panPartStart[0] = 0;
            psObject.panPartType[0] = SHPObject.SHPP_RING;
            for (i = 0; i < nParts; i++) {
                psObject.panPartStart[i] = panPartStart[i];
                if (panPartType != null)
                    psObject.panPartType[i] = panPartType[i];
                else
                    psObject.panPartType[i] = SHPObject.SHPP_RING;
            }
        }
        // Capture vertices. Note that Z and M are optional, but X and
        // Y are not.
        if (nVertices > 0) {
            psObject.padfX = new double[nVertices];
            psObject.padfY = new double[nVertices];
            psObject.padfZ = new double[nVertices];
            psObject.padfM = new double[nVertices];
            // assert( padfX != null );
            // assert( padfY != null );
            for (i = 0; i < nVertices; i++) {
                psObject.padfX[i] = padfX[i];
                psObject.padfY[i] = padfY[i];
                if (padfZ != null && bHasZ)
                    psObject.padfZ[i] = padfZ[i];
                if (padfM != null && bHasM)
                    psObject.padfM[i] = padfM[i];
            }
        }
        // Compute the extents.
        psObject.nVertices = nVertices;
        SHPComputeExtents(psObject);
        return (psObject);
    }
    
    /**
     * Create a simple (common) shape object. Destroy with SHPDestroyObject().
     */
    public SHPObject SHPCreateSimpleObject(int nSHPType, int nVertices,
            double[] padfX, double[] padfY, double[] padfZ) {
        return (SHPCreateObject(nSHPType, -1, 0, null, null, nVertices, padfX,
                padfY, padfZ, null));
    }
    
    /**
     * Read the vertices, parts, and other non-attribute information for one
     * shape.
     */
    public SHPObject SHPReadObject(SHPInfo psSHP, int hEntity) {
        SHPObject psShape;
        
        // Validate the record/entity number.
        if (hEntity < 0 || hEntity >= psSHP.nRecords)
            return (null);
        // Ensure our record buffer is large enough.
        if (psSHP.panRecSize[hEntity] + 8 > psSHP.nBufSize) {
            psSHP.nBufSize = psSHP.panRecSize[hEntity] + 8;
            psSHP.pabyRec = new byte[psSHP.nBufSize];
        }
        // Read the record.
        try {
            psSHP.fSHP.seek(psSHP.panRecOffset[hEntity]);
            psSHP.fSHP.read(psSHP.pabyRec, 0, psSHP.panRecSize[hEntity] + 8);
        } catch (IOException ioex) {
            ioex.printStackTrace();
            return null;
        }
        // Allocate and minimally initialize the object.
        psShape = new SHPObject();
        psShape.nShapeId = hEntity;
        psShape.nSHPType = readLittleEndianAsInt(psSHP.pabyRec, 8, 4);
        // Extract vertices for a Polygon or Arc.
        if (psShape.nSHPType == SHPObject.SHPT_POLYGON
                || psShape.nSHPType == SHPObject.SHPT_ARC
                || psShape.nSHPType == SHPObject.SHPT_POLYGONZ
                || psShape.nSHPType == SHPObject.SHPT_POLYGONM
                || psShape.nSHPType == SHPObject.SHPT_ARCZ
                || psShape.nSHPType == SHPObject.SHPT_ARCM
                || psShape.nSHPType == SHPObject.SHPT_MULTIPATCH) {
            int nPoints, nParts;
            int i, nOffset;
            // Get the X/Y bounds.
            psShape.dfXMin = readLittleEndianAsDouble(psSHP.pabyRec, 8 + 4, 8);
            psShape.dfYMin = readLittleEndianAsDouble(psSHP.pabyRec, 8 + 12, 8);
            psShape.dfXMax = readLittleEndianAsDouble(psSHP.pabyRec, 8 + 20, 8);
            psShape.dfYMax = readLittleEndianAsDouble(psSHP.pabyRec, 8 + 28, 8);
            // Extract part/point count, and build vertex and part arrays
            // to proper size.
            nPoints = readLittleEndianAsInt(psSHP.pabyRec, 40 + 8, 4);
            nParts = readLittleEndianAsInt(psSHP.pabyRec, 36 + 8, 4);
            psShape.nVertices = nPoints;
            psShape.padfX = new double[nPoints];
            psShape.padfY = new double[nPoints];
            psShape.padfZ = new double[nPoints];
            psShape.padfM = new double[nPoints];
            psShape.nParts = nParts;
            psShape.panPartStart = new int[nParts];
            psShape.panPartType = new int[nParts];
            for (i = 0; i < nParts; i++)
                psShape.panPartType[i] = SHPObject.SHPP_RING;
            // Copy out the part array from the record.
            for (i = 0; i < nParts; i++)
                psShape.panPartStart[i] = readLittleEndianAsInt(psSHP.pabyRec,
                        44 + 8 + 4 * i, 4);
            nOffset = 44 + 8 + 4 * nParts;
            // If this is a multipatch, we will also have parts types.
            if (psShape.nSHPType == SHPObject.SHPT_MULTIPATCH) {
                for (i = 0; i < nParts; i++)
                    psShape.panPartType[i] = readLittleEndianAsInt(
                            psSHP.pabyRec, nOffset + 4 * i, 4);
                nOffset += 4 * nParts;
            }
            // Copy out the vertices from the record.
            for (i = 0; i < nPoints; i++) {
                psShape.padfX[i] = readLittleEndianAsDouble(psSHP.pabyRec,
                        nOffset + i * 16, 8);
                psShape.padfY[i] = readLittleEndianAsDouble(psSHP.pabyRec,
                        nOffset + i * 16 + 8, 8);
            }
            nOffset += 16 * nPoints;
            // If we have a Z coordinate, collect that now.
            if (psShape.nSHPType == SHPObject.SHPT_POLYGONZ
                    || psShape.nSHPType == SHPObject.SHPT_ARCZ
                    || psShape.nSHPType == SHPObject.SHPT_MULTIPATCH) {
                psShape.dfZMin = readLittleEndianAsDouble(psSHP.pabyRec,
                        nOffset, 8);
                psShape.dfZMax = readLittleEndianAsDouble(psSHP.pabyRec,
                        nOffset + 8, 8);
                for (i = 0; i < nPoints; i++)
                    psShape.padfZ[i] = readLittleEndianAsDouble(psSHP.pabyRec,
                            nOffset + 16 + i * 8, 8);
                nOffset += 16 + 8 * nPoints;
            }
            // If we have a M measure value, then read it now. We assume
            // that the measure can be present for any shape if the size is
            // big enough, but really it will only occur for the Z shapes
            // (options), and the M shapes.
            if (psSHP.panRecSize[hEntity] + 8 >= nOffset + 16 + 8 * nPoints) {
                psShape.dfMMin = readLittleEndianAsDouble(psSHP.pabyRec,
                        nOffset, 8);
                psShape.dfMMax = readLittleEndianAsDouble(psSHP.pabyRec,
                        nOffset + 8, 8);
                for (i = 0; i < nPoints; i++)
                    psShape.padfM[i] = readLittleEndianAsDouble(psSHP.pabyRec,
                            nOffset + 16 + i * 8, 8);
            }
        }
        // Extract vertices for a MultiPoint.
        else if (psShape.nSHPType == SHPObject.SHPT_MULTIPOINT
                || psShape.nSHPType == SHPObject.SHPT_MULTIPOINTM
                || psShape.nSHPType == SHPObject.SHPT_MULTIPOINTZ) {
            int nPoints;
            int i, nOffset;
            nPoints = readLittleEndianAsInt(psSHP.pabyRec, 44, 4);
            psShape.nVertices = nPoints;
            psShape.padfX = new double[nPoints];
            psShape.padfY = new double[nPoints];
            psShape.padfZ = new double[nPoints];
            psShape.padfM = new double[nPoints];
            for (i = 0; i < nPoints; i++) {
                psShape.padfX[i] = readLittleEndianAsDouble(psSHP.pabyRec,
                        48 + i * 16, 8);
                psShape.padfY[i] = readLittleEndianAsDouble(psSHP.pabyRec,
                        48 + i * 16 + 8, 8);
            }
            nOffset = 48 + 16 * nPoints;
            // Get the X/Y bounds.
            psShape.dfXMin = readLittleEndianAsDouble(psSHP.pabyRec, 8 + 4, 8);
            psShape.dfYMin = readLittleEndianAsDouble(psSHP.pabyRec, 8 + 12, 8);
            psShape.dfXMax = readLittleEndianAsDouble(psSHP.pabyRec, 8 + 20, 8);
            psShape.dfYMax = readLittleEndianAsDouble(psSHP.pabyRec, 8 + 28, 8);
            // If we have a Z coordinate, collect that now.
            if (psShape.nSHPType == SHPObject.SHPT_MULTIPOINTZ) {
                psShape.dfZMin = readLittleEndianAsDouble(psSHP.pabyRec,
                        nOffset, 8);
                psShape.dfZMax = readLittleEndianAsDouble(psSHP.pabyRec,
                        nOffset + 8, 8);
                for (i = 0; i < nPoints; i++)
                    psShape.padfZ[i] = readLittleEndianAsDouble(psSHP.pabyRec,
                            nOffset + 16 + i * 8, 8);
                nOffset += 16 + 8 * nPoints;
            }
            // If we have a M measure value, then read it now. We assume
            // that the measure can be present for any shape if the size is
            // big enough, but really it will only occur for the Z shapes
            // (options), and the M shapes.
            if (psSHP.panRecSize[hEntity] + 8 >= nOffset + 16 + 8 * nPoints) {
                psShape.dfMMin = readLittleEndianAsDouble(psSHP.pabyRec,
                        nOffset, 8);
                psShape.dfMMax = readLittleEndianAsDouble(psSHP.pabyRec,
                        nOffset + 8, 8);
                for (i = 0; i < nPoints; i++)
                    psShape.padfM[i] = readLittleEndianAsDouble(psSHP.pabyRec,
                            nOffset + 16 + i * 8, 8);
            }
        }
        // Extract vertices for a point.
        else if (psShape.nSHPType == SHPObject.SHPT_POINT
                || psShape.nSHPType == SHPObject.SHPT_POINTM
                || psShape.nSHPType == SHPObject.SHPT_POINTZ) {
            int nOffset;
            psShape.nVertices = 1;
            psShape.padfX = new double[1];
            psShape.padfY = new double[1];
            psShape.padfZ = new double[1];
            psShape.padfM = new double[1];
            psShape.padfX[0] = readLittleEndianAsDouble(psSHP.pabyRec, 12, 8);
            psShape.padfY[0] = readLittleEndianAsDouble(psSHP.pabyRec, 20, 8);
            nOffset = 20 + 8;
            // If we have a Z coordinate, collect that now.
            if (psShape.nSHPType == SHPObject.SHPT_POINTZ) {
                psShape.padfZ[0] = readLittleEndianAsDouble(psSHP.pabyRec,
                        nOffset, 8);
                nOffset += 8;
            }
            // If we have a M measure value, then read it now. We assume
            // that the measure can be present for any shape if the size is
            // big enough, but really it will only occur for the Z shapes
            // (options), and the M shapes.
            if (psSHP.panRecSize[hEntity] + 8 >= nOffset + 8)
                psShape.padfM[0] = readLittleEndianAsDouble(psSHP.pabyRec,
                        nOffset, 8);
            // Since no extents are supplied in the record, we will apply
            // them from the single vertex.
            psShape.dfXMin = psShape.dfXMax = psShape.padfX[0];
            psShape.dfYMin = psShape.dfYMax = psShape.padfY[0];
            psShape.dfZMin = psShape.dfZMax = psShape.padfZ[0];
            psShape.dfMMin = psShape.dfMMax = psShape.padfM[0];
        }
        return (psShape);
    }
    
    public String SHPTypeName(int nSHPType) {
        switch (nSHPType) {
        case SHPObject.SHPT_NULL:
            return "NullShape";
        case SHPObject.SHPT_POINT:
            return "Point";
        case SHPObject.SHPT_ARC:
            return "Arc";
        case SHPObject.SHPT_POLYGON:
            return "Polygon";
        case SHPObject.SHPT_MULTIPOINT:
            return "MultiPoint";
        case SHPObject.SHPT_POINTZ:
            return "PointZ";
        case SHPObject.SHPT_ARCZ:
            return "ArcZ";
        case SHPObject.SHPT_POLYGONZ:
            return "PolygonZ";
        case SHPObject.SHPT_MULTIPOINTZ:
            return "MultiPointZ";
        case SHPObject.SHPT_POINTM:
            return "PointM";
        case SHPObject.SHPT_ARCM:
            return "ArcM";
        case SHPObject.SHPT_POLYGONM:
            return "PolygonM";
        case SHPObject.SHPT_MULTIPOINTM:
            return "MultiPointM";
        case SHPObject.SHPT_MULTIPATCH:
            return "MultiPatch";
        default:
            return "UnknownShapeType";
        }
    }
    
    public String SHPPartTypeName(int nPartType) {
        switch (nPartType) {
        case SHPObject.SHPP_TRISTRIP:
            return "TriangleStrip";
        case SHPObject.SHPP_TRIFAN:
            return "TriangleFan";
        case SHPObject.SHPP_OUTERRING:
            return "OuterRing";
        case SHPObject.SHPP_INNERRING:
            return "InnerRing";
        case SHPObject.SHPP_FIRSTRING:
            return "FirstRing";
        case SHPObject.SHPP_RING:
            return "Ring";
        default:
            return "UnknownPartType";
        }
    }
    
    public void SHPDestroyObject(SHPObject psShape) {
    }
    
    /** ********************************************************************* */
    /* SHPRewindObject() */
    /*                                                                      */
    /* Reset the winding of polygon objects to adhere to the */
    /* specification. */
    /** ********************************************************************* */
    
    public int SHPRewindObject( SHPInfo hSHP, SHPObject psObject )
    
    {
        int  iOpRing, bAltered = 0;
        // Do nothing if this is not a polygon object.
        if( psObject.nSHPType != SHPObject.SHPT_POLYGON
                && psObject.nSHPType != SHPObject.SHPT_POLYGONZ
                && psObject.nSHPType != SHPObject.SHPT_POLYGONM )
            return 0;
        // Process each of the rings.
        for( iOpRing = 0; iOpRing < psObject.nParts; iOpRing++ )
        {
            boolean bInner;
            int      iVert, nVertCount, nVertStart, iCheckRing;
            double   dfSum, dfTestX, dfTestY;
            // Determine if this ring is an inner ring or an outer ring
            // relative to all the other rings. For now we assume the
            // first ring is outer and all others are inner, but eventually
            // we need to fix this to handle multiple island polygons and
            // unordered sets of rings.
            dfTestX = psObject.padfX[psObject.panPartStart[iOpRing]];
            dfTestY = psObject.padfY[psObject.panPartStart[iOpRing]];
            
            bInner = false;
            for( iCheckRing = 0; iCheckRing < psObject.nParts; iCheckRing++ )
            {
                int iEdge;
                if( iCheckRing == iOpRing )
                    continue;
                nVertStart = psObject.panPartStart[iCheckRing];
                if( iCheckRing == psObject.nParts-1 )
                    nVertCount = psObject.nVertices - psObject.panPartStart[iCheckRing];
                else
                    nVertCount = psObject.panPartStart[iCheckRing+1] - psObject.panPartStart[iCheckRing];
                for( iEdge = 0; iEdge < nVertCount; iEdge++ ) {
                    int iNext;
                    if( iEdge < nVertCount-1 )
                        iNext = iEdge+1;
                    else
                        iNext = 0;
                    if( (psObject.padfY[iEdge+nVertStart] < dfTestY && psObject.padfY[iNext+nVertStart] >= dfTestY)
                            || (psObject.padfY[iNext+nVertStart] < dfTestY && psObject.padfY[iEdge+nVertStart] >= dfTestY) )
                    {
                        if( psObject.padfX[iEdge+nVertStart] 
                                           + (dfTestY - psObject.padfY[iEdge+nVertStart])
                                           / (psObject.padfY[iNext+nVertStart]
                                                             - psObject.padfY[iEdge+nVertStart])
                                                             * (psObject.padfX[iNext+nVertStart]
                                                                               - psObject.padfX[iEdge+nVertStart]) < dfTestX )
                            bInner = !bInner;
                    }
                }
            }
            // Determine the current order of this ring so we will know if
            // it has to be reversed.
            nVertStart = psObject.panPartStart[iOpRing];
            if( iOpRing == psObject.nParts-1 )
                nVertCount = psObject.nVertices - psObject.panPartStart[iOpRing];
            else
                nVertCount = psObject.panPartStart[iOpRing+1] - psObject.panPartStart[iOpRing];
            dfSum = 0.0;
            for( iVert = nVertStart; iVert < nVertStart+nVertCount-1; iVert++ )
            {
                dfSum += psObject.padfX[iVert] * psObject.padfY[iVert+1]
                                                                - psObject.padfY[iVert] * psObject.padfX[iVert+1];
            }
            dfSum += psObject.padfX[iVert] * psObject.padfY[nVertStart]
                                                            - psObject.padfY[iVert] * psObject.padfX[nVertStart];
            // Reverse if necessary.
            if( (dfSum < 0.0 && bInner) || (dfSum > 0.0 && !bInner) )
            {
                int   i;
                bAltered++;
                for( i = 0; i < nVertCount/2; i++ ) {
                    double dfSaved;
                    /* Swap X */
                    dfSaved = psObject.padfX[nVertStart+i];
                    psObject.padfX[nVertStart+i] = psObject.padfX[nVertStart+nVertCount-i-1];
                    psObject.padfX[nVertStart+nVertCount-i-1] = dfSaved;
                    /* Swap Y */
                    dfSaved = psObject.padfY[nVertStart+i];
                    psObject.padfY[nVertStart+i] = psObject.padfY[nVertStart+nVertCount-i-1];
                    psObject.padfY[nVertStart+nVertCount-i-1] = dfSaved;
                    /* Swap Z */
                    if( psObject.padfZ!=null ) {
                        dfSaved = psObject.padfZ[nVertStart+i];
                        psObject.padfZ[nVertStart+i] = psObject.padfZ[nVertStart+nVertCount-i-1];
                        psObject.padfZ[nVertStart+nVertCount-i-1] = dfSaved;
                    }
                    /* Swap M */
                    if( psObject.padfM!=null ) {
                        dfSaved = psObject.padfM[nVertStart+i];
                        psObject.padfM[nVertStart+i] = psObject.padfM[nVertStart+nVertCount-i-1];
                        psObject.padfM[nVertStart+nVertCount-i-1] = dfSaved;
                    }
                }
            }
        }
        return bAltered;
    }    
    
    public static int max(int a, int b) {
        return a >= b ? a : b;
    }
    
    public static int min(int a, int b) {
        return a <= b ? a : b;
    }
    
    public static double min(double a, double b) {
        return a <= b ? a : b;
    }
    
    public static double max(double a, double b) {
        return a >= b ? a : b;
    }
    
    /**
     * reconstructs an double from a buffer of bytes<br>
     * it assumes that the buffer has at least startPos+len bytes without
     * checking
     * 
     * @param buff
     * @param startPos
     * @param len
     * @return integer value of four bytes read in little endian order
     */
    public static double readLittleEndianAsDouble(byte buff[], int startPos, int len) {
        double retVal = 0;
        long aux = 0;
        for (int i = len - 1; i >= 0; i--)
            aux = aux * 256 + ((long) buff[startPos + i] & 0xff);
        retVal = Double.longBitsToDouble(aux);
        return retVal;
    }
  
    void showBits(byte[] buff, int offset, int len) {
        
        for (int i=offset+len-1; i>=offset; i--) {
            byte b = buff[i];
            //System.out.print("("+b+")");
            for (int j=7; j>=0; j--) {
                int x = (int)((b>>j) & 0x01);
                System.out.print(""+x);
            }
            //System.out.print(" ");
        }
        System.out.println("");
    }
    
    /**
     * reconstructs an integer from a buffer of bytes<br>
     * it assumes that the buffer has at least startPos+len bytes without
     * checking
     * 
     * @param buff
     * @param startPos
     * @param len
     * @return integer value of four bytes read in little endian order
     */
    public static int readLittleEndianAsInt(byte buff[], int startPos, int len) {
        int retVal = 0;
        for (int i = len - 1; i >= 0; i--)
            retVal = retVal * 256 +  ((int) buff[startPos + i] & 0xff);
        return retVal;
    }
    
    /**
     * reconstructs an double from a buffer of bytes<br>
     * it assumes that the buffer has at least startPos+len bytes without
     * checking
     * 
     * @param buff
     * @param startPos
     * @return integer value of four bytes read in big endian order
     */
    public static double readBigEndianAsDouble(byte buff[], int startPos, int len) {
        double retVal = 0;
        long aux = 0;
        for (int i = 0; i <= len - 1; i++)
            aux = aux * 256 + ((long) buff[startPos + i] & 0xff);
        retVal = Double.longBitsToDouble(aux);
        return retVal;
    }
    
    /**
     * reconstructs an integer from a buffer of bytes<br>
     * it assumes that the buffer has at least startPos+len bytes without
     * checking
     * 
     * @param buff
     * @param startPos
     * @return integer value of four bytes read in big endian order
     */
    public static int readBigEndianAsInt(byte buff[], int startPos, int len) {
        int retVal = 0;
        for (int i = 0; i <= len - 1; i++)
            retVal = retVal * 256 + ((int) buff[startPos + i] & 0xff);
        return retVal;
    }
    
    public static void main(String[] argv) {
        ShapeFileUtils shpUtils = new ShapeFileUtils();
        
        //set cache dir
//        ShapeFileUtils.class.getClassLoader().getResource(name)
//        JoglPanel.globals.myMapsClassLoader.setCacheDir("/home/mluc/Monalisa/src/");
        String sShapeFileName = "lia/images/joglpanel/shapefile/cntry98.shp";//Thread.currentThread().getContextClassLoader().getResource("lia/images/joglpanel/shapefile/cntry98.shp").getFile();//"/home/mluc/Monalisa/bin/lia/images/joglpanel/shapefile/cntry98.shp";//
        if(DEBUG) {
            System.out.println("sShapeFileName="+sShapeFileName);
        }
        SHPInfo hSHP;
        /* -------------------------------------------------------------------- */
        /*      Open the passed shapefile.                                      */
        /* -------------------------------------------------------------------- */
        hSHP = shpUtils.SHPOpen( sShapeFileName);
        if( hSHP == null )
        {
            System.out.println( "Unable to open: "+sShapeFileName );
            return;
        }
        int     nShapeType, nEntities, i, iPart, nInvalidCount=0;
        boolean bValidate = true;
        int []pnEntitiesAndShapeType = new int[2];
//        String sPlus;
        double  []adfMinBound = new double[4];
        double []adfMaxBound = new double[4];
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(2);
        /* -------------------------------------------------------------------- */
        /*      Print out the file bounds.                                      */
        /* -------------------------------------------------------------------- */
        shpUtils.SHPGetInfo( hSHP, pnEntitiesAndShapeType, adfMinBound, adfMaxBound );
        nEntities = pnEntitiesAndShapeType[0];
        nShapeType = pnEntitiesAndShapeType[1];
        if(DEBUG) {
            System.out.println( "Shapefile Type: "+shpUtils.SHPTypeName( nShapeType )+"   # of Shapes: "+ nEntities );
            
            System.out.println( "File Bounds: ("+nf.format(adfMinBound[0])+","+adfMinBound[1]+","+adfMinBound[2]+","+adfMinBound[3]+")\n"
                    +"         to  ("+adfMaxBound[0]+","+adfMaxBound[1]+","+adfMaxBound[2]+","+adfMaxBound[3]+")");
        }
        
        //create list of objects
        SHPObject []shapes = new SHPObject[nEntities];
        /* -------------------------------------------------------------------- */
        /*  Skim over the list of shapes, printing all the vertices.    */
        /* -------------------------------------------------------------------- */
        for( i = 0; i < nEntities; i++ )
        {
            int     j;
            SHPObject   psShape;
            
            psShape = shpUtils.SHPReadObject( hSHP, i );
            shapes[i] = psShape;
            if(DEBUG) {
                System.out.println( "\nShape:"+i+" ("+shpUtils.SHPTypeName(psShape.nSHPType)+")  nVertices="+psShape.nVertices+", nParts="+psShape.nParts+"\n"
                        +"  Bounds:("+psShape.dfXMin+","+psShape.dfYMin+", "+psShape.dfZMin+", "+psShape.dfMMin+")\n"
                        +"      to ("+psShape.dfXMax+","+psShape.dfYMax+", "+psShape.dfZMax+", "+psShape.dfMMax+")");
            }
            
            for( j = 0, iPart = 1; j < psShape.nVertices; j++ )
            {
//                String sPartType = "";
                
//                if( j == 0 && psShape.nParts > 0 )
//                    sPartType = shpUtils.SHPPartTypeName( psShape.panPartType[0] );
                
                if( iPart < psShape.nParts
                        && psShape.panPartStart[iPart] == j )
                {
//                    sPartType = shpUtils.SHPPartTypeName( psShape.panPartType[iPart] );
                    iPart++;
//                    sPlus = "+";
                }
//                else 
//                    sPlus = " ";
                
//                System.out.println("   "+sPlus+" ("+psShape.padfX[j]+","+psShape.padfY[j]+", "+psShape.padfZ[j]+", "+psShape.padfM[j]+") "+sPartType);
            }

            if( bValidate )
            {
                int nAltered = shpUtils.SHPRewindObject( hSHP, psShape );
                
                if( nAltered > 0 )
                {
                    System.out.println( "  "+nAltered+" rings wound in the wrong direction.");
                    nInvalidCount++;
                }
            }
//            shpUtils.SHPDestroyObject( psShape );
        }
        shpUtils.SHPClose( hSHP );
        
        if( bValidate )
        {
            System.out.println( nInvalidCount+" object has invalid ring orderings.");
        }

        MainClass mc = new MainClass("Draw Test", shapes, shpUtils);
        //make frame viewable
        mc.setVisible(true);
    }
}

class MainClass extends JFrame
                implements ComponentListener 
{
    /**
     * Comment for <code>serialVersionUID</code>
     */
    private static final long serialVersionUID = 1L;

    private JLabel jlb = new JLabel();
    private SHPObject shapes[];
//    private ShapeFileUtils shpUtils;
    
    public MainClass( String title, SHPObject []shapes, ShapeFileUtils shpUtils)
    {
        //set window title
        super(title);
        this.shapes = shapes;
//        this.shpUtils = shpUtils;
        //set action for "x" button
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        //window can't be resized
        this.setResizable(true);
        //set preferred dimensions for window
        //set size for the frame
        setSize(new Dimension(600,300));
        //pack();
        //get screen dimensions
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        //set location relative to screen
        setLocation(
                (screenSize.width - getWidth()) / 2,
                (screenSize.height - getHeight()) / 2
        );
        //set a specific layout that has areas that can be filled with components
        getContentPane().setLayout(new BorderLayout());
        //add image
        jlb.setSize( -1, -1);
        getContentPane().add(jlb, BorderLayout.CENTER);
        addComponentListener(this);
    }
    
    private Dimension convert2Image( double x, double y, int maxWidth, int maxHeight, Dimension dim)
    {
        //pp the limits for x and y are -180 to 180 and -90 to 90
        if ( dim == null )
            dim = new Dimension();
        dim.width = (int)((x+180.0)*maxWidth/360.0);
        dim.height = (int)((90.0-y)*maxHeight/180.0);
        return dim;
    }

    public void componentResized(ComponentEvent arg0) {
        //draw image
        //first, create image
        int nWidth, nHeight;
        nWidth = jlb.getWidth();
        nHeight = jlb.getHeight();
        if ( nWidth<40 || nHeight<20 )
            return;
        BufferedImage bi = new BufferedImage( nWidth, nHeight, BufferedImage.TYPE_INT_RGB);
        //set label with image as picture
        jlb.setIcon(new ImageIcon(bi));
        //get graphics object from image
        Graphics g = bi.getGraphics();
        //set drawing color
        g.setColor(Color.WHITE);
        //draw a line
        //g.drawLine(0, 0, nWidth, nHeight);
//        for( int i=0; i<20; i++)
//            for( int j=0; j<20; j++)
//                bi.setRGB( i, j, /*red*/(255<<16)+/*green*/(45<<8)+89);
        int     j, iPart;
        SHPObject   psShape;
//        String sPlus;
        Dimension antPoint=null, curPoint=null;
        for( int i = 0; i < shapes.length; i++ )
        {
            psShape = shapes[i];
//            System.out.println( "\nShape:"+i+" ("+shpUtils.SHPTypeName(psShape.nSHPType)+")  nVertices="+psShape.nVertices+", nParts="+psShape.nParts+"\n"
//                    +"  Bounds:("+psShape.dfXMin+","+psShape.dfYMin+", "+psShape.dfZMin+", "+psShape.dfMMin+")\n"
//                    +"      to ("+psShape.dfXMax+","+psShape.dfYMax+", "+psShape.dfZMax+", "+psShape.dfMMax+")");
            for( j = 0, iPart = 1; j < psShape.nVertices; j++ ) {
//                String sPartType = "";
                antPoint = curPoint;
                curPoint = convert2Image( psShape.padfX[j], psShape.padfY[j], nWidth, nHeight, curPoint);
//                if( j == 0 && psShape.nParts > 0 )
//                    sPartType = shpUtils.SHPPartTypeName( psShape.panPartType[0] );
                if( iPart < psShape.nParts && psShape.panPartStart[iPart] == j ) {
//                    sPartType = shpUtils.SHPPartTypeName( psShape.panPartType[iPart] );
                    iPart++;
//                    sPlus = "+";
                    antPoint = null;
                } //else
//                    sPlus = " ";
//                System.out.println("   "+sPlus+" ("+psShape.padfX[j]+","+psShape.padfY[j]+", "+psShape.padfZ[j]+", "+psShape.padfM[j]+") "+sPartType);
                if ( antPoint!=null && curPoint!=null )
                    g.drawLine( antPoint.width, antPoint.height, curPoint.width, curPoint.height);
            }
        }
    }

    public void componentMoved(ComponentEvent arg0) {}
    public void componentShown(ComponentEvent arg0) {}
    public void componentHidden(ComponentEvent arg0) {}
}
