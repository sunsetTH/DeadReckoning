package com.astar.i2r.ins.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JToolTip;
import javax.swing.SwingWorker;

import org.apache.log4j.Logger;
import org.jxmapviewer.JXMapKit;
import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.painter.CompoundPainter;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.jxmapviewer.viewer.WaypointPainter;

import com.astar.i2r.ins.motion.GeoPoint;
import com.astar.i2r.ins.motion.Step;

/**
 * A simple sample application that uses JXMapKit
 * 
 * @author Martin Steiger
 */
public class JxMap extends JFrame implements Runnable {

	/**
	 * 
	 */
	private static final Logger log = Logger.getLogger(JxMap.class.getName());
	private static final long serialVersionUID = 1L;
	private final JXMapKit jXMapKit = new JXMapKit();
	private final static String imagePath = "map/";
	private final BlockingQueue<GeoPoint> GPSQ;
	private final BlockingQueue<GeoPoint> DRQ;
	private String curImageStr = null;
	private MyLabel picLabel = new MyLabel();
	private WaypointPainter<ColoredWeightedWaypoint> waypointPainter = null;
	private Set<ColoredWeightedWaypoint> waypoints = new HashSet<ColoredWeightedWaypoint>();

	private GeoPoint lastWP = null;

	public JxMap(BlockingQueue<GeoPoint> _GPSQ, BlockingQueue<GeoPoint> _DRQ) {

		GPSQ = _GPSQ;
		DRQ = _DRQ;

		TileFactoryInfo info = new OSMTileFactoryInfo();
		DefaultTileFactory tileFactory = new DefaultTileFactory(info);
		jXMapKit.setTileFactory(tileFactory);

		final JToolTip tooltip = new JToolTip();
		tooltip.setTipText("Java");
		tooltip.setComponent(jXMapKit.getMainMap());
		jXMapKit.getMainMap().add(tooltip);

		jXMapKit.setZoom(1);

		// location of Java
		final GeoPosition gp = new GeoPosition(1.299643, 103.787948);
		jXMapKit.setAddressLocation(gp);

		jXMapKit.getMainMap().addMouseMotionListener(new MouseMotionListener() {
			public void mouseDragged(MouseEvent e) {
				// ignore
			}

			public void mouseMoved(MouseEvent e) {
				JXMapViewer map = jXMapKit.getMainMap();

				// convert to world bitmap
				Point2D worldPos = map.getTileFactory().geoToPixel(gp,
						map.getZoom());

				// convert to screen
				Rectangle rect = map.getViewportBounds();
				int sx = (int) worldPos.getX() - rect.x;
				int sy = (int) worldPos.getY() - rect.y;
				Point screenPos = new Point(sx, sy);

				// check if near the mouse
				if (screenPos.distance(e.getPoint()) < 20) {
					screenPos.x -= tooltip.getWidth() / 2;

					tooltip.setLocation(screenPos);
					tooltip.setVisible(true);
				} else {
					tooltip.setVisible(false);
				}
			}
		});

		waypointPainter = new WaypointPainter<ColoredWeightedWaypoint>();
		waypointPainter.setRenderer(new ColoredWeightedWaypointRenderer());

		// There can be many painter for a map
		List<Painter<JXMapViewer>> painters = new ArrayList<Painter<JXMapViewer>>();
		painters.add(waypointPainter);

		CompoundPainter<JXMapViewer> painter = new CompoundPainter<JXMapViewer>(
				painters);

		jXMapKit.getMainMap().setOverlayPainter(painter);

		// Display the viewer in a JFrame

		setTitle("JXMapviewer2 Example 6");
		getContentPane().add(jXMapKit);
		setSize(800, 600);
		// setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		setVisible(true);

		SwingWorker GPSworker = new SwingWorker<Void, ColoredWeightedWaypoint>() {
			@Override
			public Void doInBackground() {
				GeoPoint coordinate = null;

				while (true) {
					try {
						coordinate = GPSQ.take();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					if (coordinate != null) {
						if (lastWP == null) {
							lastWP = coordinate;
							publish(new ColoredParticle(coordinate));
						} else {
							if (GeoPoint.distance(lastWP, coordinate).getNorm() > Step.MINSTEP * 10) {
								publish(new ColoredParticle(coordinate));
								lastWP = coordinate;
							}
						}
					}
				}
			}

			@Override
			protected void process(List<ColoredWeightedWaypoint> wps) {
				for (ColoredWeightedWaypoint wp : wps) {
					waypoints.add(wp);
					System.out.println(waypoints.size() + "\t"
							+ wp.getPosition().toString());
				}
				setWaypoints(waypoints);
			}
		};

		SwingWorker DRworker = new SwingWorker<Void, ColoredWeightedWaypoint>() {
			@Override
			public Void doInBackground() {
				GeoPoint coordinate = null;

				while (true) {
					try {
						coordinate = DRQ.take();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					if (coordinate != null) {
						if (lastWP == null) {
							lastWP = coordinate;
							publish(new ColoredParticle(coordinate.lat,
									coordinate.lon,
									ColoredParticle.DEFAULTWEIGHT, Color.BLUE));
						} else {
							if (GeoPoint.distance(lastWP, coordinate).getNorm() > Step.MINSTEP * 10) {
								publish(new ColoredParticle(coordinate.lat,
										coordinate.lon,
										ColoredParticle.DEFAULTWEIGHT,
										Color.BLUE));
								lastWP = coordinate;
							}
						}
					}
				}
			}

			@Override
			protected void process(List<ColoredWeightedWaypoint> wps) {
				for (ColoredWeightedWaypoint wp : wps) {
					waypoints.add(wp);
					System.out.println(waypoints.size() + "\t"
							+ wp.getPosition().toString());
				}
				setWaypoints(waypoints);
			}
		};

		GPSworker.execute();
		DRworker.execute();

	}

	public void setWaypoints(Set<ColoredWeightedWaypoint> _waypoints) {
		waypointPainter.setWaypoints(_waypoints);
		jXMapKit.repaint();
	}

	public void setWaypoints() {
		setWaypoints(new HashSet<ColoredWeightedWaypoint>());
		jXMapKit.repaint();
	}

	public void run() {

	}

	public void setAddressLocation(double lat, double lon) {
		if (getContentPane().isAncestorOf(jXMapKit)) {
			// picLabel.clearMarker();
			jXMapKit.setAddressLocation(new GeoPosition(lat, lon));
		} else if (getContentPane().isAncestorOf(picLabel)) {
			picLabel.setMarker(lat, lon);
		}
	}

	public void dispImage(String file, double[] _s, double[] _r) {
		if (curImageStr == null) {
			curImageStr = file;
		} else if (curImageStr.compareTo(file) == 0) {
			return;
		}

		curImageStr = file;
		if (getContentPane().isAncestorOf(jXMapKit)) {
			getContentPane().remove(jXMapKit);
		}
		if (!getContentPane().isAncestorOf(picLabel)) {
			getContentPane().add(picLabel);
		}
		picLabel.setImage(imagePath + curImageStr, _s, _r);

	}

	public void hideImage() {
		curImageStr = null;

		if (getContentPane().isAncestorOf(picLabel)) {
			getContentPane().remove(picLabel);
		}
		if (!getContentPane().isAncestorOf(jXMapKit)) {
			getContentPane().add(jXMapKit);
		}
	}

}

class MyLabel extends JLabel {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private double[] scale = { 3111798, 3112660 };
	private double[] reference = { 1.3004187, 103.7864650 };

	List<Point> marker = new LinkedList<Point>();

	Dimension iconSize = null;
	Point iconOfst = null;

	double iconRatio = Double.NaN;

	private double graphicTranslateX = 0;
	private double graphicTranslateY = 0;
	private double graphicScale = 1;
	ImageIcon icon = null;

	public MyLabel() {
		Mouse m = new Mouse();

		this.addMouseListener(m);
		this.addMouseMotionListener(m);
		this.addMouseWheelListener(m);
		this.addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
				iconSize = null;
				iconOfst = null;
				iconRatio = Double.NaN;
			}
		});
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);

		AffineTransform tx = new AffineTransform();
		tx.translate(graphicTranslateX, graphicTranslateY);
		tx.scale(graphicScale, graphicScale);

		Graphics2D g2 = (Graphics2D) g;
		g2.setTransform(tx);

		if (iconSize == null || iconOfst == null || Double.isNaN(iconRatio)) {
			int sw = icon.getIconWidth();
			int sh = icon.getIconHeight();
			int dw = getWidth();
			int dh = getHeight();

			iconRatio = calcRatio(sw, sh, dw, dh);

			int imageW = (int) (sw * iconRatio);
			int imageH = (int) (sh * iconRatio);
			int imageX = (dw - imageW) / 2;
			int imageY = (dh - imageH) / 2;

			iconSize = new Dimension(imageW, imageH);
			iconOfst = new Point(imageX, imageY);
		}

		g2.clearRect(0, 0, getWidth(), getHeight());
		g2.drawImage(icon.getImage(), iconOfst.x, iconOfst.y,
				(int) iconSize.getWidth(), (int) iconSize.getHeight(), null);

		if (marker.size() > 0) {
			g2.setColor(Color.RED);
			// g2.fillOval(x + 378, y + 187, 20, 20);
			for (Point p : marker) {
				g2.fillOval(iconOfst.x + (int) (p.x * iconRatio), iconOfst.y
						+ (int) (p.y * iconRatio), 10, 10);
			}
		}
	}

	private double calcRatio(double sw, double sh, double dw, double dh) {
		double sr = sw / sh;
		double dr = dw / dh;
		if (sr > dr) {
			return dw / sw;
		} else {
			return dh / sh;
		}
	}

	public void setImage(String filename, double[] _s, double[] _r) {
		icon = new ImageIcon(filename);
		setIcon(icon);
		iconSize = null;
		iconOfst = null;
		iconRatio = Double.NaN;
		scale = _s;
		reference = _r;
	}

	public void setMarker(double lat, double lon) {
		Point p = new Point((int) ((lon - reference[1]) * scale[0]),
				(int) ((reference[0] - lat) * scale[1]));
		marker.add(p);
		repaint();
	}

	public void clearMarker() {
		marker.clear();
	}

	class Mouse implements MouseListener, MouseMotionListener,
			MouseWheelListener {

		private int lastOffsetX;
		private int lastOffsetY;

		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL) {

				// make it a reasonable amount of zoom
				// .1 gives a nice slow transition
				double scaleChange = (.1 * e.getWheelRotation());
				graphicScale -= scaleChange;
				// don't cross negative threshold.
				// also, setting scale to 0 has bad effects
				graphicScale = Math.max(0.00001, graphicScale);

				graphicTranslateX += e.getX() * scaleChange;
				graphicTranslateY += e.getY() * scaleChange;

				repaint();
			}
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			// new x and y are defined by current mouse location subtracted
			// by previously processed mouse location
			int newX = e.getX() - lastOffsetX;
			int newY = e.getY() - lastOffsetY;

			// increment last offset to last processed by drag event.
			lastOffsetX += newX;
			lastOffsetY += newY;

			// update the canvas locations
			graphicTranslateX += newX;
			graphicTranslateY += newY;

			// schedule a repaint.
			repaint();
		}

		@Override
		public void mouseMoved(MouseEvent e) {
		}

		@Override
		public void mouseClicked(MouseEvent e) {
		}

		@Override
		public void mouseEntered(MouseEvent e) {
		}

		@Override
		public void mouseExited(MouseEvent e) {
		}

		@Override
		public void mouseReleased(MouseEvent e) {
		}

		@Override
		public void mousePressed(MouseEvent e) {
			// capture starting point
			lastOffsetX = e.getX();
			lastOffsetY = e.getY();
		}

	}

}