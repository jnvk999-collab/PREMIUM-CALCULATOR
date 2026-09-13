/**
 * Watches a Gmail inbox for new emails carrying PDF attachments (policy copies)
 * and hands each PDF to the dispatcher.
 *
 * .env:
 *   MAIL_WATCH=1
 *   MAIL_WATCH_USER=you@gmail.com            (default: EMAIL_FROM)
 *   MAIL_WATCH_PASSWORD=app password         (default: EMAIL_APP_PASSWORD)
 *   MAIL_WATCH_FROM=portal@orientalinsurance.co.in, other@x.com   (optional: only these senders)
 *   MAIL_WATCH_SUBJECT=policy                (optional: subject must contain this word)
 *   MAIL_WATCH_MINUTES=1                     (poll interval)
 * Uses IMAP over TLS (imap.gmail.com:993). Only mails received after the bot
 * started are considered; handled mail ids are remembered across restarts.
 */
const fs = require('fs');
const path = require('path');
const org = require('./organise');

function config() {
  if (process.env.MAIL_WATCH !== '1') return null;
  const user = process.env.MAIL_WATCH_USER || process.env.EMAIL_FROM || '';
  const pass = (process.env.MAIL_WATCH_PASSWORD || process.env.EMAIL_APP_PASSWORD || '').replace(/\s+/g, '');
  if (!user || !pass) return null;
  return {
    user, pass,
    host: process.env.MAIL_WATCH_HOST || 'imap.gmail.com',
    port: parseInt(process.env.MAIL_WATCH_PORT || '993', 10),
    from: (process.env.MAIL_WATCH_FROM || '').split(',').map(s => s.trim().toLowerCase()).filter(Boolean),
    subject: (process.env.MAIL_WATCH_SUBJECT || '').trim().toLowerCase(),
    minutes: Math.max(1, parseInt(process.env.MAIL_WATCH_MINUTES || '1', 10)),
  };
}

const STATE = () => path.join(org.MERGED_ROOT, 'mail-processed.json');
function loadState() { try { return JSON.parse(fs.readFileSync(STATE(), 'utf8')); } catch { return { uids: [] }; } }
function saveState(st) { try { fs.mkdirSync(org.MERGED_ROOT, { recursive: true }); fs.writeFileSync(STATE(), JSON.stringify(st)); } catch {} }

/**
 * @param {(file:string, meta:{from:string, subject:string}) => Promise<void>} onPdf
 */
function start(onPdf, log = console.log) {
  const c = config();
  if (!c) return false;
  const { ImapFlow } = require('imapflow');
  const { simpleParser } = require('mailparser');
  const outDir = path.join(path.resolve(__dirname, '..'), 'outbox', 'mail');
  fs.mkdirSync(outDir, { recursive: true });
  const since = new Date(Date.now() - 24 * 3600 * 1000);          // look back one day at most
  const state = loadState();
  const done = new Set(state.uids);
  let busy = false;

  async function poll() {
    if (busy) return; busy = true;
    const client = new ImapFlow({ host: c.host, port: c.port, secure: true, auth: { user: c.user, pass: c.pass }, logger: false });
    try {
      await client.connect();
      const lock = await client.getMailboxLock('INBOX');
      try {
        const uids = await client.search({ since }, { uid: true });
        for (const uid of uids || []) {
          if (done.has(uid)) continue;
          const msg = await client.fetchOne(uid, { envelope: true, bodyStructure: true }, { uid: true });
          if (!msg) continue;
          const from = ((msg.envelope.from || [])[0] || {}).address || '';
          const subject = msg.envelope.subject || '';
          const hasPdf = JSON.stringify(msg.bodyStructure || {}).toLowerCase().includes('pdf');
          // never react to the bot's own mails (merged PDFs it emailed) or to mails from the sending account
          const own = [c.user, process.env.EMAIL_FROM || ''].map(x => x.toLowerCase()).filter(Boolean);
          if (own.includes(from.toLowerCase()) || /^WhatsApp Bot/i.test(((msg.envelope.from || [])[0] || {}).name || '')) { done.add(uid); continue; }
          const okFrom = !c.from.length || c.from.some(f => from.toLowerCase().includes(f));
          const okSubj = !c.subject || subject.toLowerCase().includes(c.subject);
          if (!hasPdf || !okFrom || !okSubj) { done.add(uid); continue; }
          const { content } = await client.download(uid, undefined, { uid: true });
          const chunks = []; for await (const ch of content) chunks.push(ch);
          const parsed = await simpleParser(Buffer.concat(chunks));
          const pdfs = (parsed.attachments || []).filter(a => /pdf/i.test(a.contentType) || /\.pdf$/i.test(a.filename || ''));
          for (const a of pdfs) {
            const name = (a.filename || `mail-${uid}.pdf`).replace(/[^\w\-. ]+/g, '_');
            const file = path.join(outDir, name.replace(/\.pdf$/i, '') + `_${uid}.pdf`);
            fs.writeFileSync(file, a.content);
            log(`[mail-watch] PDF "${name}" from ${from} (${subject})`);
            try { await onPdf(file, { from, subject }); } catch (e) { log('[mail-watch] dispatch failed: ' + e.message); }
          }
          done.add(uid);
        }
      } finally { lock.release(); }
      await client.logout();
      state.uids = [...done].slice(-2000); saveState(state);
    } catch (e) {
      log('[mail-watch] ' + e.message);
      try { await client.logout(); } catch {}
    } finally { busy = false; }
  }

  log(`[mail-watch] watching ${c.user} inbox for PDF attachments${c.from.length ? ' from ' + c.from.join(', ') : ''}${c.subject ? ' with subject containing "' + c.subject + '"' : ''} every ${c.minutes} min`);
  poll();
  setInterval(poll, c.minutes * 60 * 1000);
  return true;
}

module.exports = { start, config };
