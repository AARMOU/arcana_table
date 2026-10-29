'use strict';
// Builds the certification premerge matrix from the manifest instead of a hand-edited list,
// so a suite PR only adds its suite and test files. Runs the suites a PR adds or changes;
// a change to shared certification infrastructure re-runs every suite that has an open card PR.
const P = require('./policy.cjs');

const COPILOT = new Set(['copilot', 'copilot-swe-agent[bot]']);

function selectSuites(manifest, changedFiles) {
  const changed = new Set(changedFiles);
  const suiteOwned = f => /^certification\/suites\/[a-z0-9-]+\.json$/.test(f) ||
    /^certification\/[A-Z][A-Za-z0-9]*IndependentCertificationTest\.kt$/.test(f);
  if ([...changed].some(f => !suiteOwned(f))) return manifest.suites;
  return manifest.suites.filter(s => changed.has(s.source) || changed.has(`certification/suites/${s.key}.json`));
}

function cardPr(manifest, suite, openPrs) {
  return openPrs.find(p => COPILOT.has(String(p.user?.login || '').toLowerCase()) &&
    p.state === 'open' && p.base?.ref === manifest.base &&
    P.issueFromBody(p.body) === suite.issue && P.titleNames(p.title, suite.name)) || null;
}

// Suites whose own registration or test source the PR adds or changes.
function changedSuites(manifest, changedFiles) {
  const changed = new Set(changedFiles);
  return manifest.suites.filter(s => changed.has(s.source) || changed.has(`certification/suites/${s.key}.json`));
}

// `blocking` lists changed suites with no runnable candidate: premerge must fail rather than
// pass without executing them. Unchanged suites without an open card PR are only reported.
function planPremerge({ manifest, changedFiles, openPrs }) {
  const include = [], missing = [];
  for (const s of selectSuites(manifest, changedFiles)) {
    const pr = cardPr(manifest, s, openPrs);
    if (pr) include.push({ key: s.key, pr: pr.number, issue: s.issue, title: pr.title });
    else missing.push(s.key);
  }
  const blocking = changedSuites(manifest, changedFiles).map(s => s.key).filter(k => missing.includes(k));
  return { include, missing, blocking };
}

module.exports = { selectSuites, changedSuites, cardPr, planPremerge };
