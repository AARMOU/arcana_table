'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const workflow = name => fs.readFileSync(`.github/workflows/${name}`, 'utf8');
const topLevelBlock = (text, key) => {
  const start = text.search(new RegExp(`^  ${key}:\\s*$`, 'm'));
  assert.notEqual(start, -1, `missing top-level ${key} block`);
  const tail = text.slice(start + 1);
  const next = tail.search(/^  [a-zA-Z_][\w-]*:\s*$/m);
  return text.slice(start, next < 0 ? undefined : start + 1 + next);
};

const scheduledRunsPerDay = cron => {
  const [minute, hour] = cron.split(' ');
  assert.equal(hour, '*');
  const minutes = minute.split(',').map(Number);
  assert.ok(minutes.every(Number.isInteger));
  return minutes.length * 24;
};

test('the three polling workflows keep one 30-minute fallback', () => {
  const files = [
    'arcana-worker-status-observer.yml',
    'arcana-queue-feeder.yml',
    'arcana-card-review-inbox-refresh.yml',
  ];
  const crons = files.map(file => {
    const block = topLevelBlock(workflow(file), 'schedule');
    const matches = [...block.matchAll(/cron:\s*'([^']+)'/g)];
    assert.equal(matches.length, 1, `${file} must have one fallback schedule`);
    return matches[0][1];
  });
  assert.deepEqual(crons, Array(3).fill('7,37 * * * *'));
  const before = 3 * scheduledRunsPerDay('7,22,37,52 * * * *');
  const after = crons.reduce((n, cron) => n + scheduledRunsPerDay(cron), 0);
  assert.equal(before - after, 144, 'the fallback change should remove at least 144 scheduled runs/day');
});

test('observer wakes on worker-state changes, not unrelated workflow completions', () => {
  const text = workflow('arcana-worker-status-observer.yml');
  const runs = topLevelBlock(text, 'workflow_run');
  assert.match(runs, /Running Copilot cloud agent/);
  assert.doesNotMatch(runs, /Arcana Card Worker Dispatcher|Arcana CI Failure Router|Arcana Queue Feeder/);
  assert.match(topLevelBlock(text, 'issues'), /opened, closed, assigned, unassigned, labeled, unlabeled/);
  assert.match(topLevelBlock(text, 'pull_request_target'), /opened, closed, reopened/);
});

test('queue feeder skips code-only synchronize events but retains completion and ready-state wakes', () => {
  const text = workflow('arcana-queue-feeder.yml');
  assert.doesNotMatch(text, /^  workflow_run:/m, 'review_requested is the single Copilot completion wake');
  const pull = topLevelBlock(text, 'pull_request_target');
  const types = pull.match(/^    types: (.+)$/m)?.[1];
  assert.equal(types, '[opened, ready_for_review, review_requested]');
});

test('review inbox refresh remains gated by exact-head publication; #81 protection checks only changed inputs', () => {
  const inbox = workflow('arcana-card-review-inbox-refresh.yml');
  assert.match(topLevelBlock(inbox, 'workflow_run'), /Arcana Independent Card Certification/);
  assert.match(inbox, /Publish exact-head/);
  assert.match(inbox, /rebuild/);

  const guard = workflow('protect-coordination-issue.yml');
  assert.match(topLevelBlock(guard, 'pull_request'), /types: \[opened, edited\]/);
  assert.doesNotMatch(topLevelBlock(guard, 'pull_request'), /synchronize|reopened/);
  assert.match(topLevelBlock(guard, 'issues'), /types: \[closed\]/);
  assert.match(guard, /github\.event\.issue\.number == 81/);
  assert.doesNotMatch(guard, /^  schedule:/m, 'the event-only safety guard must not acquire a polling timer');
});
