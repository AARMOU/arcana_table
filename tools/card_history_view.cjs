'use strict';

// Read-only presentation contract. Never substitute PR counts for missing evidence.
function cardHistoryView(feed, {now = new Date().toISOString(), maxAgeMs = 2 * 60 * 60 * 1000} = {}) {
  const unavailable = reason => ({status: 'unavailable', reason, days: []});
  if (!feed || feed.schemaVersion !== 1 || !Array.isArray(feed.days) ||
      !feed.coverage || !['partial', 'complete'].includes(feed.coverage.status) ||
      !feed.trackedRef || !/^[a-f0-9]{40}$/.test(feed.trackedCommit || ''))
    return unavailable('Missing or unsupported card history');
  const checked = Date.parse(feed.checkedAt);
  const current = Date.parse(now);
  if (!Number.isFinite(checked) || !Number.isFinite(current) || checked > current ||
      !Number.isFinite(maxAgeMs) || maxAgeMs <= 0)
    return unavailable('Invalid card history timestamp');
  const dates = new Set();
  for (const day of feed.days) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(day.date || '') || dates.has(day.date) ||
        !['complete', 'partial', 'unavailable'].includes(day.status) ||
        (day.status === 'unavailable' ? day.observedRegistrations !== null :
          !Number.isSafeInteger(day.observedRegistrations) || day.observedRegistrations < 0))
      return unavailable('Invalid daily card history');
    dates.add(day.date);
  }
  if (feed.days.length !== 7) return unavailable('Seven-day history is missing');
  const stale = current - checked > maxAgeMs;
  return {
    status: stale ? 'stale' : feed.coverage.status,
    label: 'Custom card registrations',
    reason: feed.coverage.reason || '',
    checkedAt: feed.checkedAt, trackedRef: feed.trackedRef, trackedCommit: feed.trackedCommit,
    days: feed.days.map(day => {
      const complete = !stale && feed.coverage.status === 'complete' && day.status === 'complete';
      return {date: day.date, status: stale ? 'stale' : day.status,
        // Consumers can show positive observations with the partial label, but no partial zero.
        count: complete || (!stale && day.observedRegistrations > 0) ? day.observedRegistrations : null,
        verified: complete};
    })
  };
}
module.exports = {cardHistoryView};
