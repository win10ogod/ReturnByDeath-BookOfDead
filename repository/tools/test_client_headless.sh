#!/usr/bin/env bash
# Isolated Linux/Xvfb display. Never connect the game to the desktop Wayland socket.
set -euo pipefail
cd "$(dirname "$0")/.."
rbd_runtime=$(mktemp -d /tmp/rbd-headless-runtime.XXXXXX)
trap 'rm -rf "$rbd_runtime"' EXIT
if [[ -f run/client/rbd-live-report.json ]]; then
    mv run/client/rbd-live-report.json run/client/rbd-live-report.previous.json
fi
env -u WAYLAND_DISPLAY -u WAYLAND_SOCKET \
    XDG_SESSION_TYPE=x11 XDG_RUNTIME_DIR="$rbd_runtime" \
    LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null \
    timeout --signal=INT 12m xvfb-run -a -s '-screen 0 1280x720x24' \
    bash gradlew --no-daemon --no-configuration-cache -PrbdLiveTest=true "$@" runClient
python3 - <<'PY'
import json
from pathlib import Path
report = json.loads(Path('run/client/rbd-live-report.json').read_text())
assert report['result'] == 'PASS', report
for name in ('rbd-memory-live.png', 'rbd-library-live.png', 'rbd-memory-ending.png',
             'rbd-memory-reduced.png', 'rbd-memory-silence.png', 'rbd-return-silence.png'):
    assert (Path('run/client/screenshots') / name).is_file(), name
print(json.dumps(report, ensure_ascii=False, indent=2))
PY
python3 ../tools/verify_immersion_screenshots.py run/client/screenshots
