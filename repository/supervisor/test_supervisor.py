from __future__ import annotations
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import uuid
from unittest import mock
from supervisor import Supervisor, SafetyError, atomic_json, inventory, read_json, lock_world

class SupervisorTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.world = self.root / 'world'; self.world.mkdir()
        for name, content in {'level.dat': b'level-A', 'region/r.0.0.mca': b'chunks-A',
                              'entities/r.0.0.mca': b'entity-A', 'poi/r.0.0.mca': b'poi-A',
                              'playerdata/a.dat': b'inventory-A', 'data/scoreboard.dat': b'score-A',
                              'dimensions/custom/moon/region/r.1.0.mca': b'moon-A',
                              'advancements/a.json': b'{}', 'stats/a.json': b'{}'}.items():
            p = self.world / name; p.parent.mkdir(parents=True, exist_ok=True); p.write_bytes(content)
        (self.world / 'empty-dir').mkdir()
        self.s = Supervisor(self.world, self.root / 'state')
        self.s.fresh_session()

    def tearDown(self): self.temp.cleanup()

    def request(self, operation='CAPTURE'):
        rid = str(uuid.uuid4()); session = self.s.session(); active = self.s.active()
        r = {'schema': 1, 'request_id': rid, 'token': session['token'], 'world': str(self.world),
             'operation': operation, 'checkpoint_id': active['checkpoint_id'] if active else ''}
        if operation == 'RESTORE': r['death'] = {'soul_id': 'test-holder', 'cause': 'fall'}
        atomic_json(self.s.control / 'request.json', r)
        atomic_json(self.s.control / 'clean_stop.json', {'request_id': rid, 'token': session['token']})
        atomic_json(self.s.control / 'child_exit.json', {'request_id': rid, 'token': session['token'], 'returncode': 0})
        return r

    def capture(self):
        r = self.request(); self.s.process_pending(); return r

    def mutate(self):
        (self.world / 'level.dat').write_bytes(b'level-B')
        (self.world / 'region' / 'r.4.0.mca').write_bytes(b'new-chunk')
        (self.world / 'data' / 'scoreboard.dat').unlink()

    def test_capture_restore_all_files(self):
        before = inventory(self.world); cp = self.capture(); self.mutate()
        r = self.request('RESTORE'); result = self.s.process_pending()
        self.assertEqual(inventory(self.world), before)
        self.assertEqual(result['checkpoint_id'], cp['request_id'])
        self.assertTrue(Path(result['retained_failed_world']).is_dir())
        self.assertTrue((self.s.control / 'books' / (r['request_id'] + '.json')).exists())
        self.assertEqual(self.s.active()['ordinal'], 1)

    def test_idempotent_receipt_cleanup(self):
        self.capture(); r = self.request('RESTORE'); self.s.process_pending()
        atomic_json(self.s.control / 'request.json', r)
        self.s.process_pending()
        self.assertEqual(len(list((self.s.control / 'books').glob('*.json'))), 1)
        self.assertFalse((self.s.control / 'request.json').exists())

    def test_checkpoint_advances_and_old_not_selected(self):
        first = self.capture(); self.mutate(); second = self.capture()
        self.assertEqual(self.s.active()['ordinal'], 2)
        r = self.request('RESTORE'); r['checkpoint_id'] = first['request_id']
        atomic_json(self.s.control / 'request.json', r)
        with self.assertRaises(SafetyError): self.s.process_pending()
        self.assertEqual(self.s.active()['checkpoint_id'], second['request_id'])

    def test_snapshot_corruption_rejected(self):
        cp = self.capture(); self.mutate(); before = inventory(self.world)
        (self.s.control / 'snapshots' / cp['request_id'] / 'tree' / 'level.dat').write_bytes(b'tampered')
        self.request('RESTORE')
        with self.assertRaises(SafetyError): self.s.process_pending()
        self.assertEqual(inventory(self.world), before)

    def test_extra_snapshot_file_rejected(self):
        cp = self.capture()
        (self.s.control / 'snapshots' / cp['request_id'] / 'tree' / 'extra').write_bytes(b'x')
        with self.assertRaises(SafetyError): self.s.verify_snapshot(cp['request_id'])

    def test_symlink_rejected(self):
        if os.name == 'nt':
            subprocess.run(['cmd', '/c', 'mklink', '/J', str(self.world / 'escape'), str(self.root / 'state')], check=True, capture_output=True)
        else:
            (self.world / 'escape').symlink_to(self.root / 'state', target_is_directory=True)
        self.request()
        with self.assertRaises(SafetyError): self.s.process_pending()

    def test_hardlink_rejected(self):
        os.link(self.world / 'level.dat', self.root / 'external')
        with self.assertRaises(SafetyError): inventory(self.world)

    def test_bad_token(self):
        r = self.request(); r['token'] = 'x' * 64
        atomic_json(self.s.control / 'request.json', r)
        with self.assertRaises(SafetyError): self.s.process_pending()

    def test_bad_exit(self):
        self.request()
        with self.assertRaises(SafetyError): self.s.process_pending(exit_code=1)

    def test_missing_normal_stop(self):
        self.request(); (self.s.control / 'clean_stop.json').unlink()
        with self.assertRaises(SafetyError): self.s.process_pending()

    def test_missing_exit_evidence(self):
        self.request(); (self.s.control / 'child_exit.json').unlink()
        with self.assertRaises(SafetyError): self.s.process_pending()

    def test_bad_request_uuid(self):
        r = self.request(); r['request_id'] = '../escape'
        atomic_json(self.s.control / 'request.json', r)
        with self.assertRaises(SafetyError): self.s.process_pending()

    def test_live_world_record_lock(self):
        script = "from supervisor import open_lock,lock_fd; from pathlib import Path; import sys,time; fd=open_lock(Path(sys.argv[1])); lock_fd(fd); print('locked',flush=True); time.sleep(15)"
        child = subprocess.Popen([sys.executable, '-c', script, str(self.world / 'session.lock')], stdout=subprocess.PIPE, text=True, cwd=Path(__file__).parent)
        try:
            self.assertEqual(child.stdout.readline().strip(), 'locked')
            with self.assertRaises(SafetyError):
                with lock_world(self.world): pass
        finally:
            child.terminate(); child.wait(); child.stdout.close()

    def test_overlap_paths_rejected(self):
        with self.assertRaises(SafetyError): Supervisor(self.world, self.world / 'control')
        with self.assertRaises(SafetyError): Supervisor(self.world, self.root)

    def test_low_disk_space(self):
        self.request()
        with mock.patch('supervisor.shutil.disk_usage', return_value=shutil_usage(1)):
            with self.assertRaises(SafetyError): self.s.process_pending()
        self.assertIsNone(self.s.active())

    def test_recover_after_old_moved(self):
        before = inventory(self.world); self.capture(); self.mutate(); r = self.request('RESTORE')
        original = Path.rename
        def interrupted(path, target):
            if path.name.startswith('.world.rbd-new-'): raise OSError('simulated power loss before swap')
            return original(path, target)
        with mock.patch.object(Path, 'rename', interrupted):
            with self.assertRaises(OSError): self.s.process_pending()
        self.assertFalse(self.world.exists())
        self.s.process_pending()
        self.assertEqual(inventory(self.world), before)
        self.assertTrue((self.s.control / 'books' / (r['request_id'] + '.json')).exists())

    def test_recover_after_world_swapped(self):
        before = inventory(self.world); self.capture(); self.mutate(); self.request('RESTORE')
        import supervisor
        real_write = supervisor.atomic_json
        def interrupted(path, data):
            if path.parent.name == 'books': raise OSError('simulated power loss before book commit')
            real_write(path, data)
        with mock.patch('supervisor.atomic_json', side_effect=interrupted):
            with self.assertRaises(OSError): self.s.process_pending()
        self.s.process_pending()
        self.assertEqual(inventory(self.world), before)
        self.assertEqual(len(list((self.s.control / 'books').glob('*.json'))), 1)

    def test_reading_metadata_does_not_mutate_world(self):
        self.capture(); self.request('RESTORE'); self.s.process_pending()
        before = inventory(self.world)
        book = read_json(next((self.s.control / 'books').glob('*.json')))
        self.assertEqual(book['coverage'], 'DEATH_EVENT_ONLY_NOT_LIFETIME_REPLAY')
        self.assertEqual(inventory(self.world), before)

    def test_no_pending_does_nothing(self):
        before = inventory(self.world)
        self.assertIsNone(self.s.process_pending())
        self.assertEqual(inventory(self.world), before)

    def test_cross_world_request_rejected(self):
        r = self.request(); r['world'] = str(self.root / 'other')
        atomic_json(self.s.control / 'request.json', r)
        with self.assertRaises(SafetyError): self.s.process_pending()

    def test_capture_finalize_crash_recovery(self):
        self.request(); import supervisor
        write = supervisor.atomic_json
        def interrupted(path, data):
            if path.name == 'active_checkpoint.json': raise OSError('pointer write failed')
            write(path, data)
        with mock.patch('supervisor.atomic_json', side_effect=interrupted):
            with self.assertRaises(OSError): self.s.process_pending()
        self.s.process_pending()
        self.assertEqual(self.s.active()['ordinal'], 1)

    def test_restore_requires_death(self):
        self.capture(); r = self.request('RESTORE'); del r['death']
        atomic_json(self.s.control / 'request.json', r)
        with self.assertRaises(SafetyError): self.s.process_pending()

    def test_multiple_deaths_distinct_books(self):
        before = inventory(self.world); self.capture()
        for _ in range(3):
            self.mutate(); self.request('RESTORE'); self.s.process_pending()
        books = list((self.s.control / 'books').glob('*.json'))
        self.assertEqual(len(books), 3)
        self.assertEqual(inventory(self.world), before)
        self.assertEqual(len({read_json(p)['life_id'] for p in books}), 3)

    def test_multi_death_return_has_one_transaction_and_all_lives(self):
        capture = self.request('CAPTURE')
        self.s.process_pending(0)
        death = self.request('RESTORE')
        first, second = str(uuid.uuid4()), str(uuid.uuid4())
        death['death'] = {'id': first, 'soul_id': 'A', 'deaths': [
            {'id': first, 'soul_id': 'A'}, {'id': second, 'soul_id': 'B'}]}
        atomic_json(self.s.control / 'request.json', death)
        self.s.process_pending(0)
        marker = read_json(self.s.control / 'last_return.json')
        self.assertEqual([b['id'] for b in marker['deaths']], [first, second])
        self.assertEqual(marker['id'], first)
        self.assertEqual(len(list((self.s.control / 'books').glob('*.json'))), 1)
        self.assertIsNone(self.s.process_pending(0))
        self.assertEqual(read_json(self.s.control / 'last_return.json'), marker)

    def test_initial_binding_commits_with_checkpoint(self):
        holder = str(uuid.uuid4())
        atomic_json(self.s.control / 'pending_authority.json', {'schema': 1, 'holder': holder, 'name': 'FixturePlayer'})
        self.assertFalse((self.s.control / 'authority.json').exists())
        self.capture()
        self.assertEqual(read_json(self.s.control / 'authority.json')['holder'], holder)
        self.assertFalse((self.s.control / 'pending_authority.json').exists())
        self.assertIsNotNone(self.s.active())

    def test_direct_child_capture_then_normal_stop(self):
        # Fake local child tests supervisor lifecycle only, NOT Minecraft integration.
        script = self.root / 'fake_server.py'
        script.write_text("""import os,json,pathlib,uuid
c=pathlib.Path(os.environ['RBD_CONTROL_DIR']); token=os.environ['RBD_SUPERVISOR_TOKEN']
count=c/'fixture-start-count'
n=int(count.read_text())+1 if count.exists() else 1
count.write_text(str(n))
if n==1:
    session=json.loads((c/'session.json').read_text()); rid=str(uuid.uuid4())
    req={'schema':1,'request_id':rid,'token':token,'world':session['world'],'operation':'CAPTURE','checkpoint_id':''}
    (c/'request.json').write_text(json.dumps(req))
    (c/'clean_stop.json').write_text(json.dumps({'request_id':rid,'token':token}))
""")
        code = self.s.run([sys.executable, str(script)], self.root)
        self.assertEqual(code, 0)
        self.assertEqual((self.s.control / 'fixture-start-count').read_text(), '2')
        self.assertEqual(self.s.active()['ordinal'], 1)
        self.assertFalse((self.s.control / 'request.json').exists())

def shutil_usage(free):
    from collections import namedtuple
    return namedtuple('usage', 'total used free')(100, 99, free)

if __name__ == '__main__': unittest.main(verbosity=2)
