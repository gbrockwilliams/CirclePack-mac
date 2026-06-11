package ftnTheory;

import combinatorics.komplex.HalfEdge;
import dcel.CombDCEL;
import dcel.PackDCEL;
import dcel.RawManip;
import exceptions.CombException;
import komplex.EdgeSimple;
import packing.PackData;

/**
 * Builds an M×N Brooks torus: a flat torus whose fundamental domain
 * contains M×N copies of a quad-interstice Brooks packing sharing
 * boundary corner vertices.
 *
 * <p>Construction steps:
 * <ol>
 *   <li>Build the (M+1)×(N+1) rectangular corner grid with one plug vertex
 *       per cell, triangulated by connecting each plug to its cell's 4 corners.</li>
 *   <li>Apply the h/v cfrac sequence inside every cell, adding interior Brooks
 *       circles without altering the outer boundary.</li>
 *   <li>Self-adjoin left boundary with right boundary (N edges each), forming
 *       a cylinder.</li>
 *   <li>Self-adjoin top boundary with bottom boundary (M edges each), closing
 *       into a torus.</li>
 * </ol>
 *
 * <p>Minimum requirement: M ≥ 1, N ≥ 1; for a topologically correct torus
 * at least one of M or N must be ≥ 2 (otherwise the two self-adjoins share
 * an endpoint and degenerate).  The command enforces M ≥ 2, N ≥ 2.
 *
 * <p>Grid indexing (1-based):
 * <pre>
 *   corner(i,j) = j*(M+1) + i + 1,   0 ≤ i ≤ M,  0 ≤ j ≤ N
 *   plug(i,j)   = (M+1)*(N+1) + j*M + i + 1,   0 ≤ i &lt; M,  0 ≤ j &lt; N
 * </pre>
 * Column i increases to the right, row j increases downward.
 * For cell (i,j): T=corner(i,j) [TL], R=corner(i+1,j) [TR],
 * B=corner(i+1,j+1) [BR], L=corner(i,j+1) [BL], P=plug(i,j) [center].
 */
public class BrooksTorus {

    /**
     * Build an M×N Brooks torus with the same parameter in every cell.
     *
     * @param M     number of columns (≥ 2 recommended)
     * @param N     number of rows    (≥ 2 recommended)
     * @param cfrac continued-fraction sequence; cfrac[0] verticals,
     *              cfrac[1] horizontals, alternating.  May be null/empty.
     * @return new PackData (closed torus), or null on error
     */
    public static PackData build(int M, int N, int[] cfrac) {
        return build(M, N, uniformCfrac(M, N, cfrac), null);
    }

    /**
     * Build an M×N Brooks torus with a separate parameter for each cell.
     *
     * @param M         number of columns
     * @param N         number of rows
     * @param cellCfrac per-cell sequences, indexed j*M+i (row-major);
     *                  entries may be null (treated as empty)
     * @param cellOfOut if non-null and length ≥ 1, cellOfOut[0] receives
     *                  the vertex→cell map for the finished torus:
     *                  cellOf[v] = j*M+i for vertices interior to cell
     *                  (i,j), -1 for the shared corner vertices
     * @return new PackData (closed torus), or null on error
     */
    public static PackData build(int M, int N, int[][] cellCfrac,
            int[][] cellOfOut) {
        int[][] holder = new int[1][];
        PackData pd = buildGrid(M, N, cellCfrac, holder);
        if (pd == null) return null;
        PackData torus = closeTorus(pd, M, N, holder);
        if (torus != null && cellOfOut != null && cellOfOut.length > 0)
            cellOfOut[0] = holder[0];
        return torus;
    }

    /**
     * Expand a single cfrac sequence into the per-cell form
     * expected by the per-cell 'build'.
     */
    public static int[][] uniformCfrac(int M, int N, int[] cfrac) {
        if (cfrac == null) cfrac = new int[0];
        int[][] cellCfrac = new int[M * N][];
        for (int c = 0; c < M * N; c++)
            cellCfrac[c] = cfrac.clone();
        return cellCfrac;
    }

