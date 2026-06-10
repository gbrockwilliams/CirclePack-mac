package dragdrop;

import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import allMains.CPBase;
import allMains.CirclePack;
import canvasses.MyCanvasMode;
import circlePack.PackControl;
import mytools.MyTool;
import packing.CPdrawing;


/**
 * For MyTool drag/drop operation. This is the listener for the targets,
 * which are currently the active canvas, the three smaller canvasses,
 * and the canvasses of PairFrame.
 * WhichPackFlag true means that the packing number must be determined
 * from the target panel, so we have to search for which panel.
 * Also handles packing file drops (.p, .q, .off, .pl) from Finder.
 * @author kens
 *
 */
public class ToolDropListener implements DropTargetListener {

	private JPanel theCanvas;
	private String theKey;
	private int thePackNum;
	private boolean whichPackFlag;

	// Constructor
	public ToolDropListener(JPanel canvas,int packnum,boolean active) {
		theCanvas=canvas;
		thePackNum=packnum;
		whichPackFlag=active;
	}

	public void dragEnter(DropTargetDragEvent event) {
		if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
			event.acceptDrag(DnDConstants.ACTION_COPY);
	}

	public void dragExit(DropTargetEvent event) {}

	public void dragOver(DropTargetDragEvent event) {}

	public void dropActionChanged(DropTargetDragEvent event) {}

	public void drop(DropTargetDropEvent event) {

		// Packing file dropped from Finder (.p, .q, .off, .pl)
		if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
			event.acceptDrop(DnDConstants.ACTION_COPY);
			try {
				@SuppressWarnings("unchecked")
				List<File> files = (List<File>)
					event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
				for (File f : files) {
					String name = f.getName().toLowerCase();
					if (name.endsWith(".p") || name.endsWith(".q")
							|| name.endsWith(".off") || name.endsWith(".pl")
							|| name.endsWith(".g")) {
						final int pn = resolvePackNum();
						final String path = f.getAbsolutePath();
						// .g files are path files; others are packing files
						final String cmd = name.endsWith(".g")
							? "Read_p " + path + "; disp -w -g"
							: "Read " + path + "; disp -w -c";
						SwingUtilities.invokeLater(() ->
							CPBase.trafficCenter.parseWrapper(
								cmd,
								CPBase.cpDrawing[pn].getPackData(),
								false, true, 0, null));
						event.dropComplete(true);
						return;
					}
				}
			} catch (Exception e) {}
			event.dropComplete(false);
			return;
		}

		// Existing: MyTool drag from toolbar onto canvas
		if (!isDropOK(event)) {
			event.rejectDrop();
			return;
		}
		event.acceptDrop(DnDConstants.ACTION_LINK);
		Transferable transferable = event.getTransferable();
		theKey=null;
		try {
			theKey=(String)transferable.getTransferData(DataFlavor.stringFlavor);
		} catch(Exception e) {}
		if (theKey==null) return; // some failure

		MyTool mytool=(MyTool)CPBase.hashedTools.get(theKey);
		if (mytool!=null) {
			if (mytool instanceof MyCanvasMode) { // just change canvas mode
				if (theCanvas.equals(PackControl.activeFrame.activeScreen)) {
				PackControl.activeFrame.activeScreen.
						activeMode=(MyCanvasMode)mytool;
				}
				else if (theCanvas.equals(PackControl.mapPairFrame.getDomainCPS())) {
					PackControl.mapPairFrame.domainScreen.activeMode=(MyCanvasMode)mytool;
				}
				else if (theCanvas.equals(PackControl.mapPairFrame.getRangeCPS())) {
					PackControl.mapPairFrame.rangeScreen.activeMode=(MyCanvasMode)mytool;
				}
				return;
			}
			int pn = resolvePackNum();
			// check command for variables '#..': Currently check only ' #XY'
			if (mytool.getCommand().contains(" #XY") || mytool.getCommand().contains(" #xy")) {
				Point pt=event.getLocation();
				CPdrawing cpS=CPBase.cpDrawing[pn];
				Point2D.Double pot=cpS.pt2RealPt(pt, theCanvas.getWidth(),theCanvas.getHeight());
				String subxy=new String(" "+pot.x+" "+pot.y+" ");
				String newCmd=mytool.getCommand().replaceAll(" #XY",subxy).replaceAll(" #xy",subxy);
				CPBase.trafficCenter.parseWrapper(newCmd,
						CPBase.cpDrawing[pn].getPackData(),false,false,0,null);
				return;
			}
			mytool.execute(CPBase.cpDrawing[pn].getPackData());
		}
	}

	/** Resolve which pack number this canvas corresponds to. */
	private int resolvePackNum() {
		if (!whichPackFlag) return thePackNum;
		if (theCanvas.equals(PackControl.activeFrame.activeScreen))
			return CirclePack.cpb.getActivePackData().packNum;
		if (theCanvas.equals(PackControl.mapPairFrame.getDomainCPS()))
			return PackControl.mapPairFrame.getDomainNum();
		if (theCanvas.equals(PackControl.mapPairFrame.getRangeCPS()))
			return PackControl.mapPairFrame.getRangeNum();
		return thePackNum;
	}

	public boolean isDropOK(DropTargetDropEvent event) {
		return (event.getDropAction() & DnDConstants.ACTION_LINK)!=0;
	}
}
