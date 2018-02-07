package lia.Monitor.JiniClient.CommonGUI.plot;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Random;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SpringLayout;

import lia.Monitor.JiniClient.CommonGUI.StatisticsMonitor;

import org.jfree.ui.RefineryUtilities;
import org.jfree.ui.TextAnchor;

public class MultiGraphPanel extends JPanel implements MouseMotionListener, MouseListener, ActionListener, ComponentListener {

    private static final double MIN_X_GUIDES_STEP = 20;

    private static final int MAX_POINTS = 100;//720;//22;
	
	private long MAX_TIME_INTERVAL = 200 * 1000; 

    private String title = "";

	private String yAxis = "";

	private String unit = "";

	private double max = -1.0;
	private double min = -1.0;
	private double absoluteMax = -1.0;
	private double absoluteMin = -1.0;

	private long minX = 0l;
	private long maxX = 0l;
	
	protected NumberFormat nf = null;
	protected NumberFormat nf1 = null;
	protected static SimpleDateFormat sd = new SimpleDateFormat("hh:mm:ss");
	protected static String testString = "99.999,9";
	
	private String[] singleLegendItems = null;
	private LinkedList singlePoints = null;
	private boolean singleVisible[] = null;
	
	private LinkedList points[] = null;
	private Hashtable reducedPoints = new Hashtable();
	protected String[] legendItems = null;
	protected boolean visible[] = null;

	protected Font titleFont = null;
	protected Font labelFont = null;
	protected Font scaleFont = null;
	protected Font legendFont = null;
	
	protected boolean drawLegend = true; // by default draw legend

	protected Color axisColor = new Color(0, 120, 0);
	protected Color pointColor[] = null;
	protected Color lineColor[] = null;
	protected Color singleLineColor[] = null;
	protected TexturePaint singleLineLegendPaint[] = null;

	protected double titleHeight = 0.0;
	protected double yAxisWidth = 0.0;
	protected double yAxisLabelWidth = 0.0;
	protected double xAxisHeight = 0.0;
	protected double xAxisLabelHeight = 0.0;
	protected Rectangle surfaceRect = null;
	protected double newMin = 0.0, newMax = 0.0;

	protected int progress = 0;

	protected Cursor moveCursor = null;
	protected Point2D.Double mouseCursor = null;
	protected Double singleMouseCursor = null;
	protected MouseEvent mouseEvent = null;

	protected JButton zoomIn = null;
	protected JButton zoomOut = null;
	protected JButton autoRange = null;
	protected JButton downUp = null;
	protected JButton bgColorButton = null;
	
	protected String conversion = "";

	protected Point startPoint = null;
	protected Rectangle rect = null;
	
	protected Rectangle zoomRect = null;
	protected double zoomMin = -9999;
	protected double zoomMax = 9999;
	protected boolean isUp = true;
	protected SpringLayout layout = null;

	protected Color bgColor = Color.white;
	protected Color comBgColor = Color.black;

	final BasicStroke singleStroke = new BasicStroke(2.0f);
	
	final static float dash1[] = {5.0f};
	final static BasicStroke dashed = new BasicStroke
		(1.0f, 
		 BasicStroke.CAP_BUTT, 
		 BasicStroke.JOIN_MITER, 
		 10.0f, dash1, 0.0f);
	
	protected boolean componentResized = false;

	protected static final Color colorTable[] =
		new Color[] {
            Color.red,
            Color.blue,
            Color.green,
//			new Color(0, 250, 0),
			new Color(120, 120, 250),
			new Color(255, 205, 68),
			new Color(129, 226, 250),
			new Color(218, 60, 250),
			new Color(75, 250, 209), 
			new Color(250, 54, 224),
			new Color(250, 149, 188), 
			new Color(250, 63, 11)
		};
	
	protected static final Color pointColorTable[] = 
		new Color[] {
		new Color(0x7f, 0xff, 0xd4),
        new Color(0xad, 0xd8, 0xe6),
        new Color(0xdd, 0xa0, 0xdd),
	};

	public MultiGraphPanel(
		String title,
		String[] legendItems,
        Color[] legendColors,
        String[] singleLegendItems,
        Color[] singleLegendColors,
		String yAxis,
		String unit, 
		long MAX_TIME_INTERVAL ) {

		super();
		this.title = title;
		this.MAX_TIME_INTERVAL = MAX_TIME_INTERVAL;
		this.yAxis = yAxis;
		this.unit = unit;
		this.legendItems = legendItems;
		this.singleLegendItems = singleLegendItems;
		if (legendItems == null && singleLegendItems == null) {
			System.err.println("Cannot plot null series..");
			return;
		}
		if (legendItems != null) {
			int howMany = legendItems.length;
			points = new LinkedList[howMany];
			for (int i=0; i<points.length; i++) points[i] = new LinkedList();
			
			visible = new boolean[howMany];
			if ( legendColors== null ) {
				pointColor = new Color[howMany];
				lineColor = new Color[howMany];
				for (int i = 0; i < howMany; i++) {
					visible[i] = true;
					pointColor[i] = colorTable[i%colorTable.length];
					int r = pointColor[i].getRed();
					int g = pointColor[i].getGreen();
					int b = pointColor[i].getBlue();
					r = 2 * r / 3;
					g = 2 * g / 3;
					b = 2 * b / 3;
					lineColor[i] = new Color(r, g, b);
				}
			} else {
				pointColor = legendColors;
				lineColor = legendColors;
			}
		}
		if (singleLegendItems != null) {
			int howMany = singleLegendItems.length;
			singlePoints = new LinkedList();
			for (int i=0; i<howMany; i++) singlePoints.addLast(Double.valueOf(Double.NaN));
			singleVisible = new boolean[howMany];
			if (singleLegendColors == null) {
				singleLineColor = new Color[howMany];
				for (int i=0; i<howMany; i++) {
					singleVisible[i] = true;
					singleLineColor[i] = pointColorTable[i % pointColorTable.length];
				}
			} else {
				singleLineColor = singleLegendColors;
			}
			//create paints for all legends
			singleLineLegendPaint = new TexturePaint[howMany];
			for ( int i=0; i<howMany; i++) {
				BufferedImage bi = new BufferedImage(2,1, BufferedImage.TYPE_INT_ARGB);
				Graphics g = bi.getGraphics();
//				g.setColor(Color.WHITE);
//				g.drawLine(2, 0, 3, 0);
				g.setColor(singleLineColor[i]);
				g.drawLine(0, 0, 0, 0);
				g.setColor(Color.WHITE);
				g.drawLine(1, 0, 1, 0);
				Rectangle r = new Rectangle(0,0,2,1);
				TexturePaint tp = new TexturePaint(bi, r);
				singleLineLegendPaint[i]=tp;
			}
		}
		if (nf == null) {
			nf = NumberFormat.getInstance(Locale.GERMAN);
			nf.setMinimumFractionDigits(1);
			nf.setMaximumFractionDigits(1);
			nf1 = NumberFormat.getInstance(Locale.GERMAN);
			nf1.setMinimumFractionDigits(2);
			nf1.setMaximumFractionDigits(2);
		}

		titleFont = new Font("Arial", Font.BOLD, 16);
		labelFont = new Font("Arial", Font.PLAIN, 12);
		scaleFont = new Font("Arial", Font.PLAIN, 10);
		legendFont = new Font("Arial", Font.BOLD, 12);

		addMouseMotionListener(this);
		addMouseListener(this);
		addComponentListener(this);
		moveCursor = new Cursor(Cursor.CROSSHAIR_CURSOR);
		
		layout = new SpringLayout();
		setLayout(layout);
		
		zoomIn = new JButton("");
		try {
			zoomIn.setIcon(getZoomIn());
		} catch (Exception e) {
			zoomIn.setText("Zoom out");
		}
		zoomIn.setToolTipText("Click to zoom out");
		zoomIn.setMargin(new Insets(0, 0, 0, 0));
		zoomIn.addActionListener(this);
		add(zoomIn);
		
		zoomOut = new JButton("");
		try {
			zoomOut.setIcon(getZoomOut());
		} catch (Exception e) {
			zoomOut.setText("Zoom in");
		}
		zoomOut.setToolTipText("Click to zoom in");
		zoomOut.setMargin(new Insets(0, 0, 0, 0));
		zoomOut.addActionListener(this);
		add(zoomOut);
		
		autoRange = new JButton("");
		try {
			autoRange.setIcon(getFitIcon());
		} catch (Exception e) {
			autoRange.setText("Auto range");
		}
		autoRange.setToolTipText("Click to autorange");
		autoRange.setMargin(new Insets(0, 0, 0, 0));
		autoRange.addActionListener(this);
		add(autoRange);
		
		bgColorButton = new JButton("");
		try {
			bgColorButton.setIcon(getBgColorIcon());
		} catch (Exception e) {
			bgColorButton.setText("bgColor");
		}
		bgColorButton.setOpaque(true);
		bgColorButton.setToolTipText("Click to change the color of the background");
		bgColorButton.setMargin(new Insets(0, 0, 0, 0));
		bgColorButton.addActionListener(this);
		add(bgColorButton);

		downUp = new JButton("");
		try {
			downUp.setIcon(getDownIcon());
		} catch (Exception e) {
			downUp.setText("Hide menu");
		}
		downUp.setToolTipText("Click to hide the menu");
		downUp.setMargin(new Insets(0, 0, 0, 0));
		downUp.addActionListener(this);
		add(downUp);

		layout.putConstraint(SpringLayout.SOUTH, bgColorButton, 2, SpringLayout.SOUTH, this);
		layout.putConstraint(SpringLayout.WEST, bgColorButton, 2, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.SOUTH, zoomIn, 2, SpringLayout.NORTH, bgColorButton);
		layout.putConstraint(SpringLayout.WEST, zoomIn, 2, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.SOUTH, zoomOut, 2, SpringLayout.NORTH, zoomIn);
		layout.putConstraint(SpringLayout.WEST, zoomOut, 2, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.SOUTH, autoRange, 2, SpringLayout.NORTH, zoomOut);
		layout.putConstraint(SpringLayout.WEST, autoRange, 2, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.SOUTH, downUp, -1, SpringLayout.NORTH, autoRange);
		layout.putConstraint(SpringLayout.WEST, downUp, 2, SpringLayout.WEST, this);
	}

