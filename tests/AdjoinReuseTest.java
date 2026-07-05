import allMains.CPBase;
import allMains.CirclePack;
import circlePack.ShellControl;
import dcel.CombDCEL;
import input.CommandStrParser;
import packing.PackCreation;
import packing.PackData;

/**
 * Regression test for the 2026-07-05 CombDCEL.adjoin fix: adjoin
 * absorbs its second argument (nulling 'halfedge's, reindexing), so
 * it now clones pdc2 internally when pdc2!=pdc1. Before the fix,
 * every PackCreation routine that passed the same DCEL as pdc2 more
 * than once (adjoin3/4/5, pentHypTiling, fibonacci2D) crashed with
 * an NPE on the second use ("Cannot read field myRedEdge"), which
 * made the user commands 'create dyadic {n>1}' and 'create fib'
 * crash. Two companion fixes: fibonacci2D calls fixDCEL after each
 * adjoin (stale nodeCount tripped bdryCompVerts' safety counter:
 * "bdry component doesn't seem to close up"), and adjoinFace
 * captures vertexMap from oldNew (NPE in 'necklace').
 * Run: java -cp "tests:out:cpcore.jar:jars/*" AdjoinReuseTest
 */
public class AdjoinReuseTest {

	static int failures=0;

	public static void main(String[] args) {
		CirclePack.cpb=new ShellControl();
		CPBase.packings=new PackData[CPBase.NUM_PACKS];
		for (int i=0;i<CPBase.NUM_PACKS;i++)
			CPBase.packings[i]=new PackData(i);

		pdc2Intact();
		directBuilders();
		userCommands();

		System.out.println("\n==== failures: "+failures+" ====");
	}

	static void check(boolean ok,String label) {
		System.out.println("  "+(ok?"OK  ":"FAIL")+" "+label);
		if (!ok) failures++;
	}

	/**
	 * The core contract: pdc2 must survive adjoin untouched,
	 * so the same packing can be adjoined repeatedly.
	 */
	static void pdc2Intact() {
		System.out.println("pdc2 survives adjoin:");
		PackData pent=PackCreation.seed(5,0);
		pent.packDCEL.swapNodes(1,6);
		PackData base=pent.copyPackTo();

		// adjoin the SAME pent DCEL twice (pre-fix: 2nd threw NPE)
		try {
			base.packDCEL=CombDCEL.adjoin(base.packDCEL,pent.packDCEL,3,5,1);
			base.packDCEL.fixDCEL(base);
			boolean intact=true;
			for (int v=1;v<=pent.nodeCount;v++)
				if (pent.packDCEL.vertices[v].halfedge==null)
					intact=false;
			check(intact,"pdc2 halfedges all non-null after 1st adjoin");
			base.packDCEL=CombDCEL.adjoin(base.packDCEL,pent.packDCEL,4,5,1);
			base.packDCEL.fixDCEL(base);
			check(base.nodeCount>pent.nodeCount,
					"2nd adjoin of same pdc2 succeeded (n="+base.nodeCount+")");
		} catch(Throwable t) {
			check(false,"repeated adjoin threw "+t);
		}
	}

	/** PackCreation builders that reuse a pdc2 internally. */
	static void directBuilders() {
		System.out.println("PackCreation builders (all crashed pre-fix):");
		try {
			PackData p=PackCreation.pentTiling(3);
			check(p!=null && p.euler==1,"pentTiling(3): euler==1");
		} catch(Throwable t) { check(false,"pentTiling(3) threw "+t); }
		try {
			PackData p=PackCreation.pentHypTiling(3);
			check(p!=null && p.euler==1,"pentHypTiling(3): euler==1");
		} catch(Throwable t) { check(false,"pentHypTiling(3) threw "+t); }
		try {
			PackData p=PackCreation.pent3Expander(3);
			check(p!=null && p.euler==1,"pent3Expander(3): euler==1");
		} catch(Throwable t) { check(false,"pent3Expander(3) threw "+t); }
		try {
			PackData p=PackCreation.pent4Expander(3);
			check(p!=null && p.euler==1,"pent4Expander(3): euler==1");
		} catch(Throwable t) { check(false,"pent4Expander(3) threw "+t); }
		try {
			PackData p=PackCreation.fibonacci2D(3,1,1,1);
			check(p!=null && p.euler==1,"fibonacci2D(3,1,1,1): euler==1");
		} catch(Throwable t) { check(false,"fibonacci2D(3,1,1,1) threw "+t); }
	}

	/** User-facing commands, incl. unaffected adjoin paths. */
	static void userCommands() {
		System.out.println("user commands:");
		cmdCheck("create dyadic 3",51);   // crashed pre-fix for n>1
		cmdCheck("create fib 3",25);      // crashed pre-fix
		cmdCheck("necklace 12",-1);       // NPE pre-fix (random size)
		cmdCheck("create hex_tor 4 5",20);      // self-adjoin unaffected
		cmdCheck("create brooks_torus 3 3 2 1",45); // adjoinCall unaffected
	}

	static void cmdCheck(String cmd,int expectN) {
		try {
			int r=CommandStrParser.jexecute(CPBase.packings[0],cmd);
			boolean ok=r>0 && CPBase.packings[0].nodeCount>3
					&& (expectN<0 || CPBase.packings[0].nodeCount==expectN);
			check(ok,cmd+" -> nodeCount="+CPBase.packings[0].nodeCount
					+(expectN>=0?" (expect "+expectN+")":""));
		} catch(Throwable t) {
			check(false,cmd+" threw "+t);
		}
	}
}
