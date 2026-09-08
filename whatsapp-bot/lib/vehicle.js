/**
 * Finds an Indian vehicle registration number in photos using local OCR
 * (tesseract.js, runs on this computer, no internet after the first run when
 * it downloads its English data once). Returns e.g. "AP26AB1234" or null.
 */
const path = require('path');
const fs = require('fs');

// AP26 AB 1234 · TS09EA5678 · KA01 M 1234 · DL 1C AB 1234 · 22BH1234AA (Bharat series)
const REG_RE = /\b([A-Z]{2})[\s\-.]*(\d{1,2})[\s\-.]*([A-Z]{1,3})[\s\-.]*(\d{4})\b|\b(\d{2})[\s\-.]*(BH)[\s\-.]*(\d{4})[\s\-.]*([A-Z]{1,2})\b/g;
const STATE_CODES = new Set(('AN AP AR AS BR CG CH DD DL DN GA GJ HP HR JH JK KA KL LA LD MH ML MN MP MZ NL OD OR PB PY RJ SK TN TR TS UK UP WB').split(' '));

// Common OCR confusions inside the digit groups
const fixDigits = s => s.replace(/O/g, '0').replace(/[Il|]/g, '1').replace(/S/g, '5').replace(/B/g, '8').replace(/Z/g, '2');
const fixLetters = s => s.replace(/0/g, 'O').replace(/1/g, 'I').replace(/5/g, 'S').replace(/8/g, 'B').replace(/2/g, 'Z');

// Partial fallback: series letters + last four digits (e.g. "AB 1234" -> AB1234),
// or a state-code prefix with unreadable middle and readable last four ("AP26 ?? 1234" -> 1234).
const PARTIAL_RE = /\b([A-Z]{1,3})[\s\-.]*(\d{4})\b/g;
const TAIL_RE = /\b[A-Z]{2}[\s\-.]*\d{2}[\s\-.]*\S{0,4}[\s\-.]*(\d{4})\b/g;
const NOT_SERIES = new Set(['NO', 'RC', 'CC', 'KG', 'KW', 'DT', 'RTO', 'MFG', 'DOB', 'PIN', 'REG', 'REGN']);
function extractPartials(text) {
  const found = new Map();
  const t = text.toUpperCase();
  let m;
  while ((m = PARTIAL_RE.exec(t))) {
    const series = fixLetters(m[1]); const digits = fixDigits(m[2]);
    if (NOT_SERIES.has(series) || /^(19|20)\d\d$/.test(digits)) continue;   // skip years
    const key = series + digits;
    found.set(key, (found.get(key) || 0) + 1);
  }
  while ((m = TAIL_RE.exec(t))) {
    const digits = fixDigits(m[1]);
    if (/^(19|20)\d\d$/.test(digits)) continue;
    found.set(digits, (found.get(digits) || 0) + 1);
  }
  return found;
}

function extractNumbers(text) {
  const found = new Map();
  const t = text.toUpperCase();
  let m;
  while ((m = REG_RE.exec(t))) {
    let key;
    if (m[6] === 'BH') key = `${fixDigits(m[5])}BH${fixDigits(m[7])}${fixLetters(m[8])}`;
    else {
      const st = fixLetters(m[1]);
      if (!STATE_CODES.has(st)) continue;
      key = `${st}${fixDigits(m[2]).padStart(2, '0')}${fixLetters(m[3])}${fixDigits(m[4])}`;
    }
    found.set(key, (found.get(key) || 0) + 1);
  }
  return found;
}

function withTimeout(promise, ms, what) {
  let t;
  return Promise.race([
    promise.finally(() => clearTimeout(t)),
    new Promise((_, rej) => { t = setTimeout(() => rej(new Error(what + ' timed out after ' + Math.round(ms / 1000) + 's')), ms); }),
  ]);
}

let workerPromise = null;
async function getWorker() {
  if (!workerPromise) {
    const { createWorker } = require('tesseract.js');
    const cache = path.resolve(__dirname, '..', '.tessdata');
    fs.mkdirSync(cache, { recursive: true });
    const firstTime = !fs.readdirSync(cache).some(f => f.startsWith('eng'));
    if (firstTime) console.log('[ocr] first run: downloading English reading data (about 4 MB)...');
    const p = createWorker('eng', 1, {
      cachePath: cache, logger: () => {},
      errorHandler: (e) => { console.error('[ocr] worker error: ' + (e && e.message || e)); workerPromise = null; },
    });
    workerPromise = withTimeout(p, 120000, 'OCR start-up').then(w => { if (firstTime) console.log('[ocr] reading data ready'); return w; })
      .catch(e => { workerPromise = null; throw e; });
  }
  return workerPromise;
}

/** Call at startup so a download problem shows up immediately, not on the first PDF. */
async function warmUp() {
  try { await getWorker(); console.log('[ocr] vehicle-number reader ready'); }
  catch (e) { console.error('[ocr] reader not available: ' + e.message + ' (PDFs will be named by sender until this works)'); }
}

