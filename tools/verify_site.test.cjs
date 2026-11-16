'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const landing = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const closed = fs.readFileSync(path.join(root, 'closed-testing.html'), 'utf8');

test('Beta 0.35.0 copy states the actual Play testing status', () => {
  assert.match(landing, /Beta 0\.35\.0-beta1 · Google Play testing/);
  assert.match(landing, /Uploaded to Google Play for testers\. Not a public release\./);
  assert.match(landing, /It is not a public release, and joining the closed test requires an invitation/);
  assert.doesNotMatch(landing, /Build 1\d\d/);
  assert.doesNotMatch(landing, /debug APK/i);
});
test('Current feature copy lists what the beta supports and that it is a beta', () => {
  for (const phrase of [
    'four players', 'Offline AI', 'official Commander precons',
    'local Wi-Fi', 'Online rooms', 'Import a deck list',
    'Forge is licensed under GPL-3.0',
    'https://github.com/AARMOU/arcana-chimera/releases/tag/chimera-0.35.0-beta1',
  ]) assert.ok(landing.includes(phrase), 'Missing current feature/status: ' + phrase);
  assert.match(landing, /It is a beta, so some cards and prompts may not work as expected/);
});
test('Test invitation never promises admission or a public listing', () => {
  assert.match(landing, /Requesting one does not guarantee admission/);
  assert.match(landing, /Signing up does not guarantee admission/);
  assert.match(landing, /The beta is not publicly listed/);
});
test('Historical marketing screenshots are explicitly identified as earlier captures', () => {
  assert.match(landing, /Genuine app captures · Earlier builds/);
  assert.match(landing, /These screenshots show earlier Android builds and do not match the Beta 0\.35\.0 interface exactly/);
  assert.match(landing, /These genuine app captures are from earlier builds and do not match the Beta 0\.35\.0 interface exactly/);
});
test('All same-page anchor links point to unique IDs', () => {
  const ids = [...landing.matchAll(/\bid="([^"]+)"/g)].map(x => x[1]);
  assert.equal(ids.length, new Set(ids).size, 'duplicate section ID');
  for (const [,anchor] of landing.matchAll(/href="#([^"]+)"/g)) {
    assert.ok(ids.includes(anchor), 'missing section ID: ' + anchor);
  }
});
test('Referenced local links and assets exist in the site source tree', () => {
  for (const [pageName,content] of [['index.html',landing], ['closed-testing.html',closed]]) {
    for (const [,url] of content.matchAll(/(?:href|src)="(\.\/[^"#?]+)(?:[?#][^"]*)?"/g)) {
      if (url === './') continue;
      const relative = url.slice(2);
      assert.ok(!relative.split('/').includes('..'), 'invalid local URL: ' + url);
      assert.ok(fs.existsSync(path.join(root, relative)), pageName + ' points to missing ' + url);
    }
  }
});
test('Closed testing keeps the existing external waitlist form and invitation caveat', () => {
  assert.match(closed, /1FAIpQLScdE0eya-TsaCtqfxZQi_QhWWn095wr1r4lRX49i-KEoDTA3A/);
  assert.match(closed, /does not guarantee an invitation/);
  assert.match(closed, /uploaded to Google Play for testers and is not a public release/);
  assert.match(closed, /href="\.\/#current-build"/);
});
