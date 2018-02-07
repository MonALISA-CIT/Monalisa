/*
 * Created on Nov 16, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl.util;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLException;

/*
 * Copyright (c) 2003 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * - Redistribution of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistribution in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES,
 * INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN
 * MIDROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN OR
 * ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR
 * DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE
 * DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
 * ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF
 * SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that this software is not designed or intended for use
 * in the design, construction, operation or maintenance of any nuclear
 * facility.
 *
 * Sun gratefully acknowledges that this software was originally authored
 * and developed by Kenneth Bradley Russell and Christopher John Kline.
 */

/**
 *
 * Nov 16, 2004 - 6:20:48 PM
 */
public class MyGLUT {
    /** Subset of the routines provided by the GLUT interface. Note the
    signatures of many of the methods are necessarily different than
    the corresponding C version. A GLUT object must only be used from
    one particular thread at a time. <P>

    Copyright (c) Mark J. Kilgard, 1994, 1997. <P>

    (c) Copyright 1993, Silicon Graphics, Inc. <P>

    ALL RIGHTS RESERVED <P>

    Permission to use, copy, modify, and distribute this software
    for any purpose and without fee is hereby granted, provided
    that the above copyright notice appear in all copies and that
    both the copyright notice and this permission notice appear in
    supporting documentation, and that the name of Silicon
    Graphics, Inc. not be used in advertising or publicity
    pertaining to distribution of the software without specific,
    written prior permission. <P>

    THE MATERIAL EMBODIED ON THIS SOFTWARE IS PROVIDED TO YOU
    "AS-IS" AND WITHOUT WARRANTY OF ANY KIND, EXPRESS, IMPLIED OR
    OTHERWISE, INCLUDING WITHOUT LIMITATION, ANY WARRANTY OF
    MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  IN NO
    EVENT SHALL SILICON GRAPHICS, INC.  BE LIABLE TO YOU OR ANYONE
    ELSE FOR ANY DIRECT, SPECIAL, INCIDENTAL, INDIRECT OR
    CONSEQUENTIAL DAMAGES OF ANY KIND, OR ANY DAMAGES WHATSOEVER,
    INCLUDING WITHOUT LIMITATION, LOSS OF PROFIT, LOSS OF USE,
    SAVINGS OR REVENUE, OR THE CLAIMS OF THIRD PARTIES, WHETHER OR
    NOT SILICON GRAPHICS, INC.  HAS BEEN ADVISED OF THE POSSIBILITY
    OF SUCH LOSS, HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
    ARISING OUT OF OR IN CONNECTION WITH THE POSSESSION, USE OR
    PERFORMANCE OF THIS SOFTWARE. <P>

    US Government Users Restricted Rights <P>

    Use, duplication, or disclosure by the Government is subject to
    restrictions set forth in FAR 52.227.19(c)(2) or subparagraph
    (c)(1)(ii) of the Rights in Technical Data and Computer
    Software clause at DFARS 252.227-7013 and/or in similar or
    successor clauses in the FAR or the DOD or NASA FAR
    Supplement.  Unpublished-- rights reserved under the copyright
    laws of the United States.  Contractor/manufacturer is Silicon
    Graphics, Inc., 2011 N.  Shoreline Blvd., Mountain View, CA
    94039-7311. <P>

    OpenGL(TM) is a trademark of Silicon Graphics, Inc. <P>
     */
    /**
     * STROKE fonts variables
     */
    public static final int STROKE_ROMAN = 0;
    public static final int STROKE_MONO_ROMAN = 1;

    private static final StrokeFontRec[] strokeFonts = new StrokeFontRec[9];

    /**
     * BITMAP fonts variables
     */
    public static final int BITMAP_9_BY_15 = 2;
    public static final int BITMAP_8_BY_13 = 3;
    public static final int BITMAP_TIMES_ROMAN_10 = 4;
    public static final int BITMAP_TIMES_ROMAN_24 = 5;
    public static final int BITMAP_HELVETICA_10 = 6;
    public static final int BITMAP_HELVETICA_12 = 7;
    public static final int BITMAP_HELVETICA_18 = 8;

