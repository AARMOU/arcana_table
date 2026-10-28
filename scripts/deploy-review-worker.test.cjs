'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const workflow = fs.readFileSync(
  path.join(__dirname, '..', '.github', 'workflows', 'deploy-review-worker.yml'),
  'utf8',
);

test('review Worker deployment remains owner-initiated and production gated', () => {
  assert.match(workflow, /workflow_dispatch:/);
  assert.match(workflow, /DEPLOY_REVIEW_WORKER/);
  assert.match(workflow, /github\.ref == 'refs\/heads\/main'/);
  assert.match(workflow, /environment: production/);
  assert.match(workflow, /Worker tests before production/);
});

test('review Worker deploys code and both OAuth secrets in one guarded operation', () => {
  // --secrets-file was added in Wrangler 4.74.0. Two sequential "secret put"
  // calls create extra deployments and left the 2026-10-28 rollout half-failed.
  assert.match(workflow, /wrangler@4\.74\.0/);
  assert.match(workflow, /npx wrangler deploy --help \| grep -F -- '--secrets-file'/);
  assert.match(workflow, /umask 077/);
  assert.match(workflow, /mktemp --suffix=\.json/);
  assert.match(workflow, /trap 'rm -f "\$secrets_file"' EXIT/);
  assert.match(workflow, /GITHUB_OAUTH_CLIENT_SECRET: process\.env\.CLIENT_SECRET/);
  assert.match(workflow, /SESSION_SECRET: process\.env\.SESSION_SECRET/);
  assert.match(workflow, /npx wrangler deploy[^\n]*--secrets-file "\$secrets_file"/);
  assert.doesNotMatch(workflow, /wrangler secret put/);
  assert.doesNotMatch(workflow, /echo.*\$(?:CLIENT_SECRET|SESSION_SECRET)/);
});
