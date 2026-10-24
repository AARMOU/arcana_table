'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { load, append } = require('./credit-reservation-store.cjs');
const { encode } = require('./credit-reservation-comments.cjs');
const period = '2026-10';
const reservation = { op: 'reserve', id: 'assign:81:one', credits: 60, period };
const settlement = { op: 'settle', id: reservation.id, credits: 20, authorized: true, evidence: 'billing:charge20' };
const bot = (body, id = 100) => ({ id, body, user: { login: 'github-actions[bot]', type: 'Bot' },
  created_at: '2026-10-24T10:00:00Z', updated_at: '2026-10-24T10:00:00Z' });
function fixture(seed = []) {
  const comments = [...seed]; let writes = 0; let failWrite = false;
  const github = { paginate: async () => comments,
    rest: { issues: { listComments: () => {}, createComment: async ({ body }) => {
      writes++;
      if (failWrite) throw Error('uncertain-network-failure');
      const id = 200 + writes;
      comments.push(bot(body, id));
      return { data: { id } };
    } } } };
  const args = { github, owner: 'AARMOU', repo: 'arcana_table', period,
    ledgerComplete: true, expectedOperationIds: [] };
  return { github, args, comments, get writes() { return writes; }, fail: () => { failWrite = true; } };
}
test('historical inventory is required, not assumed from empty issue', async () => {
  const f = fixture();
  assert.equal((await load({ ...f.args, ledgerComplete: false })).ok, false);
  assert.equal((await load({ ...f.args, expectedOperationIds: [reservation.id] })).reason,
    'unreserved-historical-operation');
});
test('trusted existing reservation is held after worker has completed', async () => {
  const f = fixture([bot(encode(reservation))]);
  const r = await load({ ...f.args, expectedOperationIds: [reservation.id] });
  assert.equal(r.ok, true); assert.equal(r.outstanding, 60);
});
test('disabled or unserialized writer does not touch GitHub', async () => {
  const f = fixture();
  const a = { ...f.args, event: reservation, expectedOperationIds: [reservation.id] };
  await assert.rejects(append(a), /reservation-write-disabled/);
  await assert.rejects(append({ ...a, allowWrite: true }), /reservation-write-disabled/);
  assert.equal(f.writes, 0);
});
test('reserve writes before paid operation, then reads back the trusted record', async () => {
  const f = fixture();
  const r = await append({ ...f.args, event: reservation, expectedOperationIds: [reservation.id],
    allowWrite: true, serializedControl: true });
  assert.equal(r.ok, true); assert.equal(r.outstanding, 60);
  assert.equal(f.writes, 1);
});
test('duplicate reservation is rejected without a second write', async () => {
  const f = fixture([bot(encode(reservation))]);
  await assert.rejects(append({ ...f.args, event: reservation, expectedOperationIds: [reservation.id],
    allowWrite: true, serializedControl: true }), /ledger-projection-duplicate|ledger-projection-invalid/);
  assert.equal(f.writes, 0);
});
test('untrusted marker blocks writer before external action', async () => {
  const c = { ...bot(encode(reservation)), user: { login: 'outsider', type: 'User' } };
  const f = fixture([c]);
  await assert.rejects(append({ ...f.args, event: { ...reservation, id: 'assign:81:two' },
    expectedOperationIds: ['assign:81:two'], allowWrite: true, serializedControl: true }),
    /ledger-preflight-untrusted-ledger-author/);
  assert.equal(f.writes, 0);
});
test('partial settlement needs evidence and retains remaining liability', async () => {
  const f = fixture([bot(encode(reservation))]);
  const r = await append({ ...f.args, event: settlement, expectedOperationIds: [reservation.id],
    allowWrite: true, serializedControl: true });
  assert.equal(r.outstanding, 40);
});
test('duplicate settlement evidence is rejected without writing', async () => {
  const f = fixture([bot(encode(reservation)), bot(encode(settlement), 101)]);
  await assert.rejects(append({ ...f.args, event: settlement, expectedOperationIds: [reservation.id],
    allowWrite: true, serializedControl: true }), /duplicate-settlement-evidence/);
  assert.equal(f.writes, 0);
});
test('unsettled previous billing period refuses new reservation', async () => {
  const f = fixture([bot(encode({ ...reservation, period: '2026-09' }))]);
  await assert.rejects(append({ ...f.args, event: { ...reservation, id: 'assign:81:two' },
    expectedOperationIds: [reservation.id, 'assign:81:two'], allowWrite: true, serializedControl: true }),
    /ledger-preflight-unsettled-other-period/);
  assert.equal(f.writes, 0);
});
test('uncertain write outcome is never retried', async () => {
  const f = fixture(); f.fail();
  await assert.rejects(append({ ...f.args, event: reservation, expectedOperationIds: [reservation.id],
    allowWrite: true, serializedControl: true }), /uncertain-network-failure/);
  assert.equal(f.writes, 1);
});
test('post-write inventory failure blocks success even after comment creation', async () => {
  const f = fixture();
  const orig = f.github.paginate; let reads = 0;
  f.github.paginate = async () => { reads++; return reads === 1 ? [] : []; };
  await assert.rejects(append({ ...f.args, event: reservation, expectedOperationIds: [reservation.id],
    allowWrite: true, serializedControl: true }), /reservation-write-unverified/);
  assert.equal(f.writes, 1);
  f.github.paginate = orig;
});
test('incomplete legacy inventory cannot be used to claim readiness', async () => {
  const f = fixture();
  const r = await load({ ...f.args, expectedOperationIds: ['assign:old:unknown'] });
  assert.equal(r.ok, false);
});

test('write acknowledgement must match a trusted read-back comment ID', async () => {
  const f = fixture();
  const original = f.github.rest.issues.createComment;
  f.github.rest.issues.createComment = async args => {
    await original(args);
    return { data: { id: 999999 } };
  };
  await assert.rejects(append({ ...f.args, event: reservation, expectedOperationIds: [reservation.id],
    allowWrite: true, serializedControl: true }), /reservation-write-unverified/);
  assert.equal(f.writes, 1);
});
