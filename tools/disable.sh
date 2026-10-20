#!/usr/bin/env bash
# Disable every package listed in a debloat file on the TV (one package per line, # comments allowed).
# Packages are disabled for user 0, never uninstalled. Undo with tools/restore.sh <same file>.
#
# Usage: TV_HOST=192.168.1.50 tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt
set -euo pipefail
LIST="${1:?usage: disable.sh <debloat list>}"
# Resolve the list against the caller's directory before moving into the repo.
LIST_DIR="$(cd "$(dirname "$LIST")" && pwd)"
LIST="$LIST_DIR/$(basename "$LIST")"
cd "$(dirname "$0")/.."
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt"; exit 1; }
# Lists come from contributors, and the package name is pasted into a shell command on the TV.
# Refuse the whole list unless every entry is a bare package name (#165).
# Normalise first: Notepad on Windows saves a BOM and CRLF, and without this the first "# TV" line
# arrives as a package name and every entry keeps a trailing \r (#208).
PKGS=$(LC_ALL=C awk 'NR==1{sub(/^\357\273\277/,"")} {sub(/\r$/,""); print}' "$LIST" \
  | grep -vE '^\s*(#|$)' | sed -E 's/\s+#.*$//' | awk '{print $1}')
BAD=$(printf '%s\n' "$PKGS" | grep -vE '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$' || true)
if [ -n "$BAD" ]; then
  { echo "refusing $LIST: not a package name:"; printf '%s\n' "$BAD" | sed 's/^/  /'; } >&2
  exit 1
fi
# A Wi-Fi network saved during the first-run wizard belongs to setupwraith, and Android deletes it the
# moment setupwraith is disabled: the TV drops off Wi-Fi, and ADB and the phone remote go with it.
# Skip it until the network has been forgotten and re-joined in Settings (it then belongs to
# android.uid.system). FORCE_SETUPWRAITH=1 disables it anyway, e.g. on Ethernet.
SETUP=com.google.android.tungsten.setupwraith
if printf '%s\n' "$PKGS" | grep -x "$SETUP" >/dev/null && [ -z "${FORCE_SETUPWRAITH:-}" ]; then
  WIFI="$("$PY" -I tools/tv.py "dumpsys wifi" || true)"
  # no grep -q here: with pipefail its early exit would SIGPIPE the grep before it and fail open
  if printf '%s\n' "$WIFI" | grep -o 'cname=[^ ]*' | grep setupwraith >/dev/null; then
    echo "Skipping $SETUP: the saved Wi-Fi network belongs to it, and disabling it would delete that" >&2
    echo "network and take the TV off Wi-Fi. On the TV, forget the network and join it again in" >&2
    echo "Settings > Network & Internet, then run this again (or set FORCE_SETUPWRAITH=1 on Ethernet)." >&2
    PKGS="$(printf '%s\n' "$PKGS" | grep -vx "$SETUP" || true)"
  fi
fi

{
  [ -n "$PKGS" ] && printf '%s\n' "$PKGS" | sed 's/^/pm disable-user --user 0 /'
  echo "pm list packages -d"
} | "$PY" -I tools/tv.py --batch
