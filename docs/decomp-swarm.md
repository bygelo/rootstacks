# decomp-swarm — multi-agent native decompilation for rootstacks

Methodology ported from Maurice Heumann's Modern Warfare 2 decompile (≈99% of the
binary, solo, ~11 weeks, up to 17 concurrent agents). The shape that made it work:

> **Worker agents decompile one function at a time. An overseer agent reviews every
> commit. Ghidra/IDA plug in through MCP. The agents coordinate over a shared channel.**

rootstacks already has the pieces — this doc wires them into that loop. It applies to
**native** targets (`libqvirt.dylib` = customized QEMU/HVF; `MacOS/BlueStacks` = the
173-opcode hcall dispatch). Dalvik/dex stays on `bs-decomp` (ASC); neither overlaps.

## The loop

```
          ┌─ bs-ghidra manifest ──────────────┐   the work queue: one row = one function
          │   re-projects/<bin>.manifest.tsv │   (TODO = still FUN_*; sorted biggest-first)
          └───────────────┬────────────────────┘
                          │  fan out, N at a time
        ┌─────────────────┼──────────────────────────┐
   ┌────▼────┐       ┌─────▼────┐                 ┌────▼────┐
   │ worker  │  ...  │  worker  │   each: owns ONE function
   │ agent   │       │  agent   │   - reads C via Ghidra MCP (live) or bs-ghidra decompile
   └────┬────┘       └─────┬────┘   - names it, types args, writes a doc note
        │  one commit / fn │        - commits on its own branch/worktree
        └─────────┬────────┘
            ┌─────▼─────┐   OVERSEER (= the /gate skill): no commit is accepted without
            │ overseer  │   fresh build/lint exit codes AND a human-legible name+evidence.
            │  reviews  │   Rejects a rename that isn't justified by xrefs/strings/callers.
            └─────┬─────┘
                  │  accepted work merges; manifest regenerates; repeat until TODO drains
                  ▼
          docs/bluestacks-internals.md  +  docs/hcall-opcodes.tsv   (the durable output)
```

## Map onto house skills (don't reinvent the orchestrator)

The MW2 "overseer reviews every commit" barrier already exists here as the house
skills — use them, don't hand-roll:

| MW2 role              | rootstacks mechanism                                              |
|-----------------------|-------------------------------------------------------------------|
| Worker agent / fn     | `/fanout <manifest>` (COMPLETE mode) — one item = one function     |
| Overseer / commit gate| `/gate` — refuses a done-claim without fresh exit codes + evidence |
| Ghidra/IDA over MCP   | `bs-ghidra-mcp` → `.mcp.json` `ghidra` server (live), `bs-ghidra decompile` (batch) |
| "coordinate in chat"  | shared manifest TSV + per-fn commit log (the git history IS the channel) |
| Run unattended to done| `/converge` wrapping the fanout; stops on a machine verdict, not a vibe |
| Parallel lanes        | `/parcel --warp` or git worktrees — one worker per lane, no file collisions |

Worker count: the post peaked at 17. On this box, scale by `/fanout` lane width and the
house spend-guard (subagents capped 60/hr). Start at 4–6; a native function is a bigger
unit of work than a dex method.

## Run it

```bash
# 0. one-time: live Ghidra over MCP (optional but matches the methodology)
bin/bs-ghidra-mcp --fetch        # vendor GhidraMCP, write .mcp.json (endpoint :8192)

# 1. build the work queue for a target
bin/bs-ghidra analyze            # import + auto-analyze libqvirt.dylib (slow, once)
bin/bs-ghidra manifest           # -> re-projects/libqvirt.dylib.manifest.tsv
bin/bs-ghidra next 10            # peek the top-10 TODO (biggest first)

# 2. a single worker's unit of work (what one agent does, by hand)
bin/bs-ghidra decompile 0x1004a2c10     # clean C for one function → name it, commit

# 3. fan it out, overseer-gated, until the queue drains
#    (from Claude Code, in this repo)
/fanout re-projects/libqvirt.dylib.manifest.tsv
#    or unattended:
/converge /fanout re-projects/libqvirt.dylib.manifest.tsv
```

## Acceptance (what the overseer enforces — the honesty barrier)

A function is "done" only when all hold, else it stays TODO:

1. A name that a caller/string/xref **justifies** — not a guess. Cite the evidence in the commit.
2. Argument + return types filled (via MCP `set_function_prototype` or a Ghidra data-type note).
3. A one-line behaviour note in `docs/bluestacks-internals.md` if it touches the hcall/qvirt path.
4. `bs-ghidra manifest` re-run shows it flipped `todo → named`. The manifest is the scoreboard.

Residual known target: `hcallCommonCommand`'s generic passthrough body (see ROADMAP
Northstar) — the one function the manual pass left. It's the natural first swarm item.

## Why this is the right scope for rootstacks

- The target is **proprietary and not vendored** (public release strips binaries). The harness
  reads the user's own `/Applications/BlueStacks.app` — nothing redistributable is added.
- This is **research-grade RE for interoperability/analysis** of software the user runs, the
  same category as the existing hcall teardown — not circumvention of a third party's product.
- The manifest/overseer gate means scale never buys sloppiness: every name is evidence-backed
  or it doesn't land. That's the part of the MW2 process worth copying, not just the agent count.
```
