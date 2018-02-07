/*
 * Created on Apr 13, 2005
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.awt.Color;
import java.awt.Dimension;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;

import lia.Monitor.JiniClient.CommonGUI.Jogl.util.MyGLUT;

/**
 * creates 2d graphics in a plane situated in 3D space<br>
 * works only with integer values that are then converted to float in inner class
 * Apr 13, 2005 - 8:31:55 PM
 */
public class InfoPlane {
    private int nBorderSize = 1;//info box border width
    private int nFontSize = 12;//11;//text font size
    //private int nImgWidth=30;//image default width
    //private int nImgHeight=50;//image default height
    private final int hSpace = 4;//horizontal space between two components
    private final int vSpace = 4; //vertical space between two components
    private int nWidth = 0;//drawing plane width
    private int nHeight = 0;//height

    private final float FIRST_DEPTH = -0.0001f;
    private final float SECOND_DEPTH = -0.0002f;
    private final float THIRD_DEPTH = -0.0003f;
    private final float FOURTH_DEPTH = -0.0004f;
    private final float FIFTH_DEPTH = -0.0005f;
    private final float SIXTH_DEPTH = -0.0006f;
    private final float SEVENTH_DEPTH = -0.0007f;
    private final float EIGHTH_DEPTH = -0.0008f;
    private final float NINTH_DEPTH = -0.0009f;
    /** colection of 2 colors: title background and info background <br>
     *   blue colors */
    private final float[][] first_color_set = new float[][] { new float[] { 0.2f, 0.2f, 0.6f },
            new float[] { 0.8f, 0.8f, 1f } };
    /** yellow colors */
    private final float[][] second_color_set = new float[][] { new float[] { 0x55 / 255f, 0x3c / 255f, 0x35 / 255f },
            new float[] { 0x97 / 255f, 0xbc / 255f, 0x8d / 255f } };
    /** sets the active color set to be used when drawing info boxes */
    private int nActiveColorSet = 1;

    //variables to know to draw several texts one after another
    //private int start_x = 0;
    //private int start_y = 0;
    //private float start_text_yant = 0f;
    //private float start_text_max_height = 0f;
    private boolean bLeft2Right = true;//drawing should be done from left to right
    private boolean bTop2Bottom = true;//drawing should be done from top to bottom
    //	private boolean bIncXPositive = true;//computed from above, x axis is oriented from left to right
    //	private boolean bIncYPositive = false;//computed form above, y axis is oriented from bottom to top

    private static boolean bDebug = false;//shows debug messages

    private boolean bAlphaOnlyBg = false;

    /**
     * sets debug level for this class
     * @param bIsDebug set debug mode if true, else do not show debug messages
     */
    public static void setDebugLevel(boolean bIsDebug) {
        bDebug = bIsDebug;
        //        if ( bDebug )
        //            logLevel = Level.INFO;
        //        else
        //            logLevel = Level.FINEST;
    }

    /**
     * the one and only real drawing object from 2d coordinates to real 3d coordinates
     */
    Drawing2DTo3D drawObject;

    private void Init(GL2 gl) {

        nWidth = JoglPanel.globals.width;
        nHeight = JoglPanel.globals.height;

        //init drawing object after init of width and height
        drawObject = new Drawing2DTo3D(gl);

        computeNewPosition(0);
    }

    public InfoPlane(GL2 gl, boolean bl2r, boolean bt2b, int nBorder, int nFont) {
        bLeft2Right = bl2r;
        bTop2Bottom = bt2b;
        Init(gl);
        nBorderSize = nBorder;
        nFontSize = nFont;
    }

    public InfoPlane(GL2 gl, int nBorder, int nFont) {
        this(gl);
        nBorderSize = nBorder;
        nFontSize = nFont;
    }

    public InfoPlane(GL2 gl, float alphaValue) {
        this(gl);
        drawObject.alpha = alphaValue;
    }

    public InfoPlane(GL2 gl) {
        Init(gl);
    }

    public InfoPlane(GL2 gl, boolean bl2r, boolean bt2b) {
        bLeft2Right = bl2r;
        bTop2Bottom = bt2b;
        Init(gl);
    }

    public InfoPlane(GL2 gl, boolean bl2r, boolean bt2b, float alphaValue) {
        this(gl, bl2r, bt2b);
        drawObject.alpha = alphaValue;
    }

    /**
     * dummy initializer, cannot be used, or tremendous errors will be generated
     */
    public InfoPlane() {
        nWidth = JoglPanel.globals.width;
        nHeight = JoglPanel.globals.height;
        drawObject = null;
    }

    //margins for info boxes to be drawn
    private final int nLeftMargin = 10, nRightMargin = 10, nTopMargin = 10, nBottomMargin = 10;
    private int nCurrentXPosition = 0, nCurrentYPosition = 0;//current positions
    private int nCurrentMaxHeight = 0;
    private final int nHorizontalSpacing = 10, nVerticalSpacing = 10;//spacing between info boxes

    /**
     * called at begining of info box call<br>
     * based on current coordinates, and on provided width and height,
     * computes new coordinates taking in consideration directions for boxes to be drawn to
     * @param width
     * @param height
     * @return true if the info box has room to be drawn, false if not
     */
    public boolean computeNewPosition(int width) {
        if (width == 0) {
            //initialisation of position
            if (bLeft2Right) {
                nCurrentXPosition = nLeftMargin;
            } else {
                nCurrentXPosition = nWidth - nRightMargin;
            }
            if (bTop2Bottom) {
                nCurrentYPosition = nTopMargin;
            } else {
                nCurrentYPosition = nHeight - nBottomMargin;
            }
            nCurrentMaxHeight = 0;
            return true;
        }
        if (bLeft2Right) {
            if ((nCurrentXPosition + width) > (nWidth - nRightMargin)) {//if no room left on current line, go to next
                nCurrentXPosition = nLeftMargin;
                //go to next line
                if (bTop2Bottom) {
                    nCurrentYPosition += nCurrentMaxHeight + nVerticalSpacing;
                } else {
                    //from bottom to top
                    nCurrentYPosition -= (nCurrentMaxHeight + nVerticalSpacing);
                }
                nCurrentMaxHeight = 0;
            }
            ;//else the position is good, so nothing to be done
        } else {//right to left
            if ((nCurrentXPosition - width) < nLeftMargin) {//no room left on line, go to next
                nCurrentXPosition = nWidth - nRightMargin;
                //go to next line
                if (bTop2Bottom) {
                    nCurrentYPosition += nCurrentMaxHeight + nVerticalSpacing;
                } else {
                    //from bottom to top
                    nCurrentYPosition -= (nCurrentMaxHeight + nVerticalSpacing);
                }
                nCurrentMaxHeight = 0;
            }
            //update start position cause info box always draws from left to right
            nCurrentXPosition -= width;
        }
        //check if there is room left
        if (bTop2Bottom) {
            if (nCurrentYPosition > (nHeight - nBottomMargin)) {
                return false;
            }
        } else {
            if (nCurrentYPosition < nTopMargin) {
                return false;
            }
        }
        return true;
    }

    /**
     * called at end of info box<br>
     * computes next available positions for other info boxes to be drawn
     * @param width current info box widht
     * @param height current info box height
     */
    public void computeNextPossiblePosition(int width, int height) {
        if (bLeft2Right) {
            //if the box was able to be drawn, there is no point in computing if it fitted the widht of screen
            nCurrentXPosition += width + nHorizontalSpacing;
        } else {
            //
            nCurrentXPosition -= nHorizontalSpacing;
        }
        //update also the current maximal height
        if (nCurrentMaxHeight < height) {
            nCurrentMaxHeight = height;
        }
    }

