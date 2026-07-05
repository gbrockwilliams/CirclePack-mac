import allMains.CPBase;
import allMains.CirclePack;
import circlePack.ShellControl;
import exceptions.ExtenderException;
import input.CommandStrParser;
import packing.PackCreation;
import packing.PackData;

/**
 * Headless test of the empty-pack guard in PackExtender's
 * constructor: attaching any extender to an empty or unloaded
 * packing must throw ExtenderException BEFORE the derived
 * constructor touches pack data (previously most crashed with
 * assorted NPE/array errors). Valid packs must still attach.
 * Run: java -cp "tests:out:cpcore.jar:jars/*" ExtenderGuardTest
 */
public class ExtenderGuardTest {

	static int failures=0;

	public static void main(String[] args) {
		CirclePack.cpb=new ShellControl();
		CPBase.packings=new PackData[CPBase.NUM_PACKS];
		for (int i=0;i<CPBase.NUM_PACKS;i++)
			CPBase.packings[i]=new PackData(i);

		emptyPackDirect();
		emptyPackCommand();
		validPackStillWorks();
		System.out.println("\n==== failures: "+failures+" ====");
	}

	static void check(boolean ok,String label) {
		System.out.println("  "+(ok?"OK  ":"FAIL")+" "+label);
		if (!ok) failures++;
	}

	/** direct construction on an empty pack: guard must fire for
	 * every extender, including ones whose constructor bodies are
	 * not headless-safe (the throw precedes the derived body) */
	static void emptyPackDirect() {
		System.out.println("direct construction on empty pack:");
		PackData empty=new PackData(null);
		tryOne("WeldManager",empty,
				() -> new ftnTheory.WeldManager(empty));
		tryOne("BrooksQuad",empty,
				() -> new ftnTheory.BrooksQuad(empty));
		tryOne("HypDensity",empty,
				() -> new ftnTheory.HypDensity(empty));
		tryOne("ShapeShifter",empty,
				() -> new ftnTheory.ShapeShifter(empty));
		tryOne("SphereLayout",empty,
				() -> new ftnTheory.SphereLayout(empty));
		tryOne("null pack",null,
				() -> new ftnTheory.WeldManager(null));
	}

	static void tryOne(String label,PackData p,Runnable ctor) {
		try {
			ctor.run();
			check(false,label+": expected ExtenderException, got none");
		} catch (ExtenderException ex) {
			check(true,label+": ExtenderException ("+ex.getMessage()+")");
		} catch (Exception ex) {
			check(false,label+": wrong exception "+
					ex.getClass().getSimpleName()+": "+ex.getMessage());
		}
	}

	/** 'extender cw' command on an empty slot: the exception must
	 * surface (TrafficCenter reports it in the GUI), not a raw
	 * NPE/array crash */
	static void emptyPackCommand() {
		System.out.println("'extender cw' command on empty slot:");
		try {
			int ret=CommandStrParser.jexecute(
					CPBase.packings[1],"extender cw");
			check(ret<=0,"returned failure ("+ret+") without attaching");
		} catch (ExtenderException ex) {
			check(true,"ExtenderException surfaced: "+ex.getMessage());
		} catch (Exception ex) {
			check(false,"wrong exception "+
					ex.getClass().getSimpleName()+": "+ex.getMessage());
		}
		check(CPBase.packings[1].packExtensions.size()==0,
				"no extender attached to empty pack");
	}

	/** valid pack: attach via command, use it, kill it */
	static void validPackStillWorks() {
		System.out.println("valid pack still attaches:");
		PackData seed=PackCreation.seed(6,0);
		seed.status=true;
		CirclePack.cpb.swapPackData(seed,0,false);
		PackData p=CPBase.packings[0];
		int ret=CommandStrParser.jexecute(p,"extender cw");
		check(ret==1,"'extender cw' attached (ret="+ret+")");
		check(p.packExtensions.size()==1,
				"one extender registered on the pack");
		ret=CommandStrParser.jexecute(p,"extender -x cw");
		check(ret==1 && p.packExtensions.size()==0,
				"'extender -x cw' killed it (headless updateXtenders "+
				"guard held)");
	}
}
