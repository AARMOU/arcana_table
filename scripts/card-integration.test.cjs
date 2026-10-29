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
    issues:{get:async()=>({data:snap.issue}),listComments:async()=>({data:comments}),createComment:async data=>{writes.push(['comment',data]);return {data:{id:1}};}},
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
  const receipt=await P.publish({...common,github,plan:plan(),report:{status:'blocked',gates:[{name:'independent',ok:false,detail:'wrong owner'}]},files:[],expectedSources:{}});
  assert.equal(receipt.status,'blocked');assert.match(receipt.reason,/wrong owner/);
  assert.deepEqual(writes.map(w=>w[0]),['comment','comment']);
});
test('passing integration writes only seven verified files with a non-forced base lease',async()=>{
  const {github,writes}=githubMock();const p=plan();const r=result(p);
  const receipt=await P.publish({...common,github,plan:p,...r});
  assert.equal(receipt.status,'integrated');assert.equal(receipt.destinationSha,destination);
  assert.deepEqual(writes.find(w=>w[0]==='commit')[1].parents,[base]);
  assert.deepEqual(writes.find(w=>w[0]==='ref')[1],{owner:'AARMOU',repo:'arcana_table',ref:'heads/'+P.TARGET,sha:destination,force:false});
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
