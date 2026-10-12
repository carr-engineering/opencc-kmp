// Test-only native UTF8Util contract oracle, pinned by upstream.lock.json.
#include "UTF8Util.hpp"
#include <iostream>
#include <sstream>
#include <string>

static std::string bytes(const std::string& hex) {
  std::string result;
  for (size_t i = 0; i < hex.size(); i += 2) result.push_back(static_cast<char>(std::stoul(hex.substr(i, 2), nullptr, 16)));
  return result;
}
static std::string hex(const std::string& input) {
  const char* digits = "0123456789abcdef";
  std::string out;
  for (unsigned char byte : input) { out += digits[byte >> 4]; out += digits[byte & 15]; }
  return out;
}
int main() {
  std::string row;
  while (std::getline(std::cin, row)) {
    const auto first = row.find('\t');
    const auto second = row.find('\t', first + 1);
    const auto operation = row.substr(0, first);
    const auto arg = std::stoul(row.substr(first + 1, second - first - 1));
    const auto input = bytes(row.substr(second + 1));
    try {
      if (operation == "width") std::cout << opencc::UTF8Util::NextCharLengthNoException(input.c_str());
      else if (operation == "length") std::cout << opencc::UTF8Util::Length(input.c_str());
      else if (operation == "truncate") std::cout << hex(opencc::UTF8Util::TruncateUTF8(input.c_str(), arg));
      else if (operation == "selector") std::cout << opencc::UTF8Util::ContainsVariationSelector(input.data(), input.size());
      else if (operation == "ids") std::cout << opencc::UTF8Util::NextIdeographicDescriptionSequenceLength(input.data(), input.size());
      else if (operation == "incomplete") std::cout << opencc::UTF8Util::IsIncompleteIdeographicDescriptionSequencePrefix(input.data(), input.size());
      else return 2;
    } catch (const opencc::Exception&) { std::cout << "ERROR"; }
    std::cout << '\n';
  }
}
