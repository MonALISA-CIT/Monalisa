package lia.Monitor.JiniClient.CommonGUI.GlobePan;

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.swing.JSlider;
import javax.vecmath.Vector3d;

@SuppressWarnings("restriction")
public class Zoomer extends Manipulator {

  public static double MINIMUM_DISTANCE = 1.1;

  public static double DEFAULT_ZOOM_SPEED = 0.15;
  protected double zoomSpeed = DEFAULT_ZOOM_SPEED;

  TransformGroup transformGroup;

  int button = 0;
  int buttonMask = 0;

  int y_last;
  
  //save slider position as modified by the zoomer to be able to reach the same position when the case
  int posSlider;
  public void resetPosSlider()
  {
  		posSlider=scaleSlider.getValue();
  }
  
  private JSlider scaleSlider;

  public Zoomer(TransformGroup transformGroup) {
    this.transformGroup = transformGroup;
  }

  public void setSensitivity(double sensitivity) {
    zoomSpeed = sensitivity*DEFAULT_ZOOM_SPEED;
  }

  public void setButton(int b) {
    if(b > 0)
      button = b;
    else if(button == -b)
      button = 0;

    if(button == 1)
      buttonMask = MouseEvent.BUTTON1_MASK;
    else if(button == 2)
      buttonMask = MouseEvent.BUTTON2_MASK;
    else if(button == 3)
      buttonMask = MouseEvent.BUTTON3_MASK;
    else
      buttonMask = 0;
  }

  @Override
public void mouseDragged(MouseEvent e) {
    if((e.getModifiers() & buttonMask) == 0)
      return;

    int dy = e.getY() - y_last;

    Transform3D transform = new Transform3D();
    transformGroup.getTransform(transform);

    Vector3d translation = new Vector3d();
    transform.get(translation);

    // This will prevent the user from zooming _through_ the globe.
    double dist = dy*zoomSpeed/4;
    if(dist > 0.5)
      dist = 0.5;

    translation.add(new Vector3d(0, 0, dist));

    // And this will prevent the user from zooming _into_ the globe.
    if(translation.length() > MINIMUM_DISTANCE) {
      transform.setTranslation(translation);
      transformGroup.setTransform(transform);
  	  //scaleSlider.setValue((int)(10*translation.length())-80);
      posSlider -= (int)(dist*10);
      if ( posSlider > 100)
      	scaleSlider.setValue(100);
      else if ( posSlider<-100 )
      	scaleSlider.setValue(-100);
      else
      	scaleSlider.setValue(posSlider);
    }

    y_last += dy;
  }

  @Override
public void mousePressed(MouseEvent e) {
    if(e.getButton() == button)
      y_last = e.getY();
  }

  @Override
public void mouseWheelMoved(MouseWheelEvent e) {
    int dz = e.getWheelRotation();

    Transform3D transform = new Transform3D();
    transformGroup.getTransform(transform);

    Vector3d translation = new Vector3d();
    transform.get(translation);

    // This will prevent the user from zooming _through_ the globe.
    double dist = -dz*zoomSpeed;
    if(dist > 0.5)
      dist = 0.5;

	translation.add(new Vector3d(0, 0, dist));

    // And this will prevent the user from zooming _into_ the globe.
    if(translation.length() > MINIMUM_DISTANCE) {
      transform.setTranslation(translation);
      transformGroup.setTransform(transform);
      posSlider -= (int)(dist*10);
      if ( posSlider > 100)
      	scaleSlider.setValue(100);
      else if ( posSlider<-100 )
      	scaleSlider.setValue(-100);
      else
      	scaleSlider.setValue(posSlider);
    }
  }

/**
 * @param scaleSlider The scaleSlider to set.
 */
public void setScaleSlider(JSlider scaleSlider) {
	this.scaleSlider = scaleSlider;
}
}
