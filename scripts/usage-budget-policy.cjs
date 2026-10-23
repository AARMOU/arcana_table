'use strict';

// Pure budget policy. Caller must supply verified, current measurements.
// Never use Cloud Agent billing credits as a proxy for premium requests.
function decideBudget({ used, allowance, measuredAt, now = Date.now(), maxAgeMs = 60 * 60 * 1000 }) {
  if (!Number.isFinite(used) || !Number.isFinite(allowance) ||
      used < 0 || allowance <= 0 || !Number.isFinite(measuredAt) ||
      measuredAt > now || now - measuredAt > maxAgeMs) {
    return { level: 'unknown', maxNewAssignments: 0, warn: true, reason: 'Missing, invalid, or stale usage data' };
  }
  const ratio = used / allowance;
  if (ratio >= 0.9) return { level: 'paused', maxNewAssignments: 0, warn: true, ratio };
  if (ratio >= 0.75) return { level: 'slow', maxNewAssignments: 1, warn: true, ratio };
  if (ratio >= 0.5) return { level: 'warn', maxNewAssignments: 2, warn: true, ratio };
  return { level: 'normal', maxNewAssignments: 2, warn: false, ratio };
}

module.exports = { decideBudget };
