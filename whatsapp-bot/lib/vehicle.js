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

let workerPromise = null;
async function getWorker() {
  if (!workerPromise) {
    const { createWorker } = require('tesseract.js');
    const cache = path.resolve(__dirname, '..', '.tessdata');
    fs.mkdirSync(cache, { recursive: true });
    workerPromise = createWorker('eng', 1, {
      cachePath: cache, logger: () => {},
      errorHandler: (e) => { console.error('[ocr] worker error: ' + (e && e.message || e)); workerPromise = null; },
    });
  }
  return workerPromise;
}

// Recognise with a timeout so a stuck worker cannot hold the merge forever.
function recognize(worker, file, ms = 30000) {
  return Promise.race([
    worker.recognize(file),
    new Promise((_, rej) => setTimeout(() => rej(new Error('OCR timeout')), ms)),
  ]);
}

/**
 * @param {string[]} imageFiles  JPEG/PNG paths (PDFs are ignored)
 * @param {{maxImages?:number}} opts
 * @returns {Promise<{number:string|null, candidates:object}>}
 */
async function findVehicleNumber(imageFiles, opts = {}) {
  const files = imageFiles
    .filter(f => /\.(jpe?g|png)$/i.test(f))
    .filter(f => { try { return fs.statSync(f).size > 5000; } catch { return false; } })   // skip stubs/thumbnails
    .slice(0, opts.maxImages || 8);
  const tally = new Map();
  if (!files.length) return { number: null, candidates: {} };
  const worker = await getWorker();
  for (const f of files) {
    try {
      const { data } = await recognize(worker, f);
      for (const [k, v] of extractNumbers(data.text || '')) tally.set(k, (tally.get(k) || 0) + v);
    } catch (e) {
      console.error(`[ocr] ${path.basename(f)}: ${e.message}`);
      if (/timeout/.test(e.message)) { workerPromise = null; break; }
    }
  }
  let best = null, bestN = 0;
  for (const [k, v] of tally) if (v > bestN) { best = k; bestN = v; }
  return { number: best, candidates: Object.fromEntries(tally) };
}

async function close() { if (workerPromise) { try { (await workerPromise).terminate(); } catch {} workerPromise = null; } }

module.exports = { findVehicleNumber, extractNumbers, close };
