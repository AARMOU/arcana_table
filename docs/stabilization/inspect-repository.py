#!/usr/bin/env python3
"""Read-only inventory of a freshly fetched repository and explicit API snapshots."""
import json, subprocess, sys
from pathlib import Path
import yaml

root, inputs, runs_path, output = map(Path, sys.argv[1:])
data = json.loads(inputs.read_text())
runs = json.loads(runs_path.read_text())
def git(*args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True)
def ancestor(a,b):
    return subprocess.run(['git','-C',str(root),'merge-base','--is-ancestor',a,b],capture_output=True).returncode==0
prs=data['prs']
for p in prs:
    p['exact_head_runs']=runs[str(p['number'])]
    p['in_target_history']=ancestor(p['head_sha'], 'origin/'+p['base'])
    p['in_b131_history']=ancestor(p['head_sha'], '98626c7709023346d5f45756bceb864bc3041766')
    p['head_tree']=git('rev-parse',p['head_sha']+'^{tree}').strip()
    p['target_tree']=git('rev-parse','origin/'+p['base']+'^{tree}').strip()
    p['identical_tree_to_target']=p['head_tree']==p['target_tree']
    p['changed_paths']=git('diff','--name-only','origin/'+p['base'],p['head_sha']).splitlines()
    p['depends_on_prs']=[q['number'] for q in prs if q['head']==p['base']]
    p['disposition']='hold: review and combined exact-head CI required'
    if p['in_b131_history']:p['disposition']='preserved in B131 candidate history; avoid reapplying'
    if p['identical_tree_to_target']:p['disposition']='hold: no effective tree changes; do not launch duplicate worker'
    if p['number'] in [62,70,76]:p['disposition']='blocked by shared Android fixture regression; fix once on isolated base'
    if p['number'] in [79,69]:p['disposition']='hold: current measured usage and safe rollout not verified'
    p.pop('body',None)
variants={}
for b in data['branches']:
    for line in git('ls-tree','-r',b['sha'],'.github/workflows').splitlines():
        meta,path=line.split('\t',1)
        sha=meta.split()[2]
        v=variants.setdefault(sha,{'path':path,'branches':[]})
        v['branches'].append(b['name'])
for sha,v in variants.items():
    raw=git('show',sha)
    d=yaml.safe_load(raw) or {}
    v.update(blob_sha=sha,name=d.get('name'),triggers=d.get('on',d.get(True)),permissions=d.get('permissions'),
      jobs=[{'id':key,'permissions':job.get('permissions'),
         'commands':[s['run'] for s in job.get('steps',[]) if 'run' in s],
         'actions':[s['uses'] for s in job.get('steps',[]) if 'uses' in s]}
        for key,job in d.get('jobs',{}).items()])
    v['writes_git']=('git push' in raw or 'createCommit' in raw)
data['workflow_variants']=list(variants.values())
data['scope']='Metadata, lineage, tree and workflow inventory; not a full semantic review of every changed line.'
data['issues']=[{k:v for k,v in i.items() if k!='body'} for i in data['issues']]
output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps(data,indent=2)+'\n')
print(f"Inventoried {len(prs)} PRs, {len(data['issues'])} issues, {len(data['branches'])} branches, {len(variants)} distinct workflow blobs")
rows=['# Refreshed PR integration queue','',
 'Exact-head workflow results are specific to each SHA. Agent-session success is not a test result. No merges are authorized.','',
 '| PR | Owner | Head SHA | Target | Exact-head CI | Disposition |',
 '|---|---|---|---|---|---|']
for p in prs:
    rs=p['exact_head_runs']
    ci='; '.join(f"[{r['id']}]({r['url']}): {r['name']} / {r['conclusion']}" for r in rs) or 'No exact-head run found'
    rows.append(f"| [#{p['number']}]({p['url']}) | {p['owner']} | `{p['head_sha']}` | `{p['base']}` | {ci} | {p['disposition']} |")
output.with_suffix('.md').write_text('\n'.join(rows)+'\n')
