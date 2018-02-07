package lia.Monitor.JiniClient.CommonGUI.Jogl.util;

import javax.media.opengl.GL2;

/*
 * Created on 04.05.2004 01:15:58
 * Filename: MyGLU.java
 *
 */
/**
 * @author Luc
 *
 * MyGLU
 * mesa implementation of glu functions ported to java
 */
public class MyGLU {
    public void gluLookAt(GL2 gl, float eyex, float eyey, float eyez, float centerx, float centery, float centerz,
            float upx, float upy, float upz) {
        float[] m = new float[16];
        float[] x = new float[3], y = new float[3], z = new float[3];
        float mag;

        /* Make rotation matrix */

        /* Z vector */
        z[0] = eyex - centerx;
        z[1] = eyey - centery;
        z[2] = eyez - centerz;
        mag = (float) Math.sqrt((z[0] * z[0]) + (z[1] * z[1]) + (z[2] * z[2]));
        if (mag > 0) { /* mpichler, 19950515 */
            z[0] /= mag;
            z[1] /= mag;
            z[2] /= mag;
        }

        /* Y vector */
        y[0] = upx;
        y[1] = upy;
        y[2] = upz;

        /* X vector = Y cross Z */
        x[0] = (y[1] * z[2]) - (y[2] * z[1]);
        x[1] = (-y[0] * z[2]) + (y[2] * z[0]);
        x[2] = (y[0] * z[1]) - (y[1] * z[0]);

        /* Recompute Y = Z cross X */
        y[0] = (z[1] * x[2]) - (z[2] * x[1]);
        y[1] = (-z[0] * x[2]) + (z[2] * x[0]);
        y[2] = (z[0] * x[1]) - (z[1] * x[0]);

        /* mpichler, 19950515 */
        /* cross product gives area of parallelogram, which is < 1.0 for
         * non-perpendicular unit-length vectors; so normalize x, y here
         */

        mag = (float) Math.sqrt((x[0] * x[0]) + (x[1] * x[1]) + (x[2] * x[2]));
        if (mag > 0) {
            x[0] /= mag;
            x[1] /= mag;
            x[2] /= mag;
        }

        mag = (float) Math.sqrt((y[0] * y[0]) + (y[1] * y[1]) + (y[2] * y[2]));
        if (mag > 0) {
            y[0] /= mag;
            y[1] /= mag;
            y[2] /= mag;
        }

        setM(0, 0, m, x[0]);
        setM(0, 1, m, x[1]);
        setM(0, 2, m, x[2]);
        setM(0, 3, m, 0.0f);
        setM(1, 0, m, y[0]);
        setM(1, 1, m, y[1]);
        setM(1, 2, m, y[2]);
        setM(1, 3, m, 0.0f);
        setM(2, 0, m, z[0]);
        setM(2, 1, m, z[1]);
        setM(2, 2, m, z[2]);
        setM(2, 3, m, 0.0f);
        setM(3, 0, m, 0.0f);
        setM(3, 1, m, 0.0f);
        setM(3, 2, m, 0.0f);
        setM(3, 3, m, 1.0f);
        gl.glMultMatrixf(m, 0);

        /* Translate Eye to Origin */
        gl.glTranslatef(-eyex, -eyey, -eyez);

    }

    private void setM(int row, int col, float[] m, float val) {
        m[(col * 4) + row] = val;
    }
}
