package ftnTheory;

import java.util.Vector;

import allMains.CirclePack;
import packing.PackData;
import packing.PackExtender;
import packing.PackUndo;
import util.CmdStruct;

/**
 * PackExtender for an M×N Brooks torus built by 'BrooksTorus'
 * ('create brooks_torus M N cfrac...'). Remembers the grid
 * dimensions, the continued-fraction parameter of each cell, and
 * which cell each vertex is interior to, so that an individual
 * cell can be reparameterized: the whole torus is rebuilt with
 * the new parameter and swapped into this packing's slot.
 *
 * The interactive route is the 'Brooks parameter' canvas mode
 * (see 'canvasses.BROOKSmode'): click a circle interior to a
 * cell and enter the new sequence in the dialog. Command route:
 * '|bt| set_param v n1 n2 ...'.
 */
public class BrooksTorusExtender extends PackExtender {

	public int M;            // columns
	public int N;            // rows
	public int[][] cellCfrac; // per-cell cfrac sequence, indexed j*M+i
	public int[] cellOf;      // vertex -> cell index; -1 for shared corners

	// Constructor
	public BrooksTorusExtender(PackData p,int M,int N,
			int[][] cellCfrac,int[] cellOf) {
		super(p);
		extensionType="BROOKS_TORUS";
		extensionAbbrev="BT";
		toolTip="'BrooksTorus': an MxN torus of Brooks quad-interstice "+
			"packings. Reparameterize a cell with 'set_param v n1 n2 ..' "+
			"or by clicking in the 'Brooks parameter' canvas mode.";
		this.M=M;
		this.N=N;
		this.cellCfrac=cellCfrac;
		this.cellOf=cellOf;
		extenderPD.packExtensions.add(this);
		try {
			registerXType();
		} catch (Exception ex) {
			// GUI embellishments (tool icon, help frame) are optional,
			// e.g. when running headless; the extender still works
			running=true;
		}
	}

	/**
	 * Which cell is vertex v interior to?
	 * @param v int
	 * @return cell index j*M+i, or -1 (shared corner or out of range)
	 */
	public int cellOfVert(int v) {
		if (cellOf==null || v<1 || v>=cellOf.length)
			return -1;
		return cellOf[v];
	}

	/**
	 * The vertices interior to 'cell' as a space-separated string
	 * (e.g. for 'disp' highlighting).
	 */
	public String cellVertString(int cell) {
		StringBuilder sb=new StringBuilder();
		for (int v=1;v<cellOf.length;v++)
			if (cellOf[v]==cell)
				sb.append(v).append(" ");
		return sb.toString().trim();
	}

	/**
	 * Current cfrac sequence of 'cell' as a space-separated string.
	 */
	public String cfracString(int cell) {
		if (cell<0 || cell>=cellCfrac.length || cellCfrac[cell]==null)
			return "";
		StringBuilder sb=new StringBuilder();
		for (int k=0;k<cellCfrac[cell].length;k++)
			sb.append(cellCfrac[cell][k]).append(" ");
		return sb.toString().trim();
	}

	/**
	 * Set the cfrac parameter of 'cell' and rebuild the torus.
	 * The new packing replaces this one in its slot ('keepX' carries
	 * this extender over), then repack/layout/redraw.
	 * @param cell int, index j*M+i
	 * @param cfrac int[], new sequence (may be empty)
	 * @return boolean, false on failure (parameter left unchanged)
	 */
	public boolean setCellParam(int cell,int[] cfrac) {
		if (cell<0 || cell>=cellCfrac.length) {
			errorMsg("set_param: no cell "+cell);
			return false;
		}
		int[] hold=cellCfrac[cell];
		cellCfrac[cell]=cfrac;
		PackData newPack=null;
		int[][] holder=new int[1][];
		try {
			newPack=BrooksTorus.build(M,N,cellCfrac,holder);
		} catch (Exception ex) {
			newPack=null;
		}
		if (newPack==null) {
			cellCfrac[cell]=hold;
			errorMsg("set_param: rebuild failed; parameter unchanged");
			return false;
		}
		newPack.status=true;
		int pnum=extenderPD.packNum;
		// snapshot for 'undo': the old packing object is replaced
		// (not mutated), so a reference suffices; the hook restores
		// this extender's fields, which the rebuild overwrites
		final int oldCell=cell;
		final int[] oldParam=hold;
		final int[] oldCellOf=cellOf;
		PackUndo.save("brooks set_param",new int[] {pnum},
				new PackData[] {extenderPD},new Runnable() {
			public void run() {
				cellCfrac[oldCell]=oldParam;
				cellOf=oldCellOf;
				pdc=extenderPD.packDCEL;
			}
		});
		// keepX=true moves this extender onto the new packing and
		// repoints 'extenderPD'
		PackData pdata=CirclePack.cpb.swapPackData(newPack,pnum,true);
		pdc=pdata.packDCEL;
		cellOf=holder[0];
		cpCommand(pdata,"repack");
		cpCommand(pdata,"layout");
		cpCommand(pdata,"disp -w -c");
		return true;
	}

	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;

		// ======== set_param v n1 n2 ... =========
		if (cmd.startsWith("set_param")) {
			if (flagSegs==null || flagSegs.size()==0 ||
					(items=flagSegs.get(0)).size()<1)
				Oops("usage: set_param v [n1 n2 ..]");
			int v=Integer.parseInt(items.get(0));
			int cell=cellOfVert(v);
			if (cell<0) {
				errorMsg("set_param: "+v+" is a shared corner circle; "+
						"choose a circle interior to a cell");
				return 0;
			}
			int[] cfrac=new int[items.size()-1];
			for (int k=1;k<items.size();k++) {
				cfrac[k-1]=Integer.parseInt(items.get(k));
				if (cfrac[k-1]<0)
					Oops("set_param: entries must be >= 0");
			}
			if (!setCellParam(cell,cfrac))
				return 0;
			msg("cell ("+(cell%M)+","+(cell/M)+") rebuilt with parameter '"+
					cfracString(cell)+"'");
			return 1;
		}

		// ======== get_param v =========
		else if (cmd.startsWith("get_param")) {
			if (flagSegs==null || flagSegs.size()==0 ||
					(items=flagSegs.get(0)).size()<1)
				Oops("usage: get_param v");
			int v=Integer.parseInt(items.get(0));
			int cell=cellOfVert(v);
			if (cell<0) {
				msg("circle "+v+" is a shared corner (no single cell)");
				return 1;
			}
			msg("circle "+v+" is in cell ("+(cell%M)+","+(cell/M)+
					"), parameter '"+cfracString(cell)+"'");
			return 1;
		}

		// ======== status =========
		else if (cmd.startsWith("status")) {
			msg(M+"x"+N+" Brooks torus; cell parameters:");
			for (int j=0;j<N;j++)
				for (int i=0;i<M;i++)
					msg("  ("+i+","+j+"): '"+cfracString(j*M+i)+"'");
			return 1;
		}

		return super.cmdParser(cmd,flagSegs);
	}

	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("set_param","v n1 n2 ..",null,
				"Rebuild the cell containing circle v with the new "+
				"continued-fraction sequence (empty = plain quad)"));
		cmdStruct.add(new CmdStruct("get_param","v",null,
				"Report the cell containing circle v and its parameter"));
		cmdStruct.add(new CmdStruct("status",null,null,
				"List all cell parameters"));
	}

}