    private Dimension getInfoBoxDims(int width, int height, boolean bShrinkWidth, boolean bShrinkHeight, String sTitle,
            String sDesc, int nImageID, int nImageW, int nImageH, float[] coords) {
        boolean bIsImage = ((nImageID > 0) && drawObject.isImage(nImageID) && (nImageH > 0) && (nImageW > 0));
        //        boolean bImageHasMaxHeight = false;
        int x = nCurrentXPosition, y = nCurrentYPosition;//starting position, absolute
        //rest of values are relative to these starting positions
        int xT = nBorderSize + hSpace;//starting x for title
        int xD = xT;//starting x for description
        int maxY = height - nBorderSize - vSpace;//maximum y
        int maxH = maxY - nBorderSize - vSpace;//compute maximal height that text or image could have in box
        int maxW = width - (2 * nBorderSize) - (2 * hSpace);//maximal width for drawing
        if (bIsImage && (maxH < (nImageH + 2))) {//if image height is greater than maximal height, redimension image
            nImageW = (nImageW * (maxH - 2)) / nImageH;//pp ih>0
            nImageH = maxH - 2;
            if ((nImageH <= 0) || (nImageW <= 0)) {
                bIsImage = false;
                //            bImageHasMaxHeight = true;
            }
        }
        ;
        //same should be done for width of image
        int wT = maxW - (bIsImage ? 2 + nImageW + (2 * hSpace) : 0);//width of title
        int yT = nBorderSize + vSpace;//start y position for title
        int yImg = yT;//same for image
        //        int xImg = width-1 - nBorderSize-hSpace-2-nImageW;

        if (bIsImage) {//if we have an image in box, draw around it
            //try to draw title
            //first find the text width to see how it should be drawn
            int hT = drawObject.getTextHeight(wT, sTitle);
            //System.out.println("ht="+hT+" nImageH+2="+(nImageH+2));
            if (hT <= (nImageH + 2)) {//1.
                //title has room inside the image's height
                //draw background for title
                //draw line below title
                //	            int yLine = yT+hT+vSpace/2+vSpace/4;//at the middle of second half of vSpace from just below text
                int yD = yT + hT + (2 * vSpace);//starting vertical position for drawing description
                if (yD > (yImg + 2 + nImageH)) {//1.2
                    //draw description using left height and maxW
                    int leftH = maxY - yD;
                    if (bShrinkHeight) {
                        //compute text height to see if adjustments to maximal height have to be made
                        int hD = drawObject.getTextHeight(maxW, sDesc, leftH, true, false);
                        if (hD < leftH) {
                            //adjust maximal height
                            maxH = (maxH - leftH) + hD;
                        }
                        ;
                    }
                } else {//1.1
                    //description can still be drawn inside height of image, so do it
                    int hD = drawObject.getTextHeight(wT, sDesc);
                    if ((yD + hD) <= (nImageH + 2)) {//1.1.1
                        //draw description
                        if (bShrinkHeight) {
                            //set max height as image height
                            maxH = nImageH + 2;
                        }
                        ;
                    } else {//1.1.2
                        if (maxH < (nImageH + 2 + nFontSize)) {
                            //if there is no vertical space left for text to be put from image end to info box end
                            //draw description without last line
                            if (bShrinkHeight) {
                                //set max height as image height if text is not going to overtake image's height
                                maxH = nImageH + 2;
                            }
                            ;
                        } else {
                            //draw description as far as the image height allows it
                            //and then draw in full info box width
                            String sRest = drawObject.getRemainingText(x + xD, y + yD, wT, (yImg + 2 + nImageH) - yD,
                                    sDesc, false);//allow image height to be surpass with maximum one line
                            //compute new starting y position for remaining text
                            //2 variants: if text can be drawn in left space, use finalverticalspacing
                            //else, do not use
                            //try to see if space left
                            hD = drawObject.getTextHeight(wT, sDesc, (yImg + 2 + nImageH) - yD, false, true);
                            int hD2 = drawObject.getTextHeight(maxW, sRest, maxY - yD - hD, true, false);
                            if (hD2 == 0) {
                                //so do not put vertical text spacing
                                hD = drawObject.getTextHeight(wT, sDesc, (yImg + 2 + nImageH) - yD, false, false);
                            } else {//draw some of the rest
                                yD += hD;
                                hD = hD2;
                            }
                            if (bShrinkHeight) {
                                //see if height for rest of text is smaller than box's height, and, in this case,
                                //change maximal height
                                if (hD < (maxY - yD)) {
                                    maxH = (yD + hD) - nBorderSize - vSpace;
                                }
                            }
                            ;
                        }
                    }//end 1.1.2
                }//end 1.1
            } else {//2.
                //title's height is greater than image's height, so it must be drawn from 2 pieces
                if (maxH < (nImageH + 2 + nFontSize)) {//2.1
                    //if there is no vertical space left for text to be put from image end to info box end
                    //draw title without last line, and that's it
                    //compute text's limited height
                    hT = drawObject.getTextHeight(wT, sTitle, nImageH + 2, true, false);
                    //draw background for title
                    //draw line below title
                    //                    int yLine = yT+hT+vSpace/2+vSpace/4;//at the middle of second half of vSpace from just below text
                    if (bShrinkHeight) {
                        //set max height as image height if text is not going to overtake image's height
                        maxH = nImageH + 2;
                    }
                    ;
                } else {//2.2
                    //draw title as it fits the image's height, and then under image as it fits, and then the description
                    //draw title as far as the image height allows it
                    //and then draw in full info box width
                    String sRest = drawObject.getRemainingText(x + xT, y + yT, wT, 2 + nImageH, sTitle, false);//allow image height to be surpass with maximum one line
                    //compute new starting y position for remaining text
                    //try to see if space left
                    hT = drawObject.getTextHeight(wT, sTitle, 2 + nImageH, false, true);
                    //get height of second part of text
                    int hT2 = drawObject.getTextHeight(maxW, sRest, maxY - yT - hT, true, false);
                    //                    int lineWidth=maxW;
                    if (hT2 == 0) {//2.2.1
                        //there is no room to draw rest
                        //so do not put vertical text spacing
                        hT = drawObject.getTextHeight(wT, sTitle, 2 + nImageH, false, false);
                        //                        lineWidth = wT;//no other section of text for title, so retain smaller width
                    } else {//2.2.2
                        //draw some of the rest
                        //                        int yT2 = yT+hT;
                        //compute entire title text's height
                        //as hT+hT2
                        //yT = yT2;
                        hT += hT2;
                    }
                    ;
                    //draw background for title
                    //draw line below title
                    //                    int yLine = yT+hT+vSpace/2+vSpace/4;//at the middle of second half of vSpace from just below text
                    //get description drawing
                    //if max height allows it
                    int yD = yT + hT + (2 * vSpace);//starting vertical position for drawing description
                    //                    if ( yD < maxY ) {
                    //                    }
                    if (bShrinkHeight) {
                        //see if height for description is smaller than box's height, and, in this case,
                        //change maximal height
                        int hD = drawObject.getTextHeight(maxW, sDesc, maxY - yD, true, false);
                        if (hD == 0) {
                            maxH = yD - vSpace - nBorderSize - vSpace;
                        } else if (hD < (maxY - yD)) {
                            maxH = (yD + hD) - nBorderSize - vSpace;
                        }
                    }
                    ;
                }
                ;
            }//end info box with image inside
        } else {//info box has no image, so draw title and description
            if (bShrinkWidth) {
                //check to see if width size is to big
                Dimension d1 = drawObject.getTextDimensions(sTitle);
                Dimension d2 = drawObject.getTextDimensions(sDesc);
                int bigW = d1.width;
                if (d2.width > bigW) {
                    bigW = d2.width;
                }
                if (maxW > bigW) {
                    maxW = bigW;
                }
            }
            //consequently wT===maxW
            wT = maxW;
            //draw title
            //get title's height
            int hT = drawObject.getTextHeight(wT, sTitle, maxH, true, false);
            //draw background for title
            //draw line below title
            //            int yLine = yT+hT+vSpace/2+vSpace/4;//at the middle of second half of vSpace from just below text
            //get description drawing
            int yD = yT + hT + (2 * vSpace);//starting vertical position for drawing description
            //            if ( yD < maxY ) {
            //            }
            if (bShrinkHeight) {
                //see if height for description is smaller than box's height, and, in this case,
                //change maximal height
                int hD = drawObject.getTextHeight(wT, sDesc, maxY - yD, true, false);
                //System.out.println("desc height="+hD+" maxH-yD="+(maxY-yD));
                if (hD == 0) {
                    maxH = yD - vSpace - nBorderSize - vSpace;
                } else if (hD < (maxY - yD)) {
                    maxH = (yD + hD) - nBorderSize - vSpace;
                }
            }
            ;
        }//end drawing title, title background, title underline and description

        //first compute maximal dimensions
        maxW += (2 * nBorderSize) + (2 * hSpace);
        maxH += (2 * nBorderSize) + (2 * vSpace);
        return new Dimension(maxW, maxH);
    }

