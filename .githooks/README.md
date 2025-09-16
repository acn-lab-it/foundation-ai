This directory contains Git hook scripts used by the repository.

Setup (one-time):
- Configure Git to use this directory for hooks: 
  git config core.hooksPath .githooks

What it does:
- pre-commit: Runs db/scripts/export to export MongoDB collections into db/collections and stages them automatically.
