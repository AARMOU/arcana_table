'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const landing = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const closed = fs.readFileSync(path.join(root, 'closed-testing.html'), 'utf8');

test('Build 138 copy shows the actual release state without claiming Play approval', () => {
  assert.match(landing, /Build 138 · Closed-test release candidate/);
  assert.match(landing, /Google Play acceptance or closed-testing rollout has not been confirmed/);
  assert.match(landing, /427 Android tests, zero failures and five skips/);
  assert.doesNotMatch(landing, /Coming soon:<\/strong> Deck importing/);
  assert.doesNotMatch(landing, /Build 112 is still under development/);
});
test('Main feature copy distinguishes implementation from on-device acceptance', () => {
  for (const phrase of [
    'four-seat friends lobby', 're-enabled deck importer and editor',
    'Room chat and private hands', '20 chapters of guided play',
    'original Arcana launcher logo', '94 of 100 card-name slots',
    'physical multiplayer acceptance is ongoing',
  ]) assert.ok(landing.includes(phrase), 'Missing feature/status: ' + phrase);
});
test('Play release excludes standalone debug scenarios but includes tutorial practice', () => {
  assert.match(landing, /debug builds only/);
  assert.match(landing, /not included in the Play release/);
  assert.match(landing, /Guided tutorial practice is included/);
});
test('Historical marketing screenshots are not mislabeled Build 138 captures', () => {
  assert.match(landing, /Earlier-build screenshot/);
  assert.match(landing, /not newly captured Build 138 images/);
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
  assert.match(closed, /Google Play distribution is not yet confirmed/);
});
