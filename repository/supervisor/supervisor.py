#!/usr/bin/env python3
"""RBD offline world supervisor. Python 3.11+, local POSIX or Windows filesystem.

See docs/OPERATIONS.md for backup, recovery and filesystem guarantees.
The child MUST be the actual Java server process, not a forking launch script.
No EULA acceptance, downloads, network calls, shell execution or automatic pruning.
"""
from __future__ import annotations
import argparse
import contextlib
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import stat
import subprocess
import sys
import uuid
from typing import Any, Iterator
try:
    import fcntl
except ImportError:
    fcntl = None

SCHEMA = 1
class SafetyError(RuntimeError):
    pass

def sync_dir(path: Path) -> None:
    # Windows file contents are flushed before atomic rename. Python exposes no directory fsync there.
    if os.name == "nt": return
    fd = os.open(path, os.O_RDONLY | getattr(os, 'O_DIRECTORY', 0))
    try: os.fsync(fd)
    finally: os.close(fd)

def atomic_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_name(path.name + '.' + uuid.uuid4().hex + '.tmp')
    data = (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + '\n').encode('utf-8')
    try:
        fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'wb') as f:
            f.write(data); f.flush(); os.fsync(f.fileno())
        os.replace(temp, path); sync_dir(path.parent)
    finally:
        if temp.exists(): temp.unlink()

def is_link(path: Path) -> bool:
    try:
        return path.is_symlink() or bool(getattr(path.lstat(), 'st_file_attributes', 0) & 0x400)
    except FileNotFoundError:
        return False

def read_json(path: Path) -> dict[str, Any]:
    if is_link(path) or not path.is_file():
        raise SafetyError(f'Expected regular JSON file: {path}')
    if path.stat().st_size > 8 * 1024 * 1024:
        raise SafetyError(f'Control JSON too large: {path}')
    with path.open(encoding='utf-8') as f: value = json.load(f)
    if not isinstance(value, dict): raise SafetyError(f'Expected JSON object: {path}')
    return value

def valid_id(value: Any) -> str:
    try:
        if not isinstance(value, str) or str(uuid.UUID(value)) != value:
            raise ValueError()
        return value
    except (ValueError, AttributeError):
        raise SafetyError('Expected canonical UUID') from None

def file_hash(path: Path) -> str:
    h = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''): h.update(chunk)
    return h.hexdigest()

def inventory(root: Path) -> dict[str, Any]:
    """Byte-level saved-files inventory; NOT a semantic NBT/RNG correctness proof."""
    if is_link(root) or not root.is_dir(): raise SafetyError(f'Invalid world tree: {root}')
    files: dict[str, Any] = {}; directories: list[str] = []
    for base, dirs, names in os.walk(root, followlinks=False):
        for name in sorted(dirs):
            p = Path(base) / name
            if is_link(p): raise SafetyError(f'Symlink rejected: {p}')
            directories.append(p.relative_to(root).as_posix())
        for name in sorted(names):
            p = Path(base) / name
            if p.relative_to(root).as_posix() == 'session.lock': continue
            st = p.lstat()
            if not stat.S_ISREG(st.st_mode) or st.st_nlink != 1:
                raise SafetyError(f'Non-regular file or hardlink rejected: {p}')
            files[p.relative_to(root).as_posix()] = {'size': st.st_size, 'sha256': file_hash(p)}
    return {'files': files, 'directories': sorted(directories)}

def copy_tree(source: Path, target: Path) -> dict[str, Any]:
    if target.exists(): raise SafetyError(f'Never overwrite an existing staged tree: {target}')
    before = inventory(source)
    def ignore(base: str, names: list[str]) -> list[str]:
        return ['session.lock'] if Path(base) == source and 'session.lock' in names else []
    shutil.copytree(source, target, ignore=ignore, copy_function=shutil.copy2)
    for base, _, names in os.walk(target, topdown=False):
        for name in names:
            with (Path(base) / name).open('r+b') as f: os.fsync(f.fileno())
        sync_dir(Path(base))
    if inventory(target) != before or inventory(source) != before:
        raise SafetyError('World changed while copying or copy verification failed')
    return before

