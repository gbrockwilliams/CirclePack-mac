package canvasses;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.Iterator;

import javax.swing.JOptionPane;

import allMains.CirclePack;
import circlePack.PackControl;
import complex.Complex;
import ftnTheory.BrooksTorusExtender;
import images.CPIcon;
import input.TrafficCenter;
import listManip.NodeLink;
import packing.PackData;
import packing.PackExtender;
import util.SphView;

/**
 * ===== active canvas cursor mode =====
 * Brooks parameter mode: reparameterize one cell of a Brooks
 * torus by clicking it.
 *
 * The packing must carry a 'BrooksTorusExtender' (created by
 * 'create brooks_torus M N ...'). Click any circle interior to
 * a cell: the cell is highlighted and a dialog shows its current
 * continued-fraction parameter; enter a new sequence (alternating
 * vertical/horizontal counts, e.g. "2 1 3"; blank = plain quad)
 * and the torus is rebuilt, repacked, and redrawn. The mode stays
 * active for further clicks; right-click exits.
 */
public class BROOKSmode extends MyCanvasMode {

	private static final long serialVersionUID = 1L;

	static final String HILITE="195"; // cell highlight color code (red)

	// Constructor
	public BROOKSmode(String name,String cursorname,Point hotPt,
			String tool_type) {
		super(name,new CPIcon(cursorname),hotPt,
				null,null,null,"Brooks parameter",
				"Brooks: click a circle inside a cell of a Brooks torus "+
				"to enter a new continued-fraction parameter for that "+
				"cell; right-click exits",
				tool_type,true);
		updateMenuItem();
	}

	/**
	 * Convert mouse point to the packing's real coordinates
	 * (handling the spherical view) and find the nearest circle.
	 * @return int vertex, 0 if nothing suitable
	 */
	private int vertAtPoint(ActiveWrapper aW,MouseEvent e) {
		PackData p=aW.getCPDrawing().getPackData();
		if (p==null || !p.status)
			return 0;
		Point2D.Double pt=aW.getCPDrawing().pt2RealPt(e.getPoint(),
				aW.getWidth(),aW.getHeight());
		Complex z=new Complex(pt.x,pt.y);
		if (p.hes>0) { // sphere
			if (z.abs()>1.0)
				return 0;
			z=aW.getCPDrawing().sphView.toRealSph(
					SphView.visual_plane_to_s_pt(z));
		}
		NodeLink vlist=p.cir_closest(z,false);
		if (vlist==null || vlist.size()==0)
			return 0;
		return vlist.get(0);
	}

	/**
	 * Find the 'BrooksTorusExtender' attached to 'p'.
	 * @return null if there is none
	 */
	private BrooksTorusExtender findExtender(PackData p) {
		if (p==null || p.packExtensions==null)
			return null;
		Iterator<PackExtender> pXs=p.packExtensions.iterator();
		while (pXs.hasNext()) {
			PackExtender pext=pXs.next();
			if (pext instanceof BrooksTorusExtender)
				return (BrooksTorusExtender)pext;
		}
		return null;
	}

	// left click: pick a cell, prompt for its new parameter
	public void clicked1(ActiveWrapper aW,MouseEvent e) {
		PackData p=aW.getCPDrawing().getPackData();
		int v=vertAtPoint(aW,e);
		if (v==0)
			return;
		BrooksTorusExtender bte=findExtender(p);
		if (bte==null) {
			CirclePack.cpb.errMsg("brooks: this packing carries no "+
					"Brooks torus; build one with 'create brooks_torus "+
					"M N n1 n2 ..'");
			return;
		}
		int cell=bte.cellOfVert(v);
		if (cell<0) {
			CirclePack.cpb.msg("brooks: circle "+v+" is a shared corner; "+
					"click a circle inside a cell");
			return;
		}
		int ci=cell%bte.M;
		int cj=cell/bte.M;

		// highlight the chosen cell while the dialog is up
		try {
			TrafficCenter.cmdGUI(p,
					"disp -ccc"+HILITE+"t3 "+bte.cellVertString(cell));
		} catch (Exception ex) {}

		String input=JOptionPane.showInputDialog(PackControl.activeFrame,
				"New parameter for cell ("+ci+","+cj+"):\n"+
				"continued-fraction sequence, alternating vertical/"+
				"horizontal counts (e.g. \"2 1 3\"); blank = plain quad",
				bte.cfracString(cell));
		if (input==null) { // cancelled: clear the highlight
			try {
				TrafficCenter.cmdGUI(p,"disp -wr");
			} catch (Exception ex) {}
			e.consume();
			return;
		}

		int[] cfrac=null;
		try {
			String str=input.trim();
			if (str.length()==0)
				cfrac=new int[0];
			else {
				String[] ns=str.split("[\\s,]+");
				cfrac=new int[ns.length];
				for (int k=0;k<ns.length;k++) {
					cfrac[k]=Integer.parseInt(ns[k]);
					if (cfrac[k]<0)
						throw new NumberFormatException("negative");
				}
			}
		} catch (Exception ex) {
			try {
				TrafficCenter.cmdGUI(p,"disp -wr");
			} catch (Exception iex) {}
			CirclePack.cpb.errMsg("brooks: parameter must be a list of "+
					"integers >= 0, e.g. \"2 1 3\"");
			e.consume();
			return;
		}

		if (bte.setCellParam(cell,cfrac))
			CirclePack.cpb.msg("brooks: cell ("+ci+","+cj+") rebuilt "+
					"with parameter '"+bte.cfracString(cell)+
					"'; click another cell or right-click to exit");
		rePaint(aW);
		e.consume();
	}

	// right click exits the mode
	public void clicked3(ActiveWrapper aW,MouseEvent e) {
		e.consume();
		aW.setDefaultMode();
	}

	public void pressed3(ActiveWrapper aW,MouseEvent e) {} // override

}
