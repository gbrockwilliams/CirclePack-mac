import java.util.ArrayList;

import geometry.CommonMath;
import input.CommandStrParser;
import packing.PackCreation;
import packing.PackData;
import packing.WeldUtil;
import util.UtilPacket;

/**
 * Headless reproduction of self-weld failures seen in WELDmode.
 * Run: java -cp "out:cpcore.jar:jars/*:tests" WeldSelfTest
 */
public class WeldSelfTest {

	static int failures=0;

	public static void main(String[] args) {
		scenario1_commonStart();
		scenario2_commonEnd();
		scenario3_disjointArcs();
		scenario4_foldShut();
		scenario5_refineCommonStart();
		scenario6_refineDisjoint();
		scenario7_annulusFullBdry();
		scenario8_hypCommonStart();
		scenario9_hypRefine();
		scenario10_hypFoldShut();
		scenario11_twoPackEucl();
		scenario12_twoPackEuclRefine();
		scenario13_twoPackSphere();
		scenario14_twoPackHyp();
		scenario15_twoPackHypRefine();
		scenario16_undoSnapshotIndependence();
		scenario17_weldOntoCopy();
		scenario18_weldWithMap();
		System.out.println("\n==== failures: "+failures+" ====");
	}

	// walk n edges clockwise from v along bdry
	static int clwFrom(PackData p,int v,int n) {
		int next=v;
		for (int i=0;i<n;i++)
			next=p.getLastPetal(next);
		return next;
	}

	// walk n edges cclw from v along bdry
	static int cclwFrom(PackData p,int v,int n) {
		int next=v;
		for (int i=0;i<n;i++)
			next=p.getFirstPetal(next);
		return next;
	}

	static void checkCurves(PackData p,String label) {
		UtilPacket uP=new UtilPacket();
		int bad=0;
		for (int v=1;v<=p.nodeCount;v++) {
			if (!CommonMath.get_anglesum(p,v,p.getRadius(v),uP)) {
				System.out.println("  ["+label+"] angle sum FAILS at v="+v+
						" bdry="+p.isBdry(v)+" rad="+p.getRadius(v));
				bad++;
				if (bad>8) { System.out.println("  ..."); break; }
			}
		}
		if (bad==0)
			System.out.println("  ["+label+"] all angle sums OK, nodeCount="
					+p.nodeCount);
		else
			failures++;
	}

	// mimic WELDmode.doWeld
	static void tryWeld(PackData p,int v1,int w1,int v2,int w2,String label) {
		System.out.println("\n--- "+label+": v1="+v1+" w1="+w1+
				" v2="+v2+" w2="+w2);
		try {
			ArrayList<Integer> arc1=WeldUtil.arcClw(p,v1,w1);
			ArrayList<Integer> arc2=WeldUtil.arcCclw(p,v2,w2);
			if (arc1==null||arc2==null) {
				System.out.println("  arc walk failed");
				failures++;
				return;
			}
			int n1=arc1.size()-1;
			int n2=arc2.size()-1;
			System.out.println("  arcs ("+n1+"/"+n2+" edges): "+arc1+"  /  "+arc2);
			if (n1!=n2) {
				n1=WeldUtil.refineToMatch(p,v1,w1,p,v2,w2);
				System.out.println("  refined to "+n1+" edges: "+
						WeldUtil.arcClw(p,v1,w1)+"  /  "+WeldUtil.arcCclw(p,v2,w2));
			}
			PackData newPack=PackData.adjoinCall(p,p,v1,v2,n1);
			if (newPack==null) {
				System.out.println("  adjoinCall returned null");
				failures++;
				return;
			}
			newPack.packDCEL.fixDCEL(newPack);
			checkCurves(newPack,label);
			// mimic the rest of doWeld: repack; layout
			int rp=CommandStrParser.jexecute(newPack,"repack");
			int ly=CommandStrParser.jexecute(newPack,"layout");
			System.out.println("  repack="+rp+" layout="+ly);
			checkCurves(newPack,label+" after repack");
		} catch (Exception ex) {
			System.out.println("  EXCEPTION: "+ex.getClass().getSimpleName()
					+": "+ex.getMessage());
			failures++;
		}
	}

