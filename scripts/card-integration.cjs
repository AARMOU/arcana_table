'use strict';
// Trusted control-plane code. Never require candidate code in a write-capable job.
const fs = require('node:fs');
const crypto = require('node:crypto');
const C = require('../certification/policy.cjs');
const V = require('./verified-certification.cjs');
const { isCardCheck } = require('./card-checks.cjs');
const MARKER = '<!-- arcana-card-integration-v1 -->';
const SHA = /^[a-f0-9]{40}$/;
const TARGET = 'gpt/b141-candidate-20261101';
const REGISTRY = 'android/custom-cards/src/main/kotlin/com/arcanatable/customcards/CustomCards.kt';
const GENERATED = ['android/app/src/main/assets/argentum/custom-cards.jsonl',
  'android/app/src/main/assets/argentum/custom-cards.index.tsv',
  'docs/agents/generated/pattern-index.json', 'docs/agents/generated/pattern-index.md'];
const GATES = ['engine-pins','export','patterns','cardkit','scenario','independent',
  'custom-and-android','release-safety','scope'];
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
const RETRY_CLASS = 'lan-multi-host-address-in-use-v1';
const RETRY_REF = 'tags/arcana-integration-retry/';
function retryKey(plan) {
  return hash(JSON.stringify([plan.pr,plan.sourceSha,plan.target,plan.baseSha,plan.policyHash,RETRY_CLASS]));
}
function retryClassification(report) {
  const evidence = report.failureEvidence;
  if (report.status !== 'blocked' || report.reason || !Array.isArray(report.gates) ||
      report.gates.length !== GATES.length || new Set(report.gates.map(g=>g.name)).size !== GATES.length ||
      !GATES.every(name=>report.gates.some(g=>g.name===name && g.ok===(name!=='custom-and-android'))) ||
      evidence?.complete !== true || !Array.isArray(evidence.cases) || evidence.cases.length !== 1) return null;
  const f = evidence.cases[0];
  return f.className === 'com.arcanatable.FourPlayerLanTransportTest' &&
    f.name === 'threeGuestSocketsHavePrivateSendChannelsAndRejectFifthPhone' &&
    f.type === 'java.lang.AssertionError' &&
    f.message === 'java.lang.AssertionError: LAN multi-host failed to bind: java.net.BindException: Address already in use'
    ? RETRY_CLASS : null;
}
async function retryConsumed({github,owner,repo,key}) {
  try { await github.rest.git.getRef({owner,repo,ref:RETRY_REF+key}); return true; }
  catch (error) { if (error.status === 404) return false; throw error; }
}
function retryAvailable(receipt, plan) {
  return receipt?.status==='blocked' && receipt.baseSha===plan.baseSha && receipt.policyHash===plan.policyHash &&
    receipt.recovery?.classification===RETRY_CLASS && receipt.recovery?.key===retryKey(plan) &&
    receipt.recovery?.state==='available' && SHA.test(receipt.controlSha || '');
}
async function trustedRetryReceipt({github,owner,repo,receipt}) {
  const runId = Number(receipt.runUrl.split('/').at(-1));
  if (!Number.isSafeInteger(runId)) return false;
  const run = (await github.rest.actions.getWorkflowRun({owner,repo,run_id:runId})).data;
  return run.path === '.github/workflows/arcana-approved-card-integration.yml' &&
    run.head_repository?.full_name === owner+'/'+repo && run.head_branch === 'main' &&
    run.head_sha === receipt.controlSha && run.status === 'completed' && run.conclusion === 'success' &&
    ['push','schedule','workflow_run','workflow_dispatch'].includes(run.event);
}

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
    return {...r,commentUrl:comment.html_url,commentId:comment.id};
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
async function registeredOn({github,owner,repo,ref,symbol,cache={}}) {
  if (!github.rest.repos.getContent) return false;
  if (cache.text === undefined) {
    const d = (await github.rest.repos.getContent({owner,repo,path:REGISTRY,ref})).data;
    cache.text = Buffer.from(d.content || '', d.encoding === 'base64' ? 'base64' : 'utf8').toString('utf8');
  }
  return new RegExp('\\bEntry\\(\\s*' + symbol + '\\s*,').test(cache.text);
}
async function planNext({github,owner,repo,cfg,controlSha,retry=false}) {
  if (!cfg.policy.enabled || !SHA.test(controlSha)) return null;
  const baseSha = (await github.rest.git.getRef({owner,repo,ref:'heads/'+cfg.policy.target})).data.object.sha;
  const ancestor = (await github.rest.repos.compareCommitsWithBasehead({owner,repo,
    basehead:cfg.policy.targetSeed+'...'+baseSha})).data;
  if (!['ahead','identical'].includes(ancestor.status)) throw Error('Android branch no longer descends from authorized seed');
  const prs = await github.paginate(github.rest.pulls.list,{owner,repo,state:'open',per_page:100});
  const registryCache = {};
  for (const pr of prs.sort((a,b)=>a.number-b.number)) {
    if (!['copilot','copilot-swe-agent[bot]'].includes(String(pr.user?.login).toLowerCase()) || pr.draft) continue;
    const snap = await snapshot(github,owner,repo,pr.number);
    const suite = sourceEligible(cfg,snap);
    if (!suite) continue;
    const receipt = await receiptFor({github,owner,repo,pr:snap.pr});
    if (receipt?.status === 'integrated') continue;
    // A card already registered on the destination always blocks ("already registered"), so a run can only
    // re-post the same blocked receipt. Skip it until the destination changes its registry.
    if (await registeredOn({github,owner,repo,ref:baseSha,symbol:paths(suite).symbol,cache:registryCache})) continue;
    // Blocked by the target's engine (e.g. #662 `encore`, #690 registry cards): re-run only when that engine changes,
    // not on every card-only target commit. Non-engine blocks keep re-running when the target moves.
    if (!retry && await require('./destination-preflight.cjs').engineUnchangedSince({github,owner,repo,receipt,baseSha})) continue;
    const plan = {schema:1,pr:pr.number,sourceSha:snap.pr.head.sha,sourceBase:snap.pr.base.sha,
      sourceBaseRef:snap.pr.base.ref,baseSha,target:cfg.policy.target,controlSha,policyHash:cfg.policyHash,
      reviewId:ownerDecision(snap.reviews,snap.pr.head.sha,cfg.policy).id,
      suite,paths:paths(suite),sourceFiles:snap.files.map(f=>f.filename)};
    if (!retry && receipt?.status === 'blocked' && receipt.baseSha === baseSha && receipt.policyHash === cfg.policyHash) {
      if (!retryAvailable(receipt,plan) || !await trustedRetryReceipt({github,owner,repo,receipt}) ||
          await retryConsumed({github,owner,repo,key:retryKey(plan)})) continue;
      plan.autoRetry = {classification:RETRY_CLASS,key:retryKey(plan)};
    }
    return plan;
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
      '**Integration blocked.** '+String(reason).replace(/@/g,'＠').slice(0,1400))+
    (receipt.recovery ? '\n\n'+({available:'One automatic infrastructure retry is available; all destination gates must run again.',
      consumed:'The one automatic retry was reserved. No further automatic attempts; if interrupted, owner review is required.',
      exhausted:'Automatic retry failed. No more automatic attempts; owner review is required.'}[receipt.recovery.state] || '') : '');
}
// The workflow's existing concurrency group serializes this with publication. The ref
// creation is an atomic durable claim even across duplicate events/process restarts.
async function reserveRetry({github,owner,repo,cfg,plan,runAttempt}) {
  if (!plan.autoRetry) return;
  if (runAttempt !== 1 || plan.autoRetry.classification!==RETRY_CLASS || plan.autoRetry.key!==retryKey(plan) ||
      cfg.policyHash!==plan.policyHash || cfg.policy.target!==plan.target ||
      !await controlUnchanged({github,owner,repo,controlSha:plan.controlSha,suite:plan.suite})) throw Error('Stale or invalid automatic retry');
  const snap = await snapshot(github,owner,repo,plan.pr);
  if (snap.pr.head.sha!==plan.sourceSha || sourceEligible(cfg,snap)?.key!==plan.suite.key ||
      ownerDecision(snap.reviews,plan.sourceSha,cfg.policy)?.id!==plan.reviewId) throw Error('Retry approval or source changed');
  const base = (await github.rest.git.getRef({owner,repo,ref:'heads/'+plan.target})).data.object.sha;
  const receipt = await receiptFor({github,owner,repo,pr:snap.pr});
  if (base!==plan.baseSha || !retryAvailable(receipt,plan) ||
      !await trustedRetryReceipt({github,owner,repo,receipt})) throw Error('Retry base or blocker changed');
  // Never update/delete this ref. An existing ref (including a concurrent claim)
  // rejects createRef and stops validation. Interrupted attempts stay consumed.
  await github.rest.git.createRef({owner,repo,ref:'refs/'+RETRY_REF+retryKey(plan),sha:plan.controlSha});
  const consumed = {...receipt,recovery:{...receipt.recovery,state:'consumed'}};
  delete consumed.commentId; delete consumed.commentUrl;
  await github.rest.issues.updateComment({owner,repo,comment_id:receipt.commentId,body:receiptBody(consumed,receipt.reason)});
  await updateCoordinationReceipt({github,owner,repo,receipt:consumed});
}
async function updateCoordinationReceipt({github,owner,repo,receipt}) {
  const comments = await github.paginate(github.rest.issues.listComments,{owner,repo,issue_number:81,per_page:100});
  const previous = [...comments].reverse().find(c=>{
    const r=parseReceipt(c);
    return r?.pr===receipt.pr && r.sourceSha===receipt.sourceSha && r.baseSha===receipt.baseSha &&
      r.policyHash===receipt.policyHash && r.status==='blocked';
  });
  const body=receiptBody(receipt,receipt.reason);
  if (previous) await github.rest.issues.updateComment({owner,repo,comment_id:previous.id,body});
  else await github.rest.issues.createComment({owner,repo,issue_number:81,body});
}
// Paths whose change on main means the tested plan no longer reflects trusted policy/code.
const CONTROL_PATHS = ['integration/','certification/','scripts/card-integration.cjs',
  'scripts/verified-certification.cjs','scripts/card-checks.cjs','scripts/review-inbox.cjs','scripts/control-status.cjs',
  '.github/workflows/arcana-approved-card-integration.yml'];
