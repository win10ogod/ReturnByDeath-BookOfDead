#!/usr/bin/env python3
"""Fetch the pinned build dependencies; third-party JARs stay in the ignored build directory."""
import hashlib
import json
import shutil
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'riderpack-integration'


def fetch():
    target = ROOT / 'build/dependencies'
    target.mkdir(parents=True, exist_ok=True)
    dependencies = json.loads((ROOT / 'dependencies.json').read_text())
    expected = {entry['file'] for entry in dependencies}
    unexpected = {file.name for file in target.glob('*.jar')} - expected
    if unexpected:
        raise SystemExit(f'Unexpected build dependencies: {sorted(unexpected)}')
    for entry in dependencies:
        name = entry['file']
        if Path(name).name != name or not name.endswith('.jar'):
            raise SystemExit('Invalid dependency filename')
        path = target / name
        if path.exists() and hashlib.sha256(path.read_bytes()).hexdigest() == entry['sha256']:
            print('Verified:', name, flush=True)
            continue
        temporary = path.with_suffix('.download')
        try:
            request = urllib.request.Request(entry['url'], headers={'User-Agent': 'Riderpack-build/1.1.4'})
            with urllib.request.urlopen(request, timeout=120) as response, temporary.open('wb') as output:
                shutil.copyfileobj(response, output)
            if hashlib.sha256(temporary.read_bytes()).hexdigest() != entry['sha256']:
                raise SystemExit(f'Dependency checksum mismatch: {name}')
            temporary.replace(path)
            print('Downloaded and verified:', name, flush=True)
        finally:
            temporary.unlink(missing_ok=True)


if __name__ == '__main__':
    fetch()
