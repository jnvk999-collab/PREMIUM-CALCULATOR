/**
 * Per-group / per-sender email routing from email-routes.txt (see the example file).
 * Re-read on every use so edits apply without a restart.
 */
const fs = require('fs');
const path = require('path');
const DIR = path.resolve(__dirname, '..');
// Accept the usual naming slips: email-routes.txt, email-routes.txt.txt, email-routes, Email-Routes.TXT ...
function findFile() {
  try {
    const names = fs.readdirSync(DIR).filter(f => /^email-routes(\.txt)*$/i.test(f) && !/example/i.test(f));
    if (names.length) return path.join(DIR, names.sort((a, b) => a.length - b.length)[0]);
  } catch {}
  return null;
}
const FILE = path.join(DIR, 'email-routes.txt');

// Ignore case, leading/trailing/double spaces, hidden BOM, and fancy dashes.
const norm = s => String(s || '').replace(/^\uFEFF/, '').replace(/[\u2010-\u2015\u2212]/g, '-').replace(/\s+/g, ' ').trim().toLowerCase();

function load() {
  const rules = [];
  const file = findFile();
  rules.file = file;
  if (!file) return rules;
  for (const raw of fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, '').split(/\r?\n/)) {
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
  console.log(`[mail] routes: ${rules.file ? rules.length + ' rule(s) in ' + path.basename(rules.file) : 'no email-routes.txt file found in ' + DIR}; group "${ctx.group || '-'}" ${hit.length ? 'matched ' + hit.map(h => h.key).join(', ') : 'matched none'}`);
  if (!hit.length) return defaults;
  const only = hit.some(r => r.only);
  const set = new Set(only ? [] : defaults);
  hit.forEach(r => r.emails.forEach(e => set.add(e)));
  return [...set];
}

module.exports = { recipientsFor, load, FILE };
