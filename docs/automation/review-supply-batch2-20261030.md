# T5 source review batch 2 — 2026-10-30

Evidence base: `5a8b51a92b8a43bae77631ae0b3b5d069eecd344`; upstream `7fb073337e4389cfabe2ebf4c6416633a7fc63d2`. All 227 open PRs were paginated before selection; Cardkit found no title/branch claim for these eight. Live feeder claim checks remain required before dispatch. This is source review for candidate authoring, not rules certification. Depends on #546 record-format review and merge.

| Card | Score | Decision | Evidence / remaining requirement |
|---|---:|---|---|
| Old One Eye | 0.869 | NEEDS_REVIEW | Graveyard trigger and PRECOMBAT_MAIN exist, but the reviewed Squee/Ashcoat references do not establish exact optional two-card discard payment. Do not substitute partial discard plus return. No engine gap inferred. |
| Tomb Fortress | 0.863 | SUPPORTED by review | Exile-self/sorcery costs plus post-mill graveyard collection; choose during resolution, never preselect a target. |
| Past in Flames | 0.857 | SUPPORTED by review | Snapcaster/Slickshot flashback grant plus SDK collection iteration; grant only to current eligible cards. |
| Tzaangor Shaman | 0.849 | SUPPORTED by review | Sword of Wealth and Power / Flamehold Grappler next-spell-copy effects. |
| Kozilek’s Return | 0.840 | SUPPORTED by review | Graveyard cast trigger + successful optional source-exile gate + group damage. |
| Purestrain Genestealer | 0.835 | SUPPORTED by review | Actual CountersRemoved success criterion, then tapped basic-land search/shuffle. |
| Exocrine | 0.833 | SUPPORTED by review | ETB X-value damage; player and nonself creature iteration; Ravenous remains separately tested. |
| Rishkar’s Expertise | 0.829 | SUPPORTED by review | Existing filtered hand collection and optional immediate free cast after drawing. |

Each accepted decision has full Oracle/runtime hashes, reviewer, exact evidence base and pinned source URLs in `tools/cardkit/data/verified-support.json`. Its summary names the required positive/negative scenarios. Old One Eye has no new support entry. No blockers, ceilings, score thresholds, workers or card source were changed.
