/**
 * Commands you type in your OWN WhatsApp chat ("message yourself"):
 *   help                      this list
 *   today / yesterday         sets received on that day
 *   find 8670                 sets whose vehicle number ends with / contains 8670 (or a name / number / date)
 *   send 8670                 send that vehicle's PDF here (latest match)
 *   resend 8670 to a@b.com    email that PDF to an address (several: comma-separated)
 *   count                     this month's totals by group
 */
const fs = require('fs');
const path = require('path');
const register = require('./register');

const HELP = [
  '*Bot commands (this chat only)*',
  '• *today* / *yesterday* – sets received',
  '• *find 8670* – search by vehicle no. (or last digits), sender, number, or date 2026-09-08',
  '• *send 8670* – send that PDF here',
  '• *resend 8670 to name@gmail.com* – email that PDF',
  '• *count* – this month by group',
  '• *due* / *due 7* – policies expiring soon (from renewals.xlsx + scanned policies)',
  '• forward a policy PDF here (or drop it in Downloads) – I offer to send it to whoever sent that vehicle',
  '• *help* – this list',
].join('\n');

const fmt = r => `${r.date} ${r.time}  ${r.vehicle || '(no number)'}  ${r.sender}${r.group ? ' · ' + r.group : ''}  ${r.pages}p`;
const list = (rows, title) => rows.length
  ? `*${title}* (${rows.length})\n` + rows.slice(0, 25).map(fmt).join('\n') + (rows.length > 25 ? `\n… and ${rows.length - 25} more` : '')
  : `*${title}*: nothing found`;

const today = (d = new Date()) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);

/**
 * @returns {Promise<{text?:string, files?:string[]}|null>} null = not a command
 */
async function handle(text, { mailer } = {}) {
  const t = String(text || '').trim();
  const m = t.match(/^(help|today|yesterday|count|find|send|resend)\b\s*(.*)$/i);
  if (!m) return null;
  const cmd = m[1].toLowerCase(), arg = m[2].trim();

  if (cmd === 'help') return { text: HELP };
  if (cmd === 'today') return { text: list(register.onDate(today()), 'Today') };
  if (cmd === 'yesterday') return { text: list(register.onDate(today(new Date(Date.now() - 86400000))), 'Yesterday') };
  if (cmd === 'count') {
    const ym = today().slice(0, 7);
    const rows = register.readAll().filter(r => r.date.startsWith(ym));
    const by = {};
    rows.forEach(r => { const k = r.group || 'Direct'; by[k] = (by[k] || 0) + 1; });
    const lines = Object.entries(by).sort((a, b) => b[1] - a[1]).map(([k, v]) => `${v}  ${k}`);
    return { text: `*${ym}: ${rows.length} sets*\n` + (lines.join('\n') || 'none yet') };
  }
  if (!arg) return { text: `Usage: ${cmd} <vehicle number / last digits / name / date>` };

  if (cmd === 'find') return { text: list(register.search(arg), `Find "${arg}"`) };

  if (cmd === 'send') {
    const rows = register.search(arg).filter(r => r.file && fs.existsSync(r.file));
    if (!rows.length) return { text: `No PDF found for "${arg}"` };
    return { text: `Sending ${Math.min(rows.length, 3)} PDF(s) for "${arg}"`, files: rows.slice(0, 3).map(r => r.file) };
  }

  if (cmd === 'resend') {
    const mm = arg.match(/^(.+?)\s+to\s+(.+)$/i);
    if (!mm) return { text: 'Usage: resend <vehicle> to name@gmail.com' };
    const rows = register.search(mm[1]).filter(r => r.file && fs.existsSync(r.file));
    if (!rows.length) return { text: `No PDF found for "${mm[1]}"` };
    const to = mm[2].split(/[,\s]+/).filter(s => /.+@.+\..+/.test(s));
    if (!to.length) return { text: 'That does not look like an email address.' };
    if (!mailer || !mailer.enabled()) return { text: 'Email is not set up in .env (EMAIL_FROM / EMAIL_APP_PASSWORD).' };
    const r = rows[0];
    await mailer.sendPdf({ to, file: r.file, filename: path.basename(r.file), subject: `${r.vehicle || r.sender} - ${r.date} (resend)`, text: `Resent on request.\n${fmt(r)}` });
    return { text: `Emailed ${path.basename(r.file)} to ${to.join(', ')}` };
  }
  return null;
}

module.exports = { handle, HELP };
