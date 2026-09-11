#!/usr/bin/env python3
"""CI release inputs are the tested JAR and tracked sources; no caches or local test worlds."""
import argparse,hashlib,os,re,shutil,subprocess,tomllib,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--check-version',action='store_true')
a=parser.parse_args()
props=dict(line.split('=',1) for line in (root/'repository/gradle.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
version=props['mod_version'].strip()
if not re.fullmatch(r'\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?',version):raise SystemExit('Invalid mod version')
if os.environ.get('GITHUB_REF_TYPE')=='tag' and os.environ.get('GITHUB_REF_NAME')!='v'+version:
    raise SystemExit('Git tag must equal v'+version)
if a.check_version:print('Mod version:',version);raise SystemExit(0)
source=root/f'repository/build/libs/rbd-{version}.jar'
with zipfile.ZipFile(source) as z:
    assert z.testzip() is None
    assert tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode())['mods'][0]['version']==version
    assert 'dev/rbd/io/AuthorityRoster.class' in z.namelist()
    assert not any(n.startswith(('net/minecraft/','twilightforest/','com/kelco/')) for n in z.namelist())
dist=root/'dist';dist.mkdir(exist_ok=True)
jar=dist/source.name;shutil.copy2(source,jar)
archive=dist/f'rbd-{version}-source.zip'
subprocess.run(['git','archive','--format=zip','--output',str(archive),'HEAD'],cwd=root,check=True)
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    assert not any('/run/' in n or '/.rbd/' in n or n.startswith(('validation/', 'docs/', 'provenance/')) for n in z.namelist())
    assert {n for n in z.namelist() if n.lower().endswith('.md')} <= {'README.md'}, 'Internal documents must never enter release sources'
for p in (jar,archive):
    p.with_suffix(p.suffix+'.sha256').write_text(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n')
    print('Release asset:',p.name,p.stat().st_size,'bytes')
