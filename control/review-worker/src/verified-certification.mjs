// Independent certification check identity required by the owner decision service.
// Mirror scripts/verified-certification.cjs (both consumers have regression coverage).
export const CERT_NAME = 'Arcana independent certification';
export const CERT_APP_ID = 15368; // GitHub Actions GitHub App
export function isTrustedCertificationCheck(run, head) {
  return !!run && /^[a-f0-9]{40}$/.test(String(head)) &&
    run.head_sha === head && run.name === CERT_NAME &&
    run.app?.slug === 'github-actions' && run.app?.id === CERT_APP_ID &&
    typeof run.output?.title === 'string' &&
    run.output.title.endsWith(': independent rules scenarios passed') &&
    typeof run.output?.summary === 'string' &&
    run.output.summary.includes('trusted-main JUnit scenarios passed');
}
export function certificationStatus(checks, head) {
  const c = Array.isArray(checks) ? checks.filter(r => isTrustedCertificationCheck(r,head)) : [];
  return !c.length ? 'not-run' : c.some(r => r.status !== 'completed') ? 'running'
    : c.every(r => r.conclusion === 'success') ? 'certified' : 'failed';
}
