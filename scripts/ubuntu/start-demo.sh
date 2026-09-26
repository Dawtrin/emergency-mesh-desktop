#!/usr/bin/env bash

set -euo pipefail
source "$(cd -- "$(dirname -- "$0")" && pwd)/common.sh"

windows_ip="${1:-}"
if [[ -z "$windows_ip" ]]; then
  read -r -p "Nhap IPv4 Host-only cua Windows (vi du 192.168.56.1): " windows_ip
fi

validate_peer_ip "$windows_ip"
if ! command -v gnome-terminal >/dev/null 2>&1; then
  echo "Khong co gnome-terminal. Chay hai lenh sau trong hai terminal rieng:"
  printf 'bash %q %q\n' "$SCRIPT_DIR/start-relay.sh" "$windows_ip"
  printf 'bash %q\n' "$SCRIPT_DIR/start-victim.sh"
  exit 1
fi
ensure_java_and_build
export REBUILD=0

gnome-terminal --title='Emergency Mesh - Relay-01' -- \
  bash "$SCRIPT_DIR/terminal-node.sh" "$SCRIPT_DIR/start-relay.sh" "$windows_ip"
gnome-terminal --title='Emergency Mesh - Victim-01' -- \
  bash "$SCRIPT_DIR/terminal-node.sh" "$SCRIPT_DIR/start-victim.sh"
echo "Da yeu cau mo hai terminal. Kiem tra log cua tung node de xac nhan da chay."
