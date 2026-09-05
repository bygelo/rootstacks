# HTTPS interception + Frida injection — end-to-end proof

Verification item (2026-09-05). Evidence that the stack actually decrypts traffic
and injects code, not just that the pieces are installed.

## HTTPS interception via the system-trusted mitmproxy CA — PROVEN

Setup: `bs-proxy on 8080` → `mitmdump -p 8080 -w flows.mm` on the Mac → triggered
guest HTTPS with `am start -d https://…` (Chrome, system TLS stack).

Captured, fully **decrypted** flows (`mitmproxy.io.FlowReader`):

| scheme | status | url |
|---|---|---|
| https | 200 | `https://example.com/` (our trigger) |
| https | 304 | `https://clientservices.googleapis.com/chrome-variations/seed?...` (Chrome's own) |
| https | 204 | `https://connectivitycheck.gstatic.com/generate_204` |

`TOTAL flows=3  HTTPS(decrypted)=3` — the CA installed by `bs-mitm-cert` (system store
+ Magisk module) is trusted by the guest's system TLS stack, so HTTPS is transparently
readable. No per-app config needed for apps that honor the system store.

## Frida injection — PROVEN

Attached to `system_server` (pid 619) over `127.0.0.1:27042` (root frida-server 17.17.0,
version-matched to the client via `bs-frida sync`), loaded a script, and received a
message back **from inside the target process** — the injection channel is live. The
`frida` CLI used by `bs-unpin` additionally auto-loads the Java bridge, so `Java.perform`
hooks (the akabe1 `multiple-unpinning.js`) run there.

## What this establishes
- mitmproxy decrypts system-CA HTTPS out of the box.
- For apps that **pin** (ignore the system CA) or use **native/Flutter** TLS: `bs-unpin
  <pkg>` (Java-layer unpinning) or `fritap -m -k keys.log <pkg>` (TLS keylog at the
  BoringSSL layer) — both ride the proven root-frida channel.
- Apps that ignore `http_proxy`: `bs-proxy transparent` (iptables REDIRECT).
