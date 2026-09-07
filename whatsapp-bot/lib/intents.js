/**
 * Turns a free-text WhatsApp message into a calculator input, or tells the
 * caller what is missing. Pure rules, no network: it must answer in milliseconds
 * and never invent a value. Hinglish spellings are tolerated.
 *
 * Example messages it understands:
 *   "quote bike 125cc 2021 idv 60000"
 *   "car quote 1200 cc reg 2019 idv 4.5 lakh zone A ncb 25 zero dep diesel"
 *   "tp only activa 2018"
 *   "rate for scooty 2022 value 80000 name Ramesh"
 */

const VEHICLE_WORDS = [
  [/\b(bike|motorcycle|motor ?cycle|scooty|scooter|activa|splendor|pulsar|two ?wheeler|2 ?w|2wheeler|tw)\b/i, 'twoWheeler'],
  [/\b(car|hatchback|sedan|suv|swift|alto|wagonr|creta|innova|private ?car|4 ?w|pvt car)\b/i, 'privateCar'],
  [/\b(gcv|goods|tempo|truck|lorry|pickup|pick ?up|bolero pickup|commercial)\b/i, 'commercial'],
  [/\b(auto|autorickshaw|auto ?rickshaw|taxi|cab|bus|pccv|passenger)\b/i, 'pccv'],
];

const FUEL_WORDS = [
  [/\b(diesel|dsl)\b/i, 'diesel'], [/\b(cng)\b/i, 'cng'],
  [/\b(electric|ev|battery)\b/i, 'electric'], [/\b(hybrid)\b/i, 'hybrid'], [/\b(petrol|ptl)\b/i, 'petrol'],
];

function slabForCC(vehicleType, cc) {
  if (vehicleType === 'twoWheeler') return cc <= 75 ? 's1' : cc <= 150 ? 's2' : cc <= 350 ? 's3' : 's4';
  if (vehicleType === 'privateCar') return cc <= 1000 ? 's1' : cc <= 1500 ? 's2' : 's3';
  return null;
}

function slabForGVW(kg) {
  return kg <= 7500 ? 's1' : kg <= 12000 ? 's2' : kg <= 20000 ? 's3' : kg <= 40000 ? 's4' : 's5';
}

/** "4.5 lakh", "4.5l", "60k", "60,000", "6 lac" -> number */
function parseAmount(s) {
  if (!s) return null;
  const m = String(s).replace(/,/g, '').match(/([\d.]+)\s*(lakh|lac|lakhs|l|k|cr|crore)?/i);
  if (!m) return null;
  let n = parseFloat(m[1]);
  const u = (m[2] || '').toLowerCase();
  if (u.startsWith('l')) n *= 100000;
  else if (u === 'k') n *= 1000;
  else if (u.startsWith('cr')) n *= 10000000;
  return Math.round(n);
}

function isQuoteRequest(text) {
  return /\b(quote|quotation|premium|rate|price|kitna|kitni|cost|renewal|renew|insurance|bima)\b/i.test(text || '');
}

