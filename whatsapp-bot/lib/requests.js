/**
 * "Pls provide policy copy" handling.
 *
 * A payment screenshot (OCR text) carries a Proposal Number and an Amount.
 * We look the proposal up among the policies we know (merged/policies.jsonl:
 * scanned from Gmail, or received by mail-watch), check the amount against
 * the premium, and send the policy PDF to the chat that asked. If the policy
 * is not here yet, the request is parked and fulfilled when it arrives.
 */
const fs = require('fs');
const path = require('path');
const org = require('./organise');

const POLICIES = () => path.join(org.MERGED_ROOT, 'policies.jsonl');
const PENDING = () => path.join(org.MERGED_ROOT, 'policy-requests.json');

const normNo = s => String(s || '').toUpperCase().replace(/[^A-Z0-9]/g, '');

/** Parse a payment-confirmation screenshot's OCR text. */
function parsePayment(text) {
  const t = String(text || '').replace(/\s+/g, ' ');
  const out = {};
  let m;
  if ((m = t.match(/Proposal\s*(?:No|Number|Num)\.?\s*[:\-]?\s*([A-Z]{0,2}\s?\/?\s?\d[\d\s\/\-]{8,}\d)/i))) out.proposalNo = m[1].replace(/\s+/g, '').toUpperCase();
  if ((m = t.match(/(?:Amount|Amt|Total|Paid)\s*(?:Paid)?\s*[:\-]?\s*(?:Rs\.?|₹|INR|Z)?\s*([\d,]{3,}(?:\.\d{1,2})?)/i))) out.amount = parseFloat(m[1].replace(/,/g, ''));
  if ((m = t.match(/Transaction\s*(?:ID|No)\.?\s*[:\-]?\s*([A-Z0-9]{8,})/i))) out.txnId = m[1];
  if ((m = t.match(/(?:Date of Purchase|Date)\s*[:\-]?\s*(\d{1,2}[\-\/ ][A-Za-z]{3}[\-\/ ]\d{4}|\d{1,2}[\/\-]\d{1,2}[\/\-]\d{4})/i))) out.date = m[1];
  const isPayment = !!(out.proposalNo && (out.amount || out.txnId)) || /payment (successful|received|confirmation)|transaction id/i.test(t) && !!out.proposalNo;
  return { ...out, isPayment };
}

function policies() {
  try { return fs.readFileSync(POLICIES(), 'utf8').split('\n').filter(Boolean).map(l => { try { return JSON.parse(l); } catch { return null; } }).filter(Boolean); }
  catch { return []; }
}

/** Find the policy for a proposal number: by proposalNo field, else by the numeric tail (office/class/year/serial). */
function findPolicy(proposalNo) {
  const want = normNo(proposalNo);
  const tail = want.replace(/^[A-Z]+/, '');
  const all = policies().filter(p => p.file && fs.existsSync(p.file));
  let hit = all.find(p => p.proposalNo && normNo(p.proposalNo) === want);
  if (!hit && tail.length >= 10) hit = all.find(p => p.policyNo && normNo(p.policyNo) === tail);
  return hit || null;
}

function loadPending() { try { return JSON.parse(fs.readFileSync(PENDING(), 'utf8')); } catch { return []; } }
function savePending(list) { try { fs.mkdirSync(org.MERGED_ROOT, { recursive: true }); fs.writeFileSync(PENDING(), JSON.stringify(list, null, 1)); } catch {} }
function park(req) { const list = loadPending().filter(r => normNo(r.proposalNo) !== normNo(req.proposalNo) || r.chat !== req.chat); list.push({ ...req, parkedAt: new Date().toISOString() }); savePending(list); }
function takeMatching(policy) {
  const list = loadPending();
  const keep = [], hit = [];
  for (const r of list) {
    const want = normNo(r.proposalNo), tail = want.replace(/^[A-Z]+/, '');
    if ((policy.proposalNo && normNo(policy.proposalNo) === want) || (policy.policyNo && normNo(policy.policyNo) === tail)) hit.push(r); else keep.push(r);
  }
  if (hit.length) savePending(keep);
  return hit;
}

/** Amount check: paid vs premium, tolerance ₹2 or 0.5 %. */
function amountOk(paid, premium) {
  if (!paid || !premium) return null;                   // cannot check
  return Math.abs(paid - premium) <= Math.max(2, premium * 0.001);
}

/** Record a policy PDF (from mail-watch / Downloads / scan) so requests can find it. */
function recordPolicy(file, info, extra = {}) {
  const all = policies();
  if (all.some(p => p.file === file || (info.policyNo && p.policyNo === info.policyNo && fs.existsSync(p.file)))) return false;
  fs.mkdirSync(org.MERGED_ROOT, { recursive: true });
  fs.appendFileSync(POLICIES(), JSON.stringify({ mailDate: new Date().toISOString().slice(0, 10), file, ...info, ...extra }) + '\n');
  return true;
}

module.exports = { parsePayment, findPolicy, park, takeMatching, loadPending, amountOk, policies, normNo, recordPolicy };
