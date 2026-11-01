'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const P=require('./card-integration.cjs');
const cfg=P.config();
const source='a'.repeat(40), base='b'.repeat(40), control='c'.repeat(40), destination='d'.repeat(40);
const suite=cfg.manifest.suites[0], paths=P.paths(suite);
const check=(name)=>({name,head_sha:source,status:'completed',conclusion:'success'});
function snapshot(){return {
  pr:{number:410,state:'open',draft:false,title:'Implement '+suite.name,body:'Fixes #408',
    user:{login:'Copilot'},head:{sha:source,repo:{full_name:cfg.policy.repository}},
    base:{sha:base,ref:cfg.manifest.base,repo:{full_name:cfg.policy.repository}}},
  issue:{number:408,state:'open',title:'[Arcana Queue] Resurrection Orb — Cardkit SUPPORTED, unclaimed',
    body:'<!-- arcana-auto-queue-v1 -->\nsource: trusted-main-queue-feeder\n- Resurrection Orb'},
  reviews:[{id:20,state:'APPROVED',commit_id:source,user:{id:132252902,login:'AARMOU'}}],
  checks:[...cfg.manifest.requiredChecks.map(check),{...check('Arcana independent certification'),
    app:{id:15368,slug:'github-actions'},output:{title:suite.name+': independent rules scenarios passed',
      summary:'4 trusted-main JUnit scenarios passed against the exact current Copilot PR commit'}}],
  files:[{filename:paths.source,status:'added'},{filename:paths.scenario,status:'added'},
    {filename:P.REGISTRY,status:'modified'},...P.GENERATED.map(filename=>({filename,status:'modified'}))]
};}
const plan=()=>({schema:1,pr:410,sourceSha:source,sourceBase:base,sourceBaseRef:cfg.manifest.base,
  baseSha:base,target:cfg.policy.target,controlSha:control,policyHash:cfg.policyHash,reviewId:20,
  suite,paths,sourceFiles:snapshot().files.map(f=>f.filename)});
function result(p=plan()){
  const files=[paths.source,paths.scenario,P.REGISTRY,...P.GENERATED].map(path=>({path,mode:'100644',type:'blob',content:'safe '+path+'\n'}));
  return {files,report:{schema:1,status:'passed',planHash:P.hash(JSON.stringify(p)),gates:P.GATES.map(name=>({name,ok:true})),
    hashes:Object.fromEntries(files.map(f=>[f.path,P.hash(f.content)]))},
    expectedSources:Object.fromEntries(files.slice(0,3).map(f=>[f.path,f.content]))};
}
function githubMock(snap=snapshot(),{target=base,race=false,comments=[]}={}){
  const writes=[];
  const github={rest:{
    pulls:{get:async()=>({data:snap.pr}),list:async()=>({data:[snap.pr]}),listReviews:async()=>({data:snap.reviews}),listFiles:async()=>({data:snap.files})},
    checks:{listForRef:async()=>({data:{check_runs:snap.checks}})},
    issues:{get:async()=>({data:snap.issue}),listComments:async()=>({data:comments}),createComment:async data=>{writes.push(['comment',data]);return {data:{id:1}};},
      update:async data=>{writes.push(['close',data]);return {data:{}};}},
    repos:{compareCommitsWithBasehead:async()=>({data:{status:'ahead'}})},
    git:{getRef:async({ref})=>({data:{object:{sha:ref==='heads/main'?control:target}}}),
      getCommit:async()=>({data:{tree:{sha:'e'.repeat(40)}}}),
      createTree:async data=>{writes.push(['tree',data]);return {data:{sha:'f'.repeat(40)}};},
      createCommit:async data=>{writes.push(['commit',data]);return {data:{sha:destination}};},
      updateRef:async data=>{if(race)throw Error('Not a fast forward');writes.push(['ref',data]);return {data:{}};}}
  },paginate:async(fn,args)=>{const d=(await fn(args)).data;return Array.isArray(d)?d:d.check_runs;}};
  return {github,writes};
}
const common={owner:'AARMOU',repo:'arcana_table',cfg,runUrl:'https://github.com/AARMOU/arcana_table/actions/runs/123456'};

