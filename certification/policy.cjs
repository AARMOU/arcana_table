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
      !Array.isArray(input.suites) || input.suites.length < 1 || input.suites.length > 1000)
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
        // `target: "build"`: an integrated card, certified against the integration build branch (integration/policy.json).
        (s.target !== undefined && s.target !== 'build') ||
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
  // Copilot often types typographic apostrophes ("Sun’s") where Oracle names use ASCII ones.
  const plain = v => String(v || '').replace(/[\u2018\u2019]/g, "'");
  const t = plain(title);
  name = plain(name);
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
      !(feederUnit(issue, suite.name) || ownerUnit(issue, suite.name)) ||
      !String(issue.body||'').includes('- ' + suite.name) ||
      !currentHeadGreen(checks,sha,manifest.requiredChecks))
    return null;
  return suite;
}
// A queue unit emitted by the trusted main feeder: exact title and feeder markers.
const feederUnit = (issue, name) => issue.title === '[Arcana Queue] ' + name + ' — Cardkit SUPPORTED, unclaimed' &&
  String(issue.body||'').includes('<!-- arcana-auto-queue-v1 -->') &&
  String(issue.body||'').includes('source: trusted-main-queue-feeder');
// A queue unit the owner opened by hand (the dispatcher already trusts these). Owner account and
// association only; the title must name this card as a whole name, never a longer one.
const OWNER_LOGIN = 'AARMOU';
const ownerUnit = (issue, name) => issue.user?.login === OWNER_LOGIN && issue.author_association === 'OWNER' &&
  String(issue.title||'').startsWith('[Arcana Queue] ' + name) &&
  /^(?:$|\s|—|-|:)/.test(String(issue.title).slice(('[Arcana Queue] ' + name).length));
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
// A successful exact-head independent certification may expose the existing Copilot PR
// to owner review, but it never approves, merges, dispatches or integrates that PR.
async function markReadyAfterCertification({github, owner, repo, number, sha, certified}) {
  if (certified !== true || !SHA.test(String(sha))) return false;
  // Read live PR state immediately before the GraphQL mutation: do not trust the
  // earlier certification gate's PR snapshot or an agent-supplied draft flag.
  const live = (await github.rest.pulls.get({owner, repo, pull_number:number})).data;
  if (live?.state !== 'open' || live.draft !== true || live.head?.sha !== sha ||
      live.head?.repo?.full_name !== owner + '/' + repo ||
      live.base?.repo?.full_name !== owner + '/' + repo ||
      live.base?.ref !== 'gpt/arcana-integration-next' ||
      !['copilot','copilot-swe-agent[bot]'].includes(String(live.user?.login || '').toLowerCase()) ||
      typeof live.node_id !== 'string' || !live.node_id) return false;
  const result = await github.graphql(
    'mutation MarkReadyAfterCertification($id: ID!) { markPullRequestReadyForReview(input: {pullRequestId: $id}) { pullRequest { isDraft } } }',
    {id: live.node_id}
  );
  if (result?.markPullRequestReadyForReview?.pullRequest?.isDraft !== false)
    throw Error('GitHub did not confirm ready-for-review transition');
  return true;
}

module.exports = { readManifest,manifestFiles,titleNames,validateManifest,candidate,currentHeadGreen,workflowEventEligible,issueFromBody,certOutput,markReadyAfterCertification,CHECK_NAME };
