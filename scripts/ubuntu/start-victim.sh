#!/usr/bin/env bash

set -euo pipefail
source "$(cd -- "$(dirname -- "$0")" && pwd)/common.sh"

relay_port="${1:-18002}"
if [[ ! "$relay_port" =~ ^[0-9]{1,5}$ ]] || (( 10#$relay_port < 1 || 10#$relay_port > 65535 )); then
  echo "Relay port khong hop le: $relay_port" >&2
  exit 1
fi
relay_port="$((10#$relay_port))"

ensure_java_and_build
cd "$PROJECT_ROOT"
echo "Victim-01: 127.0.0.1:18001 -> Relay 127.0.0.1:$relay_port"
exec java -jar "$NODE_JAR" \
  --config "$PROJECT_ROOT/config/nodeA.properties" \
  --next-hop-port "$relay_port"
