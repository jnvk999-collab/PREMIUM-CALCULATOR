/**
 * Per-group / per-sender email routing from email-routes.txt (see the example file).
 * Re-read on every use so edits apply without a restart.
 */
const fs = require('fs');
const path = require('path');
const FILE = path.resolve(__dirname, '..', 'email-routes.txt');

// Ignore case, leading/trailing/double spaces, hidden BOM, and fancy dashes.
const norm = s => String(s || '').replace(/^\uFEFF/, '').replace(/[\u2010-\u2015\u2212]/g, '-').replace(/\s+/g, ' ').trim().toLowerCase();

function load() {
  const rules = [];
  if (!fs.existsSync(FILE)) return rules;
  for (const raw of fs.readFileSync(FILE, 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const eq = line.indexOf('=');
    if (eq < 0) continue;
    const key = norm(line.slice(0, eq));
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
  const g = norm(ctx.group), n = (ctx.sender || '').replace(/\D/g, '');
  const hit = rules.filter(r => (g && r.key === g) || (n && /^\d+$/.test(r.key) && r.key === n));
  console.log(`[mail] routes: ${rules.length} rule(s) in email-routes.txt; group "${ctx.group || '-'}" ${hit.length ? 'matched ' + hit.map(h => h.key).join(', ') : 'matched none'}`);
  if (!hit.length) return defaults;
  const only = hit.some(r => r.only);
  const set = new Set(only ? [] : defaults);
  hit.forEach(r => r.emails.forEach(e => set.add(e)));
  return [...set];
}

module.exports = { recipientsFor, load, FILE };
