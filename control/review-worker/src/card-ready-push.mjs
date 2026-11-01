// Owner-only browser push for certified, current-head cards in the existing #78 review inbox.
// No paid card workers, card approvals, GitHub writes, or Cloudflare deployments are triggered here.
const STATUS_MARKER = '<!-- arcana-control-status-v1:review -->';
const STATUS_URL = 'https://api.github.com/repos/AARMOU/arcana_table/issues/78/comments?per_page=100&page=1';
const STORE_KEY = 'owner:card-ready-v1';
const MAX_AGE_MS = 20 * 60 * 1000;
export const NOTIFICATION_TITLE = '🪿🪿🪿CARD READY FOR APPROVAL HONK🪿🪿🪿';

export function readyIds(report, clock = Date.now()) {
  const time = Date.parse(report?.checkedAt || '');
  if (report?.kind !== 'review' || !Array.isArray(report.items) || !Number.isFinite(time) ||
    time > clock + 60000 || clock - time > MAX_AGE_MS) return null;
  return [...new Set(report.items.filter(i => i?.kind === 'card' && i.hasTest === true &&
    !i.shapeProblem && i.state === 'certified' && i.certification === 'certified' &&
    // Same predicate as the inbox's readyToVerify: destination preflight passed, else no honk.
    i.readyForOwner === true && i.preflight?.status === 'passed' &&
    Number.isSafeInteger(i.pr) && i.pr > 0 && /^[0-9a-f]{40}$/.test(String(i.headSha || '')))
    .map(i => i.pr + ':' + i.headSha))];
}

export function extractReview(comments) {
  if (!Array.isArray(comments)) return null;
  // #78 status comments are edited in place, so locate the bot-authored marker,
  // rather than trusting the newest comment or a client-provided claim of readiness.
  const candidates = comments.filter(c => c?.user?.login === 'github-actions[bot]' &&
    c?.user?.type === 'Bot' && String(c.body || '').startsWith(STATUS_MARKER));
  for (const comment of candidates.reverse()) {
    const match = String(comment.body).match(/```json\s*([\s\S]*?)\s*```/);
    if (!match) continue;
    try { const report = JSON.parse(match[1]); if (report?.kind === 'review') return report; }
    catch { /* malformed report is not a push event */ }
  }
  return null;
}

function endpointAllowed(raw) {
  try {
    const url = new URL(raw);
    return url.protocol === 'https:' && !url.username && !url.password &&
      (url.hostname === 'fcm.googleapis.com' || url.hostname === 'updates.push.services.mozilla.com' ||
        url.hostname === 'web.push.apple.com' || url.hostname.endsWith('.push.apple.com')) &&
      raw.length <= 2048;
  } catch { return false; }
}

export function validSubscription(s) {
  return !!s && typeof s === 'object' && typeof s.endpoint === 'string' && endpointAllowed(s.endpoint) &&
    typeof s.keys?.p256dh === 'string' && s.keys.p256dh.length <= 150 &&
    typeof s.keys?.auth === 'string' && s.keys.auth.length <= 100;
}

const b64u = bytes => btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
const fromText = s => new TextEncoder().encode(s);
async function vapidToken(url, env, clock) {
  const key = JSON.parse(env.VAPID_PRIVATE_JWK);
  const imported = await crypto.subtle.importKey('jwk', key, { name: 'ECDSA', namedCurve: 'P-256' }, false, ['sign']);
  const h = b64u(fromText(JSON.stringify({ alg: 'ES256', typ: 'JWT' })));
  const p = b64u(fromText(JSON.stringify({ aud: new URL(url).origin,
    exp: Math.floor(clock / 1000) + 3600, sub: 'https://aarmou.github.io/arcana_table/control/' })));
  const signed = h + '.' + p;
  const signature = await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, imported, fromText(signed));
  return signed + '.' + b64u(new Uint8Array(signature));
}

