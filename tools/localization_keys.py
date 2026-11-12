#!/usr/bin/env python3
"""Extract literal and dynamic English localization calls for translation review."""
import json
import base64
import re
import sys
from pathlib import Path

TOKEN = re.compile(r'''//[^\n]*|/\*[\s\S]*?\*/|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|\w+|[^\s]''')

def data_keys(root):
    """Labels passed to I18n via the launcher's UI data tables."""
    tables = {
        'ControlPanelOverlay.java': {'ALL_CP_ITEMS': 0},
        'Profiles.java': {'COLOR_NAMES': None, 'EMOJI_NAMES': None},
    }
    for filename, arrays in tables.items():
        source = (root / 'src/com/techgenius/tvoslauncher' / filename).read_text()
        for array, column in arrays.items():
            body = re.search(r'\b' + array + r'\s*=\s*\{([\s\S]*?)\};', source).group(1)
            rows = re.findall(r'\{([^{}]*)\}', body) if column is not None else [body]
            for row in rows:
                values = [json.loads(s) for s in re.findall(r'"(?:[^"\\]|\\.)*"', row)]
                yield from ([values[column]] if column is not None else values)
    source = (root / 'src/com/techgenius/tvoslauncher/Scenes.java').read_text()
    for match in re.finditer(r'new Spec\([^,]+,\s*("(?:[^"\\]|\\.)*")\s*,', source):
        yield json.loads(match.group(1))

def calls(path):
    source = path.read_text()
    tokens = [(m.group(), m.start(), m.end()) for m in TOKEN.finditer(source)
              if not m.group().startswith(('//', '/*'))]
    for i, (name, start, _) in enumerate(tokens):
        if name not in ('t', 'tr', 'format') or i + 1 >= len(tokens) or tokens[i + 1][0] != '(':
            continue
        if name != 'tr' and (i < 2 or tokens[i - 2][0] != 'I18n'):
            continue
        args, current, depth = [], [], 0
        end = None
        for tok in tokens[i + 2:]:
            value = tok[0]
            if value == ')' and depth == 0:
                args.append(current)
                end = tok[2]
                break
            if value == ',' and depth == 0:
                args.append(current)
                current = []
                continue
            if value in ('(', '[', '{'): depth += 1
            if value in (')', ']', '}'): depth -= 1
            current.append(tok)
        contextual = name != 'tr' or path.name == 'CleanYourTv.java'
        if end is None or len(args) < (2 if contextual else 1): continue
        english = args[1] if contextual else args[0]
        if not english or not any(t[0].startswith('"') for t in english): continue
        literal = all(t[0].startswith('"') or t[0] == '+' for t in english)
        value = ''.join(json.loads(t[0]) for t in english if t[0].startswith('"')) if literal else None
        yield {'key': value, 'file': str(path), 'line': source.count('\n', 0, start) + 1,
               'expression': source[english[0][1]:english[-1][2]]}

if __name__ == '__main__':
    root = Path(__file__).resolve().parents[1]
    entries = [call for path in sorted((root / 'src/com/techgenius/tvoslauncher').rglob('*.java'))
               if path.name != 'I18n.java' for call in calls(path)]
    if '--keys' in sys.argv:
        keys = {c['key'] for c in entries if c['key'] is not None} | set(data_keys(root))
        for key in sorted(keys):
            print(base64.b64encode(key.encode()).decode())
    else:
        print(json.dumps(entries, ensure_ascii=False, indent=2))
