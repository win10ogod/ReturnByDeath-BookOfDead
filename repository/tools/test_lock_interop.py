#!/usr/bin/env python3
"""Verify Minecraft-style Java FileChannel locks against the platform supervisor in both directions."""
import argparse
from pathlib import Path
import subprocess
import sys
import tempfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'supervisor'))
from supervisor import lock_world, SafetyError
p = argparse.ArgumentParser()
p.add_argument('--java', default='java')
p.add_argument('--classes', required=True)
a = p.parse_args()
with tempfile.TemporaryDirectory(prefix='rbd-java-python-lock-') as directory:
    world = Path(directory)
    command = [a.java, '-cp', str(Path(a.classes).resolve()), 'LockProbe', str(world / 'session.lock')]
    child = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
    try:
        assert child.stdout.readline().strip() == 'LOCKED'
        try:
            with lock_world(world):
                raise AssertionError('Python acquired a Java-owned world lock')
        except SafetyError:
            pass
    finally:
        child.communicate('\n', timeout=10)
    with lock_world(world):
        result = subprocess.run(command + ['probe'], text=True, capture_output=True, check=True)
        assert result.stdout.strip() == 'BUSY', result
print('Java/Python world lock interoperability: both directions passed')
