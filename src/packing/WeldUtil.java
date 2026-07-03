package packing;

import java.util.ArrayList;

import combinatorics.komplex.HalfEdge;
import dcel.RawManip;
import exceptions.DataException;
import geometry.CommonMath;
import komplex.EdgeSimple;

/**
 * Static utilities for welding boundary arcs of packings, used
 * by 'WELDmode' (interactive welding on the canvas) but callable
 * from anywhere.
 *
 * Conventions match 'adjoin': the arc on the first packing runs
 * CLOCKWISE from v1 to w1, the arc on the second packing runs
 * COUNTERCLOCKWISE from v2 to w2; v1 is identified with v2 and
 * w1 with w2. If w==v, the arc is the full boundary component.
 *
 * When the two arcs have differing edge counts, 'refineToMatch'
 * reconciles them: each arc is parameterized by arc length
 * (distance between circle centers in the packing's current
 * geometry), normalized to [0,1], and the linear map between the
 * parameterizations dictates where new vertices are interpolated
 * so that every vertex of either arc has a partner on the other.
 * Coordinates that nearly coincide share a single partner pair
 * rather than generating tiny edges.
 */
public class WeldUtil {

	/**
	 * Evaluate the piecewise-linear map given by control points
	 * 'map' (each {x,y}, strictly increasing in both coordinates,
	 * running (0,0) to (1,1)) at x.
	 * @param map double[][]
	 * @param x double
	 * @return double h(x)
	 */
	public static double evalPL(double[][] map,double x) {
		if (x<=map[0][0])
			return map[0][1];
		for (int k=1;k<map.length;k++) {
			if (x<=map[k][0]) {
				double t=(x-map[k-1][0])/(map[k][0]-map[k-1][0]);
				return map[k-1][1]+t*(map[k][1]-map[k-1][1]);
			}
		}
		return map[map.length-1][1];
	}

	/**
	 * Convert a 'PATH x y .. END' path (as read by
	 * 'PathManager.readpath', the welding-map file format) into
	 * PL control points for 'evalPL': the points are normalized
	 * so both coordinates run 0 to 1, and must then be strictly
	 * increasing.
	 * @param path Path2D.Double
	 * @return double[][], or null if path is null
	 * @throws DataException if the map is not strictly increasing
	 */
	public static double[][] mapFromPath(java.awt.geom.Path2D.Double path) {
		if (path==null)
			return null;
		ArrayList<double[]> pl=new ArrayList<double[]>();
		java.awt.geom.PathIterator pit=path.getPathIterator(null);
		double[] c=new double[6];
		while (!pit.isDone()) {
			int typ=pit.currentSegment(c);
			if (typ==java.awt.geom.PathIterator.SEG_MOVETO ||
					typ==java.awt.geom.PathIterator.SEG_LINETO)
				pl.add(new double[] {c[0],c[1]});
			pit.next();
		}
		int n=pl.size();
		if (n<2)
			throw new DataException("weld map: fewer than 2 points");
		double x0=pl.get(0)[0];
		double xs=pl.get(n-1)[0]-x0;
		double y0=pl.get(0)[1];
		double ys=pl.get(n-1)[1]-y0;
		if (Math.abs(xs)<1e-12 || Math.abs(ys)<1e-12)
			throw new DataException("weld map: degenerate range");
		double[][] map=new double[n][2];
		for (int k=0;k<n;k++) {
			map[k][0]=(pl.get(k)[0]-x0)/xs;
			map[k][1]=(pl.get(k)[1]-y0)/ys;
		}
		for (int k=1;k<n;k++)
			if (map[k][0]<=map[k-1][0] || map[k][1]<=map[k-1][1])
				throw new DataException("weld map: not strictly "+
						"increasing at point "+k);
		return map;
	}

	// snap tolerance: a domain and range coordinate are "matched"
	//   if they differ by less than this fraction of the smaller
	//   of the two edges currently being traversed.
	public static final double SNAP_FRACTION=0.25;