	// fold: arcs share their start vertex (v1==v2)
	static void scenario1_commonStart() {
		PackData p=PackCreation.hexBuild(3);
		int b=firstBdry(p);
		int w1=clwFrom(p,b,3);
		int w2=cclwFrom(p,b,3);
		tryWeld(p,b,w1,b,w2,"common start (zip 3 edges)");
	}

	// arcs share their end vertex (w1==w2)
	static void scenario2_commonEnd() {
		PackData p=PackCreation.hexBuild(3);
		int b=firstBdry(p);
		int v1=cclwFrom(p,b,3);
		int v2=clwFrom(p,b,3);
		tryWeld(p,v1,b,v2,b,"common end (zip 3 edges)");
	}

	// arcs disjoint on the same bdry component
	static void scenario3_disjointArcs() {
		PackData p=PackCreation.hexBuild(4);
		int b=firstBdry(p);
		int v1=b;
		int w1=clwFrom(p,v1,3);
		int v2=clwFrom(p,w1,6);
		int w2=cclwFrom(p,v2,3);
		tryWeld(p,v1,w1,v2,w2,"disjoint arcs same component");
	}

	// fold the disc completely shut: arcs share BOTH endpoints,
	// each is half the bdry
	static void scenario4_foldShut() {
		PackData p=PackCreation.hexBuild(3);
		int b=firstBdry(p);
		ArrayList<Integer> comp=WeldUtil.arcClw(p,b,b);
		int total=comp.size()-1;
		int half=total/2;
		int w=clwFrom(p,b,half);
		tryWeld(p,b,w,b,w,"fold shut ("+half+"+"+(total-half)+" edges)");
	}

	// refinement: arcs share start, lengths differ (3 vs 5)
	static void scenario5_refineCommonStart() {
		PackData p=PackCreation.hexBuild(3);
		int b=firstBdry(p);
		int w1=clwFrom(p,b,3);
		int w2=cclwFrom(p,b,5);
		tryWeld(p,b,w1,b,w2,"refine: common start 3 vs 5");
	}

	// refinement: disjoint arcs, lengths differ
	static void scenario6_refineDisjoint() {
		PackData p=PackCreation.hexBuild(4);
		int b=firstBdry(p);
		int v1=b;
		int w1=clwFrom(p,v1,3);
		int v2=clwFrom(p,w1,7);
		int w2=cclwFrom(p,v2,5);
		tryWeld(p,v1,w1,v2,w2,"refine: disjoint 3 vs 5");
	}

	// annulus: weld its two full bdry components into a torus
	static void scenario7_annulusFullBdry() {
		PackData p=PackCreation.hexCylinder(5,0,4);
		if (p==null) {
			System.out.println("\n--- annulus: hexCylinder returned null, skip");
			return;
		}
		// find a bdry vert on each component
		int b1=firstBdry(p);
		ArrayList<Integer> comp1=WeldUtil.arcClw(p,b1,b1);
		int b2=-1;
		for (int v=1;v<=p.nodeCount;v++)
			if (p.isBdry(v) && !comp1.contains(v)) { b2=v; break; }
		if (b2<0) {
			System.out.println("\n--- annulus: only one bdry component?");
			failures++;
			return;
		}
		tryWeld(p,b1,b1,b2,b2,"annulus full-bdry weld (torus)");
	}

	// hyperbolic max-packed disc (horocycle boundary)
	static PackData hypDisc(int gens) {
		PackData p=PackCreation.hexBuild(gens);
		CommandStrParser.jexecute(p,"geom_to_h");
		CommandStrParser.jexecute(p,"max_pack");
		return p;
	}

	static void scenario8_hypCommonStart() {
		PackData p=hypDisc(3);
		int b=firstBdry(p);
		int w1=clwFrom(p,b,3);
		int w2=cclwFrom(p,b,3);
		tryWeld(p,b,w1,b,w2,"HYP common start (zip 3)");
	}

	static void scenario9_hypRefine() {
		PackData p=hypDisc(3);
		int b=firstBdry(p);
		int w1=clwFrom(p,b,3);
		int w2=cclwFrom(p,b,5);
		tryWeld(p,b,w1,b,w2,"HYP refine 3 vs 5");
	}