// Unrelated owner merges may advance main during long destination tests. Accept only a
// fast-forward whose complete changed-file list avoids every control path; else fail closed.
// Other cards' certification suites (and the idle-burst window) land on main all day; they never change this
// card's plan, policy or trusted code. Only additions/edits of those exact data files are exempt, never this
// card's own suite, deletions or renames; everything else under certification/ stays a control path.
const SUITE_DATA = [/^certification\/[A-Z][A-Za-z0-9]+IndependentCertificationTest\.kt$/,
  /^certification\/suites\/[a-z0-9-]+\.json$/, /^certification\/idle-burst\.json$/];
function otherSuiteData(f, suite) {
  if (!['added','modified'].includes(f.status) || f.previous_filename) return false;
  if (!SUITE_DATA.some(r => r.test(f.filename))) return false;
  return !suite || (f.filename !== suite.source && f.filename !== `certification/suites/${suite.key}.json`);
}
async function controlUnchanged({github,owner,repo,controlSha,suite}) {
  const live = (await github.rest.git.getRef({owner,repo,ref:'heads/main'})).data.object.sha;
  if (live === controlSha) return true;
  const d = (await github.rest.repos.compareCommitsWithBasehead({owner,repo,basehead:controlSha+'...'+live})).data;
  // GitHub truncates compare file lists at 300; a truncated list cannot prove absence.
  if (d.status !== 'ahead' || !Array.isArray(d.files) || d.files.length >= 300) return false;
  return !d.files.some(f => !otherSuiteData(f, suite) && [f.filename,f.previous_filename].some(n => n &&
    CONTROL_PATHS.some(p => p.endsWith('/') ? n.startsWith(p) : n === p)));
}
async function publish({github,owner,repo,cfg,plan,report,files,expectedSources,runUrl}) {
  validateOutcome(plan,report);
  if (!await controlUnchanged({github,owner,repo,controlSha:plan.controlSha,suite:plan.suite}))
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
    target:plan.target,policyHash:plan.policyHash,controlSha:plan.controlSha,status:'blocked',runUrl,at:new Date().toISOString()};
  if (plan.autoRetry && (plan.autoRetry.classification!==RETRY_CLASS || plan.autoRetry.key!==retryKey(plan) ||
      !await retryConsumed({github,owner,repo,key:retryKey(plan)}))) throw Error('Automatic retry has no durable reservation');
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
  const reason = ([report.reason,...(report.gates || []).filter(g=>!g.ok).map(g=>g.name+': '+g.detail)].filter(Boolean).join('\n') || 'Validation did not pass')+
    '\n'+(report.failures||[]).slice(0,4).join('\n');
  if (receipt.status==='blocked') {
    receipt.reason=String(reason).slice(0,2000);
    if (plan.autoRetry || retryClassification(report)) receipt.recovery={classification:RETRY_CLASS,key:retryKey(plan),
      state:plan.autoRetry || await retryConsumed({github,owner,repo,key:retryKey(plan)}) ? 'exhausted' : 'available'};
  }
  const body = receiptBody(receipt,reason);
  // Dedupe blocked notifications for unchanged source/base/policy. Integrated receipts
  // remain permanent source-PR evidence even if the source PR targets a legacy branch.
  if (!previous || previous.sourceSha!==receipt.sourceSha || previous.baseSha!==receipt.baseSha ||
      previous.policyHash!==receipt.policyHash || previous.status!==receipt.status) {
    await github.rest.issues.createComment({owner,repo,issue_number:plan.pr,body});
    await github.rest.issues.createComment({owner,repo,issue_number:81,body});
  } else if (receipt.status==='blocked' && receipt.recovery) {
    await github.rest.issues.updateComment({owner,repo,comment_id:previous.commentId,body});
    await updateCoordinationReceipt({github,owner,repo,receipt});
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
module.exports={MARKER,TARGET,REGISTRY,GENERATED,GATES,CONTROL_PATHS,hash,config,validatePolicy,paths,ownerDecision,controlUnchanged,registeredOn,otherSuiteData,
  RETRY_CLASS,RETRY_REF,retryKey,retryClassification,retryConsumed,retryAvailable,trustedRetryReceipt,reserveRetry,
  integrationBlockers,reportIntegrationSkips,SKIP_MARKER,sourceEligible,snapshot,parseReceipt,receiptFor,planNext,validateOutcome,validateReport,receiptBody,publish};
