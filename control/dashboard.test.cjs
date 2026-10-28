const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'index.html'),'utf8');
const script=html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
test('self-contained dashboard exists',()=>{assert.ok(html.startsWith('<!doctype html>'));assert.ok(html.includes('<title>Arcana Control'));assert.ok(script);
  // Only approved same-origin review and owner authorization modules may load; the inline dashboard script stays read-only.
  assert.deepEqual(html.match(/<script\s+src="[^"]*"/g),['<script src="./review.js"','<script src="./authorize.js"']);});
test('owner authorization has a visible queue panel and guarded client module',()=>{assert.match(html,/id="authorizationList"/);assert.match(html,/authorize\.js/);assert.ok(html.includes('I agree. Do it'));});
test('inline JavaScript parses',()=>{assert.doesNotThrow(()=>new vm.Script(script));});
test('all mobile navigation targets exist',()=>{for(const id of ['overview','work','safety']){assert.match(html,new RegExp('id="'+id+'"'));assert.match(html,new RegExp('data-view="'+id+'"'));}});
test('read only public API data sources',()=>{assert.match(script,/arcana-review\.arcanamtg\.workers\.dev\/v1\/public\/github/);assert.ok(!script.includes('https://api.github.com/repos/'));for(const path of ['/pulls?state=open','/actions/runs?per_page=30','/issues/81/comments?'])assert.ok(script.includes(path),path);assert.ok(!/\b(method:\s*['"](?:POST|PUT|PATCH|DELETE))/.test(script));});
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

test('queue feeder transfers suggestions through an artifact and fails closed on missing results',()=>{
  const feeder=fs.readFileSync(path.join(__dirname,'../.github/workflows/arcana-queue-feeder.yml'),'utf8');
  assert.ok(feeder.includes("fs.writeFileSync('feeder-result.json'"));
  assert.ok(feeder.includes('actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02'));
  assert.ok(feeder.includes('actions/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093'));
  assert.ok(feeder.includes("readFileSync('feeder-result.json','utf8')"));
  assert.ok(feeder.includes("if(!r||published.startsWith('failed'))"));
  assert.ok(feeder.includes("core.setFailed('Queue status not published"));
  assert.match(feeder,/createNextAutoQueue/);
  assert.ok(!feeder.includes('needs.suggest.outputs.result'));
  assert.ok(!feeder.includes("core.setOutput('result'"));
});

test('review inbox shows Copilot cards awaiting owner review, read-only, linking only to repository PRs', () => {
  const fs = require('node:fs');
  const html = fs.readFileSync(require('node:path').join(__dirname, 'index.html'), 'utf8');
  assert.ok(html.includes('id="reviewList"') && html.includes('id="reviewBadge"'));
  assert.ok(html.includes("d.kind==='review'"));
  assert.ok(html.includes("const href=ROOT+'/pull/'+i.pr+'/files'"));
  assert.ok(html.includes('navigator.setAppBadge'));
  const script = html.slice(html.indexOf('// REVIEW_INBOX'), html.indexOf('async function fetchControl'));
  assert.ok(!/POST|PUT|PATCH|DELETE|innerHTML/.test(script));
});
test('authorization button follows the dispatcher verdict, not a client copy of its rules',()=>{
  const {eligibility,reasonText}=require('./authorize.js');
  const src=fs.readFileSync(path.join(__dirname,'authorize.js'),'utf8');
  assert.ok(!src.includes('Exact current Oracle behavior'),'no mirrored Worker schema');
  assert.deepEqual(eligibility({n:1,state:'queued',eligible:true,skip:null}),{ok:true});
  // A status published before verdicts existed carries no eligible flag: fail closed.
  assert.equal(eligibility({n:1,state:'queued',skip:null}).ok,false);
  assert.equal(eligibility(undefined).ok,false);
  assert.match(reasonText('has-open-pr #219'),/open PR \(#219\)/);
  assert.match(reasonText('feasibility: not Copilot-ready: X=NEEDS_REVIEW'),/Not Copilot-ready/);
  assert.match(reasonText('feasibility: feasibility-stale (classified a, base is b)'),/pending/);
  assert.match(reasonText('not-actionable: cards-or-discovery,acceptance,base-missing'),/Old-format/);
});
test('service worker revalidates the shell so a deploy reaches the installed app',()=>{
  const sw=fs.readFileSync(path.join(__dirname,'sw.js'),'utf8');
  assert.ok(sw.includes("fetch(request,{cache:'no-cache'})"));
  assert.ok(sw.includes('caches.match(request)'),'offline fallback kept');
});
test('an approved card that cannot run yet says why it is waiting',()=>{
  const src=fs.readFileSync(path.join(__dirname,'authorize.js'),'utf8');
  assert.ok(src.includes("'Authorization recorded — waiting for guarded dispatcher'"));
  assert.ok(src.includes('(worker slot occupied).'));
});
test('closed issues are never shown as candidates and verdict age is shown',()=>{
  const src=fs.readFileSync(path.join(__dirname,'authorize.js'),'utf8');
  assert.ok(src.includes("if (info.state !== 'open') {"));
  assert.ok(src.includes('row.remove();'));
  assert.ok(src.includes('function render(container, units, checkedAt)'));
  assert.ok(html.includes("window.ArcanaAuthorize.render($('authorizationList'),w.list||[],d.checkedAt);"));
});
test('a stale-served feed is re-asked once (no polling) before being reported stale',()=>{
  assert.ok(html.includes("if(res.ok&&isStaleFeed(res)){await new Promise(r=>setTimeout(r,2000));const again=await fetch(PUBLIC_GITHUB+encodeURIComponent(path),{cache:'no-cache'})"));
  assert.equal((html.match(/isStaleFeed\(res\)/g)||[]).length,1,'single retry only');
});
