"""Preserve successful current-run evidence. Refuse stale, failed or mismatched measurements."""
import hashlib,json,shutil,sys,xml.etree.ElementTree as ET
from pathlib import Path
HERE=Path(__file__).resolve().parent
stage=HERE/'build'/sys.argv[1]
label=sys.argv[2]
log=HERE/'build'/sys.argv[3]
expected_mode=sys.argv[4]
tests=stage/'android/app/src/test/java/com/arcanatable/ui'
canonical=HERE/'J12PerformanceTest.kt'
assert canonical.read_bytes().replace(b'\r\n',b'\n')==(tests/canonical.name).read_bytes().replace(b'\r\n',b'\n'), 'Stage harness mismatch'
xml=stage/'android/app/build/test-results/testDebugUnitTest/TEST-com.arcanatable.ui.J12PerformanceTest.xml'
raw=stage/'android/app/build/j12/results.json'
assert 'BUILD SUCCESSFUL' in log.read_text(encoding='utf-8',errors='replace'), 'Build not successful'
assert xml.stat().st_mtime>=canonical.stat().st_mtime and raw.stat().st_mtime>=canonical.stat().st_mtime, 'Stale results'
tree=ET.parse(xml).getroot()
assert tree.attrib['tests']=='1' and all(tree.attrib.get(k,'0')=='0' for k in ('failures','errors','skipped')), 'Not a passing measurement'
summary=stage/'android/app/build/j12/summary.json'
assert summary.stat().st_mtime>=raw.stat().st_mtime, 'Summary stale'
data=json.loads(summary.read_text())
assert data['mode']==expected_mode, 'Wrong mode (check J12_UNEDITED)'
for screen in ('table','player'):
 row=data[screen]
 assert row['measuredUpdates']==1000 and len(row['samplesMs'])==1000
 assert row['warmupUpdates']==200 and row['recomposerChangeCount']>=1000
 assert all(isinstance(x,(float,int)) and x>0 for x in row['samplesMs'])
data['provenance']=json.loads((stage/'provenance.json').read_text())
data['harness_sha256']=hashlib.sha256((tests/canonical.name).read_bytes()).hexdigest()
data['harness_lf_sha256']=hashlib.sha256(canonical.read_bytes().replace(b'\r\n',b'\n')).hexdigest()
data['mode_adapter_sha256']=hashlib.sha256((tests/'J12Mode.kt').read_bytes()).hexdigest()
data['junit']={k:v for k,v in tree.attrib.items() if k!='hostname'}
data['jfr_sha256']={s:hashlib.sha256((stage/f'android/app/build/j12/{s}.jfr').read_bytes()).hexdigest() for s in ('table','player')}
evidence=HERE/'evidence'; evidence.mkdir(exist_ok=True)
assert not (evidence/f'{label}.json').exists(), 'Evidence already exists; never overwrite'
(evidence/f'{label}.json').write_text(json.dumps(data,indent=2))
for screen in ('table','player'):
 source=stage/f'android/app/build/screenshots/j12-{data["mode"]}-{screen}.png'
 shutil.copyfile(source,evidence/f'{label}-{screen}.png')
 shutil.copyfile(stage/f'android/app/build/j12/{screen}.jfr',evidence/f'{label}-{screen}.jfr')
print(label,[(s,data[s]['medianMs'],data[s]['p95Ms']) for s in ('table','player')])
