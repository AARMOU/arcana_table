// Arcana Control installed app. Fast open: the shell is served from the phone's cache immediately and refreshed in
// the background; the deploy stamps BUILD (pages.yml), so every deployment installs a new worker that pre-caches the
// new shell and the page reloads once onto it (index.html controllerchange). The UI itself is unchanged.
const BUILD='dev';
const CACHE='arcana-control-shell-v20-'+BUILD;
const DATA='arcana-control-data-v1';
const SHELL=['./','./index.html','./cards.html','./credits.html','./card-queue.js','./card-preflight.js','./review.js','./authorize.js','./team-checkin.js','./goosegeta.js','./credit-budget.js','./manifest.webmanifest','./icons/icon-192.png','./icons/icon-512.png'];
const scope=new URL(self.registration.scope);
const shellUrls=new Set(SHELL.map(s=>new URL(s,scope).href));
// Public, read-only status feeds the dashboard reads. They are always fetched from the network first; the last good
// copy is used ONLY when the network fails (offline / no signal), and every feed carries its own checkedAt time, which
// the dashboard shows. Authenticated requests (owner sign-in, approvals) and everything else are never stored.
const DATA_PREFIXES=['https://arcana-review.arcanamtg.workers.dev/v1/public/','https://api.github.com/repos/AARMOU/arcana_table/'];
const isPublicData=(request,url)=>request.method==='GET'&&!request.headers.has('Authorization')&&
 DATA_PREFIXES.some(p=>url.href.startsWith(p));
self.addEventListener('install',event=>{
 event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(SHELL.map(s=>new Request(s,{cache:'no-cache'})))).then(()=>self.skipWaiting()));
});
self.addEventListener('activate',event=>{
 event.waitUntil(Promise.all([
  caches.keys().then(keys=>Promise.all(keys.filter(key=>key.startsWith('arcana-control-shell-')&&key!==CACHE).map(key=>caches.delete(key)))),
  self.clients.claim()
 ]));
});
async function shell(event){
 const request=event.request;
 const cache=await caches.open(CACHE);
 const cached=await cache.match(request,{ignoreSearch:request.mode==='navigate'})||
  (request.mode==='navigate'?await cache.match(new URL('./index.html',scope).href):undefined);
 // Revalidate with the server (cheap ETag/304) in the background so the cache stays current between deploys.
 const network=fetch(request,{cache:'no-cache'}).then(response=>{
  if(response.ok)event.waitUntil(cache.put(request,response.clone()));
  return response;
 });
 if(cached){event.waitUntil(network.catch(()=>{}));return cached;}
 return network.catch(()=>Response.error());
}
async function data(event){
 const request=event.request;
 try{
  const response=await fetch(request);
  if(response.ok){const copy=response.clone();event.waitUntil(caches.open(DATA).then(cache=>cache.put(request,copy)));}
  return response;
 }catch(error){
  const cached=await caches.match(request,{cacheName:DATA});
  if(cached)return cached;
  throw error;
 }
}
self.addEventListener('fetch',event=>{
 const request=event.request,url=new URL(request.url);
 if(request.method!=='GET')return;
 // Navigations may carry a query (e.g. the sign-in return); match the page itself.
 const key=request.mode==='navigate'?url.origin+url.pathname:url.href;
 if(url.origin===scope.origin&&url.pathname.startsWith(scope.pathname)&&shellUrls.has(key)){event.respondWith(shell(event));return;}
 if(isPublicData(request,url))event.respondWith(data(event));
});
