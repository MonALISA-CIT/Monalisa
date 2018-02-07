package lia.Monitor.Plot;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Method;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TimeZone;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;

import lia.Monitor.JiniClient.Farms.OSGmap.Config.PortsPanel;

import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.DateTickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.ui.RectangleEdge;

/**
 * Panel that can be used to select a plot interval.
 */
public class IntervalSelector extends JPanel {
	
	TimeSlider sl;
	
	Font tickFont = new Font("arial", Font.PLAIN, 9);
	/** Default colour for the track */
	static Color trackColor = new Color(66, 224, 255, 100);
	static Color buttonColor = Color.white;
	static Color selectedColor = new Color(200, 100, 100, 100);
	static Color originalColor = new Color(200, 100, 100, 200);
	
	public IntervalSelector(long startDate, long endDate, long currentTime) {
		super();
		if (startDate < 0) startDate = 0;
		if (endDate < 0) endDate = 0;
		if (endDate <= startDate) endDate = startDate+1;
		setLayout(new BorderLayout());
		sl = new TimeSlider(startDate, endDate, currentTime);
		sl.setSelectedRange(startDate, endDate);
		add(sl, BorderLayout.NORTH);
	}
	
	public void setTimeZone(String timeZone) {
		
		timeZone = formatTimeZone(timeZone);
		if (timeZone == null) return;
		TimeZone tz = TimeZone.getTimeZone(timeZone);
		if (tz == null)
			return;
		createStandardTickUnits(tz);
		sl.setTimeZone(tz);
	}
	
	
	/** Add a DoubleSliderAdjustmentListener to listen for adjustment events
	 @param i the listener to add
	 */
	public void addAdjustmentListener(TimeSliderAdjustmentListener i) {
		sl.addAdjustmentListener(i);
	}
	
	/** Remove the given DoubleSliderAdjustmentListener from the list of
	 event listeners
	 @param i the listener to remove
	 */
	public void removeAdjustmentListener(TimeSliderAdjustmentListener i) {
		sl.removeAdjustmentListener(i);
	}
	
	public void setRange(long startDate, long endDate, long currentTime) {
		if (startDate < 0) startDate = 0;
		if (endDate < 0) endDate = 0;
		if (endDate <= startDate) endDate = startDate+1;
		sl.setOriginalRange(startDate, endDate, currentTime);
	}
	
	public long getMinRange() {
		return (long)sl.getSelectedMinimum();
	}
	
	public long getMaxRange() {
		return (long)sl.getSelectedMaximum();
	}
	
	public boolean isContinuous() {
		return sl.isContinuous();
	}
	
	public static void main(String args[]) {
		
		JFrame f = new JFrame();
		f.getContentPane().setLayout(new BorderLayout());
		final IntervalSelector s = new IntervalSelector(System.currentTimeMillis() - 2 * 60 * 60 * 1000, System.currentTimeMillis(), System.currentTimeMillis());
		f.getContentPane().add(s, BorderLayout.CENTER);
		f.setSize(400, 400);
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.setVisible(true);
//		new Thread(new Runnable() {
//			public void run() {
//				while (true) {
//					try {
//						Thread.sleep(1000);
//					} catch (Exception ex) { }
//					s.setRange(System.currentTimeMillis() - 2 * 60 * 60 * 1000, System.currentTimeMillis(), System.currentTimeMillis());
//				}
//			} }).start();
	}
	
	class TimeSlider extends JPanel implements ActionListener, TimeSliderAdjustmentListener {
		
		/** Size of the (square) buttons */
		private static final int buttonSize = 16;
		ArrowButton minButton,maxButton;
		JButton minusButton, plusButton, rangeButton;
		JButton plotButton, legendButton;
		boolean plotIsEnabled = false;
		/** Component for the track between the two buttons */
		Tracker track;
		Plot plot;
		/** Values representing absolute bounds of slider */
		double maxValue,minValue;
		/** Values representing current selected bounds */
		double currentMaxValue,currentMinValue;
		/** True if a drag is currently in progress */
		boolean trackingDrag = false;
		Color midToLightGray = mixColors(Color.lightGray,Color.gray);
		/** List of interested DoubleSliderAdjustmentListeners */
		java.util.List listeners;
		/** Last time a DoubleSliderAdjustmentListeners was notified (ms)*/
		long lastNotifyTime = 0;
		/** Duration in milliseconds between notifications */
		long notifyInterval = 0;
		
		long originalMinValue = 0, originalMaxValue = 0;
		
		boolean continuous = true;
		PlotTooltip tooltip;

		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss z"); 
		
		long currentTime;
		
		/** Construct a default DoubleSlider with minimum 0.0 and maximum 100.0,
		 oriented HORIZONTALly */
		public TimeSlider() {
			this(0,100, 100);
		}

		public void setTimeZone(TimeZone timeZone) {
			dateFormat.setTimeZone(timeZone);
		}
		
		public void setTimeZone(TickUnits units) {
			plot.setTickUnits(units);
		}
		
