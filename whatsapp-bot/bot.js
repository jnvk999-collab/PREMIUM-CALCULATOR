/**
 * WhatsApp photo-merger for the OIC Premium Calculator.
 *
 *   photos in  -> saved under inbox/<contact>/<date>/, merged into one A4 PDF,
 *                 sent to YOUR OWN chat (PDF_TO=me), the sender, or both
 *   "quote ..." -> optional (AUTO_QUOTE=1): priced by the real calculator
 *
 * Transport: Baileys (direct WhatsApp connection, no browser). Link once by
 * scanning the QR; the login is kept in ./baileys_auth.
 */
try { require('dotenv').config(); } catch {}
const path = require('path');
const fs = require('fs');
const qrcodeTerminal = require('qrcode-terminal');
const QRCode = require('qrcode');
const pino = require('pino');
const {
  makeWASocket, useMultiFileAuthState, fetchLatestBaileysVersion,
  DisconnectReason, downloadMediaMessage, jidNormalizedUser,
} = require('@whiskeysockets/baileys');

const { mergeFilesToPdf, PhotoBatcher } = require('./lib/pdfMerge');
const org = require('./lib/organise');

const CFG = {
  agentName: process.env.AGENT_NAME || '',
  mergeWaitSeconds: parseInt(process.env.MERGE_WAIT_SECONDS || '45', 10),
  replyInGroups: process.env.REPLY_IN_GROUPS === '1',
  allowGroups: (process.env.ALLOW_GROUPS || '').split(',').map(s => s.trim().toLowerCase()).filter(Boolean),
  allowList: (process.env.ALLOW_NUMBERS || '').split(',').map(s => s.trim()).filter(Boolean),
  autoReplyQuotes: process.env.AUTO_QUOTE === '1',
  autoMergePdf: process.env.AUTO_PDF !== '0',
  pdfTo: (process.env.PDF_TO || 'me').toLowerCase(),
};

const AUTH_DIR = path.resolve(__dirname, 'baileys_auth');
const QR_PNG = path.resolve(__dirname, 'qr.png');
const logger = pino({ level: process.env.LOG_LEVEL || 'silent' });

let sock = null;
let myJid = null;
const groupNames = new Map();          // jid -> group subject

// ── helpers ────────────────────────────────────────────────────────────────
const number = jid => (jid || '').split('@')[0].split(':')[0];

async function groupName(jid) {
  if (groupNames.has(jid)) return groupNames.get(jid);
  try { const md = await sock.groupMetadata(jid); groupNames.set(jid, md.subject || ''); return md.subject || ''; }
  catch { return ''; }
}

async function contactLabel(m) {
  const jid = m.key.remoteJid;
  const isGroup = jid.endsWith('@g.us');
  const who = isGroup ? m.key.participant : jid;
  const num = number(who);
  let label = m.pushName ? `${m.pushName} (${num})` : num;
  if (isGroup) label = ((await groupName(jid)) || 'group') + ' - ' + label;
  return label;
}

async function allowed(m) {
  if (m.key.fromMe) return false;
  const jid = m.key.remoteJid || '';
  if (jid === 'status@broadcast' || jid.endsWith('@newsletter')) return false;
  const isGroup = jid.endsWith('@g.us');
  if (isGroup) {
    if (!CFG.replyInGroups) return false;
    if (CFG.allowGroups.length && !CFG.allowGroups.includes((await groupName(jid)).toLowerCase())) return false;
  }
  const sender = number(isGroup ? m.key.participant : jid);
  if (CFG.allowList.length && !CFG.allowList.includes(sender)) return false;
  return true;
}

function textOf(m) {
  const msg = m.message || {};
  return (msg.conversation || msg.extendedTextMessage?.text || msg.imageMessage?.caption
    || msg.documentMessage?.caption || '').trim();
}

function mediaOf(m) {
  const msg = m.message || {};
  const img = msg.imageMessage; if (img) return { kind: 'image', mimetype: img.mimetype || 'image/jpeg' };
  const doc = msg.documentMessage || msg.documentWithCaptionMessage?.message?.documentMessage;
  if (doc) return { kind: 'document', mimetype: doc.mimetype || 'application/octet-stream', name: doc.fileName };
  return null;
}

async function sendText(jid, text) { await sock.sendMessage(jid, { text }); }
async function sendPdf(jid, file, caption) {
  await sock.sendMessage(jid, { document: fs.readFileSync(file), mimetype: 'application/pdf', fileName: path.basename(file), caption });
}
async function notifyOwner(text) { try { if (myJid) await sendText(myJid, '🤖 ' + text); } catch (e) { console.error('[notify] ' + e.message); } }