    /**
     * draw an info box using current general border and font size
     * that has the given width and height.<br>
     * If bShrinkHeight is true, box's height may be smaller than given height.<br>
     * The box is drawn at current position.<br>
     * The description is drawn right under title.<br>
     * @param width width in pixels of info box
     * @param height height in pixels of info box
     * @param bShrinkHeight if true, true height is less or equal to received height
     * @param sTitle title text to be drawn
     * @param sDesc description text to be drawn
     * @param nImageID image openg id to be drawn
     * @param nImageW image width in pixels
     * @param nImageH image height in pixels
     * @param vPoint point to whom this info box relates
     */
    public void doInfoBox(int width, int height, boolean bShrinkWidth, boolean bShrinkHeight, String sTitle,
            String sDesc, int nImageID, int nImageW, int nImageH, float[] coords) {
        //get color set
        float[][] color_set = (nActiveColorSet == 1 ? first_color_set : second_color_set);
        Dimension dIB = new Dimension(width, height);
        if (bShrinkHeight && bShrinkWidth) {
            dIB = getInfoBoxDims(width, height, bShrinkWidth, bShrinkHeight, sTitle, sDesc, nImageID, nImageW, nImageH,
                    coords);
        }
        //compute new info box position
        computeNewPosition(dIB.width);

        boolean bIsImage = ((nImageID > 0) && drawObject.isImage(nImageID) && (nImageH > 0) && (nImageW > 0));
        //        boolean bImageHasMaxHeight = false;
        int x = nCurrentXPosition, y = nCurrentYPosition - (bTop2Bottom ? 0 : dIB.height);//starting position, absolute
        //rest of values are relative to these starting positions
        int xT = nBorderSize + hSpace;//starting x for title
        int xD = xT;//starting x for description
        int maxY = height - nBorderSize - vSpace;//maximum y
        int maxH = maxY - nBorderSize - vSpace;//compute maximal height that text or image could have in box
        int maxW = width - (2 * nBorderSize) - (2 * hSpace);//maximal width for drawing
        if (bIsImage && (maxH < (nImageH + 2))) {//if image height is greater than maximal height, redimension image
            nImageW = (nImageW * (maxH - 2)) / nImageH;//pp ih>0
            nImageH = maxH - 2;
            if ((nImageH <= 0) || (nImageW <= 0)) {
                bIsImage = false;
                //            bImageHasMaxHeight = true;
            }
        }
        ;
        //same should be done for width of image
        int wT = maxW - (bIsImage ? 2 + nImageW + (2 * hSpace) : 0);//width of title
        int yT = nBorderSize + vSpace;//start y position for title
        int yImg = yT;//same for image
        int xImg = width - 1 - nBorderSize - hSpace - 2 - nImageW;

        if (bIsImage) {//if we have an image in box, draw around it
            //try to draw title
            //first find the text width to see how it should be drawn
            int hT = drawObject.getTextHeight(wT, sTitle);
            //System.out.println("ht="+hT+" nImageH+2="+(nImageH+2));
            if (hT <= (nImageH + 2)) {//1.
                if (bAlphaOnlyBg) {
                    drawObject.setOpaque(true);
                }
                //title has room inside the image's height
                drawObject.setLineWidth(1);
                drawObject.setColor(1f, 1f, 1f);
                drawObject.drawText(x + xT, y + yT, wT, hT, sTitle, true);//the drawn text has the computed height: hT
                //draw background for title
                if (bAlphaOnlyBg) {
                    drawObject.setOpaque(false);
                }
                drawObject.setColorv(color_set[0]);
                drawObject.fillRectangle((x + xT) - (hSpace / 2), (y + yT) - (vSpace / 2), wT + hSpace, hT + vSpace
                        + (vSpace / 2), FOURTH_DEPTH);//draw in front of the other fill
                //draw line below title
                if (bAlphaOnlyBg) {
                    drawObject.setOpaque(true);
                }
                drawObject.setLineWidth(vSpace / 2);
                drawObject.setColor(1f, 1f, 1f);
                int yLine = yT + hT + (vSpace / 2) + (vSpace / 4);//at the middle of second half of vSpace from just below text
                drawObject.drawLine((x + xT) - (hSpace / 2), y + yLine, x + xT + wT + (hSpace / 2), y + yLine);
                int yD = yT + hT + (2 * vSpace);//starting vertical position for drawing description
                if (yD > (yImg + 2 + nImageH)) {//1.2
                    //draw description using left height and maxW
                    int leftH = maxY - yD;
                    drawObject.setLineWidth(1);
                    drawObject.setColor(0f, 0f, 0f);
                    drawObject.drawText(x + xD, y + yD, maxW, leftH, sDesc, true);
                    if (bShrinkHeight) {
                        //compute text height to see if adjustments to maximal height have to be made
                        int hD = drawObject.getTextHeight(maxW, sDesc, leftH, true, false);
                        if (hD < leftH) {
                            //adjust maximal height
                            maxH = (maxH - leftH) + hD;
                        }
                        ;
                    }
                } else {//1.1
                    //description can still be drawn inside height of image, so do it
                    int hD = drawObject.getTextHeight(wT, sDesc);
                    if ((yD + hD) <= (nImageH + 2)) {//1.1.1
                        //draw description
                        drawObject.setLineWidth(1);
                        drawObject.setColor(0f, 0f, 0f);
                        drawObject.drawText(x + xD, y + yD, wT, hD, sDesc, true);
                        if (bShrinkHeight) {
                            //set max height as image height
                            maxH = nImageH + 2;
                        }
                        ;
                    } else {//1.1.2
                        if (maxH < (nImageH + 2 + nFontSize)) {
                            //if there is no vertical space left for text to be put from image end to info box end
                            //draw description without last line
                            drawObject.setLineWidth(1);
                            drawObject.setColor(0f, 0f, 0f);
                            drawObject.drawText(x + xD, y + yD, wT, (yImg + 2 + nImageH) - yD, sDesc, true);
                            if (bShrinkHeight) {
                                //set max height as image height if text is not going to overtake image's height
                                maxH = nImageH + 2;
                            }
                            ;
                        } else {
                            //draw description as far as the image height allows it
                            //and then draw in full info box width
                            drawObject.setLineWidth(1);
                            drawObject.setColor(0f, 0f, 0f);
                            String sRest = drawObject.drawText(x + xD, y + yD, wT, (yImg + 2 + nImageH) - yD, sDesc,
                                    false);//allow image height to be surpass with maximum one line
                            //compute new starting y position for remaining text
                            //2 variants: if text can be drawn in left space, use finalverticalspacing
                            //else, do not use
                            //try to see if space left
                            hD = drawObject.getTextHeight(wT, sDesc, (yImg + 2 + nImageH) - yD, false, true);
                            int hD2 = drawObject.getTextHeight(maxW, sRest, maxY - yD - hD, true, false);
                            if (hD2 == 0) {
                                //so do not put vertical text spacing
                                hD = drawObject.getTextHeight(wT, sDesc, (yImg + 2 + nImageH) - yD, false, false);
                            } else {//draw some of the rest
                                yD += hD;
                                drawObject.drawText(x + xD, y + yD, maxW, maxY - yD, sRest, true);
                                hD = hD2;
                            }
                            if (bShrinkHeight) {
                                //see if height for rest of text is smaller than box's height, and, in this case,
                                //change maximal height
                                if (hD < (maxY - yD)) {
                                    maxH = (yD + hD) - nBorderSize - vSpace;
                                }
                            }
                            ;
                        }
                    }//end 1.1.2
                }//end 1.1
            } else {//2.
                //title's height is greater than image's height, so it must be drawn from 2 pieces
                if (maxH < (nImageH + 2 + nFontSize)) {//2.1
                    //if there is no vertical space left for text to be put from image end to info box end
                    //draw title without last line, and that's it
                    if (bAlphaOnlyBg) {
                        drawObject.setOpaque(true);
                    }
                    drawObject.setLineWidth(1);
                    drawObject.setColor(1f, 1f, 1f);
                    drawObject.drawText(x + xT, y + yT, wT, nImageH + 2, sTitle, true);//limit title height to image height
                    //compute text's limited height
                    hT = drawObject.getTextHeight(wT, sTitle, nImageH + 2, true, false);
                    //draw background for title
                    if (bAlphaOnlyBg) {
                        drawObject.setOpaque(false);
                    }
                    drawObject.setColorv(color_set[0]);
                    drawObject.fillRectangle((x + xT) - (hSpace / 2), (y + yT) - (vSpace / 2), wT + hSpace,
                            hT + vSpace, FOURTH_DEPTH);//draw in front of the other fill
                    //draw line below title
                    if (bAlphaOnlyBg) {
                        drawObject.setOpaque(true);
                    }
                    drawObject.setLineWidth(vSpace / 2);
                    drawObject.setColor(1f, 1f, 1f);
                    int yLine = yT + hT + (vSpace / 2) + (vSpace / 4);//at the middle of second half of vSpace from just below text
                    drawObject.drawLine((x + xT) - (hSpace / 2), y + yLine, x + xT + wT + (hSpace / 2), y + yLine);
                    if (bShrinkHeight) {
                        //set max height as image height if text is not going to overtake image's height
                        maxH = nImageH + 2;
                    }
                    ;
                } else {//2.2
                    //draw title as it fits the image's height, and then under image as it fits, and then the description
                    //draw title as far as the image height allows it
                    //and then draw in full info box width
                    if (bAlphaOnlyBg) {
                        drawObject.setOpaque(true);
                    }
                    drawObject.setLineWidth(1);
                    drawObject.setColor(1f, 1f, 1f);
                    String sRest = drawObject.drawText(x + xT, y + yT, wT, 2 + nImageH, sTitle, false);//allow image height to be surpass with maximum one line
                    //compute new starting y position for remaining text
                    //try to see if space left
                    hT = drawObject.getTextHeight(wT, sTitle, 2 + nImageH, false, true);
                    //get height of second part of text
                    int hT2 = drawObject.getTextHeight(maxW, sRest, maxY - yT - hT, true, false);
                    int lineWidth = maxW;
                    if (hT2 == 0) {//2.2.1
                        //there is no room to draw rest
                        //so do not put vertical text spacing
                        hT = drawObject.getTextHeight(wT, sTitle, 2 + nImageH, false, false);
                        lineWidth = wT;//no other section of text for title, so retain smaller width
                    } else {//2.2.2
                        //draw some of the rest
                        int yT2 = yT + hT;
                        drawObject.drawText(x + xT, y + yT2, maxW, maxY - yT2, sRest, false);
                        //compute entire title text's height
                        //as hT+hT2
                        //yT = yT2;
                        hT += hT2;
                    }
                    ;
                    //draw background for title
                    if (bAlphaOnlyBg) {
                        drawObject.setOpaque(false);
                    }
                    drawObject.setColorv(color_set[0]);
                    drawObject.fillRectangle((x + xT) - (hSpace / 2), (y + yT) - (vSpace / 2), lineWidth + hSpace, hT
                            + vSpace + (vSpace / 2), FOURTH_DEPTH);//draw in front of the other fill
                    //draw line below title
                    if (bAlphaOnlyBg) {
                        drawObject.setOpaque(true);
                    }
                    drawObject.setLineWidth(vSpace / 2);
                    drawObject.setColor(1f, 1f, 1f);
                    int yLine = yT + hT + (vSpace / 2) + (vSpace / 4);//at the middle of second half of vSpace from just below text
                    drawObject.drawLine((x + xT) - (hSpace / 2), y + yLine, x + xT + lineWidth + (hSpace / 2), y
                            + yLine);
                    //get description drawing
                    //if max height allows it
                    int yD = yT + hT + (2 * vSpace);//starting vertical position for drawing description
                    if (yD < maxY) {
                        drawObject.setLineWidth(1);
                        drawObject.setColor(0f, 0f, 0f);
                        drawObject.drawText(x + xD, y + yD, maxW, maxY - yD, sDesc, true);
                    }
                    if (bShrinkHeight) {
                        //see if height for description is smaller than box's height, and, in this case,
                        //change maximal height
                        int hD = drawObject.getTextHeight(maxW, sDesc, maxY - yD, true, false);
                        if (hD == 0) {
                            maxH = yD - vSpace - nBorderSize - vSpace;
                        } else if (hD < (maxY - yD)) {
                            maxH = (yD + hD) - nBorderSize - vSpace;
                        }
                    }
                    ;
                }
                ;
            }//end info box with image inside
        } else {//info box has no image, so draw title and description
            if (bShrinkWidth) {
                //check to see if width size is to big
                Dimension d1 = drawObject.getTextDimensions(sTitle);
                Dimension d2 = drawObject.getTextDimensions(sDesc);
                int bigW = d1.width;
                if (d2.width > bigW) {
                    bigW = d2.width;
                }
                if (maxW > bigW) {
                    maxW = bigW;
                }
            }
            //consequently wT===maxW
            wT = maxW;
            //draw title
            if (bAlphaOnlyBg) {
                drawObject.setOpaque(true);
            }
            drawObject.setLineWidth(1);
            drawObject.setColor(1f, 1f, 1f);
            drawObject.drawText(x + xT, y + yT, wT, maxH, sTitle, true);
            //get title's height
            int hT = drawObject.getTextHeight(wT, sTitle, maxH, true, false);
            //draw background for title
            if (bAlphaOnlyBg) {
                drawObject.setOpaque(false);
            }
            drawObject.setColorv(color_set[0]);
            drawObject.fillRectangle((x + xT) - (hSpace / 2), (y + yT) - (vSpace / 2), wT + hSpace, hT + vSpace
                    + (vSpace / 2), FOURTH_DEPTH);//draw in front of the other fill
            //draw line below title
            if (bAlphaOnlyBg) {
                drawObject.setOpaque(false);
            }
            drawObject.setLineWidth(vSpace / 2);
            drawObject.setColor(1f, 1f, 1f);
            int yLine = yT + hT + (vSpace / 2) + (vSpace / 4);//at the middle of second half of vSpace from just below text
            drawObject.drawLine((x + xT) - (hSpace / 2), y + yLine, x + xT + wT + (hSpace / 2), y + yLine);
            //get description drawing
            int yD = yT + hT + (2 * vSpace);//starting vertical position for drawing description
            if (yD < maxY) {
                drawObject.setLineWidth(1);
                drawObject.setColor(0f, 0f, 0f);
                drawObject.drawText(x + xD, y + yD, maxW, maxY - yD, sDesc, true);
            }
            if (bShrinkHeight) {
                //see if height for description is smaller than box's height, and, in this case,
                //change maximal height
                int hD = drawObject.getTextHeight(wT, sDesc, maxY - yD, true, false);
                //System.out.println("desc height="+hD+" maxH-yD="+(maxY-yD));
                if (hD == 0) {
                    maxH = yD - vSpace - nBorderSize - vSpace;
                } else if (hD < (maxY - yD)) {
                    maxH = (yD + hD) - nBorderSize - vSpace;
                }
            }
            ;
        }//end drawing title, title background, title underline and description

        //draw image, if the case
        if (bIsImage) {
            //draw image border
            if (bAlphaOnlyBg) {
                drawObject.setOpaque(true);
            }
            drawObject.setLineWidth(1);
            drawObject.setColor(1f, 1f, 1f);
            drawObject.drawRectangle(x + xImg, y + yImg, nImageW + 2, nImageH + 2);
            //draw image
            drawObject.drawImage(x + xImg + 1, y + yImg + 1, nImageW, nImageH, nImageID);
        }
        ;
        //draw box background and margins
        //first compute maximal dimensions
        maxW += (2 * nBorderSize) + (2 * hSpace);
        maxH += (2 * nBorderSize) + (2 * vSpace);
        //draw outher border of the info box
        if (bAlphaOnlyBg) {
            drawObject.setOpaque(true);
        }
        drawObject.setLineWidth(nBorderSize);
        drawObject.setColorv(color_set[0]);
        drawObject.drawRectangle(x, y, maxW, maxH);
        //fill inside with light color
        if (bAlphaOnlyBg) {
            drawObject.setOpaque(false);
        }
        drawObject.setColorv(color_set[1]);
        drawObject.fillRectangle(x, y, maxW, maxH, SIXTH_DEPTH);

        //compute xc and yc, coordinates for line connecting this info box and
        //something in 3D world
        int xc = x + (maxW / 2);
        int yc;//=y+maxH-1;
        if (bTop2Bottom) {
            yc = (y + maxH) - 1;
        } else {
            yc = y;
        }
        if (bAlphaOnlyBg) {
            drawObject.setOpaque(true);
        }
        drawObject.setLineWidth(1);
        drawObject.setColor(1f, 1f, 1f);
        drawObject.drawLine2Point(xc, yc, coords);
        //put some defaults back

        //compute next available info box positions
        computeNextPossiblePosition(maxW, maxH);
    }

