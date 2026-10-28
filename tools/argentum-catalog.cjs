'use strict';
// Read-only indexed card catalog for Arcana Control. Reuse existing deck-gap audit;
// this does NOT certify Oracle behavior, add cards, or implement a new importer.
const fs=require('node:fs');
const path=require('node:path');
const {audit,norm}=require('./deck-gap-audit.cjs');
const INDEXES=[
  'android/app/src/main/assets/argentum/all-cards.index.tsv',
  'android/app/src/main/assets/argentum/custom-cards.index.tsv',
];
const priority=k=>k==='card'?2:k==='basic'?1:0;
function makeCatalog(root,sourceRef,report=audit(root,sourceRef)){
  if(!report||!Array.isArray(report.decks))throw Error('Invalid deck audit');
  const decksByCard=new Map();
  for(const deck of report.decks){
    const tag=(deck.source==='official'?'Official: ':'Personal: ')+deck.name;
    for(const card of deck.cards){
      // Only exact indexed names are associated with the catalog;
      // combined/front-face-only and absent names stay in Deck Needs.
      if(card.coverage!=='indexed')continue;
      const key=norm(card.name);
      if(!decksByCard.has(key))decksByCard.set(key,new Set());
      decksByCard.get(key).add(tag);
    }
  }
  const map=new Map();
  for(const file of INDEXES){
    const lines=fs.readFileSync(path.join(root,file),'utf8').split(/\r?\n/);
    for(const raw of lines){
      if(!raw.trim())continue;
      const fields=raw.split('\t'),name=fields[0]?.trim(),kind=fields[1]?.trim();
      if(!name||!['card','basic','token'].includes(kind))throw Error('Invalid indexed card row in '+file);
      const key=norm(name),prior=map.get(key);
      if(!prior||priority(kind)>priority(prior.kind))
        map.set(key,{name,kind});
    }
  }
  const cards=[...map.entries()].filter(([,entry])=>entry.kind!=='token')
    .map(([key,entry])=>({name:entry.name,kind:entry.kind,decks:[...(decksByCard.get(key)||[])]}))
    .sort((a,b)=>a.name.localeCompare(b.name,'en'));
  return {
    schemaVersion:1,sourceRef,generatedAt:report.generatedAt,
    methodology:'Indexed names only (all-cards + custom-cards), token-only entries excluded. Index membership does NOT prove playable Oracle behavior.',
    counts:{indexed:cards.length,withDecks:cards.filter(c=>c.decks.length).length},
    cards,
  };
}
if(require.main===module){
  const args=process.argv.slice(2),opt=key=>{const i=args.indexOf(key);return i>=0?args[i+1]:undefined;};
  const root=opt('--root')||'.',output=opt('--json'),sourceRef=opt('--source-ref');
  if(!output||!sourceRef){console.error('Usage: node tools/argentum-catalog.cjs --root <data> --source-ref <branch@sha> --json <path>');process.exit(2);}
  const result=makeCatalog(root,sourceRef);
  fs.mkdirSync(path.dirname(output),{recursive:true});
  fs.writeFileSync(output,JSON.stringify(result)+'\n');
  console.log('Cataloged '+result.cards.length+' indexed, non-token names at '+sourceRef+'. Playability remains unverified.');
}
module.exports={makeCatalog};
