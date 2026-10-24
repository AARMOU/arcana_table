'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {reserveBeforeAttempt}=require('./credit-reservation-writer.cjs');
const event={op:'reserve',id:'issue:54:run:2',credits:60,period:'2026-10'};
const invoke=(overrides={})=>reserveBeforeAttempt({issue:81,event,...overrides});
test('legacy writer API is disabled before any write',async()=>{
 let writes=0;
 const github={rest:{issues:{createComment:async()=>{writes++;}}}};
 const r=await invoke({github,loadComments:async()=>[]});
 assert.equal(r.allowed,false);assert.equal(r.reason,'legacy-writer-disabled');assert.equal(writes,0);
});
test('wrong issue cannot write reservations',async()=>{
 const r=await invoke({issue:54});assert.equal(r.allowed,false);
});
test('default adapter fails closed without explicit authorization',async()=>{
 const r=await invoke();assert.equal(r.allowed,false);
});
test('invalid event rejected without writes',async()=>{
 const r=await invoke({event:{...event,op:'settle'}});assert.equal(r.allowed,false);
});
test('unverified historical inventory blocks writes',async()=>{
 let writes=0;
 const github={paginate:async()=>[],rest:{issues:{listComments:()=>{},createComment:async()=>{writes++;}}}};
 const r=await invoke({github,expectedOperationIds:[event.id],ledgerComplete:false,allowWrite:true,serializedControl:true});
 assert.equal(r.allowed,false);assert.equal(writes,0);
});
test('missing serialized lock blocks writes',async()=>{
 let writes=0;
 const github={rest:{issues:{createComment:async()=>{writes++;}}}};
 const r=await invoke({github,expectedOperationIds:[event.id],ledgerComplete:true,allowWrite:true});
 assert.equal(r.allowed,false);assert.equal(writes,0);
});
