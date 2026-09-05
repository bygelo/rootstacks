// rootstacks — live BlueStacks guest↔host hcall tracer.
// Guest→host RPC is ioctl(fd, OPCODE, &payload) on /dev/bstpgaipc, where OPCODE
// is the hcall command id (see docs/bluestacks-internals.md). READ-ONLY: observes,
// never issues. Resolves each fd→path lazily so pre-existing fds are caught.
'use strict';
const DEV = '/dev/bstpgaipc';
const cache = {};              // fd -> path (lazy)
const seen = {};               // opcode -> count

const readlinkP = Module.getGlobalExportByName('readlink');
const readlink = readlinkP ? new NativeFunction(readlinkP, 'long', ['pointer', 'pointer', 'ulong']) : null;
const buf = Memory.alloc(512);
function fdpath(fd) {
  if (fd in cache) return cache[fd];
  let path = '';
  if (readlink) {
    try {
      const n = readlink(Memory.allocUtf8String('/proc/self/fd/' + fd), buf, 511).toInt32();
      if (n > 0) { buf.add(n).writeU8(0); path = buf.readCString(); }
    } catch (e) {}
  }
  cache[fd] = path; return path;
}

const ioctl = Module.getGlobalExportByName('ioctl');
Interceptor.attach(ioctl, {
  onEnter(a) {
    const fd = a[0].toInt32();
    if (fd < 0) return;
    if (fdpath(fd) !== DEV) return;
    const op = a[1].toInt32() >>> 0;
    let hex = '';
    try { const ba = a[2].readByteArray(16); if (ba) hex = Array.from(new Uint8Array(ba)).map((b) => ('0' + b.toString(16)).slice(-2)).join(' '); } catch (e) {}
    seen[op] = (seen[op] || 0) + 1;
    send({ t: 'hcall', fd, opcode: op, hex: '0x' + op.toString(16), payload16: hex, n: seen[op] });
  },
});
send({ t: 'ready', hasReadlink: !!readlink, note: 'lazy fd resolve, tracing ioctl on ' + DEV });
