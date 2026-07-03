package frames;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.jimrolf.functionparser.FunctionParser;

import allMains.CirclePack;
import input.CPFileManager;

/**
 * Interactive editor for "welding maps": piecewise-linear increasing
 * homeomorphisms h:[0,1]->[0,1] used by the conformal welding
 * machinery ('|cw| weld -f {file}', see 'WeldManager'). File format:
 *     PATH
 *     x1 y1
 *     ...
 *     END
 *
 * The graph is drawn in the unit square (like a game-controller
 * response curve). Left-click empty space to add a control point,
 * drag a point to move it (its coordinates are clamped strictly
 * between its neighbors', so the map always stays 1-to-1),
 * right-click a point to delete it. The endpoints (0,0) and (1,1)
 * are fixed.
 *
 * A formula can be entered instead, e.g. "x+0.1*sin(2*Pi*x)"
 * (variable x; parsed by the same function parser used elsewhere in
 * CirclePack). It is sampled, normalized so h(0)=0 and h(1)=1, and
 * must be strictly monotone; the samples become the control points.
 *
 * Open with the 'weldmap' command.
 */
public class WeldMapFrame extends JFrame {

	private static final long serialVersionUID=1L;

	static final double MIN_GAP=1e-4;  // strict monotonicity gap
	static final int PAD=20;           // canvas padding, pixels
	static final int HIT=8;            // point grab radius, pixels
	static final int FORMULA_SAMPLES=32; // control pts from a formula

	static WeldMapFrame instance=null;

	// control points, sorted by x; first is (0,0), last is (1,1)
	ArrayList<double[]> pts=new ArrayList<double[]>();

	MapPanel canvas;
	JTextField formulaField;
	JLabel status;
	File lastDir=null;

	// Constructor
	public WeldMapFrame() {
		super("Welding map editor");
		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		identityReset();

		canvas=new MapPanel();
		canvas.setPreferredSize(new Dimension(400,400));

		// formula row
		JPanel formulaRow=new JPanel(new BorderLayout(4,0));
		formulaRow.setBorder(BorderFactory.createEmptyBorder(4,6,0,6));
		formulaRow.add(new JLabel("h(x) = "),BorderLayout.WEST);
		formulaField=new JTextField("x+0.1*sin(2*Pi*x)");
		formulaRow.add(formulaField,BorderLayout.CENTER);
		JButton applyBtn=new JButton("Apply formula");
		applyBtn.addActionListener(e -> applyFormula());
		formulaRow.add(applyBtn,BorderLayout.EAST);

		// buttons row
		JPanel btnRow=new JPanel(new FlowLayout(FlowLayout.LEFT,6,4));
		JButton idBtn=new JButton("Identity");
		idBtn.addActionListener(e -> { identityReset(); refresh(); });
		JButton loadBtn=new JButton("Load…");
		loadBtn.addActionListener(e -> loadFile());
		JButton saveBtn=new JButton("Save…");
		saveBtn.addActionListener(e -> saveFile());
		btnRow.add(idBtn);
		btnRow.add(loadBtn);
		btnRow.add(saveBtn);
		status=new JLabel();
		btnRow.add(status);

		JPanel south=new JPanel(new BorderLayout());
		south.add(formulaRow,BorderLayout.NORTH);
		south.add(btnRow,BorderLayout.SOUTH);

		add(canvas,BorderLayout.CENTER);
		add(south,BorderLayout.SOUTH);
		pack();
		setLocationRelativeTo(null);
		refresh();
	}

	/**
	 * Open (or re-show) the editor.
	 */
	public static void openEditor() {
		if (instance==null)
			instance=new WeldMapFrame();
		instance.setVisible(true);
		instance.toFront();
	}

	/**
	 * The map currently shown in the editor, as PL control points
	 * for 'WeldUtil.evalPL' — or null when the editor is closed or
	 * shows the plain identity (just the two fixed endpoints).
	 * Lets the interactive weld mode pick up the on-screen map.
	 * @return double[][] or null
	 */
	public static double[][] currentMap() {
		if (instance==null || !instance.isVisible() ||
				instance.pts.size()<=2)
			return null;
		double[][] map=new double[instance.pts.size()][2];
		for (int k=0;k<instance.pts.size();k++) {
			double[] p=instance.pts.get(k);
			map[k][0]=p[0];
			map[k][1]=p[1];
		}
		return map;
	}

	void identityReset() {
		pts.clear();
		pts.add(new double[] {0.0,0.0});
		pts.add(new double[] {1.0,1.0});
	}

	void refresh() {
		status.setText(pts.size()+" points");
		canvas.repaint();
	}