		/** Construct a DoubleSlider with given minimum, maximum and orientation
		 @param orientation HORIZONTAL or VERTICAL
		 @param minValue The minimum value
		 @param maxValue The maximum value
		 */
		public TimeSlider(long minValue, long maxValue, long currentTime) {
			this.minValue = this.currentMinValue = minValue;
			this.maxValue = this.currentMaxValue = maxValue;
			this.originalMinValue = minValue;
			this.originalMaxValue = maxValue;
			this.currentTime = currentTime;
			listeners = new LinkedList();
			tooltip = new PlotTooltip();
			EventsHandler eh = new EventsHandler();
			setLayout(eh);
			minButton = new ArrowButton(ArrowButton.RIGHT);
			minButton.setToolTipText("Drag to modify the start of the time interval");
			maxButton = new ArrowButton(ArrowButton.LEFT);
			maxButton.setToolTipText("Drag to modify the end of the time interval");
			track = new Tracker();
			track.setOpaque(false);
			track.setToolTipText("Drag to modify the time interval");
			plot = new Plot(minValue, maxValue);
			plot.setBorder(BorderFactory.createLineBorder(Color.white));
			plusButton = new JButton(getZoomOutIcon());
			plusButton.setToolTipText("Zoom out");
			plusButton.addMouseListener(new MouseAdapter() {
			    public void mouseEntered(MouseEvent e) {
					plusButton.setIcon(getZoomOutIconOver());
			    }
			    public void mouseExited(MouseEvent e) {
					plusButton.setIcon(getZoomOutIcon());
			    }
			});
			plusButton.setBorderPainted(false);
			plusButton.setBackground(buttonColor);
			minusButton = new JButton(getZoomInIcon());
			minusButton.setToolTipText("Zoom in");
			minusButton.addMouseListener(new MouseAdapter() {
			    public void mouseEntered(MouseEvent e) {
					minusButton.setIcon(getZoomInIconOver());
			    }
			    public void mouseExited(MouseEvent e) {
					minusButton.setIcon(getZoomInIcon());
			    }
			});
			minusButton.setBorderPainted(false);
			minusButton.setBackground(buttonColor);
			rangeButton = new JButton(getRangeIcon());
			rangeButton.setToolTipText("Autorange");
			rangeButton.addMouseListener(new MouseAdapter() {
			    public void mouseEntered(MouseEvent e) {
					rangeButton.setIcon(getRangeIconOver());
			    }
			    public void mouseExited(MouseEvent e) {
					rangeButton.setIcon(getRangeIcon());
			    }
			});
			rangeButton.setBorderPainted(false);
			rangeButton.setBackground(buttonColor);
			plotButton = new JButton(getPlotIconInactive());
			plotButton.setFont(tickFont);
			plotButton.setToolTipText("Press to select the new time interval");
			plotButton.setBorderPainted(false);
			plotButton.setBackground(buttonColor);
			plotIsEnabled = false;
			plotButton.addMouseListener(new MouseAdapter() {
			    public void mouseEntered(MouseEvent e) {
					if (plotIsEnabled) {
						plotButton.setIcon(getPlotIconOver());
					}
			    }
			    public void mouseExited(MouseEvent e) {
					if (plotIsEnabled) {
						plotButton.setIcon(getPlotIcon());
					}
			    }
			});
			legendButton = new JButton(getLegendIcon());
			legendButton.addMouseListener(new MouseAdapter() {
			    public void mouseEntered(MouseEvent e) {
					legendButton.setIcon(getLegendIconOver());
			    }
			    public void mouseExited(MouseEvent e) {
					legendButton.setIcon(getLegendIcon());
			    }
			});
			legendButton.setToolTipText("Press to see the legend dialog");
			legendButton.setBorderPainted(false);
			legendButton.setBackground(buttonColor);
			add(track);
			add(minButton);
			add(maxButton);
			add(plusButton);
			add(minusButton);
			add(rangeButton);
			add(plotButton);
			add(plot);
			add(legendButton);
			minButton.addMouseMotionListener(eh);
			minButton.addMouseListener(eh);
			maxButton.addMouseMotionListener(eh);
			maxButton.addMouseListener(eh);
			track.addMouseMotionListener(eh);
			track.addMouseListener(eh);
			plot.addMouseMotionListener(eh);
			plot.addMouseListener(eh);
			plusButton.addActionListener(this);
			minusButton.addActionListener(this);
			rangeButton.addActionListener(this);
			plotButton.addActionListener(this);
			legendButton.addActionListener(this);
			setBackground(Color.white);
			addAdjustmentListener(this);
			layoutMyButtons();
		}
		
		public void adjustmentValueChanged() {
			this.continuous = (currentTime == currentMaxValue);
		}
		
		public boolean isContinuous() {
			return continuous;
		}
		
		public void setOriginalRange(long startDate, long endDate, long currentTime) {
			
			synchronized (getTreeLock()) {
				this.originalMinValue = startDate;
				this.originalMaxValue = endDate;
				this.currentTime = currentTime;
				long min = (long)Math.min(minValue, originalMinValue);
				long max = (long)Math.max(maxValue, originalMaxValue);
				double range = max - min;
				double center = (max+ min) / 2.0;
				setAbsoluteMinimum(center - range / 2, true);
				center = center + range / 2;
				if (center > originalMaxValue) center = originalMaxValue;
				setAbsoluteMaximum(center, true);
				if (originalMinValue > minValue && originalMinValue < maxValue) {
					currentMinValue = originalMinValue;
					if (originalMaxValue > minValue && originalMaxValue < maxValue)
						currentMaxValue = originalMaxValue;
					else
						currentMaxValue = maxValue;
				} else if (originalMaxValue > minValue && originalMaxValue < maxValue) {
					currentMaxValue = originalMaxValue;
					currentMinValue = minValue;
				}
				if (currentMinValue < minValue) currentMinValue = minValue;
				if (currentMinValue > maxValue) currentMinValue = maxValue;
				if (currentMaxValue < minValue) currentMaxValue = minValue;
				if (currentMaxValue > maxValue) currentMaxValue = maxValue;
				plot.setRange((long)minValue, (long)maxValue);
				plot.repaint();
				layoutMyButtons();
			}
		}
		
