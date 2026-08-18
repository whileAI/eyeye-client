#!/usr/bin/env sh
set -eu

set -a
. /home/tesi/eyeye-client/server/.env
set +a

exec /home/tesi/.local/bin/cloudflared tunnel run --token "$TUNNEL_TOKEN"