test('policy forbids main/release destinations and requires fixed engine hashes',()=>{
  assert.equal(P.validatePolicy(cfg.policy).target,P.TARGET);
  for(const patch of [{target:'main'},{target:'release'},{ownerId:1},{engineJars:{}},{targetSeed:'floating'}])
    assert.throws(()=>P.validatePolicy({...cfg.policy,...patch}));
});
test('eligibility requires real exact-head certification, owner, green checks and card-only scope',()=>{
  assert.equal(P.sourceEligible(cfg,snapshot()).key,suite.key);
  const bad=[s=>s.pr.draft=true,s=>s.pr.head.repo.full_name='attacker/fork',s=>s.pr.base.ref='main',
    s=>s.reviews[0].user.id=1,s=>s.reviews[0].commit_id=base,s=>s.checks.pop(),
    s=>s.checks.at(-1).app.id=7,s=>s.checks.at(-1).name='Fake certification',
    s=>s.checks[0].conclusion='failure',s=>s.checks[0].status='in_progress',
    s=>s.files.push({filename:'android/app/libs/argentum-ai.jar',status:'modified'}),
    s=>s.files.push({filename:'.github/workflows/x.yml',status:'added'}),s=>s.files[0].status='removed',
    s=>s.files=s.files.filter(f=>f.filename!==paths.scenario)];
  for(const mutate of bad){const s=snapshot();mutate(s);assert.equal(P.sourceEligible(cfg,s),null);}
});
test('later request-changes or dismissal revokes approval and comments never grant it',()=>{
  const s=snapshot();
  for(const state of ['CHANGES_REQUESTED','DISMISSED'])
    assert.equal(P.ownerDecision([...s.reviews,{id:21,state,commit_id:source,user:s.reviews[0].user}],source,cfg.policy),null);
  assert.ok(P.ownerDecision([...s.reviews,{id:21,state:'COMMENTED',user:s.reviews[0].user}],source,cfg.policy));
});
test('report binds destination, exact plan, every gate, file contents and allowlisted scope',()=>{
  const p=plan();let r=result(p);assert.equal(P.validateReport(p,r.report,r.files).length,7);
  const mutations=[r=>r.report.status='blocked',r=>r.report.planHash='0'.repeat(64),r=>r.report.gates.pop(),
    r=>r.report.gates[0].ok=false,r=>r.files[0].content+='tamper',r=>r.files[0].path='AGENTS.md',
    r=>r.files[0].mode='120000',r=>r.files[0]=r.files[1]];
  for(const mutate of mutations){r=result(p);mutate(r);assert.throws(()=>P.validateReport(p,r.report,r.files));}
  assert.throws(()=>P.validateReport({...p,baseSha:destination},result(p).report,result(p).files));
});
test('failed destination tests publish blocker, with zero target/git writes',async()=>{
  const {github,writes}=githubMock();
  const p=plan();const report={...result(p).report,status:'blocked'};
  report.gates.find(g=>g.name==='independent').ok=false;
  report.gates.find(g=>g.name==='independent').detail='wrong owner';
  const receipt=await P.publish({...common,github,plan:p,report,files:[],expectedSources:{}});
  assert.equal(receipt.status,'blocked');assert.match(receipt.reason,/wrong owner/);
  assert.deepEqual(writes.map(w=>w[0]),['comment','comment'],'a blocked integration never closes the queue request');
});
test('passing integration writes only seven verified files with a non-forced base lease',async()=>{
  const {github,writes}=githubMock();const p=plan();const r=result(p);
  const receipt=await P.publish({...common,github,plan:p,...r});
  assert.equal(receipt.status,'integrated');assert.equal(receipt.destinationSha,destination);
  assert.deepEqual(writes.find(w=>w[0]==='commit')[1].parents,[base]);
  assert.deepEqual(writes.find(w=>w[0]==='ref')[1],{owner:'AARMOU',repo:'arcana_table',ref:'heads/'+P.TARGET,sha:destination,force:false});
  assert.deepEqual(writes.find(w=>w[0]==='close')[1],{owner:'AARMOU',repo:'arcana_table',issue_number:408,state:'closed',state_reason:'completed'},
    'integrated card closes its queue request');
});
test('target/base races, source changes, approval revocation and altered reconstructed source cannot merge',async()=>{
  const p=plan();
  for(const kind of ['base','race','source','review','reconstruction','policy','control']){
    const s=snapshot();if(kind==='source')s.pr.head.sha=destination;if(kind==='review')s.reviews[0].state='DISMISSED';
    const {github,writes}=githubMock(s,{target:kind==='base'?destination:base,race:kind==='race'});
    if(kind==='control')github.rest.git.getRef=async()=>({data:{object:{sha:destination}}});
    const r=result(p);if(kind==='reconstruction')r.expectedSources[paths.source]='evil';
    await assert.rejects(P.publish({...common,github,plan:p,...r,cfg:kind==='policy'?{...cfg,policyHash:'changed'}:cfg}));
    assert.equal(writes.some(w=>w[0]==='ref'),false,kind);
    assert.equal(writes.some(w=>w[0]==='comment'&&w[1].body.includes('"status": "integrated"')),false,kind);
  }
});
test('receipt identity rejects lookalike bots and stale source; integrated status needs ancestry proof',async()=>{
  const rec={schema:1,pr:410,sourceSha:source,baseSha:base,target:P.TARGET,status:'integrated',destinationSha:destination,runUrl:common.runUrl};
  const comment={user:{id:41898282,login:'github-actions[bot]'},body:P.receiptBody(rec),html_url:'https://github.com/example'};
  assert.equal(P.parseReceipt(comment).status,'integrated');
  assert.equal(P.parseReceipt({...comment,user:{id:7,login:'github-actions[bot]'}}),null);
  const {github}=githubMock(snapshot(),{comments:[comment]});
  assert.equal((await P.receiptFor({...common,github,pr:snapshot().pr})).status,'integrated');
  github.rest.repos.compareCommitsWithBasehead=async()=>({data:{status:'diverged'}});
  assert.equal(await P.receiptFor({...common,github,pr:snapshot().pr}),null);
});
test('planner reconciles existing owner approvals and does not endlessly retry unchanged blockers',async()=>{
  const {github}=githubMock();
  assert.equal((await P.planNext({...common,github,controlSha:control})).sourceSha,source);
  const receipt={schema:1,pr:410,sourceSha:source,baseSha:base,target:P.TARGET,status:'blocked',policyHash:cfg.policyHash,runUrl:common.runUrl};
  github.rest.issues.listComments=async()=>({data:[{user:{id:41898282,login:'github-actions[bot]'},body:P.receiptBody(receipt)}]});
  assert.equal(await P.planNext({...common,github,controlSha:control}),null);
  assert.ok(await P.planNext({...common,github,controlSha:control,retry:true}));
});

