package lia.Monitor.JiniClient.CommonGUI.Mmap;

public final class FMath
{
    public static final double PI = 3.1415926535897931D;
    public static final double PI_OVER_TWO = 1.5707963267948966D;
    public static final double TWO_PI = 6.2831853071795862D;
    private static float fAsin[];
    private static double fAcos[];
    private static float fSin[];
    private static float fCos[];
    private static double fSqrt[];
    private static double asinScale;
    private static double acosScale;
    private static double sinScale;
    private static double cosScale;
    private static double sqrtScale;
    private static int maxTable;


    public static final void setResolution(int resolution)
    {
        double temp = 0.0D;
        maxTable = resolution;
        fAsin = null;
        fAcos = null;
        fSin = null;
        fCos = null;
        fSqrt = null;
        fAsin = new float[resolution + 1];
        fAcos = new double[resolution + 1];
        fSin = new float[resolution + 1];
        fCos = new float[resolution + 1];
        fSqrt = new double[resolution + 1];
        temp = 2D / (double)maxTable;
        for(int i = 0; i < maxTable + 1; i++)
        {
            fAsin[i] = (float)Math.asin((double)i * temp - 1.0D);
            fAcos[i] = Math.acos((double)i * temp - 1.0D);
        }

        asinScale = (double)maxTable / 2D;
        acosScale = asinScale;
        temp = 3.1415926535897931D / (double)maxTable;
        for(int i = 0; i < maxTable + 1; i++)
        {
            fSin[i] = (float)Math.sin((double)i * temp - 1.5707963267948966D);
            fCos[i] = (float)Math.cos((double)i * temp);
        }

        sinScale = (double)maxTable / 3.1415926535897931D;
        cosScale = sinScale;
        temp = 1.0D / (double)maxTable;
        for(int i = 0; i < maxTable + 1; i++)
            fSqrt[i] = Math.sqrt((double)i * temp);

        sqrtScale = maxTable;
    }

    public static final float ArcSin(double d)
    {
        return fAsin[(int)(0.5D + (d + 1.0D) * asinScale)];
    }

    public static final double ArcCos(double d)
    {
        int temp = (int)(0.5D + (d + 1.0D) * acosScale);
        if(temp < 0)
            temp = 0;
        else
        if(temp > maxTable)
            temp = maxTable;
        return fAcos[temp];
    }

    public static final float Sin(double ang)
    {
        return fSin[(int)(0.5D + (ang + 1.5707963267948966D) * sinScale)];
    }

    public static final float Cos(double ang)
    {
        if(ang < 0.0D)
            ang = -ang;
        if(ang > 3.1415926535897931D)
            ang = 6.2831853071795862D - ang;
        return fCos[(int)(0.5D + ang * cosScale)];
    }

    public static final double SquareRoot(double d)
    {
        return fSqrt[(int)(0.5D + d * sqrtScale)];
    }

    public FMath()
    {
    }


    static 
    {
        setResolution(1000);
    }
}