		public void actionPerformed(ActionEvent e) {
			Object src = e.getSource();
			if (src.equals(plusButton)) {
				double range = maxValue - minValue;
				double center = (maxValue + minValue) / 2.0;
				range = range * 2.0;
				if (center - range / 2 < 0) return;
				setAbsoluteMinimum(Math.min(center - range / 2, currentMinValue), false);
				center = center + range / 2;
				if (center > currentTime) center = currentTime;
				setAbsoluteMaximum(Math.max(center, currentMaxValue), false);
				if (currentMinValue < minValue) currentMinValue = minValue;
				if (currentMinValue > maxValue) currentMinValue = maxValue;
				if (currentMaxValue < minValue) currentMaxValue = minValue;
				if (currentMaxValue > maxValue) currentMaxValue = maxValue;
				plot.setRange((long)minValue, (long)maxValue);
				plot.repaint();
				sanitiseLimits();
				layoutMyButtons();
				return;
			}
			if (src.equals(minusButton)) {
				double range = maxValue - minValue;
				if (range < 5000) { // 5 seconds, no point in continuing
					currentMinValue = minValue;
					currentMaxValue = maxValue;
					plot.setRange((long)minValue, (long)maxValue);
					plot.repaint();
					layoutMyButtons();
					return;
				}
				double center = (maxValue + minValue) / 2.0;
				range = range * 0.5;
				setAbsoluteMinimum(Math.min(center - range / 2, currentMinValue), false);
				center = center + range / 2;
				if (center > currentTime) center = currentTime;
				setAbsoluteMaximum(Math.max(center, currentMaxValue), false);
				if (Math.abs(minValue - currentMinValue) < 0.0001 && Math.abs(maxValue - currentMaxValue) < 0.0001) {
					range = currentMaxValue - currentMinValue;
					center = (currentMaxValue + currentMinValue) / 2.0;
					range = range * 0.5;
					setAbsoluteMinimum(center - range / 2, false);
					center = center + range / 2;
					if (center > currentTime) center = currentTime;
					setAbsoluteMaximum(center, false);
					currentMinValue = minValue;
					currentMaxValue = maxValue;
				}
				if (currentMinValue < minValue) currentMinValue = minValue;
				if (currentMinValue > maxValue) currentMinValue = maxValue;
				if (currentMaxValue < minValue) currentMaxValue = minValue;
				if (currentMaxValue > maxValue) currentMaxValue = maxValue;
				plot.setRange((long)minValue, (long)maxValue);
				plot.repaint();
				layoutMyButtons();
				return;
			}
			if (src.equals(rangeButton)) {
				double range = originalMaxValue - originalMinValue;
				double center = (originalMaxValue + originalMinValue) / 2.0;
				setAbsoluteMinimum(center - range / 2, false);
				center = center + range / 2;
				if (center > currentTime) center = currentTime;
				setAbsoluteMaximum(center, false);
				double diff = currentMaxValue - currentMinValue;
				if (currentMinValue < minValue) currentMinValue = minValue;
				if (currentMinValue > maxValue) currentMinValue = maxValue;
				currentMaxValue = currentMinValue + diff;
				if (currentMaxValue < minValue) currentMaxValue = minValue;
				if (currentMaxValue > maxValue) currentMaxValue = maxValue;
				plot.setRange((long)minValue, (long)maxValue);
				plot.repaint();
				sanitiseLimits();
				layoutMyButtons();
				return;
			}
			if (src.equals(plotButton)) {
				if (!plotIsEnabled) return;
				if (Math.abs(currentMaxValue - currentMinValue) < 5000) {
					double middle = (currentMaxValue + currentMinValue) / 2;
					double minx = middle - 2500;
					if (minx < 0) {
						minx = 0;
					}
					double maxx = minx + 5000;
					if (maxx > maxValue) {
						maxx = maxValue;
						minx = maxx - 5000;
						if (minx < 0) minx = 0;
					}
					currentMinValue = minx;
					currentMaxValue = maxx;
				}
				originalMinValue = (long)currentMinValue;
				originalMaxValue = (long)currentMaxValue;
				double range = originalMaxValue - originalMinValue;
				double center = (originalMaxValue + originalMinValue) / 2.0;
				range = range * 1.5;
				setAbsoluteMinimum(center - range / 2, true);
				center = center + range / 2;
				if (center > currentTime) center = currentTime;
				setAbsoluteMaximum(center, true);
				if (currentMinValue < minValue) currentMinValue = minValue;
				if (currentMinValue > maxValue) currentMinValue = maxValue;
				if (currentMaxValue < minValue) currentMaxValue = minValue;
				if (currentMaxValue > maxValue) currentMaxValue = maxValue;
				plot.setRange((long)minValue, (long)maxValue);
				plot.repaint();
				sanitiseLimits();
				notifyListeners();
				layoutMyButtons();
				return;
			}
			if (src.equals(legendButton)) {
				tooltip.showPopup(this, (int)(getLocationOnScreen().getX() + plot.getX() + plot.getWidth() / 2), (int)(getLocationOnScreen().getY()+plot.getY()));
			}
		}
		
		/** Check whether the user is currently dragging the slider.
		 @return true if the user is dragging the slider
		 */
		public boolean isTrackingDrag() {
			return trackingDrag;
		}
		
		/** Add a DoubleSliderAdjustmentListener to listen for adjustment events
		 @param i the listener to add
		 */
		public void addAdjustmentListener(TimeSliderAdjustmentListener i) {
			listeners.add(i);
		}
		
		/** Remove the given DoubleSliderAdjustmentListener from the list of
		 event listeners
		 @param i the listener to remove
		 */
		public void removeAdjustmentListener(TimeSliderAdjustmentListener i) {
			listeners.remove(i);
		}
		
		/** Set the time interval between consecutive DoubleSliderAdjustmentListener
		 notifications whilst tracking a drag.
		 @param i the new interval in milliseconds
		 */
		public void setTrackNotificationThrottle(long i) {
			notifyInterval = i;
		}
		
		/** Get the current selected maximum value
		 @return maximum selected value
		 */
		public double getSelectedMinimum() {
			return currentMinValue;
		}
		
		/** Get the current selected maximum value
		 @return maximum selected value
		 */
		public double getSelectedMaximum() {
			return currentMaxValue;
		}
		
		/** Get the current absolute minimum value
		 @return minimum absolute value
		 */
		public double getAbsoluteMinimum() {
			return minValue;
		}
		
		/** Get the current absolute maximum value
		 @return maximum absolute value
		 */
		public double getAbsoluteMaximum() {
			return maxValue;
		}
		
		/** Set the minimum selected value
		 @param min the new minimum selected value
		 */
		public void setSelectedMinimum(double min) {
			currentMinValue = min;
			if (currentMaxValue<currentMinValue)
				currentMaxValue = currentMinValue;
//			sanitiseLimits();
			layoutAndNotify();
		}
		
		/** Set the maximum selected value
		 @param max the new maximum selected value
		 */
		public void setSelectedMaximum(double max) {
			currentMaxValue = max;
			if (currentMinValue>currentMaxValue)
				currentMinValue = currentMaxValue;
//			sanitiseLimits();
			layoutAndNotify();
		}
		
		/** Set the selected range
		 @param min the new minimum selected value
		 @param max the new maximum selected value
		 */
		public void setSelectedRange(double min,double max) {
			currentMinValue = min;
			currentMaxValue = max;
			if (currentMaxValue<currentMinValue)
				currentMaxValue = currentMinValue =
					(currentMaxValue+currentMinValue)/2.0;
//			sanitiseLimits();
			layoutAndNotify();
		}
		
		/** Set the minimum possible value
		 @param min the new minimum possible value
		 */
		public void setAbsoluteMinimum(double min, boolean updateCurrentMin) {
//			if (updateCurrentMin && Math.abs(minValue - currentMinValue) < 0.0001) currentMinValue = min;
			minValue = min;
			if (maxValue<minValue)
				maxValue = minValue;
//			sanitiseLimits();
			layoutAndNotify();
		}
		
		/** Set the maximum possible value
		 @param max the new maximum possible value
		 */
		public void setAbsoluteMaximum(double max, boolean updateCurrentMax) {
//			if (updateCurrentMax && Math.abs(maxValue - currentMaxValue) < 0.0001) currentMaxValue = max;
			maxValue = max;
			if (minValue>maxValue)
				minValue = maxValue;
//			sanitiseLimits();
			layoutAndNotify();
		}
		
		/** Set the maximum possible range
		 @param min the new minimum possible value
		 @param max the new maximum possible value
		 */
		public void setAbsoluteRange(double min,double max) {
			minValue = min;
			maxValue = max;
			if (maxValue<minValue)
				maxValue = minValue = (maxValue+minValue)/2.0;
//			sanitiseLimits();
			layoutAndNotify();
		}
		
