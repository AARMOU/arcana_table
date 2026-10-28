// Cache only the installable Arcana Control shell. Never cache scanner or billing/GitHub API data.
const CACHE='arcana-control-shell-v9';
const SHELL=['./','./index.html','./cards.html','./credits.html','./card-queue.js','./card-preflight.js','./review.js','./authorize.js','./credit-budget.js','./manifest.webmanifest','./icons/icon-192.png','./icons/icon-512.png'];
const scope=new URL(self.registration.scope);
const shellUrls=new Set(SHELL.map(s=>new URL(s,scope).href));
self.addEventListener('install',event=>{
 event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(SHELL)).then(()=>self.skipWaiting()));
});
self.addEventListener('activate',event=>{
 event.waitUntil(Promise.all([
  caches.keys().then(keys=>Promise.all(keys.filter(key=>key.startsWith('arcana-control-shell-')&&key!==CACHE).map(key=>caches.delete(key)))),
  self.clients.claim()
 ]));
});
self.addEventListener('fetch',event=>{
 const request=event.request,url=new URL(request.url);
 if(request.method!=='GET'||url.origin!==scope.origin||!url.pathname.startsWith(scope.pathname))return;
 // Only known site shell URLs can be cached. card-gaps.json, GitHub, billing and requests remain network-only.
 if(!shellUrls.has(url.href))return;
 event.respondWith(fetch(request).then(response=>{
  if(response.ok){
   const copy=response.clone();
   event.waitUntil(caches.open(CACHE).then(cache=>cache.put(request,copy)));
  }
  return response;
 }).catch(async()=>(await caches.match(request))||(request.mode==='navigate'?await caches.match('./index.html'):Response.error())));
});