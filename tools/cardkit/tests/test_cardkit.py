"""cardkit tests against the real Arcana card definitions, registry, assets and SDK index.

    python3 -m unittest discover -s tools/cardkit/tests -v
"""
from __future__ import annotations

import io
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from cardkit_lib import check, decklist, docs_check, gaps, oracle, patterns, readiness, refs, repo, scaffold, sdk, text  # noqa: E402

# A synthetic Oracle record built from the repo's own, tested Space Marine Scout definition.
SCOUT_TEXT = ("First strike, vigilance\nConcealed Position — When this creature enters, if an opponent controls "
              "more lands than you, you may search your library for a Plains card, put it onto the battlefield tapped, "
              "then shuffle.")
FAKE_SUPPORTED = {"name": "Cardkit Test Scout", "mana_cost": "{2}{W}", "type_line": "Creature — Astartes Scout",
                  "oracle_text": SCOUT_TEXT, "power": "2", "toughness": "1", "color_identity": ["W"],
                  "keywords": ["First strike", "Vigilance"], "rarity": "uncommon", "set": "40k",
                  "collector_number": "15", "artist": "Games Workshop", "layout": "normal"}
FAKE_GAP = {"name": "Cardkit Test Oddity", "mana_cost": "{1}{U}", "type_line": "Artifact",
            "oracle_text": "Whenever the moon is full, each player trades their sideboard with a random spectator.",
            "color_identity": ["U"], "keywords": [], "layout": "normal"}
FAKE_UNKNOWN_KW = {"name": "Cardkit Test Gizmo", "mana_cost": "{3}", "type_line": "Artifact Creature — Robot",
                   "oracle_text": "Flibberjig 3\nFlying", "power": "1", "toughness": "1", "color_identity": [],
                   "keywords": ["Flibberjig", "Flying"], "layout": "normal"}
FAKE_INFECT = {"name": "Cardkit Test Plaguebearer", "mana_cost": "{1}{B}", "type_line": "Creature — Horror",
               "oracle_text": "Infect", "power": "1", "toughness": "1", "color_identity": ["B"],
               "keywords": ["Infect"], "layout": "normal"}
FAKE_COMBO = {"name": "Cardkit Test Martyr", "mana_cost": "{1}{W}", "type_line": "Creature \u2014 Human Warrior",
              "oracle_text": "Martyrdom \u2014 When this creature dies, you gain 2 life and draw two cards.",
              "power": "2", "toughness": "2", "color_identity": ["W"], "keywords": ["Martyrdom"], "layout": "normal"}
FAKE_REVEAL = {"name": "Cardkit Test Harbor", "type_line": "Land", "color_identity": ["W", "U"], "keywords": [],
               "oracle_text": "As this land enters, you may reveal a Plains or Island card from your hand. If you don't, "
                              "this land enters tapped.\n{T}: Add {W} or {U}.", "layout": "normal"}
FAKE_VARIANT = {"name": "Cardkit Test Monument", "mana_cost": "{5}", "type_line": "Artifact", "color_identity": [],
                "keywords": [], "layout": "normal",
                "oracle_text": "Whenever you tap a creature for {C}, add an additional {C}."}
FAKES = {repo.normalize_name(c["name"]): c for c in (FAKE_SUPPORTED, FAKE_GAP, FAKE_UNKNOWN_KW, FAKE_INFECT, FAKE_COMBO,
                                                     FAKE_REVEAL, FAKE_VARIANT)}


def with_fake_oracle(test):
    def wrapper(self, *a, **kw):
        real = oracle._cache()
        merged = {**real, **FAKES}
        with mock.patch.object(oracle, "_cache", lambda: merged):
            return test(self, *a, **kw)
    return wrapper


