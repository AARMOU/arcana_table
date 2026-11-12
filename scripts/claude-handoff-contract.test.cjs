const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const flow=fs.readFileSync('.github/workflows/arcana-claude-handoff-bridge.yml','utf8');
const doc=fs.readFileSync('docs/automation/CLAUDE-HANDOFF-BRIDGE.md','utf8');
test('Claude bridge cannot dispatch from ordinary #81 handoff or non-owner comment',()=>{
  assert.match(flow,/issue_comment:\n    types: \[created\]/);
  assert.match(flow,/github\.event\.issue\.number == 81/);
  assert.match(flow,/github\.event\.comment\.user\.login == 'AARMOU'/);
  assert.match(flow,/arcana-claude-run-authorized-v1/);
  assert.match(flow,/ARCANA_CLAUDE_HANDOFF_READY == 'true'/);
  assert.doesNotMatch(flow,/schedule:|workflow_dispatch:/);
});
test('at-most-once, no competing unclosed handoff, no retry, no card build',()=>{
  assert.match(flow,/arcana-claude-dispatch-v1 source=/);
  assert.match(flow,/arcana-claude-handoff-complete-v1 source=/);
  assert.match(flow,/already reserved/);
  assert.match(flow,/No competing session launched/);
  assert.match(flow,/No automatic retry/);
  assert.match(flow,/cancel-in-progress: false/);
  assert.doesNotMatch(flow,/gradle|assembleRelease|copilot-swe-agent|merge_pull_request/);
  assert.match(doc,/NO session|no session/i);
});
