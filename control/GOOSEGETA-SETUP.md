# Goosegeta Control candidate

Depends on the B140 cloud/native candidate on `gpt/summon-goosegeta-cloud-20261031`
and its `docs/SUMMON-GOOSEGETA.md`. Neither candidate is deployed.

The existing owner OAuth session authenticates `/v1/goosegeta`. Only fixed room-code
paths and a separate service credential are forwarded over a Cloudflare service binding.
OAuth tokens never reach the rooms service. Status returns only allowed lifecycle and
counter fields, never cards, chat or capabilities. Summon HTTP success means **joining**,
not an accepted Android deck or working game.

After separate owner approval, the engineer binds `GOOSEGETA_ROOMS` to an isolated test
room Worker, installs matching random `GOOSEGETA_CONTROL_KEY` secrets and explicitly
enables the feature. Leave OpenAI disabled. No new subscription, paid worker, production
change, merge, APK/AAB or API call is performed by this source.

Phone flow after the engineering/deployment/test-build gates: issue the host-only Android
debug invite → Control → select deck and optional playful/ruthless banter → Summon →
verify actual Android lobby acceptance → start. Dismiss and Apply chat / mute target the
matching session. Extra human guests force mute pending individual-consent support.
Capabilities live only in page memory and expire after two minutes. Sign in before
issuing an invite; request a fresh one if login interrupts it.

Free banter is limited/canned. Fresh model replies/moves require separately approved
API credentials/model and game/daily consumption limits, shared by gameplay and chat.
The UI does not activate API billing. No PC process or PowerShell is intended.

Local validation: `node --test scripts/*.test.cjs control/*.test.cjs control/review-worker/tests/*.test.mjs`
passes 481 source/mocked binding regressions. No actual deployed Control, Android-hosted
game/chat, model call, phone layout acceptance or PC-off proof is claimed.
