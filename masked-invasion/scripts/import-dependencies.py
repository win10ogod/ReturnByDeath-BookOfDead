#!/usr/bin/env python3
"""Download the pinned, public dependencies and verify their SHA-256 before installation."""
from pathlib import Path
import argparse, hashlib, json, urllib.request
root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--tests',action='store_true');args=parser.parse_args()
lock=json.loads((root/'dependencies.lock.json').read_text())
for entry in lock['files']+(lock.get('test_files',[]) if args.tests else []):
    destination=root/entry['path']
    if destination.exists() and hashlib.sha256(destination.read_bytes()).hexdigest()==entry['sha256']:
        print('Verified',entry['path']);continue
    request=urllib.request.Request(entry['download_url'],headers={'User-Agent':'MaskedInvasion-dependency-importer/1.0'})
    with urllib.request.urlopen(request,timeout=120) as response:data=response.read()
    if hashlib.sha256(data).hexdigest()!=entry['sha256']:raise SystemExit('SHA-256 mismatch: '+entry['path'])
    destination.parent.mkdir(parents=True,exist_ok=True)
    temporary=destination.with_suffix('.download');temporary.write_bytes(data);temporary.replace(destination)
    print('Imported',entry['path'])
