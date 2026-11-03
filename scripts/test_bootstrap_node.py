import hashlib
import importlib.util
import tempfile
import stat
import unittest
import zipfile
from pathlib import Path


class BootstrapNodeTests(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location('bootstrap_node', Path(__file__).with_name('bootstrap_node.py'))
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)

    def archive(self, root, name='node-v24.22.1-win-x64/node.exe'):
        archive = root / 'node.zip'
        with zipfile.ZipFile(archive, 'w') as package:
            package.writestr(name, b'local-fixture')
        return archive, hashlib.sha256(archive.read_bytes()).hexdigest()

    def test_verified_archive_installs_only_beneath_destination(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            archive, digest = self.archive(root)
            self.module.extract_verified(archive, digest, root / 'output')
            self.assertEqual((root / 'output/node.exe').read_bytes(), b'local-fixture')

    def test_checksum_mismatch_does_not_create_destination(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            archive, _ = self.archive(root)
            with self.assertRaisesRegex(ValueError, 'checksum'):
                self.module.extract_verified(archive, '0' * 64, root / 'output')
            self.assertFalse((root / 'output').exists())

    def test_other_pinned_runtime_root_can_be_verified_without_changing_node_default(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            archive, digest = self.archive(root, 'jdk17/bin/java.exe')
            self.module.extract_verified(archive, digest, root / 'jdk', prefix='jdk17')
            self.assertEqual((root / 'jdk/bin/java.exe').read_bytes(), b'local-fixture')
            with self.assertRaises(ValueError):
                self.module.extract_verified(archive, digest, root / 'node')
            self.assertFalse((root / 'node').exists())

    def test_traversal_absolute_windows_paths_and_links_rejected_before_extraction(self):
        for name in ['node-v24.22.1-win-x64/../../escape', '/absolute', 'C:/escape', 'node-v24.22.1-win-x64/..\\escape', 'other/node.exe']:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                archive, digest = self.archive(root, name)
                with self.assertRaises(ValueError):
                    self.module.extract_verified(archive, digest, root / 'output')
                self.assertFalse((root / 'output').exists())

    def test_symbolic_link_is_rejected_before_any_file_is_written(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            archive = root / 'link.zip'
            with zipfile.ZipFile(archive, 'w') as package:
                package.writestr('node-v24.22.1-win-x64/node.exe', b'valid first entry')
                link = zipfile.ZipInfo('node-v24.22.1-win-x64/link')
                link.create_system = 3
                link.external_attr = (stat.S_IFLNK | 0o777) << 16
                package.writestr(link, '../../outside')
            digest = hashlib.sha256(archive.read_bytes()).hexdigest()
            with self.assertRaises(ValueError):
                self.module.extract_verified(archive, digest, root / 'output')
            self.assertFalse((root / 'output').exists())


if __name__ == '__main__':
    unittest.main()
