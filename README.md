# rootstacks

Analysis toolkit for **BlueStacks Air** on Apple Silicon macOS — root, ADB, Frida,
mitmproxy/HTTPS interception, sideloading, reverse engineering.

## How BlueStacks Air works (verified on M5 / macOS 26.5, BlueStacks 5.21.755)

- **QEMU + Apple Hypervisor.framework (HVF)** running **native ARM64 Android 13**
  (Tiramisu, API 33, `arm64-v8a`). No x86 translation. Spoofs a Samsung SM-G998B.
- **SELinux is Disabled** and `ro.debuggable=1` out of the box — ideal for instrumentation.
- Bundled ADB: `/Applications/BlueStacks.app/Contents/MacOS/hd-adb`. The instance
  listens on **`127.0.0.1:5555`** when running.
- Boot chain (in `Contents/img/initrd_hvf.img`, a gzip'd cpio ramdisk):
  `/boot/init` (loads bst kernel modules, mounts `Root.qcow2` ro, extracts Android
  `ramdisk.img`, loop-mounts `system` ro, mounts data rw) → `stage2.sh` (runs as
  **real root**) → `exec /init` (Android init). `stage2.sh` is the root-injection point.
- Config: `/Users/Shared/Library/Application Support/BlueStacks/bluestacks.conf`.
  Instance data (writable): `…/Engine/Tiramisu64/data.qcow2`.

## Root

The `bst.feature.rooting` conf flag is a **dead end** — it doesn't create `su` and
BlueStacks reverts it on launch. Real root = patch `initrd_hvf.img` to embed
**Kitsune Magisk** (a Magisk Delta fork whose `magisk64` supports the `--setup-sbin`
invocation BlueStacks' non-standard boot needs; **stock Magisk fails** here with
`magisk64 --setup-sbin` exiting status 1). The patch injects `boot/magisk/*` +
`magisk.rc` into `init.bst.rc` via `stage2.sh`.

- APK: **Magisk-Delta-Kitsune-27.001** (`assets/magisk.apk`, sha256 in `assets/SHA256SUMS`),
  pinned by `RobThePCGuy/Root-Bluestacks-with-Kitsune-Mask`.
- The bundle's `initrd_hvf.img` is code-sealed in `_CodeSignature`, so replacing it
  breaks `codesign --verify` — but it's a data resource (not a dylib), so the
  hardened-runtime app still launches. Replacing needs **sudo** (root-owned bundle).

## Usage

```bash
bin/bs-status                 # arch, adb, root, selinux, frida state
bin/bs-root                   # patch initrd w/ Kitsune, swap (sudo), launch (auto-dismiss nag), grant su
bin/bs-repatch                # idempotent: re-root the bundle initrd if an update reverted it
bin/bs-frida up               # run frida-server as root + forward; connect: frida-ps -H 127.0.0.1:27042
bin/bs-mitm-cert [ca.pem]     # trust a CA in the system store (bind-mount); --module = persist
bin/bs-block-updates on|off   # stop BlueStacks reverting the rooted initrd (see Persistence)
bin/bs-proxy on [port]        # route guest traffic → Mac mitmproxy (10.0.2.2:port)
bin/bs-objection <pkg>        # objection explore over root frida (auto-forward 27042)
bin/bs-unpin <pkg>            # spawn app with SSL pinning bypassed (akabe1 script)
bin/bs-snapshot save|restore  # snapshot/restore the data disk (BlueStacks must be quit)
bin/bs-unroot                 # restore pristine initrd (sudo)
```

Sideloading works with no root: `hd-adb -s 127.0.0.1:5555 install app.apk`.

## Installed analysis tooling

Mac-side (Homebrew / the Python env that has frida): **frida 17.17**, **objection**,
**fritap** (TLS keylogger), **frida-dexdump**, **medusa** (`vendor/medusa`), **jadx**,
**apktool**, **apkeep**. On-device: **PCAPdroid**, **Kitsune Mask**, and Magisk modules
**rootstacks-frida** / **rootstacks-cacert** (+ Zygisk stack: ReZygisk/Shamiko/LSPosed).
Frida scripts in `assets/frida/` (`multiple-unpinning.js`, `il2cpp-bridge.js`).

Quick recipes:
- Intercept HTTPS: `bs-proxy on 8080` → `mitmproxy --listen-host 0.0.0.0 -p 8080` on the Mac.
- Pinned app: `bs-unpin com.target.app`  (or `bs-objection com.target.app` then `android sslpinning disable`).
- Native/Flutter TLS mitmproxy can't see: `fritap -m -k keys.log <pkg>` (uses root frida).
- Decompile: `jadx -d out app.apk`  ·  pull an APK: `apkeep -a com.target.app .`
- **Frida client/server versions must match** — server is 17.17 (auto-started by the Magisk module).

## Persistence

- **Survives reboot, automatically:** root (Magisk in the patched initrd), the ADB-shell
  su grant, **frida-server** and the **mitmproxy CA** — the last two are installed as Magisk
  modules under `/data/adb/modules/` (`rootstacks-frida`, `rootstacks-cacert`), which live on
  the data disk, not the app bundle.
- **Survives a BlueStacks app update:** everything in `/data` (both Magisk modules + su grant).
  The **initrd patch does NOT** — an update rewrites the app bundle back to a stock initrd.
- **Re-rooting after an update is one command:** `bin/bs-root` (or `bin/bs-repatch`).
  Run `bin/bs-block-updates on` so updates never happen unless you choose — that makes root
  effectively permanent.
- **Why not a fully-automatic daemon?** macOS **App Management** protection blocks background/
  launchd processes from writing inside `/Applications/BlueStacks.app`, even as root
  (`Operation not permitted`). Re-patch therefore runs from a Terminal that holds the grant,
  not a daemon. This is an OS security boundary, not a limitation of the tool.

## Reversibility

Pristine original initrd is kept at `assets/initrd_hvf.img.orig` (and in-bundle as
`initrd_hvf.img.orig-rootstacks`). `bin/bs-unroot` restores it. `bin/bs-block-updates off`
re-enables updates. Uninstall Magisk modules from the Kitsune Mask app.

## Do NOT
- Click **"Update"** on BlueStacks' nag (updating past 5.22.130.1019 adds a disk-integrity
  check that refuses a modified /system — much harder to root). `bs-root` auto-dismisses it.
- `kill -9` BlueStacks repeatedly or launch with `open -n` (spawns competing instances that
  wedge boot). The scripts use plain `open` + nag-dismiss.

## Layout

- `bin/` — toolkit scripts (`bs-env.sh` shared).
- `assets/` — pinned Kitsune apk, pristine initrd backup, `SHA256SUMS`.
- `vendor/root-bluestacks-air/` — the initrd patch tool (hanreev), vendored.
