'use strict';
// #81 independent Oracle certification preflight. Trusted-main, read-only GitHub assessment.
// Green CI and Cardkit feasibility are NEVER independent rules certification.
// No repairs, paid dispatch, merges, artifacts or releases.
const COPILOT = new Set(['copilot', 'copilot-swe-agent[bot]']);
const MARKER = '<!-- arcana-independent-card-certification-v1 -->';
const STATUS_ISSUE = 78;
const MAX_ITEMS = 30;
const CHECK_NAMES = ['toolkit', 'cards-and-android'];
const HEX = /^[a-f0-9]{40}$/;
const str = x => String(x || '');
const norm = x => str(x).toLowerCase().trim();
const safe = x => str(x).replace(/@/g, '＠').replace(/[\r\n]/g, ' ').slice(0, 160);
const idOf = pr => Number((str(pr.body).match(/(?:Fixes|Closes|Resolves|Unit:)\s*#(\d+)/i) || [])[1]) || null;
const cardOf = pr => str(pr.title).replace(/^\s*(?:\[[^\]]*\]\s*)?(?:Implement|Add)\s+/i, '').trim().slice(0,120);
const checkName = x => norm(x).split(/\s*\/\s*/).pop();

// Never construct proof from a Copilot PR body or untrusted comments. A future
// independent runner must authenticate provenance and the exact test execution first.
function validIndependentProof(proof, sha, issue) {
  return proof?.source === 'independent-oracle-runner' &&
    proof?.provenanceVerified === true &&
    proof?.oracleReviewed === true &&
    proof?.headSha === sha && proof?.issue === issue &&
    proof?.runConclusion === 'success' &&
    Number.isSafeInteger(proof?.positiveCases) && proof.positiveCases > 0 &&
    Number.isSafeInteger(proof?.negativeCases) && proof.negativeCases > 0 &&
    proof?.noSkippedCases === true && proof?.noUnresolvedRulesGaps === true &&
    /^https:\/\/github\.com\/AARMOU\/arcana_table\/actions\/runs\/\d+$/.test(str(proof?.runUrl));
}

function assess({pr, unit, checks = [], proof = null}) {
  const issue = idOf(pr || {});
  const sha = str(pr?.head?.sha);
  const card = cardOf(pr || {});
  const n = Number(pr?.number);
  const base = {pr: Number.isSafeInteger(n)?n:null,issue,card,headSha:sha,
    url: Number.isSafeInteger(n) ? 'https://github.com/AARMOU/arcana_table/pull/'+n+'/files' : null};
  const outcome = (state,reason,checked={}) => ({...base,state,reason,checks:checked,certified:state==='certified-candidate'});
  if(!COPILOT.has(norm(pr?.user?.login)) || !HEX.test(sha) || !issue || !Number.isSafeInteger(n))
    return outcome('blocked','invalid-copilot-pr-or-head');
  if(unit?.number!==issue || unit?.state!=='open' || !str(unit?.title).startsWith('[Arcana Queue]'))
    return outcome('blocked','missing-open-arcana-queue-unit');
  const current=checks.filter(c=>c?.head_sha===sha);
  const byName=new Map();
  for(const c of current) {
    const name=checkName(c?.name);
    if(CHECK_NAMES.includes(name)) {
      const old=byName.get(name);
      if(!old || str(c.completed_at||c.started_at)>str(old.completed_at||old.started_at)) byName.set(name,c);
    }
  }
  const summary=Object.fromEntries(CHECK_NAMES.map(name=>[name,
    byName.has(name)?(byName.get(name).status==='completed'?byName.get(name).conclusion:byName.get(name).status):'missing']));
  if(CHECK_NAMES.some(name=>!byName.has(name))) return outcome('checks-incomplete','required-exact-head-check-missing',summary);
  if(CHECK_NAMES.some(name=>byName.get(name).status!=='completed'))
    return outcome('checks-running','required-check-not-finished',summary);
  if(CHECK_NAMES.some(name=>byName.get(name).conclusion!=='success'))
    return outcome('checks-failed','required-check-not-successful',summary);
  if(!validIndependentProof(proof,sha,issue))
    return outcome('independent-proof-required','green-ci-is-not-oracle-certification',summary);
  return outcome('certified-candidate','independent-exact-head-rules-proof-verified',summary);
}

function snapshot(items,now=new Date()) {
  const order={'checks-failed':0,blocked:1,'checks-incomplete':2,'checks-running':3,
    'independent-proof-required':4,'certified-candidate':5};
  const sorted=[...items].sort((a,b)=>(order[a.state]??99)-(order[b.state]??99)||a.pr-b.pr).slice(0,MAX_ITEMS);
  return {schema:1,checkedAt:now.toISOString(),items:sorted,
    certifiedCandidates:sorted.filter(x=>x.certified).length,
    needsIndependentVerification:sorted.filter(x=>!x.certified).length,
    note:'Green CI is insufficient. Independent proof required. No owner approval, merge, APK, signing or release.'};
}
async function collect({github,owner,repo,now=new Date()}) {
  const prs=await github.paginate(github.rest.pulls.list,{owner,repo,state:'open',per_page:100});
  const items=[];
  for(const pr of prs.filter(p=>COPILOT.has(norm(p.user?.login))).slice(0,MAX_ITEMS)) {
    const n=idOf(pr);
    if(!n) continue;
    let unit;
    try {unit=(await github.rest.issues.get({owner,repo,issue_number:n})).data;} catch {continue;}
    if(unit.state!=='open'||!str(unit.title).startsWith('[Arcana Queue]')) continue;
    const checks=(await github.rest.checks.listForRef({owner,repo,ref:pr.head.sha,per_page:100})).data.check_runs||[];
    // Independence is NOT established by anything the Copilot-authored PR asserts.
    items.push(assess({pr,unit,checks,proof:null}));
  }
  return snapshot(items,now);
}
function summary(p) {
  return ['Independent Oracle preflight: '+p.certifiedCandidates+' certified; '+p.needsIndependentVerification+' not certified.',
    ...p.items.slice(0,10).map(x=>'#'+x.pr+' '+safe(x.card)+': '+x.state+' ('+x.reason+').')];
}
function bodyOf(p) {
  const compact={...p,items:p.items.map(x=>({...x,card:safe(x.card)}))};
  return [MARKER,'**Independent Oracle certification preflight — informational, fail-closed**',
    ...summary(p).map(x=>'- '+safe(x)),'','~~~json',JSON.stringify(compact,null,1),'~~~'].join('\n');
}
// One replaceable bot-authored comment on #78; informational only, never used by admission.
async function publish({github,owner,repo,payload}) {
  const comments=await github.paginate(github.rest.issues.listComments,{owner,repo,issue_number:STATUS_ISSUE,per_page:100});
  const prev=comments.find(c=>c.user?.login==='github-actions[bot]'&&c.user?.type==='Bot'&&str(c.body).startsWith(MARKER));
  const body=bodyOf(payload);
  if(prev){await github.rest.issues.updateComment({owner,repo,comment_id:prev.id,body});return 'updated';}
  await github.rest.issues.createComment({owner,repo,issue_number:STATUS_ISSUE,body});
  return 'created';
}
module.exports={MARKER,CHECK_NAMES,validIndependentProof,assess,snapshot,collect,summary,bodyOf,publish};
