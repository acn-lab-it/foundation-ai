Database versioning and sync scripts

Overview
- This folder contains versioned JSON/JS definitions for MongoDB collections and data seeds.
- Goal: keep DB content under version control and provide scripts to synchronize repository <-> MongoDB instance.

Structure
- collections/ <collectionName>.json: plain JSON arrays or line-delimited JSON of documents to import.
- init/ <n>-<description>.js: ordered Mongo shell scripts for initializing or migrating data.
- scripts/: Bash scripts (primary) for export/import/seed, with PowerShell wrappers that invoke Bash via WSL on Windows.
  - If mongosh is not installed in your Linux/WSL environment, the scripts will attempt to install it automatically using db/scripts/install-mongosh.sh (may require sudo). On Ubuntu/WSL this follows the official MongoDB guide (apt repo + keyring). See: https://www.mongodb.com/docs/mongodb-shell/install/

Conventions
- Default database name comes from src/main/resources/application.properties (quarkus.mongodb.database). Override via env vars.
- Connection string defaults to mongodb://localhost:27017, override with MONGO_URI.

Quick start
- Export live DB to repo: scripts/export.sh (Linux/WSL) or scripts/export.ps1 (Windows, calls WSL)
- Import repo to DB (replace collections): scripts/import.sh or scripts/import.ps1
- Seed minimal dev data: scripts/seed.sh or scripts/seed.ps1

Note
- I wrapper PowerShell stampano la versione di PowerShell rilevata (PSVersion) all'esecuzione, utile per diagnosi.

Filter specific collections
- Export only some collections:
  - scripts/export.sh policy prompts
  - scripts/export.ps1 -Collections policy,prompts
- Import only some collections (must exist as db\collections\<name>.json):
  - scripts/import.sh prompts
  - scripts/import.ps1 -Collections @('policy','whathappened')

Safety
- import.ps1 drops and reimports only the listed collections. Review before running in non-dev environments.
