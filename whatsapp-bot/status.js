/** Shows whether the background bot is running and the last log lines.   node status.js */
const fs = require('fs');
const path = require('path');
const PID = path.join(__dirname, '.pid');
function alive(pid) { try { process.kill(pid, 0); return true; } catch { return false; } }
const pid = fs.existsSync(PID) ? parseInt(fs.readFileSync(PID, 'utf8'), 10) : 0;
console.log(pid && alive(pid) ? `RUNNING (pid ${pid})` : 'NOT RUNNING  ->  start with: node start.js');
const ver = fs.existsSync(path.join(__dirname, '.version')) ? fs.readFileSync(path.join(__dirname, '.version'), 'utf8').trim().slice(0, 7) : 'unknown';
console.log('version ' + ver);
const dir = path.join(__dirname, 'logs');
if (fs.existsSync(dir)) {
  const files = fs.readdirSync(dir).filter(f => f.endsWith('.log')).sort();
  if (files.length) {
    const lines = fs.readFileSync(path.join(dir, files[files.length - 1]), 'utf8').trim().split('\n');
    console.log(`\nlast ${Math.min(25, lines.length)} lines of ${files[files.length - 1]}:`);
    console.log(lines.slice(-25).join('\n'));
  }
}
if (fs.existsSync(path.join(__dirname, 'qr.png'))) console.log('\nPhone not linked: open qr.png in this folder and scan it from WhatsApp > Linked devices.');