		/** Set the maximum possible range and selected range all together
		 @param absMin the new minimum possible value
		 @param absMax the new maximum possible value
		 @param selectedMin the new minimum selected value
		 @param selectedMax the new maximum selected value
		 */
		public void setValues( double absMin, double absMax, double selectedMin, double selectedMax) {
			minValue = absMin;
			maxValue = absMax;
			currentMinValue = selectedMin;
			currentMaxValue = selectedMax;
			if (maxValue<minValue)
				maxValue = minValue = (maxValue+minValue)/2.0;
//			sanitiseLimits();
			layoutAndNotify();
		}
		
		/** Get the current color for the track
		 @return The current track color
		 */
		public Color getTrackColor() {
			return trackColor;
		}
		
		/** Set the current color for the track
		 @param c the new color
		 */
		public void setTrackColor(Color c) {
			trackColor = c;
		}
		
		/** Get the average of two colours
		 @param a Color to mix
		 @param b Color to mix
		 @return (a+b)/2
		 */
		private Color mixColors(Color a,Color b) {
			return new Color( (a.getRed() + b.getRed())/2,
					(a.getGreen() + b.getGreen())/2,
					(a.getBlue() + b.getBlue())/2);
		}
		
		/** Return the given value pinned to the range 0.0-1.0
		 @param x Value to pin
		 @return min(max(x,1.0),0.0)
		 */
		float pinToUnity(double x) {
			if (x>1.0F)
				return 1.0F;
			if (x<0.0F)
				return 0.0F;
			return (float) x;
		}
		
		/** Return the given Color after scaling by the given factor.
		 @param a Color to scale
		 @param factor Scale factor
		 @return a*factor
		 */
		Color scaleColor(Color a,double factor) {
			return new Color(
					(float) pinToUnity((a.getRed()/255.0) * factor),
					(float) pinToUnity((a.getGreen()/255.0) * factor),
					(float) pinToUnity((a.getBlue()/255.0) * factor));
		}
		
		/** The middle track component */
		private class Plot extends JComponent {
			
			DateAxis axis;
			
			public Plot(long startDate, long endDate) {
				if (startDate >= endDate) endDate = startDate+1;
				axis = new DateAxis();
				axis.setAxisLineVisible(true);
				axis.setTickLabelsVisible(true);
				axis.setTickMarksVisible(true);
				axis.setRange(new Date(startDate), new Date(endDate));
				axis.setTickLabelFont(tickFont);
				setToolTipText("");
			}
			
			public String getToolTipText(MouseEvent e) {
				Component c = (Component) e.getSource();
				int x = e.getX();
				long date = (long)minValue + (long)pixelToValue(x);
				return dateFormat.format(new Date(date));
//				tooltip.showPopup(this, (int)(getLocationOnScreen().getX() + e.getX()), (int)(getLocationOnScreen().getY()+e.getY()));
//				return null;
			}
			
			public void setRange(long startDate, long endDate) {
				if (startDate < 0) startDate = 0;
				if (endDate <= startDate) endDate = startDate+1;
				axis.setRange(new Date(startDate), new Date(endDate));
			}
			
			public void setTickUnits(TickUnits units) {
				axis.setStandardTickUnits(units);
				axis.configure();
			}
			
			private void drawPlot(Graphics g) {
				Rectangle2D rect = getBounds();
				rect.setRect(rect.getX()-3*buttonSize, rect.getY(), rect.getWidth(), rect.getHeight());
				Method method = null;
				try {
					method = axis.getClass().getMethod("draw", new Class[] { Graphics2D.class, double.class, Rectangle2D.class, Rectangle2D.class, RectangleEdge.class });
					method.invoke(axis, new Object[] { (Graphics2D)g, Double.valueOf(1), rect, rect, RectangleEdge.BOTTOM });
				} catch (Exception ex) {
					try {
						method = axis.getClass().getMethod("draw", new Class[] { Graphics2D.class, double.class, Rectangle2D.class, Rectangle2D.class, RectangleEdge.class, PlotRenderingInfo.class });
						method.invoke(axis, new Object[] { (Graphics2D)g, Double.valueOf(1), rect, rect, RectangleEdge.BOTTOM, null });
					} catch (Exception ex1) {
						ex1.printStackTrace();
					}
				}
//				axis.draw((Graphics2D)g, rect.getHeight()/2, rect, rect, RectangleEdge.BOTTOM);
			}
			
			/** Draw the track, possibly highlighting a range somewhere */
			public void paint(Graphics g) {
				g.setColor(selectedColor);
				g.fill3DRect(0,0,getWidth(),getHeight(),true);
				g.setColor(originalColor);
				int start,size;
				int x1 = valueToPixel(originalMinValue - minValue);
				if (x1 < 0) start = 0;
				else start = x1;
				x1 = valueToPixel(originalMaxValue - minValue);
				int x2 = valueToPixel(maxValue - minValue);
				if (x1 > x2) size = getWidth() - start;
				else size = getWidth() - start - (x2 - x1);
				if (size <= 0) size = 1;
				g.fillRect(start,1,size,getHeight()-2);
				drawPlot(g);
			}
		} // end of Plot
		
		/** The middle track component */
		private class Tracker extends JComponent {
			
			public Tracker() {
			}
			
			/** Draw the track, possibly highlighting a range somewhere */
			public void paint(Graphics g) {
				g.setColor(trackColor);
				g.fill3DRect(0,0,getWidth(),getHeight(),true);
			}
		} // end of Tracker
		
		/** The arrow button component */
		private class ArrowButton extends JComponent {
			
			/** Possible orientation */
			public static final int RIGHT = 0, LEFT = 1;
			/** Current orientation */
			private int facing;
			
			/** Construct a new ArrowButton facing in the given direction */
			ArrowButton(int facing) {
				this.facing = facing;
				setOpaque(false);
			}
			
			/** Draw a triangle centered around the given point of the given size
			 @param g The Graphics context
			 @param x X coordinate of center
			 @param y Y coordinate of center
			 @param size the number of pixels wide/high
			 */
			public void paintTriangle(Graphics g,int x,int y,int size) {
				
				for (int i=0;i<size;i++) {
					switch (facing) {
					case LEFT:
						g.drawLine(x-size/2+i,y-i,x-size/2+i,y+i);
						break;
					case RIGHT:
						g.drawLine(x+size/2-i,y-i,x+size/2-i,y+i);
						break;
					}
				}
			}
			
