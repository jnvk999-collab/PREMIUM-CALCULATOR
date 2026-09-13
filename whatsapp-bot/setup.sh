#!/bin/bash
cd "$(dirname "$0")"
echo "=== OIC WhatsApp bot: one-time setup ==="
command -v node >/dev/null || { echo "Node.js is not installed. Install it from https://nodejs.org (LTS) and run this again."; exit 1; }
npm install
npx playwright install chromium
[ -f .env ] || cp .env.example .env
echo; echo "Running self-test (should end with ALL OK)..."
npm test
echo; echo "Setup finished. Now open .env in a text editor, fill in your name and allowed numbers, then run ./start.sh"
