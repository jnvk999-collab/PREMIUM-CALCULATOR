/**
 * Pulls the latest bot files from GitHub into this folder.
 *   node update.js
 * Keeps your .env, inbox/ and login. Runs `npm install` if package.json changed.
 * The list of files comes from update-manifest.json on GitHub, so new files
 * are picked up automatically. If update.js itself changes, it re-runs once.
 */
const fs = require('fs');
const path = require('path');
const { execSync, spawnSync } = require('child_process');

const BASE = process.env.UPDATE_BASE_URL
  || 'https://raw.githubusercontent.com/jnvk999-collab/PREMIUM-CALCULATOR/claude/whatsapp-photo-pdf-automation-mgr9gu/whatsapp-bot/';
const FALLBACK = ['bot.js', 'update.js', 'package.json', 'lib/calculator.js', 'lib/intents.js', 'lib/organise.js', 'lib/pdfMerge.js', 'lib/reply.js', 'lib/mailer.js'];

async function get(f) {
  const res = await fetch(BASE + f + '?t=' + Date.now());
  if (!res.ok) throw new Error(`${f}: HTTP ${res.status}`);
  return Buffer.from(await res.arrayBuffer());
}

(async () => {
  let files = FALLBACK;
  try { files = JSON.parse((await get('update-manifest.json')).toString()); } catch (e) { console.log('  (manifest unavailable, using built-in list)'); }

  // update the updater first; if it changed, run the new one instead
  if (!process.env.__UPDATER_RERUN) {
    const body = await get('update.js');
    const dest = path.join(__dirname, 'update.js');
    if (!fs.existsSync(dest) || !fs.readFileSync(dest).equals(body)) {
      fs.writeFileSync(dest, body);
      console.log('  updated update.js, re-running...');
      const r = spawnSync(process.execPath, [dest], { stdio: 'inherit', env: { ...process.env, __UPDATER_RERUN: '1' } });
      process.exit(r.status || 0);
    }
  }

  let changed = 0, pkgChanged = false;
  for (const f of files) {
    if (f === 'update.js') continue;
    let body;
    try { body = await get(f); } catch (e) { console.log('  skip ' + e.message); continue; }
    const dest = path.join(__dirname, f);
    if (fs.existsSync(dest) && fs.readFileSync(dest).equals(body)) continue;
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.writeFileSync(dest, body);
    console.log(`  updated ${f}`);
    changed++;
    if (f === 'package.json') pkgChanged = true;
  }
  if (!changed) return console.log('Already up to date.');
  if (pkgChanged || !fs.existsSync(path.join(__dirname, 'node_modules'))) {
    console.log('Installing packages...');
    execSync(process.platform === 'win32' ? 'npm.cmd install --no-audit --no-fund' : 'npm install --no-audit --no-fund', { cwd: __dirname, stdio: 'inherit' });
  }
  console.log(`\nUpdated ${changed} file(s). Restart the bot: Ctrl+C, then node bot.js`);
})().catch(e => { console.error('Update failed: ' + e.message); process.exit(1); });
