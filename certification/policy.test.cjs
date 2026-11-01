'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const P = require('./policy.cjs');
const manifest = P.readManifest(__dirname + '/suites.json');
const sha = 'a'.repeat(40);
const repo = 'AARMOU/arcana_table';
const suite = manifest.suites[0];
const checks = manifest.requiredChecks.map(name => ({name,head_sha:sha,status:'completed',conclusion:'success'}));
const pr = (overrides={}) => ({
  state:'open', title:'Implement Resurrection Orb', body:'Adds equipment; Fixes #408',
  head:{sha,repo:{full_name:repo}}, base:{ref:manifest.base,repo:{full_name:repo}},
  user:{login:'Copilot'}, ...overrides
});
const issue = (overrides={}) => ({
  number:408,state:'open',title:'[Arcana Queue] Resurrection Orb — Cardkit SUPPORTED, unclaimed',
  body:'<!-- arcana-auto-queue-v1 -->\n<!-- source: trusted-main-queue-feeder -->\n## Cards\n- Resurrection Orb',
  ...overrides
});
const choose = (opts={}) => P.candidate(manifest,{pr:opts.pr||pr(),issue:opts.issue||issue(),sha:opts.sha||sha,checks:opts.checks||checks});
test('trusted suite manifest passes schema, class, source and uniqueness checks',()=>{
  assert.equal(suite.name,'Resurrection Orb');
  assert.throws(()=>P.validateManifest({...manifest,suites:[suite,suite]}),/duplicate/i);
  assert.throws(()=>P.validateManifest({...manifest,suites:[{...suite,source:'../../evil.kt'}]}),/Invalid/i);
  assert.throws(()=>P.validateManifest({...manifest,suites:[{...suite,tests:0}]}),/Invalid/i);
  assert.throws(()=>P.validateManifest({...manifest,suites:[{...suite,key:'x; rm -rf'}]}),/Invalid/i);
});
test('normal eligible exact head passes and update of head invalidates eligibility',()=>{
  assert.equal(choose().key,'resurrection-orb');
  assert.equal(choose({sha:'b'.repeat(40)}),null);
  assert.equal(choose({pr:pr({head:{sha:'b'.repeat(40),repo:{full_name:repo}}})}),null);
});
test('worker, provenance, base and trusted unit are required',()=>{
  assert.equal(choose({pr:pr({user:{login:'AARMOU'}})}),null);
  assert.equal(choose({pr:pr({head:{sha,repo:{full_name:'attacker/fork'}}})}),null);
  assert.equal(choose({pr:pr({base:{ref:'main',repo:{full_name:repo}}})}),null);
  assert.equal(choose({pr:pr({body:'Fixes #409'})}),null);
  assert.equal(choose({issue:issue({state:'closed'})}),null);
  assert.equal(choose({issue:issue({body:'## Cards\n- Resurrection Orb'})}),null);
  assert.equal(choose({pr:pr({title:'Implement Some Other Card'})}),null);
});
test('all three green current-head prerequisite checks are mandatory',()=>{
  assert.equal(choose({checks:checks.slice(0,2)}),null);
  assert.equal(choose({checks:checks.map(c=>c.name==='toolkit'?{...c,conclusion:'failure'}:c)}),null);
  assert.equal(choose({checks:checks.map(c=>({...c,head_sha:'b'.repeat(40)}))}),null);
  assert.equal(choose({checks:checks.map(c=>({...c,status:'in_progress'}))}),null);
});
test('workflow_run event must be a successful linked CI run on this repository',()=>{
  const run={event:'pull_request',conclusion:'success',head_sha:sha,name:'Arcana Cardkit',head_repository:{full_name:repo}};
  assert.equal(P.workflowEventEligible(run,repo),true);
  assert.equal(P.workflowEventEligible({...run,head_repository:{full_name:'untrusted/fork'}},repo),false);
  assert.equal(P.workflowEventEligible({...run,name:'Unrelated CI'},repo),false);
  assert.equal(P.workflowEventEligible({...run,event:'push'},repo),false);
  assert.equal(P.workflowEventEligible({...run,conclusion:'failure'},repo),false);
});
test('next independent card needs a manifest row and trusted tests, never a new workflow',()=>{
  const second={key:'sample-card',name:'Sample Card',issue:999,
    source:'certification/SampleCardIndependentCertificationTest.kt',
    testClass:'com.arcanatable.customcards.SampleCardIndependentCertificationTest',tests:5};
  const m=P.validateManifest({...manifest,suites:[suite,second]});
  const p=pr({title:'Add Sample Card',body:'Fixes #999'});
  const i=issue({number:999,title:'[Arcana Queue] Sample Card — Cardkit SUPPORTED, unclaimed',
    body:'<!-- arcana-auto-queue-v1 -->\n<!-- source: trusted-main-queue-feeder -->\n## Cards\n- Sample Card'});
  assert.equal(P.candidate(m,{pr:p,issue:i,sha,checks}).key,'sample-card');
  assert.equal(P.certOutput(second,true).title,'Sample Card: independent rules scenarios passed');
  assert.equal(P.issueFromBody('Fixes #999'),999);
});

