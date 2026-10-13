// Pinned ConversionTest reference loop / mt19937 corpus, Apache-2.0. Test oracle only.
#include "Config.hpp"
#include "Converter.hpp"
#include "Conversion.hpp"
#include "ConversionChain.hpp"
#include "MarisaDict.hpp"
#include "PrefixMatch.hpp"
#include "UTF8Util.hpp"
#include <iostream>
#include <random>
#include <vector>
using namespace opencc;
static std::string utf8(const char* value) { return value; }
static std::string hex(const std::string& text) { const char* digits="0123456789abcdef"; std::string out; for (unsigned char c : text) {out += digits[c>>4];out += digits[c&15];}return out; }
std::string ReferenceConvert(const PrefixMatch& prefixMatch,
                             const std::string& input) {
  std::string output;
  const char* pstr = input.data();
  const char* end = pstr + input.size();
  while (pstr < end) {
    const size_t remaining = end - pstr;
    const PrefixMatchView matched = prefixMatch.MatchPrefixView(pstr, remaining);
    size_t matchedLength;
    if (!matched.matched) {
      matchedLength =
          UTF8Util::NextIdeographicDescriptionSequenceLength(pstr, remaining);
      if (matchedLength == 0) {
        matchedLength = UTF8Util::NextCharLength(pstr);
      }
      if (matchedLength > remaining) {
        matchedLength = remaining;
      }
      output.append(pstr, matchedLength);
    } else {
      matchedLength = matched.keyLength;
      if (matchedLength > remaining) {
        matchedLength = remaining;
      }
      output.append(matched.value.data(), matched.value.size());
    }
    pstr += matchedLength;
  }
  return output;
}

int main(int argc, char** argv) {
 if (argc != 3) return 2;
 const auto converter = Config().NewFromFile(argv[1], std::vector<std::string>{argv[2]}, argv[0]);
 const auto dict = converter->GetConversionChain()->GetConversions().front()->GetDict();
 const Conversion conversion(dict);
 const auto marisaDict = MarisaDict::NewFromDict(*dict);
 const Conversion marisaConversion(marisaDict);
 const PrefixMatch groupReference(dict), marisaReference(marisaDict);
  const std::vector<std::string> fragments = {
      utf8("太后"),   utf8("头发"),  utf8("干燥"),  utf8("鼠标"),
      utf8("干"),     utf8("后"),    utf8("发"),    utf8("里"),
      utf8("天地"),   utf8("、。"), utf8("⿰"),    utf8("⿳"),
      utf8("——"),   utf8("🎉"),    "ascii text ",  "12345",
  };
  for (int target = 0; target < 2; ++target) {
    std::mt19937 rng(20260724);
    for (int iteration = 0; iteration < 300; iteration++) {
      std::string input;
      const int pieces = 1 + static_cast<int>(rng() % 40);
      for (int i = 0; i < pieces; i++) {
        const uint32_t choice = rng() % 10;
        if (choice < 7) {
          input += fragments[rng() % fragments.size()];
        } else if (choice < 9) {
          // Raw bytes, frequently invalid or truncated UTF-8.
          const int count = 1 + static_cast<int>(rng() % 4);
          for (int b = 0; b < count; b++) {
            input.push_back(static_cast<char>(rng() % 256));
          }
        } else if (!input.empty()) {
          // Truncate the tail mid-character.
          input.resize(input.size() - 1);
        }
      }
      bool referenceThrew = false;
      bool convertThrew = false;
      std::string expected, actual;
      try {
        expected = ReferenceConvert(target == 0 ? groupReference : marisaReference, input);
      } catch (const InvalidUTF8&) {
        referenceThrew = true;
      }
      try {
        actual = (target == 0 ? conversion : marisaConversion).Convert(input);
      } catch (const InvalidUTF8&) {
        convertThrew = true;
      }
      if (referenceThrew != convertThrew || (!referenceThrew && expected != actual)) return 3;
      std::cout << target << "|" << hex(input) << "|" << (referenceThrew ? "ERROR" : "OK") << "|" << hex(expected) << "\n";
    }
  }
}