    private static final BitmapFontRec[] bitmapFonts = new BitmapFontRec[9];

    /**
     * STROKE fonts functions
     */

    public void glutStrokeCharacter(GL2 gl, int font, char character) {
        StrokeFontRec fontinfo = getStrokeFont(font);
        int c = character & 0xFFFF;
        if ((c < 0) || (c >= fontinfo.num_chars)) {
            return;
        }
        StrokeCharRec ch = fontinfo.ch[c];
        if (ch != null) {
            for (int i = 0; i < ch.num_strokes; i++) {
                StrokeRec stroke = ch.stroke[i];
                gl.glBegin(GL.GL_LINE_STRIP);
                for (int j = 0; j < stroke.num_coords; j++) {
                    CoordRec coord = stroke.coord[j];
                    gl.glVertex2f(coord.x, coord.y);
                }
                gl.glEnd();
            }
            gl.glTranslatef(ch.right, 0.0f, 0.0f);
        }
    }

    public void glutStrokeString(GL2 gl, int font, String string) {
        StrokeFontRec fontinfo = getStrokeFont(font);
        int len = string.length();
        for (int pos = 0; pos < len; pos++) {
            int c = string.charAt(pos) & 0xFFFF;
            if ((c < 0) || (c >= fontinfo.num_chars)) {
                continue;
            }
            StrokeCharRec ch = fontinfo.ch[c];
            if (ch != null) {
                for (int i = 0; i < ch.num_strokes; i++) {
                    StrokeRec stroke = ch.stroke[i];
                    gl.glBegin(GL.GL_LINE_STRIP);
                    for (int j = 0; j < stroke.num_coords; j++) {
                        CoordRec coord = stroke.coord[j];
                        gl.glVertex2f(coord.x, coord.y);
                    }
                    gl.glEnd();
                }
                gl.glTranslatef(ch.right, 0.0f, 0.0f);
            }
        }
    }

    public float glutStrokeWidth(int font, char character) {
        StrokeFontRec fontinfo = getStrokeFont(font);
        int c = character & 0xFFFF;
        if ((c < 0) || (c >= fontinfo.num_chars)) {
            return 0;
        }
        StrokeCharRec ch = fontinfo.ch[c];
        if (ch != null) {
            return ch.right;
        } else {
            return 0;
        }
    }

    public float glutStrokeHeightTop(int font) {
        StrokeFontRec fontinfo = getStrokeFont(font);
        return fontinfo.top;
    }

    public float glutStrokeHeightBottom(int font) {
        StrokeFontRec fontinfo = getStrokeFont(font);
        return fontinfo.bottom;
    }

    public float glutStrokeHeight(int font) {
        StrokeFontRec fontinfo = getStrokeFont(font);
        return fontinfo.top - fontinfo.bottom;
    }

    public float glutStrokeHeightInit(int font) {
        StrokeFontRec fontinfo = getStrokeFont(font);
        return fontinfo.bottom;
    }

    private static StrokeFontRec getStrokeFont(int font) {
        StrokeFontRec rec = strokeFonts[font];
        if (rec == null) {
            switch (font) {
            case STROKE_ROMAN:
                rec = GLUTStrokeRoman.glutStrokeRoman;
                break;
            case STROKE_MONO_ROMAN:
                rec = GLUTStrokeMonoRoman.glutStrokeMonoRoman;
                break;
            default:
                throw new GLException("Unknown stroke font number " + font);
            }
        }
        return rec;
    }

    public float glutStrokeLength(int font, String string) {
        StrokeFontRec fontinfo = getStrokeFont(font);
        float length = 0;
        int len = string.length();
        for (int i = 0; i < len; i++) {
            char c = string.charAt(i);
            if ((c >= 0) && (c < fontinfo.num_chars)) {
                StrokeCharRec ch = fontinfo.ch[c];
                if (ch != null) {
                    length += ch.right;
                }
            }
        }
        return length;
    }

