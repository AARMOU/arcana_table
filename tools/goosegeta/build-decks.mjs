import { readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { loadDecks, runtimeCardIndex, validateDeck } from './logic.mjs';
const paths = ['all-cards.index.tsv','custom-cards.index.tsv'].map(n => new URL('../../android/app/src/main/assets/argentum/'+n,import.meta.url));
const index = runtimeCardIndex(paths);
const bundle = { schema:1, runtimeIndexes: paths.map(p => ({name:p.pathname.split('/').pop(),sha256:createHash('sha256').update(readFileSync(p)).digest('hex')})),
  decks:loadDecks().map(d => ({id:d.id,name:d.name,...validateDeck(d,index)})) };
const target = new URL('../../android/cloud-room/src/goose-decks.json',import.meta.url);
const text = JSON.stringify(bundle,null,2)+'\n';
if (process.argv.includes('--check')) { if (readFileSync(target,'utf8') !== text) throw Error('Guest deck bundle differs from current runtime indexes; regenerate.'); }
else writeFileSync(target,text);
