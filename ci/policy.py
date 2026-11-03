"""Repository test policy, evaluated before publishing any image."""
import json
from pathlib import Path
import re
import subprocess
import os

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
base_sha = os.environ.get('BASE_SHA', '')
if base_sha and set(base_sha) != {'0'}:
    for file in ['ci/coverage-baseline.json', 'ci/frontend-baseline.json', 'ci/critical-coverage.json']:
        previous = subprocess.run(['git', 'show', f'{base_sha}:{file}'], capture_output=True, text=True)
        if previous.returncode == 0:
            before, after = json.loads(previous.stdout), json.loads(Path(file).read_text())
            def ratchet(old, new, key=''):
                for name, value in old.items():
                    assert name in new, f'Cannot remove coverage floor {key}/{name}'
                    if isinstance(value, dict): ratchet(value, new[name], f'{key}/{name}')
                    else: assert new[name] >= value, f'Cannot lower quality floor {key}/{name}'
            ratchet(before, after)
print('Full-suite and no-skip policies passed')
