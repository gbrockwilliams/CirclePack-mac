package packing;

import allMains.CPBase;
import allMains.CirclePack;
import input.TrafficCenter;

/**
 * One-deep undo for destructive packing operations (weld/adjoin,
 * Brooks reparameterization, etc.). An operation saves the affected
 * packing(s) BEFORE changing anything; the 'undo' command restores
 * them all at once. Only the most recent saved operation is kept;
 * a new save replaces the previous record.
 *
 * Two ways for an operation to save a packing:
 *  - a reference, when the operation REPLACES the PackData object
 *    in its slot without mutating it (e.g. rebuild-from-scratch, as
 *    in Brooks reparameterization);
 *  - a copy ('copyPackTo'), when the operation mutates the packing
 *    in place (e.g. boundary refinement before welding).
 *
 * The optional Runnable hook runs after the packings are swapped
 * back, for fixing auxiliary state (e.g. PackExtender fields).
 */
public class PackUndo {

	static String opName=null;     // operation label, e.g. "weld"
	static int[] pnums=null;       // affected pack slots
	static PackData[] olds=null;   // pre-operation packings
	static Runnable postHook=null; // auxiliary state restoration

	/**
	 * Record the pre-operation state. Call BEFORE any mutation.
	 * @param op String, short label shown by 'undo'
	 * @param packNums int[], slots of the affected packings
	 * @param oldPacks PackData[], pre-op packings (refs or copies)
	 * @param hook Runnable or null, run after restore
	 */
	public static void save(String op,int[] packNums,
			PackData[] oldPacks,Runnable hook) {
		opName=op;
		pnums=packNums;
		olds=oldPacks;
		postHook=hook;
	}

	public static void clear() {
		opName=null;
		pnums=null;
		olds=null;
		postHook=null;
	}

	public static boolean pending() {
		return olds!=null;
	}

	public static String getOpName() {
		return opName;
	}

	/**
	 * Restore the packings saved by the last operation, run the
	 * hook, redraw. The record is consumed (one-deep).
	 * @return int, count of packings restored
	 */
	public static int restore() {
		if (olds==null)
			return 0;
		int cnt=0;
		for (int k=0;k<olds.length;k++) {
			if (olds[k]==null || pnums[k]<0 ||
					pnums[k]>=CPBase.NUM_PACKS)
				continue;
			// keepX keeps the slot's PackExtenders attached
			CirclePack.cpb.swapPackData(olds[k],pnums[k],true);
			cnt++;
		}
		if (postHook!=null) {
			try {
				postHook.run();
			} catch (Exception ex) {
				CirclePack.cpb.errMsg("undo: state hook failed: "+
						ex.getMessage());
			}
		}
		int[] pn=pnums;
		clear();
		// redraw the restored packings
		for (int k=0;k<pn.length;k++) {
			if (pn[k]<0 || pn[k]>=CPBase.NUM_PACKS)
				continue;
			try {
				TrafficCenter.cmdGUI(CPBase.packings[pn[k]],
						"disp -w -c");
				CPBase.cpDrawing[pn[k]].repaint();
			} catch (Exception ex) {}
		}
		return cnt;
	}

}
