'use strict';
/* Arcana Commander deck coverage; uses existing shipped deck data, never imports a deck. */
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const INDEXES = [
  'android/app/src/main/assets/argentum/all-cards.index.tsv',
  'android/app/src/main/assets/argentum/custom-cards.index.tsv',
];
const DECKS = 'android/app/src/main/assets/argentum/commander-decks.json';
const OFFICIAL = 'investigation/argentum-six-precons/deck-manifest.json';
const norm = name => String(name).normalize('NFKC').trim().toLocaleLowerCase('en');
const face = name => String(name).split(/\s+\/\/\s+/)[0].trim();
function read(root, rel) { return fs.readFileSync(path.join(root, rel), 'utf8'); }
function readIndexes(root) {
  const byName = new Map();
  for (const rel of INDEXES) for (const line of read(root, rel).split(/\r?\n/)) {
    if (!line.trim()) continue;
    const [name, kind] = line.split('\t');
    if (!name || !kind) throw new Error('Malformed card index: ' + rel);
    const key = norm(name);
    if (!byName.has(key) || byName.get(key) === 'token') byName.set(key, kind);
  }
  return byName;
}
function classify(name, index) {
  const kind = index.get(norm(name));
  if (kind && kind !== 'token') return 'indexed';
  const front = face(name);
  if (front !== name && index.has(norm(front)) && index.get(norm(front)) !== 'token') return 'face-name-review';
  return 'missing';
}
function deckEntries(data, source) {
  const decks = source === 'personal' ? data : data.decks;
  if (!Array.isArray(decks)) throw new Error('Expected a list of decks');
  return decks.map(d => {
    if (!d.name || !d.commander || !d.cards) throw new Error('Missing deck fields');
    const entries = Array.isArray(d.cards) ? d.cards : Object.entries(d.cards).map(([name, quantity]) => ({ name, quantity }));
    const cards = entries.map(c => {
      const name = typeof c === 'string' ? c : c.name;
      const quantity = typeof c === 'string' ? 1 : c.quantity;
      if (!name || !Number.isSafeInteger(quantity) || quantity < 1) throw new Error('Invalid card or quantity in ' + d.name);
      return { name, quantity };
    });
    return { name: d.name, commander: d.commander, source, cards };
  });
}
function audit(root, sourceRef) {
  const index = readIndexes(root);
  const personal = deckEntries(JSON.parse(read(root, DECKS)), 'personal');
  const official = deckEntries(JSON.parse(read(root, OFFICIAL)), 'official');
  const seen = new Set();
  const decks = [...personal, ...official].map(d => {
    const tag = d.source + ':' + d.name;
    if (seen.has(tag)) throw new Error('Duplicate deck: ' + tag);
    seen.add(tag);
    const cards = d.cards.map(c => ({ ...c, coverage: classify(c.name, index) }));
    return { name: d.name, commander: d.commander, source: d.source,
      slots: cards.reduce((sum, c) => sum + c.quantity, 0),
      indexed: cards.filter(c => c.coverage === 'indexed').reduce((n,c)=>n+c.quantity,0),
      missing: cards.filter(c => c.coverage === 'missing'),
      faceReview: cards.filter(c => c.coverage === 'face-name-review'),
      cards,
    };
  });
  return { sourceRef, indexNames: index.size, generatedAt: new Date().toISOString(),
    methodology: 'Index membership and front-face matching only; not executable-rules certification',
    decks };
}
function tableValue(s) { return String(s).replace(/\|/g, '\\|').replace(/[\r\n]/g,' '); }
function reportMarkdown(a) {
  const lines = [
    '## Automated deck-index coverage snapshot',
    'Source: `' + a.sourceRef + '`. Indexes: `all-cards.index.tsv` and `custom-cards.index.tsv`.',
    '**Index membership is not playable/Oracle-certified.** Front-face-only matches require import and rules QA.',
    '',
    '| Source | Deck | Indexed slots | Missing slots | Front-face review slots |',
    '| --- | --- | ---: | ---: | ---: |',
  ];
  for (const d of a.decks) {
    const m = d.missing.reduce((n,c)=>n+c.quantity,0);
    const f = d.faceReview.reduce((n,c)=>n+c.quantity,0);
    lines.push('| '+d.source+' | '+tableValue(d.name)+' | '+d.indexed+'/'+d.slots+' | '+m+' | '+f+' |');
  }
  const groups = [{key:'missing',title:'Not indexed (genuine index gaps)'},{key:'faceReview',title:'Front face indexed; verify combined-name import and back face'}];
  for (const g of groups) {
    lines.push('', '### '+g.title);
    const byName=new Map();
    for(const d of a.decks) for(const c of d[g.key]) {
      if (!byName.has(c.name)) byName.set(c.name,[]);
      byName.get(c.name).push(d.name);
    }
    if (!byName.size) lines.push('None.');
    else for(const [name,decks] of [...byName].sort((a,b)=>a[0].localeCompare(b[0])))
      lines.push('- **'+tableValue(name)+'** — '+decks.map(tableValue).join('; '));
  }
  lines.push('', '_Generated from existing Arcana data; no new deck importer or automatic paid assignments._');
  return lines.join('\n')+'\n';
}
if (require.main === module) {
  const args = process.argv.slice(2);
  const get = k => { const i = args.indexOf(k); return i >= 0 ? args[i+1] : undefined; };
  const root=get('--root')||'.', json=get('--json'), md=get('--markdown');
  if (!json || !md) { console.error('Usage: node tools/deck-gap-audit.cjs --root <repo-data-dir> --json <output.json> --markdown <output.md>'); process.exit(2); }
  const sourceRef=get('--source-ref')||'unverified-local';
  const result=audit(root,sourceRef);
  for(const out of [json,md])fs.mkdirSync(path.dirname(out),{recursive:true});
  fs.writeFileSync(json,JSON.stringify(result,null,2)+'\n');
  fs.writeFileSync(md,reportMarkdown(result));
  console.log('Audited '+result.decks.length+' complete Commander deck manifests against '+result.indexNames+' indexed names.');
}
module.exports = { norm, face, classify, readIndexes, deckEntries, audit, reportMarkdown };
