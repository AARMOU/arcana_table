# #51: source-only filter-land solver repair

This candidate adds one bounded fallback to `ManaSolver.solve`. The ordinary
priority plan prefers Plains for a white pip. That strands Skycloud Expanse's
`{1}` activation cost, even though activating Skycloud first would supply WU.
After normal planning fails, the fallback prefers a costed composite source for
colored pips. Existing output, restriction, tap and activation-cost settlement
still determine whether the plan succeeds. No card names or invented mana are
used; successful ordinary plans retain their previous source choices.

`filter-retry.patch` is deliberately **not in the mandatory adoption series**.
No app/runtime jar is replaced, generated library is rewritten, engine pin is
changed, or APK produced. Owner approval and full source-build/integration review
remain necessary before runtime adoption. The normal committed-runtime tests
remain red until adoption; the overlay is a separate validation mode.

## Baseline provenance

Arcana development baseline: `01053b6024f748773e90eb63550094ef65688439`.
The four runtime jars' last change is commit
`7e823fd747f31eb3a920c48f6a885fc34b0cb4be`, recording Argentum
`f5dc31813bfe12dbc12c412f69687452e86bd63f`. The runner checks every jar against
that commit before proceeding. It reconstructs only `ManaSolver.kt` from that
upstream object plus every relevant mandatory patch **as recorded at the runtime
commit**, then strictly applies the candidate.

The current audit/AGENTS source pin instead says
`173a09a2ed15763dce854e385b2df0d085e03975`. The candidate also applied to that
pin plus its 34 mandatory patches during investigation. Compiling that newer
source against the shipped SDK failed on newer `hasLostManaAbilities` and
`RevealSelf` APIs. This source/runtime mismatch is disclosed, not silently fixed
or used as a reason to change binaries.

## Reproduce without replacing jars

Use the existing JDK21 and Android SDK environment. Supply an isolated local
Argentum git object store containing the exact upstream SHA (the runner reads it
with `git show`; it never changes that checkout). For example:

```powershell
git clone --filter=blob:none --no-checkout https://github.com/wingedsheep/argentum-engine.git investigation/crayola-filter-engine
python investigation/filter-land-51/validate.py --baseline
python investigation/filter-land-51/validate.py
python investigation/filter-land-51/validate.py --all
```

`--engine-source PATH` selects a different source store. Baseline intentionally
returns a failing exit status when the defect is reproduced. Candidate compiles
one replacement source **into the test output directory**, ahead of the unchanged
runtime jar on the test classpath. `-Xfriend-paths` preserves access to existing
engine-internal helpers. This is class-overlay validation against the actual
consumer, not a full rebuilt-engine, packaging, Android/device or AI certification.
The init script is opt-in and the normal Gradle configuration is unchanged.

Generated source, XML, logs and jar SHA-256 manifests live under ignored `build/`.
Each invocation writes a unique timestamp/UUID directory containing its log,
manifest and (only after exit zero) current XML. Old `baseline-xml`/`candidate-xml`
directories and top-level manifests are historical artifacts, never current-run
evidence. The runner clears Gradle's test-result directory before launching and
rejects a successful command with no fresh XML. Failed commands publish no XML.
An exclusive runner lock prevents duplicate validation invocations. The runner
never writes `android/app/libs` and checks its hashes again on exit. It fails
closed if those jars change or no longer match the recorded baseline.
On interruption or output failure, Windows tree termination must succeed and the
root must be reaped before unlocking. If termination cannot be confirmed (including
an already-exited root or non-Windows descendant uncertainty), the lock stays for
manual process inspection; do not delete it just to retry.

Lightweight runner failure-path checks (including a real Windows child/grandchild):
`python -m unittest discover -s investigation/filter-land-51 -p test_validate.py -v`.

## Regression coverage

The original seven tests remain: affordability and direct AutoPay for Voice of
Victory and Deft Duelist; manual activation; WW/UU rejection; third-Plains
controls. Three further tests cover tapped seed/filter rejection, a lone filter
unable to seed itself, and reversed battlefield insertion order through actual
Deft Duelist resolution. Failed payment must preserve the previously untapped
land and leave the stack empty. The card library and Oracle definitions are
unchanged.
