#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.run"
BACKEND_PID_FILE="$RUN_DIR/backend.pid"
ADMIN_PID_FILE="$RUN_DIR/admin-web.pid"

stop_from_pid_file() {
  local name="$1"
  local pid_file="$2"

  if [[ ! -f "$pid_file" ]]; then
    echo "$name: no pid file, skip"
    return
  fi

  local pid
  pid="$(cat "$pid_file")"
  if ps -p "$pid" >/dev/null 2>&1; then
    kill "$pid"
    echo "$name: stopped pid $pid"
  else
    echo "$name: pid $pid not running"
  fi
  rm -f "$pid_file"
}

stop_from_pid_file "admin-web" "$ADMIN_PID_FILE"
stop_from_pid_file "backend" "$BACKEND_PID_FILE"
