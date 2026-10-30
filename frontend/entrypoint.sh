#!/bin/sh
set -e

envsubst < /app/js/config.template.js > /app/js/config.js

cd /app
exec python3 -u server.py
