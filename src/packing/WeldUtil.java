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
		ArrayList<Integer> arc1=arcClw(p1,v1,w1);
		ArrayList<Integer> arc2=arcCclw(p2,v2,w2);
		if (arc1==null || arc2==null)
			throw new DataException(
					"weld: arc endpoints not on a common bdry component");
		double[] s=arcCoords(p1,arc1);
		double[] t=arcCoords(p2,arc2);
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
