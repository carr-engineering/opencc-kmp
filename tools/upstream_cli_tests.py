"""Trace explicit CLI case names without executing/importing the process harness."""
import ast


def cli_case_review(root, name):
    path = root / 'tools/test_cli_upstream.py'
    if not path.exists(): return None
    tree = ast.parse(path.read_text())
    cases = next((node.value for node in tree.body if isinstance(node, ast.Assign)
                  and any(isinstance(target, ast.Name) and target.id == 'CASES' for target in node.targets)), None)
    if not isinstance(cases, ast.Dict): return None
    names = {node.value for node in cases.keys if isinstance(node, ast.Constant) and isinstance(node.value, str)}
    extras = next((node.value for node in tree.body if isinstance(node, ast.Assign)
                   and any(isinstance(target, ast.Name) and target.id == 'EXTRA_CASES' for target in node.targets)), None)
    if isinstance(extras, ast.List):
        assert any(isinstance(node, ast.FunctionDef) and node.name == 'extended_case' for node in tree.body)
        assert any(isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute)
                   and isinstance(node.func.value, ast.Name) and node.func.value.id == 'CASES'
                   and node.func.attr == 'update' for node in ast.walk(tree))
        names.update(node.value for node in extras.elts if isinstance(node, ast.Constant) and isinstance(node.value, str))
    if name not in names: return None
    assert any(isinstance(node, ast.FunctionDef) and node.name == 'verify' for node in tree.body)
    return {'port_review': 'ported-assertions', 'kotlin_test': 'tools/test_cli_upstream.py::verify[' + name + ']',
            'kotlin_test_file': str(path.relative_to(root)),
            'contract_adaptation': 'Named CLI case compares native and Kotlin process status/stdout/stderr/output bytes and explicit native assertions; filesystem adapters own I/O'}
