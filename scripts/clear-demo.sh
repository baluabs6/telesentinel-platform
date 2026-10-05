#!/usr/bin/env bash
# Clears the alarms raised by demo.sh. The incident resolves automatically shortly afterwards.
set -euo pipefail
URL="${1:-http://localhost:8081}"
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
clear_alarm() {  # id node severity type
  curl -s -X POST "$URL/api/v1/alarms" -H 'Content-Type: application/json' -d \
   "{\"alarmId\":\"$1\",\"nodeId\":\"$2\",\"severity\":\"$3\",\"type\":\"$4\",\"message\":\"cleared\",\"raisedAt\":\"$NOW\",\"status\":\"CLEARED\"}"; echo
}
clear_alarm A1c gnb-204 MAJOR NO_BACKHAUL
clear_alarm A2c gnb-205 MAJOR NO_BACKHAUL
clear_alarm A3c agg-router-12 CRITICAL LINK_DOWN