test('a blocked approved card does not hold up the next eligible card',async()=>{
  const other={...suite,key:'other-card',name:'Other Card',issue:409,
    source:'certification/OtherCardIndependentCertificationTest.kt',testClass:'com.arcanatable.customcards.OtherCardIndependentCertificationTest'};
  const cfg2={...cfg,manifest:{...cfg.manifest,suites:[suite,other]}};
  const second=snapshot();second.pr.number=411;second.pr.title='Implement Other Card';second.pr.body='Fixes #409';
  second.issue={number:409,state:'open',title:'[Arcana Queue] Other Card — Cardkit SUPPORTED, unclaimed',
    body:'<!-- arcana-auto-queue-v1 -->\nsource: trusted-main-queue-feeder\n- Other Card'};
  const p2=P.paths(other);second.files=[{filename:p2.source,status:'added'},{filename:p2.scenario,status:'added'},{filename:P.REGISTRY,status:'modified'}];
  const blocked={schema:1,pr:410,sourceSha:source,baseSha:base,target:P.TARGET,status:'blocked',policyHash:cfg.policyHash,runUrl:common.runUrl};
  const {github}=githubMock();
  github.rest.pulls.list=async()=>({data:[snapshot().pr,second.pr]});
  github.rest.pulls.get=async({pull_number})=>({data:pull_number===410?snapshot().pr:second.pr});
  github.rest.pulls.listFiles=async({pull_number})=>({data:pull_number===410?snapshot().files:second.files});
  github.rest.issues.get=async({issue_number})=>({data:issue_number===408?snapshot().issue:second.issue});
  github.rest.issues.listComments=async({issue_number})=>({data:issue_number===410?
    [{user:{id:41898282,login:'github-actions[bot]'},body:P.receiptBody(blocked)}]:[]});
  assert.equal((await P.planNext({...common,cfg:cfg2,github,controlSha:control})).pr,411);
});
test('unrelated main merges during destination tests do not discard a passing integration',async()=>{
  const live='9'.repeat(40);
  const setup=compare=>{const m=githubMock();const getRef=m.github.rest.git.getRef;
    m.github.rest.git.getRef=async a=>a.ref==='heads/main'?{data:{object:{sha:live}}}:getRef(a);
    m.github.rest.repos.compareCommitsWithBasehead=async({basehead})=>{assert.equal(basehead,control+'...'+live);return {data:compare};};
    return m;};
  const {github,writes}=setup({status:'ahead',files:[{filename:'control/index.html'},{filename:'scripts/worker-status.cjs'}]});
  const p=plan();assert.equal((await P.publish({...common,github,plan:p,...result(p)})).status,'integrated');
  assert.ok(writes.some(w=>w[0]==='ref'));
  for(const compare of [{status:'ahead',files:[{filename:'integration/policy.json'}]},
    {status:'ahead',files:[{filename:'certification/suites.json'}]},
    {status:'ahead',files:[{filename:'scripts/x.cjs',previous_filename:'scripts/card-integration.cjs'}]},
    {status:'ahead',files:[{filename:'.github/workflows/arcana-approved-card-integration.yml'}]},
    {status:'diverged',files:[]},{status:'ahead'},
    {status:'ahead',files:Array.from({length:300},(_,i)=>({filename:'docs/'+i+'.md'}))}]){
    const m=setup(compare);
    await assert.rejects(P.publish({...common,github:m.github,plan:p,...result(p)}),/control changed/);
    assert.equal(m.writes.length,0,JSON.stringify(compare).slice(0,80));
  }
});

