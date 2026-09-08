/**
 * Makes the bot start automatically when you log in to Windows (Task Scheduler).
 *   node install-autostart.js          -> install
 *   node install-autostart.js remove   -> remove
 * The task runs "node start.js" in this folder, which launches the bot in the background (no window).
 */
const { spawnSync } = require('child_process');
const path = require('path');
const TASK = 'OIC_WhatsApp_Bot';
if (process.platform !== 'win32') { console.log('This installer is for Windows. On Mac/Linux use pm2: npx pm2 start run.js --name oic-bot'); process.exit(0); }
const remove = process.argv[2] === 'remove';
// The task runs node start.js, which launches the bot hidden. A console may flash for a second at login.
const args = remove
  ? ['/Delete', '/TN', TASK, '/F']
  : ['/Create', '/F', '/TN', TASK, '/SC', 'ONLOGON', '/RL', 'LIMITED', '/TR', `"${process.execPath}" "${path.join(__dirname, 'start.js')}"`];
const r = spawnSync('schtasks.exe', args, { stdio: 'inherit' });
if (r.status === 0) console.log(remove ? 'Autostart removed.' : `Done. "${TASK}" will start at every login. To start it now: node start.js`);
else console.log('schtasks failed. Right-click the terminal and choose "Run as administrator", then try again.');
