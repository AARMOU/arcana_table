import { test } from 'node:test';
import assert from 'node:assert/strict';
import worker, { sealSession } from '../src/index.mjs';
import { authorizeUnit, authorizationInfo, AUTHORIZE_LABEL } from '../src/authorize.mjs';

const ENV = { GITHUB_OAUTH_CLIENT_ID: 'id', GITHUB_OAUTH_CLIENT_SECRET: 'secret', SESSION_SECRET: 's'.repeat(40) };
const ORIGIN = 'https://aarmou.github.io';
const URL = 'https://arcana-review.example.workers.dev';
const UPDATED = '2026-10-28T19:24:00Z';
const BODY = '<!-- arcana-auto-queue-v1 -->\n## Cards\n\n- Malanthrope\n\n## Acceptance\n\n- Exact current Oracle behavior\n- Executable regression scenarios\n\nBase: gpt/arcana-integration-next\n';
function mock({ state='open', title='[Arcana Queue] Malanthrope — Cardkit SUPPORTED',
  stamp=UPDATED, extraLabels=[], assignees=[], author='github-actions[bot]', type='Bot',
  association='NONE', body=BODY, other=[], failList=false } = {}) {
  const writes=[];
  const issue = {number:172, state, title, updated_at:stamp, body, author_association:association,
    user:{login:author,type}, assignees, labels:extraLabels.map(name=>({name}))};
  const gh = async (path, opts={})=>{
    if(path==='/issues/172') return {ok:true,status:200,body:issue};
    if(path==='/issues?state=open&labels=arcana%3Aauthorize-once&per_page=100')
      return failList?{ok:false,status:503}:{ok:true,status:200,body:other};
    if(path==='/issues/172/labels' && opts.method==='POST'){
      const data=JSON.parse(opts.body);writes.push(data);issue.labels.push({name:AUTHORIZE_LABEL});
      return {ok:true,status:200,body:issue.labels};
    }
    return {ok:false,status:404};
  };
  return {gh,issue,writes};
}
test('authorizes only the selected verified issue; never calls Copilot, merge or deploy', async()=>{
  const m=mock(); const [status,result]=await authorizeUnit(m.gh,{issue:172,expectedUpdatedAt:UPDATED});
  assert.equal(status,200);assert.equal(result.state,'authorized-waiting');
  assert.deepEqual(m.writes,[{labels:[AUTHORIZE_LABEL]}]);
  assert.equal((await authorizeUnit(m.gh,{issue:172,expectedUpdatedAt:UPDATED}))[1].duplicate,true);
  assert.equal(m.writes.length,1,'duplicate click cannot add another authorization');
});
test('denies stale units, wrong targets, untrusted issues, incomplete acceptance, claimed card and conflicting pending approvals',async()=>{
  const cases=[
    [mock(),{issue:170,expectedUpdatedAt:UPDATED},404],
    [mock(),{issue:172,expectedUpdatedAt:'invalid'},400],
    [mock({state:'closed'}),{issue:172,expectedUpdatedAt:UPDATED},422],
    [mock({title:'[Card Request] Malanthrope'}),{issue:172,expectedUpdatedAt:UPDATED},422],
    [mock({author:'attacker',type:'User',association:'NONE'}),{issue:172,expectedUpdatedAt:UPDATED},422],
    [mock({body:'## Cards\nMalanthrope'}),{issue:172,expectedUpdatedAt:UPDATED},422],
    [mock({stamp:'2026-10-28T20:00:00Z'}),{issue:172,expectedUpdatedAt:UPDATED},409],
    [mock({assignees:[{login:'Copilot'}]}),{issue:172,expectedUpdatedAt:UPDATED},409],
    [mock({other:[{number:173}]}),{issue:172,expectedUpdatedAt:UPDATED},409],
    [mock({failList:true}),{issue:172,expectedUpdatedAt:UPDATED},502],
  ];
  for(const [m,request,expected] of cases){assert.equal((await authorizeUnit(m.gh,request))[0],expected);assert.equal(m.writes.length,0);}
});
test('trusted human-authored queue request is allowed and labels do not certify a card', async()=>{
  const m=mock({author:'AARMOU',type:'User',association:'OWNER',
    body:'## Cards\n- Malanthrope\n## Acceptance\nExact current Oracle behavior\nExecutable regression scenarios\nBase: gpt/arcana-integration-next\n'});
  assert.equal((await authorizationInfo(m.gh,172))[0],200);
  assert.equal((await authorizeUnit(m.gh,{issue:172,expectedUpdatedAt:UPDATED}))[0],200);
});
test('Cloudflare route enforces owner login, origin, methods and no-store; refuses all unauthenticated writes',async()=>{
  const token=await sealSession(ENV.SESSION_SECRET,{t:'oauth',login:'AARMOU',exp:Math.floor(Date.now()/1000)+600});
  const bogus=await sealSession(ENV.SESSION_SECRET,{t:'oauth',login:'attacker',exp:Math.floor(Date.now()/1000)+600});
  const infoRequest=(method,origin=ORIGIN,session=token,path='/v1/authorization?issue=172')=>new Request(URL+path,{
    method,headers:{Origin:origin,Authorization:'Bearer '+session,...(method==='POST'?{'Content-Type':'application/json'}:{})},
    ...(method==='POST'?{body:JSON.stringify({issue:172,expectedUpdatedAt:UPDATED})}:{})});
  const makeFetch=m=>async(url,init={})=>{
    const path=String(url).replace('https://api.github.com/repos/AARMOU/arcana_table','');
    const response=await m.gh(path,init);
    return new Response(JSON.stringify(response.body||{}),{status:response.status});
  };
  const m=mock();
  for(const req of [
    infoRequest('POST', 'https://evil.example',token,'/v1/authorize'),
    infoRequest('POST', ORIGIN,bogus,'/v1/authorize'),
    infoRequest('POST', ORIGIN,'tampered','/v1/authorize'),
  ]){const result=await worker.fetch(req,ENV,{},makeFetch(m));assert.ok(result.status===403||result.status===401);}
  assert.equal(m.writes.length,0);
  const info=await worker.fetch(infoRequest('GET'),ENV,{},makeFetch(m));
  assert.equal(info.status,200);assert.equal(info.headers.get('Cache-Control'),'no-store');
  const allow=await worker.fetch(infoRequest('POST',ORIGIN,token,'/v1/authorize'),ENV,{},makeFetch(m));
  assert.equal(allow.status,200);assert.equal((await allow.json()).state,'authorized-waiting');
  assert.equal(m.writes.length,1);
  const preflight=await worker.fetch(new Request(URL+'/v1/authorize',{method:'OPTIONS',
    headers:{Origin:ORIGIN,'Access-Control-Request-Method':'POST','Access-Control-Request-Headers':'authorization, content-type'}}),ENV);
  assert.equal(preflight.status,204);
});