    /**
     * <b>DEPRECATED</b><br>
     * draw an info box using current general border and font size<br>
     * that has the given width and height.<br>
     * The box is drawn at current position.
     * @param width
     * @param height
     * @param sTitle
     * @param sDesc
     * @param nImageID
     */
    public void doInfoBox(String sTitle, String sDesc, int nImageID, int nImageW, int nImageH, VectorO vPoint) {
        //get color set
        float[][] color_set = (nActiveColorSet == 1 ? first_color_set : second_color_set);
        //compute needed start x and y based on
        //start_x and start_y, and w and h
        int x = nCurrentXPosition;
        int y = nCurrentYPosition;

        boolean bIsImage = ((nImageID > 0) && drawObject.isImage(nImageID));
        if (bDebug) {
            System.out.println("Debug in InfoPlane.doInfoBox");
        }
        Dimension d1 = drawObject.getTextDimensions(sTitle);
        if (bDebug) {
            System.out.println("title=" + sTitle + " has dimensions: (" + d1.width + " px, " + d1.height + " px)");
        }
        int w = d1.width + (bIsImage ? (2 * hSpace) + 2/*picture border*/+ nImageW : 0);
        int h = d1.height + vSpace + 1/*line width*/;
        if (bIsImage && (h < (nImageH + 2/*picture border up and down*/))) {
            h = nImageH + 2;
        }
        Dimension d2 = drawObject.getTextDimensions(sDesc);
        if (bDebug) {
            System.out.println("description=" + sDesc + " has dimensions: (" + d2.width + " px, " + d2.height + " px)");
        }
        if (w < d2.width) {
            w = d2.width;
        }
        //final width
        w = (2 * nBorderSize) + hSpace + w + hSpace;
        if (bDebug) {
            System.out.println("info box width=" + w);
        }
        int y_desc = nBorderSize + vSpace + h + vSpace;//starting y position for description
        if (bDebug) {
            System.out.println("Desc text Y start position: " + y_desc);
        }
        h = y_desc + d2.height + vSpace + nBorderSize;//final height
        if (bDebug) {
            System.out.println("info box height=" + h);
        }
        if (bDebug) {
            System.out.println("info box starting position: (" + x + " px, " + y + " px)");
        }
        //draw outher border of the info box
        if (bAlphaOnlyBg) {
            drawObject.setOpaque(true);
        }
        drawObject.setLineWidth(nBorderSize);
        drawObject.setColorv(color_set[0]);
        drawObject.drawRectangle(x, y, w, h);
        //fill inside with light color
        if (bAlphaOnlyBg) {
            drawObject.setOpaque(false);
        }
        drawObject.setColorv(color_set[1]);
        drawObject.fillRectangle(x, y, w, h, SIXTH_DEPTH);
        //draw title
        if (bAlphaOnlyBg) {
            drawObject.setOpaque(true);
        }
        drawObject.setLineWidth(1);
        drawObject.setColor(1f, 1f, 1f);
        drawObject.drawText(x + nBorderSize + hSpace, y + nBorderSize + vSpace, sTitle);
        //draw background for title
        if (bAlphaOnlyBg) {
            drawObject.setOpaque(false);
        }
        drawObject.setColorv(color_set[0]);
        drawObject.fillRectangle(x + nBorderSize + (hSpace / 2), y + nBorderSize + (vSpace / 2), w - (2 * nBorderSize)
                - (bIsImage ? (2 * hSpace) + 2 + nImageW : 0) - hSpace,
                (vSpace / 2) + d1.height + vSpace + 1/*line width*/, FOURTH_DEPTH);//draw in front of the other fill
        //draw line below title
        if (bAlphaOnlyBg) {
            drawObject.setOpaque(true);
        }
        drawObject.setLineWidth(1);
        drawObject.setColor(1f, 1f, 1f);
        drawObject.drawLine(x + nBorderSize + (hSpace / 2), y + nBorderSize + vSpace + d1.height + vSpace, (x + w)
                - nBorderSize - (bIsImage ? (2 * hSpace) + 2 + nImageW : 0) - (hSpace / 2), y + nBorderSize + vSpace
                + d1.height + vSpace);
        if (bIsImage) {
            //draw image border
            drawObject.drawRectangle((x + w) - nBorderSize - hSpace - nImageW - 2, y + nBorderSize + vSpace,
                    nImageW + 2, nImageH + 2);
            //draw image
            drawObject.drawImage((x + w) - nBorderSize - hSpace - nImageW - 1, y + nBorderSize + vSpace + 1, nImageW,
                    nImageH, nImageID);
        }
        ;
        //draw description text
        drawObject.setLineWidth(1);
        drawObject.setColor(0f, 0f, 0f);
        drawObject.drawText(x + nBorderSize + hSpace, y_desc, sDesc);
        //compute xc and yc, coordinates for line connecting this info box and
        //something in 3D world
        int xc = x + (w / 2);
        int yc = (y + h) - 1;
        drawObject.setLineWidth(1);
        drawObject.setColor(1f, 1f, 1f);
        drawObject.drawLine2Point(xc, yc, vPoint);
        //put some defaults back

        //compute next available info box positions
        computeNextPossiblePosition(w, h);
    }

    public static int checkPointOnRotBar(int mouse_x, int mouse_y) {
        int /*nWidthRB, */nHeightRB;
        //        nWidthRB = Globals.ROTATION_BAR_WIDTH;
        nHeightRB = Globals.ROTATION_BAR_HEIGHT;
        int nTotalHeight = JoglPanel.globals.height;
        int nTotalWidth = JoglPanel.globals.width;
        int nSelectedBars = 0;
        if ((mouse_x >= 0) && (mouse_x <= nHeightRB) && (mouse_y >= 0) && (mouse_y <= nTotalHeight)) {
            nSelectedBars |= POSITION_LEFT;
        }
        if ((mouse_y >= 0) && (mouse_y <= nHeightRB) && (mouse_x >= 0) && (mouse_x <= nTotalWidth)) {
            nSelectedBars |= POSITION_TOP;
        }
        if ((mouse_x >= (nTotalWidth - nHeightRB)) && (mouse_x <= nTotalWidth) && (mouse_y >= 0)
                && (mouse_y <= nTotalHeight)) {
            nSelectedBars |= POSITION_RIGHT;
        }
        if ((mouse_y >= (nTotalHeight - nHeightRB)) && (mouse_y <= nTotalHeight) && (mouse_x >= 0)
                && (mouse_x <= nTotalWidth)) {
            nSelectedBars |= POSITION_BOTTOM;
        }
        return nSelectedBars;
    }

    public static int checkPointOnSpot(int mouse_x, int mouse_y, int nSpotPosition) {
        int nWidthRB;
        int nHeightRB;
        int nBorderRB = Globals.ROTATION_BAR_BORDER;
        int nTotalWidth = JoglPanel.globals.width;
        int nTotalHeight = JoglPanel.globals.height;
        //int nSpotPosition = JoglPanel.globals.nRBHotSpotPos;
        int nSelectedSpots = 0;
        int nSpotDim;
        int startX, startY;
        int startSX;
        int startSY;
        //check each bar and return first spot that is selected
        //vertical
        nWidthRB = Globals.ROTATION_BAR_HEIGHT;
        nHeightRB = Globals.ROTATION_BAR_WIDTH;
        int min = nWidthRB;
        if (nHeightRB < min) {
            min = nHeightRB;
        }
        nSpotDim = (3 * ((min / 2) - nBorderRB)) / 4;//as half of the usable height * 3/4
        //left
        startX = 0;
        startY = (nTotalHeight - nHeightRB) / 2;
        startSX = startX + (nWidthRB / 2);
        startSY = startY + (nHeightRB / 2) + ((nSpotPosition * ((nHeightRB / 2) - nBorderRB - nSpotDim)) / 100);
        if ((mouse_x >= (startSX - nSpotDim)) && (mouse_x <= (startSX + nSpotDim)) && (mouse_y >= (startSY - nSpotDim))
                && (mouse_y <= (startSY + nSpotDim))) {
            nSelectedSpots |= POSITION_LEFT;
        }
        //right
        startX = nTotalWidth - nWidthRB;
        startY = (nTotalHeight - nHeightRB) / 2;
        startSX = startX + (nWidthRB / 2);
        startSY = startY + (nHeightRB / 2) + ((nSpotPosition * ((nHeightRB / 2) - nBorderRB - nSpotDim)) / 100);
        if ((mouse_x >= (startSX - nSpotDim)) && (mouse_x <= (startSX + nSpotDim)) && (mouse_y >= (startSY - nSpotDim))
                && (mouse_y <= (startSY + nSpotDim))) {
            nSelectedSpots |= POSITION_RIGHT;
        }
        //horizontal
        nWidthRB = Globals.ROTATION_BAR_WIDTH;
        nHeightRB = Globals.ROTATION_BAR_HEIGHT;
        //top
        startX = (nTotalWidth - nWidthRB) / 2;
        startY = 0;
        startSX = startX + (nWidthRB / 2) + ((nSpotPosition * ((nWidthRB / 2) - nBorderRB - nSpotDim)) / 100);
        startSY = startY + (nHeightRB / 2);
        if ((mouse_x >= (startSX - nSpotDim)) && (mouse_x <= (startSX + nSpotDim)) && (mouse_y >= (startSY - nSpotDim))
                && (mouse_y <= (startSY + nSpotDim))) {
            nSelectedSpots |= POSITION_TOP;
        }
        //bottom
        startX = (nTotalWidth - nWidthRB) / 2;
        startY = nTotalHeight - nHeightRB;
        startSX = startX + (nWidthRB / 2) + ((nSpotPosition * ((nWidthRB / 2) - nBorderRB - nSpotDim)) / 100);
        startSY = startY + (nHeightRB / 2);
        if ((mouse_x >= (startSX - nSpotDim)) && (mouse_x <= (startSX + nSpotDim)) && (mouse_y >= (startSY - nSpotDim))
                && (mouse_y <= (startSY + nSpotDim))) {
            nSelectedSpots |= POSITION_BOTTOM;
        }

        return nSelectedSpots;
    }

