#!/usr/bin/env bash
set -euo pipefail

# Report build script using the Jasper Reporter HTTP service.
# Usage: build.sh <report-file.jrxml> [--port PORT]
# Example: ./build.sh timesheet.jrxml
# Example: ./build.sh timesheet.jrxml --port 8081

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REPORTS_DIR="$ROOT_DIR/reports"
PORT=8080

if [ "$#" -lt 1 ]; then
  echo "Usage: $(basename "$0") <report-file.jrxml> [--port PORT]" >&2
  exit 2
fi

JRXML_FILE="$1"
shift || true

while [ $# -gt 0 ]; do
  case "$1" in
    --port)
      PORT="$2"
      shift 2
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
done

# Extract name without extension
NAME="${JRXML_FILE%.jrxml}"
if [ "$NAME" = "$JRXML_FILE" ]; then
  echo "Error: File must have .jrxml extension" >&2
  exit 1
fi
JRXML="$REPORTS_DIR/$NAME.jrxml"
JSON="$REPORTS_DIR/$NAME.json"
OUT_PDF="/tmp/$NAME-$(date +%s).pdf"
BASE_URL="http://localhost:$PORT"

if [ ! -f "$JRXML" ]; then
  echo "Error: JRXML not found: $JRXML" >&2
  exit 1
fi

if [ ! -f "$JSON" ]; then
  echo "Error: datasource JSON not found: $JSON" >&2
  exit 1
fi

# Check if service is running
if ! curl -s "$BASE_URL/list" >/dev/null 2>&1; then
  echo "Error: Jasper Reporter service not responding at $BASE_URL" >&2
  exit 1
fi

JASPER="${NAME}.jasper"
echo "Compiling $JRXML_FILE -> $JASPER"
COMPILE_RESPONSE=$(curl -sS -X POST "$BASE_URL/compile" \
  -H 'Content-Type: application/json' \
  -d "{\"source\":\"$JRXML_FILE\",\"target\":\"$JASPER\",\"force\":true}")

if ! echo "$COMPILE_RESPONSE" | grep -q '"success":true'; then
  echo "Compilation failed:" >&2
  echo "$COMPILE_RESPONSE" | jq . >&2
  exit 1
fi

echo "Rendering $JASPER -> PDF"
RENDER_PAYLOAD=$(jq -nc \
  --slurpfile json "$JSON" \
  "{report:\"$JASPER\",format:\"pdf\",datasource:{type:\"json\",data:\$json[0]}}")

curl -sS -X POST "$BASE_URL/render" \
  -H 'Content-Type: application/json' \
  -d "$RENDER_PAYLOAD" \
  -o "$OUT_PDF"

if [ ! -f "$OUT_PDF" ]; then
  echo "Error: PDF generation failed" >&2
  exit 1
fi

PDF_SIZE=$(stat -f%z "$OUT_PDF" 2>/dev/null || stat -c%s "$OUT_PDF" 2>/dev/null)
echo "✓ PDF generated: $OUT_PDF ($PDF_SIZE bytes)"
echo "Opening in default PDF viewer..."
open "$OUT_PDF"
