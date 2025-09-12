#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Ensure mongosh is available (attempt auto-install if missing)
if ! command -v mongosh >/dev/null 2>&1; then
  if [ -x "$SCRIPT_DIR/install-mongosh.sh" ]; then
    "$SCRIPT_DIR/install-mongosh.sh"
  else
    echo "[WARN] install-mongosh.sh not found. Trying to run it via bash..." >&2
    bash "$SCRIPT_DIR/install-mongosh.sh" || true
  fi
fi

MONGO_URI="${MONGO_URI:-mongodb://localhost:27017}"
DB_NAME="${MONGO_DB:-}"

if [[ -z "$DB_NAME" ]]; then
  APP_PROPS="$PROJECT_ROOT/../src/main/resources/application.properties"
  if [[ -f "$APP_PROPS" ]]; then
    DB_NAME="$(grep -E '^quarkus\.mongodb\.database=' "$APP_PROPS" | head -n1 | sed 's/.*=//')"
  fi
  if [[ -z "$DB_NAME" ]]; then DB_NAME="local_db"; fi
fi

INIT_DIR="$PROJECT_ROOT/../db/init"
if [[ -d "$INIT_DIR" ]]; then
  # Sort by name and apply
  find "$INIT_DIR" -maxdepth 1 -type f -name '*.js' -printf '%f\n' | sort | while read -r f; do
    echo "Applying init script $f ..."
    mongosh "$MONGO_URI/$DB_NAME" "$INIT_DIR/$f"
  done
else
  echo "No init scripts found."
fi
