'use strict';
// Trusted control-plane code. Never require candidate code in a write-capable job.
const fs = require('node:fs');
const crypto = require('node:crypto');
const C = require('../certification/policy.cjs');
const V = require('./verified-certification.cjs');
const { isCardCheck } = require('./card-checks.cjs');
const MARKER = '<!-- arcana-card-integration-v1 -->';
const SHA = /^[a-f0-9]{40}$/;
const TARGET = 'gpt/b140-card-request-test-apk-20261028';
const REGISTRY = 'android/custom-cards/src/main/kotlin/com/arcanatable/customcards/CustomCards.kt';
const GENERATED = ['android/app/src/main/assets/argentum/custom-cards.jsonl',
  'android/app/src/main/assets/argentum/custom-cards.index.tsv',
  'docs/agents/generated/pattern-index.json', 'docs/agents/generated/pattern-index.md'];
const GATES = ['engine-pins','export','patterns','cardkit','scenario','independent',
  'custom-and-android','release-safety','scope'];
const hash = value => crypto.createHash('sha256').update(value).digest('hex');

function validatePolicy(p) {
  if (p?.schema !== 1 || typeof p.enabled !== 'boolean' || p.repository !== 'AARMOU/arcana_table' ||
      p.ownerId !== 132252902 || p.target !== TARGET || !SHA.test(p.targetSeed || '') ||
      typeof p.authorization !== 'string' || p.authorization.length < 40 ||
      JSON.stringify(Object.keys(p.engineJars || {}).sort()) !== JSON.stringify([
        'argentum-ai.jar','argentum-mtg-sdk-tooling.jar','argentum-mtg-sdk.jar','argentum-rules-engine.jar']) ||
      Object.values(p.engineJars).some(x => !/^[a-f0-9]{64}$/.test(x)))
    throw Error('Invalid card-only integration policy');
  return p;
}
function config(root = '.') {
  const policyBytes = fs.readFileSync(root + '/integration/policy.json');
  const suitesBytes = Buffer.concat(C.manifestFiles(root + '/certification/suites.json').map(f => fs.readFileSync(f)));
  const manifest = C.readManifest(root + '/certification/suites.json');
  const trustedInputs = [policyBytes,suitesBytes,fs.readFileSync(root+'/scripts/card-integration.cjs'),
    fs.readFileSync(root+'/integration/runner.py'),fs.readFileSync(root+'/certification/runner.py'),
    ...manifest.suites.map(s=>fs.readFileSync(root+'/'+s.source))];
  return {policy:validatePolicy(JSON.parse(policyBytes)), manifest, policyHash:hash(Buffer.concat(trustedInputs))};
}
function paths(suite) {
  const symbol = suite.source.split('/').pop().replace('IndependentCertificationTest.kt','');
  if (!/^[A-Z][A-Za-z0-9]+$/.test(symbol)) throw Error('Unsafe card symbol');
  return {symbol, source:`android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/${symbol}.kt`,
    scenario:`android/custom-cards/src/test/kotlin/com/arcanatable/customcards/${symbol}ScenarioTest.kt`, registry:REGISTRY};
}
function ownerDecision(reviews, sha, policy) {
  const meaningful = reviews.filter(r => r.user?.id === policy.ownerId &&
    String(r.user?.login).toLowerCase() === 'aarmou' &&
    ['APPROVED','CHANGES_REQUESTED','DISMISSED'].includes(r.state))
    .sort((a,b) => Number(a.id)-Number(b.id));
  const last = meaningful.at(-1);
  return last?.state === 'APPROVED' && last.commit_id === sha ? last : null;
}
// Every reason an approved card PR cannot be integrated. sourceEligible() is exactly "no reasons",
// so the explanation and the gate can never disagree.
function integrationBlockers(cfg, snapshot) {
  const {pr,issue,checks,reviews,files} = snapshot, reasons = [];
  const suite = C.candidate(cfg.manifest,{pr,issue,sha:pr.head?.sha,checks,repository:cfg.policy.repository});
  if (!cfg.policy.enabled) reasons.push('integration is disabled by policy');
  if (!suite) reasons.push('no certification match (registered suite, trusted open queue unit, base branch or required checks)');
  if (pr.draft) reasons.push('the PR is still a draft');
  if (!ownerDecision(reviews,pr.head?.sha,cfg.policy)) reasons.push('no owner approval on the current commit');
  if (V.certification(checks,pr.head?.sha) !== 'certified') reasons.push('not independently certified on the current commit');
  // Pending or failing ancillary checks are not silently discarded.
  if (checks.some(c => c.head_sha === pr.head?.sha && isCardCheck(c) &&
    (c.status !== 'completed' || !['success','skipped','neutral'].includes(c.conclusion))))
    reasons.push('card checks are pending or failing');
  if (suite) {
    const p = paths(suite), allowed = [p.source,p.scenario,p.registry,...GENERATED];
    if (!Array.isArray(files)) reasons.push('changed-file list unavailable');
    else {
      const outside = files.filter(f => !allowed.includes(f.filename) || !['added','modified'].includes(f.status));
      if (outside.length) reasons.push('changes files outside the card-only set: ' + outside.map(f => f.filename + (allowed.includes(f.filename) ? ' (' + f.status + ')' : '')).slice(0, 5).join(', '));
      if (files.length < 3 || files.length > allowed.length) reasons.push('unexpected number of changed files (' + files.length + ')');
      const missing = [p.source,p.scenario].filter(name => !files.some(f => f.filename === name && f.status === 'added'));
      if (missing.length) reasons.push('must add ' + missing.join(' and '));
      if (!files.some(f => f.filename === p.registry && f.status === 'modified')) reasons.push('must modify ' + p.registry);
    }
  }
  return {suite, reasons};
}
function sourceEligible(cfg, snapshot) {
  const {suite, reasons} = integrationBlockers(cfg, snapshot);
  return reasons.length ? null : suite;
}
async function snapshot(github, owner, repo, number) {
  const pr = (await github.rest.pulls.get({owner,repo,pull_number:number})).data;
  const unit = C.issueFromBody(pr.body);
  if (!unit) return {pr,issue:null,checks:[],reviews:[],files:[]};
  const [issue,checks,reviews,files] = await Promise.all([
    github.rest.issues.get({owner,repo,issue_number:unit}),
    github.paginate(github.rest.checks.listForRef,{owner,repo,ref:pr.head.sha,per_page:100}),
    github.paginate(github.rest.pulls.listReviews,{owner,repo,pull_number:number,per_page:100}),
    github.paginate(github.rest.pulls.listFiles,{owner,repo,pull_number:number,per_page:100})]);
  return {pr,issue:issue.data,checks,reviews,files};
}
function parseReceipt(comment) {
  if (comment.user?.id !== 41898282 || comment.user?.login !== 'github-actions[bot]') return null;
  const body = String(comment.body || '');
  if (!body.startsWith(MARKER + '\n```json\n')) return null;
  try {
    const r = JSON.parse(body.split('\n```json\n')[1].split('\n```')[0]);
    if (r.schema !== 1 || !Number.isSafeInteger(r.pr) || r.pr <= 0 || !SHA.test(r.sourceSha) ||
        !SHA.test(r.baseSha) || r.target !== TARGET ||
        !['blocked','integrated'].includes(r.status) ||
        !/^https:\/\/github\.com\/AARMOU\/arcana_table\/actions\/runs\/\d+$/.test(r.runUrl) ||
        (r.status === 'integrated' && !SHA.test(r.destinationSha))) return null;
    return {...r,commentUrl:comment.html_url};
  } catch { return null; }
}
async function receiptFor({github,owner,repo,pr,verifyIntegrated=true}) {
  if (!github.rest.issues.listComments || !github.rest.git?.getRef) return null;
  const comments = await github.paginate(github.rest.issues.listComments,{owner,repo,issue_number:pr.number,per_page:100});
  const receipts = comments.map(parseReceipt).filter(r => r?.pr === pr.number && r.sourceSha === pr.head.sha);
  const integrated = receipts.filter(r => r.status === 'integrated').at(-1);
  if (integrated && verifyIntegrated) {
    const ref = (await github.rest.git.getRef({owner,repo,ref:'heads/'+TARGET})).data.object.sha;
    const comparison = (await github.rest.repos.compareCommitsWithBasehead({owner,repo,
      basehead:integrated.destinationSha+'...'+ref})).data;
    if (['ahead','identical'].includes(comparison.status)) return integrated;
  }
  return receipts.filter(r => r.status === 'blocked').at(-1) || null;
}
async function planNext({github,owner,repo,cfg,controlSha,retry=false}) {
  if (!cfg.policy.enabled || !SHA.test(controlSha)) return null;
  const baseSha = (await github.rest.git.getRef({owner,repo,ref:'heads/'+cfg.policy.target})).data.object.sha;
  const ancestor = (await github.rest.repos.compareCommitsWithBasehead({owner,repo,
    basehead:cfg.policy.targetSeed+'...'+baseSha})).data;
  if (!['ahead','identical'].includes(ancestor.status)) throw Error('Android branch no longer descends from authorized seed');
  const prs = await github.paginate(github.rest.pulls.list,{owner,repo,state:'open',per_page:100});
  for (const pr of prs.sort((a,b)=>a.number-b.number)) {
    if (!['copilot','copilot-swe-agent[bot]'].includes(String(pr.user?.login).toLowerCase()) || pr.draft) continue;
    const snap = await snapshot(github,owner,repo,pr.number);
    const suite = sourceEligible(cfg,snap);
    if (!suite) continue;
    const receipt = await receiptFor({github,owner,repo,pr:snap.pr});
    if (receipt?.status === 'integrated') continue;
    if (!retry && receipt?.status === 'blocked' && receipt.baseSha === baseSha && receipt.policyHash === cfg.policyHash) continue;
    return {schema:1,pr:pr.number,sourceSha:snap.pr.head.sha,sourceBase:snap.pr.base.sha,
      sourceBaseRef:snap.pr.base.ref,baseSha,target:cfg.policy.target,controlSha,policyHash:cfg.policyHash,
      reviewId:ownerDecision(snap.reviews,snap.pr.head.sha,cfg.policy).id,
      suite,paths:paths(suite),sourceFiles:snap.files.map(f=>f.filename)};
  }
  return null;
}
function validateOutcome(plan, report) {
  if (report.schema !== 1 || !['passed','blocked'].includes(report.status) ||
      report.planHash !== hash(JSON.stringify(plan)) || !Array.isArray(report.gates) ||
      report.gates.length !== GATES.length || new Set(report.gates.map(g=>g.name)).size !== GATES.length ||
      report.gates.some(g=>!GATES.includes(g.name)||typeof g.ok!=='boolean'))
    throw Error('Missing, malformed or wrong-plan destination evidence');
}
function validateReport(plan, report, files) {
  validateOutcome(plan,report);
  if (report.status !== 'passed' || GATES.some(g => !report.gates.some(x=>x.name===g && x.ok===true)) ||
      report.gates.some(x=>x.ok!==true)) throw Error('Destination validation did not pass every gate');
  const allowed = [plan.paths.source,plan.paths.scenario,REGISTRY,...GENERATED];
  if (!Array.isArray(files) || files.length !== allowed.length ||
      new Set(files.map(f=>f.path)).size !== allowed.length || files.some(f => !allowed.includes(f.path) ||
        f.mode !== '100644' || f.type !== 'blob' || typeof f.content !== 'string' ||
        !f.content.trim() || Buffer.byteLength(f.content) > 2*1024*1024 || report.hashes?.[f.path] !== hash(f.content)))
    throw Error('Invalid, duplicate, out-of-scope or tampered integration files');
  return files;
}
function receiptBody(receipt, reason='') {
  return MARKER+'\n```json\n'+JSON.stringify(receipt,null,2)+'\n```\n'+
    (receipt.status==='integrated' ? '**Integrated into Android source; ready for a separately authorized APK build.** No APK built.' :
      '**Integration blocked.** '+String(reason).replace(/@/g,'＠').slice(0,1400));
}
// Paths whose change on main means the tested plan no longer reflects trusted policy/code.
const CONTROL_PATHS = ['integration/','certification/','scripts/card-integration.cjs',
  'scripts/verified-certification.cjs','scripts/card-checks.cjs','scripts/review-inbox.cjs','scripts/control-status.cjs',
  '.github/workflows/arcana-approved-card-integration.yml'];