class RepoTests(unittest.TestCase):
    def test_reads_real_card_sources_and_registry(self):
        sources = repo.card_sources()
        self.assertGreater(len(sources), 200)
        entries = repo.registry_entries()
        self.assertGreater(len(entries), 200)
        by_val = {c.val: c for c in sources}
        scout = by_val["SpaceMarineScout"]
        self.assertEqual(scout.name, "Space Marine Scout")
        self.assertEqual(scout.props["manaCost"], "{2}{W}")
        self.assertEqual(scout.props["power"], 2)
        self.assertTrue(scout.props["oracleText"].startswith("First strike, vigilance\nConcealed Position"))
        self.assertIn("Six WotC precons", {e.deck for e in entries})
        self.assertIn("Tokens Win 9/26", {e.deck for e in entries}, "deck constants must resolve")

    def test_multi_face_card_is_found(self):
        amulet = [c for c in repo.card_sources() if c.val == "PrimalAmulet"]
        self.assertEqual(len(amulet), 1)
        self.assertEqual(amulet[0].name, "Primal Amulet")

    def test_val_naming_matches_repo_convention(self):
        sources = repo.card_sources()
        same = sum(1 for c in sources if repo.val_name_for(c.name) == c.val)
        self.assertGreater(same / len(sources), 0.95)
        self.assertEqual(repo.val_name_for("Ulamog's Crusher"), "UlamogsCrusher")
        self.assertEqual(repo.val_name_for("Bosco, Just a Bear"), "BoscoJustABear")

    def test_kotlin_string_round_trip(self):
        s = 'Say "hi" for {2}\nthen $5'
        lit = repo.kotlin_string(s)
        value, _ = repo.read_string_expr(lit, 0)
        self.assertEqual(value, s)
        value, _ = repo.read_string_expr('"ab" +\n    "cd"', 0)
        self.assertEqual(value, "abcd")


class TextTests(unittest.TestCase):
    def test_self_references_and_numbers_normalize(self):
        a = text.normalize_line("When Space Marine Scout enters, draw two cards.", "Space Marine Scout")
        b = text.normalize_line("When this creature enters, draw three cards.", "Other Card")
        self.assertEqual(a, b)

    def test_ability_word_prefix_is_ignored_but_keyword_costs_are_not(self):
        self.assertEqual(text.strip_ability_word("Grav-cannon — When this creature enters, draw a card."),
                         "When this creature enters, draw a card.")
        self.assertEqual(text.strip_ability_word("Flashback—{1}{B}{G}, Pay 3 life."), "Flashback—{1}{B}{G}, Pay 3 life.")

    def test_clauses_split_triggers_and_costs_but_not_quoted_abilities(self):
        self.assertEqual(text.clauses(text.normalize_line("When this creature dies, you gain 2 life and draw two cards.")),
                         ["when ~ dies,", "you gain n life and draw n cards."])
        self.assertEqual(text.clauses(text.normalize_line("{T}, Pay 1 life: Add {C}. Spend this mana only on X.")),
                         ["{t}, pay n life:", "add {c}.", "spend this mana only on x."])
        quoted = text.normalize_line('Lands you control have "{T}: Add {C}. Spend this mana only on X."')
        self.assertEqual(text.clauses(quoted), [quoted])

    def test_ability_words_are_not_keywords(self):
        self.assertEqual(text.ability_words("Martyrdom \u2014 When this creature dies, draw a card.\nFlying"), {"martyrdom"})

    def test_reminder_text_removed(self):
        self.assertEqual(text.split_lines("Ravenous (This creature enters with X counters.)\nFlying"), ["Ravenous", "Flying"])


class RefsTests(unittest.TestCase):
    def test_finds_the_custom_card_for_its_own_line(self):
        line = SCOUT_TEXT.split("\n")[1]
        best = refs.index().search(line, "Some New Card", k=1)[0]
        self.assertEqual(best.ref.card, "Space Marine Scout")
        self.assertEqual(best.ref.source, "custom")
        self.assertAlmostEqual(best.score, 1.0)
        self.assertIn("SpaceMarineScoutScenarioTest.kt", refs.where(best.ref.card, best.ref.source))

    def test_library_cards_point_at_pinned_upstream_source(self):
        matches = refs.index().search("Target creature gets -3/-3 until end of turn.", k=5)
        lib = [m for m in matches if m.ref.source == "library"]
        self.assertTrue(lib)
        self.assertTrue(any(repo.UPSTREAM_COMMIT in refs.where(m.ref.card, "library") for m in lib))


