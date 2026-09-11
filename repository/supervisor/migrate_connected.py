#!/usr/bin/env python3
"""Copy a stopped legacy supervisor archive into the connected backend's default location.

The original world and archive are retained. Run once before starting the new server.
"""
from __future__ import annotations
import argparse
import os
from pathlib import Path
import uuid
from supervisor import (SafetyError, atomic_json, copy_tree, inventory, is_link,
                        lock_fd, lock_world, open_lock, read_json, sync_dir, valid_id)

# Windows forbids reading the held byte-range lock through a second file handle.
# These two coordination files contain no world, memory or authority data.
ARCHIVE_LOCKS = frozenset({'session.lock', 'supervisor.lock'})


def migrate(world: Path, source: Path) -> Path:
    if is_link(world) or is_link(source) or not world.is_dir() or not source.is_dir():
        raise SafetyError('World and archive must be existing ordinary directories')
    world, source = world.resolve(), source.resolve()
    target = world.parent / '.rbd' / world.name
    if (world == source or world in source.parents or source in world.parents
            or source == target or source in target.parents or target in source.parents):
        raise SafetyError('World, legacy archive and new archive must be disjoint')
    if target.exists() or is_link(target) or is_link(target.parent):
        raise SafetyError('Destination already exists or is linked; never overwrite an archive')
    if is_link(source / 'supervisor.lock'):
        raise SafetyError('Legacy supervisor lock is linked')
    fd = open_lock(source / 'supervisor.lock')
    try:
        lock_fd(fd, supervisor=True)
        with lock_world(world):
            for name in ('request.json', 'transaction.json', 'fault.json', 'active.json'):
                if (source / name).exists():
                    raise SafetyError(f'Resolve legacy {name} before migration')
            session = read_json(source / 'session.json')
            if Path(session.get('world', '')).resolve() != world:
                raise SafetyError('Legacy session belongs to a different world')
            active_file = source / 'active_checkpoint.json'
            active = read_json(active_file) if active_file.exists() else None
            manifests = {}
            for snapshot in (source / 'snapshots').iterdir():
                try: cid = valid_id(snapshot.name)
                except SafetyError: continue
                if not (snapshot / 'manifest.json').is_file():
                    continue  # Preserve interrupted copies, without treating them as checkpoints.
                manifest = read_json(snapshot / 'manifest.json')
                if manifest.get('schema') != 1 or manifest.get('checkpoint_id') != cid:
                    raise SafetyError(f'Invalid legacy checkpoint: {cid}')
                if inventory(snapshot / 'tree') != manifest.get('inventory'):
                    raise SafetyError(f'Legacy checkpoint checksum mismatch: {cid}')
                manifests[cid] = manifest
            if active:
                cid = valid_id(active.get('checkpoint_id'))
                if cid not in manifests or active.get('ordinal') != manifests[cid].get('ordinal'):
                    raise SafetyError('Active checkpoint is missing or mismatched')
            before = inventory(source, excluded_root_files=ARCHIVE_LOCKS)
            size = sum(f['size'] for f in before['files'].values())
            import shutil
            if shutil.disk_usage(world.parent).free < size + 16 * 1024 * 1024:
                raise SafetyError('Insufficient space for a complete archive copy')
            target.parent.mkdir(exist_ok=True)
            stage = target.with_name(target.name + '.migrating-' + uuid.uuid4().hex)
            copy_tree(source, stage, excluded_root_files=ARCHIVE_LOCKS)
            for cid, manifest in manifests.items():
                snapshot = stage / 'snapshots' / cid
                (snapshot / 'manifest.json').rename(snapshot / 'manifest-supervisor.json')
                atomic_json(snapshot / 'manifest.json', manifest['inventory'])
            if active:
                atomic_json(stage / 'active.json', {'id': active['checkpoint_id'], 'ordinal': active['ordinal']})
            atomic_json(stage / 'migration.json', {'format': 'connected-1', 'source': str(source),
                        'world': str(world), 'checkpoints': len(manifests)})
            if inventory(source, excluded_root_files=ARCHIVE_LOCKS) != before:
                raise SafetyError(f'Legacy archive changed; staged copy retained at {stage}')
            if target.exists():
                raise SafetyError(f'Destination appeared; staged copy retained at {stage}')
            stage.rename(target)
            sync_dir(target.parent)
            return target
    finally:
        os.close(fd)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--world', type=Path, required=True)
    parser.add_argument('--control', type=Path, required=True, help='Existing legacy supervisor control directory')
    args = parser.parse_args()
    try:
        print('Connected archive ready:', migrate(args.world, args.control))
    except (SafetyError, OSError, ValueError) as exc:
        parser.exit(1, str(exc) + '\n')
