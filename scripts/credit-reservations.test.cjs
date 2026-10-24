'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { reconcile } = require('./credit-reservations.cjs');
const reserve = (id='u68:assign:1', credits=60, period='2026-10') => ({op:'reserve',id,credits,period});
test('unsettled liability survives worker completion and new aggregate billing readings', () => {
  assert.deepEqual(reconcile([reserve()],{period:'2026-10'}),{ok:true,outstanding:60,open:1});
});
test('partial settlement retains remainder', () => {
  assert.equal(reconcile([reserve(),{op:'settle',id:'u68:assign:1',credits:20,authorized:true}]).outstanding,40);
});
test('duplicate reservation fails closed', () => assert.equal(reconcile([reserve(),reserve()]).ok,false));
test('unknown settlement fails closed', () => assert.equal(reconcile([{op:'settle',id:'unknown',credits:60,authorized:true}]).ok,false));
test('unapproved settlement fails closed', () => assert.equal(reconcile([reserve(),{op:'settle',id:'u68:assign:1',credits:60}]).ok,false));
test('over-settlement fails closed', () => assert.equal(reconcile([reserve(),{op:'settle',id:'u68:assign:1',credits:61,authorized:true}]).ok,false));
test('month rollover refuses unsettled previous period', () => assert.equal(reconcile([reserve()],{period:'2026-11'}).ok,false));
test('missing ledger fails closed', () => assert.equal(reconcile(null).ok,false));
test('independent reservations do not double count', () => assert.equal(reconcile([reserve(),reserve('u69:repair:1',30)]).outstanding,90));
test('explicit full settlement clears reservation', () => assert.equal(reconcile([reserve(),{op:'settle',id:'u68:assign:1',credits:60,authorized:true}]).outstanding,0));
