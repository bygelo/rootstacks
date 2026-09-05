# rootstacks ROADMAP — analysis/RE capabilities to add

Base already working: native ARM64 Android 13, Magisk Delta/Kitsune 27001 root
(persistent, ADB-granted), root frida-server 17.9.1, mitmproxy CA in system store,
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
      (Ghidra: `brew install --cask ghidra` when you need .so decompilation — large, install on demand.)
- [x] SSL-pinning: akabe1 **multiple-unpinning.js** in `assets/frida/` (`bs-unpin <pkg>`);
      objection `android sslpinning disable` as the one-liner.

## Tier 2 — network interception depth (what mitmproxy misses)
- [x] Route guest → Mac mitmproxy: `bs-proxy on 8080` (sets `http_proxy 10.0.2.2:8080`).
      CA already trusted system-wide → most apps decrypt.
- [x] **friTap** 2.3.0 (`fritap -m -k keys.log <pkg>`) — frida TLS keylogger; beats native+Flutter pinning.
- [x] **PCAPdroid** installed on-device (on-device capture + decrypting proxy).
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
- [ ] **r2frida** / **jnitrace** — live native memory + JNI/libc tracing (`pip install`/`npm` on demand).

## Tier 5 — emulator/root-detection bypass (when a target fights back)
- [ ] objection/medusa runtime bypass first (patch Build.*, su/which probes) — no footprint.
- [ ] **FridaBypassKit** / **Anti-EmuDetector** — combined root+SSL+emulator+debug bypass scripts.
      BlueStacks already spoofs SM-G998B, so `Build` checks pass; these patch the QEMU/hypervisor
      artifacts + frida presence that still leak.

## Tier 6 — toolkit polish (rootstacks itself)
- [ ] `bs-install <apk>` / `bs-pull <pkg>` helpers; `bs-proxy on|off` (sets guest http_proxy).
- [ ] `bs-objection <pkg>` / `bs-unpin <pkg>` one-shot wrappers over frida.
- [ ] Bundle a curated frida script library (unpinning, root-bypass, common hooks) in `assets/`.
- [ ] `bs-snapshot` — copy data.qcow2 for a known-good rooted baseline to roll back to.

## Explicitly out of scope (won't work here)
- Full Play Integrity DEVICE/STRONG (no keybox on a VM). Stock Zygisk / Zygisk Next (kernel-blocked).
  x86-only tooling (we're native ARM64). WireGuard transparent routing (10.0.2.2 already reaches host).
