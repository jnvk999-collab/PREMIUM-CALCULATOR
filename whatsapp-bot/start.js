/**
 * Starts the bot in the background with no window.   node start.js
 * Logs go to logs/bot-<date>.log. Check with `node status.js`, stop with `node stop.js`.
 */
const { spawn, spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const PID = path.join(__dirname, '.pid');
const RUN = path.join(__dirname, 'run.js');

function alive(pid) { try { process.kill(pid, 0); return true; } catch { return false; } }
if (fs.existsSync(PID)) {
  const pid = parseInt(fs.readFileSync(PID, 'utf8'), 10);
  if (pid && alive(pid)) { console.log(`Bot is already running in the background (pid ${pid}). Use node status.js to see it.`); process.exit(0); }
}
try { fs.unlinkSync(path.join(__dirname, '.stop')); } catch {}

let pid;
if (process.platform === 'win32') {
  // Start-Process -WindowStyle Hidden is the only reliable way to get NO console window on Windows.
  const ps = `$p = Start-Process -FilePath '${process.execPath}' -ArgumentList '"${RUN}"' -WorkingDirectory '${__dirname}' -WindowStyle Hidden -PassThru; Write-Output $p.Id`;
  const r = spawnSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-Command', ps], { encoding: 'utf8', windowsHide: true, env: { ...process.env, __BACKGROUND: '1' } });
  pid = parseInt((r.stdout || '').trim(), 10);
  if (!pid) { console.error('Could not start hidden:', (r.stderr || '').trim()); process.exit(1); }
} else {
  const child = spawn(process.execPath, [RUN], { cwd: __dirname, detached: true, stdio: 'ignore', env: { ...process.env, __BACKGROUND: '1' } });
  child.unref();
  pid = child.pid;
}
console.log(`Bot started in the background (pid ${pid}). No window will appear.`);
console.log('  node status.js   -> is it running, last log lines');
console.log('  node stop.js     -> stop it');
console.log('If the phone is not linked yet, open qr.png in this folder to scan the QR (it appears within ~20 s).');
