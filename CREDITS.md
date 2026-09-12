# Credits & attribution

rootstacks is a thin analysis/persistence/RE layer built **on top of** other people's
work. The hard root primitive is not ours. Credit where due:

## Root mechanism (vendored, not written here)
- **[hanreev/root-bluestacks-air](https://github.com/hanreev/root-bluestacks-air)** — the
  `initrd_hvf.img` Magisk-injection tool. `vendor/root-bluestacks-air/` is a verbatim copy;
  `bin/bs-root` and `bin/bs-repatch` shell out to its `root.sh`. This is *the* BlueStacks Air
  rooter and the foundation everything else sits on.
- **[Jordan231111/bluestacks-air-oneclick-root](https://github.com/Jordan231111/bluestacks-air-oneclick-root)**
  — corroborating one-command wrapper of the same approach.
- **[RobThePCGuy/Root-Bluestacks-with-Kitsune-Mask](https://github.com/RobThePCGuy/Root-Bluestacks-with-Kitsune-Mask)**
  & **[BlueStacks-Root-GUI](https://github.com/RobThePCGuy/BlueStacks-Root-GUI)** — the pinned,
  hash-verified **Magisk-Delta-Kitsune 27.001** APK (`assets/magisk.apk`) and the Zygisk/ReZygisk
  guidance for BlueStacks.

## Magisk / Zygisk
- **[topjohnwu/Magisk](https://github.com/topjohnwu/Magisk)** and the **Kitsune Mask / Magisk
  Delta** fork ([HuskyDG](https://github.com/HuskyDG/magisk-files), [1q23lyc45](https://github.com/1q23lyc45/KitsuneMagisk)).
- **[PerformanC/ReZygisk](https://github.com/PerformanC/ReZygisk)** — the ptrace Zygisk injector
  that works on BlueStacks' kernel. **[JingMatrix/LSPosed](https://github.com/JingMatrix/LSPosed)**.

## Analysis tooling (installed, not authored)
- [frida/frida](https://github.com/frida/frida), [sensepost/objection](https://github.com/sensepost/objection),
  [Ch0pin/medusa](https://github.com/Ch0pin/medusa), [fkie-cad/friTap](https://github.com/fkie-cad/friTap),
  [emanuele-f/PCAPdroid](https://github.com/emanuele-f/PCAPdroid),
  [MG1937/ASC](https://github.com/MG1937/ASC) (Droid ASC, Apache-2.0, vendored at `vendor/asc/` pin `4df2f32`; `bs-decomp` shells out to it),
  [skylot/jadx](https://github.com/skylot/jadx), [iBotPeaches/Apktool](https://github.com/iBotPeaches/Apktool),
  [EFForg/apkeep](https://github.com/EFForg/apkeep), [rizinorg/rizin](https://github.com/rizinorg/rizin),
  [dwisiswant0/apkleaks](https://github.com/dwisiswant0/apkleaks),
  [quark-engine](https://github.com/quark-engine/quark-engine),
  [mitmproxy/mitmproxy](https://github.com/mitmproxy/mitmproxy).
- SSL-unpinning script: **@akabe1** (Maurizio Siddu), *frida-multiple-unpinning*.
- Unity bridge: [vfsfitvnm/frida-il2cpp-bridge](https://github.com/vfsfitvnm/frida-il2cpp-bridge).

## What IS original to rootstacks
The wiring, not the parts: the Kitsune-vs-stock diagnosis, the persistence engineering
(Magisk-module frida/cert + auto-reapply + the macOS App-Management finding), `bs-frida sync`,
`bs-doctor`, `bs-proxy transparent`, `bs-triage`, `bs-decomp`, `bs-hcall-trace`, and the
`docs/bluestacks-internals.md` reverse-engineering of BlueStacks' `libqvirt`/`bstpgaipc`
guest↔host bridge (no prior public write-up known).

## Licensing note
Vendored components keep their upstream licenses. hanreev's tool, Magisk/Kitsune, and the
GPL'd bstpgaipc kernel module are copyleft — anyone redistributing this repo must preserve
those notices. Confirm each upstream license before publishing.
