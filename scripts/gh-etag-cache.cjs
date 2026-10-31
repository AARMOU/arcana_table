'use strict';
// Conditional-request cache for workflow GitHub API reads.
//
// Every Arcana control workflow shares one GITHUB_TOKEN quota (1,000 requests/hour per repository), and the
// unit, inbox and integration scans re-read the same issue timelines, comments, PR files and check runs on
// every run. GitHub does not count a conditional request that returns 304 Not Modified against the rate
// limit, so each GET is sent with the ETag last seen for that exact URL; on 304 the cached body is returned.
// The data is exactly what GitHub would have returned (it confirmed it is unchanged), so no logic changes.
//
// The store is a gzip JSON file restored/saved with actions/cache by the workflow
// (.gh-etag-cache/cache.json.gz). Missing or corrupt cache = plain requests. Only GETs are touched.
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');

const DEFAULT_FILE = path.join('.gh-etag-cache', 'cache.json.gz');
const MAX_ENTRIES = 4000;
const MAX_AGE_MS = 3 * 864e5;
const MAX_BODY_BYTES = 2 * 1024 * 1024;

function load(file) {
  try {
    const store = JSON.parse(zlib.gunzipSync(fs.readFileSync(file)).toString('utf8'));
    return store && typeof store === 'object' && store.v === 1 && store.entries && typeof store.entries === 'object'
      ? store.entries : {};
  } catch { return {}; }
}

function keyOf(endpoint) {
  const h = endpoint.headers || {};
  return endpoint.url + '|' + String(h.accept || '') + '|' + String(h['x-github-api-version'] || '');
}

function install(github, { file = DEFAULT_FILE, core = null, now = () => Date.now(), saveOnExit = true } = {}) {
  if (!github?.hook?.wrap || !github?.request?.endpoint?.parse) return null;
  if (github.__arcanaEtagCache) return github.__arcanaEtagCache;
  const previous = load(file);
  const touched = {};
  const stats = { hits: 0, misses: 0, uncached: 0 };
  github.hook.wrap('request', async (request, options) => {
    const method = String(options.method || 'GET').toUpperCase();
    const headers = options.headers || {};
    if (method !== 'GET' || headers['if-none-match'] || headers['If-None-Match']) { stats.uncached++; return request(options); }
    let key;
    try { key = keyOf(github.request.endpoint.parse(options)); } catch { stats.uncached++; return request(options); }
    const hit = touched[key] || previous[key];
    // before-after-hook binds this same options object for every wrapped hook (auth included), so the
    // header must be set on it in place; passing a new object to request() would be ignored.
    if (hit?.etag) options.headers = Object.assign(options.headers || {}, { 'if-none-match': hit.etag });
    try {
      const res = await request(options);
      stats.misses++;
      const etag = res?.headers?.etag;
      if (etag && res.status === 200) {
        const body = JSON.stringify(res.data);
        if (body !== undefined && body.length <= MAX_BODY_BYTES)
          touched[key] = { etag, data: res.data, link: res.headers.link || null, at: now() };
      }
      return res;
    } catch (error) {
      if (Number(error?.status) !== 304 || !hit) throw error;
      stats.hits++;
      touched[key] = { ...hit, at: now() };
      const h = { ...(error.response?.headers || {}), etag: hit.etag };
      delete h.link;
      if (hit.link) h.link = hit.link;
      return { status: 200, url: error.response?.url || options.url, headers: h, data: hit.data };
    }
  });
  const save = () => {
    try {
      const t = now();
      const merged = { ...previous, ...touched };
      const entries = Object.entries(merged).filter(([, e]) => e && e.etag && t - Number(e.at || 0) <= MAX_AGE_MS)
        .sort((a, b) => Number(b[1].at) - Number(a[1].at)).slice(0, MAX_ENTRIES);
      fs.mkdirSync(path.dirname(file), { recursive: true });
      fs.writeFileSync(file, zlib.gzipSync(JSON.stringify({ v: 1, entries: Object.fromEntries(entries) })));
      if (core) core.info(`GitHub API ETag cache: ${stats.hits} not-modified (free), ${stats.misses} fetched, ${stats.uncached} uncached; ${entries.length} entries saved.`);
    } catch (error) {
      if (core) core.warning('GitHub API ETag cache not saved: ' + String(error?.message || error).slice(0, 200));
    }
  };
  if (saveOnExit) process.on('exit', save);
  const api = { stats, save };
  Object.defineProperty(github, '__arcanaEtagCache', { value: api });
  return api;
}

module.exports = { install, load, keyOf, DEFAULT_FILE };
