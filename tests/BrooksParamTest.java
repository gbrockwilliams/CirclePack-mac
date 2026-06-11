import ftnTheory.BrooksTorus;
import packing.PackData;

/**
 * Headless test of per-cell Brooks torus builds and the vertex->cell
 * tracking that backs click-to-reparameterize (BROOKSmode /
 * BrooksTorusExtender).
 * Run: java -cp "out:cpcore.jar:jars/*:tests" BrooksParamTest
 */
public class BrooksParamTest {

	static int failures=0;

	public static void main(String[] args) {
		uniformBuild();
		reparamCenterCell();
		reparamToEmpty();
		reparamSeveral();
		System.out.println("\n==== failures: "+failures+" ====");
	}

	static void check(boolean ok,String label) {
		System.out.println("  "+(ok?"OK  ":"FAIL")+" "+label);
		if (!ok) failures++;
	}

	/** count vertices mapped to 'cell' (-1 counts corners) */
	static int cellCount(int[] cellOf,int cell) {
		int n=0;
		for (int v=1;v<cellOf.length;v++)
			if (cellOf[v]==cell) n++;
		return n;
	}

	/**
	 * Torus sanity + per-cell census. Expected interior count of a
	 * cell is 1 (plug) + sum of its cfrac entries. Also: every
	 * interior vertex's petals stay within its own cell or corners.
	 */
	static void verify(String label,PackData p,int[] cellOf,
			int M,int N,int[][] cellCfrac) {
		check(p!=null,label+": build returned a packing");
		if (p==null) return;
		check(p.euler==0,label+": euler==0 (got "+p.euler+")");
		check(p.getBdryCompCount()==0,label+": no boundary");
		check(cellOf!=null && cellOf.length==p.nodeCount+1,
				label+": cellOf sized to nodeCount");
		if (cellOf==null || cellOf.length!=p.nodeCount+1) return;

		// corner census: torus identifies (M+1)(N+1) corners down to M*N
		check(cellCount(cellOf,-1)==M*N,
				label+": "+M*N+" shared corners (got "+
						cellCount(cellOf,-1)+")");

		// per-cell census
		int total=M*N;
		for (int c=0;c<M*N;c++) {
			int expect=1;
			for (int k=0;k<cellCfrac[c].length;k++)
				expect+=cellCfrac[c][k];
			total+=expect;
			int got=cellCount(cellOf,c);
			if (got!=expect)
				check(false,label+": cell "+c+" has "+got+
						" interior verts, expected "+expect);
		}
		check(p.nodeCount==total,
				label+": nodeCount=="+total+" (got "+p.nodeCount+")");

		// adjacency: interior verts of a cell only touch that cell
		// and corners
		int bad=0;
		for (int v=1;v<=p.nodeCount;v++) {
			if (cellOf[v]<0) continue;
			int[] petals=p.getPetals(v);
			for (int k=0;k<petals.length;k++) {
				int w=petals[k];
				if (cellOf[w]>=0 && cellOf[w]!=cellOf[v]) bad++;
			}
		}
		check(bad==0,label+": cell adjacency consistent ("+bad+
				" cross-cell edges)");
	}

	static void uniformBuild() {
		System.out.println("uniform 3x3, cfrac [2 1]:");
		int M=3,N=3;
		int[][] cellCfrac=BrooksTorus.uniformCfrac(M,N,new int[]{2,1});
		int[][] holder=new int[1][];
		PackData p=BrooksTorus.build(M,N,cellCfrac,holder);
		verify("uniform",p,holder[0],M,N,cellCfrac);
	}

	static void reparamCenterCell() {
		System.out.println("3x3 [2 1], center cell -> [3 2]:");
		int M=3,N=3;
		int[][] cellCfrac=BrooksTorus.uniformCfrac(M,N,new int[]{2,1});
		cellCfrac[4]=new int[]{3,2}; // cell (1,1)
		int[][] holder=new int[1][];
		PackData p=BrooksTorus.build(M,N,cellCfrac,holder);
		verify("center",p,holder[0],M,N,cellCfrac);
	}

	static void reparamToEmpty() {
		System.out.println("3x3 [2 1], cell (0,0) -> [] (plain quad):");
		int M=3,N=3;
		int[][] cellCfrac=BrooksTorus.uniformCfrac(M,N,new int[]{2,1});
		cellCfrac[0]=new int[0];
		int[][] holder=new int[1][];
		PackData p=BrooksTorus.build(M,N,cellCfrac,holder);
		verify("empty",p,holder[0],M,N,cellCfrac);
	}

	static void reparamSeveral() {
		System.out.println("4x3, all cells distinct:");
		int M=4,N=3;
		int[][] cellCfrac=new int[M*N][];
		for (int c=0;c<M*N;c++) {
			cellCfrac[c]=new int[(c%3)+1];
			for (int k=0;k<cellCfrac[c].length;k++)
				cellCfrac[c][k]=(c%4)+1;
		}
		int[][] holder=new int[1][];
		PackData p=BrooksTorus.build(M,N,cellCfrac,holder);
		verify("distinct",p,holder[0],M,N,cellCfrac);
	}

}
