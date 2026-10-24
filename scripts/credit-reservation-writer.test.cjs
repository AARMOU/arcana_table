'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {reserveBeforeAttempt}=require('./credit-reservation-writer.cjs');
const event={op:'reserve',id:'issue:54:run:2',credits:60,period:'2026-10'};
const bot=body=>({body,user:{login:'github-actions[bot]',type:'Bot'},created_at:'2026-10-24T10:00:00Z',updated_at:'2026-10-24T10:00:00Z'});
function setup({failWrite=false,failReadback=false,duplicate=false}={}) {
 const comments=[];let reads=0,writes=0;
 const github={rest:{issues:{createComment:async({body})=>{writes++;if(failWrite)throw Error('timeout');comments.push(bot(body));if(duplicate)comments.push(bot(body));}}}};
 const loadComments=async()=>{reads++;if(failReadback&&reads===2)throw Error('unavailable');return comments;};
 return {github,loadComments,stats:()=>({reads,writes})};
}
const run=s=>reserveBeforeAttempt({github:s.github,loadComments:s.loadComments,owner:'AARMOU',repo:'arcana_table',issue:54,event});
test('reserve is recorded and verified before allowing next phase',async()=>{const s=setup();const r=await run(s);assert.equal(r.allowed,true);assert.equal(r.outstanding,60);});
test('write uncertainty blocks external attempt',async()=>{const s=setup({failWrite:true});assert.equal((await run(s)).reason,'reservation-write-uncertain');});
test('readback failure blocks external attempt',async()=>{const s=setup({failReadback:true});assert.equal((await run(s)).allowed,false);});
test('duplicate records block external attempt',async()=>{const s=setup({duplicate:true});assert.equal((await run(s)).allowed,false);});
test('invalid reservation never writes',async()=>{const s=setup();const r=await reserveBeforeAttempt({github:s.github,loadComments:s.loadComments,owner:'AARMOU',repo:'arcana_table',issue:54,event:{...event,credits:0}});assert.equal(r.allowed,false);assert.equal(s.stats().writes,0);});
test('missing inventory loader never writes',async()=>{const s=setup();const r=await reserveBeforeAttempt({github:s.github,owner:'AARMOU',repo:'arcana_table',issue:54,event});assert.equal(r.allowed,false);assert.equal(s.stats().writes,0);});
test('existing reservation id blocks repeat write',async()=>{const s=setup();assert.equal((await run(s)).allowed,true);assert.equal((await run(s)).allowed,false);assert.equal(s.stats().writes,1);});
