'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { evaluateCreditAdmission: gate } = require('./credit-admission.cjs');
const NOW = Date.parse('2026-10-24T14:00:00Z');
const snapshot = (used=400) => ({metric:'ai_credits',period:'2026-10',account:'AARMOU',sku:'AI_CREDITS',
  used,allowance:1500,measuredAt:new Date(NOW - 5*60*1000).toISOString()});
const reserve = (id,credits=60,period='2026-10') => ({op:'reserve',id,credits,period});
const check = (x={}) => gate({events:[],expectedOperationIds:[],ledgerComplete:true,
  snapshot:snapshot(),candidateCredits:60,now:NOW,...x});
test('unknown ledger blocks even when billing appears healthy', () => {
  assert.equal(check({ledgerComplete:false}).reason,'unverified-reservation-ledger');
  assert.equal(check({events:null}).allowed,false);
  assert.equal(check({expectedOperationIds:null}).allowed,false);
});
test('wrong units, missing account, period and SKU block', () => {
  for(const field of ['metric','account','period','sku']) {
    const s=snapshot(); delete s[field];
    assert.equal(check({snapshot:s}).allowed,false,field);
  }
  assert.equal(check({snapshot:{...snapshot(),metric:'premium_requests'}}).allowed,false);
});
test('unreserved past operation blocks even with zero active slots', () => {
  assert.equal(check({expectedOperationIds:['assign:100']}).reason,'unreserved-paid-operation');
});
test('finished work stays financially reserved until evidenced settlement', () => {
  const events=[reserve('assign:100')];
  assert.equal(check({events,expectedOperationIds:['assign:100']}).outstanding,60);
});
test('candidate is counted once in addition to unsettled operations', () => {
  const events=[reserve('assign:100'),reserve('repair:200',30)];
  const d=check({events,expectedOperationIds:['assign:100','repair:200']});
  assert.equal(d.allowed,true); assert.equal(d.outstanding,90); assert.equal(d.committed,60);
  assert.equal(d.budget.ratio,550/1500);
});
test('stale billing, future timestamp, and missing usage all block', () => {
  assert.equal(check({snapshot:{...snapshot(),measuredAt:new Date(NOW-2*3600000).toISOString()}}).allowed,false);
  assert.equal(check({snapshot:{...snapshot(),measuredAt:new Date(NOW+1000).toISOString()}}).allowed,false);
  assert.equal(check({snapshot:{...snapshot(),used:null}}).allowed,false);
});
test('month rollover with unsettled previous-period liability blocks', () => {
  assert.equal(check({events:[reserve('assign:100')],expectedOperationIds:['assign:100'],
    snapshot:{...snapshot(),period:'2026-11'}}).reason,'reservation-unsettled-other-period');
});
test('settlement requires stable evidence and explicit authorization', () => {
  const e=[reserve('assign:100'),{op:'settle',id:'assign:100',credits:60,authorized:true}];
  assert.equal(check({events:e,expectedOperationIds:['assign:100']}).reason,'settlement-evidence-missing');
  e[1].evidence='billing:invoice-123';
  assert.equal(check({events:e,expectedOperationIds:['assign:100']}).outstanding,0);
  e[1].authorized=false;
  assert.equal(check({events:e,expectedOperationIds:['assign:100']}).allowed,false);
});
test('duplicate reservation and duplicate expected operation block', () => {
  assert.equal(check({events:[reserve('assign:100'),reserve('assign:100')],
    expectedOperationIds:['assign:100']}).allowed,false);
  assert.equal(check({expectedOperationIds:['assign:100','assign:100']}).reason,'invalid-operation-inventory');
});
test('reservation plus candidate pushes account into pause without new spend', () => {
  const d=check({events:[reserve('assign:100',200)],expectedOperationIds:['assign:100'],snapshot:snapshot(1200)});
  assert.equal(d.allowed,false);assert.equal(d.reason,'budget-paused');
});
test('no candidate estimate, malformed event, or incomplete ledger fails closed', () => {
  assert.equal(check({candidateCredits:0}).allowed,false);
  assert.equal(check({events:[{op:'reserve',id:'bad',credits:-1,period:'2026-10'}]}).allowed,false);
});
