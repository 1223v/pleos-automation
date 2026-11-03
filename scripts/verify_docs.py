"""Check repository Markdown local files, fence balance, and fenced JSON examples.

Uses rg's ignore rules so generated SDK, dependency, and build documentation is excluded.
This is a documentation check; it does not validate application behavior or remote URLs.
"""
import json
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

root = Path(__file__).resolve().parent.parent
files = subprocess.check_output(["rg", "--files", "-g", "*.md"], cwd=root, encoding="utf-8").splitlines()
errors = []
links = 0
examples = 0
for filename in files:
    path = root / filename
    text = path.read_text(encoding="utf-8-sig")
    fences = re.findall(r"^\s*```[^\n]*$", text, flags=re.MULTILINE)
    if len(fences) % 2:
        errors.append(f"{filename}: unbalanced code fences")
    for example in re.finditer(r"^\s*```json\s*\n(.*?)^\s*```\s*$", text, flags=re.MULTILINE | re.DOTALL):
        examples += 1
        try:
            json.loads(example.group(1))
        except json.JSONDecodeError as error:
            errors.append(f"{filename}: invalid JSON example: {error}")
    prose = re.sub(r"^\s*```[^\n]*\n.*?^\s*```\s*$", "", text, flags=re.MULTILINE | re.DOTALL)
    for match in re.finditer(r"!?\[[^\]]*\]\((<[^>]+>|[^)\s]+)(?:\s+[\"'][^)]*)?\)", prose):
        target = match.group(1).strip("<>")
        if target.startswith("#") or urlsplit(target).scheme:
            continue
        target = unquote(target.split("#", 1)[0])
        if not target:
            continue
        links += 1
        if not (path.parent / target).is_file():
            errors.append(f"{filename}: missing linked file {target}")
print(f"Markdown files: {len(files)}, local file links: {links}, JSON examples: {examples}")
for error in errors:
    print(error)
print(f"Errors: {len(errors)}")
sys.exit(bool(errors))