	public MultiGraphPanel(
		String title,
		String[] legendItems,
        Color[] legendColors,
        String[] singleLegendItems,
        Color[] singleLegendColors,
		String yAxis,
		String unit,
		double min,
		double max, long MAX_TIME_INTERVAL) {

		this(title, legendItems, legendColors, singleLegendItems, singleLegendColors, yAxis, unit, MAX_TIME_INTERVAL);
		this.absoluteMin = this.min = min;
		this.absoluteMax = this.max = max;
	}
	
	protected void menuDown() {

		try {
			downUp.setIcon(getUpIcon());
		} catch (Exception e) {
			downUp.setText("View menu");
		}
		downUp.setToolTipText("Click to view the menu");

		remove(zoomIn);
		remove(zoomOut);
		remove(autoRange);
		remove(bgColorButton);
		
		layout.putConstraint(SpringLayout.SOUTH, downUp, -2, SpringLayout.SOUTH, this);
		layout.putConstraint(SpringLayout.WEST, downUp, 2, SpringLayout.WEST, this);
		revalidate();
		repaint();
	}
	
	protected void menuUp() {
	
		try {
			downUp.setIcon(getDownIcon());
		} catch (Exception e) {
			downUp.setText("Hide menu");
		}
		downUp.setToolTipText("Click to hide the menu");

		add(zoomIn);
		add(zoomOut);
		add(autoRange);
		add(bgColorButton);

		layout.putConstraint(SpringLayout.SOUTH, bgColorButton, -1, SpringLayout.SOUTH, this);
		layout.putConstraint(SpringLayout.WEST, bgColorButton, 2, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.SOUTH, zoomIn, -1, SpringLayout.NORTH, bgColorButton);
		layout.putConstraint(SpringLayout.WEST, zoomIn, 2, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.SOUTH, zoomOut, -1, SpringLayout.NORTH, zoomIn);
		layout.putConstraint(SpringLayout.WEST, zoomOut, 2, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.SOUTH, autoRange, -1, SpringLayout.NORTH, zoomOut);
		layout.putConstraint(SpringLayout.WEST, autoRange, 2, SpringLayout.WEST, this);
		layout.putConstraint(SpringLayout.SOUTH, downUp, -1, SpringLayout.NORTH, autoRange);
		layout.putConstraint(SpringLayout.WEST, downUp, 2, SpringLayout.WEST, this);
		revalidate();
		repaint();
	}
	
	private Color getComplementColor(Color color) {
		
		int r = color.getRed();
		int g = color.getGreen();
		int b = color.getBlue();
		return new Color(255-r, 255-g, 255-b);
	}

	protected Color checkAxisColor(Color color) {
		
		int r = color.getRed();
		int g = color.getGreen();
		int b = color.getBlue();
		r = ((255 - r) + 255) % 255;
		g = ((255-g) + Math.abs(120 - g)) % 255;
		b = ((255 - b) + 255) % 255;
		return new Color(r, g, b);
	}
	
	protected Color checkPointColor(Color baseColor, Color color) {
		
		int r = color.getRed();
		int g = color.getGreen();
		int b = color.getBlue();
		int rb = baseColor.getRed();
		int gb = baseColor.getGreen();
		int bb = baseColor.getBlue();
		r = Math.abs(rb - r)% 255;
		g = Math.abs(gb - g) % 255;
		b = Math.abs(bb - b)% 255;
		return new Color(r, g, b);
	}
	
	protected Color checkLineColor(Color baseColor, Color color) {
		
		int r = color.getRed();
		int g = color.getGreen();
		int b = color.getBlue();
		int rb = baseColor.getRed();
		int gb = baseColor.getGreen();
		int bb = baseColor.getBlue();
		r = Math.abs(rb - r) % 255;
		g = Math.abs(gb - g) % 255;
		b = Math.abs(bb - b) % 255;
		return new Color(r, g, b);
	}

	public void setMin(double min) {
		this.absoluteMin = this.min = min;
	}
	
	public void setMax(double max) {
		this.absoluteMax = this.max = max;
	}
	
	public void newSingleValue(int no, double value) {
		
		if (singlePoints == null || singleLegendItems == null || singleLegendItems.length <= no) return;
		if (singlePoints.size() <= no) {
			for (int i=singlePoints.size(); i<=no; i++) singlePoints.addLast(Double.valueOf(Double.NaN));
		}
		singlePoints.set(no, Double.valueOf(value));
	}

