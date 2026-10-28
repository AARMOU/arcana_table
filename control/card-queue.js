(()=>{'use strict';
const $=id=>document.getElementById(id),ISSUES='https://github.com/AARMOU/arcana_table/issues/',MAX=8,KEY='arcana-control-selection-v1';
let cards=[],source='Unavailable';
const chosen=new Map(),clean=s=>String(s||'').normalize('NFKC').trim().replace(/\s+/g,' ').slice(0,150),key=s=>clean(s).toLowerCase();
const rank=s=>({missing:0,'face-name-review':1,indexed:2,manual:3})[s]??3;
const status=s=>({missing:'Not indexed','face-name-review':'Double-face review',indexed:'Indexed · rules unverified',manual:'Manually entered'})[s]||'Unverified';
function elem(t,c,s){const n=document.createElement(t);if(c)n.className=c;if(s!==undefined)n.textContent=s;return n;}
function text(id,s){$(id).textContent=s;}
function digest(report){
 if(!report||!Array.isArray(report.decks)||typeof report.sourceRef!=='string')throw Error('Invalid scanner report');
 const found=new Map(),decks=new Set();
 for(const d of report.decks){
  if(!d||typeof d.name!=='string'||!Array.isArray(d.cards))continue;
  const deck=(d.source==='official'?'Official: ':'Personal: ')+d.name;decks.add(deck);
  for(const c of d.cards){
   if(!c||typeof c.name!=='string'||!['missing','face-name-review','indexed'].includes(c.coverage))continue;
   const name=clean(c.name),k=key(name);if(!k)continue;
   let item=found.get(k);if(!item){item={name,status:c.coverage,decks:[],key:k};found.set(k,item);}
   if(rank(c.coverage)<rank(item.status))item.status=c.coverage;
   if(!item.decks.includes(deck))item.decks.push(deck);
  }
 }
 return {cards:[...found.values()].sort((a,b)=>rank(a.status)-rank(b.status)||a.name.localeCompare(b.name)),decks:[...decks].sort()};
}
function save(){try{localStorage.setItem(KEY,JSON.stringify([...chosen.values()]));}catch{}}
function restore(){try{const a=JSON.parse(localStorage.getItem(KEY)||'[]');if(Array.isArray(a))for(const c of a.slice(0,MAX)){const name=clean(c.name);if(name)chosen.set(key(name),{name,status:['missing','face-name-review','indexed'].includes(c.status)?c.status:'manual',decks:Array.isArray(c.decks)?c.decks.slice(0,15):[]});}}catch{}}
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
 chosen.set(k,{name:item.name,status:item.status,decks:item.decks});
 text('notice','');selected();draw();return true;
}
function draw(){
 const q=key($('search').value),deck=$('deck').value,coverage=$('coverage').value;
 const matched=cards.filter(c=>(coverage==='all'||c.status===coverage)&&(!deck||c.decks.includes(deck))&&(!q||key(c.name).includes(q)));
 text('count',matched.length+' matching card'+(matched.length===1?'':'s'));
 $('entries').replaceChildren();
 if(!matched.length){$('entries').append(elem('p','muted',cards.length?'No cards match these filters.':'No scanner data loaded. You can still add a name manually.'));return;}
 for(const item of matched.slice(0,180)){
  const l=elem('label','entry'),cb=elem('input');cb.type='checkbox';cb.checked=chosen.has(item.key);cb.setAttribute('aria-label','Select '+item.name);cb.addEventListener('change',()=>choose(item,cb.checked));
  const c=elem('span');c.append(elem('span','name',item.name),elem('span','state',status(item.status)),elem('span','decks',item.decks.join(' · ')));
  l.append(cb,c);$('entries').append(l);
 }
 if(matched.length>180)$('entries').append(elem('p','muted','Showing first 180. Narrow your search to see more.'));
}
async function coverage(){
 try{
  const r=await fetch('./card-gaps.json',{cache:'no-store'});if(!r.ok)throw Error('HTTP '+r.status);
  const report=await r.json(),data=digest(report);cards=data.cards;source=report.sourceRef;
  for(const d of data.decks){const opt=elem('option','',d);opt.value=d;$('deck').append(opt);}
  const time=Date.parse(report.generatedAt);text('source','Source: '+source+' · scan '+(Number.isFinite(time)?new Date(time).toLocaleString():'date unknown'));
 }catch(e){text('source','Scanner feed unavailable ('+e.message+'). Add names manually or open #321.');}
 draw();
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
 const lines=['## Cards requested',...picked.map(c=>'- '+c.name+' — '+status(c.status)+(c.decks.length?' — '+c.decks.join('; '):' — manually entered')),'','## Source and triage','- Scanner source: '+source,'- Index presence does not certify Oracle rules behavior.','- Check existing PRs, engine capability blockers, and regression acceptance before any queue work unit.','- Coordinate with #321 and #81. Do not duplicate existing assignments.','','## Request status','Owner-reviewed tracking request only. Not an [Arcana Queue] dispatchable work unit. No authorizing label, paid worker, merge or APK build requested.'];
 const url=ISSUES+'new?title='+encodeURIComponent(title)+'&body='+encodeURIComponent(lines.join('\n'));
 if(url.length>8000){text('notice','Request too long. Please select fewer cards.');return;}
 window.open(url,'_blank','noopener,noreferrer');
}
$('search').addEventListener('input',draw);$('deck').addEventListener('change',draw);$('coverage').addEventListener('change',draw);
$('add').addEventListener('submit',e=>{e.preventDefault();const name=clean($('manual').value);if(name.length<2){text('notice','Enter a complete card name.');return;}if(choose({name,status:'manual',decks:[]},true))$('manual').value='';});
$('clear').addEventListener('click',()=>{chosen.clear();selected();draw();});
$('request').addEventListener('click',request);
restore();selected();coverage();openIssues();
})();