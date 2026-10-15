#!/bin/sh
set -eu
exec python3 "$(dirname "$0")/sync_remotes.py" "$@"
