const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'index.html'),'utf8');
const script=html.match(/<script>([\s\S]*?)<\/script>/)?.[1];
const progressSource=script.split('// CARD_PROGRESS_PURE_START')[1]?.split('// CARD_PROGRESS_PURE_END')[0];
function progressHelpers(){
  const context=vm.createContext({});
  vm.runInContext(progressSource,context);
  return {state:vm.runInContext('cardProgressState',context),items:vm.runInContext('cardProgressItems',context)};
}
const progressCard={pr:463,kind:'card',headSha:'a'.repeat(40),hasTest:true,state:'awaiting-review',checks:{toolkit:'success','cards-and-android':'success'},certification:'not-run'};
test('goose tracker follows certification and exact-head approval, not green CI alone',()=>{
  const {state}=progressHelpers();
  assert.equal(state(progressCard).step,1);
  assert.equal(state({...progressCard,state:'certified',certification:'certified'}).step,2);
  assert.equal(state({...progressCard,state:'approved',certification:'certified'}).step,3);
  assert.equal(state({...progressCard,state:'changes-requested',certification:'certified'}).step,0);
  assert.equal(state({...progressCard,headSha:'b'.repeat(40),state:'checks-running',certification:'not-run',checks:{}}).step,0,'new head never inherits old progress');
  assert.equal(state({...progressCard,kind:'engine'}),null);
  assert.equal(state({...progressCard,headSha:'bad'}),null);
});
test('only a matching Android receipt reaches app ready; merged PRs and old receipts do not',()=>{
  const {state}=progressHelpers();
  const card={...progressCard,state:'approved',certification:'certified'};
  const receipt={pr:463,sourceSha:card.headSha,status:'integrated',destinationSha:'d'.repeat(40),target:'gpt/b140-card-request-test-apk-20261028',at:'2026-10-29T23:21:11Z'};
  assert.equal(state({...card,state:'integrated'}).step,0,'a generic merged PR is insufficient');
  assert.equal(state({...card,integration:receipt}).step,5);
  assert.equal(state({...card,integration:{...receipt,sourceSha:'b'.repeat(40)}}).step,3);
  assert.equal(state({...card,integration:{...receipt,pr:465}}).step,3);
  assert.equal(state({...card,integration:{...receipt,destinationSha:null}}).step,3);
  const blocked=state({...card,integration:{...receipt,status:'blocked',reason:'custom-and-android failed'}});
  assert.equal(blocked.step,4);assert.match(blocked.label,/Integration blocked/);
});
test('tracker prioritizes pending approved cards and excludes blocker-only rows',()=>{
  const {items}=progressHelpers();
  const rows=items({items:[{...progressCard,pr:1,state:'integrated'},{...progressCard,pr:2,kind:'engine'},{...progressCard,pr:3,state:'approved',certification:'certified'},progressCard]});
  assert.equal(rows[0].pr,3);
  assert.equal(rows.length,3);
  assert.equal(items(null).length,0);
});
test('goose picker shows recent unfinished cards, separates verified completed receipts, and searches old cards',()=>{
  const {items}=progressHelpers();
  const ctx=vm.createContext({});
  vm.runInContext(progressSource,ctx);
  const category=vm.runInContext('cardProgressCategory',ctx),choices=vm.runInContext('cardProgressChoices',ctx);
  const checkedAt='2026-10-30T16:00:00Z',newAt='2026-10-30T15:45:00Z',oldAt='2026-10-20T15:45:00Z';
  const approved={...progressCard,pr:344,card:'Abstruse Archaic',state:'approved',certification:'certified',updatedAt:newAt};
  const blocked={...progressCard,pr:416,card:'Sister Repentia',state:'approved',certification:'certified',updatedAt:newAt,
    integration:{pr:416,sourceSha:progressCard.headSha,status:'blocked',reason:'cardkit failed'}};
  const finished={...progressCard,pr:405,card:'Mawloc',state:'integrated',updatedAt:newAt,integration:{
    pr:405,sourceSha:progressCard.headSha,status:'integrated',destinationSha:'f'.repeat(40),
    target:'gpt/b140-card-request-test-apk-20261028',at:newAt}};
  const historical={...progressCard,pr:85,card:'Matter Reshaper and Scaretiller',updatedAt:oldAt};
  const withoutReceipt={...progressCard,pr:77,card:'Old integrated PR',state:'integrated',updatedAt:newAt};
  const report={checkedAt,items:[historical,withoutReceipt,finished,approved,blocked]};
  assert.deepEqual(Array.from(choices(report,'active'),x=>x.pr),[416,344]);
  assert.deepEqual(Array.from(choices(report,'completed'),x=>x.pr),[405]);
  assert.deepEqual(Array.from(choices(report,'all','#85'),x=>x.pr),[85]);
  assert.equal(category(withoutReceipt,checkedAt),'older','unverified merge is not app-ready');
  assert.equal(category({...finished,integration:{...finished.integration,sourceSha:'z'.repeat(40)}},checkedAt),'older');
  assert.equal(items(null).length,0);
});
test('mobile goose picker uses bounded inline choices instead of a giant native select',()=>{
  assert.match(html,/data-progress-filter="active"/);
  assert.match(html,/data-progress-filter="completed"/);
  assert.match(html,/data-progress-filter="all"/);
  assert.match(html,/type="search" id="cardProgressSearch"/);
  assert.match(script,/choices\.slice\(0,6\)/);
  assert.match(script,/cardProgressChoices\(current,cardProgressFilter,cardProgressQuery\)/);
  assert.match(script,/cardProgressSelection=Number\(button\.dataset\.cardPr\)/);
  assert.doesNotMatch(html,/id="cardProgressSelect"/);
  assert.match(html,/class="card-progress-goose"/);
});
test('compact tracker is near the top, uses transparent goose over current dot, and updates after owner decisions',()=>{
  assert.ok(html.indexOf('id="cardProgressTitle"')<html.indexOf('<div class="metrics">'));
  assert.match(html,/\.card-progress-goose\{[^}]*font-size:22px;[^}]*background:transparent/);
  assert.match(html,/\.card-progress-stage\.current \.card-progress-dot\{visibility:hidden\}/);
  assert.match(script,/ArcanaReview\.render\(list,r,cardReviewChanged\)/);
  assert.match(script,/local\.sha!==item\.headSha/);
  assert.match(script,/Feed unavailable · last recorded status/);
});
test('self-contained dashboard exists',()=>{assert.ok(html.startsWith('<!doctype html>'));assert.ok(html.includes('<title>Arcana Control'));assert.ok(script);
  // Only approved same-origin review and owner authorization modules may load; the inline dashboard script stays read-only.
  assert.deepEqual(html.match(/<script\s+src="[^"]*"/g),['<script src="./review.js"','<script src="./authorize.js"']);});
test('owner authorization has a visible queue panel and guarded client module',()=>{assert.match(html,/id="authorizationList"/);assert.match(html,/authorize\.js/);assert.ok(html.includes('I agree. Do it'));});
test('inline JavaScript parses',()=>{assert.doesNotThrow(()=>new vm.Script(script));});
test('all mobile navigation targets exist',()=>{for(const id of ['overview','work','safety']){assert.match(html,new RegExp('id="'+id+'"'));assert.match(html,new RegExp('data-view="'+id+'"'));}});
test('read only public API data sources',()=>{assert.match(script,/arcana-review\.arcanamtg\.workers\.dev\/v1\/public\/github/);assert.ok(script.includes("fetch('https://api.github.com/repos/'+REPO+path"));for(const path of ['/pulls?state=open','/actions/runs?per_page=30','/issues/81/comments?'])assert.ok(script.includes(path),path);assert.ok(!/\b(method:\s*['"](?:POST|PUT|PATCH|DELETE))/.test(script));});
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
  assert.ok(html.includes("window.ArcanaAuthorize.render($('authorizationList'),d.workers?.list||[],d.checkedAt);"));
});
test('stale public feed falls back to fresh GitHub without polling',()=>{
  assert.ok(html.includes("if(res.ok&&isStaleFeed(res)){"));
  assert.ok(html.includes("fetch('https://api.github.com/repos/'+REPO+path"));
  assert.ok(html.includes("if(fresh?.ok){res=fresh;directAt=Date.now();}"));
  assert.equal((html.match(/isStaleFeed\(res\)/g)||[]).length,1);
});
test('source time survives the stale bypass and a hidden custom header',()=>{
  // A bypassed read records its own check time; never GitHub's Last-Modified.
  assert.ok(html.includes("const source=directAt??feedCheckedAt(res);"));
  assert.ok(html.includes("r.headers.get('x-arcana-checked-at')||r.headers.get('last-modified')"));
  // Review freshness reports the observer's checkedAt, not the client page-read time.
  assert.ok(html.includes("sourceTimes.push(newer);"));
  assert.ok(!html.includes("sourceTimes.push(Date.now())"));
});
test('signed-out approval checks GitHub live before showing a button',()=>{
  const auth=fs.readFileSync(path.join(__dirname,'authorize.js'),'utf8');
  assert.ok(auth.includes("https://api.github.com/repos/AARMOU/arcana_table/issues/"));
  assert.ok(auth.includes("if (!direct?.ok) throw Error('Fresh issue state unavailable')"));
  assert.ok(auth.includes("button.textContent = 'Sign in to authorize'"));
});

test('installed PWA refreshes when an updated service worker activates',()=>{
  assert.ok(html.includes("updateViaCache: 'none'"));
  assert.ok(html.includes("registration.update()"));
  assert.ok(html.includes("addEventListener('controllerchange'"));
  assert.ok(html.includes('if (reloading) return'));
  assert.ok(html.includes('location.reload()'));
  assert.ok(html.includes("updateViaCache: 'none'"));
});

test('operations hub replaces marketing banner with real owner review and authorization entry points', () => {
  assert.doesNotMatch(html,/All systems\. One command center\./);
  for (const id of ['quickVerify','quickDoIt','quickVerifyCount','quickDoItCount','opsWorkers','opsWorkerCount','dailyCardCount','dailyBars','dailyFresh'])
    assert.match(html,new RegExp('id="'+id+'"'),id);
  assert.match(script,/ArcanaReview\?\.openNext\?\.\(\)/);
  assert.match(script,/authorizationList/);
  assert.match(script,/renderOpsWorkers\(workerReport\)/);
  assert.match(script,/renderDailyCards\(r\)/);
  assert.match(fs.readFileSync(path.join(__dirname,'review.js'),'utf8'),/api\.openNext = \(\) =>/);
  // The top-level action cannot send an owner authorization or review by itself.
  assert.doesNotMatch(script,/method:\s*['"]POST/);
});
test('daily merged card counts use Mountain calendar days, do not count duplicates or pending work', () => {
  const pure = script.split('// DAILY_CARDS_PURE_START')[1]?.split('// DAILY_CARDS_PURE_END')[0];
  assert.ok(pure);
  const ctx=vm.createContext({});
  vm.runInContext(pure,ctx);
  const sample=[
    {pr:1,card:'A',mergedAt:'2026-10-29T01:30:00Z'},
    {pr:1,card:'A',mergedAt:'2026-10-29T01:30:00Z'},
    {pr:2,card:'B',mergedAt:'2026-10-28T06:05:00Z'},
    {pr:3,card:'C',mergedAt:'2026-10-29T06:05:00Z'},
    {pr:4,card:'Unmerged',state:'checks-running'}
  ];
  const stats=vm.runInContext('dailyCardStats',ctx)(sample,new Date('2026-10-29T01:45:00Z'));
  assert.equal(stats.today,2);
  assert.equal(stats.total,2);
  assert.equal(stats.days.length,7);
  assert.equal(stats.days[6].day,'2026-10-28');
  assert.equal(vm.runInContext('dailyCardStats',ctx)([],new Date('2026-10-29T01:45:00Z')).today,0);
  assert.equal(vm.runInContext('dailyCardStats',ctx)(null),null);
});
test('daily count is sourced from merged Copilot PR activity rather than incomplete 30-row review inbox', () => {
  const source=fs.readFileSync(path.join(__dirname,'../scripts/review-inbox.cjs'),'utf8');
  assert.match(source,/integratedActivity\.push\(/);
  assert.match(source,/mergedAt: pr\.merged_at/);
  assert.match(source,/per_page: 100/);
  assert.ok(script.includes('integratedActivity'));
  assert.ok(script.includes('Merged ≠ independently certified'));
});

test('dispatcher report age is distinct from page freshness and never implies a live agent',()=>{
  for(const id of ['opsWorkerFresh','opsWorkerChecked','opsWorkerCount'])
    assert.match(html,new RegExp('id="'+id+'"'),id);
  assert.ok(script.includes('dispatcher reports working (unconfirmed)'));
  assert.ok(script.includes('reported working'));
  assert.ok(script.includes('not live Copilot telemetry'));
  assert.ok(script.includes('GitHub worker observer or dispatcher publishes a newer report'));
  const pure = script.split('// WORKER_FRESHNESS_PURE_START')[1]?.split('// WORKER_FRESHNESS_PURE_END')[0];
  assert.ok(pure);
  const ctx=vm.createContext({});
  vm.runInContext(pure,ctx);
  const freshness=vm.runInContext('workerSnapshotStatus',ctx);
  const start='2026-10-28T18:00:00Z';
  const minutes=n=>Date.parse(start)+n*60000;
  assert.equal(freshness(start,minutes(2)),'recent');
  assert.equal(freshness(start,minutes(21)),'aging');
  assert.equal(freshness(start,minutes(59)),'aging');
  assert.equal(freshness(start,minutes(60)),'stale');
  assert.equal(freshness('invalid',minutes(20)),'unknown');
  assert.equal(freshness('2026-10-28T20:00:00Z',minutes(20)),'unknown');
});
test('periodic status refresh is bounded, read-only, visibility gated and coalesced',()=>{
  assert.match(script,/const WORKER_POLL_MS=120000/);
  assert.match(script,/document\.visibilityState==='visible'&&navigator\.onLine!==false/);
  assert.match(script,/Date\.now\(\)-controlLastAttempt>=WORKER_POLL_MS/);
  assert.match(script,/document\.addEventListener\('visibilitychange'/);
  assert.match(script,/fetchControl\(\{summaryOnly:true\}\)/);
  assert.match(script,/if\(controlInFlight\)return controlInFlight/);
  assert.match(script,/if\(!summaryOnly\)void fetchCopilotAssignment/);
  assert.ok(!script.includes("fetch('/actions/workflows/"));
  assert.ok(!/method:\s*['"](?:POST|PUT|PATCH|DELETE)/.test(script));
  const sw=fs.readFileSync(path.join(__dirname,'sw.js'),'utf8');
  assert.ok(sw.includes('arcana-control-shell-v16'));
});

test('rate-limited public edge falls back to read-only GitHub with a bounded per-path browser cache',async()=>{
  const from=script.indexOf('const directFeedCache=new Map();');
  const to=script.indexOf('function statusForRun(run)',from);
  assert.ok(from>=0&&to>from,'public feed fallback functions found');
  const src=script.slice(from,to);
  let edge=0,direct=0;
  const path='/issues/78/comments?per_page=100&page=1';
  const host='https://api.github.com/repos/AARMOU/arcana_table';
  const context=vm.createContext({
    Map, Date, REPO:'AARMOU/arcana_table',
    PUBLIC_GITHUB:'https://arcana-review.arcanamtg.workers.dev/v1/public/github?path=',
    staleFeeds:0,cachedFeeds:0,sourceTimes:[],staleSources:[],
    isStaleFeed:()=>false,
    fetch:async(url,options={})=>{
      if(String(url).startsWith('https://arcana-review.')){
        edge++;
        return new Response(JSON.stringify({error:'github-upstream-http-403'}),{status:503});
      }
      direct++;
      assert.equal(String(url),host+path);
      assert.equal(options.headers.Accept,'application/vnd.github+json');
      assert.equal(options.method,undefined,'direct fallback must never mutate GitHub');
      assert.equal(options.headers.Authorization,undefined,'no OAuth or repository tokens in browser');
      return new Response(JSON.stringify([{user:{login:'github-actions[bot]'}}]),{
        status:200,headers:{Link:'<https://api.github.com/mock>; rel="next"'}
      });
    },
  });
  vm.runInContext(src+'\nthis.callApi=api;',context);
  const first=await context.callApi(path);
  assert.equal(first.data.length,1);
  assert.ok(first.link.includes('next'));
  const second=await context.callApi(path);
  assert.equal(second.data.length,1);
  assert.equal(edge,2);
  assert.equal(direct,1,'a two-minute refresh must not spend another public rate-limit request');
  assert.equal(vm.runInContext('directFeedFallbacks',context),2);
  assert.equal(context.cachedFeeds,1);
  assert.equal(await context.publicGithubDirect('/private/admin'),null);
  assert.equal(direct,1);
});

test('dashboard never treats arbitrary edge 503 as permission to bypass its public feed',async()=>{
  const from=script.indexOf('const directFeedCache=new Map();');
  const to=script.indexOf('function statusForRun(run)',from);
  let direct=0;
  const context=vm.createContext({
    Map,Date,REPO:'AARMOU/arcana_table',PUBLIC_GITHUB:'https://arcana-review.example/v1/public/github?path=',
    staleFeeds:0,cachedFeeds:0,sourceTimes:[],staleSources:[],isStaleFeed:()=>false,
    fetch:async url=>{
      if(String(url).startsWith('https://api.github.com')){direct++;return new Response('{}');}
      return new Response(JSON.stringify({error:'public-github-feed-unavailable'}),{status:503});
    }
  });
  vm.runInContext(script.slice(from,to)+'\nthis.callApi=api;',context);
  await assert.rejects(context.callApi('/issues/78/comments?per_page=100&page=1'),/GitHub feed HTTP 503/);
  assert.equal(direct,0);
});

test('independent review refresh requires no Copilot worker, dispatcher or paid authorization',()=>{
  const flow=fs.readFileSync(path.join(__dirname,'../.github/workflows/arcana-card-review-inbox-refresh.yml'),'utf8');
  assert.match(flow,/workflow_run:/);
  assert.match(flow,/Arcana Independent Card Certification/);
  assert.match(flow,/cron: '\*\/15 \* \* \* \*'/);
  assert.match(flow,/workflow_dispatch:/);
  assert.match(flow,/github\.ref == 'refs\/heads\/main'/);
  assert.match(flow,/R\.buildReviewInbox/);
  assert.match(flow,/S\.publishStatus/);
  assert.match(flow,/kind:'review'/);
  assert.match(flow,/if \(result\.startsWith\('failed'\)\)/);
  assert.doesNotMatch(flow,/createWorkflowDispatch|arcana-card-dispatcher|arcana:authorize-once|GITHUB_OAUTH_CLIENT_SECRET|ARCANA_AI_CREDIT_BUDGET/);
});
test('signed-out card authorization falls back to a fresh public GitHub issue when edge is unavailable',()=>{
  const source=fs.readFileSync(path.join(__dirname,'authorize.js'),'utf8');
  assert.match(source,/const response = await fetch\(API \+ '\/v1\/public\/github\?path='/);
  assert.match(source,/\.catch\(\(\) => null\)/);
  assert.match(source,/if \(!info \|\| \(info\.state === 'open' && verdict\.ok/);
  assert.match(source,/cache: 'no-store', headers: \{ Accept:/);
  assert.match(source,/if \(!direct\?\.ok\) throw Error\('Fresh issue state unavailable'\)/);
  assert.match(source,/button\.disabled = true;[\s\S]*Duplicate approval blocked/);
});

test('fresh independent worker observations never change dispatcher authorization',()=>{
  assert.ok(script.includes("d.kind==='workers'"));
  assert.ok(script.includes("Date.parse(observed.checkedAt)>Date.parse(d.checkedAt)"));
  assert.ok(script.includes("renderOpsWorkers(workerReport)"));
  assert.ok(script.includes("window.ArcanaAuthorize.render($('authorizationList'),d.workers?.list||[],d.checkedAt)"));
});

test('aging review snapshot rechecks only its known bot comment, avoids rollback and respects 5m throttle',async()=>{
  const src=script.split('// REVIEW_FRESHNESS_START')[1]?.split('// REVIEW_FRESHNESS_END')[0];
  assert.ok(src,'review freshness code present');
  let clock=Date.parse('2026-10-29T05:32:00Z'),calls=0,requestUrl='';
  const legit={id:123,user:{login:'github-actions[bot]',type:'Bot'},body:'old',html_url:'https://github.com/AARMOU/arcana_table/issues/78#issuecomment-123'};
  const response={...legit,body:JSON.stringify({kind:'review',checkedAt:'2026-10-29T05:30:25Z',awaitingReview:1,items:[{pr:407,readyForOwner:true}]})};
  const ctx={Date:class extends Date{static now(){return clock}},
    REPO:'AARMOU/arcana_table',sourceTimes:[],readTimeout:()=>undefined,
    isBotComment:c=>c?.user?.login==='github-actions[bot]'&&c?.user?.type==='Bot',
    parseStatus:body=>{try{return JSON.parse(body)}catch{return null}},
    fetch:async (url,options)=>{calls++;requestUrl=url;assert.equal(options.cache,'no-store');return {ok:true,json:async()=>response}}
  };
  vm.createContext(ctx);
  vm.runInContext(src,ctx);
  const refresh=vm.runInContext('refreshReviewSnapshot',ctx);
  const stale={data:{kind:'review',checkedAt:'2026-10-29T05:21:00Z',awaitingReview:0},url:legit.html_url};
  const out={review:stale};
  await refresh(out,legit);
  assert.equal(calls,1);
  assert.equal(requestUrl,'https://api.github.com/repos/AARMOU/arcana_table/issues/comments/123');
  assert.equal(out.review.data.awaitingReview,1);
  assert.equal(out.review.data.items[0].pr,407);
  const regressed={review:stale};
  await refresh(regressed,legit);
  assert.equal(calls,1,'no extra GitHub API request within throttle period');
  assert.equal(regressed.review.data.awaitingReview,1,'old edge cache cannot erase verified newer review');
  clock+=5*60*1000+1000;
  await refresh({review:stale},legit);
  assert.equal(calls,2,'recheck becomes eligible again after 5m');
});
test('review recheck refuses untrusted source comments and does not mislabel stale review count as zero',async()=>{
  const src=script.split('// REVIEW_FRESHNESS_START')[1]?.split('// REVIEW_FRESHNESS_END')[0];
  let calls=0;
  const ctx={Date:class extends Date{static now(){return Date.parse('2026-10-29T05:32:00Z')}},
    REPO:'AARMOU/arcana_table',sourceTimes:[],readTimeout:()=>undefined,
    isBotComment:c=>c?.user?.login==='github-actions[bot]'&&c?.user?.type==='Bot',
    parseStatus:()=>null,
    fetch:async()=>{calls++;throw Error('should not fetch')}
  };
  vm.createContext(ctx);vm.runInContext(src,ctx);
  const refresh=vm.runInContext('refreshReviewSnapshot',ctx);
  const out={review:{data:{kind:'review',checkedAt:'2026-10-29T05:22:00Z'}}};
  await refresh(out,{id:123,user:{login:'attacker',type:'User'}});
  assert.equal(calls,0);
  assert.ok(script.includes("text('quickVerifyCount',reviewCountLabel(r));"));
  assert.ok(!script.includes("'— stale'"));
  assert.ok(!/method:\s*['"](?:POST|PUT|PATCH|DELETE)/.test(src));
});

test('Verify count keeps the last published count after five minutes and labels older snapshots',()=>{
  const start=script.indexOf('function reviewCountLabel(');
  const end=script.indexOf('const REVIEW_CURRENT_MS',start);
  assert.ok(start>0&&end>start,'pure count formatter found');
  const actual=require('./review.js');
  const ctx=vm.createContext({Date,window:{ArcanaReview:actual}});
  vm.runInContext(script.slice(start,end)+'\nconst REVIEW_CURRENT_MS=15*60*1000;this.countLabel=reviewCountLabel;',ctx);
  const now=Date.parse('2026-10-29T05:52:00Z');
  const card={pr:407,headSha:'8'.repeat(40),kind:'card',hasTest:true,draft:false,state:'certified',certification:'certified'};
  const review=(min,items=[card])=>({checkedAt:new Date(now-min*60000).toISOString(),items});
  assert.equal(ctx.countLabel(review(2),now),'1 ready');
  assert.equal(ctx.countLabel(review(6),now),'1 ready','5-minute aging does not hide Verify count');
  assert.equal(ctx.countLabel(review(14),now),'1 ready','normal 15-minute bot cadence');
  assert.equal(ctx.countLabel(review(16),now),'1 reported · 16m old','older report retains count and visibly qualifies it');
  assert.equal(ctx.countLabel(review(75),now),'1 reported · 1h old');
  assert.equal(ctx.countLabel(review(16,[]),now),'Not current · 16m old','an old zero is never shown as a current count');
  assert.equal(ctx.countLabel(review(3000,[]),now),'Not current · 2d old');
  assert.equal(ctx.countLabel(review(6,[{...card,draft:true}]),now),'1 ready','a certified draft is ready: Approve marks it ready');
  assert.equal(ctx.countLabel(review(6,[{...card,certification:'not-run'}]),now),'0 ready');
  assert.equal(ctx.countLabel(review(6,[{...card,state:'approved'}]),now),'0 ready');
  assert.equal(ctx.countLabel({checkedAt:'bogus',items:[card]},now),'— unavailable');
  assert.equal(ctx.countLabel({checkedAt:new Date(now+120000).toISOString(),items:[card]},now),'— unavailable');
  assert.equal(ctx.countLabel({checkedAt:review(1).checkedAt,items:null},now),'— unavailable');
  assert.match(script,/text\('quickVerifyCount',reviewCountLabel\(r\)\)/,
    'owner review callback must update the tile when an owner decision changes its count');
  assert.doesNotMatch(script,/quickVerifyCount',reviewFresh\?/);
});

test('a missing review report or failed status feed marks Verify not current',()=>{
  assert.ok(script.includes("if(!r||!Array.isArray(r.items)){reviewUnavailable();return;}"));
  assert.ok(script.includes("renderCardProgress(null);reviewUnavailable();"));
  assert.ok(script.includes("current?'Nothing waiting':'Not current'"));
});


test('a feed with a recent source time is not stale just because the edge says STALE',()=>{
  const start=script.indexOf('const feedStale=');
  assert.ok(start>0);
  const ctx=vm.createContext({Date});
  vm.runInContext(script.slice(start,script.indexOf(';',script.indexOf('UPDATING',start))+1)+'\nthis.f=feedStale;',ctx);
  const now=Date.now();
  assert.equal(ctx.f('live','STALE',now-2*60000),false,'2-minute-old source behind a 60 s edge TTL is current');
  assert.equal(ctx.f('live','HIT',now-16*60000),true,'over 15 minutes is stale');
  assert.equal(ctx.f('stale','HIT',now),true,'Worker-declared stale stays stale');
  assert.equal(ctx.f(null,'UPDATING',NaN),true,'no source time: edge marker decides');
  assert.equal(ctx.f(null,'HIT',NaN),false);
  assert.ok(script.includes('if(feedStale(state,cf,source)){staleFeeds++;'));
});

test('worker visibility bypasses old edge status using only a verified bot comment, and never rolls back',async()=>{
  const pure=script.split('// WORKER_DIRECT_FRESHNESS_START')[1]?.split('// WORKER_DIRECT_FRESHNESS_END')[0];
  assert.ok(pure,'isolated worker freshness code');
  assert.ok(script.includes("if(d.kind==='workers')trustedWorkers=c;"),'only a parsed bot status comment supplies the worker ID');
  assert.ok(script.includes('refreshWorkerSnapshot(out,trustedWorkers)'),'worker refresh is called on status reads');
  let calls=0;
  const old=new Date(Date.now()-7*60*1000).toISOString(),recent=new Date().toISOString();
  const make=checkedAt=>({kind:'workers',checkedAt,workers:{running:checkedAt===recent?1:0,queued:1,list:[{n:509,state:checkedAt===recent?'working':'queued'}]}});
  const comment={id:6488466493,user:{login:'github-actions[bot]',type:'Bot'},body:JSON.stringify(make(recent)),html_url:'https://github.com/AARMOU/arcana_table/issues/78#issuecomment-6488466493'};
  const ctx={REPO:'AARMOU/arcana_table',sourceTimes:[],readTimeout:()=>undefined,
    isBotComment:c=>c?.user?.login==='github-actions[bot]'&&c?.user?.type==='Bot',
    parseStatus:JSON.parse,fetch:async(url,opts)=>{calls++;assert.match(url,/\/issues\/comments\/6488466493$/);assert.equal(opts.cache,'no-store');return {ok:true,json:async()=>comment};}};
  vm.createContext(ctx);vm.runInContext(pure,ctx);
  const refresh=vm.runInContext('refreshWorkerSnapshot',ctx);
  const out={workers:{data:make(old)}};
  await refresh(out,comment);
  assert.equal(out.workers.data.workers.running,1);
  assert.equal(out.workers.data.workers.list[0].n,509);
  assert.equal(calls,1);
  const oldAgain={workers:{data:make(old)}};
  await refresh(oldAgain,comment);
  assert.equal(oldAgain.workers.data.checkedAt,recent,'cached old feed cannot roll back a verified newer worker report');
  assert.equal(calls,1,'bounded direct refresh does not make a second API request');
});
test('worker visibility uses bounded, trust-checked read-only refresh',async()=>{
  const pure=script.split('// WORKER_DIRECT_FRESHNESS_START')[1]?.split('// WORKER_DIRECT_FRESHNESS_END')[0];
  const old=new Date(Date.now()-10*60*1000).toISOString();
  let calls=0;
  const fake={id:321,user:{login:'other-user',type:'User'},body:'{}'};
  const ctx={REPO:'AARMOU/arcana_table',sourceTimes:[],readTimeout:()=>undefined,
    isBotComment:c=>c?.user?.login==='github-actions[bot]'&&c?.user?.type==='Bot',
    parseStatus:JSON.parse,fetch:async()=>{calls++;return {ok:true,json:async()=>fake};}};
  vm.createContext(ctx);vm.runInContext(pure,ctx);
  const refresh=vm.runInContext('refreshWorkerSnapshot',ctx);
  await refresh({workers:{data:{kind:'workers',checkedAt:old,workers:{running:0}}}},fake);
  assert.equal(calls,0,'untrusted comment never triggers a direct GitHub read');
  const valid={...fake,user:{login:'github-actions[bot]',type:'Bot'}};
  await refresh({workers:{data:{kind:'workers',checkedAt:old,workers:{running:0}}}},valid);
  await refresh({workers:{data:{kind:'workers',checkedAt:old,workers:{running:0}}}},valid);
  assert.equal(calls,1,'one request per 5-minute window, even on invalid return data');
});