class SdkTests(unittest.TestCase):
    def test_committed_index_has_real_members_only(self):
        self.assertTrue(sdk.member_exists("Effects", "DrawCards"))
        self.assertTrue(sdk.member_exists("Triggers", "self"))
        self.assertTrue(sdk.member_exists("ObjectTriggerSubject", "enters"))
        self.assertTrue(sdk.member_exists("Costs", "Tap"))
        self.assertFalse(sdk.member_exists("Effects", "DrawCardsAndWinTheGame"))
        self.assertIn("FLYING", sdk.keyword_enum())
        self.assertNotIn("INFECT", sdk.keyword_enum())

    @unittest.skipUnless(shutil.which("javap"), "needs a JDK")
    def test_committed_index_is_current(self):
        self.assertTrue(sdk.write(check=True), "run `tools/cardkit/cardkit sdk-index`")


class PatternTests(unittest.TestCase):
    def test_pattern_index_only_lists_tested_cards(self):
        data = patterns.build()
        enters = data["Triggers"]["Triggers.self.enters"]
        self.assertGreater(enters["count"], 10)
        for ex in enters["examples"]:
            self.assertTrue((repo.ROOT / ex["file"]).exists())
            self.assertTrue((repo.ROOT / ex["test"]).exists())

    def test_committed_pattern_index_is_current(self):
        self.assertTrue(patterns.write(check=True), "run `tools/cardkit/cardkit patterns`")


