#!/usr/bin/env python3
"""Verify pinned B131 file provenance and optionally restore every tracked file in isolation."""
from pathlib import Path
import hashlib, json, subprocess, sys, tarfile, tempfile

root=Path(sys.argv[1]).resolve()
sha='98626c7709023346d5f45756bceb864bc3041766'
def git(*args,**kw):return subprocess.check_output(['git','-C',str(root),*args],**kw)
assert git('rev-parse','HEAD',text=True).strip()==sha, 'Use an isolated checkout of the pinned baseline'
m=json.loads((root/'docs/integration/B131_RUNTIME.json').read_text())
assert m['upstream']=='61e71e586b100faa661f86cac827ac3c3bbb5a78'
for name,expected in m['files'].items():
 p=root/'android/app'/('libs' if name.startswith('libs/') else 'src/main/assets/argentum')/Path(name).name
 assert hashlib.sha256(p.read_bytes()).hexdigest()==expected,name
assets=root/'android/app/src/main/assets/argentum'
names=[s.split('\t')[0] for s in (assets/'custom-cards.index.tsv').read_text().splitlines() if s.strip()]
data=[json.loads(s)['name'] for s in (assets/'custom-cards.jsonl').read_text().splitlines() if s.strip()]
stock={s.split('\t')[0].casefold() for s in (assets/'all-cards.index.tsv').read_text().splitlines() if s.strip()}
assert names==data and len(names)==len(set(n.casefold() for n in names))==211
assert not [n for n in names if n.casefold() in stock]
restored=0
if '--restore' in sys.argv:
 with tempfile.TemporaryDirectory(prefix='arcana-b131-restore-') as dest:
  archive=Path(dest)/'baseline.tar'
  with archive.open('wb') as f:subprocess.run(['git','-C',str(root),'archive',sha],stdout=f,check=True)
  target=Path(dest)/'restored'
  with tarfile.open(archive) as t:t.extractall(target,filter='data')
  for name in git('ls-tree','-r','--name-only',sha,text=True).splitlines():
   assert hashlib.sha256((root/name).read_bytes()).digest()==hashlib.sha256((target/name).read_bytes()).digest(),name
   restored+=1
print(json.dumps({'source_sha':sha,'runtime_files_verified':len(m['files']),
 'custom_definitions':len(names),'restored_files':restored,'result':'pass'}))
