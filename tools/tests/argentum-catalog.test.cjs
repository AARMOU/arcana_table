'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {makeCatalog}=require('../argentum-catalog.cjs');
function fixture(fn){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'arcana-catalog-'));
 const put=(rel,content)=>{const dst=path.join(root,rel);fs.mkdirSync(path.dirname(dst),{recursive:true});fs.writeFileSync(dst,content);};
 try{
  put('android/app/src/main/assets/argentum/all-cards.index.tsv','Known\tcard\nFront\tcard\nToken Only\ttoken\nDuplicate\ttoken\nBasic Example\tbasic\n');
  put('android/app/src/main/assets/argentum/custom-cards.index.tsv','Known\tcard\nDuplicate\tcard\n');
  put('android/app/src/main/assets/argentum/commander-decks.json',JSON.stringify([{name:'My deck',commander:'Known',cards:{Known:1,'Front // Back':1,'Missing Card':1}}]));
  put('investigation/argentum-six-precons/deck-manifest.json',JSON.stringify({decks:[{name:'Official test',commander:'Known',cards:[{name:'Known',quantity:1},{name:'Duplicate',quantity:1}]}]}));
  fn(root);
 }finally{fs.rmSync(root,{recursive:true,force:true});}
}
test('full catalog includes indexed outside-deck cards but omits missing and token-only entries',()=>fixture(root=>{
 const out=makeCatalog(root,'integration@abc123');
 assert.equal(out.schemaVersion,1);
 assert.equal(out.sourceRef,'integration@abc123');
 assert.deepEqual(out.cards.map(x=>x.name).sort(),['Basic Example','Duplicate','Front','Known'].sort());
 assert.equal(out.counts.indexed,4);
 assert.equal(out.counts.withDecks,2);
 assert.equal(out.cards.find(x=>x.name==='Basic Example').decks.length,0);
 assert.equal(out.cards.find(x=>x.name==='Duplicate').kind,'card');
 assert.deepEqual(out.cards.find(x=>x.name==='Known').decks.sort(),['Official: Official test','Personal: My deck'].sort());
 assert.equal(out.cards.find(x=>x.name==='Front').decks.length,0,'front-face-only alias is not exact indexed deck membership');
 assert.match(out.methodology,/does NOT prove playable/);
}));
test('invalid index row fails closed rather than publishing a partial catalog',()=>fixture(root=>{
 fs.appendFileSync(path.join(root,'android/app/src/main/assets/argentum/custom-cards.index.tsv'),'Malformed Without Tab\n');
 assert.throws(()=>makeCatalog(root,'integration@abc123'),/Malformed card index|Invalid indexed card row/);
}));
