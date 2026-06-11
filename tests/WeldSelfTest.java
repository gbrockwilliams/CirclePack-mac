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

	static int firstBdry(PackData p) {
		for (int v=1;v<=p.nodeCount;v++)
			if (p.isBdry(v))
				return v;
		throw new RuntimeException("no bdry vertex");
	}
}
