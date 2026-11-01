const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync(__dirname+'/goosegeta.js','utf8');
test('parked client removes only experiment locator and invitation without network calls',()=>{
 const stores=new Map([['arcana-goosegeta-last-room-v1','GOOSE1'],['arcana-review-session-v1','OWNER-SESSION']]);
 const controls=[{disabled:false},{disabled:false}];let submit;
 const form={hidden:false,querySelectorAll:()=>controls,addEventListener:(type,fn)=>{assert.equal(type,'submit');submit=fn;}};
 const window={location:{hash:'#goose-invite=GOOSE1.private',pathname:'/control/',search:'?view=overview'},localStorage:{removeItem:k=>stores.delete(k)}};
 vm.runInNewContext(source,{window,document:{getElementById:id=>id==='gooseForm'?form:null},history:{replaceState(a,b,url){assert.equal(url,'/control/?view=overview');window.location.hash='';}},fetch:()=>{throw Error('Parked client must not make requests');}});
 assert.equal(form.hidden,true);assert.ok(controls.every(c=>c.disabled));assert.equal(window.location.hash,'');
 assert.equal(stores.has('arcana-goosegeta-last-room-v1'),false);assert.equal(stores.get('arcana-review-session-v1'),'OWNER-SESSION');
 let prevented=false;submit({preventDefault(){prevented=true;}});assert.equal(prevented,true);
});
test('parked dashboard exposes no opponent actions and preserves ordinary navigation hashes',()=>{
 const html=fs.readFileSync(__dirname+'/index.html','utf8');
 assert.match(html,/aria-label="Goosegeta parked"/);assert.match(html,/>Parked</);
 assert.doesNotMatch(html,/id="goose(?:Form|Summon|Invite|Model|Chat|Deck|Recover|Dismiss)"/);
 vm.runInNewContext(source,{window:{location:{hash:'#development'},localStorage:{removeItem(){throw Error('storage blocked');}}},document:{getElementById:()=>null},history:{replaceState(){throw Error('ordinary hash must stay');}},fetch:()=>{throw Error('no network');}});
});