class ReadinessTests(unittest.TestCase):
    def test_registered_tested_custom_card(self):
        v = readiness.classify("Space Marine Scout")
        self.assertEqual(v["readiness"], "IMPLEMENTED_AND_TESTED")
        self.assertEqual(v["source"], "custom:custom-card-scenario-test")

    def test_argentum_library_card(self):
        v = readiness.classify("Sol Ring")
        self.assertEqual(v["readiness"], "IMPLEMENTED_AND_TESTED")
        self.assertEqual(v["source"], "argentum-indexed")
        self.assertIn("no Arcana card-specific scenario", v["reasons"][0])

    def test_recorded_engine_gap_routes_to_claude(self):
        v = readiness.classify("Port Town")
        self.assertEqual(v["readiness"], "ENGINE_BLOCKED")
        self.assertEqual(v["route"], "claude")
        self.assertIn("#141", " ".join(v["reasons"]))

    def test_uncached_unknown_card_needs_review(self):
        v = readiness.classify("Cardkit Definitely Not A Card")
        self.assertEqual(v["readiness"], "NEEDS_REVIEW")

    @with_fake_oracle
    def test_lines_covered_by_implemented_cards_are_supported(self):
        v = readiness.classify("Cardkit Test Scout")
        self.assertEqual(v["readiness"], "SUPPORTED", v["reasons"])
        self.assertEqual(v["route"], "copilot")
        rules = [l for l in v["lines"] if l["kind"] == "rules"]
        self.assertEqual(rules[0]["matches"][0]["card"], "Space Marine Scout")

    @with_fake_oracle
    def test_line_combining_implemented_clauses_is_supported_and_ability_words_ignored(self):
        v = readiness.classify("Cardkit Test Martyr")
        self.assertEqual(v["readiness"], "SUPPORTED", v["reasons"])
        line = v["lines"][0]
        self.assertGreater(line["score"], line["wholeScore"])
        self.assertEqual(len(line["clauses"]), 2)
        self.assertEqual(v["keywords"], [])

    @with_fake_oracle
    def test_blocked_mechanic_blocks_every_card_with_that_wording(self):
        v = readiness.classify("Cardkit Test Harbor")
        self.assertEqual(v["readiness"], "ENGINE_BLOCKED", v["reasons"])
        self.assertIn("as-enters reveal", " ".join(v["reasons"]))

    @with_fake_oracle
    def test_near_variant_wording_is_not_supported_without_verification(self):
        v = readiness.classify("Cardkit Test Monument")
        self.assertEqual(v["readiness"], "NEEDS_REVIEW", v["reasons"])
        self.assertEqual(v["reviewHint"], "near-variant")
        self.assertIn("creature", " ".join(v["reasons"]))

    def test_verified_support_and_blockers_never_overlap(self):
        blocked = {repo.normalize_name(k) for k in readiness.blockers()["cards"]}
        self.assertFalse(blocked & set(readiness.verified_support()))
        for name, entry in readiness.verified_support().items():
            self.assertTrue(entry["summary"] and entry["references"], name)

    @with_fake_oracle
    def test_unmatched_line_is_review_with_gap_hint(self):
        v = readiness.classify("Cardkit Test Oddity")
        self.assertEqual(v["readiness"], "NEEDS_REVIEW")
        self.assertEqual(v.get("reviewHint"), "possible-engine-gap")
        self.assertEqual(v["route"], "claude")

    @with_fake_oracle
    def test_unknown_keyword_is_never_supported(self):
        v = readiness.classify("Cardkit Test Gizmo")
        self.assertEqual(v["readiness"], "NEEDS_REVIEW")
        self.assertIn("Flibberjig", " ".join(v["reasons"]))

    @with_fake_oracle
    def test_blocked_keyword(self):
        v = readiness.classify("Cardkit Test Plaguebearer")
        self.assertEqual(v["readiness"], "ENGINE_BLOCKED")

    def test_twins_of_discord_cannot_be_routed_as_supported_without_bloodthirst(self):
        # Original Eldrazi Incursion slot. The pinned SDK has no grantable
        # BLOODTHIRST keyword; matching only the odd/even attack text is not enough.
        v = readiness.classify("Twins of Discord")
        self.assertEqual(v["readiness"], "ENGINE_BLOCKED", v["reasons"])
        self.assertEqual(v["route"], "claude")
        self.assertIn("Bloodthirst", readiness.blockers()["keywords"])
        self.assertIn("bloodthirst", " ".join(v["reasons"]).lower())

    def test_exported_chittering_dispatcher_still_blocked_by_proven_myriad_gap(self):
        # #181 compiled and executed the actual three-player engine scenario.
        # Its defender-choice failure must beat indexed/tested source precedence.
        state, _, source = readiness.implementation_state("Chittering Dispatcher")
        self.assertEqual(state, "IMPLEMENTED_AND_TESTED")
        self.assertTrue(source.startswith("custom:"))
        v = readiness.classify("Chittering Dispatcher")
        self.assertEqual(v["readiness"], "ENGINE_BLOCKED", v["reasons"])
        self.assertEqual(v["route"], "claude")
        self.assertIn("#181", " ".join(v["reasons"]))
        self.assertIn("Myriad", readiness.blockers()["keywords"])


