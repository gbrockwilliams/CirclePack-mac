package util;

import java.awt.Component;
import java.awt.Cursor;

import javax.swing.RootPaneContainer;

import circlePack.PackControl;

/**
 * Show the system wait cursor on CirclePack's windows while a
 * long operation runs. The wait cursor is rendered by the OS, so
 * it stays visible (and on macOS, animates) even when the Swing
 * event thread itself is blocked by the operation.
 *
 * The cursor is put on each frame's glass pane; a plain glass
 * pane with no mouse listeners shows its cursor over every child
 * component (including canvasses with their own mode cursors)
 * while letting events pass through.
 *
 * Usage: call 'push()' before the work and 'pop()' in a finally
 * block. Calls nest — the cursor is restored when the outermost
 * 'pop()' runs. Both are no-ops when running headless.
 */
public class BusyCursor {

	private static int depth=0;

	public static synchronized void push() {
		if (depth++==0)
			showBusy(true);
	}

	public static synchronized void pop() {
		if (depth>0 && --depth==0)
			showBusy(false);
	}

	private static void showBusy(boolean busy) {
		try {
			setOn(PackControl.activeFrame,busy);
			setOn(PackControl.mapPairFrame,busy);
			setOn(PackControl.frame,busy);
		} catch (Exception ex) {} // headless or frames not up yet
	}

	private static void setOn(Object frame,boolean busy) {
		if (frame==null || !(frame instanceof RootPaneContainer))
			return;
		Component gp=((RootPaneContainer)frame).getGlassPane();
		if (busy) {
			gp.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			gp.setVisible(true);
		}
		else {
			gp.setVisible(false);
			gp.setCursor(Cursor.getDefaultCursor());
		}
	}

}
