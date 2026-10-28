const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'index.html'),'utf8');
const script=html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
test('self-contained dashboard exists',()=>{assert.ok(html.startsWith('<!doctype html>'));assert.ok(html.includes('<title>Arcana Control'));assert.ok(script);assert.ok(!/<script\s+src=/.test(html));});
test('inline JavaScript parses',()=>{assert.doesNotThrow(()=>new vm.Script(script));});
test('all mobile navigation targets exist',()=>{for(const id of ['overview','work','safety']){assert.match(html,new RegExp('id="'+id+'"'));assert.match(html,new RegExp('data-view="'+id+'"'));}});
test('read only public API data sources',()=>{assert.match(script,/api\.github\.com\/repos/);for(const path of ['/pulls?state=open','/actions/runs?per_page=30','/issues/81/comments?'])assert.ok(script.includes(path),path);assert.ok(!/\b(method:\s*['"](?:POST|PUT|PATCH|DELETE))/.test(script));});
test('unknown budget and worker presence are explicit',()=>{assert.ok(html.includes('No verified billing feed'));assert.ok(html.includes('Presence unknown'));assert.ok(html.includes('Live session state not exposed'));});
test('live GitHub response rendered as text, not HTML',()=>{assert.match(script,/node\.textContent\s*=\s*value/);assert.ok(script.includes('a.textContent')===false);assert.ok(!script.includes('innerHTML'));});
test('public dashboard links limited to this repo',()=>{assert.ok(script.includes("url.startsWith(ROOT + '/')"));});
test('billing and dispatcher status come only from github-actions bot status comments on #78',()=>{assert.ok(script.includes("'/issues/78/comments?per_page=100&page=1'"));assert.ok(script.includes("c?.user?.login==='github-actions[bot]'&&c?.user?.type==='Bot'"));assert.ok(script.includes("<!-- arcana-control-status-v1:"));});
test('status view shows freshness, blockers, liabilities and worker states',()=>{for(const id of ['creditsFresh','readyFresh','blockerAction','liabValue','wRunning','wPending','wQueued','wDone','wBlocked','workerList'])assert.match(html,new RegExp('id="'+id+'"'),id);for(const s of ['Last successful billing check','premium requests, Actions minutes and ChatGPT usage are separate','never substituted'])assert.ok(script.includes(s),s);});
test('status parser rejects malformed or foreign payloads',()=>{const fn=new vm.Script('('+script.match(/function parseStatus\(body\)\{[\s\S]*?return null;\}\}/)[0]+')');const ctx={STATUS_MARKER:'<!-- arcana-control-status-v1:'};vm.createContext(ctx);const parse=fn.runInContext(ctx);const mk=(k,j)=>'<!-- arcana-control-status-v1:'+k+' -->\nx\n```json\n'+j+'\n```';assert.equal(parse(mk('billing','{"kind":"billing","ok":true}')).ok,true);assert.equal(parse(mk('billing','{"kind":"other"}')),null);assert.equal(parse(mk('billing','not json')),null);assert.equal(parse('hello'),null);});

test('PWA metadata and manifest are scoped to the control dashboard',()=>{
  const manifest=JSON.parse(fs.readFileSync(path.join(__dirname,'manifest.webmanifest'),'utf8'));
  assert.equal(manifest.id,'/arcana_table/control/');
  assert.equal(manifest.scope,'./');
  assert.equal(manifest.start_url,'./');
  assert.equal(manifest.display,'standalone');
  assert.match(html,/rel="manifest" href="\.\/manifest\.webmanifest"/);
  assert.match(html,/serviceWorker\.register\('\.\/sw\.js'/);
  for(const size of [192,512]){
    const icon=manifest.icons.find(icon=>icon.sizes===size+'x'+size);
    assert.ok(icon,'required PNG size '+size);
    assert.match(icon.src,/^\.\/icons\/icon-(192|512)\.png$/);
    const bytes=fs.readFileSync(path.join(__dirname,icon.src));
    assert.equal(bytes.subarray(0,8).toString('hex'),'89504e470d0a1a0a');
    assert.equal(bytes.readUInt32BE(16),size);
    assert.equal(bytes.readUInt32BE(20),size);
  }
});
test('service worker does not cache API or third-party data',()=>{
  const sw=fs.readFileSync(path.join(__dirname,'sw.js'),'utf8');
  assert.doesNotThrow(()=>new vm.Script(sw));
  assert.ok(sw.includes("url.origin!==scope.origin"));
  assert.ok(sw.includes("url.pathname.startsWith(scope.pathname)"));
  assert.ok(sw.includes("request.method!=='GET'"));
  assert.ok(sw.includes("shellUrls.has(url.href)"));
  assert.ok(!sw.includes("'./card-gaps.json'"));
  assert.ok(!sw.includes('api.github.com'));
});
test('Pages publishes the install manifest, worker and icons',()=>{
  const workflow=fs.readFileSync(path.join(__dirname,'../.github/workflows/pages.yml'),'utf8');
  for(const asset of ['manifest.webmanifest','sw.js','icons'])assert.ok(workflow.includes('control/'+asset),asset);
});


test('current Copilot assignment panel is visible on mobile overview and uses read-only public evidence',()=>{
  for(const id of ['assignmentCard','assignmentStage','assignmentDetail','assignmentIssue','assignmentPr','assignmentAgentRun','assignmentFresh'])
    assert.match(html,new RegExp('id="'+id+'"'),id);
  assert.match(html,/Current Copilot assignment/);
  for(const route of ["'/issues/'+candidate.number","'/issues/'+candidate.number+'/timeline?per_page=100'","'/pulls/'+latest.number","'/actions/runs?branch='"])
    assert.ok(script.includes(route),route);
  assert.ok(script.includes("x.actor?.login==='Copilot'"));
  assert.ok(script.includes("x.source?.issue?.user?.login==='Copilot'"));
  assert.ok(script.includes("detail.data?.head?.repo?.full_name===REPO"));
  assert.ok(script.includes("x.head_branch===pr.head.ref"));
  assert.ok(script.includes("a.href=safelyLink(url)"));
  assert.ok(!script.includes('innerHTML'));
});
const pureSrc=script.split('// COPILOT_PURE_START')[1]?.split('// COPILOT_PURE_END')[0];
function pureFunction(name){
  assert.ok(pureSrc,'tested pure Copilot helpers');
  const ctx=vm.createContext({});
  vm.runInContext(pureSrc,ctx);
  return vm.runInContext(name,ctx);
}
test('assignment selection picks an active worker, then confirmed dispatch, then recent finished',()=>{
  const select=pureFunction('selectCopilotAssignment');
  assert.equal(select(null),null);
  assert.equal(select({workers:{list:[]}}),null);
  assert.equal(select({issue:173,result:'confirmed',workers:{list:[{n:172,state:'queued'}]}}).number,173);
  assert.equal(select({issue:173,result:'confirmed',workers:{list:[{n:151,state:'working'},{n:173,state:'queued'}]}}).number,151);
  assert.equal(select({workers:{list:[{n:173,state:'review',recentlyFinished:true}]}}).kind,'recent');
  assert.equal(select({issue:173,result:'denied',workers:{list:[{n:173,state:'queued'}]}}),null);
  assert.equal(select({issue:0,result:'confirmed',workers:{list:[]}}),null);
});
test('Copilot lifecycle distinguishes assignment, pickup, agent running and finished without claiming card support',()=>{
  const stage=pureFunction('copilotStage');
  const pr={number:330,title:'Hold Ulamog pending engine support'};
  assert.equal(stage(null,null,'confirmed').label,'Assigned');
  assert.equal(stage(pr,null,'confirmed').label,'PR opened');
  assert.equal(stage(pr,{status:'in_progress'},'confirmed').label,'Agent running');
  const success=stage(pr,{status:'completed',conclusion:'success'},'confirmed');
  assert.equal(success.label,'Agent finished');
  assert.match(success.detail,/does not certify the card/);
  assert.match(stage(pr,{status:'completed',conclusion:'failure'},'confirmed').label,/Run failure/);
  assert.equal(stage(null,null,'recent').label,'Recently finished');
});
test('queue feeder suggestions render with repo-only new-issue links',()=>{assert.ok(script.includes("d.kind==='queue'"));assert.match(html,/id="queueList"/);assert.ok(script.includes("s.newIssueUrl.startsWith(ROOT+'/issues/new?')"));});