	/**
	 * Boundary arc from v clockwise to w; v first, w last.
	 * If w==v, the full boundary component (v appears first and
	 * last).
	 * @param p PackData
	 * @param v int
	 * @param w int
	 * @return ArrayList<Integer>, null if w not on v's component
	 */
	public static ArrayList<Integer> arcClw(PackData p,int v,int w) {
		return walkArc(p,v,w,false);
	}

	/**
	 * Boundary arc from v counterclockwise to w; v first, w last.
	 * If w==v, the full boundary component.
	 * @param p PackData
	 * @param v int
	 * @param w int
	 * @return ArrayList<Integer>, null if w not on v's component
	 */
	public static ArrayList<Integer> arcCclw(PackData p,int v,int w) {
		return walkArc(p,v,w,true);
	}

	private static ArrayList<Integer> walkArc(PackData p,int v,int w,
			boolean cclw) {
		if (v<1 || v>p.nodeCount || w<1 || w>p.nodeCount ||
				!p.isBdry(v) || !p.isBdry(w))
			return null;
		ArrayList<Integer> arc=new ArrayList<Integer>();
		arc.add(v);
		int next=v;
		int safety=p.nodeCount+1;
		do {
			next=cclw ? p.getFirstPetal(next) : p.getLastPetal(next);
			if (next<=0)
				return null;
			arc.add(next);
			safety--;
		} while (next!=w && safety>0);
		if (next!=w)
			return null;
		return arc;
	}

	/**
	 * Cumulative arc-length coordinates of the arc's vertices,
	 * normalized to [0,1]. Lengths are distances between circle
	 * centers in the packing's current geometry; degenerate
	 * lengths (NaN/infinite/zero, e.g. unlaid-out circles) fall
	 * back to combinatorial length 1 for that edge.
	 * @param p PackData
	 * @param arc ArrayList<Integer>
	 * @return double[], same size as arc
	 */
	public static double[] arcCoords(PackData p,ArrayList<Integer> arc) {
		int n=arc.size();
		double[] coords=new double[n];
		coords[0]=0.0;
		for (int i=1;i<n;i++) {
			double len=CommonMath.get_pt_dist(p.getCenter(arc.get(i-1)),
					p.getCenter(arc.get(i)),p.hes);
			if (Double.isNaN(len) || Double.isInfinite(len) || len<=0.0)
				len=1.0;
			coords[i]=coords[i-1]+len;
		}
		double total=coords[n-1];
		if (total<=0.0)
			throw new DataException("weld: arc has no length");
		for (int i=1;i<n;i++)
			coords[i] /= total;
		return coords;
	}

	/**
	 * Add a new bdry vertex between adjacent bdry vertices v and
	 * v_next, where v_next=p.getFirstPetal(v). (Static adaptation
	 * of 'WeldManager.add_between'.) If v or v_next lies in just
	 * one face, an interior vertex is first added on the opposite
	 * edge so the split is legal.
	 * @param p PackData
	 * @param v int
	 * @param v_next int
	 * @return int, index of new bdry vertex, 0 on error
	 */
	public static int addBetween(PackData p,int v,int v_next) {
		HalfEdge hedge=p.packDCEL.findHalfEdge(new EdgeSimple(v,v_next));
		if (!p.isBdry(v) || !p.isBdry(v_next) ||
				hedge==null || !hedge.isBdry())
			return 0;

		if (hedge.face!=null && hedge.face.faceIndx<0)
			hedge=hedge.twin;

		// if no interior petal, have to split opposite edge first
		if (hedge.next.isBdry()) {
			HalfEdge opp=hedge.next.next;
			if (opp.isBdry() ||
					RawManip.splitEdge_raw(p.packDCEL,opp)==null)
				return 0;
		}

		if (RawManip.splitEdge_raw(p.packDCEL,hedge)==null)
			return 0;

		p.packDCEL.fixDCEL(p);
		return p.nodeCount;
	}

