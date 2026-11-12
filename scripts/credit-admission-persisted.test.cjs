'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {evaluatePersistedCreditAdmission:evaluate}=require('./credit-admission-persisted.cjs');
const period='2026-10';
const snapshot={metric:'ai_credits',period,account:'AARMOU',sku:'copilot-pro',used:100,allowance:1500,measuredAt:'2026-10-24T14:00:00Z'};
const base={period,snapshot,candidateCredits:60,now:Date.parse('2026-10-24T14:01:00Z')};
test('missing serialization fails before ledger access',async()=>assert.equal((await evaluate(base)).reason,'serialization-unverified'));
test('missing shared CI fails before ledger access',async()=>assert.equal((await evaluate({...base,serializedControl:true})).reason,'shared-ci-unverified'));
test('missing ownership fails before ledger access',async()=>assert.equal((await evaluate({...base,serializedControl:true,sharedCiVerified:true})).reason,'ownership-unverified'));
test('mismatched billing period fails before ledger access',async()=>assert.equal((await evaluate({...base,serializedControl:true,sharedCiVerified:true,ownershipVerified:true,period:'2026-09'})).reason,'billing-period-mismatch'));
test('missing historical inventory blocks shadow admission',async()=>{
 const github={paginate:async()=>[],rest:{issues:{listComments:()=>{}}}};
 const r=await evaluate({...base,github,owner:'AARMOU',repo:'arcana_table',expectedOperationIds:['assign:old:1'],ledgerComplete:true,serializedControl:true,sharedCiVerified:true,ownershipVerified:true});
 assert.equal(r.allowed,false);assert.match(r.reason,/unreserved-historical-operation/);
});
test('explicit incomplete inventory blocks shadow admission',async()=>{
 const github={paginate:async()=>[],rest:{issues:{listComments:()=>{}}}};
 const r=await evaluate({...base,github,owner:'AARMOU',repo:'arcana_table',expectedOperationIds:[],ledgerComplete:false,serializedControl:true,sharedCiVerified:true,ownershipVerified:true});
 assert.equal(r.allowed,false);assert.match(r.reason,/unverified-historical-inventory/);
});
test('verified empty inventory is only a shadow budget decision',async()=>{
 const github={paginate:async()=>[],rest:{issues:{listComments:()=>{}}}};
 const r=await evaluate({...base,github,owner:'AARMOU',repo:'arcana_table',expectedOperationIds:[],ledgerComplete:true,serializedControl:true,sharedCiVerified:true,ownershipVerified:true});
 assert.equal(r.allowed,true);assert.equal(r.reason,'shadow-budget-eligible');
});
