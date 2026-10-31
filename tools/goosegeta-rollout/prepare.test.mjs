import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync,mkdirSync,writeFileSync,readFileSync,rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { prepare,ROOM_NAME,ROOM_ORIGIN } from './prepare.mjs';
test('approved configuration isolates rooms/app and keeps OpenAI off without putting credentials in files',()=>{
 const root=mkdtempSync(join(tmpdir(),'goose-approved-'));
 try {
  mkdirSync(join(root,'control/review-worker'),{recursive:true});mkdirSync(join(root,'cloud/android/cloud-room'),{recursive:true});mkdirSync(join(root,'cloud/android/app/src/main/java/com/arcanatable'),{recursive:true});
  writeFileSync(join(root,'control/review-worker/wrangler.toml'),'name = "arcana-review"\n');
  writeFileSync(join(root,'cloud/android/cloud-room/wrangler.toml'),'name = "arcana-table-rooms"\n[vars]\nGOOSEGETA_ENABLED = "false"\nGOOSEGETA_OPENAI_ENABLED = "false"\n');
  writeFileSync(join(root,'cloud/android/app/src/main/java/com/arcanatable/TableModel.kt'),'internal const val DEFAULT_CLOUD_ROOM_ENDPOINT = "https://arcana-table-rooms.arcanamtg.workers.dev"');
  const result=prepare(root,join(root,'cloud'));assert.equal(result.apiModel,'OFF');assert.equal(result.testPackage,'com.arcanatable.goosegetatest');
  const rooms=readFileSync(join(root,'cloud/android/cloud-room/wrangler.toml'),'utf8');assert.ok(rooms.includes('name = "'+ROOM_NAME+'"'));assert.ok(rooms.includes('GOOSEGETA_OPENAI_ENABLED = "false"'));
  assert.ok(readFileSync(join(root,'control/review-worker/wrangler.toml'),'utf8').includes('service = "'+ROOM_NAME+'"'));
  assert.ok(readFileSync(join(root,'cloud/android/app/src/main/java/com/arcanatable/TableModel.kt'),'utf8').includes(ROOM_ORIGIN));
  assert.throws(()=>prepare(root,join(root,'cloud')),/baseline changed/);
 } finally {rmSync(root,{recursive:true,force:true});}
});
