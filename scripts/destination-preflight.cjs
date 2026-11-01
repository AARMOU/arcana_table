'use strict';
// Destination preflight: run a certified card's destination gates on the CURRENT integration target BEFORE the owner
// is asked to Verify it, and stop re-running approved cards that are blocked by the target's engine itself.
//
// 2026-10-31: #662 Phyrexian Triniform was certified on the upgraded engine (int-base), approved by the owner, then
// blocked at integration because the B140 target's engine has no `encore`. #690 Tangleweave Armor was blocked the same
// way (its suite needs Koma and Thrasios, which B140's engine lacks). Each new B140 commit re-ran both, for nothing.
// Evidence is bound to the card head SHA, the target SHA and the target's engine fingerprint (its four Argentum JARs),
// so a new card commit or a new engine re-checks, and a card-only target commit does not.
// Read-only toward the target branch: this never integrates, merges, approves or spends.
const crypto = require('node:crypto');
const I = require('./card-integration.cjs');

const MARKER = '<!-- arcana-destination-preflight-v1 -->';
const SHA = /^[a-f0-9]{40}$/;
const JARS = ['argentum-ai.jar', 'argentum-mtg-sdk-tooling.jar', 'argentum-mtg-sdk.jar', 'argentum-rules-engine.jar'];
const hash = v => crypto.createHash('sha256').update(v).digest('hex');
// Only these block reasons are properties of the target's ENGINE; anything else keeps today's retry behaviour.
const ENGINE_BOUND = [/(^|\n)export: /, /Unresolved reference/, /Card not found in registry/];
const engineBound = reason => ENGINE_BOUND.some(r => r.test(String(reason || '')));
// Reasons a certified card is not yet integrable that preflight deliberately ignores: the owner hasn't decided yet.
const PRE_VERIFY = new Set(['no owner approval on the current commit', 'the PR is still a draft']);

// The target's engine identity at a commit: git blob SHAs of the four runtime JARs. One API call.
async function engineFingerprint({ github, owner, repo, ref }) {
  const dir = (await github.rest.repos.getContent({ owner, repo, path: 'android/app/libs', ref })).data;
  const blobs = Object.fromEntries((Array.isArray(dir) ? dir : []).filter(f => JARS.includes(f.name)).map(f => [f.name, f.sha]));
  if (JARS.some(j => !/^[a-f0-9]{40}$/.test(blobs[j] || ''))) throw Error('engine JARs not found at ' + ref);
  return hash(JSON.stringify(JARS.map(j => [j, blobs[j]])));
}

function parse(comment) {
  if (comment.user?.id !== 41898282 || comment.user?.login !== 'github-actions[bot]') return null;
  const body = String(comment.body || '');
  if (!body.startsWith(MARKER + '\n```json\n')) return null;
  try {
    const r = JSON.parse(body.split('\n```json\n')[1].split('\n```')[0]);
    if (r.schema !== 1 || !Number.isSafeInteger(r.pr) || !SHA.test(r.sourceSha) || !SHA.test(r.baseSha) ||
        !/^[a-f0-9]{64}$/.test(r.engineFingerprint || '') || !['passed', 'blocked'].includes(r.status) ||
        !/^https:\/\/github\.com\/AARMOU\/arcana_table\/actions\/runs\/\d+$/.test(r.runUrl)) return null;
    return r;
  } catch { return null; }
}
// Newest preflight for this exact card head on this exact engine, or null.
const latestFor = (comments, sourceSha, fingerprint) =>
  (comments || []).map(parse).filter(r => r && r.sourceSha === sourceSha && r.engineFingerprint === fingerprint).at(-1) || null;

async function preflightFor({ github, owner, repo, pr, fingerprint }) {
  const comments = await github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: pr.number, per_page: 100 });
  return latestFor(comments, pr.head.sha, fingerprint);
}

