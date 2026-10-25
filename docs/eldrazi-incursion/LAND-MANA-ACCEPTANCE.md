# Eldrazi land mana acceptance follow-up

Adds eight real-engine acceptance cases while preserving the two original tests. All ten focused
scenarios passed against the unchanged #201 runtime. This changes **test evidence**, not the
91-name index inventory. No card definition, registry, exported card data or runtime jar changed.

## Unclaimed Territory (six scenarios total)

- The creature type is explicitly chosen **as the land enters**. Choosing Bear permits the tracked
  restricted green unit to pay for Grizzly Bears, with exact consumption and actual resolution.
- A Llanowar Elf is rejected without spending mana. Adding unrestricted green permits the Elf
  while preserving Territory's restricted unit.
- Elvish Visionary cannot use restricted mana for its generic portion even when the unrestricted
  green pip is available. Add one unrestricted colorless and the actual cast succeeds, still
  preserving the restricted unit.
- A noncreature Kindred Sorcery with subtype Bear still cannot spend that mana: the Oracle says
  **creature spell**. An unregistered, JSON-round-tripped test fixture isolates this predicate;
  unrestricted green casts it and the restricted unit remains untouched.
- The unrestricted colorless mode really casts Sol Ring; both modes reject a second activation
  while the land is tapped, without creating extra mana.
- The original mana-generation test remains unchanged.

## Tendo Ice Bridge (four scenarios total)

- Removing its charge produces colored mana. Untapping does not restore the charge; the second
  colored activation fails without tapping or spending. The unrestricted colorless mode still
  works after exhaustion, and both units actually pay for Grizzly Bears.
- Colorless mode consumes no charge and casts Sol Ring. After untapping, the still-present charge
  can pay for a colored activation and is consumed exactly once.
- Either mode rejects another activation while tapped, preserving the pool.
- The original entering-charge/colored-mana test remains unchanged.

Oracle was fetched through Cardkit from Scryfall for both cards and cached with this change.
The updated 100-slot audit promotes these two cards from missing acceptance cases to passing
positive/negative acceptance coverage. Benthic Anomaly moves from uninvestigated missing to a
qualified, reproduced primitive-composition blocker per #203. It remains absent from the index.
No new original card slot is counted, no failing blocker probe is hidden in the normal suite,
and no APK or 100/100 readiness claim is made.
