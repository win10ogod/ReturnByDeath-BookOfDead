#!/usr/bin/env python3
"""Package the integration JAR and explicitly tracked source files only."""
import argparse
import hashlib
import os
import re
import shutil
import subprocess
import tomllib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / 'riderpack-integration'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--check-version', action='store_true')
args = parser.parse_args()
version = tomllib.loads((PROJECT / 'src/main/resources/META-INF/neoforge.mods.toml').read_text())['mods'][0]['version']
if not re.fullmatch(r'\d+\.\d+\.\d+', version):
    raise SystemExit('Invalid integration version')
if f"version = '{version}'" not in (PROJECT / 'build.gradle').read_text():
    raise SystemExit('Gradle and mod metadata versions differ')
if os.environ.get('GITHUB_REF_TYPE') == 'tag' and os.environ.get('GITHUB_REF_NAME') != 'riderpack-v' + version:
    raise SystemExit('Tag must equal riderpack-v' + version)
if args.check_version:
    print('Integration version:', version)
    raise SystemExit(0)

source = PROJECT / f'build/libs/riderpack-integration-{version}.jar'
with zipfile.ZipFile(source) as archive:
    assert archive.testzip() is None
    assert tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode())['mods'][0]['version'] == version
    assert 'dev/riderpack/mixin/AcceleratedMeshIdentityMixin.class' in archive.namelist()
    assert 'dev/riderpack/mixin/ImmediatelyFastMeshLayerMixin.class' in archive.namelist()
    assert all(name.startswith(('META-INF/', 'dev/riderpack/', 'assets/riderpack/', 'data/')) or name in {'dev/', 'assets/', 'riderpack.mixins.json'} for name in archive.namelist())

dist = ROOT / 'dist/riderpack'
dist.mkdir(parents=True, exist_ok=True)
jar = dist / source.name
shutil.copyfile(source, jar)
sources = dist / f'riderpack-integration-{version}-source.zip'
paths = ['riderpack-integration', 'tools/riderpack_dependencies.py', 'tools/riderpack_artifacts.py', '.github/workflows/riderpack-release.yml']
subprocess.run(['git', 'archive', '--format=zip', '--output', str(sources), 'HEAD', '--', *paths], cwd=ROOT, check=True)
with zipfile.ZipFile(sources) as archive:
    assert archive.testzip() is None
    assert all('/build/' not in name and '/.gradle/' not in name and '/run/' not in name for name in archive.namelist())
for asset in [jar, sources]:
    asset.with_suffix(asset.suffix + '.sha256').write_text(hashlib.sha256(asset.read_bytes()).hexdigest() + '  ' + asset.name + '\n')
    print('Release asset:', asset.name, asset.stat().st_size)
