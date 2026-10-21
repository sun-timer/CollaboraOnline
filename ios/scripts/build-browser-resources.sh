#!/bin/sh
# Build the resources bundled by Xcode, including when Xcode has a minimal PATH.
set -eu
browser_dir="$(CDPATH= cd -- "$(dirname -- "$0")/../../browser" && pwd)"
export PATH="/opt/homebrew/bin:/usr/local/bin:${PATH:-/usr/bin:/bin}"
node_binary="${NODE_BINARY:-}"
if [ -z "$node_binary" ] && [ -f "$browser_dir/Makefile" ]; then
    node_binary=$(sed -n 's/^NODE = //p' "$browser_dir/Makefile" | head -n 1)
fi
if [ -n "$node_binary" ] && [ -x "$node_binary" ]; then
    PATH="$(dirname -- "$node_binary"):$PATH"
    export PATH
fi
if ! command -v node >/dev/null 2>&1; then
    echo "error: Node.js is unavailable. Configure Online with Node.js on PATH or set NODE_BINARY in Xcode." >&2
    exit 1
fi
exec /usr/bin/make -C "$browser_dir"
