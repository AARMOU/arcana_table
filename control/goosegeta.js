/* Goosegeta is parked by owner decision. No network or model operations. */
(() => {
  'use strict';
  // Old invitations must not launch the retired flow or remain in the address bar.
  if (window.location.hash.startsWith('#goose-invite='))
    history.replaceState(null, '', window.location.pathname + window.location.search);
  try { window.localStorage.removeItem('arcana-goosegeta-last-room-v1'); } catch {}
  // Also neutralize a previously cached page paired with this newer script.
  const form = document.getElementById('gooseForm');
  if (form) {
    form.hidden = true;
    for (const control of form.querySelectorAll('input, select, button')) control.disabled = true;
    form.addEventListener('submit', event => event.preventDefault());
  }
})();
