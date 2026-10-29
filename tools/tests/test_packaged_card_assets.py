import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import warnings
from zipfile import ZipFile

spec = importlib.util.spec_from_file_location("packaged_cards", Path(__file__).parents[1] / "verify_packaged_card_assets.py")
cards = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cards)


class PackagedCardAssetsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.assets = self.root / "assets"
        self.assets.mkdir()
        self.data = {name: b"fixture\n" for name in cards.CARD_ASSETS}
        self.data["custom-cards.jsonl"] = (json.dumps({"name": "Resurrection Orb", "script": {"test": True}}) + "\n").encode()
        self.data["custom-cards.index.tsv"] = b"Resurrection Orb\tcard\n"
        for name, data in self.data.items():
            (self.assets / name).write_bytes(data)

    def package(self, extension="apk", changed=None, omitted=(), duplicate=None):
        path = self.root / ("test." + extension)
        prefix = "base/assets/argentum/" if extension == "aab" else "assets/argentum/"
        with ZipFile(path, "w") as package:
            for name, data in self.data.items():
                if name not in omitted:
                    package.writestr(prefix + name, (changed or {}).get(name, data))
            if duplicate:
                with warnings.catch_warnings():
                    warnings.simplefilter("ignore", UserWarning)
                    package.writestr(prefix + duplicate, self.data[duplicate])
        return path

    def test_apk_and_aab_match_every_runtime_asset(self):
        for extension in ("apk", "aab"):
            result = cards.verify(self.package(extension), self.assets, ["Resurrection Orb"])
            self.assertEqual(result["custom_card_count"], 1)
            self.assertEqual(set(result["asset_sha256"]), set(cards.CARD_ASSETS))

    def test_changed_card_rules_rejected_even_when_index_unchanged(self):
        with self.assertRaisesRegex(ValueError, "stale.*custom-cards.jsonl"):
            cards.verify(self.package(changed={"custom-cards.jsonl": b'{"name":"Resurrection Orb","script":{}}\n'}), self.assets)

    def test_missing_any_runtime_asset_rejected(self):
        for name in cards.CARD_ASSETS:
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "exactly one"):
                cards.verify(self.package(omitted=[name]), self.assets)

    def test_duplicate_zip_member_rejected(self):
        with self.assertRaisesRegex(ValueError, "exactly one"):
            cards.verify(self.package(duplicate="custom-cards.jsonl"), self.assets)

    def test_both_source_and_package_inconsistent_index_rejected(self):
        (self.assets / "custom-cards.index.tsv").write_bytes(b"Other Card\tcard\n")
        with self.assertRaisesRegex(ValueError, "names differ"):
            cards.verify(self.package(changed={"custom-cards.index.tsv": b"Other Card\tcard\n"}), self.assets)

    def test_required_card_not_implied_by_matching_bytes(self):
        with self.assertRaisesRegex(ValueError, "Required custom card absent"):
            cards.verify(self.package(), self.assets, ["Missing Card"])

    def test_empty_source_and_package_rejected(self):
        (self.assets / "custom-cards.jsonl").write_bytes(b"")
        with self.assertRaisesRegex(ValueError, "Empty"):
            cards.verify(self.package(changed={"custom-cards.jsonl": b""}), self.assets)


if __name__ == "__main__":
    unittest.main()
