/**
 * One-time (or repeatable) scan of your Gmail for policy PDFs from the last N days.
 *   node scan-policies.js            -> last 365 days
 *   node scan-policies.js 90         -> last 90 days
 * Reads each PDF (policy no, insured, vehicle, period, mobile) and writes
 *   merged/policies.jsonl  and  merged/register/Policies.xlsx  (day-wise, newest first)
 * Uses MAIL_WATCH_USER / EMAIL_FROM and the App Password from .env.
 */
try { require('dotenv').config(); } catch {}
const fs = require('fs');
const path = require('path');
const { ImapFlow } = require('imapflow');
const { simpleParser } = require('mailparser');
const org = require('./lib/organise');
const policy = require('./lib/policy');

const days = parseInt(process.argv[2] || '365', 10);
const user = process.env.MAIL_WATCH_USER || process.env.EMAIL_FROM;
const pass = (process.env.MAIL_WATCH_PASSWORD || process.env.EMAIL_APP_PASSWORD || '').replace(/\s+/g, '');
const fromFilter = (process.env.MAIL_WATCH_FROM || '').split(',').map(s => s.trim().toLowerCase()).filter(Boolean);
if (!user || !pass) { console.error('Set EMAIL_FROM and EMAIL_APP_PASSWORD (or MAIL_WATCH_USER / MAIL_WATCH_PASSWORD) in .env first.'); process.exit(1); }

const OUT = path.join(org.MERGED_ROOT, 'policies.jsonl');
const PDFDIR = path.join(org.MERGED_ROOT, 'policies');
fs.mkdirSync(PDFDIR, { recursive: true });
const have = new Set(); try { fs.readFileSync(OUT, 'utf8').split('\n').filter(Boolean).forEach(l => { try { have.add(JSON.parse(l).mailUid); } catch {} }); } catch {}

(async () => {
  const client = new ImapFlow({ host: 'imap.gmail.com', port: 993, secure: true, auth: { user, pass }, logger: false });
  await client.connect();
  const lock = await client.getMailboxLock('INBOX');
  let found = 0, scanned = 0;
  try {
    const since = new Date(Date.now() - days * 86400000);
    const uids = await client.search({ since }, { uid: true });
    console.log(`${uids.length} mails since ${since.toISOString().slice(0, 10)}; looking for PDF attachments...`);
    for (const uid of uids) {
      if (have.has(uid)) continue;
      const msg = await client.fetchOne(uid, { envelope: true, bodyStructure: true }, { uid: true });
      if (!msg) continue;
      const from = ((msg.envelope.from || [])[0] || {}).address || '';
      const fname = ((msg.envelope.from || [])[0] || {}).name || '';
      if (from.toLowerCase() === user.toLowerCase() || /^WhatsApp Bot/i.test(fname)) continue;
      if (fromFilter.length && !fromFilter.some(f => from.toLowerCase().includes(f))) continue;
      if (!JSON.stringify(msg.bodyStructure || {}).toLowerCase().includes('pdf')) continue;
      const { content } = await client.download(uid, undefined, { uid: true });
      const chunks = []; for await (const ch of content) chunks.push(ch);
      const parsed = await simpleParser(Buffer.concat(chunks));
      for (const a of (parsed.attachments || []).filter(a => /pdf/i.test(a.contentType) || /\.pdf$/i.test(a.filename || ''))) {
        scanned++;
        const name = (a.filename || `mail-${uid}.pdf`).replace(/[^\w\-. ]+/g, '_');
        const file = path.join(PDFDIR, `${(msg.envelope.date || new Date()).toISOString().slice(0, 10)}_${name}`);
        fs.writeFileSync(file, a.content);
        const info = await policy.fromPdf(file);
        if (!info.policyNo && !info.vehicle) { try { fs.unlinkSync(file); } catch {} continue; }   // not a policy
        const row = { mailUid: uid, mailDate: (msg.envelope.date || new Date()).toISOString().slice(0, 10), mailFrom: from, subject: msg.envelope.subject || '', file, ...info };
        fs.appendFileSync(OUT, JSON.stringify(row) + '\n');
        found++;
        process.stdout.write(`  ${row.mailDate}  ${info.vehicle || '-'}  ${info.policyNo || '-'}  ${info.insured || '-'}  to ${info.to || '?'}  ${info.mobile || ''}\n`);
      }
    }
  } finally { lock.release(); await client.logout(); }

  // re-read older rows that were recorded before proposal numbers were extracted
  let rows = fs.readFileSync(OUT, 'utf8').split('\n').filter(Boolean).map(l => JSON.parse(l));
  let fixed = 0;
  for (const r of rows) if (!('proposalNo' in r) && r.file && fs.existsSync(r.file)) { const info = await policy.fromPdf(r.file); Object.assign(r, info); r.proposalNo = info.proposalNo || ''; fixed++; }
  if (fixed) fs.writeFileSync(OUT, rows.map(r => JSON.stringify(r)).join('\n') + '\n');
  // Excel, newest first
  rows = rows.sort((a, b) => b.mailDate.localeCompare(a.mailDate));
  const ExcelJS = require('exceljs');
  const wb = new ExcelJS.Workbook(); const ws = wb.addWorksheet('Policies', { views: [{ state: 'frozen', ySplit: 1 }] });
  ws.columns = [
    { header: 'Mail date', key: 'mailDate', width: 12 }, { header: 'Vehicle', key: 'vehicle', width: 14 }, { header: 'Policy No', key: 'policyNo', width: 24 }, { header: 'Proposal No', key: 'proposalNo', width: 24 },
    { header: 'Insured', key: 'insured', width: 28 }, { header: 'Mobile', key: 'mobile', width: 14 }, { header: 'From', key: 'from', width: 12 }, { header: 'To (expiry)', key: 'to', width: 12 },
    { header: 'Make/Model', key: 'make', width: 24 }, { header: 'Premium', key: 'premium', width: 10 }, { header: 'Mail from', key: 'mailFrom', width: 30 }, { header: 'Subject', key: 'subject', width: 40 }, { header: 'PDF', key: 'file', width: 50 },
  ];
  ws.getRow(1).font = { bold: true }; ws.autoFilter = { from: 'A1', to: 'M1' };
  rows.forEach(r => ws.addRow({ ...r, file: path.basename(r.file) }));
  fs.mkdirSync(path.join(org.MERGED_ROOT, 'register'), { recursive: true });
  const xf = path.join(org.MERGED_ROOT, 'register', 'Policies.xlsx');
  await wb.xlsx.writeFile(xf);
  console.log(`\nScanned ${scanned} PDF(s), ${found} new polic${found === 1 ? 'y' : 'ies'} recorded. Total ${rows.length}.\nExcel: ${xf}\nPDFs:  ${PDFDIR}\nType *due* in your own WhatsApp chat to see upcoming expiries.`);
})().catch(e => { console.error('Scan failed: ' + e.message); process.exit(1); });
