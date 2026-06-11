package panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import exceptions.MiscException;
import input.TrafficCenter;
import packing.CPdrawing;
import packing.PackData;

/**
 * Panel containing the three (or more??) small packing images
 * and associated packing info.
 *
 * Click on a packing's header bar to make it the active packing.
 * Drag from one header bar and drop onto another to copy that packing.
 * An "undo" button appears in the destination header after a copy.
 */
public class SmallCanvasPanel extends JPanel {

	private static final long
	serialVersionUID = 1L;

	static Dimension cpSDim=new Dimension(PackControl.smallSide,PackControl.smallSide);
	static Dimension infoDim=new Dimension(PackControl.smallSide,25);
	static Dimension smallDim=new Dimension(PackControl.smallSide+2,PackControl.smallSide+25);

	static Color actColor=new Color(150,250,150); // sickly green for active pack
	static Color nonColor=new Color(200,200,200); // light grey for nonactive
	static Color dragHoverColor=new Color(100,180,255); // blue highlight during drag

	public JPanel []smallPanel;
	public JPanel []cpInfo;
	public JLabel []packName;
	public JButton []undoBtn;   // one per panel, hidden until a copy lands here
	public CPdrawing []ourScreens;

	// Drag-copy state
	private int dragSourceIdx = -1;
	private int dragHoverIdx  = -1;
	private boolean isDragging = false;
	private int pressX, pressY;
	private static final int DRAG_THRESHOLD = 5;

	// Undo state (only one level)
	private PackData undoPack    = null;
	private int      undoDestPack = -1;

	// Constructor
	public SmallCanvasPanel(CPdrawing []screens) {
		super();
		ourScreens=screens;
		smallPanel=new JPanel[CPBase.NUM_PACKS];
		cpInfo=new JPanel[CPBase.NUM_PACKS];
		packName=new JLabel[CPBase.NUM_PACKS];
		undoBtn=new JButton[CPBase.NUM_PACKS];

		// create individual panels
		try {
			for (int i=0;i<CPBase.NUM_PACKS;i++) {
				createSmall(i);
			}
		} catch (Exception ex) {
			throw new MiscException("Failed to create small canvases");
		}
		cpInfo[0].setBackground(actColor);
		cpInfo[0].setBorder(new LineBorder(Color.black,2,false));

		initGUI();
		addInteractions();
	}

