"""Generate pinned cppjieba C atof token contracts, including hexadecimal rounding edges."""
import argparse
import json
import random
import subprocess
from upstream import ROOT
from build_probes import build_probes


def prepare(probes_ready=False):
    if not probes_ready:
        build_probes()
    cases = ['text', '', '12.5suffix', '\v\f-12.5tail', '1e+tail', '1e2tail',
             '0x1p2tail', '0xdead', '0x.8', '-0X1.8p+2', '0x1p-1074', '0x1p-1075',
             '0x1.0000000000001p-1075', '0x1.fffffffffffffp1023', '0x1.fffffffffffff8p1023',
             '0x1.00000000000008p0', '0x1.00000000000008001p0', '0x0.fffffffffffff8p-1022',
             '-0x0p1000000000000', '0x1p1000000000000', '0x1p-1000000000000',
             '0x1' + '0' * 400 + 'p-1600', '0x1' + '0' * 400 + 'p-1601',
             'INFtail', '-infinitytail', '12\x00.5', '0x', '0x.p2', '0x1p+tail']
    randomizer = random.Random(142)
    for _ in range(256):
        size = randomizer.randrange(1, 90)
        digits = ''.join(randomizer.choice('0123456789abcdef') for _ in range(size))
        split = randomizer.randrange(size + 1)
        cases.append(randomizer.choice(['', '-', '+', '\v\f ']) + '0x' + digits[:split] + '.' +
                     digits[split:] + 'p' + str(randomizer.randrange(-1600, 1600)) + 'suffix')
    for exponent in [-400, -324, -323, -308, -1, 0, 1, 308, 309, 400]:
        for mantissa in ['0', '-0', '1', '1.234567890123456789', '1.7976931348623157']:
            cases.append(f'{mantissa}e{exponent}tail')
    result = subprocess.run([str(ROOT / '.cache/probes/jieba_numeric_probe')],
        input=''.join(token.encode().hex() + '\n' for token in cases),
        text=True, capture_output=True, check=True).stdout.splitlines()
    assert len(result) == len(cases)
    output = ROOT / '.cache/conformance/jieba-numbers.tsv'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(''.join(token.encode().hex() + '\t' + bits + '\n' for token, bits in zip(cases, result)))
    print(json.dumps({'cases': len(cases), 'file': str(output)}))
    return output


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--probes-ready', action='store_true')
    prepare(parser.parse_args().probes_ready)
