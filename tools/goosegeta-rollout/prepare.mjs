import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
export const ROOM_NAME = 'arcana-table-rooms-goosegeta-test';
export const ROOM_ORIGIN = 'https://'+ROOM_NAME+'.arcanamtg.workers.dev';
export function prepare(root, cloud) {
  const review=resolve(root,'control/review-worker/wrangler.toml');
  const old=readFileSync(review,'utf8');
  if (!old.includes('name = "arcana-review"') || /\n\[vars\]\n/.test(old)) throw Error('Review config baseline changed');
  writeFileSync(review,old+'\n[vars]\nGOOSEGETA_ENABLED = "true"\n\n[[services]]\nbinding = "GOOSEGETA_ROOMS"\nservice = "'+ROOM_NAME+'"\n');
  const rooms=resolve(cloud,'android/cloud-room/wrangler.toml');
  const roomText=readFileSync(rooms,'utf8');
  if (!roomText.includes('GOOSEGETA_OPENAI_ENABLED = "false"') || !roomText.includes('name = "arcana-table-rooms"')) throw Error('Room baseline changed');
  writeFileSync(rooms,roomText.replace('name = "arcana-table-rooms"','name = "'+ROOM_NAME+'"'));
  const model=resolve(cloud,'android/app/src/main/java/com/arcanatable/TableModel.kt');
  const app=readFileSync(model,'utf8');
  const original='internal const val DEFAULT_CLOUD_ROOM_ENDPOINT = "https://arcana-table-rooms.arcanamtg.workers.dev"';
  if (!app.includes(original)) throw Error('Android endpoint baseline changed');
  writeFileSync(model,app.replace(original,'internal const val DEFAULT_CLOUD_ROOM_ENDPOINT = "'+ROOM_ORIGIN+'"'));
  return {rooms:ROOM_NAME,origin:ROOM_ORIGIN,apiModel:'OFF',testPackage:'com.arcanatable.goosegetatest'};
}
if (process.argv[1] && import.meta.url.endsWith(process.argv[1])) console.log(JSON.stringify(prepare(resolve(process.argv[2]||'.'),resolve(process.argv[3]||'cloud-source'))));
