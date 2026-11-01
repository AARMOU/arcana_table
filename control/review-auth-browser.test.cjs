'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const src = fs.readFileSync(path.join(__dirname, 'review.js'), 'utf8');

class Element {
  constructor(tag) {
    this.tagName = tag;
    this.children = [];
    this.handlers = {};
    this.style = {};
    this.textContent = '';
    this.disabled = false;
    this.hidden = false;
    this.open = false;
  }
  addEventListener(name, callback) { (this.handlers[name] ||= []).push(callback); }
  append(...nodes) { this.children.push(...nodes); }
  replaceChildren(...nodes) { this.children = [...nodes]; }
  setAttribute(key, value) { this[key] = value; }
  showModal() { this.open = true; }
  close() { this.open = false; }
  dispatch(name) { for(const cb of this.handlers[name] || []) cb(); }
}
const items = node => [node, ...node.children.flatMap(items)];
const memory = blocked => {
  const map = new Map();
  return {
    map,
    getItem(key) { if (blocked) throw Error('storage-blocked'); return map.get(key) || null; },
    setItem(key,value) { if (blocked) throw Error('storage-blocked'); map.set(key,value); },
    removeItem(key) { if (blocked) throw Error('storage-blocked'); map.delete(key); },
  };
};
function browser({ hash='', localBlocked=false, sessionBlocked=false, authStatus=200,
  decisionStatus=200, decisionBody={ok:true,state:'approved'}, decisionThrows=false, decisionHold=null, reviewItem={}, oracleThrows=false }={}) {
  const localStorage=memory(localBlocked),sessionStorage=memory(sessionBlocked);
  const requests=[], listeners={}, location={ hash, pathname:'/arcana_table/control/',search:'',href:'' };
  const body = new Element('body');
  const document = { body, createElement: tag => new Element(tag) };
  const history = { replaceState(_state,_title,url) { location.replaced=url; location.hash=''; } };
  const fetch = async (url,opts={}) => {
    requests.push({url,opts});
    if(url.endsWith('/health')) return new Response(JSON.stringify({ok:true}));
    if(url.endsWith('/v1/session')) return new Response(JSON.stringify(authStatus===200 ?
      {ok:true,login:'AARMOU',expiresAt:1900000000} : {error:'sign-in-required'}),{status:authStatus});
    if(url.endsWith('/v1/decision')) {
      if(decisionHold) await decisionHold;
      if(decisionThrows) throw Error('network-blocked');
      return new Response(JSON.stringify(decisionBody),{status:decisionStatus});
    }
    if(url.includes('api.scryfall.com')) { if(oracleThrows) throw Error('offline'); return new Response('{}'); }
    throw Error('Unexpected network call');
  };
  const sandbox = {
    document,location,history,localStorage,sessionStorage,fetch,
    addEventListener(name,fn){(listeners[name] ||= []).push(fn);},
  };
  vm.runInNewContext(src,sandbox,{filename:'review.js'});
  const item={ pr:360, headSha:'a'.repeat(40), state:'certified', certification:'certified', readyForOwner:true, preflight:{status:'passed'},
    card:'Test Card',kind:'card',hasTest:true,draft:false,checks:[],checkRuns:[],problems:[],abilities:[],...reviewItem };
  const host = new Element('div');
  const open = () => {
    sandbox.ArcanaReview.render(host,{items:[item]});
    const cardRow = items(host).find(x => x.tagName === 'button' && x.handlers.click);
    assert.ok(cardRow, 'a classified card must have an owner review row');
    cardRow.dispatch('click');
  };
  const find = text => items(body).find(x=>x.textContent===text);
  const event = name => { for(const cb of listeners[name] || []) cb(); };
  return { location,localStorage,sessionStorage,requests,open,find,event,body,document };
}
const tick=()=>new Promise(resolve=>setImmediate(resolve));
const ID='arcana-review-session-v1';

