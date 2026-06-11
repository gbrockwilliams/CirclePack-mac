package script;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.geom.Rectangle2D;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.TransferHandler;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;

/**
 * Plain-text script editor. One command per line; blank lines are skipped.
 * Replaces the drag-and-drop tree editor as the default script view.
 */
public class SimpleScriptPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	public JTextArea textArea;
	private JScrollPane scrollPane;
	private int nextLineIndex = 0;
	private Object currentHighlight = null;
	private boolean loading = false; // true while loadFromTree is populating the text area
	private final UndoManager undoManager = new UndoManager();

	private static final Highlighter.HighlightPainter HIGHLIGHT =
		new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 255, 150));

	// Find bar state
	private JPanel findBar;
	private JTextField findField;
	private JLabel findStatus;
	private int lastFindPos = -1; // start of last found match, -1 if none yet

	public SimpleScriptPanel() {
		super(new BorderLayout());
		textArea = new JTextArea();
		textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
		textArea.setLineWrap(false);
		textArea.setTabSize(4);

		// Undo/redo: record edits, but not programmatic loads
		textArea.getDocument().addUndoableEditListener(undoManager);

		textArea.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e)  { markChanged(); }
			public void removeUpdate(DocumentEvent e)  { markChanged(); }
			public void changedUpdate(DocumentEvent e) {}
			private void markChanged() {
				if (loading) return;
				CPBase.scriptManager.hasChanged = true;
				SwingUtilities.invokeLater(() -> { highlightCurrentLine(); syncButtons(); });
			}
		});

		scrollPane = new JScrollPane(textArea);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		add(scrollPane, BorderLayout.CENTER);

		// Keyboard shortcuts
		int meta = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		KeyStroke undo  = KeyStroke.getKeyStroke(KeyEvent.VK_Z, meta);
		KeyStroke redo  = KeyStroke.getKeyStroke(KeyEvent.VK_Z, meta | InputEvent.SHIFT_DOWN_MASK);
		KeyStroke find  = KeyStroke.getKeyStroke(KeyEvent.VK_F, meta);
		KeyStroke esc   = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);

		textArea.getInputMap().put(undo, "undo");
		textArea.getInputMap().put(redo, "redo");
		textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, meta), "redo"); // Cmd+Y alias
		textArea.getInputMap().put(find, "openFind");
		textArea.getInputMap().put(esc,  "closeFindBar");

		textArea.getActionMap().put("undo", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				try { undoManager.undo(); } catch (CannotUndoException ignored) {}
			}
		});
		textArea.getActionMap().put("redo", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				try { undoManager.redo(); } catch (CannotRedoException ignored) {}
			}
		});
		textArea.getActionMap().put("openFind", new AbstractAction() {
			public void actionPerformed(ActionEvent e) { showFindBar(); }
		});
		textArea.getActionMap().put("closeFindBar", new AbstractAction() {
			public void actionPerformed(ActionEvent e) {
				if (findBar.isVisible()) hideFindBar();
			}
		});

		findBar = buildFindBar();
		add(findBar, BorderLayout.SOUTH);
		findBar.setVisible(false);

		// File drop on the text area: use TransferHandler so .cps/.xml files dropped from
		// Finder load the script, while non-file drops (text from other apps) still work.
		TransferHandler defaultTH = textArea.getTransferHandler();
		textArea.setTransferHandler(new TransferHandler() {
			@Override public boolean canImport(TransferHandler.TransferSupport s) {
				return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
					|| defaultTH.canImport(s);
			}
			@Override public boolean importData(TransferHandler.TransferSupport s) {
				if (!s.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
					return defaultTH.importData(s);
				try {
					@SuppressWarnings("unchecked")
					List<File> files = (List<File>) s.getTransferable()
						.getTransferData(DataFlavor.javaFileListFlavor);
					for (File f : files) {
						String n = f.getName().toLowerCase();
						if (n.endsWith(".cps") || n.endsWith(".xmd")
								|| n.endsWith(".cmd") || n.endsWith(".xml")) {
							String path = f.getAbsolutePath();
							SwingUtilities.invokeLater(() ->
								CPBase.scriptManager.getScript(path, path, true));
							return true;
						}
					}
				} catch (Exception ex) {}
				return false;
			}
			@Override public void exportAsDrag(JComponent c, java.awt.event.InputEvent e, int action) {
				defaultTH.exportAsDrag(c, e, action);
			}
			@Override public int getSourceActions(JComponent c) { return defaultTH.getSourceActions(c); }
		});
		installFileDrop(this); // fallback for panel margins outside the scroll pane
	}

	// -----------------------------------------------------------------------
	// File drag-and-drop — accepts .cps / .xml script files dragged from Finder
	// Package-private so ScriptHover can install it on other components too.
	// -----------------------------------------------------------------------

	static void installFileDrop(Component c) {
		new DropTarget(c, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
			@Override
			public void dragEnter(DropTargetDragEvent e) {
				if (e.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
					e.acceptDrag(DnDConstants.ACTION_COPY);
				else
					e.rejectDrag();
			}
			@Override
			public void drop(DropTargetDropEvent e) {
				if (!e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
					e.rejectDrop();
					return;
				}
				e.acceptDrop(DnDConstants.ACTION_COPY);
				try {
					@SuppressWarnings("unchecked")
					List<File> files = (List<File>)
						e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					for (File f : files) {
						String name = f.getName().toLowerCase();
						if (name.endsWith(".cps") || name.endsWith(".xmd")
								|| name.endsWith(".cmd") || name.endsWith(".xml")) {
							String path = f.getAbsolutePath();
							SwingUtilities.invokeLater(() ->
								CPBase.scriptManager.getScript(path, path, true));
							break;
						}
					}
					e.dropComplete(true);
				} catch (Exception ex) {
					e.dropComplete(false);
				}
			}
		});
	}

	// -----------------------------------------------------------------------
	// Find bar
	// -----------------------------------------------------------------------

	private JPanel buildFindBar() {
		JPanel bar = new JPanel();
		bar.setLayout(new BoxLayout(bar, BoxLayout.LINE_AXIS));
		bar.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
			BorderFactory.createEmptyBorder(3, 6, 3, 6)));

		findField = new JTextField(20);
		findField.setMaximumSize(new Dimension(280, 26));

		findStatus = new JLabel("  ");
		findStatus.setFont(findStatus.getFont().deriveFont(Font.PLAIN, 11f));
		findStatus.setForeground(Color.GRAY);

		JButton prevBtn  = new JButton("▲");
		JButton nextBtn  = new JButton("▼");
		JButton closeBtn = new JButton("✕");
		for (JButton b : new JButton[]{prevBtn, nextBtn, closeBtn})
			b.setFont(b.getFont().deriveFont(11f));
		prevBtn.setToolTipText("Find previous (Shift+Enter)");
		nextBtn.setToolTipText("Find next (Enter)");
		closeBtn.setToolTipText("Close find bar (Escape)");

		prevBtn.addActionListener(e -> findNext(true));
		nextBtn.addActionListener(e -> findNext(false));
		closeBtn.addActionListener(e -> hideFindBar());

		// Enter = next, Shift+Enter = previous
		findField.addActionListener(e -> findNext(false));
		findField.getInputMap().put(
			KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "findPrev");
		findField.getActionMap().put("findPrev", new AbstractAction() {
			public void actionPerformed(ActionEvent e) { findNext(true); }
		});

		// Escape from anywhere inside the find bar closes it
		AbstractAction escAction = new AbstractAction() {
			public void actionPerformed(ActionEvent e) { hideFindBar(); }
		};
		KeyStroke esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
		bar.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(esc, "close");
		bar.getActionMap().put("close", escAction);

		// Live match count as user types
		findField.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e)  { lastFindPos = -1; updateMatchCount(); }
			public void removeUpdate(DocumentEvent e)  { lastFindPos = -1; updateMatchCount(); }
			public void changedUpdate(DocumentEvent e) {}
		});

		bar.add(new JLabel("Find: "));
		bar.add(findField);
		bar.add(Box.createHorizontalStrut(4));
		bar.add(prevBtn);
		bar.add(nextBtn);
		bar.add(Box.createHorizontalStrut(8));
		bar.add(findStatus);
		bar.add(Box.createHorizontalGlue());
		bar.add(closeBtn);
		return bar;
	}

	private void showFindBar() {
		findBar.setVisible(true);
		findField.selectAll();
		findField.requestFocusInWindow();
		lastFindPos = -1;
		updateMatchCount();
	}

	private void hideFindBar() {
		findBar.setVisible(false);
		// drop the selection created by find
		int pos = textArea.getCaretPosition();
		textArea.select(pos, pos);
		textArea.requestFocusInWindow();
	}

	private void findNext(boolean backward) {
		String query = findField.getText();
		if (query.isEmpty()) { findStatus.setText("  "); return; }
		String text      = textArea.getText();
		String textLow   = text.toLowerCase();
		String queryLow  = query.toLowerCase();
		int    qlen      = queryLow.length();

		int start;
		if (!backward) {
			// forward: from lastFindPos+1, wrap to 0
			start = textLow.indexOf(queryLow, lastFindPos + 1);
			if (start < 0) start = textLow.indexOf(queryLow, 0);
		} else {
			// backward: from lastFindPos-1, wrap from end
			int from = (lastFindPos > 0) ? lastFindPos - 1 : text.length() - 1;
			start = textLow.lastIndexOf(queryLow, from);
			if (start < 0) start = textLow.lastIndexOf(queryLow);
		}

		if (start < 0) {
			findStatus.setForeground(new Color(180, 0, 0));
			findStatus.setText("Not found");
			return;
		}

		lastFindPos = start;
		textArea.select(start, start + qlen);
		try {
			Rectangle2D r = textArea.modelToView2D(start);
			if (r != null) textArea.scrollRectToVisible(r.getBounds());
		} catch (BadLocationException ignored) {}

		// count matches for "N of M" label
		int total = 0, which = 0, cur = 0;
		while (true) {
			int idx = textLow.indexOf(queryLow, cur);
			if (idx < 0) break;
			total++;
			if (idx == start) which = total;
			cur = idx + 1;
		}
		findStatus.setForeground(Color.GRAY);
		findStatus.setText(which + " of " + total);
	}

	private void updateMatchCount() {
		String query = findField.getText();
		if (query.isEmpty()) { findStatus.setText("  "); return; }
		String textLow  = textArea.getText().toLowerCase();
		String queryLow = query.toLowerCase();
		int count = 0, idx = 0;
		while ((idx = textLow.indexOf(queryLow, idx)) >= 0) { count++; idx++; }
		if (count == 0) {
			findStatus.setForeground(new Color(180, 0, 0));
			findStatus.setText("Not found");
		} else {
			findStatus.setForeground(Color.GRAY);
			findStatus.setText(count + " found");
		}
	}

	// -----------------------------------------------------------------------
	// Load / populate
	// -----------------------------------------------------------------------

	/**
	 * Walk the CPTreeNode tree and extract command strings into the text area.
	 * Called after any script load.
	 */
	public void loadFromTree(CPTreeNode root) {
		if (root == null) return;
		StringBuilder sb = new StringBuilder();
		collectCommands(root, sb);
		loading = true;
		textArea.setText(sb.toString());
		loading = false;
		undoManager.discardAllEdits(); // don't let load be undoable
		nextLineIndex = 0;
		highlightCurrentLine();
		syncButtons();
	}

	private void collectCommands(CPTreeNode node, StringBuilder sb) {
		if (node == null) return;
		if ((node.tntype == CPTreeNode.COMMAND || node.tntype == CPTreeNode.MODE)
				&& node.tTool != null) {
			String cmd = node.tTool.getCommand();
			if (cmd != null && !cmd.trim().isEmpty())
				sb.append(cmd.trim()).append("\n");
		}
		@SuppressWarnings("unchecked")
		Enumeration<javax.swing.tree.TreeNode> children = node.children();
		while (children.hasMoreElements())
			collectCommands((CPTreeNode) children.nextElement(), sb);
	}

	// -----------------------------------------------------------------------
	// Execution
	// -----------------------------------------------------------------------

	/** Execute the current next line and advance to the following non-blank line. */
	public void executeNext() {
		List<Integer> lines = nonBlankLineIndices();
		if (lines.isEmpty()) return;

		// find first non-blank line at or after nextLineIndex
		int execLine = -1;
		for (int li : lines) {
			if (li >= nextLineIndex) { execLine = li; break; }
		}
		if (execLine < 0) return;

		String cmd = stripNamePrefix(getLine(execLine));
		if (cmd != null && !cmd.isEmpty()) {
			String oldName = PackControl.scriptManager.scriptName;
			CPBase.trafficCenter.parseWrapper(
				cmd, CirclePack.cpb.getActivePackData(), false, true, 0, null);
			String newName = PackControl.scriptManager.scriptName;
			if (oldName == null ? newName != null : !oldName.equals(newName)) return;
		}

		// advance past the executed line
		nextLineIndex = execLine + 1;
		highlightCurrentLine();
		syncButtons();
	}

	/** Reset back to the first non-blank line. */
	public void resetToTop() {
		nextLineIndex = 0;
		highlightCurrentLine();
		syncButtons();
	}

	// -----------------------------------------------------------------------
	// Highlight & scroll
	// -----------------------------------------------------------------------

	private void highlightCurrentLine() {
		Highlighter hl = textArea.getHighlighter();
		if (currentHighlight != null) {
			hl.removeHighlight(currentHighlight);
			currentHighlight = null;
		}

		List<Integer> lines = nonBlankLineIndices();
		int targetLine = -1;
		for (int li : lines) {
			if (li >= nextLineIndex) { targetLine = li; break; }
		}
		if (targetLine < 0) return;

		try {
			int start = textArea.getLineStartOffset(targetLine);
			int end   = textArea.getLineEndOffset(targetLine);
			currentHighlight = hl.addHighlight(start, end, HIGHLIGHT);
			// scroll highlighted line into view without disturbing the caret
			Rectangle2D r = textArea.modelToView2D(start);
			if (r != null) textArea.scrollRectToVisible(r.getBounds());
		} catch (BadLocationException ignored) {}
	}

	public void syncButtons() {
		if (PackControl.scriptBar == null || PackControl.vertScriptBar == null) return;
		List<Integer> lines = nonBlankLineIndices();
		boolean hasNext = false;
		for (int li : lines) {
			if (li >= nextLineIndex) { hasNext = true; break; }
		}
		boolean hasTop = !lines.isEmpty() && nextLineIndex > 0;
		PackControl.scriptBar.nextBundle.enableNext(hasNext);
		PackControl.vertScriptBar.nextBundle.enableNext(hasNext);
		PackControl.scriptBar.nextBundle.enableTop(hasTop);
		PackControl.vertScriptBar.nextBundle.enableTop(hasTop);
	}

	// -----------------------------------------------------------------------
	// Save helper
	// -----------------------------------------------------------------------

	/** Return all non-blank trimmed lines for use when saving. */
	public List<String> getCommands() {
		List<String> cmds = new ArrayList<>();
		for (String line : textArea.getText().split("\n", -1)) {
			String t = line.trim();
			if (!t.isEmpty()) cmds.add(t);
		}
		return cmds;
	}

	// -----------------------------------------------------------------------
	// Utilities
	// -----------------------------------------------------------------------

	private List<Integer> nonBlankLineIndices() {
		List<Integer> result = new ArrayList<>();
		int count = textArea.getLineCount();
		for (int i = 0; i < count; i++) {
			String line = getLine(i);
			if (line != null && !line.trim().isEmpty())
				result.add(i);
		}
		return result;
	}

	private String getLine(int lineIndex) {
		try {
			int start = textArea.getLineStartOffset(lineIndex);
			int end   = textArea.getLineEndOffset(lineIndex);
			return textArea.getText(start, end - start).stripTrailing();
		} catch (BadLocationException e) {
			return null;
		}
	}

	/**
	 * Strip a leading [name]:= or [name]: label, returning just the command.
	 * E.g. "[a]:= seed; disp -f" -> "seed; disp -f"
	 */
	static String stripNamePrefix(String line) {
		if (line == null) return null;
		String t = line.trim();
		if (t.startsWith("[")) {
			int close = t.indexOf(']');
			if (close > 0) {
				String rest = t.substring(close + 1).trim();
				if (rest.startsWith(":=")) return rest.substring(2).trim();
				if (rest.startsWith(":"))  return rest.substring(1).trim();
			}
		}
		return line.trim();
	}

	/**
	 * Search lines for one whose [name] prefix starts with firstChar.
	 * Returns the command part (after stripping the prefix), or null.
	 */
	public String findNamedCmd(char firstChar) {
		for (String line : textArea.getText().split("\n", -1)) {
			String t = line.trim();
			if (t.startsWith("[")) {
				int close = t.indexOf(']');
				if (close > 1) {
					char nameFirst = t.charAt(1);
					if (nameFirst == firstChar)
						return stripNamePrefix(t);
				}
			}
		}
		return null;
	}
}
