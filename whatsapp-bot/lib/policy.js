/**
 * Pulls the key fields out of a policy PDF's text (Oriental Insurance schedules
 * and most Indian motor policies): policy number, insured name, vehicle,
 * period from/to, mobile. Heuristic; returns what it can find.
 */
const fs = require('fs');
const { extractNumbers } = require('./vehicle');

async function textOf(file, maxPages = 4) {
  const pdfjs = require('pdfjs-dist/legacy/build/pdf.mjs');
  const doc = await pdfjs.getDocument({ data: new Uint8Array(fs.readFileSync(file)), useSystemFonts: true, disableFontFace: true, verbosity: 0 }).promise;
  let text = '';
  for (let i = 1; i <= Math.min(doc.numPages, maxPages); i++) {
    const c = await (await doc.getPage(i)).getTextContent();
    text += c.items.map(it => it.str).join(' ') + '\n';
  }
  return text.replace(/\s+/g, ' ');
}

const MONTHS = { jan: 1, feb: 2, mar: 3, apr: 4, may: 5, jun: 6, jul: 7, aug: 8, sep: 9, sept: 9, oct: 10, nov: 11, dec: 12 };
function toISO(s) {
  if (!s) return null;
  let m = s.match(/(\d{1,2})[\/\-.](\d{1,2})[\/\-.](\d{4})/);
  if (m) return `${m[3]}-${m[2].padStart(2, '0')}-${m[1].padStart(2, '0')}`;
  m = s.match(/(\d{1,2})[\s\-]([A-Za-z]{3,9})[\s\-,]+(\d{4})/);
  if (m && MONTHS[m[2].slice(0, 3).toLowerCase()]) return `${m[3]}-${String(MONTHS[m[2].slice(0, 3).toLowerCase()]).padStart(2, '0')}-${m[1].padStart(2, '0')}`;
  m = s.match(/(\d{4})-(\d{2})-(\d{2})/);
  if (m) return m[0];
  return null;
}
const DATE = '(\\d{1,2}[\\/\\-.]\\d{1,2}[\\/\\-.]\\d{4}|\\d{1,2}[\\s\\-][A-Za-z]{3,9}[\\s\\-,]+\\d{4})';

function parse(text) {
  const t = text || '';
  const out = {};
  let m;
  if ((m = t.match(/Policy\s*(?:No|Number)\.?\s*[:\-]?\s*([0-9]{2,}[0-9\/\-]{6,})/i))) out.policyNo = m[1].trim();
  if ((m = t.match(/(?:Name of (?:the )?Insured|Insured(?:'s)? Name|Insured)\s*[:\-]?\s*(?:Mr\.?|Mrs\.?|Ms\.?|M\/s\.?|Shri|Smt)?\s*([A-Z][A-Za-z.]+(?:\s+[A-Z][A-Za-z.]+){0,4})/))) {
    const STOP = /\b(Address|Mobile|Phone|Email|Policy|Period|Date|Registration|Vehicle|Contact|Pin|GSTIN|PAN|Hypothecat\w*)\b/;
    out.insured = m[1].split(STOP)[0].replace(/\s+/g, ' ').trim();
  }
  if ((m = t.match(new RegExp('(?:Period of (?:Insurance|Cover)|Policy Period)[\\s\\S]{0,60}?' + DATE + '[\\s\\S]{0,60}?' + DATE, 'i')))) { out.from = toISO(m[1]); out.to = toISO(m[2]); }
  if (!out.to && (m = t.match(new RegExp('(?:To|Till|Upto|Up to|Expiry|End Date)\\s*[:\\-]?\\s*(?:Midnight (?:of|on)\\s*)?' + DATE, 'i')))) out.to = toISO(m[1]);
  if (!out.from && (m = t.match(new RegExp('From\\s*[:\\-]?\\s*' + DATE, 'i')))) out.from = toISO(m[1]);
  if ((m = t.match(/(?:Mobile|Mob|Phone|Contact|Cell)\s*(?:No\.?|Number)?\s*[:\-]?\s*(?:\+91[\s\-]?|0)?([6-9]\d{9})\b/i))) out.mobile = m[1];
  const regs = extractNumbers(t);
  let best = null, n = 0; for (const [k, v] of regs) if (v > n) { best = k; n = v; }
  if (best) out.vehicle = best;
  if ((m = t.match(/(?:Make|Manufacturer)\s*(?:\/\s*Model)?\s*[:\-]?\s*([A-Z][A-Za-z0-9 .\-]{2,30}?)(?:\s{2,}|\s(?:Model|Variant|Engine|Chassis|Cubic|Year|Fuel|Seating|GVW|Body)\b)/))) out.make = m[1].trim();
  if ((m = t.match(/(?:Total Premium|Premium Payable|Gross Premium|Total Amount)\s*(?:\(?Rs\.?\)?|₹|INR)?\s*[:\-]?\s*(?:Rs\.?|₹|INR)?\s*([\d,]+(?:\.\d{1,2})?)/i))) out.premium = parseFloat(m[1].replace(/,/g, ''));
  return out;
}

async function fromPdf(file) { try { return parse(await textOf(file)); } catch (e) { return { error: e.message }; } }

module.exports = { parse, fromPdf, textOf, toISO };
