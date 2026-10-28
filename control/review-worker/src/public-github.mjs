// Public, read-only, allowlisted GitHub feeds for Arcana Control.
// Native Workers Caching (wrangler cache.enabled=true) serves these read-only feeds at the edge,
// including on workers.dev. This does NOT use the older caches.default API, which is ineffective
// for workers.dev deployments. No secrets, OAuth sessions, or private billing figures enter this
// endpoint. Stale data are for display only; never an admission/approval authority.
const GH_REPO_API = 'https://api.github.com/repos/AARMOU/arcana_table';
const PWA_ORIGIN = 'https://aarmou.github.io';
const FRESH_S = 15 * 60;
const STALE_S = 24 * 60 * 60;
const REFRESH_S = 60 * 60;

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
  'Cache-Control': 'public, max-age=' + ttl + ', stale-while-revalidate=' + REFRESH_S + ', stale-if-error=' + STALE_S,
  'Access-Control-Allow-Origin': PWA_ORIGIN,
  'Access-Control-Expose-Headers': 'Link, X-Arcana-Feed, X-Arcana-Checked-At, Cf-Cache-Status',
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
// Cloudflare Workers Caching operates before this handler runs. A valid 200 response is
// cached for 15 minutes; expired responses may be served stale for up to 24 hours on
// origin errors. All error responses and authenticated routes are explicitly no-store.
export async function publicGithub(request, { fetchImpl = fetch, clock = () => new Date() } = {}) {
  const url = new URL(request.url);
  if (request.method !== 'GET') return error('method-not-allowed', 405);
  const path = url.searchParams.get('path');
  if (!isAllowedGithubPath(path)) return error('github-path-not-allowed', 403);
  try {
    const upstream = await fetchImpl(GH_REPO_API + path, { headers: {
      'Accept': 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
      'User-Agent': 'ArcanaControlPublicFeed/1.0',
    } });
    if (!upstream.ok) return error('github-upstream-http-' + upstream.status, 503);
    const body = await upstream.text();
    JSON.parse(body); // Never cache a GitHub login page, proxy error, or malformed response.
    return reply(body, 'live', clock().toISOString(), upstream.headers.get('Link'), FRESH_S);
  } catch {
    return error('public-github-feed-unavailable', 503);
  }
}
