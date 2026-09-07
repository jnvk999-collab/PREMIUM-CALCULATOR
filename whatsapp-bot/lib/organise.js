/**
 * Puts every incoming file where you can find it again:
 *   inbox/<Contact name or number>/<YYYY-MM-DD>/<HHMMSS>_<n>.<ext>
 * and appends one JSON line per item to inbox/log.jsonl so you can grep
 * "who sent what, when" without opening WhatsApp.
 */
const fs = require('fs');
const path = require('path');

const ROOT = process.env.INBOX_DIR || path.resolve(__dirname, '..', 'inbox');

function safeName(s) {
  return String(s || 'unknown').replace(/[^\w\-+. ]+/g, '_').trim().slice(0, 60) || 'unknown';
}

function todayISO(d = new Date()) {
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

function contactDir(contactLabel, date = new Date()) {
  const dir = path.join(ROOT, safeName(contactLabel), todayISO(date));
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

let seq = 0;
function saveMedia(contactLabel, media, extra = {}) {
  const dir = contactDir(contactLabel);
  const ext = extra.ext || ({ 'image/jpeg': 'jpg', 'image/png': 'png', 'application/pdf': 'pdf' }[media.mimetype] || 'bin');
  const stamp = new Date().toTimeString().slice(0, 8).replace(/:/g, '');
  const file = path.join(dir, `${stamp}_${++seq}.${ext}`);
  fs.writeFileSync(file, Buffer.from(media.data, 'base64'));
  log({ type: 'media', contact: contactLabel, file: path.relative(ROOT, file), mimetype: media.mimetype, ...extra });
  return file;
}

function saveOutput(contactLabel, filename, bytes, extra = {}) {
  const dir = contactDir(contactLabel);
  const file = path.join(dir, filename);
  fs.writeFileSync(file, bytes);
  log({ type: 'output', contact: contactLabel, file: path.relative(ROOT, file), ...extra });
  return file;
}

function log(entry) {
  fs.mkdirSync(ROOT, { recursive: true });
  fs.appendFileSync(path.join(ROOT, 'log.jsonl'), JSON.stringify({ ts: new Date().toISOString(), ...entry }) + '\n');
}

module.exports = { ROOT, saveMedia, saveOutput, log, safeName, contactDir };
