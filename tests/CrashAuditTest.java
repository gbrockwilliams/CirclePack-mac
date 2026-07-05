import allMains.CPBase;
import allMains.CirclePack;
import circlePack.ShellControl;
import input.CommandStrParser;
import packing.PackCreation;
import packing.PackData;

/**
 * Unexpected-state crash audit (2026-07-05), kept as a regression
 * test. Fires data-side commands at (a) an empty pack and (b) a
 * valid pack with garbage arguments. A command may fail loudly
 * (return <=0, or throw a CirclePack exception with a message) but
 * must NOT raise a raw JVM fault (NPE, index-out-of-bounds, class
 * cast, arithmetic). Display commands are excluded: headless they
 * fault on the null cpDrawing, which cannot happen in the GUI.
 * Run: java -cp "tests:out:cpcore.jar:jars/*" CrashAuditTest
 */
public class CrashAuditTest {

	static int failures=0;

	static final String[] EMPTY_PACK_CMDS={
		"repack","layout","max_pack",
		"geom_to_e","geom_to_h","geom_to_s",
		"add_gen 3","add_layer 1 2","add_cir 1","add_edge 1 2",
		"alpha 5","gamma 3",
		"set_rad .3 a","set_aim -d a","set_center 0.0 0.0 a",
		"norm_scale -u 2","scale 2","rotate .25",
		"count -v a","count -f a",
		"flip 1 2","rm_cir 1","rm_edge 1 2","puncture 1",
		"hex_refine","add_bary f","double 1","prune",
		"enfold 1","split_edge 1 2",
		"adjoin 1 2 1 1 3",
		"torus_t","normalize_torus","tile_torus 1","undo",
		"weld_arcs 1 2 -q1 1 2",
		"cookie","slit 1 2","proj 1","spiral 1 1",
		"mark -v a","color -c 5 a","set_vlist a","vert_map",
		"write /tmp/nothing.p","Write /tmp/nothing.p",
		"pack_info","fexec bogus"
	};

	static final String[] BAD_ARGS_CMDS={
		"alpha 9999","alpha xyz","gamma 9999",
		"flip 9999 1","flip 1 9999","flip 1 1",
		"puncture 9999","rm_cir 9999","rm_edge 9999 1",
		"set_rad .5 9999","set_aim 3.14 9999",
		"split_edge 9999 1","split_edge 1 4",
		"add_cir 9999","add_edge 1 3","add_edge 9999 1",
		"add_layer 4 9999","enfold 9999","double 9999",
		"slit 9999 1","slit 1 9999",
		"adjoin 0 1 9999 1 3","adjoin 0 5 1 1 3",
		"weld_arcs 9999 1 -q0 1 2","weld_arcs 1 2 -q9 3 4",
		"count -v 9999","norm_scale -h 9999 1",
		"swap 1 9999","vert_map 9999",
		"tile_torus 1","normalize_torus","torus_t"
	};

	public static void main(String[] args) {
		CirclePack.cpb=new ShellControl();
		CPBase.packings=new PackData[CPBase.NUM_PACKS];
		for (int i=0;i<CPBase.NUM_PACKS;i++)
			CPBase.packings[i]=new PackData(i);

		System.out.println("empty-pack battery:");
		for (String c : EMPTY_PACK_CMDS)
			fire(CPBase.packings[0],c);

		System.out.println("bad-arguments battery (fresh valid pack each):");
		for (String c : BAD_ARGS_CMDS) {
			PackData seed=PackCreation.seed(6,0);
			seed.status=true;
			CirclePack.cpb.swapPackData(seed,0,false);
			CommandStrParser.jexecute(seed,"add_gen 6 6");
			CommandStrParser.jexecute(seed,"max_pack");
			fire(CPBase.packings[0],c);
		}
		System.out.println("\n==== failures: "+failures+" ====");
	}

	static void fire(PackData p,String c) {
		try {
			CommandStrParser.jexecute(p,c);
		} catch (NullPointerException|IndexOutOfBoundsException|
				ClassCastException|ArithmeticException|
				NegativeArraySizeException ex) {
			StackTraceElement st=ex.getStackTrace().length>0?
					ex.getStackTrace()[0]:null;
			System.out.println("  CRASH "+ex.getClass().getSimpleName()
					+"  '"+c+"'   at "+st);
			failures++;
		} catch (Throwable ex) {
			// loud failure with a message: acceptable
		}
	}
}