class UnitCommandTests(unittest.TestCase):
    """`cardkit unit` must fail closed: no queue body for anything that is not dispatchable."""

    def _run(self, *args):
        return subprocess.run([sys.executable, str(HERE.parent / "cardkit.py"), "unit", *args],
                              capture_output=True, text=True)

    def test_blocked_and_implemented_cards_are_refused_even_with_allow_review(self):
        for card in ("Port Town", "Sol Ring", "Space Marine Scout"):
            res = self._run(card, "--allow-review")
            self.assertNotEqual(res.returncode, 0, card)
            self.assertNotIn("## Cards", res.stdout, card)
            self.assertIn("No queue body printed", res.stderr)

    def test_missing_oracle_is_refused(self):
        res = self._run("Cardkit Definitely Not A Card", "--allow-review")
        self.assertNotEqual(res.returncode, 0)
        self.assertEqual(res.stdout, "")

    def test_one_bad_card_refuses_the_whole_unit(self):
        res = self._run("Port Town", "Mawloc")
        self.assertNotEqual(res.returncode, 0)
        self.assertEqual(res.stdout, "")

    @with_fake_oracle
    def test_unverifiable_claims_refuse_dispatch(self):
        import cardkit
        with mock.patch.object(gaps, "open_pr_claims", side_effect=gaps.ClaimsUnavailable("gh missing")):
            out = io.StringIO()
            with mock.patch("sys.stdout", out), mock.patch("sys.stderr", io.StringIO()):
                code = cardkit.main(["unit", "Cardkit Test Scout"])
        self.assertEqual(code, 1)
        self.assertEqual(out.getvalue(), "")

    @with_fake_oracle
    def test_supported_unclaimed_card_prints_body_and_review_needs_flag(self):
        import cardkit
        with mock.patch.object(gaps, "open_pr_claims", return_value=[]):
            out = io.StringIO()
            with mock.patch("sys.stdout", out), mock.patch("sys.stderr", io.StringIO()):
                self.assertEqual(cardkit.main(["unit", "Cardkit Test Scout"]), 0)
            self.assertIn("## Cards", out.getvalue())
            with mock.patch("sys.stdout", io.StringIO()), mock.patch("sys.stderr", io.StringIO()):
                self.assertEqual(cardkit.main(["unit", "Cardkit Test Monument"]), 1)
            out = io.StringIO()
            with mock.patch("sys.stdout", out), mock.patch("sys.stderr", io.StringIO()):
                self.assertEqual(cardkit.main(["unit", "Cardkit Test Monument", "--allow-review"]), 0)
            self.assertIn("Triage required", out.getvalue())

    @with_fake_oracle
    def test_claimed_card_is_refused(self):
        import cardkit
        prs = [{"number": 9, "title": "Add Cardkit Test Scout", "branch": "copilot/x", "body": ""}]
        with mock.patch.object(gaps, "open_pr_claims", return_value=prs), \
                mock.patch("sys.stdout", io.StringIO()) as out, mock.patch("sys.stderr", io.StringIO()):
            self.assertEqual(cardkit.main(["unit", "Cardkit Test Scout"]), 1)
        self.assertEqual(out.getvalue(), "")

    def test_gaps_cli_end_to_end(self):
        with tempfile.TemporaryDirectory() as tmp:
            res = subprocess.run([sys.executable, str(HERE.parent / "cardkit.py"), "gaps", "--no-refs",
                                  "--json-output", f"{tmp}/g.json", "--markdown-output", f"{tmp}/g.md"],
                                 capture_output=True, text=True)
            self.assertEqual(res.returncode, 0, res.stderr)
            self.assertIn("NOT DISPATCHABLE", res.stdout)
            self.assertFalse(json.loads(Path(f"{tmp}/g.json").read_text())["dispatch"]["dispatchable"])
            self.assertIn("## Missing cards", Path(f"{tmp}/g.md").read_text())

    def test_gaps_claims_failure_exits_nonzero(self):
        import cardkit
        with mock.patch.object(gaps, "open_pr_claims", side_effect=gaps.ClaimsUnavailable("no gh")), \
                mock.patch("sys.stdout", io.StringIO()), mock.patch("sys.stderr", io.StringIO()):
            self.assertEqual(cardkit.main(["gaps", "--no-refs", "--claims-from-github"]), 4)

    def test_gaps_without_claims_is_marked_not_dispatchable(self):
        r = gaps.analyze(gaps.load_decks(), use_refs=False)
        self.assertFalse(r["dispatch"]["dispatchable"])
        r = gaps.analyze(gaps.load_decks(), use_refs=False, claims=[])
        self.assertTrue(r["dispatch"]["dispatchable"])


