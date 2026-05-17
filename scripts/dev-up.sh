#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.run"
LOG_DIR="$RUN_DIR/logs"
BACKEND_PID_FILE="$RUN_DIR/backend.pid"
ADMIN_PID_FILE="$RUN_DIR/admin-web.pid"
BACKEND_PORT=7878
ADMIN_PORT=5173

mkdir -p "$LOG_DIR"

find_port_pid() {
  local port="$1"
  lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null | head -n 1 || true
}

wait_for_port() {
  local port="$1"
  local retries="${2:-20}"
  local delay="${3:-0.5}"

  for ((i = 0; i < retries; i++)); do
    local pid
    pid="$(find_port_pid "$port")"
    if [[ -n "$pid" ]]; then
      echo "$pid"
      return 0
    fi
    sleep "$delay"
  done
  return 1
}

describe_pid() {
  local pid="$1"
  ps -p "$pid" -o pid=,command= 2>/dev/null || true
}

start_backend() {
  local existing_pid
  existing_pid="$(find_port_pid "$BACKEND_PORT")"
  if [[ -n "$existing_pid" ]]; then
    echo "backend: port $BACKEND_PORT already in use -> $(describe_pid "$existing_pid")"
    return
  fi

  echo "backend: starting Spring Boot on $BACKEND_PORT"
  (
    cd "$ROOT_DIR"
    nohup ./mvnw spring-boot:run >"$LOG_DIR/backend.log" 2>&1 &
  )
  local new_pid
  if new_pid="$(wait_for_port "$BACKEND_PORT")"; then
    echo "$new_pid" >"$BACKEND_PID_FILE"
    echo "backend: pid $new_pid, log $LOG_DIR/backend.log"
  else
    echo "backend: started command, but port $BACKEND_PORT is not ready yet"
  fi
}

start_admin_web() {
  local existing_pid
  existing_pid="$(find_port_pid "$ADMIN_PORT")"
  if [[ -n "$existing_pid" ]]; then
    echo "admin-web: port $ADMIN_PORT already in use -> $(describe_pid "$existing_pid")"
    return
  fi

  echo "admin-web: starting Vite on $ADMIN_PORT"
  (
    cd "$ROOT_DIR/admin-web"
    nohup npm run dev >"$LOG_DIR/admin-web.log" 2>&1 &
  )
  local new_pid
  if new_pid="$(wait_for_port "$ADMIN_PORT")"; then
    echo "$new_pid" >"$ADMIN_PID_FILE"
    echo "admin-web: pid $new_pid, log $LOG_DIR/admin-web.log"
  else
    echo "admin-web: started command, but port $ADMIN_PORT is not ready yet"
  fi
}

print_summary() {
  echo
  echo "URLs:"
  echo "  backend   http://localhost:$BACKEND_PORT"
  echo "  admin-web http://localhost:$ADMIN_PORT"
  echo
  echo "Tip: miniapp still uses WeChat DevTools and c-uniapp/dist/dev/mp-weixin"
}

start_backend
start_admin_web
print_summary
