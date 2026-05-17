#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.run"
BACKEND_PID_FILE="$RUN_DIR/backend.pid"
ADMIN_PID_FILE="$RUN_DIR/admin-web.pid"

print_service() {
  local name="$1"
  local port="$2"
  local pid_file="$3"

  local port_pid
  port_pid="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null | head -n 1 || true)"

  echo "$name:"
  if [[ -f "$pid_file" ]]; then
    echo "  pid file: $(cat "$pid_file")"
  else
    echo "  pid file: none"
  fi

  if [[ -n "$port_pid" ]]; then
    echo "  listening: yes ($port_pid)"
    ps -p "$port_pid" -o command= 2>/dev/null | sed 's/^/  command: /'
  else
    echo "  listening: no"
  fi
}

print_service "backend" 7878 "$BACKEND_PID_FILE"
echo
print_service "admin-web" 5173 "$ADMIN_PID_FILE"
echo
echo "miniapp:"
echo "  run in WeChat DevTools"
echo "  dev output: c-uniapp/dist/dev/mp-weixin"