// Unrelated owner merges may advance main during long destination tests. Accept only a
// fast-forward whose complete changed-file list avoids every control path; else fail closed.
async function controlUnchanged({github,owner,repo,controlSha}) {
  const live = (await github.rest.git.getRef({owner,repo,ref:'heads/main'})).data.object.sha;
  if (live === controlSha) return true;
  const d = (await github.rest.repos.compareCommitsWithBasehead({owner,repo,basehead:controlSha+'...'+live})).data;
  // GitHub truncates compare file lists at 300; a truncated list cannot prove absence.
  if (d.status !== 'ahead' || !Array.isArray(d.files) || d.files.length >= 300) return false;
  return !d.files.some(f => [f.filename,f.previous_filename].some(n => n &&
    CONTROL_PATHS.some(p => p.endsWith('/') ? n.startsWith(p) : n === p)));
}
async function publish({github,owner,repo,cfg,plan,report,files,expectedSources,runUrl}) {
  validateOutcome(plan,report);
  if (!await controlUnchanged({github,owner,repo,controlSha:plan.controlSha}))
    throw Error('Trusted integration control changed on main; revalidate on next pass');
  const snap = await snapshot(github,owner,repo,plan.pr);
  const suite = sourceEligible(cfg,snap);
  if (!suite || suite.key !== plan.suite.key || snap.pr.head.sha !== plan.sourceSha ||
      ownerDecision(snap.reviews,plan.sourceSha,cfg.policy)?.id !== plan.reviewId ||
      cfg.policyHash !== plan.policyHash || cfg.policy.target !== plan.target)
    throw Error('Approval, certification, source or trusted policy changed during validation');
  const ref = (await github.rest.git.getRef({owner,repo,ref:'heads/'+plan.target})).data.object.sha;
  if (ref !== plan.baseSha) throw Error('Android target advanced; revalidate on next pass');
  const receipt = {schema:1,pr:plan.pr,card:suite.name,sourceSha:plan.sourceSha,baseSha:plan.baseSha,
    target:plan.target,policyHash:plan.policyHash,status:'blocked',runUrl,at:new Date().toISOString()};
  if (report.status === 'passed') {
    validateReport(plan,report,files);
    for (const path of [plan.paths.source,plan.paths.scenario,REGISTRY]) {
      if (files.find(f=>f.path===path)?.content !== expectedSources[path])
        throw Error('Tested source differs from independently reconstructed approved changes');
    }
    const commit = (await github.rest.git.getCommit({owner,repo,commit_sha:plan.baseSha})).data;
    const tree = (await github.rest.git.createTree({owner,repo,base_tree:commit.tree.sha,tree:files})).data;
    const result = (await github.rest.git.createCommit({owner,repo,tree:tree.sha,parents:[plan.baseSha],
      message:`Integrate ${suite.name} from approved PR #${plan.pr}\n\nSource: ${plan.sourceSha}\nOwner review: ${plan.reviewId}\nDestination tests: ${runUrl}\nCard-only integration; no APK or release.`})).data;
    // Non-forced fast-forward of a single-parent commit is also an atomic base lease:
    // if another writer advanced the target after our read, GitHub rejects this update.
    await github.rest.git.updateRef({owner,repo,ref:'heads/'+plan.target,sha:result.sha,force:false});
    receipt.status='integrated'; receipt.destinationSha=result.sha;
  }
  const previous = await receiptFor({github,owner,repo,pr:snap.pr,verifyIntegrated:false});
  const reason = ((report.gates || []).filter(g=>!g.ok).map(g=>g.name+': '+g.detail).join('\n') || report.reason || 'Validation did not pass')+
    '\n'+(report.failures||[]).slice(0,4).join('\n');
  if (receipt.status==='blocked') receipt.reason=String(reason).slice(0,2000);
  const body = receiptBody(receipt,reason);
  // Dedupe blocked notifications for unchanged source/base/policy. Integrated receipts
  // remain permanent source-PR evidence even if the source PR targets a legacy branch.
  if (!previous || previous.sourceSha!==receipt.sourceSha || previous.baseSha!==receipt.baseSha ||
      previous.policyHash!==receipt.policyHash || previous.status!==receipt.status) {
    await github.rest.issues.createComment({owner,repo,issue_number:plan.pr,body});
    await github.rest.issues.createComment({owner,repo,issue_number:81,body});
  }
  // The card is done: close its queue request so Control's queue lists only open work.
  // Best effort; the integration itself already landed.
  if (receipt.status==='integrated' && snap.issue?.state==='open' && String(snap.issue.title||'').startsWith('[Arcana Queue] ')) {
    try { await github.rest.issues.update({owner,repo,issue_number:snap.issue.number,state:'closed',state_reason:'completed'}); }
    catch { /* a later pass or the owner can close it */ }
  }
  return receipt;
}
// Owner-approved Copilot card PRs that integration skips without a receipt, with the reasons,
// posted on #78 only when the list changes. Read-only apart from that one note.
const SKIP_MARKER = '<!-- arcana-integration-skips-v1 -->';
async function reportIntegrationSkips({github,owner,repo,root='.',issue=78}) {
  const cfg = config(root);
  const prs = await github.paginate(github.rest.pulls.list,{owner,repo,state:'open',per_page:100});
  const skipped = [];
  for (const pr of prs.sort((a,b)=>a.number-b.number)) {
    if (!['copilot','copilot-swe-agent[bot]'].includes(String(pr.user?.login).toLowerCase())) continue;
    const reviews = await github.paginate(github.rest.pulls.listReviews,{owner,repo,pull_number:pr.number,per_page:100});
    if (!ownerDecision(reviews,pr.head.sha,cfg.policy)) continue;
    // A receipt (integrated or blocked) for this commit already explains the outcome.
    const comments = await github.paginate(github.rest.issues.listComments,{owner,repo,issue_number:pr.number,per_page:100});
    if (comments.map(parseReceipt).some(r => r?.pr === pr.number && r.sourceSha === pr.head.sha)) continue;
    const {reasons} = integrationBlockers(cfg, await snapshot(github,owner,repo,pr.number));
    if (reasons.length) skipped.push({pr:pr.number,sha:pr.head.sha,title:pr.title,reasons});
    if (skipped.length >= 15) break;
  }
  const body = [SKIP_MARKER, '### 🤖 Approved cards that integration is skipping (automated)', '',
    skipped.length ? 'Each card below has the owner\'s approval on its current commit but cannot be integrated. **Card authors:** fix the reason on the PR; integration retries automatically. No action was taken on these PRs.'
      : 'No owner-approved card PR is being skipped by integration.', '',
    ...skipped.map(s => `- #${s.pr} ${String(s.title).replace(/[@\r\n]/g,' ').slice(0,70)} (head ${s.sha.slice(0,7)}): ${s.reasons.join('; ')}`)].join('\n');
  const comments = await github.paginate(github.rest.issues.listComments,{owner,repo,issue_number:issue,per_page:100});
  const previous = [...comments].reverse().find(c => c.user?.login === 'github-actions[bot]' && c.user?.type === 'Bot' && String(c.body||'').startsWith(SKIP_MARKER));
  if (previous?.body === body || (!previous && !skipped.length)) return 'unchanged';
  await github.rest.issues.createComment({owner,repo,issue_number:issue,body});
  return 'posted ' + skipped.length;
}
module.exports={MARKER,TARGET,REGISTRY,GENERATED,GATES,CONTROL_PATHS,hash,config,validatePolicy,paths,ownerDecision,controlUnchanged,
  integrationBlockers,reportIntegrationSkips,SKIP_MARKER,sourceEligible,snapshot,parseReceipt,receiptFor,planNext,validateOutcome,validateReport,receiptBody,publish};