    public static int doMouse2RotBarDepl(int mouse_depl) {
        int nWidthRB;
        int nHeightRB;
        int nBorderRB = Globals.ROTATION_BAR_BORDER;
        nWidthRB = Globals.ROTATION_BAR_WIDTH;
        nHeightRB = Globals.ROTATION_BAR_HEIGHT;
        //        int nTotalWidth = JoglPanel.globals.width;
        int nSpotDim;
        nSpotDim = (3 * ((nHeightRB / 2) - nBorderRB)) / 4;//as half of the usable height * 3/4
        //compute total spot distance from 0 to the next
        int nTotalDist;
        nTotalDist = (nWidthRB / 2) - nBorderRB - nSpotDim;
        int percent = (100 * mouse_depl) / nTotalDist;
        return percent;
    }

    /**
     * draws the rotation bar considering constants from Globals and also variables giving state of spot
     * and position
     */
    /**    public void doRotationBar()
        {
            //width and height for rotation bar
            int nWidthRB;
            int nHeightRB;
            //border size, so that drawing width and height are nWidthRB-2*nBorderRB, nHeightRB-2*nBorderRB
            int nBorderRB = Globals.ROTATION_BAR_BORDER;
            //init color used in drawing the bar
            Color cBorderRB = new Color( 0xa8, 0xf8, 0xfb);//border color
            Color cBgRB = new Color( 0x6c, 0xb0, 0xcf);//background color
            Color cSpot = new Color( 0xd4, 0xd3, 0x70);//moveable dot color
            Color cHotSpot = new Color( 0, 0, 0);//selected dot color
            //the spot is a circle colored inside, that has the radius half the remaining height
            //because the drawing object doesn't implement circle, the spot will become a rectangle
            //compute width and height, width > height
            nWidthRB = Globals.ROTATION_BAR_WIDTH;
            nHeightRB = Globals.ROTATION_BAR_HEIGHT;
            //compute starting x and y
            //the bar will be drawn at bottom, centered
            //TODO: make a possibility to drawn it at top also, deppending on bTop2Bottom variable
            int startX, startY;
            startX = (nWidth-nWidthRB)/2;
            startY = nHeight-nHeightRB;
            //start drawing
            //first, draw background
            drawObject.setColor( cBgRB);
            drawObject.fillRectangle( startX, startY, nWidthRB, nHeightRB, SIXTH_DEPTH);
            //then draw border
            drawObject.setLineWidth(nBorderRB); drawObject.setColor( cBorderRB);
            drawObject.drawRectangle( startX, startY, nWidthRB, nHeightRB);
            drawObject.setLineWidth(1);
            //then draw spot
            //check to see if hot spot
            boolean bHotSpot = JoglPanel.globals.bRBIsHotSpot;
            //get spot position as percentage from -100 to 0 to 100 %
            int nSpotPosition = JoglPanel.globals.nRBHotSpotPos;
            //compute spot radius
            int nSpotDim;
            nSpotDim = 3*(nHeightRB/2-nBorderRB)/4;//as half of the usable height * 3/4
            //compute starting x position for center of spot
            int startSX;
            startSX = startX+nWidthRB/2+nSpotPosition*(nWidthRB/2-nBorderRB-nSpotDim)/100;
            int startSY;
            startSY = startY+nHeightRB/2;
            //draw supporting line for spot
            drawObject.setColor( cBorderRB);
            drawObject.fillRectangle( startX+nBorderRB+nSpotDim, startSY-1, nWidthRB-(nBorderRB+nSpotDim)*2, 2, FIFTH_DEPTH);
            //start drawing the spot
            drawObject.setColor( cSpot);
            drawObject.fillRectangle( startSX-nSpotDim, startSY-nSpotDim, nSpotDim*2, nSpotDim*2, FOURTH_DEPTH);
            //and the hotspot
            if ( bHotSpot ) {
                drawObject.setColor( cHotSpot);
            } else {
                drawObject.setColor( cBorderRB);
            }
            drawObject.drawRectangle( startSX-nSpotDim, startSY-nSpotDim, nSpotDim*2, nSpotDim*2);
            if ( bHotSpot ) {
                drawObject.setColor( 255, 255, 255);

                //should use other dimmensions correctly computed...
                drawObject.drawText( startX, startY-nFontSize-4, "drag yellow spot to rotate earth");
            };
        }
     */
    public static final int POSITION_LEFT = 1;
    public static final int POSITION_TOP = 2;
    public static final int POSITION_RIGHT = 4;
    public static final int POSITION_BOTTOM = 8;

    /**
     * shows a bar with a draggable spot in the specified part of the window:<br>
     * mode = 0 => left<br>
     * mode = 1 => top<br>
     * mode = 2 => right<br>
     * mode = 3 => bottom<br>
     * @param mode int value in range { 1, 2, 4, 8} to indicate left, top, right, bottom bound
     * @param nSpotPosition position of spot on bar in range -100 to 0 to 100
     * @param bShowTooltip show/hide utilisation tooltip
     */
    public void doRotationBar(int mode, int nSpotPosition, boolean bShowTooltip) {
        //return for unknown mode:
        if (!((mode == POSITION_LEFT) || (mode == POSITION_TOP) || (mode == POSITION_RIGHT) || (mode == POSITION_BOTTOM))) {
            return;
        }
        //width and height for rotation bar
        int nWidthRB;
        int nHeightRB;
        //border size, so that drawing width and height are nWidthRB-2*nBorderRB, nHeightRB-2*nBorderRB
        int nBorderRB = Globals.ROTATION_BAR_BORDER;
        //init color used in drawing the bar
        Color cBorderRB = new Color(0xa8, 0xf8, 0xfb);//border color
        Color cBgRB = new Color(0x6c, 0xb0, 0xcf);//background color
        Color cSpot = new Color(0xff, 0x00, 0x00);//0xd4, 0xd3, 0x70);//moveable dot color
        Color cHotSpot = new Color(0, 0, 0);//selected dot color border
        //the spot is a circle colored inside, that has the radius half the remaining height
        //because the drawing object doesn't implement circle, the spot will become a rectangle
        boolean bVerticalBar = ((mode == POSITION_LEFT) || (mode == POSITION_RIGHT));
        //compute width and height, width > height
        if (bVerticalBar) {//vertical bar
            nWidthRB = Globals.ROTATION_BAR_HEIGHT;
            nHeightRB = Globals.ROTATION_BAR_WIDTH;
        } else {//suppose horizontal bar
            nWidthRB = Globals.ROTATION_BAR_WIDTH;
            nHeightRB = Globals.ROTATION_BAR_HEIGHT;
        }
        //compute starting x and y
        //the bar will be drawn at bottom, centered
        //TODO: make a possibility to drawn it at top also, deppending on bTop2Bottom variable
        int startX, startY;
        //        startX = (nWidth-nWidthRB)/2;
        //        startY = nHeight-nHeightRB;
        if (bVerticalBar) {//vertical bar, see if top or bottom
            startY = (nHeight - nHeightRB) / 2;
            if (mode == POSITION_LEFT) {
                startX = 0;
            } else {
                startX = nWidth - nWidthRB;
            }
        } else { //suppose horizontal bar
            startX = (nWidth - nWidthRB) / 2;
            if (mode == POSITION_TOP) {
                startY = 0;
            } else {
                startY = nHeight - nHeightRB;
            }
        }
        //start drawing
        //first, draw background
        drawObject.setColor(cBgRB);
        drawObject.fillRectangle(startX, startY, nWidthRB, nHeightRB, THIRD_DEPTH);
        //then draw border
        drawObject.setLineWidth(nBorderRB);
        drawObject.setColor(cBorderRB);
        drawObject.drawRectangle(startX, startY, nWidthRB, nHeightRB);
        drawObject.setLineWidth(1);
        //then draw spot
        //check to see if hot spot
        boolean bHotSpot = (JoglPanel.globals.nRBHotSpot == mode);

        //get spot position as percentage from -100 to 0 to 100 %
        //int nSpotPosition = JoglPanel.globals.nRBHotSpotPos;
        //compute spot radius
        int nSpotDim;
        int min = nWidthRB;
        if (nHeightRB < min) {
            min = nHeightRB;
        }
        nSpotDim = (3 * ((min / 2) - nBorderRB)) / 4;//as half of the usable height * 3/4
        //compute starting x position for center of spot
        int startSX;
        int startSY;
        if (bVerticalBar) {//vertical bar
            startSX = startX + (nWidthRB / 2);
            startSY = startY + (nHeightRB / 2) + ((nSpotPosition * ((nHeightRB / 2) - nBorderRB - nSpotDim)) / 100);
        } else {
            startSX = startX + (nWidthRB / 2) + ((nSpotPosition * ((nWidthRB / 2) - nBorderRB - nSpotDim)) / 100);
            startSY = startY + (nHeightRB / 2);
        }
        //draw supporting line for spot
        drawObject.setColor(cBorderRB);
        if (bVerticalBar) {
            drawObject.fillRectangle(startSX - 1, startY + nBorderRB + nSpotDim, 2, nHeightRB
                    - ((nBorderRB + nSpotDim) * 2), SECOND_DEPTH);
        } else {
            drawObject.fillRectangle(startX + nBorderRB + nSpotDim, startSY - 1, nWidthRB
                    - ((nBorderRB + nSpotDim) * 2), 2, SECOND_DEPTH);
        }
        //start drawing the spot
        drawObject.setColor(cSpot);
        drawObject.fillRectangle(startSX - nSpotDim, startSY - nSpotDim, nSpotDim * 2, nSpotDim * 2, FIRST_DEPTH);
        //and the hotspot
        if (bHotSpot) {
            drawObject.setColor(cHotSpot);
        } else {
            drawObject.setColor(cBorderRB);
        }
        drawObject.drawRectangle(startSX - nSpotDim, startSY - nSpotDim, nSpotDim * 2, nSpotDim * 2);
        if (bShowTooltip) {
            drawObject.setColor(255, 255, 255);
            //            float oldFontSpaceY = drawObject.getFontSpaceY();
            //            drawObject.setFontSpaceY(0);
            //should use other dimmensions correctly computed...
            String sText;
            if (bVerticalBar) {
                sText = "drag red spot\nto rotate earth";
            } else {
                sText = "drag red spot to rotate earth";
            }
            Dimension dim = drawObject.getTextDimensions(sText);
            int nTextPosX, nTextPosY;
            if (bVerticalBar) {
                nTextPosY = startSY - ((int) dim.getHeight() / 2);
                if (mode == POSITION_LEFT) {
                    nTextPosX = startX + nWidthRB;
                } else {
                    nTextPosX = startX - (int) dim.getWidth();
                }
            } else {
                nTextPosX = startX - (((int) dim.getWidth() - nWidthRB) / 2);
                if (mode == POSITION_TOP) {
                    nTextPosY = startY + nHeightRB;
                } else {
                    nTextPosY = startY - (int) dim.getHeight();
                }
            }
            drawObject.drawText(nTextPosX, nTextPosY, sText);
            //            drawObject.setFontSpaceY(oldFontSpaceY);
        }
        ;
    }

