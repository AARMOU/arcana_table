(()=>{'use strict';
const $=id=>document.getElementById(id),ISSUES='https://github.com/AARMOU/arcana_table/issues/',MAX=8,KEY='arcana-control-selection-v1';
const PAGE=100,OUTSIDE='__outside_decks__';
let deckCards=[],libraryCards=[],deckSource='',librarySource='',view='decks',showCount=PAGE,deckReady=false,libraryReady=false;
const chosen=new Map(),clean=s=>String(s||'').normalize('NFKC').trim().replace(/\s+/g,' ').slice(0,150),key=s=>clean(s).toLocaleLowerCase('en');
const rank=s=>({missing:0,'face-name-review':1,indexed:2,manual:3})[s]??3;
const status=s=>({missing:'Not indexed','face-name-review':'Double-face name review',indexed:'Indexed · rules unverified',manual:'Manually entered'})[s]||'Unverified';
function elem(t,c,s){const n=document.createElement(t);if(c)n.className=c;if(s!==undefined)n.textContent=s;return n;}
function text(id,s){$(id).textContent=s;}
function number(n){return n.toLocaleString();}
function digestDecks(report){
 if(!report||!Array.isArray(report.decks)||typeof report.sourceRef!=='string')throw Error('Invalid deck scanner report');
 const found=new Map(),decks=new Set();
 for(const d of report.decks){
  if(!d||typeof d.name!=='string'||!Array.isArray(d.cards))continue;
  const deck=(d.source==='official'?'Official: ':'Personal: ')+d.name;decks.add(deck);
  for(const c of d.cards){
   if(!c||typeof c.name!=='string'||!['missing','face-name-review','indexed'].includes(c.coverage))continue;
   const name=clean(c.name),k=key(name);if(!k)continue;
   let item=found.get(k);if(!item){item={name,status:c.coverage,decks:[],key:k,origin:'deck scanner'};found.set(k,item);}
   if(rank(c.coverage)<rank(item.status))item.status=c.coverage;
   if(!item.decks.includes(deck))item.decks.push(deck);
  }
 }
 return {cards:[...found.values()].sort((a,b)=>rank(a.status)-rank(b.status)||a.name.localeCompare(b.name)),decks:[...decks].sort()};
}
function digestCatalog(report){
 if(!report||report.schemaVersion!==1||!Array.isArray(report.cards)||typeof report.sourceRef!=='string')throw Error('Invalid Argentum catalog');
 const found=new Map();
 for(const c of report.cards){
  if(!c||typeof c.name!=='string'||!['card','basic'].includes(c.kind)||!Array.isArray(c.decks))throw Error('Malformed catalog row');
  const name=clean(c.name),k=key(name);
  if(!name||found.has(k))throw Error('Duplicate/empty indexed name');
  found.set(k,{name,status:'indexed',decks:c.decks.filter(d=>typeof d==='string'),key:k,origin:'Argentum index'});
 }
 return [...found.values()].sort((a,b)=>a.name.localeCompare(b.name));
}
function save(){try{localStorage.setItem(KEY,JSON.stringify([...chosen.values()]));}catch{}}
function restore(){try{
 const a=JSON.parse(localStorage.getItem(KEY)||'[]');
 if(Array.isArray(a))for(const c of a.slice(0,MAX)){
  const name=clean(c.name);
  if(name)chosen.set(key(name),{name,status:['missing','face-name-review','indexed'].includes(c.status)?c.status:'manual',decks:Array.isArray(c.decks)?c.decks.slice(0,15).filter(x=>typeof x==='string'):[],origin:'saved selection'});
 }
}catch{}}
function selected(){
 $('selected').replaceChildren();
 if(!chosen.size)$('selected').append(elem('span','muted','Select some cards above.'));
 for(const c of chosen.values())$('selected').append(elem('span','tag',c.name));
 text('picked',chosen.size+' card'+(chosen.size===1?'':'s')+' selected');
 $('request').disabled=!chosen.size;$('clear').disabled=!chosen.size;save();
}
function choose(item,on){
 const k=key(item.name);
 if(!on){chosen.delete(k);selected();draw();return true;}
 if(!chosen.has(k)&&chosen.size>=MAX){text('notice','Limit: 8 cards per request. Submit this batch before selecting more.');draw();return false;}
 chosen.set(k,{name:item.name,status:item.status,decks:item.decks,origin:item.origin});
 text('notice','');selected();draw();return true;
}
function deckNames(){
 const found=new Set();
 for(const c of deckCards)for(const name of c.decks)found.add(name);
 return [...found].sort();
}
function updateDeckFilter(){
 const select=$('deck');select.replaceChildren();
 const empty=elem('option','',view==='decks'?'All scanned decks':'All indexed names');empty.value='';select.append(empty);
 if(view==='library'){const outside=elem('option','','Outside the 19 scanned decks');outside.value=OUTSIDE;select.append(outside);}
 for(const name of deckNames()){const option=elem('option','',name);option.value=name;select.append(option);}
 text('deckLabel',view==='decks'?'Deck':'Appears in deck');
}
function syncMode(){
 const isDeck=view==='decks';
 $('deckTab').classList.toggle('active',isDeck);$('deckTab').setAttribute('aria-pressed',String(isDeck));
 $('libraryTab').classList.toggle('active',!isDeck);$('libraryTab').setAttribute('aria-pressed',String(!isDeck));
 $('coverageGroup').hidden=!isDeck;
 text('browserHeading',isDeck?'Browse cards needed by your decks':'Browse Argentum-indexed cards');
 text('viewExplanation',isDeck?'Only cards from the 19 scanned Commander decklists. Start with missing names, then investigate double-faced names.':'Every non-token name from both Argentum indexes, including cards outside all 19 decklists. Indexed does not mean tested or playable.');
 text('coverageFootnote',isDeck?'Not indexed = absent from the two checked indexes. Indexed does not mean Oracle-verified playable.':'These cards appear in the Argentum indexes. This view cannot certify executable Oracle rules, and it does not include missing deck-only names.');
 updateDeckFilter();
 showCount=PAGE;
 draw();
}
function setMode(mode){if(mode!==view){view=mode;syncMode();}}
function draw(){
 const list=view==='decks'?deckCards:libraryCards,ready=view==='decks'?deckReady:libraryReady;
 const q=key($('search').value),deck=$('deck').value,coverage=$('coverage').value;
 const matched=list.filter(c=>(view!=='decks'||coverage==='all'||c.status===coverage)&&(!deck||(deck===OUTSIDE?!c.decks.length:c.decks.includes(deck)))&&(!q||key(c.name).includes(q)));
 text('count',ready?number(matched.length)+' matching '+(view==='decks'?'deck card names':'indexed card names')+' · showing '+number(Math.min(matched.length,showCount)):'Loading '+(view==='decks'?'deck scan':'Argentum catalog')+'…');
 text('source',ready?'Source: '+(view==='decks'?deckSource:librarySource):'Source not loaded yet');
 const el=$('entries'),scroll=el.scrollTop;el.replaceChildren();
 if(!ready){el.append(elem('p','muted','Loading source data…'));$('showMore').hidden=true;return;}
 if(!matched.length){el.append(elem('p','muted','No matching cards. Try another filter or add a card manually.'));$('showMore').hidden=true;return;}
 for(const item of matched.slice(0,showCount)){
  const l=elem('label','entry'),cb=elem('input');
  cb.type='checkbox';cb.checked=chosen.has(item.key);cb.setAttribute('aria-label','Select '+item.name);
  cb.addEventListener('change',()=>choose(item,cb.checked));
  const c=elem('span');c.append(
    elem('span','name',item.name),
    elem('span','state',status(item.status)),
    elem('span','decks',item.decks.length?item.decks.join(' · '):view==='library'?'Outside scanned decks':'Deck name unavailable')
  );
  l.append(cb,c);el.append(l);
 }
 el.scrollTop=scroll;
 $('showMore').hidden=matched.length<=showCount;
}
async function loadDecks(){
 try{
  const r=await fetch('./card-gaps.json',{cache:'no-store'});if(!r.ok)throw Error('HTTP '+r.status);
  const report=await r.json(),data=digestDecks(report);
  deckCards=data.cards;deckSource=report.sourceRef;
  const missing=deckCards.filter(c=>c.status==='missing').length,review=deckCards.filter(c=>c.status==='face-name-review').length;
  text('deckUnique',number(deckCards.length));text('deckMissing',number(missing));text('deckReview',number(review));
  deckReady=true;
  if(view==='decks')syncMode();else updateDeckFilter();
 }catch(e){
  text('deckUnique','—');text('deckMissing','—');text('deckReview','—');
  text('notice','Deck scan unavailable: '+e.message+'. You can still add a card manually.');
  deckReady=true;deckSource='Deck scan unavailable';
  if(view==='decks')syncMode();
 }
}
async function loadCatalog(){
 try{
  const r=await fetch('./argentum-catalog.json',{cache:'no-store'});if(!r.ok)throw Error('HTTP '+r.status);
  const report=await r.json();libraryCards=digestCatalog(report);librarySource=report.sourceRef;
  text('libraryTotal',number(libraryCards.length));
  text('libraryStatus','Index snapshot: '+librarySource+' · indexed names only; not rules-certified. Token-only entries excluded.');
  libraryReady=true;if(view==='library')syncMode();
 }catch(e){
  text('libraryStatus','Library catalog unavailable: '+e.message+'. Deck Needs may still work.');
  text('libraryTotal','—');libraryReady=true;librarySource='Library unavailable';
  if(view==='library')syncMode();
 }
}
async function openIssues(){
 try{
  const r=await fetch('https://api.github.com/repos/AARMOU/arcana_table/issues?state=open&per_page=100',{cache:'no-store'});if(!r.ok)throw Error('GitHub HTTP '+r.status);
  const data=await r.json();if(!Array.isArray(data))throw Error('Unexpected response');
  const visible=data.filter(x=>!x.pull_request&&/^\[(?:Arcana Queue|Card Request)\]/i.test(x.title)).slice(0,20);
  $('issues').replaceChildren();
  if(!visible.length)$('issues').append(elem('p','muted','No card requests found in the first 100 open issues.'));
  for(const issue of visible){if(!Number.isSafeInteger(issue.number))continue;const a=elem('a','','#'+issue.number+' · '+issue.title);a.href=ISSUES+issue.number;a.target='_blank';a.rel='noopener noreferrer';$('issues').append(a);}
 }catch(e){$('issues').replaceChildren(elem('p','muted','Cannot load issues right now. Use #321 directly.'));}
}
function request(){
 const picked=[...chosen.values()];if(!picked.length||picked.length>MAX)return;
 const title='[Card Request] '+picked.map(c=>c.name).join(', ').slice(0,175);
 const lines=['## Cards requested',...picked.map(c=>'- '+c.name+' — '+status(c.status)+(c.decks.length?' — '+c.decks.join('; '):' — outside scanned decks or manually entered')+' ('+(c.origin||'unverified')+')'),
  '','## Source and triage','- Deck scan source: '+(deckSource||'unavailable'),'- Argentum index source: '+(librarySource||'unavailable'),
  '- An indexed card is NOT necessarily implemented or Oracle-verified. The catalog excludes token-only names.',
  '- Check existing PRs, engine blockers, and regression acceptance before making any worker unit.','- Coordinate with #321 and #81; do not duplicate assignments.',
  '','## Request status','Owner-reviewed tracking request only. Not an [Arcana Queue] dispatchable work unit. No authorization label, paid worker, merge, or APK build requested.'];
 const url=ISSUES+'new?title='+encodeURIComponent(title)+'&body='+encodeURIComponent(lines.join('\n'));
 if(url.length>8000){text('notice','Request too long. Select fewer cards.');return;}
 window.open(url,'_blank','noopener,noreferrer');
}
$('deckTab').addEventListener('click',()=>setMode('decks'));
$('libraryTab').addEventListener('click',()=>setMode('library'));
$('search').addEventListener('input',()=>{showCount=PAGE;draw();});
$('deck').addEventListener('change',()=>{showCount=PAGE;draw();});
$('coverage').addEventListener('change',()=>{showCount=PAGE;draw();});
$('showMore').addEventListener('click',()=>{showCount+=PAGE;draw();});
$('add').addEventListener('submit',e=>{e.preventDefault();const name=clean($('manual').value);if(name.length<2){text('notice','Enter a complete card name.');return;}if(choose({name,status:'manual',decks:[],origin:'manual'},true))$('manual').value='';});
$('clear').addEventListener('click',()=>{chosen.clear();selected();draw();});
$('request').addEventListener('click',request);
restore();selected();syncMode();loadDecks();loadCatalog();openIssues();
})();