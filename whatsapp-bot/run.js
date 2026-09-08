/**
 * Supervisor: keeps the bot running, updates it automatically, restarts it.
 *   node run.js
 *
 *  - starts bot.js and restarts it if it ever stops (5 s delay)
 *  - every UPDATE_CHECK_MINUTES (default 30) asks GitHub for the latest version;
 *    if there is one, runs update.js, asks the bot to finish pending photos,
 *    and restarts it
 *  - writes everything to logs/bot-YYYY-MM-DD.log as well as the screen
 *
 * Use install-autostart.js once to start this automatically at Windows login.
 */
const { spawn, spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');
try { require('dotenv').config(); } catch {}

const CHECK_MIN = parseInt(process.env.UPDATE_CHECK_MINUTES || '30', 10);
const AUTO_UPDATE = process.env.AUTO_UPDATE !== '0';
const REPO = 'jnvk999-collab/PREMIUM-CALCULATOR';
const BRANCH = 'claude/whatsapp-photo-pdf-automation-mgr9gu';
const LOG_DIR = path.join(__dirname, 'logs');
fs.mkdirSync(LOG_DIR, { recursive: true });

function logFile() { return path.join(LOG_DIR, 'bot-' + new Date().toISOString().slice(0, 10) + '.log'); }
function log(line) {
  const msg = `[${new Date().toLocaleTimeString('en-IN', { hour12: false })}] ${line}`;
  process.stdout.write(msg + '\n');
  try { fs.appendFileSync(logFile(), msg + '\n'); } catch {}
}

let child = null, restarting = false;

function startBot() {
  child = spawn(process.execPath, [path.join(__dirname, 'bot.js')], { cwd: __dirname, stdio: ['ignore', 'pipe', 'pipe', 'ipc'], env: { ...process.env, __SUPERVISED: '1' } });
  log(`bot started (pid ${child.pid})`);
  const pipe = (stream) => { let buf = ''; stream.on('data', d => { buf += d.toString(); let i; while ((i = buf.indexOf('\n')) >= 0) { log(buf.slice(0, i)); buf = buf.slice(i + 1); } }); };
  pipe(child.stdout); pipe(child.stderr);
  child.on('exit', (code) => {
    log(`bot stopped (code ${code})${restarting ? ', restarting with the update' : ', restarting in 5 s'}`);
    child = null;
    setTimeout(startBot, restarting ? 1000 : 5000);
    restarting = false;
  });
}

async function latestVersion() {
  const res = await fetch(`https://api.github.com/repos/${REPO}/commits/${encodeURIComponent(BRANCH)}`, { headers: { 'User-Agent': 'oic-bot-supervisor', 'Accept': 'application/vnd.github.sha' } });
  if (!res.ok) throw new Error('GitHub HTTP ' + res.status);
  return (await res.text()).trim();
}

async function checkForUpdate() {
  if (!AUTO_UPDATE) return;
  try {
    const latest = await latestVersion();
    const current = fs.existsSync(path.join(__dirname, '.version')) ? fs.readFileSync(path.join(__dirname, '.version'), 'utf8').trim() : '';
    if (latest === current) return;
    log(`update available (${latest.slice(0, 7)}), installing...`);
    const r = spawnSync(process.execPath, [path.join(__dirname, 'update.js')], { cwd: __dirname, stdio: 'pipe', env: { ...process.env, __SUPERVISED: '1' } });
    (r.stdout.toString() + r.stderr.toString()).split('\n').filter(Boolean).forEach(l => log('  ' + l));
    if (r.status !== 0) return log('update failed, keeping the current version');
    if (child) {
      restarting = true;
      log('asking the bot to finish pending photos and restart...');
      try { child.send('shutdown'); } catch { child.kill(); }
      setTimeout(() => { if (child) { log('bot did not stop in time, forcing'); child.kill(); } }, 120000);
    }
  } catch (e) { log('update check skipped: ' + e.message); }
}

startBot();
setTimeout(checkForUpdate, 60 * 1000);
setInterval(checkForUpdate, CHECK_MIN * 60 * 1000);
process.on('SIGINT', () => { log('stopping'); if (child) { try { child.send('shutdown'); } catch { child.kill(); } setTimeout(() => process.exit(0), 15000); child.on('exit', () => process.exit(0)); } else process.exit(0); });
