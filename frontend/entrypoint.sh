#!/bin/sh
set -e

envsubst < /app/js/config.template.js > /app/js/config.js

exec python3 -m http.server "${PORT:-80}" --directory /app
