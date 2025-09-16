set -euo pipefail

# Determine project root as the directory containing this script's parent folder
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

# Defaults from env or fallbacks
MONGO_URI="${MONGO_URI:-mongodb://localhost:27017}"
DB_NAME="${MONGO_DB:-}"

# Read DB name from application.properties if not set
if [[ -z "$DB_NAME" ]]; then
  APP_PROPS="$PROJECT_ROOT/../src/main/resources/application.properties"
  # Try typical locations (when run from WSL inside repo)
  if [[ ! -f "$APP_PROPS" ]]; then
    APP_PROPS="$PROJECT_ROOT/../src/main/resources/application.properties"
  fi
  if [[ -f "$APP_PROPS" ]]; then
    DB_NAME="$(grep -E '^quarkus\.mongodb\.database=' "$APP_PROPS" | head -n1 | sed 's/.*=//')"
  fi
  if [[ -z "$DB_NAME" ]]; then
    DB_NAME="local_db"
  fi
fi

# Collections: from args or default set
if [[ $# -gt 0 ]]; then
  collections=("$@")
else
  collections=(policy whathappened prompts)
fi

EXPORT_DIR="$PROJECT_ROOT/../db/collections"
mkdir -p "$EXPORT_DIR"

for coll in "${collections[@]}"; do
  echo "Exporting ${DB_NAME}.${coll} ..."
  out="$EXPORT_DIR/${coll}.json"
  # Build inline JS for mongosh with pretty formatting (2 spaces)
  js="const c='${coll//\''/'\\''}'; const docs=db.getCollection(c).find({}).toArray(); print(JSON.stringify(docs,null, 2));"

  mongosh --quiet --eval "$js" "$MONGO_URI/$DB_NAME" >"$out"
done