			/** Draw the ArrowButton component, with its arrow */
			public void paint(Graphics g) {
				g.setColor(trackColor);
				g.fill3DRect(0,0,getWidth(),getHeight(),true);
				g.setColor(Color.black);
				paintTriangle(g,getWidth()/2,getHeight()/2,
						Math.min(getWidth(),getHeight())/2-3);
			}
		} // end of ArrowButton
		
		/** Notify registered listeners that a change has occurred */
		void notifyListeners() {
			if (trackingDrag) {
				long timeNow = System.currentTimeMillis();
				if ((timeNow-lastNotifyTime)>=notifyInterval)
					lastNotifyTime = timeNow;
				else return;
			}
			Iterator itr = listeners.iterator();
			while (itr.hasNext()) {
				((TimeSliderAdjustmentListener)itr.next()).
				adjustmentValueChanged();
			}
		}
		
//		/** Ensure that the given value is within the absolute maximum and
//		minimum values.
//		@return min(max(limit,maxValue),minValue)
//		*/
//		private double pinLimits(double limit) {
//		if (limit>maxValue)
//		limit = maxValue;
//		if (limit<minValue)
//		limit = minValue;
//		return limit;
//		}
//		
//		/** Ensure that the current selected and highlighted limits are sane */
		void sanitiseLimits() {
			if (currentMinValue < 0) currentMinValue = 0;
			if (minValue < 0) minValue = 0;
			if (currentMaxValue > currentTime) currentMaxValue = currentTime;
			if (maxValue > currentTime) maxValue = currentTime;
			int min = valueToPixel(currentMinValue - minValue);
			int max = valueToPixel(currentMaxValue - minValue);
			if (min < 0 || max < 0) return;
			if (Math.abs(max - min) < 3 * buttonSize) {
				int middle = (max + min) / 2;
				int minx = (int)(middle - 1.5 * buttonSize);
				if (minx < 0) {
					minx = 0;
				}
				int maxx = minx + 3 * buttonSize;
				if (maxx > getTrackSize()) {
					maxx = getTrackSize();
					minx = maxx - 3 * buttonSize;
					if (minx < 0) minx = 0;
				}
				currentMinValue = minValue + pixelToValue(minx);
				currentMaxValue = minValue + pixelToValue(maxx);
			}
		}
		
		/** Update the locations of the components and tell any listeners */
		void layoutAndNotify() {
			layoutMyButtons();
//			notifyListeners();
		}
		
		/** Coordinates of last mouse coordinates during a drag */
		int lastX,lastY;
		/** Simple nested class to hide the public Listener and LayoutManager
		 events
		 */
		private class EventsHandler implements LayoutManager, MouseMotionListener, MouseListener {
			
			/** Handle a mouse press at the beginning of a drag */
			public void mousePressed(MouseEvent e) {
				Component c = (Component) e.getSource();
				lastX = e.getX() + c.getX();
				lastY = e.getY() + c.getY();
				trackingDrag = true;
				lastNotifyTime = System.currentTimeMillis();
			}
			
			/** Handle a mouse release at the end of a drag */
			public void mouseReleased(MouseEvent e) {
				trackingDrag = false;
//				System.out.println((long)originalMaxValue+"-"+(long)originalMinValue+"-"+(long)currentMaxValue+"-"+(long)currentMinValue);
				plotIsEnabled = (!(Math.abs(originalMaxValue - ((long)currentMaxValue)) < 100 && Math.abs(originalMinValue - ((long)currentMinValue)) < 100));
				plotButton.setIcon(plotIsEnabled?getPlotIcon():getPlotIconInactive());
//				notifyListeners();
			}
			
			/** Handle user moving mouse */
			public void mouseDragged(MouseEvent e) {
				if (maxValue!=minValue) {
					Component c = (Component) e.getSource();
					int newX = e.getX() + c.getX();
					int offset;
					offset = newX - lastX;
					if (c==minButton)
						offset = updateMinimum(offset);
					else if (c==maxButton)
						offset = updateMaximum(offset);
					else if (c==track)
						offset = updateMinimumAndMaximum(offset);
					lastX = newX - offset;
					sanitiseLimits();
					layoutAndNotify();
				}
			}
			
			/** Not used */
			public void mouseClicked(MouseEvent e) {
			}
			
			/** Not used */
			public void mouseEntered(MouseEvent e) {
			}
			
			/** Not used */
			public void mouseExited(MouseEvent e) {
			}
			
			/** Not used */
			public void mouseMoved(MouseEvent e) {
			}
			
			/** Not used */
			public void addLayoutComponent(String name, Component comp) {
			}
			
			/** Relayout the components following (eg) a parent resize */
			public void layoutContainer(Container parent) {
				layoutMyButtons();
			}
			
			/** Get the minimum size of this component */
			public Dimension minimumLayoutSize(Container parent) {
				return preferredLayoutSize(parent);
			}
			
			/** Get the preferred size of this component */
			public Dimension preferredLayoutSize(Container parent) {
				return new Dimension(buttonSize * 4, buttonSize);
			}
			
			/** Not used */
			public void removeLayoutComponent(Component comp) {
			}
		} // end of EventsHandler
		
		/** Figure out the number of pixels in the track
		 @return The number of pixels in the track
		 */
		private int getTrackSize() {
			return plot.getWidth(); //- buttonSize*4;
		}
		
		/** Convert a pixel difference to a value difference */
		private double pixelToValue(int pixel) 	{
			if (getTrackSize()==0)
				return 0.0;
			return pixel * (maxValue-minValue) /
			getTrackSize();
		}
		
		/** Convert a value difference to a value pixel */
		int valueToPixel(double value) {
			if (maxValue==minValue) {
				return 0;
			}
			return (int) (0.5 + value * getTrackSize() /
					(maxValue-minValue));
		}
		
		/** Given a mouse drag of offset on the minimum button
		 update the bounds and return a compensation if
		 they've moved too far.
		 */
		int updateMinimum(int offset) {
			double newMinValue;
			newMinValue = currentMinValue + pixelToValue(offset);
			if (newMinValue<minValue) {
//				Chap has gone off end.
				setAbsoluteMinimum(newMinValue, false);
				plot.setRange((long)minValue, (long)maxValue);
				plot.repaint();
				currentMinValue = minValue;
				offset = 0;
			} else if (newMinValue>currentMaxValue) {
				currentMinValue = currentMaxValue-1;
				offset = valueToPixel(newMinValue - currentMaxValue);
			} else {
				currentMinValue = newMinValue;
				offset = 0;
			}
			return offset;
		}
		
