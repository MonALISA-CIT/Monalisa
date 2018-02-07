package lia.web.servlets.map2d;

public final class Sun
{

    private static final double rightAscension[] = new double[367];
    private static final double declination[] = new double[367];
    private static final int tzOffset;
    
    public static final double getRightAscension(int dayNumber)
    {
        return rightAscension[dayNumber];
    }

    public static final double getDeclination(int dayNumber)
    {
        return declination[dayNumber];
    }

    public static final double getGST(int dayNumber, double LMT)
    {
        double GMT = 24D * LMT + (double)tzOffset;
        double t0 = (double)dayNumber * 0.065709799999999999D - 17.411472D;
        double t1 = GMT * 1.0027379999999999D;
        double GST = t0 + t1;
        if(GST > 24D)
            GST -= 24D;
        else
        if(GST < 0.0D)
            GST += 24D;
        GST /= 24D;
        return GST;
    }

    private static final void solarPosition()
    {
        for(int i = 0; i < 367; i++)
        {
            double D = i;
            double N = (6.2831853071795862D * D) / 365.24220000000003D;
            double M = (N + 4.8665633379913098D) - 4.9322376866427815D;
            if(M < 0.0D)
                M += 6.2831853071795862D;
            double Ec = 0.033436D * Math.sin(M);
            double lambda = N + Ec + 4.8665633379913098D;
            if(lambda > 6.2831853071795862D)
                lambda -= 6.2831853071795862D;
            rightAscension[i] = lambda;
            declination[i] = 0.0D;
        }

        for(int i = 0; i < 367; i++)
        {
            double dec = Math.asin(Math.sin(declination[i]) * Math.cos(0.40913805867057845D) + Math.cos(declination[i]) * Math.sin(0.40913805867057845D) * Math.sin(rightAscension[i]));
            double y = Math.sin(rightAscension[i]) * Math.cos(0.40913805867057845D) - Math.tan(declination[i]) * Math.sin(0.40913805867057845D);
            double x = Math.cos(rightAscension[i]);
            double RA = Math.atan2(y, x);
            rightAscension[i] = RA;
            declination[i] = dec;
        }

    }

    public Sun()
    {
    }


    static 
    {
//        tzOffset = (int)((double)localDate.getTimezoneOffset() / 60D);
		//tzOffset = (int)((double)(Calendar.getInstance().get(Calendar.ZONE_OFFSET) /*+
		 //Calendar.getInstance().get(Calendar.DST_OFFSET)*/ ) / (1000D * 60D * 60D));
        	tzOffset = 0;
        solarPosition();
    }
}