test('OAuth fragment is consumed at startup and owner is validated before approval becomes enabled',async()=>{
  const b=browser({hash:'#review-session=encrypted.session'});
  await tick();
  assert.equal(b.localStorage.getItem(ID),'encrypted.session');
  assert.equal(b.location.hash,'','OAuth session must be stripped from address bar');
  assert.equal(b.location.replaced,'/arcana_table/control/');
  assert.ok(b.requests.some(x=>x.url.endsWith('/v1/session') &&
    x.opts.headers.Authorization==='Bearer encrypted.session'));
  b.open();
  assert.equal(b.find('Sign in with GitHub (owner)').hidden,true);
  assert.equal(b.find('Verify / Approve').disabled,false);
  assert.ok(items(b.body).some(x=>x.textContent.includes('Signed in as GitHub owner')));
  assert.equal(b.requests.some(x=>x.opts?.method==='POST'),false);
});
test('session fragment arriving after PWA resumes is captured on hashchange and pageshow',async()=>{
  const b=browser();
  b.open();
  assert.equal(b.find('Verify / Approve').disabled,true);
  b.location.hash='#review-session=late.callback';
  b.event('hashchange');
  await tick();
  assert.equal(b.localStorage.getItem(ID),'late.callback');
  assert.equal(b.location.hash,'');
  assert.equal(b.find('Sign in with GitHub (owner)').hidden,true);
  assert.equal(b.find('Verify / Approve').disabled,false);
  b.event('pageshow');
  await tick();
  assert.equal(b.find('Verify / Approve').disabled,false);
});
test('Android localStorage failure falls back to sessionStorage without leaking fragment',async()=>{
  const b=browser({hash:'#review-session=alternate.session',localBlocked:true});
  await tick();
  assert.equal(b.sessionStorage.getItem(ID),'alternate.session');
  assert.equal(b.find('Verify / Approve'),undefined,'no modal auto-open');
  b.open();
  assert.equal(b.find('Verify / Approve').disabled,false);
  assert.equal(b.location.hash,'');
});
test('browser blocking both stores gives visible explanation and forbids decisions',async()=>{
  const b=browser({hash:'#review-session=blocked.session',localBlocked:true,sessionBlocked:true});
  await tick();
  b.open();
  assert.equal(b.find('Verify / Approve').disabled,true);
  assert.equal(b.find('Request Fix').disabled,true);
  assert.equal(b.find('Sign in with GitHub (owner)').hidden,false);
  assert.ok(items(b.body).some(x=>x.textContent.includes('browser blocked session storage')));
  assert.equal(b.location.hash,'');
});
test('expired or invalid session clears it and never enables review writes',async()=>{
  const b=browser({hash:'#review-session=expired.session',authStatus:401});
  await tick();
  b.open();
  assert.equal(b.localStorage.getItem(ID),null);
  assert.equal(b.find('Verify / Approve').disabled,true);
  assert.equal(b.find('Sign in with GitHub (owner)').hidden,false);
  assert.ok(items(b.body).some(x=>x.textContent.includes('expired or could not be verified')));
  assert.equal(b.requests.some(x=>x.opts?.method==='POST'),false);
});

test('server rejection stays visible after pressing Verify / Approve, instead of being replaced by sign-in notice',async()=>{
  const b=browser({hash:'#review-session=test.session',decisionStatus:422,
    decisionBody:{error:'checks-not-green',checks:['publish']}});
  await tick();
  b.open();
  assert.equal(b.find('Verify / Approve').disabled,false);
  b.find('Verify / Approve').dispatch('click');
  await tick();
  assert.ok(items(b.body).some(x=>x.textContent.includes('Cannot approve: checks are not all passing (publish)')));
  assert.ok(!items(b.body).some(x=>x.textContent==='Signed in as GitHub owner. No card decision has been submitted.'));
  assert.equal(b.find('Verify / Approve').disabled,false,'a confirmed rejection may be retried after the blocker is fixed');
  const posts=b.requests.filter(x=>x.url.endsWith('/v1/decision'));
  assert.equal(posts.length,1);
  assert.equal(JSON.parse(posts[0].opts.body).pr,360);
  assert.equal(JSON.parse(posts[0].opts.body).sha,'a'.repeat(40));
  assert.equal(JSON.parse(posts[0].opts.body).decision,'approve');
});

