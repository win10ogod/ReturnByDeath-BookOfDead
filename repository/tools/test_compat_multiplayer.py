#!/usr/bin/env python3
"""Disposable loopback server plus three isolated Xvfb clients. Requires exported NeoGradle launch specs."""
from __future__ import annotations
import argparse,hashlib,json,os,signal,socket,subprocess,sys,time
from pathlib import Path

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--specs',required=True,type=Path)
parser.add_argument('--run-root',required=True,type=Path)
parser.add_argument('--timeout',type=int,default=1200)
a=parser.parse_args();root=a.run_root.resolve()
if root.exists():raise SystemExit('Use a new run-root; existing test evidence is never overwritten.')
root.mkdir(parents=True);(root/'.rbd-test-fixture').write_text('Explicit disposable compatibility test.\n')
server=root/'server';server.mkdir();(server/'world').mkdir()
# This is solely the local automated game test instance requested by the workspace owner.
(server/'eula.txt').write_text('eula=true\n')
with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
(server/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\nlevel-name=world\nlevel-seed=8675309\ngamemode=survival\ndifficulty=normal\nspawn-protection=0\nview-distance=6\nsimulation-distance=6\nmax-tick-time=0\n')
config=server/'config';config.mkdir();(config/'rbd-common.toml').write_text('maxHolders=2\nautoBindOnJoin=false\nautoBindSingleplayer=false\n')
base_env=os.environ.copy()
for key in ('WAYLAND_DISPLAY','WAYLAND_SOCKET','DISPLAY','XAUTHORITY'):base_env.pop(key,None)
base_env.update(XDG_SESSION_TYPE='x11',LIBGL_ALWAYS_SOFTWARE='1',ALSOFT_DRIVERS='null')
processes=[];logs=[]
def spec(side):return json.loads((a.specs/f'{side}.json').read_text())
def launch(side,role=None):
    data=spec(side);cwd=server if side=='server' else root/role
    cwd.mkdir(exist_ok=True)
    jvm=[x for x in data['jvm'] if not x.startswith('-Drbd.liveTest=')]
    jvm+=['-Drbd.liveTest=false','-Drbd.compatTest=true',f'-Drbd.testRoot={root}','-Xmx6G']
    args=list(data['args']);env=base_env|data['environment']
    if role:
        runtime=cwd/'xdg';runtime.mkdir(mode=0o700);env['XDG_RUNTIME_DIR']=str(runtime)
        jvm += [f'-Drbd.testRole={role}',f'-Drbd.testAddress=127.0.0.1:{port}']
        args+=['--username',role,'--width','854','--height','480']
        # Explicit fixture viewport and view distance; memory images remain native and every tick.
        (cwd/'options.txt').write_text('lang:zh_tw\nrenderDistance:6\nsimulationDistance:6\nmaxFps:60\npauseOnLostFocus:false\n')
    command=[data['java'],*jvm,'-cp',data['classpath'],data['main'],*args]
    if side=='server':
        supervisor=Path(__file__).resolve().parents[1]/'supervisor/supervisor.py'
        command=[sys.executable,str(supervisor),'--world',str(server/'world'),'--control',str(root/'control'),'--cwd',str(server),'run','--',*command]
    else:command=['xvfb-run','-a','-s','-screen 0 1280x720x24',*command]
    log=(root/f'{role or side}.log').open('w');logs.append(log)
    process=subprocess.Popen(command,cwd=cwd,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
    processes.append((role or side,process));return process
try:
    launch('server')
    for role in ('RbdA','RbdB','RbdC'):launch('client',role)
    (root/'runner.json').write_text(json.dumps({'serverAddress':f'127.0.0.1:{port}','processes':{name:p.pid for name,p in processes},'isolation':'Separate Xvfb per client; desktop display and Wayland variables removed; private XDG runtime directories; software rendering and null audio','fixtureViewport':[854,480],'maxFps':60,'renderDistance':6,'memoryImageIntervalTicks':1,'memoryWidth':0},indent=2))
    print('Started isolated compatibility fixture:',root,flush=True)
    deadline=time.monotonic()+a.timeout;previous=None
    while time.monotonic()<deadline:
        file=root/'scenario.json'
        if file.exists():
            stage=json.loads(file.read_text()).get('stage')
            if stage!=previous:print('Scenario stage',stage,flush=True);previous=stage
        report=root/'report.json'
        if report.exists():
            result=json.loads(report.read_text());print(json.dumps(result,indent=2),flush=True)
            if result['result']!='PASS':raise RuntimeError(result)
            for name,p in processes:p.wait(timeout=40)
            for name,p in processes:
                if p.returncode:raise RuntimeError(f'{name} exited {p.returncode}')
                if name!='server':
                    done=json.loads((root/f'{name}-done.json').read_text())
                    assert done['joins']>=4,(name,done)
            print('Three clients each joined at least four times; supervisor and clients exited cleanly.',flush=True)
            break
        for name,p in processes:
            if p.poll() is not None:raise RuntimeError(f'{name} exited early with {p.returncode}; see its log')
        time.sleep(2)
    else:raise TimeoutError(f'Fixture did not complete in {a.timeout}s; evidence retained at {root}')
finally:
    for _,p in processes:
        if p.poll() is None:
            try:os.killpg(p.pid,signal.SIGINT)
            except ProcessLookupError:pass
    for _,p in processes:
        try:p.wait(timeout=20)
        except subprocess.TimeoutExpired:
            os.killpg(p.pid,signal.SIGTERM);p.wait(timeout=10)
    for log in logs:log.close()
