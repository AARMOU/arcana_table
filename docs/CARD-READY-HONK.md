# Arcana Control — card-ready approval HONK (Web Push)

**Requested notification text (exact):** `🪿🪿🪿CARD READY FOR APPROVAL HONK🪿🪿🪿`

This is a native Android **PWA notification**, not a status badge and not a ChatGPT reminder. When Arcana Control is installed using Chrome, its service worker receives Web Push even when the dashboard is closed. Tapping the notification opens the existing Verify / Approve inbox. The owner can enable or disable it from the **Card implementation review** panel; the server registers only a GitHub-owner-authenticated subscription.

## Trigger and safety

Every 15 minutes, the **existing** Cloudflare review Worker checks the canonical `github-actions[bot]` review-status comment on issue #78. It only sends a push for a *new* `PR:headSHA` meeting the existing Control approval criteria: card implementation, executable test, no shape problem, exact-head certified state and independent certification. It rejects missing/invalid status, stale reports (over 20 minutes), other authors, and all engine/audit/failed/approved/integrated cards. It never sends from a cached page, and it never alters a card's state.

One push is sent for a batch of new ready items; a stored seen-set prevents repeated honks for the same certified PR commit. If the push gateway fails, the event is **not** acknowledged and is retried on the next scheduled check. HTTP 404/410 clears an expired subscription. Currently, one owner phone subscription is supported (new activation replaces the previous phone). The review inbox has a 30-row reporting limit; items that become ready and disappear before a successful fresh status check cannot be notified retroactively.

## Deployment prerequisites — not automatic

The existing review Worker is manually deployed and is not authorized to deploy as part of a code merge. The implementation is **inert** until all of the following are provisioned for that manual deployment:

1. Create one Cloudflare Workers KV namespace for push subscription state in the owner's Cloudflare account. Put its existing 32-character namespace ID into the GitHub Actions **variable** `REVIEW_CARD_PUSH_KV_ID`. No namespace is created by CI.
2. Generate a P-256 VAPID key pair offline, and store its URL-safe uncompressed public key (87 characters) in the GitHub Actions **secret** `REVIEW_VAPID_PUBLIC_KEY` and the matching private-key JWK JSON in the GitHub Actions **secret** `REVIEW_VAPID_PRIVATE_JWK`. Never commit either key or paste the private JWK into #81 or chat.
3. After review, exact-head CI, merge approval, and separate explicit Cloudflare-deployment approval, run the existing **Deploy Arcana Control review Worker (manual only)** workflow with its required confirmation. The workflow adds the KV binding only when all three values are present, then deploys and installs the two VAPID secrets.
4. On the phone, open the installed Arcana Control PWA, sign in as GitHub owner if needed, tap **Enable 🪿 approval honks**, and allow Android/Chrome notifications. If the browser blocks notifications, update Android notification permissions for Chrome / the installed PWA.

For engineers creating keys offline, this Node 22 snippet writes two local files, not GitHub commits:

```js
const fs = require('node:fs');
const { generateKeyPairSync } = require('node:crypto');
const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const pub = publicKey.export({format:'jwk'});
const uncompressed = Buffer.concat([
  Buffer.from([4]), Buffer.from(pub.x, 'base64url'), Buffer.from(pub.y, 'base64url')
]).toString('base64url');
fs.writeFileSync('vapid.public.txt', uncompressed);
fs.writeFileSync('vapid.private.jwk', JSON.stringify(privateKey.export({format:'jwk'})));
```

Verification: `node --test control/review-worker/tests/card-ready-push.test.mjs` plus the repository's existing control regressions. Confirm owner session and subscription status, a *new* independently certified card, exactly one notification, correct text, notification click navigation, and no duplicate on refresh/cron. A synthetic test push is not sent from a live production account.

**Costs and ownership:** Cloudflare KV and Web Push may have provider usage costs; no paid Copilot or Claude work is created. Only the owner-authorized review Worker holds the push endpoint, keys, and seen-state. The browser never receives VAPID private material. The review Worker's authenticated approval endpoint remains unchanged.