	/**
	 * Refine the boundary arcs (clockwise v1 to w1 in p1,
	 * counterclockwise v2 to w2 in p2) so they have the same
	 * number of edges, inserting vertices per the linear map
	 * between the arcs' normalized arc-length coordinates: each
	 * original vertex of either arc gains a partner (possibly
	 * newly created) at the corresponding location on the other,
	 * except where coordinates nearly coincide and existing
	 * vertices are matched. p1 and p2 may be the same packing
	 * (arcs should then be edge-disjoint).
	 *
	 * Both packings are modified in place (combinatorics only;
	 * a repack/layout afterwards is advisable).
	 * @param p1 PackData
	 * @param v1 int
	 * @param w1 int
	 * @param p2 PackData
	 * @param v2 int
	 * @param w2 int
	 * @return int, common edge count after refinement, 0 on error
	 */
	public static int refineToMatch(PackData p1,int v1,int w1,
			PackData p2,int v2,int w2) {
		return refineToMatch(p1,v1,w1,p2,v2,w2,null);
	}

	/**
	 * As 'refineToMatch', but matching through a welding map h:
	 * the point at (normalized arc-length) parameter x on arc 1
	 * is welded to the point at parameter h(x) on arc 2. 'hmap'
	 * gives h by its piecewise-linear control points
	 * {{0,0},..,{1,1}}, strictly increasing in both coordinates
	 * (see 'evalPL'); null means the identity map. Since h is
	 * strictly increasing, arc 1's mapped coordinates stay in
	 * order and the merge walk is unchanged — h only changes
	 * which points pair up.
	 * @param p1 PackData
	 * @param v1 int
	 * @param w1 int
	 * @param p2 PackData
	 * @param v2 int
	 * @param w2 int
	 * @param hmap double[][], PL control points, or null (identity)
	 * @return int, common edge count after refinement, 0 on error
	 */
	public static int refineToMatch(PackData p1,int v1,int w1,
			PackData p2,int v2,int w2,double[][] hmap) {
		ArrayList<Integer> arc1=arcClw(p1,v1,w1);
		ArrayList<Integer> arc2=arcCclw(p2,v2,w2);
		if (arc1==null || arc2==null)
			throw new DataException(
					"weld: arc endpoints not on a common bdry component");
		double[] s=arcCoords(p1,arc1);
		double[] t=arcCoords(p2,arc2);
		if (hmap!=null) // push arc 1's coordinates through h
			for (int i=0;i<s.length;i++)
				s[i]=evalPL(hmap,s[i]);
		int m=arc1.size()-1; // edge counts
		int n=arc2.size()-1;

		// Walk both arcs in parallel, merging the coordinate
		// lists. cur1/cur2 are the verts (original or new) at the
		// current weld position; arc1.get(i)/arc2.get(j) are the
		// next original verts ahead of it.
		int i=1;
		int j=1;
		int cur1=arc1.get(0);
		int cur2=arc2.get(0);
		while (i<m || j<n) {
			double si=(i<m) ? s[i] : 2.0; // sentinel past the end
			double tj=(j<n) ? t[j] : 2.0;
			double snap=SNAP_FRACTION*Math.min(
					(i<m) ? s[i]-s[i-1] : 1.0,
					(j<n) ? t[j]-t[j-1] : 1.0);
			if (i<m && j<n && Math.abs(si-tj)<snap) {
				// existing vertices match
				cur1=arc1.get(i++);
				cur2=arc2.get(j++);
			}
			else if (si<tj) {
				// p1's vertex comes first: give it a new partner
				// in p2's current edge. Walking cclw, that edge
				// is (cur2, arc2.get(j)).
				int nv=addBetween(p2,cur2,arc2.get(j));
				if (nv==0)
					return 0;
				cur1=arc1.get(i++);
				cur2=nv;
			}
			else {
				// p2's vertex comes first: new partner in p1's
				// current edge. Walking clw, the cclw-oriented
				// edge is (arc1.get(i), cur1).
				int nv=addBetween(p1,arc1.get(i),cur1);
				if (nv==0)
					return 0;
				cur1=nv;
				cur2=arc2.get(j++);
			}
		}

		// recount and sanity-check
		ArrayList<Integer> chk1=arcClw(p1,v1,w1);
		ArrayList<Integer> chk2=arcCclw(p2,v2,w2);
		if (chk1==null || chk2==null || chk1.size()!=chk2.size())
			throw new DataException("weld: refinement left arcs "+
					"mismatched; packings may be modified");
		return chk1.size()-1;
	}

}
