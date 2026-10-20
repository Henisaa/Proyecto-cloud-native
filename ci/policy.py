"""Repository test policy, evaluated before publishing any image."""
import json
from pathlib import Path
import re
import subprocess

workflow = Path('.github/workflows/ci.yml').read_text()
assert 'paths:' not in workflow and 'branches:' not in workflow, 'Every push must run the full pipeline'
assert 'continue-on-error:' not in workflow, 'Failures cannot be ignored'
assert 'cancel-in-progress: true' not in workflow, 'Every push must finish its verification'
for path in Path('.').glob('**/*'):
    if any(part in {'node_modules', 'build', 'work', '.git', '.gradle'} for part in path.parts):
        continue
    if path.is_file() and (path.name.endswith('.spec.ts') or path.name.endswith('Tests.kt')):
        text = path.read_text(encoding='utf-8')
        assert not re.search(r'\b(?:test|describe|it)\.(?:only|skip|fixme)\s*\(', text), f'Focused/skipped test: {path}'
        assert '@Disabled' not in text, f'Disabled test: {path}'
baseline = json.loads(Path('ci/coverage-baseline.json').read_text())
assert len(baseline) == 8
assert all(v['LINE'] > 0 and v['BRANCH'] > 0 and v['tests'] > 0 for v in baseline.values())
assert 'e2e-main' not in Path('frontend/src/main.ts').read_text()
assert 'e2e' not in Path('frontend/tsconfig.app.json').read_text()
print('Full-suite and no-skip policies passed')
