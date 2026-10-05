#!/usr/bin/env bash
# Sends a small demo: an alarm storm (agg-router-12 plus its cells) and an IRSF pattern.
# Usage: ./scripts/demo.sh [ingestion-url]   (default http://localhost:8081)
set -euo pipefail
URL="${1:-http://localhost:8081}"
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)

alarm() {  # id node severity type [status]
  curl -s -X POST "$URL/api/v1/alarms" -H 'Content-Type: application/json' -d \
   "{\"alarmId\":\"$1\",\"nodeId\":\"$2\",\"severity\":\"$3\",\"type\":\"$4\",\"message\":\"demo\",\"raisedAt\":\"$NOW\",\"status\":\"${5:-RAISED}\"}"; echo
}
cdr() {    # id from to seconds type
  curl -s -X POST "$URL/api/v1/cdrs" -H 'Content-Type: application/json' -d \
   "{\"cdrId\":\"$1\",\"callingNumber\":\"$2\",\"calledNumber\":\"$3\",\"startTime\":\"$NOW\",\"durationSeconds\":$4,\"callType\":\"$5\"}"; echo
}

echo "== Alarm storm: two cells and their aggregation router"
alarm A1 gnb-204 MAJOR NO_BACKHAUL
alarm A2 gnb-205 MAJOR NO_BACKHAUL
alarm A3 agg-router-12 CRITICAL LINK_DOWN

echo "== IRSF: 2 x 1000 s to a high-risk range"
cdr D1 +919800000001 +882130001 1000 INTERNATIONAL
cdr D2 +919800000001 +882130002 1000 INTERNATIONAL

echo "== Wangiri: 25 one-ring calls"
for i in $(seq 1 25); do cdr "W$i" +919811111111 "+9198000100$(printf %02d "$i")" 2 INTERNATIONAL >/dev/null; done

echo "To clear the fault later (incident auto-resolves ~30-40 s after all alarms clear):"
echo "  ./scripts/clear-demo.sh"
echo "Wait ~15 s, then:"
echo "  curl localhost:8083/api/v1/incidents"
echo "  curl localhost:8082/api/v1/fraud/alerts"
