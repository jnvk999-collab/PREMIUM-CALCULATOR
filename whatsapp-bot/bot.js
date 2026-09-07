/**
 * WhatsApp automation for the OIC Premium Calculator.
 *
 *   photos in  -> saved to inbox/<contact>/<date>/, merged into one PDF, sent back
 *   "quote ..." -> parsed, priced by the real calculator (headless), reply + PDF
 *   "pdf"       -> merge whatever photos are waiting right now
 *   "help"      -> usage text
 *
 * Uses whatsapp-web.js: it links to YOUR WhatsApp number like WhatsApp Web
 * (scan the QR once). See README.md for the trade-offs vs the official
 * WhatsApp Business Cloud API.
 */
try { require('dotenv').config(); } catch {}
const path = require('path');
const fs = require('fs');
const qrcode = require('qrcode-terminal');
const QRCode = require('qrcode');
const { Client, LocalAuth, MessageMedia } = require('whatsapp-web.js');

const calc = require('./lib/calculator');
const { parseQuote, isQuoteRequest } = require('./lib/intents');
const { mergeImagesToPdf, PhotoBatcher } = require('./lib/pdfMerge');
const org = require('./lib/organise');
const { quoteText, missingText, HELP } = require('./lib/reply');

const CFG = {
  agentName: process.env.AGENT_NAME || '',
  mergeWaitSeconds: parseInt(process.env.MERGE_WAIT_SECONDS || '45', 10),
  replyInGroups: process.env.REPLY_IN_GROUPS === '1',
  // comma-separated group names (exact, case-insensitive). Empty + REPLY_IN_GROUPS=1 = every group.
  allowGroups: (process.env.ALLOW_GROUPS || '').split(',').map(s => s.trim().toLowerCase()).filter(Boolean),
  // comma-separated numbers (country code, digits only). Empty = reply to everyone.
  // In groups this is checked against the person who wrote the message.
  allowList: (process.env.ALLOW_NUMBERS || '').split(',').map(s => s.trim()).filter(Boolean),
  autoReplyQuotes: process.env.AUTO_QUOTE !== '0',
  autoMergePdf: process.env.AUTO_PDF !== '0',
};

// whatsapp-web.js loads the current WhatsApp Web build itself. Set WA_WEB_VERSION
// in .env only if support tells you to pin a specific build.
const clientOpts = {
  authStrategy: new LocalAuth({ dataPath: path.resolve(__dirname, '.wwebjs_auth') }),
  puppeteer: { headless: true, args: ['--no-sandbox', '--disable-setuid-sandbox'] },
};
if (process.env.WA_WEB_VERSION) {
  clientOpts.webVersion = process.env.WA_WEB_VERSION;
  clientOpts.webVersionCache = {
    type: 'remote',
    remotePath: `https://raw.githubusercontent.com/wppconnect-team/wa-version/main/html/${process.env.WA_WEB_VERSION}.html`,
  };
}
const client = new Client(clientOpts);

async function contactLabel(msg) {
  try {
    const c = await msg.getContact();
    const num = (c.number || (msg.author || msg.from).split('@')[0]);
    let who = (c.name || c.pushname) ? `${c.name || c.pushname} (${num})` : num;
    if (msg.from.endsWith('@g.us')) { try { who = ((await msg.getChat()).name || 'group') + ' - ' + who; } catch {} }
    return who;
  } catch { return (msg.author || msg.from).split('@')[0]; }
}

const batcher = new PhotoBatcher({
  waitSeconds: CFG.mergeWaitSeconds,
  onFlush: async (chatId, files) => {
    const label = files[0].label;
    const paths = files.map(f => f.path);
    try {
      const { bytes, pages, skipped } = await mergeImagesToPdf(paths, { label: label.replace(/\s*\(.*\)$/, ''), title: `Photos from ${label}` });
      const name = `photos_${org.safeName(label).replace(/\s+/g, '_')}_${Date.now()}.pdf`;
      const out = org.saveOutput(label, name, Buffer.from(bytes), { pages, source: paths.length });
      const media = MessageMedia.fromFilePath(out);
      let caption = `Merged ${pages} photo${pages === 1 ? '' : 's'} into one PDF.`;
      if (skipped.length) caption += ` Skipped ${skipped.length} file(s) that were not JPEG/PNG.`;
      await client.sendMessage(chatId, media, { caption, sendMediaAsDocument: true });
      console.log(`[pdf] ${label}: ${pages} pages -> ${out}`);
    } catch (e) {
      console.error('[pdf] merge failed', e);
      await client.sendMessage(chatId, 'Sorry, could not merge those photos. They are saved and I will do it manually.');
    }
  },
});

async function allowed(msg) {
  if (msg.fromMe) return false;
  if (msg.from === 'status@broadcast') return false;
  const isGroup = msg.from.endsWith('@g.us');
  if (isGroup) {
    if (!CFG.replyInGroups) return false;
    if (CFG.allowGroups.length) {
      let name = '';
      try { name = ((await msg.getChat()).name || '').toLowerCase(); } catch {}
      if (!CFG.allowGroups.includes(name)) return false;
    }
  }
  const sender = (isGroup ? (msg.author || '') : msg.from).split('@')[0];
  if (CFG.allowList.length && !CFG.allowList.includes(sender)) return false;
  return true;
}