test('control-plane feeder/observer runs cannot block an approved card; real failing checks still do', () => {
  const s = snapshot();
  s.checks.push({ name: 'suggest', head_sha: source, status: 'in_progress', conclusion: null },
    { name: 'publish', head_sha: source, status: 'completed', conclusion: 'failure' });
  assert.equal(P.sourceEligible(cfg, s).key, suite.key);
  s.checks.push({ name: 'coordination-guard', head_sha: source, status: 'completed', conclusion: 'failure' });
  assert.equal(P.sourceEligible(cfg, s), null);
});
test('integration blockers name why an approved card is skipped, and match the gate exactly',()=>{
  assert.deepEqual(P.integrationBlockers(cfg,snapshot()).reasons,[]);
  const extra=snapshot();extra.files.push({filename:'tools/cardkit/tests/test_cardkit.py',status:'modified'});
  const r=P.integrationBlockers(cfg,extra).reasons;
  assert.equal(P.sourceEligible(cfg,extra),null);
  assert.ok(r.some(x=>x.includes('outside the card-only set: tools/cardkit/tests/test_cardkit.py')),r.join('|'));
  const draft=snapshot();draft.pr.draft=true;
  assert.deepEqual(P.integrationBlockers(cfg,draft).reasons,['the PR is still a draft']);
});
test('skip report posts approved, receipt-less, ineligible PRs on #78 only when the list changes',async()=>{
  const snap=snapshot();snap.files.push({filename:'tools/cardkit/tests/test_cardkit.py',status:'modified'});
  const comments=[];const posted=[];
  const {github}=githubMock(snap);
  github.rest.issues.listComments=async({issue_number})=>({data:issue_number===78?comments:[]});
  github.rest.issues.createComment=async({body})=>{posted.push(body);comments.push({user:{login:'github-actions[bot]',type:'Bot'},body});};
  const run=()=>P.reportIntegrationSkips({github,owner:'AARMOU',repo:'arcana_table'});
  assert.equal(await run(),'posted 1');
  assert.ok(posted[0].startsWith(P.SKIP_MARKER)&&posted[0].includes('#410')&&posted[0].includes('test_cardkit.py'));
  assert.equal(await run(),'unchanged');
  // Eligible again (fixed by the author): one "none skipped" note, then quiet.
  snap.files.pop();
  assert.equal(await run(),'posted 0');
  assert.equal(await run(),'unchanged');
  // An existing receipt (here: integrated) for the current commit already explains the outcome.
  snap.files.push({filename:'tools/cardkit/tests/test_cardkit.py',status:'modified'});
  const receipt={user:{id:41898282,login:'github-actions[bot]',type:'Bot'},html_url:'u',body:P.MARKER+'\n```json\n'+JSON.stringify({schema:1,pr:410,
    sourceSha:source,baseSha:base,target:P.TARGET,status:'integrated',destinationSha:destination,runUrl:'https://github.com/AARMOU/arcana_table/actions/runs/1'})+'\n```'};
  github.rest.issues.listComments=async({issue_number})=>({data:issue_number===78?comments:[receipt]});
  assert.equal(await run(),'unchanged','integrated cards are never listed');
  // Unapproved PRs are never listed.
  const quiet=githubMock((()=>{const s=snapshot();s.reviews=[];s.files.push({filename:'x.txt',status:'added'});return s;})());
  quiet.github.rest.issues.listComments=async()=>({data:[]});
  quiet.github.rest.issues.createComment=async()=>{throw Error('must not post');};
  assert.equal(await P.reportIntegrationSkips({github:quiet.github,owner:'AARMOU',repo:'arcana_table'}),'unchanged');
});