    /**
     * BITMAP fonts functions
     */

    public void glutBitmapCharacter(GL2 gl, int font, char character) {
        int[] swapbytes = new int[1];
        int[] lsbfirst = new int[1];
        int[] rowlength = new int[1];
        int[] skiprows = new int[1];
        int[] skippixels = new int[1];
        int[] alignment = new int[1];
        beginBitmap(gl, swapbytes, lsbfirst, rowlength, skiprows, skippixels, alignment);
        bitmapCharacterImpl(gl, font, character);
        endBitmap(gl, swapbytes, lsbfirst, rowlength, skiprows, skippixels, alignment);
    }

    public void glutBitmapString(GL2 gl, int font, String string) {
        int[] swapbytes = new int[1];
        int[] lsbfirst = new int[1];
        int[] rowlength = new int[1];
        int[] skiprows = new int[1];
        int[] skippixels = new int[1];
        int[] alignment = new int[1];
        beginBitmap(gl, swapbytes, lsbfirst, rowlength, skiprows, skippixels, alignment);
        int len = string.length();
        for (int i = 0; i < len; i++) {
            bitmapCharacterImpl(gl, font, string.charAt(i));
        }
        endBitmap(gl, swapbytes, lsbfirst, rowlength, skiprows, skippixels, alignment);
    }

    public int glutBitmapWidth(int font, char character) {
        BitmapFontRec fontinfo = getBitmapFont(font);
        int c = character & 0xFFFF;
        if ((c < fontinfo.first) || (c >= (fontinfo.first + fontinfo.num_chars))) {
            return 0;
        }
        BitmapCharRec ch = fontinfo.ch[c - fontinfo.first];
        if (ch != null) {
            return (int) ch.advance;
        } else {
            return 0;
        }
    }

    public int glutBitmapLength(int font, String string) {
        BitmapFontRec fontinfo = getBitmapFont(font);
        int length = 0;
        int len = string.length();
        for (int pos = 0; pos < len; pos++) {
            int c = string.charAt(pos) & 0xFFFF;
            if ((c >= fontinfo.first) && (c < (fontinfo.first + fontinfo.num_chars))) {
                BitmapCharRec ch = fontinfo.ch[c - fontinfo.first];
                if (ch != null) {
                    length += ch.advance;
                }
            }
        }
        return length;
    }

    public float glutBitmapHeightTop(int font) {
        float top = 0, ch_top;
        BitmapFontRec fontinfo = getBitmapFont(font);
        //          int length = 0;
        for (int c = fontinfo.first; c < (fontinfo.first + fontinfo.num_chars); c++) {
            BitmapCharRec ch = fontinfo.ch[c - fontinfo.first];
            if (ch != null) {
                ch_top = ch.height - ch.yorig;
                if (ch_top > top) {
                    top = ch_top;
                }
            }
        }
        return top;
    }

    public float glutBitmapHeightBottom(int font) {
        float bottom = 0, ch_bot;
        BitmapFontRec fontinfo = getBitmapFont(font);
        //          int length = 0;
        for (int c = fontinfo.first; c < (fontinfo.first + fontinfo.num_chars); c++) {
            BitmapCharRec ch = fontinfo.ch[c - fontinfo.first];
            if (ch != null) {
                ch_bot = -ch.yorig;
                if (ch_bot < bottom) {
                    bottom = ch_bot;
                }
            }
        }
        return bottom;
    }

    private static void bitmapCharacterImpl(GL2 gl, int font, char cin) {
        BitmapFontRec fontinfo = getBitmapFont(font);
        int c = cin & 0xFFFF;
        if ((c < fontinfo.first) || (c >= (fontinfo.first + fontinfo.num_chars))) {
            return;
        }
        BitmapCharRec ch = fontinfo.ch[c - fontinfo.first];
        if (ch != null) {
            gl.glBitmap(ch.width, ch.height, ch.xorig, ch.yorig, ch.advance, 0, ch.bitmap, 0);
        }
    }

