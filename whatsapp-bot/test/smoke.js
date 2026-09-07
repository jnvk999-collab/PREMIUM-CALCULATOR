// Runs without WhatsApp: checks the headless calculator against one of the
// calculator's own golden cases, the text parser, and the photo->PDF merge.
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { PDFDocument } = require('pdf-lib');
const calc = require('../lib/calculator');
const { parseQuote } = require('../lib/intents');
const { mergeImagesToPdf } = require('../lib/pdfMerge');
const { quoteText } = require('../lib/reply');

(async () => {
  // 1. Golden case from index.html: twoWheeler s1 zone A age 0.5 idv 95000 petrol comp,
  //    nilDep on, inception 2026-07-01 -> total 1915
  const r = await calc.motorQuote({
    vehicleType: 'twoWheeler', slab: 's1', zone: 'A', fuel: 'petrol', policyType: 'comprehensive',
    regDate: '2026-07-01', incDate: '2026-07-01', idv: 95000, ncbYears: 0, addons: ['nilDep'],
    customerName: 'Smoke Test', vehicleModel: 'Test Bike',
  });
  assert(r.ok, 'quote failed: ' + JSON.stringify(r));
  assert.strictEqual(r.summary.total, 1915, 'golden total mismatch: ' + JSON.stringify(r.summary));
  assert(r.pdfBase64.length > 1000 && r.filename.endsWith('.pdf'));
  console.log('✓ calculator golden case  total ₹' + r.summary.total + '  ' + r.filename);
  console.log(quoteText(r.summary, 'Agent').split('\n').slice(0, 3).join(' | '));

  // 2. validation path: missing IDV should come back as an error, not a crash
  const bad = await calc.motorQuote({ vehicleType: 'privateCar', slab: 's2', zone: 'B', fuel: 'petrol', policyType: 'comprehensive', regDate: '2020-01-01', idv: 0 });
  assert(!bad.ok && bad.errors.length, 'expected validation error');
  console.log('✓ validation error surfaced: ' + bad.errors.join('; '));

  // 3. parser
  const p1 = parseQuote('quote bike 125cc 2021 idv 60000');
  assert.deepStrictEqual([p1.input.vehicleType, p1.input.slab, p1.input.regDate, p1.input.idv, p1.missing.length], ['twoWheeler', 's2', '2021-06-01', 60000, 0]);
  const p2 = parseQuote('Car quote 1200 cc reg 2019 idv 4.5 lakh zone A ncb 25% zero dep diesel for Ramesh Kumar');
  assert.deepStrictEqual([p2.input.vehicleType, p2.input.slab, p2.input.idv, p2.input.zone, p2.input.ncbYears, p2.input.fuel, p2.input.addons, p2.input.customerName],
    ['privateCar', 's2', 450000, 'A', 2, 'diesel', ['nilDep'], 'Ramesh Kumar']);
  const p3 = parseQuote('tp only activa 2018');
  assert.deepStrictEqual([p3.input.vehicleType, p3.input.slab, p3.input.policyType, p3.missing.length], ['twoWheeler', 's2', 'thirdParty', 0]);
  const p4 = parseQuote('quote please');
  assert(p4.missing.length >= 3);
  console.log('✓ parser: ' + p4.missing.join(' / '));

  // 4. parsed message all the way through the calculator
  const r2 = await calc.motorQuote(p2.input);
  assert(r2.ok && r2.summary.total > 0, JSON.stringify(r2));
  console.log('✓ end-to-end car quote  total ₹' + r2.summary.total);

  // 5. photo merge
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'merge-'));
  const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAD0lEQVR4nGP4z8DwHwYBAA4DBQAsKrKfAAAAAElFTkSuQmCC', 'base64');
  const files = [1, 2, 3].map(i => { const f = path.join(tmp, `p${i}.png`); fs.writeFileSync(f, png); return f; });
  fs.writeFileSync(path.join(tmp, 'junk.jpg'), 'not an image'); files.push(path.join(tmp, 'junk.jpg'));
  const m = await mergeImagesToPdf(files, { label: 'Ramesh', title: 'test' });
  const doc = await PDFDocument.load(m.bytes);
  assert.strictEqual(doc.getPageCount(), 3); assert.strictEqual(m.skipped.length, 1);
  console.log('✓ merged 3 images into a 3-page PDF, skipped 1 bad file');

  await calc.close();
  console.log('ALL OK');
})().catch(async e => { console.error(e); await calc.close(); process.exit(1); });
