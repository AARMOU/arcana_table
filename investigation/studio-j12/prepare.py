"""Create immutable-source measurement stages inside this workspace; never edits source branches."""
import io,json,os,shutil,subprocess,zipfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
BUILD=HERE/'build'
PINS={'baseline':'01053b6024f748773e90eb63550094ef65688439','studio':'3ccba4e'}
FIXTURE='577d40f5bd6cc77ce5e0c06d3e8f572108277c6d'
def prepare(mode):
    sha=subprocess.check_output(['git','rev-parse',PINS[mode]],cwd=ROOT,text=True).strip()
    stage=BUILD/mode
    if stage.exists():
        raise SystemExit(f'Existing stage retained: {stage}; inspect before reuse')
    stage.mkdir(parents=True)
    archive=subprocess.check_output(['git','archive', '--format=zip',sha,'android'],cwd=ROOT)
    with zipfile.ZipFile(io.BytesIO(archive)) as z:
        for name in z.namelist():
            if not (stage/name).resolve().is_relative_to(stage.resolve()): raise RuntimeError(name)
        z.extractall(stage)
    tests=stage/'android/app/src/test/java/com/arcanatable/ui'
    shutil.copyfile(HERE/'J12PerformanceTest.kt',tests/'J12PerformanceTest.kt')
    shutil.copyfile(HERE/f'{mode}.kt',tests/'J12Mode.kt')
    fixture=stage/'android/app/src/test/resources/argentum/studio-busy-four-player.json'
    fixture.write_bytes(subprocess.check_output(['git','show',f'{FIXTURE}:android/app/src/test/resources/argentum/studio-busy-four-player.json'],cwd=ROOT))
    (stage/'provenance.json').write_text(json.dumps({'sha':sha,'fixture_sha':FIXTURE,'mode':mode},indent=2))
    return stage
if __name__=='__main__':
    import sys
    print(prepare(sys.argv[1]))
