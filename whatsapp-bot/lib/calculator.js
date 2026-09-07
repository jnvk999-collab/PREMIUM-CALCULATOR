/**
 * Headless driver for the OIC Premium Calculator (../index.html).
 *
 * The calculator's pricing lives inside the browser page (calculate() reads the
 * DOM and writes state.lastQuote; generatePDF() builds the jsPDF document).
 * Rather than duplicate 10,000 lines of rating logic, we open the real page in
 * headless Chromium once and drive it the same way the built-in self-test does.
 * Any rate change you make to index.html is therefore picked up automatically.
 */
const path = require('path');
const { chromium } = require('playwright');

const INDEX = 'file://' + path.resolve(__dirname, '..', '..', 'index.html');

let browser = null;
let page = null;

async function getPage() {
  if (page) return page;
  browser = await chromium.launch({ headless: true });
  page = await browser.newPage();
  page.on('pageerror', e => console.error('[calculator page error]', e.message));
  await page.goto(INDEX);
  await page.waitForFunction(
    () => typeof calculate === 'function' && typeof generatePDF === 'function' && window.jspdf,
    null, { timeout: 30000 }
  );
  return page;
}

async function close() {
  if (browser) { await browser.close(); browser = null; page = null; }
}

/**
 * @param {object} inp
 *   vehicleType  'twoWheeler' | 'privateCar' | 'commercial' | 'pccv' | 'misc' | 'twoWheelerHire'
 *   slab         slab key for that vehicle type, e.g. 's2'
 *   zone         'A' | 'B' | 'C'
 *   fuel         'petrol' | 'diesel' | 'cng' | 'electric' | 'hybrid'
 *   policyType   'comprehensive' | 'odOnly' | 'thirdParty'
 *   regDate      'YYYY-MM-DD'
 *   incDate      'YYYY-MM-DD' (defaults to today)
 *   idv          number (ignored for thirdParty)
 *   ncbYears     0..5
 *   addons       array of addon keys, e.g. ['nilDep']
 *   imt23        boolean (commercial only)
 *   customerName, vehicleModel, regNumber   strings for the PDF
 * @returns {{ok:true, summary:object, pdfBase64:string, filename:string} | {ok:false, errors:string[]}}
 */
async function motorQuote(inp) {
  const pg = await getPage();
  return pg.evaluate((inp) => {
    const $ = id => document.getElementById(id);
    _stBaseline();                                   // same clean slate the self-test uses
    state.vehicleType = inp.vehicleType;
    if (typeof renderSlabs === 'function') renderSlabs();
    state.zone = inp.zone || 'B';
    state.fuel = inp.fuel || 'petrol';
    state.policyTerm = inp.policyTerm || '1yr';
    state.ncbYears = inp.ncbYears || 0;
    state.odDiscount = 0;
    state.nilDepDiscount = 0;

    $('ccSlab').value = inp.slab;
    if ($('ccSlab').value !== inp.slab) {
      return { ok: false, errors: ['Unknown slab "' + inp.slab + '" for ' + inp.vehicleType] };
    }
    $('regDate').value = inp.regDate || '';
    if ($('incDate')) $('incDate').value = inp.incDate || new Date().toISOString().slice(0, 10);
    $('idv').value = inp.idv || '';
    if ($('elecAcc')) $('elecAcc').value = inp.elecAcc || 0;
    if ($('nonElecAcc')) $('nonElecAcc').value = 0;
    if ($('cngKit')) $('cngKit').value = inp.cngKit || 0;
    if ($('paxCount')) $('paxCount').value = inp.paxCount || 0;
    if ($('gcvActualGVW')) $('gcvActualGVW').value = '';
    if ($('exactCC')) $('exactCC').value = inp.exactCC || '';
    if ($('customerName')) $('customerName').value = inp.customerName || '';
    if ($('vehicleModel')) $('vehicleModel').value = inp.vehicleModel || '';
    if ($('regNumber')) $('regNumber').value = inp.regNumber || '';

    [['comprehensive', 'polComp'], ['odOnly', 'polOD'], ['thirdParty', 'polTP']]
      .forEach(([p, r]) => { if ($(r)) $(r).checked = (p === (inp.policyType || 'comprehensive')); });

    if ($('swImt23')) $('swImt23').checked = !!inp.imt23;
    state.switches.imt23 = !!inp.imt23;

    if (typeof renderAddons === 'function') renderAddons();
    const wanted = new Set(inp.addons || []);
    document.querySelectorAll('#addonsGrid input').forEach(i => { i.checked = wanted.has(i.dataset.addon); });

    state.lastQuote = null;
    calculate(true);
    const q = state.lastQuote;
    if (!q) {
      const errors = Array.from(document.querySelectorAll('.field.has-error .error')).map(e => e.textContent.trim());
      return { ok: false, errors: errors.length ? errors : ['Calculator did not produce a quote'] };
    }
    const { doc, filename } = generatePDF(q);
    const pdfBase64 = doc.output('datauristring').split(',')[1];
    const addons = (q.addons && q.addons.items || []).map(a => ({ key: a.key, label: a.label, amount: Math.round(a.amount || 0) }));
    return {
      ok: true,
      filename,
      pdfBase64,
      summary: {
        vehicleType: q.data.vehicleType, slab: q.data.slab, zone: q.data.zone, fuel: q.data.fuel,
        policyType: q.data.policyType, idv: q.idv, ageYears: Math.round((q.ageYears || 0) * 10) / 10,
        ncbPct: Math.round((RATES.ncb[q.data.ncbYears] || 0) * 100),
        odBase: Math.round(q.odBase || 0), odDiscountAmt: Math.round(q.odDiscountAmt || 0),
        ncbDiscount: Math.round(q.ncbDiscount || 0), netOD: Math.round(q.netOD || 0),
        tpPremium: Math.round(q.tpPremium || 0), addons,
        subtotal: Math.round(q.subtotal || 0), gst: Math.round(q.gst || 0), total: Math.round(q.total || 0)
      }
    };
  }, inp);
}

module.exports = { motorQuote, close };
