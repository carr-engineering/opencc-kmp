// Native encoded candidate and value-only dictionary reader contract oracle.
#include "Config.hpp"
#include "Lexicon.hpp"
#include "ConversionCandidates.hpp"
#include "BinaryDict.hpp"
#include "SerializedValues.hpp"
#include <iostream>
#include <string>

static std::string bytes(const std::string& hex) {
  std::string out;
  for (size_t i = 0; i < hex.size(); i += 2) out.push_back(static_cast<char>(std::stoul(hex.substr(i, 2), nullptr, 16)));
  return out;
}
static void hex(std::string_view input) {
  const char* chars = "0123456789abcdef";
  for (unsigned char c : input) std::cout << chars[c >> 4] << chars[c & 15];
}
int main(int argc, char** argv) {
  if (argc < 2) return 2;
  opencc::ConverterPtr converter;
  std::string operation = argv[1];
  if (operation == "candidates") {
    if (argc != 4) return 2;
    opencc::Config config;
    converter = config.NewFromFile(argv[2], std::vector<std::string>{argv[3]}, nullptr);
  }
  std::string line;
  while (std::getline(std::cin, line)) {
    const auto input = bytes(line);
    try {
      if (operation == "candidates") {
        const auto result = opencc::GetAllConversions(*converter, std::string_view(input));
        std::cout << "OK";
        for (const auto& value : result) { std::cout << '|'; hex(value); }
      } else {
        opencc::LexiconPtr lexicon;
        if (operation == "binary") lexicon = opencc::BinaryDict::NewFromBuffer(input.data(), input.size())->GetLexicon();
        else if (operation == "values") lexicon = opencc::SerializedValues::NewFromBuffer(input.data(), input.size(), nullptr)->GetLexicon();
        else return 2;
        std::cout << "OK";
        for (const auto& entry : *lexicon) { std::cout << '|'; hex(entry->Key()); std::cout << ':' << entry->NumValues(); }
      }
    } catch (const opencc::Exception&) { std::cout << "ERROR"; }
    std::cout << '\n';
  }
}
