'use strict';
// A GitHub Actions bot comment is not a Copilot coding-agent invocation.
// The existing owner dispatch token may deliver requests, but ONLY after
// verifying its authenticated GitHub identity is the repository owner.
// The secret is never logged, embedded in a comment or returned as a reason.
const headers = token => ({
  Authorization: 'Bearer ' + token,
  Accept: 'application/vnd.github+json',
  'X-GitHub-Api-Version': '2022-11-28',
  'Content-Type': 'application/json',
});
const failure = reason => ({ ok: false, reason });
const account = name => String(name || '').toLowerCase();
function createCopilotDelivery({ owner, repo, token, fetchImpl = fetch }) {
  let verified = false;
  async function preflight() {
    verified = false;
    if (!token) return failure('owner-delivery-token-missing');
    try {
      const r = await fetchImpl('https://api.github.com/user',
        { headers: headers(token) });
      if (!r.ok) return failure('owner-delivery-identity-http-' + r.status);
      const user = await r.json();
      if (account(user?.login) !== account(owner) || !owner)
        return failure('owner-delivery-identity-not-owner');
      verified = true;
      return { ok: true };
    } catch {
      return failure('owner-delivery-identity-unavailable');
    }
  }
  async function post({ issue_number, body }) {
    // Always require identity preflight for each admission attempt.
    if (!verified) return failure('owner-delivery-not-verified');
    verified = false;
    if (!Number.isSafeInteger(issue_number) || issue_number < 1 || !body)
      return failure('owner-delivery-invalid-request');
    try {
      const url = 'https://api.github.com/repos/' +
        encodeURIComponent(owner) + '/' + encodeURIComponent(repo) +
        '/issues/' + issue_number + '/comments';
      const r = await fetchImpl(url, {
        method: 'POST', headers: headers(token), body: JSON.stringify({body}),
      });
      if (!r.ok) return failure('owner-delivery-post-http-' + r.status);
      const posted = await r.json();
      if (account(posted?.user?.login) !== account(owner) ||
        posted?.body !== body || !Number.isSafeInteger(posted?.id))
        return failure('owner-delivery-unconfirmed');
      return {ok: true, id: posted.id};
    } catch {
      return failure('owner-delivery-post-uncertain');
    }
  }
  return {preflight, post};
}
module.exports = {createCopilotDelivery};
