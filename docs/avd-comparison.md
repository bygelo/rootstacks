# Rooted arm64 AVD vs. BlueStacks Air — the cleaner-root comparison

We stood up a **rooted Android Studio AVD** (arm64-v8a, google_apis, API 33) alongside
BlueStacks Air to compare root ergonomics head-to-head on the same Mac. Both run native
ARM64 Android 13 under a hypervisor; the difference is entirely in *how you root and keep it*.

## Setup (reproducible)
```
sdkmanager "emulator" "platform-tools" "system-images;android-33;google_apis;arm64-v8a"
avdmanager create avd -n rootstacks-avd -k "system-images;android-33;google_apis;arm64-v8a"
git clone https://github.com/newbit1/rootAVD && cd rootAVD
./rootAVD.sh system-images/android-33/google_apis/arm64-v8a/ramdisk.img   # patches ramdisk w/ Magisk
emulator -avd rootstacks-avd -no-snapshot -writable-system                # cold boot → rooted
```
rootAVD patches the image **ramdisk** (not a signed app bundle), installs Magisk.apk, and
keeps a `ramdisk.img.backup`. No sudo, no App-Management wall, no per-update re-patch dance.

## Head-to-head

| Dimension | BlueStacks Air (rootstacks) | Rooted arm64 AVD (rootAVD) |
|---|---|---|
| CPU model | QEMU + HVF, native ARM64 | QEMU (ranchu) + HVF, native ARM64 |
| Root method | patch signed `initrd_hvf.img` w/ Kitsune | patch image `ramdisk.img` w/ Magisk |
| Needs sudo / bundle write | **yes** (macOS App Management wall) | **no** (image lives in your SDK dir) |
| Survives OS/app update | **no** — reverts, re-run `bs-root` | **yes** — the SDK image is yours |
| Magisk flavor | Kitsune/Delta 27001 (stock fails on BS) | stock Magisk (rootAVD-managed) |
| Zygisk | ReZygisk (kernel blocks stock) | stock Zygisk works |
| Play Services / consumer apps | polished, Play Store, GameCenter | google_apis (no Play Store unless you add it) |
| Speed / app compat | faster, tuned for consumer apps/games | fussier, slower cold boot, dev-oriented |
| ADB | `127.0.0.1:5555` | `emulator-5560` / `127.0.0.1:5560` |
| Guest→host net | `10.0.2.2` (SLIRP) | `10.0.2.2` (SLIRP) — same |

## Verdict
- **Cleaner, more durable root → AVD.** No signed-bundle patching, no sudo, no App-Management
  wall, root survives updates. If your goal is *pure app RE*, the AVD is the lower-friction box.
- **Better fidelity / consumer-app behavior → BlueStacks Air.** Real Play Store, GameCenter,
  gaming optimizations, and the device spoof (SM-G998B) that many consumer apps expect.
- **Same frida/mitmproxy/objection tooling drives both** — the rootstacks `assets/frida/`
  scripts, `bs-proxy` routing (`10.0.2.2`), and the CA install approach are identical; only the
  ADB serial differs. You can point most `bs-*` helpers at the AVD by setting
  `BS_SERIAL=emulator-5560`.

## Root verification (measured)
Booted headless (`-gpu swiftshader_indirect`, port 5560), `boot_completed=1` in ~20s.
- **`adb root` → `uid=0(root) … context=u:r:su:s0` directly** — the AVD image is `userdebug`,
  so adb root just works and **sticks** (contrast BlueStacks Air, where `adb root` prints
  "restarting adbd as root" then reverts to shell). No sudo, no bundle patch, no nag dialog.
- **Magisk 25.2 (25200)** present from the rootAVD ramdisk patch (module support available).
- Toolkit reuse confirmed: `BS_SERIAL=emulator-5560 bin/bs-status` drives the AVD with the
  same scripts.

**Empirical verdict:** the AVD reaches root in one command (`adb root`) with none of BlueStacks
Air's friction (Kitsune requirement, signed-bundle sudo write, macOS App-Management wall,
per-update re-patch). It is the lower-friction, more durable root; BlueStacks Air wins only on
consumer-app fidelity (Play Store/GameCenter/gaming). Same frida/mitmproxy tooling on both.

## One caveat
Magisk `su` for the `shell` user still needs a manager grant (same as BlueStacks) — but on the
AVD you don't need it: `adb root` gives full root outright. Keep the emulator running with
`emulator -avd rootstacks-avd -no-snapshot` (or `-writable-system` if patching /system).
