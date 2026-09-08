/** Stops the background bot gracefully (finishes pending photos first), then makes sure no copy is left.   node stop.js */
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');
const PID = path.join(__dirname, '.pid');
function alive(pid) { try { process.kill(pid, 0); return true; } catch { return false; } }

// Every node process running run.js or bot.js from THIS folder (catches stray copies).
function findAll() {
  if (process.platform !== 'win32') {
    const r = spawnSync('pgrep', ['-f', `node .*(run|bot)\\.js`], { encoding: 'utf8' });
    return (r.stdout || '').split('\n').map(s => parseInt(s, 10)).filter(n => n && n !== process.pid);
  }
  const ps = `Get-CimInstance Win32_Process | Where-Object { $_.Name -like 'node*' -and $_.CommandLine -like '*${path.basename(__dirname)}*' -and ($_.CommandLine -like '*run.js*' -or $_.CommandLine -like '*bot.js*') } | ForEach-Object { $_.ProcessId }`;
  const r = spawnSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', ps], { encoding: 'utf8', windowsHide: true });
  return (r.stdout || '').split('\n').map(s => parseInt(s, 10)).filter(n => n && n !== process.pid);
}

(async () => {
  const pid = fs.existsSync(PID) ? parseInt(fs.readFileSync(PID, 'utf8'), 10) : 0;
  if (pid && alive(pid)) {
    fs.writeFileSync(path.join(__dirname, '.stop'), String(Date.now()));
    process.stdout.write('Stopping');
    const t0 = Date.now();
    while (alive(pid) && Date.now() - t0 < 150000) { await new Promise(r => setTimeout(r, 1000)); process.stdout.write('.'); }
    console.log(alive(pid) ? ' did not stop in time, forcing.' : ' stopped.');
  } else console.log('Main bot process not running.');
  try { fs.unlinkSync(PID); } catch {}
  const stray = findAll();
  for (const p of stray) { try { process.kill(p); console.log(`Killed leftover copy (pid ${p}).`); } catch {} }
  if (!stray.length) console.log('No leftover copies.');
})();
