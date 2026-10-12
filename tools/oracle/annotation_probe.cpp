// Test-only probe for the pinned upstream unstable API, never a runtime binding.
#include "Config.hpp"
#include "ConversionAmbiguities.hpp"
#include "ConversionCandidates.hpp"
#include "UTF8Util.hpp"
#include <fstream>
#include <iomanip>
#include <iostream>
#include <iterator>

void hex(std::string_view text) {
  const char* digits = "0123456789abcdef";
  for (unsigned char byte : text) std::cout << digits[byte >> 4] << digits[byte & 15];
}
int main(int argc, char** argv) {
  if (argc != 4 && argc != 5) return 2;
  std::ifstream input(argv[2], std::ios::binary);
  std::string text((std::istreambuf_iterator<char>(input)), std::istreambuf_iterator<char>());
  opencc::Config config;
  auto converter = config.NewFromFile(argv[1], std::vector<std::string>{argv[3]}, nullptr);
  if (argc == 5) {
    opencc::AmbiguityStream stream(converter);
    auto emit = [](const opencc::AmbiguityStream::Chunk& chunk) {
      std::cout << "OUTPUT\t"; hex(chunk.output); std::cout << '\n';
      std::cout << "ANALYZED\t" << chunk.analyzed << '\n';
      for (const auto& source : chunk.newSources) { std::cout << "SOURCE\t"; hex(source); std::cout << '\n'; }
      for (const auto& span : chunk.ambiguities)
        std::cout << "SPAN\t" << span.outputOffset << '\t' << span.outputLength << '\t' << span.sourceIndex << '\n';
    };
    size_t start = 0;
    while (start < text.size()) {
      size_t end = start;
      for (int i = 0; i < 3 && end < text.size(); i++) end += opencc::UTF8Util::NextCharLength(text.data() + end);
      emit(stream.ConvertChunk(std::string_view(text).substr(start, end - start)));
      start = end;
    }
    emit(stream.Finish());
    std::cout << "COUNT\t" << stream.SourceCount() << '\n';
    return 0;
  }
  const auto result = opencc::ConvertWithAmbiguities(*converter, text);
  std::cout << "OUTPUT\t"; hex(result.output); std::cout << '\n';
  std::cout << "ANALYZED\t" << result.analyzed << '\n';
  for (const auto& source : result.sources) { std::cout << "SOURCE\t"; hex(source); std::cout << '\n'; }
  for (const auto& span : result.ambiguities)
    std::cout << "SPAN\t" << span.outputOffset << '\t' << span.outputLength << '\t' << span.sourceIndex << '\n';
  for (const auto& candidate : opencc::GetAllConversions(*converter, text)) {
    std::cout << "CANDIDATE\t"; hex(candidate); std::cout << '\n';
  }
}