function transientReport(p=plan()) {
  const r=result(p).report;
  r.status='blocked';r.gates.find(g=>g.name==='custom-and-android').ok=false;
  r.failureEvidence={complete:true,cases:[{
    className:'com.arcanatable.FourPlayerLanTransportTest',
    name:'threeGuestSocketsHavePrivateSendChannelsAndRejectFifthPhone',type:'java.lang.AssertionError',
    message:'java.lang.AssertionError: LAN multi-host failed to bind: java.net.BindException: Address already in use'}]};
  return r;
}
function retryMock() {
  const m=githubMock(), refs=new Map(), comments=[], updates=[];
  const getRef=m.github.rest.git.getRef;
  m.github.rest.git.getRef=async a=>{
    if (!a.ref.startsWith(P.RETRY_REF)) return getRef(a);
    if (!refs.has(a.ref)) throw Object.assign(Error('Not found'),{status:404});
    return {data:{object:{sha:refs.get(a.ref)}}};
  };
  m.github.rest.git.createRef=async a=>{
    const ref=a.ref.replace(/^refs\//,'');
    if(refs.has(ref))throw Object.assign(Error('Reference already exists'),{status:422});
    refs.set(ref,a.sha);m.writes.push(['retry-ref',a]);return {data:{ref:a.ref}};
  };
  m.github.rest.actions={getWorkflowRun:async()=>({data:{
    path:'.github/workflows/arcana-approved-card-integration.yml',head_branch:'main',head_sha:control,
    head_repository:{full_name:cfg.policy.repository},event:'schedule',status:'completed',conclusion:'success'}})};
  m.github.rest.issues.listComments=async({issue_number})=>({data:comments.filter(c=>c.issue===issue_number)});
  m.github.rest.issues.createComment=async a=>{
    const c={id:comments.length+1,issue:a.issue_number,user:{id:41898282,login:'github-actions[bot]'},body:a.body};
    comments.push(c);m.writes.push(['comment',a]);return {data:c};
  };
  m.github.rest.issues.updateComment=async a=>{
    const c=comments.find(c=>c.id===a.comment_id);assert.ok(c);c.body=a.body;updates.push(a);
  };
  return {...m,refs,comments,updates};
}
async function firstTransient(m) {
  return P.publish({...common,github:m.github,plan:plan(),report:transientReport(),files:[],expectedSources:{}});
}
test('only the exact single LAN bind failure with all other gates green is retryable',()=>{
  assert.equal(P.retryClassification(transientReport()),P.RETRY_CLASS);
  const mutations=[r=>delete r.failureEvidence,r=>r.failureEvidence.complete=false,
    r=>r.failureEvidence.cases.push({...r.failureEvidence.cases[0]}),r=>r.failureEvidence.cases=[],
    r=>r.failureEvidence.cases[0].type='java.net.BindException',r=>r.failureEvidence.cases[0].name='other',
    r=>r.failureEvidence.cases[0].message+=' plus something else',r=>r.gates[0].ok=false,
    r=>r.gates.pop(),r=>r.gates[0]=r.gates[1],r=>r.reason='unrelated infrastructure failure',r=>r.status='passed'];
  for(const mutate of mutations){const r=transientReport();mutate(r);assert.equal(P.retryClassification(r),null);}
});
test('full failure -> one atomic retry -> failure updates existing receipts and stays blocked',async()=>{
  const m=retryMock();const initial=await firstTransient(m);
  assert.equal(initial.recovery.state,'available');
  assert.equal(m.writes.some(w=>['tree','ref','close'].includes(w[0])),false);
  const p=await P.planNext({...common,github:m.github,controlSha:control});
  assert.equal(p.autoRetry.key,initial.recovery.key);
  await P.reserveRetry({...common,github:m.github,plan:p,runAttempt:1});
  assert.equal(m.refs.size,1);
  assert.equal(m.comments.length,2,'reservation edits the original PR and #81 notes');
  assert.match(m.comments[0].body,/reserved/);
  for(let i=0;i<4;i++)assert.equal(await P.planNext({...common,github:m.github,controlSha:control}),null);
  await assert.rejects(P.reserveRetry({...common,github:m.github,plan:p,runAttempt:1}));
  const receipt=await P.publish({...common,github:m.github,plan:p,report:transientReport(p),files:[],expectedSources:{}});
  assert.equal(receipt.recovery.state,'exhausted');
  assert.equal(m.comments.length,2,'second failure does not spam duplicate receipts');
  assert.match(m.comments[0].body,/Automatic retry failed/);
  assert.equal(await P.planNext({...common,github:m.github,controlSha:control}),null);
  assert.equal(m.writes.some(w=>['tree','ref','close'].includes(w[0])),false);
  assert.ok(await P.planNext({...common,github:m.github,controlSha:control,retry:true}),'explicit owner retry remains available');
});
test('one successful retry still requires every destination gate and reconstructed approved file',async()=>{
  const m=retryMock();await firstTransient(m);
  const p=await P.planNext({...common,github:m.github,controlSha:control});
  await P.reserveRetry({...common,github:m.github,plan:p,runAttempt:1});
  const r=result(p);r.report.gates[0].ok=false;
  await assert.rejects(P.publish({...common,github:m.github,plan:p,...r}),/every gate/);
  assert.equal(m.writes.some(w=>w[0]==='ref'),false);
  const receipt=await P.publish({...common,github:m.github,plan:p,...result(p)});
  assert.equal(receipt.status,'integrated');
  assert.equal(m.writes.filter(w=>w[0]==='ref').length,1);
});
test('duplicate simultaneous reservations use one durable claim; cancellation never refunds it',async()=>{
  const m=retryMock();await firstTransient(m);
  const p=await P.planNext({...common,github:m.github,controlSha:control});
  const results=await Promise.allSettled([1,2].map(()=>P.reserveRetry({...common,github:m.github,plan:p,runAttempt:1})));
  assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
  assert.equal(m.refs.size,1);
  // Simulate a new process/cron run after cancellation: only remote state survives.
  assert.equal(await P.planNext({...common,github:m.github,controlSha:control}),null);
  await assert.rejects(P.reserveRetry({...common,github:m.github,plan:p,runAttempt:2}));
});
test('retry provenance rejects bot comments from PRs, unrelated workflows and stale controls',async()=>{
  const changes=[r=>r.event='pull_request',r=>r.head_branch='attacker',r=>r.path='other.yml',
    r=>r.head_repository.full_name='attacker/fork',r=>r.head_sha=destination,r=>r.conclusion='failure',r=>r.status='in_progress'];
  for(const mutate of changes){
    const m=retryMock();await firstTransient(m);
    const original=m.github.rest.actions.getWorkflowRun;
    m.github.rest.actions.getWorkflowRun=async()=>{const r=await original();mutate(r.data);return r;};
    assert.equal(await P.planNext({...common,github:m.github,controlSha:control}),null);
    assert.equal(m.refs.size,0);
  }
  const m=retryMock();await firstTransient(m);
  m.comments[0].user.id=7;
  assert.equal((await P.planNext({...common,github:m.github,controlSha:control})).autoRetry,undefined);
});
test('recheck before reservation refuses changed source, owner, base or policy without spending retry',async()=>{
  for(const kind of ['source','review','base','policy','api','rerun']){
    const m=retryMock();await firstTransient(m);
    const p=await P.planNext({...common,github:m.github,controlSha:control});
    if(kind==='source')m.github.rest.pulls.get=async()=>({data:{...snapshot().pr,head:{...snapshot().pr.head,sha:destination}}});
    if(kind==='review')m.github.rest.pulls.listReviews=async()=>({data:[]});
    if(kind==='base'){
      const get=m.github.rest.git.getRef;
      m.github.rest.git.getRef=async a=>a.ref==='heads/'+P.TARGET?{data:{object:{sha:destination}}}:get(a);
    }
    if(kind==='api')m.github.rest.actions.getWorkflowRun=async()=>{throw Error('API unavailable');};
    await assert.rejects(P.reserveRetry({...common,github:m.github,plan:p,
      cfg:kind==='policy'?{...cfg,policyHash:'changed'}:cfg,runAttempt:kind==='rerun'?2:1}));
    assert.equal(m.refs.size,0,kind);
  }
});
test('durable retry identity binds source, target, base, PR and policy; API uncertainty stops planning',async()=>{
  const p=plan();
  for(const change of [{pr:411},{sourceSha:destination},{baseSha:destination},{target:'main'},{policyHash:'changed'}])
    assert.notEqual(P.retryKey({...p,...change}),P.retryKey(p));
  const m=retryMock();await firstTransient(m);
  m.github.rest.git.getRef=async()=>{throw Object.assign(Error('Rate limited'),{status:403});};
  await assert.rejects(P.planNext({...common,github:m.github,controlSha:control}),/Rate limited/);
});
test('workflow reserves before candidate execution, forbids automatic reruns and keeps PRs read-only',()=>{
  const y=require('node:fs').readFileSync('.github/workflows/arcana-approved-card-integration.yml','utf8');
  assert.match(y,/group: arcana-approved-card-integration\n  cancel-in-progress: false/);
  const reservation=y.split('  retry-reservation:')[1].split('  destination-tests:')[0];
  assert.match(reservation,/github.event_name != 'pull_request'/);
  assert.match(reservation,/P.reserveRetry/);
  assert.doesNotMatch(reservation,/path: candidate|python3|gradlew|@copilot/i);
  const destination=y.split('  destination-tests:')[1].split('  receipt-contract:')[0];
  assert.match(destination,/needs: \[plan, retry-reservation\]/);
  assert.match(destination,/needs.retry-reservation.result == 'success'/);
  assert.match(destination,/github.run_attempt == 1/);
  assert.match(destination,/contents: read/);
  assert.doesNotMatch(destination,/contents: write|issues: write/);
  assert.match(y,/Manual blocked retry requires an owner workflow dispatch/);
});
test('partial reservation publication cannot refund the consumed attempt or run candidate code',async()=>{
  const m=retryMock();await firstTransient(m);
  const p=await P.planNext({...common,github:m.github,controlSha:control});
  m.github.rest.issues.updateComment=async()=>{throw Error('Comments unavailable');};
  await assert.rejects(P.reserveRetry({...common,github:m.github,plan:p,runAttempt:1}),/Comments unavailable/);
  assert.equal(m.refs.size,1);
  assert.equal(await P.planNext({...common,github:m.github,controlSha:control}),null);
  assert.equal(m.writes.some(w=>['tree','ref','close'].includes(w[0])),false);
});
test('changed base or policy gets fresh full revalidation, not an unchanged automatic retry',async()=>{
  const m=retryMock();await firstTransient(m);
  const changed=await P.planNext({...common,github:m.github,controlSha:control,cfg:{...cfg,policyHash:'new-policy'}});
  assert.equal(changed.autoRetry,undefined);
  const get=m.github.rest.git.getRef;
  m.github.rest.git.getRef=async a=>a.ref==='heads/'+P.TARGET?{data:{object:{sha:destination}}}:get(a);
  const newBase=await P.planNext({...common,github:m.github,controlSha:control});
  assert.equal(newBase.baseSha,destination);
  assert.equal(newBase.autoRetry,undefined);
  assert.equal(m.refs.size,0);
});

test('another card\'s newly added suite on main does not block publishing; edits, own suite, idle-burst and other control changes still do',async()=>{
  const live='9'.repeat(40);
  const setup=files=>{const m=githubMock();const getRef=m.github.rest.git.getRef;
    m.github.rest.git.getRef=async a=>a.ref==='heads/main'?{data:{object:{sha:live}}}:getRef(a);
    m.github.rest.repos.compareCommitsWithBasehead=async()=>({data:{status:'ahead',files}});return m;};
  const p=plan();
  const added=[{filename:'certification/BaneOfBalaGedIndependentCertificationTest.kt',status:'added'},
    {filename:'certification/suites/bane-of-bala-ged.json',status:'added'}];
  const ok=setup(added);
  assert.equal((await P.publish({...common,github:ok.github,plan:p,...result(p)})).status,'integrated');
  for(const files of [
    [...added,{filename:'certification/OtherCardIndependentCertificationTest.kt',status:'modified'}],
    [{filename:'certification/suites/other-card.json',status:'modified'}],
    [{filename:suite.source,status:'added'}],
    [{filename:`certification/suites/${suite.key}.json`,status:'added'}],
    [{filename:'certification/idle-burst.json',status:'modified'}],
    [{filename:'certification/BaneOfBalaGedIndependentCertificationTest.kt',status:'removed'}],
    [{filename:'certification/XIndependentCertificationTest.kt',status:'renamed',previous_filename:'certification/policy.cjs'}],
    [{filename:'certification/policy.cjs',status:'modified'}],
    [{filename:'certification/suites.json',status:'modified'}],
    Array.from({length:300},(_,i)=>({filename:`certification/C${i}IndependentCertificationTest.kt`,status:'added'}))]){
    const m=setup(files);
    await assert.rejects(P.publish({...common,github:m.github,plan:p,...result(p)}),/control changed/,JSON.stringify(files).slice(0,120));
    assert.equal(m.writes.length,0);
  }
  assert.equal(P.otherSuiteData(added[0],undefined),false,'unknown plan suite: nothing is exempt');
});

test('an already-registered card runs once to a visible "already registered" receipt, then stops repeating',async()=>{
  const registry=t=>({data:{encoding:'base64',content:Buffer.from(t).toString('base64')}});
  const receipt=reason=>({id:7,html_url:'u',user:{id:41898282,login:'github-actions[bot]'},body:P.MARKER+'\n```json\n'+
    JSON.stringify({schema:1,pr:410,card:suite.name,sourceSha:source,baseSha:'e'.repeat(40),target:P.TARGET,policyHash:cfg.policyHash,
      controlSha:control,status:'blocked',runUrl:'https://github.com/AARMOU/arcana_table/actions/runs/1',at:'2026-11-01T00:00:00Z',reason})+'\n```\nblocked'});
  const present=()=>registry(`        Entry(${paths.symbol}, "SET", POOL),\n`);
  // No receipt yet: it runs, so a visible disposition is posted (nothing silently skipped).
  let m=githubMock();m.github.rest.repos.getContent=present;
  assert.equal((await P.planNext({...common,github:m.github,controlSha:control})).pr,410);
  // The destination moved since the receipt (as B141 does all day); it says already registered and the registry
  // still has it: skip instead of re-running.
  m=githubMock(snapshot(),{comments:[receipt('Card is already registered on destination')]});m.github.rest.repos.getContent=present;
  assert.equal(await P.planNext({...common,github:m.github,controlSha:control}),null);
  // Registry entry removed, or a different symbol with the same prefix: plan again.
  m=githubMock(snapshot(),{comments:[receipt('Card is already registered on destination')]});
  m.github.rest.repos.getContent=async()=>registry(`        Entry(${paths.symbol}Token, "SET", POOL),\n`);
  assert.equal((await P.planNext({...common,github:m.github,controlSha:control})).pr,410);
  // A different block reason is not affected by this rule.
  m=githubMock(snapshot(),{comments:[receipt('scenario: 1 failed')]});m.github.rest.repos.getContent=present;
  assert.notEqual(await P.planNext({...common,github:m.github,controlSha:control,retry:true}),null);
});
