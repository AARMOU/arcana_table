/** AUTHORIZED ISOLATED TRANSPORT SMOKE ONLY. Synthetic host packets; NOT Android/Argentum proof. */
import assert from 'node:assert/strict';
import { pack, unpack } from '../../cloud-source/android/cloud-room/src/goose-wire.mjs';
const origin='https://arcana-table-rooms-goosegeta-test.arcanamtg.workers.dev';
const key=process.env.GOOSE_CONTROL_KEY;
assert.ok(key && key.length>=32,'Internal test credential missing');
const code='G'+crypto.randomUUID().replaceAll('-','').slice(0,7).toUpperCase();
const socket=new WebSocket(origin.replace(/^http/,'ws')+'/room/'+code);
const queue=[],waiters=[];
socket.addEventListener('message',e=>{const p=JSON.parse(e.data);const i=waiters.findIndex(w=>w.match(p));if(i<0)queue.push(p);else{const w=waiters.splice(i,1)[0];clearTimeout(w.timer);w.resolve(p);}});
const send=p=>socket.send(JSON.stringify(p));
function wait(match){const i=queue.findIndex(match);if(i>=0)return Promise.resolve(queue.splice(i,1)[0]);return new Promise((resolve,reject)=>{const w={match,resolve,timer:setTimeout(()=>reject(Error('isolated smoke response timeout')),10000)};waiters.push(w);});}
const control=async input=>{const r=await fetch(origin+'/internal/goosegeta/'+code,{method:input?'POST':'GET',headers:{Authorization:'Bearer '+key},...(input?{body:JSON.stringify(input)}:{})});assert.equal(r.status,200);return r.json();};
const game=async(p,seq)=>send({type:'relay',to:1,seq,data:await pack({...p,_relayLink:'rollout-synthetic-host'})});
async function outgoing(type){for(;;){const p=await wait(p=>p.type==='relay');const m=await unpack(p.data);if(m.type===type)return m;}}
try {
  await new Promise((resolve,reject)=>{socket.addEventListener('open',resolve,{once:true});socket.addEventListener('error',()=>reject(Error('isolated socket failed')),{once:true});});
  send({type:'create',code,password:'arcana-argentum-'+code,name:'SYNTHETIC DEPLOY SMOKE'});await wait(p=>p.type==='welcome');
  send({type:'relaySeats',kinds:['friend']});await wait(p=>p.type==='relay-seats-ack');
  send({type:'goosegetaInvite',seat:1,requestId:'rollout-smoke'});const invite=await wait(p=>p.type==='goosegeta-invite');assert.ok(invite.capability);
  const session=await control({action:'summon',capability:invite.capability,requestId:crypto.randomUUID(),deckId:'goosegeta-glue',model:false,chatMode:'ruthless'});
  assert.equal(session.status,'joining');assert.equal(session.model,'off');
  const hello=await outgoing('lanHello');assert.equal(hello.deck.length,99);assert.equal(hello.strictDeck,true);
  await game({type:'lanSeat',strictDeck:true,seat:'offline-ai'},1);await outgoing('lanReady');
  await game({type:'mulliganDecision',hand:['FIXTURE-ONLY']},2);await outgoing('keepHand');
  await game({type:'stateUpdate',interactive:true,stateVersion:1,interactionEpoch:'offline-1',legalActions:[{action:{type:'PassPriority',playerId:'offline-ai'},isAffordable:true}]},3);
  assert.equal((await outgoing('submitAction')).action.type,'PassPriority');
  await game({type:'arcanaChat',id:1,seat:0,text:'Synthetic test taunt'},4);assert.match((await outgoing('arcanaChatSend')).text,/HONK/);
  const stopped=await control({action:'dismiss',sessionId:session.sessionId});assert.equal(stopped.status,'stopped');await outgoing('arcanaBye');
  console.log('PASS isolated deployed synthetic transport: invite/deck/ready/keep/pass/chat/dismiss; model OFF. NOT Android gameplay evidence.');
} finally {if(socket.readyState===WebSocket.OPEN)send({type:'leave'});socket.close();}
