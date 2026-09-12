# rootstacks ROADMAP — analysis/RE capabilities to add

Base already working: native ARM64 Android 13, Magisk Delta/Kitsune 27001 root
(persistent, ADB-granted), root frida-server 17.17.0, mitmproxy CA in system store,
SELinux permissive. Everything below is additive. Ranked by value for this exact box.

Platform truths that shape priorities:
- **Native ARM64 under HVF** → device-matched (arm64) binaries only; ignore x86-emulator tooling.
- **Play Integrity DEVICE/STRONG will never pass** on a VM (no hardware keybox). Aim for
  basic-integrity + root/frida *hiding*, not attestation.
- **BlueStacks' kernel blocks stock Zygisk injection** → use **ReZygisk** (ptrace injector),
  never Zygisk Next / built-in Zygisk (bootloop/denied).
- Guest net is `10.0.2.x`, gateway **`10.0.2.2` = the Mac** → point guest proxy at
  `10.0.2.2:8080` to reach a mac-side mitmproxy (no LAN IP needed).

---

## Tier 0 — persistence hardening (do first; protects what we built)
- [ ] `bin/bs-block-updates on` — stop BlueStacks reverting the rooted initrd / nagging to
      update past the rootable build. (Root itself already survives reboots; updates are the
      only thing that reverts it, and they're user-initiated installers.)
- [ ] After any deliberate BlueStacks update: `bin/bs-root` (one command re-roots).
      *Note:* macOS App Management blocks a background daemon from auto-writing the app bundle,
      so re-root is one command, not a silent daemon (see README "Persistence").
- [x] frida-server + mitm CA already installed as Magisk modules (`/data`) → auto across reboots+updates.

## Tier 1 — the daily RE toolkit (highest ROI)
- [x] **objection** 1.12.5 (`bs-objection <pkg>`) — SSL-unpin, root/emulator bypass, enum, dumps.
- [x] **medusa** — vendored at `vendor/medusa` (run `python vendor/medusa/mango.py`).
- [x] Static kit (brew, Apple-Silicon native): **jadx 1.5.6**, **apktool 3.0.3**, **apkeep 1.0.0**.
      Ghidra is optional (this box: `brew install --cask ghidra` → `ghidraRun`/`analyzeHeadless`; not required on a fresh clone).
- [x] SSL-pinning: akabe1 **multiple-unpinning.js** in `assets/frida/` (`bs-unpin <pkg>`);
      objection `android sslpinning disable` as the one-liner.

## Tier 2 — network interception depth (what mitmproxy misses)
- [x] Route guest → Mac mitmproxy: `bs-proxy on 8080` (sets `http_proxy 10.0.2.2:8080`).
      CA already trusted system-wide → most apps decrypt.
- [x] **friTap** 2.3.0 (`fritap -m -k keys.log <pkg>`) — frida TLS keylogger; beats native+Flutter pinning.
- [x] **PCAPdroid** installed on-device (on-device capture + decrypting proxy).
- [x] **`bs-vpn`** — egress-IP control via an *upstream* proxy (not just Mac interception):
      guest TCP `--iptables REDIRECT (owner !0)--> gost redir :12345 --SOCKS5--> floxy`.
      Root-only gost binary (`gost_3.3.0_android_arm64`), so every app socket takes one path
      while frida-server (uid 0) stays direct to the Mac. Verified 2026-09-07: guest exit IP =
      floxy `84.75.151.11`, game TCP redirected live. `bs-vpn install|on|off|status|check`.
- [ ] Fallback for apps that ignore the proxy: Magisk iptables REDIRECT 443→10.0.2.2 (transparent).

## Tier 3 — Zygisk stack (root/frida hiding + Xposed) — BlueStacks-specific care
- [x] **ReZygisk v1.0.0** — INSTALLED & RUNNING (`zygisk-ptrace64`+`zygiskd64` as root). Its ptrace
      injector works on BlueStacks where stock Zygisk fails, confirmed. Base for LSPosed.
- [x] **LSPosed** (JingMatrix "Vector" v2.2) — module `zygisk_vector` active. Open the manager from
      its boot notification (LSPosed hides its launcher icon).
- [ ] **Shamiko** — BLOCKED: every version's installer requires Magisk >27005; Kitsune Air is
      **27001** → `Please install Magisk Canary (>27005)`. Zip is zstd-compressed so it can't be
      patched locally. Substitute: Magisk **denylist** (built-in) + **objection/medusa runtime
      root-detection bypass** per app. Revisit if Kitsune ships a >27005 build.
- [ ] **PlayIntegrityFork** (osm0sis) — basic-integrity + fingerprint spoof only; DEVICE/STRONG
      never passes on a VM. Install only if a target does soft checks.

## Tier 4 — target-type specialists
- [x] **frida-il2cpp-bridge** — `assets/frida/il2cpp-bridge.js` (Unity IL2CPP dump/trace).
- [x] **frida-dexdump** 2.0.1 installed (`frida-dexdump -U -p <pid>`) — rip decrypted DEX (packed apps).
- [ ] **blutter** (+ **reFlutter**) — Flutter: recover Dart AOT symbols from libapp.so; heavy build,
      do on demand (`brew install` deps then build). → github.com/worawit/blutter
- [x] **jnitrace** — installed (`jnitrace`); live JNI/libc tracing.
- [ ] **r2frida** — live native memory (`r2pm` / on demand).

## Tier 5 — emulator/root-detection bypass (when a target fights back)
- [ ] objection/medusa runtime bypass first (patch Build.*, su/which probes) — no footprint.
- [ ] **FridaBypassKit** / **Anti-EmuDetector** — combined root+SSL+emulator+debug bypass scripts.
      BlueStacks already spoofs SM-G998B, so `Build` checks pass; these patch the QEMU/hypervisor
      artifacts + frida presence that still leak.

## Tier 6 — toolkit polish (rootstacks itself)
- [x] `bs-proxy on|transparent`, `bs-vpn` (upstream/floxy egress), `bs-objection`, `bs-unpin`, `bs-triage`, `bs-hide`, `bs-doctor`, `bs-frida sync`.
- [x] frida script library in `assets/frida/` (multiple-unpinning.js, il2cpp-bridge.js).
- [x] `bs-snapshot` — compressed data.qcow2 baseline (`rooted-baseline`, 4.1G).
- [x] Toolkit under git (`git log`); `.gitignore` excludes snapshots/large blobs.
- [x] `docs/bluestacks-internals.md` (hcall/qvirt RE) + `docs/https-intercept-proof.md` (e2e evidence).
- [x] `bs-install` / `bs-pull` convenience helpers.

## Explicitly out of scope (won't work here)
- Full Play Integrity DEVICE/STRONG (no keybox on a VM). Stock Zygisk / Zygisk Next (kernel-blocked).
  x86-only tooling (we're native ARM64). WireGuard transparent routing (10.0.2.2 already reaches host).

## Northstar / research-grade (done this pass)
- [x] **Reverse BlueStacks internals** — `libqvirt`=customized QEMU/HVF; `bstpgaipc` guest↔host
  bridge fully protocol-mapped (`ioctl(fd,OPCODE,&payload)` over a world-writable char dev;
  mmap+IRQ hot path; 74 hcalls). Live tracer `bs-hcall-trace`. Finding: **guest self-root via
  hcall is NOT reachable** (rooting hcalls are host-gated notifications) → initrd patch stays
  the root method. Doc: `docs/bluestacks-internals.md`. Opcode table dumped (173)
  in `docs/hcall-opcodes.tsv`; residual is `hcallCommonCommand`'s generic passthrough body.
- [x] **Publishable polish** — `CREDITS.md` (attribution), scoped `LICENSE` (MIT for original
  work; vendored copyleft preserved), README repositioned as the definitive Air stack.
- [ ] Before public push: confirm each vendored license; swap large binary assets for a download script.

## Research-grade follow-ups (done)
- [x] **Decompile `hcallCommonCommand` opcode table** — full hcall dispatch RE'd: sorted
  `{opcode, handler}` array in the main binary, **173 opcodes** → `docs/hcall-opcodes.tsv`.
  Proves every root/adb/install hcall is a `plrOn*` guest→host *notification* → guest self-root
  via hcall is impossible; initrd patch is the only path. `docs/bluestacks-internals.md`.
- [x] **Rooted arm64 AVD comparison** — stood up Android Studio AVD (google_apis arm64 33 +
  rootAVD Magisk 25.2). `adb root` → uid=0 directly (cleaner: no sudo/bundle-patch/App-Management,
  survives updates). Same tooling via `BS_SERIAL=emulator-5560`. `docs/avd-comparison.md`.