		/** Given a mouse drag of offset on the maximum button
		 update the bounds and return a compensation if
		 they've moved too far.
		 */
		int updateMaximum(int offset) {
			double newMaxValue;
			newMaxValue = currentMaxValue + pixelToValue(offset);
			if (newMaxValue>maxValue) {
//				Chap has gone off end.
				if (maxValue >= currentTime) {
//					currentMaxValue -= currentMinValue - minValue;
					currentMaxValue = maxValue;
					offset = valueToPixel(newMaxValue - currentMaxValue);
				} else {
					if (newMaxValue > currentTime) newMaxValue = currentTime;
					setAbsoluteMaximum(newMaxValue, false);
					plot.setRange((long)minValue, (long)maxValue);
					plot.repaint();
					currentMaxValue = maxValue;
					offset = 0;
				}
			} else if (newMaxValue<currentMinValue) {
				currentMaxValue = currentMinValue;
				offset = valueToPixel(newMaxValue - currentMinValue);
			} else {
				currentMaxValue = newMaxValue;
				offset = 0;
			}
			return offset;
		}
		
		/** Given a mouse drag of offset on the track
		 update the bounds and return a compensation if
		 they've moved too far.
		 */
		int updateMinimumAndMaximum(int offset) {
			
			double newMaxValue = currentMaxValue + pixelToValue(offset);
			double newMinValue = currentMinValue + pixelToValue(offset);
			double diff = currentMaxValue - currentMinValue;
			if (newMaxValue>maxValue) {
				if (maxValue >= currentTime) {
					currentMaxValue = maxValue;
					currentMinValue = currentMaxValue - diff;
					offset = 0;
				} else {
					if (newMaxValue > currentTime) {
						newMaxValue = currentTime;
					}
					diff = newMaxValue - maxValue;
					newMinValue = newMinValue + diff;
					setAbsoluteMinimum(minValue + diff, false);
					setAbsoluteMaximum(newMaxValue, false);
					plot.setRange((long)minValue, (long)maxValue);
					plot.repaint();
					currentMaxValue = maxValue;
					currentMinValue += diff;
					offset = 0;
				}
			} else if (newMinValue<minValue) {
				diff = minValue - newMinValue;
				newMaxValue = newMinValue + diff;
				setAbsoluteMinimum(newMinValue, false);
				setAbsoluteMaximum(maxValue - diff, false);
				plot.setRange((long)minValue, (long)maxValue);
				plot.repaint();
				currentMinValue = minValue;
				currentMaxValue -= diff;
				offset = 0;
			} else {
				currentMaxValue = newMaxValue;
				currentMinValue = newMinValue;
				offset = 0;
			}
			return offset;
		}
		
		/** Set the bounds of our components based on the values */
		void layoutMyButtons() {
			int width = getWidth();
			int minOffset = valueToPixel(currentMinValue - minValue);
			if (minOffset < 0) minOffset = 0;
			int maxOffset = valueToPixel(maxValue - currentMaxValue);
			if (maxOffset < 0) maxOffset = 0;
			plusButton.setBounds(0, 0, buttonSize, buttonSize);
			minusButton.setBounds(buttonSize, 0, buttonSize, buttonSize);
			rangeButton.setBounds(2 * buttonSize, 0, buttonSize, buttonSize);
			plot.setBounds(3*buttonSize, 0, width - 3 * buttonSize-4*buttonSize, buttonSize);
			minButton.setBounds(3 * buttonSize + minOffset,0,
					buttonSize,buttonSize);
			track.setBounds(4 * buttonSize+minOffset,0,
					width-maxOffset-5*buttonSize-minOffset-4*buttonSize, buttonSize);
			maxButton.setBounds(- buttonSize + width-maxOffset-4*buttonSize,0,
					buttonSize,buttonSize);
			plotButton.setBounds(width- 4*buttonSize, 0, 3*buttonSize, buttonSize);
			legendButton.setBounds(width - buttonSize, 0, buttonSize, buttonSize);
			track.repaint();
		} // end of layoutMyButtons
	} // end of DoubleSlider 
	
	private Icon zoomInIcon = null;
	private Icon zoomInIconOver = null;
	
	Icon getZoomInIcon() {
		if (zoomInIcon != null) return zoomInIcon;
		URL iconURL = this.getClass().getResource("/lia/images/groups/icon_zoom_in.png");
		zoomInIcon = new ImageIcon(iconURL);
		return zoomInIcon;
	}

	Icon getZoomInIconOver() {
		if (zoomInIconOver != null) return zoomInIconOver;
		URL iconURL = this.getClass().getResource("/lia/images/groups/icon_zoom_in_over.png");
		zoomInIconOver = new ImageIcon(iconURL);
		return zoomInIconOver;
	}

	private Icon zoomOutIcon = null;
	private Icon zoomOutIconOver = null;
	
	Icon getZoomOutIcon() {
		if (zoomOutIcon != null) return zoomOutIcon;
		URL iconURL = this.getClass().getResource("/lia/images/groups/icon_zoom_out.png");
		zoomOutIcon = new ImageIcon(iconURL);
		return zoomOutIcon;
	}

	Icon getZoomOutIconOver() {
		if (zoomOutIconOver != null) return zoomOutIconOver;
		URL iconURL = this.getClass().getResource("/lia/images/groups/icon_zoom_out_over.png");
		zoomOutIconOver = new ImageIcon(iconURL);
		return zoomOutIconOver;
	}

	private Icon legendIcon = null;
	private Icon legendIconOver = null;
	
	Icon getLegendIcon() {
		if (legendIcon != null) return legendIcon;
		URL iconURL = this.getClass().getResource("/lia/images/groups/icon_legend.png");
		legendIcon = new ImageIcon(iconURL);
		return legendIcon;
	}
	
	Icon getLegendIconOver() {
		if (legendIconOver != null) return legendIconOver;
		URL iconURL = this.getClass().getResource("/lia/images/groups/icon_legend_over.png");
		legendIconOver = new ImageIcon(iconURL);
		return legendIconOver;
	}
	
	private Icon rangeIcon = null;
	private Icon rangeIconOver = null;
	
	Icon getRangeIcon() {
		if (rangeIcon != null) return rangeIcon;
		URL iconURL = this.getClass().getResource("/lia/images/groups/icon_zoom_orig.png");
		rangeIcon = new ImageIcon(iconURL);
		return rangeIcon;
	}

	Icon getRangeIconOver() {
		if (rangeIconOver != null) return rangeIconOver;
		URL iconURL = this.getClass().getResource("/lia/images/groups/icon_zoom_orig_over.png");
		rangeIconOver = new ImageIcon(iconURL);
		return rangeIconOver;
	}
	
	private Icon plotIcon = null;
	private Icon plotIconInactive = null;
	private Icon plotIconOver = null;
	
