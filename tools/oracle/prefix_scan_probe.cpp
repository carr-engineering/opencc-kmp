// Native observable PrefixMatch skip-distance oracle; no runtime dependency.
#include "Config.hpp"
#include "Converter.hpp"
#include "Conversion.hpp"
#include "ConversionChain.hpp"
#include "PrefixMatch.hpp"
#include <iostream>
#include <string>
static std::string bytes(const std::string& hex) {
  std::string out;
  for (size_t i = 0; i < hex.size(); i += 2) out.push_back(static_cast<char>(std::stoul(hex.substr(i, 2), nullptr, 16)));
  return out;
}
int main(int argc, char** argv) {
  if (argc != 3) return 2;
  opencc::Config loader;
  const auto converter = loader.NewFromFile(argv[1], std::vector<std::string>{argv[2]}, nullptr);
  const auto dict = converter->GetConversionChain()->GetConversions().front()->GetDict();
  const opencc::PrefixMatch matcher(dict);
  std::string line;
  while (std::getline(std::cin, line)) {
    const auto input = bytes(line);
    std::cout << matcher.SkipUnmatchable(input.data(), input.size()) << '\n';
  }
}
