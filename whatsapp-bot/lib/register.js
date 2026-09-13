/**
 * Intake register: one row per merged set.
 *  - register.jsonl (the source of truth, appended)
 *  - register/Intake_<YYYY-MM>.xlsx rebuilt after every set (one sheet per month file)
 * Both live in MERGED_ROOT (the folder with the merged PDFs).
 */
const fs = require('fs');
const path = require('path');
const org = require('./organise');

const JSONL = () => path.join(org.MERGED_ROOT, 'register.jsonl');
const XDIR = () => path.join(org.MERGED_ROOT, 'register');

function readAll() {
  try { return fs.readFileSync(JSONL(), 'utf8').split('\n').filter(Boolean).map(l => { try { return JSON.parse(l); } catch { return null; } }).filter(Boolean); }
  catch { return []; }
}

async function add(row) {
  fs.mkdirSync(org.MERGED_ROOT, { recursive: true });
  fs.appendFileSync(JSONL(), JSON.stringify(row) + '\n');
  try { await rebuildMonth(row.date.slice(0, 7)); } catch (e) { console.error('[register] Excel not updated: ' + e.message + (/(EBUSY|EPERM)/.test(e.message) ? ' (is the file open in Excel? it will be rewritten with the next set)' : '')); }
}

async function rebuildMonth(ym) {
  const ExcelJS = require('exceljs');
  const rows = readAll().filter(r => r.date.startsWith(ym));
  const wb = new ExcelJS.Workbook();
  const ws = wb.addWorksheet('Intake ' + ym, { views: [{ state: 'frozen', ySplit: 1 }] });
  ws.columns = [
    { header: 'Date', key: 'date', width: 12 },
    { header: 'Time', key: 'time', width: 8 },
    { header: 'Vehicle', key: 'vehicle', width: 16 },
    { header: 'Sender', key: 'sender', width: 24 },
    { header: 'Number', key: 'number', width: 16 },
    { header: 'Group', key: 'group', width: 34 },
    { header: 'Photos', key: 'photos', width: 8 },
    { header: 'PDFs', key: 'pdfs', width: 6 },
    { header: 'Pages', key: 'pages', width: 7 },
    { header: 'File', key: 'file', width: 40 },
    { header: 'Emailed to', key: 'emailed', width: 36 },
    { header: 'Folder', key: 'folder', width: 60 },
  ];
  ws.getRow(1).font = { bold: true };
  ws.autoFilter = { from: 'A1', to: 'L1' };
  for (const r of rows) ws.addRow({ ...r, file: path.basename(r.file || ''), folder: path.dirname(r.file || ''), emailed: (r.emailed || []).join(', ') });
  ws.getColumn('folder').eachCell((c, i) => { if (i > 1 && c.value) c.value = { text: c.value, hyperlink: 'file:///' + String(c.value).replace(/\\/g, '/') }; });
  fs.mkdirSync(XDIR(), { recursive: true });
  const file = path.join(XDIR(), `Intake_${ym}.xlsx`);
  await wb.xlsx.writeFile(file);
  return file;
}

/** Search rows: by full/partial vehicle number, sender name, number, or date (YYYY-MM-DD). */
function search(q) {
  const s = String(q || '').trim().toUpperCase().replace(/\s+/g, '');
  if (!s) return [];
  return readAll().filter(r => {
    const v = (r.vehicle || '').toUpperCase().replace(/\s+/g, '');
    return (v && (v === s || v.endsWith(s) || v.includes(s)))
      || (r.sender || '').toUpperCase().includes(s)
      || (r.number || '').includes(s.replace(/\D/g, '') || '§')
      || (r.date || '') === s.toLowerCase();
  }).sort((a, b) => (b.date + b.time).localeCompare(a.date + a.time));
}

function onDate(date) { return readAll().filter(r => r.date === date); }

module.exports = { add, readAll, rebuildMonth, search, onDate, JSONL, XDIR };
