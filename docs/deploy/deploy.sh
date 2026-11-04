#!/usr/bin/env bash
# /opt/insightshub/deploy.sh [tag]: pull the backend image (default: latest), restart it, and roll
# back to the image that was running if it isn't healthy within 3 minutes. CI pushes `latest` and
# `sha-<7>` on every push to main; pass a sha tag to pin or roll back by hand.
set -euo pipefail
cd /opt/insightshub
set -a; source ./deploy.env; set +a
export TAG=${1:-latest}
dc() { docker compose --env-file deploy.env "$@"; }

# Remember what runs now, as a local tag the rollback can return to.
running=$(dc ps -q backend 2>/dev/null || true)
if [[ -n "$running" ]]; then
  docker image tag "$(docker inspect --format '{{.Image}}' "$running")" "$IMAGE:previous"
fi

dc pull backend
dc up -d --remove-orphans

healthy() { curl -sf --max-time 5 http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; }

for _ in $(seq 1 36); do
  if healthy; then
    echo "deploy: $IMAGE:$TAG is up"
    docker image prune -f >/dev/null
    exit 0
  fi
  sleep 5
done

echo "deploy: $IMAGE:$TAG is not healthy after 3 minutes" >&2
dc logs --tail 80 backend >&2 || true
if [[ -n "$running" ]]; then
  echo "deploy: rolling back to the previous image" >&2
  TAG=previous dc up -d backend
fi
exit 1