    /**
     * Steps 1-3 (uniform parameter): see the per-cell version.
     */
    public static PackData buildGrid(int M, int N, int[] cfrac) {
        return buildGrid(M, N, uniformCfrac(M, N, cfrac), null);
    }

    /**
     * Steps 1-3: build the open M×N rectangular Brooks grid (with
     * boundary), before the torus-closing self-adjoins.
     *
     * @param cellCfrac per-cell cfrac sequences, indexed j*M+i
     * @param cellOfOut if non-null and length ≥ 1, cellOfOut[0] receives
     *                  the vertex→cell map (see per-cell 'build')
     */
    public static PackData buildGrid(int M, int N, int[][] cellCfrac,
            int[][] cellOfOut) {
        if (M < 1 || N < 1) return null;
        if (cellCfrac == null || cellCfrac.length != M * N)
            return null;

        // ── Step 1: build bouquet for (M+1)×(N+1) corners + M×N plugs ──────

        int numCorners = (M + 1) * (N + 1);
        int numPlugs   = M * N;
        int vertcount  = numCorners + numPlugs;

        int[][] bouquet = new int[vertcount + 1][];

        for (int j = 0; j <= N; j++) {
            for (int i = 0; i <= M; i++) {
                int v      = cIdx(i, j, M);
                boolean lt = (i == 0);
                boolean rt = (i == M);
                boolean tp = (j == 0);
                boolean bt = (j == N);

                // Petal lists are counterclockwise in math coordinates
                // (x = i increasing right, y = -j so row j increases downward).
                if (tp && lt) {
                    // TL corner: SE cell only  →  [S, SE_plug, E]
                    bouquet[v] = new int[]{
                        cIdx(i,   j+1, M),
                        pIdx(i,   j,   M, numCorners),
                        cIdx(i+1, j,   M)
                    };
                } else if (tp && rt) {
                    // TR corner: SW cell only  →  [W, SW_plug, S]
                    bouquet[v] = new int[]{
                        cIdx(i-1, j,   M),
                        pIdx(i-1, j,   M, numCorners),
                        cIdx(i,   j+1, M)
                    };
                } else if (bt && lt) {
                    // BL corner: NE cell only  →  [E, NE_plug, N]
                    bouquet[v] = new int[]{
                        cIdx(i+1, j,   M),
                        pIdx(i,   j-1, M, numCorners),
                        cIdx(i,   j-1, M)
                    };
                } else if (bt && rt) {
                    // BR corner: NW cell only  →  [N, NW_plug, W]
                    bouquet[v] = new int[]{
                        cIdx(i,   j-1, M),
                        pIdx(i-1, j-1, M, numCorners),
                        cIdx(i-1, j,   M)
                    };
                } else if (lt) {
                    // left edge (0<j<N): SE and NE cells  →  [S, SE_plug, E, NE_plug, N]
                    bouquet[v] = new int[]{
                        cIdx(i,   j+1, M),
                        pIdx(i,   j,   M, numCorners),
                        cIdx(i+1, j,   M),
                        pIdx(i,   j-1, M, numCorners),
                        cIdx(i,   j-1, M)
                    };
                } else if (rt) {
                    // right edge (0<j<N): NW and SW cells  →  [N, NW_plug, W, SW_plug, S]
                    bouquet[v] = new int[]{
                        cIdx(i,   j-1, M),
                        pIdx(i-1, j-1, M, numCorners),
                        cIdx(i-1, j,   M),
                        pIdx(i-1, j,   M, numCorners),
                        cIdx(i,   j+1, M)
                    };
                } else if (tp) {
                    // top edge (0<i<M): SW and SE cells  →  [W, SW_plug, S, SE_plug, E]
                    bouquet[v] = new int[]{
                        cIdx(i-1, j,   M),
                        pIdx(i-1, j,   M, numCorners),
                        cIdx(i,   j+1, M),
                        pIdx(i,   j,   M, numCorners),
                        cIdx(i+1, j,   M)
                    };
                } else if (bt) {
                    // bottom edge (0<i<M): NE and NW cells  →  [E, NE_plug, N, NW_plug, W]
                    bouquet[v] = new int[]{
                        cIdx(i+1, j,   M),
                        pIdx(i,   j-1, M, numCorners),
                        cIdx(i,   j-1, M),
                        pIdx(i-1, j-1, M, numCorners),
                        cIdx(i-1, j,   M)
                    };
                } else {
                    // interior corner: all 4 cells  →  [W, SW, S, SE, E, NE, N, NW, W]
                    bouquet[v] = new int[]{
                        cIdx(i-1, j,   M),
                        pIdx(i-1, j,   M, numCorners),
                        cIdx(i,   j+1, M),
                        pIdx(i,   j,   M, numCorners),
                        cIdx(i+1, j,   M),
                        pIdx(i,   j-1, M, numCorners),
                        cIdx(i,   j-1, M),
                        pIdx(i-1, j-1, M, numCorners),
                        cIdx(i-1, j,   M)
                    };
                }
            }
        }

        // plugs: interior 4-cycle cclw  →  [TL, BL, BR, TR, TL]
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < M; i++) {
                int v = pIdx(i, j, M, numCorners);
                bouquet[v] = new int[]{
                    cIdx(i,   j,   M),
                    cIdx(i,   j+1, M),
                    cIdx(i+1, j+1, M),
                    cIdx(i+1, j,   M),
                    cIdx(i,   j,   M)
                };
            }
        }

        // ── Step 2: create the initial packing from the bouquet ──────────────

        PackData pd = new PackData(null);
        // alpha must be an interior vertex: use the (0,0) cell's plug
        int alp = pIdx(0, 0, M, numCorners);
        PackDCEL pdcel = CombDCEL.getRawDCEL(bouquet, alp);
        pdcel.fixDCEL(pd);

        // vertex→cell map: corners are shared (-1), plugs and the split
        // vertices added below belong to exactly one cell
        int finalCount = vertcount;
        for (int c = 0; c < M * N; c++) {
            int[] cf = cellCfrac[c];
            if (cf != null)
                for (int k = 0; k < cf.length; k++)
                    finalCount += cf[k];
        }
        int[] cellOf = new int[finalCount + 1];
        java.util.Arrays.fill(cellOf, -1);
        for (int j = 0; j < N; j++)
            for (int i = 0; i < M; i++)
                cellOf[pIdx(i, j, M, numCorners)] = j * M + i;

        // ── Step 3: apply each cell's cfrac h/v sequence ────────────────────

        for (int j = 0; j < N; j++) {
            for (int i = 0; i < M; i++) {
                int T = cIdx(i,   j,   M);   // TL corner
                int L = cIdx(i,   j+1, M);   // BL corner
                int B = cIdx(i+1, j+1, M);   // BR corner
                int R = cIdx(i+1, j,   M);   // TR corner
                int P = pIdx(i,   j,   M, numCorners);

                int cell = j * M + i;
                int[] cfrac = cellCfrac[cell];
                if (cfrac == null) cfrac = new int[0];

                for (int k = 0; k < cfrac.length; k++) {
                    int n = cfrac[k];
                    if (k % 2 == 0) {
                        // even index → verticals: split edge P→L
                        for (int q = 0; q < n; q++) {
                            HalfEdge PL = pd.packDCEL.findHalfEdge(new EdgeSimple(P, L));
                            if (PL == null)
                                throw new CombException("BrooksTorus: edge P-L missing in cell ("
                                        + i + "," + j + ") after " + q + " verticals");
                            RawManip.splitEdge_raw(pd.packDCEL, PL);
                            pd.packDCEL.fixDCEL(pd);
                            L = pd.packDCEL.vertCount;
                            cellOf[L] = cell;
                        }
                    } else {
                        // odd index → horizontals: split edge P→T
                        for (int q = 0; q < n; q++) {
                            HalfEdge PT = pd.packDCEL.findHalfEdge(new EdgeSimple(P, T));
                            if (PT == null)
                                throw new CombException("BrooksTorus: edge P-T missing in cell ("
                                        + i + "," + j + ") after " + q + " horizontals");
                            RawManip.splitEdge_raw(pd.packDCEL, PT);
                            pd.packDCEL.fixDCEL(pd);
                            T = pd.packDCEL.vertCount;
                            cellOf[T] = cell;
                        }
                    }
                }
            }
        }

        if (cellOfOut != null && cellOfOut.length > 0)
            cellOfOut[0] = cellOf;
        return pd;
    }

    /**
     * Step 4 (no tracking): see the tracked version.
     */
    public static PackData closeTorus(PackData pd, int M, int N) {
        return closeTorus(pd, M, N, null);
    }

    /**
     * Step 4: two self-adjoins closing the open grid into a torus.
     *
     * @param cellOfHolder if non-null with cellOfHolder[0] set (from
     *                     'buildGrid'), the vertex→cell map is remapped
     *                     through both adjoins and left in cellOfHolder[0]
     */
    public static PackData closeTorus(PackData pd, int M, int N,
            int[][] cellOfHolder) {

        int c00 = cIdx(0, 0, M);   // TL corner
        int cM0 = cIdx(M, 0, M);   // TR corner

        // Adjoin right column to left column, N boundary edges:
        // clw from cM0 (TR) walks down the right side; cclw from c00 (TL)
        // walks down the left side. TR is identified with TL, BR with BL,
        // giving a cylinder whose two boundary circles are the top and
        // bottom rows (each a closed M-edge loop). 'adjoinCall' handles
        // all the red-chain rebuilding a raw 'CombDCEL.adjoin' skips.
        PackData cyl = PackData.adjoinCall(pd, pd, cM0, c00, N);

        if (cellOfHolder != null && cellOfHolder[0] != null)
            cellOfHolder[0] = remapCellOf(cellOfHolder[0], cyl);

        // Anchor the second adjoin away from the just-pasted seam: use
        // the column-1 vertices of the top and bottom rows. 'vertexMap'
        // only lists vertices whose index changed; findW gives 0 for
        // vertices that kept their index.
        int c10 = cIdx(1, 0, M);
        int c1N = cIdx(1, N, M);
        if (cyl.vertexMap != null) {
            int nv = cyl.vertexMap.findW(c10);
            if (nv != 0) c10 = nv;
            nv = cyl.vertexMap.findW(c1N);
            if (nv != 0) c1N = nv;
        }

        // Adjoin bottom row to top row, M boundary edges: clw from c1N
        // walks the bottom circle, cclw from c10 walks the top circle,
        // identifying vertices at equal column positions. Torus, no bdry.
        PackData torus = PackData.adjoinCall(cyl, cyl, c1N, c10, M);

        if (cellOfHolder != null && cellOfHolder[0] != null)
            cellOfHolder[0] = remapCellOf(cellOfHolder[0], torus);

        torus.set_aim_default();
        return torus;
    }

    /**
     * Push a vertex→cell map through an adjoin: 'newPack.vertexMap'
     * lists (old,new) pairs for vertices whose index changed; findW
     * returns 0 for vertices that kept their index. Identified seam
     * vertices are corners on both sides (cell -1), so collisions
     * are harmless.
     */
    private static int[] remapCellOf(int[] cellOf, PackData newPack) {
        int[] out = new int[newPack.nodeCount + 1];
        java.util.Arrays.fill(out, -1);
        for (int v = 1; v < cellOf.length; v++) {
            if (cellOf[v] < 0) continue;
            int nv = v;
            if (newPack.vertexMap != null) {
                int w = newPack.vertexMap.findW(v);
                if (w != 0) nv = w;
            }
            if (nv >= 1 && nv <= newPack.nodeCount)
                out[nv] = cellOf[v];
        }
        return out;
    }

    /** 1-based index of corner(i,j) in the M-column grid. */
    public static int cIdx(int i, int j, int M) {
        return j * (M + 1) + i + 1;
    }

    /** 1-based index of plug(i,j) in the M-column, N-row grid. */
    public static int pIdx(int i, int j, int M, int numCorners) {
        return numCorners + j * M + i + 1;
    }
}