async function printGroups() {
  try {
    const groups = (await client.getChats()).filter(c => c.isGroup).map(c => c.name);
    if (!groups.length) return console.log('[groups] this number is not in any group');
    console.log('[groups] your groups (copy the exact name into ALLOW_GROUPS in .env):');
    groups.forEach(g => console.log('   ' + g));
  } catch (e) { console.error('[groups] could not list groups', e.message); }
}

async function handleQuote(msg, text, label) {
  const { input, missing } = parseQuote(text);
  if (missing.length) {
    org.log({ type: 'quote-incomplete', contact: label, text, missing });
    return msg.reply(missingText(missing));
  }
  const res = await calc.motorQuote(input);
  if (!res.ok) {
    org.log({ type: 'quote-error', contact: label, text, errors: res.errors });
    return msg.reply('Could not calculate: ' + res.errors.join('; ') + '\n\n' + missingText([]));
  }
  const pdfPath = org.saveOutput(label, res.filename, Buffer.from(res.pdfBase64, 'base64'), { total: res.summary.total, text });
  await msg.reply(quoteText(res.summary, CFG.agentName));
  await client.sendMessage(msg.from, MessageMedia.fromFilePath(pdfPath), { sendMediaAsDocument: true });
  org.log({ type: 'quote', contact: label, text, input, total: res.summary.total, file: path.basename(pdfPath) });
  console.log(`[quote] ${label}: "${text}" -> ₹${res.summary.total}`);
}

client.on('qr', qr => {
  const png = path.resolve(__dirname, 'qr.png');
  QRCode.toFile(png, qr, { width: 400 }).catch(() => {});
  console.log('\n==============================================');
  console.log('Open WhatsApp on your phone > Linked devices > Link a device');
  console.log('and scan the QR below, or open this image: ' + png);
  console.log('==============================================\n');
  qrcode.generate(qr, { small: true });
});
client.on('authenticated', () => {
  try { fs.unlinkSync(path.resolve(__dirname, 'qr.png')); } catch {}
  console.log('[whatsapp] login accepted, loading your chats... (this can take 1-2 minutes)');
});
client.on('loading_screen', (pct) => console.log(`[whatsapp] loading ${pct}%`));
client.on('change_state', (st) => console.log('[whatsapp] state: ' + st));
let readySeen = false;
setTimeout(() => {
  if (readySeen) return;
  console.log('\n[whatsapp] still not ready after 3 minutes. Press Ctrl+C, then delete the folders');
  console.log('           .wwebjs_auth and .wwebjs_cache inside the whatsapp-bot folder, run node bot.js');
  console.log('           again and scan the QR once more.\n');
}, 3 * 60 * 1000);
client.on('ready', () => {
  readySeen = true;
  console.log(`Ready. Inbox: ${org.ROOT}  merge wait: ${CFG.mergeWaitSeconds}s  groups: ${CFG.replyInGroups ? (CFG.allowGroups.join(', ') || 'all') : 'off'}`);
  printGroups();
});
client.on('auth_failure', m => console.error('Auth failure', m));
client.on('disconnected', r => { console.error('Disconnected:', r); process.exit(1); });

client.on('message', async msg => {
  if (!(await allowed(msg))) return;
  const label = await contactLabel(msg);
  const text = (msg.body || '').trim();

  try {
    if (msg.hasMedia) {
      let media = null;
      try { media = await msg.downloadMedia(); } catch (e) { console.error(`[media] could not download from ${label}: ${e.message}`); }
      if (!media) { org.log({ type: 'media-failed', contact: label, caption: text }); return; }
      const file = org.saveMedia(label, media, { caption: text, from: msg.from });
      if (CFG.autoMergePdf && /^image\//.test(media.mimetype)) {
        const n = batcher.add(msg.from, { path: file, label });
        if (n === 1) await msg.reply(`Got it. I will merge the photos into one PDF in ${CFG.mergeWaitSeconds}s (send *pdf* to do it now).`);
      }
      // a photo captioned with a quote request still gets a quote
      if (CFG.autoReplyQuotes && isQuoteRequest(text)) await handleQuote(msg, text, label);
      return;
    }

    if (/^(pdf|merge|done)$/i.test(text)) {
      if (!batcher.pending(msg.from)) return msg.reply('No photos waiting. Send the photos first, then *pdf*.');
      return batcher.flush(msg.from);
    }
    if (/^(help|hi|hello|menu|start)$/i.test(text)) return msg.reply(HELP);
    if (CFG.autoReplyQuotes && isQuoteRequest(text)) return handleQuote(msg, text, label);

    org.log({ type: 'text', contact: label, text });
  } catch (e) {
    console.error(`[handler] ${label}: ${e.message}`);
  }
});

process.on('SIGINT', async () => { await calc.close(); await client.destroy(); process.exit(0); });
client.initialize();
