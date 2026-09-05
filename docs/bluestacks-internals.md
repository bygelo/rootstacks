# BlueStacks Air internals (reverse-engineering notes)

Findings from static inspection of the macOS/Apple-Silicon bundle. Research item —
no product surface; this is the map for future work.

## VMM = a customized QEMU (`libqvirt.dylib`)

`Contents/Frameworks/libqvirt.dylib` (arm64, ~24 MB) is a **QEMU fork** driving
Apple's **Hypervisor.framework (HVF)**. Confirmed by its embedded QEMU option help:
`virtio`, `dirty-ring-size`, `-chardev ringbuf`, `one-insn-per-tb`, `dump-guest-core`,
`hostfwd/guestfwd` (SLIRP user-net — hence guest `10.0.2.x`, gateway `10.0.2.2` = host),
`maxmem`, `vhostforce`. Boot is QEMU-style: `-kernel kernel_hvf -initrd initrd_hvf.img
-append <cmdline>` (see the `SHELL_BEFORE_*` cmdline debug hooks in the initrd's `init`).

## Guest ↔ host bridge = `hcall` RPC over `bstpgaipc`

The host app (`Contents/MacOS/BlueStacks`) exposes **74 `hcall*` handlers** — a guest→host
RPC surface. Transport is the guest kernel module **`bstpgaipc.ko`** (loaded first in the
initrd's `init`), a paravirtualized IPC channel into `libqvirt`. Sibling guest drivers:
`bstvmsg` (messaging), `bstinput`, `bstaudio`, `bstcamera`, `bstgmd` (graphics).

### Security-relevant hcalls
| hcall | meaning |
|---|---|
| `hcallOnRootDeviceCompleted` | native "root device" flow exists (the `enable_root_access` path) — host-mediated, gated by `bst.feature.rooting` |
| `hcallOnEnableAdbCompleted` | ADB enable handshake (matches `bst.enable_adb_access`) |
| `hcallOnInstallApkCompleted` / `hcallSyncInstalledApps` / `hcallOnAppInstalled` | host-driven APK install + app inventory sync |
| `hcallCommonCommand` | **generic command channel** — the most interesting target: a catch-all guest→host command dispatcher |
| `hcallStartExportFiles` / `hcallStartImportFiles` / `hcallExportFiles` / `hcallUnzipFile` | host filesystem access from the guest (shared-folder + zip) |
| `hcallGetProps` / `hcallSetDeviceProfile` | device-profile spoofing (drives the SM-G998B identity) |
| `hcallHandleCustomIAP` / `hcallAvailableNowggAccounts` | now.gg account + IAP integration |

## Why this matters (future levers, not yet built)
- **Native root via hcall:** `hcallOnRootDeviceCompleted` implies the host can flip guest
  root through the bridge when `bst.feature.rooting=1`. We proved the *conf flag alone*
  doesn't yield `su` on this Air build (host reverts it), so the hcall path is host-gated —
  but reversing `hcallCommonCommand` / the rooting handler in `libqvirt` may reveal a cleaner
  root than patching `initrd_hvf.img`, and one that survives updates (no bundle write).
- **`hcallCommonCommand`** is the highest-value RE target — a generic dispatcher reachable
  from the guest. Map its sub-commands with rizin on `libqvirt.dylib` + the guest side via
  the `bstpgaipc` protocol (trace `/dev/` node the `.ko` creates, or hook it with the root
  frida-server on the guest kernel interface).
- **`hcallExportFiles`/`hcallUnzipFile`** are a guest→host filesystem path worth auditing.

## How to dig deeper
```
rizin -A /Applications/BlueStacks.app/Contents/Frameworks/libqvirt.dylib   # aaa; afl~hcall
nm -gU  /Applications/BlueStacks.app/Contents/Frameworks/libqvirt.dylib | grep -i hcall
# guest side: pull bstpgaipc.ko from the initrd, analyze its ioctl/char-dev interface
```
Status: mapped the interface + transport; deep decompilation of `hcallCommonCommand` is the
open thread (Tier deferred — high effort, no product surface yet).