	/**
	 * Parse the formula (variable x), sample it, normalize to
	 * h(0)=0, h(1)=1, verify strict monotonicity, and install the
	 * samples as control points. A strictly decreasing formula is
	 * fine: normalization flips it to increasing.
	 */
	void applyFormula() {
		String str=formulaField.getText().trim();
		if (str.length()==0)
			return;
		FunctionParser fp=new FunctionParser();
		fp.setImplicitMultiplication(true);
		fp.parseExpression(str);
		if (fp.funcHasError()) {
			JOptionPane.showMessageDialog(this,
					"Could not parse formula:\n"+fp.getFuncErrorInfo(),
					"Welding map",JOptionPane.ERROR_MESSAGE);
			return;
		}
		int n=FORMULA_SAMPLES;
		double[] vals=new double[n+1];
		for (int k=0;k<=n;k++) {
			double v=fp.evalFunc((double)k/(double)n);
			if (Double.isNaN(v) || Double.isInfinite(v)) {
				JOptionPane.showMessageDialog(this,
						"Formula is not finite at x="+((double)k/n),
						"Welding map",JOptionPane.ERROR_MESSAGE);
				return;
			}
			vals[k]=v;
		}
		double span=vals[n]-vals[0];
		if (Math.abs(span)<1e-12) {
			JOptionPane.showMessageDialog(this,
					"Formula has h(1)=h(0); cannot normalize",
					"Welding map",JOptionPane.ERROR_MESSAGE);
			return;
		}
		double[] normed=new double[n+1];
		for (int k=0;k<=n;k++)
			normed[k]=(vals[k]-vals[0])/span;
		for (int k=1;k<=n;k++) {
			if (normed[k]<=normed[k-1]) {
				JOptionPane.showMessageDialog(this,
						"Formula is not 1-to-1: not strictly monotone "+
						"near x="+((double)k/n),
						"Welding map",JOptionPane.ERROR_MESSAGE);
				return;
			}
		}
		pts.clear();
		for (int k=0;k<=n;k++)
			pts.add(new double[] {(double)k/(double)n,normed[k]});
		refresh();
	}

	/**
	 * Default to 'PackingDirectory': that is where '|cw| weld -f'
	 * resolves bare filenames, so saved maps are found by default.
	 */
	File defaultDir() {
		if (lastDir!=null)
			return lastDir;
		if (CPFileManager.PackingDirectory!=null &&
				CPFileManager.PackingDirectory.exists())
			return CPFileManager.PackingDirectory;
		return CPFileManager.CurrentDirectory;
	}

