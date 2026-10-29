'use strict';
// Trusted-main-only certification policy. Never load this from a Copilot branch.
// Each suite is reviewed once in main; no per-card workflow changes are needed.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const SHA = /^[0-9a-f]{40}$/;
const KEY = /^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/;
const TEST_FILE = /^certification\/[A-Z][A-Za-z0-9]*IndependentCertificationTest\.kt$/;
const TEST_CLASS = /^com\.arcanatable\.customcards\.[A-Z][A-Za-z0-9]*IndependentCertificationTest$/;
const CHECK_NAME = 'Arcana independent certification';

function validateManifest(input) {
  if (!input || input.schema !== 1 || input.base !== 'gpt/arcana-integration-next' ||
      !Array.isArray(input.requiredChecks) ||
      JSON.stringify(input.requiredChecks) !== JSON.stringify(['toolkit','scaffold','cards-and-android']) ||
      !Array.isArray(input.suites) || input.suites.length < 1 || input.suites.length > 250)
    throw Error('Invalid independent certification manifest');
  const keys = new Set(), names = new Set(), issues = new Set();
  for (const s of input.suites) {
    if (!s || typeof s.key !== 'string' || !KEY.test(s.key) ||
        typeof s.name !== 'string' || s.name.length < 3 || s.name.length > 120 ||
        !Number.isSafeInteger(s.issue) || s.issue <= 0 ||
        typeof s.source !== 'string' || !TEST_FILE.test(s.source) ||
        typeof s.testClass !== 'string' || !TEST_CLASS.test(s.testClass) ||
        !Number.isSafeInteger(s.tests) || s.tests < 3 || s.tests > 100 ||
        !s.testClass.endsWith(s.source.split('/').pop().replace('.kt','')) ||
        keys.has(s.key) || names.has(s.name) || issues.has(s.issue))
      throw Error('Invalid or duplicate certification suite: ' + String(s?.key));
    keys.add(s.key); names.add(s.name); issues.add(s.issue);
  }
  return input;
}
// Suites may also be registered one per file in suites/<key>.json beside the manifest, so
// parallel suite PRs add files instead of editing one shared array (no merge conflicts).
function manifestFiles(filename) {
  const dir = path.join(path.dirname(filename), 'suites');
  const extra = fs.existsSync(dir) ? fs.readdirSync(dir).filter(f => f.endsWith('.json')).sort() : [];
  return [filename, ...extra.map(f => path.join(dir, f))];
}
function readManifest(filename) {
  const [base, ...extra] = manifestFiles(filename);
  const data = JSON.parse(fs.readFileSync(base, 'utf8'));
  for (const f of extra) {
    const s = JSON.parse(fs.readFileSync(f, 'utf8'));
    if (path.basename(f) !== String(s?.key) + '.json') throw Error('Suite file name must equal its key: ' + path.basename(f));
    if (Array.isArray(data?.suites)) data.suites.push(s);
  }
  return validateManifest(data);
}
const issueFromBody = body => {
  const match = String(body || '').match(/(?:Fixes|Closes|Resolves|Unit:)\s*#(\d+)\b/i);
  return match ? Number(match[1]) : null;
};
function currentHeadGreen(checks, sha, required) {
  if (!Array.isArray(checks)) return false;
  return required.every(name => checks.some(c => c.name === name && c.head_sha === sha &&
    c.status === 'completed' && c.conclusion === 'success'));
}
// Copilot titles often append detail ("Add X custom card", "Implement X’s trigger").
// The exact card binding comes from the trusted queue issue below; the title must
// still name that card as a whole word, so "Add Xylophone" never matches card "X".
function titleNames(title, name) {
  const t = String(title || '');
  return ['Implement ', 'Add '].some(prefix => t.startsWith(prefix + name) &&
    /^(?:$|\s|[’']s(?:$|\s))/.test(t.slice(prefix.length + name.length)));
}
// Does not trust agent-authored test names, issue claims, status, or filenames.
function candidate(manifest, {pr, issue, sha, checks, repository = 'AARMOU/arcana_table'}) {
  if (!SHA.test(String(sha)) || !pr || pr.state !== 'open' ||
      pr.head?.sha !== sha || pr.head?.repo?.full_name !== repository ||
      pr.base?.ref !== manifest.base || pr.base?.repo?.full_name !== repository ||
      !['copilot','copilot-swe-agent[bot]'].includes(String(pr.user?.login||'').toLowerCase()))
    return null;
  const issueNo = issueFromBody(pr.body);
  const suite = manifest.suites.find(s => s.issue === issueNo &&
    titleNames(pr.title, s.name));
  if (!suite || !issue || issue.number !== suite.issue || issue.state !== 'open' ||
      issue.title !== '[Arcana Queue] ' + suite.name + ' — Cardkit SUPPORTED, unclaimed' ||
      !String(issue.body||'').includes('<!-- arcana-auto-queue-v1 -->') ||
      !String(issue.body||'').includes('source: trusted-main-queue-feeder') ||
      !String(issue.body||'').includes('- ' + suite.name) ||
      !currentHeadGreen(checks,sha,manifest.requiredChecks))
    return null;
  return suite;
}
function workflowEventEligible(run, repository) {
  return run?.event === 'pull_request' && run.conclusion === 'success' &&
    SHA.test(String(run.head_sha)) && run.head_repository?.full_name === repository &&
    ['Arcana Cardkit','Arcana Development CI'].includes(run.name);
}
function certOutput(suite, ok) {
  return {
    title: suite.name + (ok ? ': independent rules scenarios passed' : ': certification blocked'),
    summary: ok
      ? suite.tests + ' trusted-main JUnit scenarios passed against the exact current Copilot PR commit; Cardkit and Android prerequisite checks green. Card-specific evidence only; no integration, APK or release approval.'
      : 'Independent tests failed or current-head prerequisites are not green. No owner approval is permitted.',
  };
}
module.exports = { readManifest,manifestFiles,titleNames,validateManifest,candidate,currentHeadGreen,workflowEventEligible,issueFromBody,certOutput,CHECK_NAME };