	Icon getPlotIcon() {
		if (plotIcon != null) return plotIcon;
		URL iconURL = this.getClass().getResource("/lia/images/groups/plot.png");
		plotIcon = new ImageIcon(iconURL);
		return plotIcon;
	}

	Icon getPlotIconInactive() {
		if (plotIconInactive != null) return plotIconInactive;
		URL iconURL = this.getClass().getResource("/lia/images/groups/plot_inactive.png");
		plotIconInactive = new ImageIcon(iconURL);
		return plotIconInactive;
	}

	Icon getPlotIconOver() {
		if (plotIconOver != null) return plotIconOver;
		URL iconURL = this.getClass().getResource("/lia/images/groups/plot_over.png");
		plotIconOver = new ImageIcon(iconURL);
		return plotIconOver;
	}

	protected static final String[][] tzs = {
		{"A", "GMT+1"},
		{"ACDT", "GMT+10"} ,
		{"ACST", "GMT+9"},
		{"ADT", "GMT-3"}, 
		{"AEDT", "GMT+11"},
		{"AEST", "GMT+10"},
		{"AKDT", "GMT-8"}, 
		{"AKST", "GMT-9"}, 
		{"AST", "GMT-4"}, 
		{"AWST", "GMT+8"}, 
		{"B", "GMT+2"}, 
		{"BST", "GMT+1"}, 
		{"C", "GMT+3"}, 
		{"CDT", "GMT+10"},
		{"CDTA", "GMT+10"},
		{"CDTN", "GMT-5"},
		{"CEST", "GMT+2"}, 
		{"CET", "GMT+1"}, 
		{"CST", "GMT+9"}, 
		{"CSTA", "GMT+9"},
		{"CSTN", "GMT-6"},
		{"CXT", "GMT+7"}, 
		{"D", "GMT+4"}, 
		{"E", "GMT+5"}, 
		{"EDT", "GMT+11"},
		{"EDTA", "GMT+11"},
		{"EDTN", "GMT-4"},
		{"EEST", "GMT+3"}, 
		{"EET", "GMT+2"}, 
		{"EST", "GMT+10"},
		{"ESTA", "GMT+10"},
		{"ESTN", "GMT-5"},
		{"F", "GMT+6"}, 
		{"G", "GMT+7"}, 
		{"GMT", "GMT"},
		{"H", "GMT+8"}, 
		{"HAA", "GMT-3"}, 
		{"HAC", "GMT-5"}, 
		{"HADT", "GMT-9"}, 
		{"HAE", "GMT-4"}, 
		{"HAP", "GMT-7"}, 
		{"HAR", "GMT-6"}, 
		{"HAST", "GMT-10"}, 
		{"HAT", "GMT-2"}, 
		{"HAY", "GMT-8"}, 
		{"HNA", "GMT-4"}, 
		{"HNC", "GMT-6"}, 
		{"HNE", "GMT-5"}, 
		{"HNP", "GMT-8"}, 
		{"HNR", "GMT-7"}, 
		{"HNT", "GMT-3"}, 
		{"HNY", "GMT-9"}, 
		{"I", "GMT+9"}, 
		{"IST", "GMT+1"}, 
		{"K", "GMT+10"}, 
		{"L", "GMT+11"}, 
		{"M", "GMT+12"}, 
		{"MDT", "GMT-6"}, 
		{"MESZ", "GMT+2"}, 
		{"MEZ", "GMT+1"}, 
		{"MST", "GMT-7"}, 
		{"N", "GMT-1"}, 
		{"NDT", "GMT-2"}, 
		{"NFT", "GMT+11"}, 
		{"NST", "GMT-3"}, 
		{"O", "GMT-2"}, 
		{"P", "GMT-3"}, 
		{"PDT", "GMT-7"}, 
		{"PST", "GMT-8"}, 
		{"Q", "GMT-4"}, 
		{"R", "GMT-5"}, 
		{"S", "GMT-6"}, 
		{"T", "GMT-7"}, 
		{"U", "GMT-8"}, 
		{"UTC", "GMT"}, 
		{"V", "GMT-9"}, 
		{"W", "GMT-10"}, 
		{"WEST", "GMT+1"}, 
		{"WET", "GMT"}, 
		{"WST", "GMT+8"}, 
		{"X", "GMT-11"}, 
		{"Y", "GMT-12"}, 
		{"Z", "GMT"}		
	};
	
	protected String formatTimeZone( String tz ) {
		
		if (tz == null) return null;
		for (int i=0; i<tzs.length; i++)
			if (tz.equals(tzs[i][0]))
				return tzs[i][1];
		
		return tz;
	}
	