    public void setActiveColorSet(int set) {
        if (set == 2) {
            nActiveColorSet = 2;
        } else {
            nActiveColorSet = 1;
        }
    }

    /**
     * <b>Drawing2DTo3D</b><br>
     * @author Luc<br><br>
     * offers 2d drawing primitives to draw on info plane
     * that is actually a 3d plane.<br>
     * the conversion from integer coordinates to real 3d coordinates
     * is entirely done by this class<br>
     */
    private class Drawing2DTo3D {
        private float dx = 0;//width of info drawing plane
        private float dy = 0;//height of info drawing plane
        //private float z=0;//position of info drawing plane from eye on axis leaving the eye and perpendicular on plane
        private float dz = Globals.MIN_DEPTH;//width of space for more planes drawing
        private float pixelWidth = 0f;//a screen pixel's width on info plane
        private float pixelHeight = 0f;//a screen pixel's height on info plane, should be equal with pixelWidth

        private final GL2 gl;//graphical context used for drawing in 3D
        private final MyGLUT ipglut = new MyGLUT();

        private final VectorO vTopLeftCorner;//left-top corner for 2d plane in 3d space
        private final VectorO vXAxis;//2d x axis mapped in 3d space
        private final VectorO vYAxis;//2d y axis mapped in 3d space
        private final VectorO vZAxis;//eye direction, perpendicular to (x,y) plane

        private final VectorO vRotAxis1, vRotAxis2;//rotation axis for 3d objects to get into info plane
        private float fRotAngle1, fRotAngle2;//rotation angles for 3d objects to get into info plane

        private float red, green, blue;//components for current color
        private float alpha = 1.0f;//alpha value
        private int lineWidth;//line width
        private boolean bUseAlpha = true;

        private boolean bFontTypeBitmap = false;//font type, true=bitmap font, false=stroke font
        private int font = MyGLUT.STROKE_MONO_ROMAN;/**/
        private float fontHeightTop = 0;
        private float fontHeightBottom = 0;
        private final int fontSize;
        private float fontSpaceY;//fraction of full font height that represents the vertical space between lines of text
        private final float fontScaleY, fontScaleX;//scale factor for x and y axis, should be the same

        /**
         * init variables needed for drawing in 3d space
         * @param gl
         * @param ip
         */
        public Drawing2DTo3D(GL2 gl) {
            this.gl = gl;
            //text display in 3d space  related variables
            float dzmin = Globals.NEAR_CLIP;
            float alpha_x = Globals.FOV_ANGLE;
            dz = Globals.MIN_DEPTH;
            float z = dzmin + (0.5f * dz);
            //width of available screen at z position
            dx = 2 * z * (float) Math.tan((0.5 * alpha_x * Math.PI) / 180);
            //height of available screen at z position
            dy = dx / JoglPanel.globals.fAspect;

            //compute screen's pixel width on info plane:
            pixelWidth = dx / nWidth;
            pixelHeight = dy / nHeight;//should be equal with width

            //init font related variables
            fontSize = nFontSize;
            fontSpaceY = .25f;

            bFontTypeBitmap = true;
            font = MyGLUT.STROKE_ROMAN;//STROKE_MONO_ROMAN;/**/
            font = MyGLUT.BITMAP_8_BY_13;
            if (!bFontTypeBitmap) {
                fontHeightTop = ipglut.glutStrokeHeightTop(font);
                fontHeightBottom = ipglut.glutStrokeHeightBottom(font);
            } else {
                fontHeightTop = ipglut.glutBitmapHeightTop(font);
                fontHeightBottom = ipglut.glutBitmapHeightBottom(font);
            }

            //compute factor of scaling on y axis as height to be
            //divided by actual font height; height to be is
            //requested font size, in pixels, multiplied by a pixel's
            //height
            fontScaleY = (fontSize * pixelHeight) / fontHeightTop;
            //compute factor of scaling on x axis, that depends on
            //y scaling
            fontScaleX = (fontSize * pixelWidth) / fontHeightTop;

            //construct starting upper-left point and the left2right and top2bottom axis
            //used for drawing in 2d, to transform easely to 3d
            vYAxis = new VectorO(JoglPanel.globals.EyeNormal);//init with eye direction
            vYAxis.MultiplyScalar(-1);//change direction because of how the 2d
            //coordinates are used: from top to bottom
            vZAxis = new VectorO(JoglPanel.globals.EyeDirection);
            //construct x axis as cross product between top to bottom direction
            //with eye into screen direction
            vXAxis = vYAxis.CrossProduct(vZAxis);
            //construct starting point: from center of screen to top left corner
            vTopLeftCorner = new VectorO(JoglPanel.globals.EyePosition);//starts from eye position
            VectorO vAux = new VectorO(vZAxis);
            vAux.MultiplyScalar(z);
            vTopLeftCorner.AddVector(vAux);//position on center of plane
            vAux.duplicate(vXAxis);
            vAux.MultiplyScalar((-pixelWidth * nWidth) / 2f);
            vTopLeftCorner.AddVector(vAux);//move to extreme left position on x axis
            vAux.duplicate(vYAxis);
            vAux.MultiplyScalar((-pixelHeight * nHeight) / 2f);
            vTopLeftCorner.AddVector(vAux);//move to extreme top position on y axis
            //so, now, vTopLeftCorner is at (0,0) coordinates on 2d plane

            //compute rotation parameters for 3d objects to get into this drawing
            //3d plane
            //set text draw plane
            vRotAxis2 = new VectorO(vZAxis);
            //VectorO vY = new VectorO(vYAxis);//opposed to eye normal
            //VectorO vM = new VectorO(vXAxis);
            vRotAxis2.MultiplyScalar(-1);
            VectorO vZ = new VectorO(0, 0, 1);
            fRotAngle1 = 0;
            fRotAngle2 = 0;
            vRotAxis1 = vZ.CrossProduct(vRotAxis2);
            //obtain first rotation angle to get z vector over -vD vector in degrees
            fRotAngle1 = (float) ((Math.acos(Globals.limitValue(vZ.DotProduct(vRotAxis2), -1, 1)) * 180) / Math.PI);
            //check to see if angle 1 is the correct one:
            vZ.RotateDegree(vRotAxis1, fRotAngle1);
            if (!vZ.equals(vRotAxis2)) {
                fRotAngle1 = 360 - fRotAngle1;
            }
            //update x axis to be able to rotate it also
            VectorO vX = new VectorO(1, 0, 0);
            vX.RotateDegree(vRotAxis1, fRotAngle1);
            //obtain second rotation angle to get x and y vectors over vM and vN in degrees
            fRotAngle2 = (float) ((Math.acos(Globals.limitValue(vX.DotProduct(vXAxis), -1, 1)) * 180) / Math.PI);
            //check to see if angle 2 is the correct one:
            vX.RotateDegree(vRotAxis2, fRotAngle2);
            if (!vX.equals(vXAxis)) {
                fRotAngle2 = 360 - fRotAngle2;
            }
        }

        /**
         * computes height in pixels for a text that is wrapped so
         * that could have the given width.<br>
         * @param width maximal width of text in pixels
         * @param sText
         * @return the height the text would have, also in pixels
         */
        public int getTextHeight(int width, String sText) {
            //find dimension on y axis, with vertical line spacing
            float fWidth = (width * pixelWidth) / fontScaleX;//maximal width for text
            int nNoLines = 0;//lines number
            //on several rows if "\n" character is encountered
            //and also if width is depasita
            float faux;
            String[] result = sText.split("\\n");
            StringBuilder lineText = new StringBuilder();
            StringBuilder restOfLineText = new StringBuilder();
            int i = 0;
            for (; i < result.length; i++) {
                //get next line
                lineText.delete(0, lineText.length());
                lineText.append(result[i]);
                while (lineText.length() > 0) {
                    nNoLines++;//new line
                    restOfLineText.delete(0, restOfLineText.length());
                    do {
                        faux = getStringLength(font, lineText.toString());
                        if (faux > fWidth) {//remove characters if line too long
                            restOfLineText.insert(0, lineText.charAt(lineText.length() - 1));
                            lineText.deleteCharAt(lineText.length() - 1);
                        }
                        ;
                    } while (faux > fWidth);
                    //System.out.println("line "+nNoLines+" is $"+lineText+"$");
                    lineText.delete(0, lineText.length());
                    lineText.append(restOfLineText);
                }
                ;
            }
            //System.out.println("number of lines: "+nNoLines);
            //compute text height based on number of lines
            float dimy;
            dimy = ((nNoLines * (1 + fontSpaceY)) - fontSpaceY) * (fontHeightTop - fontHeightBottom);
            //System.out.println("text height: "+(int)((nNoLines*(1+fontSpaceY)-fontSpaceY))*(fontHeightTop-fontHeightBottom)*fontScaleY/pixelHeight));
            //convert 3d dimensions in 2d integer dimensions
            return (int) Math.ceil((dimy * fontScaleY) / pixelHeight);
        }

        /**
         * gets the height of text, given a reference height<br>
         * used to be continued with another section of text as it includes the spacing between lines
         * of text if bFinalVerticalHeight is true<br>
         * if bOnlyLess is true, it does not exceeds the desired height
         * @param width
         * @param sText
         * @param desiredHeight
         * @param bOnlyLess true if required to be less than provided height
         * @param bFinalVertSpacing keep final verical spacing or not
         * @return height in pixels of text
         */
        public int getTextHeight(int width, String sText, int desiredHeight, boolean bOnlyLess,
                boolean bFinalVertSpacing) {
            boolean bOverOnce = !bOnlyLess;//if can be less or equal, or a little bit over, then we have over only once
            //find dimension on y axis, with vertical line spacing
            float fWidth = (width * pixelWidth) / fontScaleX;//maximal width for text
            float fHeight = (desiredHeight * pixelHeight) / fontScaleY;//maximal height for text in font dimension
            float lineHeight = (fontHeightTop - fontHeightBottom);
            //int nNoLines = 0;//lines number
            //on several rows if "\n" character is encountered
            //and also if width is depasita
            float currentHeight = 0f;
            float antHeight = 0f;
            float faux;
            String[] result = sText.split("\\n");
            StringBuilder lineText = new StringBuilder();
            StringBuilder restOfLineText = new StringBuilder();
            int i = 0;
            boolean bFirst = true;
            for (; i < result.length; i++) {
                //get next line
                lineText.delete(0, lineText.length());
                lineText.append(result[i]);
                while (lineText.length() > 0) {
                    //if first line
                    if (bFirst) {
                        bFirst = false;
                        //add line and check if is over
                        currentHeight += lineHeight;
                        if (currentHeight > fHeight) {//if it is, then we must return 0 if only less, or line height plus bFinalVertSpacing
                            if (bFinalVertSpacing) {
                                currentHeight += fontSpaceY * lineHeight;
                            }
                            if (bOverOnce) {
                                return (int) Math.ceil((currentHeight * fontScaleY) / pixelHeight);
                            } else {
                                return 0;
                            }
                        }
                        //else we go on to the next lines
                    } else {//not any more the first line
                        antHeight = currentHeight;
                        currentHeight += (1 + fontSpaceY) * lineHeight;//add vertical spacing from previous line
                        //check for a new line
                        if (currentHeight > fHeight) {//if this line's height is over
                            if (bFinalVertSpacing) {//add final vertical spacing if the case
                                currentHeight += fontSpaceY * lineHeight;
                                antHeight += fontSpaceY * lineHeight;
                            }
                            ;
                            if (bOverOnce) {
                                return (int) Math.ceil((currentHeight * fontScaleY) / pixelHeight);
                            } else {
                                return (int) Math.ceil((antHeight * fontScaleY) / pixelHeight);
                            }
                        }
                    }

                    restOfLineText.delete(0, restOfLineText.length());
                    do {
                        faux = getStringLength(font, lineText.toString());
                        if (faux > fWidth) {//remove characters if line too long
                            restOfLineText.insert(0, lineText.charAt(lineText.length() - 1));
                            lineText.deleteCharAt(lineText.length() - 1);
                        }
                        ;
                    } while (faux > fWidth);
                    lineText.delete(0, lineText.length());
                    lineText.append(restOfLineText);
                }
                ;
            }
            //the text's height is never greater than desired height, so check to see if finalverticalspacing
            if (bFinalVertSpacing) {
                currentHeight += fontSpaceY * lineHeight;
            }
            //compute text height based on number of lines
            //float dimy = (nNoLines*(1+fontSpaceY)-fontSpaceY)*(fontHeightTop-fontHeightBottom);
            //convert 3d dimensions in 2d integer dimensions
            return (int) Math.ceil((currentHeight * fontScaleY) / pixelHeight);
        }

