#!/usr/bin/env python3
"""Sequential evidence for pixel-cell and debugger caching, using the installed Bitwig JVM."""
from pathlib import Path
import json,os,subprocess,time
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'target/display-benchmark/round2'
FRAMES=ROOT/'target/display-benchmark/frames'
PROFILER=ROOT/'target/display-benchmark/deps/async-profiler-4.5-macos/lib/libasyncProfiler.dylib'
OUT.mkdir(parents=True,exist_ok=True)
if not PROFILER.exists() or len(list(FRAMES.glob('*.png'))) != 8:
    raise SystemExit('See README: provision the profiler and eight capture fixtures first.')
commands=[]
frame_arg='-Dpull.benchmark.frames='+str(FRAMES)
def run(name,pattern,args):
    cmd=[str(ROOT/'tools/benchmark-push-display'),*args]
    print('START',name,flush=True);start=time.monotonic()
    with (OUT/(name+'.log')).open('w') as log:
        subprocess.run(cmd,cwd=ROOT,env=dict(os.environ,PULL_BENCH_FILTER=pattern),stdout=log,stderr=subprocess.STDOUT,check=True)
    commands.append(dict(name=name,filter=pattern,command=cmd,seconds=time.monotonic()-start))
    (OUT/'commands.json').write_text(json.dumps(commands,indent=2)+'\n')
    print('DONE',name,flush=True)
def average(name,pattern,content):
    run(name,pattern,['-p','content='+content,'-f','3','-wi','4','-i','5',
        '-jvmArgsAppend',frame_arg,'-rf','json','-rff',str(OUT/(name+'.json'))])
average('timings',r'\.FrameBenchmark\.(rowCached|optimized)$','capture,random,gray,unchanged,playhead,patch,macro')
average('debug-timings',r'\.DebugFrameBenchmark\.(baseline|optimized)$','capture,random,unchanged,macro')
run('without-superword',r'\.FrameBenchmark\.optimized$',
    ['-p','content=random','-f','3','-wi','4','-i','5','-jvmArgsAppend','-XX:-UseSuperWord','-rf','json','-rff',str(OUT/'without-superword.json')])
run('latency',r'\.FrameBenchmark\.(rowCached|optimized)$',
    ['-p','content=capture,macro','-f','2','-wi','3','-i','2','-r','3s','-bm','sample','-jvmArgsAppend',frame_arg,'-rf','json','-rff',str(OUT/'latency.json')])
for benchmark,methods,content in [('FrameBenchmark',('rowCached','optimized'),'capture'),('FrameBenchmark',('rowCached','optimized'),'macro'),('DebugFrameBenchmark',('baseline','optimized'),'unchanged')]:
    for method in methods:
        run('profile-'+benchmark+'-'+method+'-'+content,r'\.'+benchmark+r'\.'+method+'$',
            ['-p','content='+content,'-f','1','-wi','3','-i','1','-r','12s','-jvmArgsAppend',frame_arg,'-prof',
             'async:libPath='+str(PROFILER)+';event=cpu;interval=1000000;output=flamegraph;dir='+str(OUT/'profiles')])
run('assembly',r'\.FrameBenchmark\.optimized$',
    ['-p','content=random','-f','1','-wi','3','-i','1','-jvmArgsAppend','-Xbatch -XX:CompileCommand=print,de.mossgrabers.controller.ableton.push.controller.PushFrameEncoder::*'])
print('Evidence suite complete.',flush=True)
