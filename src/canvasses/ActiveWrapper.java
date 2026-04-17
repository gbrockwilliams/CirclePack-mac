package canvasses;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import circlePack.PackControl;
import complex.Complex;
import handlers.ACTIVEHandler;
import input.TrafficCenter;
import packing.CPdrawing;
import script.ScriptManager;
import util.PopupBuilder;
import util.SphView;

/**
 * Simple wrapper for canvases displaying circle packings; catch key,
 * mouse, and mouse motion events. The 'ActiveWrapper' is used in the 
 * 'MainPanel'; derived class, 'PairWrapper' (which override most
 * mouse events) is used for side-by-side window mode.
 * @author kens
 *
 */
//AF>>>//
// Implement MouseWheelListener for mouse wheel zooming.
public class ActiveWrapper extends JPanel implements KeyListener, 
	MouseListener, MouseMotionListener, MouseWheelListener {
//<<<AF//

	private static final long serialVersionUID = 1L;
	
	protected CPdrawing cpDrawing;
	ACTIVEHandler activeHandler;
	
	// 'MyCanvasMode's effect cursor and mouse operations.
	public static MyCanvasMode defaultMode;
	public MyCanvasMode activeMode;
	
	public PopupBuilder button3Popup;

	// Constructor
	public ActiveWrapper(File mainMytFile,CPdrawing cpd) {
		super();
		cpDrawing=cpd;
		setFocusable(true);
		addMouseListener(this);
		addMouseMotionListener(this);
		//AF>>>//
		addMouseWheelListener(this);
		//<<<AF//
		addKeyListener(this);
		
		// set up a tool handler
		button3Popup=null;
		activeHandler = new ACTIVEHandler(mainMytFile,this);
		defaultMode = CursorCtrl.defaultMode;
		activeMode=defaultMode;

		registerPinchHandler();
	}

	// Wire macOS trackpad pinch-to-zoom via com.apple.eawt.event.GestureUtilities.
	// Done via reflection so the code compiles and runs on non-Mac platforms unchanged.
	private void registerPinchHandler() {
		try {
			Class<?> gestureUtils    = Class.forName("com.apple.eawt.event.GestureUtilities");
			Class<?> magnListenerCls = Class.forName("com.apple.eawt.event.MagnificationListener");
			Class<?> gestureListenerCls = Class.forName("com.apple.eawt.event.GestureListener");

			Object listener = Proxy.newProxyInstance(
				getClass().getClassLoader(),
				new Class<?>[] { magnListenerCls },
				(proxy, method, args) -> {
					if ("magnify".equals(method.getName()) && args != null && args.length > 0) {
						Object event = args[0];
						double magnification = (Double) event.getClass().getMethod("getMagnification").invoke(event);
						// MagnificationEvent has no position info, so read the pointer
						// location from the OS and convert to component coordinates.
						java.awt.Point screenPt = java.awt.MouseInfo.getPointerInfo().getLocation();
						SwingUtilities.convertPointFromScreen(screenPt, ActiveWrapper.this);
						final Point anchor = screenPt;
						SwingUtilities.invokeLater(() -> applyPinchZoom(magnification, anchor));
					}
					return null;
				}
			);

			Method addListener = gestureUtils.getMethod(
				"addGestureListenerTo", javax.swing.JComponent.class, gestureListenerCls);
			addListener.setAccessible(true);
			addListener.invoke(null, this, listener);
		} catch (Exception e) {
			System.err.println("Pinch-to-zoom unavailable: " + e.getClass().getSimpleName()
				+ " — run via ./run.sh to enable it");
		}
	}

	private void applyPinchZoom(double magnification, Point anchor) {
		// magnification > 0: fingers spreading (zoom in), < 0: fingers converging (zoom out).
		// factor < 1 = zoom in, factor > 1 = zoom out (ViewBox convention).
		double factor = 1.0 / (1.0 + magnification);
		if (anchor != null) {
			// Shift the viewport center toward the pinch point before scaling,
			// so the gesture anchor stays fixed on screen (same math as scroll-wheel zoom).
			Point2D.Double realPt = cpDrawing.pt2RealPt(anchor, getWidth(), getHeight());
			Complex pinchCenter = new Complex(realPt.x, realPt.y);
			Complex viewCenter  = cpDrawing.realBox.lz.add(cpDrawing.realBox.rz).divide(2.0);
			cpDrawing.realBox.transView(pinchCenter.minus(viewCenter).mult(1.0 - factor));
		}
		try {
			cpDrawing.realBox.scaleView(factor);
			cpDrawing.update(2);
			TrafficCenter.cmdGUI(cpDrawing.getPackData(), "disp -wr");
		} catch (Exception ex) { return; }
		repaint();
	}
	
	public void setCPDrawing(CPdrawing cpd) {
		cpDrawing=cpd;
	}
	
	public CPdrawing getCPDrawing() {
		return cpDrawing;
	}
	
	public ACTIVEHandler getToolHandler() {
		return activeHandler;
	}
	
	//AF>>>//
	// Changed zoomOut and zoomIn to allow zooming by specified value.
	// Calling the old versions (no arguments) will zoom by the old
	// default amounts.
	public void zoomOut() {
		zoomOut(2.0D);
	}

	public void zoomIn() {
		zoomIn(0.5D);
	}
	
	public void zoomOut(double zoomOutMultiplier) {
		try {
			cpDrawing.realBox.scaleView(zoomOutMultiplier);
			cpDrawing.update(2);
			TrafficCenter.cmdGUI(cpDrawing.getPackData(),"disp -wr");
		} catch (Exception ex) {return;}
		repaint();
	}

	public void zoomIn(double zoomInMultiplier) {
		try {
			cpDrawing.realBox.scaleView(zoomInMultiplier);
			cpDrawing.update(2);
			TrafficCenter.cmdGUI(cpDrawing.getPackData(),"disp -wr");
		} catch (Exception ex) {return;}
		repaint();
	}
	//<<<AF//
	
	public void setDefaultMode() {
		activeHandler.setCanvasMode(CursorCtrl.defaultMode);
	}
	
	// must have these keyListener methods
	public void keyReleased(KeyEvent e) {} 
	    // cuation: keyPressed desirable, since extraneous keyReleases might be caught. 
	public void keyPressed(KeyEvent e) {
		if (!(e.getComponent() instanceof ActiveWrapper)) // for correct window?
			return;
		char c = e.getKeyChar();
		ScriptManager mgr = PackControl.scriptManager;
		if (mgr.isScriptLoaded()) {
			if (c==KeyEvent.VK_ENTER) mgr.executeNextCmd(); // c == e.VK_ENTER, execute next
			else {
				String key=String.valueOf(c);
				mgr.executeCmdByKey(e,key); // use first character only
			}
		}
	}
	public void keyTyped(KeyEvent e) {}
	
	// Methods required for MouseListener/MouseMotionListener
	public void mouseClicked(MouseEvent e) {
		if (e.getButton() == MouseEvent.BUTTON2 ||
				(e.getButton() == MouseEvent.BUTTON1 && 
						(e.getModifiersEx() & ActionEvent.CTRL_MASK)==
						ActionEvent.CTRL_MASK)) {
			activeMode.clicked2(this,e);
		}
		else if (e.getButton() == MouseEvent.BUTTON3 ||
				(e.getButton() == MouseEvent.BUTTON1 && 
						(e.getModifiersEx() & ActionEvent.SHIFT_MASK)==
						ActionEvent.SHIFT_MASK)) {
			activeMode.clicked3(this,e);
		}
		else if (e.getButton() == MouseEvent.BUTTON1) 
			activeMode.clicked1(this,e);
		e.consume();
	}
	
	public void mousePressed(MouseEvent e) {
		if (e.getButton() == MouseEvent.BUTTON2 ||
				(e.getButton() == MouseEvent.BUTTON1 && 
						(e.getModifiersEx() & ActionEvent.CTRL_MASK)==
						ActionEvent.CTRL_MASK)) {
			activeMode.pressed2(this,e);
		}
		else if (e.getButton() == MouseEvent.BUTTON3 ||
				(e.getButton() == MouseEvent.BUTTON1 && 
						(e.getModifiersEx() & ActionEvent.SHIFT_MASK)==
						ActionEvent.SHIFT_MASK)) {
			activeMode.pressed3(this,e);
		}
		else if (e.getButton() == MouseEvent.BUTTON1) 
			activeMode.pressed1(this,e);
		e.consume();
	}
	
	public void mouseReleased(MouseEvent e) {
		if (e.getButton() == MouseEvent.BUTTON2 ||
				(e.getButton() == MouseEvent.BUTTON1 && 
						(e.getModifiersEx() & ActionEvent.CTRL_MASK)==
						ActionEvent.CTRL_MASK)) {
			activeMode.release2(this,e);
		}
		else if (e.getButton() == MouseEvent.BUTTON3 ||
				(e.getButton() == MouseEvent.BUTTON1 && 
						(e.getModifiersEx() & ActionEvent.SHIFT_MASK)==
						ActionEvent.SHIFT_MASK)) {
			activeMode.release3(this,e);
		}
		else if (e.getButton() == MouseEvent.BUTTON1) 
			activeMode.release1(this,e);
		e.consume();	
	}

	// MouseMotionListener 
	public void mouseDragged(MouseEvent e) {
		activeMode.dragged(this,e);
	}
	public void mouseEntered(MouseEvent e) {
		requestFocus(); // send focus to activeScreen so it gets key events
	}
	public void mouseExited(MouseEvent e) {
		PackControl.mbarPanel.requestFocusInWindow(); // move focus to innocuous place
	}

	// update cursor coordinate indicator in 'MainFrame'
	public void mouseMoved(MouseEvent e) {
		Point2D.Double pt2D=cpDrawing.pt2RealPt(e.getPoint(),getWidth(),getHeight());
		Complex z=new Complex(pt2D.x,pt2D.y);
		if (cpDrawing.getGeom()>0) { // sphere
			if (z.abs()>1.0) return;
  		  	z=cpDrawing.sphView.toRealSph(SphView.visual_plane_to_s_pt(z));
		}
		PackControl.activeFrame.updateLocPanel(cpDrawing.getGeom(),z);
	}
	
	//AF>>>//
	public void mouseWheelMoved(MouseWheelEvent mwe) {
		double delta = mwe.getPreciseWheelRotation();
		if (delta == 0) return;

		int width  = mwe.getComponent().getWidth();
		int height = mwe.getComponent().getHeight();

		if (mwe.isControlDown()) {
			// Pinch-to-zoom or Ctrl+scroll. Negative delta = zoom in.
			double factor = Math.pow(1.05, delta);
			int x = (int) mwe.getPoint().getX();
			int y = (int) mwe.getPoint().getY();
			Point2D.Double realPt = cpDrawing.pt2RealPt(new Point(x, y), width, height);
			Complex cursor = new Complex(realPt);
			Complex center = cpDrawing.realBox.lz.add(cpDrawing.realBox.rz).divide(2.0);
			// Shift center toward cursor before scaling so the gesture point stays fixed.
			cpDrawing.realBox.transView(cursor.minus(center).mult(1.0 - factor));
			try {
				cpDrawing.realBox.scaleView(factor);
				cpDrawing.update(2);
				TrafficCenter.cmdGUI(cpDrawing.getPackData(), "disp -wr");
			} catch (Exception ex) { return; }
			repaint();
		}
	}
	//<<<AF//

	/**
	 * Throw in correct 'CPDrawing' image 
	 */
	public void paintComponent(Graphics g) {
		g.drawImage(cpDrawing.packImage,0,0,getWidth(),getHeight(),null);
		if (cpDrawing.isAxisMode()) {
			Graphics2D g2=(Graphics2D)g;
			cpDrawing.drawXAxis(g2);
			cpDrawing.drawYAxis(g2);
		}
	}
	
}