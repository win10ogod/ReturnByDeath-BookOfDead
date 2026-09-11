import os
import subprocess
import sys
import unittest
from pathlib import Path
from migrate_connected import migrate
from supervisor import SafetyError, atomic_json, inventory, read_json
from test_supervisor import SupervisorTests


class MigrationTests(unittest.TestCase):
    setUp = SupervisorTests.setUp
    tearDown = SupervisorTests.tearDown
    request = SupervisorTests.request
    capture = SupervisorTests.capture
    mutate = SupervisorTests.mutate
    def test_migration_preserves_all_history(self):
        first = self.capture(); self.mutate(); second = self.capture()
        (self.s.control / 'memories').mkdir()
        (self.s.control / 'memories' / 'recording.bin').write_bytes(b'full memory, no truncation')
        atomic_json(self.s.control / 'authority.json', {'holder': 'unchanged', 'knowledge': {'name': 'Witness'}})
        with self.s.exclusive(): pass
        before, world_before = inventory(self.s.control), inventory(self.world)
        target = migrate(self.world, self.s.control)
        self.assertEqual(inventory(self.s.control), before)
        self.assertEqual(inventory(self.world), world_before)
        self.assertEqual(read_json(target / 'active.json'), {'id': second['request_id'], 'ordinal': 2})
        self.assertEqual((target / 'memories' / 'recording.bin').read_bytes(), b'full memory, no truncation')
        self.assertEqual(read_json(target / 'authority.json')['knowledge'], {'name': 'Witness'})
        for request in (first, second):
            snap = target / 'snapshots' / request['request_id']
            self.assertEqual(read_json(snap / 'manifest.json'), inventory(snap / 'tree'))
            self.assertEqual(read_json(snap / 'manifest-supervisor.json'),
                             read_json(self.s.control / 'snapshots' / request['request_id'] / 'manifest.json'))

    def test_migration_rejects_corruption_without_publishing(self):
        cp = self.capture()
        (self.s.control / 'snapshots' / cp['request_id'] / 'tree' / 'level.dat').write_bytes(b'corrupt')
        with self.assertRaises(SafetyError): migrate(self.world, self.s.control)
        self.assertFalse((self.world.parent / '.rbd' / self.world.name).exists())

    def test_migration_rejects_pending_and_existing_target(self):
        self.capture(); self.request('RESTORE')
        with self.assertRaises(SafetyError): migrate(self.world, self.s.control)
        self.s.process_pending()
        target = migrate(self.world, self.s.control); before = inventory(target)
        with self.assertRaises(SafetyError): migrate(self.world, self.s.control)
        self.assertEqual(inventory(target), before)

    def test_migration_refuses_running_supervisor(self):
        self.capture()
        with self.s.exclusive():
            run = subprocess.run([sys.executable, str(Path(__file__).with_name('migrate_connected.py')),
                                  '--world', str(self.world), '--control', str(self.s.control)], capture_output=True)
        self.assertNotEqual(run.returncode, 0)
        self.assertIn(b'locked', run.stderr)

    def test_java_can_restore_migrated_checkpoint(self):
        classpath = os.environ.get('RBD_MIGRATION_CLASSPATH')
        if not classpath: self.skipTest('Set RBD_MIGRATION_CLASSPATH for Java storage interoperability')
        self.capture(); before = inventory(self.world); self.mutate()
        target = migrate(self.world, self.s.control)
        run = subprocess.run(['java', '-cp', classpath, 'StorageSelfTest', '--restore-migrated',
                              str(self.world), str(target)], text=True, capture_output=True)
        self.assertEqual(run.returncode, 0, run.stdout + run.stderr)
        self.assertEqual(inventory(self.world), before)


del SupervisorTests
