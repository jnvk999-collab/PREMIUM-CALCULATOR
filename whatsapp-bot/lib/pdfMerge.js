/**
 * Collects photos per chat and merges them into one A4 PDF.
 *
 * WhatsApp delivers a burst of photos as separate messages, so we buffer them
 * per sender and flush when the burst goes quiet (MERGE_WAIT_SECONDS) or when
 * the sender says "pdf". JPEG and PNG are embedded as-is by pdf-lib; anything
 * else is skipped with a note in the caption.
 */
const fs = require('fs');
const path = require('path');
const { PDFDocument, rgb, StandardFonts } = require('pdf-lib');

const A4 = { w: 595.28, h: 841.89 };
const MARGIN = 24;

async function mergeImagesToPdf(files, opts = {}) {
  const pdf = await PDFDocument.create();
  const font = await pdf.embedFont(StandardFonts.Helvetica);
  const skipped = [];
  let n = 0;
  for (const f of files) {
    const bytes = fs.readFileSync(f);
    let img;
    try {
      if (/\.png$/i.test(f) || bytes.slice(0, 8).toString('hex') === '89504e470d0a1a0a') img = await pdf.embedPng(bytes);
      else img = await pdf.embedJpg(bytes);
    } catch (e) { skipped.push(path.basename(f)); continue; }
    const page = pdf.addPage([A4.w, A4.h]);
    const maxW = A4.w - 2 * MARGIN, maxH = A4.h - 2 * MARGIN - 14;
    const s = Math.min(maxW / img.width, maxH / img.height, 1e9);
    const w = img.width * s, h = img.height * s;
    page.drawImage(img, { x: (A4.w - w) / 2, y: (A4.h - h) / 2 + 7, width: w, height: h });
    n++;
    const footer = `${opts.label || ''}  ·  ${n}/${files.length}`.trim();
    page.drawText(footer, { x: MARGIN, y: 8, size: 8, font, color: rgb(0.45, 0.45, 0.45) });
  }
  if (opts.title) pdf.setTitle(opts.title);
  return { bytes: await pdf.save(), pages: n, skipped };
}

/** Per-chat buffer with a quiet-period timer. */
class PhotoBatcher {
  constructor({ waitSeconds = 45, onFlush }) {
    this.wait = waitSeconds * 1000;
    this.onFlush = onFlush;
    this.buffers = new Map();   // chatId -> { files:[], timer }
  }
  add(chatId, file) {
    let b = this.buffers.get(chatId);
    if (!b) { b = { files: [], timer: null }; this.buffers.set(chatId, b); }
    b.files.push(file);
    if (b.timer) clearTimeout(b.timer);
    b.timer = setTimeout(() => this.flush(chatId), this.wait);
    return b.files.length;
  }
  async flush(chatId) {
    const b = this.buffers.get(chatId);
    if (!b || !b.files.length) return null;
    this.buffers.delete(chatId);
    if (b.timer) clearTimeout(b.timer);
    return this.onFlush(chatId, b.files);
  }
  pending(chatId) { const b = this.buffers.get(chatId); return b ? b.files.length : 0; }
}

module.exports = { mergeImagesToPdf, PhotoBatcher };
