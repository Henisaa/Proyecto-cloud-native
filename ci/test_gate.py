import tempfile
import unittest
from pathlib import Path
from gate import check_module


class GateTests(unittest.TestCase):
    def fixture(self, root, skipped=0, failures=0, tests=2, covered=8, missed=2):
        (root / 'TEST-example.xml').write_text(
            f'<testsuite tests="{tests}" skipped="{skipped}" errors="0" failures="{failures}">'
            '<testcase classname="A" name="one"/><testcase classname="A" name="two"/></testsuite>')
        (root / 'jacocoTestReport.xml').write_text(
            f'<report><counter type="LINE" covered="{covered}" missed="{missed}"/>'
            '<counter type="BRANCH" covered="5" missed="5"/></report>')

    def test_missing_suite_fails(self):
        with tempfile.TemporaryDirectory() as d, self.assertRaises(ValueError):
            check_module(Path(d), 'example', {'tests': 2, 'LINE': 50, 'BRANCH': 50})

    def test_skipped_or_failed_test_fails(self):
        for options in [{'skipped': 1}, {'failures': 1}]:
            with tempfile.TemporaryDirectory() as d, self.assertRaises(ValueError):
                self.fixture(Path(d), **options)
                check_module(Path(d), 'example', {'tests': 2, 'LINE': 50, 'BRANCH': 50})

    def test_coverage_regression_fails(self):
        with tempfile.TemporaryDirectory() as d, self.assertRaises(ValueError):
            self.fixture(Path(d), covered=1, missed=9)
            check_module(Path(d), 'example', {'tests': 2, 'LINE': 50, 'BRANCH': 50})

    def test_test_count_regression_fails(self):
        with tempfile.TemporaryDirectory() as d, self.assertRaises(ValueError):
            self.fixture(Path(d), tests=1)
            check_module(Path(d), 'example', {'tests': 2, 'LINE': 50, 'BRANCH': 50})

    def test_verified_suite_passes(self):
        with tempfile.TemporaryDirectory() as d:
            self.fixture(Path(d))
            result = check_module(Path(d), 'example', {'tests': 2, 'LINE': 50, 'BRANCH': 50})
            self.assertEqual(result['coverage']['LINE'], 80)


if __name__ == '__main__':
    unittest.main()
