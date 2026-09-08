/**
 * Makes the bot start automatically when you log in to Windows (Task Scheduler).
 *   node install-autostart.js          -> install
 *   node install-autostart.js remove   -> remove
 * The task runs "node run.js" in this folder; a console window shows the log.
 */
const { spawnSync } = require('child_process');
const path = require('path');
const TASK = 'OIC WhatsApp Bot';
if (process.platform !== 'win32') { console.log('This installer is for Windows. On Mac/Linux use pm2: npx pm2 start run.js --name oic-bot'); process.exit(0); }
const remove = process.argv[2] === 'remove';
const args = remove
  ? ['/Delete', '/TN', TASK, '/F']
  : ['/Create', '/F', '/TN', TASK, '/SC', 'ONLOGON', '/RL', 'LIMITED', '/TR', `"${process.execPath}" "${path.join(__dirname, 'run.js')}"`];
const r = spawnSync('schtasks', args, { stdio: 'inherit', shell: true });
if (r.status === 0) console.log(remove ? 'Autostart removed.' : `Done. "${TASK}" will start at every login. To start it now without logging out: node run.js`);
else console.log('schtasks failed. Right-click the terminal and choose "Run as administrator", then try again.');