test('Poxwalkers certification accepts only exact current Copilot head and trusted source',()=>{
  const target=manifest.suites.find(s=>s.issue===406);
  assert.equal(target.key,'poxwalkers');
  assert.equal(target.tests,6);
  const candidatePr=pr({title:'Implement Poxwalkers',body:'Six independent rules scenarios; Fixes #406'});
  const candidateIssue=issue({
    number:406,
    title:'[Arcana Queue] Poxwalkers — Cardkit SUPPORTED, unclaimed',
    body:'<!-- arcana-auto-queue-v1 -->\\n<!-- source: trusted-main-queue-feeder -->\\n## Cards\\n- Poxwalkers'
  });
  const match=(head=sha,currentChecks=checks,changes={})=>P.candidate(manifest,{
    pr:{...candidatePr,...changes},issue:candidateIssue,sha:head,checks:currentChecks
  });
  assert.equal(match()?.key,'poxwalkers');
  assert.equal(match('b'.repeat(40)),null);
  assert.equal(match(sha,checks.slice(0,2)),null);
  // Descriptive suffixes name the same card; the trusted issue still binds it exactly.
  assert.equal(match(sha,checks,{title:'Implement Poxwalkers custom card'})?.key,'poxwalkers');
  assert.equal(match(sha,checks,{title:'Add Poxwalkers’s rise trigger'})?.key,'poxwalkers');
  for(const title of ['Implement Poxwalkersbane','Add Poxwalkers2','Implement Poxwalkers’sx','Fix Poxwalkers','Implement poxwalkers'])
    assert.equal(match(sha,checks,{title}),null,title);
  assert.equal(match(sha,checks,{user:{login:'AARMOU'}}),null);
});

test('per-suite files beside the manifest register suites without editing the shared array', () => {
  const fs = require('node:fs'), os = require('node:os'), path = require('node:path');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'suites-'));
  fs.copyFileSync(__dirname + '/suites.json', path.join(dir, 'suites.json'));
  fs.mkdirSync(path.join(dir, 'suites'));
  const extra = { key: 'sample-card', name: 'Sample Card', issue: 9999,
    source: 'certification/SampleCardIndependentCertificationTest.kt',
    testClass: 'com.arcanatable.customcards.SampleCardIndependentCertificationTest', tests: 3 };
  fs.writeFileSync(path.join(dir, 'suites', 'sample-card.json'), JSON.stringify(extra));
  const m = P.readManifest(path.join(dir, 'suites.json'));
  // Only suites.json was copied: compare with its own array, not the live per-file suites.
  assert.equal(m.suites.length, JSON.parse(fs.readFileSync(__dirname + '/suites.json', 'utf8')).suites.length + 1);
  assert.deepEqual(m.suites.at(-1), extra);
  assert.deepEqual(P.manifestFiles(path.join(dir, 'suites.json')).map(f => path.basename(f)), ['suites.json', 'sample-card.json']);
  // File name must equal the key, and keys/issues stay unique across all files.
  fs.renameSync(path.join(dir, 'suites', 'sample-card.json'), path.join(dir, 'suites', 'other.json'));
  assert.throws(() => P.readManifest(path.join(dir, 'suites.json')), /must equal its key/);
  fs.writeFileSync(path.join(dir, 'suites', 'other.json'), JSON.stringify({ ...suite, key: 'other' }));
  assert.throws(() => P.readManifest(path.join(dir, 'suites.json')), /duplicate/i);
});

test('typographic apostrophes in Copilot titles match ASCII Oracle names', () => {
  assert.equal(P.titleNames('Implement Verdant Sun’s Avatar', "Verdant Sun's Avatar"), true);
  assert.equal(P.titleNames("Add Kenrith‘s Transformation", "Kenrith's Transformation"), true);
  assert.equal(P.titleNames('Implement Verdant Suns Avatar', "Verdant Sun's Avatar"), false);
  assert.equal(P.titleNames('Implement Verdant Sun’s Avatarx', "Verdant Sun's Avatar"), false);
});

