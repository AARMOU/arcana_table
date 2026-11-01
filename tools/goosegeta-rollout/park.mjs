import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {writeFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
const TARGETS = {
  'arcana-table-rooms-goosegeta-test':['GOOSEGETA_OPENAI_ENABLED','GOOSEGETA_ENABLED'],
  'arcana-review':['GOOSEGETA_ENABLED']
};
export function planBindings(name,bindings) {
  assert.ok(Object.hasOwn(TARGETS,name),'Only the isolated experiment and its shared Control feature flag may change');
  assert.ok(Array.isArray(bindings));
  const names=new Set();
  for(const b of bindings) { assert.equal(typeof b.name,'string');assert.ok(!names.has(b.name),'Duplicate binding');names.add(b.name); }
  const flags=TARGETS[name];
  return [...bindings.filter(b=>!flags.includes(b.name)).map(b=>({name:b.name,type:'inherit'})),
    ...flags.map(name=>({name,type:'plain_text',text:'false'}))];
}
const canonical=v=>JSON.stringify(v,(_,x)=>x&&typeof x==='object'&&!Array.isArray(x)?Object.fromEntries(Object.keys(x).sort().map(k=>[k,x[k]])):x);
const digest=v=>createHash('sha256').update(typeof v==='string'?v:canonical(v)).digest('hex');
const others=(name,s)=>s.bindings.filter(b=>!TARGETS[name].includes(b.name)).map(b=>{
  if(b.type==='secret_text'||b.type==='secret_key') return {name:b.name,type:b.type};
  return b;
}).sort((a,b)=>a.name.localeCompare(b.name));
async function main(){
  assert.equal(process.env.OWNER_NO_ACTIVE_MATCH,'confirmed-2026-10-31');
  const account=process.env.CLOUDFLARE_ACCOUNT_ID, token=process.env.CLOUDFLARE_API_TOKEN;
  assert.ok(account&&token,'Existing deployment identity required');
  const base='https://api.cloudflare.com/client/v4/accounts/'+encodeURIComponent(account)+'/workers/scripts/';
  async function api(name,suffix,init={}){
    assert.ok(Object.hasOwn(TARGETS,name));
    const r=await fetch(base+encodeURIComponent(name)+suffix,{...init,headers:{Authorization:'Bearer '+token,...init.headers},signal:AbortSignal.timeout(30000)});
    const data=await r.json();
    // Never print upstream bodies: they may contain private configuration.
    assert.ok(r.ok&&data.success,'Cloudflare operation failed: '+r.status+' '+name+' '+suffix);
    return data.result;
  }
  const result={schema:1,operation:'owner-approved-parking',ownerConfirmedNoActiveMatch:true,checkedAt:new Date().toISOString(),services:[]};
  for(const name of Object.keys(TARGETS)){
    const before=await api(name,'/settings');
    const deployment=await api(name,'/deployments');
    const latestVersions=await api(name,'/versions');
    const versions=deployment.deployments?.[0]?.versions;
    assert.ok(Array.isArray(versions)&&versions.length===1&&versions[0].percentage===100,'Refuse a split or unknown deployment');
    assert.equal(latestVersions.items?.[0]?.id,versions[0].version_id,'Refuse inheritance from an undeployed latest version');
    const schedules=await api(name,'/schedules');
    const form=new FormData();
    form.set('settings',new Blob([JSON.stringify({bindings:planBindings(name,before.bindings)})],{type:'application/json'}));
    await api(name,'/settings',{method:'PATCH',body:form});
    const after=await api(name,'/settings');
    for(const flag of TARGETS[name]) assert.equal(after.bindings.find(b=>b.name===flag)?.text,'false','Runtime flag must read back OFF');
    assert.equal(canonical(others(name,before)),canonical(others(name,after)),'Non-Goosegeta binding drift');
    assert.equal(canonical(schedules),canonical(await api(name,'/schedules')),'Cron schedule drift');
    const afterDeployment=await api(name,'/deployments');
    result.services.push({name,flags:Object.fromEntries(TARGETS[name].map(x=>[x,false])),beforeDeployment:versions,afterDeployment:afterDeployment.deployments?.[0]?.versions,
      retainedBindings:after.bindings.filter(b=>!TARGETS[name].includes(b.name)).map(b=>({name:b.name,type:b.type})),otherBindingsFingerprint:digest(others(name,after)),cronUnchanged:true});
    console.log('OFF verified: '+name+'; other bindings, secrets and cron retained.');
  }
  for(const name of Object.keys(TARGETS)){
    const url='https://'+name+'.arcanamtg.workers.dev/health';
    assert.equal((await fetch(url,{signal:AbortSignal.timeout(20000)})).status,200,name+' health');
  }
  const denied=await fetch('https://arcana-table-rooms-goosegeta-test.arcanamtg.workers.dev/internal/goosegeta/GOOSE1',{method:'POST',signal:AbortSignal.timeout(20000)});
  assert.equal(denied.status,403);
  const review=await fetch('https://arcana-review.arcanamtg.workers.dev/v1/goosegeta?code=GOOSE1',{headers:{Origin:'https://aarmou.github.io'},signal:AbortSignal.timeout(20000)});
  assert.equal(review.status,401,'Owner authentication remains required');
  const login=await fetch('https://arcana-review.arcanamtg.workers.dev/auth/login?return='+encodeURIComponent('https://aarmou.github.io/arcana_table/control/'),{redirect:'manual',signal:AbortSignal.timeout(20000)});
  assert.equal(login.status,302,'Normal Control sign-in remains available');
  result.noProviderCall=true;result.healthAndAuthPassed=true;
  await writeFile('GOOSEGETA-PARKING-RESULT.json',JSON.stringify(result,null,2)+'\n');
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href) await main();
