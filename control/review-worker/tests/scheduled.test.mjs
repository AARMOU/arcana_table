import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import worker from '../src/index.mjs';
import { runScheduled, SCHEDULE } from '../src/scheduled.mjs';

test('timer starts only the allowlisted workflows on main, with the token', async () => {
  const calls = [];
  const fetchImpl = async (url, init) => { calls.push({ url, init }); return new Response(null, { status: 204 }); };
  const r = await runScheduled('*/15 * * * *', { token: 't0k', fetchImpl, scheduledTime: Date.parse('2026-10-30T05:10:00Z') });
  assert.deepEqual(r, [{ workflow: 'arcana-card-review-inbox-refresh.yml', status: 204 }]);
  assert.equal(calls[0].url, 'https://api.github.com/repos/AARMOU/arcana_table/actions/workflows/arcana-card-review-inbox-refresh.yml/dispatches');
  assert.equal(calls[0].init.method, 'POST');
  assert.equal(calls[0].init.headers.Authorization, 'Bearer t0k');
  assert.deepEqual(JSON.parse(calls[0].init.body), { ref: 'main', inputs: {} });
  await runScheduled('41 * * * *', { token: 't0k', fetchImpl });
  assert.deepEqual(JSON.parse(calls[1].init.body), { ref: 'main', inputs: { scheduled: 'true' } },
    'backfill timer runs must be scheduled runs that never re-run green CI');
});

test('without a token or for an unknown cron the timer starts nothing', async () => {
  let calls = 0;
  const fetchImpl = async () => { calls++; return new Response(null, { status: 204 }); };
  assert.deepEqual(await runScheduled('*/15 * * * *', { token: '', fetchImpl }), [{ cron: '*/15 * * * *', skipped: 'no-dispatch-token' }]);
  assert.deepEqual(await runScheduled('0 0 * * *', { token: 't', fetchImpl }), []);
  assert.equal(calls, 0);
});

test('wrangler crons match the schedule and the Worker exports a scheduled handler', () => {
  const toml = readFileSync(new URL('../wrangler.toml', import.meta.url), 'utf8');
  const crons = JSON.parse(toml.match(/^crons\s*=\s*(\[.*\])$/m)[1]);
  assert.deepEqual(crons.sort(), Object.keys(SCHEDULE).sort());
  assert.equal(typeof worker.scheduled, 'function');
  for (const jobs of Object.values(SCHEDULE)) for (const j of jobs)
    assert.match(j.workflow, /^arcana-(card-review-inbox-refresh|certification-backfill|card-dispatcher|queue-feeder)\.yml$/);
});

test('the dispatcher runs at :00 and :30 and the feeder at :15 and :45, never with input overrides', async () => {
  const seen = [];
  const fetchImpl = async (url, init) => { seen.push([url.split('/workflows/')[1].split('/')[0], JSON.parse(init.body)]); return new Response(null, { status: 204 }); };
  for (const m of ['00', '15', '30', '45']) await runScheduled('*/15 * * * *', { token: 't', fetchImpl, scheduledTime: Date.parse(`2026-10-30T05:${m}:00Z`) });
  assert.deepEqual(seen.filter(([w]) => w === 'arcana-card-dispatcher.yml').length, 2);
  assert.equal(seen.filter(([w]) => w === 'arcana-card-review-inbox-refresh.yml').length, 4);
  assert.deepEqual(seen.filter(([w]) => w === 'arcana-queue-feeder.yml').length, 2, 'feeder at :15 and :45, before each dispatcher slot');
  for (const [, body] of seen) assert.deepEqual(body, { ref: 'main', inputs: {} });
});
