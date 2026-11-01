/** Owner parked Goosegeta. Preserve OAuth/origin checks in the existing route.
 * Fail closed even when an old deployment binding or browser still says enabled.
 * Original implementation: archive/goosegeta/2026-10-31/642 and /707.
 */
export async function goosegeta() {
  return [410, { error: 'goosegeta-parked' }];
}
