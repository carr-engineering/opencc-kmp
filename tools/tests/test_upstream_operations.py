import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from upstream_operations import public_declarations


class DeclarationInventoryTest(unittest.TestCase):
    def test_public_nested_overloads_and_private_bodies(self):
        records = public_declarations('''
        namespace opencc {
        class Example {
          void Hidden();
        public:
          Example(int x) : value(x) {}
          int Read() const { if (true) { return value; } return 0; }
          int Read(int n) const;
          struct Result { int count = 0; };
        private:
          int value;
          void Internal() {}
        };
        namespace internal { inline int HiddenFunction() { return 0; } }
        }
        ''')
        self.assertEqual(['Example', 'Read', 'Read', 'count'], [r['symbol'] for r in records])
        self.assertEqual('Example::Result', records[-1]['owner'])

    def test_c_callbacks_and_conditional_branches(self):
        records = public_declarations('''
        extern "C" {
        typedef struct { int (*create)(void* args); size_t size; } descriptor;
        #ifdef _WIN32
        int wide(const wchar_t* name);
        #endif
        int narrow(const char* name);
        }
        ''')
        self.assertEqual(['create', 'size', 'wide', 'narrow'], [r['symbol'] for r in records])
        self.assertEqual('descriptor', records[0]['owner'])


if __name__ == '__main__': unittest.main()
