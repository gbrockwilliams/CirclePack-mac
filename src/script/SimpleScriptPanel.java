package script;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

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
	private static final Highlighter.HighlightPainter HIGHLIGHT =
		new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 255, 150));

	public SimpleScriptPanel() {
		super(new BorderLayout());
		textArea = new JTextArea();
		textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
		textArea.setLineWrap(false);
		textArea.setTabSize(4);

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
			Rectangle r = textArea.modelToView(start);
			if (r != null) textArea.scrollRectToVisible(r);
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