	static void scenario10_hypFoldShut() {
		PackData p=hypDisc(3);
		int b=firstBdry(p);
		ArrayList<Integer> comp=WeldUtil.arcClw(p,b,b);
		int total=comp.size()-1;
		int half=total/2;
		int w=clwFrom(p,b,half);
		tryWeld(p,b,w,b,w,"HYP fold shut");
	}

	/**
	 * mimic WELDmode.doWeld for TWO packings (p1!=p2): refine if the
	 * edge counts differ, adjoin, verify. 'expectClosed' true when
	 * the result should have no boundary (skip repack: repacking a
	 * closed surface headless hits the known CirclePack.cpb NPE).
	 */
	static void tryWeld2(PackData p1,int v1,int w1,PackData p2,
			int v2,int w2,boolean expectClosed,String label) {
		System.out.println("\n--- "+label+": p1 v1="+v1+" w1="+w1+
				" / p2 v2="+v2+" w2="+w2);
		try {
			ArrayList<Integer> arc1=WeldUtil.arcClw(p1,v1,w1);
			ArrayList<Integer> arc2=WeldUtil.arcCclw(p2,v2,w2);
			if (arc1==null||arc2==null) {
				System.out.println("  arc walk failed");
				failures++;
				return;
			}
			int n1=arc1.size()-1;
			int n2=arc2.size()-1;
			System.out.println("  arcs ("+n1+"/"+n2+" edges): "+
					arc1+"  /  "+arc2);
			if (n1!=n2) {
				n1=WeldUtil.refineToMatch(p1,v1,w1,p2,v2,w2);
				System.out.println("  refined to "+n1+" edges: "+
						WeldUtil.arcClw(p1,v1,w1)+"  /  "+
						WeldUtil.arcCclw(p2,v2,w2));
			}
			PackData newPack=PackData.adjoinCall(p1,p2,v1,v2,n1);
			if (newPack==null) {
				System.out.println("  adjoinCall returned null");
				failures++;
				return;
			}
			newPack.packDCEL.fixDCEL(newPack);
			// sanity: node count should be p1+p2 minus shared arc verts
			int expect=p1.nodeCount+p2.nodeCount-(n1+1);
			if (expectClosed) // full-bdry weld: ends identified too
				expect=p1.nodeCount+p2.nodeCount-n1;
			if (newPack.nodeCount!=expect)
				System.out.println("  note: nodeCount "+newPack.nodeCount+
						" (naive expectation "+expect+")");
			int bdryCnt=0;
			for (int v=1;v<=newPack.nodeCount;v++)
				if (newPack.isBdry(v)) bdryCnt++;
			if (expectClosed && bdryCnt!=0) {
				System.out.println("  FAIL: expected closed surface, "+
						bdryCnt+" bdry verts remain");
				failures++;
				return;
			}
			checkCurves(newPack,label);
			if (expectClosed) {
				System.out.println("  closed surface: repack skipped "+
						"(headless cpb limitation)");
				return;
			}
			int rp=CommandStrParser.jexecute(newPack,"repack");
			int ly=CommandStrParser.jexecute(newPack,"layout");
			System.out.println("  repack="+rp+" layout="+ly);
			checkCurves(newPack,label+" after repack");
		} catch (Exception ex) {
			System.out.println("  EXCEPTION: "+ex.getClass().getSimpleName()
					+": "+ex.getMessage());
			failures++;
		}
	}

	// two euclidean discs, equal 3-edge arcs
	static void scenario11_twoPackEucl() {
		PackData p1=PackCreation.hexBuild(3);
		PackData p2=PackCreation.hexBuild(3);
		int b1=firstBdry(p1);
		int b2=firstBdry(p2);
		tryWeld2(p1,b1,clwFrom(p1,b1,3),p2,b2,cclwFrom(p2,b2,3),
				false,"two-pack eucl zip 3");
	}

