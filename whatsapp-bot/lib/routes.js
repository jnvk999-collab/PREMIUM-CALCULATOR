/**
 * Per-group / per-sender email routing from email-routes.txt (see the example file).
 * Re-read on every use so edits apply without a restart.
 */
const fs = require('fs');
const path = require('path');
const FILE = path.resolve(__dirname, '..', 'email-routes.txt');

function load() {
  const rules = [];
  if (!fs.existsSync(FILE)) return rules;
  for (const raw of fs.readFileSync(FILE, 'utf8').split('\n')) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const eq = line.indexOf('=');
    if (eq < 0) continue;
    const key = line.slice(0, eq).trim().toLowerCase();
    let rhs = line.slice(eq + 1).trim();
    const only = /\(only\)\s*$/i.test(rhs);
    rhs = rhs.replace(/\(only\)\s*$/i, '');
    const emails = rhs.split(/[,;\s]+/).map(s => s.trim()).filter(s => /.+@.+\..+/.test(s));
    if (key && emails.length) rules.push({ key, emails, only });
  }
  return rules;
}

/**
 * @param {{group?:string, sender?:string}} ctx  group name and sender number (digits)
 * @param {string[]} defaults  EMAIL_TO list
 * @returns {string[]} recipients
 */
function recipientsFor(ctx, defaults) {
  const rules = load();
  const g = (ctx.group || '').toLowerCase(), n = (ctx.sender || '').replace(/\D/g, '');
  const hit = rules.filter(r => (g && r.key === g) || (n && r.key.replace(/\D/g, '') === n && /^\d+$/.test(r.key.replace(/\D/g, ''))));
  if (!hit.length) return defaults;
  const only = hit.some(r => r.only);
  const set = new Set(only ? [] : defaults);
  hit.forEach(r => r.emails.forEach(e => set.add(e)));
  return [...set];
}

module.exports = { recipientsFor, load, FILE };
