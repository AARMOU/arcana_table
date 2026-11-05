'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const landing = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
const closed = fs.readFileSync(path.join(root, 'closed-testing.html'), 'utf8');

test('Build 141 copy states the actual debug-test release status', () => {
  assert.match(landing, /Build 141 · Android test build/);
  assert.match(landing, /Debug APK not uploaded to Google Play or released publicly/);
  assert.match(landing, /Build 141 is a debug test APK, not a public release/);
  assert.doesNotMatch(landing, /Build 112 is still under development/);
  assert.doesNotMatch(landing, /Coming soon:<\/strong> Deck importing/);
});
test('Current feature copy distinguishes supported testing from public deck import', () => {
  for (const phrase of [
    'four players', 'offline AI', 'premade Commander decks',
    'local Wi-Fi', 'across networks online',
    'Public deck import is turned off',
  ]) assert.ok(landing.includes(phrase), 'Missing current feature/status: ' + phrase);
  assert.match(landing, /Additional card interactions are still in development/);
});
test('Test invitation never claims to distribute the debug APK', () => {
  assert.match(landing, /The closed-test form requests a Google Play invitation; it does not distribute this APK/);
  assert.match(landing, /form does not guarantee admission or distribute the Build 141 debug APK/);
  assert.match(landing, /not been uploaded to Google Play or released publicly/);
});
test('Historical marketing screenshots are explicitly identified as earlier captures', () => {
  assert.match(landing, /Genuine app captures · Earlier builds/);
  assert.match(landing, /These screenshots show earlier Android builds and may not match the Build 141 interface exactly/);
  assert.match(landing, /These genuine app captures are from earlier builds and may not match Build 141 exactly/);
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