    private static BitmapFontRec getBitmapFont(int font) {
        BitmapFontRec rec = bitmapFonts[font];
        if (rec == null) {
            switch (font) {
            case BITMAP_9_BY_15:
                rec = GLUTBitmap9x15.glutBitmap9By15;
                break;
            case BITMAP_8_BY_13:
                rec = GLUTBitmap8x13.glutBitmap8By13;
                break;
            case BITMAP_TIMES_ROMAN_10:
                rec = GLUTBitmapTimesRoman10.glutBitmapTimesRoman10;
                break;
            case BITMAP_TIMES_ROMAN_24:
                rec = GLUTBitmapTimesRoman24.glutBitmapTimesRoman24;
                break;
            case BITMAP_HELVETICA_10:
                rec = GLUTBitmapHelvetica10.glutBitmapHelvetica10;
                break;
            case BITMAP_HELVETICA_12:
                rec = GLUTBitmapHelvetica12.glutBitmapHelvetica12;
                break;
            case BITMAP_HELVETICA_18:
                rec = GLUTBitmapHelvetica18.glutBitmapHelvetica18;
                break;
            default:
                throw new GLException("Unknown bitmap font number " + font);
            }
            bitmapFonts[font] = rec;
        }
        return rec;
    }

    private static void beginBitmap(GL gl, int[] swapbytes, int[] lsbfirst, int[] rowlength, int[] skiprows,
            int[] skippixels, int[] alignment) {
        gl.glGetIntegerv(GL2.GL_UNPACK_SWAP_BYTES, swapbytes, 0);
        gl.glGetIntegerv(GL2.GL_UNPACK_LSB_FIRST, lsbfirst, 0);
        gl.glGetIntegerv(GL2.GL_UNPACK_ROW_LENGTH, rowlength, 0);
        gl.glGetIntegerv(GL2.GL_UNPACK_SKIP_ROWS, skiprows, 0);
        gl.glGetIntegerv(GL2.GL_UNPACK_SKIP_PIXELS, skippixels, 0);
        gl.glGetIntegerv(GL2.GL_UNPACK_ALIGNMENT, alignment, 0);
        /* Little endian machines (DEC Alpha for example) could
           benefit from setting GL_UNPACK_LSB_FIRST to GL_TRUE
           instead of GL_FALSE, but this would require changing the
           generated bitmaps too. */
        gl.glPixelStorei(GL2.GL_UNPACK_SWAP_BYTES, GL.GL_FALSE);
        gl.glPixelStorei(GL2.GL_UNPACK_LSB_FIRST, GL.GL_FALSE);
        gl.glPixelStorei(GL2.GL_UNPACK_ROW_LENGTH, 0);
        gl.glPixelStorei(GL2.GL_UNPACK_SKIP_ROWS, 0);
        gl.glPixelStorei(GL2.GL_UNPACK_SKIP_PIXELS, 0);
        gl.glPixelStorei(GL2.GL_UNPACK_ALIGNMENT, 1);
    }

    private static void endBitmap(GL gl, int[] swapbytes, int[] lsbfirst, int[] rowlength, int[] skiprows,
            int[] skippixels, int[] alignment) {
        /* Restore saved modes. */
        gl.glPixelStorei(GL2.GL_UNPACK_SWAP_BYTES, swapbytes[0]);
        gl.glPixelStorei(GL2.GL_UNPACK_LSB_FIRST, lsbfirst[0]);
        gl.glPixelStorei(GL2.GL_UNPACK_ROW_LENGTH, rowlength[0]);
        gl.glPixelStorei(GL2.GL_UNPACK_SKIP_ROWS, skiprows[0]);
        gl.glPixelStorei(GL2.GL_UNPACK_SKIP_PIXELS, skippixels[0]);
        gl.glPixelStorei(GL2.GL_UNPACK_ALIGNMENT, alignment[0]);
    }

}