	// two euclidean discs of different size, 3 vs 5 edges (refine)
	static void scenario12_twoPackEuclRefine() {
		PackData p1=PackCreation.hexBuild(3);
		PackData p2=PackCreation.hexBuild(4);
		int b1=firstBdry(p1);
		int b2=firstBdry(p2);
		tryWeld2(p1,b1,clwFrom(p1,b1,3),p2,b2,cclwFrom(p2,b2,5),
				false,"two-pack eucl refine 3 vs 5");
	}

	// weld two discs along their FULL boundaries: sphere
	static void scenario13_twoPackSphere() {
		PackData p1=PackCreation.hexBuild(3);
		PackData p2=PackCreation.hexBuild(3);
		int b1=firstBdry(p1);
		int b2=firstBdry(p2);
		tryWeld2(p1,b1,b1,p2,b2,b2,true,"two-pack full bdry (sphere)");
	}

	// two hyperbolic discs, equal arcs
	static void scenario14_twoPackHyp() {
		PackData p1=hypDisc(3);
		PackData p2=hypDisc(3);
		int b1=firstBdry(p1);
		int b2=firstBdry(p2);
		tryWeld2(p1,b1,clwFrom(p1,b1,3),p2,b2,cclwFrom(p2,b2,3),
				false,"two-pack HYP zip 3");
	}

	// two hyperbolic discs, mismatched arcs (refine)
	static void scenario15_twoPackHypRefine() {
		PackData p1=hypDisc(3);
		PackData p2=hypDisc(4);
		int b1=firstBdry(p1);
		int b2=firstBdry(p2);
		tryWeld2(p1,b1,clwFrom(p1,b1,3),p2,b2,cclwFrom(p2,b2,5),
				false,"two-pack HYP refine 3 vs 5");
	}

	// the weld-undo snapshot relies on 'copyPackTo' copies being
	// unaffected by the in-place boundary refinement: verify.
	static void scenario16_undoSnapshotIndependence() {
		System.out.println("\n--- undo snapshot independence");
		try {
			PackData p1=PackCreation.hexBuild(3);
			PackData p2=PackCreation.hexBuild(4);
			int b1=firstBdry(p1);
			int b2=firstBdry(p2);
			int w1=clwFrom(p1,b1,3);
			int w2=cclwFrom(p2,b2,5);
			PackData c1=p1.copyPackTo();
			PackData c2=p2.copyPackTo();
			int n1before=c1.nodeCount;
			int n2before=c2.nodeCount;
			int n=WeldUtil.refineToMatch(p1,b1,w1,p2,b2,w2);
			if (n<=0) {
				System.out.println("  FAIL: refineToMatch failed");
				failures++;
				return;
			}
			// originals should have grown; copies must be untouched
			if (p1.nodeCount<=n1before && p2.nodeCount<=n2before) {
				System.out.println("  FAIL: refinement added no vertices?");
				failures++;
				return;
			}
			if (c1.nodeCount!=n1before || c2.nodeCount!=n2before) {
				System.out.println("  FAIL: snapshot copies were mutated "+
						"by refinement");
				failures++;
				return;
			}
			// and the copies must still be weldable (what 'undo'
			// hands back to the user)
			ArrayList<Integer> a1=WeldUtil.arcClw(c1,b1,w1);
			ArrayList<Integer> a2=WeldUtil.arcCclw(c2,b2,w2);
			if (a1==null || a2==null || a1.size()!=4 || a2.size()!=6) {
				System.out.println("  FAIL: copies' arcs wrong: "+a1+
						" / "+a2);
				failures++;
				return;
			}
			checkCurves(c1,"snapshot copy 1");
			checkCurves(c2,"snapshot copy 2");
			System.out.println("  OK: refinement grew originals ("+
					n1before+"->"+p1.nodeCount+", "+n2before+"->"+
					p2.nodeCount+"); copies untouched");
		} catch (Exception ex) {
			System.out.println("  EXCEPTION: "+
					ex.getClass().getSimpleName()+": "+ex.getMessage());
			failures++;
		}
	}