// Pick ONE certified, not-yet-approved card PR with no preflight on (head, current engine). Same eligibility as
// integration, minus "owner approval" and "draft", which are exactly what preflight comes before.
async function planPreflight({ github, owner, repo, cfg, controlSha }) {
  if (!cfg.policy.enabled || !SHA.test(controlSha)) return null;
  const baseSha = (await github.rest.git.getRef({ owner, repo, ref: 'heads/' + cfg.policy.target })).data.object.sha;
  const fingerprint = await engineFingerprint({ github, owner, repo, ref: baseSha });
  const prs = await github.paginate(github.rest.pulls.list, { owner, repo, state: 'open', per_page: 100 });
  for (const pr of prs.sort((a, b) => a.number - b.number)) {
    if (!['copilot', 'copilot-swe-agent[bot]'].includes(String(pr.user?.login).toLowerCase())) continue;
    const snap = await I.snapshot(github, owner, repo, pr.number);
    const { suite, reasons } = I.integrationBlockers(cfg, snap);
    if (!suite || reasons.some(r => !PRE_VERIFY.has(r))) continue;
    if (I.ownerDecision(snap.reviews, snap.pr.head.sha, cfg.policy)) continue;  // approved: integration's job
    if (await preflightFor({ github, owner, repo, pr: snap.pr, fingerprint })) continue;
    return { schema: 1, pr: pr.number, sourceSha: snap.pr.head.sha, sourceBase: snap.pr.base.sha, sourceBaseRef: snap.pr.base.ref,
      baseSha, target: cfg.policy.target, controlSha, policyHash: cfg.policyHash, reviewId: null, preflight: true,
      engineFingerprint: fingerprint, suite, paths: I.paths(suite), sourceFiles: snap.files.map(f => f.filename) };
  }
  return null;
}

function receiptBody(r) {
  return [MARKER, '```json', JSON.stringify(r, null, 2), '```',
    r.status === 'passed'
      ? `**Destination preflight passed** on the current build target \`${r.baseSha.slice(0, 8)}\`. Ready for owner Verify. Nothing was integrated.`
      : `**Destination preflight blocked** on the current build target \`${r.baseSha.slice(0, 8)}\`: this card would not integrate if approved, so it is held back from the owner's Verify list. ` +
        (engineBound(r.reason) ? 'The cause is the target\'s engine; this re-checks automatically when the target engine changes.' : 'Re-checked when the card changes.') +
        '\n\n' + String(r.reason || '').slice(0, 1500)].join('\n');
}

async function publish({ github, owner, repo, plan, report, runUrl }) {
  I.validateOutcome(plan, report);
  const reason = report.status === 'passed' ? null
    : report.gates.filter(g => !g.ok).map(g => g.name + ': ' + String(g.detail || '').replace(/@/g, '＠')).join('\n').slice(0, 3000) || report.reason || 'blocked';
  // The PR may have moved while tests ran; a stale result is not posted.
  const pr = (await github.rest.pulls.get({ owner, repo, pull_number: plan.pr })).data;
  if (pr.head.sha !== plan.sourceSha || pr.state !== 'open') return { state: 'stale', pr: plan.pr };
  const r = { schema: 1, pr: plan.pr, sourceSha: plan.sourceSha, baseSha: plan.baseSha, target: plan.target,
    engineFingerprint: plan.engineFingerprint, policyHash: plan.policyHash, status: report.status, reason, runUrl, at: new Date().toISOString() };
  await github.rest.issues.createComment({ owner, repo, issue_number: plan.pr, body: receiptBody(r) });
  return { state: 'published', ...r };
}

// Integration side: an approved card blocked by the target's ENGINE is not re-run on a new target commit unless the
// engine itself changed. Non-engine blocks keep the existing behaviour (re-run when the target moves).
async function engineUnchangedSince({ github, owner, repo, receipt, baseSha }) {
  if (receipt?.status !== 'blocked' || !engineBound(receipt.reason) || !SHA.test(receipt.baseSha || '') || receipt.baseSha === baseSha) return false;
  try {
    return await engineFingerprint({ github, owner, repo, ref: receipt.baseSha }) === await engineFingerprint({ github, owner, repo, ref: baseSha });
  } catch { return false; }  // unknown: fall back to re-running, never to silence
}

module.exports = { MARKER, JARS, engineBound, engineFingerprint, parse, latestFor, preflightFor, planPreflight, receiptBody, publish, engineUnchangedSince, PRE_VERIFY };
