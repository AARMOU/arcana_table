#!/usr/bin/env node
'use strict';

// Registry evidence only: this does not certify cards or prove APK delivery.
const {execFileSync} = require('node:child_process');
const REGISTRY = 'android/custom-cards/src/main/kotlin/com/arcanatable/customcards/CustomCards.kt';
const dayFormat = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'America/Denver', year: 'numeric', month: '2-digit', day: '2-digit'
});
function denverDay(value) {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) throw new Error('Invalid integration timestamp');
  const parts = Object.fromEntries(dayFormat.formatToParts(date).map(p => [p.type, p.value]));
  return `${parts.year}-${parts.month}-${parts.day}`;
}
function identifiers(source) {
  // Restrict to the registry list, excluding comments (including commented-out Entry calls).
  const clean = source.replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, '');
  const start = clean.indexOf('val entries: List<Entry> = listOf(');
  if (start < 0) throw new Error('Unsupported registry declaration; coverage unavailable');
  const list = clean.slice(start);
  const entries = [...list.matchAll(/\bEntry\s*\(\s*([A-Za-z_][A-Za-z0-9_]*)\s*,/g)].map(m => m[1]);
  if ((list.match(/\bEntry\s*\(/g) || []).length !== entries.length)
    throw new Error('Unsupported registry entry; coverage unavailable');
  return new Set(entries);
}
function history(snapshots, {trackedRef, trackedCommit, trackedCommittedAt, checkedAt, days = 7}) {
  if (!Number.isInteger(days) || days < 1 || days > 31) throw new Error('days must be 1..31');
  const today = denverDay(checkedAt);
  const dates = Array.from({length: days}, (_, i) => {
    const d = new Date(`${today}T12:00:00Z`);
    d.setUTCDate(d.getUTCDate() - days + 1 + i);
    return d.toISOString().slice(0, 10);
  });
  const seen = new Set();
  const receipts = [];
  for (const s of snapshots) {
    const added = [...s.cards].filter(c => !seen.has(c)).sort();
    for (const c of s.cards) seen.add(c);
    const date = denverDay(s.committedAt);
    if (dates.includes(date) && added.length)
      receipts.push({commit: s.commit, committedAt: s.committedAt, denverDate: date, cardIdentifiers: added});
  }
  const throughDate = denverDay(trackedCommittedAt);
  return {schemaVersion: 1, checkedAt, trackedRef, trackedCommit, trackedCommittedAt, source: REGISTRY,
    metric: 'Unique first registrations on the tracked first-parent history; not certification or APK delivery',
    coverage: {status: 'partial', reason: 'Custom registry only; engine-library additions and playable-card validation are not covered',
      history: 'complete-first-parent', timezone: 'America/Denver'},
    days: dates.map(date => ({date, status: date <= throughDate ? 'partial' : 'unavailable',
      observedRegistrations: date <= throughDate ? receipts.filter(r => r.denverDate === date)
        .reduce((n, r) => n + r.cardIdentifiers.length, 0) : null})), receipts};
}
function collect(ref, checkedAt = new Date().toISOString()) {
  const git = (...args) => execFileSync('git', args, {encoding: 'utf8', maxBuffer: 32 * 1024 * 1024}).trim();
  if (git('rev-parse', '--is-shallow-repository') !== 'false')
    throw new Error('Shallow history; full deduplication unavailable');
  const head = git('rev-parse', '--verify', `${ref}^{commit}`);
  const commits = git('log', '--first-parent', '--reverse', '--format=%H', head, '--', REGISTRY).split('\n').filter(Boolean);
  if (!commits.length) throw new Error('No registry history; coverage unavailable');
  const snapshots = commits.map(commit => {
    const committedAt = git('show', '-s', '--format=%cI', commit);
    const exists = git('ls-tree', '--name-only', commit, '--', REGISTRY);
    return {commit, committedAt, cards: exists ? identifiers(git('show', `${commit}:${REGISTRY}`)) : new Set()};
  });
  return history(snapshots, {trackedRef: ref, trackedCommit: head,
    trackedCommittedAt: git('show', '-s', '--format=%cI', head), checkedAt});
}
if (require.main === module) {
  try {
    if (!process.argv[2]) throw new Error('Usage: node tools/card_integration_history.cjs <tracked-ref> [checked-at]');
    process.stdout.write(JSON.stringify(collect(process.argv[2], process.argv[3]), null, 2) + '\n');
  } catch (error) {
    process.stderr.write(`Card integration history unavailable: ${error.message}\n`);
    process.exitCode = 1;
  }
}
module.exports = {denverDay, identifiers, history, collect};
