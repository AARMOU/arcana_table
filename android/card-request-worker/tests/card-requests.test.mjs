import test from "node:test";
import assert from "node:assert/strict";
import worker, { _test, CardRequestQueue } from "../src/index.mjs";
test("canonical names, DFC dedupe and hostile GitHub markup", () => {
  assert.equal(_test.canonicalName("  Ratchet, Field Medic // Ratchet, Rescue Racer  "), "Ratchet, Field Medic // Ratchet, Rescue Racer");
  assert.equal(_test.canonicalName("@someone"), null);
  assert.equal(_test.canonicalName("card\nhello"), "card hello"); // normalized into one safe name
  assert.equal(_test.canonicalName("[GitHub](link)"), null);
  assert.equal(_test.keyOf("Ratchet, Field Medic // Ratchet, Rescue Racer"), "ratchet, field medic");
});
test("reject overlarge, repeated request ids and malformed batches", () => {
  const good = "aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa";
  assert.equal(_test.parseBatch({cardNames:["Ratchet, Field Medic // Ratchet, Rescue Racer","Ratchet, Field Medic"],requestId:good}).names.length,1);
  assert.equal(_test.parseBatch({cardNames:[],requestId:good}),null);
  assert.equal(_test.parseBatch({cardNames:["card"],requestId:"bad"}),null);
  assert.equal(_test.parseBatch({cardNames:new Array(11).fill("Card"),requestId:good}),null);
});
test("public notes never publish deck details or tag GitHub users",()=>{
 const note = _test.publicComment("Goblin Welder",2);
 assert.match(note,/Goblin Welder/); assert.match(note,/requests:\*\* 2/);
 assert.doesNotMatch(note,/@/);
});
function storage() {
 const values = new Map();
 return { get: async key => values.get(key), put: async (key,value)=>values.set(key, structuredClone(value)), values };
}
test("durable object dedupes retries, updates request totals and enforces per-client daily limit",async()=>{
 const prevFetch = globalThis.fetch, calls=[];
 globalThis.fetch = async (url,opts)=>{calls.push({url,opts});return {ok:true,json:async()=>({id:777})};};
 try {
  const state={storage:storage()},env={GITHUB_REQUESTS_TOKEN:"fake"};
  const q=new CardRequestQueue(state,env), client="a".repeat(64);
  const send=(id,names)=>q.fetch(new Request("https://internal/submit",{method:"POST",headers:{"x-opaque-client":client,"content-type":"application/json"},body:JSON.stringify({requestId:id,cardNames:names})}));
  const id=n=>"00000000-0000-4000-8000-"+String(n).padStart(12,"0");
  let response=await send(id(1),["Goblin Welder"]);
  assert.equal(response.status,200);
  assert.equal(calls.length,1);assert.match(calls[0].url,/issues\/321\/comments$/);
  response=await send(id(1),["Goblin Welder"]);
  assert.equal((await response.json()).duplicate,true);
  response=await send(id(2),["Goblin Welder"]);
  assert.equal(response.status,200);assert.equal(calls.length,2);
  assert.match(calls[1].url,/issues\/comments\/777$/);
  await send(id(3),["Ratchet, Field Medic"]);
  response=await send(id(4),["Third time"]);
  assert.equal(response.status,429);
  assert.equal(state.storage.values.get("records")["goblin welder"].count,2);
 } finally {globalThis.fetch=prevFetch;}
});
test("GitHub outage queues cards for scheduled retry instead of claiming submitted",async()=>{
 const prevFetch=globalThis.fetch;
 let fail=true;
 globalThis.fetch=async()=>fail?{ok:false}:{ok:true,json:async()=>({id:42})};
 try{
  const q=new CardRequestQueue({storage:storage()},{GITHUB_REQUESTS_TOKEN:"fake"});
  const res=await q.fetch(new Request("https://internal/submit",{method:"POST",headers:{"x-opaque-client":"b".repeat(64)},body:JSON.stringify({requestId:"bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb",cardNames:["Goblin Welder"]})}));
  assert.equal(res.status,202);assert.equal((await res.json()).pending,true);
  fail=false;
  const sync=await q.fetch(new Request("https://internal/sync",{method:"POST"}));
  assert.equal((await sync.json()).synced,1);
 }finally{globalThis.fetch=prevFetch;}
});

test("public endpoint stops overlarge streamed bodies before parsing or writing", async () => {
 const tooLarge = "X".repeat(4097);
 const response = await worker.fetch(new Request("https://example.workers.dev/v1/card-requests", {
   method: "POST", headers: { "content-type": "application/json" }, body: tooLarge,
 }), { RATE_SALT: "test", GITHUB_REQUESTS_TOKEN: "fake", REQUEST_QUEUE: {} });
 assert.equal(response.status, 413);
});
test("daily global intake limit also bounds many distinct clients", async () => {
 const original = globalThis.fetch;
 globalThis.fetch = async () => ({ok: true, json: async () => ({id: 123})});
 try {
  const store = storage(), q = new CardRequestQueue({storage: store},{GITHUB_REQUESTS_TOKEN:"fake"});
  let last;
  for (let n = 1; n <= 61; n++) {
    const client = n.toString(16).padStart(64, "0");
    last = await q.fetch(new Request("https://internal/submit", {
      method: "POST", headers: {"x-opaque-client": client}, body: JSON.stringify({
       requestId: "11111111-2222-4333-8444-" + String(n).padStart(12,"0"),
       cardNames:["Unique Request " + n]
      })
    }));
  }
  assert.equal(last.status,429);
  const day = new Date().toISOString().slice(0,10);
  assert.equal(store.values.get("rate-global:"+day),60);
 } finally { globalThis.fetch = original; }
});
