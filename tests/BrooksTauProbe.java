import allMains.CPBase;
import allMains.CirclePack;
import circlePack.ShellControl;
import complex.Complex;
import input.CommandStrParser;
import packing.PackData;
import packing.TorusData;

/**
 * Headless tau computation for Brooks tori: builds
 * 'create brooks_torus 3 3 {cfrac}' variants, repacks hard, and
 * reports teich (actual layout period) and tau (PSL(2,Z) modular
 * representative) for each.
 */
public class BrooksTauProbe {
	public static void main(String[] args) {
		CirclePack.cpb=new ShellControl();
		CPBase.packings=new PackData[CPBase.NUM_PACKS];
		for (int i=0;i<CPBase.NUM_PACKS;i++)
			CPBase.packings[i]=new PackData(i);

		String[] variants={
				"",       // plain quad cells
				"1",
				"2",
				"3",
				"0 3",    // transposed [3]: horizontals first
				"2 1"
		};
		Complex teich3=null,teich03=null;
		for (String cf : variants) {
			String cmd="create brooks_torus 3 3"+(cf.isEmpty()?"":" "+cf);
			int ret=CommandStrParser.jexecute(CPBase.packings[0],cmd);
			PackData p=CPBase.packings[0];
			if (ret<=0) {
				System.out.println("cfrac ["+cf+"]: build FAILED");
				continue;
			}
			p.repack_call(30000); // squeeze angle-sum error down
			CommandStrParser.jexecute(p,"layout");
			try {
				TorusData td=new TorusData(p);
				System.out.println("cfrac ["+cf+"]: nodeCount="+p.nodeCount
						+"  teich="+td.teich.x+" + "+td.teich.y+"i"
						+"  tau="+td.tau.x+" + "+td.tau.y+"i"
						+(td.flat?"":"  (AFFINE)"));
				if (cf.equals("3")) teich3=td.teich;
				if (cf.equals("0 3")) teich03=td.teich;
			} catch (Exception ex) {
				System.out.println("cfrac ["+cf+"]: TorusData failed: "+ex);
			}
		}
		if (teich3!=null && teich03!=null) {
			Complex inv=new Complex(-1.0,0.0).divide(teich3);
			System.out.println("\ncheck: -1/teich[3] = "+inv.x+" + "+inv.y
					+"i   vs teich[0 3] = "+teich03.x+" + "+teich03.y+"i");
		}
	}
}
