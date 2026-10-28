#!/usr/bin/env python3
"""Evidence-based original-slot audit; index matches alone never yield category 1."""
from pathlib import Path
from collections import Counter
import argparse
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / 'android/app/src/main/assets'
MAIN = ROOT / 'android/custom-cards/src/main/kotlin/com/arcanatable/customcards'
TESTS = ROOT / 'android/custom-cards/src/test/kotlin/com/arcanatable/customcards'
POLICY = json.loads((Path(__file__).with_name('review-policy.json')).read_text())
LABELS = {1: 'Fully implemented and tested (reviewed acceptance scope)', 2: 'Implemented but missing acceptance tests/evidence', 3: 'Implemented but failing tests', 4: 'Missing implementation', 5: 'Blocked by pinned Argentum capability/runtime'}

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def audit(junit_dir):
    manifest_path = ROOT / 'investigation/argentum-six-precons/deck-manifest.json'
    deck = next(d for d in json.loads(manifest_path.read_text())['decks'] if d['name'] == 'Eldrazi Incursion')
    assert sum(c['quantity'] for c in deck['cards']) == 100
    bundled = Counter()
    for line in (ASSETS / 'precons/eldrazi_incursion.txt').read_text().splitlines():
        match = re.fullmatch(r'(\d+)\s+(.+)', line.strip())
        if match:
            bundled[match[2]] += int(match[1])
    assert bundled == Counter({c['name']: c['quantity'] for c in deck['cards']}), 'Picker asset differs from original manifest'
    definitions = {}
    indexes = {}
    for stem in ['all-cards', 'custom-cards']:
        index_path = ASSETS / f'argentum/{stem}.index.tsv'
        index = [s.split('\t')[0] for s in index_path.read_text().splitlines() if s.strip()]
        data = [json.loads(s) for s in (ASSETS / f'argentum/{stem}.jsonl').read_text().splitlines() if s.strip()]
        assert index == [c['name'] for c in data], f'{stem} data/index mismatch'
        assert len(index) == len(set(index)), f'{stem} duplicate names'
        indexes[stem] = set(index)
        for c in data:
            definitions.setdefault(c['name'], (stem, c))  # Library wins, matching Android.
    sources = {}
    for p in (MAIN / 'cards').glob('*.kt'):
        match = re.search(r'card\("([^"]+)"\)', p.read_text())
        if match:
            sources[match[1]] = p
    registry = (MAIN / 'CustomCards.kt').read_text()
    observed = {}
    for p in junit_dir.glob('*.xml'):
        x = ET.parse(p).getroot()
        observed[x.get('name').split('.')[-1]] = {k: int(x.get(k, 0)) for k in ['tests', 'failures', 'errors', 'skipped']}
    assert observed, 'No executable JUnit evidence supplied'
    rows = []
    for c in deck['cards']:
        name = c['name']; found = definitions.get(name); source = sources.get(name)
        test = TESTS / (source.stem + 'ScenarioTest.kt') if source else None
        evidence = observed.get(test.stem) if test and test.exists() else None
        row = dict(name=name, quantity=c['quantity'], commander=c.get('commander', False), indexed=bool(found))
        row['source'] = str(source.relative_to(ROOT)) if source else ('bundled Argentum definition' if found else None)
        row['test'] = str(test.relative_to(ROOT)) if test and test.exists() else None
        row['junit'] = evidence
        row['definitionHasBehavior'] = bool(found and (found[1].get('script') or found[1].get('keywords') or found[1].get('keywordAbilities')))
        if name in POLICY['blocked']:
            category=5; reason=POLICY['blocked'][name]
        elif not found:
            category=4; reason=POLICY['missing'].get(name, 'No registered/exported implementation.')
        elif evidence and (evidence['failures'] or evidence['errors']):
            category=3; reason='Observed card-specific test failure on candidate runtime.'
        elif name in POLICY['incompleteAcceptance']:
            category=2; reason=POLICY['incompleteAcceptance'][name]
        elif found[0] == 'custom-cards' and source and f'Entry({source.stem},' in registry and row['definitionHasBehavior'] and evidence and evidence['tests'] >= 2 and evidence['skipped'] == 0:
            category=1; reason='Registered nonempty definition; reviewed positive/negative card-specific scenarios executed green. Scope is the assertions in the linked suite, not all possible interactions.'
        else:
            category=2; reason='Nonempty bundled definition inspected, but no reviewed positive/negative card-specific execution against this candidate runtime supplied. Upstream/index presence is not certification.'
        row.update(category=category, classification=LABELS[category], reason=reason)
        if source:
            row['sourceSha256'] = digest(source)
        if test and test.exists():
            row['testSha256'] = digest(test)
        rows.append(row)
    return {
        'deck': deck['name'], 'originalSlots': 100,
        'sourceBase': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'scope': POLICY['scope'], 'pickerAssetMatchesOriginalManifest': True,
        'indexedSlots': sum(r['quantity'] for r in rows if r['indexed']),
        'categorySlots': {str(k): sum(r['quantity'] for r in rows if r['category']==k) for k in LABELS},
        'runtimeSha256': {p.name: digest(p) for p in sorted((ROOT / 'android/app/libs').glob('argentum-*.jar'))},
        'assetSha256': {str(p.relative_to(ROOT)): digest(p) for p in [manifest_path, ASSETS / 'precons/eldrazi_incursion.txt', *sorted((ASSETS / 'argentum').glob('*cards.index.tsv')), *sorted((ASSETS / 'argentum').glob('*cards.jsonl'))]},
        'junitTotals': {k: sum(x[k] for x in observed.values()) for k in ['tests','failures','errors','skipped']},
        'cards': rows,
    }

if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--junit-dir', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args=parser.parse_args()
    report=audit(args.junit_dir)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / 'coverage.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
    lines=['# Eldrazi Incursion: original 100-slot evidence audit', '', f"Indexed: **{report['indexedSlots']}/100**. This is not playable coverage.", '', report['scope'], '', '| Category | Slots |', '|---|---:|']
    for k, label in LABELS.items():
        lines.append(f"| {k}. {label} | {report['categorySlots'][str(k)]} |")
    lines += ['', 'Reported/API-only blockers are distinguished from reproduced failures in each row.', '', '| Original card | Qty | Category | Evidence / remaining gap |', '|---|---:|---:|---|']
    for r in report['cards']:
        test=f" `{Path(r['test']).name}` ({r['junit']['tests']} green cases)." if r['junit'] and not (r['junit']['failures'] or r['junit']['errors']) else ''
        lines.append(f"| {r['name']} | {r['quantity']} | {r['category']} | {r['reason']}{test} |")
    (args.output / 'COVERAGE.md').write_text('\n'.join(lines)+'\n')
    print(json.dumps({k:report[k] for k in ['indexedSlots','categorySlots','junitTotals']}))