        /**
         * computes dimensions in pixels for a text that is about to be drawn.<br>
         * the dimension si greater or equal to real size of text
         * @param sText
         * @return a Dimension object containing width and height in pixels
         * of text greater or equal to real size of text, because of approximation
         */
        public Dimension getTextDimensions(String sText) {
            float dimx = 0f, dimy;
            float faux = 0f;
            //find maximal dimension on x axis, and then cumulate the lines to
            //find dimension on y axis, with vertical line spacing
            String[] result = sText.split("\\n");
            for (int i = 0; i < result.length; i++) {
                faux = getStringLength(font, result[i]);
                if (bDebug) {
                    System.out.println("Debug in Drawing2DTo3D.getTextDimensions ipglut.glutStrokeLength(" + result[i]
                            + ")=" + faux);
                }
                if (faux > dimx) {
                    dimx = faux;
                }
            }
            //compute text height based on number of lines
            dimy = ((result.length * (1 + fontSpaceY)) - fontSpaceY) * (fontHeightTop - fontHeightBottom);
            if (bDebug) {
                System.out.println("Debug in Drawing2DTo3D.getTextDimensions float (" + dimx + "," + dimy + ")");
            }
            if (bDebug) {
                System.out.println("Debug in Drawing2DTo3D.getTextDimensions dx=" + dx + " nWidth=" + nWidth
                        + " fontSize=" + fontSize + " pixelWidth=" + pixelWidth + " fontHeightTop=" + fontHeightTop
                        + " fontScaleX=" + fontScaleX);
            }
            if (bDebug) {
                System.out.println("Debug in Drawing2DTo3D.getTextDimensions int ("
                        + (int) Math.ceil((dimx * fontScaleX) / pixelWidth) + ","
                        + (int) Math.ceil((dimy * fontScaleY) / pixelHeight) + ")");
            }
            //convert 3d dimensions in 2d integer dimensions
            return new Dimension((int) Math.ceil((dimx * fontScaleX) / pixelWidth), (int) Math.ceil((dimy * fontScaleY)
                    / pixelHeight));
        }

        public String getRemainingText(int x, int y, int width, int height, String sText, boolean bOnlyLess) {
            StringBuilder sbResult = new StringBuilder();
            boolean bOverOnce = !bOnlyLess;//if can be less or equal, or a little bit over, then we have over only once
            //draw text
            float fWidth = (width * pixelWidth) / fontScaleX;//maximal width for text
            float fHeight = (height * pixelHeight) / fontScaleY;//maximal height for text
            float lineHeight = (fontHeightTop - fontHeightBottom);
            float currentHeight = 0f;//current height of text
            //on several rows if "\n" character is encountered
            //and also if width is surpassed
            float faux;
            String[] result = sText.split("\\n");
            StringBuilder lineText = new StringBuilder();
            StringBuilder restOfLineText = new StringBuilder();
            int i = 0;
            boolean bBreak = false;
            for (; i < result.length; i++) {
                //get next line
                lineText.delete(0, lineText.length());
                lineText.append(result[i]);
                while (lineText.length() > 0) {
                    //add this line's height to current height
                    currentHeight += lineHeight;
                    if (currentHeight > fHeight) {
                        //with this line the height would be greater than requested
                        //so discard it.
                        if (!bOverOnce) {
                            bBreak = true;
                            break;//by leaving the while and then the for
                        }
                        bOverOnce = false;
                    }
                    restOfLineText.delete(0, restOfLineText.length());
                    do {
                        faux = getStringLength(font, lineText.toString());
                        if (faux > fWidth) {//remove characters if line too long
                            restOfLineText.insert(0, lineText.charAt(lineText.length() - 1));
                            lineText.deleteCharAt(lineText.length() - 1);
                        }
                        ;
                    } while (faux > fWidth);
                    //add spacing to next line
                    currentHeight += fontSpaceY * lineHeight;

                    lineText.delete(0, lineText.length());
                    lineText.append(restOfLineText);
                }
                ;
                if (bBreak) {
                    break;
                }
            }
            if (restOfLineText.length() > 0) {
                sbResult.append(restOfLineText + "\n");
                i++;//over height error in middle of line, so put rest of line and then go to next lines
            }
            ;
            //add discarded lines starting at last i
            for (; i < result.length; i++) {
                sbResult.append(result[i] + "\n");
            }

            //System.out.println("text $"+sText+"$ has height: "+(int)(currentHeight*fontScaleY/pixelHeight));
            return sbResult.toString();
        }

        /**
         * draws the text given as input parameter starting from
         * given position, below and to the right, using current font
         * name, size and color
         * @param x start left pixel from which the text is drawn to the left
         * @param y start top pixel from which the text is drawn up-down
         * @param sText text to be drawn
         */
        public void drawText(int x, int y, String sText) {
            //save gl matrix
            gl.glPushMatrix();

            position3DObjectInPlane(x, y);

            //scale text to draw it
            gl.glScalef(fontScaleX, fontScaleY, 1f);
            //translate text so that it's top-left corner is situated in (0,0,0)
            gl.glTranslatef(0f, -fontHeightTop, 0f);
            //draw text
            //on several rows if "\n" character is encountered
            String[] result = sText.split("\\n");
            for (int i = 0; i < result.length; i++) {
                gl.glPushMatrix();
                //renderStrokeString( gl, font, result[i]);
                renderString(gl, font, result[i]);
                gl.glPopMatrix();
                //translate so that next tokens be under current, with full height
                //of text and the font spaceing on y
                gl.glTranslatef(0f, -(1 + fontSpaceY) * (fontHeightTop - fontHeightBottom), 0f);
            }

            //revert to initial draw matrix
            gl.glPopMatrix();
        }

        /**
         * draws the text given as input parameter starting from
         * given position, below and to the right, using current font
         * name, size and color in respect to given dimensions; text is
         * wrapped automatically to respect the width and is cropped if
         * hight is smaller than its real height.
         * @param x start left pixel from which the text is drawn to the left
         * @param y start top pixel from which the text is drawn up-down
         * @param width width of rectangle the text should be drawn in
         * @param height height of rectangle
         * @param sText text to be drawn
         * @param bOnlyLess true if text height is to be less than maximal height,
         * and false if text can go over maximal line with only one line
         * @return returns remaining text to be drawn
         */
        public String drawText(int x, int y, int width, int height, String sText, boolean bOnlyLess) {
            StringBuilder sbResult = new StringBuilder();
            boolean bOverOnce = !bOnlyLess;//if can be less or equal, or a little bit over, then we have over only once
            //save gl matrix
            gl.glPushMatrix();

            position3DObjectInPlane(x, y);

            //scale text to draw it
            gl.glScalef(fontScaleX, fontScaleY, 1f);
            //translate text so that it's top-left corner is situated in (0,0,0)
            gl.glTranslatef(0f, -fontHeightTop, 0f);
            //draw text
            float fWidth = (width * pixelWidth) / fontScaleX;//maximal width for text
            float fHeight = (height * pixelHeight) / fontScaleY;//maximal height for text
            float lineHeight = (fontHeightTop - fontHeightBottom);
            float currentHeight = 0f;//current height of text
            //on several rows if "\n" character is encountered
            //and also if width is surpassed
            float faux;
            String[] result = sText.split("\\n");
            StringBuilder lineText = new StringBuilder();
            StringBuilder restOfLineText = new StringBuilder();
            int i = 0;
            boolean bBreak = false;
            for (; i < result.length; i++) {
                //get next line
                lineText.delete(0, lineText.length());
                lineText.append(result[i]);
                while (lineText.length() > 0) {
                    //add this line's height to current height
                    currentHeight += lineHeight;
                    if (currentHeight > fHeight) {
                        //with this line the height would be greater than requested
                        //so discard it.
                        if (!bOverOnce) {
                            bBreak = true;
                            break;//by leaving the while and then the for
                        }
                        bOverOnce = false;
                    }
                    restOfLineText.delete(0, restOfLineText.length());
                    do {
                        faux = getStringLength(font, lineText.toString());
                        if (faux > fWidth) {//remove characters if line too long
                            restOfLineText.insert(0, lineText.charAt(lineText.length() - 1));
                            lineText.deleteCharAt(lineText.length() - 1);
                        }
                        ;
                    } while (faux > fWidth);
                    gl.glPushMatrix();
                    //renderStrokeString( gl, font, lineText.toString());
                    renderString(gl, font, lineText.toString());
                    gl.glPopMatrix();
                    //add spacing to next line
                    currentHeight += fontSpaceY * lineHeight;
                    //translate so that next tokens be under current, with full height
                    //of text and the font spaceing on y
                    gl.glTranslatef(0f, -(1 + fontSpaceY) * lineHeight, 0f);

                    lineText.delete(0, lineText.length());
                    lineText.append(restOfLineText);
                }
                ;
                if (bBreak) {
                    break;
                }
            }
            if (restOfLineText.length() > 0) {
                sbResult.append(restOfLineText + "\n");
                i++;//over height error in middle of line, so put rest of line and then go to next lines
            }
            ;
            //add discarded lines starting at last i
            for (; i < result.length; i++) {
                sbResult.append(result[i] + "\n");
            }

            //revert to initial draw matrix
            gl.glPopMatrix();
            //System.out.println("text $"+sText+"$ has height: "+(int)(currentHeight*fontScaleY/pixelHeight));
            return sbResult.toString();
        }

