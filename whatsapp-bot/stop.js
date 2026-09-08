/** Stops the background bot gracefully (finishes pending photos first).   node stop.js */
const fs = require('fs');
const path = require('path');
const PID = path.join(__dirname, '.pid');
function alive(pid) { try { process.kill(pid, 0); return true; } catch { return false; } }
const pid = fs.existsSync(PID) ? parseInt(fs.readFileSync(PID, 'utf8'), 10) : 0;
if (!pid || !alive(pid)) { console.log('Bot is not running.'); try { fs.unlinkSync(PID); } catch {} process.exit(0); }
fs.writeFileSync(path.join(__dirname, '.stop'), String(Date.now()));
process.stdout.write('Stopping');
const t0 = Date.now();
const iv = setInterval(() => {
  process.stdout.write('.');
  if (!alive(pid)) { clearInterval(iv); console.log(' stopped.'); try { fs.unlinkSync(PID); } catch {} process.exit(0); }
  if (Date.now() - t0 > 150000) { clearInterval(iv); console.log(' did not stop in time, forcing.'); try { process.kill(pid); } catch {} try { fs.unlinkSync(PID); } catch {} process.exit(0); }
}, 1000);
