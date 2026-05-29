#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.rocketmq.yml"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker: not found"
  echo "Please install Docker Desktop first, then run this script again."
  exit 1
fi

echo "Starting RocketMQ NameServer and Broker..."
docker compose -f "$COMPOSE_FILE" up -d

echo
echo "RocketMQ is starting."
echo "NameServer: localhost:9876"
echo "Broker ports: 10909, 10911, 10912"
echo
echo "Then start Spring Boot with:"
echo "  SPRING_PROFILES_ACTIVE=dev,mq ROCKETMQ_ENABLED=true ROCKETMQ_NAME_SERVER=localhost:9876 ./mvnw spring-boot:run"