	void saveFile() {
		JFileChooser jfc=new JFileChooser(defaultDir());
		jfc.setDialogTitle("Save welding map (PATH format)");
		if (jfc.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)
			return;
		File f=jfc.getSelectedFile();
		lastDir=f.getParentFile();
		try {
			BufferedWriter bw=new BufferedWriter(new FileWriter(f));
			bw.write("PATH\n");
			for (int i=0;i<pts.size();i++) {
				double[] p=pts.get(i);
				bw.write(p[0]+" "+p[1]+"\n");
			}
			bw.write("END\n");
			bw.close();
			String hint="";
			if (f.getParentFile()!=null && f.getParentFile().equals(
					CPFileManager.PackingDirectory))
				hint="; use e.g. '|cw| weld -q1 v w -f "+f.getName()+" -a'";
			else
				hint="; NOTE: '|cw| weld -f' looks for bare filenames "+
						"in "+CPFileManager.PackingDirectory;
			CirclePack.cpb.msg("weldmap: saved "+pts.size()+
					" points to "+f.getAbsolutePath()+hint);
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this,
					"Save failed: "+ex.getMessage(),
					"Welding map",JOptionPane.ERROR_MESSAGE);
		}
	}

	void loadFile() {
		JFileChooser jfc=new JFileChooser(defaultDir());
		jfc.setDialogTitle("Load welding map (PATH format)");
		if (jfc.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)
			return;
		File f=jfc.getSelectedFile();
		lastDir=f.getParentFile();
		try {
			ArrayList<Double> nums=new ArrayList<Double>();
			BufferedReader br=new BufferedReader(new FileReader(f));
			String line;
			boolean in=false;
			while ((line=br.readLine())!=null) {
				line=line.trim();
				if (line.startsWith("PATH")) { in=true; continue; }
				if (line.startsWith("END")) break;
				if (!in || line.length()==0 || line.startsWith("#"))
					continue;
				String[] toks=line.split("\\s+");
				for (int j=0;j<toks.length;j++)
					nums.add(Double.parseDouble(toks[j]));
			}
			br.close();
			if (nums.size()<4 || nums.size()%2!=0)
				throw new Exception("expected pairs 'x y' between "+
						"PATH and END");
			ArrayList<double[]> newpts=new ArrayList<double[]>();
			for (int j=0;j<nums.size();j+=2)
				newpts.add(new double[] {nums.get(j),nums.get(j+1)});
			for (int j=1;j<newpts.size();j++)
				if (newpts.get(j)[0]<=newpts.get(j-1)[0] ||
						newpts.get(j)[1]<=newpts.get(j-1)[1])
					throw new Exception("map in file is not strictly "+
							"increasing at point "+j);
			pts=newpts;
			refresh();
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this,
					"Load failed: "+ex.getMessage(),
					"Welding map",JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * The square canvas: unit square, reference diagonal, the
	 * piecewise-linear graph, and draggable control points.
	 */
	class MapPanel extends JPanel {

		private static final long serialVersionUID=1L;

		int dragIdx=-1;

		MapPanel() {
			setBackground(Color.WHITE);
			MouseAdapter ma=new MouseAdapter() {
				public void mousePressed(MouseEvent e) {
					int idx=hitPoint(e);
					if (javax.swing.SwingUtilities.isRightMouseButton(e)) {
						// delete (not the endpoints)
						if (idx>0 && idx<pts.size()-1) {
							pts.remove(idx);
							refresh();
						}
						return;
					}
					if (idx>=0)
						dragIdx=idx;
					else { // add a new point
						double[] xy=toReal(e.getX(),e.getY());
						if (xy[0]<=MIN_GAP || xy[0]>=1.0-MIN_GAP)
							return;
						int at=1;
						while (at<pts.size() && pts.get(at)[0]<xy[0])
							at++;
						// clamp BOTH coordinates strictly between the
						// neighbors': the map must be strictly
						// increasing (no vertical or horizontal runs)
						double xlo=pts.get(at-1)[0]+MIN_GAP;
						double xhi=pts.get(at)[0]-MIN_GAP;
						double ylo=pts.get(at-1)[1]+MIN_GAP;
						double yhi=pts.get(at)[1]-MIN_GAP;
						if (xlo>xhi || ylo>yhi)
							return; // no room
						xy[0]=Math.max(xlo,Math.min(xhi,xy[0]));
						xy[1]=Math.max(ylo,Math.min(yhi,xy[1]));
						pts.add(at,xy);
						dragIdx=at;
						refresh();
					}
				}
				public void mouseReleased(MouseEvent e) {
					dragIdx=-1;
				}
				public void mouseDragged(MouseEvent e) {
					if (dragIdx<=0 || dragIdx>=pts.size()-1)
						return;
					double[] xy=toReal(e.getX(),e.getY());
					// clamp strictly between neighbors: stays 1-to-1
					double[] lo=pts.get(dragIdx-1);
					double[] hi=pts.get(dragIdx+1);
					xy[0]=Math.max(lo[0]+MIN_GAP,
							Math.min(hi[0]-MIN_GAP,xy[0]));
					xy[1]=Math.max(lo[1]+MIN_GAP,
							Math.min(hi[1]-MIN_GAP,xy[1]));
					pts.set(dragIdx,xy);
					refresh();
				}
			};
			addMouseListener(ma);
			addMouseMotionListener(ma);
		}

		int side() {
			return Math.min(getWidth(),getHeight())-2*PAD;
		}

		int toPixX(double x) {
			return PAD+(int)Math.round(x*side());
		}

		int toPixY(double y) {
			return PAD+(int)Math.round((1.0-y)*side());
		}

		double[] toReal(int px,int py) {
			double s=(double)side();
			double x=(px-PAD)/s;
			double y=1.0-(py-PAD)/s;
			return new double[] {
					Math.max(0.0,Math.min(1.0,x)),
					Math.max(0.0,Math.min(1.0,y))};
		}

		int hitPoint(MouseEvent e) {
			for (int i=0;i<pts.size();i++) {
				double[] p=pts.get(i);
				int dx=e.getX()-toPixX(p[0]);
				int dy=e.getY()-toPixY(p[1]);
				if (dx*dx+dy*dy<=HIT*HIT)
					return i;
			}
			return -1;
		}

		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2=(Graphics2D)g;
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);

			// unit square and quarter grid
			g2.setColor(new Color(230,230,230));
			for (int k=1;k<4;k++) {
				double t=k/4.0;
				g2.drawLine(toPixX(t),toPixY(0),toPixX(t),toPixY(1));
				g2.drawLine(toPixX(0),toPixY(t),toPixX(1),toPixY(t));
			}
			// identity diagonal for reference
			g2.setColor(new Color(200,200,200));
			g2.drawLine(toPixX(0),toPixY(0),toPixX(1),toPixY(1));
			g2.setColor(Color.GRAY);
			g2.drawRect(toPixX(0),toPixY(1),side(),side());

			// the map
			g2.setColor(new Color(30,90,200));
			g2.setStroke(new BasicStroke(2f));
			for (int i=1;i<pts.size();i++) {
				double[] a=pts.get(i-1);
				double[] b=pts.get(i);
				g2.drawLine(toPixX(a[0]),toPixY(a[1]),
						toPixX(b[0]),toPixY(b[1]));
			}

			// control points
			for (int i=0;i<pts.size();i++) {
				double[] p=pts.get(i);
				int px=toPixX(p[0]);
				int py=toPixY(p[1]);
				if (i==0 || i==pts.size()-1)
					g2.setColor(Color.GRAY); // fixed endpoints
				else
					g2.setColor(i==dragIdx ? Color.RED :
						new Color(30,90,200));
				g2.fillOval(px-4,py-4,9,9);
			}
		}
	}

}