	public void newValue(int no, long x, double value) {
		
		synchronized (getTreeLock()) {
			int i=0;
			long startx = 0;
			for (Iterator it = points[no].iterator(); it.hasNext(); ) {
				Point2D.Double point = (Point2D.Double)it.next();
				if (i == 0) startx = (long)point.getX();
				long xx = (long)point.getX();
				if (xx == x) {
					points[no].remove(i);
					points[no].add(i, new Point2D.Double(x, value));
					repaint();
					try {
						checkMouse(mouseEvent);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
					return;
				}
				i++;
			}
			if (points[no].size() == 0) {
				points[no].addLast(new Point2D.Double(x, value));
			} else {
				long lastX = (long)((Point2D.Double)points[no].getLast()).getX();
				if (startx > x) {
					long diff = lastX - x;
					if (diff > MAX_TIME_INTERVAL) return; // can not add the new point
					points[no].addFirst(new Point2D.Double(x, value));
				} else if (x > lastX) {
					points[no].addLast(new Point2D.Double(x, value));
				} else {
					i = 0;
					for (Iterator it = points[no].iterator(); it.hasNext(); ) {
						long xx = (long)((Point2D.Double)it.next()).getX();
						if (xx > x) {
							points[no].add(i, new Point2D.Double(x, value));
							break;
						}
						i++;
					}
				}
			}
			recheckPoints();
			repaint();
			try {
				checkMouse(mouseEvent);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
	
	private void recheckPoints() {
		
		long maxX = 0;
		for (int i=0; i<points.length; i++) {
			if (points[i].size() == 0) continue;
			long newx = (long)((Point2D.Double)points[i].getLast()).getX();
			if (newx > maxX) maxX = newx;
		}
		for (int i=0; i<points.length; i++) {
			if (points[i].size() == 0) continue;
			long startx = (long)((Point2D.Double)points[i].getFirst()).getX();
			if (maxX - startx > MAX_TIME_INTERVAL) {
				while (points[i].size() != 0 && maxX - startx > MAX_TIME_INTERVAL) {
					Point2D.Double p = (Point2D.Double)points[i].removeFirst();
					if (points[i].size() == 0) break;
					startx = (long)((Point2D.Double)points[i].getFirst()).getX();
					if (maxX - startx <= MAX_TIME_INTERVAL) {
						long x1 = (long)p.getX();
						double y1 = p.getY();
						double y2 = ((Point2D.Double)points[i].getFirst()).getY();
						long x = maxX - MAX_TIME_INTERVAL;
						double y = y2 - (startx - x) * (y2 - y1) / (startx - x1);
						points[i].addFirst(new Point2D.Double(x, y));
						break;
					}
				}
			}
		}
	}
	
	public void setUnit(String unit) {

		this.unit = unit;
		repaint();
	}
	
	public Color getLegendColor(int poz) {
		
		if (poz < 0 || poz >= pointColor.length) return null;
		return pointColor[poz];
	}
	
	public Color getSingleLegendColor(int poz) {
		
		if (poz < 0 || poz >= singleLineColor.length) return null;
		return singleLineColor[poz];
	}
	
	public String getTitle() {
		
		return title;
	}
	
	public void setDrawLegend(boolean draw) {
		drawLegend = draw;
		repaint();
	}

	private double[] computeYScale(Graphics2D g2) {

		if (points == null && singlePoints == null) return null;
		synchronized (getTreeLock()) {
			boolean atLeastOnePoint = false;
			if (points != null) {
				for (int i=0; i<points.length; i++) 
					if (points[i].size() > 0) { atLeastOnePoint = true; break; }
				if (!atLeastOnePoint) {
					for (Iterator it = singlePoints.iterator(); it.hasNext(); ) {
						if (!((Double)it.next()).isNaN()) {
							atLeastOnePoint = true; break;
						}
					}
				}
			}
			if (!atLeastOnePoint && singlePoints != null) {
				for (Iterator it = singlePoints.iterator(); it.hasNext();) {
					if (!((Double)it.next()).isNaN()) {
						atLeastOnePoint = true; break;
					}
				}
			}
			if (!atLeastOnePoint)
				return null;
			
			FontMetrics fm = g2.getFontMetrics(scaleFont);
			double h = getHeight() - titleHeight - xAxisHeight;
			// from where to where the text should enter
			double dd[] = null;
			double min = this.min;
			double max = this.max;
			
			double localMin = -1.0;
			double localMax = -1.0;
			boolean found = false;
			if (points != null) {
				int start = 0;
				while (start<visible.length && !visible[start]) start++;
				if (start < visible.length) {found = true;
				if (zoomMin == -9999) {
					if (points != null && points.length > start && points[start] != null && points[start].size() > 0 && points[start].getFirst() != null) {
						localMin = ((Point2D.Double)points[start].getFirst()).getY();
						localMax = ((Point2D.Double)points[start].getFirst()).getY();
					}		
					for (int k = 0; k < points.length; k++) {
						if (!visible[k]) continue;
						for (Iterator it = points[k].iterator(); it.hasNext(); ) {
							double y = ((Point2D.Double)it.next()).getY();
							if (y < localMin)
								localMin = y;
							if (y > localMax)
								localMax = y;
						}
					}
				} else {
					localMin = zoomMin;
					localMax = zoomMax;
				}
				}
			}
			if (singlePoints != null) {
				int start = 0;
				while (start < singleVisible.length && (!singleVisible[start] || (((Double)singlePoints.get(start)).isNaN()))) start++;
				if (start < singleVisible.length) {
					found = true;
					if (zoomMin == -9999) {
						int i=0; 
						for (Iterator it = singlePoints.iterator(); it.hasNext(); i++) {
							double y = ((Double)it.next()).doubleValue();
							if (!singleVisible[i]) continue;
							if (Double.isNaN(y)) continue;
							if (y < localMin)
								localMin = y;
							if (y > localMax)
								localMax = y;
						}
					} else {
						localMin = zoomMin;
						localMax = zoomMax;
					}
				}
			}
			if (!found) return null;
			
			int nr = (int) (h / (fm.getHeight() + 6.0)) + 1;
			dd = new double[nr];
			
			boolean maxDefined = false;
			boolean minDefined = false;
			
			if (min > 0.0 && localMin < min && zoomMin == -9999)
				min = localMin;
			else if (min < 0.0)
				min = localMin;
			else
				if (zoomMin == -9999)
					minDefined = true;
			if (max >= 0.0 && localMax > max && zoomMin == -9999)
				max = localMax;
			else if (max < 0.0)
				max = localMax;
			else
				if (zoomMin == -9999)
					maxDefined = true;
			if (zoomMin != -9999) {
				max = zoomMax;
				min = zoomMin;
			}
			
			if ( Math.abs(min - max)<0.0001 ) {
				if (min < 0.0001) {
					min = 0.0;
					max = 0.5;
				} else {
					min = min - 1.0;
					max = max + 1.0;
				}
			} else {
				if (this.min < 0 && this.max < 0) {
					max += (max - min) * 0.1;
					min -= (max - min) * 0.1;
				}
			}
			if (min<0) min=0.0;
			
			int i = 0;
			for (; i < nr - 1; i++) {
				dd[i] = max - i * (max - min) / (nr - 1);
			}
			dd[nr - 1] = min;
			
			double con = 1.0;
			double tmpMin = min;
			double tmpMax = max;
			
			boolean needConversionLow = false;
			boolean needConversionUpper = false;
			boolean alreadyLower = false;
			while (true) {
				needConversionLow = false;
				for (i=1; i < nr; i++)
					if (Math.abs(dd[i]-dd[i-1]) < 0.1) {
						needConversionLow = true;
						alreadyLower = true;
						break;
					}
				if (needConversionLow) {
					for (i=0; i < nr; i++)
						dd[i] = dd[i] * 10;
					tmpMin *= 10;
					tmpMax *= 10;
					con = con / 10;
					continue;
				}
				needConversionUpper = false;
				for (i=1; i<nr; i++)
					if (Math.abs(dd[i]-dd[i-1]) > 999 || dd[i] > 999 || dd[i-1] > 999) {
						needConversionUpper = true;
						break;
					}
				if (needConversionUpper && !alreadyLower) {
					for (i=0; i<nr; i++)
						dd[i] = dd[i] / 10;
					tmpMin /= 10;
					tmpMax /= 10;
					con = con * 10;
					continue;
				}
				break;
			}
			
			double contmp = con;
			
			if (con < 1) {
				int rr = 0;
				while (con < 1) {
					con = con * 10;
					rr++;
				}
				conversion = "-"+rr;
			} else if (con > 1) {
				int rr = 0;
				while (con > 1) {
					con = con / 10;
					rr++;
				}
				conversion = ""+rr;
			} else
				conversion = "";
			
			
			int imin = (int)(tmpMin * 10);
			int imax = (int)(tmpMax * 10);
			if (nr > 1 && !minDefined && !maxDefined) {
				int tick = (int)((dd[0]-dd[1]) * 10);
				for (i=2; i<dd.length; i++)
					if ((int)((dd[i-1]-dd[i])*10) > tick) tick = (int)((dd[i-1]-dd[i])*10);
				if (tick <= 1 && (imin+(nr-1)) >= imax) {
					tick = 1;
					imax = imin + (nr-1) * tick;
					for (i=0; i<nr-1; i++)
						dd[i] = (imax - i * tick) / 10.0;
					dd[nr-1] = imin / 10.0;
				}
				else if (tick <=2 && (2 * (int)(imin / 2.0)+(nr-1)*2) >= imax) {
					tick = 2;
					if (imin % 2 != 0) imin = 2 * (imin / 2);
					imax = imin + (nr-1) * tick;
					for (i=0; i<nr-1; i++)
						dd[i] = (imax - i * tick) / 10.0;
					dd[nr - 1] = imin / 10.0;
				} else {
					i = 1;
					while (true) {
						if (tick <= (5 * i) && (tick * (int)(imin / (double)tick)+(nr-1)*(5*i)) >= imax) {
							tick = 5 * i;
							if (imin % tick != 0) imin = tick * (imin / tick);
							imax = imin + (nr-1) * tick;
							for (int j=0; j<nr-1; j++)
								dd[j] = (imax - j * tick) / 10.0;
							dd[nr-1] = imin / 10.0;
							if (i % 2 == 0 && contmp < 1) {
								con = contmp * 10;
								int rr = 0;
								while (con < 1) {
									con = con * 10;
									rr++;
								}
								if (rr != 0)
									conversion = "-"+rr;
								else
									conversion = "";
								for (int j=0; j<nr; j++) dd[j] /= 10.0;
							}
							break;
						}
						i++;
					}
				}
			}
			
			newMin = imin * contmp / 10.0;
			newMax = imax * contmp / 10.0;
			return dd;
		}
	}

	private void drawYAxisLabel(Graphics2D g2) {

		double angle = 3.0 * Math.PI / 2.0;
		String str = "";
		if (unit != null && !unit.equals("-"))
			str = /*yAxis + " [" + */unit/* + "]"*/;
		else
			str = (yAxis != null && yAxis.length() != 0) ? yAxis : "yAxis";
		g2.setFont(labelFont);
		g2.setColor(comBgColor);
		FontMetrics fm = g2.getFontMetrics();
		Rectangle2D labelBounds = fm.getStringBounds(str, g2);
		AffineTransform t =
			AffineTransform.getRotateInstance(
				angle,
				labelBounds.getCenterX(),
				labelBounds.getCenterY());
		Shape rotatedLabelBounds = t.createTransformedShape(labelBounds);
		labelBounds = rotatedLabelBounds.getBounds2D();
		double labelx = 10;
		int height = getHeight();
		double labely =
			height
				- (height - titleHeight) / 2.0
				- labelBounds.getHeight() / 2.0;
		RefineryUtilities.drawRotatedString(
				str,
				g2,
				(float) labelx,
				(float) labely,
				TextAnchor.CENTER,
				TextAnchor.CENTER,
				angle);
		yAxisLabelWidth = 20.0 + labelBounds.getWidth();
		yAxisWidth =
			yAxisLabelWidth
				+ g2.getFontMetrics(scaleFont).stringWidth(testString);
	}

	private void drawYScale(Graphics2D g2) {

		double dd[] = computeYScale(g2);
		if (dd == null) return;
		g2.setFont(scaleFont);
//		g2.setColor(Color.white);
		g2.setColor(comBgColor);
		FontMetrics fm = g2.getFontMetrics();
		double start = titleHeight + fm.getHeight() / 4.0;
		if (!conversion.equals("")) {
			String str = "[ * 10  ]";
			Font modifiedF = scaleFont.deriveFont(8); 
			FontMetrics fm1 = g2.getFontMetrics(modifiedF);
			int w = (int)(yAxisWidth - 20 - fm.stringWidth(str) - fm1.stringWidth(conversion));
			str = "[ * 10 ";
			g2.drawString(str, w, (int)start - 20);
			w += fm.stringWidth(str);
			g2.setFont(modifiedF);
			if (conversion.equals("1")) conversion = "";
			g2.drawString(conversion, w, (int)start - 23);
			g2.setFont(scaleFont);
			w += fm1.stringWidth(conversion);
			str = " ]";
			g2.drawString(str, w, (int)start - 20);
		}
		
		double np = surfaceRect.getHeight() / (dd.length - 1);
		for (int i = 0; i < dd.length; i++) {
			String str = "" + nf.format(dd[i]);
			double w = yAxisWidth - 20 - fm.stringWidth(str);
			g2.drawString(str, (int) w, (int) start);
			start += np;
		}

		g2.setColor(axisColor);
		start = titleHeight + np;
		for (int i = 1; i < dd.length - 1; i++) {
			double w = yAxisWidth - 5;
			g2.drawLine((int) w, (int) start, getWidth() - 20, (int) start);
			start += np;
		}

	}

	protected void levelZoom(Graphics2D g2) {
		
		if (zoomRect == null) return;
		synchronized (getTreeLock()) {
		int minZoomPoint = (int)zoomRect.getY();
		int maxZoomPoint = (int)(zoomRect.getY() + zoomRect.getHeight());
		FontMetrics fm = g2.getFontMetrics(scaleFont);
		double h = getHeight() - titleHeight - xAxisHeight;
		// from where to where the text should enter

		double localMin = -1.0;
		double localMax = -1.0;
		boolean found = false;
		if (points != null) {
			int start = 0;
			while (start<visible.length && !visible[start]) start++;
			if (start < visible.length) {
				found = true;
				if (zoomMin == -9999) {
					if (points.length > 0 && points[start].size() > 0) {
						localMin = ((Point2D.Double)points[start].getFirst()).getY();
						localMax = ((Point2D.Double)points[start].getFirst()).getY();
					}		
					for (int k = 0; k < points.length; k++) {
						if (!visible[k]) continue;
						for (Iterator it = points[k].iterator(); it.hasNext(); ) {
							double y = ((Point2D.Double)it.next()).getY();
							if (y < localMin)
								localMin = y;
							if (y > localMax)
								localMax = y;
						}
					}
				} else {
					localMin = zoomMin;
					localMax = zoomMax;
				}
			}
		}
		if (singlePoints != null) {
			int start = 0;
			while (start < singleVisible.length && (!singleVisible[start] || ((Double)singlePoints.get(start)).isNaN())) start++;
			if (start < singleVisible.length) {
				found = true;
				if (zoomMin == -9999) {
					int k = 0;
					for (Iterator it = singlePoints.iterator(); it.hasNext(); k++) {
						double d = ((Double)it.next()).doubleValue();
						if (!singleVisible[k]) continue;
						if (!Double.isNaN(d)) {
							if (d < localMin) localMin = d;
							if (d > localMax) localMax = d;
						}
					}
				} else {
					localMin = zoomMin;
					localMax = zoomMax;
				}
			}
		}
		if (!found) return; // no point found, so nothing can be done for zooming
		
		int nr = (int) (h / (fm.getHeight() + 6.0)) + 1;
		double np = surfaceRect.getHeight() / (nr - 1);

		if (localMin == localMax) {
			if (localMin ==  0) {
				localMin = 0.0;
				localMax = 0.5;
			} else {
				localMin = localMin - 1.0;
				localMax = localMax + 1.0;
			}
		} else {
			if (this.min < 0 && this.max < 0) {
				localMax += (localMax - localMin) * 0.1;
				localMin -= (localMax - localMin) * 0.1;
			}
		}
		if (localMin<0) localMin=0.0;

		double startt = titleHeight + fm.getHeight() / 4.0;
		
		zoomMax = (minZoomPoint - startt) * (localMin-localMax) / ((nr-2)*np) + localMax;
		zoomMin = (maxZoomPoint - startt) * (localMin-localMax) / ((nr-2)*np) + localMax;
		if (absoluteMin > 0.0 && zoomMin < absoluteMin) zoomMin = absoluteMin;
		if (absoluteMax > 0.0 && zoomMax > absoluteMax) zoomMax = absoluteMax;
		if (absoluteMin <= 0.0 && zoomMin < localMin) zoomMin = localMin;
		if (absoluteMax <= 0.0 && zoomMax > localMax) zoomMax = localMax;
		}
	}
	
	/* This method is called in order to handle the drawing of the legend */

	
	private int drawLegend(Graphics2D g2) {

		if (!drawLegend) return 0;
		
		g2.setFont(legendFont);
		FontMetrics fm = g2.getFontMetrics(legendFont);
		int h = fm.getHeight() / 2;
		int poz = 0;
		
		boolean done = false;
		if (legendItems != null)
			for (int i=0; i<legendItems.length; i++)
				if (visible[i]) {
					done = true;
					continue;
				}
		if (singleLegendItems != null)
			for (int i=0; i<singleLegendItems.length; i++)
				if (singleVisible[i]) {
					done = true;
					continue;
				}
		if (!done) return 0;

		int hLegend = h+2;

		// compute the width needed
		int w = 40;
		if (legendItems != null) {
			for (int i = 0; i < legendItems.length; i++) {
				if (!visible[i]) continue;
				w += fm.stringWidth(legendItems[i]) + hLegend + h + 1;
			}
			w += (legendItems.length - 1) * 10;
		}
		if (singleLegendItems != null) {
			for (int i=0; i<singleLegendItems.length; i++) {
				if (!singleVisible[i]) continue;
				w += fm.stringWidth(singleLegendItems[i] + hLegend + h + 1);
			}
			w += (singleLegendItems.length - 1) * 10;
		}

		// how many rows needed
		int rows = w / getWidth();

		boolean legendDone = false;
		for (int i = rows; i > -1; i--) {
			boolean jumpToSingle = true;
			w = 20;
			int ww = 0;
			if (!legendDone && legendItems != null) {
				for (int k = poz; k < legendItems.length; k++) {
					if  (!visible[k]) continue;
					int www = ww + hLegend + h + 1 + fm.stringWidth(legendItems[k]);
					if (www >= getWidth() - 40) {
						jumpToSingle = false;
						break;
					}
					ww += fm.stringWidth(legendItems[k]) + hLegend + h + 1 + 10;
				}
			}
			if (jumpToSingle && singleLegendItems != null) {
				for (int k = poz; k < singleLegendItems.length; k++) {
					if  (!singleVisible[k]) continue;
					int www = ww + hLegend + h + 1 + fm.stringWidth(singleLegendItems[k]);
					if (www >= getWidth() - 40)
						break;
					ww += fm.stringWidth(singleLegendItems[k]) + hLegend + h + 1 + 10;
				}
			}
			ww -= 10;
			w = (getWidth() - ww) / 2;

			// try to pus all on this row
			if (!legendDone && legendItems != null) {
				for (int k = poz; k < legendItems.length; k++) {
					if (!visible[k]) continue;
					int x = w;
					w += hLegend + h + 1 + fm.stringWidth(legendItems[k]);
					if (w < getWidth() - 20) { // draw the
						// legendItem
						g2.setColor(pointColor[k]);
						int y = getHeight() - 10 - (h + 10) * i;
						g2.fillRect(x, y - hLegend, hLegend, hLegend);
						g2.setColor(Color.black);
						g2.drawRect(x-1, y - hLegend-1, hLegend+1, hLegend+1);
						g2.setColor(comBgColor);
						g2.drawString(legendItems[k], x + hLegend +h+ 1, y);
					} else {
						poz = k;
						jumpToSingle = false;
						break;
					}
					w += 16;
				}
				legendDone = true;
				poz = 0; // if it comes this much it means that all normal legendItems were drawn, so start
				// enumerating again for singleLegendItems
			}
			if (jumpToSingle && singleLegendItems != null) {
				for (int k = poz; k < singleLegendItems.length; k++) {
					if (!singleVisible[k]) continue;
					int x = w;
					w += hLegend + h + 1 + fm.stringWidth(singleLegendItems[k]);
					if (w < getWidth() - 20) { 
						// draw singleLegendItem
//						g2.setColor(singleLineColor[k]);
						int y = getHeight() - 10 - (h + 10) * i;
						g2.setPaint(singleLineLegendPaint[k]);
						g2.fillRect(x, y - hLegend, hLegend+1, hLegend);
						g2.setColor(Color.black);
						g2.drawRect(x-1, y - hLegend, hLegend+2, hLegend+1);
						g2.setColor(comBgColor);
						g2.drawString(singleLegendItems[k], x + hLegend +h+ 1, y);
					} else {
						poz = k;
						break;
					}
					w += 16;
				}
			}
		}

		return (rows + 1) * (2 * h) + 20;
	}

	private void drawXAxisLabel(Graphics2D g2) {

		int legendHeight = drawLegend(g2);

		String xText = "Time [hh:mm:ss]";
		g2.setFont(labelFont);
		g2.setColor(comBgColor);
		FontMetrics fm = g2.getFontMetrics(labelFont);
		double w =
			yAxisWidth
				+ (getWidth() - 20 - yAxisWidth) / 2.0
				- fm.stringWidth(xText) / 2.0;
		g2.drawString(xText, (int) w, getHeight() - 10 - legendHeight);
		Rectangle2D bounds = fm.getStringBounds(xText, g2);
		xAxisLabelHeight = 20 + bounds.getHeight() + legendHeight;
		xAxisHeight = xAxisLabelHeight + g2.getFontMetrics(scaleFont).getHeight();
	}
	
	private void checkMinMax(int no) {
		
		synchronized (getTreeLock()) {
			if (points[no].size() == 0) return;
			long firstX = (long)((Point2D.Double)points[no].getFirst()).getX();
			if (firstX < minX) {
				minX = firstX;
				maxX = firstX + MAX_TIME_INTERVAL;
			}
		}
	}

	private void drawXScale(Graphics2D g2) {

		g2.setFont(scaleFont);
		g2.setColor(comBgColor);
		FontMetrics fm = g2.getFontMetrics();
		double start = yAxisWidth;
		double yp = getHeight() - xAxisLabelHeight + 10;
		double np = surfaceRect.getWidth() / MAX_POINTS;
        int nStep = 1;
        if ( np< MIN_X_GUIDES_STEP ) nStep = (int)(MIN_X_GUIDES_STEP/np);

		boolean done = false;
		minX = Long.MAX_VALUE;
		maxX = 0l;
		for (int i=0; i<legendItems.length; i++)
			if (visible[i]) {
				done = true;
				checkMinMax(i); 
			}
		if (!done) return;
		
		// draw the x scale values

		g2.setColor(axisColor);
		start = yAxisWidth;
		int lastXPoz = -1;
		for (int i = 0; i < MAX_POINTS; i+=nStep) {
			if ( start > yAxisWidth+surfaceRect.getWidth()) {
				break;
			}
			g2.drawLine((int) start, (int) titleHeight, (int) start, (int) (getHeight() - xAxisHeight + 5.0));
			long v = (long)(minX + (start-yAxisWidth) * (maxX - minX) / surfaceRect.getWidth());
			String str = "" + sd.format(new Date(v));
			int sw = fm.stringWidth(str);
			if (lastXPoz < 0) {
				int s = (int)(start - sw / 2);
				g2.drawString(str, s, (int) yp);
				lastXPoz = (int)(start + sw / 2);
			} else {
				int s = (int)(start - sw / 2);
				if (lastXPoz <= s-5) {
					g2.drawString(str, s, (int) yp);
					lastXPoz = (int)(start + sw / 2);
				}
			}
			start += np*nStep;
		}
	}

	/* it's better just to initialize this once */
	private final int []polyX = new int[4];
	private final int []polyY = new int[4];
	
	private void drawPoints(Graphics2D g2) {

		synchronized (getTreeLock()) {
			g2.setClip(surfaceRect);
			double np = (surfaceRect.getWidth()+20) / MAX_POINTS;
			double h = surfaceRect.getHeight();
			int lastX = -1, lastY = -1;
			
			if (componentResized) {
				reducedPoints.clear();
				componentResized = false;
			}
			
			long diffx = (maxX - minX);
			if (diffx <= 0l) diffx = 1l;
			
			polyY[2]=polyY[3]=(int)(titleHeight+h);
			// draw the points (if there are some existent)
			if (points != null)
				for (int k = 0; k < points.length; k++) {
					if (!visible[k]) continue;
					lastX = lastY = -1;
					g2.setColor(pointColor[k]);
					LinkedList l = null;
					if (legendItems != null && legendItems.length > k && reducedPoints.containsKey(legendItems[k])) l = (LinkedList)reducedPoints.get(legendItems[k]);
					if (l != null && points[k].size() == MAX_POINTS) { // it means that np < 1.0 and that we already decided on the y values
						Point2D.Double p = (Point2D.Double)l.getFirst();
						if (Math.abs(p.getX() - ((Point2D.Double)points[k].getFirst()).getX()) < 0.00001) {
							int i = 0;
							for (Iterator it = l.iterator(); it.hasNext() && i < points[k].size(); i++) {
								p = (Point2D.Double)it.next();
								int x = (int)(yAxisWidth + i); // this is the current poz...
								int y = (int)(titleHeight + h - (p.getY() - newMin) * h / (newMax - newMin));
								polyX[1]=polyX[2]=x;
								polyY[1]=y;
								if ( lastX!=-1 && lastY!=-1 ) {
									g2.fillPolygon(polyX, polyY, 4);
								};
								lastX=x;
								lastY=y;
								polyX[0]=polyX[3]=lastX;
								polyY[0]=lastY;
							}
						} else { // something changed...
							// should we move the line ?
							boolean found = false;
							int i = 0;
							int diff = 0;
							for (Iterator it = l.iterator(); it.hasNext() && i < points[k].size(); i++) {
								p = (Point2D.Double)it.next();
								int x = (int)(yAxisWidth + i);
								for (Iterator it1 = points[k].iterator(); it1.hasNext(); ) {
									double px = ((Point2D.Double)it1.next()).getX();
									if (Math.abs(px - p.getX()) < 0.00001) { // found the point
										int newx = (int)(yAxisWidth + (px - minX) * surfaceRect.getWidth() / diffx);
										found = true;
										diff = x-newx;
										break;
									}
								}
								if (found) break;
							}
							for (i=0; i<diff; i++)l.removeFirst();
							int w = (int)surfaceRect.getWidth();
							for (int j=w-diff-1; j < w-1; j++) {
								for (Iterator it = points[k].iterator(); it.hasNext(); ) {
									Point2D.Double pp = (Point2D.Double)it.next(); 
									int x = (int)((pp.getX() - minX) * surfaceRect.getWidth() / diffx);
									if (x == j) {
										l.addLast(new Point2D.Double(pp.getX(), pp.getY()));
										break;
									}
								}
							}
							i = 0;
							for (Iterator it = l.iterator(); it.hasNext() && i < points[k].size(); i++) {
								p = (Point2D.Double)it.next();
								int x = (int)(yAxisWidth + i); // this is the current poz...
								int y = (int)(titleHeight + h - (p.getY() - newMin) * h / (newMax - newMin));
								polyX[1]=polyX[2]=x;
								polyY[1]=y;
								if ( lastX!=-1 && lastY!=-1 ) {
									g2.fillPolygon(polyX, polyY, 4);
								};
								lastX=x;
								lastY=y;
								polyX[0]=polyX[3]=lastX;
								polyY[0]=lastY;
							}
						}
					} else {
						//draw all points but last with smaller circles
						for (Iterator it = points[k].iterator(); it.hasNext(); ) {
							Point2D.Double pp = (Point2D.Double)it.next(); 
							// draw point normally
							int x = (int)(yAxisWidth + (pp.getX() - minX) * surfaceRect.getWidth() / diffx);
							if (x == lastX) continue;
							if (np < 1.0 && points[k].size() == MAX_POINTS) {
								if (l == null) {
									l = new LinkedList();
									reducedPoints.put(legendItems[k], l);
								}
								l.addLast(new Point2D.Double(pp.getX(), pp.getY()));
							}
							int y = (int)(titleHeight + h - (pp.getY() - newMin) * h / (newMax - newMin));
							polyX[1]=polyX[2]=x;
							polyY[1]=y;
							if ( lastX!=-1 && lastY!=-1 ) {
								g2.fillPolygon(polyX, polyY, 4);
							};
							lastX=x;
							lastY=y;
							polyX[0]=polyX[3]=lastX;
							polyY[0]=lastY;
						}
					}
				}
			// now draw the singlePoints (series of single points)
			if (singlePoints != null) {
				int k = 0;
				for (Iterator it = singlePoints.iterator(); it.hasNext(); k++) {
					double y = ((Double)it.next()).doubleValue();
					if (!singleVisible[k]) {
						continue; // the series is not visible, jump to the next one
					}
					if (Double.isNaN(y)) {
						continue; // the series is not initialized, the default NaN value was found
					}
					// else the series is ok, so let's draw it
					y = (int)(titleHeight + h - (y - newMin) * h / (newMax - newMin));
					g2.setStroke(dashed);//singleStroke);
					g2.setColor(singleLineColor[k]);
					g2.drawLine((int)yAxisWidth, (int)y, (int)(yAxisWidth + surfaceRect.getWidth()), (int)y);
				}
			}
		}
	}

	/** Method called in order to draw a tooltip for the current selected point */
	private void drawMouseCursorPozition(Graphics2D g2, Rectangle clipArea) {

		if ( mouseEvent == null )
			return;
		int mX = mouseEvent.getX();
		int mY = mouseEvent.getY();
		if (mouseCursor == null) {
			if (singleMouseCursor != null) {
				g2.setFont(scaleFont);
				StringBuilder buf = new StringBuilder();
				buf.append("[");
				if ( "bytes/s".equals(unit) ) {
					buf.append(StatisticsMonitor.valToString(singleMouseCursor.doubleValue(), StatisticsMonitor.VALUE_2_STRING_UNIT));
					buf.append("B/s");
				} else {
					buf.append(nf1.format(singleMouseCursor.doubleValue()));
					if (unit != null && unit.length() != 0)
						buf.append(" ").append(unit);
				}
				buf.append("]");
				String sResult = buf.toString();
				g2.setColor(Color.white);
				FontMetrics fm = g2.getFontMetrics();
				int wText = fm.stringWidth(sResult);
				if ( wText+mX+3>clipArea.x+clipArea.width )
					mX = (int)(clipArea.x+clipArea.width-wText-3);
				if ( mX<clipArea.x )
					mX = clipArea.x;
				g2.drawString(sResult, mX+2, mY-5);
				g2.drawString(sResult, mX+4, mY-5);
				g2.drawString(sResult, mX+3, mY-6);
				g2.drawString(sResult, mX+3, mY-4);
				g2.setColor(comBgColor);
				g2.drawString(sResult, mX+3, mY-5);
			} 
			return;
		}
		g2.setFont(scaleFont);
		StringBuilder buf = new StringBuilder();
		buf.append("[");
		if ( "bytes/s".equals(unit) ) {
			buf.append(StatisticsMonitor.valToString(mouseCursor.getY(), StatisticsMonitor.VALUE_2_STRING_UNIT));
			buf.append("B/s");
		} else {
			buf.append(nf1.format(mouseCursor.getY()));
			if (unit != null && unit.length() != 0)
				buf.append(" ").append(unit);
		}
		buf.append(" at ");
		buf.append(sd.format(new Date((long) mouseCursor.getX())));
		buf.append("]");
		String sResult = buf.toString();
		g2.setColor(Color.white);
		FontMetrics fm = g2.getFontMetrics();
		int wText = fm.stringWidth(sResult);
		if ( wText+mX+3>clipArea.x+clipArea.width )
			mX = (int)(clipArea.x+clipArea.width-wText-3);
		if ( mX<clipArea.x )
			mX = clipArea.x;
		g2.drawString(sResult, mX+2, mY-5);
		g2.drawString(sResult, mX+4, mY-5);
		g2.drawString(sResult, mX+3, mY-6);
		g2.drawString(sResult, mX+3, mY-4);
		g2.setColor(comBgColor);
		g2.drawString(sResult, mX+3, mY-5);
	}
	
	/** Method inherited from JPanel that is called on repaint */

	public void paintComponent(Graphics g) {

		Graphics2D g2 = (Graphics2D) g;
		int width = getWidth();
		int height = getHeight();

		g2.setClip(0, 0, width, height);
		g2.setColor(bgColor);
		g2.fillRect(0, 0, width, height);

		//		drawProgress(g2);

		// draw the title
		g2.setFont(titleFont);
		g2.setColor(comBgColor);
		FontMetrics fm = g2.getFontMetrics();
		int w = fm.stringWidth(title);
		int h = fm.getHeight();
		g2.drawString(title, width / 2 - w / 2, (int) (10.0 + fm.getHeight() / 2.0));
		titleHeight = 20 + h;

		// draw the axes
		drawYAxisLabel(g2);
		drawXAxisLabel(g2);
		surfaceRect = new Rectangle( (int) yAxisWidth, (int) titleHeight,
				(int) (getWidth() - yAxisWidth - 20.0), (int) (getHeight() - xAxisHeight - titleHeight));
		if (zoomRect != null) {
			levelZoom(g2);
			zoomRect = null;
		}
		drawYScale(g2);
		drawXScale(g2);
		g2.setColor(axisColor);
		g2.drawRect(
			(int) surfaceRect.getX(),
			(int) surfaceRect.getY(),
			(int) surfaceRect.getWidth(),
			(int) surfaceRect.getHeight());
		g2.drawLine(
			(int) (surfaceRect.getX() - 5.0),
			(int) surfaceRect.getY(),
			(int) surfaceRect.getX(),
			(int) surfaceRect.getY());
		g2.drawLine(
			(int) (surfaceRect.getX() - 5.0),
			(int) (surfaceRect.getY() + surfaceRect.getHeight()),
			(int) surfaceRect.getX(),
			(int) (surfaceRect.getY() + surfaceRect.getHeight()));

		// draw the points
		drawPoints(g2);

		// draw mouse cursor
		drawMouseCursorPozition(g2,surfaceRect);
		
		g2.setClip(0, 0, width, height);
		
		if (rect != null) {
			g2.setColor(comBgColor);
			g2.setStroke(dashed);
			g2.drawRect((int)rect.getX(), (int)rect.getY(), (int)rect.getWidth(), (int)rect.getHeight());
		}
	}
	
	
	/* Method that can be use to show/hide a series */
	public void setVisible(int i, boolean state) {
		synchronized (getTreeLock()) {
			if (visible == null || i<0 || i>= visible.length) return;
			visible[i] = state;
			repaint();
		}
	}

	/* Method that can be use to show/hide a single points series */
	public void setSingleVisible(int i, boolean state) {
		synchronized (getTreeLock()) {
			if (singleVisible == null || i < 0 || i >= singleVisible.length) return;
			singleVisible[i] = state;
			repaint();
		}
	}
	
	/** method called on a mousedragged event */
//	protected void finalize() {
//		thread.stopIt();
//		thread = null;
//	}

	public void mouseDragged(MouseEvent e) {

		try {
			checkMouse(e);
		} catch (Exception ex) {
		}
		if (startPoint != null && surfaceRect != null) {
			int y1 = (int)startPoint.getY();
			int y2 = (int)e.getPoint().getY();
			int x = (int)surfaceRect.getX();
			int y = (y1 < y2) ? y1 : y2;
			if (y < surfaceRect.getY()) y = (int)surfaceRect.getY();
			int w = (int)surfaceRect.getWidth();
			int h = Math.abs(y1-y2);
			if ((y+h) > (surfaceRect.getY()+surfaceRect.getHeight())) h = (int)(surfaceRect.getY()+surfaceRect.getHeight()-y);
			rect = new Rectangle(x, y, w, h);
			repaint();
		}
	}
	

	private Object checkMouseIntersect(MouseEvent e) throws Exception {

		synchronized (getTreeLock()) {
			double h = surfaceRect.getHeight();
			long diffx = maxX - minX;
			if (diffx <= 0) diffx = 1;
			if (points != null)
				for (int k = 0; k < points.length; k++) {
					if (!visible[k]) continue;
					for (Iterator it = points[k].iterator(); it.hasNext(); ) {
						Point2D.Double p = (Point2D.Double)it.next(); 
						// compute point coordinates
						double x = yAxisWidth + (p.getX() - minX) * surfaceRect.getWidth() / diffx;
						double y = titleHeight + h - (p.getY() - newMin) * h / (newMax - newMin);
						if (e.getX() < (x + 3) && e.getX() > (x - 3) && e.getY() < (y + 3) && e.getY() > (y - 3))
							return p;
					}
				}
			if (singlePoints != null) {
				int k = 0;
				for (Iterator it = singlePoints.iterator(); it.hasNext(); k++) {
					double y = ((Double)it.next()).doubleValue();
					if (!singleVisible[k]) continue;
					if (Double.isNaN(y)) continue;
					y = titleHeight + h - (y - newMin) * h / (newMax - newMin);
					if (e.getY() < (y + 3) && e.getY() > (y - 3))
						return Double.valueOf(y);
				}
			}
			return null;
		}
	}

	/* Utility method that is used to check if the current mouse cursor intersect any drawn points */
	private void checkMouse(MouseEvent e) throws Exception {

		if (e == null) // this should never happen, but still....
			return;
		Object point = checkMouseIntersect(e);
		mouseCursor = null;
		singleMouseCursor = null;
		mouseEvent = null;
		if (point == null) {
			setCursor(Cursor.getDefaultCursor());
		} else {
			setCursor(moveCursor);
			if (point instanceof Point2D.Double) {
				mouseCursor = (Point2D.Double)point;
				mouseEvent = e;
			} else if (point instanceof Double) {
				singleMouseCursor = (Double)point;
				mouseEvent = e;
			}
		}
		repaint();
	}

	public void zoom(Rectangle rect) {
		
		zoomRect = rect;
		repaint();
	}
	
	public void zoomOut() {
	
		if (surfaceRect == null) return;
		int y0 = (int)surfaceRect.getY();
		int h = (int)surfaceRect.getHeight();
		y0 = (int)(y0 + h * 0.3);
		h = (int)(h - 2 * h * 0.3);
		zoomRect = new Rectangle((int)surfaceRect.getX(), y0, (int)surfaceRect.getWidth(), h);
		repaint();
	}
	
	public void zoomIn() {
		
		if (surfaceRect == null) return;
		int y0 = (int)surfaceRect.getY();
		int h = (int)surfaceRect.getHeight();
		y0 = (int)(y0 - h * 0.3);
		h = (int)(h + 2 * h * 0.3);
		zoomRect = new Rectangle((int)surfaceRect.getX(), y0, (int)surfaceRect.getWidth(), h);
		repaint();
	}

	
	public void mouseMoved(MouseEvent e) {

		try {
			checkMouse(e);
		} catch (Exception ex) {
		}
	}

	public void mouseClicked(MouseEvent e) {
	}

	public void mouseEntered(MouseEvent e) {
	}

	public void mouseExited(MouseEvent e) {
	}

	public void mousePressed(MouseEvent e) {
		if ( surfaceRect.contains(e.getPoint()) )
			startPoint = e.getPoint();
	}

	public void mouseReleased(MouseEvent e) {

		Point endPoint = e.getPoint();
		if ( !endPoint.equals(startPoint) )
			zoom(rect);
		startPoint = null;
		rect = null;
		repaint();
	}

	public static void main(String args[]) {

		JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		MultiGraphPanel panel =
			new MultiGraphPanel(
				"Test",
				new String[] { "tttttttt", "zzzzzzz"},
                new Color[] {new Color(123, 45, 67, 160), new Color(98, 99, 160, 160)},
                new String[] { "min", "max" }, null,
				"T",
				"ms", 20 * 1000);

		frame.getContentPane().setLayout(new BorderLayout());
		frame.getContentPane().add(panel, BorderLayout.CENTER);
		frame.setSize(300, 300);
		panel.setPreferredSize(new Dimension(300, 300));
		frame.pack();
		frame.setVisible(true);

		Random random = new Random();
		
//		for (int i=0; i<MAX_POINTS; i++)
//			panel.newValue(0, System.currentTimeMillis(), random.nextInt(10));
		panel.setVisible(0, true);
		panel.setVisible(1, true);
		int k=0;
		for (int i=0; i<10; i++) {
//		while (true) {
			if ( k ==  9 ) {
				panel.newValue(1, System.currentTimeMillis(), random.nextInt(10));
				panel.newValue(0, System.currentTimeMillis(), random.nextInt(10));
			} else
				panel.newValue(0, System.currentTimeMillis(), random.nextInt(10));
			panel.newSingleValue(0, k);
			panel.newSingleValue(1, 10 * k);
			k = (k+1)%10;
			try {
				Thread.sleep(200);
			} catch (Exception e) {
			}
		}
	}
	
	public void actionPerformed(ActionEvent e) {

		if (e.getSource().equals(zoomIn)) {
			zoomIn();
			return;
		}
		if (e.getSource().equals(zoomOut)) {
			zoomOut();
			return;
		} 
		if (e.getSource().equals(autoRange)) {
			zoomRect = null;
			zoomMin = -9999;
			zoomMax = 9999;
			repaint();
			return;
		}
		if (e.getSource().equals(downUp)) {
			if (isUp) {
				menuDown();
				isUp = false;
			} else {
				menuUp();
				isUp = true;
			}
			return;
		}
		if (e.getSource().equals(bgColorButton)) {
			Color newColor = JColorChooser.showDialog(
                    this,
                    "Choose Background Color",
                    bgColor);
			if (newColor != null) {
				bgColor = newColor;
				comBgColor = getComplementColor(newColor);
				axisColor = checkAxisColor(newColor);
				if (pointColor != null)
					for (int i=0; i<pointColor.length; i++)
						pointColor[i] = checkPointColor(colorTable[i], newColor);
				if (lineColor != null)
					for (int i=0; i<lineColor.length; i++) {
						int r1 = colorTable[i].getRed();
						int g1 = colorTable[i].getGreen();
						int b1 = colorTable[i].getBlue();
						r1 = 2 * r1 / 3;
						g1 = 2 * g1 / 3;
						b1 = 2 * b1 / 3;
						lineColor[i] = checkLineColor(new Color(r1, g1, b1), newColor);
					}
//				if (parent != null)
//					parent.redoColors();
				repaint();
			}
			return;
		}
	}
	
	public void setYAxisLabel(String yAxisLabel) {
		
		this.yAxis = yAxisLabel;
//		if (history != null)
//			history.setYAxisLabel(yAxisLabel);
	}

    public ImageIcon loadIcon(String resource) {
        ImageIcon ico = null;
        ClassLoader myClassLoader = getClass().getClassLoader();

        try {
            URL resLoc = myClassLoader.getResource(resource);
            ico = new ImageIcon(resLoc);
        } catch (Throwable t) {
            System.out.println("Failed to get image ..."+resource);
        }
        return ico;
    }
	
	protected Icon getBgColorIcon() {
		return loadIcon("lia/images/plot/bgColor.gif");
	}

	protected Icon getZoomIn() {
        return loadIcon("lia/images/plot/MagnifyMinus.gif");
	}
	
	protected Icon getZoomOut() {
        return loadIcon("lia/images/plot/MagnifyPlus.gif");
	}
	
	protected Icon getHistoryIcon() {
        return loadIcon("lia/images/plot/Begin.gif");
	}
	
	protected Icon getFitIcon() {
        return loadIcon("lia/images/plot/Magnify.gif");
	}

	protected Icon getUpIcon() {
        return loadIcon("lia/images/plot/RotCCUp.gif");
	}
	
	protected Icon getDownIcon() {
        return loadIcon("lia/images/plot/RotCCDown.gif");
	}

	public void componentHidden(ComponentEvent e) {
	}

	public void componentMoved(ComponentEvent e) {
	}

	public void componentResized(ComponentEvent e) {
		componentResized = true;
	}

	public void componentShown(ComponentEvent e) {
	}

} // end of class GraphPanel