test('one successful owner approval displays persistent confirmation and disables the old exact-head button',async()=>{
  const b=browser({hash:'#review-session=test.session'});
  await tick();
  b.open();
  b.find('Verify / Approve').dispatch('click');
  await tick();
  assert.ok(items(b.body).some(x=>x.textContent.includes('Approved. Recorded on GitHub for this exact commit.')));
  assert.equal(b.find('Verify / Approve').disabled,true);
  assert.equal(b.requests.filter(x=>x.url.endsWith('/v1/decision')).length,1);
});

test('unconfirmed network response warns to inspect GitHub rather than reporting generic sign-in status',async()=>{
  const b=browser({hash:'#review-session=test.session',decisionThrows:true});
  await tick();
  b.open();
  b.find('Verify / Approve').dispatch('click');
  await tick();
  assert.ok(items(b.body).some(x=>x.textContent.includes('Approval is NOT confirmed; check the PR reviews before retrying.')));
  assert.equal(b.requests.filter(x=>x.url.endsWith('/v1/decision')).length,1);
});

test('slow owner approval disables duplicate taps until first request completes',async()=>{
  let release;
  const hold=new Promise(resolve=>{release=resolve;});
  const b=browser({hash:'#review-session=test.session',decisionHold:hold});
  await tick();
  b.open();
  const button=b.find('Verify / Approve');
  button.dispatch('click');
  assert.equal(button.disabled,true);
  button.dispatch('click');
  assert.ok(items(b.body).some(x=>x.textContent.includes('Submitting to GitHub')));
  release();
  await tick();
  assert.equal(b.requests.filter(x=>x.url.endsWith('/v1/decision')).length,1);
  assert.ok(items(b.body).some(x=>x.textContent.includes('Approved. Recorded on GitHub')));
});

test('PR-read failure diagnostics tell the owner the next safe action and do not imply approval',async()=>{
  const cases=[
    [401,{error:'github-credential-rejected'},'rejected the stored OAuth credential'],
    [503,{error:'github-rate-limited',upstreamStatus:403},'limiting API requests'],
    [403,{error:'github-access-denied',upstreamStatus:403},'denied the approval service'],
    [404,{error:'github-pr-not-accessible'},'returned HTTP 404'],
    [502,{error:'github-pr-read-failed',upstreamStatus:502},'could not read the PR']
  ];
  for(const [decisionStatus,decisionBody,explanation] of cases) {
    const b=browser({hash:'#review-session=test.session',decisionStatus,decisionBody});
    await tick();
    b.open();
    b.find('Verify / Approve').dispatch('click');
    await tick();
    assert.ok(items(b.body).some(x=>x.textContent.includes(explanation)),JSON.stringify(decisionBody));
    assert.equal(b.requests.filter(x=>x.url.endsWith('/v1/decision')).length,1);
    assert.ok(items(b.body).some(x=>x.textContent.includes('No approval recorded')));
  }
});


test('real review sheet shows the canonical card title and trusted Oracle while Scryfall is offline',async()=>{
  const b=browser({oracleThrows:true,reviewItem:{card:'Zephyrim with Squad and Miracle',name:'Zephyrim',issue:703,
    oracle:{source:'trusted-queue-snapshot',issue:703,record:{name:'Zephyrim',type_line:'Creature',oracle_text:'Squad {2}'}}}});
  b.open();
  await tick();
  assert.ok(b.find('Zephyrim'));
  assert.ok(items(b.body).some(x=>x.textContent.includes('Squad {2}') && x.textContent.includes('trusted queue Oracle snapshot')));
  assert.equal(b.requests.some(x=>x.url.includes('scryfall')),false);
  assert.equal(b.requests.some(x=>x.opts.method==='POST'),false);
});
