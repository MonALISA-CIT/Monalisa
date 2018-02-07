package lia.Monitor.JiniClient.CommonGUI.Jogl;

/*
 * Created on Mar 30, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */

/**
 * @author mluc
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class VectorO 
{
	/**
	 * chartezian coordinates system
	 */
	private double x, y, z;//vector's projections on carthezian system's axes xyz
	/**
	 * spherical coordinates system
	 * R - radius
	 * phi - angle between the vector and the z axis
	 * theta - angle between the projection of the vector on (xy) plane and the x axis
	 * the angles are expressed in radians
	 */
	private double R, theta, phi;
	public VectorO()
	{
		x = y = z = 0;
		R = 0;
		theta = phi = 0;
	}
	public static VectorO constructVectorO( double LAT, double LONG, double radius)
	{
		double x, y, z, r;
		y = radius*Math.sin(LAT*Math.PI/180);
		r = radius*Math.cos(LAT*Math.PI/180.0f);
		z = r * Math.cos(LONG*Math.PI/180.0f);
		x = r * Math.sin(LONG*Math.PI/180.0f);
		return new VectorO( x, y, z);
	}
	public VectorO( double x, double y, double z)
	{
		this.x = x;
		this.y = y;
		this.z = z;
		computeSpherical();
	}
	
	public VectorO( double[] coords)
	{
		this.x = coords[0];
		this.y = coords[1];
		this.z = coords[2];
		computeSpherical();
	}
	
	public VectorO( float[] coords)
	{
		this.x = coords[0];
		this.y = coords[1];
		this.z = coords[2];
		computeSpherical();
	}
	
	private void computeSpherical()
	{
		R = Math.sqrt( x*x + y*y + z*z );
		if ( R != 0 ) {
			phi = Math.acos( z/R );
//			double sin_phi = Math.sqrt( 1 - z*z/R/R);//Math.sin(phi);
//			if ( sin_phi != 0 ) {
			//changed to below
			if ( x/R>=0.00001 || x/R<=-0.00001 ) {
//			if ( x!=0 || y!=0 ) {
			    //double aux = x/R/sin_phi;
			    //change to below to be sure that aux is in [-1,1]
			    double aux = 0;
			    //if ( x!=0 )
			        aux = x/Math.sqrt(x*x+y*y);
				theta = Math.acos( aux );
				if ( y < 0 )
				    if ( theta > 0.00001 )
				        theta = 2*Math.PI - theta;
			} else
				theta = 0;//vector is on z axis, so no theta
		} else {//no vector, so no theta and phi
			phi = 0;
			theta = 0;
		};
	}
	
	public VectorO( VectorO v)
	{
		this(v.getXprojection(), v.getYprojection(), v.getZprojection());
	}
	
	public static VectorO fromSpherical( double R, double p, double t)
	{
		VectorO v= new VectorO();
		v.R = R;
		v.phi = p;
		v.theta = t;
		v.z = R*Math.cos(v.phi);
		double prR;//projection of radius on xy plane
		prR = R*Math.sin(v.phi);
		v.x = prR*Math.cos(v.theta);
		v.y = prR*Math.sin(v.theta);
		return v;
	}

	/**
	 * normalizes vector to unitar magnitude
	 */
	public void Normalize()
	{
		/**
		 * compute vector's magnitude === modul
		 * and normalize projections to it
		 */
		double mag;
		mag = Math.sqrt(x*x+y*y+z*z);
		if ( mag > 0 ) {
			x = x/mag;
			y = y/mag;
			z = z/mag;
		};
		/**
		 * results that x^2+y^2+z^2 = 1
		 */
		computeSpherical();
	}
	
	/**
	 * computes vectorial cross product between this vector ( vector a ) and b vector
	 * to create a new vector ( result )
	 * @param b - the second vector
	 * @return cross product beween the two vectors
	 */
	public VectorO CrossProduct( VectorO b)
	{
		/**
		 * check to see if vectors are on same line, and if so, return 0
		 */
		VectorO result;
//		if ( x==0 && y==0 && z==0 || b.x==0 && b.y==0 && b.z==0 )
//			result = new VectorO();
//		else if ( Math.abs(x/R)==Math.abs(b.x/b.R) && Math.abs(y/R)==Math.abs(b.y/b.R) && Math.abs(z/R)==Math.abs(b.z/b.R) )
//				result = new VectorO();
//		else
		result = new VectorO( y*b.z - z*b.y, z*b.x - x*b.z, x*b.y - y*b.x);
		return result;
	}
	
	/**
	 * computes vectorial dot product between this vector and b
	 * this.b = |this|.|b|cos(angle), where angle is the angle between the two vectors,
	 * expressed in radians
	 * @param b the second vector
	 * @return scalar value of the product
	 */
	public double DotProduct( VectorO b)
	{
		double result;
		result = x*b.x + y*b.y + z*b.z;
		return result;
	}
	
	/**
	 * multiplies a vector with a scalar
	 * increasing the magnitude
	 * @param value
	 * @return this vector
	 */
	public void MultiplyScalar( double value)
	{
		x*=value;
		y*=value; 
		z*=value;
		computeSpherical();
	}
	
	/**
	 * Substracts from this vector the one received as parameter
	 * @param b The second vector
	 * @return this vector contains the sum
	 */
	public void SubstractVector( VectorO b)
	{
		x -= b.x;
		y -= b.y;
		z -= b.z;
		computeSpherical();
	}
	
	/**
	 * substract b from a
	 * @param a vector
	 * @param b vector
	 * @return the result
	 */
	public static VectorO SubstractVector( VectorO a, VectorO b)
	{
		VectorO v = new VectorO(a);
		v.SubstractVector(b);
		return v;
	}
	
	/**
	 * adds this vector with the one received as parameter
	 * @param b The second vector
	 * @return this vector contains the sum
	 */
	public void AddVector( VectorO b)
	{
		x += b.x;
		y += b.y;
		z += b.z;
		computeSpherical();
	}
	
	public static VectorO AddVector( VectorO a, VectorO b)
	{
		VectorO result = new VectorO(a);
		result.AddVector(b);
		return result;
	}
	
	public void RotateDegree( VectorO vAxis, double angle)
	{
	    Rotate( vAxis, angle);
	}
	
	public void RotateRadian( VectorO vAxis, double angle)
	{
	    Rotate( vAxis, angle*180/Math.PI);
	}

	/**
	 * rotates this vector around the axis determined by vAxis,
	 * where angle is expressed in degrees 
	 * @param vAxis
	 * @return modifies the current vector
	 */
	public void Rotate( VectorO vAxis, double angle)
	{
		if ( y*vAxis.z - z*vAxis.y == 0 && z*vAxis.x - x*vAxis.z==0 && x*vAxis.y - y*vAxis.x == 0 )
			return;
		VectorO vPrAxis, vPrPAxis, vNormal;//projection on axis and projection perpendicular on axis, and normal on plane fomed by the vector and axis
		vPrAxis = new VectorO(vAxis);
		vPrAxis.Normalize();
		/**
		 * compute normal of the plane formed by this vector and rotation axis
		 */
		vNormal = CrossProduct( vAxis);
		vNormal.Normalize();	//normalize it
		/**
		 * compute perpendicular axis on axis, in plane formed by vector and rotation axis
		 */
		vPrPAxis = vPrAxis.CrossProduct( vNormal);//normalized as product of two normalized vectors
		vPrPAxis.Normalize();//to be sure
		/**
		 * compute angle between vector and axis
		 */
		double cosVA;
		cosVA = DotProduct(vPrAxis)/R;
		/**
		 * compute projection on axis
		 * by multiplying normalized axis with value of cosinus
		 */
		vPrAxis.MultiplyScalar(cosVA*R);
		/**
		 * compute projection on perpendicular axis on axis
		 * as value of normalized perpendicular axis multiplied by magnitude of vetor and sinus of angle
		 * between axis and vector
		 */
		vPrPAxis.MultiplyScalar( R*Math.sin(Math.acos(cosVA)));
		/**
		 * compute rotation of projection on perpendicular axis of vector
		 * the 2 components:
		 */
		vNormal.MultiplyScalar( -R*Math.sin(Math.acos(cosVA))*Math.sin(angle*Math.PI/180));
		vPrPAxis.MultiplyScalar( Math.cos(angle*Math.PI/180));
		/**
		 * add all components
		 */
		duplicate(vPrAxis);
		AddVector(vNormal);
		AddVector(vPrPAxis);
	}
	
	public void duplicate(VectorO v)
	{
		x = v.getXprojection();
		y = v.getYprojection();
		z = v.getZprojection();
		computeSpherical();
	}
	
	/**
	 * @return Returns the phi angle between the z axis and vector in radians, expressed in radians.
	 */
	public double getPhi() {
		return phi;
	}
	/**
	 * @return Returns the radius, magnitude of vector.
	 */
	public double getRadius() {
		return R;
	}
	/**
	 * @return Returns the theta angle between the x axis and projection of vector on (xy) plane in radians,
	 * expressed in radians.
	 */
	public double getTheta() {
		return theta;
	}
	/**
	 * @return Returns the projection value of the vector on x axis.
	 */
	public double getXprojection() {
		return x;
	}
	/**
	 * @return Returns the projection value of the vector on y axis.
	 */
	public double getYprojection() {
		return y;
	}
	/**
	 * @return Returns the projection value of the vector on z axis.
	 */
	public double getZprojection() {
		return z;
	}
	
	public String toString()
	{
		return "vector (x,y,z) -> ("+x+","+y+","+z+")\n" +
		"       (R,phi,theta) -> ("+R+","+phi+","+theta+")";
//		NumberFormat nf = NumberFormat.getInstance();
//		return "vector (x,y,z) -> ("+nf.format(x)+","+nf.format(y)+","+nf.format(z)+")\n" +
//				"       (R,phi,theta) -> ("+nf.format(R)+","+nf.format(phi)+","+nf.format(theta)+")";
	}
    
    /**
     * reinitializes the vector
     * @param x
     * @param y
     * @param z
     */
    public void setXYZ( float x, float y, float z) 
    {
        this.x = x;
        this.y = y;
        this.z = z;
        computeSpherical();
    }
    
	/**
	 * @return Returns the x.
	 */
	public float getX() {
		return (float)x;
	}
	/**
	 * @param x The x to set.
	 */
	public void setX(double x) {
		this.x = x;
		computeSpherical();
	}
	/**
	 * @return Returns the y.
	 */
	public float getY() {
		return (float)y;
	}
	/**
	 * @param y The y to set.
	 */
	public void setY(double y) {
		this.y = y;
		computeSpherical();
	}
	/**
	 * @return Returns the z.
	 */
	public float getZ() {
		return (float)z;
	}
	/**
	 * @param z The z to set.
	 */
	public void setZ(double z) {
		this.z = z;
		computeSpherical();
	}
	
	public double distanceTo( VectorO v)
	{
		return Math.sqrt((x-v.x)*(x-v.x) + (y-v.y)*(y-v.y) + (z-v.z)*(z-v.z));
	}
	
	public boolean isNull()
	{
	    System.out.println("vector is "+(R<=0.0001?"":"not")+" null. Radius is "+R);
	    return (R<=0.0001?true:false);
	}
	
	/**
	 * equals 2 vectors
	 *
	 * Nov 19, 2004 - 1:17:03 PM
	 */
	public boolean equals( Object o)
	{
        VectorO v;
        if ( !(o instanceof VectorO) )
            return false;
        v = (VectorO)o;
        if ( v==null )
            return false;
	    if ( Math.abs(this.R-v.R)<=0.00001 && Math.abs(this.phi-v.phi)<=0.00001 && Math.abs(this.theta-v.theta)<=0.00001 )
	        return true;
	    return false;
	}

    /**
     * 
     * 
     * Aug 12, 2005 - 2:32:45 PM
     */
    public int hashCode() {
        int rez;
        rez = ((int)theta)&0xff + (((int)phi)&0xff)<<8 + (((int)R)&0xffff)<<16;
        return rez;
    }
}