function parseQuote(text) {
  const t = ' ' + (text || '') + ' ';
  const out = { addons: [], policyType: 'comprehensive', zone: 'B', fuel: 'petrol', ncbYears: 0 };
  const missing = [];

  for (const [re, vt] of VEHICLE_WORDS) { if (re.test(t)) { out.vehicleType = vt; break; } }
  for (const [re, f] of FUEL_WORDS) { if (re.test(t)) { out.fuel = f; break; } }

  // slab: engine CC, kW, or GVW
  const cc = t.match(/(\d{2,4})\s*cc\b/i);
  const kw = t.match(/(\d{1,3})\s*kw\b/i);
  const gvw = t.match(/(\d{3,6})\s*(kg|kgs|gvw|ton|tonne|tons|t)\b/i);
  if (out.vehicleType === 'commercial' && gvw) {
    let kg = parseInt(gvw[1], 10);
    if (/^t/i.test(gvw[2])) kg *= 1000;
    out.slab = slabForGVW(kg);
  } else if (cc && out.vehicleType) {
    out.slab = slabForCC(out.vehicleType, parseInt(cc[1], 10));
    out.exactCC = parseInt(cc[1], 10);
  } else if (kw && out.vehicleType) {
    const k = parseInt(kw[1], 10);
    out.slab = out.vehicleType === 'twoWheeler' ? (k <= 3 ? 's1' : k <= 7 ? 's2' : k <= 16 ? 's3' : 's4')
             : out.vehicleType === 'privateCar' ? (k <= 30 ? 's1' : k <= 65 ? 's2' : 's3') : null;
    if (out.fuel === 'petrol') out.fuel = 'electric';
  }
  // common defaults when CC is obvious from the model name
  if (!out.slab && /\bactiva|jupiter|access|dio\b/i.test(t)) { out.vehicleType = 'twoWheeler'; out.slab = 's2'; out.exactCC = 110; }
  if (!out.slab && /\bsplendor|shine|platina|hf deluxe\b/i.test(t)) { out.vehicleType = 'twoWheeler'; out.slab = 's2'; out.exactCC = 100; }

  // registration date: full date, month-year, or year
  const full = t.match(/\b(\d{1,2})[\/\-.](\d{1,2})[\/\-.](\d{4})\b/);
  const my = t.match(/\b(\d{1,2})[\/\-.](\d{4})\b/);
  const yr = t.match(/\b(?:reg|regd|registered|model|mfg|year|yr|purchase[d]?)?\s*(20[0-4]\d|19[89]\d)\b/i);
  if (full) out.regDate = `${full[3]}-${full[2].padStart(2, '0')}-${full[1].padStart(2, '0')}`;
  else if (my) out.regDate = `${my[2]}-${my[1].padStart(2, '0')}-01`;
  else if (yr) out.regDate = `${yr[1]}-06-01`;

  // IDV
  const idv = t.match(/\b(?:idv|value|worth|amount|sum insured|si)\s*(?:of|is|:|=)?\s*(?:rs\.?|₹)?\s*([\d.,]+\s*(?:lakh|lac|lakhs|l|k|cr|crore)?)/i)
           || t.match(/(?:rs\.?|₹)\s*([\d.,]+\s*(?:lakh|lac|lakhs|l|k)?)/i);
  if (idv) out.idv = parseAmount(idv[1]);

  // policy type
  if (/\b(tp only|third party|3rd party|tp|act only|liability only)\b/i.test(t)) out.policyType = 'thirdParty';
  else if (/\b(od only|own damage only|standalone od|saod)\b/i.test(t)) out.policyType = 'odOnly';

  // zone
  const z = t.match(/\bzone\s*[-:]?\s*([abc])\b/i);
  if (z) out.zone = z[1].toUpperCase();
  else if (/\b(hyderabad|bangalore|bengaluru|chennai|mumbai|delhi|kolkata|pune|ahmedabad)\b/i.test(t)) out.zone = 'A';

  // NCB: "ncb 25", "25% ncb", "ncb 2 years"
  const ncbPct = t.match(/\bncb\s*[-:]?\s*(\d{1,2})\s*%|(\d{1,2})\s*%\s*ncb/i);
  const ncbYrs = t.match(/\bncb\s*(\d)\s*(?:yr|yrs|year|years)\b/i);
  const PCT_TO_YEARS = { 0: 0, 20: 1, 25: 2, 35: 3, 45: 4, 50: 5 };
  if (ncbYrs) out.ncbYears = Math.min(5, parseInt(ncbYrs[1], 10));
  else if (ncbPct) { const p = parseInt(ncbPct[1] || ncbPct[2], 10); out.ncbYears = PCT_TO_YEARS[p] ?? 0; }

  // add-ons
  if (/\b(zero dep|nil dep|zerodep|nildep|bumper to bumper|bumper|0 dep)\b/i.test(t)) out.addons.push('nilDep');
  if (/\b(engine protect|engine cover|hydrostatic)\b/i.test(t)) out.addons.push('engineProtect');
  if (/\brsa|road ?side\b/i.test(t)) out.addons.push('rsa');
  if (/\bimt ?23\b/i.test(t)) out.imt23 = true;

  // names for the PDF
  const name = t.match(/\b(?:name|for|customer|client)\s*[:\-]?\s*(?:mr\.?|mrs\.?|ms\.?|shri|smt)?\s*([A-Z][a-z]+(?:\s+[A-Z][a-z]+){0,2})/);
  if (name && !/^(Quote|Bike|Car|Zone|Idv|Ncb|Tp|Comp)$/i.test(name[1])) out.customerName = name[1];
  const reg = t.match(/\b([A-Z]{2}\s?\d{1,2}\s?[A-Z]{1,3}\s?\d{4})\b/i);
  if (reg) out.regNumber = reg[1].toUpperCase().replace(/\s+/g, ' ');
  const model = t.match(/\b(activa|jupiter|access|dio|splendor|shine|platina|pulsar|apache|swift|alto|wagonr|wagon r|baleno|creta|innova|nexon|punch|brezza|bolero|scorpio|thar|xuv\d*)\b/i);
  if (model) out.vehicleModel = model[1].replace(/\b\w/g, c => c.toUpperCase());

  if (!out.vehicleType) missing.push('vehicle type (bike / car / auto / goods)');
  if (out.vehicleType && !out.slab) missing.push(out.vehicleType === 'commercial' ? 'GVW in kg' : 'engine CC (e.g. 125cc)');
  if (!out.regDate) missing.push('registration year (e.g. 2021)');
  if (out.policyType !== 'thirdParty' && !out.idv) missing.push('IDV (e.g. idv 60000)');
  if (out.vehicleType === 'pccv' && !out.slab) missing.splice(missing.indexOf('engine CC (e.g. 125cc)'), 1, 'PCCV class — auto / taxi / bus (send from the calculator app for now)');

  return { input: out, missing };
}

module.exports = { parseQuote, isQuoteRequest, parseAmount, slabForCC, slabForGVW };