class DecklistTests(unittest.TestCase):
    def test_manabox_and_moxfield_text(self):
        deck = decklist.parse("Commander\n1 Rocco, Cabaretti Caterer (SNC) 190\n\nDeck\n1x Sol Ring (CMM) 400 *F*\n"
                              "2 Forest\n// comment\nSideboard\n1 Lightning Bolt\n", "t")
        by = {c["name"]: c for c in deck["cards"]}
        self.assertTrue(by["Rocco, Cabaretti Caterer"]["commander"])
        self.assertEqual(by["Sol Ring"]["set"], "CMM")
        self.assertEqual(by["Forest"]["quantity"], 2)
        self.assertNotIn("Lightning Bolt", by)

    def test_manabox_csv(self):
        deck = decklist.parse("Name,Set code,Set name,Collector number,Foil,Rarity,Quantity\n"
                              "Sol Ring,CMM,Commander Masters,400,normal,uncommon,1\n"
                              "\"Bosco, Just a Bear\",TLE,x,1,normal,rare,1\n", "csv")
        self.assertEqual({c["name"] for c in deck["cards"]}, {"Sol Ring", "Bosco, Just a Bear"})

    def test_split_names(self):
        deck = decklist.parse("1 Fire // Ice\n")
        self.assertEqual(deck["cards"][0]["name"], "Fire // Ice")


class GapsTests(unittest.TestCase):
    def test_six_precons_match_existing_coverage_audit(self):
        result = gaps.analyze(gaps.load_decks(), use_refs=False)
        audit = subprocess.run([sys.executable, str(repo.ROOT / "investigation/argentum-six-precons/coverage-audit.py")],
                               capture_output=True, text=True, check=True).stdout
        for d in result["decks"]:
            # coverage-audit.py is intentionally a raw *index* inventory.
            # A reproduced engine blocker can make readiness strictly lower.
            self.assertIn(f"{d['name']}: {d['indexedSlots']}/100 supported", audit)
            self.assertLessEqual(d["playableSlots"], d["indexedSlots"])
        incursion = next(d for d in result["decks"] if d["name"] == "Eldrazi Incursion")
        self.assertEqual(90, incursion["indexedSlots"])
        self.assertEqual(89, incursion["playableSlots"])  # Myriad #181 fails in 3-player engine
        self.assertGreaterEqual(incursion["ENGINE_BLOCKED"], 1)
        self.assertEqual(sum(result["totals"].values()), len(result["cards"]))
        self.assertEqual(set(result["dispatch"]), {"copilot", "claude", "gpt", "claimed", "claimsVerified", "dispatchable"})

    @with_fake_oracle
    def test_open_pr_claims_keep_cards_out_of_dispatch(self):
        deck = {"name": "t", "cards": [{"name": "Cardkit Test Scout", "quantity": 1, "commander": False},
                                       {"name": "Cardkit Test Martyr", "quantity": 1, "commander": False}]}
        prs = [{"number": 7, "title": "Add Cardkit Test Scout", "branch": "copilot/x", "body": ""},
               {"number": 8, "title": "batch", "branch": "gpt/y", "body": "staged: Cardkit Test Martyr"}]
        r = gaps.analyze([deck], claims=prs)
        self.assertEqual(r["dispatch"]["claimed"], {"Cardkit Test Scout": "PR #7 (copilot/x)"})
        self.assertNotIn("Cardkit Test Scout", r["dispatch"]["copilot"])
        self.assertIn("Cardkit Test Martyr", r["dispatch"]["copilot"])
        martyr = next(c for c in r["cards"] if c["name"] == "Cardkit Test Martyr")
        self.assertEqual(martyr["mentionedInOpenPRs"], ["#8"])

    @with_fake_oracle
    def test_queue_unit_matches_dispatcher_sections(self):
        body = gaps.queue_unit([readiness.classify("Cardkit Test Scout")])
        for section in ("## Cards", "## Acceptance", "Base: gpt/arcana-integration-next"):
            self.assertIn(section, body)
        self.assertIn("- Cardkit Test Scout: First strike, vigilance", body)


