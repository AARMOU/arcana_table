// Public, read-only, allowlisted GitHub feeds for Arcana Control.
// Cache at the Cloudflare edge instead of spending each viewer's 60/hour unauthenticated
// api.github.com allowance. No secrets, OAuth sessions, or billing figures are used here.
// A stale cached response is display-only, never an admission/approval authority.
const GH_REPO_API = 'https://api.github.com/repos/AARMOU/arcana_table';
const PWA_ORIGIN = 'https://aarmou.github.io';
const FRESH_S = 15 * 60;
const STALE_S = 24 * 60 * 60;

const allowed = [
  /^\/pulls\?state=open&sort=updated&direction=desc&per_page=100$/,
  /^\/actions\/runs\?per_page=30$/,
  /^\/actions\/runs\?branch=[a-zA-Z0-9._%/-]{1,160}&per_page=20$/,
  /^\/actions\/workflows\/argentum-weekly-upgrade\.yml\/runs\?per_page=10$/,
  /^\/issues\/78\/comments\?per_page=100(?:&page=[1-9][0-9]{0,2})?$/,
  /^\/issues\/81\/comments\?per_page=100&page=[1-9][0-9]{0,2}$/,
  /^\/issues\?state=open&per_page=100$/,
  /^\/issues\/[1-9][0-9]{0,6}$/,
  /^\/issues\/[1-9][0-9]{0,6}\/timeline\?per_page=100$/,
  /^\/pulls\/[1-9][0-9]{0,6}$/,
];
export const isAllowedGithubPath = path => typeof path === 'string' && path.length < 250 && allowed.some(r => r.test(path));

const headersFor = (state, date, link, ttl) => ({
  'Content-Type': 'application/json; charset=utf-8',
  'Cache-Control': 'public, max-age=' + ttl,
  'Access-Control-Allow-Origin': PWA_ORIGIN,
  'Access-Control-Expose-Headers': 'Link, X-Arcana-Feed, X-Arcana-Checked-At',
  'X-Content-Type-Options': 'nosniff',
  'X-Arcana-Feed': state,
  'X-Arcana-Checked-At': date,
  ...(link ? { Link: link } : {}),
});
const reply = (body, state, date, link, ttl) => new Response(body, { status: 200, headers: headersFor(state, date, link, ttl) });
const error = (code, status) => new Response(JSON.stringify({ error: code }), { status, headers: {
  'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store',
  'Access-Control-Allow-Origin': PWA_ORIGIN,
  'X-Content-Type-Options': 'nosniff',
} });
const fromCache = (response, state) => {
  const headers = new Headers(response.headers);
  headers.set('X-Arcana-Feed', state);
  return new Response(response.body, { status: 200, headers });
};

export async function publicGithub(request, { fetchImpl = fetch, cache = globalThis.caches?.default, clock = () => new Date() } = {}) {
  const url = new URL(request.url);
  if (request.method !== 'GET') return error('method-not-allowed', 405);
  const path = url.searchParams.get('path');
  if (!isAllowedGithubPath(path)) return error('github-path-not-allowed', 403);

  // Same-origin internal keys. Separate long-lived fallback from the fresh cache;
  // stale data are visibly labeled in the dashboard and never authorize decisions.
  const key = suffix => new Request(url.origin + '/_arcana_cache/' + suffix + '?path=' + encodeURIComponent(path));
  const freshKey = key('fresh'), staleKey = key('stale');
  const safeMatch = async k => { try { return cache ? await cache.match(k) : null; } catch { return null; } };
  const fresh = await safeMatch(freshKey);
  if (fresh) return fromCache(fresh, 'cached');

  try {
    const upstream = await fetchImpl(GH_REPO_API + path, { headers: {
      'Accept': 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
      'User-Agent': 'ArcanaControlPublicFeed/1.0',
    } });
    if (!upstream.ok) throw Error('upstream-' + upstream.status);
    const body = await upstream.text();
    JSON.parse(body); // Never cache login pages, proxy errors, or malformed JSON as a status.
    const date = clock().toISOString();
    const link = upstream.headers.get('Link');
    const freshResponse = reply(body, 'live', date, link, FRESH_S);
    if (cache) {
      // Cache writes are best effort; a Cloudflare cache fault must not hide valid GitHub data.
      try { await cache.put(freshKey, freshResponse.clone()); } catch {}
      try { await cache.put(staleKey, reply(body, 'stale', date, link, STALE_S)); } catch {}
    }
    return freshResponse;
  } catch {
    const stale = await safeMatch(staleKey);
    if (stale) return fromCache(stale, 'stale');
    return error('public-github-feed-unavailable', 503);
  }
}
