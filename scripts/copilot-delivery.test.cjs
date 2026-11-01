'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const {createCopilotDelivery}=require('./copilot-delivery.cjs');
const options=(token,fetchImpl)=>({owner:'AARMOU',repo:'arcana_table',token,fetchImpl});
const response=(status,data)=>({ok:status>=200&&status<300,status,json:async()=>data});
const body='<!-- arcana-ci-repair-router-v2 -->\n@copilot Repair exact-head failure.';
test('missing token rejects owner preflight and never posts',async()=>{
  let calls=0;
  const d=createCopilotDelivery(options('',async()=>{calls++;throw Error('unused')}));
  assert.equal((await d.preflight()).reason,'owner-delivery-token-missing');
  assert.equal((await d.post({issue_number:405,body})).reason,'owner-delivery-not-verified');
  assert.equal(calls,0);
});
test('identity must be exactly the repo owner, not merely any user or bot',async()=>{
  for (const [login,reason] of [['someone','owner-delivery-identity-not-owner'],
    ['github-actions[bot]','owner-delivery-identity-not-owner']]) {
    let calls=0;
    const d=createCopilotDelivery(options('TEST_SECRET',async()=>{calls++;return response(200,{login});}));
    assert.equal((await d.preflight()).reason,reason);
    assert.equal((await d.post({issue_number:405,body})).reason,'owner-delivery-not-verified');
    assert.equal(calls,1);
  }
});
test('invalid credentials fail closed with no repair comment',async()=>{
  const d=createCopilotDelivery(options('TEST_SECRET',async()=>response(403,{})));
  assert.equal((await d.preflight()).reason,'owner-delivery-identity-http-403');
  assert.equal((await d.post({issue_number:405,body})).ok,false);
});
test('owner-only authenticated POST is verified and single-use',async()=>{
  const urls=[],requests=[];let n=0;
  const d=createCopilotDelivery(options('TEST_SECRET',async(url,init)=>{
    urls.push(url);requests.push(init);n++;
    if(n===1)return response(200,{login:'aarmou'});
    return response(201,{id:9,user:{login:'AARMOU'},body});
  }));
  assert.equal((await d.preflight()).ok,true);
  assert.deepEqual(await d.post({issue_number:405,body}),{ok:true,id:9});
  assert.deepEqual(urls,['https://api.github.com/user',
    'https://api.github.com/repos/AARMOU/arcana_table/issues/405/comments']);
  assert.equal(requests[1].method,'POST');
  assert.deepEqual(JSON.parse(requests[1].body),{body});
  assert.equal((await d.post({issue_number:405,body})).ok,false,'no second POST without fresh identity');
  assert.equal(n,2);
});
test('POST response mismatching owner, message or id is never reported as delivered',async()=>{
  for(const data of [{id:1,user:{login:'someone'},body},{id:2,user:{login:'AARMOU'},body:'changed'},
    {user:{login:'AARMOU'},body}]){
    let n=0;
    const d=createCopilotDelivery(options('TEST_SECRET',async()=>{
      n++;return n===1?response(200,{login:'AARMOU'}):response(201,data);
    }));
    assert.equal((await d.preflight()).ok,true);
    assert.equal((await d.post({issue_number:405,body})).reason,'owner-delivery-unconfirmed');
  }
});
test('POST rate-limit or network uncertainty does not produce a success claim or leak secrets',async()=>{
  for(const issue of [403,429,503]){
    let n=0;
    const d=createCopilotDelivery(options('PRIVATE_TOKEN',async()=>{
      n++;return n===1?response(200,{login:'AARMOU'}):response(issue,{});
    }));
    await d.preflight();
    const v=await d.post({issue_number:405,body});
    assert.equal(v.ok,false);assert.equal(v.reason,'owner-delivery-post-http-'+issue);
    assert.ok(!JSON.stringify(v).includes('PRIVATE_TOKEN'));
  }
});
