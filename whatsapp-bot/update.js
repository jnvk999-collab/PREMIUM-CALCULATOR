/**
 * Pulls the latest bot files from GitHub into this folder.
 *   node update.js
 * Keeps your .env, inbox/ and login. Runs `npm install` if package.json changed.
 */
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const BASE = process.env.UPDATE_BASE_URL
  || 'https://raw.githubusercontent.com/jnvk999-collab/PREMIUM-CALCULATOR/claude/whatsapp-photo-pdf-automation-mgr9gu/whatsapp-bot/';
const FILES = [
  'bot.js', 'update.js', 'package.json', 'README.md', '.env.example',
  'lib/calculator.js', 'lib/intents.js', 'lib/organise.js', 'lib/pdfMerge.js', 'lib/reply.js',
  'test/smoke.js', 'setup.bat', 'setup.sh', 'start.bat', 'start.sh',
];

(async () => {
  let changed = 0, pkgChanged = false;
  for (const f of FILES) {
    const res = await fetch(BASE + f + '?t=' + Date.now());
    if (!res.ok) { console.log(`  skip ${f} (${res.status})`); continue; }
    const body = Buffer.from(await res.arrayBuffer());
    const dest = path.join(__dirname, f);
    const old = fs.existsSync(dest) ? fs.readFileSync(dest) : null;
    if (old && old.equals(body)) continue;
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.writeFileSync(dest, body);
    console.log(`  updated ${f}`);
    changed++;
    if (f === 'package.json') pkgChanged = true;
  }
  if (!changed) return console.log('Already up to date.');
  if (pkgChanged) {
    console.log('package.json changed, running npm install...');
    execSync(process.platform === 'win32' ? 'npm.cmd install --no-audit --no-fund' : 'npm install --no-audit --no-fund', { cwd: __dirname, stdio: 'inherit' });
  }
  console.log(`\nUpdated ${changed} file(s). Restart the bot: Ctrl+C, then node bot.js`);
})().catch(e => { console.error('Update failed: ' + e.message); process.exit(1); });
