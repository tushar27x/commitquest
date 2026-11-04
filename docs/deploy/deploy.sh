#!/usr/bin/env bash
# /opt/insightshub/deploy.sh <jar>: install a new backend jar, restart, and roll back if it
# isn't healthy within 3 minutes. Run as root (the deploy workflow calls it with sudo).
set -euo pipefail

DIR=/opt/insightshub
NEW=${1:?usage: deploy.sh <path to jar>}
NAME=$(basename "$NEW")

install -o insightshub -g insightshub -m 0640 "$NEW" "$DIR/releases/$NAME"
rm -f "$NEW"
PREV=$(readlink -f "$DIR/app.jar" 2>/dev/null || true)

ln -sfn "$DIR/releases/$NAME" "$DIR/app.jar"
systemctl restart insightshub

healthy() { curl -sf --max-time 5 http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; }

for _ in $(seq 1 36); do
  if healthy; then
    echo "deploy: $NAME is up"
    # Keep the three newest releases for a manual rollback.
    ls -1t "$DIR/releases"/*.jar | tail -n +4 | xargs -r rm -f
    exit 0
  fi
  sleep 5
done

echo "deploy: $NAME is not healthy after 3 minutes, rolling back" >&2
journalctl -u insightshub -n 60 --no-pager >&2 || true
if [[ -n "$PREV" && "$PREV" != "$DIR/releases/$NAME" && -f "$PREV" ]]; then
  ln -sfn "$PREV" "$DIR/app.jar"
  systemctl restart insightshub
  echo "deploy: back on $(basename "$PREV")" >&2
fi
exit 1
