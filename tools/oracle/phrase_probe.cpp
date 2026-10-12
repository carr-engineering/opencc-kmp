// Test-only probe linked to the pinned upstream library; never shipped with Kotlin artifacts.
#include "PhraseExtract.hpp"
#include <fstream>
#include <iomanip>
#include <iostream>
#include <iterator>
#include <set>

int main(int argc, char** argv) {
  if (argc != 6) return 2;
  std::ifstream input(argv[1], std::ios::binary);
  std::string text((std::istreambuf_iterator<char>(input)), std::istreambuf_iterator<char>());
  opencc::PhraseExtract extractor;
  extractor.SetWordMinLength(std::stoul(argv[2]));
  extractor.SetWordMaxLength(std::stoul(argv[3]));
  extractor.SetPrefixSetLength(std::stoul(argv[4]));
  extractor.SetSuffixSetLength(std::stoul(argv[5]));
  extractor.SetPostCalculationFilter([](const auto&, const auto&) { return false; });
  extractor.Extract(text);
  std::cout << std::setprecision(17);
  for (const auto& word : extractor.WordCandidates()) {
    const auto& signal = extractor.Signal(word);
    std::cout << word.ToString() << '\t' << signal.frequency << '\t' << signal.cohesion << '\t'
              << signal.prefixEntropy << '\t' << signal.suffixEntropy << '\t'
              << extractor.LogProbability(word) << '\t' << extractor.Probability(word) << '\t'
              << !opencc::PhraseExtract::DefaultPostCalculationFilter(extractor, word) << '\n';
  }
}