// ── photo batching → merged PDF ────────────────────────────────────────────
const batcher = new PhotoBatcher({
  waitSeconds: CFG.mergeWaitSeconds,
  onFlush: async (chatId, files) => {
    const first = files[0];
    const label = first.label;
    try {
      const stamp = new Date();
      const date = stamp.toLocaleDateString('en-CA');                       // YYYY-MM-DD
      const hhmm = stamp.toTimeString().slice(0, 5).replace(':', '');
      const who = org.safeName(first.name || number(first.sender)).replace(/\s+/g, '_');
      const { bytes, pages, items, skipped } = await mergeFilesToPdf(
        files.map(f => ({ path: f.path, caption: f.caption })),
        {
          label: first.name || number(first.sender),
          title: `${first.name || number(first.sender)} - ${date}`,
          cover: {
            title: 'Documents received on WhatsApp',
            from: first.name || '', number: number(first.sender), group: first.group || '',
            received: first.receivedAt.toLocaleString('en-IN'), agent: CFG.agentName,
          },
        });
      const out = org.saveOutput(label, `${who}_${date}_${hhmm}.pdf`, Buffer.from(bytes), { pages, source: files.length });
      const photos = items.filter(i => i.kind === 'Photo').length, pdfs = items.length - photos;
      let caption = `${label}: ${photos} photo${photos === 1 ? '' : 's'}${pdfs ? ` + ${pdfs} PDF${pdfs === 1 ? '' : 's'}` : ''} merged (${pages} page${pages === 1 ? '' : 's'}).`;
      if (skipped.length) caption += ` Skipped ${skipped.length} unsupported file(s).`;
      if (CFG.pdfTo === 'me' || CFG.pdfTo === 'both') await sendPdf(myJid, out, caption);
      if (CFG.pdfTo === 'sender' || CFG.pdfTo === 'both') await sendPdf(chatId, out, `Merged into one PDF (${pages} page${pages === 1 ? '' : 's'}).`);
      console.log(`[pdf] ${label}: ${pages} pages -> ${out}`);
    } catch (e) {
      console.error('[pdf] merge failed: ' + e.message);
      await notifyOwner(`Could not merge files from ${label}. They are saved in the inbox folder.`);
    }
  },
});

// ── optional quotes ────────────────────────────────────────────────────────
async function handleQuote(m, text, label) {
  const calc = require('./lib/calculator');
  const { parseQuote } = require('./lib/intents');
  const { quoteText } = require('./lib/reply');
  const jid = m.key.remoteJid;
  const { input, missing } = parseQuote(text);
  if (missing.length) {
    org.log({ type: 'quote-incomplete', contact: label, text, missing });
    return notifyOwner(`Quote request from ${label} needs your reply:\n"${text}"\n(missing: ${missing.join(', ')})`);
  }
  const res = await calc.motorQuote(input);
  if (!res.ok) {
    org.log({ type: 'quote-error', contact: label, text, errors: res.errors });
    return notifyOwner(`Could not auto-quote for ${label}: "${text}"\n${res.errors.join('; ')}`);
  }
  const pdfPath = org.saveOutput(label, res.filename, Buffer.from(res.pdfBase64, 'base64'), { total: res.summary.total, text });
  await sendText(jid, quoteText(res.summary, CFG.agentName));
  await sendPdf(jid, pdfPath, '');
  org.log({ type: 'quote', contact: label, text, input, total: res.summary.total, file: path.basename(pdfPath) });
  console.log(`[quote] ${label}: "${text}" -> ₹${res.summary.total}`);
}