	private void createStandardTickUnits( TimeZone tz ) {
		
		TickUnits units = new TickUnits();
		
		SimpleDateFormat format = null;

		// milliseconds
		format = new SimpleDateFormat("HH:mm:ss.SSS"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 1,
				format));
		format = new SimpleDateFormat("HH:mm:ss.SSS"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 5, DateTickUnit.MILLISECOND, 1,
				format));
		format = new SimpleDateFormat("HH:mm:ss.SSS"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 10, DateTickUnit.MILLISECOND, 1,
				format));
		format = new SimpleDateFormat("HH:mm:ss.SSS"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 25, DateTickUnit.MILLISECOND, 5,
				format));
		format = new SimpleDateFormat("HH:mm:ss.SSS"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 50, DateTickUnit.MILLISECOND, 10,
				format));
		format = new SimpleDateFormat("HH:mm:ss.SSS"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 100, DateTickUnit.MILLISECOND, 10,
				format));
		format = new SimpleDateFormat("HH:mm:ss.SSS"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 250, DateTickUnit.MILLISECOND, 10,
				format));
		format = new SimpleDateFormat("HH:mm:ss.SSS"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MILLISECOND, 500, DateTickUnit.MILLISECOND, 50,
				format));

		// seconds
		format  = new SimpleDateFormat("HH:mm:ss"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.SECOND, 1, DateTickUnit.MILLISECOND, 50,
				format));
		format = new SimpleDateFormat("HH:mm:ss"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.SECOND, 5, DateTickUnit.SECOND, 1, 
				format));
		format = new SimpleDateFormat("HH:mm:ss"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.SECOND, 10, DateTickUnit.SECOND, 1, 
				format));
		format = new SimpleDateFormat("HH:mm:ss");
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.SECOND, 30, 
				DateTickUnit.SECOND, 5, format));

		// minutes
		format = new SimpleDateFormat("HH:mm"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MINUTE, 1, DateTickUnit.SECOND, 5, 
				format));
		format = new SimpleDateFormat("HH:mm"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MINUTE, 2, DateTickUnit.SECOND, 10, 
				format));
		format = new SimpleDateFormat("HH:mm"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MINUTE, 5, DateTickUnit.MINUTE, 1, 
				format));
		format = new SimpleDateFormat("HH:mm"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MINUTE, 10, DateTickUnit.MINUTE, 1, 
				format));
		format = new SimpleDateFormat("HH:mm"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MINUTE, 15, DateTickUnit.MINUTE, 5, 
				format));
		format = new SimpleDateFormat("HH:mm"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MINUTE, 20, DateTickUnit.MINUTE, 5, 
				format));
		format =new SimpleDateFormat("HH:mm"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MINUTE, 30, DateTickUnit.MINUTE, 5, 
				format));

		// hours
		format = new SimpleDateFormat("HH:mm"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.HOUR, 1, DateTickUnit.MINUTE, 5, 
				format));
		format = new SimpleDateFormat("HH:mm"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.HOUR, 2, DateTickUnit.MINUTE, 10, 
				format));
		format = new SimpleDateFormat("HH:mm"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.HOUR, 4, DateTickUnit.MINUTE, 30, 
				format));
		format = new SimpleDateFormat("HH:mm"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.HOUR, 6, DateTickUnit.HOUR, 1, 
				format));
		format = new SimpleDateFormat("d-MMM, HH:mm"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.HOUR, 12, DateTickUnit.HOUR, 1, 
				format));

		// days
		format = new SimpleDateFormat("d-MMM"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.DAY, 1, DateTickUnit.HOUR, 1, 
				format));
		format = new SimpleDateFormat("d-MMM"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.DAY, 2, DateTickUnit.HOUR, 1, 
				format));
		format = new SimpleDateFormat("d-MMM"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.DAY, 7, DateTickUnit.DAY, 1, 
				format));
		format = new SimpleDateFormat("d-MMM"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.DAY, 15, DateTickUnit.DAY, 1, 
				format));

		// months
		format = new SimpleDateFormat("MMM-yyyy"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MONTH, 1, DateTickUnit.DAY, 1, 
				format));
		format = new SimpleDateFormat("MMM-yyyy"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MONTH, 2, DateTickUnit.DAY, 1, 
				format));
		format = new SimpleDateFormat("MMM-yyyy"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MONTH, 3, DateTickUnit.MONTH, 1, 
				format));
		format = new SimpleDateFormat("MMM-yyyy"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MONTH, 4,  DateTickUnit.MONTH, 1, 
				format));
		format = new SimpleDateFormat("MMM-yyyy"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.MONTH, 6,  DateTickUnit.MONTH, 1, 
				format));

		// years
		format = new SimpleDateFormat("yyyy"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.YEAR, 1,  DateTickUnit.MONTH, 1, 
				format));
		format = new SimpleDateFormat("yyyy"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.YEAR, 2,  DateTickUnit.MONTH, 3, 
				format));
		format = new SimpleDateFormat("yyyy"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.YEAR, 5,  DateTickUnit.YEAR, 1, 
				format));
		format = new SimpleDateFormat("yyyy"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.YEAR, 10,  DateTickUnit.YEAR, 1, 
				format));
		format = new SimpleDateFormat("yyyy"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.YEAR, 25, DateTickUnit.YEAR, 5, 
				format));
		format = new SimpleDateFormat("yyyy"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.YEAR, 50, DateTickUnit.YEAR, 10, 
				format));
		format = new SimpleDateFormat("yyyy"); 
		format.setTimeZone(tz);
		units.add(new DateTickUnit(DateTickUnit.YEAR, 100, DateTickUnit.YEAR, 20, 
				format));

		sl.setTimeZone(units);
	}
	
	public class PlotTooltip extends JToolTip {
		Popup popup = null;
		
		public PlotTooltip() {
			super();
			setLayout(new BorderLayout());
			add(new PlotPanel(), BorderLayout.CENTER);
			setPreferredSize(new Dimension(220, 110));
			addMouseMotionListener(new MouseMotionListener() {
				public void mouseDragged(MouseEvent e) {
					hidePopup();
				}
				public void mouseMoved(MouseEvent e) {
					hidePopup();
				}
			});
			addMouseListener(new MouseAdapter() {
			    public void mouseExited(MouseEvent e) {
					hidePopup();
			    }
			});
		}
		
		public void showPopup(Component owner, int x, int y) {
			if (owner == null)
				return;
			if (popup != null) {
				popup.hide();
				popup = null;
			}
			popup = PopupFactory.getSharedInstance().getPopup(owner, this, x, y);
			popup.show();
			requestFocus();
		}
		
		public void hidePopup() {
			if (popup != null) {
				popup.hide();
				popup = null;
			}
		}
		
		class PlotPanel extends JPanel {
			
			public final Font textFont = new Font("Arial", Font.PLAIN, 10);
			
			public PlotPanel() {
				super();
				setBackground(Color.white);
			}
			
			private void drawLegendRect(Graphics2D g2, Color rectColor, int x, int y, int w) {
				g2.setColor(rectColor);
				g2.fillRect(x+1, y+1, w+9, w+9);
				g2.setColor(Color.black);
				g2.drawRect(x, y, w+10, w+10);
//				g2.drawRect(x+1, y+1, w+8, w+8);
//				g2.drawRect(x+2, y+2, w+6, w+6);
//				g2.setFont(PortsPanel.portFont);
			}
			
			public void paintComponent(Graphics g) {
				
				super.paintComponent(g);
				
				Graphics2D g2 = (Graphics2D)g;
				// draw the in title
				g2.setFont(PortsPanel.titleFont);
				FontMetrics fm = g2.getFontMetrics();
				int titleW = fm.stringWidth("LEGEND");
				g2.drawString("LEGEND", (getWidth()-titleW)/2, 20);
				g2.setFont(PortsPanel.portFont);
				fm = g2.getFontMetrics();
				int maxLen = fm.stringWidth("1");
				int w = fm.stringWidth("2");
				if (maxLen < w) maxLen = w;
				int startRect = 20;
				
				// draw the ports...
				int startY = 20 + fm.getHeight();
				drawLegendRect(g2, trackColor, startRect, startY, w);
				int middleY = startY + fm.getHeight();
				g2.setFont(textFont);
				g2.setColor(Color.black);
				g2.drawString("Current selected time interval", startRect + 22 + maxLen, middleY);
				fm = g2.getFontMetrics();
				
				startY = startY + maxLen + 20;
				drawLegendRect(g2, originalColor, startRect, startY, w);
				middleY = startY + fm.getHeight();
				g2.setFont(textFont);
				g2.setColor(Color.black);
				g2.drawString("Original selected time interval", startRect + 22 + maxLen, middleY);
			} 
		}
	}
	
} // end of class IntervalSelector

