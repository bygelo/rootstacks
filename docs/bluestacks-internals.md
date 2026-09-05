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

## The `bstpgaipc` transport protocol (reverse-engineered)

Decoded from the guest driver `bstpgaipc.ko` (GPL, aarch64, kernel 5.15) — specifically
`bstpgaipc_read_write` + `bstpgaipc_hypercall`, cross-checked live on the running guest.

**Device:** `/dev/bstpgaipc` — misc char device, major 10 minor 125, mode **`crw-rw-rw-`
(world-writable)**. Any guest process, including unprivileged `shell` (uid 2000), can open it.

**Connection:** `open()` → `bstpgaipc_connect` allocates a **`cid`** (connection id), logged
to dmesg (`allocated cid N`). A process opens several (e.g. GMS DroidGuard holds ~11).

**Call:** userspace issues **`ioctl(fd, OPCODE, &payload)`**:
1. driver reads the fd's `cid`, `_copy_from_user`s the payload into a shared MMIO descriptor
   slot at `base + cid*16 + 4` (16-byte descriptors, host-visible via the PCI BAR0);
2. calls `bstpgaipc_hypercall(cid, OPCODE)` → pokes BAR0 MMIO → **traps to the host
   (libqvirt/QEMU)**, which reads the descriptor and dispatches to hcall handler `OPCODE`
   in the main `BlueStacks` binary;
3. host writes the result back into the descriptor; driver `_copy_to_user`s it and returns
   the status word (bit 0x1f set = error). Error path logs `cid %d, cmd %d, len %d: host cmd failed`.

**So `OPCODE` == the ioctl request number == the hcall command id.** `bstpgaipc_wait_host`
+ the threaded IRQ handler (`bstpgaipc_irq_handler_threaded`) handle the async/host-notify
direction. `bin/bs-hcall-trace` observes live `(fd, opcode, payload)` via root frida (read-only).

**Two data paths (measured):** the driver also exports `bstpgaipc_fops_mmap`, and consumers
map the BAR for a **zero-copy shared-memory ring** driven by `bstpgaipc_irq_handler_threaded`.
Empirically, ambient `ioctl` opcode traffic on framework processes is near-zero — the
high-frequency graphics/input path goes through the mmap ring + IRQ, while `ioctl(OPCODE)` is
the **control path** (occasional hcalls: props, install/root/adb completion, file export).
So `bs-hcall-trace` surfaces the interesting *control* hcalls; it won't show the bulk GL traffic.

### Security assessment — can the guest self-root through this?
Direction matters. The rooting/adb hcalls are `hcallOn*Completed` = guest→host **notifications**
("root finished"), plus host **services** (GetProps, ExportFiles, UnzipFile). The actual
root-enable is **host-initiated and gated by `bst.feature.rooting`** — we proved the guest can't
flip it (the conf flag is host-reverted, no `su` appears). So **firing an hcall from the guest
does not grant guest root** — the privileged direction is host→guest.

The residual interest is **`hcallCommonCommand`** (generic dispatcher) and the file hcalls
(`ExportFiles`/`ImportFiles`/`UnzipFile`) as a guest→host **filesystem** surface — a sandbox /
host-fs audit target, not a self-root. Blindly issuing opcodes is risky (MMIO to the host);
map the opcode→handler table in the main binary first (static) and trace live before issuing.

## How to dig deeper
```
rizin -A /Applications/BlueStacks.app/Contents/Frameworks/libqvirt.dylib   # aaa; afl~hcall
nm -gU  /Applications/BlueStacks.app/Contents/Frameworks/libqvirt.dylib | grep -i hcall
# guest side: pull bstpgaipc.ko from the initrd, analyze its ioctl/char-dev interface
```
Status: mapped the interface + transport; deep decompilation of `hcallCommonCommand` is the
open thread (Tier deferred — high effort, no product surface yet).
