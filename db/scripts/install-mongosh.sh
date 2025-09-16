#!/usr/bin/env bash
set -euo pipefail

# This script installs MongoDB Shell (mongosh) if not already present.
# It supports common Linux distros and is intended to run inside WSL or Linux.
# It will attempt to use the official MongoDB repo on Debian/Ubuntu when possible,
# otherwise it will fall back to the distro package manager if it provides a 'mongosh' package.

if command -v mongosh >/dev/null 2>&1; then
  exit 0
fi

echo "[INFO] mongosh not found. Attempting installation..." >&2
if [ -r /etc/os-release ]; then . /etc/os-release; echo "[INFO] Detected: ID=${ID:-} ID_LIKE=${ID_LIKE:-} VERSION_ID=${VERSION_ID:-} VERSION_CODENAME=${VERSION_CODENAME:-}" >&2; fi

need_sudo() {
  if [ "${EUID:-$(id -u)}" -ne 0 ]; then
    echo sudo
  else
    echo
  fi
}

SUDO="$(need_sudo)"

# Detect OS
ID=""
ID_LIKE=""
if [ -r /etc/os-release ]; then
  # shellcheck disable=SC1091
  . /etc/os-release
  ID="${ID:-}"
  ID_LIKE="${ID_LIKE:-}"
fi

has_cmd() { command -v "$1" >/dev/null 2>&1; }

install_debian_ubuntu() {
  # Follow MongoDB Shell installation steps for Ubuntu/Debian using the 8.0 repository.
  # Reference (Ubuntu 24.04 Noble): https://www.mongodb.com/docs/mongodb-shell/install/
  if ! has_cmd apt-get; then
    return 1
  fi

  echo "[INFO] Detected Debian/Ubuntu family. Following MongoDB docs to install mongosh..." >&2

  # Determine Ubuntu codename (noble/jammy/focal/bionic)
  CODENAME="$(. /etc/os-release; echo "${VERSION_CODENAME:-}")"
  if [ -z "$CODENAME" ] && has_cmd lsb_release; then
    CODENAME="$(lsb_release -c -s 2>/dev/null || true)"
  fi
  if [ -z "$CODENAME" ]; then
    case "$VERSION_ID" in
      24.04) CODENAME=noble;;
      22.04) CODENAME=jammy;;
      20.04) CODENAME=focal;;
      18.04) CODENAME=bionic;;
    esac
  fi

  # Ensure required tools
  if ! has_cmd wget; then $SUDO apt-get update -y || true; $SUDO apt-get install -y wget; fi
  if ! has_cmd gnupg; then $SUDO apt-get update -y || true; $SUDO apt-get install -y gnupg; fi

  # Import MongoDB 8.0 public key to trusted.gpg.d (as .asc), per guide
  KEY_ASC="/etc/apt/trusted.gpg.d/server-8.0.asc"
  if [ ! -f "$KEY_ASC" ]; then
    echo "[INFO] Importing MongoDB 8.0 GPG key to $KEY_ASC" >&2
    wget -qO- https://www.mongodb.org/static/pgp/server-8.0.asc | $SUDO tee "$KEY_ASC" >/dev/null
  fi

  # Remove legacy MongoDB 7.0 repo list to avoid conflicts
  if [ -f /etc/apt/sources.list.d/mongodb-org-7.0.list ]; then
    echo "[INFO] Removing legacy MongoDB 7.0 repo list to avoid conflicts" >&2
    $SUDO rm -f /etc/apt/sources.list.d/mongodb-org-7.0.list
  fi

  # Create MongoDB 8.0 repo list
  if [ -n "$CODENAME" ]; then
    LISTFILE="/etc/apt/sources.list.d/mongodb-org-8.0.list"
    REPO_LINE="deb [ arch=amd64,arm64 ] https://repo.mongodb.org/apt/ubuntu $CODENAME/mongodb-org/8.0 multiverse"
    echo "[INFO] Adding repo: $REPO_LINE" >&2
    echo "$REPO_LINE" | $SUDO tee "$LISTFILE" >/dev/null
  fi

  $SUDO apt-get update -y || true

  # Install mongosh (latest stable) per guide; try fallbacks if needed
  if $SUDO apt-get install -y mongodb-mongosh; then
    return 0
  fi
  $SUDO apt-get install -y mongodb-mongosh-shared-openssl3 || \
  $SUDO apt-get install -y mongodb-mongosh-shared-openssl11 || \
  $SUDO apt-get install -y mongosh || return 1

  return 0
}

install_rhel_fedora() {
  if has_cmd dnf; then PM=dnf; elif has_cmd yum; then PM=yum; else PM=""; fi
  if [ -n "$PM" ]; then
    echo "[INFO] Trying $PM install mongosh..." >&2
    if $SUDO $PM install -y mongosh; then
      return 0
    fi
  fi
  return 1
}

install_suse() {
  if has_cmd zypper; then
    echo "[INFO] Trying zypper install mongosh..." >&2
    if $SUDO zypper --non-interactive install mongosh; then
      return 0
    fi
  fi
  return 1
}

install_alpine() {
  if has_cmd apk; then
    echo "[INFO] Trying apk add mongosh..." >&2
    if $SUDO apk add --no-cache mongosh; then
      return 0
    fi
  fi
  return 1
}

# Try install based on detection
if echo "$ID $ID_LIKE" | grep -qiE '(ubuntu|debian)'; then
  install_debian_ubuntu || true
elif echo "$ID $ID_LIKE" | grep -qiE '(rhel|centos|fedora)'; then
  install_rhel_fedora || true
elif echo "$ID $ID_LIKE" | grep -qi 'suse'; then
  install_suse || true
elif echo "$ID $ID_LIKE" | grep -qi 'alpine'; then
  install_alpine || true
fi

# Last resort: try any package manager present
if ! command -v mongosh >/dev/null 2>&1; then
  for pm in apt-get dnf yum zypper apk; do
    if has_cmd "$pm"; then
      echo "[INFO] Last resort: trying $pm install mongosh..." >&2
      case "$pm" in
        apt-get) $SUDO apt-get update -y || true; $SUDO apt-get install -y mongosh || true ;;
        dnf) $SUDO dnf install -y mongosh || true ;;
        yum) $SUDO yum install -y mongosh || true ;;
        zypper) $SUDO zypper --non-interactive install mongosh || true ;;
        apk) $SUDO apk add --no-cache mongosh || true ;;
      esac
      command -v mongosh >/dev/null 2>&1 && break || true
    fi
  done
fi

if command -v mongosh >/dev/null 2>&1; then
  echo "[INFO] mongosh installed successfully: $(mongosh --version 2>/dev/null | head -n1)" >&2
  exit 0
else
  echo "[ERROR] Unable to install mongosh automatically. Please install it manually inside your Linux/WSL environment." >&2
  echo "        See: https://www.mongodb.com/docs/mongodb-shell/install/" >&2
  exit 1
fi
