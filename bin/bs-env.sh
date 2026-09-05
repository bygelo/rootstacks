#!/bin/bash
# Shared environment for rootstacks — BlueStacks Air analysis toolkit (Apple Silicon).
# Source this from other scripts: . "$(dirname "$0")/bs-env.sh"

BS_APP="${BS_APP:-/Applications/BlueStacks.app}"
BS_ADB="${BS_ADB:-$BS_APP/Contents/MacOS/hd-adb}"
BS_SERIAL="${BS_SERIAL:-127.0.0.1:5555}"
BS_CONF="${BS_CONF:-/Users/Shared/Library/Application Support/BlueStacks/bluestacks.conf}"
BS_IMG_DIR="${BS_IMG_DIR:-$BS_APP/Contents/img}"
BS_INITRD="${BS_INITRD:-$BS_IMG_DIR/initrd_hvf.img}"
REPO_DIR="${REPO_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

# adb wrapper that always targets the BlueStacks instance
bsadb() { "$BS_ADB" -s "$BS_SERIAL" "$@"; }

bs_connect() { "$BS_ADB" connect "$BS_SERIAL" >/dev/null 2>&1; }

bs_wait_boot() {
  local n="${1:-40}" i bc
  for i in $(seq 1 "$n"); do
    bs_connect
    bc=$(bsadb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    [ "$bc" = "1" ] && { echo "boot_completed (~$((i*3))s)"; return 0; }
    sleep 3
  done
  echo "timeout waiting for boot" >&2; return 1
}

bs_running() { pgrep -f "MacOS/BlueStacks" >/dev/null 2>&1; }

bs_quit() { pkill -x BlueStacks 2>/dev/null; pkill -f "MacOS/BlueStacks" 2>/dev/null; sleep 3; }

bs_start() { open "$BS_APP"; }

# Dismiss the "Upgrade available" nag that silently blocks VM boot. Clicks the
# top-right X (button 1) only — NEVER "Update" (updating past 5.22.130.1019
# re-locks BlueStacks' disk-integrity check and undoes root).
bs_dismiss_upgrade() {
  /usr/bin/osascript <<'OSA' >/dev/null 2>&1
tell application "System Events"
  set ps to (every process whose name contains "BlueStacks")
  if (count of ps) is 0 then return
  repeat with w in (every window of (item 1 of ps))
    if (name of w) is "Upgrade available" then
      try
        click (button 1 of w)
      end try
    end if
  end repeat
end tell
OSA
}

# Launch and wait for Android, auto-dismissing the upgrade nag. Arg: max polls (default 60, x4s).
bs_launch_and_wait() {
  bs_start
  local n="${1:-60}" i bc
  for i in $(seq 1 "$n"); do
    sleep 4
    bs_dismiss_upgrade
    bs_connect
    bc=$(bsadb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    [ "$bc" = "1" ] && { echo "booted (~$((i*4))s)"; return 0; }
  done
  echo "boot timeout" >&2; return 1
}

# True if we have working root (magisk su)
bs_has_root() {
  bs_connect
  [ "$(bsadb shell 'su -c id -u' 2>/dev/null | tr -d '\r')" = "0" ]
}
