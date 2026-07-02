package canvasses;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;

import javax.swing.JOptionPane;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import complex.Complex;
import images.CPIcon;
import input.TrafficCenter;
import listManip.NodeLink;
import packing.PackData;
import packing.PackUndo;
import packing.WeldUtil;
import util.SphView;

/**
 * ===== active canvas cursor mode =====
 * Weld mode: paste together boundary arcs of packings by
 * clicking their end circles.
 *
 * Four clicks: (1) start and (2) end of an arc on one packing,
 * then (3) start and (4) end of an arc on another packing ---
 * or the same packing for a self-weld. Switch the active packing
 * between clicks 2 and 3 to weld two different packings. Click
 * the same circle twice to select a full boundary component.
 *
 * Start circles are identified with one another, as are end
 * circles; following 'adjoin' conventions, the first arc runs
 * clockwise from its start, the second counterclockwise.
 * Selections are highlighted as they are made. If the arcs'
 * edge counts differ, the user is offered boundary refinement
 * (see 'WeldUtil.refineToMatch'). The result lands in the first
 * packing's slot; seam vertices are left in 'vlist'.
 *
 * Right-click (or switching modes) cancels.
 */
public class WELDmode extends MyCanvasMode {

	private static final long serialVersionUID = 1L;

	static final String HILITE_1="195"; // arc 1 color code (red)
	static final String HILITE_2="45";  // arc 2 color code (blue)

	int state;          // number of arc ends picked so far, 0-3
	int pack1,pack2;    // pack numbers of the two arcs
	int v1,w1,v2,w2;    // arc ends: v's are starts, w's are ends

	// Constructor
	public WELDmode(String name,String cursorname,Point hotPt,
			String tool_type) {
		super(name,new CPIcon(cursorname),hotPt,
				null,null,null,"Weld bdry arcs",
				"Weld: click start/end bdry circles of an arc on one "+
				"packing, then on another (same circle twice = full bdry); "+
				"right-click cancels",
				tool_type,true);
		packSwitchOK=true; // welding two packings needs a mid-mode switch
		clearState();
		updateMenuItem();
	}

	public void clearState() {
		state=0;
		pack1=pack2=-1;
		v1=w1=v2=w2=0;
	}

