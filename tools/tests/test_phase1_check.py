import unittest
from pathlib import Path
from tools.phase1_check import check_repository


class Phase1CheckTest(unittest.TestCase):
    def test_real_repository_has_phase1_architecture_packet(self):
        base = Path(__file__).resolve().parents[2]
        self.assertEqual([],check_repository(base))

    def test_missing_files_does_not_masquerade_as_success(self):
        from tempfile import TemporaryDirectory
        with TemporaryDirectory() as temp:
            issues=check_repository(Path(temp))
            self.assertTrue(any("Missing Phase 1 deliverable" in item for item in issues))


if __name__ == "__main__":
    unittest.main()
