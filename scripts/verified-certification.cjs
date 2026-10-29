'use strict';
// Trusted Github Actions check-run identity, verified against the live exact-SHA
// Resurrection Orb check before first rollout. Not a generic "contains certif" gate.
const NAME = 'Arcana independent certification';
const APP_ID = 15368; // GitHub Actions (github-actions), as shown by GitHub REST API.
const APP_SLUG = 'github-actions';
const PASS_SUFFIX = ': independent rules scenarios passed';
function isTrustedCheck(run, head) {
  return !!run && typeof head === 'string' && /^[a-f0-9]{40}$/.test(head) &&
    run.head_sha === head && run.name === NAME &&
    run.app?.id === APP_ID && run.app?.slug === APP_SLUG &&
    typeof run.output?.title === 'string' && run.output.title.endsWith(PASS_SUFFIX) &&
    typeof run.output?.summary === 'string' &&
    run.output.summary.includes('trusted-main JUnit scenarios passed');
}
function certification(checkRuns, head) {
  const runs = Array.isArray(checkRuns) ? checkRuns.filter(r => isTrustedCheck(r,head)) : [];
  return !runs.length ? 'not-run' : runs.some(r => r.status !== 'completed') ? 'running'
    : runs.every(r => r.conclusion === 'success') ? 'certified' : 'failed';
}
module.exports={NAME,APP_ID,APP_SLUG,isTrustedCheck,certification};
