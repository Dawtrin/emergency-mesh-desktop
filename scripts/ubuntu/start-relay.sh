#!/usr/bin/env bash

set -euo pipefail
source "$(cd -- "$(dirname -- "$0")" && pwd)/common.sh"

windows_ip="${1:-}"
if [[ -z "$windows_ip" ]]; then
  read -r -p "Nhap IPv4 Host-only cua Windows (vi du 192.168.56.1): " windows_ip
fi
validate_peer_ip "$windows_ip"

ensure_java_and_build
cd "$PROJECT_ROOT"
echo "Relay-01: 0.0.0.0:18002 -> Base $windows_ip:18888"
exec java -jar "$NODE_JAR" \
  --config "$PROJECT_ROOT/config/nodeB1.properties" \
  --next-hop-host "$windows_ip"
