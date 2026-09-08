/**
 * Groups / numbers to ignore, from ignore-list.txt (see the example file).
 * Re-read on every use so edits apply without a restart.
 */
const fs = require('fs');
const path = require('path');
const DIR = path.resolve(__dirname, '..');
const norm = s => String(s || '').replace(/^﻿/, '').replace(/[‐-―−]/g, '-').replace(/\s+/g, ' ').trim().toLowerCase();

function findFile() {
  try {
    const names = fs.readdirSync(DIR).filter(f => /^ignore-list(\.txt)*$/i.test(f) && !/example/i.test(f));
    if (names.length) return path.join(DIR, names.sort((a, b) => a.length - b.length)[0]);
  } catch {}
  return null;
}

function load() {
  const groups = new Set(), numbers = new Set();
  const file = findFile();
  if (!file) return { groups, numbers, file: null };
  for (const raw of fs.readFileSync(file, 'utf8').replace(/^﻿/, '').split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const digits = line.replace(/[\s\-+()]/g, '');
    if (/^\d{8,15}$/.test(digits)) numbers.add(digits); else groups.add(norm(line));
  }
  return { groups, numbers, file };
}

/** @returns {string|null} reason to ignore, or null */
function isIgnored({ group, sender }) {
  const { groups, numbers } = load();
  if (group && groups.has(norm(group))) return `group "${group}" is in ignore-list`;
  const n = (sender || '').replace(/\D/g, '');
  if (n && numbers.has(n)) return `number ${n} is in ignore-list`;
  return null;
}

module.exports = { isIgnored, load };