export async function pushEmpty(subscription, env, { fetchImpl = fetch, clock = Date.now() } = {}) {
  const jwt = await vapidToken(subscription.endpoint, env, clock);
  // Empty push: no payload encryption keys leave the server and the service worker
  // owns the fixed text. This is a standards-compliant Web Push ping.
  return fetchImpl(subscription.endpoint, { method: 'POST', headers: {
    Authorization: 'vapid t=' + jwt + ', k=' + env.VAPID_PUBLIC_KEY,
    TTL: '3600', Urgency: 'high',
  } });
}

export async function handleCardPush(method, request, env) {
  if (!env.CARD_PUSH || !env.VAPID_PUBLIC_KEY || !env.VAPID_PRIVATE_JWK)
    return [503, { error: 'push-not-configured' }];
  if (method === 'GET') {
    const stored = await env.CARD_PUSH.get(STORE_KEY, 'json');
    return [200, { enabled: !!stored?.subscription }];
  }
  if (method === 'DELETE') {
    await env.CARD_PUSH.delete(STORE_KEY);
    return [200, { enabled: false }];
  }
  if (method !== 'POST') return [405, { error: 'method-not-allowed' }];
  if (Number(request.headers.get('content-length') || '0') > 4096)
    return [413, { error: 'too-large' }];
  let input;
  try { input = JSON.parse((await request.text()).slice(0, 4097)); }
  catch { return [400, { error: 'bad-json' }]; }
  if (!validSubscription(input) || JSON.stringify(input).length > 4096)
    return [400, { error: 'invalid-push-subscription' }];
  const prior = await env.CARD_PUSH.get(STORE_KEY, 'json');
  await env.CARD_PUSH.put(STORE_KEY, JSON.stringify({
    subscription: { endpoint: input.endpoint, keys: { p256dh: input.keys.p256dh, auth: input.keys.auth } },
    seen: prior?.subscription?.endpoint === input.endpoint && Array.isArray(prior.seen) ? prior.seen : [],
  }));
  return [200, { enabled: true }];
}

export async function runCardReadyPush(env, { fetchImpl = fetch, clock = Date.now() } = {}) {
  if (!env.CARD_PUSH || !env.VAPID_PUBLIC_KEY || !env.VAPID_PRIVATE_JWK)
    return { skipped: 'not-configured' };
  const prior = await env.CARD_PUSH.get(STORE_KEY, 'json');
  if (!validSubscription(prior?.subscription)) return { skipped: 'not-subscribed' };
  let response;
  try {
    response = await fetchImpl(STATUS_URL, { headers: {
      Accept: 'application/vnd.github+json', 'User-Agent': 'ArcanaControlCardPush/1.0',
      ...(env.GITHUB_PUBLIC_READ_TOKEN ? { Authorization: 'Bearer ' + env.GITHUB_PUBLIC_READ_TOKEN } : {}),
    }, cache: 'no-store' });
    if (!response.ok) return { skipped: 'review-feed-http-' + response.status };
    const report = extractReview(await response.json());
    const ids = readyIds(report, clock);
    if (ids === null) return { skipped: 'review-feed-invalid-or-stale' };
    const seen = Array.isArray(prior.seen) ? prior.seen.filter(x => typeof x === 'string') : [];
    const fresh = ids.filter(id => !seen.includes(id));
    if (!fresh.length) return { ready: ids.length, alerted: 0 };
    const sent = await pushEmpty(prior.subscription, env, { fetchImpl, clock });
    if (sent.status === 404 || sent.status === 410) {
      await env.CARD_PUSH.delete(STORE_KEY);
      return { skipped: 'push-subscription-expired' };
    }
    if (!sent.ok) return { skipped: 'push-service-http-' + sent.status };
    // Persist only after successful delivery acceptance: a failed push retries next cron.
    await env.CARD_PUSH.put(STORE_KEY, JSON.stringify({
      subscription: prior.subscription, seen: [...new Set([...seen, ...fresh])].slice(-1000),
    }));
    return { ready: ids.length, alerted: fresh.length };
  } catch {
    return { skipped: 'push-check-failed' };
  }
}
