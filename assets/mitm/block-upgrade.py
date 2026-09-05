# Surgical BlueStacks update-block — a mitmproxy addon that fails ONLY the
# upgrade-check request, leaving every other cloud.bluestacks.com feature (login,
# nowbux, stats) working. The blunt alternative (bin/bs-block-updates) null-routes
# the whole host in /etc/hosts.
#
# Usage: point the guest at this proxy, run:
#   mitmdump -s assets/mitm/block-upgrade.py
# then `bs-proxy on 8080`. The BlueStacks *host app* (not the guest) makes the
# check_upgrade call, so to catch it set the MAC's system proxy or run this as a
# transparent proxy in front of the app. For the guest-only case it still blocks any
# in-guest upgrade pings.
from mitmproxy import http

BLOCK_PATHS = ("/bs3/check_upgrade",)

def request(flow: http.HTTPFlow) -> None:
    host = flow.request.pretty_host
    if host.endswith("bluestacks.com") and any(p in flow.request.path for p in BLOCK_PATHS):
        # Return a well-formed "no upgrade available" so the app proceeds cleanly.
        flow.response = http.Response.make(
            200, b'{"upgrade_available":false}',
            {"Content-Type": "application/json"},
        )
