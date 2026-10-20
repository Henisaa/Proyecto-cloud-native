"""Fail closed on absent suites, skipped tests, regressions and incomplete browser runs."""
import argparse
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

MODULES = ['bff', 'shipments', 'catalog', 'notify', 'rabbit-admin', 'audit', 'report', 'kafka-admin']


def check_module(root, module, baseline):
    paths = sorted(root.glob('**/TEST-*.xml'))
    if not paths:
        raise ValueError(f'{module}: no JUnit results; tests may not have executed')
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    cases = set()
    for path in paths:
        suite = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(suite.get(key, 0))
        for case in suite.findall('testcase'):
            identity = (case.get('classname'), case.get('name'))
            if identity in cases:
                raise ValueError(f'{module}: duplicate JUnit case {identity}')
            cases.add(identity)
    if any(totals[k] for k in ['failures', 'errors', 'skipped']):
        raise ValueError(f'{module}: failures/errors/skips are forbidden: {totals}')
    if totals['tests'] < baseline['tests']:
        raise ValueError(f'{module}: test count regressed: {totals["tests"]} < {baseline["tests"]}')
    if len(cases) != totals['tests']:
        raise ValueError(f'{module}: declared test count does not match executed cases')
    reports = list(root.glob('**/jacocoTestReport.xml'))
    if len(reports) != 1:
        raise ValueError(f'{module}: expected exactly one JaCoCo XML, got {len(reports)}')
    report = ET.parse(reports[0]).getroot()
    counters = {c.get('type'): c for c in report.findall('counter')}
    coverage = {}
    for key in ['LINE', 'BRANCH']:
        if key not in counters:
            raise ValueError(f'{module}: missing {key} coverage counter')
        covered, missed = (int(counters[key].get(k)) for k in ['covered', 'missed'])
        coverage[key] = 100 * covered / (covered + missed) if covered + missed else 100
        if coverage[key] < baseline[key]:
            raise ValueError(f'{module}: {key} coverage {coverage[key]:.2f}% < {baseline[key]}%')
    critical_path = Path('ci/critical-coverage.json')
    if critical_path.exists():
        critical = json.loads(critical_path.read_text())
        for cls in report.findall('package/class'):
            if cls.get('name') in critical:
                for counter in cls.findall('counter'):
                    kind = counter.get('type')
                    if kind in critical[cls.get('name')]:
                        covered, missed = (int(counter.get(k)) for k in ['covered', 'missed'])
                        percent = 100 * covered / (covered + missed) if covered + missed else 100
                        if percent < critical[cls.get('name')][kind]:
                            raise ValueError(f'{module}: critical class {cls.get("name")} {kind} regressed to {percent:.2f}%')
    return dict(module=module, **totals, coverage=coverage)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('root', type=Path)
    parser.add_argument('--module')
    args = parser.parse_args()
    baseline = json.loads(Path('ci/coverage-baseline.json').read_text())
    names = [args.module] if args.module else MODULES
    results = []
    for name in names:
        root = args.root if args.module else args.root / f'tests-ms-rutaexpress-{name}'
        results.append(check_module(root, name, baseline[name]))
    if not args.module:
        summaries = list(args.root.glob('**/frontend-results.json'))
        coverage_reports = list(args.root.glob('**/coverage-summary.json'))
        if len(summaries) != 1 or len(coverage_reports) != 1:
            raise ValueError('missing/ambiguous frontend test and coverage results')
        front = json.loads(summaries[0].read_text())
        floor = json.loads(Path('ci/frontend-baseline.json').read_text())
        if front['failed'] or front['skipped'] or front['tests'] < floor['tests']:
            raise ValueError(f'frontend test regression: {front}')
        front_coverage = json.loads(coverage_reports[0].read_text())['total']
        for metric in ['lines', 'branches']:
            if front_coverage[metric]['pct'] < floor[metric]:
                raise ValueError(f'frontend {metric} coverage below {floor[metric]}%')
        browser_files = list(args.root.glob('**/browser-results.json'))
        if len(browser_files) != 1:
            raise ValueError('missing/ambiguous Playwright results')
        stats = json.loads(browser_files[0].read_text())['stats']
        if stats['unexpected'] or stats['skipped'] or stats['flaky'] or stats['expected'] < 30:
            raise ValueError(f'browser tests incomplete or failed: {stats}')
    print(json.dumps(results, indent=2))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, ET.ParseError) as error:
        print(f'QUALITY GATE FAILED: {error}', file=sys.stderr)
        sys.exit(1)