        /**
         * draws a texture at given coordinates and with given dimensions
         * @param x
         * @param y
         * @param width
         * @param height
         * @param imgID
         */
        public void drawImage(int x, int y, int width, int height, int imgID) {
            //transform width and height in 3d space distances
            float fWidth = width * pixelWidth;
            float fHeight = height * pixelHeight;

            //save gl matrix
            gl.glPushMatrix();

            position3DObjectInPlane(x, y);

            //enable texturing
            gl.glEnable(GL.GL_TEXTURE_2D);
            //draw the image
            float lT = 0f, rT = 1f, bT = 0f, tT = 1f;
            if ((imgID == 0) || !gl.glIsTexture(imgID)) {
                imgID = JoglPanel.globals.no_texture_id;
            }
            gl.glBindTexture(GL.GL_TEXTURE_2D, imgID);
            gl.glBegin(GL.GL_TRIANGLE_STRIP);
            gl.glTexCoord2f(lT, tT);
            gl.glVertex3f(0f, 0f, 0f);
            gl.glTexCoord2f(lT, bT);
            gl.glVertex3f(0f, -fHeight, 0f);
            gl.glTexCoord2f(rT, tT);
            gl.glVertex3f(fWidth, 0f, 0f);
            gl.glTexCoord2f(rT, bT);
            gl.glVertex3f(fWidth, -fHeight, 0f);
            gl.glEnd();

            //Texture.drawTree( gl);
            gl.glDisable(GL.GL_TEXTURE_2D);

            //revert to initial draw matrix
            gl.glPopMatrix();
        }

        /**
         * fills an rectangle with current selected color
         * and then positions it to its correct coordinates in info plane.
         * @param x
         * @param y
         * @param width
         * @param height
         */
        public void fillRectangle(int x, int y, int width, int height) {
            fillRectangle(x, y, width, height, -.001f);
        }

        /**
         * fills an rectangle with current selected color
         * and then positions it to its correct coordinates in info plane.<br>
         * It uses zIndex to draw in a paralel plane so that would not overlap
         * with another object.
         * @param x
         * @param y
         * @param width
         * @param height
         */
        public void fillRectangle(int x, int y, int width, int height, float zIndex) {
            //            int x2 = x+width-1;
            //            int y2 = y+height-1;

            //save gl matrix
            gl.glPushMatrix();

            position3DObjectInPlane(x, y);

            //filled rectangle is drawn below and to the right of (0,0,0)
            //with a blank margin of 0.5 px on x and y
            gl.glBegin(GL2.GL_POLYGON);
            gl.glVertex3f(0.5f * pixelWidth, (-height + 0.5f) * pixelHeight, zIndex * dz);
            gl.glVertex3f((width - 0.5f) * pixelWidth, (-height + 0.5f) * pixelHeight, zIndex * dz);
            gl.glVertex3f((width - 0.5f) * pixelWidth, -.5f * pixelHeight, zIndex * dz);
            gl.glVertex3f(0.5f * pixelWidth, -.5f * pixelHeight, zIndex * dz);
            gl.glEnd();

            //revert to initial draw matrix
            gl.glPopMatrix();
        }

        /**
         * draws a set of four lines to form a border for an rectangle
         * @param x
         * @param y
         * @param width
         * @param height
         */
        public void drawRectangle(int x, int y, int width, int height) {
            int x2 = (x + width) - 1;
            int y2 = (y + height) - 1;
            drawLine(x, y, x2, y);//upper horizontal line
            drawLine(x2, y, x2, y2);//right vertical line
            drawLine(x2, y2, x, y2);//lower horizontal line
            drawLine(x, y2, x, y);//left vertical line
        }

        /**
         * draw line from point in info plane to another external point
         * @param x
         * @param y
         * @param vPoint
         */
        public void drawLine2Point(int x, int y, float[] coords) {
            if ((coords == null) || (coords.length != 3)) {
                return;
            }
            VectorO vPoint1, vAux;
            vPoint1 = new VectorO(vTopLeftCorner);
            vAux = new VectorO(vXAxis);
            vAux.MultiplyScalar(pixelWidth * (x + .5f));//add shift inside of pixel for centering line
            vPoint1.AddVector(vAux);//position on x axis
            vAux.duplicate(vYAxis);
            vAux.MultiplyScalar(pixelHeight * (y + .5f));//add shift inside of pixel for centering line
            vPoint1.AddVector(vAux);//position also on y axis
            gl.glBegin(GL.GL_LINES);
            gl.glVertex3f(vPoint1.getX(), vPoint1.getY(), vPoint1.getZ());
            gl.glVertex3f(coords[0], coords[1], coords[2]);
            gl.glEnd();
        }

        /**
         * draw line from point in info plane to another external point
         * @param x
         * @param y
         * @param vPoint
         */
        public void drawLine2Point(int x, int y, VectorO vPoint) {
            drawLine2Point(x, y, new float[] { vPoint.getX(), vPoint.getY(), vPoint.getZ() });
        }

        /**
         * draws a line from one 2d point to another in 3d space<br>
         * For that, it computes the real 3d coordinates and, tacking
         * in consideration line width, shifts with lineWidht/2 from
         * computed position on x and y. That has to be done because of
         * the fact that lineWidth is rasterized(2d) width, not 3d width.
         * @param x1
         * @param y1
         * @param x2
         * @param y2
         */
        public void drawLine(int x1, int y1, int x2, int y2) {
            VectorO vPoint1, vPoint2, vAux;
            vPoint1 = new VectorO(vTopLeftCorner);
            vAux = new VectorO(vXAxis);
            vAux.MultiplyScalar(pixelWidth * (x1 + .5f));//add shift inside of pixel for centering line
            vPoint1.AddVector(vAux);//position on x axis
            vAux.duplicate(vYAxis);
            vAux.MultiplyScalar(pixelHeight * (y1 + .5f));//add shift inside of pixel for centering line
            vPoint1.AddVector(vAux);//position also on y axis
            vPoint2 = new VectorO(vTopLeftCorner);
            vAux.duplicate(vXAxis);
            vAux.MultiplyScalar(pixelWidth * (x2 + .5f));//add shift inside of pixel for centering line
            vPoint2.AddVector(vAux);//position on x axis
            vAux.duplicate(vYAxis);
            vAux.MultiplyScalar(pixelHeight * (y2 + .5f));//add shift inside of pixel for centering line
            vPoint2.AddVector(vAux);//position also on y axis
            gl.glBegin(GL.GL_LINES);
            gl.glVertex3f(vPoint1.getX(), vPoint1.getY(), vPoint1.getZ());
            gl.glVertex3f(vPoint2.getX(), vPoint2.getY(), vPoint2.getZ());
            gl.glEnd();
        }

        public void setOpaque(boolean bOpaque) {
            bUseAlpha = !bOpaque;
        }

        public void setColor(Color c) {
            red = c.getRed() / 255f;
            green = c.getGreen() / 255f;
            blue = c.getBlue() / 255f;
            if (bUseAlpha) {
                gl.glColor4f(red, green, blue, alpha);
            } else {
                gl.glColor4f(red, green, blue, 1.0f);
            }
        }

        public void setColor(int r, int g, int b) {
            red = r / 255f;
            green = g / 255f;
            blue = b / 255f;
            if (bUseAlpha) {
                gl.glColor4f(red, green, blue, alpha);
            } else {
                gl.glColor4f(red, green, blue, 1.0f);
            }
        }

        public void setColor(float r, float g, float b) {
            red = r;
            green = g;
            blue = b;
            if (bUseAlpha) {
                gl.glColor4f(red, green, blue, alpha);
            } else {
                gl.glColor4f(red, green, blue, 1.0f);
            }
        }

        public void setColorv(float[] v) {
            red = v[0];
            green = v[1];
            blue = v[2];
            if (bUseAlpha) {
                gl.glColor4f(red, green, blue, alpha);
            } else {
                gl.glColor4f(red, green, blue, 1.0f);
            }
        }

        public void setLineWidth(int lineW) {
            lineWidth = lineW;
            gl.glLineWidth(lineWidth);
        }

        /**
         * positions an object in 2d plane.<br>
         * Object is drawn in usual plane (x,y) centered in (0,0,0) and
         * is rotated to be in 2d plane, and then translated so that
         * point (0,0,0) to be mapped to 2d coordinates (x,y) in 3d info drawing
         * plane.<br><br>
         * <b>ATTENTION:</b> Current matrix is modiffied, so it should be saved before
         * calling this function, and restore after.<br>
         * Call this function before drawing the object to affect its position.
         * @param x position on info plane
         * @param y position on info plane
         */
        private void position3DObjectInPlane(int x, int y) {

            //translate 3d point (0,0,0) to 3d point in 2d space: (x,y)
            VectorO vPos = new VectorO(vTopLeftCorner);
            VectorO vAux = new VectorO(vXAxis);
            vAux.MultiplyScalar(pixelWidth * x);
            vPos.AddVector(vAux);//position on x axis
            vAux.duplicate(vYAxis);
            vAux.MultiplyScalar(pixelHeight * y);
            vPos.AddVector(vAux);//position also on y axis
            gl.glTranslatef(vPos.getX(), vPos.getY(), vPos.getZ());

            //rotate text to be in screen(z) plane
            gl.glRotatef(fRotAngle2, vRotAxis2.getX(), vRotAxis2.getY(), vRotAxis2.getZ());
            gl.glRotatef(fRotAngle1, vRotAxis1.getX(), vRotAxis1.getY(), vRotAxis1.getZ());
        }

        /**
         * calls glut functions to render text, depending on current font
         * @param gl
         * @param font
         * @param string
         */
        private void renderString(GL2 gl, int font, String string) {
            if (bFontTypeBitmap) {
                gl.glRasterPos2f(0f, 0f);
                ipglut.glutBitmapString(gl, font, string);
            } else {
                ipglut.glutStrokeString(gl, font, string);
            }
        }

        private float getStringLength(int font, String string) {
            if (bFontTypeBitmap) {
                return ipglut.glutBitmapLength(font, string);
            }
            return ipglut.glutStrokeLength(font, string);
        }

        public boolean isImage(int imageID) {
            return gl.glIsTexture(imageID);
        }

        public float getFontSpaceY() {
            return fontSpaceY;
        }

        public void setFontSpaceY(float fontSpaceY) {
            this.fontSpaceY = fontSpaceY;
        }
    }

    public boolean isBDebug() {
        return bDebug;
    }

    public void setBDebug(boolean debug) {
        bDebug = debug;
    }

    public void setBgAlphaOnly(boolean alphaOnlyBg) {
        bAlphaOnlyBg = alphaOnlyBg;
    }
}