test('successful exact-head independent certification un-drafts only the verified Copilot PR', async () => {
  const calls = [];
  const live = { ...pr(), node_id:'PR_test', draft:true };
  const github = {
    rest:{pulls:{get:async args => {calls.push(['get',args]); return {data:live};}}},
    graphql:async (query,variables) => {
      calls.push(['graphql',query,variables]);
      return {markPullRequestReadyForReview:{pullRequest:{isDraft:false}}};
    }
  };
  assert.equal(await P.markReadyAfterCertification({
    github,owner:'AARMOU',repo:'arcana_table',number:410,sha,certified:true
  }),true);
  assert.equal(calls.length,2);
  assert.equal(calls[0][1].pull_number,410);
  assert.match(calls[1][1],/markPullRequestReadyForReview/);
  assert.equal(calls[1][2].id,'PR_test');
});

test('failed certification cannot look up or un-draft a PR', async () => {
  let calls=0;
  const github={rest:{pulls:{get:async()=>{calls++;return {data:{}};}}},graphql:async()=>{calls++;}};
  for(const certified of [false,undefined,null]) {
    assert.equal(await P.markReadyAfterCertification({
      github,owner:'AARMOU',repo:'arcana_table',number:410,sha,certified
    }),false);
  }
  assert.equal(calls,0);
});

test('stale, non-Copilot, closed, ready and untrusted PRs never get un-drafted', async () => {
  const good={...pr(),node_id:'PR_test',draft:true};
  const badCases=[
    {draft:false},
    {state:'closed'},
    {head:{...good.head,sha:'b'.repeat(40)}},
    {user:{login:'AARMOU'}},
    {user:{login:'another-bot'}},
    {head:{...good.head,repo:{full_name:'attacker/fork'}}},
    {base:{...good.base,ref:'main'}},
    {base:{...good.base,repo:{full_name:'attacker/fork'}}},
    {node_id:''}
  ];
  for(const change of badCases) {
    let writes=0;
    const github={
      rest:{pulls:{get:async()=>({data:{...good,...change}})}},
      graphql:async()=>{writes++;throw Error('unexpected write');}
    };
    assert.equal(await P.markReadyAfterCertification({
      github,owner:'AARMOU',repo:'arcana_table',number:410,sha,certified:true
    }),false,JSON.stringify(change));
    assert.equal(writes,0);
  }
});

test('GraphQL refusal never pretends to have marked a PR ready', async () => {
  const github={
    rest:{pulls:{get:async()=>({data:{...pr(),node_id:'PR_test',draft:true}})}},
    graphql:async()=>({markPullRequestReadyForReview:{pullRequest:{isDraft:true}}})
  };
  await assert.rejects(P.markReadyAfterCertification({
    github,owner:'AARMOU',repo:'arcana_table',number:410,sha,certified:true
  }),/did not confirm/);
});

test('an owner-opened queue unit is accepted; look-alikes and other authors are not', () => {
  const owned = (o = {}) => issue({ title: '[Arcana Queue] Resurrection Orb', body: '## Cards\n\n- Resurrection Orb',
    user: { login: 'AARMOU' }, author_association: 'OWNER', ...o });
  assert.equal(choose({ issue: owned() })?.key, 'resurrection-orb');
  assert.equal(choose({ issue: owned({ title: '[Arcana Queue] Resurrection Orb — owner-authorized one-shot' }) })?.key, 'resurrection-orb');
  // Every other gate still applies to owner units.
  assert.equal(choose({ issue: owned(), sha: 'b'.repeat(40) }), null);
  assert.equal(choose({ issue: owned({ state: 'closed' }) }), null);
  assert.equal(choose({ issue: owned({ body: '## Cards\n\n- Something Else' }) }), null);
  // Not the owner, or not the owner association: rejected without feeder markers.
  assert.equal(choose({ issue: owned({ user: { login: 'someone' } }) }), null);
  assert.equal(choose({ issue: owned({ author_association: 'COLLABORATOR' }) }), null);
  assert.equal(choose({ issue: owned({ user: { login: 'github-actions[bot]' }, author_association: 'NONE' }) }), null);
  // The title must name this card as a whole name, never a longer one.
  assert.equal(choose({ issue: owned({ title: '[Arcana Queue] Resurrection Orbital' }) }), null);
  assert.equal(choose({ issue: owned({ title: '[Card Request] Resurrection Orb' }) }), null);
  // Feeder units are unchanged.
  assert.equal(choose()?.key, 'resurrection-orb');
  assert.equal(choose({ issue: issue({ body: '## Cards\n- Resurrection Orb' }) }), null, 'feeder title without markers is rejected');
});