// Recognise with a timeout so a stuck worker cannot hold the merge forever.
function recognize(worker, file, ms = 30000) {
  return Promise.race([
    worker.recognize(file),
    new Promise((_, rej) => setTimeout(() => rej(new Error('OCR timeout')), ms)),
  ]);
}

// Clean-up variants of a photo for OCR: EXIF-straightened, grayscale, enlarged,
// normalised, sharpened; plus 90/270 rotations for sideways photos.
async function variants(file) {
  let sharp; try { sharp = require('sharp'); } catch { return [file]; }
  const out = [];
  try {
    const base = sharp(file).rotate().grayscale().normalise();
    const meta = await sharp(file).rotate().metadata();
    const width = Math.max(meta.width || 0, 1800);
    const tmp = path.join(require('os').tmpdir(), 'ocr-' + process.pid + '-' + Date.now());
    const clean = tmp + '-a.png';
    await base.clone().resize({ width, withoutEnlargement: false }).sharpen().toFile(clean);
    out.push(clean);
    const thresh = tmp + '-b.png';
    await base.clone().resize({ width }).threshold(150).toFile(thresh);
    out.push(thresh);
    for (const angle of [90, 270]) {
      const r = tmp + '-r' + angle + '.png';
      await base.clone().resize({ width }).sharpen().rotate(angle).toFile(r);
      out.push(r);
    }
  } catch (e) { /* fall back to the original */ }
  return out.length ? out : [file];
}

/**
 * @param {string[]} imageFiles  JPEG/PNG paths (PDFs are ignored)
 * @param {{maxImages?:number}} opts
 * @returns {Promise<{number:string|null, candidates:object}>}
 */
async function findVehicleNumber(imageFiles, opts = {}) {
  const files = imageFiles
    .filter(f => /\.(jpe?g|png)$/i.test(f))
    .filter(f => { try { return fs.statSync(f).size > 1500; } catch { return false; } })   // skip stubs/thumbnails
    .slice(0, opts.maxImages || 8);
  const tally = new Map(), partial = new Map();
  if (!files.length) return { number: null, partial: null, candidates: {} };
  const started = Date.now();
  console.log(`[ocr] reading ${files.length} photo${files.length === 1 ? '' : 's'} for a vehicle number...`);
  const worker = await getWorker();
  const deadline = started + (opts.budgetMs || 90000);          // never hold a PDF for more than this
  for (const f of files) {
    if (Date.now() > deadline) { console.log('[ocr] time budget used up, giving up on the rest'); break; }
    const imgs = await variants(f);
    for (const img of imgs) {
      try {
        const { data } = await recognize(worker, img);
        const text = data.text || '';
        for (const [k, v] of extractNumbers(text)) tally.set(k, (tally.get(k) || 0) + v);
        for (const [k, v] of extractPartials(text)) partial.set(k, (partial.get(k) || 0) + v);
      } catch (e) {
        console.error(`[ocr] ${path.basename(f)}: ${e.message}`);
        if (/timeout/.test(e.message)) { workerPromise = null; break; }
      }
      if (img !== f) { try { fs.unlinkSync(img); } catch {} }
      if (tally.size) break;                       // full number found: no need for more variants
      if (Date.now() > deadline) break;
    }
    if (tally.size) break;
    // no full number in this photo: partials keep accumulating across photos and variants,
    // so the real digits (seen repeatedly) outvote one-off garbage
  }
  const best = m => { let b = null, n = 0; for (const [k, v] of m) if (v > n) { b = k; n = v; } return b; };
  const number = best(tally);
  console.log(`[ocr] done in ${Math.round((Date.now() - started) / 1000)}s: ${number ? 'found ' + number : (partial.size ? 'partial only' : 'nothing readable')}`);
  // prefer a partial with series letters over bare digits
  let part = null;
  if (!number && partial.size) {
    // score = own count + count of the other form of the same digits (AB1234 supports 1234 and vice versa)
    const scored = [...partial].map(([k, v]) => {
      const digits = k.slice(-4);
      let support = 0;
      for (const [k2, v2] of partial) if (k2 !== k && k2.slice(-4) === digits) support += v2;
      return [k, v + support, /^[A-Z]/.test(k) ? 1 : 0];
    }).sort((a, b) => b[1] - a[1] || b[2] - a[2]);
    // only accept when it is clearly the best (not a one-off tie)
    if (scored.length === 1 || scored[0][1] > scored[1][1]) part = scored[0][0];
  }
  return { number, partial: part, candidates: { ...Object.fromEntries(tally), ...Object.fromEntries(partial) } };
}

async function close() { if (workerPromise) { try { (await workerPromise).terminate(); } catch {} workerPromise = null; } }

module.exports = { findVehicleNumber, extractNumbers, extractPartials, warmUp, close };
