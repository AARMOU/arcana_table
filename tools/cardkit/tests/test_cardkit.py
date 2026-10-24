"""cardkit tests against the real Arcana card definitions, registry, assets and SDK index.

    python3 -m unittest discover -s tools/cardkit/tests -v
"""
from __future__ import annotations

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
FAKES = {repo.normalize_name(c["name"]): c for c in (FAKE_SUPPORTED, FAKE_GAP, FAKE_UNKNOWN_KW, FAKE_INFECT)}


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
        self.assertEqual(v["source"], "custom")

    def test_argentum_library_card(self):
        v = readiness.classify("Sol Ring")
        self.assertEqual(v["readiness"], "IMPLEMENTED_AND_TESTED")
        self.assertEqual(v["source"], "argentum-library")

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
            self.assertIn(f"{d['name']}: {d['playableSlots']}/100 supported", audit)
        self.assertEqual(sum(result["totals"].values()), len(result["cards"]))
        self.assertTrue(set(result["dispatch"]) == {"copilot", "claude", "gpt"})

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
