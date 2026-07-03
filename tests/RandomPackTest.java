import allMains.CirclePack;
import circlePack.ShellControl;
import geometry.CommonMath;
import packing.PackData;
import random.RandomTriangulation;
import util.UtilPacket;

/**
 * Headless test of 'create random N' (RandomTriangulation.randomDiscPack):
 * Poisson points in the unit disc + random points on the unit circle,
 * Delaunay triangulation, random euclidean boundary radii in
 * [1-jitter,1+jitter]*(2pi/bdryN) (default jitter 0.25, '-j {pct}'
 * flag), then repack.
 * Run: java -cp "tests:out:cpcore.jar:jars/*" RandomPackTest
 */
public class RandomPackTest {

	static int failures=0;

	public static void main(String[] args) {
		// CPBase constructor extracts the 'triangle' binary that
		// the Delaunay call shells out to
		CirclePack.cpb=new ShellControl();

		buildAndRepack(60);
		buildAndRepack(200);
		explicitBdryCount();
		jitterVariants();
		System.out.println("\n==== failures: "+failures+" ====");
	}

	static void check(boolean ok,String label) {
		System.out.println("  "+(ok?"OK  ":"FAIL")+" "+label);
		if (!ok) failures++;
	}

	static int bdryCount(PackData p) {
		int n=0;
		for (int v=1;v<=p.nodeCount;v++)
			if (p.isBdry(v)) n++;
		return n;
	}

	/** max |anglesum - 2pi| over interior vertices */
	static double worstCurve(PackData p) {
		UtilPacket uP=new UtilPacket();
		double worst=0.0;
		for (int v=1;v<=p.nodeCount;v++) {
			if (p.isBdry(v)) continue;
			if (!CommonMath.get_anglesum(p,v,p.getRadius(v),uP))
				return Double.MAX_VALUE;
			double err=Math.abs(uP.value-2.0*Math.PI);
			if (err>worst) worst=err;
		}
		return worst;
	}

	/** true iff every bdry radius is in [1-jitter,1+jitter]*(2pi/bdryN) */
	static boolean radiiInBand(PackData p,double jitter) {
		double base=2.0*Math.PI/(double)bdryCount(p);
		for (int v=1;v<=p.nodeCount;v++) {
			if (!p.isBdry(v)) continue;
			double r=p.getRadius(v);
			if (r<(1.0-jitter)*base-1e-12 || r>(1.0+jitter)*base+1e-12)
				return false;
		}
		return true;
	}

	static void buildAndRepack(int N) {
		System.out.println("build N="+N+":");
		PackData p=RandomTriangulation.randomDiscPack(N,0,0.25,false);
		check(p!=null,"randomDiscPack returned a packing");
		if (p==null) return;
		check(p.hes==0,"euclidean (hes==0)");
		check(p.euler==1,"euler==1, a disc (got "+p.euler+")");
		check(p.getBdryCompCount()==1,"one boundary component");
		// N interior requested minus bdryN, plus bdryN on the circle;
		// Delaunay keeps them all, so nodeCount should be N exactly
		check(Math.abs(p.nodeCount-N)<=1,
				"nodeCount ~ N (got "+p.nodeCount+")");
		int bn=bdryCount(p);
		int expect=(int)Math.round(Math.PI*Math.sqrt((double)N));
		check(bn==expect,"bdry count "+bn+" == pi*sqrt(N) ("+expect+")");

		check(radiiInBand(p,0.25),
				"bdry radii in [0.75,1.25]*(2pi/bdryN)");

		int cycles=p.repack_call(2000);
		check(cycles>=0,"repack ran ("+cycles+" cycles)");
		double worst=worstCurve(p);
		check(worst<0.001,"repacked angle sums flat (worst err "
				+worst+")");
		try {
			p.packDCEL.layoutPacking();
			check(true,"layout succeeded");
		} catch (Exception ex) {
			check(false,"layout threw: "+ex.getMessage());
		}
	}

	static void explicitBdryCount() {
		System.out.println("explicit bdryN=20, N=100:");
		PackData p=RandomTriangulation.randomDiscPack(100,20,0.25,false);
		check(p!=null,"randomDiscPack returned a packing");
		if (p==null) return;
		check(bdryCount(p)==20,"bdry count == 20 (got "
				+bdryCount(p)+")");
		check(p.nodeCount==100,"nodeCount == 100 (got "
				+p.nodeCount+")");
	}

	static void jitterVariants() {
		System.out.println("jitter variants, N=100:");
		// jitter 0: every bdry radius exactly 2pi/bdryN
		PackData p=RandomTriangulation.randomDiscPack(100,0,0.0,false);
		check(p!=null,"jitter 0 returned a packing");
		if (p!=null)
			check(radiiInBand(p,0.0),"jitter 0: radii == 2pi/bdryN");
		// jitter 0.5: wider band
		p=RandomTriangulation.randomDiscPack(100,0,0.5,false);
		check(p!=null,"jitter 0.5 returned a packing");
		if (p!=null)
			check(radiiInBand(p,0.5),"jitter 0.5: radii in "
					+"[0.5,1.5]*(2pi/bdryN)");
		// out-of-range jitter falls back to the 0.25 default
		p=RandomTriangulation.randomDiscPack(100,0,1.5,false);
		check(p!=null,"jitter 1.5 returned a packing");
		if (p!=null)
			check(radiiInBand(p,0.25),"jitter 1.5 falls back to 0.25");
	}
}
