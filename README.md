<h1 align="center">rootstacks</h1>

<p align="center">
  <b>The complete BlueStacks Air analysis stack for Apple Silicon macOS.</b><br>
  Root · ADB · Frida · mitmproxy/HTTPS interception · Zygisk/LSPosed · static+dynamic RE<br>
  — plus the first public reverse-engineering of BlueStacks' <code>libqvirt</code>/<code>bstpgaipc</code> guest↔host bridge.
</p>

<p align="center">
  <img alt="platform" src="https://img.shields.io/badge/platform-macOS%20Apple%20Silicon-black">
  <img alt="target" src="https://img.shields.io/badge/target-BlueStacks%20Air%20·%20Android%2013%20arm64-1f6feb">
  <img alt="root" src="https://img.shields.io/badge/root-Magisk%20Delta%2FKitsune-brightgreen">
  <img alt="doctor" src="https://img.shields.io/badge/bs--doctor-17%2F17-brightgreen">
  <img alt="license" src="https://img.shields.io/badge/license-MIT%20(+vendored)-blue">
</p>

---

Emulator rooters stop at `su`; generic Frida wrappers don't know what BlueStacks is. **rootstacks
is the layer in between** — it turns a stock BlueStacks Air install into a fully instrumented
Android RE box, and keeps it that way across reboots and updates.

