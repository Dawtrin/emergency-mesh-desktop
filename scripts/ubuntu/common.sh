#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
NODE_JAR="$PROJECT_ROOT/target/MeshNodeClient-jar-with-dependencies.jar"

validate_peer_ip() {
  local peer_ip="$1" octet
  local -a octets
  if [[ ! "$peer_ip" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
    echo "IPv4 khong hop le: $peer_ip" >&2
    return 1
  fi
  IFS='.' read -r -a octets <<< "$peer_ip"
  for octet in "${octets[@]}"; do
    if (( 10#$octet > 255 )); then
      echo "IPv4 khong hop le: $peer_ip" >&2
      return 1
    fi
  done
  if (( 10#${octets[0]} == 0 || 10#${octets[0]} == 127 || 10#${octets[0]} >= 224 )); then
    echo "Hay nhap IP Host-only cua Windows, khong dung loopback/bind/multicast." >&2
    return 1
  fi
}

ensure_java_and_build() {
  if [[ -z "${DISPLAY:-}" && -z "${WAYLAND_DISPLAY:-}" ]]; then
    echo "Can Ubuntu Desktop GUI. Mo Terminal trong desktop cua VM." >&2
    exit 1
  fi
  if [[ -n "${JAVA_HOME:-}" ]]; then
    if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
      echo "JAVA_HOME khong hop le: $JAVA_HOME" >&2
      exit 1
    fi
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
  if ! command -v java >/dev/null 2>&1; then
    echo "Khong tim thay Java. Cai bang: sudo apt install openjdk-21-jdk" >&2
    exit 1
  fi
  local major
  major="$(java -version 2>&1 | awk -F'[\".]' '/version/ {print $2; exit}')"
  if [[ ! "$major" =~ ^[0-9]+$ ]] || (( major < 21 )); then
    echo "Can JDK 21 tro len. Hien tai:" >&2
    java -version >&2
    exit 1
  fi
  if [[ "${REBUILD:-0}" == 1 || ! -f "$NODE_JAR" ]]; then
    echo "Dang build project bang JDK 21..."
    (cd "$PROJECT_ROOT" && bash ./mvnw -q package)
  fi
}
