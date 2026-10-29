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
function browser({ hash='', localBlocked=false, sessionBlocked=false, authStatus=200 }={}) {
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
    if(url.includes('api.scryfall.com')) return new Response('{}');
    throw Error('Unexpected network call');
  };
  const sandbox = {
    document,location,history,localStorage,sessionStorage,fetch,
    addEventListener(name,fn){(listeners[name] ||= []).push(fn);},
  };
  vm.runInNewContext(src,sandbox,{filename:'review.js'});
  const item={ pr:360, headSha:'a'.repeat(40), state:'certified', certification:'certified',
    card:'Test Card',kind:'card',hasTest:true,draft:false,checks:[],checkRuns:[],problems:[],abilities:[] };
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
