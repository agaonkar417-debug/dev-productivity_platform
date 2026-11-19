#!/usr/bin/env bash
# Stops DevFlow AI. Your kits and history stay in the database volume.
# To also delete all data:  docker compose down -v
set -euo pipefail
cd "$(dirname "$0")"
docker compose down
echo "DevFlow AI stopped."
