'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const read=n=>fs.readFileSync(path.join(__dirname,n),'utf8');
const page=read('cards.html'),script=read('card-queue.js'),credits=read('credits.html'),creditJs=read('credit-budget.js'),dashboard=read('index.html'),worker=read('sw.js');
const workflow=fs.readFileSync(path.join(__dirname,'../.github/workflows/pages.yml'),'utf8');
test('desktop and Android app navigation link both features',()=>{
 for(const url of ['./cards.html','./credits.html'])assert.ok(dashboard.includes('href="'+url+'"'));
 assert.ok(dashboard.includes('grid-template-columns:repeat(5,1fr)'));
 assert.ok(worker.includes("'./cards.html'")&&worker.includes("'./credits.html'"));
});
test('card picker browse, filter, manual entry and explicit owner issue review',()=>{
 for(const id of ['search','deck','coverage','entries','manual','add','request','issues','source','selected'])assert.ok(page.includes('id="'+id+'"'),id);
 assert.doesNotThrow(()=>new vm.Script(script));
 assert.ok(script.includes("fetch('./card-gaps.json'"));
 assert.ok(script.includes("'[Card Request] '"));
 assert.ok(script.includes("window.open(url,'_blank','noopener,noreferrer')"));
 assert.ok(script.includes('MAX=8'));
 assert.ok(script.includes('encodeURIComponent(lines.join'));
 assert.ok(!script.includes('innerHTML'));
 assert.ok(!script.includes('arcana:authorize-once'));
 assert.ok(!/\b(?:POST|PUT|PATCH|DELETE)\b/.test(script));
});
test('scanner comes from current integration-branch deck data without running branch code',()=>{
 assert.ok(workflow.includes('ref: gpt/arcana-integration-next'));
 assert.ok(workflow.includes('path: integration-source'));
 assert.ok(workflow.includes('node tools/deck-gap-audit.cjs --root integration-source'));
 assert.ok(workflow.includes('--json _site/control/card-gaps.json'));
 assert.ok(workflow.includes('SOURCE_SHA="$(git -C integration-source rev-parse HEAD)"'));
 for(const file of ['cards.html','card-queue.js','credits.html','credit-budget.js'])assert.ok(workflow.includes('cp control/'+file));
});
test('credit calculator is private and guides only authorized GitHub settings changes',()=>{
 for(const id of ['included','extra','total','copy'])assert.ok(credits.includes('id="'+id+'"'));
 for(const name of ['ARCANA_AI_CREDIT_BUDGET','ARCANA_AI_CREDIT_RESERVE'])assert.ok(credits.includes(name));
 assert.ok(credits.includes('https://github.com/AARMOU/arcana_table/settings/variables/actions'));
 assert.ok(credits.includes('https://github.com/settings/billing'));
 assert.ok(credits.includes('arcana-copilot-billing-watch.yml'));
 assert.doesNotThrow(()=>new vm.Script(creditJs));
 assert.ok(!/fetch\(|localStorage|sessionStorage|POST|PUT|PATCH/.test(creditJs));
 assert.ok(creditJs.includes('navigator.clipboard.writeText'));
 assert.ok(creditJs.includes('Math.ceil(total*0.1)'));
 assert.ok(credits.includes('cannot purchase credits or directly change GitHub'));
});
test('app shell never caches scanner, billing or GitHub API data',()=>{
 assert.ok(worker.includes('shellUrls.has(url.href)'));
 assert.ok(!worker.includes("'./card-gaps.json'"));
 assert.ok(!worker.includes("'https://api.github.com"));
});

test('dual source browser labels indexed names as unverified and counts deck gaps separately',()=>{
 for(const id of ['deckTab','libraryTab','deckUnique','deckMissing','deckReview','libraryTotal','libraryStatus','showMore'])assert.ok(page.includes('id="'+id+'"'),id);
 assert.ok(script.includes("fetch('./argentum-catalog.json'"));
 assert.ok(script.includes("fetch('./card-gaps.json'"));
 assert.ok(script.includes("const PAGE=100"));
 assert.ok(script.includes("status:'indexed'"));
 assert.ok(script.includes("status:'missing'")===false || script.includes("deckCards.filter(c=>c.status==='missing')"));
 assert.ok(script.includes('Not indexed'));
 assert.ok(script.includes('Indexed · rules unverified'));
 assert.ok(script.includes('Outside scanned decks'));
 assert.ok(workflow.includes('--json _site/control/argentum-catalog.json'));
 assert.ok(worker.includes('shellUrls.has(url.href)'));
 assert.ok(!worker.includes("'./argentum-catalog.json'"));
});

test('owner card priority requires GitHub sign-in and shows Cardkit skipped reasons, never directly dispatches',()=>{
 assert.ok(page.includes('id="priorityStatus"'));
 assert.ok(page.includes('Cardkit verifies SUPPORTED / copilot'));
 assert.ok(page.includes('Review priority request'));
 assert.ok(script.includes("loadPriorityStatus()"));
 assert.ok(script.includes("arcana-control-status-v1:queue"));
 assert.ok(script.includes("Not sent to Copilot:"));
 assert.ok(script.includes("Owner-selected priority request only"));
 assert.ok(script.includes("Not an [Arcana Queue]")===false);
 assert.ok(!script.includes('createWorkflowDispatch'));
 assert.ok(!script.includes('issues.create'));
});
