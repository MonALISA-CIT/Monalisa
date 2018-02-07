/*
 * Created on Mar 30, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package lia.Monitor.JiniClient.CommonGUI.Jogl;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;

/**
 * @author mluc
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class JoGLDirectedArc3D extends JoGLArc3D {
    /**
     * bowPerpendicular represents the deviation of the arc from its natural path in crossProduct direction 
     */
    protected double bowPerpendicular = 0.2;

    /**
     * vectors to be able to construct arrow for a directed link
     */
    public VectorO vPointFrom = new VectorO(), vPointTo = new VectorO();

    public JoGLDirectedArc3D(double fromLAT, double fromLONG, double toLAT, double toLONG, double radius,
            float degreeIncrement, double bowPar, float zTranslation, double bowPer) {
        super(fromLAT, fromLONG, toLAT, toLONG, radius, degreeIncrement, bowPar, zTranslation);
        bowPerpendicular = bowPer;
    }

    public void drawArc(GL2 gl) {
        if ((vCrossProduct == null) || (nrPoints == 0)) {
            return;
        }

        if (points != null) {
            points.clear();
        }

        VectorO vPoint, vPointAux;
        VectorO vPar, vPer;//vParalel and vPerpendicular
        vPer = new VectorO(vCrossProduct);
        vPer.Normalize();
        double factor;
        factor = ((angleVectors / Math.PI) * (fArcRadius + zTranslation)) / nrPoints / nrPoints;
        double scalar;
        vPoint = new VectorO();
        vPointAux = new VectorO();
        if (bPlaneProjection) {
            VectorO vDiff = VectorO.SubstractVector(vEnd, vStart);
            vPar = vDiff.CrossProduct(vCrossProduct);
            vPar.Normalize();

            gl.glBegin(GL.GL_LINE_STRIP);
            for (int i = 0; i <= nrPoints; i++) {
                scalar = i * (nrPoints - i) * factor;
                vPoint.duplicate(vDiff);
                vPoint.MultiplyScalar(i / (double) nrPoints);
                vPoint.AddVector(vStart);
                vPointAux.duplicate(vPar);
                vPointAux.MultiplyScalar(bowPerpendicular * scalar);
                vPoint.AddVector(vPointAux);
                vPointAux.duplicate(vPer);
                vPointAux.MultiplyScalar(0.02 + (bowParalel * scalar * 2));
                vPointAux.MultiplyScalar(scalar);
                vPoint.AddVector(vPointAux);

                gl.glVertex3f(vPoint.getX(), vPoint.getY(), vPoint.getZ());
                //System.out.println("point "+i+": "+vPoint);
                if (points != null) {
                    points.add(new VectorO(vPoint));
                }

                //get small arrow position
                if (i == (nrPoints / 3)) {
                    vPointFrom.duplicate(vPoint);
                } else if (i == ((nrPoints / 3) + 1)) {
                    vPointTo.duplicate(vPoint);
                }
                ;

            }
            ;
            gl.glEnd();

        } else {//sphere projection
            VectorO vRot = new VectorO(vStart);

            gl.glBegin(GL.GL_LINE_STRIP);
            for (int i = 0; i <= nrPoints; i++) {
                scalar = i * (nrPoints - i) * factor;
                vPoint.duplicate(vRot);
                vRot.Rotate(vCrossProduct, degreeIncrement);
                vPoint.MultiplyScalar(1 + (bowParalel * scalar));
                vPointAux.duplicate(vPer);
                vPointAux.MultiplyScalar(bowPerpendicular * scalar);
                vPoint.AddVector(vPointAux);

                vPoint.setZ(vPoint.getZprojection() + zTranslation);

                gl.glVertex3f(vPoint.getX(), vPoint.getY(), vPoint.getZ());
                if (points != null) {
                    points.add(new VectorO(vPoint));//add point to list of points that make the arc
                }

                //get small arrow position
                if (i == (nrPoints / 3)) {
                    vPointFrom.duplicate(vPoint);
                } else if (i == ((nrPoints / 3) + 1)) {
                    vPointTo.duplicate(vPoint);
                }
                ;
            }
            ;
            gl.glEnd();
        }

    }
}
