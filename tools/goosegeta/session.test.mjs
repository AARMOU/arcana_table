import test from 'node:test';
import assert from 'node:assert/strict';
import { candidates, freshSession, receive, publicStatus } from './session.mjs';
import { modelRequest, modelDecision } from './model.mjs';
const me='offline-ai';
const state=(extra={})=>({type:'stateUpdate',interactive:true,interactionEpoch:'offline-2',stateVersion:2,
  legalActions:[{action:{type:'PassPriority',playerId:me},isAffordable:true}],...extra});
test('persisted epoch never replays earlier state; rejection pauses without retry',()=>{
  const g=freshSession('id',1,'deck',100);receive(g,{type:'lanSeat',strictDeck:true},100);
  receive(g,state(),101);assert.equal(g.epoch,2);const restored=JSON.parse(JSON.stringify(g));
  receive(restored,state({interactionEpoch:'offline-1'}),102);assert.equal(restored.epoch,2);
  receive(restored,{type:'error'},103);receive(restored,state({interactionEpoch:'offline-3'}),104);
  assert.equal(restored.status,'needs attention');assert.equal(restored.epoch,2);
  assert.ok(!JSON.stringify(publicStatus(restored)).includes('hostLink'));
});
test('decision candidates use only offered player, option indices and target IDs',()=>{
  const yes=candidates(state({pendingDecision:{type:'YesNoDecision',id:'d',playerId:me}}),me);
  assert.equal(yes.length,2);assert.equal(yes[0].action.response.choice,false);
  const target={type:'ChooseTargetsDecision',id:'d',playerId:me,targetRequirements:[{index:0,minTargets:1,maxTargets:1}],legalTargets:{0:['public-a','public-b']}};
  const opts=candidates(state({pendingDecision:target}),me);
  assert.deepEqual(opts.map(o=>o.action.response.selectedTargets[0]),[['public-a'],['public-b']]);
  target.targetRequirements[0].sameOwner=true;assert.deepEqual(candidates(state({pendingDecision:target}),me),[]);
  assert.deepEqual(candidates(state({pendingDecision:{type:'ChooseOptionDecision',id:'d',playerId:'opponent',options:['a']}}),me),[]);
  assert.deepEqual(candidates(state({pendingDecision:{type:'UnknownDecision',id:'d',playerId:me}}),me),[]);
});
test('noninteractive/malformed/foreign actions cannot become a model candidate',()=>{
  assert.deepEqual(candidates(state({interactive:false}),me),[]);
  assert.deepEqual(candidates(state({legalActions:[{action:{type:'CastSpell',playerId:'other'}}]}),me),[]);
  const g=freshSession('id',1,'deck',100);assert.equal(receive(g,{type:'mulliganDecision'},100).type,'keepHand');
  assert.equal(receive(g,{type:'mulliganDecision'},101),null);
});
test('model request has strict enum, no tools/storage/chat and bounded input',()=>{
  const options=candidates(state(),me);const {body,reserve}=modelRequest('approved-model',state({state:{chat:['secret']}}),options);
  assert.equal(body.store,false);assert.deepEqual(body.tools,[]);assert.ok(reserve>body.max_output_tokens);
  assert.ok(!body.input.includes('secret'));assert.deepEqual(body.text.format.schema.properties.candidateId.enum,['0']);
  assert.throws(()=>modelRequest('approved-model',state({state:{cards:{a:'x'.repeat(20000)}}}),options),/limit/);
});
test('provider fixture is validated; fabricated ids, refusal and incomplete responses fail closed',async()=>{
  const options=candidates(state(),me);const {body}=modelRequest('approved-model',state(),options);
  const reply=(text,status='completed')=>async(url,init)=>{
    assert.equal(url,'https://api.openai.com/v1/responses');assert.equal(init.redirect,'manual');
    return Response.json({status,output:[{type:'message',content:[{type:'output_text',text}]}],usage:{total_tokens:12}});
  };
  assert.equal((await modelDecision('test-key',body,options,reply('{"candidateId":"0"}'))).candidateId,'0');
  await assert.rejects(modelDecision('test-key',body,options,reply('{"candidateId":"fabricated"}')),/fabricated/);
  await assert.rejects(modelDecision('test-key',body,options,reply('{"candidateId":"0","action":"evil"}')),/schema/);
  await assert.rejects(modelDecision('test-key',body,options,reply('{}','incomplete')),/incomplete/);
  await assert.rejects(modelDecision('test-key',body,options,async()=>new Response('',{status:429})),/unavailable/);
});