def open_lock(path: Path) -> int:
    if os.name != 'nt':
        return os.open(path, os.O_RDWR | os.O_CREAT, 0o600)
    # Share-delete is essential: the protected world directory is renamed while this handle is held.
    import ctypes
    import msvcrt
    from ctypes import wintypes
    create = ctypes.WinDLL('kernel32', use_last_error=True).CreateFileW
    create.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD, ctypes.c_void_p,
                       wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]
    create.restype = wintypes.HANDLE
    handle = create(str(path), 0xC0000000, 7, None, 4, 0x80, None)
    if handle == wintypes.HANDLE(-1).value:
        raise ctypes.WinError(ctypes.get_last_error())
    return msvcrt.open_osfhandle(handle, os.O_RDWR | os.O_BINARY)

def lock_fd(fd: int, *, supervisor: bool = False) -> None:
    if os.name == 'nt':
        import msvcrt
        os.lseek(fd, 0, os.SEEK_SET)
        try: msvcrt.locking(fd, msvcrt.LK_NBLCK, 1)
        except OSError as exc: raise SafetyError('World or supervisor is locked by another process') from exc
    else:
        if fcntl is None: raise SafetyError('Platform has no supported file-lock implementation')
        try:
            if supervisor: fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            else: fcntl.lockf(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as exc: raise SafetyError('World or supervisor is locked by another process') from exc

@contextlib.contextmanager
def lock_world(world: Path) -> Iterator[None]:
    """POSIX record lock, interoperable with Java FileChannel.tryLock (not flock)."""
    world.mkdir(parents=True, exist_ok=True)
    path = world / 'session.lock'
    if is_link(path): raise SafetyError('session.lock symlink rejected')
    fd = open_lock(path)
    try:
        lock_fd(fd)
        yield
    finally:
        os.close(fd)

@contextlib.contextmanager
def rename_boundary(*worlds: Path) -> Iterator[None]:
    # Windows cannot rename a directory containing a locked child file even with share-delete.
    # Verify all writers have exited, then release handles before rename; the supervisor lock
    # remains held. Deployment must route every JVM through this one supervisor.
    if os.name == 'nt':
        with contextlib.ExitStack() as stack:
            for world in worlds: stack.enter_context(lock_world(world))
        yield
    else:
        with contextlib.ExitStack() as stack:
            for world in worlds: stack.enter_context(lock_world(world))
            yield

class Supervisor:
    def __init__(self, world: Path, control: Path):
        if fcntl is None and os.name != 'nt': raise SafetyError('No supported file-lock implementation')
        if is_link(world) or is_link(control): raise SafetyError('Root symlinks are forbidden')
        self.world = world.resolve(); self.control = control.resolve()
        if self.world == self.control or self.world in self.control.parents or self.control in self.world.parents:
            raise SafetyError('World and control paths must be disjoint')
        self.control.mkdir(parents=True, exist_ok=True, mode=0o700)
        for name in ('snapshots', 'books', 'receipts', 'requests', 'staging', 'failed'):
            (self.control / name).mkdir(mode=0o700, exist_ok=True)
        self._lock_fd: int | None = None

    @contextlib.contextmanager
    def exclusive(self) -> Iterator[None]:
        path = self.control / 'supervisor.lock'
        if is_link(path): raise SafetyError('Supervisor lock symlink')
        fd = open_lock(path)
        try:
            lock_fd(fd, supervisor=True)
            self._lock_fd = fd
            yield
        finally:
            self._lock_fd = None; os.close(fd)

    def session(self) -> dict[str, Any]:
        data = read_json(self.control / 'session.json')
        if data.get('world') != str(self.world) or len(data.get('token', '')) < 32:
            raise SafetyError('Session does not belong to this world')
        return data

    def fresh_session(self) -> dict[str, Any]:
        if (self.control / 'request.json').exists() or (self.control / 'transaction.json').exists():
            raise SafetyError('Recover pending work before creating a new session')
        value = {'schema': SCHEMA, 'token': secrets.token_hex(32), 'world': str(self.world)}
        atomic_json(self.control / 'session.json', value)
        return value

    def active(self) -> dict[str, Any] | None:
        p = self.control / 'active_checkpoint.json'
        return read_json(p) if p.exists() else None

    def validate_request(self, request: dict[str, Any], exit_code: int) -> None:
        session = self.session(); rid = valid_id(request.get('request_id'))
        if request.get('schema') != SCHEMA or request.get('world') != str(self.world):
            raise SafetyError('Request schema/world mismatch')
        if not secrets.compare_digest(str(request.get('token', '')), session['token']):
            raise SafetyError('Request token mismatch')
        if request.get('operation') not in ('CAPTURE', 'RESTORE'): raise SafetyError('Unknown operation')
        if exit_code != 0: raise SafetyError('JVM exit was not successful')
        clean = read_json(self.control / 'clean_stop.json')
        if clean.get('request_id') != rid or clean.get('token') != session['token']:
            raise SafetyError('No matching normal-stop marker')
        if request['operation'] == 'RESTORE':
            active = self.active()
            if not active or request.get('checkpoint_id') != active.get('checkpoint_id'):
                raise SafetyError('Only the current checkpoint may be restored')
            if not isinstance(request.get('death'), dict): raise SafetyError('RESTORE needs a sealed death')

    def verify_snapshot(self, cid: str) -> tuple[Path, dict[str, Any]]:
        snap = self.control / 'snapshots' / valid_id(cid)
        manifest = read_json(snap / 'manifest.json')
        if manifest.get('checkpoint_id') != cid or manifest.get('schema') != SCHEMA:
            raise SafetyError('Snapshot manifest mismatch')
        if inventory(snap / 'tree') != manifest.get('inventory'):
            raise SafetyError('Checkpoint integrity verification failed; no fallback to older checkpoints')
        return snap / 'tree', manifest

    def budget(self, at: Path, size: int) -> None:
        # Retains failed branches. Reject rather than auto-delete any history.
        if shutil.disk_usage(at).free < size + 16 * 1024 * 1024:
            raise SafetyError('Insufficient space for a complete staging copy and reserve')

    def capture(self, request: dict[str, Any]) -> dict[str, Any]:
        rid = valid_id(request['request_id'])
        active = self.active(); target = self.control / 'snapshots' / rid
        if target.exists():
            _, manifest = self.verify_snapshot(rid)
            # A crash after finalizing the snapshot but before publishing the pointer is recoverable.
            if manifest.get('request_id') != rid: raise SafetyError('Snapshot collision')
        else:
            stage = target.with_name(rid + '.partial')
            if stage.exists():
                # Never silently delete an interrupted copy. Move it aside for inspection.
                stage.rename(stage.with_name(rid + '.incomplete-' + uuid.uuid4().hex))
            stage.mkdir()
            with lock_world(self.world):
                before = inventory(self.world)
                self.budget(stage, sum(x['size'] for x in before['files'].values()))
                inv = copy_tree(self.world, stage / 'tree')
            ordinal = 1 if active is None else int(active['ordinal']) + 1
            manifest = {'schema': SCHEMA, 'checkpoint_id': rid, 'request_id': rid,
                        'ordinal': ordinal, 'world': str(self.world), 'inventory': inv}
            atomic_json(stage / 'manifest.json', manifest)
            sync_dir(stage); stage.rename(target); sync_dir(target.parent)
        pointer = {'schema': SCHEMA, 'checkpoint_id': rid, 'ordinal': manifest['ordinal'],
                   'branch_id': rid}
        # A receipt-idempotent replay cannot move the pointer back to an older checkpoint.
        if active and int(active['ordinal']) > int(pointer['ordinal']):
            raise SafetyError('Refusing checkpoint regression')
        atomic_json(self.control / 'active_checkpoint.json', pointer)
        pending_authority = self.control / 'pending_authority.json'
        if pending_authority.exists():
            authority = read_json(pending_authority)
            valid_id(authority.get('holder'))
            atomic_json(self.control / 'authority.json', authority)
            pending_authority.unlink(); sync_dir(self.control)
        return {'request_id': rid, 'operation': 'CAPTURE', 'checkpoint_id': rid,
                'ordinal': pointer['ordinal']}

    def restore(self, request: dict[str, Any]) -> dict[str, Any]:
        rid = valid_id(request['request_id']); cid = valid_id(request['checkpoint_id'])
        txn_path = self.control / 'transaction.json'
        new = self.control / 'staging' / ('.' + self.world.name + '.rbd-new-' + rid)
        old = self.control / 'failed' / ('.' + self.world.name + '.rbd-rejected-' + rid)
        tree, manifest = self.verify_snapshot(cid)
        expected = manifest['inventory']
        if not txn_path.exists():
            if old.exists(): raise SafetyError('Backup path collision')
            if new.exists(): raise SafetyError('Unjournaled staging tree; inspect before recovery')
            self.budget(self.world.parent, sum(x['size'] for x in expected['files'].values()))
            with lock_world(self.world):
                copy_tree(tree, new)
                txn = {'schema': SCHEMA, 'request_id': rid, 'checkpoint_id': cid, 'phase': 'PREPARED'}
                atomic_json(txn_path, txn)
        txn = read_json(txn_path)
        if txn.get('request_id') != rid or txn.get('checkpoint_id') != cid:
            raise SafetyError('Transaction identity mismatch')
        # Infer only unambiguous, generated-path states; never guess from an arbitrary world.
        if self.world.exists() and new.exists() and not old.exists():
            if inventory(new) != expected: raise SafetyError('Staged copy corrupt')
            with rename_boundary(self.world, new):
                self.world.rename(old); sync_dir(self.world.parent)
                txn['phase'] = 'OLD_MOVED'; atomic_json(txn_path, txn)
                new.rename(self.world); sync_dir(self.world.parent)
                txn['phase'] = 'SWAPPED'; atomic_json(txn_path, txn)
        elif not self.world.exists() and new.exists() and old.exists():
            if inventory(new) != expected: raise SafetyError('Staged recovery copy corrupt')
            with rename_boundary(old, new):
                new.rename(self.world); sync_dir(self.world.parent)
                txn['phase'] = 'SWAPPED'; atomic_json(txn_path, txn)
        elif self.world.exists() and old.exists() and not new.exists():
            with lock_world(self.world), lock_world(old):
                if inventory(self.world) != expected: raise SafetyError('Swapped world no longer matches checkpoint')
        else:
            raise SafetyError('Ambiguous restore state; retain all files and stop')
        if inventory(self.world) != expected: raise SafetyError('Post-swap verification failed')
        book = {'schema': SCHEMA, 'book_id': rid, 'life_id': rid,
                'checkpoint_id': cid, 'branch_id': rid, 'authority_life': True,
                'death': request['death'], 'coverage': request['death'].get('coverage', 'DEATH_EVENT_ONLY_NOT_LIFETIME_REPLAY')}
        atomic_json(self.control / 'books' / (rid + '.json'), book)
        atomic_json(self.control / 'last_return.json', request['death'])
        # Book id = request id. Repeating recovery cannot create a duplicate failed life.
        return {'request_id': rid, 'operation': 'RESTORE', 'checkpoint_id': cid,
                'book_id': rid, 'retained_failed_world': str(old)}

    def finish(self, request: dict[str, Any], receipt: dict[str, Any]) -> None:
        rid = valid_id(request['request_id'])
        atomic_json(self.control / 'receipts' / (rid + '.json'), receipt)
        redacted = dict(request); redacted.pop('token', None)
        atomic_json(self.control / 'requests' / (rid + '.json'), redacted)
        # request.json remains until all transaction and stop metadata is cleaned.
        for name in ('transaction.json', 'clean_stop.json', 'child_exit.json'):
            (self.control / name).unlink(missing_ok=True)
        sync_dir(self.control)
        (self.control / 'request.json').unlink(missing_ok=True)
        sync_dir(self.control)

    def process_pending(self, exit_code: int | None = None) -> dict[str, Any] | None:
        path = self.control / 'request.json'
        if not path.exists():
            if (self.control / 'transaction.json').exists(): raise SafetyError('Orphaned transaction')
            return None
        request = read_json(path); rid = valid_id(request.get('request_id'))
        receipt_path = self.control / 'receipts' / (rid + '.json')
        if receipt_path.exists():
            # Completion interrupted during cleanup. No second restore or book.
            receipt = read_json(receipt_path)
            if receipt.get('request_id') != rid: raise SafetyError('Receipt mismatch')
            self.finish(request, receipt); return receipt
        if exit_code is None:
            status = read_json(self.control / 'child_exit.json')
            if status.get('request_id') != rid or status.get('token') != self.session()['token']:
                raise SafetyError('No durable JVM exit evidence; inspect offline rather than guess')
            exit_code = status['returncode']
        self.validate_request(request, exit_code)
        receipt = self.capture(request) if request['operation'] == 'CAPTURE' else self.restore(request)
        self.finish(request, receipt)
        return receipt

    def run(self, command: list[str], cwd: Path) -> int:
        if not command: raise SafetyError('Missing direct Java server command after --')
        with self.exclusive():
            if (self.control / 'fault.json').exists():
                raise SafetyError('fault.json is present. Resolve the fault offline before restarting')
            self.process_pending()
            while True:
                session = self.fresh_session()
                env = dict(os.environ, RBD_CONTROL_DIR=str(self.control), RBD_SUPERVISOR_TOKEN=session['token'])
                # Local, direct child. Parent environment is not sent over the network.
                child = subprocess.Popen(command, cwd=cwd, env=env)
                try: code = child.wait()
                except KeyboardInterrupt:
                    # No automatic restore without durable successful-exit evidence.
                    child.wait(); raise
                req = self.control / 'request.json'
                if (self.control / 'fault.json').exists():
                    raise SafetyError('Child reported a fault; keep world and control files for offline inspection')
                if not req.exists(): return code  # normal stop: do not restart unconditionally
                request = read_json(req)
                atomic_json(self.control / 'child_exit.json', {'request_id': request['request_id'],
                            'token': session['token'], 'returncode': code})
                self.process_pending(code)
                # Restart only after a completed CAPTURE or RESTORE transaction.

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--world', required=True, type=Path)
    parser.add_argument('--control', required=True, type=Path)
    parser.add_argument('--cwd', type=Path, default=Path.cwd())
    parser.add_argument('action', choices=['run', 'recover'])
    parser.add_argument('command', nargs=argparse.REMAINDER)
    args = parser.parse_args(); s: Supervisor | None = None
    try:
        s = Supervisor(args.world, args.control)
        if args.action == 'recover':
            with s.exclusive():
                if (s.control / 'fault.json').exists():
                    raise SafetyError('fault.json requires manual offline inspection; recover will not bypass it')
                result = s.process_pending()
                print(json.dumps(result or {'status': 'no_pending_transaction'}, ensure_ascii=False, indent=2))
            return 0
        command = args.command[1:] if args.command[:1] == ['--'] else args.command
        return s.run(command, args.cwd.resolve())
    except (OSError, ValueError, KeyError, SafetyError) as exc:
        print(f'RBD FAIL-CLOSED: {exc}', file=sys.stderr)
        # Do not auto-mark ordinary CLI/config errors as a persistent world fault.
        # The pending request / transaction remains the authoritative recovery evidence.
        return 2

if __name__ == '__main__':
    raise SystemExit(main())
