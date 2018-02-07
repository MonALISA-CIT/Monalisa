package lia.Monitor.JiniClient.CommonGUI.GlobePan;

import javax.media.j3d.Transform3D;
import javax.vecmath.Matrix3d;
import javax.vecmath.Vector3d;

// TODO
// * Create more intelligent constructors (for string representations, etc.).

@SuppressWarnings("restriction")
public class WorldCoordinates {

  public double latitude;
  public double longitude;
  public double radius;

  /** Default constructor.
    * <tt>latitude</tt> is set to 0 degrees N.
    * <tt>longitude</tt> is set to 0 degrees E.
    * <tt>radius</tt> is set to 1.0. */
  public WorldCoordinates() {
    latitude = 0.0;
    longitude = 0.0;
    radius = 1.0;
  }

  /** Constructor.
    * <tt>radius</tt> is set to 1.0
    * @param latitude in degrees, positive for N, negative for S
    * @param longitude in degrees, positive for E, negative for W */
  public WorldCoordinates(double latitude, double longitude) {
    this.latitude = latitude;
    this.longitude = longitude;
    radius = 1.0;
  }

  public WorldCoordinates(double latitude, double longitude, double radius) {
    this.latitude = latitude;
    this.longitude = longitude;
    this.radius = radius;
  }

  public WorldCoordinates(int latDegrees, int latMinutes, double latSeconds,
                          int longDegrees, int longMinutes, double longSeconds)
  {
    latitude = latDegrees + latMinutes/60.0 + latSeconds/3600.0;
    longitude = longDegrees + longMinutes/60.0 + longSeconds/3600.0;
  }

  /** Constructor.
    * <tt>radius</tt> is set to 1.0.
    * @param latitude string representation of the latitude, in degrees
    * @param longitude string representation of the longitude, in degrees */
  public WorldCoordinates(String latitude, String longitude) {
      
      //defualt some place in the ocean
      this.latitude  = -21.22D;
      this.longitude = -111.15D;

    try {
        this.latitude = Double.parseDouble(latitude);
        this.longitude = Double.parseDouble(longitude);
    } catch ( Throwable t  ){  }
    radius = 1.0;
  }

  public WorldCoordinates(Vector3d pos) {
    radius = pos.length();
    Vector3d unit = new Vector3d(pos);
    unit.scale(1/radius);
    latitude = Math.asin(unit.y)*180.0/Math.PI;
    unit.y = 0;
    unit.normalize();
    if(unit.x >= 0)
      longitude = Math.acos(unit.z)*180.0/Math.PI;
    else
      longitude = -Math.acos(unit.z)*180.0/Math.PI;
  }

  public final Vector3d toVector() {
    double phi = latitude*Math.PI/180.0;
    double theta = longitude*Math.PI/180.0;
    double cos_phi = Math.cos(phi);
    return new Vector3d(radius*cos_phi*Math.sin(theta), radius*Math.sin(phi), radius*cos_phi*Math.cos(theta));
  }

  public final Vector3d toUnitVector() {
    double phi = latitude*Math.PI/180.0;
    double theta = longitude*Math.PI/180.0;
    double cos_phi = Math.cos(phi);
    return new Vector3d(cos_phi*Math.sin(theta), Math.sin(phi), cos_phi*Math.cos(theta));
  }

  public final Transform3D toTransform() {
    double phi = latitude*Math.PI/180.0;
    double theta = longitude*Math.PI/180.0;
    double cos_phi = Math.cos(phi);
    double sin_phi = Math.sin(phi);
    double cos_theta = Math.cos(theta);
    double sin_theta = Math.sin(theta);

    Matrix3d rotation = new Matrix3d(
        cos_theta,  -sin_phi*sin_theta, cos_phi*sin_theta,
        0,          cos_phi,            sin_phi,
        -sin_theta, -sin_phi*cos_theta, cos_phi*cos_theta);
    Vector3d translation = new Vector3d(
        radius*cos_phi*sin_theta, radius*sin_phi, radius*cos_phi*cos_theta);
    return new Transform3D(rotation, translation, 1.0);
  }

  public final Transform3D toUnitTransform() {
    double phi = latitude*Math.PI/180.0;
    double theta = longitude*Math.PI/180.0;
    double cos_phi = Math.cos(phi);
    double sin_phi = Math.sin(phi);
    double cos_theta = Math.cos(theta);
    double sin_theta = Math.sin(theta);

    Matrix3d rotation = new Matrix3d(
        cos_theta,  -sin_phi*sin_theta, cos_phi*sin_theta,
        0,          cos_phi,            sin_phi,
        -sin_theta, -sin_phi*cos_theta, cos_phi*cos_theta);
    Vector3d translation = new Vector3d(
        cos_phi*sin_theta, sin_phi, cos_phi*cos_theta);
    return new Transform3D(rotation, translation, 1.0);
  }

  public static void main(String[] args) {
    System.out.println("TESTING WorldCoordinates");
    Vector3d origin = new Vector3d(0, 1, 1);
    System.out.println("origin = " + origin);
    WorldCoordinates wo = new WorldCoordinates(origin);
    System.out.println("origin latitude = " + wo.latitude + ", longitude = " + wo.longitude);
    System.out.println("transformed origin = " + wo.toVector());
  }

}
