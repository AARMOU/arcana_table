"""Verify archived application/runtime/existing tests are exact committed bytes."""
import hashlib,json,subprocess
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
report={}
for mode in ('baseline','studio'):
 stage=HERE/'build'/mode
 provenance=json.loads((stage/'provenance.json').read_text())
 rows=subprocess.check_output(['git','ls-tree','-rz',provenance['sha'],'android/app/src/main','android/app/src/test','android/app/src/testDebug','android/app/libs'],cwd=ROOT).split(b'\0')
 count=0; jars={}
 for row in rows:
  if not row: continue
  meta,name=row.split(b'\t',1)
  obj=meta.split()[2].decode()
  relative=name.decode()
  data=(stage/relative).read_bytes()
  actual=hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
  assert actual==obj, f'Unexpected source/runtime/test edit: {mode}/{relative}'
  count+=1
  if relative.endswith('.jar'): jars[relative]={'git_blob':obj,'sha256':hashlib.sha256(data).hexdigest()}
 report[mode]={'sha':provenance['sha'],'committed_files_verified':count,'jars':jars}
print(json.dumps(report,indent=2))
