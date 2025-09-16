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

# Collections to import: args list or detect from db/collections/*.json
COLL_DIR="$PROJECT_ROOT/../db/collections"
if [[ $# -gt 0 ]]; then
  collections=("$@")
else
  mapfile -t collections < <(find "$COLL_DIR" -maxdepth 1 -type f -name '*.json' -printf '%f\n' 2>/dev/null | sed 's/\.json$//' )
fi

relaxed=true

for coll in "${collections[@]:-}"; do
  file="$COLL_DIR/${coll}.json"
  if [[ ! -f "$file" ]]; then
    echo "[WARN] File not found for collection '$coll': $file. Skipping." >&2
    continue
  fi
  echo "Importing into ${DB_NAME}.${coll} from $file ..."
  rawContent="$(cat "$file")"
  if [[ -z "$rawContent" ]]; then
    echo "[WARN] Empty file for collection '$coll': $file. Skipping." >&2
    continue
  fi
  # Create temp JS file to avoid command-length issues
  tmpJsPath="$(mktemp --suffix=.js)"
  collJs="${coll//\'/'\\'}"
  dbJs="${DB_NAME//\'/'\\'}"
  # Escape EOF content safely
  {
    echo "const c = '$collJs';"
    echo "const raw = \`"
    printf '%s' "$rawContent"
    echo "\`;"
    echo "db = db.getSiblingDB('$dbJs');"
    echo "db.getCollection(c).drop();"
    echo "let docs;"
    echo "try {"
    echo "  docs = EJSON.parse(raw, { relaxed: ${relaxed} });"
    echo "} catch (e) { print('Failed to parse EJSON for collection ' + c + ': ' + e); quit(1); }"
    echo "if (!Array.isArray(docs)) { print('Input is not a JSON array for collection ' + c); quit(1); }"
    echo "if (docs.length > 0) { db.getCollection(c).insertMany(docs, { ordered: false }); print('Inserted ' + docs.length + ' docs into ' + c); } else { print('No documents to insert for ' + c); }"
  } > "$tmpJsPath"

  mongosh --quiet --file "$tmpJsPath" "$MONGO_URI/$DB_NAME" || true
  rm -f "$tmpJsPath"
 done
