# CirclePack — custom commands and modes

Documentation for the commands, canvas modes, and behaviors added on
top of Ken Stephenson's CirclePack (see `git log` for which commits
are local). Everything here is also in the in-app **Help** window
(Help → command details), which is generated from
`src/Resources/doc/CmdDetails.txt` — see [Maintaining the
docs](#maintaining-the-docs) below.

---

## Torus commands

### `create brooks_torus M N [n1 n2 ...]`

Builds an M×N torus (M, N ≥ 3) of quadrilateral cells, each filled
with a Brooks packing prescribed by a continued-fraction parameter.
The integer sequence `n1 n2 ...` alternates **vertical** then
**horizontal** subdivisions of each cell's interstice; an empty
sequence gives a plain quad cell. All cells initially share the same
parameter.

```
create brooks_torus 3 3 2 1
```

A `BROOKS_TORUS` pack extender (command prefix `|bt|`) is attached
automatically, recording M, N, the per-cell parameters, and the
vertex→cell map. Its commands:

- `|bt| set_param v n1 n2 ...` — rebuild the cell containing circle
  `v` with a new parameter (empty sequence = plain quad)
- `|bt| get_param v` — report the cell containing circle `v` and its
  parameter
- `|bt| status` — list all cell parameters

Reparameterization rebuilds the **whole torus** from scratch and swaps
it into the same pack slot (the packing is small; local surgery on a
closed surface is delicate). `undo` reverts the last
reparameterization.

### "Brooks parameter" canvas mode (gear cursor)

Interactive route to `set_param`. Select the gear cursor from the
main canvas **Active cursors** menu, then:

1. Click a circle interior to a cell — the cell highlights.
2. A dialog opens pre-filled with the cell's current parameter; edit
   the sequence and press OK. The torus is rebuilt, repacked, and
   redrawn.
3. The mode stays active for more clicks; right-click exits.

Clicking a shared corner circle (it belongs to four cells) or a
packing without the extender gives a message instead.

### `normalize_torus`

For a euclidean torus with radii and layout computed: sets up the two
side-pairings if needed, moves the corner vertex to 0 with an
adjacent corner at 1, so the fundamental domain has corners
**0, 1, 1+τ, τ**. Reports τ — the Teichmüller parameter, i.e. the
actual lattice period — and its modular-group representative in the
fundamental region (what `torus_t` reports). Sets alpha to the
corner vertex.

### `tile_torus [m [n]] [-flags]`

Displays a euclidean torus as a doubly periodic tiling: clears the
canvas and draws a (2m+1)×(2n+1) lattice of copies of the fundamental
domain, every circle shifted by integer combinations of the two
period vectors. Defaults m=n=2; `tile_torus m` uses m both ways.

Optional flags in the usual `disp` style choose what is drawn, in
the order given: `-c` circles, `-cf` filled circles, `-f` faces,
`-ff` filled faces. Options follow as in `disp` (color codes,
`t3` thickness); objects use their stored colors when no color is
given. E.g. `tile_torus 2 -cf` or `tile_torus 3 -ff -c`.

- Run `normalize_torus` first for the standard 0/1/1+τ/τ corners.
- Only **flat** tori can be tiled by translation; affine tori (where
  the fundamental domain is not a parallelogram) are rejected.
- The tiling (including flags) is recorded for redraw, so **zooming
  and panning preserve it** (see [Redraw behavior](#redraw-behavior)).
- Large tilings take a while to draw; the cursor switches to the
  system busy cursor while CirclePack is working.

Typical sequence:

```
create brooks_torus 3 3 2 1
normalize_torus
tile_torus 2 -ff -c
```

---

## Welding

### "weld" canvas mode (mapping-pair cursor)

Interactive boundary welding, selected from the **Active cursors**
menu. Four clicks choose the two boundary arcs:

1. **Click 1–2**: start and end of the first arc — the arc runs
   **clockwise** from start to end (the `adjoin` convention).
2. **Click 3–4**: start and end of the second arc — this one runs
   **counterclockwise**.
3. Clicking the *same* circle twice selects that whole boundary
   component.
4. To weld two different packings, switch the active pack between
   clicks 2 and 3. Both packings must have the same geometry
   (`geom_to_e` / `geom_to_h` to convert first).

Selections are highlighted as they are made. If the two arcs have
different edge counts, a dialog offers **boundary refinement**: new
vertices are interpolated by arc length (in each packing's current
geometry) on both sides until the counts match — the linear
arc-length matching from the dissertation. The weld then runs
`adjoin`, puts the result in the first packing's slot, repacks and
redraws. The seam vertices are left in `vlist`
(e.g. `disp -cc195t3 vlist` to highlight the seam).

Right-click cancels and leaves the mode. `undo` reverts a completed
weld — including the refinement, on **both** packings if two were
involved.

The non-interactive equivalent is Ken's `adjoin p q v w n` command,
which now also saves an `undo` snapshot.

### Map-driven welding (`extender cw`)

Brock's conformal-welding machinery (revived from the original C
code) lives in the `CONFORMAL_WELDING` pack extender. Attach with
`extender cw`, then:

- `|cw| weld -q{p} v w -f {mapfile} -a` — weld the attached packing
  onto pack p, matching the boundaries through a welding map
  h: [0,1] → [0,1] read from the file (`-a` performs the adjoin).
- `|cw| findWM -q{q} v w n` / `|cw| writeHomeo {file}` — the inverse
  problem: recover the welding homeomorphism from two max packings.
- `|cw| unweld {e..}` — cut a packing apart along an edge path.
- `|cw| randC {N}` — build a random packing: ~N² random points in a
  random N-gon inscribed in the circle, Delaunay-triangulated
  (Shewchuk's `triangle`, bundled for macOS) and max-packed.

### `weldmap` — welding-map editor

Opens a square-canvas editor for building the welding map, like a
game-controller response curve. Left-click adds a control point,
dragging moves one (coordinates are clamped between its neighbors',
so the map always stays 1-to-1), right-click deletes; endpoints are
fixed at (0,0) and (1,1). A formula in x can be entered instead
(e.g. `x+0.1*sin(2*Pi*x)`) — it is sampled, normalized to h(0)=0,
h(1)=1, and checked for strict monotonicity. **Save…** writes the
`PATH x y ... END` file that `|cw| weld -f` reads.

---

## Undo

### `undo`

Restores the packing(s) saved before the most recent destructive
operation. Operations that currently save a snapshot:

- `adjoin` (command)
- the interactive **weld** mode (snapshots both packings *before*
  boundary refinement, so a refined-then-welded pair is fully
  restored)
- Brooks reparameterization (`|bt| set_param` or the Brooks
  parameter mode — the extender's recorded parameters are restored
  too)

One level only: each new operation replaces the saved state. `undo`
with nothing saved is harmless ("nothing to undo").

Implementation note: snapshots live in `packing/PackUndo.java`. An
operation that *replaces* the `PackData` in its slot saves a
reference; one that *mutates* in place (weld refinement) saves a
`copyPackTo()` copy. To make another operation undoable, call
`PackUndo.save(...)` before it changes anything.

---

## Redraw behavior

`disp -wr` ("wipe and redraw") is what zooming (pinch, scroll wheel,
toolbar buttons) and panning issue. It now replays the **full drawing
history since the last canvas wipe** — every successful `disp`
command plus `tile_torus` — so faces, filled circles, edge colorings,
and torus tilings survive zoom and pan instead of being reduced to
plain circles.

Details:

- Each pack's canvas (`CPdrawing`) records the commands; a command
  starting with `-w` starts a fresh history, others append.
- If there is no history (e.g. right after loading), `disp -wr`
  falls back to the old behavior: the stored display options from
  the Screen panel / `set_disp_flags` / `Disp`.
- The history is capped at 512 commands; entries that fail on replay
  (e.g. a vertex that no longer exists) are skipped silently.
- `disp ... -q{p}` (drawing one pack's objects on another pack's
  canvas) is *not* recorded.

---

## Script editor

The script window opens in a plain-text **Simple Editor** (one
command per line, blank lines skipped) instead of the classic
drag-and-drop tree editor; a button at the bottom toggles between
the two. Extras in simple mode:

- The next line to execute is highlighted yellow; the **Next**
  button steps through.
- `[name]:= command` (or `[name]: command`) labels a line; keyboard
  shortcuts can find commands by the first letter of the name.
- Files are saved as minimal `.cps` XML, and `.xml` / `.cps.xml`
  files (what macOS makes of downloaded scripts) load fine.

---

## Maintaining the docs

The Help window's "command details" page, its index, and the
command-completion data are all generated from one XML-ish source:

1. Edit `src/Resources/doc/CmdDetails.txt` (entries are
   alphabetical; `<command cmd="..." flags="...">` with
   `<description>`, `<options>`, `<examples>`, `<seealso>`).
2. Regenerate (the tool wants a `CirclePack/` prefix on `user.dir`,
   hence the symlink):
   ```bash
   mkdir -p /tmp/cphelp && ln -sfn "$PWD" /tmp/cphelp/CirclePack
   cd /tmp/cphelp
   java -cp "CirclePack/out:CirclePack/cpcore.jar:CirclePack/jars/*" \
        infoProcessing.Info2HTML
   ```
   This rewrites `CmdDetails.html`, `CmdIndex.html`, and
   `CmdCompletion.txt` next to the `.txt`.
3. Copy the four files into `out/Resources/doc/` — `out/` precedes
   `cpcore.jar` on the classpath, so the running app picks them up:
   ```bash
   cp src/Resources/doc/Cmd{Details.txt,Details.html,Index.html,Completion.txt} out/Resources/doc/
   ```

This file (`docs/COMMANDS.md`) is the narrative companion; keep the
two in sync when commands change.