	// called when canvas mode changes, so stale selections die
	public void moreReset() {
		clearState();
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

	private void highlight(PackData p,String verts,String colorCode) {
		try {
			TrafficCenter.cmdGUI(p,
					"disp -ccc"+colorCode+"t3 "+verts);
		} catch (Exception ex) {}
	}

	private static String arcToString(ArrayList<Integer> arc) {
		StringBuilder sb=new StringBuilder();
		Iterator<Integer> ait=arc.iterator();
		while (ait.hasNext())
			sb.append(ait.next()).append(" ");
		return sb.toString().trim();
	}

	/**
	 * Redraw the involved packings to clear highlights, reset.
	 */
	private void cancel(ActiveWrapper aW,String why) {
		try {
			if (pack1>=0)
				TrafficCenter.cmdGUI(
						CPBase.cpDrawing[pack1].getPackData(),"disp -wr");
			if (pack2>=0 && pack2!=pack1)
				TrafficCenter.cmdGUI(
						CPBase.cpDrawing[pack2].getPackData(),"disp -wr");
		} catch (Exception ex) {}
		clearState();
		if (why!=null)
			CirclePack.cpb.msg("weld: "+why);
		rePaint(aW);
	}

	// left click: pick the next arc end
	public void clicked1(ActiveWrapper aW,MouseEvent e) {
		PackData p=aW.getCPDrawing().getPackData();
		int pnum=aW.getCPDrawing().getPackNum();
		int v=vertAtPoint(aW,e);
		if (v==0)
			return;
		if (!p.isBdry(v)) {
			CirclePack.cpb.errMsg("weld: circle "+v+
					" is not on the boundary");
			return;
		}

		switch (state) {
		case 0: // start of first arc
		{
			pack1=pnum;
			v1=v;
			highlight(p,Integer.toString(v),HILITE_1);
			CirclePack.cpb.msg("weld: arc 1 starts at "+v+" (p"+pnum+
					"); now click its end (same circle = full bdry)");
			state=1;
			break;
		}
		case 1: // end of first arc
		{
			if (pnum!=pack1) {
				CirclePack.cpb.errMsg("weld: both ends of the first "+
						"arc must be on pack p"+pack1);
				return;
			}
			ArrayList<Integer> arc=WeldUtil.arcClw(p,v1,v);
			if (arc==null) {
				CirclePack.cpb.errMsg("weld: "+v1+" and "+v+
						" are not on the same bdry component");
				return;
			}
			w1=v;
			highlight(p,arcToString(arc),HILITE_1);
			CirclePack.cpb.msg("weld: arc 1 = "+(arc.size()-1)+
					" edges clockwise from "+v1+" to "+w1+" (p"+pack1+
					"); now click the start of arc 2 (switch packings "+
					"first if welding to another packing)");
			state=2;
			break;
		}
		case 2: // start of second arc
		{
			pack2=pnum;
			v2=v;
			highlight(p,Integer.toString(v),HILITE_2);
			CirclePack.cpb.msg("weld: arc 2 starts at "+v+" (p"+pnum+
					"); now click its end");
			state=3;
			break;
		}
		case 3: // end of second arc; carry out the weld
		{
			if (pnum!=pack2) {
				CirclePack.cpb.errMsg("weld: both ends of the second "+
						"arc must be on pack p"+pack2);
				return;
			}
			ArrayList<Integer> arc=WeldUtil.arcCclw(p,v2,v);
			if (arc==null) {
				CirclePack.cpb.errMsg("weld: "+v2+" and "+v+
						" are not on the same bdry component");
				return;
			}
			w2=v;
			highlight(p,arcToString(arc),HILITE_2);
			doWeld(aW);
			break;
		}
		}
		e.consume();
	}

	/**
	 * Both arcs are chosen: reconcile edge counts (offering
	 * refinement if they differ), 'adjoin', and display.
	 */
	private void doWeld(ActiveWrapper aW) {
		PackData p1=CPBase.cpDrawing[pack1].getPackData();
		PackData p2=CPBase.cpDrawing[pack2].getPackData();
		if (p1!=p2 && p1.hes!=p2.hes) {
			cancel(aW,null);
			CirclePack.cpb.errMsg("weld: packings have different "+
					"geometries; convert one first ('geom_to_e', "+
					"'geom_to_h')");
			aW.setDefaultMode();
			return;
		}
		try {
			ArrayList<Integer> arc1=WeldUtil.arcClw(p1,v1,w1);
			ArrayList<Integer> arc2=WeldUtil.arcCclw(p2,v2,w2);
			int n1=arc1.size()-1;
			int n2=arc2.size()-1;

			// snapshot for 'undo'; copies, since refinement below
			// mutates the packings in place
			if (p1!=p2)
				PackUndo.save("weld",new int[] {pack1,pack2},
						new PackData[] {p1.copyPackTo(),p2.copyPackTo()},
						null);
			else
				PackUndo.save("weld",new int[] {pack1},
						new PackData[] {p1.copyPackTo()},null);

			if (n1!=n2) {
				int ans=JOptionPane.showConfirmDialog(
						PackControl.activeFrame,
						"The arcs have "+n1+" and "+n2+" edges. Refine "+
						"the boundaries so they match?\n(New vertices are "+
						"interpolated by arc length in each packing's "+
						"current geometry.)",
						"Weld: arcs differ",
						JOptionPane.YES_NO_OPTION);
				if (ans!=JOptionPane.YES_OPTION) {
					PackUndo.clear(); // nothing changed
					cancel(aW,"cancelled");
					aW.setDefaultMode();
					return;
				}
				n1=WeldUtil.refineToMatch(p1,v1,w1,p2,v2,w2);
				if (n1<=0) {
					cancel(aW,null);
					CirclePack.cpb.errMsg(
							"weld: boundary refinement failed");
					aW.setDefaultMode();
					return;
				}
				CirclePack.cpb.msg("weld: boundaries refined; arcs now "+
						"have "+n1+" edges each");
			}

			PackData newPack=PackData.adjoinCall(p1,p2,v1,v2,n1);
			if (newPack==null)
				throw new Exception("'adjoin' returned nothing");
			newPack.packDCEL.fixDCEL(newPack);
			PackData pdata=CirclePack.cpb.swapPackData(newPack,pack1,true);
			TrafficCenter.cmdGUI(pdata,"repack;layout;disp -w -c");
			CirclePack.cpb.msg("weld: done; result is in p"+pack1+
					" ("+pdata.nodeCount+" vertices); seam vertices "+
					"are in vlist (e.g. 'disp -cc"+HILITE_1+"t3 vlist'); "+
					"'undo' reverts");
		} catch (Exception ex) {
			ex.printStackTrace(); // full trace to the launch terminal
			StackTraceElement[] st=ex.getStackTrace();
			String where=(st!=null && st.length>0)?
					(" ["+st[0].getClassName()+"."+st[0].getMethodName()+
					":"+st[0].getLineNumber()+"]"):"";
			CirclePack.cpb.errMsg("weld failed: "+ex.getMessage()+where);
		}
		clearState();
		rePaint(aW);
		aW.setDefaultMode();
	}

	// right click cancels and leaves weld mode
	public void clicked3(ActiveWrapper aW,MouseEvent e) {
		cancel(aW,"cancelled");
		e.consume();
		aW.setDefaultMode();
	}

	public void pressed3(ActiveWrapper aW,MouseEvent e) {} // override

}
