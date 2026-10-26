"""Focused source-merge regressions for owner-requested tutorial step 2."""
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from next_apk_tutorial_hand_fix import PLAYER_REQUIRED, TABLE_FIXED, TABLE_OLD, patch_ui, verify_lesson

UI_SAMPLE = (
    'if (showOverview) {\n'
    '    if (argentumEnabled && gameInProgress) HandPeekBar(\n'
    '                    ' + TABLE_OLD + '\n'
    '    )\n'
    '} else {\n'
    '                    ' + PLAYER_REQUIRED + '\n'
    '}\n'
)

GOOD_CATALOG = {
    "chapters": [
        {"id": "first-turn", "steps": [
            {"id": "welcome", "advance": {"kind": "next"}},
            {"id": "hand-handle", "target": "hand-handle", "advance": {"kind": "tap"}},
            {"id": "forest", "title": "Play a land", "advance": {"kind": "event", "event": "LandPlayed"}},
            *({"id": f"step-{i}"} for i in range(3, 16)),
        ]},
        *({"id": f"chapter-{i}"} for i in range(1, 20)),
    ],
}


class TutorialStep2NextApkTest(unittest.TestCase):
    def test_patches_table_without_changing_player(self):
        output = patch_ui(UI_SAMPLE)
        self.assertEqual(output.count(TABLE_FIXED), 1)
        self.assertEqual(output.count(PLAYER_REQUIRED), 1)
        self.assertEqual(output.count('TutorialActions.tap("hand-handle")'), 2)
        self.assertNotIn(TABLE_OLD, output)

    def test_port_is_idempotent_if_upstream_fix_is_already_in_source(self):
        output = patch_ui(UI_SAMPLE)
        self.assertEqual(patch_ui(output), output)

    def test_unknown_table_callback_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "changed or duplicated"):
            patch_ui(UI_SAMPLE.replace("showHand = true }", "showHand = false }", 1))

    def test_missing_player_callback_fails_closed(self):
        with self.assertRaisesRegex(ValueError, "Player-view"):
            patch_ui(UI_SAMPLE.replace("TutorialActions.tap(\"hand-handle\")", "Unit", 1))

    def test_first_turn_step2_and_engine_step3(self):
        verify_lesson(GOOD_CATALOG)

    def test_step2_wrong_target_fails(self):
        bad = json.loads(json.dumps(GOOD_CATALOG))
        bad["chapters"][0]["steps"][1]["target"] = "hand-card:Forest"
        with self.assertRaisesRegex(ValueError, "HAND handle"):
            verify_lesson(bad)

    def test_step3_cannot_advance_on_tap(self):
        bad = json.loads(json.dumps(GOOD_CATALOG))
        bad["chapters"][0]["steps"][2]["advance"] = {"kind": "tap"}
        with self.assertRaisesRegex(ValueError, "engine-confirmed"):
            verify_lesson(bad)


if __name__ == "__main__":
    unittest.main()
