'use strict';
const test=require('node:test'); const assert=require('node:assert/strict');const fs=require('node:fs');const os=require('node:os');const path=require('node:path');
const {classify,audit,reportMarkdown,deckEntries}=require('../deck-gap-audit.cjs');
test('exact-indexed, front-face-only and missing remain distinct',()=>{
 const ix=new Map([['one','card'],['front','card'],['a token','token']]);
 assert.equal(classify('one',ix),'indexed');
 assert.equal(classify('Front // Back',ix),'face-name-review');
 assert.equal(classify('A Token',ix),'missing');
 assert.equal(classify('Unknown',ix),'missing');
});
test('official and existing personal manifests are scanned with quantities and deduped coverage',()=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'arcana-deck-gap-'));
 try {
   const put=(p,s)=>{const f=path.join(root,p);fs.mkdirSync(path.dirname(f),{recursive:true});fs.writeFileSync(f,s);};
   put('android/app/src/main/assets/argentum/all-cards.index.tsv','Known\\tcard\\nFront\\tcard\\nToken\\ttoken\\n'.replace(/\\t/g,'\t').replace(/\\n/g,'\n'));
   put('android/app/src/main/assets/argentum/custom-cards.index.tsv','Other\tcard\n');
   put('android/app/src/main/assets/argentum/commander-decks.json',JSON.stringify([{name:'My test deck',commander:'Known',cards:{Known:1,'Front // Back':2,Unknown:3}}]));
   put('investigation/argentum-six-precons/deck-manifest.json',JSON.stringify({decks:[{name:'Official',commander:'Other',cards:[{name:'Other',quantity:1},{name:'Token',quantity:1}]}]}));
   const report=audit(root,'test-sha');
   assert.equal(report.decks.length,2);
   assert.equal(report.decks[0].slots,6);
   assert.equal(report.decks[0].missing[0].name,'Unknown');
   assert.equal(report.decks[0].faceReview[0].name,'Front // Back');
   assert.equal(report.decks[1].missing[0].name,'Token');
   assert.match(reportMarkdown(report),/Front-face-only matches/);
   assert.match(reportMarkdown(report),/Unknown/);
 } finally {fs.rmSync(root,{recursive:true,force:true});}
});
test('reject malformed or placeholder card quantities',()=>{
 assert.throws(()=>deckEntries([{name:'Bad',commander:'Known',cards:{Foo:0}}],'personal'),/Invalid/);
});

test('scheduled #321 coverage uses an owner-confirmed build pin and fails closed on branch drift',()=>{
 const workflow=fs.readFileSync(path.resolve(__dirname,'../../.github/workflows/personal-deck-gap-audit.yml'),'utf8');
 assert.match(workflow,/ARCANA_DECK_AUDIT_SOURCE_REF:\s+gpt\/b140-card-request-test-apk-20261028/);
 assert.match(workflow,/ARCANA_DECK_AUDIT_SOURCE_SHA:\s+3df12c94be3692e586f4f687bf5d6bafbb97c358/);
 assert.match(workflow,/ref:\s+\$\{\{ env\.ARCANA_DECK_AUDIT_SOURCE_SHA \}\}/);
 assert.match(workflow,/git ls-remote https:\/\/github\.com\/AARMOU\/arcana_table\.git/);
 assert.match(workflow,/\[ "\$REMOTE_SHA" != "\$SRC_SHA" \]/);
 assert.match(workflow,/--source-ref "\$ARCANA_DECK_AUDIT_SOURCE_REF@\$SRC_SHA"/);
 assert.doesNotMatch(workflow,/ref:\s+gpt\/arcana-integration-next/);
});
