Database versioning and sync scripts

Overview
- This folder contains versioned JSON/JS definitions for MongoDB collections and data seeds.
- Goal: keep DB content under version control and provide scripts to synchronize repository <-> MongoDB instance.

Structure
- collections/ <collectionName>.json: plain JSON arrays or line-delimited JSON of documents to import.
- init/ <n>-<description>.js: ordered Mongo shell scripts for initializing or migrating data.
- scripts/: cross-platform PowerShell scripts to export/import collections.

Conventions
- Default database name comes from src/main/resources/application.properties (quarkus.mongodb.database). Override via env vars.
- Connection string defaults to mongodb://localhost:27017, override with MONGO_URI.

Quick start
- Export live DB to repo: scripts/export.ps1
- Import repo to DB (replace collections): scripts/import.ps1
- Seed minimal dev data: scripts/seed.ps1

Filter specific collections
- Export only some collections:
  - scripts/export.ps1 -Collections policy,prompts
  - scripts/export.ps1 -Collections @('policy','prompts')
- Import only some collections (must exist as db\collections\<name>.json):
  - scripts/import.ps1 -Collections prompts
  - scripts/import.ps1 -Collections @('policy','whathappened')

Safety
- import.ps1 drops and reimports only the listed collections. Review before running in non-dev environments.
