'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
test('billing watch only has a main-branch change trigger and never enables dispatch',()=>{
 const s=fs.readFileSync('.github/workflows/arcana-copilot-billing-watch.yml','utf8');
 assert.match(s,/on:\n  push:\n    branches: \[main\]/);
 assert.match(s,/paths:\n      - '\.github\/workflows\/arcana-copilot-billing-watch\.yml'/);
 assert.match(s,/scripts\/billing-monitor\.cjs/);
 assert.match(s,/scripts\/usage-gate\.cjs/);
 assert.match(s,/workflow_dispatch:/);
 assert.match(s,/schedule:/);
 assert.doesNotMatch(s,/ARCANA_AUTOMATION_READY|copilot-swe-agent/);
});