Built **on** the community rooter ([hanreev/root-bluestacks-air](https://github.com/hanreev/root-bluestacks-air),
vendored + credited in [`CREDITS.md`](CREDITS.md)); rootstacks adds the analysis, persistence, and
internals layers around it.

## Quick start

```bash
git clone https://github.com/bygelo/rootstacks && cd rootstacks
bin/bs-fetch-assets     # download the 3rd-party binaries (Kitsune/ReZygisk/LSPosed/PCAPdroid)
bin/bs-root             # patch BlueStacks' initrd with Kitsune Magisk (asks for sudo), grant su
bin/bs-doctor           # verify the whole chain end-to-end  → 17/17
```

That's it — you now have root, a root Frida server, and a system-trusted mitmproxy CA.
Sideloading needs no root: `hd-adb -s 127.0.0.1:5555 install app.apk`.

> **Requirements:** BlueStacks Air ≤ 5.22.130.1019 (later builds add a disk-integrity check),
> Homebrew, and a Python env with `frida`. `bs-fetch-assets` pulls the rest.

## Commands

| Command | What it does |
|---|---|
| `bs-fetch-assets` | Download the 3rd-party binaries (run once after cloning) |
| `bs-root` | Patch `initrd_hvf.img` with Kitsune Magisk, launch (auto-dismiss the upgrade nag), grant ADB su |
| `bs-repatch` | Idempotently re-root the initrd if a BlueStacks update reverted it |
| `bs-unroot` | Restore the pristine initrd |
| `bs-doctor` | End-to-end self-test: adb → root → frida inject → cert → zygisk → tool versions |
| `bs-status` | One-look state: arch, root, SELinux, frida, zygisk, modules, proxy, updates |
| `bs-frida up \| sync` | Run frida-server as root; `sync` matches the on-device server to your frida client |
| `bs-proxy on \| transparent` | Route guest traffic → Mac mitmproxy (`transparent` = iptables for proxy-ignoring apps) |
| `bs-vpn install \| on \| off \| status \| check` | Guest TCP → gost redir → upstream SOCKS5 (floxy egress). Root-only; frida-server (uid 0) stays direct. |
| `bs-mitm-cert [ca.pem]` | Trust a CA in the system store (bind-mount; `--module` = persist) |
| `bs-objection <pkg>` | `objection explore` over the root frida server |
| `bs-unpin <pkg>` | Spawn an app with SSL pinning bypassed (akabe1 script) |
| `bs-bypass <pkg>` | Spawn an app with root + emulator detection bypassed (fridantiroot) |
| `bs-hide <pkg>` | Magisk denylist enforce + open LSPosed manager |
| `bs-triage <pkg\|apk>` | Static triage: apkleaks + jadx + apktool manifest + quark |
| `bs-decomp getclass \| findrefs \| gui` | On-demand class decompile + xref via vendored ASC (Python 3.11). `<apk\|pkg>` then class/query. |
| `bs-ghidra analyze \| manifest \| next \| decompile \| list` | Native-binary RE (headless Ghidra over `libqvirt`/`MacOS/BlueStacks`). Builds the per-function decompile work queue the agent swarm consumes. |
| `bs-ghidra mcp [--fetch]` | Wire up the Ghidra MCP bridge (`.mcp.json`) so agents drive Ghidra live. Playbook: [`docs/decomp-swarm.md`](docs/decomp-swarm.md). |
| `bs-install <apk>` / `bs-pull <pkg>` | Sideload / pull an APK |
| `bs-snapshot save \| restore` | Snapshot/restore the data disk (compressed) |
| `bs-hcall-trace [proc]` | Live-trace the guest↔host hcall channel (read-only RE) |

## How BlueStacks Air works

- **QEMU + Apple Hypervisor.framework (HVF)** running **native ARM64 Android 13** (API 33,
  `arm64-v8a`). No x86 translation. Spoofs a Samsung SM-G998B.
- **SELinux is Disabled** and `ro.debuggable=1` out of the box — ideal for instrumentation.
- ADB listens on **`127.0.0.1:5555`** (bundled `hd-adb`). Guest gateway `10.0.2.2` **is the Mac**.
- Boot chain (in `Contents/img/initrd_hvf.img`): `/boot/init` → `stage2.sh` (runs as **real root**)
  → Android `/init`. `stage2.sh` is the root-injection point.

Full teardown — the QEMU fork, the `bstpgaipc` bridge protocol, and the 173-opcode hcall table —
is in [`docs/bluestacks-internals.md`](docs/bluestacks-internals.md).

## Why Kitsune, and why the initrd

The `bst.feature.rooting` conf flag is a dead end (no `su`, and BlueStacks reverts it). Real root
means patching `initrd_hvf.img` to embed Magisk — and **stock Magisk fails** here (`magisk64
--setup-sbin` exits status 1). You need the **Kitsune / Magisk Delta** fork, whose `magisk64`
supports that path. `bs-fetch-assets` pulls the pinned, hash-verified `27.001` build.

## Persistence

| Survives… | What | How |
|---|---|---|
| **Reboot** (automatic) | root, ADB-su grant, frida-server, mitmproxy CA | Magisk in the initrd + `/data` Magisk modules (`rootstacks-frida`, `rootstacks-cacert`) |
| **BlueStacks update** | everything in `/data` | not the initrd — re-run `bs-root` (or `bs-block-updates on` so updates never happen) |

A fully-silent auto-reapply daemon is **impossible on macOS**: App Management blocks background
processes from writing inside `/Applications/BlueStacks.app` even as root. Re-root is one command
from a Terminal that holds the grant. Details in [Persistence notes](docs/bluestacks-internals.md).

## Recipes

```bash
# Intercept HTTPS (CA already trusted system-wide)
bs-proxy on 8080 ; mitmproxy --listen-host 0.0.0.0 -p 8080

# Bypass SSL pinning on a specific app
bs-unpin com.target.app          # or: bs-objection com.target.app → "android sslpinning disable"

# TLS that mitmproxy can't see (native/Flutter)
fritap -m -k keys.log com.target.app

# Static triage / decompile
bs-triage com.target.app ; jadx -d out app.apk ; apkeep -a com.target.app .
bs-decomp getclass com.target.app com.target.Main -o Main.java
bs-decomp findrefs app.apk string Authorization
```

> **Frida versions must match.** `bs-frida sync` fetches the on-device server for your client version.

## Installed tooling

Mac-side (Homebrew + the Python env with frida): `frida`, `objection`, `fritap`, `frida-dexdump`,
`jnitrace`, `medusa` (vendored), **ASC** (vendored `vendor/asc`, `bs-decomp`), `jadx`, `apktool`, `apkeep`, `rizin`, `apkleaks`, `quark`, `mitmproxy`.
On-device: Kitsune Mask, PCAPdroid, ReZygisk + LSPosed, and the `rootstacks-frida` / `rootstacks-cacert`
Magisk modules. Frida scripts in [`assets/frida/`](assets/frida).

## Docs

- [`docs/bluestacks-internals.md`](docs/bluestacks-internals.md) — the RE map: QEMU/HVF, the
  `bstpgaipc` bridge protocol, and the hcall dispatch table.
- [`docs/hcall-opcodes.tsv`](docs/hcall-opcodes.tsv) — 173 reverse-engineered hcall opcodes.
- [`docs/https-intercept-proof.md`](docs/https-intercept-proof.md) — end-to-end interception evidence.
- [`docs/avd-comparison.md`](docs/avd-comparison.md) — rooted arm64 AVD vs. BlueStacks Air.
- [`ROADMAP.md`](ROADMAP.md) · [`CREDITS.md`](CREDITS.md) · [`LICENSE`](LICENSE)

## Reversibility & safety

`bs-unroot` restores the original initrd (kept locally at `assets/initrd_hvf.img.orig` +
in-bundle `.orig-rootstacks`). `bs-block-updates off` re-enables updates. **Don't** click BlueStacks'
"Update" nag (later builds are much harder to root — `bs-root` auto-dismisses it), and don't launch
with `open -n`.

## Legal

For authorized security research and analysis of apps you own or are permitted to test. rootstacks
bundles no third-party binaries — `bs-fetch-assets` pulls them from their upstreams; BlueStacks'
proprietary images are never redistributed. See [`CREDITS.md`](CREDITS.md) for attribution and the
copyleft notices that vendored components carry.
