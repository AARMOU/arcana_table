'use strict';
// Check runs that Arcana's own control plane attaches to every Copilot PR head: the queue
// feeder (`suggest`, `publish`) and worker observer (`observe`) run on pull_request_target
// events such as ready_for_review. They say nothing about the card, yet a running or
// quota-failed one made "all checks green" gates flap (Verify refused, inbox "checks
// running", integration ineligible). Every other check, including CI and certification,
// still counts. Keep in sync with control/review-worker/src/index.mjs CONTROL_PLANE_CHECKS.
const CONTROL_PLANE_CHECKS = new Set(['suggest', 'publish', 'observe']);
const isCardCheck = run => !CONTROL_PLANE_CHECKS.has(String(run?.name || ''));
module.exports = { CONTROL_PLANE_CHECKS, isCardCheck };
