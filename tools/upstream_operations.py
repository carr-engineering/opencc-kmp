"""Lexically inventory public header declarations, keeping overloads and nested types.

This is a source inventory, not an assertion of behaviour parity or a C++ ABI parser.
Inline bodies and private/protected members are excluded. All preprocessor branches
are included, so Windows declarations remain visible when generated on macOS.
"""
import re

# Public headers include several implementation helpers. Classifying these explicitly
# prevents their exposed C++ mechanics from silently becoming Kotlin API requirements.
INTERNAL_HEADERS = {"Common.hpp", "Export.hpp", "Utf8SkipScan.hpp", "StreamWindow.hpp"}


def public_declarations(text):
    text = re.sub(r'typedef\s+struct\s*\{([^{}]*)\}\s*(\w+)\s*;', r'struct \2 {\1};', text, flags=re.S)
    text = re.sub(r'/\*.*?\*/|//[^\n]*', '', text, flags=re.S)
    text = re.sub(r'^\s*#.*(?:\\\n.*)*', '', text, flags=re.M)
    # Literal braces cannot affect scope. Preserve quoted content in recorded declarations.
    tokens = re.finditer(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|\b(?:public|private|protected)\s*:|[{};]|[^{};"\']+', text)
    stack = []
    pending = ''
    skip = 0
    declarations = []

    def visible():
        return all(frame['access'] == 'public' for frame in stack if frame['kind'] == 'class')

    def emit(value):
        value = ' '.join(value.split()).strip()
        if value == 'enum' or value.startswith(('class ', 'struct ', 'enum ', 'namespace ', 'extern ')):
            return
        if not value or not visible() or not any(c in value for c in ('(', '=')) and value.startswith(('using ', 'typedef ')):
            return
        if any(frame['name'] in ('internal', 'detail') for frame in stack if frame['kind'] == 'namespace'):
            return
        if value.startswith(('using ', 'typedef ', 'template <>')):
            return
        owner = '::'.join(frame['name'] for frame in stack if frame['kind'] == 'class')
        match = re.search(r'(operator\s*(?:\(\)|[^\s(]+)|~?[A-Za-z_]\w*)\s*\(', value)
        pointer = re.search(r'\(\s*\*\s*(\w+)\s*\)', value)
        symbol = pointer.group(1) if pointer else match.group(1).replace(' ', '') if match else re.sub(r'\s*=.*', '', value).split()[-1]
        declarations.append({'owner': owner, 'symbol': symbol, 'declaration': value})

    for match in tokens:
        token = match.group()
        if skip:
            if token == '{': skip += 1
            elif token == '}': skip -= 1
            continue
        if re.fullmatch(r'(public|private|protected)\s*:', token):
            if stack: stack[-1]['access'] = token.split(':')[0].strip()
            pending = ''
        elif token == '{':
            value = ' '.join(pending.split())
            ns = re.search(r'\bnamespace\s+(\w+)\s*$', value)
            cls = re.search(r'\b(class|struct)\s+(?:OPENCC_EXPORT\s+)?(\w+)(?:<[^>]+>)?(?:\s*:[^{]+)?\s*$', value)
            if value.startswith('extern'):
                stack.append({'kind': 'namespace', 'name': 'C', 'access': 'public'})
            elif ns:
                stack.append({'kind': 'namespace', 'name': ns.group(1), 'access': 'public'})
            elif cls and '(' not in value:
                stack.append({'kind': 'class', 'name': cls.group(2), 'access': 'public' if cls.group(1) == 'struct' else 'private'})
            else:
                emit(pending)
                skip = 1
            pending = ''
        elif token == '}':
            if stack: stack.pop()
            pending = ''
        elif token == ';':
            emit(pending)
            pending = ''
        else:
            # Access labels may occur inside a larger non-delimiter token.
            chunks = re.split(r'\b(public|private|protected)\s*:', token)
            pending += chunks[0]
            for i in range(1, len(chunks), 2):
                if stack: stack[-1]['access'] = chunks[i]
                pending = chunks[i + 1]
    return declarations
