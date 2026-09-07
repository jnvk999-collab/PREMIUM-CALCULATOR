#!/bin/bash
cd "$(dirname "$0")"
while true; do node bot.js; echo "Bot stopped. Restarting in 10 seconds (Ctrl+C to stop)..."; sleep 10; done