// ── message handling ───────────────────────────────────────────────────────
async function onMessage(m) {
  if (!m.message) return;
  if (!(await allowed(m))) return;
  const jid = m.key.remoteJid;
  const label = await contactLabel(m);
  const text = textOf(m);
  try {
    const media = mediaOf(m);
    if (media) {
      let buf = null;
      try { buf = await downloadMediaMessage(m, 'buffer', {}, { logger, reuploadRequest: sock.updateMediaMessage }); }
      catch (e) { console.error(`[media] could not download from ${label}: ${e.message}`); }
      if (!buf) { org.log({ type: 'media-failed', contact: label, caption: text }); return; }
      const file = org.saveMedia(label, { data: buf.toString('base64'), mimetype: media.mimetype }, { caption: text, from: jid });
      const mergeable = /^image\/(jpeg|png)$/i.test(media.mimetype) || media.mimetype === 'application/pdf';
      if (CFG.autoMergePdf && mergeable) {
        const isGroup = jid.endsWith('@g.us');
        const n = batcher.add(jid, {
          path: file, label, caption: text, receivedAt: new Date(),
          name: m.pushName || '', sender: isGroup ? m.key.participant : jid,
          group: isGroup ? await groupName(jid) : '',
        });
        if (n === 1) console.log(`[files] ${label}: collecting, PDF in ${CFG.mergeWaitSeconds}s`);
        if (n === 1 && CFG.pdfTo !== 'me') await sendText(jid, `Got it. I will merge the photos into one PDF in ${CFG.mergeWaitSeconds}s (send *pdf* to do it now).`);
      }
      if (CFG.autoReplyQuotes && text && require('./lib/intents').isQuoteRequest(text)) await handleQuote(m, text, label);
      return;
    }
    if (/^(pdf|merge|done)$/i.test(text)) {
      if (batcher.pending(jid)) return batcher.flush(jid);
      if (CFG.pdfTo !== 'me') return sendText(jid, 'No photos waiting. Send the photos first, then *pdf*.');
      return;
    }
    if (CFG.autoReplyQuotes && /^(help|menu)$/i.test(text)) return sendText(jid, require('./lib/reply').HELP);
    if (CFG.autoReplyQuotes && text && require('./lib/intents').isQuoteRequest(text)) return handleQuote(m, text, label);
    org.log({ type: 'text', contact: label, text });
  } catch (e) {
    console.error(`[handler] ${label}: ${e.message}`);
  }
}

// ── connection ─────────────────────────────────────────────────────────────
async function start() {
  const { state, saveCreds } = await useMultiFileAuthState(AUTH_DIR);
  const { version } = await fetchLatestBaileysVersion().catch(() => ({ version: undefined }));
  sock = makeWASocket({ version, auth: state, logger, printQRInTerminal: false, syncFullHistory: false, markOnlineOnConnect: false });
  sock.ev.on('creds.update', saveCreds);

  sock.ev.on('connection.update', async (u) => {
    const { connection, lastDisconnect, qr } = u;
    if (qr) {
      QRCode.toFile(QR_PNG, qr, { width: 400 }).catch(() => {});
      console.log('\n==============================================');
      console.log('Open WhatsApp on your phone > Linked devices > Link a device');
      console.log('and scan the QR below, or open this image: ' + QR_PNG);
      console.log('==============================================\n');
      qrcodeTerminal.generate(qr, { small: true });
    }
    if (connection === 'open') {
      try { fs.unlinkSync(QR_PNG); } catch {}
      myJid = jidNormalizedUser(sock.user.id);
      console.log(`Ready as ${number(myJid)}. Inbox: ${org.ROOT}  merge wait: ${CFG.mergeWaitSeconds}s  PDF to: ${CFG.pdfTo}  groups: ${CFG.replyInGroups ? (CFG.allowGroups.join(', ') || 'all') : 'off'}`);
      if (CFG.replyInGroups) {
        try {
          const groups = Object.values(await sock.groupFetchAllParticipating());
          groups.forEach(g => groupNames.set(g.id, g.subject));
          console.log('[groups] your groups (copy an exact name into ALLOW_GROUPS in .env to limit):');
          groups.forEach(g => console.log('   ' + g.subject));
        } catch (e) { console.log('[groups] could not list groups: ' + e.message); }
      }
    }
    if (connection === 'close') {
      const code = lastDisconnect?.error?.output?.statusCode;
      if (code === DisconnectReason.loggedOut) {
        console.error('Logged out from the phone. Delete the baileys_auth folder and run node bot.js again to re-link.');
        process.exit(1);
      }
      console.log(`[whatsapp] connection closed (${code || 'unknown'}), reconnecting...`);
      setTimeout(start, 3000);
    }
  });

  sock.ev.on('messages.upsert', async ({ messages, type }) => {
    if (type !== 'notify') return;
    for (const m of messages) { try { await onMessage(m); } catch (e) { console.error('[upsert] ' + e.message); } }
  });
}

process.on('SIGINT', async () => { try { await require('./lib/calculator').close(); } catch {} process.exit(0); });
start().catch(e => { console.error('Fatal: ' + e.message); process.exit(1); });
