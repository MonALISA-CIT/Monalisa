/*
 * Created on Mar 29, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl;

import java.util.ArrayList;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;

/**
 * @author mluc
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class JoGLArc3D {

    /**
     * the projections x, y, z of the start vector are equals with the coordinates values for the
     * peek of the vector, because the base point is in (0,0,0), and also in the center of the sphere
     */
    protected VectorO vStart = null;
    protected VectorO vEnd = null;
    protected VectorO vCrossProduct = null;
    //float phiAngle, thetaAngle;/* angles are in degrees */
    protected double fArcRadius;
    /**
     * number of points to be drawn
     */
    protected int nrPoints;
    /**
     * the increment angle to get to the next point on the arc
     */
    protected float degreeIncrement;
    /**
     * bowParalel represents the deviation of the arc from its natural path in the plane formed by vStart and vEnd
     */
    protected double bowParalel = 0.2;
    /**
     * z translation for rendering the point
     */
    protected float zTranslation = 0;
    /**
     * angle between vectors
     */
    protected double angleVectors = 0;
    /**
     * what type of projection is, based on initial radius
     */
    protected boolean bPlaneProjection = false;

    protected ArrayList points = null;//points that compose the arc

    /**
     * fractionRadius is used to indicate how much the arc will get above the sphere
     * @param fromLAT
     * @param fromLONG
     * @param toLAT
     * @param toLONG
     * @param radius
     * @param degreeIncrement
     * @param fractionRadius defaults to 0.2
     */
    public JoGLArc3D(double fromLAT, double fromLONG, double toLAT, double toLONG, double radius,
            float degreeIncrement, double fractionRadius, float zTranslation) {
        if (radius == -1) {
            bPlaneProjection = true;
        }
        if (bPlaneProjection) {
            fArcRadius = Globals.MAP_HEIGHT / 2;
            this.zTranslation = 0;
            vStart = new VectorO((fromLONG / 360f) * Globals.MAP_WIDTH, (fromLAT / 180f) * Globals.MAP_HEIGHT, 0);
            vEnd = new VectorO((toLONG / 360f) * Globals.MAP_WIDTH, (toLAT / 180f) * Globals.MAP_HEIGHT, 0);
            vCrossProduct = new VectorO(0, 0, 1);

            //apply the "3 simple" rule to compute angle on plane projection, considering that the diagonal of the map coresponds to 2*PI
            angleVectors = (2 * Math.PI * vStart.distanceTo(vEnd))
                    / Math.sqrt((Globals.MAP_WIDTH * Globals.MAP_WIDTH) + (Globals.MAP_HEIGHT * Globals.MAP_HEIGHT));
        } else {//sphere projection
            fArcRadius = radius;
            this.zTranslation = zTranslation;
            float[] coords = Globals.point2Dto3D((float) fromLAT, (float) fromLONG, null);
            vStart = new VectorO(coords);
            vStart.setZ(vStart.getZprojection() - zTranslation);
            coords = Globals.point2Dto3D((float) toLAT, (float) toLONG, coords);
            vEnd = new VectorO(coords);
            vEnd.setZ(vEnd.getZprojection() - zTranslation);
            vCrossProduct = vStart.CrossProduct(vEnd);
            //vCrossProduct.Normalize();
            /**
             * the angle between the 2 vectors is acos(vector1.vector2/|vector1|/|vector2|)
             * but, because the vectors are normalized, their magnitudes are unitar
             */
            angleVectors = Math.acos(vStart.DotProduct(vEnd) / vStart.getRadius() / vEnd.getRadius());
        }
        ;
        //number of points to be drawn to create the arc
        nrPoints = (angleVectors < ((degreeIncrement * Math.PI) / 180)) ? 5 : (int) (5 + ((angleVectors
                / degreeIncrement / Math.PI) * 180));
        this.degreeIncrement = (float) (((angleVectors / nrPoints) * 180) / Math.PI);
        this.bowParalel = fractionRadius;
    }

    /*
    	void drawPointsArc( GL gl)
    	{
    		if ( vCrossProduct == null )
    			return;
    		gl.glPushMatrix();
    		for( int i=0; i<=nrPoints; i++) {
    			gl.glRotatef( i*degreeIncrement, vCrossProduct.getX(), vCrossProduct.getY(), vCrossProduct.getZ());
    			gl.glBegin(GL.GL_POINTS);
    				gl.glVertex3f( vStart.getX(), vStart.getY(), vStart.getZ()+(float)zTranslation);
    			gl.glEnd();
    			gl.glRotatef( -i*degreeIncrement, (float)vCrossProduct.getXprojection(), (float)vCrossProduct.getYprojection(), (float)vCrossProduct.getZprojection());
    		}
    		gl.glPopMatrix();
    	}
     */
    public void drawArc(GL2 gl) {
        if (vCrossProduct == null) {
            return;
        }
        if (points != null) {
            points.clear();
        }
        VectorO vPoint;
        double factor;
        factor = ((angleVectors / Math.PI) * (fArcRadius + zTranslation)) / nrPoints / nrPoints;
        double scalar;
        if (bPlaneProjection) {
            vPoint = new VectorO();
            VectorO vPointAux = new VectorO();
            VectorO vDiff = VectorO.SubstractVector(vEnd, vStart);
            gl.glBegin(GL.GL_LINE_STRIP);
            for (int i = 0; i <= nrPoints; i++) {
                scalar = i * (nrPoints - i) * factor;
                vPoint.duplicate(vDiff);
                vPoint.MultiplyScalar(i / (double) nrPoints);
                vPoint.AddVector(vStart);
                vPointAux.duplicate(vCrossProduct);
                vPointAux.MultiplyScalar(bowParalel * scalar * 4);
                vPoint.AddVector(vPointAux);

                gl.glVertex3f(vPoint.getX(), vPoint.getY(), vPoint.getZ());
                if (points != null) {
                    points.add(new VectorO(vPoint));//add point to list of points that make the arc
                }

            }
            ;
            gl.glEnd();
        } else {
            vPoint = new VectorO(vStart);
            gl.glBegin(GL.GL_LINE_STRIP);
            for (int i = 0; i <= nrPoints; i++) {
                scalar = 1 + (bowParalel * i * (nrPoints - i) * factor);
                vPoint.MultiplyScalar(scalar);
                gl.glVertex3f(vPoint.getX(), vPoint.getY(), vPoint.getZ() + zTranslation);
                if (points != null) {
                    points.add(new VectorO(vPoint));//add point to list of points that make the arc
                }
                vPoint.MultiplyScalar(1 / scalar);

                vPoint.Rotate(vCrossProduct, degreeIncrement);
            }
            gl.glEnd();
        }
        ;
    }

    public void drawArcAxis(GL2 gl) {
        if (vCrossProduct == null) {
            return;
        }
        gl.glBegin(GL.GL_LINES);
        gl.glVertex3f(0.0f, 0.0f, 0.0f);
        gl.glVertex3f((float) vCrossProduct.getXprojection(), (float) vCrossProduct.getYprojection(),
                (float) vCrossProduct.getZprojection() + zTranslation);
        gl.glEnd();
    }

    public void setZtranslation(float z) {
        zTranslation = z;
    }

    public float getZtranslation() {
        return zTranslation;
    }

    public ArrayList getPoints() {
        return points;
    }

    /**
     * if user wants list of points for this arc, he should provide an empty arraylist as parameter
     * that the @link drawArc method will fill with points from start to end<br>
     * if null, there will be no available list of points
     * @param points preferablly non null array list, but empty
     */
    public void setPoints(ArrayList points) {
        this.points = points;
    }
}
