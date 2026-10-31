import test from 'node:test';
import assert from 'node:assert/strict';
import { candidates, freshSession, receive, publicStatus, unsupportedReason } from './session.mjs';
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
test('remote combat candidates use the host supplied attacker targets and safe empty blocks',()=>{
  const attack=state({legalActions:[{
    action:{type:'DeclareAttackers',playerId:me,attackers:{}},
    validAttackers:['creature-1','creature-2'],
    mandatoryAttackers:['creature-1'],
    attackTargetsByAttacker:{'creature-1':['opponent-2'],'creature-2':['opponent-2']},
  }]});
  const attacking=candidates(attack,me);
  assert.equal(attacking.length,1);
  assert.deepEqual(attacking[0].action,{
    type:'DeclareAttackers',playerId:me,
    attackers:{'creature-1':'opponent-2','creature-2':'opponent-2'}
  });
  assert.deepEqual(candidates(state({legalActions:[{
    action:{type:'DeclareAttackers',playerId:me,attackers:{}},
    validAttackers:['creature-1'],mandatoryAttackers:['creature-1'],
    attackTargetsByAttacker:{'creature-1':[]}
  }]}),me),[],'no fabricated target for mandatory attacker');

  const blocks=state({legalActions:[{
    action:{type:'DeclareBlockers',playerId:me,blockers:{}},
    mandatoryBlockerAssignments:{}
  }]});
  assert.deepEqual(candidates(blocks,me).map(c=>c.action),[
    {type:'DeclareBlockers',playerId:me,blockers:{}}
  ]);
  assert.deepEqual(candidates(state({legalActions:[{
    action:{type:'DeclareBlockers',playerId:me},
    mandatoryBlockerAssignments:{'blocker-1':['attacker-1']}
  }]}),me),[],'must not omit mandatory blocker assignments');
  assert.deepEqual(candidates(state({legalActions:[{
    action:{type:'DeclareBlockers',playerId:'opponent',blockers:{}}
  }]}),me),[],'no opponent seat actions');
  assert.deepEqual(candidates(state({
    pendingDecision:{type:'PlayCardDecision',id:'card-choice',playerId:me},
    legalActions:[{action:{type:'DeclareAttackers',playerId:me}}]
  }),me),[],'combat never wrapped as a PlayCardResponse');
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

test('Control reports safe unsupported decision/action category, never target or private card IDs',()=>{
  const badDecision=state({pendingDecision:{
    type:'ChooseCardsDecision',id:'private-123',playerId:me,options:['top-secret-card-name']
  }});
  assert.equal(unsupportedReason(badDecision),'unsupported-decision-or-action:decision-ChooseCardsDecision');
  const badOptions=state({legalActions:[
    {action:{type:'ActivateAbility',playerId:me,cardId:'hidden-card'}},
    {action:{type:'DeclareAttackers',playerId:me}},
    {action:{type:'ActivateAbility',playerId:me}}
  ]});
  assert.equal(unsupportedReason(badOptions),'unsupported-decision-or-action:actions-ActivateAbility,DeclareAttackers');
  assert.ok(!unsupportedReason(badDecision).includes('private'));
  assert.ok(!unsupportedReason(badOptions).includes('hidden-card'));
  assert.equal(unsupportedReason(state({pendingDecision:{type:'<unsafe-token>'}})),
    'unsupported-decision-or-action:decision-unknown');
});
test('accepted receipt retains version until later host snapshot; no double counting',()=>{
  const g=freshSession('id',1,'deck',100);
  receive(g,{type:'lanSeat',strictDeck:true},101);
  receive(g,state({interactionEpoch:'offline-10',stateVersion:10}),102);
  g.awaiting={epoch:10,version:10,type:'PassPriority'};
  receive(g,{type:'actionReceipt',accepted:true,interactionEpoch:'offline-10'},103);
  assert.equal(g.receipts,1);
  assert.equal(g.transitions,0);
  assert.equal(g.acceptedVersion,10);
  receive(g,state({interactionEpoch:'offline-11',stateVersion:11}),104);
  assert.equal(g.transitions,1);
  assert.equal(g.acceptedVersion,null);
  receive(g,state({interactionEpoch:'offline-11',stateVersion:11}),105);
  assert.equal(g.transitions,1);
  g.awaiting={epoch:11,version:11,type:'PassPriority'};
  receive(g,state({interactionEpoch:'offline-12',stateVersion:12}),106);
  assert.equal(g.transitions,2);
  assert.equal(g.receipts,1,'snapshots do not prove host acceptance');
});


test('host-authored SelectCardsDecision yields bounded typed card selections and rejects constraints',()=>{
  const mk=extra=>state({pendingDecision:{type:'SelectCardsDecision',id:'c-select',playerId:me,
    options:['a','b','c'],minSelections:1,maxSelections:1,...extra}});
  const res=candidates(mk(),me);
  assert.deepEqual(res.map(o=>o.action.response),[
    {type:'CardsSelectedResponse',decisionId:'c-select',selectedCards:['a']},
    {type:'CardsSelectedResponse',decisionId:'c-select',selectedCards:['b']},
    {type:'CardsSelectedResponse',decisionId:'c-select',selectedCards:['c']},
  ]);
  assert.deepEqual(candidates(mk({minSelections:2,maxSelections:2}),me).map(o=>o.action.response.selectedCards),
    [['a','b'],['b','c']]);
  assert.deepEqual(candidates(mk({minSelections:0,maxSelections:1}),me).map(o=>o.action.response.selectedCards),
    [[],['a'],['b'],['c']]);
  assert.deepEqual(candidates(mk({options:[],minSelections:0,maxSelections:1}),me).map(o=>o.action.response.selectedCards),[[]]);
  assert.deepEqual(candidates(mk({options:['a'],minSelections:2,maxSelections:3}),me),[]);
  assert.deepEqual(candidates(mk({minSelections:2,maxSelections:1}),me),[]);
  assert.deepEqual(candidates(mk({minSelections:1,maxSelections:1,onePerCardType:true}),me),[]);
  assert.deepEqual(candidates(mk({minSelections:0,maxSelections:3,onePerCardName:true}),me).map(o=>o.action.response.selectedCards),[[]]);
  assert.deepEqual(candidates(mk({minSelections:0,maxSelections:3,minTotalManaValue:5}),me),[]);
  assert.deepEqual(candidates(mk({minSelections:2,maxSelections:2,selectionLimits:{a:2}}),me),[]);
  assert.deepEqual(candidates(mk({options:['a','a'],minSelections:1}),me),[]);
  assert.deepEqual(candidates(mk({playerId:'offline-you'}),me),[]);
  assert.deepEqual(candidates(state({interactive:false,pendingDecision:mk().pendingDecision}),me),[]);
  const many=Array.from({length:120},(_,i)=>'card-'+i);
  assert.equal(candidates(mk({options:many}),me).length,24);
  assert.ok(candidates(mk({options:many,minSelections:33,maxSelections:33}),me).length===0);
});
