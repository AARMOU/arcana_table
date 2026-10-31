/** Paid-Goosegeta PREPARATION checks only. Provider responses are local fixtures: ZERO API spend. */
import test from 'node:test';
import assert from 'node:assert/strict';
import { modelRequest, modelDecision, modelChat } from './model.mjs';
import { chatRequest } from './banter.mjs';

const me = 'offline-ai';
const options = [
  {id:'0',action:{type:'PlayLand',playerId:me,cardId:'safe-land'}},
  {id:'1',action:{type:'CastSpell',playerId:me,cardId:'safe-spell'}},
  {id:'2',action:{type:'PassPriority',playerId:me}},
];
const packet = {
  type:'stateUpdate', interactive:true, interactionEpoch:'offline-17',
  stateVersion:17, pendingDecision:null,
  state:{turnNumber:4,phase:'PRECOMBAT MAIN',
    cards:{'safe-land':{manaValue:0},'safe-spell':{manaValue:3}},
    chat:['PRIVATE CHAT MUST NOT LEAVE THE PHONE'],
    players:[{name:'GOOSEGETA',life:40}]},
};
function fixture(value,{status='completed',usage=42,httpStatus=200}={}) {
  return async (url, init) => {
    assert.equal(url,'https://api.openai.com/v1/responses');
    assert.equal(init.redirect,'manual');
    assert.equal(init.method,'POST');
    assert.ok(init.headers.Authorization.startsWith('Bearer '));
    const posted = JSON.parse(init.body);
    assert.equal(posted.store,false);
    assert.deepEqual(posted.tools,[]);
    return Response.json({status,output:[{type:'message',content:[{type:'output_text',text:JSON.stringify(value)}]}],
      usage:{total_tokens:usage}},{status:httpStatus});
  };
}

test('Luna uses bounded non-reasoning structured output over only allowed actions',()=>{
  const {body,reserve}=modelRequest('gpt-6-luna',packet,options);
  assert.deepEqual(body.reasoning,{effort:'none'});
  assert.equal(body.model,'gpt-6-luna');
  assert.equal(body.max_output_tokens,256);
  assert.equal(body.store,false);
  assert.deepEqual(body.tools,[]);
  assert.equal(body.text.format.type,'json_schema');
  assert.equal(body.text.format.strict,true);
  assert.deepEqual(body.text.format.schema.properties.candidateId.enum,['0','1','2']);
  assert.ok(reserve>4096 && Number.isSafeInteger(reserve));
  assert.ok(!body.input.includes('PRIVATE CHAT MUST NOT LEAVE THE PHONE'));
  assert.match(body.instructions,/commander synergies/i);
  assert.match(body.instructions,/untrusted data/i);
  assert.ok(!body.instructions.includes('API key'));
  // Other configured models aren't silently given Luna-specific request parameters.
  assert.equal(modelRequest('approved-model',packet,options).body.reasoning,undefined);
});

test('provider may only choose an offered ID; failures and bad output never become an action',async()=>{
  const {body}=modelRequest('gpt-6-luna',packet,options);
  const selected=await modelDecision('LOCAL-DUMMY-NOT-A-KEY',body,options,fixture({candidateId:'1'}));
  assert.deepEqual(selected,{candidateId:'1',usage:42});
  await assert.rejects(modelDecision('LOCAL-DUMMY-NOT-A-KEY',body,options,fixture({candidateId:'999'})),/fabricated/);
  await assert.rejects(modelDecision('LOCAL-DUMMY-NOT-A-KEY',body,options,fixture({candidateId:'1',extra:true})),/schema/);
  await assert.rejects(modelDecision('LOCAL-DUMMY-NOT-A-KEY',body,options,fixture({candidateId:'0'},{status:'incomplete'})),/incomplete/);
  await assert.rejects(modelDecision('LOCAL-DUMMY-NOT-A-KEY',body,options,fixture({candidateId:'0'},{httpStatus:429})),/unavailable/);
  await assert.rejects(modelDecision('LOCAL-DUMMY-NOT-A-KEY',body,options,async()=>{throw Error('network-down')}),/network-down/);
});

test('model chat uses same no-tools boundary; multi-line or oversized speech is refused',async()=>{
  const {body,reserve}=chatRequest('gpt-6-luna','ruthless',{kind:'gloat',text:'Host accepted a legal spell.'});
  assert.equal(body.store,false);
  assert.deepEqual(body.tools,[]);
  assert.ok(reserve>4096);
  const reply=await modelChat('LOCAL-DUMMY-NOT-A-KEY',body,fixture({text:'HONK. My spell resolved, meatsack.'}));
  assert.match(reply.text,/HONK/);
  await assert.rejects(modelChat('LOCAL-DUMMY-NOT-A-KEY',body,fixture({text:'first line\nsecond line'})),/schema/);
  await assert.rejects(modelChat('LOCAL-DUMMY-NOT-A-KEY',body,fixture({text:'X'.repeat(301)})),/schema/);
});

test('unbounded or malicious state fails before any API request',()=>{
  assert.throws(()=>modelRequest('gpt-6-luna',{...packet,state:{cards:{x:'Z'.repeat(20000)}}},options),/input-limit/);
  assert.throws(()=>modelRequest('gpt-6-luna',packet,[]),/model-input/);
  assert.throws(()=>modelRequest('gpt-6-luna',packet,Array.from({length:33},(_,i)=>({id:String(i),action:options[0].action}))),/model-input/);
  assert.throws(()=>modelRequest('bad/model/name',packet,options),/model-input/);
});
