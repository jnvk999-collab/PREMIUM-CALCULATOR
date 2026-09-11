/**
 * Daily reminders to your own WhatsApp chat, from reminders.txt (re-read every minute, no restart).
 *
 * One reminder per line:   HH:MM[-HH:MM]  [days]  message
 *   10:00-10:15  Mon-Sat  Mark attendance (before 10:15 AM)
 *   14:00-14:15  Mon-Sat  Mark afternoon attendance (before 2:15 PM)
 *   20:00        Sun      Prepare Monday renewals
 * The reminder goes at the first time; if the laptop was off then, it still goes as soon as the bot
 * is up, as long as the end time (default: 30 minutes later) has not passed. Days can be a range
 * (Mon-Fri), a list (Mon,Wed,Fri), "daily" or left out (= every day). Each line fires once a day.
 */
const fs = require('fs');
const path = require('path');
const DIR = path.resolve(__dirname, '..');
const STATE = path.join(DIR, 'merged', 'reminders-sent.json');
const DAYS = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'];

const DEFAULT_FILE =
  '# Daily reminders sent to your own WhatsApp chat. Edit freely, no restart needed.\n' +
  '# Format:  start[-end]  [days]  message      (24-hour clock; days: Mon-Sat, Mon,Wed, daily)\n' +
  '# The reminder is sent at the start time (or as soon as the bot is up, if before the end time).\n' +
  '10:00-10:15  Mon-Sat  ⏰ Mark attendance now - before 10:15 AM\n' +
  '14:00-14:15  Mon-Sat  ⏰ Mark afternoon attendance now - before 2:15 PM\n';

function findFile() {
  try {
    const names = fs.readdirSync(DIR).filter(f => /^reminders(\.txt)*$/i.test(f) && !/example/i.test(f));
    if (names.length) return path.join(DIR, names.sort((a, b) => a.length - b.length)[0]);
  } catch {}
  return null;
}

/** Creates reminders.txt with the attendance reminders if there is none yet. */
function ensureFile() {
  if (findFile()) return false;
  fs.writeFileSync(path.join(DIR, 'reminders.txt'), DEFAULT_FILE);
  return true;
}

function parseDays(s) {
  s = String(s || '').toLowerCase();
  if (!s || s === 'daily' || s === 'all' || s === 'everyday') return new Set([0, 1, 2, 3, 4, 5, 6]);
  const out = new Set();
  for (const part of s.split(',')) {
    const m = part.match(/^([a-z]{3})[a-z]*(?:-([a-z]{3})[a-z]*)?$/);
    if (!m) return null;
    const a = DAYS.indexOf(m[1]), b = m[2] ? DAYS.indexOf(m[2]) : a;
    if (a < 0 || b < 0) return null;
    for (let d = a; ; d = (d + 1) % 7) { out.add(d); if (d === b) break; }
  }
  return out.size ? out : null;
}

const toMin = t => { const m = t.match(/^(\d{1,2})[:.](\d{2})$/); return m ? +m[1] * 60 + +m[2] : null; };

function parseLine(raw, n) {
  const line = raw.replace(/^﻿/, '').trim();
  if (!line || line.startsWith('#')) return null;
  const m = line.match(/^(\d{1,2}[:.]\d{2})(?:\s*-\s*(\d{1,2}[:.]\d{2}))?\s+(.*)$/);
  if (!m) return { error: `line ${n}: must start with a time like 10:00` };
  const start = toMin(m[1]), end = m[2] ? toMin(m[2]) : start + 30;
  if (start == null || end == null) return { error: `line ${n}: bad time` };
  let rest = m[3].trim(), days = parseDays('');
  const dm = rest.match(/^(daily|all|everyday|(?:[a-z]{3}[a-z]*(?:-[a-z]{3}[a-z]*)?)(?:,[a-z]{3}[a-z]*(?:-[a-z]{3}[a-z]*)?)*)\s+(.+)$/i);
  if (dm && parseDays(dm[1])) { days = parseDays(dm[1]); rest = dm[2].trim(); }
  if (!rest) return { error: `line ${n}: no message` };
  return { id: `${n}:${m[1]}:${rest}`, start, end: Math.max(end, start), days, text: rest };
}

function load() {
  const file = findFile();
  const list = [], errors = [];
  if (!file) return { list, errors, file: null };
  fs.readFileSync(file, 'utf8').split(/\r?\n/).forEach((raw, i) => {
    const r = parseLine(raw, i + 1);
    if (!r) return;
    if (r.error) errors.push(r.error); else list.push(r);
  });
  return { list, errors, file };
}

function readState() { try { return JSON.parse(fs.readFileSync(STATE, 'utf8')); } catch { return {}; } }
function writeState(s) { fs.mkdirSync(path.dirname(STATE), { recursive: true }); fs.writeFileSync(STATE, JSON.stringify(s)); }
const dayKey = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/** Reminders that should go out right now (not yet sent today, inside their window). */
function due(now = new Date()) {
  const { list } = load();
  const state = readState();
  const today = dayKey(now), mins = now.getHours() * 60 + now.getMinutes();
  const out = [];
  for (const r of list) {
    if (!r.days.has(now.getDay())) continue;
    if (mins < r.start || mins > r.end) continue;
    if (state[r.id] === today) continue;
    out.push(r);
  }
  return out;
}

function markSent(r, now = new Date()) {
  const state = readState();
  const today = dayKey(now);
  for (const k of Object.keys(state)) if (state[k] !== today) delete state[k];
  state[r.id] = today;
  writeState(state);
}

function describe() {
  const { list, errors, file } = load();
  const fmt = m => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
  const dayText = d => d.size === 7 ? 'daily' : [1, 2, 3, 4, 5, 6, 0].filter(i => d.has(i)).map(i => DAYS[i][0].toUpperCase() + DAYS[i].slice(1)).join(',');
  const lines = list.map(r => `${fmt(r.start)}${r.end !== r.start ? '-' + fmt(r.end) : ''}  ${dayText(r.days)}  ${r.text}`);
  return { file, lines, errors };
}

module.exports = { load, due, markSent, ensureFile, describe, parseLine, parseDays, DEFAULT_FILE, STATE };