class ScaffoldTests(unittest.TestCase):
    @with_fake_oracle
    def test_scaffold_is_explicitly_unfinished(self):
        with tempfile.TemporaryDirectory() as tmp:
            info = scaffold.create("Cardkit Test Scout", Path(tmp) / "cards", Path(tmp) / "tests", online=False)
            card = (Path(tmp) / "cards/CardkitTestScout.kt").read_text()
            test = (Path(tmp) / "tests/CardkitTestScoutScenarioTest.kt").read_text()
        self.assertEqual(info["val"], "CardkitTestScout")
        self.assertIn('val CardkitTestScout = card("Cardkit Test Scout")', card)
        self.assertIn('manaCost = "{2}{W}"', card)
        self.assertIn('colorIdentity = "W"', card)
        self.assertIn("power = 2", card)
        self.assertIn("keywords(Keyword.FIRST_STRIKE, Keyword.VIGILANCE)", card)
        self.assertIn(repo.SCAFFOLD_MARK, card)
        self.assertIn(f"{repo.TODO_MARK}: Concealed Position", card)
        self.assertIn("Space Marine Scout [custom]", card)
        self.assertIn("fail(", test)
        self.assertIn(repo.SCAFFOLD_MARK, test)

    def test_refuses_cards_that_already_exist(self):
        with self.assertRaises(SystemExit):
            scaffold.create("Space Marine Scout", online=False)
        with self.assertRaises(SystemExit):
            scaffold.create("Sol Ring", online=False)


class CheckTests(unittest.TestCase):
    def test_repository_passes(self):
        errors, _ = check.run()
        self.assertEqual(errors, [])

    @with_fake_oracle
    def test_registered_scaffold_is_an_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            scaffold.create("Cardkit Test Scout", Path(tmp), Path(tmp), online=False)
            fake = repo.parse_card_file(Path(tmp) / "CardkitTestScout.kt")
            sources = repo.card_sources() + tuple(fake)
            entries = repo.registry_entries() + (repo.RegistryEntry("CardkitTestScout", "40K", "test"),)
            with mock.patch.object(repo, "card_sources", lambda: sources), \
                    mock.patch.object(repo, "registry_entries", lambda: entries):
                errors, _ = check.run()
        self.assertTrue(any("still a scaffold" in e for e in errors), errors)
        self.assertTrue(any("without a scenario test" in e for e in errors), errors)

    @with_fake_oracle
    def test_unregistered_scaffold_is_allowed(self):
        with tempfile.TemporaryDirectory() as tmp:
            scaffold.create("Cardkit Test Scout", Path(tmp), Path(tmp), online=False)
            fake = repo.parse_card_file(Path(tmp) / "CardkitTestScout.kt")
            sources = repo.card_sources() + tuple(fake)
            with mock.patch.object(repo, "card_sources", lambda: sources):
                errors, _ = check.run()
        self.assertEqual(errors, [])


class DocsTests(unittest.TestCase):
    def test_agent_docs_reference_only_real_api_and_code(self):
        self.assertEqual(docs_check.run(), [])

    def test_docs_check_catches_invented_api_and_fake_excerpts(self):
        src = "android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/Venomthrope.kt"
        with tempfile.TemporaryDirectory() as tmp:
            doc = Path(tmp) / "doc.md"
            doc.write_text("Use `Effects.DrawCardsAndWinTheGame(1)` and `Triggers.self.explodes()`.\n\n"
                           f"<!-- excerpt: {src} -->\n```kotlin\n    keywords(Keyword.FLYING, Keyword.INFECT)\n```\n"
                           "[gone](nowhere.md)\n")
            errors = docs_check.check_file(doc)
        self.assertTrue(any("DrawCardsAndWinTheGame" in e for e in errors))
        self.assertTrue(any("Triggers.self.explodes" in e for e in errors))
        self.assertTrue(any("excerpt line not found" in e for e in errors))
        self.assertTrue(any("broken link" in e for e in errors))


if __name__ == "__main__":
    unittest.main()
