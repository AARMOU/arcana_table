"""Summarize JFR samples after a successful J12 test; inclusive counts are not exact durations."""
import collections,json,os,subprocess,sys
from pathlib import Path
stage=Path(sys.argv[1]).resolve()
java_home=Path(os.environ['JAVA_HOME'])
output=stage/'android/app/build/j12'
results=json.loads((output/'results.json').read_text())
for label in ('table','player'):
    raw=subprocess.check_output([str(java_home/'bin/jfr.exe'),'print','--json','--events','jdk.ExecutionSample,jdk.GarbageCollection',str(output/f'{label}.jfr')])
    data=json.loads(raw)
    counts=collections.Counter(); leaf=collections.Counter(); samples=gc=0
    for event in data['recording']['events']:
        if event['type']=='jdk.GarbageCollection':
            gc+=1; continue
        if event['type']!='jdk.ExecutionSample': continue
        samples+=1
        frames=(event['values'].get('stackTrace') or {}).get('frames',[])
        names=[f["method"]["type"]["name"].replace('/','.')+'.'+f['method']['name'] for f in frames]
        counts.update(set(names))
        if names: leaf[names[0]]+=1
    results[label].update(jfrExecutionSamples=samples,gcEvents=gc,
        topInclusiveSampleFrames=dict(counts.most_common(30)),topLeafSampleFrames=dict(leaf.most_common(20)),
        applicationInclusiveSampleFrames={k:v for k,v in counts.most_common() if k.startswith('com.arcanatable.')})
    print(label, results[label]['medianMs'],results[label]['p95Ms'],results[label]['recomposerChangeCount'],samples)
(output/'summary.json').write_text(json.dumps(results,indent=2))