	// Brock's 2026-07-01 GUI crash: weld a packing to a COPY of
	// itself with refinement. 'copyPackTo' (via 'cloneDCEL') used to
	// leave 'sizeLimit' at 1000 while allocating 'vertices' tightly,
	// so the first 'splitEdge_raw' on the copy ran off the array
	// ("Index 170 out of bounds for length 170").
	static void scenario17_weldOntoCopy() {
		PackData p1=PackCreation.hexBuild(6); // 127 verts
		PackData p2=p1.copyPackTo();
		int b1=firstBdry(p1);
		int b2=firstBdry(p2);
		// mismatched arcs so the COPY gets refined (split) too
		tryWeld2(p1,b1,clwFrom(p1,b1,9),p2,b2,cclwFrom(p2,b2,13),
				false,"weld onto copyPackTo copy, refine 9 vs 13");
	}

	// partial-arc welds through a NON-identity welding map h:
	// parameter x on arc 1 pairs with h(x) on arc 2
	static void scenario18_weldWithMap() {
		double[][] hmap= {{0.0,0.0},{0.3,0.6},{0.7,0.85},{1.0,1.0}};

		// (a) mismatched arcs (4 vs 6 edges) with the map
		{
			PackData p1=PackCreation.hexBuild(4);
			PackData p2=PackCreation.hexBuild(4);
			int b1=firstBdry(p1);
			int b2=firstBdry(p2);
			int v1=b1,w1=clwFrom(p1,b1,4);
			int v2=b2,w2=cclwFrom(p2,b2,6);
			System.out.println("\n--- weld with map, arcs 4 vs 6");
			try {
				int n=WeldUtil.refineToMatch(p1,v1,w1,p2,v2,w2,hmap);
				if (n<=0) {
					System.out.println("  FAIL: refine returned "+n);
					failures++;
					return;
				}
				PackData np=PackData.adjoinCall(p1,p2,v1,v2,n);
				np.packDCEL.fixDCEL(np);
				checkCurves(np,"map weld 4v6 (n="+n+")");
				CommandStrParser.jexecute(np,"repack");
				CommandStrParser.jexecute(np,"layout");
				checkCurves(np,"map weld 4v6 after repack");
			} catch (Exception ex) {
				System.out.println("  EXCEPTION: "+
						ex.getClass().getSimpleName()+": "+ex.getMessage());
				failures++;
			}
		}

		// (b) EQUAL arc counts but non-identity map: h must still
		// re-pair the vertices (insertions on both sides)
		{
			PackData p1=PackCreation.hexBuild(4);
			PackData p2=PackCreation.hexBuild(4);
			int b1=firstBdry(p1);
			int b2=firstBdry(p2);
			int v1=b1,w1=clwFrom(p1,b1,5);
			int v2=b2,w2=cclwFrom(p2,b2,5);
			System.out.println("\n--- weld with map, arcs 5 vs 5");
			try {
				int n=WeldUtil.refineToMatch(p1,v1,w1,p2,v2,w2,hmap);
				if (n<=0) {
					System.out.println("  FAIL: refine returned "+n);
					failures++;
					return;
				}
				System.out.println("  refined 5/5 -> "+n+
						" (map re-pairs, may insert)");
				PackData np=PackData.adjoinCall(p1,p2,v1,v2,n);
				np.packDCEL.fixDCEL(np);
				checkCurves(np,"map weld 5v5 (n="+n+")");
				CommandStrParser.jexecute(np,"repack");
				CommandStrParser.jexecute(np,"layout");
				checkCurves(np,"map weld 5v5 after repack");
			} catch (Exception ex) {
				System.out.println("  EXCEPTION: "+
						ex.getClass().getSimpleName()+": "+ex.getMessage());
				failures++;
			}
		}

		// (c) evalPL sanity
		double[][] id= {{0,0},{1,1}};
		if (Math.abs(WeldUtil.evalPL(id,0.37)-0.37)>1e-12 ||
				Math.abs(WeldUtil.evalPL(hmap,0.3)-0.6)>1e-12 ||
				Math.abs(WeldUtil.evalPL(hmap,0.15)-0.3)>1e-12) {
			System.out.println("  FAIL: evalPL values wrong");
			failures++;
		}
		else
			System.out.println("  OK   evalPL identity/interp values");
	}

	static int firstBdry(PackData p) {
		for (int v=1;v<=p.nodeCount;v++)
			if (p.isBdry(v))
				return v;
		throw new RuntimeException("no bdry vertex");
	}
}
