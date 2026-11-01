'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const P = require('./policy.cjs');
const { planPremerge, selectSuites } = require('./premerge-plan.cjs');
const manifest = P.readManifest(__dirname + '/suites.json');
const orb = manifest.suites.find(s => s.key === 'resurrection-orb');
const pox = manifest.suites.find(s => s.key === 'poxwalkers');
const pr = (number, title, issue, extra = {}) => ({ number, title, body: 'Fixes #' + issue, state: 'open',
  user: { login: 'Copilot' }, base: { ref: manifest.base }, ...extra });
const open = [pr(410, 'Implement Resurrection Orb', 408), pr(407, 'Implement Poxwalkers custom card', 406)];

test('a suite-only PR runs just the suites it adds or changes', () => {
  assert.deepEqual(selectSuites(manifest, [orb.source]).map(s => s.key), ['resurrection-orb']);
  assert.deepEqual(selectSuites(manifest, ['certification/suites/poxwalkers.json']).map(s => s.key), ['poxwalkers']);
  assert.deepEqual(planPremerge({ manifest, changedFiles: [pox.source], openPrs: open }).include,
    [{ key: 'poxwalkers', pr: 407, issue: 406, title: 'Implement Poxwalkers custom card' }]);
});

test('shared infrastructure or manifest changes re-run every suite with an open card PR', () => {
  for (const f of ['certification/policy.cjs', 'certification/suites.json', '.github/workflows/arcana-card-certification-premerge.yml']) {
    const r = planPremerge({ manifest, changedFiles: [f, orb.source], openPrs: open });
    assert.deepEqual(r.include.map(i => i.key).sort(), ['poxwalkers', 'resurrection-orb']);
    assert.ok(r.missing.length === manifest.suites.length - 2);
  }
});

test('only an open Copilot PR on the base, naming the card and fixing its unit, is a candidate', () => {
  const bad = [pr(1, 'Implement Resurrection Orb', 408, { user: { login: 'AARMOU' } }),
    pr(2, 'Implement Resurrection Orb', 408, { base: { ref: 'main' } }),
    pr(3, 'Implement Resurrection Orbit', 408), pr(4, 'Implement Resurrection Orb', 999),
    pr(5, 'Implement Resurrection Orb', 408, { state: 'closed' })];
  const r = planPremerge({ manifest, changedFiles: [orb.source], openPrs: bad });
  assert.deepEqual(r, { include: [], missing: ['resurrection-orb'], blocking: ['resurrection-orb'] });
});

test('a changed suite without a runnable candidate blocks premerge; unchanged ones only report', () => {
  // All changed suites missing.
  assert.deepEqual(planPremerge({ manifest, changedFiles: [orb.source], openPrs: [] }).blocking, ['resurrection-orb']);
  assert.deepEqual(planPremerge({ manifest, changedFiles: ['certification/suites/poxwalkers.json'], openPrs: [] }).blocking, ['poxwalkers']);
  // Partially missing: one changed suite runs, the other cannot.
  const partial = planPremerge({ manifest, changedFiles: [orb.source, pox.source], openPrs: [open[0]] });
  assert.deepEqual(partial.include.map(i => i.key), ['resurrection-orb']);
  assert.deepEqual(partial.blocking, ['poxwalkers']);
  // Infrastructure-only change: retired suites are skipped, nothing blocks.
  const infra = planPremerge({ manifest, changedFiles: ['certification/policy.cjs'], openPrs: [] });
  assert.deepEqual(infra.include, []);
  assert.deepEqual(infra.blocking, []);
});

test('a build-target suite (integrated card) runs on the build branch only when the PR adds or changes it', () => {
  const build = { key: 'urzas-saga', name: "Urza's Saga", issue: 9001, source: 'certification/UrzasSagaIndependentCertificationTest.kt',
    testClass: 'com.arcanatable.customcards.UrzasSagaIndependentCertificationTest', tests: 5, target: 'build' };
  const m = P.validateManifest({ ...manifest, suites: [...manifest.suites, build] });
  const own = planPremerge({ manifest: m, changedFiles: [build.source, 'certification/suites/urzas-saga.json'], openPrs: [] });
  assert.deepEqual(own.include, [{ key: 'urzas-saga', pr: 0, issue: 9001, title: 'integrated card on the build branch', target: 'build' }]);
  assert.deepEqual(own.blocking, [], 'no card PR is needed');
  const infra = planPremerge({ manifest: m, changedFiles: ['certification/policy.cjs'], openPrs: open });
  assert.ok(!infra.include.some(i => i.key === 'urzas-saga'), 'infrastructure changes do not re-run every integrated-card suite');
  assert.ok(!infra.missing.includes('urzas-saga'));
  assert.throws(() => P.validateManifest({ ...manifest, suites: [...manifest.suites, { ...build, target: 'elsewhere' }] }));
});