	/**
	 * Each packing has a canvas surmounted by a panel for name and icons,
	 * also to be colored to indicate active.
	 */
	private void createSmall(int i) {
		smallPanel[i]=new JPanel(new BorderLayout());
		cpInfo[i]=new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));

		smallPanel[i].add(cpInfo[i],BorderLayout.NORTH);
		smallPanel[i].add(ourScreens[i],BorderLayout.CENTER);
		smallPanel[i].setBorder(new EmptyBorder(2,5,2,5));

		smallPanel[i].setPreferredSize(smallDim);
		smallPanel[i].setMaximumSize(smallDim);
		smallPanel[i].setMinimumSize(smallDim);
		smallPanel[i].setPreferredSize(smallDim);

		cpInfo[i].setPreferredSize(infoDim);
		cpInfo[i].setMaximumSize(infoDim);
		cpInfo[i].setMinimumSize(infoDim);
		cpInfo[i].setPreferredSize(infoDim);
		cpInfo[i].setBorder(new LineBorder(Color.BLACK,1,false));

		ourScreens[i].setPreferredSize(cpSDim);
		ourScreens[i].setMaximumSize(cpSDim);
		ourScreens[i].setMinimumSize(cpSDim);
		ourScreens[i].setPreferredSize(cpSDim);

		packName[i]=new JLabel();
		packName[i].setFont(new Font(packName[i].getFont().toString(),Font.ITALIC,9));
		packName[i].setText("P"+i+" empty");

		// Undo button: tiny, hidden until a copy lands on this panel
		undoBtn[i] = new JButton("↩");
		undoBtn[i].setFont(new Font(undoBtn[i].getFont().getName(), Font.PLAIN, 8));
		undoBtn[i].setToolTipText("Undo last copy into p" + i);
		undoBtn[i].setMargin(new java.awt.Insets(0,2,0,2));
		undoBtn[i].setFocusable(false);
		undoBtn[i].setVisible(false);
		final int fi = i;
		undoBtn[i].addActionListener(e -> undoCopy(fi));

		cpInfo[i].add(packName[i]);
		cpInfo[i].add(undoBtn[i]);
	}

	private void initGUI() {
		try {
			this.setLayout(new BoxLayout(this,BoxLayout.LINE_AXIS));
			for (int i=0;i<CPBase.NUM_PACKS;i++) {
				this.add(smallPanel[i]);
			}
			this.setBorder(new LineBorder(Color.black,2,false));
			this.validate();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Wire up click-to-activate and drag-to-copy on each cpInfo header bar.
	 */
	private void addInteractions() {
		for (int i = 0; i < CPBase.NUM_PACKS; i++) {
			final int idx = i;
			MouseAdapter ma = new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					dragSourceIdx = idx;
					isDragging = false;
					pressX = e.getXOnScreen();
					pressY = e.getYOnScreen();
				}

				@Override
				public void mouseDragged(MouseEvent e) {
					int dx = e.getXOnScreen() - pressX;
					int dy = e.getYOnScreen() - pressY;
					if (!isDragging &&
							(Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
						isDragging = true;
					}
					if (isDragging) {
						// Convert screen position to SmallCanvasPanel-relative coords
						Point pt = new Point(e.getXOnScreen(), e.getYOnScreen());
						SwingUtilities.convertPointFromScreen(pt, SmallCanvasPanel.this);
						int newHover = hitTest(pt);
						if (newHover != dragHoverIdx) {
							clearDragHighlight();
							dragHoverIdx = newHover;
							if (dragHoverIdx >= 0 && dragHoverIdx != dragSourceIdx) {
								cpInfo[dragHoverIdx].setBackground(dragHoverColor);
							}
						}
					}
				}

				@Override
				public void mouseReleased(MouseEvent e) {
					if (isDragging) {
						// Primary: re-test at actual release position (most reliable)
						Point pt = new Point(e.getXOnScreen(), e.getYOnScreen());
						SwingUtilities.convertPointFromScreen(pt, SmallCanvasPanel.this);
						int dest = hitTest(pt);
						// Fallback: use whatever hover was tracked during drag
						if (dest < 0) dest = dragHoverIdx;
						clearDragHighlight();
						if (dest >= 0 && dest != dragSourceIdx) {
							doCopyWithUndo(dragSourceIdx, dest);
						}
					} else {
						// plain click → activate this packing
						PackControl.switchActivePack(idx);
					}
					dragSourceIdx = -1;
					isDragging    = false;
				}
			};

			cpInfo[i].addMouseListener(ma);
			cpInfo[i].addMouseMotionListener(ma);
		}
	}

	/** Restore the hovered panel's normal background. */
	private void clearDragHighlight() {
		if (dragHoverIdx >= 0) {
			boolean active = (dragHoverIdx == CPBase.activePackNum);
			cpInfo[dragHoverIdx].setBackground(active ? actColor : nonColor);
		}
		dragHoverIdx = -1;
	}

	/**
	 * Return the index of the smallPanel containing point {@code pt}
	 * (in SmallCanvasPanel's own coordinate space), or -1.
	 */
	private int hitTest(Point pt) {
		for (int i = 0; i < CPBase.NUM_PACKS; i++) {
			if (smallPanel[i].getBounds().contains(pt)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Copy pack {@code src} into pack {@code dest}, saving dest's old state
	 * for a one-level undo.
	 */
	private void doCopyWithUndo(int src, int dest) {
		// Hide any previous undo button
		if (undoDestPack >= 0 && undoDestPack < CPBase.NUM_PACKS)
			undoBtn[undoDestPack].setVisible(false);
		undoPack     = null;
		undoDestPack = -1;

		// Save old dest state for undo — skip silently if dest is empty
		try {
			if (CPBase.packings[dest] != null && CPBase.packings[dest].nodeCount > 0) {
				undoPack     = CPBase.packings[dest].copyPackTo();
				undoDestPack = dest;
			}
		} catch (Exception ex) { /* empty dest — undo not available */ }

		// Do the copy (same as CirclePack 'copy' command)
		try {
			PackData copy = CPBase.packings[src].copyPackTo();
			CirclePack.cpb.swapPackData(copy, dest, false);

			// Redraw dest — packImage only updates via disp, not swapPackData
			TrafficCenter.cmdGUI(CPBase.cpDrawing[dest].getPackData(), "disp -w -c");
			CPBase.cpDrawing[dest].repaint();
			PackControl.activeFrame.activeScreen.repaint();

			// Show undo button only if we saved old state
			if (undoDestPack == dest) {
				undoBtn[dest].setVisible(true);
				cpInfo[dest].revalidate();
				cpInfo[dest].repaint();
			}
		} catch (Exception ex) {
			System.err.println("SmallCanvas copy FAILED: " + ex);
			ex.printStackTrace();
		}
	}

	/**
	 * Undo the last copy into pack {@code destIdx}.  Only valid immediately
	 * after a copy — there is one level of undo.
	 */
	private void undoCopy(int destIdx) {
		if (undoPack == null || undoDestPack != destIdx) return;
		CirclePack.cpb.swapPackData(undoPack, destIdx, false);
		TrafficCenter.cmdGUI(CPBase.cpDrawing[destIdx].getPackData(), "disp -w -c");
		CPBase.cpDrawing[destIdx].repaint();
		PackControl.activeFrame.activeScreen.repaint();
		undoPack     = null;
		undoDestPack = -1;
		undoBtn[destIdx].setVisible(false);
		cpInfo[destIdx].revalidate();
		cpInfo[destIdx].repaint();
	}

	/**
	 * Change active packing indicators, pack 'n' active.
	 */
	public void changeActive(int n) {
		if (n<0 || n>=CPBase.NUM_PACKS)
			return;
		for (int i=0;i<CPBase.NUM_PACKS;i++) {
			if (i==n) {
				cpInfo[i].setBackground(actColor);
				cpInfo[i].setBorder(new LineBorder(Color.black,1,false));
			}
			else {
				cpInfo[i].setBackground(nonColor);
				cpInfo[i].setBorder(new LineBorder(Color.black,1,false));
			}
		}
	}

}
